package de.lardtom.propwash.race;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PropType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostLookTest {
    private static final long MS = 1_000_000L;

    private static GhostTrack track(String frame, GhostLook look) {
        int samples = 40;
        double[] positions = new double[samples * 3];
        float[] rotations = new float[samples * 4];
        for (int i = 0; i < samples; i++) {
            positions[i * 3] = 10.0 + i * 0.3;
            positions[i * 3 + 1] = 70.0;
            positions[i * 3 + 2] = -4.0 + i * 0.1;
            rotations[i * 4 + 3] = 1.0F;
        }
        return new GhostTrack(GhostTrack.INTERVAL_NANOS, positions, rotations, 1_950L * MS, frame, 77, look);
    }

    @Test
    void everyPresetSurvivesTheRoundTrip() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            GhostLook look = GhostLook.of(preset, DronePaint.DEFAULT);
            assertEquals(Optional.of(look), GhostLook.decode(look.encode()), preset.frame().key());
        }
    }

    @Test
    void customBuildWithAccessoriesAndPaintSurvivesTheRoundTrip() {
        DroneBuild whoop = DroneBuild.WHOOP.withBattery(BatteryType.B1S_300_HV).withProp(PropType.P31_4B)
                .withAccessory(AccessoryType.LED_STRIP);
        DroneBuild freestyle = DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY).withAccessory(AccessoryType.LED_STRIP);
        DronePaint paint = DronePaint.DEFAULT.with(PaintSlot.values()[0], 0xFF00AA).withAllProps(0x22CC44);
        for (DroneBuild shown : new DroneBuild[]{whoop, freestyle}) {
            GhostLook look = GhostLook.of(shown, paint);
            GhostLook decoded = GhostLook.decode(look.encode()).orElseThrow();
            assertEquals(shown, decoded.build());
            assertEquals(paint, decoded.paint());
        }
    }

    @Test
    void unknownPartsOrBrokenDataGiveNoLook() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(GhostLook.VERSION);
            out.writeUTF("hexacopter_9");
            for (int i = 0; i < 5; i++) {
                out.writeUTF("x");
            }
            out.writeByte(0);
            out.writeUTF("");
        }
        assertTrue(GhostLook.decode(bytes.toByteArray()).isEmpty());
        byte[] valid = GhostLook.of(DroneBuild.RACER, DronePaint.DEFAULT).encode();
        assertTrue(GhostLook.decode(Arrays.copyOf(valid, valid.length - 3)).isEmpty());
        assertTrue(GhostLook.decode(new byte[0]).isEmpty());
        byte[] wrongVersion = valid.clone();
        wrongVersion[0] = 99;
        assertTrue(GhostLook.decode(wrongVersion).isEmpty());
    }

    @Test
    void ghostCodecStoresTheLook() throws IOException {
        GhostLook look = GhostLook.of(DroneBuild.WHOOP.withAccessory(AccessoryType.LED_STRIP), DronePaint.DEFAULT.withAllProps(0xFF8800));
        GhostTrack decoded = GhostCodec.decode(GhostCodec.encode(track("whoop_65", look)));
        assertEquals(look, decoded.look());
        assertEquals("whoop_65", decoded.frame());
        assertEquals(77, decoded.layout());
        assertEquals(1_950L * MS, decoded.lapNanos());
        GhostTrack withoutLook = GhostCodec.decode(GhostCodec.encode(track("race_5", null)));
        assertNull(withoutLook.look());
        assertEquals("race_5", withoutLook.frame());
    }

    @Test
    void oldRecordingsWithoutLookStillLoad() throws IOException {
        GhostTrack original = track("cinewhoop_3", GhostLook.of(DroneBuild.CINEWHOOP, DronePaint.DEFAULT));
        byte[] old = GhostCodec.encode(original, GhostCodec.VERSION_WITHOUT_LOOK);
        GhostTrack decoded = GhostCodec.decode(old);
        assertNull(decoded.look());
        assertEquals(original.samples(), decoded.samples());
        assertEquals("cinewhoop_3", decoded.frame());
        assertEquals(original.lapNanos(), decoded.lapNanos());
    }

    @Test
    void lapCopiesKeepTheLook() {
        GhostLook look = GhostLook.of(DroneBuild.LONG_RANGE, DronePaint.DEFAULT);
        GhostTrack track = track("long_range_7", look);
        assertSame(look, track.withLap(2_000L * MS, "long_range_7", 78).look());
        assertNull(track.withLook(null).look());
    }

    @Test
    void presetFallbackFollowsTheClass() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            assertEquals(preset, GhostLook.preset(preset.frame().key()).build());
            assertTrue(GhostLook.hasPreset(preset.frame().key()));
        }
        assertEquals(DroneBuild.FREESTYLE, GhostLook.preset("").build());
        assertFalse(GhostLook.hasPreset("unknown"));
        assertSame(GhostLook.preset("whoop_65"), GhostLook.preset("whoop_65"));
        assertTrue(GhostLook.of(DroneBuild.RACER, null).matches(DroneBuild.RACER, DronePaint.DEFAULT));
    }
}
