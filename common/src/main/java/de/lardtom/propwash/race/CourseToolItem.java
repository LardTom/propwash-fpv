package de.lardtom.propwash.race;

import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.content.ItemHelp;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.util.function.Consumer;

public class CourseToolItem extends Item {
    public static final long REPEAT_GUARD_TICKS = 10L;
    private static final Map<UUID, LastClick> LAST = new HashMap<>();

    private record LastClick(UUID gate, long tick) {
    }

    public CourseToolItem(Item.Properties properties) {
        super(properties);
    }

    static void forget(UUID player) {
        LAST.remove(player);
    }

    static void clear() {
        LAST.clear();
    }

    private static boolean held(ServerPlayer player, GateEntity gate) {
        long now = gate.level().getGameTime();
        LastClick last = LAST.get(player.getUUID());
        boolean held = last != null && last.gate().equals(gate.getUUID()) && now - last.tick() >= 0L && now - last.tick() < REPEAT_GUARD_TICKS;
        if (held) {
            remember(player, gate);
        }
        return held;
    }

    private static void remember(ServerPlayer player, GateEntity gate) {
        LAST.put(player.getUUID(), new LastClick(gate.getUUID(), gate.level().getGameTime()));
    }

    public static int courseOf(ItemStack stack) {
        Integer course = stack.get(RaceModule.COURSE.get());
        return course == null ? GateEntity.NO_COURSE : Math.max(GateEntity.NO_COURSE, course);
    }

