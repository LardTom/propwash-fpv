package de.lardtom.propwash.api;

import de.lardtom.propwash.api.client.FlightSchoolScenes;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scenes.SceneKit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightSchoolLinksTest {
    private static final String NS = "linktest";
    private static final String OWN_SCENE = NS + ":shapes";
    private static final AtomicBoolean VISIBLE = new AtomicBoolean();
    private static boolean registered;

    @BeforeAll
    static void registerOwnScene() {
        synchronized (FlightSchoolLinksTest.class) {
            if (registered) {
                return;
            }
            registered = true;
            GuideScenes.linkParts(OWN_SCENE, NS, EnumSet.of(PartKind.FRAME));
            GuideScenes.register(() -> SceneBuilder.scene(OWN_SCENE)
                    .icon(NS + ":icon_only")
                    .step("intro", 40.0F, s -> s.at(0.0F).callout("frame", Anchor.at(0.0F, 1.0F, 0.0F), Cue.Side.ABOVE, 0.0F))
                    .build(), VISIBLE::get);
        }
    }

    private static List<String> ids(String itemId, boolean isPart, PartKind kind) {
        List<String> ids = new ArrayList<>();
        for (Scene scene : GuideScenes.forItem(itemId, isPart, kind)) {
            ids.add(scene.builtin() ? Scene.BUILTIN_NAMESPACE + ":" + scene.id() : scene.id());
        }
        return ids;
    }

    @Test
    void baseSceneConstantsNameEveryBuiltinScene() {
        List<String> builtin = new ArrayList<>();
        for (Scene scene : GuideScenes.registered()) {
            if (scene.builtin()) {
                builtin.add(Scene.BUILTIN_NAMESPACE + ":" + scene.id());
            }
        }
        assertEquals(builtin, FlightSchoolScenes.ALL);
        for (String id : FlightSchoolScenes.ALL) {
            Scene scene = GuideScenes.byId(id);
            assertNotNull(scene, id);
            assertTrue(scene.builtin(), id);
        }
    }

    @Test
    void addonPartsGetTheBuiltinScenesOfTheirKind() {
        assertEquals(List.of(FlightSchoolScenes.DRONE_BUILD, FlightSchoolScenes.BATTERY, FlightSchoolScenes.SURVIVAL),
                ids(NS + ":battery_a", true, PartKind.BATTERY));
        assertEquals(List.of(FlightSchoolScenes.DRONE_BUILD, FlightSchoolScenes.VIDEO, FlightSchoolScenes.SURVIVAL),
                ids(NS + ":video_a", true, PartKind.VIDEO));
        assertEquals(List.of(FlightSchoolScenes.DRONE_BUILD, FlightSchoolScenes.CONFIGURATOR, FlightSchoolScenes.SURVIVAL),
                ids(NS + ":stack_a", true, PartKind.STACK));
        assertEquals(List.of(FlightSchoolScenes.DRONE_BUILD, FlightSchoolScenes.REPAIR, FlightSchoolScenes.SURVIVAL),
                ids(NS + ":prop_a", true, PartKind.PROP));
        assertEquals(List.of(FlightSchoolScenes.DRONE_BUILD, FlightSchoolScenes.SURVIVAL), ids(NS + ":motor_a", true, PartKind.MOTOR));
        assertEquals(List.of(FlightSchoolScenes.DRONE_BUILD, FlightSchoolScenes.SURVIVAL), ids(NS + ":part_unknown", true, null));
        assertTrue(ids(NS + ":widget", false, null).isEmpty());
        assertTrue(ids("minecraft:stick", false, null).isEmpty());
        assertTrue(ids("", false, null).isEmpty());
    }

    @Test
    void builtinPartsKeepTheirScenes() {
        assertTrue(GuideScenes.forItem(SceneKit.part(BatteryType.B6S_1300), true).stream().anyMatch(s -> s.id().equals("battery")));
        assertTrue(GuideScenes.forItem(SceneKit.part(PropType.values()[0]), true).stream().anyMatch(s -> s.id().equals("repair")));
        assertFalse(GuideScenes.forItem(SceneKit.part(MotorType.M2207_1750), true).stream().anyMatch(s -> s.id().equals("repair")));
    }

    @Test
    void linksAddScenesForItemsAndPartsOfOneNamespace() {
        GuideScenes.linkItems(FlightSchoolScenes.REPAIR, List.of(NS + ":motor_linked"));
        GuideScenes.linkItems(FlightSchoolScenes.RACE, List.of(NS + ":gate"));
        assertTrue(ids(NS + ":motor_linked", true, PartKind.MOTOR).contains(FlightSchoolScenes.REPAIR));
        assertFalse(ids(NS + ":motor_other", true, PartKind.MOTOR).contains(FlightSchoolScenes.REPAIR));
        assertEquals(List.of(FlightSchoolScenes.RACE), ids(NS + ":gate", false, null));
        VISIBLE.set(true);
        try {
            assertTrue(ids(NS + ":frame_a", true, PartKind.FRAME).contains(OWN_SCENE));
            assertFalse(ids("other:frame_a", true, PartKind.FRAME).contains(OWN_SCENE));
            assertFalse(ids(NS + ":motor_a", true, PartKind.MOTOR).contains(OWN_SCENE));
            assertFalse(ids(NS + ":frame_a", false, null).contains(OWN_SCENE));
            assertEquals(List.of(OWN_SCENE), ids(NS + ":icon_only", false, null));
        } finally {
            VISIBLE.set(false);
        }
        assertFalse(ids(NS + ":frame_a", true, PartKind.FRAME).contains(OWN_SCENE));
    }

    @Test
    void linksToScenesRegisteredLaterBecomeActive() {
        GuideScenes.linkItems(NS + ":later", List.of(NS + ":early_item"));
        assertTrue(ids(NS + ":early_item", false, null).isEmpty());
        GuideScenes.linkItems("propwash:hold", Set.of(NS + ":hold_item"));
        assertEquals(List.of(FlightSchoolScenes.HOLD), ids(NS + ":hold_item", false, null));
    }

    @Test
    void invalidLinksAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> GuideScenes.linkItems("Kein Name", List.of(NS + ":x")));
        assertThrows(IllegalArgumentException.class, () -> GuideScenes.linkItems(FlightSchoolScenes.REPAIR, List.of("Kein Item")));
        assertThrows(IllegalArgumentException.class, () -> GuideScenes.linkParts(FlightSchoolScenes.REPAIR, "Kein NS", Set.of(PartKind.PROP)));
        assertThrows(IllegalArgumentException.class, () -> PropwashClientApi.linkFlightSchoolParts(FlightSchoolScenes.REPAIR, ""));
        assertThrows(NullPointerException.class, () -> PropwashClientApi.linkFlightSchoolParts(FlightSchoolScenes.REPAIR, NS, (PartKind) null));
        assertThrows(IllegalArgumentException.class, () -> PropwashClientApi.linkFlightSchoolItems("a:b:c", NS + ":x"));
        assertTrue(ids(NS + ":x", false, null).isEmpty());
    }

    @Test
    void emptyKindListLinksEveryPartOfTheNamespace() {
        PropwashClientApi.linkFlightSchoolParts(FlightSchoolScenes.FPV_LOS, NS + "_all");
        for (PartKind kind : PartKind.values()) {
            assertTrue(ids(NS + "_all:any_" + kind.key(), true, kind).contains(FlightSchoolScenes.FPV_LOS), kind.key());
        }
        assertFalse(ids(NS + ":any_frame", true, PartKind.FRAME).contains(FlightSchoolScenes.FPV_LOS));
    }
}
