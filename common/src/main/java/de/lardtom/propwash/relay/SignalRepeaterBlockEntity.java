package de.lardtom.propwash.relay;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SignalRepeaterBlockEntity extends BlockEntity {
    private boolean indexed;

    public SignalRepeaterBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RelayModule.SIGNAL_REPEATER_BE.get(), worldPosition, blockState);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        Level level = this.getLevel();
        if (level != null) {
            RepeaterIndex.of(level).put(this.getBlockPos(), this.getBlockState(), this);
            this.indexed = true;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Level level = this.getLevel();
        if (this.indexed && level != null) {
            BlockPos pos = this.getBlockPos();
            if (RepeaterIndex.placed(level, pos)) {
                RepeaterIndex.of(level).keep(pos, this);
            } else {
                RepeaterIndex.of(level).remove(pos, this);
            }
        }
        this.indexed = false;
    }

    @Override
    public void setBlockState(BlockState blockState) {
        super.setBlockState(blockState);
        Level level = this.getLevel();
        if (this.indexed && level != null && blockState.getBlock() instanceof SignalRepeaterBlock) {
            RepeaterIndex.of(level).put(this.getBlockPos(), blockState, this);
        }
    }
}
