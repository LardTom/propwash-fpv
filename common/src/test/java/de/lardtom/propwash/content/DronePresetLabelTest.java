package de.lardtom.propwash.content;

import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DronePresetLabelTest {
    private static final DroneBuild CUSTOM = DroneBuild.FREESTYLE.withBattery(BatteryType.B6S_1100);

    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }

    @Test
    void builtinPresetsKeepTheirName() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            assertEquals(DronePresets.translationKey(preset), key(DronePresets.label(preset, List.of()).orElseThrow()));
            assertEquals(DronePresets.translationKey(preset),
                    key(DronePresets.label(preset.withAccessory(AccessoryType.LED_STRIP), List.of()).orElseThrow()));
        }
        assertTrue(DronePresets.label(CUSTOM, List.of()).isEmpty());
    }

    @Test
    void addonPresetsAreLabelledWithTheirNameOrTranslation() {
        DronePreset named = new DronePreset(Identifier.fromNamespaceAndPath("addon", "named"),
                new DroneData(Conversions.spec(CUSTOM), PaintSpec.DEFAULT, TuneSpec.EMPTY, Optional.of("Budget 6S")), false);
        assertEquals("Budget 6S", DronePresets.label(CUSTOM, List.of(named)).orElseThrow().getString());
        DronePreset unnamed = DronePreset.of(Identifier.fromNamespaceAndPath("addon", "budget"), DroneData.of(Conversions.spec(CUSTOM)));
        assertEquals("addon.preset.budget", key(DronePresets.label(CUSTOM.withAccessory(AccessoryType.LED_STRIP), List.of(unnamed)).orElseThrow()));
        assertEquals(DronePresets.translationKey(DroneBuild.FREESTYLE), key(DronePresets.label(DroneBuild.FREESTYLE, List.of(unnamed)).orElseThrow()));
        assertTrue(DronePresets.label(DroneBuild.RACER.withBattery(BatteryType.B6S_1300), List.of(named, unnamed)).isEmpty());
    }
}
