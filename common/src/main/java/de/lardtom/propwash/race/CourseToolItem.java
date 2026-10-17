package de.lardtom.propwash.race;

import de.lardtom.propwash.content.ItemHelp;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

import java.util.function.Consumer;

public class CourseToolItem extends Item {
    public CourseToolItem(Item.Properties properties) {
        super(properties);
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
                                course.gates.size(), CourseRegistry.lapsLabel(course.laps), CourseRegistry.ownerName(course)));
                        RaceServer.sendLeaderboard(serverPlayer, course.id, true);
                    }
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static void denied(ServerPlayer player, CourseRegistry.Course course) {
        player.sendOverlayMessage(Component.translatable("propwash.race.tool.not_owner", course.id, CourseRegistry.ownerName(course))
                .withStyle(ChatFormatting.RED));
    }

    public static void useOnGate(ItemStack stack, ServerPlayer player, GateEntity gate) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return;
        }
        if (!gate.kind().courseable()) {
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
            player.sendOverlayMessage(Component.translatable("propwash.race.tool.already", gate.gateIndex() + 1, course.id));
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
        int index = registry.appendGate(id, gate);
        if (index < 0) {
            player.sendOverlayMessage(Component.translatable("propwash.race.tool.full", CourseData.MAX_GATES).withStyle(ChatFormatting.RED));
            return;
        }
        registry.claim(course, player);
        if (currentCourse != null) {
            registry.claim(currentCourse, player);
        }
        if (gate.element()) {
            String key = slalom ? "propwash.race.tool.assigned_slalom" : "propwash.race.tool.assigned_element";
            player.sendOverlayMessage(Component.translatable(key, GateMounting.elementName(gate.kind()), index + 1, id,
                    GateMounting.variantName(gate.geometry())).withStyle(ChatFormatting.GREEN));
        } else {
            player.sendOverlayMessage(Component.translatable("propwash.race.tool.assigned", index + 1, id).withStyle(ChatFormatting.GREEN));
        }
        float pitch = 0.6F + Math.min(1.4F, index * 0.08F);
        gate.level().playSound(null, gate.getX(), gate.getY() + 1.0, gate.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, pitch);
    }

    private static boolean previousIsPole(CourseRegistry.Course course) {
        return !course.gates.isEmpty() && course.gates.getLast().kind() == CourseElement.POLE;
    }

    static int slalomSide(CourseRegistry.Course course, int own) {
        if (!previousIsPole(course)) {
            return own;
        }
        return CourseElement.slalomSide(course.gates.getLast().variant(), own);
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
                .useLine(ItemHelp.line("propwash.race.tool.tooltip.sneak_air"))
                .useLine(ItemHelp.line("propwash.race.tool.tooltip.sneak_air_2"))
                .useLine(ItemHelp.line(ItemHelp.PREFIX + "course_tool.use.4"))
                .appendTo(builder);
    }
}
