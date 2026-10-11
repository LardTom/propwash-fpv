package de.lardtom.propwash.relay;

import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.HelpBlockItem;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class RelayModule {
    public static final PropwashRegistries.BlockWithItem<SignalRepeaterBlock> SIGNAL_REPEATER = PropwashRegistries.blockWithItem(
            ContentIds.SIGNAL_REPEATER.getPath(),
            SignalRepeaterBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.6F)
                    .sound(SoundType.METAL)
                    .noCollision()
                    .noOcclusion()
                    .pushReaction(PushReaction.POPPED),
            HelpBlockItem::new,
            p -> p);

    public static final RegHolder<BlockEntityType<SignalRepeaterBlockEntity>> SIGNAL_REPEATER_BE = PropwashRegistries.blockEntity(
            ContentIds.SIGNAL_REPEATER.getPath(), SignalRepeaterBlockEntity::new, SIGNAL_REPEATER.block());

    private RelayModule() {
    }

    public static void init() {
    }
}
