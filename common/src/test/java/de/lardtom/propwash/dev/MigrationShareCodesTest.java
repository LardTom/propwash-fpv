package de.lardtom.propwash.dev;

import com.google.gson.JsonObject;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.share.AddonDirectory;
import de.lardtom.propwash.share.Pw3Carry;
import de.lardtom.propwash.share.Pw3Codec;
import de.lardtom.propwash.share.ShareContent;
import de.lardtom.propwash.share.ShareDecoded;
import de.lardtom.propwash.share.ShareFormat;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareStatus;
import de.lardtom.propwash.share.ShareTable;
import de.lardtom.propwash.share.ShareTables;
import de.lardtom.propwash.share.ShareVectors;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationShareCodesTest {
    private static final String PATH = "drones[uuid=00000000-0000-0000-0000-000000000001]";
    private static final ShareTables TABLES = tables();
    private static final MigrationValues.ShareAccess OFFLINE = new MigrationValues.ShareAccess() {
        @Override
        public String export(DroneData data, Collection<ShareLayer> layers) {
            EnumSet<ShareLayer> chosen = EnumSet.of(ShareLayer.PARTS);
            chosen.addAll(layers);
            return Pw3Codec.encode(ShareContent.of(data, chosen, Optional.empty()), TABLES, Pw3Codec.Options.DEFAULT, Pw3Carry.EMPTY);
        }

        @Override
        public ShareDecoded decode(String code) {
            return ShareFormat.decode(code, TABLES);
        }
    };

    private static ShareTables tables() {
        ShareTables frozen = ShareTables.frozen();
        Map<Identifier, PartKind> kinds = new HashMap<>();
        for (PartInfo part : PartIndex.registered()) {
            if (part.builtin()) {
                kinds.put(part.id(), part.definition().kind());
            }
        }
        ShareTables.Builder builder = ShareTables.builder().directory(AddonDirectory.builtin());
        for (ShareTable table : frozen.installedTables().values()) {
            builder.installed(table);
        }
        builder.parts(id -> {
            PartKind known = kinds.get(id);
            if (known != null) {
                return Optional.of(known);
            }
            ShareTable table = frozen.installedTables().get(id.getNamespace());
            return table == null ? Optional.empty() : table.find(id).map(ShareTable.Located::kind);
        });
        return builder.build();
    }

    private static JsonObject codes(String pw2) {
        JsonObject codes = new JsonObject();
        codes.addProperty("pw2", pw2);
        codes.addProperty("layers", "[PARTS, PAINT, TUNE, NAME]");
        return codes;
    }

    private static DroneData legacy(String code) {
        return ShareFormat.decode(code, ShareTables.frozen(), false).require().toDroneData();
    }

    @Test
    void droneWithItsOldCodeExportsAndConvertsToTheSameText() {
        DroneData data = legacy(ShareVectors.PW2_NAMED);
        MigrationValues.ShareCheck check = MigrationValues.shareCheck(PATH, data, codes(ShareVectors.PW2_NAMED), OFFLINE);
        assertEquals(List.of(), check.failures());
        assertEquals(List.of("pw2 → " + ShareVectors.PW2_NAMED_PW3), check.conversions());
        assertEquals(1, check.sameText());
        assertTrue(check.export().startsWith("PW3-"), check.export());
        assertEquals(ShareStatus.OK, OFFLINE.decode(check.export()).status());
    }

    @Test
    void droneWithJustMorePartsExportsAndReadsBack() {
        MigrationValues.ShareCheck check = MigrationValues.shareCheck(PATH, DroneData.of(ShareVectors.jmpBuild()), null, OFFLINE);
        assertEquals(List.of(), check.failures());
        assertEquals(ShareVectors.jmpBuild(), OFFLINE.decode(check.export()).require().build());
        assertEquals(0, check.converted());
    }

    @Test
    void droneWithOtherPartsThanItsOldCodeIsADifference() {
        DroneData data = legacy(ShareVectors.PW2_NAMED);
        Identifier frame = data.build().frame().equals(Identifier.parse("propwash:race_5")) ? Identifier.parse("propwash:freestyle_5")
                : Identifier.parse("propwash:race_5");
        DroneData other = new DroneData(data.build().withPart(PartKind.FRAME, frame), data.paint(), data.tune(), data.name());
        MigrationValues.ShareCheck check = MigrationValues.shareCheck(PATH, other, codes(ShareVectors.PW2_NAMED), OFFLINE);
        assertEquals(1, check.failures().size(), String.valueOf(check.failures()));
        assertTrue(check.failures().getFirst().contains("mit anderen Teilen als die Drohne in der Welt"), check.failures().getFirst());
    }

    @Test
    void exportOtherThanTheConvertedOldCodeIsADifference() {
        DroneData data = legacy(ShareVectors.PW2_NAMED);
        DroneData renamed = new DroneData(data.build(), data.paint(), data.tune(), Optional.of("Umbenannt"));
        MigrationValues.ShareCheck check = MigrationValues.shareCheck(PATH, renamed, codes(ShareVectors.PW2_NAMED), OFFLINE);
        assertEquals(1, check.failures().size(), String.valueOf(check.failures()));
        assertTrue(check.failures().getFirst().contains("ist nicht derselbe Text wie pw2 umgerechnet " + ShareVectors.PW2_NAMED_PW3),
                check.failures().getFirst());
    }

    @Test
    void brokenOldCodeIsADifference() {
        DroneData data = legacy(ShareVectors.PW2_NAMED);
        String broken = ShareVectors.PW2_NAMED.substring(0, ShareVectors.PW2_NAMED.length() - 4);
        MigrationValues.ShareCheck check = MigrationValues.shareCheck(PATH, data, codes(broken), OFFLINE);
        assertEquals(1, check.failures().size(), String.valueOf(check.failures()));
        assertTrue(check.failures().getFirst().contains("alter Code pw2 nicht umgerechnet"), check.failures().getFirst());
    }

    @Test
    void failingExportIsADifference() {
        MigrationValues.ShareAccess failing = new MigrationValues.ShareAccess() {
            @Override
            public String export(DroneData data, Collection<ShareLayer> layers) {
                throw new IllegalStateException("kein Export");
            }

            @Override
            public ShareDecoded decode(String code) {
                return OFFLINE.decode(code);
            }
        };
        MigrationValues.ShareCheck check = MigrationValues.shareCheck(PATH, DroneData.of(ShareVectors.freestyle()), null, failing);
        assertEquals(List.of(PATH + ": PW3-Export scheitert: java.lang.IllegalStateException: kein Export"), check.failures());
        assertEquals("", check.export());
    }
}
