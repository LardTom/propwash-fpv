package de.lardtom.propwash.rx;

import de.lardtom.propwash.content.ContentIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

public class RxBlock extends Block implements EntityBlock {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final EnumProperty<RxLinkState> LINK = EnumProperty.create("link", RxLinkState.class);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<RxOutput> OUTPUT = EnumProperty.create("output", RxOutput.class);
    public static final double HOUSING_HEIGHT = 6.0;
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, HOUSING_HEIGHT, 14.0);
    private static volatile BiConsumer<BlockPos, Boolean> clientUse = (pos, unbind) -> {
    };

    public RxBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0).setValue(LINK, RxLinkState.NONE).setValue(FACING, Direction.NORTH)
                .setValue(OUTPUT, RxOutput.ALL));
    }

    public boolean unlimited() {
        return false;
    }

    public static boolean unlimited(BlockState state) {
        return state.getBlock() instanceof RxBlock rx && rx.unlimited();
    }

    public static BlockState facing(BlockState state, Direction facing) {
        return state.setValue(FACING, facing.getAxis().isHorizontal() ? facing : Direction.NORTH).setValue(OUTPUT, RxOutput.FRONT);
    }

    public static void clientUse(BiConsumer<BlockPos, Boolean> handler) {
        clientUse = handler;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER, LINK, FACING, OUTPUT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return facing(this.defaultBlockState(), context.getHorizontalDirection());
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(OUTPUT).signal(state.getValue(FACING), direction, state.getValue(POWER));
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(OUTPUT).direct(state.getValue(FACING), direction, state.getValue(POWER));
    }

    @Override
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return state.getValue(OUTPUT).connects(state.getValue(FACING), direction);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        boolean wasStrong = oldState.is(this) && oldState.getValue(OUTPUT) == RxOutput.FRONT;
        boolean strong = state.getValue(OUTPUT) == RxOutput.FRONT;
        boolean turned = wasStrong && (!strong || oldState.getValue(FACING) != state.getValue(FACING));
        if (turned) {
            updateFront(level, pos, oldState);
        }
        if (strong && (!wasStrong || turned || oldState.getValue(POWER) != state.getValue(POWER))) {
            updateFront(level, pos, state);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston && state.getValue(OUTPUT) == RxOutput.FRONT) {
            updateFront(level, pos, state);
        }
    }

    private void updateFront(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos front = pos.relative(facing);
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, facing, Direction.UP);
        level.neighborChanged(front, this, orientation);
        level.updateNeighborsAtExceptFromFacing(front, this, facing.getOpposite(), orientation);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new RxBlockEntity(worldPosition, blockState);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hitResult) {
        if (!ContentIds.isRemote(itemStack)) {
            return itemStack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            clientUse.accept(pos.immutable(), Boolean.FALSE);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            RxServer.useRemote(serverPlayer, serverLevel, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            RxServer.showStatus(serverPlayer, serverLevel, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
