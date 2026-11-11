package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.CollisionFlags;
import de.lardtom.propwash.api.drone.DroneDataKey;
import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.apiimpl.client.WorkbenchExtensions;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamespaceRulesTest {
    private static final String MOD = "probemod";

    @BeforeEach
    void resolver() {
        NamespaceRules.reset();
        NamespaceRules.setModResolver(type -> Optional.of(MOD));
    }

    @AfterEach
    void reset() {
        NamespaceRules.reset();
    }

    private static Identifier pw(String path) {
        return Identifier.fromNamespaceAndPath("propwash", path);
    }

    @Test
    void onlyTheExactNamespaceIsReserved() {
        assertTrue(NamespaceRules.isReserved("propwash"));
        for (String other : List.of("propwash_example", "propwashy", "minecraft", "justmoreparts", "Propwash", "")) {
            assertFalse(NamespaceRules.isReserved(other), other);
        }
        assertTrue(NamespaceRules.isReservedId("propwash:x"));
        assertFalse(NamespaceRules.isReservedId("propwash_example:x"));
        assertFalse(NamespaceRules.isReservedId("x"));
        assertFalse(NamespaceRules.isReservedId(null));
        assertEquals("propwash", NamespaceRules.namespaceOf("propwash:a/b"));
        assertEquals("", NamespaceRules.namespaceOf("ohne"));
    }

    @Test
    void theMessageNamesModAndId() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> NamespaceRules.requireForeign("propwash:y", "osd element"));
        assertEquals("Mod 'probemod' tried to register 'propwash:y' (osd element): the namespace propwash is reserved for Propwash. Use your mod id.",
                e.getMessage());
        assertEquals("mymod:y", NamespaceRules.requireForeign("mymod:y", "osd element"));
        assertEquals(1, NamespaceRules.refusals().size());
        NamespaceRules.Refusal refusal = NamespaceRules.refusals().get(0);
        assertEquals(MOD, refusal.mod());
        assertEquals("propwash:y", refusal.id());
        assertEquals("osd element", refusal.what());
        assertTrue(refusal.thrown());
        assertEquals(MOD, NamespaceRules.callerMod());
        NamespaceRules.setModResolver(type -> Optional.empty());
        assertEquals(NamespaceRules.UNKNOWN_MOD, NamespaceRules.callerMod());
        NamespaceRules.setModResolver(type -> {
            throw new IllegalStateException("kaputt");
        });
        assertEquals(NamespaceRules.UNKNOWN_MOD, NamespaceRules.callerMod());
    }

    @Test
    void refusingRecordsOnceWithoutException() {
        List<NamespaceRules.Refusal> seen = new ArrayList<>();
        NamespaceRules.onRefusal(seen::add);
        assertTrue(NamespaceRules.refuse("propwash:tab", "config tab"));
        assertTrue(NamespaceRules.refuse("propwash:tab", "config tab"));
        assertFalse(NamespaceRules.refuse("mymod:tab", "config tab"));
        assertFalse(NamespaceRules.refuse("tab", "config tab"));
        assertFalse(NamespaceRules.refuse(null, "config tab"));
        assertEquals(1, NamespaceRules.refusals().size());
        assertEquals(1, seen.size());
        assertFalse(seen.get(0).thrown());
        assertEquals(MOD, seen.get(0).mod());
    }

    @Test
    void newPointsOfApi17Throw() {
        List<Executable> calls = List.of(
                () -> PropwashApi.switches().register(SwitchSpec.builder(pw("ghost")).build()),
                () -> PropwashApi.switches().registerAxis(AxisSpec.builder(pw("tilt")).build()),
                () -> PropwashApi.worldRules().registerBoolean(pw("allow_buckets"), false),
                () -> PropwashApi.worldRules().registerInt(pw("cooldown"), 1, 0, 2),
                () -> PropwashApi.droneData().register(pw("mission"), Codec.INT, 0, DroneDataKey.Sync.NONE),
                () -> PropwashApi.drones().registerCollisionRule(pw("ghost"), query -> CollisionFlags.GHOST),
                () -> PropwashApi.drones().autonomy().registerExitHandler(pw("follow"), (drone, pilot) -> Optional.empty()),
                () -> Api17Registries.AUTONOMY.start(null, null, null, AutonomyOptions.of(pw("follow"))),
                () -> PropwashClientApi.registerOsdElement(OsdElementSpec.builder(pw("ammo")).content(context -> null).build()),
                () -> PropwashClientApi.registerOsdWarning(pw("ghost"), context -> null));
        for (Executable call : calls) {
            assertThrows(IllegalArgumentException.class, call);
        }
        long thrown = NamespaceRules.refusals().stream().filter(NamespaceRules.Refusal::thrown).count();
        assertEquals(calls.size(), thrown, NamespaceRules.refusals().toString());
    }

    @Test
    void pointsThatAlreadyThrewIn16ThrowTheEnglishText() {
        assertReservedMessage(() -> PropwashApi.flight().registerAssist("propwash:kino", profile -> null), "flight hook");
        assertReservedMessage(() -> PropwashApi.flight().registerForces("propwash:wind", profile -> null), "flight hook");
        assertReservedMessage(() -> PropwashApi.accessories().register(pw("werfer"), new AccessoryBehavior() {
        }), "accessory behavior");
        assertReservedMessage(() -> PartIndex.validateId(pw("m9999"), "Bauteil"), "part");
        assertReservedMessage(() -> PartIndex.validateId(pw("lipo_x"), "Zellchemie"), "cell chemistry");
        assertReservedMessage(() -> ExtensionTabs.checkNamespace("propwash"), "creative tab");
        assertThrows(IllegalArgumentException.class, () -> ExtensionTabs.checkNamespace("minecraft"));
        assertDoesNotThrow(() -> PartIndex.validateId(Identifier.fromNamespaceAndPath("justmoreparts", "m2306"), "Bauteil"));
    }

    @Test
    void tolerantPointsRefuseWithoutException() {
        WorkbenchExtension extension = new WorkbenchExtension() {
            @Override
            public Identifier id() {
                return pw("sharecode_probe");
            }

            @Override
            public void init(WorkbenchContext context) {
            }
        };
        assertDoesNotThrow(() -> PropwashClientApi.registerWorkbenchExtension(extension));
        assertFalse(WorkbenchExtensions.all().contains(extension));
        Scene scene = SceneBuilder.scene("propwash:nsprobe_scene")
                .icon("propwash:freestyle_5")
                .step("intro", 40.0F, s -> s.at(0.0F).callout("frame", Anchor.at(0.0F, 1.0F, 0.0F), Cue.Side.ABOVE, 0.0F))
                .build();
        assertDoesNotThrow(() -> PropwashClientApi.registerFlightSchoolScene(() -> scene));
        assertTrue(GuideScenes.registered().stream().noneMatch(registered -> registered.id().equals("nsprobe_scene")));
        assertEquals(List.of("workbench extension", "flight school scene"),
                NamespaceRules.refusals().stream().filter(refusal -> !refusal.thrown()).map(NamespaceRules.Refusal::what).toList());
    }

    private static void assertReservedMessage(Executable call, String what) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call);
        assertTrue(e.getMessage().startsWith("Mod '" + MOD + "' tried to register 'propwash:"), e.getMessage());
        assertTrue(e.getMessage().contains("(" + what + "): the namespace propwash is reserved for Propwash. Use your mod id."), e.getMessage());
    }
}
