package de.lardtom.propwash.rx;

import net.minecraft.world.level.block.state.BlockBehaviour;

public class CreativeRxBlock extends RxBlock {
    public CreativeRxBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean unlimited() {
        return true;
    }
}
