package de.lardtom.propwash.accessory.hang;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.parts.PartJson;
import de.lardtom.propwash.webexport.PartData;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HangsStraightExportTest {
    private static final double EPS = 1.0E-9;

    private record Web(double tilt, double stretch, double[] origin, double[][] corners, double[] center, double[][] leds) {
    }

    private static AccessoryDefinition.Builder addon(String path, AccessorySlot slot, double width, double height, double depth) {
        return AccessoryDefinition.builder(Identifier.fromNamespaceAndPath("hangexport", path)).basics(PartBasics.of(18.0)).slot(slot)
                .size(width, height, depth).frontalArea(0.0004);
    }

    private static List<AccessoryDefinition> addons() {
        return List.of(
                addon("spear_holder", AccessorySlot.BOTTOM, 24.0, 10.0, 60.0).hangsStraight(true).build(),
                addon("spear_holder_tilted", AccessorySlot.BOTTOM, 24.0, 10.0, 60.0).build(),
                addon("holder_recessed", AccessorySlot.BOTTOM, 24.0, 18.0, 90.0).hangsStraight(true).recess(20.0).drop(3.0).build(),
                addon("holder_recessed_tilted", AccessorySlot.BOTTOM, 24.0, 18.0, 90.0).recess(20.0).drop(3.0).build(),
                addon("booster", AccessorySlot.TOP, 18.0, 14.0, 36.0).hangsStraight(true).recess(6.0).build(),
                addon("booster_tilted", AccessorySlot.TOP, 18.0, 14.0, 36.0).recess(6.0).drop(2.0).build(),
                addon("led_bar", AccessorySlot.BOTTOM, 40.0, 4.0, 6.0).leds(8).hangsStraight(true).drop(1.0).build(),
                addon("dispenser", AccessorySlot.FRONT, 62.0, 58.0, 64.0).hangsStraight(true).recess(58.0).drop(1.5).build());
    }

    private static List<AccessoryDefinition> accessories() {
        List<AccessoryDefinition> out = new ArrayList<>();
        for (PartDefinition builtin : PropwashApi.parts().all(PartKind.ACCESSORY)) {
            out.add((AccessoryDefinition) builtin);
        }
        out.addAll(addons());
        return out;
    }

    private static double field(JsonObject data, String name) {
        return data.has(name) ? data.get(name).getAsDouble() : 0.0;
    }

    private static double[] vec(JsonArray array) {
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    private static JsonObject anchor(JsonObject frame, String slot) {
        JsonObject table = frame.getAsJsonObject("accessory_anchors");
        if (table.has(slot)) {
            return table.getAsJsonObject(slot);
        }
        double w = frame.get("wheelbase_mm").getAsDouble();
        double[] spec = switch (slot) {
            case "top" -> new double[]{0.0, 0.16 * w, -0.23 * w, 15.0, 0.0};
            case "bottom" -> new double[]{0.0, -3.0, -0.25 * w, 30.0, 0.15 * w};
            default -> new double[]{0.0, 0.0, -0.3 * w, 0.0, 0.0};
        };
        JsonObject out = new JsonObject();
        JsonArray pos = new JsonArray();
        pos.add(spec[0]);
        pos.add(spec[1]);
        pos.add(spec[2]);
        out.add("pos", pos);
        out.addProperty("tilt_deg", spec[3]);
        out.addProperty("length_mm", spec[4]);
        return out;
    }

    private static double[] toFrame(double[] pos, double tiltDeg, double lx, double ly, double lz) {
        double tilt = Math.toRadians(tiltDeg);
        double cos = Math.cos(tilt);
        double sin = Math.sin(tilt);
        return new double[]{pos[0] + lx, pos[1] + ly * cos - lz * sin, pos[2] + ly * sin + lz * cos};
    }

    private static Web web(JsonObject frame, JsonObject data) {
        String slot = data.get("slot").getAsString();
        JsonObject anchor = anchor(frame, slot);
        double[] pos = vec(anchor.getAsJsonArray("pos"));
        double length = anchor.get("length_mm").getAsDouble();
        boolean straight = data.has("hangs_straight") && data.get("hangs_straight").getAsBoolean();
        double[] size = vec(data.getAsJsonArray("size_mm"));
        double width = size[0];
        double height = size[1];
        double depth = size[2];
        boolean path = !data.getAsJsonArray("led_path").isEmpty();
        double recess = field(data, "recess_mm");
        double drop = field(data, "drop_mm");
        double tilt = straight ? 0.0 : anchor.get("tilt_deg").getAsDouble();
        double stretch = slot.equals("bottom") && !straight && !path && length > 0.0 && width > 0.0 ? length / width : 1.0;
        double[] origin = recess == 0.0 && drop == 0.0 ? pos : toFrame(pos, tilt, 0.0, -drop, recess);
        double[] min = switch (slot) {
            case "top" -> new double[]{-width / 2.0, AccessoryType.HINGE_CLEARANCE_MM, -depth / 2.0};
            case "bottom" -> new double[]{-width / 2.0, -height, -depth / 2.0};
            default -> new double[]{-width / 2.0, -height, -depth};
        };
        double[] max = switch (slot) {
            case "top" -> new double[]{width / 2.0, AccessoryType.HINGE_CLEARANCE_MM + height, depth / 2.0};
            case "bottom" -> new double[]{width / 2.0, 0.0, depth / 2.0};
            default -> new double[]{width / 2.0, 0.0, 0.0};
        };
        min = new double[]{min[0], min[1] - drop, min[2] + recess};
        max = new double[]{max[0], max[1] - drop, max[2] + recess};
        double[][] corners = new double[8][];
        for (int c = 0; c < 8; c++) {
            double lx = ((c & 1) == 0 ? min[0] : max[0]) * stretch;
            double ly = (c & 2) == 0 ? min[1] : max[1];
            double lz = (c & 4) == 0 ? min[2] : max[2];
            corners[c] = toFrame(pos, tilt, lx, ly, lz);
        }
        double[] center = toFrame(pos, tilt, (min[0] + max[0]) / 2.0, (min[1] + max[1]) / 2.0, (min[2] + max[2]) / 2.0);
        int count = data.get("leds").getAsInt();
        double[][] leds = new double[path ? 0 : count][];
        double strip = !straight && length > 0.0 ? length : width;
        for (int i = 0; i < leds.length; i++) {
            double spacing = strip / Math.max(1, count);
            leds[i] = toFrame(pos, tilt, -strip / 2.0 + spacing * (i + 0.5), -height - drop, recess);
        }
        return new Web(tilt, stretch, origin, corners, center, leds);
    }

    private static void assertVec(double[] expected, double[] actual, String what) {
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(expected[axis], actual[axis], EPS, what + " Achse " + axis);
        }
    }

    private static void assertSameAsGame(FrameType frame, AccessoryDefinition definition) {
        AccessoryType type = (AccessoryType) PartIndex.detached(definition);
        AccessoryAnchor anchor = AccessoryAnchor.of(frame, type.mount);
        Web web = web(PartData.data(frame.definition()), PartData.data(definition));
        String what = frame.id() + " + " + definition.id();
        assertEquals(anchor.tiltFor(type), web.tilt(), EPS, what + " Neigung");
        assertEquals(anchor.stretch(type), web.stretch(), EPS, what + " Streckung");
        assertVec(anchor.modelOrigin(type), web.origin(), what + " Ursprung");
        double[][] corners = anchor.corners(type);
        for (int c = 0; c < 8; c++) {
            assertVec(corners[c], web.corners()[c], what + " Ecke " + c);
        }
        assertVec(anchor.centerOf(type), web.center(), what + " Mitte");
        for (int i = 0; i < web.leds().length; i++) {
            assertVec(anchor.ledMm(type, i), web.leds()[i], what + " LED " + i);
        }
    }

    @Test
    void theGripperIsExportedAsHangingStraight() {
        JsonObject data = PartData.data(AccessoryType.GRIPPER.definition());
        assertTrue(data.has("hangs_straight"), data.toString());
        assertTrue(data.get("hangs_straight").getAsBoolean(), data.toString());
    }

    @Test
    void anAddonAccessoryCarriesTheFieldOnlyWhenItHangsStraight() {
        AccessoryDefinition straight = addon("spear_holder", AccessorySlot.BOTTOM, 24.0, 10.0, 60.0).hangsStraight(true).build();
        JsonObject exported = PartData.data(straight);
        assertTrue(exported.has("hangs_straight"), exported.toString());
        assertEquals(PartJson.encode(straight).get("hangs_straight"), exported.get("hangs_straight"));
        JsonObject tilted = PartData.data(addon("spear_holder_tilted", AccessorySlot.BOTTOM, 24.0, 10.0, 60.0).build());
        assertFalse(tilted.has("hangs_straight"), tilted.toString());
        int checked = 0;
        for (PartDefinition builtin : PropwashApi.parts().all(PartKind.ACCESSORY)) {
            boolean gripper = builtin.id().equals(AccessoryType.GRIPPER_ID);
            assertEquals(gripper, PartData.data(builtin).has("hangs_straight"), builtin.id().toString());
            checked++;
        }
        assertTrue(checked >= 5, "eingebautes Zubehör: " + checked);
    }

    @Test
    void freestyle5WithTheGripperBelowGivesTheGameValues() {
        JsonObject frame = PartData.data(FrameType.FREESTYLE_5.definition());
        JsonObject bottom = frame.getAsJsonObject("accessory_anchors").getAsJsonObject("bottom");
        assertEquals(30.0, bottom.get("tilt_deg").getAsDouble(), 0.0);
        Web web = web(frame, PartData.data(AccessoryType.GRIPPER.definition()));
        assertEquals(0.0, web.tilt(), 0.0);
        assertEquals(1.0, web.stretch(), 0.0);
        AccessoryAnchor anchor = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryType.GRIPPER.mount);
        assertEquals(0.0, anchor.tiltFor(AccessoryType.GRIPPER), 0.0);
        assertEquals(1.0, anchor.stretch(AccessoryType.GRIPPER), 0.0);
        assertSameAsGame(FrameType.FREESTYLE_5, AccessoryType.GRIPPER.definition());
        Web strip = web(frame, PartData.data(AccessoryType.LED_STRIP.definition()));
        assertEquals(30.0, strip.tilt(), 0.0);
        assertEquals(34.0 / AccessoryType.LED_STRIP.widthMm, strip.stretch(), EPS);
    }

    @Test
    void recessAndDropTurnWithTheTiltTheGameUses() {
        AccessoryDefinition straight = addon("holder_recessed", AccessorySlot.BOTTOM, 24.0, 18.0, 90.0).hangsStraight(true).recess(20.0).drop(3.0).build();
        Web web = web(PartData.data(FrameType.FREESTYLE_5.definition()), PartData.data(straight));
        AccessoryAnchor anchor = AccessoryAnchor.of(FrameType.FREESTYLE_5, ((AccessoryType) PartIndex.detached(straight)).mount);
        assertVec(new double[]{anchor.x(), anchor.y() - 3.0, anchor.z() + 20.0}, web.origin(), "gerade hängend eingerückt");
        assertSameAsGame(FrameType.FREESTYLE_5, straight);
    }

    @Test
    void theWebFormulaOnTheExportMatchesTheGameForEveryFrameAndAccessory() {
        int checked = 0;
        int straight = 0;
        for (FrameType frame : FrameType.all()) {
            for (AccessoryDefinition definition : accessories()) {
                assertSameAsGame(frame, definition);
                checked++;
                if (((AccessoryType) PartIndex.detached(definition)).hangsStraight()) {
                    straight++;
                }
            }
        }
        assertTrue(checked >= 5 * 13, "geprüft: " + checked);
        assertTrue(straight >= 5 * 6, "gerade hängend: " + straight);
    }
}
