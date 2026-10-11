package de.lardtom.propwash.content;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class BatteryChargerBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<ChargerState> STATE = EnumProperty.create("state", ChargerState.class);
    public static final EnumProperty<BayState> BAY_0 = EnumProperty.create("bay_0", BayState.class);
    public static final EnumProperty<BayState> BAY_1 = EnumProperty.create("bay_1", BayState.class);
    public static final EnumProperty<BayState> BAY_2 = EnumProperty.create("bay_2", BayState.class);
    public static final EnumProperty<BayState> BAY_3 = EnumProperty.create("bay_3", BayState.class);
    private static final List<EnumProperty<BayState>> BAYS = List.of(BAY_0, BAY_1, BAY_2, BAY_3);
    private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(1.0, 0.0, 2.0, 15.0, 8.0, 14.0);
    private static final VoxelShape SHAPE_EAST_WEST = Block.box(2.0, 0.0, 1.0, 14.0, 8.0, 15.0);

    public BatteryChargerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState initial = this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATE, ChargerState.IDLE);
        for (EnumProperty<BayState> bay : BAYS) {
            initial = initial.setValue(bay, BayState.EMPTY);
        }
        this.registerDefaultState(initial);
    }

    public static EnumProperty<BayState> bay(int slot) {
        return BAYS.get(slot);
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(STATE).light();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new BatteryChargerBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ContentBlocks.BATTERY_CHARGER_BE.get(),
                (var1, pos, state, entity) -> BatteryChargerBlockEntity.serverTick(serverLevel, pos, state, entity))
                : null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATE, BAY_0, BAY_1, BAY_2, BAY_3);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return facing == Direction.EAST || facing == Direction.WEST ? SHAPE_EAST_WEST : SHAPE_NORTH_SOUTH;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BatteryChargerBlockEntity charger) {
            player.openMenu(charger);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hitResult) {
        if (!Batteries.isBattery(itemStack) || player.isSecondaryUseActive()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (PartCondition.broken(itemStack)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("propwash.content.charger.dead_pack").withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        if (!(level.getBlockEntity(pos) instanceof BatteryChargerBlockEntity charger)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        int slot = charger.firstFreeSlot();
        if (slot < 0) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            charger.setItem(slot, itemStack.split(1));
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof BatteryChargerBlockEntity charger) {
            return (int) Math.ceil(15.0 * charger.fullCount() / BatteryChargerBlockEntity.SLOTS);
        }
        return 0;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
