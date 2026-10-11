package de.lardtom.propwash.race;

import de.lardtom.propwash.content.ItemHelp;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public class FreeformGateItem extends GateItem {
    public FreeformGateItem(Item.Properties properties) {
        super(properties, GateSize.MEDIUM);
    }

    public record Plan(FreeformFill.Aim aim, FreeformFill.Result result, @Nullable GateEntity occupant) {
        public boolean ok() {
            return this.result.ok() && this.occupant == null;
        }

        public @Nullable GateGeometry geometry() {
            FreeformRegion region = this.result.region();
            if (region == null) {
                return null;
            }
            return GateGeometry.freeform(this.result.anchorX(), this.result.anchorY(), this.result.anchorZ(), region, this.aim.variant());
        }
    }

    public static boolean reversed(@Nullable Player player, boolean clientSide) {
        return GateMounting.preference(player, clientSide).variant(CourseElement.FREEFORM) == CourseElement.TOWARD;
    }

    public static Vec3 look(@Nullable Player player, Direction face) {
        if (player != null) {
            return player.getViewVector(1.0F);
        }
        return new Vec3(-face.getStepX(), -face.getStepY(), -face.getStepZ());
    }

    public static Plan plan(Level level, BlockPos clicked, Direction face, Vec3 look, boolean reverse) {
        FreeformFill.Aim aim = FreeformBlocks.aim(level, clicked, face, look, reverse);
        FreeformFill.Result result = FreeformBlocks.fill(level, aim);
        GateEntity occupant = result.ok() ? FreeformBlocks.occupant(level, aim.seedX(), aim.seedY(), aim.seedZ(), null) : null;
        return new Plan(aim, result, occupant);
    }

    public static MutableComponent problem(Plan plan) {
        if (plan.occupant() != null) {
            return Component.translatable("propwash.race.freeform.taken");
        }
        return problem(plan.result());
    }

    public static MutableComponent problem(FreeformFill.Result result) {
        int limit = result.limit();
        return switch (result.status()) {
            case OK -> Component.translatable("propwash.race.freeform.ok", result.width(), result.height());
            case START_BLOCKED -> Component.translatable("propwash.race.freeform.start_blocked");
            case OPEN -> Component.translatable("propwash.race.freeform.open", limit, limit);
            case TOO_BIG -> Component.translatable("propwash.race.freeform.too_big", result.width(), result.height(), limit, limit);
            case UNLOADED -> Component.translatable("propwash.race.freeform.unloaded");
        };
    }

    @Override
    public GateGeometry placement(GatePlacement.Click click, GatePlacement.Preference preference) {
        return GateGeometry.freeform(click.floorX(), click.floorY() + 0.5, click.floorZ(), FreeformRegion.single(FreeformRegion.AXIS_Z), 0);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player != null && !player.mayBuild()) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("propwash.race.mount.not_allowed").withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        Direction face = context.getClickedFace();
        Plan plan = plan(level, context.getClickedPos(), face, look(player, face), reversed(player, level.isClientSide()));
        if (!plan.ok()) {
            if (player != null && !level.isClientSide()) {
                player.sendOverlayMessage(problem(plan).withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (level instanceof ServerLevel serverLevel) {
            GateEntity gate = spawn(serverLevel, plan, stack, player);
            if (gate == null) {
                return InteractionResult.FAIL;
            }
            if (player != null) {
                player.sendOverlayMessage(Component.translatable("propwash.race.freeform.placed", GateMounting.describe(gate.geometry()))
                        .withStyle(ChatFormatting.GREEN));
            }
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    private static @Nullable GateEntity spawn(ServerLevel level, Plan plan, ItemStack stack, @Nullable Player player) {
        GateGeometry geometry = plan.geometry();
        FreeformRegion region = plan.result().region();
        if (geometry == null || region == null) {
            return null;
        }
        GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (gate == null) {
            return null;
        }
        gate.setSize(GateSize.MEDIUM);
        gate.setFreeform(region, plan.aim().variant());
        gate.setCourse(GateEntity.NO_COURSE, GateEntity.NO_INDEX);
        gate.snapTo(geometry.x(), geometry.y(), geometry.z(), geometry.yawDegrees(), 0.0F);
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            gate.setCustomName(stack.getHoverName());
        }
        level.addFreshEntity(gate);
        level.playSound(null, geometry.centerX(), geometry.centerY(), geometry.centerZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F,
                1.2F);
        gate.gameEvent(GameEvent.ENTITY_PLACE, player);
        return gate;
    }

    @Override
    public InteractionResult stackOn(GateEntity target, Player player, ItemStack stack) {
        if (!target.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable(target.kind() == CourseElement.FREEFORM ? "propwash.race.freeform.taken"
                    : "propwash.race.freeform.no_stack"));
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        String id = ItemHelp.PREFIX + "freeform_gate";
        ItemHelp.summary(id + ".summary")
                .what(id)
                .use(id)
                .args(id + ".use.2", Component.keybind(ROTATE_KEY))
                .appendTo(builder);
    }
}
