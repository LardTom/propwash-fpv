package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintPreset;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.core.FlightTune;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionsTest {
    private static final Identifier UNKNOWN = Identifier.fromNamespaceAndPath("somewhere", "else");

    private static List<DroneBuild> builds() {
        List<DroneBuild> builds = new ArrayList<>(DroneBuild.PRESETS);
        builds.add(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY).withAccessory(AccessoryType.LED_STRIP));
        builds.add(DroneBuild.LONG_RANGE.withAccessory(AccessoryType.ACTION_CAM_LIGHT));
        return builds;
    }

    @Test
    void everyBuiltinBuildSurvivesTheSpecRoundTrip() {
        for (DroneBuild build : builds()) {
            BuildSpec spec = Conversions.spec(build);
            assertEquals(Optional.of(build), Conversions.build(spec), spec.toString());
            assertTrue(Conversions.unknownParts(spec).isEmpty(), spec.toString());
            assertEquals("propwash", spec.frame().getNamespace());
        }
        BuildSpec camera = Conversions.spec(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY));
        assertEquals(Optional.of(AccessoryType.ACTION_CAM_HEAVY.id()), camera.accessory(AccessorySlot.TOP));
        assertEquals(Optional.empty(), camera.accessory(AccessorySlot.BOTTOM));
    }

    @Test
    void unknownIdsAreListedInBuildOrder() {
        BuildSpec spec = Conversions.spec(DroneBuild.FREESTYLE).withPart(PartKind.MOTOR, UNKNOWN).withAccessory(AccessorySlot.BOTTOM, UNKNOWN);
        assertEquals(List.of(UNKNOWN, UNKNOWN), Conversions.unknownParts(spec));
        assertEquals(Optional.empty(), Conversions.build(spec));
        BuildSpec wrongKind = Conversions.spec(DroneBuild.FREESTYLE).withPart(PartKind.MOTOR, DroneBuild.FREESTYLE.prop().id());
        assertEquals(List.of(DroneBuild.FREESTYLE.prop().id()), Conversions.unknownParts(wrongKind));
    }

    @Test
    void accessoriesInTheWrongSlotDoNotBuild() {
        BuildSpec spec = Conversions.spec(DroneBuild.FREESTYLE).withAccessory(AccessorySlot.TOP, AccessoryType.LED_STRIP.id());
        assertTrue(Conversions.unknownParts(spec).isEmpty());
        assertEquals(Optional.empty(), Conversions.build(spec));
    }

    @Test
    void paintRoundTripUsesTheStableSlotKeys() {
        for (PaintPreset preset : PaintPreset.values()) {
            DronePaint paint = preset.paint();
            PaintSpec spec = Conversions.paint(paint);
            assertEquals(paint, Conversions.paint(spec));
            assertEquals(paint.count(), spec.colors().size());
        }
        DronePaint single = DronePaint.DEFAULT.with(PaintSlot.PROP_REAR_LEFT, 0x123456);
        assertEquals(Map.of("prop_rl", 0x123456), Conversions.paint(single).colors());
        assertEquals(PaintSlot.values().length, PaintSpec.SLOTS.size());
        for (PaintSlot slot : PaintSlot.values()) {
            assertEquals(slot.key(), PaintSpec.SLOTS.get(slot.ordinal()));
        }
        assertTrue(Conversions.paint(PaintSpec.DEFAULT).isDefault());
    }

    @Test
    void tuneRoundTripKeepsEveryValue() {
        FlightTune tune = new FlightTune(Map.of("rates.roll.rcRate", 1.25F, "pid.roll.p", 48.0F, "vtx.channel", 5.0F));
        TuneSpec spec = Conversions.tune(tune);
        assertEquals(tune.values(), spec.values());
        assertEquals(tune, Conversions.tune(spec));
        assertEquals(FlightTune.EMPTY, Conversions.tune(TuneSpec.EMPTY));
    }
}
