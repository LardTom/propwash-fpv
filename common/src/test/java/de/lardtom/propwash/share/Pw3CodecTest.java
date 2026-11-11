package de.lardtom.propwash.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.creative.CreativeParam;
import de.lardtom.propwash.creative.CreativeShareCode;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.share.legacy.Pw2Tables;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pw3CodecTest {
    private static final ShareTables FROZEN = ShareTables.frozen();
    private static final ShareTables EXTRA = ShareTestData.context("extra");
    private static final ShareTables NEWER = ShareTestData.context("extra_newer");

    private static Identifier id(String text) {
        return Identifier.parse(text);
    }

    private static BuildSpec freestyle() {
        return BuildSpec.of(id("propwash:freestyle_5"), id("propwash:stack_30"), id("propwash:m2207_1750"), id("propwash:p51_3b"),
                id("propwash:digital_hd"), id("propwash:b6s_1300"));
    }

    private static String frame(BitWriter writer) {
        return Pw3Params.PREFIX + Base62.encode(LayerCodec.frame(writer.toByteArray()));
    }

    @Test
    void goldenVectorsAreByteIdentical() {
        JsonArray computed = Pw3VectorFiles.compute();
        if (ShareTestData.regenerate()) {
            ShareTestData.write(Pw3VectorFiles.FILE, Pw3VectorFiles.file(computed));
        }
        JsonObject golden = ShareTestData.read(Pw3VectorFiles.FILE);
        assertEquals(ShareTestData.contexts(), golden.getAsJsonObject("contexts"));
        JsonArray expected = golden.getAsJsonArray("vectors");
        assertEquals(11 + 135, expected.size());
        assertEquals(expected.size(), computed.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), computed.get(i), expected.get(i).getAsJsonObject().get("name").getAsString());
        }
    }

    @Test
    void goldenVectorsDecodeAndEncodeBack() {
        for (JsonObject vector : ShareTestData.vectors(Pw3VectorFiles.FILE)) {
            String name = vector.get("name").getAsString();
            ShareTables decodeContext = ShareTestData.context(vector.get("decode_context").getAsString());
            ShareDecoded decoded = ShareFormat.decode(vector.get("code").getAsString(), decodeContext);
            assertEquals(vector.get("status").getAsString(), decoded.status().name(), name);
            if (!vector.get("encode_context").isJsonNull()) {
                ShareContent content = ShareTestData.content(vector.getAsJsonObject("encode_content"));
                assertEquals(vector.get("code").getAsString(), Pw3Codec.encode(content, ShareTestData.context(vector.get("encode_context").getAsString())), name);
            }
            if (decoded.ok() && decoded.renamed().isEmpty()) {
                String again = Pw3Codec.encode(decoded.require(), decodeContext, Pw3Codec.Options.DEFAULT, decoded.carry());
                String expected = vector.get("code").getAsString();
                assertEquals(expected.startsWith(Pw3Params.PREFIX) ? expected : Pw3Params.PREFIX + expected, again, name);
            }
        }
    }

    @Test
    void appendixBCoversEveryCase() {
        Map<String, JsonObject> byName = new TreeMap<>();
        for (JsonObject vector : ShareTestData.vectors(Pw3VectorFiles.FILE)) {
            byName.put(vector.get("name").getAsString().split("_")[0], vector);
        }
        JsonObject p4 = byName.get("P4");
        JsonObject head = p4.getAsJsonArray("namespaces").get(0).getAsJsonObject();
        assertEquals("otheraddon", head.get("namespace").getAsString());
        assertEquals(0, head.get("short_id").getAsInt());
        assertFalse(head.get("table").getAsBoolean());
        assertEquals("Other Addon", head.get("name").getAsString());
        assertEquals(Pw3Params.LINK_MODRINTH, head.get("link_type").getAsInt());
        assertEquals("other-addon", head.get("link").getAsString());
        assertEquals("NO_TABLE", head.get("result").getAsString());
        JsonObject p5 = byName.get("P5").getAsJsonArray("namespaces").get(0).getAsJsonObject();
        assertEquals("extraparts", p5.get("namespace").getAsString());
        assertTrue(p5.get("table").getAsBoolean());
        assertEquals("MATCH", p5.get("result").getAsString());
        assertEquals("1.1.0", p5.get("min_version").getAsString());
        assertEquals("extraparts:x_cam", byName.get("P5").getAsJsonObject("content").getAsJsonObject("build").getAsJsonObject("accessories").get("top")
                .getAsString());
        JsonObject p6 = byName.get("P6");
        assertEquals("pw3.unknown:extraparts/frame/3", p6.getAsJsonObject("content").getAsJsonObject("build").get("frame").getAsString());
        assertEquals("NEWER", p6.getAsJsonArray("namespaces").get(0).getAsJsonObject().get("result").getAsString());
        assertEquals("1.2.0", p6.getAsJsonArray("namespaces").get(0).getAsJsonObject().get("min_version").getAsString());
        JsonObject p7 = byName.get("P7");
        assertEquals("extraparts:new_motor", p7.getAsJsonObject("content").getAsJsonObject("build").get("motor").getAsString());
        assertEquals("extraparts:old_motor", p7.getAsJsonArray("renamed").get(0).getAsJsonObject().get("from").getAsString());
        JsonObject p8 = byName.get("P8").getAsJsonArray("namespaces").get(0).getAsJsonObject();
        assertEquals(Pw3Params.LINK_HTTPS, p8.get("link_type").getAsInt());
        assertEquals("example.org/free-addon", p8.get("link").getAsString());
        JsonObject p9 = byName.get("P9").getAsJsonArray("namespaces").get(1).getAsJsonObject();
        assertEquals("1.3.0-beta.1", p9.get("min_version").getAsString());
        assertEquals(Pw3Params.LINK_CURSEFORGE, p9.get("link_type").getAsInt());
        assertEquals("CHECKSUM", byName.get("P10").get("status").getAsString());
        assertEquals("OK", byName.get("P11").get("status").getAsString());
        assertEquals(byName.get("P2").get("content"), byName.get("P11").get("content"));
        assertEquals(byName.get("P2").get("code").getAsString(), Pw3Params.PREFIX + byName.get("P11").get("code").getAsString());
        JsonObject p2 = byName.get("P2");
        assertEquals(2, p2.getAsJsonArray("namespaces").get(0).getAsJsonObject().get("short_id").getAsInt());
        assertEquals("1.0.0", p2.getAsJsonArray("namespaces").get(0).getAsJsonObject().get("min_version").getAsString());
        assertEquals("0.4.0", p2.getAsJsonArray("namespaces").get(1).getAsJsonObject().get("min_version").getAsString());
    }

    @Test
    void measuredLengthsStayWithinAChatLine() {
        int max = 0;
        for (JsonObject vector : ShareTestData.vectors(Pw3VectorFiles.FILE)) {
            max = Math.max(max, vector.get("length").getAsInt());
        }
        assertTrue(max <= 140, "längster Vektor " + max);
    }

    private static final List<String> FOREIGN_NAMESPACES = List.of("otheraddon", "a.b-c_d", "zz", "x".repeat(64));
    private static final List<String> FOREIGN_PATHS = List.of("frame_x", "a", "x.y-z_0", "p".repeat(128), "sub/dir");

    static ShareContent randomContent(Random random, int round, boolean withUnknownFrame) {
        Identifier[] six = new Identifier[6];
        for (int i = 0; i < 6; i++) {
            PartKind kind = Pw3Params.KINDS.get(i);
            six[i] = randomPart(random, kind);
        }
        if (withUnknownFrame) {
            six[0] = id("extraparts:x_frame_3");
        }
        EnumMap<AccessorySlot, Identifier> accessories = new EnumMap<>(AccessorySlot.class);
        for (AccessorySlot slot : Pw3Params.ACCESSORY_SLOTS) {
            if (random.nextBoolean()) {
                accessories.put(slot, randomPart(random, PartKind.ACCESSORY));
            }
        }
        BuildSpec build = new BuildSpec(six[0], six[1], six[2], six[3], six[4], six[5], accessories);
        PaintSpec paint = PaintSpec.DEFAULT;
        for (String slot : PaintSpec.SLOTS) {
            if (random.nextInt(3) == 0) {
                paint = paint.with(slot, switch (random.nextInt(3)) {
                    case 0 -> Pw3Params.COLORS.get(random.nextInt(Pw3Params.COLORS.size()));
                    case 1 -> 0x1B1D20;
                    default -> random.nextInt(0x1000000);
                });
            }
        }
        Map<String, Float> tune = new TreeMap<>();
        int entries = random.nextInt(40);
        for (int i = 0; i < entries; i++) {
            String key = random.nextInt(4) == 0 ? "pid.custom" + random.nextInt(100) : Pw3Params.TUNE_KEYS.get(random.nextInt(Pw3Params.TUNE_KEYS.size()));
            float value = switch (random.nextInt(5)) {
                case 0 -> (float) (random.nextInt(4001) * 5 / 1000.0);
                case 1 -> random.nextFloat() * 1000.0F - 500.0F;
                case 2 -> (float) (random.nextInt(2001) / 10.0 - 100.0);
                case 3 -> (float) random.nextGaussian();
                default -> (float) random.nextInt(2000);
            };
            tune.put(key, value);
        }
        Optional<ShareOsd> osd = switch (random.nextInt(5)) {
            case 0 -> Optional.of(new ShareOsd(1 + random.nextInt(3), "{\"v\":1,\"x\":\"ä€😀" + round + "\"}"));
            case 1 -> Optional.of(new ShareOsd(1, Pw2Tables.osdPreset(List.of("full", "standard", "race", "minimal").get(random.nextInt(4)))
                    .replace("\"row\":" + random.nextInt(10), "\"row\":" + random.nextInt(15))));
            default -> Optional.empty();
        };
        Optional<String> name = switch (random.nextInt(4)) {
            case 0 -> Optional.of("Drohne Nr. " + round + " – ßü");
            case 1 -> Optional.of("Quad " + round);
            case 2 -> Optional.of("Q#" + round + "!");
            default -> Optional.empty();
        };
        return new ShareContent(build, random.nextBoolean() ? Optional.of(paint) : Optional.empty(),
                random.nextBoolean() ? Optional.of(new TuneSpec(tune)) : Optional.empty(), osd, name);
    }

    private static Identifier randomPart(Random random, PartKind kind) {
        int choice = random.nextInt(6);
        if (choice <= 1) {
            List<ShareTable.Entry> entries = ShareTables.propwashTable().entries(kind);
            return id("propwash:" + entries.get(random.nextInt(entries.size())).id());
        }
        if (choice == 2 && kind != PartKind.ACCESSORY) {
            List<ShareTable.Entry> entries = FROZEN.table("justmoreparts").orElseThrow().entries(kind);
            return id("justmoreparts:" + entries.get(random.nextInt(entries.size())).id());
        }
        if (choice == 3 && (kind == PartKind.FRAME)) {
            return id("extraparts:x_frame_" + (1 + random.nextInt(2)));
        }
        if (choice == 3 && kind == PartKind.MOTOR) {
            return id("extraparts:new_motor");
        }
        if (choice == 4) {
            String namespace = random.nextBoolean() ? "propwash" : "justmoreparts";
            return Identifier.fromNamespaceAndPath(namespace, "not_in_table_" + random.nextInt(5));
        }
        return Identifier.fromNamespaceAndPath(FOREIGN_NAMESPACES.get(random.nextInt(FOREIGN_NAMESPACES.size())),
                FOREIGN_PATHS.get(random.nextInt(FOREIGN_PATHS.size())));
    }

    @Test
    void tenThousandRandomBuildsRoundTrip() {
        Random random = new Random(20261018L);
        int unknownRounds = 0;
        for (int round = 0; round < 10000; round++) {
            boolean unknown = round % 10 == 0;
            ShareContent content = randomContent(random, round, unknown);
            String code = Pw3Codec.encode(content, unknown ? NEWER : EXTRA);
            assertTrue(code.startsWith(Pw3Params.PREFIX), code);
            ShareDecoded decoded = ShareFormat.decode(code, EXTRA);
            assertEquals(ShareStatus.OK, decoded.status(), code);
            if (unknown) {
                unknownRounds++;
                assertEquals(id("pw3.unknown:extraparts/frame/3"), decoded.require().build().frame(), code);
                assertEquals(content.withBuild(content.build().withPart(PartKind.FRAME, id("pw3.unknown:extraparts/frame/3"))), decoded.require(), code);
                assertFalse(decoded.carry().isEmpty());
                assertEquals(code, Pw3Codec.encode(decoded.require(), EXTRA, Pw3Codec.Options.DEFAULT, decoded.carry()), code);
                assertEquals(content, ShareFormat.decode(code, NEWER).require(), code);
            } else {
                assertEquals(content, decoded.require(), code);
                assertEquals(List.copyOf(content.layers()), decoded.layers(), code);
                assertEquals(code, Pw3Codec.encode(decoded.require(), EXTRA), code);
            }
            assertTrue(code.length() <= Pw3Params.MAX_INPUT_CHARS);
        }
        assertEquals(1000, unknownRounds);
    }

    @Test
    void unknownEntriesNeedTheUnchangedHead() {
        ShareContent content = ShareContent.of(freestyle().withPart(PartKind.FRAME, id("extraparts:x_frame_3")).withPart(PartKind.MOTOR, id("extraparts:new_motor")));
        String code = Pw3Codec.encode(content, NEWER);
        ShareDecoded decoded = ShareFormat.decode(code, EXTRA);
        assertEquals(code, Pw3Codec.encode(decoded.require(), EXTRA, Pw3Codec.Options.DEFAULT, decoded.carry()));
        ShareContent changed = decoded.require().withBuild(decoded.require().build().withPart(PartKind.BATTERY, id("propwash:b6s_1100")));
        ShareContent removed = decoded.require().withBuild(decoded.require().build().withPart(PartKind.MOTOR, id("propwash:m2306_1860")));
        assertThrows(ShareException.class, () -> Pw3Codec.encode(removed, EXTRA, Pw3Codec.Options.DEFAULT, decoded.carry()));
        ShareContent otherExtra = decoded.require().withBuild(decoded.require().build().withAccessory(AccessorySlot.TOP, id("extraparts:x_frame_1")));
        ShareException error = assertThrows(ShareException.class, () -> Pw3Codec.encode(otherExtra, EXTRA, Pw3Codec.Options.DEFAULT, decoded.carry()));
        assertEquals(ShareStatus.UNKNOWN_ENTRIES, error.status());
        assertEquals("propwash.share.error.unknown_entries", error.key());
        assertNotNull(Pw3Codec.encode(changed, EXTRA, Pw3Codec.Options.DEFAULT, decoded.carry()));
        assertThrows(ShareException.class, () -> Pw3Codec.encode(decoded.require(), EXTRA));
        assertThrows(ShareException.class, () -> Pw3Codec.encode(ShareContent.of(freestyle().withPart(PartKind.FRAME, id("pw2.unknown:frame.200"))), EXTRA));
        assertThrows(ShareException.class, () -> Pw3Codec.encode(ShareContent.of(freestyle().withPart(PartKind.FRAME, id("pw1.unknown.7:x"))), EXTRA));
    }

    @Test
    void unknownShortIdsStayReadable() {
        BitWriter w = new BitWriter();
        w.bits(0, 5).golomb(1, 0);
        w.golomb(1, Pw3Params.K_SHORT_ID).bit(true).bits(TableChecksum.of(List.of(new TableChecksum.Line(PartKind.STACK, 3, "stack_30"))), 10).bit(false).bit(false);
        w.golomb(9, Pw3Params.K_SHORT_ID).bit(true).bits(77, 10).bit(true).golomb(2, 0).golomb(0, 0).golomb(1, 0).bit(false).bit(false);
        w.bits(1, 1).golomb(4, Pw3Params.K_PART);
        w.bits(0, 1).golomb(3, Pw3Params.K_PART);
        w.bits(1, 1).golomb(0, Pw3Params.K_PART);
        IdText.write(w, "big_motor", 128);
        w.bits(1, 1).golomb(2, Pw3Params.K_PART);
        w.bits(1, 1).golomb(1, Pw3Params.K_PART);
        w.bits(1, 1).golomb(1, Pw3Params.K_PART);
        w.golomb(0, 0);
        String code = frame(w);
        ShareDecoded decoded = ShareFormat.decode(code, FROZEN);
        assertEquals(ShareStatus.OK, decoded.status(), code);
        assertEquals(id("pw3.unknown:pw3.short.9/frame/4"), decoded.require().build().frame());
        assertEquals(id("pw3.unknown:pw3.short.9/motor/0/big_motor"), decoded.require().build().motor());
        assertEquals("?frame.4", Pw3Codec.display(decoded.require().build().frame()));
        NamespaceReport report = decoded.namespaces().get(1);
        assertTrue(report.unknownShortId());
        assertEquals(9, report.shortId());
        assertEquals(NamespaceReport.Result.MISSING, report.result());
        assertEquals(Optional.of("2.0.1"), report.minVersion());
        assertEquals(NamespaceReport.Result.MATCH, decoded.namespaces().get(0).result());
        String again = Pw3Codec.encode(decoded.require(), FROZEN, Pw3Codec.Options.NO_INFO, decoded.carry());
        ShareDecoded back = ShareFormat.decode(again, FROZEN);
        assertEquals(decoded.require(), back.require());
        assertEquals(decoded.carry(), back.carry());
        assertEquals(again, Pw3Codec.encode(back.require(), FROZEN, Pw3Codec.Options.NO_INFO, back.carry()));
        List<MissingAddons.Line> lines = MissingAddons.of(ShareFormat.decode(code, ShareTables.builtin()), ShareTables.builtin(), "de_de");
        assertEquals(1, lines.size());
        assertEquals(MissingAddons.Kind.UNKNOWN_SHORT_ID, lines.get(0).kind());
        assertEquals(AddonDirectory.ADDONS_PAGE, lines.get(0).links().get(0).url());
    }

    private static ShareTable appended(ShareTable table, PartKind kind, String path, String since) {
        Map<PartKind, List<ShareTable.Entry>> parts = new EnumMap<>(PartKind.class);
        for (PartKind k : PartKind.values()) {
            parts.put(k, new ArrayList<>(table.entries(k)));
        }
        parts.get(kind).add(ShareTable.Entry.of(path, since));
        return new ShareTable(table.namespace(), table.shortId(), parts);
    }

    private static Identifier entry(ShareTable table, PartKind kind, int index) {
        return table.id(table.entries(kind).get(index));
    }

    private static void rewritesUnchanged(String code, ShareTables reader, String namespace, Identifier unknown, PartKind kind,
                                          BuildSpec otherKnown, BuildSpec otherNamespace) {
        ShareDecoded decoded = ShareFormat.decode(code, reader);
        assertEquals(ShareStatus.OK, decoded.status(), code);
        assertEquals(unknown, decoded.require().build().part(kind), code);
        NamespaceReport report = decoded.namespaces().stream().filter(r -> r.namespace().equals(Optional.of(namespace))).findFirst().orElseThrow();
        assertTrue(report.shortId() > 0, code);
        assertEquals(reader.installed(namespace) ? NamespaceReport.Result.NEWER : NamespaceReport.Result.MISSING, report.result(), code);
        assertTrue(decoded.carry().head(namespace).isPresent(), code);
        assertEquals(code, Pw3Codec.encode(decoded.require(), reader, Pw3Codec.Options.DEFAULT, decoded.carry()), code);
        ShareException error = assertThrows(ShareException.class,
                () -> Pw3Codec.encode(decoded.require().withBuild(otherKnown), reader, Pw3Codec.Options.DEFAULT, decoded.carry()), code);
        assertEquals(ShareStatus.UNKNOWN_ENTRIES, error.status(), code);
        String again = Pw3Codec.encode(decoded.require().withBuild(otherNamespace), reader, Pw3Codec.Options.DEFAULT, decoded.carry());
        assertNotEquals(code, again, code);
        ShareDecoded back = ShareFormat.decode(again, reader);
        assertEquals(ShareStatus.OK, back.status(), again);
        assertEquals(otherNamespace, back.require().build(), again);
        assertEquals(again, Pw3Codec.encode(back.require(), reader, Pw3Codec.Options.DEFAULT, back.carry()), again);
    }

    @Test
    void carryKeepsKnownPartsBesideUnknownNumbersUnderShortIds() {
        ShareTable jmp = ShareTables.builtin().table("justmoreparts").orElseThrow();
        ShareTables jmpNewer = ShareTables.builder().installed(ShareTables.propwashTable()).installed(appended(jmp, PartKind.MOTOR, "m0702_40000", "1.3.0"))
                .directory(AddonDirectory.builtin()).version("justmoreparts", "1.3.0").build();
        ShareTables jmpOlder = ShareTables.builder().installed(ShareTables.propwashTable()).directory(AddonDirectory.builtin()).version("justmoreparts", "1.2.0")
                .build();
        BuildSpec jmpBuild = freestyle().withPart(PartKind.FRAME, entry(jmp, PartKind.FRAME, 0)).withPart(PartKind.STACK, entry(jmp, PartKind.STACK, 0))
                .withPart(PartKind.MOTOR, id("justmoreparts:m0702_40000"));
        String jmpCode = Pw3Codec.encode(ShareContent.of(jmpBuild), jmpNewer);
        assertEquals("PW3-0FgYoLNsCT6UJympsOG", jmpCode);
        Identifier unknownMotor = id("pw3.unknown:justmoreparts/motor/" + (jmp.size(PartKind.MOTOR) + 1));
        BuildSpec read = jmpBuild.withPart(PartKind.MOTOR, unknownMotor);
        for (ShareTables reader : List.of(FROZEN, jmpOlder, ShareTables.builtin())) {
            rewritesUnchanged(jmpCode, reader, "justmoreparts", unknownMotor, PartKind.MOTOR,
                    read.withPart(PartKind.FRAME, entry(jmp, PartKind.FRAME, 1)), read.withPart(PartKind.BATTERY, id("propwash:b6s_1100")));
        }
        ShareTable propwash = ShareTables.propwashTable();
        ShareTables propwashNewer = ShareTables.builder().installed(appended(propwash, PartKind.FRAME, "future_frame", "0.7.0")).directory(AddonDirectory.builtin())
                .build();
        ShareTables propwashNow = ShareTables.builder().installed(propwash).directory(AddonDirectory.builtin()).build();
        String propwashCode = Pw3Codec.encode(ShareContent.of(freestyle().withPart(PartKind.FRAME, id("propwash:future_frame"))), propwashNewer);
        assertEquals("PW3-02oZS2VKfohRJ", propwashCode);
        Identifier unknownFrame = id("pw3.unknown:propwash/frame/" + (propwash.size(PartKind.FRAME) + 1));
        BuildSpec readFrame = freestyle().withPart(PartKind.FRAME, unknownFrame);
        for (ShareTables reader : List.of(FROZEN, propwashNow, ShareTables.builtin(), EXTRA)) {
            rewritesUnchanged(propwashCode, reader, "propwash", unknownFrame, PartKind.FRAME, readFrame.withPart(PartKind.MOTOR, id("propwash:m2306_1860")),
                    readFrame.withAccessory(AccessorySlot.TOP, id("otheraddon:frame_x")));
        }
        BuildSpec withAccessory = freestyle().withPart(PartKind.FRAME, id("propwash:future_frame")).withAccessory(AccessorySlot.TOP, entry(propwash, PartKind.ACCESSORY, 0))
                .withAccessory(AccessorySlot.FRONT, entry(propwash, PartKind.ACCESSORY, 2));
        String accessoryCode = Pw3Codec.encode(ShareContent.of(withAccessory), propwashNewer);
        BuildSpec readAccessory = withAccessory.withPart(PartKind.FRAME, unknownFrame);
        for (ShareTables reader : List.of(FROZEN, propwashNow)) {
            rewritesUnchanged(accessoryCode, reader, "propwash", unknownFrame, PartKind.FRAME,
                    readAccessory.withAccessory(AccessorySlot.TOP, entry(propwash, PartKind.ACCESSORY, 1)),
                    readAccessory.withAccessory(AccessorySlot.BOTTOM, id("otheraddon:frame_x")));
        }
    }

    @Test
    void everyStatusIsReachable() {
        String p1 = Pw3Codec.encode(ShareContent.of(freestyle()), FROZEN);
        String body = p1.substring(4);
        Set<ShareStatus> seen = EnumSet.noneOf(ShareStatus.class);
        Map<String, ShareStatus> cases = new TreeMap<>();
        cases.put(p1, ShareStatus.OK);
        cases.put("", ShareStatus.EMPTY);
        cases.put(" \n\t ", ShareStatus.EMPTY);
        cases.put("hello world!", ShareStatus.INVALID_PREFIX);
        cases.put("PW3-" + body.substring(0, 3) + "_" + body.substring(4), ShareStatus.BAD_CHARACTER);
        cases.put("PW3-" + "0".repeat(Pw3Params.MAX_INPUT_CHARS), ShareStatus.TOO_LONG);
        cases.put("PW3-", ShareStatus.TRUNCATED);
        cases.put("PW3-0000", ShareStatus.TRUNCATED);
        cases.put(Pw3VectorFiles.swapped(p1), ShareStatus.CHECKSUM);
        cases.put("PW4-" + body, ShareStatus.UNSUPPORTED_VERSION);
        cases.put("pw9-abc", ShareStatus.UNSUPPORTED_VERSION);
        cases.put("PW1-AB!CD", ShareStatus.BAD_CHARACTER);
        cases.put("PW3-" + "0".repeat(Base62.symbolsFor(Pw3Params.MAX_FRAME_BYTES + 1)), ShareStatus.TOO_LARGE);
        cases.put("PW3-" + "z".repeat(Base62.symbolsFor(5)), ShareStatus.CORRUPT);
        cases.put("PWT1-ABCDEF", ShareStatus.OTHER_CODE);
        cases.put("pwd1-abc", ShareStatus.CORRUPT);
        cases.put(CreativeShareCode.encode(CreativeSpec.EMPTY.with(CreativeParam.MOTOR_KV, 2600.0)), ShareStatus.OK);
        for (Map.Entry<String, ShareStatus> entry : cases.entrySet()) {
            ShareDecoded decoded = ShareFormat.decode(entry.getKey(), FROZEN);
            assertEquals(entry.getValue(), decoded.status(), entry.getKey().length() > 60 ? entry.getKey().substring(0, 60) : entry.getKey());
            seen.add(decoded.status());
        }
        assertEquals(ShareStatus.EMPTY, ShareFormat.decode(null, FROZEN).status());
        assertEquals("PWT1", ShareFormat.decode("PWT1-x", FROZEN).format());
        assertEquals("PWD1", ShareFormat.decode("PWD1-x", FROZEN).format());
        assertEquals(ShareStatus.TOO_LONG, ShareFormat.decode("x".repeat(Pw3Params.HARD_INPUT_LIMIT + 1), FROZEN).status());
        seen.add(ShareConvert.toPw3(ShareTestData.vectors("pw2-vectors.json").get(4).get("code").getAsString()).status());
        try {
            Pw3Codec.encode(ShareFormat.decode(Pw3Codec.encode(ShareContent.of(freestyle().withPart(PartKind.FRAME, id("extraparts:x_frame_3"))), NEWER), EXTRA)
                    .require(), EXTRA);
        } catch (ShareException e) {
            seen.add(e.status());
        }
        assertEquals(EnumSet.allOf(ShareStatus.class), seen);
        BitWriter padded = new BitWriter();
        padded.bits(0, 5).golomb(0, 0).golomb(1, 1).bit(true).bits(0, 10).bit(false).bit(false);
        for (int i = 0; i < 6; i++) {
            padded.golomb(1, Pw3Params.K_PART);
        }
        padded.golomb(0, 0);
        byte[] bytes = padded.toByteArray();
        assertEquals(ShareStatus.OK, ShareFormat.decode(Pw3Params.PREFIX + Base62.encode(LayerCodec.frame(bytes)), FROZEN).status());
        bytes[bytes.length - 1] |= 1;
        assertEquals(ShareStatus.CORRUPT, ShareFormat.decode(Pw3Params.PREFIX + Base62.encode(LayerCodec.frame(bytes)), FROZEN).status());
        byte[] longer = Arrays.copyOf(padded.toByteArray(), padded.toByteArray().length + 1);
        assertEquals(ShareStatus.CORRUPT, ShareFormat.decode(Pw3Params.PREFIX + Base62.encode(LayerCodec.frame(longer)), FROZEN).status());
    }

    @Test
    void inputIsReadTolerantly() {
        String code = Pw3Codec.encode(ShareContent.of(freestyle()), FROZEN);
        String body = code.substring(4);
        ShareContent expected = ShareFormat.decode(code, FROZEN).require();
        for (String variant : List.of("pw3-" + body, "PW3" + body, "Pw3-" + body, body, "  PW3-" + body.substring(0, 5) + "\n" + body.substring(5) + "  ",
                "PW3-" + body.substring(0, 4) + "-" + body.substring(4, 9) + "-" + body.substring(9), " " + code + " ")) {
            ShareDecoded decoded = ShareFormat.decode(variant, FROZEN);
            assertEquals(ShareStatus.OK, decoded.status(), variant);
            assertEquals(expected, decoded.require(), variant);
            assertEquals("PW3", decoded.format());
        }
        assertNotEquals(ShareStatus.OK, ShareFormat.decode("PW3-" + body.toLowerCase(), FROZEN).status());
        assertEquals(ShareStatus.INVALID_PREFIX, ShareFormat.decode(body.substring(1), FROZEN).status());
    }

    @Test
    void limitsAreEnforced() {
        BuildSpec base = freestyle();
        ShareContent longNamespace = ShareContent.of(base.withPart(PartKind.FRAME, Identifier.fromNamespaceAndPath("n".repeat(64), "p".repeat(128))));
        assertEquals(longNamespace, ShareFormat.decode(Pw3Codec.encode(longNamespace, EXTRA), EXTRA).require());
        assertThrows(ShareException.class, () -> Pw3Codec.encode(ShareContent.of(base.withPart(PartKind.FRAME, Identifier.fromNamespaceAndPath("n".repeat(65), "x"))), EXTRA));
        assertThrows(ShareException.class, () -> Pw3Codec.encode(ShareContent.of(base.withPart(PartKind.FRAME, Identifier.fromNamespaceAndPath("ok", "p".repeat(129)))), EXTRA));
        assertThrows(ShareException.class, () -> Pw3Codec.encode(ShareContent.of(base.withPart(PartKind.FRAME, id("pw3.short.4:x"))), EXTRA));
        Map<String, Float> many = new TreeMap<>();
        for (int i = 0; i < 100; i++) {
            many.put("pid.k" + i, (float) i);
        }
        ShareContent full = new ShareContent(base, Optional.empty(), Optional.of(new TuneSpec(many)), Optional.of(new ShareOsd(1, "{" + "x".repeat(8190) + "}")),
                Optional.of("n".repeat(48)));
        assertEquals(full, ShareFormat.decode(Pw3Codec.encode(full, EXTRA), EXTRA).require());
        assertThrows(IllegalArgumentException.class, () -> new ShareOsd(1, "x".repeat(8193)));
        assertThrows(IllegalArgumentException.class, () -> new ShareContent(base, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("n".repeat(49))));
        StringBuilder noise = new StringBuilder();
        Random random = new Random(1);
        while (noise.length() < 8190) {
            noise.append((char) ('!' + random.nextInt(90)));
        }
        String json = "{" + noise.toString().replace("\"", "").replace("\\", "") + "}";
        ShareContent big = new ShareContent(base, Optional.empty(), Optional.of(new TuneSpec(many)), Optional.of(new ShareOsd(1, json)), Optional.empty());
        ShareException tooLarge = assertThrows(ShareException.class, () -> Pw3Codec.encode(big, EXTRA));
        assertEquals(ShareStatus.TOO_LARGE, tooLarge.status());
        Map<String, Float> limit = new TreeMap<>();
        for (int i = 0; i < 257; i++) {
            limit.put("pid.k" + i, 1.0F);
        }
        assertThrows(IllegalArgumentException.class, () -> new TuneSpec(limit));
        BitWriter tooMany = new BitWriter();
        tooMany.bits(0, 5).golomb(16, 0);
        assertEquals(ShareStatus.CORRUPT, ShareFormat.decode(frame(tooMany), FROZEN).status());
        BitWriter propwashText = new BitWriter();
        propwashText.bits(0, 5).golomb(0, 0).golomb(0, 1);
        IdText.write(propwashText, "propwash", 64);
        assertEquals(ShareStatus.CORRUPT, ShareFormat.decode(frame(propwashText), FROZEN).status());
        BitWriter reserved = new BitWriter();
        reserved.bits(0, 5).golomb(0, 0).golomb(0, 1);
        IdText.write(reserved, "pw3.unknown", 64);
        assertEquals(ShareStatus.CORRUPT, ShareFormat.decode(frame(reserved), FROZEN).status());
        BitWriter duplicate = new BitWriter();
        duplicate.bits(0, 5).golomb(1, 0).golomb(1, 1).bit(false).bit(false).bit(false).golomb(1, 1).bit(false).bit(false).bit(false);
        assertEquals(ShareStatus.CORRUPT, ShareFormat.decode(frame(duplicate), FROZEN).status());
        BitWriter longSuffix = new BitWriter();
        longSuffix.bits(0, 5).golomb(0, 0).golomb(1, 1).bit(false).bit(true).golomb(1, 0).golomb(0, 0).golomb(0, 0).bit(true);
        LayerCodec.writeText(longSuffix, "a".repeat(33));
        assertEquals(ShareStatus.CORRUPT, ShareFormat.decode(frame(longSuffix), FROZEN).status());
    }

    @Test
    void codesNameEveryNamespaceOnce() {
        BuildSpec mixed = freestyle().withPart(PartKind.FRAME, id("justmoreparts:deadcat_5")).withPart(PartKind.MOTOR, id("justmoreparts:m22075_1850"))
                .withAccessory(AccessorySlot.TOP, id("propwash:action_cam_light")).withAccessory(AccessorySlot.FRONT, id("otheraddon:cam"));
        ShareDecoded decoded = ShareFormat.decode(Pw3Codec.encode(ShareContent.of(mixed), EXTRA), EXTRA);
        assertEquals(List.of("justmoreparts", "propwash", "otheraddon"), decoded.namespaces().stream().map(NamespaceReport::key).toList());
        assertEquals(2, decoded.namespaces().get(0).shortId());
        assertEquals(1, decoded.namespaces().get(1).shortId());
        assertEquals(0, decoded.namespaces().get(2).shortId());
        assertEquals(NamespaceReport.Result.MISSING, decoded.namespaces().get(0).result());
        assertEquals(List.of(id("justmoreparts:deadcat_5"), id("justmoreparts:m22075_1850")), decoded.unknownParts().subList(0, 2));
    }

    @Test
    void checksumMismatchIsReported() {
        ShareTable shuffled = ShareTestData.table(ShareTestData.EXTRA_V1.replace("x_frame_1", "tmp").replace("x_frame_2", "x_frame_1").replace("tmp", "x_frame_2"));
        ShareTables other = ShareTables.builder().installed(ShareTables.propwashTable()).installed(shuffled).version("extraparts", "1.1.0").build();
        String code = Pw3Codec.encode(ShareContent.of(freestyle().withPart(PartKind.FRAME, id("extraparts:x_frame_2"))), EXTRA);
        ShareDecoded decoded = ShareFormat.decode(code, other);
        assertEquals(ShareStatus.OK, decoded.status());
        assertEquals(NamespaceReport.Result.MISMATCH, decoded.namespaces().get(0).result());
        assertTrue(decoded.partsLocked());
        assertEquals(MissingAddons.Kind.MISMATCH, MissingAddons.of(decoded, other, "en_us").get(0).kind());
        assertFalse(ShareFormat.decode(code, EXTRA).partsLocked());
    }

    @Test
    void hundredThousandDamagedInputsNeverThrow() {
        Random random = new Random(7);
        List<byte[]> seeds = new ArrayList<>();
        for (JsonObject vector : ShareTestData.vectors(Pw3VectorFiles.FILE)) {
            if (vector.get("status").getAsString().equals("OK")) {
                String code = vector.get("code").getAsString();
                try {
                    seeds.add(Base62.decode(code.startsWith(Pw3Params.PREFIX) ? code.substring(4) : code));
                } catch (ShareFormatException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        int ok = 0;
        Set<ShareStatus> seen = EnumSet.noneOf(ShareStatus.class);
        List<ShareTables> contexts = List.of(FROZEN, EXTRA, NEWER);
        for (int round = 0; round < 80000; round++) {
            byte[] frame = seeds.get(random.nextInt(seeds.size())).clone();
            int body = frame.length - 2;
            int flips = 1 + random.nextInt(4);
            for (int i = 0; i < flips; i++) {
                int at = random.nextInt(body);
                frame[at] = (byte) (random.nextInt(4) == 0 ? random.nextInt(256) : frame[at] ^ (1 << random.nextInt(8)));
            }
            byte[] payload = Arrays.copyOf(frame, random.nextInt(5) == 0 ? 1 + random.nextInt(body) : body);
            String code = Pw3Params.PREFIX + Base62.encode(LayerCodec.frame(payload));
            ShareTables context = contexts.get(random.nextInt(contexts.size()));
            ShareDecoded decoded = ShareFormat.decode(code, context);
            assertNotNull(decoded);
            seen.add(decoded.status());
            if (decoded.ok()) {
                ok++;
                try {
                    assertNotNull(Pw3Codec.encode(decoded.require(), context, Pw3Codec.Options.DEFAULT, decoded.carry()));
                } catch (ShareException e) {
                    assertNotNull(e.status());
                }
                assertNotNull(MissingAddons.of(decoded, context, "de_de"));
            }
            assertNotNull(ShareConvert.toPw3(code));
        }
        for (int round = 0; round < 20000; round++) {
            StringBuilder text = new StringBuilder(List.of("PW3-", "PW2", "PW1-", "pw3", "", "PW").get(random.nextInt(6)));
            int length = random.nextInt(300);
            String alphabet = Base62.ALPHABET + "-_ \n!äß";
            for (int i = 0; i < length; i++) {
                text.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            assertNotNull(ShareFormat.decode(text.toString(), FROZEN));
            assertNotNull(ShareConvert.toPw3(text.toString()));
        }
        assertTrue(ok > 0);
        assertTrue(seen.contains(ShareStatus.CORRUPT) && seen.contains(ShareStatus.TRUNCATED), seen.toString());
    }

    @Test
    void base62AndIdTextAreStable() throws ShareFormatException {
        int previous = -1;
        for (int bytes = 0; bytes <= 2600; bytes++) {
            int symbols = Base62.symbolsFor(bytes);
            assertTrue(symbols > previous);
            assertEquals(bytes, Base62.bytesFor(symbols));
            previous = symbols;
        }
        assertEquals("00", Base62.encode(new byte[]{0}));
        assertEquals(40, Pw3Params.ID_ALPHABET.length());
        BitWriter w = new BitWriter();
        IdText.write(w, "justmoreparts", 64);
        BitReader r = new BitReader(w.toByteArray(), w.toByteArray().length);
        assertEquals("justmoreparts", IdText.read(r, 64));
        assertEquals(IdText.bits("justmoreparts"), w.length());
        assertFalse(IdText.encodable("Upper", 64));
        assertFalse(IdText.encodable("", 64));
        BitWriter bad = new BitWriter();
        bad.golomb(0, 3).bits(45, 6);
        assertThrows(ShareFormatException.class, () -> IdText.read(new BitReader(bad.toByteArray(), bad.toByteArray().length), 64));
    }

    @Test
    void sharedLayersAreBitIdenticalToPw2() throws ShareFormatException {
        int compared = 0;
        for (JsonObject vector : ShareTestData.vectors("pw2-vectors.json")) {
            if (!vector.get("status").getAsString().equals("OK") || !vector.get("skipped").getAsJsonArray().isEmpty()) {
                continue;
            }
            ShareContent content = ShareTestData.content(vector.getAsJsonObject("content"));
            if (content.build().allParts().stream().anyMatch(Pw3Codec::unknownMarker)) {
                continue;
            }
            String pw3 = Pw3Codec.encode(content, FROZEN);
            assertEquals(layerBits(vector.get("code").getAsString().substring(3), false), layerBits(pw3.substring(4), true), vector.get("name").getAsString());
            compared++;
        }
        assertTrue(compared > 400, "verglichen: " + compared);
    }

    private static String layerBits(String symbols, boolean pw3) throws ShareFormatException {
        byte[] frame = Base62.decode(symbols);
        BitReader in = new BitReader(frame, frame.length - 2);
        in.bits(5);
        if (pw3) {
            int count = (int) in.golomb(0) + 1;
            boolean[] tables = new boolean[count];
            for (int i = 0; i < count; i++) {
                if (in.golomb(1) == 0) {
                    IdText.read(in, 64);
                }
                tables[i] = in.bit();
                if (tables[i]) {
                    in.bits(10);
                }
                if (in.bit()) {
                    in.golomb(0);
                    in.golomb(0);
                    in.golomb(0);
                    if (in.bit()) {
                        LayerCodec.readText(in, 32);
                    }
                }
                if (in.bit()) {
                    LayerCodec.readText(in, 48);
                    int type = (int) in.bits(2);
                    if (type == 1 || type == 2) {
                        IdText.read(in, 64);
                    } else if (type == 3) {
                        LayerCodec.readText(in, 200);
                    }
                }
            }
            int bits = LayerCodec.indexBits(count);
            for (int i = 0; i < 6; i++) {
                skipPw3Ref(in, tables, bits);
            }
            long accessories = in.golomb(0);
            for (int i = 0; i < accessories; i++) {
                in.golomb(0);
                skipPw3Ref(in, tables, bits);
            }
        } else {
            for (int i = 0; i < 6; i++) {
                skipPw2Ref(in);
            }
            long accessories = in.golomb(0);
            for (int i = 0; i < accessories; i++) {
                in.golomb(0);
                skipPw2Ref(in);
            }
        }
        StringBuilder out = new StringBuilder();
        while (in.remaining() > 0) {
            out.append(in.bit() ? '1' : '0');
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == '0') {
            end--;
        }
        return out.substring(0, end);
    }

    private static void skipPw3Ref(BitReader in, boolean[] tables, int bits) throws ShareFormatException {
        int index = (int) in.bits(bits);
        if (!tables[index] || in.golomb(3) == 0) {
            IdText.read(in, 128);
        }
    }

    private static void skipPw2Ref(BitReader in) throws ShareFormatException {
        if (in.golomb(4) == 0) {
            LayerCodec.readText(in, 64);
            LayerCodec.readText(in, 128);
        }
    }
}