    public static boolean isCourseTool(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof CourseToolItem;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null) {
                if (player.isSecondaryUseActive()) {
                    boolean had = courseOf(stack) != GateEntity.NO_COURSE;
                    stack.remove(RaceModule.COURSE.get());
                    serverPlayer.sendOverlayMessage(Component.translatable("propwash.race.tool.cleared").withStyle(ChatFormatting.GREEN));
                    if (had) {
                        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 0.7F);
                    }
                } else {
                    int id = courseOf(stack);
                    CourseRegistry.Course course = id == GateEntity.NO_COURSE ? null : registry.course(id);
                    if (course == null) {
                        course = registry.nearestCourse(serverPlayer, RaceCommands.NEAREST_COURSE_RANGE);
                    }
                    if (course == null) {
                        serverPlayer.sendOverlayMessage(Component.translatable("propwash.race.tool.no_course"));
                    } else {
                        serverPlayer.sendOverlayMessage(Component.translatable("propwash.race.tool.info", CourseRegistry.label(course),
                                course.gates.size(), CourseRegistry.formatLabel(course), CourseRegistry.ownerName(course)));
                        RaceServer.sendLeaderboard(serverPlayer, course.id, true);
                    }
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static void denied(ServerPlayer player, CourseRegistry.Course course) {
        player.sendOverlayMessage(CourseRegistry.notOwner(course)
                .withStyle(ChatFormatting.RED));
    }

    public static void useOnGate(ItemStack stack, ServerPlayer player, GateEntity gate) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return;
        }
        if (gate.kind() == CourseElement.GATE_POLE && !gate.geometry().verticalPole()) {
            player.sendOverlayMessage(Component.translatable("propwash.race.score.pole.wall").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (!gate.kind().courseable() && gate.kind() != CourseElement.GATE_POLE) {
            player.sendOverlayMessage(Component.translatable("propwash.race.pole.not_in_course").withStyle(ChatFormatting.YELLOW));
            return;
        }
        int current = registry.courseOf(gate.getUUID());
        CourseRegistry.Course currentCourse = registry.course(current);
        if (currentCourse != null && !CourseRegistry.canEdit(player, currentCourse)) {
            denied(player, currentCourse);
            return;
        }
        String dimension = CourseRegistry.dimensionOf(gate.level());
        int id = courseOf(stack);
        CourseRegistry.Course course = id == GateEntity.NO_COURSE ? null : registry.course(id);
        if (course != null && !course.dimension.equals(dimension)) {
            course = null;
        }
        if (course != null && !CourseRegistry.canEdit(player, course)) {
            denied(player, course);
            return;
        }
        if (course != null && current == course.id) {
            if (!held(player, gate)) {
                repeat(player, gate, course);
            }
            return;
        }
        if (!gate.kind().startable() && (course == null || course.gates.isEmpty())) {
            player.sendOverlayMessage(Component.translatable("propwash.race.tool.start_gate", GateMounting.elementName(gate.kind()))
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (course == null) {
            id = registry.createCourse(dimension, player);
            course = registry.course(id);
            if (course == null) {
                player.sendOverlayMessage(Component.translatable("propwash.race.tool.too_many", CourseRegistry.MAX_COURSES).withStyle(ChatFormatting.RED));
                return;
            }
            stack.set(RaceModule.COURSE.get(), id);
            player.sendSystemMessage(Component.translatable("propwash.race.tool.new_course", id, CourseRegistry.lapsLabel(course.laps))
                    .withStyle(ChatFormatting.GREEN));
        }
        boolean slalom = false;
        if (gate.kind() == CourseElement.POLE && course.gates.size() < CourseData.MAX_GATES) {
            int side = slalomSide(course, gate.variant());
            slalom = side != gate.variant() || previousIsPole(course);
            if (side != gate.variant()) {
                gate.setVariant(side);
            }
        }
        if (gate.kind() == CourseElement.GATE_POLE && course.gates.size() < CourseData.MAX_GATES) {
            ScoreArea score = gate.score();
            if (!score.scoring()) {
                score = score.withRole(ScoreArea.Role.PASS);
                if (previousIsPole(course)) {
                    score = score.withSide(CourseElement.slalomSide(previousSide(course), score.side()));
                    slalom = true;
                }
                gate.setScore(score);
            } else if (score.role() == ScoreArea.Role.PASS && previousIsPole(course)) {
                int side = CourseElement.slalomSide(previousSide(course), score.side());
                slalom = true;
                if (side != score.side()) {
                    gate.setScore(score.withSide(side));
                }
            }
        }
        int index = registry.appendGate(id, gate);
        if (index < 0) {
            player.sendOverlayMessage(Component.translatable("propwash.race.tool.full", CourseData.MAX_GATES).withStyle(ChatFormatting.RED));
            return;
        }
        remember(player, gate);
        registry.claim(course, player);
        if (currentCourse != null) {
            registry.claim(currentCourse, player);
        }
        Component assigned;
        if (gate.kind() == CourseElement.GATE_POLE) {
            String key = slalom ? "propwash.race.score.pole.assigned_slalom" : "propwash.race.score.pole.assigned";
            assigned = Component.translatable(key, GateMounting.elementName(gate.kind()), index + 1, id, GateMounting.scoreName(gate.score()));
        } else if (gate.element()) {
            String key = slalom ? "propwash.race.tool.assigned_slalom" : "propwash.race.tool.assigned_element";
            assigned = Component.translatable(key, GateMounting.elementName(gate.kind()), index + 1, id, GateMounting.variantName(gate.geometry()));
        } else {
            assigned = Component.translatable("propwash.race.tool.assigned", index + 1, id);
        }
        player.sendOverlayMessage(finishNote(course, index, assigned).withStyle(ChatFormatting.GREEN));
        warnOverlap(player, course, index);
        float pitch = 0.6F + Math.min(1.4F, index * 0.08F);
        gate.level().playSound(null, gate.getX(), gate.getY() + 1.0, gate.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, pitch);
    }

    public static void warnOverlap(ServerPlayer player, CourseRegistry.Course course, int index) {
        List<GateGeometry> gates = course.toData().geometry();
        for (PassZoneOverlap.Finding finding : PassZoneOverlap.around(gates, index, course.pointToPoint())) {
            player.sendSystemMessage(overlapWarning(course, gates, finding));
        }
    }

    public static Component overlapWarning(CourseRegistry.Course course, List<GateGeometry> gates, PassZoneOverlap.Finding finding) {
        Component element = GateMounting.elementName(gates.get(finding.element()).kind());
        int number = finding.element() + 1;
        MutableComponent text = Component.translatable("propwash.race.overlap.warning", element, number, GateMounting.elementName(gates.get(finding.neighbour()).kind()),
                finding.neighbour() + 1).withStyle(ChatFormatting.YELLOW);
        if (!finding.shortenable()) {
            return text.append(" ").append(Component.translatable("propwash.race.overlap.fix.move"));
        }
        String value = GateMounting.degrees((float) finding.shortened());
        boolean turning = gates.get(finding.element()).scoringKind() == CourseElement.FLAG;
        String command = "/propwash race trim " + course.id + " " + number;
        return text.append(" ").append(Component.translatable(turning ? "propwash.race.overlap.fix.flag" : "propwash.race.overlap.fix.pole", value)).append(" ")
                .append(Component.translatable("propwash.race.overlap.button", value).withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("propwash.race.overlap.button.tooltip", element, number, value)))));
    }

