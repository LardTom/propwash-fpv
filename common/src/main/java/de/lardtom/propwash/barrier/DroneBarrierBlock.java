package de.lardtom.propwash.barrier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DroneBarrierBlock extends BarrierBlock {
    private final BarrierKind kind;

    public DroneBarrierBlock(BarrierKind kind, BlockBehaviour.Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public BarrierKind kind() {
        return this.kind;
    }

    public VoxelShape droneShape() {
        return this.kind.solidFor(Collider.DRONE) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.kind.solidFor(DroneBarriers.collider(context)) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DroneBarriers.outlines(context) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
