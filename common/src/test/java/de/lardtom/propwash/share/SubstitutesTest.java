package de.lardtom.propwash.share;

import com.google.gson.JsonObject;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubstitutesTest {
    private static final ShareTables TABLES = ShareTables.builtin();
    private static final String MOUNTS = "mountparts";
    private static final String MOUNTS_TABLE = "{\"format\": 1, \"namespace\": \"mountparts\", \"parts\": {"
            + "\"frame\": [{\"id\": \"cine_frame\", \"since\": \"1.0.0\", \"fallback\": \"propwash:long_range_7\"}],"
            + "\"stack\": [{\"id\": \"nav_stack\", \"since\": \"1.0.0\", \"fallback\": \"propwash:stack_30\"}],"
            + "\"motor\": [{\"id\": \"m4114_320\", \"since\": \"1.0.0\", \"fallback\": \"propwash:m2807_1300\"}],"
            + "\"prop\": [{\"id\": \"fold_9\", \"since\": \"1.0.0\", \"fallback\": \"propwash:p7_2b\"}],"
            + "\"video\": [{\"id\": \"cam_link\", \"since\": \"1.0.0\", \"fallback\": \"propwash:digital_hd\"}],"
            + "\"battery\": [{\"id\": \"smart_6s\", \"since\": \"1.0.0\", \"fallback\": \"propwash:b6s_4000_lion\"}],"
            + "\"accessory\": [{\"id\": \"belly_light\", \"since\": \"1.0.0\", \"fallback\": \"propwash:action_cam_light\"},"
            + " {\"id\": \"gimbal\", \"since\": \"1.0.0\", \"fallback\": \"propwash:action_cam_heavy\"},"
            + " {\"id\": \"top_box\", \"since\": \"1.0.0\"},"
            + " {\"id\": \"top_cam\", \"since\": \"1.0.0\", \"fallback\": \"propwash:action_cam_light\"}]}}";
    private static final BuildSpec MOUNTS_CORE = BuildSpec.of(id("mountparts:cine_frame"), id("mountparts:nav_stack"), id("mountparts:m4114_320"),
            id("mountparts:fold_9"), id("mountparts:cam_link"), id("mountparts:smart_6s"));
    private static final BuildSpec STAND_INS = BuildSpec.of(id("propwash:long_range_7"), id("propwash:stack_30"), id("propwash:m2807_1300"),
            id("propwash:p7_2b"), id("propwash:digital_hd"), id("propwash:b6s_4000_lion"));
    private static final Map<AccessorySlot, Identifier> WITHOUT_STAND_IN = Map.of(AccessorySlot.FRONT, id("mountparts:gimbal"), AccessorySlot.TOP,
            id("mountparts:top_box"), AccessorySlot.BOTTOM, id("mountparts:belly_light"));

    private static Identifier id(String text) {
        return Identifier.parse(text);
    }

    private static ShareTable mountsTable() {
        return ShareTestData.table(MOUNTS_TABLE);
    }

    private static AddonDirectory mountsDirectory() {
        List<AddonDirectory.Addon> addons = new ArrayList<>(AddonDirectory.builtin().addons());
        addons.add(new AddonDirectory.Addon(MOUNTS, 9, Map.of("en_us", "Mount Parts", "de_de", "Halterungsteile"), Optional.of("mount-parts"),
                Optional.empty(), Optional.of("1.0.0"), Optional.of(mountsTable())));
        return new AddonDirectory(addons, List.of());
    }

    private static Map<Identifier, PartKind> builtinKinds() {
        Map<Identifier, PartKind> kinds = new HashMap<>();
        for (PartInfo part : PartIndex.registered()) {
            if (part.builtin()) {
                kinds.put(part.id(), part.definition().kind());
            }
        }
        return kinds;
    }

    private static ShareTables withMounts() {
        Map<Identifier, PartKind> kinds = builtinKinds();
        ShareTable table = mountsTable();
        for (PartKind kind : PartKind.values()) {
            for (ShareTable.Entry entry : table.entries(kind)) {
                kinds.put(table.id(entry), kind);
            }
        }
        kinds.put(id("otheraddon:gimbal"), PartKind.ACCESSORY);
        kinds.put(id("otheraddon:top_box"), PartKind.ACCESSORY);
        kinds.put(id("otheraddon:belly_light"), PartKind.ACCESSORY);
        return ShareTables.builder().installed(ShareTables.propwashTable()).installed(table).directory(mountsDirectory()).version(MOUNTS, "1.0.0")
                .version("otheraddon", "2.1.0").parts(id -> Optional.ofNullable(kinds.get(id))).build();
    }

    private static ShareTables withoutMounts() {
        Map<Identifier, PartKind> kinds = builtinKinds();
        return ShareTables.builder().installed(ShareTables.propwashTable()).directory(mountsDirectory()).parts(id -> Optional.ofNullable(kinds.get(id)))
                .build();
    }

    private static ShareDecoded importWithoutAddon(BuildSpec build) {
        ShareDecoded decoded = ShareFormat.decode(Pw3Codec.encode(ShareContent.of(build), withMounts()), withoutMounts());
        assertTrue(decoded.ok(), decoded.status().toString());
        assertFalse(decoded.partsKnown());
        return decoded;
    }

    @Test
    void everyJmpPresetGetsABuildableSubstitute() {
        int presets = 0;
        Map<Substitutes.Method, Integer> methods = new EnumMap<>(Substitutes.Method.class);
        for (JsonObject vector : ShareTestData.vectors("pw2-vectors.json")) {
            String name = vector.get("name").getAsString();
            if (!name.startsWith("preset/justmoreparts:") || !name.endsWith("/parts")) {
                continue;
            }
            presets++;
            BuildSpec build = ShareTestData.content(vector.getAsJsonObject("content")).build();
            for (Identifier id : build.allParts()) {
                if (id.getNamespace().equals("justmoreparts")) {
                    assertTrue(TABLES.fallback(id).isPresent(), name + " " + id);
                }
            }
            Substitutes.Result result = Substitutes.find(build, TABLES);
            assertTrue(result.found(), name);
            assertTrue(PropwashApi.builds().check(result.build().orElseThrow()).buildable(), name + " " + result);
            for (Identifier id : result.build().orElseThrow().allParts()) {
                assertEquals("propwash", id.getNamespace(), name);
            }
            assertEquals(result, Substitutes.find(build, TABLES), name);
            methods.merge(result.method(), 1, Integer::sum);
            for (Substitutes.Swap swap : result.swaps()) {
                assertEquals(swap.position().startsWith("accessory:") ? Optional.empty() : Optional.of("justmoreparts"),
                        swap.position().startsWith("accessory:") ? Optional.empty() : Optional.of(swap.from().getNamespace()), name);
            }
        }
        assertEquals(22, presets);
        assertTrue(methods.getOrDefault(Substitutes.Method.FALLBACK, 0) > 0, methods.toString());
    }

    @Test
    void everySingleJmpPartInAPropwashBuildFindsASubstitute() {
        BuildSpec base = Pw3VectorFiles.legacy("preset/propwash:freestyle/parts").build();
        ShareTable jmp = TABLES.table("justmoreparts").orElseThrow();
        for (PartKind kind : List.of(PartKind.FRAME, PartKind.STACK, PartKind.MOTOR, PartKind.PROP, PartKind.VIDEO, PartKind.BATTERY)) {
            for (ShareTable.Entry entry : jmp.entries(kind)) {
                BuildSpec build = base.withPart(kind, jmp.id(entry));
                Substitutes.Result result = Substitutes.find(build, TABLES);
                assertTrue(result.found(), entry.id());
                assertTrue(PropwashApi.builds().check(result.build().orElseThrow()).buildable(), entry.id());
            }
        }
    }

    @Test
    void knownBuildsNeedNothingAndImpossibleOnesGetNothing() {
        BuildSpec base = Pw3VectorFiles.legacy("preset/propwash:freestyle/parts").build();
        assertEquals(Substitutes.Method.NOT_NEEDED, Substitutes.find(base, TABLES).method());
        BuildSpec broken = base.withPart(PartKind.FRAME, Identifier.parse("otheraddon:frame_x"));
        Substitutes.Result none = Substitutes.find(broken, (id, kind) -> id.getNamespace().equals("propwash"), id -> Optional.empty(), spec -> false,
                List.of(base));
        assertEquals(Substitutes.Method.NONE, none.method());
        assertFalse(none.found());
        Substitutes.Result preset = Substitutes.find(broken, (id, kind) -> id.getNamespace().equals("propwash"), id -> Optional.empty(), spec -> true,
                List.of(base));
        assertEquals(Substitutes.Method.PRESET, preset.method());
        assertEquals(Optional.of(0), preset.preset());
        assertEquals(base, preset.build().orElseThrow());
    }

    @Test
    void unknownAccessoriesAreReplacedOrRemoved() {
        BuildSpec base = Pw3VectorFiles.legacy("preset/propwash:freestyle/parts").build().withAccessory(AccessorySlot.TOP, Identifier.parse("otheraddon:cam"));
        Substitutes.Result result = Substitutes.find(base, TABLES);
        assertTrue(result.found());
        assertEquals(Optional.empty(), result.build().orElseThrow().accessory(AccessorySlot.TOP));
        assertEquals("accessory:top", result.swaps().get(0).position());
        assertEquals(Optional.empty(), result.swaps().get(0).to());
    }

    @Test
    void inactivePartsAreSubstitutedLikeMissingOnes() {
        BuildSpec base = Pw3VectorFiles.legacy("preset/propwash:racer/parts").build();
        Substitutes.Result result = Substitutes.find(base, (id, kind) -> !id.getPath().equals("race_5"),
                id -> id.getPath().equals("race_5") ? Optional.of(Identifier.parse("propwash:freestyle_5")) : Optional.empty(),
                spec -> PropwashApi.builds().check(spec).buildable(), List.of());
        assertEquals(Substitutes.Method.FALLBACK, result.method());
        assertEquals(Identifier.parse("propwash:freestyle_5"), result.build().orElseThrow().frame());
    }

    @Test
    void addonAccessoryWithoutFittingStandInIsDroppedInEverySlotAndTheRestKeepsItsStandIns() {
        assertTrue(PropwashApi.builds().check(STAND_INS).buildable());
        ShareTables without = withoutMounts();
        for (AccessorySlot slot : Pw3Params.ACCESSORY_SLOTS) {
            Identifier accessory = WITHOUT_STAND_IN.get(slot);
            ShareDecoded decoded = importWithoutAddon(MOUNTS_CORE.withAccessory(slot, accessory));
            assertTrue(decoded.unknownParts().contains(accessory), slot + " " + decoded.unknownParts());
            Substitutes.Result result = ShareServer.substitutes(decoded, without);
            assertEquals(Substitutes.Method.FALLBACK, result.method(), slot + " " + result);
            assertEquals(Optional.empty(), result.preset(), slot.key());
            assertEquals(STAND_INS, result.build().orElseThrow(), slot.key());
            assertTrue(result.swaps().contains(new Substitutes.Swap("accessory:" + slot.key(), accessory, Optional.empty())), slot + " " + result.swaps());
            assertEquals(Optional.empty(), result.build().orElseThrow().accessory(slot), slot.key());
            assertTrue(PropwashApi.builds().check(result.build().orElseThrow()).buildable(), slot.key());
            MissingAddons.Line line = MissingAddons.of(decoded, without, "en_us").stream().filter(l -> l.key().equals(MOUNTS)).findFirst().orElseThrow();
            assertTrue(line.parts().stream().anyMatch(part -> part.id().equals(accessory) && part.kind() == PartKind.ACCESSORY), slot + " " + line.parts());
            assertEquals(Optional.of(STAND_INS), ShareServer.substituted(decoded, without).map(ShareContent::build), slot.key());
        }
    }

    @Test
    void fittingAccessoryStandInsStayAndOnlyTheOthersAreDropped() {
        BuildSpec build = MOUNTS_CORE.withAccessory(AccessorySlot.FRONT, id("mountparts:gimbal")).withAccessory(AccessorySlot.TOP, id("mountparts:top_cam"))
                .withAccessory(AccessorySlot.BOTTOM, id("mountparts:top_box"));
        ShareDecoded decoded = importWithoutAddon(build);
        Substitutes.Result result = ShareServer.substitutes(decoded, withoutMounts());
        assertEquals(Substitutes.Method.FALLBACK, result.method(), result.toString());
        assertEquals(STAND_INS.withAccessory(AccessorySlot.TOP, id("propwash:action_cam_light")), result.build().orElseThrow());
        assertTrue(result.swaps().contains(new Substitutes.Swap("accessory:front", id("mountparts:gimbal"), Optional.empty())), result.swaps().toString());
        assertTrue(result.swaps().contains(new Substitutes.Swap("accessory:top", id("mountparts:top_cam"), Optional.of(id("propwash:action_cam_light")))),
                result.swaps().toString());
        assertTrue(result.swaps().contains(new Substitutes.Swap("accessory:bottom", id("mountparts:top_box"), Optional.empty())), result.swaps().toString());
        assertEquals(result, ShareServer.substitutes(decoded, withoutMounts()));
    }

    @Test
    void propwashPartsStayWhenOnlyTheAccessoriesOfAnUnknownAddonAreMissing() {
        BuildSpec core = Pw3VectorFiles.legacy("preset/propwash:freestyle/parts").build();
        BuildSpec build = core.withAccessory(AccessorySlot.FRONT, id("otheraddon:gimbal")).withAccessory(AccessorySlot.TOP, id("otheraddon:top_box"))
                .withAccessory(AccessorySlot.BOTTOM, id("otheraddon:belly_light"));
        ShareDecoded decoded = importWithoutAddon(build);
        Substitutes.Result result = ShareServer.substitutes(decoded, withoutMounts());
        assertEquals(Substitutes.Method.FALLBACK, result.method(), result.toString());
        assertEquals(Optional.empty(), result.preset());
        assertEquals(core, result.build().orElseThrow());
        assertEquals(3, result.swaps().size(), result.swaps().toString());
        for (Substitutes.Swap swap : result.swaps()) {
            assertEquals(Optional.empty(), swap.to(), swap.toString());
        }
    }

    @Test
    void unknownCorePartsWithoutStandInStillUseAPresetButAccessoriesNeverComeFromIt() {
        BuildSpec core = Pw3VectorFiles.legacy("preset/propwash:freestyle/parts").build();
        BuildSpec broken = core.withPart(PartKind.FRAME, id("otheraddon:frame_x")).withAccessory(AccessorySlot.FRONT, id("otheraddon:gimbal"));
        BuildSpec preset = core.withAccessory(AccessorySlot.FRONT, id("propwash:action_cam_light"));
        Substitutes.Result result = Substitutes.find(broken, (id, kind) -> id.getNamespace().equals("propwash"), id -> Optional.empty(), spec -> true,
                List.of(preset));
        assertEquals(Substitutes.Method.PRESET, result.method());
        assertEquals(Optional.of(0), result.preset());
        assertEquals(core, result.build().orElseThrow());
        assertEquals(new Substitutes.Swap("accessory:front", id("otheraddon:gimbal"), Optional.empty()), result.swaps().get(result.swaps().size() - 1));
    }
}
