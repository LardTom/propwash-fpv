package de.lardtom.propwash.content;

import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

public final class ContentBlocks {
    public static final PropwashRegistries.BlockWithItem<DroneWorkbenchBlock> DRONE_WORKBENCH = PropwashRegistries.blockWithItem(
            ContentIds.DRONE_WORKBENCH.getPath(),
            DroneWorkbenchBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava(),
            HelpBlockItem::new,
            p -> p);

    public static final PropwashRegistries.BlockWithItem<BatteryChargerBlock> BATTERY_CHARGER = PropwashRegistries.blockWithItem(
            ContentIds.BATTERY_CHARGER.getPath(),
            BatteryChargerBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F)
                    .sound(SoundType.METAL)
                    .lightLevel(BatteryChargerBlock::lightLevel)
                    .noOcclusion(),
            HelpBlockItem::new,
            p -> p);

    public static final RegHolder<BlockEntityType<BatteryChargerBlockEntity>> BATTERY_CHARGER_BE = PropwashRegistries.blockEntity(
            ContentIds.BATTERY_CHARGER.getPath(), BatteryChargerBlockEntity::new, BATTERY_CHARGER.block());

    public static final RegHolder<MenuType<DroneWorkbenchMenu>> DRONE_WORKBENCH_MENU = PropwashRegistries.menu(
            ContentIds.DRONE_WORKBENCH.getPath(), DroneWorkbenchMenu::new);

    public static final RegHolder<MenuType<BatteryChargerMenu>> BATTERY_CHARGER_MENU = PropwashRegistries.menu(
            ContentIds.BATTERY_CHARGER.getPath(), BatteryChargerMenu::new);

    private ContentBlocks() {
    }

    static void bootstrap() {
    }
}
