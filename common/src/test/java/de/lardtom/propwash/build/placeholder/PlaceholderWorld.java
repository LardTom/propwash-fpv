package de.lardtom.propwash.build.placeholder;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.Accessories;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.core.DroneBuildCodecs;
import de.lardtom.propwash.parts.BundledParts;
import de.lardtom.propwash.share.Pw3Codec;
import de.lardtom.propwash.share.ShareContent;
import de.lardtom.propwash.share.ShareDecoded;
import de.lardtom.propwash.share.ShareFormat;
import de.lardtom.propwash.share.ShareTables;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class PlaceholderWorld {
    static final String NAMESPACE = "placeholdertest";
    static final Map<PartKind, String> COPIED = copied();
    static final Map<AccessoryMount, String> ACCESSORIES = accessories();

    private PlaceholderWorld() {
    }

    private static Map<PartKind, String> copied() {
        Map<PartKind, String> out = new LinkedHashMap<>();
        out.put(PartKind.FRAME, "freestyle_5");
        out.put(PartKind.STACK, "stack_30");
        out.put(PartKind.MOTOR, "m2207_1750");
        out.put(PartKind.PROP, "p51_3b");
        out.put(PartKind.VIDEO, "digital_hd");
        out.put(PartKind.BATTERY, "b6s_1300");
        return out;
    }

    private static Map<AccessoryMount, String> accessories() {
        Map<AccessoryMount, String> out = new LinkedHashMap<>();
        out.put(AccessoryMount.FRONT, "{\"format\": 1, \"mass_g\": 95.0, \"slot\": \"front\", \"size_mm\": [34.0, 26.0, 40.0],"
                + " \"frontal_area_m2\": 0.0009, \"sort\": 1}");
        out.put(AccessoryMount.TOP, "{\"format\": 1, \"mass_g\": 40.0, \"slot\": \"top\", \"size_mm\": [18.0, 14.0, 36.0],"
                + " \"frontal_area_m2\": 0.0003, \"sort\": 2}");
        out.put(AccessoryMount.BOTTOM, "{\"format\": 1, \"mass_g\": 60.0, \"slot\": \"bottom\", \"size_mm\": [12.0, 10.0, 60.0],"
                + " \"frontal_area_m2\": 0.0004, \"hangs_straight\": true, \"sort\": 3}");
        return out;
    }

    static Identifier id(PartKind kind) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, "addon_" + kind.key());
    }

    static Identifier id(AccessoryMount mount) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, "mount_" + mount.key());
    }

    public static String withAddon(String ignored) {
        register();
        DroneBuild build = drone();
        assertTrue(build.missingParts().isEmpty(), "mit Add-on fehlt nichts");
        assertEquals(3, build.accessories().size());
        JsonObject out = save(build);
        out.addProperty("mass", build.totalMassGrams());
        return out.toString();
    }

    public static String withoutAddon(String input) {
        JsonObject in = JsonParser.parseString(input).getAsJsonObject();
        assertTrue(PartIndex.registered().stream().noneMatch(part -> part.id().getNamespace().equals(NAMESPACE)), "Add-on ist weg");
        DroneBuild fromSave = DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, in.get("save")).getOrThrow();
        DroneBuild fromWire = DroneBuildCodecs.STREAM_CODEC.decode(buffer(in.get("wire").getAsString()));
        for (DroneBuild build : List.of(fromSave, fromWire)) {
            assertPlaceholders(build);
        }
        assertEquals(fromSave, fromWire);
        BuildSpec shared = shareSpec(in.get("share").getAsString());
        assertEquals(Conversions.spec(fromSave), shared, "Sharecode behält Ids und Halterungen");
        JsonObject out = save(fromSave);
        assertEquals(in.get("save"), out.get("save"), "Speicherstand ohne Add-on unverändert");
        assertEquals(in.get("wire").getAsString(), out.get("wire").getAsString(), "Netz-Bytes ohne Add-on unverändert");
        out.addProperty("mass", in.get("mass").getAsDouble());
        return out.toString();
    }

    public static String addonBack(String input) {
        JsonObject in = JsonParser.parseString(input).getAsJsonObject();
        register();
        DroneBuild expected = drone();
        DroneBuild fromSave = DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, in.get("save")).getOrThrow();
        DroneBuild fromWire = DroneBuildCodecs.STREAM_CODEC.decode(buffer(in.get("wire").getAsString()));
        DroneBuild fromShare = Conversions.build(shareSpec(in.get("share").getAsString())).orElseThrow();
        for (DroneBuild build : List.of(fromSave, fromWire, fromShare)) {
            assertEquals(expected, build);
            assertTrue(build.missingParts().isEmpty(), "Originalteile zurück: " + build.missingParts());
            for (AccessoryMount mount : AccessoryMount.values()) {
                AccessoryType type = build.accessories().at(mount).orElseThrow();
                assertSame(PartIndex.byId(id(mount)).orElseThrow(), type, mount.key());
            }
            assertEquals(in.get("mass").getAsDouble(), build.totalMassGrams(), 1.0E-9, "Masse wie vor dem Entfernen");
        }
        return "ok";
    }

    static void assertPlaceholders(DroneBuild build) {
        for (PartInfo part : List.of(build.frame(), build.stack(), build.motor(), build.prop(), build.video(), build.battery())) {
            PartKind kind = part.definition().kind();
            assertTrue(part.missing(), kind.key() + " ist Platzhalter");
            assertEquals(id(kind), part.id(), kind.key() + " behält die Id");
            assertSame(PartIndex.missing(kind, id(kind)), part, kind.key());
        }
        assertEquals(3, build.accessories().size(), "alle drei Zubehörteile bleiben");
        for (AccessoryMount mount : AccessoryMount.values()) {
            AccessoryType type = build.accessories().at(mount).orElseThrow(() -> new AssertionError("Zubehör " + mount.key() + " fehlt"));
            assertTrue(type.missing(), mount.key());
            assertEquals(id(mount), type.id(), mount.key());
            assertEquals(mount, type.mount, mount.key());
            assertEquals(mount.slot(), type.definition().slot(), mount.key());
        }
        assertEquals(DroneBuild.Issue.MISSING_PART, build.problems().getFirst());
        assertEquals(9, build.missingParts().size());
        assertFalse(build.buildable());
    }

    static void register() {
        Map<String, byte[]> files = new TreeMap<>();
        for (Map.Entry<PartKind, String> entry : COPIED.entrySet()) {
            String kind = entry.getKey().key();
            files.put("data/" + NAMESPACE + "/" + BundledParts.FOLDER + "/" + kind + "/" + id(entry.getKey()).getPath() + ".json",
                    builtinJson(kind, entry.getValue()));
        }
        for (Map.Entry<AccessoryMount, String> entry : ACCESSORIES.entrySet()) {
            files.put("data/" + NAMESPACE + "/" + BundledParts.FOLDER + "/accessory/" + id(entry.getKey()).getPath() + ".json",
                    entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(files.size(), BundledParts.load(NAMESPACE, files, true));
    }

    static DroneBuild drone() {
        List<AccessoryType> accessories = new ArrayList<>();
        for (AccessoryMount mount : AccessoryMount.values()) {
            accessories.add(PartIndex.byId(AccessoryType.class, id(mount)).orElseThrow());
        }
        return new DroneBuild(PartIndex.byId(FrameType.class, id(PartKind.FRAME)).orElseThrow(),
                PartIndex.byId(StackType.class, id(PartKind.STACK)).orElseThrow(),
                PartIndex.byId(MotorType.class, id(PartKind.MOTOR)).orElseThrow(),
                PartIndex.byId(PropType.class, id(PartKind.PROP)).orElseThrow(),
                PartIndex.byId(VideoType.class, id(PartKind.VIDEO)).orElseThrow(),
                PartIndex.byId(BatteryType.class, id(PartKind.BATTERY)).orElseThrow(),
                Accessories.of(accessories));
    }

    static JsonObject save(DroneBuild build) {
        JsonObject out = new JsonObject();
        JsonElement json = DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, build).getOrThrow();
        out.add("save", json);
        ByteBuf buffer = Unpooled.buffer();
        DroneBuildCodecs.STREAM_CODEC.encode(buffer, build);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        out.addProperty("wire", Base64.getEncoder().encodeToString(bytes));
        out.addProperty("share", Pw3Codec.encode(ShareContent.of(Conversions.spec(build)), ShareTables.builtin()));
        return out;
    }

    static BuildSpec shareSpec(String code) {
        ShareDecoded decoded = ShareFormat.decode(code, ShareTables.builtin());
        BuildSpec spec = decoded.require().build();
        for (AccessorySlot slot : AccessorySlot.values()) {
            assertEquals(id(AccessoryMount.of(slot)), spec.accessory(slot).orElseThrow(), "Sharecode " + slot.key());
        }
        return spec;
    }

    private static ByteBuf buffer(String base64) {
        return Unpooled.wrappedBuffer(Base64.getDecoder().decode(base64));
    }

    private static byte[] builtinJson(String kind, String path) {
        String resource = "data/propwash/" + BundledParts.FOLDER + "/" + kind + "/" + path + ".json";
        try (InputStream in = PlaceholderWorld.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("fehlt: " + resource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(resource, e);
        }
    }
}
