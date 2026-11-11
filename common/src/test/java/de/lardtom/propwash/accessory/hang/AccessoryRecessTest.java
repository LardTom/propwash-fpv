package de.lardtom.propwash.accessory.hang;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.accessory.MountPoses;
import de.lardtom.propwash.accessory.dispense.DispenseOrigin;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.ActionCamDefinition;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.drone.client.render.AccessoryFit;
import de.lardtom.propwash.drone.client.render.FallbackShapes;
import de.lardtom.propwash.drone.client.render.Shape;
import de.lardtom.propwash.parts.PartJson;
import de.lardtom.propwash.parts.PartRegistryImpl;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.testutil.TestPaths;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessoryRecessTest {
    private static final double EPS = 1.0E-9;
    private static final double RECESS = 58.0;
    private static final double DROP = 1.5;
    private static final Vec3 COM = new Vec3(0.5, 70.0, 0.5);

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("recesstest", path);
    }

    private static AccessoryDefinition.Builder front(String path) {
        return AccessoryDefinition.builder(id(path)).basics(PartBasics.of(150.0)).slot(AccessorySlot.FRONT).size(62.0, 58.0, 64.0).frontalArea(0.0036);
    }

    private static AccessoryType type(AccessoryDefinition definition) {
        return (AccessoryType) PartIndex.detached(definition);
    }

    private static AccessoryType plain() {
        return type(front("plain").build());
    }

    private static AccessoryType recessed() {
        return type(front("recessed").recess(RECESS).drop(DROP).build());
    }

    private static AccessoryType recessedBy(double mm) {
        return type(front("recessed_" + (int) mm).recess(mm).build());
    }

    private static AccessoryType spearMount() {
        return type(AccessoryDefinition.builder(id("spear_mount")).basics(PartBasics.of(40.0)).slot(AccessorySlot.BOTTOM)
                .size(24.0, 18.0, 90.0).frontalArea(0.0004).build());
    }

    private static double[] shifted(double[] local, double recess, double drop) {
        return new double[]{local[0], local[1] - drop, local[2] + recess};
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static float[] batteryUnderRace5() throws IOException {
        Path assets = TestPaths.common().resolve("src/main/resources/assets/propwash");
        JsonArray at = json(assets.resolve("drone_layout.json")).getAsJsonObject("frames").getAsJsonObject("race_5").getAsJsonArray("battery");
        JsonObject item = json(assets.resolve("items/drone_render/battery/" + DroneBuild.RACER.battery().key() + "_under.json")).getAsJsonObject("model");
        Identifier model = Identifier.parse(item.get("model").getAsString());
        float[] quads = AccessoryFit.elementQuads(json(assets.resolve("models/" + model.getPath() + ".json")));
        for (int i = 0; i + 2 < quads.length; i += 3) {
            quads[i] += at.get(0).getAsFloat();
            quads[i + 1] += at.get(1).getAsFloat();
            quads[i + 2] += at.get(2).getAsFloat();
        }
        return quads;
    }

    private static AccessoryFit.Fit against(float[] body, FrameType frame, AccessoryType type) {
        AccessoryAnchor anchor = AccessoryAnchor.of(frame, AccessoryMount.FRONT);
        return AccessoryFit.measure(body, AccessoryFit.boxQuads(anchor, type), anchor, type);
    }

    @Test
    void withoutTheFieldEveryBuiltinAccessoryStaysWhereItWas() {
        int checked = 0;
        for (AccessoryType builtin : AccessoryType.all()) {
            assertEquals(0.0, builtin.definition().recessMm(), 0.0, builtin.key());
            assertEquals(0.0, builtin.definition().dropMm(), 0.0, builtin.key());
            assertFalse(PartJson.encode(builtin.definition()).has("recess_mm"), builtin.key());
            assertFalse(PartJson.encode(builtin.definition()).has("drop_mm"), builtin.key());
            assertArrayEquals(builtin.modelBoxMinMm(), builtin.boxMinMm(), 0.0, builtin.key());
            assertArrayEquals(builtin.modelBoxMaxMm(), builtin.boxMaxMm(), 0.0, builtin.key());
            assertArrayEquals(builtin.modelLensMm(), builtin.lensMm(), 0.0, builtin.key());
            for (DroneBuild preset : DroneBuild.PRESETS) {
                if (preset.frame().accessoryMounts.contains(builtin.mount)) {
                    AccessoryAnchor anchor = AccessoryAnchor.of(preset.frame(), builtin.mount);
                    double[] origin = anchor.modelOrigin(builtin);
                    assertArrayEquals(new double[]{anchor.x(), anchor.y(), anchor.z()}, origin, 0.0, preset.frame().key() + " " + builtin.key());
                }
            }
            checked++;
        }
        assertTrue(checked >= 5);
    }

    @Test
    void theBoxMovesBackAndDownAndMuzzleCenterAndLensFollow() {
        AccessoryType plain = plain();
        AccessoryType recessed = recessed();
        assertEquals(RECESS, recessed.recessMm, 0.0);
        assertEquals(DROP, recessed.dropMm, 0.0);
        assertArrayEquals(new double[]{-31.0, -59.5, -6.0}, recessed.boxMinMm(), EPS);
        assertArrayEquals(new double[]{31.0, -1.5, 58.0}, recessed.boxMaxMm(), EPS);
        assertArrayEquals(new double[]{0.0, -30.5, 26.0}, recessed.centerMm(), EPS);
        assertArrayEquals(new double[]{0.0, -30.5, -6.0}, recessed.muzzleMm(), EPS);
        assertArrayEquals(shifted(plain.muzzleMm(), RECESS, DROP), recessed.muzzleMm(), EPS);
        assertArrayEquals(plain.boxMinMm(), recessed.modelBoxMinMm(), EPS);
        assertArrayEquals(plain.boxMaxMm(), recessed.modelBoxMaxMm(), EPS);
        AccessoryType aimed = type(front("aimed").recess(RECESS).drop(DROP).muzzle(0.0, 20.0, 0.0).build());
        assertArrayEquals(new double[]{0.0, -10.5, -6.0}, aimed.muzzleMm(), EPS);
        ActionCamDefinition cam = AccessoryType.ACTION_CAM_HEAVY.definition().actionCam().orElseThrow();
        AccessoryDefinition.Builder camera = AccessoryDefinition.builder(id("camera")).basics(PartBasics.of(90.0)).slot(AccessorySlot.TOP)
                .size(40.0, 30.0, 40.0).frontalArea(0.001).actionCam(cam).lens(2.0, 10.0);
        AccessoryType flat = type(camera.build());
        AccessoryType sunk = type(camera.recess(12.0).drop(4.0).build());
        assertArrayEquals(shifted(flat.lensMm(), 12.0, 4.0), sunk.lensMm(), EPS);
        assertArrayEquals(flat.lensMm(), sunk.modelLensMm(), EPS);
        AccessoryDefinition.Builder lit = AccessoryDefinition.builder(id("strip")).basics(PartBasics.of(9.0)).slot(AccessorySlot.BOTTOM)
                .size(30.0, 4.0, 8.0).frontalArea(0.0002).leds(4);
        AccessoryType strip = type(lit.build());
        AccessoryType sunkStrip = type(lit.recess(5.0).drop(1.0).build());
        assertArrayEquals(shifted(strip.ledMm(1, 30.0), 5.0, 1.0), sunkStrip.ledMm(1, 30.0), EPS);
        assertArrayEquals(strip.ledMm(1, 30.0), sunkStrip.modelLedMm(1, 30.0), EPS);
    }

    @Test
    void theDrawnModelSitsAtTheOriginThatMatchesTheBoxCorners() {
        AccessoryType recessed = recessed();
        for (FrameType frame : FrontAnchorFitTest.frontFrames()) {
            AccessoryAnchor anchor = AccessoryAnchor.of(frame, AccessoryMount.FRONT);
            assertArrayEquals(new double[]{anchor.x(), anchor.y() - DROP, anchor.z() + RECESS}, anchor.modelOrigin(recessed), EPS, frame.key());
        }
        AccessoryType bottom = type(AccessoryDefinition.builder(id("bottom")).basics(PartBasics.of(40.0)).slot(AccessorySlot.BOTTOM)
                .size(24.0, 18.0, 90.0).frontalArea(0.0004).recess(30.0).drop(6.0).build());
        AccessoryAnchor tilted = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.BOTTOM);
        double[] origin = tilted.modelOrigin(bottom);
        double[] model = bottom.modelBoxMinMm();
        double[] corner = tilted.corners(bottom)[0];
        double[] turned = tilted.rotate(model[0] * tilted.stretch(bottom), model[1], model[2]);
        assertArrayEquals(new double[]{origin[0] + turned[0], origin[1] + turned[1], origin[2] + turned[2]}, corner, 1.0E-6);
        double[] offset = tilted.rotate(0.0, -6.0, 30.0);
        assertArrayEquals(new double[]{tilted.x(), tilted.y() + offset[1], tilted.z() + offset[2]}, origin, 1.0E-6);
    }

    @Test
    void theFallbackShapeIsDrawnFromTheModelOriginAndDoesNotMoveItself() {
        Shape flat = FallbackShapes.accessory(plain());
        Shape sunk = FallbackShapes.accessory(recessed());
        assertEquals(flat.min().x(), sunk.min().x(), 0.0F);
        assertEquals(flat.min().y(), sunk.min().y(), 0.0F);
        assertEquals(flat.min().z(), sunk.min().z(), 0.0F);
        assertEquals(flat.max().x(), sunk.max().x(), 0.0F);
        assertEquals(flat.max().y(), sunk.max().y(), 0.0F);
        assertEquals(flat.max().z(), sunk.max().z(), 0.0F);
    }

    @Test
    void mountPoseFollowsAndTheAnchorStays() {
        AccessoryType plain = plain();
        AccessoryType recessed = recessed();
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        AccessoryAnchor anchor = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.FRONT);
        Quaternionf level = new Quaternionf();
        MountPose flat = MountPoses.pose(COM, level, p, anchor, plain);
        MountPose sunk = MountPoses.pose(COM, level, p, anchor, recessed);
        assertEquals(flat.anchor(), sunk.anchor());
        assertEquals(flat.forward(), sunk.forward());
        assertEquals(flat.up(), sunk.up());
        assertEquals(RECESS / 1000.0, sunk.front().z - flat.front().z, 1.0E-5);
        assertEquals(-DROP / 1000.0, sunk.front().y - flat.front().y, 1.0E-5);
        assertEquals(RECESS / 1000.0, sunk.center().z - flat.center().z, 1.0E-5);
        assertEquals(-DROP / 1000.0, sunk.center().y - flat.center().y, 1.0E-5);
        assertTrue(sunk.front().z < anchor.z() / 1000.0 + p.comZ + COM.z, "die Mündung bleibt vor dem Anker");
        double lowestFlat = DispenseOrigin.lowestY(COM, level, p, anchor, plain);
        double lowestSunk = DispenseOrigin.lowestY(COM, level, p, anchor, recessed);
        assertEquals(-DROP / 1000.0, lowestSunk - lowestFlat, 1.0E-5);
    }

    @Test
    void flightValuesFollowTheRecessedBox() {
        AccessoryType plain = plain();
        AccessoryType recessed = recessed();
        DroneBuild withPlain = DroneBuild.FREESTYLE.withAccessory(plain);
        DroneBuild withRecess = DroneBuild.FREESTYLE.withAccessory(recessed);
        AirframeSpec flat = AirframeSpec.of(withPlain);
        AirframeSpec sunk = AirframeSpec.of(withRecess);
        assertEquals(flat.massKg, sunk.massKg, 0.0);
        assertEquals(0.150 * DROP / 1000.0 / sunk.massKg, flat.comHeight - sunk.comHeight, 1.0E-9);
        assertTrue(sunk.inertiaXx < flat.inertiaXx, "weniger Trägheit um die Nickachse: " + sunk.inertiaXx + " gegen " + flat.inertiaXx);
        assertTrue(sunk.inertiaYy < flat.inertiaYy, "weniger Trägheit um die Hochachse: " + sunk.inertiaYy + " gegen " + flat.inertiaYy);
        assertTrue(sunk.accessoryPointCount() > 0);
        assertEquals(flat.accessoryPointCount(), sunk.accessoryPointCount());
        AccessoryAnchor anchor = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.FRONT);
        double lowest = Double.POSITIVE_INFINITY;
        for (double[] corner : anchor.corners(recessed)) {
            lowest = Math.min(lowest, corner[1] / 1000.0 - sunk.comHeight);
        }
        assertEquals(lowest, AccessoryStandTest.lowestCornerBelowCom(withRecess), EPS);
        double back = Double.NEGATIVE_INFINITY;
        double ahead = Double.POSITIVE_INFINITY;
        for (int k = 0; k < sunk.accessoryPointCount(); k++) {
            back = Math.max(back, -sunk.accessoryZ(k) - sunk.comZ);
            ahead = Math.min(ahead, -sunk.accessoryZ(k) - sunk.comZ);
        }
        assertEquals((anchor.z() + 58.0) / 1000.0, back, 1.0E-5, "hinterste Standecke");
        assertEquals((anchor.z() - 6.0) / 1000.0, ahead, 1.0E-5, "vorderste Standecke");
    }

    @Test
    void ladenVariantsAndCodecKeepTheField() {
        AccessoryType recessed = recessed();
        AccessoryType laden = recessed.laden(3000L);
        assertEquals(RECESS, laden.recessMm, 0.0);
        assertEquals(DROP, laden.dropMm, 0.0);
        assertArrayEquals(recessed.boxMinMm(), laden.boxMinMm(), 0.0);
        AccessoryDefinition definition = recessed.definition();
        JsonObject written = PartRegistryImpl.INSTANCE.toJson(definition);
        assertEquals(RECESS, written.get("recess_mm").getAsDouble(), 0.0);
        assertEquals(DROP, written.get("drop_mm").getAsDouble(), 0.0);
        assertEquals(definition, PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, definition.id(), written));
        AccessoryDefinition onlyRecess = front("only").recess(RECESS).build();
        assertFalse(PartJson.encode(onlyRecess).has("drop_mm"));
        assertEquals(onlyRecess, PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, onlyRecess.id(), PartJson.encode(onlyRecess)));
        AccessoryDefinition open = front("plain").build();
        assertFalse(PartJson.encode(open).has("recess_mm"));
        assertEquals(open, PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, open.id(), PartJson.encode(open)));
        assertNotEquals(PartIndex.hashOf(open), PartIndex.hashOf(front("plain").recess(RECESS).build()));
        assertNotEquals(PartIndex.hashOf(open), PartIndex.hashOf(front("plain").drop(DROP).build()));
        AccessoryDefinition sixteen = new AccessoryDefinition(open.id(), open.basics(), open.slot(), open.widthMm(), open.heightMm(), open.depthMm(),
                open.flightBatteryWatts(), open.frontalAreaM2(), open.ledCount(), Optional.empty(), 0.0, 0.0, List.of(), false, Optional.empty(), Set.of());
        assertEquals(open, sixteen);
    }

    @Test
    void valuesOutsideTheBoxAreRejectedWithTheFieldName() {
        assertTrue(front("whole").recess(64.0).drop(58.0).build().recessMm() == 64.0);
        for (double wrong : new double[]{-0.5, 64.5, Double.NaN}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> front("wrong").recess(wrong).build());
            assertTrue(error.getMessage().contains("recess_mm"), error.getMessage());
        }
        for (double wrong : new double[]{-0.5, 58.5, Double.NaN}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> front("wrong").drop(wrong).build());
            assertTrue(error.getMessage().contains("drop_mm"), error.getMessage());
        }
        JsonObject text = PartJson.encode(front("wrong").build());
        text.addProperty("recess_mm", "weit");
        IllegalArgumentException typed = assertThrows(IllegalArgumentException.class,
                () -> PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, id("wrong"), text));
        assertTrue(typed.getMessage().contains("recess_mm"), typed.getMessage());
        JsonObject far = PartJson.encode(front("wrong").build());
        far.addProperty("drop_mm", 80.0);
        IllegalArgumentException range = assertThrows(IllegalArgumentException.class,
                () -> PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, id("wrong"), far));
        assertTrue(range.getMessage().contains("drop_mm"), range.getMessage());
    }

    @Test
    void theRecessedBoxTouchesEveryBuiltinFrameAndOnlyTheWantedOverlapIsAllowed() throws IOException {
        List<String> misses = new ArrayList<>();
        List<FrameType> frames = FrontAnchorFitTest.frontFrames();
        assertEquals(4, frames.size(), frames.toString());
        for (FrameType frame : frames) {
            AccessoryAnchor anchor = AccessoryAnchor.of(frame, AccessoryMount.FRONT);
            AccessoryType recessed = recessed();
            AccessoryFit.Fit fit = AccessoryFit.measure(FrontAnchorFitTest.frameQuads(frame), AccessoryFit.boxQuads(anchor, recessed), anchor, recessed);
            if (!fit.ok()) {
                misses.add(frame.key() + ": " + fit.describe(anchor));
            }
            assertTrue(fit.wantedOverlapMm() >= fit.overlapMm(), frame.key());
        }
        assertTrue(misses.isEmpty(), "eingerücktes Zubehör nicht am Rahmen:\n" + String.join("\n", misses));
    }

    @Test
    void theMeasureSplitsWantedOverlapFromOverlapInFrontOfTheAnchor() {
        AccessoryType recessed = type(AccessoryDefinition.builder(id("small")).basics(PartBasics.of(50.0)).slot(AccessorySlot.FRONT)
                .size(20.0, 10.0, 20.0).frontalArea(0.001).recess(15.0).build());
        AccessoryType plate = type(AccessoryDefinition.builder(id("plate")).basics(PartBasics.of(50.0)).slot(AccessorySlot.FRONT)
                .size(20.0, 10.0, 20.0).frontalArea(0.001).build());
        AccessoryAnchor anchor = new AccessoryAnchor(0.0, 0.0, 0.0, 0.0, 0.0);
        float[] behind = AccessoryFit.boxQuads(new AccessoryAnchor(0.0, 0.0, 20.0, 0.0, 0.0), plate);
        AccessoryFit.Fit wanted = AccessoryFit.measure(behind, AccessoryFit.boxQuads(anchor, recessed), anchor, recessed);
        assertEquals(0.0F, wanted.gapMm(), 1.0E-4F);
        assertEquals(0.0F, wanted.overlapMm(), 1.0E-4F);
        assertEquals(5.0F, wanted.wantedOverlapMm(), 0.26F);
        assertTrue(wanted.ok(), wanted.describe(anchor));
        assertTrue(wanted.describe(anchor).contains("gewollt eingerückt"), wanted.describe(anchor));
        float[] ahead = AccessoryFit.boxQuads(new AccessoryAnchor(0.0, 0.0, 17.0, 0.0, 0.0), plate);
        AccessoryFit.Fit deep = AccessoryFit.measure(ahead, AccessoryFit.boxQuads(anchor, recessed), anchor, recessed);
        assertEquals(1.5F, deep.overlapMm(), 0.51F);
        AccessoryFit.Fit plain = AccessoryFit.measure(behind, AccessoryFit.boxQuads(anchor, plate), anchor, plate);
        assertEquals(0.0F, plain.overlapMm(), 1.0E-4F);
        assertEquals(0.0F, plain.wantedOverlapMm(), 0.0F);
        assertFalse(plain.describe(anchor).contains("gewollt"), plain.describe(anchor));
    }

    @Test
    void fiftyEightMillimetresReachTheBatteryUnderRace5AndTheBottomSlotAndTheNumbersAreKnown() throws IOException {
        float[] battery = batteryUnderRace5();
        AccessoryFit.Fit before = against(battery, FrameType.RACE_5, plain());
        assertEquals(0.0F, before.overlapMm(), 0.0F, before.describe(AccessoryAnchor.of(FrameType.RACE_5, AccessoryMount.FRONT)));
        assertEquals(23.0F, before.gapMm(), 0.5F);
        AccessoryFit.Fit after = against(battery, FrameType.RACE_5, recessed());
        assertEquals(0.0F, after.gapMm(), 0.0F);
        assertEquals(29.0F, after.wantedOverlapMm(), 1.0F, "Akku unter race_5 im eingerückten Quader");
        AccessoryFit.Fit touching = against(battery, FrameType.RACE_5, recessedBy(23.0));
        assertEquals(0.0F, touching.wantedOverlapMm(), 0.26F, "bis 23 mm bleibt der Akku frei");
        assertTrue(against(battery, FrameType.RACE_5, recessedBy(25.0)).wantedOverlapMm() > 0.5F, "ab 24 mm berührt der Quader den Akku");
        AccessoryType spear = spearMount();
        for (FrameType frame : FrontAnchorFitTest.frontFrames()) {
            float[] bottom = AccessoryFit.boxQuads(AccessoryAnchor.of(frame, AccessoryMount.BOTTOM), spear);
            AccessoryFit.Fit flat = against(bottom, frame, plain());
            AccessoryFit.Fit sunk = against(bottom, frame, recessed());
            assertEquals(11.5F, flat.overlapMm(), 0.6F, frame.key() + ": Speer-Halterung ohne Einrücken");
            assertEquals(28.2F, sunk.wantedOverlapMm(), 0.8F, frame.key() + ": Speer-Halterung im eingerückten Quader");
        }
    }
}
