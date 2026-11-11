package de.lardtom.propwash.build.antenna;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.api.part.AnchorSpec;
import de.lardtom.propwash.api.part.AntennaCap;
import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.DuctSpec;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.MassSplit;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.api.part.VideoAntenna;
import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.api.part.VideoLink;
import de.lardtom.propwash.build.AntennaRules;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.parts.JsonText;
import de.lardtom.propwash.parts.PartDataProblems;
import de.lardtom.propwash.parts.PartJson;
import de.lardtom.propwash.testutil.TestPaths;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntennaDataTest {
    static final String FRAME_JSON = """
            {"format": 1, "mass_g": 120.0, "wheelbase_mm": 226, "max_prop_mm": 130,
             "motor_mounts": ["propwash:m22"], "stack_mounts": ["propwash:s30"],
             "antenna_anchor": {"pos": [0.0, 31.0, 51.0], "tilt_deg": 40.0},
             "aero": {"frontal_area_m2": 0.012, "drag_coefficient": 0.9},
             "battery_fit": {"cells": [4, 6], "max_mass_g": 230.0}}
            """;
    static final String VIDEO_JSON = """
            {"format": 1, "mass_g": 24.0, "link": "digital", "latency_ms": 24, "range_blocks": 650, "image_quality": 1.0,
             "camera_width_mm": 20.0, "vtx_power_mw": 1000,
             "antenna": {"count": 2, "length_mm": 95.0, "cap": "pagoda"}}
            """;

    static Path frames() {
        return TestPaths.mainResources().resolve("data/propwash/propwash_parts/frame");
    }

    static PartDefinition decode(PartKind kind, String path, String text) {
        PartDataProblems problems = new PartDataProblems(path + ".json");
        JsonObject json = PartJson.parse(text, problems);
        assertNotNull(json, problems.errors().toString());
        PartDefinition definition = PartJson.decode(kind, Identifier.fromNamespaceAndPath("justmoreparts", path), json, problems);
        assertEquals(List.of(), problems.errors(), path);
        assertEquals(List.of(), problems.warnings(), path);
        assertNotNull(definition, path);
        return definition;
    }

    @Test
    void everyCoreFrameDeclaresItsAntennaHolderAtTheRear() throws IOException {
        List<Path> files;
        try (Stream<Path> list = Files.list(frames())) {
            files = list.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertEquals(5, files.size(), files.toString());
        for (Path file : files) {
            JsonObject frame = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertTrue(frame.has("antenna_anchor"), file.getFileName() + ": kein antenna_anchor");
            JsonObject anchor = frame.getAsJsonObject("antenna_anchor");
            JsonArray pos = anchor.getAsJsonArray("pos");
            assertEquals(3, pos.size(), file.getFileName().toString());
            assertEquals(0.0, pos.get(0).getAsDouble(), 0.0, file.getFileName() + ": Mitte");
            assertTrue(pos.get(2).getAsDouble() > 0.0, file.getFileName() + ": hinten");
            double tilt = anchor.get("tilt_deg").getAsDouble();
            assertTrue(tilt >= 35.0 && tilt <= 45.0, file.getFileName() + ": Neigung " + tilt);
        }
    }

    @Test
    void antennaHolderAndAntennaOverrideSurviveTheJsonRoundTrip() {
        PartDefinition frame = decode(PartKind.FRAME, "fs_test", FRAME_JSON);
        JsonObject frameJson = PartJson.encode(frame);
        assertTrue(frameJson.has("antenna_anchor"), JsonText.pretty(frameJson));
        JsonObject anchor = frameJson.getAsJsonObject("antenna_anchor");
        assertEquals(51.0, anchor.getAsJsonArray("pos").get(2).getAsDouble(), 1.0E-9);
        assertEquals(40.0, anchor.get("tilt_deg").getAsDouble(), 1.0E-9);
        assertEquals(frame, decode(PartKind.FRAME, "fs_test", JsonText.pretty(frameJson)));

        PartDefinition video = decode(PartKind.VIDEO, "hd_test", VIDEO_JSON);
        JsonObject videoJson = PartJson.encode(video);
        assertTrue(videoJson.has("antenna"), JsonText.pretty(videoJson));
        JsonObject antenna = videoJson.getAsJsonObject("antenna");
        assertEquals(2, antenna.get("count").getAsInt());
        assertEquals(95.0, antenna.get("length_mm").getAsDouble(), 1.0E-9);
        assertEquals("pagoda", antenna.get("cap").getAsString());
        assertEquals(video, decode(PartKind.VIDEO, "hd_test", JsonText.pretty(videoJson)));
    }

    static VideoDefinition vtx(String path, double grams, VideoLink link, int latency, int range, double quality, int powerMw) {
        return VideoDefinition.builder(Identifier.fromNamespaceAndPath("justmoreparts", path)).mass(grams).link(link).latency(latency).range(range)
                .imageQuality(quality).vtxPower(powerMw).build();
    }

    @Test
    void addonVtxGetAntennasFromTheirExistingData() {
        Map<VideoDefinition, VideoAntenna> expected = Map.of(
                vtx("analog_whoop_25mw", 2.2, VideoLink.ANALOG, 12, 200, 0.5, 25), new VideoAntenna(1, 30.0, AntennaCap.WHIP),
                vtx("digital_micro", 4.8, VideoLink.DIGITAL, 24, 220, 0.78, 200), new VideoAntenna(1, 35.0, AntennaCap.WHIP),
                vtx("digital_low_latency", 9.8, VideoLink.DIGITAL, 14, 380, 0.82, 500), new VideoAntenna(1, 50.0, AntennaCap.STUBBY),
                vtx("analog_race_800", 9.5, VideoLink.ANALOG, 11, 520, 0.68, 800), new VideoAntenna(1, 60.0, AntennaCap.LOLLIPOP),
                vtx("digital_hd_pro", 24.0, VideoLink.DIGITAL, 24, 650, 1.0, 1000), new VideoAntenna(2, 100.0, AntennaCap.LOLLIPOP),
                vtx("digital_lr", 19.0, VideoLink.DIGITAL, 32, 900, 0.9, 1200), new VideoAntenna(2, 150.0, AntennaCap.PAGODA),
                vtx("analog_lr_1600", 16.0, VideoLink.ANALOG, 12, 820, 0.62, 1600), new VideoAntenna(1, 138.0, AntennaCap.PAGODA));
        for (Map.Entry<VideoDefinition, VideoAntenna> entry : expected.entrySet()) {
            VideoAntenna got = AntennaRules.antenna(entry.getKey());
            assertEquals(entry.getValue().count(), got.count(), entry.getKey().id().toString());
            assertEquals(entry.getValue().cap(), got.cap(), entry.getKey().id().toString());
            assertEquals(entry.getValue().lengthMm(), got.lengthMm(), 0.5, entry.getKey().id().toString());
        }
        assertEquals(AntennaRules.Kind.LONG_RANGE, AntennaRules.kind(vtx("lr", 19.0, VideoLink.DIGITAL, 32, 700, 0.9, 1200)));
        assertEquals(AntennaRules.Kind.HD, AntennaRules.kind(vtx("hd", 23.0, VideoLink.DIGITAL, 30, 699, 0.95, 700)));
        assertEquals(AntennaRules.Kind.LITE, AntennaRules.kind(vtx("lite", 8.5, VideoLink.DIGITAL, 26, 300, 0.85, 400)));
        assertEquals(AntennaRules.Kind.WHOOP, AntennaRules.kind(vtx("tiny", 6.0, VideoLink.DIGITAL, 26, 300, 1.0, 0)));
        assertEquals(new VideoAntenna(1, 30.0, AntennaCap.WHIP), AntennaRules.antenna(vtx("tiny", 6.0, VideoLink.DIGITAL, 26, 300, 1.0, 0)));
        for (VideoType video : VideoType.values()) {
            VideoAntenna antenna = AntennaRules.antenna(video.definition());
            assertTrue(antenna.lengthMm() >= 30.0 && antenna.lengthMm() <= 150.0, video.key());
        }
    }

    @Test
    void anAddonMayOverrideTheAntennaAndOldConstructorsStayValid() {
        VideoAntenna patch = new VideoAntenna(1, 70.0, AntennaCap.PATCH);
        VideoDefinition base = vtx("custom", 12.0, VideoLink.DIGITAL, 22, 400, 0.9, 500);
        VideoDefinition custom = VideoDefinition.builder(base.id()).mass(12.0).link(VideoLink.DIGITAL).latency(22).range(400).imageQuality(0.9)
                .vtxPower(500).antenna(patch).build();
        assertEquals(Optional.empty(), base.antenna());
        assertEquals(patch, AntennaRules.antenna(custom));
        assertEquals(AntennaRules.derived(custom), AntennaRules.antenna(base));
        VideoDefinition legacy = new VideoDefinition(base.id(), base.basics(), VideoLink.ANALOG, 12, 300, 0.6, 19.0, OptionalDouble.empty(), 400,
                OptionalDouble.empty(), List.of());
        assertEquals(Optional.empty(), legacy.antenna());
        assertThrows(IllegalArgumentException.class, () -> new VideoAntenna(3, 50.0, AntennaCap.WHIP));
        assertThrows(IllegalArgumentException.class, () -> new VideoAntenna(1, 5.0, AntennaCap.WHIP));
        assertThrows(IllegalArgumentException.class, () -> new VideoAntenna(1, 300.0, AntennaCap.WHIP));
        assertThrows(NullPointerException.class, () -> new VideoAntenna(1, 50.0, null));
        for (AntennaCap cap : AntennaCap.values()) {
            assertEquals(Optional.of(cap), AntennaCap.byKey(cap.key().toUpperCase(Locale.ROOT)));
        }
        assertEquals(Optional.empty(), AntennaCap.byKey("dipole"));
        PartDataProblems problems = new PartDataProblems("broken.json");
        JsonObject json = PartJson.parse(VIDEO_JSON.replace("\"pagoda\"", "\"dipole\""), problems);
        assertNotNull(json);
        assertEquals(null, PartJson.decode(PartKind.VIDEO, Identifier.fromNamespaceAndPath("justmoreparts", "broken"), json, problems));
        assertTrue(problems.hasErrors(), problems.errors().toString());
    }

    @Test
    void antennasKeepTheLengthOfTheirClassAndStayInProportionOnSmallFrames() {
        List<VideoDefinition> vtx = List.of(vtx("analog_whoop_25mw", 2.2, VideoLink.ANALOG, 12, 200, 0.5, 25),
                vtx("digital_micro", 4.8, VideoLink.DIGITAL, 24, 220, 0.78, 200), vtx("analog_nano", 4.0, VideoLink.ANALOG, 12, 250, 0.55, 200),
                vtx("analog_race", 8.0, VideoLink.ANALOG, 12, 380, 0.65, 400), vtx("digital_low_latency", 9.8, VideoLink.DIGITAL, 14, 380, 0.82, 500),
                vtx("analog_race_800", 9.5, VideoLink.ANALOG, 11, 520, 0.68, 800), vtx("digital_lite", 8.5, VideoLink.DIGITAL, 26, 300, 0.85, 400),
                vtx("digital_hd", 23.0, VideoLink.DIGITAL, 30, 520, 1.0, 700), vtx("digital_hd_pro", 24.0, VideoLink.DIGITAL, 24, 650, 1.0, 1000),
                vtx("digital_lr", 19.0, VideoLink.DIGITAL, 32, 900, 0.9, 1200), vtx("analog_lr_1600", 16.0, VideoLink.ANALOG, 12, 820, 0.62, 1600));
        FrameDefinition five = frame("fs_5_bash", 226, true, 32.0, 12.0, 20.6, false);
        for (VideoDefinition video : vtx) {
            VideoAntenna antenna = AntennaRules.antenna(video);
            String name = video.id().getPath();
            switch (AntennaRules.kind(video)) {
                case WHOOP -> assertTrue(antenna.count() == 1 && antenna.cap() == AntennaCap.WHIP && antenna.lengthMm() >= 30.0
                        && antenna.lengthMm() <= 35.0, name + ": Peitsche 30–35 mm statt " + antenna);
                case RACE -> assertTrue(antenna.count() == 1 && (antenna.cap() == AntennaCap.STUBBY || antenna.cap() == AntennaCap.LOLLIPOP)
                        && antenna.lengthMm() >= 45.0 && antenna.lengthMm() <= 60.0, name + ": 45–60 mm mit Kappe statt " + antenna);
                case LITE -> assertTrue(antenna.count() == 1 && antenna.cap() == AntennaCap.LOLLIPOP && antenna.lengthMm() >= 45.0
                        && antenna.lengthMm() <= 60.0, name + ": 45–60 mm Lollipop statt " + antenna);
                case HD -> assertTrue(antenna.count() == 2 && antenna.lengthMm() >= 85.0 && antenna.lengthMm() <= 100.0,
                        name + ": zwei um 90 mm statt " + antenna);
                case LONG_RANGE -> assertTrue(antenna.cap() == AntennaCap.PAGODA && antenna.lengthMm() >= 120.0 && antenna.lengthMm() <= 150.0,
                        name + ": 120–150 mm statt " + antenna);
            }
            assertEquals(antenna, AntennaRules.antenna(video, five), name + ": am 5″-Rahmen ungekürzt");
        }
        Object[][] small = {{"whoop_65_lite", 65, false, -3.0, true, 78.0}, {"whoop_75_duct", 75, false, -3.5, true, 90.0},
                {"tp_2_x", 90, true, 7.0, false, 108.0}, {"cw_25_duct", 112, true, 16.0, true, 134.5}, {"tp_3_x", 125, true, 12.0, false, 150.0}};
        for (Object[] row : small) {
            FrameDefinition frame = frame((String) row[0], (int) row[1], (boolean) row[2], (double) row[3], 5.0, 15.0, (boolean) row[4]);
            assertEquals((double) row[5], AntennaRules.maxLengthMm(frame), 1.0E-9, (String) row[0]);
            for (VideoDefinition video : vtx) {
                VideoAntenna full = AntennaRules.antenna(video);
                VideoAntenna fitted = AntennaRules.antenna(video, frame);
                assertEquals(Math.min(full.lengthMm(), (double) row[5]), fitted.lengthMm(), 1.0E-9, row[0] + " + " + video.id().getPath());
                assertEquals(full.count(), fitted.count());
                assertEquals(full.cap(), fitted.cap());
            }
        }
        assertEquals(AntennaRules.PAIR_LEAN_DEG, AntennaRules.pairLeanDeg(five), 0.0, "5″: V mit 30°");
        assertEquals(AntennaRules.PAIR_LEAN_DEG, AntennaRules.pairLeanDeg(frame("cw_25_duct", 112, true, 16.0, 8.0, 29.5, true)), 0.0,
                "Cinewhoop im Duct: V mit 30°");
        assertEquals(AntennaRules.PAIR_LEAN_DEG, AntennaRules.pairLeanDeg(frame("whoop", 65, false, -3.0, 4.0, 18.0, true)), 0.0, "Whoop im Duct");
        assertEquals(5.0, AntennaRules.pairLeanDeg(frame("tp_25_x", 110, true, 9.0, 5.5, 14.1, false)), 1.0E-9, "Toothpick ohne Duct: schmales V");
        assertEquals(8.9, AntennaRules.pairLeanDeg(frame("tp_35_stretched", 159, true, 14.0, 7.0, 12.0, false)), 1.0E-9, "3,5″-Toothpick");
        assertEquals(11.3, AntennaRules.pairLeanDeg(frame("lr_4_x", 175, true, 22.0, 9.0, 16.9, false)), 1.0E-9, "4″ Long Range");
        VideoDefinition patch = VideoDefinition.builder(Identifier.fromNamespaceAndPath("justmoreparts", "patch")).mass(12.0).link(VideoLink.DIGITAL)
                .latency(22).range(400).imageQuality(0.9).vtxPower(500).antenna(new VideoAntenna(1, 90.0, AntennaCap.PATCH)).build();
        assertEquals(new VideoAntenna(1, 78.0, AntennaCap.PATCH), AntennaRules.antenna(patch, frame("whoop", 65, false, -3.0, 4.0, 18.0, true)));
    }

    static FrameDefinition frame(String path, int wheelbase, boolean onTop, double plate, double electronics, double cameraY, boolean ducted) {
        FrameDefinition.Builder b = FrameDefinition.builder(Identifier.fromNamespaceAndPath("justmoreparts", path)).mass(100.0).wheelbase(wheelbase)
                .maxProp(Math.min(400, Math.max(30, wheelbase / 2))).motorMount(Identifier.fromNamespaceAndPath("propwash", "m22"))
                .stackMount(Identifier.fromNamespaceAndPath("propwash", "s30")).battery(onTop, plate).electronics(electronics)
                .camera(new Vec3Mm(0.0, cameraY, -0.25 * wheelbase)).aero(0.01, 1.0).batteryFit(new BatteryFitRule(1, 6, 1000.0, 1, 0));
        if (ducted) {
            b.duct(DuctSpec.STANDARD);
        }
        return b.build();
    }

    @Test
    void framesWithoutHolderFallBackBehindTheRearOfTheirTopPlate() {
        Object[][] measured = {
                {"cw_25_duct", 112, true, 16.0, 8.0, 29.5, true, 25.0, 16.0},
                {"cw_35_duct", 158, true, 20.0, 10.0, 34.8, true, 36.0, 20.0},
                {"deadcat_5", 223, true, 30.0, 12.0, 14.0, false, 49.0, 30.0},
                {"fs_5_bash", 226, true, 32.0, 12.0, 20.6, false, 50.0, 32.0},
                {"fs_5_squashed", 239, true, 30.0, 12.0, 16.9, false, 53.0, 30.0},
                {"hframe_5", 252, true, 28.0, 14.0, 18.0, false, 55.0, 28.0},
                {"lr_4_x", 175, true, 22.0, 9.0, 16.9, false, 38.0, 22.0},
                {"lr_6_x", 255, true, 30.0, 12.0, 21.0, false, 56.0, 30.0},
                {"lr_7_deadcat", 302, true, 32.0, 14.0, 16.0, false, 66.0, 32.0},
                {"lr_8_x", 360, true, 34.0, 15.0, 20.2, false, 70.0, 34.0},
                {"race_5_stretched", 236, false, -2.8, 10.0, 16.5, false, 52.0, 24.0},
                {"xclass_10", 460, true, 45.0, 20.0, 27.0, false, 77.0, 45.0},
                {"xclass_12", 700, true, 55.0, 25.0, 26.0, false, 92.0, 55.0},
                {"xclass_13", 820, true, 60.0, 28.0, 26.0, false, 100.0, 60.0},
                {"xclass_13_h", 828, true, 60.0, 30.0, 30.0, false, 101.0, 60.0}};
        for (Object[] row : measured) {
            FrameDefinition frame = frame((String) row[0], (int) row[1], (boolean) row[2], (double) row[3], (double) row[4], (double) row[5],
                    (boolean) row[6]);
            assertTrue(frame.geometry().antennaAnchor().isEmpty());
            AnchorSpec anchor = AntennaRules.anchor(frame);
            assertEquals(anchor, AntennaRules.fallbackAnchor(frame));
            assertEquals(0.0, anchor.x(), 0.0, (String) row[0]);
            assertEquals((double) row[7] + AntennaRules.BEHIND_PLATE_MM, anchor.z(), 4.0, row[0] + ": hinter dem Ende des Top-Plates");
            assertEquals((double) row[8], anchor.y(), 0.5, row[0] + ": Oberkante des Top-Plates");
            assertEquals(AirframeGeometry.DEFAULT_ANTENNA_TILT, anchor.tiltDeg(), 0.0, (String) row[0]);
        }
        FrameDefinition whoop = frame("whoop_65_lite", 65, false, -3.0, 4.0, 18.5, true);
        AnchorSpec whoopAnchor = AntennaRules.anchor(whoop);
        assertTrue(whoopAnchor.z() > 5.0 && whoopAnchor.z() < 15.0, "Whoop: hinten am Canopy " + whoopAnchor.z());
        assertTrue(whoopAnchor.y() >= 18.5, "Whoop: über der Kamera " + whoopAnchor.y());
        AnchorSpec explicit = new AnchorSpec(0.0, 33.0, 57.0, 30.0, 0.0);
        FrameDefinition set = FrameDefinition.builder(whoop.id()).mass(100.0).wheelbase(226).maxProp(130)
                .motorMount(Identifier.fromNamespaceAndPath("propwash", "m22")).stackMount(Identifier.fromNamespaceAndPath("propwash", "s30"))
                .aero(0.01, 1.0).batteryFit(new BatteryFitRule(4, 6, 230.0, 4, 0)).antennaAnchor(explicit).build();
        assertEquals(explicit, AntennaRules.anchor(set));
        assertTrue(AntennaRules.explicitAnchor(set));
        AirframeGeometry legacy = new AirframeGeometry(List.of(), true, 30.0, 12.0, Optional.empty(), 20.0, Vec3Mm.ZERO, MassSplit.OPEN, Map.of());
        assertEquals(Optional.empty(), legacy.antennaAnchor());
        for (FrameType core : FrameType.values()) {
            if (core.builtin()) {
                assertTrue(AntennaRules.explicitAnchor(core.definition()), core.key());
            }
        }
    }
}
