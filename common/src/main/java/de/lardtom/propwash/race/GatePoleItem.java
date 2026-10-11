package de.lardtom.propwash.race;

import de.lardtom.propwash.content.ItemHelp;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class GatePoleItem extends CourseElementItem {
    public GatePoleItem(Item.Properties properties) {
        super(properties, CourseElement.GATE_POLE);
    }

    @Override
    public boolean allowed(GatePlacement.Click click) {
        return true;
    }

    @Override
    public GateGeometry placement(GatePlacement.Click click, GatePlacement.Preference preference) {
        return GatePoles.place(click, preference, preference.variant(CourseElement.GATE_POLE), GatePoles.DEFAULT_LENGTH);
    }

    public static @Nullable GateGeometry snapped(GatePoleLinks.Target target, GatePlacement.Preference preference) {
        if (target.refusal() != GatePoles.Refusal.NONE) {
            return null;
        }
        return GatePoles.onSeat(target.seat(), preference.variant(CourseElement.GATE_POLE), GatePoles.DEFAULT_LENGTH);
    }

    public static boolean fits(Level level, GateGeometry geometry) {
        return GateItem.insideWorld(level, geometry) && GateItem.free(level, geometry, GateStacking.Base.STACKED, true);
    }

    @Override
    public InteractionResult stackOn(GateEntity target, Player player, ItemStack stack) {
        return this.stackOn(target, player, stack, Vec3.ZERO);
    }

    @Override
    public InteractionResult stackOn(GateEntity target, Player player, ItemStack stack, Vec3 location) {
        if (!player.mayBuild() || target.isRemoved()) {
            return InteractionResult.PASS;
        }
        Level level = target.level();
        boolean server = !level.isClientSide();
        if (player instanceof ServerPlayer serverPlayer) {
            CourseRegistry.Course course = foreignCourse(serverPlayer, target);
            if (course != null) {
                serverPlayer.sendOverlayMessage(Component.translatable("propwash.race.tool.not_owner", course.id, CourseRegistry.ownerName(course))
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
        }
        GatePoleLinks.Target found = GatePoleLinks.target(target, target.getX() + location.x, target.getZ() + location.z);
        if (found == null) {
            if (server) {
                player.sendOverlayMessage(Component.translatable("propwash.race.pole.no_seat"));
            }
            return InteractionResult.FAIL;
        }
        if (found.refusal() == GatePoles.Refusal.TOO_HIGH) {
            if (server) {
                player.sendOverlayMessage(Component.translatable("propwash.race.pole.too_high", GatePoles.MAX_CHAIN));
            }
            return InteractionResult.FAIL;
        }
        GateGeometry geometry = snapped(found, GateMounting.preference(player, !server));
        if (geometry == null || !fits(level, geometry)) {
            if (server) {
                player.sendOverlayMessage(Component.translatable("propwash.race.gate.blocked"));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            GateEntity placed = this.spawn(serverLevel, geometry, stack, player, 1.0F + 0.1F * found.chain());
            if (placed == null) {
                return InteractionResult.FAIL;
            }
            placed.refreshStack();
            target.refreshColumn();
            GateEntity support = GatePoleLinks.support(placed);
            if (support != null) {
                support.refreshStack();
            }
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        String id = ItemHelp.PREFIX + CourseElement.GATE_POLE.itemId();
        ItemHelp.summary(id + ".summary")
                .what(id)
                .use(id)
                .args(id + ".use.2", Component.keybind(ROTATE_KEY), Component.keybind(ROTATE_KEY))
                .specs(out -> out.accept(Component.translatable("propwash.race.pole.tooltip", GateMounting.degrees((float) GatePoles.DEFAULT_LENGTH),
                        GateMounting.degrees((float) GatePoles.MIN_LENGTH), GateMounting.degrees((float) GatePoles.MAX_LENGTH))
                        .withStyle(ChatFormatting.GRAY)))
                .appendTo(builder);
    }

    static CourseRegistry.@Nullable Course foreignCourse(ServerPlayer player, GateEntity target) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return null;
        }
        GateEntity base = target;
        for (int guard = 0; guard < GatePoles.MAX_CHAIN * 4 && base.kind() == CourseElement.GATE_POLE; guard++) {
            GateEntity below = GatePoleLinks.support(base);
            if (below == null) {
                break;
            }
            base = below;
        }
        List<GateEntity> carriers = base.stackable() ? base.stack() : List.of(base);
        for (GateEntity gate : carriers) {
            CourseRegistry.Course course = registry.course(registry.courseOf(gate.getUUID()));
            if (course != null && !CourseRegistry.canEdit(player, course)) {
                return course;
            }
        }
        return null;
    }
}
