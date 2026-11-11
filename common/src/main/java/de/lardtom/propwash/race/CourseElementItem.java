package de.lardtom.propwash.race;

import de.lardtom.propwash.content.ItemHelp;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public class CourseElementItem extends GateItem {
    private final CourseElement kind;

    public CourseElementItem(Item.Properties properties, CourseElement kind) {
        super(properties, GateSize.MEDIUM);
        this.kind = kind;
    }

    public CourseElement kind() {
        return this.kind;
    }

    @Override
    public boolean allowed(GatePlacement.Click click) {
        return click.surface() == GateMount.Surface.FLOOR;
    }

    @Override
    public GateGeometry placement(GatePlacement.Click click, GatePlacement.Preference preference) {
        return GatePlacement.resolveElement(click, preference, this.kind);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!this.allowed(click(context))) {
            Player player = context.getPlayer();
            if (player != null && !context.getLevel().isClientSide()) {
                player.sendOverlayMessage(Component.translatable("propwash.race.element.floor_only"));
            }
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResult stackOn(GateEntity target, Player player, ItemStack stack) {
        if (!target.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable("propwash.race.stack.cube_no_stack"));
        }
        return InteractionResult.FAIL;
    }

    public boolean seatable() {
        return this.kind == CourseElement.FLAG || this.kind == CourseElement.POLE;
    }

    public static @Nullable GateGeometry seated(GatePoleLinks.Target target, GatePlacement.Preference preference, CourseElement kind, float playerYaw) {
        if (target.refusal() != GatePoles.Refusal.NONE) {
            return null;
        }
        int turn = preference.floor().turns() == 4 ? preference.floorTurn() : 0;
        float fine = preference.floor().turns() == 4 ? preference.floorFine() : 0.0F;
        return GatePoles.elementOnSeat(target.seat(), kind, preference.variant(kind), playerYaw + GatePlacement.QUARTER * turn, fine);
    }

    @Override
    public InteractionResult stackOn(GateEntity target, Player player, ItemStack stack, Vec3 location) {
        if (!this.seatable()) {
            return this.stackOn(target, player, stack);
        }
        if (!player.mayBuild() || target.isRemoved()) {
            return InteractionResult.PASS;
        }
        Level level = target.level();
        boolean server = !level.isClientSide();
        if (player instanceof ServerPlayer serverPlayer) {
            CourseRegistry.Course course = GatePoleItem.foreignCourse(serverPlayer, target);
            if (course != null) {
                serverPlayer.sendOverlayMessage(CourseRegistry.notOwner(course)
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
        }
        GatePoleLinks.Target found = GatePoleLinks.target(target, target.getX() + location.x, target.getZ() + location.z);
        if (found == null) {
            if (server) {
                player.sendOverlayMessage(Component.translatable("propwash.race.stack.no_seat", GateMounting.elementName(this.kind)));
            }
            return InteractionResult.FAIL;
        }
        if (found.refusal() == GatePoles.Refusal.TOO_HIGH) {
            if (server) {
                player.sendOverlayMessage(Component.translatable("propwash.race.pole.too_high", GatePoles.MAX_CHAIN));
            }
            return InteractionResult.FAIL;
        }
        GateGeometry geometry = seated(found, GateMounting.preference(player, !server), this.kind, player.getYRot());
        if (geometry == null || !GatePoleItem.fits(level, geometry)) {
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
            GateEntity support = GatePoleLinks.support(placed);
            if (support != null) {
                support.refreshStack();
            }
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable("propwash.race.stack.placed", GateMounting.elementName(this.kind),
                        support == null ? Component.empty() : GateMounting.elementName(support.kind())).withStyle(ChatFormatting.GREEN));
            }
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        String id = ItemHelp.PREFIX + this.kind.itemId();
        ItemHelp.summary(id + ".summary")
                .what(id)
                .use(ItemHelp.PREFIX + "course_element")
                .args(ItemHelp.PREFIX + "course_element.use.2", Component.keybind(ROTATE_KEY), Component.keybind(ROTATE_KEY))
                .args(ItemHelp.PREFIX + "course_element.use.5", Component.keybind(GateItem.EDIT_MOUSE_KEY))
                .specs(out -> out.accept(Component.translatable("propwash.race.element.tooltip." + this.kind.key(),
                        GateMounting.degrees((float) this.kind.defaultWidth()), GateMounting.degrees((float) this.kind.defaultHeight()))
                        .withStyle(ChatFormatting.GRAY)))
                .appendTo(builder);
    }
}
