package de.lardtom.propwash.accessory.fix;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.PartTooltips;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.parts.PartJson;
import de.lardtom.propwash.parts.PartRegistryImpl;
import de.lardtom.propwash.testutil.TestPaths;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessoryFramesTest {
    static final Identifier MOUNT = Identifier.fromNamespaceAndPath("apiframes", "cine_mount");
    static final Identifier CINEWHOOP = Identifier.fromNamespaceAndPath("propwash", "cinewhoop_3");
    static final Identifier LONG_RANGE = Identifier.fromNamespaceAndPath("propwash", "long_range_7");
    static final String JSON = "{\"format\": 1, \"mass_g\": 60.0, \"slot\": \"front\", \"size_mm\": [40.0, 30.0, 40.0],"
            + " \"frontal_area_m2\": 0.0012, \"frames\": [\"propwash:long_range_7\", \"propwash:cinewhoop_3\"]}";

    static JsonObject json() {
        return JsonParser.parseString(JSON).getAsJsonObject();
    }

    static List<String> names(List<DroneBuild.Issue> issues) {
        List<String> out = new ArrayList<>();
        for (DroneBuild.Issue issue : issues) {
            out.add(issue.name());
        }
        return out;
    }

    private static AccessoryDefinition.Builder mount() {
        return AccessoryDefinition.builder(MOUNT).basics(PartBasics.of(60.0)).slot(AccessorySlot.FRONT).size(40.0, 30.0, 40.0).frontalArea(0.0012);
    }

    private static AccessoryType restricted() {
        return (AccessoryType) PartIndex.detached(mount().frames(CINEWHOOP, LONG_RANGE).build());
    }

    @Test
    void jsonFramesKeepTheAccessoryOffOtherFrames() {
        AccessoryDefinition definition = (AccessoryDefinition) PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, MOUNT, json());
        AccessoryType type = (AccessoryType) PartIndex.detached(definition);
        assertEquals(List.of("ACCESSORY_FRAME"), names(DroneBuild.FREESTYLE.withAccessory(type).problems()));
        assertEquals(List.of("ACCESSORY_FRAME"), names(DroneBuild.RACER.withAccessory(type).problems()));
        assertEquals(List.of(), names(DroneBuild.CINEWHOOP.withAccessory(type).problems()));
        assertEquals(List.of(), names(DroneBuild.LONG_RANGE.withAccessory(type).problems()));
        assertTrue(PartJson.encode(definition).has("frames"), PartJson.encode(definition).toString());
    }

    @Test
    void framesAreAdditiveInTheApiAndTheCodec() {
        AccessoryDefinition open = mount().build();
        assertEquals(Set.of(), open.frames());
        assertEquals(open, new AccessoryDefinition(MOUNT, PartBasics.of(60.0), AccessorySlot.FRONT, 40.0, 30.0, 40.0, 0.0, 0.0012, 0,
                Optional.empty(), 0.0, 0.0));
        assertEquals(open, new AccessoryDefinition(MOUNT, PartBasics.of(60.0), AccessorySlot.FRONT, 40.0, 30.0, 40.0, 0.0, 0.0012, 0,
                Optional.empty(), 0.0, 0.0, List.of(), false, Optional.empty()));
        assertEquals(open, new AccessoryDefinition(MOUNT, PartBasics.of(60.0), AccessorySlot.FRONT, 40.0, 30.0, 40.0, 0.0, 0.0012, 0,
                Optional.empty(), 0.0, 0.0, List.of(), false, Optional.empty(), null));
        assertFalse(PartJson.encode(open).has("frames"));
        AccessoryDefinition one = mount().frames(LONG_RANGE, CINEWHOOP, CINEWHOOP).build();
        AccessoryDefinition other = mount().frames(CINEWHOOP, LONG_RANGE).build();
        assertEquals(Set.of(CINEWHOOP, LONG_RANGE), one.frames());
        assertEquals(other, one);
        assertEquals(other.toString(), one.toString());
        assertEquals(PartIndex.hashOf(other), PartIndex.hashOf(one));
        assertNotEquals(PartIndex.hashOf(open), PartIndex.hashOf(one));
        assertThrows(UnsupportedOperationException.class, () -> one.frames().add(MOUNT));
        assertEquals(List.of(CINEWHOOP, LONG_RANGE), List.copyOf(one.frames()));
        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class, () -> mount().frames(CINEWHOOP, null).build());
        assertTrue(empty.getMessage().contains("frames"), empty.getMessage());
        JsonObject written = PartRegistryImpl.INSTANCE.toJson(one);
        JsonArray frames = written.getAsJsonArray("frames");
        assertEquals(List.of("propwash:cinewhoop_3", "propwash:long_range_7"), List.of(frames.get(0).getAsString(), frames.get(1).getAsString()));
        assertEquals(one, PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, MOUNT, written));
        assertEquals(open, PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, MOUNT, PartJson.encode(open)));
        AccessoryType type = (AccessoryType) PartIndex.detached(one);
        assertEquals(one.frames(), type.laden(3000L).definition().frames());
        assertEquals(one.frames(), type.frames());
        for (AccessoryType builtin : AccessoryType.all()) {
            assertTrue(builtin.frames().isEmpty(), builtin.key());
            assertFalse(PartJson.encode(builtin.definition()).has("frames"), builtin.key());
        }
    }

    @Test
    void jsonFramesAcceptIdsAndNameTheBadEntry() {
        JsonObject single = json();
        single.addProperty("frames", "cinewhoop_3");
        AccessoryDefinition one = (AccessoryDefinition) PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, MOUNT, single);
        assertEquals(Set.of(CINEWHOOP), one.frames());
        JsonObject none = json();
        none.add("frames", new JsonArray());
        assertEquals(Set.of(), ((AccessoryDefinition) PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, MOUNT, none)).frames());
        JsonObject number = json();
        JsonArray wrong = new JsonArray();
        wrong.add("propwash:cinewhoop_3");
        wrong.add(3);
        number.add("frames", wrong);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, MOUNT, number));
        assertTrue(error.getMessage().contains("frames[1]"), error.getMessage());
        JsonObject upper = json();
        JsonArray bad = new JsonArray();
        bad.add("Propwash:Cinewhoop_3");
        upper.add("frames", bad);
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, MOUNT, upper));
        assertTrue(invalid.getMessage().contains("frames[0]"), invalid.getMessage());
    }

    @Test
    void partCompatibilityLiftsItLikeTheOtherMountProblems() {
        DroneBuild wrong = DroneBuild.FREESTYLE.withAccessory(restricted());
        assertTrue(DroneBuild.Issue.ACCESSORY_FRAME.compatibility());
        assertFalse(wrong.buildable());
        assertFalse(wrong.buildable(PartCompatibility.ENFORCED));
        assertTrue(wrong.buildable(PartCompatibility.LIFTED));
        assertEquals(List.of("ACCESSORY_FRAME"), names(wrong.liftedProblems(PartCompatibility.LIFTED)));
        assertEquals(EnumSet.of(PartCategory.FRAME, PartCategory.ACCESSORY), BuildSlots.affectedBy(DroneBuild.Issue.ACCESSORY_FRAME));
        assertEquals("propwash.build.issue.accessory_frame", DroneBuild.Issue.ACCESSORY_FRAME.translationKey());
        DroneBuild both = new DroneBuild(FrameType.FREESTYLE_5, StackType.AIO_20, DroneBuild.FREESTYLE.motor(), DroneBuild.FREESTYLE.prop(),
                DroneBuild.FREESTYLE.video(), DroneBuild.FREESTYLE.battery()).withAccessory(restricted());
        assertEquals(List.of("STACK_MOUNT", "ACCESSORY_FRAME"), names(both.problems()));
    }

    @Test
    void workbenchOffersItOnlyOnItsFrames() {
        AccessoryType type = restricted();
        assertFalse(BuildSlots.fitsFrame(type, FrameType.FREESTYLE_5, PartCompatibility.ENFORCED));
        assertFalse(BuildSlots.fitsFrame(type, FrameType.RACE_5, PartCompatibility.ENFORCED));
        assertTrue(BuildSlots.fitsFrame(type, FrameType.CINEWHOOP_3, PartCompatibility.ENFORCED));
        assertTrue(BuildSlots.fitsFrame(type, FrameType.LONG_RANGE_7, PartCompatibility.ENFORCED));
        assertTrue(BuildSlots.fitsFrame(type, FrameType.FREESTYLE_5, PartCompatibility.LIFTED));
        assertTrue(BuildSlots.fitsFrame(type, null, PartCompatibility.ENFORCED));
        assertTrue(BuildSlots.fitsFrame(StackType.STACK_30, FrameType.FREESTYLE_5, PartCompatibility.ENFORCED));
        assertTrue(BuildSlots.fitsFrame(AccessoryType.LED_STRIP, FrameType.FREESTYLE_5, PartCompatibility.ENFORCED));
        assertTrue(BuildSlots.accepts(BuildSlots.accessorySlot(type.mount), type));
        BuildSlots.Check check = BuildSlots.check(BuildSlots.layout(DroneBuild.FREESTYLE.withAccessory(type)));
        assertTrue(check.complete());
        assertFalse(check.buildable(PartCompatibility.ENFORCED));
        assertTrue(check.buildable(PartCompatibility.LIFTED));
        List<FrameType> fitting = new ArrayList<>();
        for (FrameType frame : FrameType.values()) {
            if (PartTooltips.fitsFrame(type, frame)) {
                fitting.add(frame);
            }
        }
        assertEquals(List.of(FrameType.CINEWHOOP_3, FrameType.LONG_RANGE_7), fitting);
        assertFalse(PartTooltips.fitsFrame(AccessoryType.ACTION_CAM_LIGHT, FrameType.WHOOP_65));
        assertTrue(PartTooltips.fitsFrame(AccessoryType.LED_STRIP, FrameType.WHOOP_65));
    }

    @Test
    void textsExistInBothLanguages() throws IOException {
        for (String locale : List.of("en_us", "de_de")) {
            JsonObject lang = JsonParser.parseString(Files.readString(TestPaths.lang("api-acc-fix").resolve(locale + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(lang.has(DroneBuild.Issue.ACCESSORY_FRAME.translationKey()), locale);
            assertTrue(lang.get("propwash.content.workbench.slot_hint.accessory_other_frames").getAsString().contains("%s"), locale);
        }
    }

    @Test
    void foreignFramesFollowTheWorldRuleInWorkbenchImportsAndPw3() {
        assertEquals("ok", FramesRestart.run("worldRule", ""));
    }

    @Test
    void savedDronesKeepTheAccessoryAndReportTheProblem() {
        String saved = FramesRestart.run("beforeFrames", "");
        assertEquals("ok", FramesRestart.run("withFrames", saved));
    }
}
