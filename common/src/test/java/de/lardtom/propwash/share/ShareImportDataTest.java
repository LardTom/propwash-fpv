package de.lardtom.propwash.share;

import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareImportDataTest {
    private static final BuildSpec FREESTYLE = BuildSpec.of(propwash("freestyle_5"), propwash("stack_30"), propwash("m2207_1750"), propwash("p51_3b"),
            propwash("analog_race"), propwash("b6s_1300"));
    private static final BuildSpec DEADCAT = BuildSpec.of(Identifier.fromNamespaceAndPath("cov_missing", "deadcat_5"), propwash("stack_30"),
            Identifier.fromNamespaceAndPath("cov_missing", "m22075_1850"), propwash("p51_3b"), propwash("analog_race"), propwash("b6s_1300"));
    private static final PaintSpec PAINT = PaintSpec.DEFAULT.with("frame", 0x123456);
    private static final TuneSpec TUNE = new TuneSpec(Map.of("rates.roll.rcRate", 1.2F));

    @Test
    void layersWithoutPartsCarryTheTargetBuildWhenTheCodeHasUnknownParts() {
        ShareContent code = new ShareContent(DEADCAT, Optional.of(PAINT), Optional.of(TUNE), Optional.empty(), Optional.of("Deadcat"));
        assertFalse(ShareServer.usable(DEADCAT));
        assertTrue(ShareServer.usable(FREESTYLE));
        for (EnumSet<ImportLayer> layers : List.of(EnumSet.of(ImportLayer.TUNE), EnumSet.of(ImportLayer.PAINT),
                EnumSet.of(ImportLayer.PAINT, ImportLayer.TUNE, ImportLayer.NAME))) {
            DroneData data = ShareServer.importData(code, layers, FREESTYLE);
            assertEquals(FREESTYLE, data.build(), layers.toString());
            assertEquals(PAINT, data.paint(), layers.toString());
            assertEquals(TUNE, data.tune(), layers.toString());
            assertEquals(Optional.of("Deadcat"), data.name(), layers.toString());
        }
    }

    @Test
    void partsLayerAndKnownCodesKeepTheCodeBuild() {
        ShareContent unknown = new ShareContent(DEADCAT, Optional.of(PAINT), Optional.of(TUNE), Optional.empty(), Optional.empty());
        assertEquals(unknown.toDroneData(), ShareServer.importData(unknown, EnumSet.of(ImportLayer.PARTS, ImportLayer.TUNE), FREESTYLE));
        BuildSpec known = FREESTYLE.withPart(PartKind.MOTOR, propwash("m2306_1860"));
        ShareContent own = new ShareContent(known, Optional.of(PAINT), Optional.of(TUNE), Optional.empty(), Optional.empty());
        assertTrue(ShareServer.usable(known));
        assertEquals(own.toDroneData(), ShareServer.importData(own, EnumSet.of(ImportLayer.TUNE), FREESTYLE));
    }

    private static Identifier propwash(String path) {
        return Identifier.fromNamespaceAndPath("propwash", path);
    }
}