    static MutableComponent finishNote(CourseRegistry.Course course, int index, Component assigned) {
        if (course.mode() == CourseMode.POINT_TO_POINT && index == PointToPoint.finish(course.gates.size())
                && index > 0) {
            return Component.translatable("propwash.race.p2p.tool.finish", assigned);
        }
        return assigned.copy();
    }

    public static boolean reverseFor(ServerPlayer player, GateEntity gate) {
        Vec3 look = player.getLookAngle();
        return CourseOrder.repeatSide(look.x, look.y, look.z, gate.geometry()) < 0;
    }

    private static void repeat(ServerPlayer player, GateEntity gate, CourseRegistry.Course course) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return;
        }
        boolean reverse = reverseFor(player, gate);
        int first = gate.gateIndex();
        int index = registry.appendPass(course.id, gate, reverse);
        if (index < 0) {
            player.sendOverlayMessage(Component.translatable("propwash.race.tool.full", CourseData.MAX_GATES).withStyle(ChatFormatting.RED));
            return;
        }
        remember(player, gate);
        registry.claim(course, player);
        Component direction = Component.translatable(reverse ? "propwash.race.loop.reverse" : "propwash.race.loop.forward");
        player.sendOverlayMessage(finishNote(course, index, Component.translatable("propwash.race.loop.added", GateMounting.elementName(gate.kind()),
                first + 1, index + 1, course.id, direction)).withStyle(ChatFormatting.GREEN));
        warnOverlap(player, course, index);
        float pitch = 0.6F + Math.min(1.4F, index * 0.08F);
        gate.level().playSound(null, gate.getX(), gate.getY() + 1.0, gate.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, pitch);
    }

    private static boolean previousIsPole(CourseRegistry.Course course) {
        return !course.gates.isEmpty() && course.gates.getLast().scoringKind() == CourseElement.POLE;
    }

    private static int previousSide(CourseRegistry.Course course) {
        CourseRegistry.GateRecord last = course.gates.getLast();
        return last.kind() == CourseElement.GATE_POLE ? last.scoreArea().side() : last.variant();
    }

    static int slalomSide(CourseRegistry.Course course, int own) {
        if (!previousIsPole(course)) {
            return own;
        }
        return CourseElement.slalomSide(previousSide(course), own);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return courseOf(stack) != GateEntity.NO_COURSE;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        int course = courseOf(stack);
        Component status = course == GateEntity.NO_COURSE
                ? Component.translatable("propwash.race.tool.tooltip.none").withStyle(ChatFormatting.GRAY)
                : Component.translatable("propwash.race.tool.tooltip.course", course).withStyle(ChatFormatting.AQUA);
        ItemHelp.builder("course_tool")
                .status(status)
                .clearUse()
                .useLine(ItemHelp.line("propwash.race.tool.tooltip.click"))
                .useLine(ItemHelp.line("propwash.race.tool.tooltip.air"))
                .useLine(ItemHelp.line("propwash.race.tool.tooltip.sneak_gate", Component.keybind(GateItem.EDIT_MOUSE_KEY)))
                .useLine(ItemHelp.line("propwash.race.tool.tooltip.rotate", Component.keybind(GateItem.ROTATE_KEY)))
                .useLine(ItemHelp.line("propwash.race.tool.tooltip.repeat"))
                .useLine(ItemHelp.line("propwash.race.tool.tooltip.repeat_2"))
                .useLine(ItemHelp.line("propwash.race.tool.tooltip.sneak_air"))
                .useLine(ItemHelp.line("propwash.race.tool.tooltip.sneak_air_2"))
                .useLine(ItemHelp.line(ItemHelp.PREFIX + "course_tool.use.4"))
                .appendTo(builder);
    }
}
