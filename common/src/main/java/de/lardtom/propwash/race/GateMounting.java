package de.lardtom.propwash.race;

import de.lardtom.propwash.network.ServerPayloadContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class GateMounting {
    public static final double EXTRA_REACH = 2.0;
    public static final double EDIT_REACH = 16.0;
    private static final Map<UUID, GatePlacement.Preference> PREFERENCES = new ConcurrentHashMap<>();
    private static volatile Function<Player, GatePlacement.Preference> client = player -> GatePlacement.Preference.DEFAULT;
    private static volatile int lastUnlinked = GateEntity.NO_COURSE;
    private static volatile Component lastRescan = Component.empty();

    public enum Result {
        ROTATED,
        EDITED,
        UNLINKED,
        MISSING,
        TOO_FAR,
        NOT_ALLOWED,
        NOT_OWNER,
        STACK_LOCKED,
        BLOCKED,
        NEED_TOOL,
        NOT_IN_COURSE,
        RESCANNED,
        RESCAN_FAILED,
        SIGNAL_SET
    }

    private GateMounting() {
    }

    public static void setClientPreference(Function<Player, GatePlacement.Preference> source) {
        client = source;
    }

    public static GatePlacement.Preference preference(@Nullable Player player, boolean clientSide) {
        if (player == null) {
            return GatePlacement.Preference.DEFAULT;
        }
        if (clientSide) {
            return client.apply(player);
        }
        return PREFERENCES.getOrDefault(player.getUUID(), GatePlacement.Preference.DEFAULT);
    }

    public static void setPreference(ServerPlayer player, GatePlacement.Preference preference) {
        if (preference.equals(GatePlacement.Preference.DEFAULT)) {
            PREFERENCES.remove(player.getUUID());
        } else {
            PREFERENCES.put(player.getUUID(), preference);
        }
    }

    static void forget(ServerPlayer player) {
        PREFERENCES.remove(player.getUUID());
    }

    static void clear() {
        PREFERENCES.clear();
    }

    static void onPreference(RacePayloads.GatePreferenceC2S payload, ServerPayloadContext context) {
        setPreference(context.player(), payload.preference());
    }

    static void onRotate(RacePayloads.GateRotateC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        Entity entity = player.level().getEntity(payload.entityId());
        GateEntity gate = entity instanceof GateEntity found ? found : null;
        Result result = gate == null ? Result.MISSING : rotate(player, gate, payload.changeMount(), payload.step());
        Component message = message(result, gate);
        if (message != null) {
            player.sendOverlayMessage(message);
        }
    }

    static void onEdit(RacePayloads.GateEditC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        Entity entity = player.level().getEntity(payload.entityId());
        GateEntity gate = entity instanceof GateEntity found ? found : null;
        Result result;
        if (gate == null) {
            result = Result.MISSING;
        } else if (payload.action() == RacePayloads.GateEditAction.UNLINK) {
            result = unlink(player, gate);
        } else if (payload.action() == RacePayloads.GateEditAction.RESCAN) {
            result = holdsTool(player) ? rescan(player, gate) : Result.NEED_TOOL;
        } else if (!holdsTool(player)) {
            result = Result.NEED_TOOL;
        } else {
            result = edit(player, gate, payload.heading(), payload.pitch(), payload.roll(), payload.openingWidth(), payload.openingHeight(),
                    payload.variant());
        }
        Component message = result == Result.BLOCKED ? Component.translatable("propwash.race.edit.status.blocked").withStyle(ChatFormatting.RED)
                : result == Result.RESCAN_FAILED ? lastRescan
                : message(result, gate);
        if (message != null) {
            player.sendOverlayMessage(message);
        }
    }

    static void onSignal(RacePayloads.GateSignalC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        Entity entity = player.level().getEntity(payload.entityId());
        GateEntity gate = entity instanceof GateEntity found ? found : null;
        Result result = gate == null ? Result.MISSING : signal(player, gate, GateSignal.unpack(payload.signal()));
        Component message = message(result, gate);
        if (message != null) {
            player.sendOverlayMessage(message);
        }
    }

    public static Result signal(ServerPlayer player, GateEntity gate, GateSignal signal) {
        if (!gate.kind().signals()) {
            return Result.MISSING;
        }
        if (!holdsTool(player)) {
            return Result.NEED_TOOL;
        }
        Result denied = access(player, gate, EDIT_REACH);
        if (denied != null) {
            return denied;
        }
        if (!signal.equals(gate.signal())) {
            gate.setSignal(signal);
            gate.gameEvent(GameEvent.ENTITY_INTERACT, player);
        }
        return Result.SIGNAL_SET;
    }

    public static MutableComponent describeSignal(GateSignal signal) {
        if (!signal.enabled()) {
            return Component.translatable("propwash.race.signal.off");
        }
        MutableComponent pulse = Component.translatable("propwash.race.signal.on", signal.pulseTicks(), GateSignal.secondsText(signal.pulseTicks()));
        return signal.raceOnly() ? Component.translatable("propwash.race.signal.race_only", pulse) : pulse;
    }

    public static boolean holdsTool(Player player) {
        return player.getMainHandItem().getItem() instanceof GateItem || player.getOffhandItem().getItem() instanceof GateItem
                || CourseToolItem.isCourseTool(player.getMainHandItem()) || CourseToolItem.isCourseTool(player.getOffhandItem());
    }

    private static @Nullable Result access(ServerPlayer player, GateEntity gate, double reach) {
        if (gate.isRemoved() || gate.level() != player.level()) {
            return Result.MISSING;
        }
        if (!player.isWithinEntityInteractionRange(gate, reach)) {
            return Result.TOO_FAR;
        }
        if (!player.mayBuild()) {
            return Result.NOT_ALLOWED;
        }
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(registry.courseOf(gate.getUUID()));
        if (course != null && !CourseRegistry.canEdit(player, course)) {
            return Result.NOT_OWNER;
        }
        return null;
    }

    public static Result rotate(ServerPlayer player, GateEntity gate, boolean changeMount) {
        return rotate(player, gate, changeMount, GatePlacement.QUARTER);
    }

    public static Result rotate(ServerPlayer player, GateEntity gate, boolean changeMount, float step) {
        Result denied = access(player, gate, EXTRA_REACH);
        if (denied != null) {
            return denied;
        }
        boolean stacked = gate.inStack();
        GateGeometry next = GatePlacement.rotated(gate.geometry(), changeMount, stacked, step);
        if (next == null) {
            return Result.STACK_LOCKED;
        }
        GateStacking.Base base = stacked ? gate.base() : GateStacking.placedBase(next);
        return apply(player, gate, next, base, Result.ROTATED);
    }

    public static Result edit(ServerPlayer player, GateEntity gate, float heading, float pitch, float roll, double openingWidth,
                              double openingHeight) {
        return edit(player, gate, heading, pitch, roll, openingWidth, openingHeight, gate.variant());
    }

    public static Result edit(ServerPlayer player, GateEntity gate, float heading, float pitch, float roll, double openingWidth,
                              double openingHeight, int variant) {
        Result denied = access(player, gate, EDIT_REACH);
        if (denied != null) {
            return denied;
        }
        GateGeometry current = gate.geometry();
        GateGeometry next = preview(gate, heading, pitch, roll, openingWidth, openingHeight, variant);
        if (next.sameAs(current)) {
            return Result.EDITED;
        }
        if (gate.inStack() && !GatePoleLinks.keepsRiders(current, next)) {
            return Result.STACK_LOCKED;
        }
        return apply(player, gate, next, GateStacking.placedBase(next), Result.EDITED);
    }

    public static GateGeometry preview(GateEntity gate, float heading, float pitch, float roll, double openingWidth, double openingHeight) {
        return preview(gate, heading, pitch, roll, openingWidth, openingHeight, gate.variant());
    }

    public static GateGeometry preview(GateEntity gate, float heading, float pitch, float roll, double openingWidth, double openingHeight,
                                       int variant) {
        CourseElement kind = gate.kind();
        if (kind == CourseElement.FREEFORM) {
            return gate.geometry().withVariant(variant);
        }
        if (!kind.gate()) {
            return GatePlacement.edited(gate.geometry().withVariant(variant), heading, 0.0F, 0.0F, kind.outerWidth(openingWidth),
                    kind.outerHeight(openingHeight));
        }
        GateSize size = gate.size();
        return GatePlacement.edited(gate.geometry(), heading, pitch, roll, size.outer(openingWidth), size.outer(openingHeight));
    }

    private static Result apply(ServerPlayer player, GateEntity gate, GateGeometry next, GateStacking.Base base, Result done) {
        ServerLevel level = player.level();
        if (!GateItem.insideWorld(level, next) || !GateItem.free(level, next, base, true, gate)) {
            return Result.BLOCKED;
        }
        gate.applyGeometry(next);
        gate.refreshColumn();
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null && registry.courseOf(gate.getUUID()) != GateEntity.NO_COURSE) {
            registry.gateRotated(gate);
        }
        level.playSound(null, next.centerX(), next.centerY(), next.centerZ(), SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0F, 0.8F);
        gate.gameEvent(GameEvent.ENTITY_INTERACT, player);
        return done;
    }

    public static Result rescan(ServerPlayer player, GateEntity gate) {
        Result denied = access(player, gate, EDIT_REACH);
        if (denied != null) {
            return denied;
        }
        GateGeometry current = gate.geometry();
        if (!current.freeform()) {
            return Result.MISSING;
        }
        ServerLevel level = player.level();
        FreeformFill.Result result = FreeformBlocks.rescan(level, current);
        GateEntity occupant = null;
        if (result.ok()) {
            int[] seed = FreeformBlocks.seed(current);
            occupant = FreeformBlocks.occupant(level, seed[0], seed[1], seed[2], gate);
        }
        if (!result.ok() || occupant != null) {
            lastRescan = (occupant != null ? Component.translatable("propwash.race.freeform.taken") : FreeformGateItem.problem(result))
                    .withStyle(ChatFormatting.RED);
            return Result.RESCAN_FAILED;
        }
        GateGeometry next = GateGeometry.freeform(result.anchorX(), result.anchorY(), result.anchorZ(), result.region(), current.variant());
        if (!next.sameAs(current)) {
            gate.applyGeometry(next);
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null && registry.courseOf(gate.getUUID()) != GateEntity.NO_COURSE) {
                registry.gateRotated(gate);
            }
        }
        level.playSound(null, next.centerX(), next.centerY(), next.centerZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.4F);
        gate.gameEvent(GameEvent.ENTITY_INTERACT, player);
        return Result.RESCANNED;
    }

    public static Result unlink(ServerPlayer player, GateEntity gate) {
        Result denied = access(player, gate, EDIT_REACH);
        if (denied != null) {
            return denied;
        }
        if (!CourseToolItem.isCourseTool(player.getMainHandItem()) && !CourseToolItem.isCourseTool(player.getOffhandItem())) {
            return Result.NEED_TOOL;
        }
        CourseRegistry registry = CourseRegistry.get();
        int current = registry == null ? GateEntity.NO_COURSE : registry.courseOf(gate.getUUID());
        CourseRegistry.Course course = registry == null ? null : registry.course(current);
        if (course == null || !registry.removeGate(gate.getUUID())) {
            return Result.NOT_IN_COURSE;
        }
        registry.claim(course, player);
        gate.setCourse(GateEntity.NO_COURSE, GateEntity.NO_INDEX);
        gate.level().playSound(null, gate.getX(), gate.getY() + 1.0, gate.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 0.5F);
        lastUnlinked = current;
        return Result.UNLINKED;
    }

    public static @Nullable Component message(Result result, @Nullable GateEntity gate) {
        return switch (result) {
            case ROTATED -> gate == null ? null : Component.translatable("propwash.race.mount.rotated", describe(gate.geometry()));
            case EDITED -> gate == null ? null : Component.translatable("propwash.race.edit.done", describe(gate.geometry()));
            case UNLINKED -> Component.translatable("propwash.race.tool.removed", lastUnlinked).withStyle(ChatFormatting.YELLOW);
            case RESCANNED -> gate == null ? null : Component.translatable("propwash.race.freeform.rescanned", describe(gate.geometry()))
                    .withStyle(ChatFormatting.GREEN);
            case RESCAN_FAILED -> lastRescan;
            case SIGNAL_SET -> gate == null ? null : Component.translatable("propwash.race.signal.saved", describeSignal(gate.signal()))
                    .withStyle(ChatFormatting.GREEN);
            case NEED_TOOL -> Component.translatable("propwash.race.mount.need_tool").withStyle(ChatFormatting.RED);
            case NOT_IN_COURSE -> Component.translatable("propwash.race.tool.not_in_course");
            case MISSING -> null;
            case TOO_FAR -> Component.translatable("propwash.race.mount.too_far").withStyle(ChatFormatting.RED);
            case NOT_ALLOWED -> Component.translatable("propwash.race.mount.not_allowed").withStyle(ChatFormatting.RED);
            case NOT_OWNER -> {
                CourseRegistry registry = CourseRegistry.get();
                CourseRegistry.Course course = registry == null || gate == null ? null : registry.course(registry.courseOf(gate.getUUID()));
                yield course == null ? null : Component.translatable("propwash.race.tool.not_owner", course.id, CourseRegistry.ownerName(course))
                        .withStyle(ChatFormatting.RED);
            }
            case STACK_LOCKED -> Component.translatable(gate != null && gate.carrying() ? "propwash.race.pole.locked"
                    : "propwash.race.mount.stack_locked").withStyle(ChatFormatting.RED);
            case BLOCKED -> Component.translatable("propwash.race.mount.blocked").withStyle(ChatFormatting.RED);
        };
    }

    public static MutableComponent mountName(GateMount mount) {
        return Component.translatable("propwash.race.mount." + mount.key());
    }

    public static MutableComponent surfaceName(GateMount.Surface surface) {
        return Component.translatable("propwash.race.mount.surface." + surface.key());
    }

    public static String directionKey(GateGeometry gate) {
        if (Math.abs(gate.normalY()) > 0.5) {
            return gate.normalY() < 0.0 ? "down" : "up";
        }
        if (gate.mount() == GateMount.WALL_WINDOW) {
            return gate.flipped() ? "out" : "in";
        }
        double nx = gate.normalX();
        double nz = gate.normalZ();
        if (Math.abs(nx) > Math.abs(nz)) {
            return nx > 0.0 ? "east" : "west";
        }
        return nz > 0.0 ? "south" : "north";
    }

    public static String turnKey(GateMount mount, int turn) {
        int step = Math.floorMod(turn, mount.turns());
        return switch (mount) {
            case STANDING, HANGING -> switch (step) {
                case 1 -> "right";
                case 2 -> "back";
                case 3 -> "left";
                default -> "ahead";
            };
            case WALL_SIDE -> step == 1 ? "left" : "right";
            case WALL_WINDOW -> step == 1 ? "out" : "in";
            case FLOOR_FLAT, CEILING_FLAT, WALL_DIVE -> step == 1 ? "up" : "down";
        };
    }

    public static MutableComponent elementName(CourseElement kind) {
        return Component.translatable("propwash.race.element." + kind.key());
    }

    public static MutableComponent variantName(CourseElement kind, int variant) {
        if (kind == CourseElement.GATE_POLE) {
            return Component.translatable("color.minecraft." + kind.variantKey(variant));
        }
        return Component.translatable("propwash.race.element." + kind.key() + "." + kind.variantKey(variant));
    }

    public static MutableComponent variantName(GateGeometry gate) {
        if (gate.freeform()) {
            return Component.translatable("propwash.race.mount.dir." + directionKey(gate));
        }
        return variantName(gate.kind(), gate.variant());
    }

    public static MutableComponent freeformSize(GateGeometry gate) {
        FreeformRegion region = gate.region();
        if (region == null) {
            return Component.empty();
        }
        return Component.translatable("propwash.race.freeform.size", region.width(), region.height(), region.cells());
    }

    public static MutableComponent describe(GateGeometry gate) {
        if (gate.freeform()) {
            return Component.translatable("propwash.race.element.pose", elementName(gate.kind()), freeformSize(gate), variantName(gate));
        }
        if (!gate.gate()) {
            return Component.translatable("propwash.race.element.pose", elementName(gate.kind()), variantName(gate.kind(), gate.variant()),
                    Component.translatable("propwash.race.element.heading", degrees(gate.heading())));
        }
        GateTilt tilt = gate.tilt();
        if (tilt.none()) {
            return Component.translatable("propwash.race.mount.pose", surfaceName(gate.mount().surface()), mountName(gate.mount()),
                    Component.translatable("propwash.race.mount.dir." + directionKey(gate)));
        }
        MutableComponent pose = Component.translatable("propwash.race.mount.pose", surfaceName(gate.mount().surface()), mountName(gate.mount()),
                Component.translatable("propwash.race.mount.yaw", degrees(gate.heading())));
        if (tilt.pitch() != 0.0F) {
            pose = Component.translatable("propwash.race.mount.pose_more", pose, Component.translatable("propwash.race.mount.pitch", degrees(tilt.pitch())));
        }
        if (tilt.roll() != 0.0F) {
            pose = Component.translatable("propwash.race.mount.pose_more", pose, Component.translatable("propwash.race.mount.roll", degrees(tilt.roll())));
        }
        return pose;
    }

    public static MutableComponent describe(GateMount mount, int turn) {
        return Component.translatable("propwash.race.mount.pose", surfaceName(mount.surface()), mountName(mount),
                Component.translatable("propwash.race.mount.dir." + turnKey(mount, turn)));
    }

    public static MutableComponent describe(GateMount mount, int turn, float fine) {
        if (fine == 0.0F) {
            return describe(mount, turn);
        }
        float relative = GateTilt.wrap(turn * GatePlacement.QUARTER + fine);
        return Component.translatable("propwash.race.mount.pose", surfaceName(mount.surface()), mountName(mount),
                Component.translatable("propwash.race.mount.dir.angle", signedDegrees(relative)));
    }

    public static String degrees(float value) {
        float rounded = Math.round(value * GateTilt.RESOLUTION) / GateTilt.RESOLUTION;
        if (rounded == Math.rint(rounded)) {
            return Integer.toString((int) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    public static String signedDegrees(float value) {
        String text = degrees(value);
        return value > 0.0F ? "+" + text : text;
    }
}
