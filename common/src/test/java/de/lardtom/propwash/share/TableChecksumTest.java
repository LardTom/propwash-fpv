package de.lardtom.propwash.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.lardtom.propwash.api.part.PartKind;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TableChecksumTest {
    static final String FILE = "tablechecksum-vectors.json";

    static Map<String, List<TableChecksum.Line>> cases() {
        Map<String, List<TableChecksum.Line>> out = new LinkedHashMap<>();
        out.put("empty", List.of());
        out.put("one_frame", List.of(new TableChecksum.Line(PartKind.FRAME, 2, "freestyle_5")));
        out.put("freestyle_preset", List.of(new TableChecksum.Line(PartKind.FRAME, 2, "freestyle_5"), new TableChecksum.Line(PartKind.STACK, 3, "stack_30"),
                new TableChecksum.Line(PartKind.MOTOR, 4, "m2207_1750"), new TableChecksum.Line(PartKind.PROP, 4, "p51_3b"),
                new TableChecksum.Line(PartKind.VIDEO, 3, "digital_hd"), new TableChecksum.Line(PartKind.BATTERY, 4, "b6s_1300")));
        out.put("unsorted_with_duplicates", List.of(new TableChecksum.Line(PartKind.ACCESSORY, 2, "action_cam_light"),
                new TableChecksum.Line(PartKind.BATTERY, 4, "b6s_1300"), new TableChecksum.Line(PartKind.FRAME, 3, "deadcat_5"),
                new TableChecksum.Line(PartKind.ACCESSORY, 2, "action_cam_light"), new TableChecksum.Line(PartKind.ACCESSORY, 1, "action_cam_heavy"),
                new TableChecksum.Line(PartKind.MOTOR, 16, "m22075_1850")));
        out.put("jmp_bash5_6s", List.of(new TableChecksum.Line(PartKind.FRAME, 4, "fs_5_bash"), new TableChecksum.Line(PartKind.STACK, 10, "stack_30_6s_80a"),
                new TableChecksum.Line(PartKind.MOTOR, 21, "m2207_1950"), new TableChecksum.Line(PartKind.PROP, 30, "p51_20_3b_pc"),
                new TableChecksum.Line(PartKind.VIDEO, 2, "analog_race_800"), new TableChecksum.Line(PartKind.BATTERY, 17, "b6s_1800")));
        return out;
    }

    static JsonObject file() {
        JsonArray vectors = new JsonArray();
        for (Map.Entry<String, List<TableChecksum.Line>> entry : cases().entrySet()) {
            JsonObject vector = new JsonObject();
            vector.addProperty("name", entry.getKey());
            List<String> lines = new ArrayList<>();
            for (TableChecksum.Line line : entry.getValue()) {
                lines.add(line.kind().key() + ":" + line.number() + ":" + line.path());
            }
            vector.add("lines", ShareTestData.strings(lines));
            vector.addProperty("text", TableChecksum.text(entry.getValue()));
            CRC32 crc = new CRC32();
            crc.update(TableChecksum.text(entry.getValue()).getBytes(StandardCharsets.UTF_8));
            vector.addProperty("crc32", String.format("%08x", crc.getValue()));
            vector.addProperty("checksum", TableChecksum.of(entry.getValue()));
            vectors.add(vector);
        }
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.addProperty("rule", "untere 10 Bit der CRC-32 (IEEE) über die UTF-8-Zeilen <art>:<nummer>:<pfad>\\n, sortiert nach Art (frame, stack, motor, prop, video, battery, accessory) und Nummer, ohne Doppelte; lines ist die ungeordnete Eingabe");
        root.add("vectors", vectors);
        return root;
    }

    @Test
    void sharedVectorsMatch() {
        JsonObject computed = file();
        if (ShareTestData.regenerate()) {
            ShareTestData.write(FILE, computed);
        }
        assertEquals(ShareTestData.read(FILE), computed);
    }

    @Test
    void ruleIsIndependentlyReproducible() {
        String text = "frame:2:freestyle_5\n";
        CRC32 crc = new CRC32();
        crc.update(text.getBytes(StandardCharsets.UTF_8));
        assertEquals((int) (crc.getValue() & 0x3FF), TableChecksum.of(List.of(new TableChecksum.Line(PartKind.FRAME, 2, "freestyle_5"))));
        assertEquals(0, TableChecksum.of(List.of()));
        List<TableChecksum.Line> unsorted = cases().get("unsorted_with_duplicates");
        assertEquals("frame:3:deadcat_5\nmotor:16:m22075_1850\nbattery:4:b6s_1300\naccessory:1:action_cam_heavy\naccessory:2:action_cam_light\n",
                TableChecksum.text(unsorted));
        assertThrows(IllegalArgumentException.class, () -> new TableChecksum.Line(PartKind.FRAME, 0, "x"));
    }

    @Test
    void appendingToTheTableKeepsTheChecksum() {
        ShareTables v1 = ShareTestData.context("extra");
        ShareTables v2 = ShareTestData.context("extra_newer");
        ShareContent content = ShareContent.of(Pw3VectorFiles.legacy("preset/propwash:freestyle/parts").build()
                .withPart(PartKind.FRAME, Pw3VectorFiles.id("extraparts:x_frame_2")));
        String code = Pw3Codec.encode(content, v1);
        assertEquals(NamespaceReport.Result.MATCH, ShareFormat.decode(code, v2).namespaces().get(0).result());
        assertEquals(NamespaceReport.Result.MATCH, ShareFormat.decode(code, v1).namespaces().get(0).result());
    }
}
