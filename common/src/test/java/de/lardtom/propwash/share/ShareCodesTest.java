package de.lardtom.propwash.share;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.share.ShareCodes;
import de.lardtom.propwash.api.share.ShareOptions;
import de.lardtom.propwash.api.share.ShareResult;
import de.lardtom.propwash.share.legacy.Pw2Tables;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareCodesTest {
    private static final ShareCodes CODES = PropwashApi.shareCodes();

    private static DroneData drone() {
        BuildSpec build = Pw3VectorFiles.legacy("preset/propwash:freestyle/parts").build();
        return new DroneData(build, PaintSpec.DEFAULT.with("frame", 0x1B1D20), new TuneSpec(Map.of("rates.roll.rcRate", 1.2F, "vtx.channel", 3.0F)),
                Optional.of("Night Ripper"));
    }

    @Test
    void apiUsesTheCodec() {
        assertSame(ShareCodesImpl.INSTANCE, CODES);
        String code = CODES.encode(drone(), ShareOptions.defaults());
        assertTrue(code.startsWith("PW3-"), code);
        ShareResult result = CODES.decode(code);
        assertEquals(ShareResult.Status.OK, result.status());
        assertEquals(Optional.of(ShareResult.Format.PW3), result.format());
        DroneData back = result.data().orElseThrow();
        assertEquals(drone().build(), back.build());
        assertEquals(drone().paint(), back.paint());
        assertEquals(Map.of("rates.roll.rcRate", 1.2F), back.tune().values());
        assertEquals(Optional.of("Night Ripper"), back.name());
        assertEquals(1, result.namespaces().size());
        ShareResult.NamespaceInfo info = result.namespaces().get(0);
        assertEquals("propwash", info.namespace());
        assertEquals(1, info.shortId().getAsInt());
        assertEquals(ShareResult.Check.MATCH, info.check());
        assertEquals(Optional.of("0.4.0"), info.minVersion());
        assertTrue(result.unknownParts().isEmpty());
        assertTrue(result.convertedCode().isEmpty());
    }

    @Test
    void layersAndOsdFollowTheOptions() {
        String partsOnly = CODES.encode(drone(), ShareOptions.defaults().withLayers(EnumSet.of(ShareOptions.Layer.PARTS)));
        DroneData parts = CODES.decode(partsOnly).data().orElseThrow();
        assertEquals(PaintSpec.DEFAULT, parts.paint());
        assertEquals(Optional.empty(), parts.name());
        String osdJson = Pw2Tables.osdPreset("race");
        ShareResult withOsd = CODES.decode(CODES.encode(drone(), ShareOptions.defaults().withLayers(EnumSet.allOf(ShareOptions.Layer.class)).withOsd(osdJson)));
        assertEquals(Optional.of(osdJson), withOsd.osdJson());
        ShareResult withoutJson = CODES.decode(CODES.encode(drone(), ShareOptions.defaults().withLayers(EnumSet.allOf(ShareOptions.Layer.class))));
        assertEquals(Optional.empty(), withoutJson.osdJson());
    }

    @Test
    void convertReturnsThePw3Code() {
        ShareResult converted = CODES.convert("PW205zwQBrAS4");
        assertEquals(ShareResult.Status.OK, converted.status());
        assertEquals(Optional.of(ShareResult.Format.PW2), converted.format());
        assertTrue(converted.convertedCode().orElseThrow().startsWith("PW3-"));
        assertEquals(Identifier.parse("propwash:freestyle_5"), converted.data().orElseThrow().build().frame());
        assertEquals(ShareResult.Status.CHECKSUM, CODES.convert("PW205zwQCrAS4").status());
        assertEquals(ShareResult.Status.NOT_CONVERTIBLE, CODES.convert(ShareTestData.vectors("pw2-vectors.json").get(4).get("code").getAsString()).status());
        assertEquals(ShareResult.Status.OTHER_CODE, CODES.decode("PWT1-abc").status());
        assertEquals(ShareResult.Status.EMPTY, CODES.decode(null).status());
        assertFalse(CODES.decode("PW3-abc").ok());
    }

    @Test
    void foreignAddonsAppearWithTheirNamespace() {
        DroneData data = drone().withBuild(drone().build().withPart(PartKind.FRAME, Identifier.parse("justmoreparts:deadcat_5")));
        ShareResult result = CODES.decode(CODES.encode(data, ShareOptions.defaults()));
        ShareResult.NamespaceInfo jmp = result.namespaces().get(0);
        assertEquals("justmoreparts", jmp.namespace());
        assertEquals(2, jmp.shortId().getAsInt());
        assertEquals(ShareResult.Check.MISSING, jmp.check());
        assertEquals(Optional.of("Propwash: Just More Parts"), jmp.name());
        assertEquals(Optional.of("https://modrinth.com/mod/propwash-just-more-parts"), jmp.link());
        assertTrue(jmp.trustedLink());
        assertEquals(List.of(Identifier.parse("justmoreparts:deadcat_5")), result.unknownParts());
    }

    @Test
    void linksFromTheCodeAreNeverTrusted() {
        ShareTables extra = ShareTestData.context("extra");
        String code = Pw3Codec.encode(ShareContent.of(Pw3VectorFiles.legacy("preset/propwash:freestyle/parts").build()
                .withPart(PartKind.FRAME, Identifier.parse("otheraddon:frame_x"))), extra);
        ShareResult result = CODES.decode(code);
        ShareResult.NamespaceInfo other = result.namespaces().get(0);
        assertEquals("otheraddon", other.namespace());
        assertEquals(Optional.of("Other Addon"), other.name());
        assertEquals(Optional.of("https://modrinth.com/mod/other-addon"), other.link());
        assertFalse(other.trustedLink());
    }
}
