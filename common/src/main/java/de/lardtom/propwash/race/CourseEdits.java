package de.lardtom.propwash.race;

import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.ServerRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CourseEdits {
    private static final long WINDOW_NANOS = 2_000_000_000L;
    private static final int WINDOW_EDITS = 80;
    private static final Map<UUID, Integer> DEFAULT_LAPS = new HashMap<>();
    private static final Map<UUID, long[]> RATE = new HashMap<>();

    public enum Action {
        RENAME,
        LAPS,
        MOVE,
        START,
        REMOVE,
        DELETE,
        BIND_TOOL;

        private static final Action[] VALUES = values();

        public static Action byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : RENAME;
        }
    }

    public enum Result {
        DONE,
        UNCHANGED,
        NO_COURSE,
        DENIED,
        NOT_STARTABLE,
        NO_TOOL
    }

    private CourseEdits() {
    }

    static void clear() {
        DEFAULT_LAPS.clear();
        RATE.clear();
    }

    static void forget(ServerPlayer player) {
        DEFAULT_LAPS.remove(player.getUUID());
        RATE.remove(player.getUUID());
    }

    public static int defaultLaps(UUID player) {
        return DEFAULT_LAPS.getOrDefault(player, CourseLaps.UNSET);
    }

    public static void setDefaultLaps(UUID player, int laps) {
        DEFAULT_LAPS.put(player, CourseLaps.clamp(laps));
    }

    public static int lapsFor(CourseRegistry.Course course, UUID player) {
        int personal = defaultLaps(player);
        return CourseLaps.resolve(course.laps, CourseLaps.set(personal) ? personal : CourseLaps.FALLBACK);
    }

    static void onDefaults(RacePayloads.CourseDefaultsC2S payload, ServerPayloadContext context) {
        setDefaultLaps(context.player().getUUID(), payload.laps());
    }

    static void onEdit(RacePayloads.CourseEditC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        if (!allow(player.getUUID(), System.nanoTime())) {
            return;
        }
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(payload.courseId());
        Component label = course == null ? Component.empty() : CourseRegistry.label(course);
        CourseAccess access = course == null ? CourseAccess.ALLOWED : access(player, course, payload.action());
        Result result = apply(player, payload);
        Component message = message(result, payload, course, label, access);
        if (message != null) {
            player.sendOverlayMessage(message);
        }
    }

    static boolean allow(UUID player, long now) {
        long[] window = RATE.computeIfAbsent(player, uuid -> new long[]{now, 0L});
        if (now - window[0] > WINDOW_NANOS) {
            window[0] = now;
            window[1] = 0L;
        }
        return ++window[1] <= WINDOW_EDITS;
    }

    public static Result apply(ServerPlayer player, RacePayloads.CourseEditC2S payload) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(payload.courseId());
        if (registry == null || course == null) {
            return Result.NO_COURSE;
        }
        if (!access(player, course, payload.action()).allowed()) {
            return Result.DENIED;
        }
        Result result = switch (payload.action()) {
            case RENAME -> done(registry.rename(course, payload.text()));
            case LAPS -> done(registry.setLaps(course, CourseLaps.clamp(payload.first())));
            case MOVE -> move(registry, course, payload.first(), payload.second());
            case START -> start(registry, course, payload.first());
            case REMOVE -> remove(registry, course, payload.first());
            case DELETE -> done(registry.deleteCourse(course.id));
            case BIND_TOOL -> bind(player, course);
        };
        if (result == Result.DONE && payload.action() == Action.DELETE) {
            registry.save();
        }
        return result;
    }

    public static CourseAccess access(ServerPlayer player, CourseRegistry.Course course, Action action) {
        boolean build = !player.isSpectator() && player.mayBuild();
        boolean sameDimension = CourseRegistry.sameDimension(course, player.level());
        boolean owner = course.ownedBy(player.getUUID());
        if (action == Action.DELETE) {
            return CourseAccess.delete(build, sameDimension, owner, CourseRegistry.isGamemaster(player) || ServerRules.isHost(player),
                    !course.worldCourse.isEmpty());
        }
        return CourseAccess.edit(build, sameDimension, course.owner.isEmpty(), owner, CourseRegistry.isGamemaster(player), near(player, course));
    }

    public static boolean mayEdit(ServerPlayer player, CourseRegistry.Course course, Action action) {
        return access(player, course, action).allowed();
    }

    private static boolean near(ServerPlayer player, CourseRegistry.Course course) {
        for (CourseRegistry.GateRecord gate : course.gates) {
            if (CourseAccess.near(player.distanceToSqr(gate.x, gate.y, gate.z), RaceCommands.NEAREST_COURSE_RANGE)) {
                return true;
            }
        }
        return false;
    }

    private static Result done(boolean changed) {
        return changed ? Result.DONE : Result.UNCHANGED;
    }

    private static Result move(CourseRegistry registry, CourseRegistry.Course course, int from, int to) {
        if (!CourseOrder.inside(course.gates.size(), from) || !CourseOrder.inside(course.gates.size(), to) || from == to) {
            return Result.UNCHANGED;
        }
        return registry.moveGate(course, from, to) ? Result.DONE : Result.NOT_STARTABLE;
    }

    private static Result start(CourseRegistry registry, CourseRegistry.Course course, int index) {
        if (index == 0 || !CourseOrder.inside(course.gates.size(), index)) {
            return Result.UNCHANGED;
        }
        return registry.makeStart(course, index) ? Result.DONE : Result.NOT_STARTABLE;
    }

    private static Result remove(CourseRegistry registry, CourseRegistry.Course course, int index) {
        if (!CourseOrder.inside(course.gates.size(), index)) {
            return Result.UNCHANGED;
        }
        if (!CourseOrder.canRemove(course.kinds(), index)) {
            return Result.NOT_STARTABLE;
        }
        return done(registry.detachGate(course, index));
    }

    private static Result bind(ServerPlayer player, CourseRegistry.Course course) {
        ItemStack tool = tool(player);
        if (tool == null) {
            return Result.NO_TOOL;
        }
        if (CourseToolItem.courseOf(tool) == course.id) {
            return Result.UNCHANGED;
        }
        tool.set(RaceModule.COURSE.get(), course.id);
        return Result.DONE;
    }

    private static @Nullable ItemStack tool(ServerPlayer player) {
        if (CourseToolItem.isCourseTool(player.getMainHandItem())) {
            return player.getMainHandItem();
        }
        if (CourseToolItem.isCourseTool(player.getOffhandItem())) {
            return player.getOffhandItem();
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (CourseToolItem.isCourseTool(stack)) {
                return stack;
            }
        }
        return null;
    }

    public static Component denied(CourseAccess access, Component label, Component owner) {
        return Component.translatable("propwash.race.course_edit.denied." + access.name().toLowerCase(Locale.ROOT), label, owner,
                (int) RaceCommands.NEAREST_COURSE_RANGE).withStyle(ChatFormatting.RED);
    }

    private static @Nullable Component message(Result result, RacePayloads.CourseEditC2S payload, CourseRegistry.@Nullable Course course,
                                               Component label, CourseAccess access) {
        return switch (result) {
            case DENIED -> course == null ? null : denied(access, label, CourseRegistry.ownerName(course));
            case NOT_STARTABLE -> Component.translatable("propwash.race.course_edit.not_startable").withStyle(ChatFormatting.RED);
            case NO_TOOL -> Component.translatable("propwash.race.course_edit.no_tool").withStyle(ChatFormatting.YELLOW);
            case NO_COURSE -> Component.translatable("propwash.race.command.unknown_course", payload.courseId()).withStyle(ChatFormatting.RED);
            case DONE -> switch (payload.action()) {
                case DELETE -> Component.translatable("propwash.race.course_edit.deleted", label).withStyle(ChatFormatting.YELLOW);
                case BIND_TOOL -> Component.translatable("propwash.race.course_edit.tool_bound", label).withStyle(ChatFormatting.GREEN);
                default -> null;
            };
            case UNCHANGED -> payload.action() == Action.BIND_TOOL
                    ? Component.translatable("propwash.race.course_edit.tool_bound", label).withStyle(ChatFormatting.GREEN) : null;
        };
    }
}
