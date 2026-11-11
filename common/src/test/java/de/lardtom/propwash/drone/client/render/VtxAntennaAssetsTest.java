package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.api.part.AnchorSpec;
import de.lardtom.propwash.api.part.AntennaCap;
import de.lardtom.propwash.api.part.VideoAntenna;
import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.api.part.VideoLink;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.AntennaRules;
import de.lardtom.propwash.build.BatteryFit;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.VideoType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VtxAntennaAssetsTest {
    private static final Path ASSETS = LayoutFromDefinitionTest.resources().resolve("assets/propwash");
    private static final Map<String, VideoAntenna> CORE = Map.of(
            "analog_nano", new VideoAntenna(1, 35.0, AntennaCap.WHIP),
            "analog_race", new VideoAntenna(1, 50.0, AntennaCap.LOLLIPOP),
            "digital_lite", new VideoAntenna(1, 60.0, AntennaCap.LOLLIPOP),
            "digital_hd", new VideoAntenna(2, 90.0, AntennaCap.LOLLIPOP));
    private static final float[] TILTS = {0.0F, 10.0F, 20.0F, 30.0F, 40.0F, 50.0F, 60.0F, 70.0F, 80.0F};
    private static final float MARGIN_MM = 0.3F;
    private static final double MAX_LENGTH_OF_WHEELBASE = 1.2;
    private static final float PACK_GAP_MM = 5.0F;
    private static final List<String> LEAD_ELEMENTS = List.of("wire", "connector", "balance", "lead", "plug", "xt", "pigtail");

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static Path model(String category, String key) {
        return ASSETS.resolve("models/part/" + category + "/" + key + ".json");
    }

    private static List<VideoType> videos() {
        List<VideoType> out = new ArrayList<>();
        for (VideoType video : VideoType.values()) {
            if (video.builtin()) {
                out.add(video);
            }
        }
        return out;
    }

    private static List<FrameType> frames() {
        List<FrameType> out = new ArrayList<>();
        for (FrameType frame : FrameType.values()) {
            if (frame.builtin()) {
                out.add(frame);
            }
        }
        return out;
    }

    private static Vector3fc anchor(FrameType frame) {
        AnchorSpec anchor = frame.definition().geometry().antennaAnchor().orElseThrow(() -> new AssertionError(frame.key() + ": ohne antenna_anchor"));
        return new Vector3f((float) anchor.x(), (float) anchor.y(), (float) anchor.z());
    }

    record Box(String name, Vector3f lo, Vector3f hi, Vector3f origin, Matrix3f rotation, Vector3f at, Matrix3f place, float stretchX) {
        Box placed(Vector3fc position, float tiltDegrees, float stretch) {
            return new Box(this.name, this.lo, this.hi, this.origin, this.rotation, new Vector3f(position),
                    new Matrix3f().rotationX((float) Math.toRadians(tiltDegrees)), stretch);
        }

        Vector3f toDrone(Vector3f local) {
            Vector3f p = new Vector3f(local).sub(this.origin);
            this.rotation.transform(p);
            p.add(this.origin);
            p.x *= this.stretchX;
            this.place.transform(p);
            return p.add(this.at);
        }

        boolean contains(Vector3fc point, float margin) {
            Vector3f p = new Vector3f(point).sub(this.at);
            new Matrix3f(this.place).transpose().transform(p);
            p.x /= this.stretchX;
            p.sub(this.origin);
            new Matrix3f(this.rotation).transpose().transform(p);
            p.add(this.origin);
            return p.x >= this.lo.x - margin && p.x <= this.hi.x + margin && p.y >= this.lo.y - margin && p.y <= this.hi.y + margin
                    && p.z >= this.lo.z - margin && p.z <= this.hi.z + margin;
        }

        List<Vector3f> samples(int along) {
            List<Vector3f> out = new ArrayList<>();
            for (int i = 0; i <= 3; i++) {
                for (int j = 0; j <= along; j++) {
                    for (int k = 0; k <= 3; k++) {
                        out.add(this.toDrone(new Vector3f(this.lo.x + (this.hi.x - this.lo.x) * i / 3.0F,
                                this.lo.y + (this.hi.y - this.lo.y) * j / along, this.lo.z + (this.hi.z - this.lo.z) * k / 3.0F)));
                    }
                }
            }
            return out;
        }
    }

    private static List<Box> boxes(Path path) throws IOException {
        List<Box> out = new ArrayList<>();
        for (JsonElement e : json(path).getAsJsonArray("elements")) {
            JsonObject el = e.getAsJsonObject();
            Vector3f from = mm(el.getAsJsonArray("from"));
            Vector3f to = mm(el.getAsJsonArray("to"));
            Matrix3f rotation = PlugAnchors.rotation(el.get("rotation"));
            Vector3f origin = rotation == null ? new Vector3f() : mm(el.getAsJsonObject("rotation").getAsJsonArray("origin"));
            out.add(new Box(el.has("name") ? el.get("name").getAsString() : "", new Vector3f(from).min(to), new Vector3f(from).max(to), origin,
                    rotation == null ? new Matrix3f() : rotation, new Vector3f(), new Matrix3f(), 1.0F));
        }
        return out;
    }

    private static Vector3f mm(JsonArray v) {
        return new Vector3f(PlugAnchors.millimetres(v.get(0).getAsFloat()), PlugAnchors.millimetres(v.get(1).getAsFloat()),
                PlugAnchors.millimetres(v.get(2).getAsFloat()));
    }

    private static List<Box> placed(List<Box> boxes, Vector3fc position, float tilt, float stretch) {
        List<Box> out = new ArrayList<>(boxes.size());
        for (Box box : boxes) {
            out.add(box.placed(position, tilt, stretch));
        }
        return out;
    }

    private static float topMm(List<Box> boxes) {
        float top = Float.NEGATIVE_INFINITY;
        for (Box box : boxes) {
            for (Vector3f p : box.samples(1)) {
                top = Math.max(top, p.y);
            }
        }
        return top;
    }

    private static float bladeTopMm(List<Box> boxes, float fromRadius) {
        float top = Float.NEGATIVE_INFINITY;
        for (Box box : boxes) {
            for (Vector3f p : box.samples(8)) {
                if (p.x * p.x + p.z * p.z >= fromRadius * fromRadius) {
                    top = Math.max(top, p.y);
                }
            }
        }
        return top;
    }

    private static List<Vector3f> antennaPoints(FrameType frame, VideoType video, Vector3fc at, boolean mount) {
        List<Vector3f> out = new ArrayList<>();
        VideoAntenna antenna = AntennaRules.antenna(video.definition(), frame.definition());
        for (AntennaGeometry.Piece piece : AntennaGeometry.pieces(antenna, DroneLayout.antennaTilt(frame), frame.wheelbaseMm,
                AntennaRules.pairLeanDeg(frame.definition()))) {
            if (piece.name().equals("mount") == mount) {
                for (Vector3f p : piece.samples(24)) {
                    out.add(p.add(at));
                }
            }
        }
        return out;
    }

    @Test
    void coreVtxModelsCarryNoAntennaAndNoAntennaVariantIsShipped() throws IOException {
        JsonObject paint = json(ASSETS.resolve("drone_paint.json")).getAsJsonObject("models");
        for (VideoType video : videos()) {
            String key = video.key();
            for (String variant : List.of(key, key + PartModel.PAINT_SUFFIX)) {
                for (Box box : boxes(model("video", variant))) {
                    assertFalse(AntennaMask.isAntenna(box.name()) || box.name().startsWith("lollipop"),
                            variant + ": Antenne steckt im VTX-Modell (" + box.name() + ")");
                }
            }
            assertFalse(Files.exists(model("video", key + "_antenna")), key + "_antenna ist kein Modell mehr");
            assertFalse(Files.exists(ASSETS.resolve("items/drone_render/video/" + key + "_antenna.json")), key + "_antenna");
            assertFalse(paint.has("video/" + key + "_antenna"), key + "_antenna in drone_paint.json");
        }
        for (Path dir : List.of(ASSETS.resolve("models/part/video"), ASSETS.resolve("items/drone_render/video"), ASSETS.resolve("textures/item/part/video"))) {
            try (Stream<Path> files = Files.list(dir)) {
                assertEquals(List.of(), files.filter(p -> p.getFileName().toString().contains("_antenna")).toList(), dir.toString());
            }
        }
    }

    @Test
    void coreVtxAntennasAreDerivedFromTheVtxDataWithTheirRealSize() {
        for (VideoType video : videos()) {
            VideoAntenna expected = CORE.get(video.key());
            assertNotNull(expected, video.key());
            VideoAntenna antenna = AntennaRules.antenna(video.definition());
            assertTrue(video.definition().antenna().isEmpty(), video.key() + ": Kern-VTX ohne Vorgabe, Antenne aus den Daten");
            assertEquals(expected, antenna, video.key());
            List<Float> sides = new ArrayList<>();
            for (int i = 0; i < antenna.count(); i++) {
                Vector3f axis = AntennaGeometry.axis(antenna, AirframeGeometry.DEFAULT_ANTENNA_TILT, i);
                double back = Math.toDegrees(Math.atan2(axis.z, axis.y));
                assertTrue(axis.y > 0.0F && back >= 35.0 && back <= 45.0, String.format("%s: %.1f° statt 35–45° nach hinten geneigt", video.key(), back));
                sides.add(axis.x);
            }
            if (antenna.count() == 1) {
                assertEquals(0.0F, sides.getFirst(), 1.0E-6F, video.key() + ": einzelne Antenne gerade nach hinten oben");
            }
            if (sides.size() == 2) {
                assertTrue(sides.get(0) * sides.get(1) < 0.0F, video.key() + ": zwei Antennen im V");
            }
            float top = Float.NEGATIVE_INFINITY;
            for (AntennaGeometry.Piece piece : AntennaGeometry.pieces(antenna, 0.0, 225.0)) {
                for (Vector3f p : piece.samples(1)) {
                    top = Math.max(top, p.y);
                }
            }
            float tip = (float) (AntennaGeometry.BASE_Y_MM + antenna.lengthMm() * Math.cos(Math.toRadians(antenna.count() == 1
                    ? AntennaGeometry.SINGLE_LEAN_DEG : AntennaGeometry.V_LEAN_DEG)));
            assertTrue(top >= tip - 1.0F && top <= AntennaGeometry.BASE_Y_MM + antenna.lengthMm() + 0.5F,
                    String.format("%s: Spitze bei %.1f mm, erwartet um %.1f mm", video.key(), top, tip));
        }
    }

    @Test
    void everyCoreFrameCarriesTheHolderBehindItsTopPlateWithinReachOfTheMount() throws IOException {
        for (FrameType frame : frames()) {
            Vector3fc at = anchor(frame);
            float rear = Float.NEGATIVE_INFINITY;
            float top = Float.NEGATIVE_INFINITY;
            for (Box box : boxes(model("frame", frame.key()))) {
                if (box.name().equals("top_plate") || box.name().equals("canopy_top")) {
                    rear = Math.max(rear, box.hi().z);
                    top = Math.max(top, box.hi().y);
                }
            }
            assertTrue(Float.isFinite(rear), frame.key() + ": keine Deckplatte im Modell");
            assertEquals(0.0F, at.x(), 0.0F, frame.key());
            assertEquals(top, at.y(), 1.0F, frame.key() + ": Höhe der Deckplatte");
            assertTrue(at.z() >= rear && at.z() - AntennaGeometry.reachMm(frame.wheelbaseMm) <= rear - 2.0F,
                    frame.key() + ": Fuß bei " + at.z() + " hinter der Deckplatte " + rear + ", Halter reicht bis an die Platte");
            double tilt = frame.definition().geometry().antennaAnchor().orElseThrow().tiltDeg();
            assertTrue(tilt >= 35.0 && tilt <= 45.0, frame.key() + ": Neigung " + tilt);
        }
    }

    @Test
    void layoutTakesTheHolderFromTheFrameAndAResourcePackMayMoveIt() throws IOException {
        DroneLayoutParser.Result shipped = LayoutFromDefinitionTest.shippedLayouts();
        JsonObject frames = json(ASSETS.resolve("drone_layout.json")).getAsJsonObject("frames");
        for (FrameType frame : frames()) {
            DroneLayout layout = shipped.layouts().get(frame);
            assertFalse(frames.getAsJsonObject(frame.key()).has("antenna"), frame.key() + ": Halter steht im Rahmen, nicht in drone_layout.json");
            assertTrue(layout.antenna().isEmpty(), frame.key());
            assertEquals(0.0F, layout.antenna(frame).distance(anchor(frame)), 1.0E-4F, frame.key());
            assertEquals(0.0F, DroneLayout.fromDefinition(frame).antenna(frame).distance(anchor(frame)), 1.0E-4F, frame.key());
            assertEquals(frame.definition().geometry().antennaAnchor().orElseThrow().tiltDeg(), DroneLayout.antennaTilt(frame), 1.0E-4F, frame.key());
            AnchorSpec fallback = AntennaRules.fallbackAnchor(frame.definition());
            assertEquals(0.0, fallback.x(), 0.0, frame.key());
            assertEquals(AirframeGeometry.DEFAULT_ANTENNA_TILT, fallback.tiltDeg(), 0.0, frame.key());
            assertTrue(fallback.z() > DroneLayout.fromDefinition(frame).battery().z(), frame.key() + ": Rückfall hinter dem Akku");
            assertTrue(Math.abs(fallback.z() - anchor(frame).z()) <= 6.0, frame.key() + ": Rückfall " + fallback.z() + " statt um " + anchor(frame).z());
        }
        JsonObject entry = frames.getAsJsonObject(FrameType.RACE_5.key()).deepCopy();
        entry.add("antenna", JsonParser.parseString("[0.0, 21.0, 40.0]"));
        List<String> problems = new ArrayList<>();
        DroneLayout moved = DroneLayoutParser.parseFrame(FrameType.RACE_5, entry, problems);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(0.0F, moved.antenna(FrameType.RACE_5).distance(new Vector3f(0.0F, 21.0F, 40.0F)), 1.0E-4F);
        entry.addProperty("antenna", "hinten");
        problems.clear();
        DroneLayout broken = DroneLayoutParser.parseFrame(FrameType.RACE_5, entry, problems);
        assertTrue(problems.stream().anyMatch(p -> p.contains("antenna")), problems.toString());
        assertEquals(0.0F, broken.antenna(FrameType.RACE_5).distance(anchor(FrameType.RACE_5)), 1.0E-4F);
    }

    @Test
    void antennasClearBatteryPropsCameraAndAccessoriesOnEveryCoreFrame() throws IOException {
        DroneLayoutParser.Result shipped = LayoutFromDefinitionTest.shippedLayouts();
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (FrameType frame : frames()) {
            DroneLayout layout = shipped.layouts().get(frame);
            assertNotNull(layout, frame.key());
            Vector3fc at = layout.antenna(frame);
            List<Box> frameBoxes = boxes(model("frame", frame.key()));
            Map<String, List<Box>> others = new LinkedHashMap<>();
            for (BatteryType battery : BatteryFit.fitting(frame)) {
                if (!battery.builtin()) {
                    continue;
                }
                List<Box> pack;
                if (layout.batteryBelow() && Files.exists(model("battery", battery.key() + PartModel.UNDER_SUFFIX))) {
                    pack = placed(boxes(model("battery", battery.key() + PartModel.UNDER_SUFFIX)), layout.battery(), 0.0F, 1.0F);
                } else if (layout.batteryBelow()) {
                    List<Box> raw = boxes(model("battery", battery.key()));
                    pack = placed(raw, DroneAssembly.hangBelow(layout.battery(), topMm(raw)), 0.0F, 1.0F);
                } else {
                    pack = placed(boxes(model("battery", battery.key())), layout.battery(), 0.0F, 1.0F);
                }
                others.put("Akku " + battery.key(), pack);
            }
            for (AccessoryMount mount : frame.accessoryMounts) {
                AccessoryAnchor anchor = layout.accessory(mount).orElseThrow();
                Vector3f position = new Vector3f((float) anchor.x(), (float) anchor.y(), (float) anchor.z());
                for (AccessoryType type : AccessoryType.values()) {
                    if (type.builtin() && type.fits(mount) && type.fitsFrame(frame)) {
                        others.put("Zubehör " + type.key(), placed(boxes(model("accessory", type.key())), position, (float) anchor.tiltFor(type),
                                (float) anchor.stretch(type)));
                    }
                }
                if (mount.hasBase()) {
                    others.put("Halter " + mount.key(), placed(boxes(model("accessory", "mount_" + mount.key())), position, 0.0F, 1.0F));
                }
            }
            for (VideoType video : videos()) {
                List<Box> camera = new ArrayList<>();
                List<Box> body = new ArrayList<>();
                for (Box box : boxes(model("video", video.key()))) {
                    (box.name().startsWith("cam_") || box.name().startsWith("lens_") ? camera : body).add(box);
                }
                Map<String, List<Box>> obstacles = new LinkedHashMap<>(others);
                for (float tilt : TILTS) {
                    obstacles.put("Kamera bei " + tilt + "°", placed(camera, layout.video(), tilt, 1.0F));
                }
                List<Box> vtx = placed(body, layout.video(), layout.cameraTiltDegrees(), 1.0F);
                if (!overlaps(vtx, frameBoxes)) {
                    obstacles.put("VTX " + video.key(), vtx);
                }
                List<Vector3f> stalk = antennaPoints(frame, video, at, false);
                List<Vector3f> mountPoints = antennaPoints(frame, video, at, true);
                String where = frame.key() + " + " + video.key() + ": ";
                for (Map.Entry<String, List<Box>> obstacle : obstacles.entrySet()) {
                    if (hits(stalk, obstacle.getValue())) {
                        problems.add(where + "Antenne durch " + obstacle.getKey());
                    }
                    if (hits(mountPoints, obstacle.getValue())) {
                        problems.add(where + "Antennenhalter durch " + obstacle.getKey());
                    }
                }
                if (hits(stalk, frameBoxes)) {
                    problems.add(where + "Antenne durch den Rahmen");
                }
                String prop = propHit(frame, layout, stalk);
                if (prop != null) {
                    problems.add(where + "Antenne im Prop-Kreis " + prop);
                }
                checked++;
            }
        }
        assertEquals(frames().size() * videos().size(), checked);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    static VideoDefinition addonVtx(String path, double grams, VideoLink link, int latency, int range, double quality, int powerMw) {
        return VideoDefinition.builder(Identifier.fromNamespaceAndPath("justmoreparts", path)).mass(grams).link(link).latency(latency).range(range)
                .imageQuality(quality).vtxPower(powerMw).build();
    }

    static List<VideoDefinition> vtxUnderTest() {
        List<VideoDefinition> out = new ArrayList<>();
        for (VideoType video : videos()) {
            out.add(video.definition());
        }
        out.add(addonVtx("analog_whoop_25mw", 2.2, VideoLink.ANALOG, 12, 200, 0.5, 25));
        out.add(addonVtx("digital_micro", 4.8, VideoLink.DIGITAL, 24, 220, 0.78, 200));
        out.add(addonVtx("digital_low_latency", 9.8, VideoLink.DIGITAL, 14, 380, 0.82, 500));
        out.add(addonVtx("analog_race_800", 9.5, VideoLink.ANALOG, 11, 520, 0.68, 800));
        out.add(addonVtx("digital_hd_pro", 24.0, VideoLink.DIGITAL, 24, 650, 1.0, 1000));
        out.add(addonVtx("digital_lr", 19.0, VideoLink.DIGITAL, 32, 900, 0.9, 1200));
        out.add(addonVtx("analog_lr_1600", 16.0, VideoLink.ANALOG, 12, 820, 0.62, 1600));
        return out;
    }

    static VideoAntenna rendered(VideoDefinition video, FrameType frame) {
        return AntennaRules.antenna(video, frame.definition());
    }

    static List<List<Vector3f>> cornersOf(List<Box> boxes) {
        List<List<Vector3f>> out = new ArrayList<>(boxes.size());
        for (Box box : boxes) {
            List<Vector3f> corners = new ArrayList<>(8);
            for (Vector3f local : AntennaVisibility.corners(box.lo().x, box.lo().y, box.lo().z, box.hi().x, box.hi().y, box.hi().z)) {
                corners.add(box.toDrone(local));
            }
            out.add(corners);
        }
        return out;
    }

    static List<Box> batteryBoxes(DroneLayout layout, BatteryType battery) throws IOException {
        if (layout.batteryBelow() && Files.exists(model("battery", battery.key() + PartModel.UNDER_SUFFIX))) {
            return placed(boxes(model("battery", battery.key() + PartModel.UNDER_SUFFIX)), layout.battery(), 0.0F, 1.0F);
        }
        if (layout.batteryBelow()) {
            List<Box> raw = boxes(model("battery", battery.key()));
            return placed(raw, DroneAssembly.hangBelow(layout.battery(), topMm(raw)), 0.0F, 1.0F);
        }
        return placed(boxes(model("battery", battery.key())), layout.battery(), 0.0F, 1.0F);
    }

    @Test
    void antennasStandFreeOfTheBatteryFromBehindAndFromTheSide() throws IOException {
        DroneLayoutParser.Result shipped = LayoutFromDefinitionTest.shippedLayouts();
        List<String> problems = new ArrayList<>();
        int checked = 0;
        int failed = 0;
        for (FrameType frame : frames()) {
            DroneLayout layout = shipped.layouts().get(frame);
            Vector3fc at = layout.antenna(frame);
            double tilt = DroneLayout.antennaTilt(frame);
            for (BatteryType battery : BatteryFit.fitting(frame)) {
                if (!battery.builtin()) {
                    continue;
                }
                List<List<Vector3f>> pack = cornersOf(batteryBoxes(layout, battery));
                for (VideoDefinition video : vtxUnderTest()) {
                    VideoAntenna antenna = rendered(video, frame);
                    String where = frame.key() + " + " + battery.key() + " + " + video.id().getPath();
                    int before = problems.size();
                    for (AntennaVisibility.View view : AntennaVisibility.SIDE_VIEWS) {
                        float[][] result = AntennaVisibility.freeAndTotal(antenna, tilt, frame.wheelbaseMm,
                                AntennaRules.pairLeanDeg(frame.definition()), at, AntennaVisibility.silhouettes(pack, view), view);
                        for (float[] one : result) {
                            if (one[0] < AntennaVisibility.SIDE_FREE * one[1]) {
                                problems.add(String.format(Locale.ROOT, "%s, von %s: nur %.0f von %.0f mm frei", where, view.name(), one[0], one[1]));
                            }
                        }
                    }
                    for (AntennaVisibility.View view : AntennaVisibility.QUARTER_VIEWS) {
                        float[][] result = AntennaVisibility.freeAndTotal(antenna, tilt, frame.wheelbaseMm,
                                AntennaRules.pairLeanDeg(frame.definition()), at, AntennaVisibility.silhouettes(pack, view), view);
                        float need = antenna.count() == 1 ? AntennaVisibility.QUARTER_FREE_SINGLE : AntennaVisibility.QUARTER_FREE_PAIR;
                        for (float[] one : result) {
                            if (one[0] < need * one[1]) {
                                problems.add(String.format(Locale.ROOT, "%s, %s: nur %.0f von %.0f mm vor freiem Grund statt vor dem Akku", where,
                                        view.name(), one[0], one[1]));
                            }
                        }
                    }
                    failed += problems.size() > before ? 1 : 0;
                    checked++;
                }
            }
        }
        assertTrue(checked > 0);
        assertTrue(problems.isEmpty(), failed + " von " + checked + " Kombinationen:\n" + String.join("\n", problems));
    }

    @Test
    void theHolderSitsBehindTheFrameEdgeAndBehindEveryFittingPack() throws IOException {
        DroneLayoutParser.Result shipped = LayoutFromDefinitionTest.shippedLayouts();
        List<String> problems = new ArrayList<>();
        for (FrameType frame : frames()) {
            DroneLayout layout = shipped.layouts().get(frame);
            Vector3fc at = layout.antenna(frame);
            float edge = Float.NEGATIVE_INFINITY;
            for (Box box : boxes(model("frame", frame.key()))) {
                if (box.name().endsWith("_plate") || box.name().startsWith("rear_mount") || box.name().startsWith("canopy")) {
                    edge = Math.max(edge, box.hi().z);
                }
            }
            if (at.z() < edge) {
                problems.add(String.format(Locale.ROOT, "%s: Halter bei z %.1f vor der Hinterkante des Rahmens %.1f", frame.key(), at.z(), edge));
            }
            if (!layout.batteryBelow()) {
                for (BatteryType battery : BatteryFit.fitting(frame)) {
                    if (!battery.builtin()) {
                        continue;
                    }
                    float rear = Float.NEGATIVE_INFINITY;
                    for (Box box : batteryBoxes(layout, battery)) {
                        if (LEAD_ELEMENTS.stream().noneMatch(box.name()::startsWith)) {
                            for (Vector3f p : box.samples(1)) {
                                rear = Math.max(rear, p.z);
                            }
                        }
                    }
                    if (at.z() < rear + PACK_GAP_MM) {
                        problems.add(String.format(Locale.ROOT, "%s + %s: Halter bei z %.1f nicht %.0f mm hinter dem Akku (%.1f)", frame.key(),
                                battery.key(), at.z(), PACK_GAP_MM, rear));
                    }
                }
            }
            double tilt = DroneLayout.antennaTilt(frame);
            if (tilt < 35.0 || tilt > 45.0) {
                problems.add(frame.key() + ": Neigung " + tilt + " statt 35–45°");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void antennasStayInProportionOnSmallFramesAndPairsSpreadAboutThirtyDegrees() {
        DroneLayoutParser.Result shipped;
        try {
            shipped = LayoutFromDefinitionTest.shippedLayouts();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        List<String> problems = new ArrayList<>();
        for (FrameType frame : frames()) {
            DroneLayout layout = shipped.layouts().get(frame);
            Vector3fc at = layout.antenna(frame);
            double limit = MAX_LENGTH_OF_WHEELBASE * frame.wheelbaseMm;
            for (VideoType video : videos()) {
                DroneAssembly.Part part = DroneAssembly.antennaPart(layout, frame, video);
                Shape shape = part.solid();
                float reach = 0.0F;
                Vector3f p = new Vector3f();
                for (int v = 0; v < shape.vertexCount(); v++) {
                    shape.position(v, p);
                    p.mul(DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK).add(part.position());
                    reach = Math.max(reach, p.distance(at));
                }
                if (reach > limit + 4.0) {
                    problems.add(String.format(Locale.ROOT, "%s + %s: Antenne reicht %.0f mm weit, Radstand %d mm (höchstens %.0f mm)", frame.key(),
                            video.key(), reach, frame.wheelbaseMm, limit));
                }
            }
            for (VideoDefinition video : vtxUnderTest()) {
                VideoAntenna antenna = rendered(video, frame);
                if (antenna.lengthMm() > limit + 1.0E-6) {
                    problems.add(String.format(Locale.ROOT, "%s + %s: %.0f mm lang, höchstens %.0f mm", frame.key(), video.id().getPath(),
                            antenna.lengthMm(), limit));
                }
                if (antenna.count() == 2) {
                    double tilt = DroneLayout.antennaTilt(frame);
                    double lean = AntennaRules.pairLeanDeg(frame.definition());
                    double spread = Math.toDegrees(AntennaGeometry.axis(antenna, tilt, 0, lean).angle(AntennaGeometry.axis(antenna, tilt, 1, lean)));
                    if (spread < 25.0 || spread > 35.0) {
                        problems.add(String.format(Locale.ROOT, "%s + %s: V mit %.0f° statt etwa 30°", frame.key(), video.id().getPath(), spread));
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void antennaElementsOfAddonVtxModelsAreLeftOut() {
        JsonObject model = JsonParser.parseString("""
                {"elements": [
                  {"name": "air_unit", "from": [5, 8, 9], "to": [11, 9.5, 13.5], "faces": {"up": {}, "down": {}, "north": {}, "south": {}, "east": {}, "west": {}}},
                  {"name": "port_panel", "from": [6, 8.5, 13.5], "to": [10, 9.4, 13.6], "faces": {"south": {}}},
                  {"name": "antenna_socket", "from": [6.375, 9, 13.5], "to": [6.875, 9.375, 14.0625], "faces": {"up": {}, "south": {}, "east": {}, "west": {}}},
                  {"name": "antenna", "from": [6.4125, 9.375, 13.6], "to": [6.8375, 13.625, 14.025],
                   "rotation": {"x": 64, "y": -28, "z": 0, "origin": [6.625, 9.375, 13.8125]},
                   "faces": {"up": {}, "down": {}, "north": {}, "south": {}, "east": {}, "west": {}}},
                  {"name": "antenna_tip", "from": [6.3625, 13.625, 13.55], "to": [6.8875, 14.625, 14.075],
                   "rotation": {"x": 64, "y": -28, "z": 0, "origin": [6.625, 9.375, 13.8125]},
                   "faces": {"up": {}, "down": {}, "north": {}, "south": {}, "east": {}, "west": {}}},
                  {"name": "mmcx", "from": [10, 9.5, 12], "to": [10.5, 10, 12.5], "faces": {"up": {}}}
                ]}
                """).getAsJsonObject();
        AntennaMask mask = AntennaMask.of(model);
        assertEquals(3, mask.antennaElements());
        assertEquals(16, mask.antennaFaces());
        assertTrue(AntennaMask.isAntenna("antenna") && AntennaMask.isAntenna("antenna_tee") && AntennaMask.isAntenna("antenna_joint")
                && AntennaMask.isAntenna("antenna_lead") && AntennaMask.isAntenna("Antenna_Tip"));
        assertFalse(AntennaMask.isAntenna("mmcx") || AntennaMask.isAntenna("vtx_antenna"));
        Matrix3f rotation = PlugAnchors.rotation(JsonParser.parseString("{\"x\": 64, \"y\": -28, \"z\": 0}"));
        assertNotNull(rotation);
        Vector3f origin = new Vector3f(6.625F, 9.375F, 13.8125F);
        Vector3f[] top = new Vector3f[4];
        float[][] corners = {{6.4125F, 14.025F}, {6.8375F, 14.025F}, {6.8375F, 13.6F}, {6.4125F, 13.6F}};
        for (int i = 0; i < 4; i++) {
            top[i] = rotation.transform(new Vector3f(corners[i][0], 13.625F, corners[i][1]).sub(origin)).add(origin);
        }
        assertTrue(mask.hides(top), "Stirnfläche der gedrehten Antenne");
        assertTrue(mask.hides(new Vector3f(6.375F, 9.375F, 13.5F), new Vector3f(6.875F, 9.375F, 13.5F), new Vector3f(6.875F, 9.375F, 14.0625F),
                new Vector3f(6.375F, 9.375F, 14.0625F)), "Oberseite der Buchse");
        assertFalse(mask.hides(new Vector3f(5, 9.5F, 9), new Vector3f(11, 9.5F, 9), new Vector3f(11, 9.5F, 13.5F), new Vector3f(5, 9.5F, 13.5F)),
                "Oberseite des Senders");
        assertFalse(mask.hides(new Vector3f(6, 8.5F, 13.6F), new Vector3f(10, 8.5F, 13.6F), new Vector3f(10, 9.4F, 13.6F), new Vector3f(6, 9.4F, 13.6F)),
                "Rückseite der Anschlussplatte");
        assertTrue(AntennaMask.of(JsonParser.parseString("{\"elements\": [{\"name\": \"vtx\", \"from\": [0, 0, 0], \"to\": [1, 1, 1]}]}")
                .getAsJsonObject()).isEmpty());
    }

    private static boolean overlaps(List<Box> a, List<Box> b) {
        for (Box box : a) {
            if (hits(box.samples(4), b)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hits(List<Vector3f> points, List<Box> boxes) {
        for (Vector3f p : points) {
            for (Box box : boxes) {
                if (box.contains(p, MARGIN_MM)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String propHit(FrameType frame, DroneLayout layout, List<Vector3f> points) throws IOException {
        for (PropType prop : PropType.values()) {
            if (!prop.builtin() || prop.diameterMm > frame.maxPropMm || prop.diameterMm < frame.minPropMm) {
                continue;
            }
            float bladeTop = bladeTopMm(boxes(model("prop", prop.key())), prop.diameterMm * 0.25F);
            float radius = prop.diameterMm * 0.5F + 1.5F;
            for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                Vector3fc motor = layout.motor(slot);
                float hub = motor.y() + layout.propHeight();
                for (Vector3f p : points) {
                    float dx = p.x - motor.x();
                    float dz = p.z - motor.z();
                    if (dx * dx + dz * dz < radius * radius && p.y > hub - 3.0F && p.y < hub + bladeTop + 2.0F) {
                        return String.format(java.util.Locale.ROOT, "%s an Motor %d bei (%.1f, %.1f, %.1f), %.1f mm von der Nabe, Blatt bis %.1f mm", prop.key(), slot, p.x, p.y, p.z, Math.sqrt(dx * dx + dz * dz), hub + bladeTop);
                    }
                }
            }
        }
        return null;
    }
}
