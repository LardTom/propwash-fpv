package de.lardtom.propwash.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.share.ShareResult;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.apiimpl.ImportWire;
import de.lardtom.propwash.creative.CreativeParam;
import de.lardtom.propwash.creative.CreativeScene;
import de.lardtom.propwash.creative.CreativeShareCode;
import de.lardtom.propwash.creative.CreativeSpec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeShareTest {
    static final String FILE = "pw3-creative-vectors.json";
    private static final ShareTables FROZEN = ShareTables.frozen();

    private static Identifier id(String text) {
        return Identifier.parse(text);
    }

    private static BuildSpec creativeFreestyle() {
        return BuildSpec.of(id("propwash:freestyle_5"), id("propwash:creative_fc"), id("propwash:m2207_1750"), id("propwash:p51_3b"),
                id("propwash:digital_hd"), id("propwash:b6s_1300"));
    }

    private static BuildSpec creativeJmp() {
        return new BuildSpec(id("justmoreparts:deadcat_5"), id("propwash:creative_fc"), id("justmoreparts:m22075_1850"),
                id("justmoreparts:p130_36_3b_bi"), id("justmoreparts:digital_hd_pro"), id("propwash:b6s_1300"),
                new EnumMap<>(Map.of(AccessorySlot.TOP, id("propwash:action_cam_light"))));
    }

    private static CreativeSpec sample() {
        return CreativeSpec.EMPTY.with(CreativeParam.MOTOR_KV, 2600.0).with(CreativeParam.PROP_THRUST_COEFFICIENT, 0.123)
                .with(CreativeParam.INERTIA_YAW, 23456.0).withBase(PartKind.FRAME, "whoop_65").withBase(PartKind.PROP, "justmoreparts:p13_2b");
    }

    private static ShareContent editorExport(BuildSpec build, CreativeSpec spec) {
        return new ShareContent(build, Optional.of(PaintSpec.DEFAULT.with("frame", 0x6A2CBF)), Optional.of(new TuneSpec(Map.of("rates.roll.rcRate", 1.25F))),
                Optional.empty(), Optional.of("Kreativ-Rakete"), Optional.of(spec));
    }

    @Test
    void oldDroneEditorCodesAreReadAsCreativeDrone() {
        ShareDecoded decoded = ShareFormat.decode(CreativeShareCode.encode(sample()), FROZEN);
        assertEquals(ShareStatus.OK, decoded.status(), decoded.toString());
        assertEquals("PWD1", decoded.format());
        assertTrue(decoded.legacy());
        assertTrue(decoded.layers().stream().anyMatch(layer -> layer.key().equals("creative")), decoded.layers().toString());
        assertEquals(List.of(ShareLayer.PARTS, ShareLayer.CREATIVE), decoded.layers());
        assertEquals(CreativeShare.PWD1_BUILD, decoded.require().build());
        assertEquals(Optional.of(sample()), decoded.require().creative());
        ShareDecoded builtin = ShareFormat.decode(CreativeShareCode.encode(sample()), ShareTables.builtin());
        assertTrue(builtin.partsKnown(), builtin.toString());
    }

    @Test
    void oldDroneEditorCodesConvertToPw3() {
        ShareConvert.Conversion conversion = ShareConvert.toPw3(CreativeShareCode.encode(sample()));
        assertEquals(ShareStatus.OK, conversion.status(), conversion.toString());
        assertTrue(conversion.converted());
        assertTrue(conversion.code().orElseThrow().startsWith(Pw3Params.PREFIX), conversion.toString());
        ShareDecoded pw3 = ShareFormat.decode(conversion.code().orElseThrow(), FROZEN);
        assertEquals("PW3", pw3.format());
        assertEquals(CreativeShare.PWD1_BUILD, pw3.require().build());
        assertEquals(Optional.of(sample()), pw3.require().creative());
        assertEquals(List.of(ShareLayer.PARTS, ShareLayer.CREATIVE), pw3.layers());
        ShareConvert.Conversion again = ShareConvert.toPw3(conversion.code().orElseThrow());
        assertFalse(again.converted());
        assertEquals(conversion.code(), again.code());
    }

    @Test
    void editorCodeAndPasteRoundTrip() {
        DroneData data = new DroneData(creativeFreestyle(), PaintSpec.DEFAULT.with("frame", 0x6A2CBF), new TuneSpec(Map.of("camera.uptiltDeg", 35.0F)),
                Optional.of("Editor"));
        String code = CreativeShare.editorCode(data, sample(), true);
        assertTrue(code.startsWith(Pw3Params.PREFIX), code);
        CreativeShare.Pasted pasted = CreativeShare.paste(code, ShareTables.builtin());
        assertEquals(CreativeShare.PasteStatus.OK, pasted.status());
        assertEquals(Optional.of(sample()), pasted.spec());
        assertFalse(pasted.legacy());
        CreativeShare.Pasted old = CreativeShare.paste(CreativeShareCode.encode(sample()), ShareTables.builtin());
        assertEquals(CreativeShare.PasteStatus.OK, old.status());
        assertTrue(old.legacy());
        assertEquals(Optional.of(sample()), old.spec());
        String plain = Pw3Codec.encode(ShareContent.of(creativeFreestyle()), ShareTables.builtin());
        assertEquals(CreativeShare.PasteStatus.NO_VALUES, CreativeShare.paste(plain, ShareTables.builtin()).status());
        assertEquals(CreativeShare.PasteStatus.INVALID, CreativeShare.paste("kein Code", ShareTables.builtin()).status());
        assertEquals(CreativeShare.PasteStatus.INVALID, CreativeShare.paste(null, ShareTables.builtin()).status());
    }

    @Test
    void apiKeepsPwd1AsOtherCodeButConvertsIt() {
        String pwd1 = CreativeShareCode.encode(sample());
        assertEquals(ShareResult.Status.OTHER_CODE, ShareCodesImpl.INSTANCE.decode(pwd1).status());
        ShareResult converted = ShareCodesImpl.INSTANCE.convert(pwd1);
        assertEquals(ShareResult.Status.OK, converted.status());
        assertTrue(converted.convertedCode().orElseThrow().startsWith(Pw3Params.PREFIX));
        assertEquals(CreativeShare.PWD1_BUILD, converted.data().orElseThrow().build());
    }

    @Test
    void createPayloadRoundTripsOnTheWire() {
        DroneData data = editorExport(creativeJmp(), sample()).toDroneData();
        ShareCreativeC2S payload = new ShareCreativeC2S(42, ImportWire.of(ImportRequest.createDrone(data)), sample());
        ByteBuf buffer = Unpooled.buffer();
        try {
            ShareCreativeC2S.CODEC.encode(buffer, payload);
            ShareCreativeC2S back = ShareCreativeC2S.CODEC.decode(buffer);
            assertEquals(42, back.requestId());
            assertEquals(sample(), back.spec());
            assertEquals(data, back.request().toRequest().data());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void pwd1ReferenceBuildIsTheFreestyleOfTheFlightSchool() {
        assertEquals(Conversions.spec(CreativeScene.FREESTYLE_BUILD), CreativeShare.PWD1_BUILD);
        assertTrue(CreativeShare.creativeStack(CreativeShare.PWD1_BUILD));
    }

    @Test
    void brokenOldCodesAreCorruptNotEmpty() {
        assertEquals(ShareStatus.CORRUPT, ShareFormat.decode("PWD1-abc", FROZEN).status());
        assertEquals(ShareStatus.CORRUPT, ShareFormat.decode("PWD1-!!!", FROZEN).status());
        assertEquals("PWD1", ShareFormat.decode("PWD1-abc", FROZEN).format());
        assertEquals(ShareStatus.OK, ShareFormat.decode(CreativeShareCode.encode(CreativeSpec.EMPTY), FROZEN).status());
    }

    @Test
    void editorExportRoundTripsWithAllLayers() {
        for (BuildSpec build : List.of(creativeFreestyle(), creativeJmp())) {
            ShareContent content = editorExport(build, sample());
            String code = Pw3Codec.encode(content, FROZEN);
            ShareDecoded decoded = ShareFormat.decode(code, FROZEN);
            assertEquals(ShareStatus.OK, decoded.status(), code);
            assertEquals(content, decoded.require(), code);
            assertEquals(List.of(ShareLayer.PARTS, ShareLayer.PAINT, ShareLayer.TUNE, ShareLayer.NAME, ShareLayer.CREATIVE), decoded.layers());
            assertEquals(List.of(), decoded.skipped());
            assertTrue(code.length() < 256, code.length() + " " + code);
            assertEquals(code, Pw3Codec.encode(decoded.require(), FROZEN));
        }
    }

    @Test
    void codesWithoutCreativeValuesStayByteIdentical() {
        ShareContent plain = new ShareContent(creativeFreestyle(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        ShareContent withEmpty = plain.withCreative(Optional.empty());
        assertEquals(Pw3Codec.encode(plain, FROZEN), Pw3Codec.encode(withEmpty, FROZEN));
        assertNotEquals(Pw3Codec.encode(plain, FROZEN), Pw3Codec.encode(plain.withCreative(Optional.of(CreativeSpec.EMPTY)), FROZEN));
        ShareDecoded empty = ShareFormat.decode(Pw3Codec.encode(plain.withCreative(Optional.of(CreativeSpec.EMPTY)), FROZEN), FROZEN);
        assertEquals(Optional.of(CreativeSpec.EMPTY), empty.require().creative());
        assertFalse(ShareFormat.decode(Pw3Codec.encode(plain, FROZEN), FROZEN).require().creative().isPresent());
    }

    @Test
    void oldReadersSkipTheCreativeExtensionExactly() throws ShareFormatException {
        ShareContent content = editorExport(creativeJmp(), sample());
        BitWriter out = new BitWriter();
        Pw3Codec.writeExtensions(out, content, List.of("justmoreparts", "propwash"));
        out.bits(0b1011, 4);
        byte[] bytes = out.toByteArray();
        BitReader in = new BitReader(bytes, bytes.length);
        ShareParsed parsed = new ShareParsed();
        LayerCodec.skipExtensions(in, parsed);
        assertEquals(List.of("layer:5"), parsed.skipped);
        assertEquals(0b1011, in.bits(4));
        assertEquals(out.length(), in.position());
    }

    @Test
    void baseInTheNamespaceOfAHeadUsesTheHeadAndOthersGoAsText() throws ShareFormatException {
        CreativeSpec spec = CreativeSpec.EMPTY.withBase(PartKind.FRAME, "whoop_65").withBase(PartKind.MOTOR, "justmoreparts:m22075_1850")
                .withBase(PartKind.PROP, "otheraddon:prop_x").withBase(PartKind.STACK, "propwash:stack_30").withBase(PartKind.BATTERY, "Kaputt Ä");
        List<String> heads = List.of("propwash", "justmoreparts");
        BitWriter out = CreativeLayer.write(spec, heads);
        byte[] bytes = out.toByteArray();
        ShareParsed parsed = new ShareParsed();
        assertEquals(spec, CreativeLayer.read(new BitReader(bytes, bytes.length), heads, parsed));
        BitWriter head = new BitWriter();
        CreativeLayer.writeBase(head, "whoop_65", heads);
        BitWriter text = new BitWriter();
        CreativeLayer.writeBase(text, "whoop_65", List.of("justmoreparts"));
        assertTrue(head.length() < text.length(), head.length() + " " + text.length());
    }

    @Test
    void valuesKeepTheirExactDouble() throws ShareFormatException {
        double[] values = {0.0, 1.0, -1.0, 0.1, 0.123, 2600.0, 5.0E6, 1.0E-7, -3.25, 123456.789, 1.0 / 3.0, Math.PI, 9.007199254740993E15,
                -1.0E300, Double.MIN_VALUE, 4.9E-324, 1234.5678901234567};
        for (double value : values) {
            BitWriter out = new BitWriter();
            CreativeLayer.writeValue(out, value);
            byte[] bytes = out.toByteArray();
            double back = CreativeLayer.readValue(new BitReader(bytes, bytes.length));
            assertEquals(Double.doubleToRawLongBits(value + 0.0), Double.doubleToRawLongBits(back), Double.toString(value));
        }
        BitWriter small = new BitWriter();
        CreativeLayer.writeValue(small, 2600.0);
        assertTrue(small.length() < 24, Long.toString(small.length()));
    }

    @Test
    void everyCreativeParamHasATableIndex() {
        for (CreativeParam param : CreativeParam.values()) {
            assertTrue(Pw3Params.creativeIndex(param.key()) > 0, param.key());
        }
        assertEquals(CreativeParam.values().length, Pw3Params.CREATIVE_KEYS.size());
        assertEquals(CreativeSpec.BASE_KINDS, Pw3Params.CREATIVE_BASE_KINDS);
        assertEquals(CreativeSpec.MAX_VALUES, Pw3Params.MAX_CREATIVE_VALUES);
        assertEquals(CreativeSpec.MAX_KEY_LENGTH, Pw3Params.MAX_CREATIVE_KEY_CHARS);
    }

    static CreativeSpec random(Random random) {
        TreeMap<String, Double> values = new TreeMap<>();
        CreativeParam[] params = CreativeParam.values();
        int count = random.nextInt(12);
        for (int i = 0; i < count; i++) {
            CreativeParam param = params[random.nextInt(params.length)];
            double value = param.min() + random.nextDouble() * (param.max() - param.min());
            values.put(param.key(), random.nextInt(4) == 0 ? value : param.round(value));
        }
        if (random.nextInt(5) == 0) {
            values.put("future.key_" + random.nextInt(100), random.nextGaussian() * 1000.0);
        }
        if (random.nextInt(9) == 0) {
            values.put("Schlüssel mit Ä " + random.nextInt(10), (double) random.nextInt(100));
        }
        EnumMap<PartKind, String> base = new EnumMap<>(PartKind.class);
        String[] keys = {"freestyle_5", "whoop_65", "stack_30", "m2207_1750", "p51_3b", "b6s_1300", "justmoreparts:deadcat_5", "otheraddon:x",
                "propwash:stack_30", "pw3.short.9:frame_a", "weird key"};
        for (PartKind kind : CreativeSpec.BASE_KINDS) {
            if (random.nextInt(3) == 0) {
                base.put(kind, keys[random.nextInt(keys.length)]);
            }
        }
        return new CreativeSpec(base, values);
    }

    @Test
    void randomSpecsRoundTrip() {
        Random random = new Random(0x50574433L);
        List<BuildSpec> builds = List.of(creativeFreestyle(), creativeJmp());
        for (int i = 0; i < 3000; i++) {
            CreativeSpec spec = random(random);
            ShareContent content = ShareContent.of(builds.get(i % 2)).withCreative(Optional.of(spec));
            String code = Pw3Codec.encode(content, FROZEN);
            ShareDecoded decoded = ShareFormat.decode(code, FROZEN);
            assertEquals(ShareStatus.OK, decoded.status(), code);
            assertEquals(Optional.of(spec), decoded.require().creative(), code);
            assertEquals(code, Pw3Codec.encode(decoded.require(), FROZEN));
            ShareDecoded old = ShareFormat.decode(CreativeShareCode.encode(spec), FROZEN);
            assertEquals(Optional.of(spec), old.require().creative());
        }
    }

    @Test
    void brokenCreativeLayersNeverThrow() {
        Random random = new Random(7L);
        String code = Pw3Codec.encode(editorExport(creativeJmp(), sample()), FROZEN);
        String body = code.substring(Pw3Params.PREFIX.length());
        for (int i = 0; i < 20000; i++) {
            char[] chars = body.toCharArray();
            int changes = 1 + random.nextInt(3);
            for (int c = 0; c < changes; c++) {
                chars[random.nextInt(chars.length)] = Base62.ALPHABET.charAt(random.nextInt(Base62.ALPHABET.length()));
            }
            ShareDecoded decoded = ShareFormat.decode(Pw3Params.PREFIX + new String(chars), FROZEN);
            assertEquals(decoded.ok(), decoded.content().isPresent());
        }
    }

    @Test
    void framingOfTheCreativeExtensionIsChecked() {
        ShareContent content = ShareContent.of(creativeFreestyle()).withCreative(Optional.of(sample()));
        BitWriter layer = CreativeLayer.write(sample(), List.of("propwash"));
        assertEquals(ShareStatus.CORRUPT, frame(content, layer, 2, layer.length()).status());
        assertEquals(ShareStatus.CORRUPT, frame(content, layer, 1, layer.length() - 1).status());
        ShareDecoded longer = frame(content, layer, 1, layer.length() + 5);
        assertEquals(ShareStatus.OK, longer.status());
        assertEquals(Optional.of(sample()), longer.require().creative());
    }

    private static ShareDecoded frame(ShareContent content, BitWriter layer, int copies, long declared) {
        BitWriter out = new BitWriter();
        out.bit(false).bit(false).bit(false).bit(false).bit(true);
        out.golomb(0, Pw3Params.K_NAMESPACE_COUNT);
        out.golomb(1, Pw3Params.K_SHORT_ID).bit(false).bit(false).bit(false);
        int indexBits = LayerCodec.indexBits(1);
        for (Identifier part : List.of(content.build().frame(), content.build().stack(), content.build().motor(), content.build().prop(),
                content.build().video(), content.build().battery())) {
            out.bits(0, indexBits);
            IdText.write(out, part.getPath(), Pw3Params.MAX_PATH_CHARS);
        }
        out.golomb(0, Pw3Params.K_ACCESSORY_COUNT);
        out.golomb(copies, Pw3Params.K_EXTENSION_COUNT);
        for (int i = 0; i < copies; i++) {
            out.golomb(Pw3Params.CREATIVE_EXTENSION, Pw3Params.K_EXTENSION_LAYER);
            out.golomb(declared, Pw3Params.K_EXTENSION_BITS);
            out.append(layer);
            for (long pad = layer.length(); pad < declared; pad++) {
                out.bit(true);
            }
        }
        return ShareFormat.decode(Pw3Params.PREFIX + Base62.encode(LayerCodec.frame(out.toByteArray())), FROZEN);
    }

    static JsonObject creative(Optional<CreativeSpec> spec) {
        JsonObject out = new JsonObject();
        if (spec.isEmpty()) {
            return out;
        }
        JsonObject base = new JsonObject();
        spec.get().base().forEach((kind, key) -> base.addProperty(kind.key(), key));
        JsonObject values = new JsonObject();
        spec.get().values().forEach(values::addProperty);
        out.add("base", base);
        out.add("values", values);
        return out;
    }

    static List<JsonObject> computeVectors() {
        List<JsonObject> out = new ArrayList<>();
        Map<String, ShareContent> contents = new java.util.LinkedHashMap<>();
        contents.put("C1_editor_export", editorExport(creativeFreestyle(), sample()));
        contents.put("C2_jmp_values_only", ShareContent.of(creativeJmp()).withCreative(Optional.of(CreativeSpec.EMPTY.with(CreativeParam.FRAME_MASS, 312.5)
                .with(CreativeParam.ARM_FRONT_LEFT_FORWARD, 88.0).with(CreativeParam.ARM_FRONT_LEFT_RIGHT, -91.5).with(CreativeParam.BATTERY_CELLS, 8.0))));
        contents.put("C3_empty_values", ShareContent.of(creativeFreestyle()).withCreative(Optional.of(CreativeSpec.EMPTY)));
        contents.put("C4_text_keys_and_raw_numbers", ShareContent.of(creativeFreestyle()).withCreative(Optional.of(new CreativeSpec(
                Map.of(PartKind.MOTOR, "otheraddon:motor_x", PartKind.BATTERY, "b6s_1300"),
                Map.of("future.lift", 1.0 / 3.0, CreativeParam.MOTOR_KV.key(), 2600.0, CreativeParam.INERTIA_ROLL.key(), 5.0E6)))));
        for (Map.Entry<String, ShareContent> entry : contents.entrySet()) {
            String code = Pw3Codec.encode(entry.getValue(), FROZEN);
            ShareDecoded decoded = ShareFormat.decode(code, FROZEN);
            JsonObject vector = new JsonObject();
            vector.addProperty("name", entry.getKey());
            vector.addProperty("code", code);
            vector.addProperty("status", decoded.status().name());
            vector.add("layers", ShareTestData.layers(decoded.layers()));
            vector.add("content", ShareTestData.content(decoded.require()));
            vector.add("creative", creative(decoded.require().creative()));
            vector.add("skipped", ShareTestData.strings(decoded.skipped()));
            out.add(vector);
        }
        Map<String, CreativeSpec> old = new java.util.LinkedHashMap<>();
        old.put("D1_pwd1_sample", sample());
        old.put("D2_pwd1_empty", CreativeSpec.EMPTY);
        old.put("D3_pwd1_unknown_key", CreativeSpec.EMPTY.withValues(Map.of("future.lift", 0.75, CreativeParam.PROP_PITCH.key(), 4.3)));
        for (Map.Entry<String, CreativeSpec> entry : old.entrySet()) {
            String code = CreativeShareCode.encode(entry.getValue());
            ShareConvert.Conversion conversion = ShareConvert.toPw3(code);
            JsonObject vector = new JsonObject();
            vector.addProperty("name", entry.getKey());
            vector.addProperty("from", code);
            vector.addProperty("status", conversion.status().name());
            vector.add("pw3", conversion.code().<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE));
            vector.add("content", ShareTestData.content(ShareFormat.decode(code, FROZEN).require()));
            vector.add("creative", creative(Optional.of(entry.getValue())));
            out.add(vector);
        }
        return out;
    }

    @Test
    void creativeGoldenVectorsAreByteIdentical() {
        JsonArray computed = new JsonArray();
        computeVectors().forEach(computed::add);
        if (ShareTestData.regenerate()) {
            JsonObject root = new JsonObject();
            root.addProperty("format", 1);
            root.addProperty("code_format", Pw3Params.FORMAT);
            root.addProperty("revision", Pw3Params.REVISION);
            root.addProperty("source", "Pw3Codec mit eingefrorenen Daten; C1–C4 Kreativ-Ebene (Erweiterung 0), D1–D3 PWD1 → ShareConvert.toPw3");
            root.addProperty("count", computed.size());
            root.add("vectors", computed);
            ShareTestData.write(FILE, root);
        }
        JsonObject golden = ShareTestData.read(FILE);
        assertEquals(Pw3Params.REVISION, golden.get("revision").getAsString());
        JsonArray expected = golden.getAsJsonArray("vectors");
        assertEquals(7, expected.size());
        assertEquals(expected.size(), computed.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), computed.get(i), expected.get(i).getAsJsonObject().get("name").getAsString());
        }
        for (JsonElement element : expected) {
            JsonObject vector = element.getAsJsonObject();
            String code = vector.has("code") ? vector.get("code").getAsString() : vector.get("pw3").getAsString();
            ShareDecoded decoded = ShareFormat.decode(code, FROZEN);
            assertEquals(vector.getAsJsonObject("creative"), creative(decoded.require().creative()), vector.get("name").getAsString());
        }
    }
}
