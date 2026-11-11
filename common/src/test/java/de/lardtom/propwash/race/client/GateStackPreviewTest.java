package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateStackPreviewTest {
    private static final double EPS = 1.0E-6;
    private static final float[] STEPS = {90.0F, 45.0F, 15.0F, 5.0F};
    private static final float[] LOWER = {0.0F, 90.0F, 30.0F, 225.0F};

    private static GateGeometry lower(float heading) {
        return GatePlacement.turnedTo(GateGeometry.of(10.5, 64.0, -3.5, 0.0F, GateSize.MEDIUM), heading);
    }

    private static float relative(GateGeometry upper, GateGeometry lower) {
        return GateTilt.wrap(upper.heading() - lower.heading());
    }

    private static void assertTurn(float expected, float actual, String where) {
        assertEquals(0.0F, GateTilt.wrap(expected - actual), 0.05F, where + ": " + actual + "° statt " + expected + "°");
    }

    private static GateGeometry ghost(GateGeometry lower, float playerYaw, GatePlacement.Preference preference) {
        return GateStacking.stackFor(lower, lower.yawDegrees(), GateSize.SMALL, playerYaw, preference);
    }

    @Test
    void theGhostTurnsWithTheRotateKeyAndShowsTheFootOfTheStackedGate() {
        for (float heading : LOWER) {
            GateGeometry lower = lower(heading);
            for (float step : STEPS) {
                GatePlacement.Preference preference = GatePlacement.Preference.DEFAULT;
                int presses = Math.min(Math.round(360.0F / step), 12);
                for (int press = 0; press <= presses; press++) {
                    String where = "unten " + heading + "°, Schritt " + step + "°, " + press + "× gedrückt";
                    float turn = GateTilt.wrap(press * step);
                    GateGeometry ghost = ghost(lower, lower.yawDegrees(), preference);
                    assertTurn(turn, GateStacking.placementTurn(preference), where + ": Vorliebe");
                    assertTurn(turn, relative(ghost, lower), where + ": Geist zum unteren Gate");
                    boolean half = GateStacking.halfTurn(turn);
                    GateStacking.Base base = GateStacking.stackBase(ghost, lower);
                    assertEquals(half ? GateStacking.Base.STACKED : GateStacking.Base.MOUNTED, base, where + ": Fuß des Geists");
                    assertEquals(half ? GateStacking.seat(lower) : lower.y() + lower.height(), ghost.y(), EPS, where + ": Sitzhöhe");
                    assertEquals(lower.x(), ghost.x(), EPS, where + ": Mitte x");
                    assertEquals(lower.z(), ghost.z(), EPS, where + ": Mitte z");
                    assertTrue(GateStacking.restsOn(ghost, lower), where + ": Geist liegt auf dem Gate");
                    assertEquals(base, GateStacking.base(List.of(lower, ghost), 1), where + ": gesetztes Gate bekommt den Fuß des Geists");
                    GatePlacement.Preference next = GateKeyRoute.stackPreference(preference, false, step);
                    assertEquals(preference.turnedBy(GateMount.Surface.FLOOR, step), next, where + ": Schritt wie am Boden");
                    preference = next;
                }
            }
        }
    }

    @Test
    void lookingFromBehindFlipsTheGhostLikeTheClick() {
        for (float heading : LOWER) {
            GateGeometry lower = lower(heading);
            GatePlacement.Preference preference = GateKeyRoute.stackPreference(GatePlacement.Preference.DEFAULT, false, 45.0F);
            GateGeometry front = ghost(lower, lower.yawDegrees(), preference);
            GateGeometry back = ghost(lower, lower.yawDegrees() + 180.0F, preference);
            assertTurn(45.0F, relative(front, lower), "unten " + heading + "°, von vorn");
            assertTurn(-135.0F, relative(back, lower), "unten " + heading + "°, von hinten");
            assertEquals(GateStacking.Base.MOUNTED, GateStacking.stackBase(back, lower));
        }
    }

    @Test
    void sneakingSwitchesTheMountLikeOnTheFloorAndKeepsWallAndCeiling() {
        GatePlacement.Preference start = new GatePlacement.Preference(GateMount.STANDING, 1, GateMount.WALL_SIDE, 1, GateMount.CEILING_FLAT, 1, 15.0F,
                0.0F, 2);
        GatePlacement.Preference lid = GateKeyRoute.stackPreference(start, true, 45.0F);
        assertEquals(start.nextMount(GateMount.Surface.FLOOR), lid);
        assertEquals(GateMount.FLOOR_FLAT, lid.floor());
        assertEquals(0.0F, GateStacking.placementTurn(lid), 0.0F, "liegend legt sich der Deckel nach dem Ring, nicht nach der Drehung");
        GatePlacement.Preference back = GateKeyRoute.stackPreference(lid, true, 45.0F);
        assertEquals(GateMount.STANDING, back.floor());
        assertEquals(lid.nextMount(GateMount.Surface.FLOOR), back);
        GatePlacement.Preference turned = GateKeyRoute.stackPreference(start, false, 15.0F);
        for (GatePlacement.Preference p : List.of(lid, back, turned)) {
            assertEquals(start.wall(), p.wall());
            assertEquals(start.wallTurn(), p.wallTurn());
            assertEquals(start.ceiling(), p.ceiling());
            assertEquals(start.ceilingTurn(), p.ceilingTurn());
            assertEquals(start.variant(), p.variant());
        }
        assertTurn(GateTilt.wrap(GateStacking.placementTurn(start) + 15.0F), GateStacking.placementTurn(turned), "Feinschritt");
    }

    @Test
    void theRotateKeyLeavesTheLidAloneBecauseTheRingSetsItsDirection() {
        GatePlacement.Preference start = new GatePlacement.Preference(GateMount.STANDING, 1, GateMount.WALL_SIDE, 1, GateMount.CEILING_FLAT, 1, 15.0F,
                0.0F, 2);
        GatePlacement.Preference lid = GateKeyRoute.stackPreference(start, true, 45.0F);
        GatePlacement.Preference upwards = lid.with(GateMount.FLOOR_FLAT, 1);
        for (GatePlacement.Preference flat : List.of(lid, upwards)) {
            for (float step : STEPS) {
                assertEquals(flat, GateKeyRoute.stackPreference(flat, false, step), "liegend, Stellung " + flat.floorTurn() + ", Schritt " + step
                        + "°: die Dreh-Taste ändert am Deckel nichts");
            }
            assertEquals(GateMount.STANDING, GateKeyRoute.stackPreference(flat, true, 45.0F).floor(), "Schleichen + Taste wechselt weiter die Lage");
        }
    }

    private static GateGeometry ringGate(double x, double z, float yaw) {
        return GateGeometry.of(x, 64.0, z, yaw, GateSize.MEDIUM);
    }

    private static GateGeometry lidOnRing() {
        GateGeometry south = ringGate(0.5, 0.5, 0.0F);
        GateGeometry east = GateStacking.snap(ringGate(2.5, 2.5, 90.0F), List.of(south));
        GateGeometry west = GateStacking.snap(ringGate(-1.5, 2.5, 90.0F), List.of(south));
        GateGeometry north = GateStacking.snap(ringGate(0.5, 4.5, 0.0F), List.of(east, west));
        GateStacking.Lid lid = GateStacking.lid(south, List.of(east, west, north), GateSize.MEDIUM);
        assertEquals(GateStacking.LidRefusal.NONE, lid.refusal());
        return lid.geometry();
    }

    @Test
    void theLidHintShowsTheDirectionOfTheLidAndOnlyTheMountKey() {
        GateGeometry lid = lidOnRing();
        assertEquals("down", GateMounting.directionKey(lid), "Deckel wird von oben nach unten durchflogen");
        GatePlacement.Preference lying = GatePlacement.Preference.DEFAULT.nextMount(GateMount.Surface.FLOOR);
        GatePreview.Target target = new GatePreview.Target(lid, null, true, GateMount.Surface.FLOOR, true, false, null, true);
        for (GatePlacement.Preference preference : List.of(lying, lying.with(GateMount.FLOOR_FLAT, 1))) {
            assertEquals(GateMounting.describe(GateMount.FLOOR_FLAT, 0), GatePreview.status(target, preference),
                    "Stellung " + preference.floorTurn() + ": Hinweis zeigt die Richtung des Deckels");
            assertEquals(GateMounting.describe(GateMount.FLOOR_FLAT, 0), GatePreview.status(target,
                    GateKeyRoute.stackPreference(preference, false, 45.0F)), "Stellung " + preference.floorTurn() + ", nach der Dreh-Taste");
        }
        GatePreview.Target blocked = new GatePreview.Target(lid, null, false, GateMount.Surface.FLOOR, true, false, null, true);
        assertEquals(Component.translatable("propwash.race.mount.preview_blocked", GateMounting.describe(GateMount.FLOOR_FLAT, 0)),
                GatePreview.status(blocked, lying.with(GateMount.FLOOR_FLAT, 1)));
        assertTrue(GatePreview.keys(target).getContents() instanceof TranslatableContents keys && keys.getKey().equals("propwash.race.lid.hint"),
                "Tasten-Zeile am Deckel nur mit dem Lagewechsel");
    }

    @Test
    void aFlatGateOnTheFloorStillShowsThePreference() {
        GateGeometry flat = GateGeometry.of(0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM, GateMount.FLOOR_FLAT, true);
        GatePreview.Target floor = new GatePreview.Target(flat, null, true, GateMount.Surface.FLOOR, true);
        GatePlacement.Preference upwards = GatePlacement.Preference.DEFAULT.nextMount(GateMount.Surface.FLOOR).with(GateMount.FLOOR_FLAT, 1);
        assertEquals(GateMounting.describe(GateMount.FLOOR_FLAT, 1), GatePreview.status(floor, upwards));
        assertTrue(GatePreview.keys(floor).getContents() instanceof TranslatableContents keys && keys.getKey().equals("propwash.race.mount.hint"));
    }
}
