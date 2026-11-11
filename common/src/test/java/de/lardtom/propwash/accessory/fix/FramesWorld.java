package de.lardtom.propwash.accessory.fix;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.apiimpl.BuildAccessImpl;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.client.WorkbenchReport;
import de.lardtom.propwash.core.DroneBuildCodecs;
import de.lardtom.propwash.parts.BundledParts;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.share.Pw3Codec;
import de.lardtom.propwash.share.ShareContent;
import de.lardtom.propwash.share.ShareFormat;
import de.lardtom.propwash.share.ShareTables;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.webexport.RuleCheck;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class FramesWorld {
    static final String NAMESPACE = "apiframes";
    static final Identifier MOUNT = Identifier.fromNamespaceAndPath(NAMESPACE, "cine_mount");
    static final String OPEN = "{\"format\": 1, \"mass_g\": 60.0, \"slot\": \"front\", \"size_mm\": [40.0, 30.0, 40.0],"
            + " \"frontal_area_m2\": 0.0012}";
    static final String RESTRICTED = "{\"format\": 1, \"mass_g\": 60.0, \"slot\": \"front\", \"size_mm\": [40.0, 30.0, 40.0],"
            + " \"frontal_area_m2\": 0.0012, \"frames\": [\"propwash:cinewhoop_3\"]}";

    private FramesWorld() {
    }

    public static String beforeFrames(String ignored) {
        AccessoryType mount = register(OPEN);
        assertTrue(mount.frames().isEmpty());
        DroneBuild build = DroneBuild.FREESTYLE.withAccessory(mount);
        assertTrue(build.buildable(), "ohne frames passt das Zubehör an freestyle_5: " + build.problems());
        JsonObject out = new JsonObject();
        out.add("save", DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, build).getOrThrow());
        ByteBuf buffer = Unpooled.buffer();
        DroneBuildCodecs.STREAM_CODEC.encode(buffer, build);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        out.addProperty("wire", Base64.getEncoder().encodeToString(bytes));
        out.addProperty("share", Pw3Codec.encode(ShareContent.of(Conversions.spec(build)), ShareTables.builtin()));
        return out.toString();
    }

    public static String withFrames(String input) {
        JsonObject in = JsonParser.parseString(input).getAsJsonObject();
        AccessoryType mount = register(RESTRICTED);
        assertEquals(Set.of(Identifier.fromNamespaceAndPath("propwash", "cinewhoop_3")), mount.frames());
        DroneBuild expected = DroneBuild.FREESTYLE.withAccessory(mount);
        DroneBuild fromSave = DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, in.get("save")).getOrThrow();
        DroneBuild fromWire = DroneBuildCodecs.STREAM_CODEC.decode(Unpooled.wrappedBuffer(Base64.getDecoder().decode(in.get("wire").getAsString())));
        BuildSpec spec = ShareFormat.decode(in.get("share").getAsString(), ShareTables.builtin()).require().build();
        DroneBuild fromShare = Conversions.build(spec).orElseThrow();
        for (DroneBuild build : List.of(fromSave, fromWire, fromShare)) {
            assertEquals(expected, build);
            assertSame(mount, build.accessories().at(AccessoryMount.FRONT).orElseThrow(), "Zubehör bleibt an der Drohne");
            assertTrue(build.missingParts().isEmpty());
            assertEquals(List.of(DroneBuild.Issue.ACCESSORY_FRAME), build.problems());
            assertFalse(build.buildable());
            assertTrue(build.buildable(PartCompatibility.LIFTED));
        }
        assertEquals(in.get("save"), DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, fromSave).getOrThrow(), "Speicherstand unverändert");
        BuildCheck check = PropwashApi.builds().check(spec);
        assertEquals(List.of("accessory_frame"), check.problems());
        assertFalse(check.buildable());
        assertEquals(check.problems(), RuleCheck.evaluate(spec).problems());
        BuildCheck lifted = BuildAccessImpl.check(spec, fromShare, PartCompatibility.LIFTED);
        assertTrue(lifted.problems().isEmpty());
        assertTrue(lifted.warnings().contains("accessory_frame"), lifted.warnings().toString());
        BuildSpec cine = Conversions.spec(DroneBuild.CINEWHOOP.withAccessory(mount));
        assertEquals(List.of(), PropwashApi.builds().check(cine).problems());
        assertEquals(List.of(), RuleCheck.evaluate(cine).problems());
        return "ok";
    }

    public static String worldRule(String ignored) {
        AccessoryType mount = register(RESTRICTED);
        DroneBuild wrong = DroneBuild.FREESTYLE.withAccessory(mount);
        String code = Pw3Codec.encode(ShareContent.of(Conversions.spec(wrong)), ShareTables.builtin());
        BuildSpec spec = ShareFormat.decode(code, ShareTables.builtin()).require().build();
        DroneBuild imported = Conversions.build(spec).orElseThrow();
        assertEquals(wrong, imported);
        assertSame(mount, imported.accessories().at(AccessoryMount.FRONT).orElseThrow(), "PW3-Code behält das Zubehör");
        BuildSpec cine = Conversions.spec(DroneBuild.CINEWHOOP.withAccessory(mount));
        BuildSlots.Check bench = BuildSlots.check(BuildSlots.layout(wrong));
        assertTrue(bench.complete());
        List<FrameType> withMount = new ArrayList<>();
        for (FrameType frame : FrameType.values()) {
            if (frame.accessoryMounts.contains(mount.mount)) {
                withMount.add(frame);
            }
        }
        assertTrue(withMount.contains(FrameType.CINEWHOOP_3) && withMount.contains(FrameType.FREESTYLE_5), withMount.toString());
        try {
            for (boolean enforced : new boolean[] {true, false, true}) {
                String what = enforced ? "Regel an" : "Regel aus";
                RuleValues values = RuleValues.DEFAULTS.with(WorldRuleSpecs.PART_COMPATIBILITY, enforced ? 1 : 0);
                WorldRules.clientValues(() -> values);
                PartCompatibility rule = WorldRules.partCompatibility();
                assertEquals(PartCompatibility.of(enforced), rule, what);
                assertEquals(enforced, PropwashApi.builds().partCompatibility(), what);
                List<FrameType> takes = new ArrayList<>();
                for (FrameType frame : withMount) {
                    if (BuildSlots.fitsFrame(mount, frame, rule)) {
                        takes.add(frame);
                    }
                }
                assertEquals(enforced ? List.of(FrameType.CINEWHOOP_3) : withMount, takes, what);
                WorkbenchReport report = WorkbenchReport.of(bench, true, rule);
                assertEquals(!enforced, report.buildable(), what);
                assertEquals(!enforced, bench.buildable(rule), what);
                assertEquals(enforced ? List.of() : List.of(DroneBuild.Issue.ACCESSORY_FRAME), report.liftedProblems(), what);
                BuildCheck check = PropwashApi.builds().check(spec);
                assertEquals(enforced ? List.of("accessory_frame") : List.of(), check.problems(), what);
                assertEquals(!enforced, check.warnings().contains("accessory_frame"), what + ": " + check.warnings());
                assertEquals(!enforced, check.buildable(), what);
                assertEquals(!enforced, imported.buildable(rule), what);
                assertEquals(enforced, ServerRules.refusesParts(rule, imported), what);
                assertEquals(List.of(), PropwashApi.builds().check(cine).problems(), what);
                assertEquals(List.of("accessory_frame"), RuleCheck.evaluate(spec).problems(), what);
                if (enforced) {
                    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                            () -> PropwashApi.builds().createDrone(DroneData.of(spec)), what);
                    assertTrue(refused.getMessage().contains("accessory_frame"), refused.getMessage());
                }
            }
        } finally {
            WorldRules.clientValues(null);
        }
        assertEquals(PartCompatibility.ENFORCED, WorldRules.partCompatibility());
        AirframeSpec airframe = AirframeSpec.of(imported);
        assertFalse(airframe.buildable);
        assertTrue(airframe.assemblable);
        return "ok";
    }

    private static AccessoryType register(String json) {
        String path = "data/" + NAMESPACE + "/" + BundledParts.FOLDER + "/accessory/" + MOUNT.getPath() + ".json";
        assertEquals(1, BundledParts.load(NAMESPACE, Map.of(path, json.getBytes(StandardCharsets.UTF_8)), true));
        return PartIndex.byId(AccessoryType.class, MOUNT).orElseThrow();
    }
}
