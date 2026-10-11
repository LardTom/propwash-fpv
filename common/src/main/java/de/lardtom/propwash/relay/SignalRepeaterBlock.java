package de.lardtom.propwash.relay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.function.Function;

public class SignalRepeaterBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public static final double ANTENNA_HEIGHT = 6.0 / 16.0;
    private final Function<BlockState, VoxelShape> shapes;

    public SignalRepeaterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.FLOOR));
        Map<AttachFace, Map<Direction, VoxelShape>> rotated = Shapes.rotateAttachFace(Shapes.or(
                Block.box(3.0, 4.0, 12.5, 13.0, 12.0, 16.0),
                Block.box(5.0, 7.5, 8.0, 11.0, 8.5, 12.5)));
        this.shapes = this.getShapeForEachState(state -> rotated.get(state.getValue(FACE)).get(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new SignalRepeaterBlockEntity(worldPosition, blockState);
    }

    public static Direction outward(BlockState state) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> Direction.UP;
            case CEILING -> Direction.DOWN;
            case WALL -> state.getValue(FACING);
        };
    }

    public static Vec3 antenna(BlockPos pos, BlockState state) {
        Direction out = outward(state);
        double offset = ANTENNA_HEIGHT - 0.5;
        return new Vec3(pos.getX() + 0.5 + out.getStepX() * offset, pos.getY() + 0.5 + out.getStepY() * offset,
                pos.getZ() + 0.5 + out.getStepZ() * offset);
    }
}
