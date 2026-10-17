package de.lardtom.propwash.guide;

import de.lardtom.propwash.build.PropRepair;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.DronePart;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneProjection;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scenes.BatteryScene;
import de.lardtom.propwash.guide.scenes.BindScene;
import de.lardtom.propwash.guide.scenes.ConfiguratorScene;
import de.lardtom.propwash.guide.scenes.FlightViewScene;
import de.lardtom.propwash.guide.scenes.GateFreeScene;
import de.lardtom.propwash.guide.scenes.RaceScene;
import de.lardtom.propwash.guide.scenes.RecoveryScene;
import de.lardtom.propwash.guide.scenes.RepairScene;
import de.lardtom.propwash.guide.scenes.RescueScene;
import de.lardtom.propwash.guide.scenes.SceneKit;
import de.lardtom.propwash.guide.scenes.SurvivalScene;
import de.lardtom.propwash.guide.scenes.VideoScene;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightSchoolStoriesTest {
    private static Scene scene(String id) {
        Scene scene = GuideScenes.byId(id);
        assertNotNull(scene, id);
        return scene;
    }

    private static int step(Scene scene, String name) {
        for (int i = 0; i < scene.stepCount(); i++) {
            if (scene.steps().get(i).name().equals(name)) {
                return i;
            }
        }
        throw new AssertionError(scene.id() + " ohne Schritt " + name);
    }

    private static ActorState actor(SceneState state, String id) {
        ActorState actor = state.actor(id);
        assertNotNull(actor, id + " fehlt bei " + state.time());
        return actor;
    }

    private static float angleBetween(float a, float b) {
        return Math.abs(SceneKit.wrap(a - b));
    }

    @Test
    void bindingEndsWithAFpvReadyPilot() {
        Scene scene = scene(BindScene.ID);
        SceneState afterRemote = SceneEvaluator.evaluate(scene, scene.stepEnd(0) - 1.0F);
        assertEquals(SceneKit.REMOTE, actor(afterRemote, BindScene.PILOT).handItem());
        SceneState afterWear = SceneEvaluator.evaluate(scene, scene.stepEnd(1) - 1.0F);
        assertEquals(SceneKit.GOGGLES_DIGITAL, actor(afterWear, BindScene.PILOT).headItem());
        assertEquals(SceneKit.REMOTE, actor(afterWear, BindScene.PILOT).handItem());
        float matchStart = scene.stepEnd(1);
        assertEquals(SceneKit.GOGGLES_ANALOG, actor(SceneEvaluator.evaluate(scene, matchStart + 80.0F), BindScene.PILOT).headItem());
        assertEquals("", actor(SceneEvaluator.evaluate(scene, matchStart + 120.0F), BindScene.PILOT).headItem());
        assertEquals(SceneKit.GOGGLES_DIGITAL, actor(SceneEvaluator.evaluate(scene, scene.stepEnd(2) - 1.0F), BindScene.PILOT).headItem());
        SceneState end = SceneEvaluator.evaluate(scene, scene.duration());
        ActorState pilot = actor(end, BindScene.PILOT);
        assertEquals(SceneKit.GOGGLES_DIGITAL, pilot.headItem());
        assertEquals(SceneKit.REMOTE, pilot.handItem());
        ActorState drone = actor(end, BindScene.DRONE);
        assertTrue(drone.armed());
        assertTrue(drone.position().y() > BindScene.DRONE_POS.y() + 0.8F);
    }

    @Test
    void lineOfSightViewFollowsTheDrone() {
        Scene scene = scene(FlightViewScene.ID);
        int los = step(scene, "los");
        SceneState fpv = SceneEvaluator.evaluate(scene, scene.stepStart(los) - 2.0F);
        assertEquals(SceneKit.GOGGLES_DIGITAL, actor(fpv, FlightViewScene.PILOT).headItem());
        float start = scene.stepStart(los) + FlightViewScene.LOOP_START;
        for (float t = start + 10.0F; t < start + FlightViewScene.LOOP_LENGTH; t += 5.0F) {
            SceneState state = SceneEvaluator.evaluate(scene, t);
            ActorState pilot = actor(state, FlightViewScene.PILOT);
            ActorState drone = actor(state, FlightViewScene.DRONE);
            assertEquals("", pilot.headItem());
            float wanted = SceneKit.pilotYaw(pilot.position(), drone.position());
            assertTrue(angleBetween(pilot.yaw(), wanted) < 25.0F, "Blick bei " + t + ": " + pilot.yaw() + " statt " + wanted);
            assertTrue(Math.abs(pilot.headPitch() - SceneKit.headPitch(pilot.position(), drone.position())) < 20.0F, "Kopf bei " + t);
        }
        SceneState end = SceneEvaluator.evaluate(scene, scene.duration());
        ActorState drone = actor(end, FlightViewScene.DRONE);
        assertFalse(drone.armed());
        assertEquals(1.0F, drone.position().y(), 1.0E-3F);
    }

    @Test
    void batteryLeavesAndReturnsAndChargerTurnsGreen() {
        Scene scene = scene(BatteryScene.ID);
        SceneState taken = SceneEvaluator.evaluate(scene, scene.stepEnd(step(scene, "menu")) - 1.0F);
        ActorState drone = actor(taken, BatteryScene.DRONE);
        assertFalse(drone.part(DronePart.BATTERY).visible());
        assertTrue(drone.part(DronePart.FRAME).attached());
        assertFalse(drone.armed());
        SceneState swapped = SceneEvaluator.evaluate(scene, scene.stepEnd(step(scene, "swap")) - 1.0F);
        assertTrue(actor(swapped, BatteryScene.DRONE).fullyAssembled());
        SceneState charging = SceneEvaluator.evaluate(scene, scene.stepStart(step(scene, "charger")) + 60.0F);
        SceneState.BlockView block = charging.block(BatteryScene.CHARGER_X, 1, BatteryScene.CHARGER_Z);
        assertNotNull(block);
        assertEquals("charging", block.block().properties().get("state"));
        SceneState end = SceneEvaluator.evaluate(scene, scene.duration());
        assertEquals("done", end.block(BatteryScene.CHARGER_X, 1, BatteryScene.CHARGER_Z).block().properties().get("state"));
        assertNotNull(end.block(BatteryScene.CHARGER_X + 1, 1, BatteryScene.CHARGER_Z));
    }

    @Test
    void freeGateTurnsTiltsRollsAndGrowsAsTheTextSays() {
        Scene scene = scene(GateFreeScene.ID);
        float sizeStart = scene.stepStart(step(scene, "size"));
        ActorState diamond = actor(SceneEvaluator.evaluate(scene, sizeStart - 1.0F), GateFreeScene.GATE);
        assertEquals(GateFreeScene.DIAGONAL_YAW, diamond.yaw(), 1.0E-3F);
        assertEquals(0.0F, diamond.pitch(), 1.0E-3F);
        assertEquals(GateFreeScene.ROLL, diamond.roll(), 1.0E-3F);
        float angles = scene.stepStart(step(scene, "angles"));
        ActorState tilted = actor(SceneEvaluator.evaluate(scene, angles + 90.0F), GateFreeScene.GATE);
        assertEquals(GateFreeScene.PITCH, tilted.pitch(), 1.0E-3F);
        SceneState end = SceneEvaluator.evaluate(scene, scene.stepEnd(step(scene, "step")) - 1.0F);
        ActorState wide = actor(end, GateFreeScene.WIDE);
        ActorSpec.Gate spec = (ActorSpec.Gate) wide.spec();
        assertEquals(GateFreeScene.WIDE_OPENING, spec.openingWidth(), 1.0E-6F);
        assertEquals(GateFreeScene.YAW + 45.0F, wide.yaw(), 1.0E-3F);
        GateGeometry geometry = GateGeometry.of(0.0, 64.0, 0.0, diamond.yaw(), GateSize.MEDIUM, GateMount.STANDING, false, GateSize.MEDIUM.width(),
                GateSize.MEDIUM.height(), new GateTilt(0.0F, diamond.pitch(), diamond.roll()));
        double[] lowest = {Double.POSITIVE_INFINITY};
        geometry.bounds((minX, minY, minZ, maxX, maxY, maxZ) -> lowest[0] = minY);
        assertEquals(64.0, lowest[0], 1.0E-6, "die Raute steht mit der Spitze auf dem Boden");
    }

    @Test
    void raceLapPassesEveryGateForwardAndTheGreenLightMovesOn() {
        Scene scene = scene(RaceScene.ID);
        int fly = step(scene, "fly");
        float base = scene.stepStart(fly);
        int[] order = {0, 1, 2, 0};
        for (int pass = 0; pass < order.length; pass++) {
            int gate = order[pass];
            float t = base + RaceScene.passTime(pass);
            ScenePos before = actor(SceneEvaluator.evaluate(scene, t - 2.0F), RaceScene.DRONE).position();
            ScenePos after = actor(SceneEvaluator.evaluate(scene, t + 2.0F), RaceScene.DRONE).position();
            ScenePos pos = RaceScene.GATE_POS[gate];
            GateGeometry geometry = new GateGeometry(pos.x(), pos.y(), pos.z(), RaceScene.GATE_YAW[gate], RaceScene.GATE_WIDTH[gate],
                    RaceScene.GATE_HEIGHT[gate], 0.5, 0.5);
            double crossing = geometry.crossing(before.x(), before.y(), before.z(), after.x(), after.y(), after.z(), true);
            assertNotEquals(GateGeometry.NO_CROSSING, crossing, "Durchflug " + pass + " durch Gate " + (gate + 1));
            SceneState lit = SceneEvaluator.evaluate(scene, t + 1.0F);
            int next = pass + 1 < order.length ? order[pass + 1] : 1;
            for (int g = 0; g < RaceScene.GATES.length; g++) {
                assertEquals(g == next, actor(lit, RaceScene.GATES[g]).lit(), "Licht von Gate " + (g + 1) + " nach Durchflug " + pass);
            }
        }
        for (int g = 1; g < RaceScene.GATES.length; g++) {
            assertEquals(RaceScene.GATE_YAW[0], RaceScene.GATE_YAW[g], 1.0E-6F, "alle Gates gleich ausgerichtet");
            assertTrue(RaceScene.GATE_POS[g].distance(RaceScene.GATE_POS[g - 1]) >= 4.0F, "Gates " + g + " und " + (g + 1) + " stehen auseinander");
        }
        SceneState start = SceneEvaluator.evaluate(scene, base - 1.0F);
        assertTrue(actor(start, RaceScene.GATES[0]).lit());
        assertTrue(actor(start, RaceScene.DRONE).armed());
    }

    @Test
    void stackedGatesFormALadderAndADoubleGate() {
        Scene scene = scene(RaceScene.ID);
        SceneState end = SceneEvaluator.evaluate(scene, scene.stepEnd(step(scene, "stack")) - 1.0F);
        GateGeometry below = geometry(end, RaceScene.GATES[1]);
        for (String id : RaceScene.LADDER) {
            GateGeometry upper = geometry(end, id);
            assertTrue(GateStacking.restsOn(upper, below), id + " sitzt auf dem Gate darunter");
            assertNotEquals(below.yawDegrees(), upper.yawDegrees(), 1.0E-3F, id + " zeigt im Zickzack zurück");
            below = upper;
        }
        GateGeometry lower = geometry(end, RaceScene.GATES[2]);
        GateGeometry upper = geometry(end, RaceScene.DOUBLE);
        assertTrue(GateStacking.restsOn(upper, lower));
        assertEquals(lower.yawDegrees(), upper.yawDegrees(), 1.0E-3F);
        assertNull(end.actor(RaceScene.DRONE));
    }

    private static GateGeometry geometry(SceneState state, String id) {
        ActorState gate = actor(state, id);
        ScenePos pos = gate.position();
        ActorSpec.Gate spec = (ActorSpec.Gate) gate.spec();
        return GateGeometry.of(pos.x(), pos.y(), pos.z(), gate.yaw(), GateSize.byKey(spec.size()), GateMount.byKey(spec.mount()), spec.flipped());
    }

    @Test
    void theDiveGoesDownThroughTheWallGateAndTheHangingGateReachesTheCeiling() {
        Scene scene = scene(RaceScene.ID);
        float base = scene.stepStart(step(scene, "mount"));
        SceneState end = SceneEvaluator.evaluate(scene, scene.stepEnd(step(scene, "mount")) - 1.0F);
        GateGeometry dive = geometry(end, RaceScene.DIVE_GATE);
        assertEquals(GateMount.WALL_DIVE, dive.mount());
        assertEquals(-1.0, dive.normalY(), 1.0E-9);
        assertEquals(RaceScene.WALL_Z + 1.0, dive.z(), 1.0E-6, "Tauchtor liegt an der Wand");
        GateGeometry hanging = geometry(end, RaceScene.HANGING_GATE);
        assertEquals(GateMount.HANGING, hanging.mount());
        assertEquals(RaceScene.WALL_TOP, hanging.y() + hanging.topExtent(), 1.0E-6, "Gurte reichen bis an die Decke");
        assertNull(end.actor(RaceScene.GATES[0]));
        assertNull(end.actor(RaceScene.LADDER[0]));
        int crossings = 0;
        ScenePos last = null;
        for (float t = base + RaceScene.DIVE_START_TIME; t <= base + RaceScene.DIVE_START_TIME + RaceScene.DIVE_LENGTH; t += 0.5F) {
            ScenePos now = actor(SceneEvaluator.evaluate(scene, t), RaceScene.DIVER).position();
            if (last != null) {
                if (dive.crossing(last.x(), last.y(), last.z(), now.x(), now.y(), now.z(), true) != GateGeometry.NO_CROSSING) {
                    crossings++;
                }
                assertEquals(GateGeometry.NO_CROSSING, dive.crossing(last.x(), last.y(), last.z(), now.x(), now.y(), now.z(), false),
                        "kein Durchflug gegen die Richtung");
            }
            last = now;
        }
        assertEquals(1, crossings, "die Drohne taucht genau einmal durch das Tauchtor");
    }

    @Test
    void theDigitalDroneRejectsAnalogGoggles() {
        Scene scene = scene(VideoScene.ID);
        int mismatch = step(scene, "mismatch");
        SceneState rejected = SceneEvaluator.evaluate(scene, scene.stepStart(mismatch) + 50.0F);
        ActorState barrier = actor(rejected, "no");
        ActorState digital = actor(rejected, VideoScene.DIGITAL);
        assertEquals(SceneKit.BARRIER, ((ActorSpec.Item) barrier.spec()).itemId());
        assertTrue(Math.abs(barrier.position().x() - digital.position().x()) < 0.01F);
        assertEquals(SceneKit.GOGGLES_ANALOG, actor(rejected, VideoScene.PILOT).handItem());
        SceneState end = SceneEvaluator.evaluate(scene, scene.duration());
        assertEquals(SceneKit.GOGGLES_CREATIVE, actor(end, VideoScene.PILOT).headItem());
        assertNull(end.actor("no"));
    }

    @Test
    void sticksMatchTheDroneAndRatesDifferInSpeed() {
        Scene scene = scene(ConfiguratorScene.ID);
        SceneState rolling = SceneEvaluator.evaluate(scene, 72.0F);
        assertTrue(actor(rolling, ConfiguratorScene.RADIO).rightX() > 0.5F);
        assertTrue(actor(rolling, ConfiguratorScene.DRONE).roll() > 10.0F);
        SceneState pitching = SceneEvaluator.evaluate(scene, 106.0F);
        assertTrue(actor(pitching, ConfiguratorScene.RADIO).rightY() > 0.5F);
        assertTrue(actor(pitching, ConfiguratorScene.DRONE).pitch() < -10.0F);
        SceneState yawing = SceneEvaluator.evaluate(scene, 140.0F);
        assertTrue(actor(yawing, ConfiguratorScene.RADIO).leftX() > 0.5F);
        assertTrue(actor(yawing, ConfiguratorScene.DRONE).yaw() > 20.0F);
        int rates = step(scene, "rates");
        SceneState flipping = SceneEvaluator.evaluate(scene, scene.stepStart(rates) + 52.0F);
        float slow = actor(flipping, ConfiguratorScene.DRONE).roll();
        float fast = actor(flipping, ConfiguratorScene.SECOND).roll();
        assertTrue(fast > slow * 1.8F, "hohe Rate dreht nicht schneller: " + slow + " / " + fast);
        SceneState modes = SceneEvaluator.evaluate(scene, scene.stepStart(step(scene, "modes")) + 145.0F);
        assertTrue(actor(modes, ConfiguratorScene.DRONE).roll() > 40.0F, "Acro hält den Winkel nicht");
        assertEquals(0.0F, actor(modes, ConfiguratorScene.RADIO).rightX(), 1.0E-3F);
        assertTrue(actor(modes, ConfiguratorScene.RADIO).spec() instanceof ActorSpec.Remote);
        SceneState end = SceneEvaluator.evaluate(scene, scene.duration());
        assertFalse(actor(end, ConfiguratorScene.DRONE).armed());
        assertEquals(1.0F, actor(end, ConfiguratorScene.DRONE).position().y(), 1.0E-3F);
    }

    @Test
    void rescueClimbsFliesHomeAndLands() {
        Scene scene = scene(RescueScene.ID);
        int home = step(scene, "return");
        float highest = 0.0F;
        for (float t = scene.stepStart(home); t < scene.stepEnd(home); t += 2.0F) {
            highest = Math.max(highest, actor(SceneEvaluator.evaluate(scene, t), RescueScene.DRONE).position().y());
        }
        assertTrue(highest >= RescueScene.ALTITUDE - 0.05F, "Rückflughöhe nicht erreicht: " + highest);
        SceneState end = SceneEvaluator.evaluate(scene, scene.duration());
        ActorState drone = actor(end, RescueScene.DRONE);
        assertFalse(drone.armed());
        assertTrue(drone.position().distance(RescueScene.HOME) < 0.01F);
        assertNotNull(end.block(0, 2, 0));
    }

    @Test
    void crashedDroneIsFlippedAndArmedAgain() {
        Scene scene = scene(RecoveryScene.ID);
        SceneState crashed = SceneEvaluator.evaluate(scene, scene.stepEnd(step(scene, "crash")) - 1.0F);
        ActorState wreck = actor(crashed, RecoveryScene.DRONE);
        assertFalse(wreck.armed());
        assertEquals(180.0F, Math.abs(wreck.roll()), 1.0F);
        assertEquals(RecoveryScene.WRECK.y(), wreck.position().y(), 0.01F);
        int flip = step(scene, "flip");
        SceneState upright = SceneEvaluator.evaluate(scene, scene.stepStart(flip) + 60.0F);
        assertEquals(0.0F, SceneKit.wrap(actor(upright, RecoveryScene.DRONE).roll()), 1.0F);
        assertFalse(actor(upright, RecoveryScene.DRONE).armed());
        SceneState flying = SceneEvaluator.evaluate(scene, scene.stepEnd(flip) - 1.0F);
        assertTrue(actor(flying, RecoveryScene.DRONE).armed());
        int turtle = step(scene, "turtle");
        assertEquals(flip + 1, turtle);
        ActorState capsized = actor(SceneEvaluator.evaluate(scene, scene.stepStart(turtle) + 30.0F), RecoveryScene.DRONE);
        assertFalse(capsized.armed());
        assertEquals(180.0F, Math.abs(SceneKit.wrap(capsized.roll())), 1.0F);
        ActorState turtleArmed = actor(SceneEvaluator.evaluate(scene, scene.stepStart(turtle) + 70.0F), RecoveryScene.DRONE);
        assertTrue(turtleArmed.armed(), "Turtle-Modus braucht scharf geschaltete Motoren");
        ActorState flipped = actor(SceneEvaluator.evaluate(scene, scene.stepStart(turtle) + 120.0F), RecoveryScene.DRONE);
        assertFalse(flipped.armed());
        assertEquals(0.0F, SceneKit.wrap(flipped.roll()), 1.0F);
        assertEquals(RecoveryScene.WRECK.y(), flipped.position().y(), 0.01F);
        assertTrue(actor(SceneEvaluator.evaluate(scene, scene.stepEnd(turtle) - 1.0F), RecoveryScene.DRONE).armed());
        int air = step(scene, "air");
        assertEquals(turtle + 1, air);
        ActorState falling = actor(SceneEvaluator.evaluate(scene, scene.stepStart(air) + 30.0F), RecoveryScene.DRONE);
        assertFalse(falling.armed());
        assertTrue(Math.abs(SceneKit.wrap(falling.roll())) > 60.0F, "Drohne kippt beim Fallen nicht");
        assertTrue(falling.position().y() < RecoveryScene.AIR_START.y(), "Drohne fällt nicht");
        ActorState caught = actor(SceneEvaluator.evaluate(scene, scene.stepEnd(air) - 1.0F), RecoveryScene.DRONE);
        assertTrue(caught.armed());
        assertEquals(0.0F, SceneKit.wrap(caught.roll()), 1.0F);
        assertTrue(caught.position().y() > RecoveryScene.AIR_LOW.y() + 0.5F, "Drohne wird in der Luft nicht abgefangen");
        SceneState end = SceneEvaluator.evaluate(scene, scene.duration());
        ActorState lost = actor(end, RecoveryScene.DRONE);
        assertFalse(lost.armed());
        assertTrue(lost.position().distance(RecoveryScene.LOST) < 0.01F);
        assertEquals("minecraft:red_concrete", end.block(3, 0, 2).block().id());
    }

    @Test
    void survivalEndsInAWorkshop() {
        Scene scene = scene(SurvivalScene.ID);
        SceneState crafted = SceneEvaluator.evaluate(scene, scene.stepEnd(0) - 1.0F);
        assertNotNull(crafted.actor(SurvivalScene.RESULT));
        assertNotNull(crafted.block(-1, 1, 0));
        SceneState looted = SceneEvaluator.evaluate(scene, scene.stepStart(step(scene, "chests")) + 80.0F);
        assertNotNull(looted.actor(SurvivalScene.CHEST));
        for (int i = 0; i < SurvivalScene.LOOT.length; i++) {
            assertTrue(actor(looted, "loot" + i).position().y() > SurvivalScene.CHEST_POS.y() + 0.5F);
        }
        SceneState end = SceneEvaluator.evaluate(scene, scene.duration());
        assertNull(end.block(-1, 1, 0));
        assertEquals(SceneKit.WORKBENCH, end.block(0, 1, 1).block().id());
        assertEquals(SceneKit.CHARGER, end.block(1, 1, -1).block().id());
        assertTrue(actor(end, SurvivalScene.DRONE).armed());
    }

    @Test
    void survivalCatalogIsATidyRowWithTheGogglesCalledOut() {
        Scene scene = scene(SurvivalScene.ID);
        SceneState book = SceneEvaluator.evaluate(scene, scene.stepStart(step(scene, "book")) + 70.0F);
        SceneProjection projection = new SceneProjection(book.camera(), 100.0F, 0.0F, 0.0F);
        float width = 2.0F * 0.28F * SurvivalScene.CATALOG_SCALE * 100.0F * book.camera().zoom();
        float previous = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < SurvivalScene.CATALOG.length; i++) {
            ActorState item = actor(book, "catalog" + i);
            float[] screen = projection.project(item.position());
            assertTrue(screen[0] - previous > width * 1.2F, "Teil " + i + " überlappt seinen Nachbarn");
            previous = screen[0];
        }
        assertEquals(SceneKit.GOGGLES_CREATIVE, SurvivalScene.CATALOG[SurvivalScene.CATALOG.length - 1]);
        SceneState.CalloutView goggles = book.callouts().stream()
                .filter(callout -> callout.textKey().endsWith("no_creative")).findFirst().orElseThrow();
        ScenePos creative = actor(book, SurvivalScene.CREATIVE_GOGGLES_ACTOR).position();
        assertTrue(goggles.anchor().distance(creative) < 0.3F, "Hinweis zeigt auf die Kreativ-Brille");
    }

    @Test
    void crashBreaksPropsAndFieldRepairRestoresThem() {
        Scene scene = scene(RepairScene.ID);
        int crash = step(scene, "crash");
        SceneState before = SceneEvaluator.evaluate(scene, scene.stepStart(crash) + RepairScene.CRASH_AT - 2.0F);
        for (int slot = 0; slot < 4; slot++) {
            assertEquals(1.0F, actor(before, RepairScene.DRONE).propCondition(slot));
        }
        SceneState crashed = SceneEvaluator.evaluate(scene, scene.stepEnd(crash) - 1.0F);
        ActorState wreck = actor(crashed, RepairScene.DRONE);
        assertFalse(wreck.armed());
        assertEquals(0.0F, wreck.propCondition(0));
        assertTrue(wreck.propCondition(1) < 0.7F);
        int swap = step(scene, "swap");
        SceneState swapping = SceneEvaluator.evaluate(scene, scene.stepStart(swap) + RepairScene.SWAP_AT + 4.0F);
        ActorState pilot = actor(swapping, RepairScene.PILOT);
        assertTrue(pilot.crouching());
        assertEquals(RepairScene.PROPS, pilot.handItem());
        SceneState repaired = SceneEvaluator.evaluate(scene, scene.stepEnd(swap) - 1.0F);
        ActorState fixed = actor(repaired, RepairScene.DRONE);
        assertTrue(fixed.fullyAssembled());
        for (int slot = 0; slot < 4; slot++) {
            assertEquals(1.0F, fixed.propCondition(slot));
        }
        SceneState end = SceneEvaluator.evaluate(scene, scene.duration());
        assertTrue(actor(end, RepairScene.DRONE).armed());
        assertNotNull(end.block(RepairScene.BENCH_X, 1, RepairScene.BENCH_Z));
    }

    @Test
    void oldPropsFromTheCrashCombineIntoOne() {
        Scene scene = scene(RepairScene.ID);
        int swap = step(scene, "swap");
        int combine = step(scene, "combine");
        assertEquals(swap + 1, combine);
        assertEquals(RepairScene.AFTER_CRASH[1], RepairScene.OLD_PROPS[0]);
        assertEquals(RepairScene.AFTER_CRASH[2], RepairScene.OLD_PROPS[1]);
        SceneState before = SceneEvaluator.evaluate(scene, scene.stepStart(combine) + RepairScene.MERGE_AT - 20.0F);
        assertNotNull(before.block(RepairScene.CRAFT_X, 1, RepairScene.CRAFT_Z));
        assertEquals(RepairScene.OLD_PROPS[0], actor(before, RepairScene.OLD_PROP_A).propCondition(0));
        assertEquals(RepairScene.OLD_PROPS[1], actor(before, RepairScene.OLD_PROP_B).propCondition(0));
        assertNull(before.actors().get(RepairScene.REPAIRED_PROP));
        SceneState after = SceneEvaluator.evaluate(scene, scene.stepStart(combine) + RepairScene.MERGE_AT + 20.0F);
        assertEquals(PropRepair.combine(RepairScene.OLD_PROPS[0], RepairScene.OLD_PROPS[1]), actor(after, RepairScene.REPAIRED_PROP).propCondition(0));
        assertEquals(1.0F, RepairScene.REPAIRED);
        assertTrue(after.visibleActors().stream().noneMatch(a -> a.id().equals(RepairScene.OLD_PROP_A) || a.id().equals(RepairScene.OLD_PROP_B)));
        SceneState bench = SceneEvaluator.evaluate(scene, scene.stepStart(combine + 1) + 10.0F);
        assertNull(bench.block(RepairScene.CRAFT_X, 1, RepairScene.CRAFT_Z));
    }
}
