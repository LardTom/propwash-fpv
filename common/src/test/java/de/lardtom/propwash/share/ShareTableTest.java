package de.lardtom.propwash.share;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.share.ShareTableCheck;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.share.legacy.Pw2Tables;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareTableTest {
    @Test
    void propwashTableCoversEveryBuiltinPart() {
        ShareTable table = ShareTables.propwashTable();
        assertEquals("propwash", table.namespace());
        assertEquals(1, table.shortId().orElseThrow());
        for (PartKind kind : Pw3Params.KINDS) {
            TreeSet<String> builtin = new TreeSet<>();
            for (PartInfo part : PartIndex.registered(kind)) {
                if (part.builtin()) {
                    builtin.add(part.id().getPath());
                }
            }
            TreeSet<String> listed = new TreeSet<>();
            table.entries(kind).forEach(entry -> listed.add(entry.id()));
            assertEquals(builtin, listed, kind.key());
        }
        assertTrue(ShareTables.coverage(Map.of("propwash", table), ShareTables.registeredParts()).isEmpty(),
                ShareTables.coverage(Map.of("propwash", table), ShareTables.registeredParts()).toString());
    }

    @Test
    void propwashTableIsOnlyAppendedAgainstTheBaseline() throws IOException {
        List<ShareTableCheck.Finding> findings = ShareTableCheck.check(ShareTestData.repo("common/src/main/resources"), "0.6.0",
                ShareTestData.resource("propwash-baseline.json"), new ShareTableCheck.Options(true));
        assertEquals(List.of(), findings);
    }

    @Test
    void propwashTableIsThePw2TableFiltered() {
        ShareTable table = ShareTables.propwashTable();
        for (PartKind kind : Pw3Params.KINDS) {
            List<String> pw2 = new ArrayList<>();
            for (String id : Pw2Tables.parts(kind)) {
                if (id.startsWith("propwash:")) {
                    pw2.add(id.substring("propwash:".length()));
                }
            }
            List<String> listed = table.entries(kind).stream().map(ShareTable.Entry::id).toList();
            assertEquals(pw2, listed.subList(0, pw2.size()), kind.key());
            for (ShareTable.Entry entry : table.entries(kind)) {
                boolean late = List.of("creative_fc", "action_cam_flashback", "gripper", "p51_29_3b").contains(entry.id());
                if (pw2.contains(entry.id())) {
                    assertEquals(late ? "0.5.0" : "0.4.0", entry.since(), entry.id());
                }
                assertTrue(entry.fallback().isEmpty(), entry.id());
                assertTrue(entry.deprecated().isEmpty(), entry.id());
            }
        }
    }

    @Test
    void toolPresetsAreThePropwashPresets() {
        assertEquals(DroneBuild.PRESETS.size(), ShareTableCheck.PROPWASH_PRESETS.size());
        for (int i = 0; i < DroneBuild.PRESETS.size(); i++) {
            DroneBuild build = DroneBuild.PRESETS.get(i);
            Map<String, String> preset = ShareTableCheck.PROPWASH_PRESETS.get(i);
            assertEquals(build.frame().id().getPath(), preset.get("frame"));
            assertEquals(build.stack().id().getPath(), preset.get("stack"));
            assertEquals(build.motor().id().getPath(), preset.get("motor"));
            assertEquals(build.prop().id().getPath(), preset.get("prop"));
            assertEquals(build.video().id().getPath(), preset.get("video"));
            assertEquals(build.battery().id().getPath(), preset.get("battery"));
        }
        ShareTableCheck.Builtin builtin = ShareTableCheck.Builtin.load();
        assertEquals(35, builtin.parts().size());
        assertEquals("justmoreparts", builtin.shortIds().get(2));
    }
}
