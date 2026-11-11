package de.lardtom.propwash.share;

import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissingAddonsTest {
    private static final ShareTables EXTRA = ShareTestData.context("extra");
    private static final ShareTables NEWER = ShareTestData.context("extra_newer");

    private static BuildSpec base() {
        return Pw3VectorFiles.legacy("preset/propwash:freestyle/parts").build();
    }

    private static Identifier id(String text) {
        return Identifier.parse(text);
    }

    @Test
    void missingOfficialAddonNamesItAndLinksTheStores() {
        BuildSpec build = Pw3VectorFiles.legacy("preset/justmoreparts:bash5_6s/parts").build();
        ShareDecoded decoded = ShareFormat.decode(Pw3Codec.encode(ShareContent.of(build), ShareTables.frozen()), EXTRA);
        List<MissingAddons.Line> lines = MissingAddons.of(decoded, EXTRA, "de_de");
        assertEquals(1, lines.size());
        MissingAddons.Line line = lines.get(0);
        assertEquals(MissingAddons.Kind.MISSING, line.kind());
        assertEquals("Propwash: Just More Parts", line.name());
        assertEquals(Optional.of("1.0.0"), line.requiredVersion());
        assertEquals(List.of(new MissingAddons.Link("https://modrinth.com/mod/propwash-just-more-parts", true, true),
                new MissingAddons.Link("https://www.curseforge.com/minecraft/mc-mods/propwash-just-more-parts", true, true)), line.links());
        assertEquals(build.allParts().stream().filter(p -> p.getNamespace().equals("justmoreparts")).count(), line.parts().size());
        assertEquals(Optional.of("5-Zoll-Freestyle-Rahmen (Bash)"), line.parts().get(0).name());
        assertEquals(Optional.of(id("propwash:freestyle_5")), line.parts().get(0).fallback());
        assertEquals("5″ Freestyle Frame (Bash)", MissingAddons.of(decoded, EXTRA, "en_us").get(0).parts().get(0).name().orElseThrow());
    }

    @Test
    void legacyCodesWithMissingAddonsAlsoGetALine() {
        ShareDecoded decoded = ShareFormat.decode(ShareTestData.vectors("pw2-vectors.json").get(1).get("code").getAsString(), EXTRA);
        List<MissingAddons.Line> lines = MissingAddons.of(decoded, EXTRA, "en_us");
        assertEquals(MissingAddons.Kind.MISSING, lines.get(0).kind());
        assertEquals(Optional.of("justmoreparts"), lines.get(0).namespace());
        assertTrue(lines.get(0).hasClickableLink());
    }

    @Test
    void olderAddonSaysWhichVersionIsNeeded() {
        String code = Pw3Codec.encode(ShareContent.of(base().withPart(PartKind.FRAME, id("extraparts:x_frame_3"))), NEWER);
        ShareDecoded decoded = ShareFormat.decode(code, EXTRA);
        MissingAddons.Line line = MissingAddons.of(decoded, EXTRA, "en_us").get(0);
        assertEquals(MissingAddons.Kind.OLDER, line.kind());
        assertEquals(Optional.of("1.2.0"), line.requiredVersion());
        assertEquals(Optional.of("1.1.0"), line.installedVersion());
        assertEquals("Extra Parts", line.name());
        assertEquals(3, line.parts().get(0).number());
        assertEquals(PartKind.FRAME, line.parts().get(0).kind());
        assertEquals(List.of(new MissingAddons.Link("https://modrinth.com/mod/extra-parts", true, false)), line.links());
    }

    @Test
    void inactiveAddonPartsAreReported() {
        ShareTables inactive = ShareTables.builder().installed(ShareTables.propwashTable()).installed(ShareTestData.table(ShareTestData.EXTRA_V1))
                .version("extraparts", "1.1.0").parts(new ShareTables.PartLookup() {
                    @Override
                    public Optional<PartKind> kind(Identifier id) {
                        return EXTRA.parts().kind(id);
                    }

                    @Override
                    public boolean active(Identifier id) {
                        return !id.getNamespace().equals("extraparts");
                    }
                }).build();
        String code = Pw3Codec.encode(ShareContent.of(base().withPart(PartKind.FRAME, id("extraparts:x_frame_1"))), EXTRA);
        ShareDecoded decoded = ShareFormat.decode(code, inactive);
        assertEquals(List.of(id("extraparts:x_frame_1")), decoded.inactiveParts());
        assertFalse(decoded.partsKnown());
        MissingAddons.Line line = MissingAddons.of(decoded, inactive, "de_de").get(0);
        assertEquals(MissingAddons.Kind.INACTIVE, line.kind());
        assertEquals(Optional.of(id("propwash:freestyle_5")), line.parts().get(0).fallback());
    }

    @Test
    void onlyModrinthAndCurseForgeLinksAreClickable() {
        String free = Pw3Codec.encode(ShareContent.of(base().withPart(PartKind.FRAME, id("freeaddon:fp_frame"))), EXTRA);
        ShareTables without = ShareTables.builder().installed(ShareTables.propwashTable()).build();
        MissingAddons.Line line = MissingAddons.of(ShareFormat.decode(free, without), without, "en_us").get(0);
        assertEquals(MissingAddons.Kind.MISSING, line.kind());
        assertEquals("Free Addon", line.name());
        assertEquals(List.of(new MissingAddons.Link("https://example.org/free-addon", false, false)), line.links());
        assertFalse(line.hasClickableLink());
        String modrinth = Pw3Codec.encode(ShareContent.of(base().withPart(PartKind.FRAME, id("otheraddon:frame_x"))), EXTRA);
        MissingAddons.Line other = MissingAddons.of(ShareFormat.decode(modrinth, without), without, "en_us").get(0);
        assertEquals(List.of(new MissingAddons.Link("https://modrinth.com/mod/other-addon", true, false)), other.links());
        String curse = Pw3Codec.encode(ShareContent.of(base().withPart(PartKind.MOTOR, id("betaaddon:beta_motor"))), EXTRA);
        MissingAddons.Line beta = MissingAddons.of(ShareFormat.decode(curse, without), without, "en_us").get(0);
        assertEquals(List.of(new MissingAddons.Link("https://www.curseforge.com/minecraft/mc-mods/beta-addon", true, false)), beta.links());
        assertEquals(Optional.of("1.3.0-beta.1"), beta.requiredVersion());
    }

    @Test
    void linksAreParsedConservatively() {
        assertEquals(new Pw3Codec.Link(Pw3Params.LINK_MODRINTH, Optional.of("abc-def")), Pw3Codec.link("https://modrinth.com/mod/abc-def/versions"));
        assertEquals(new Pw3Codec.Link(Pw3Params.LINK_CURSEFORGE, Optional.of("abc")), Pw3Codec.link("https://www.curseforge.com/minecraft/mc-mods/abc?x=1"));
        assertEquals(new Pw3Codec.Link(Pw3Params.LINK_HTTPS, Optional.of("example.org/x")), Pw3Codec.link("https://example.org/x"));
        assertEquals(Pw3Params.LINK_NONE, Pw3Codec.link("http://example.org").type());
        assertEquals(Pw3Params.LINK_NONE, Pw3Codec.link("https://exa mple.org").type());
        assertEquals(Pw3Params.LINK_NONE, Pw3Codec.link(null).type());
        assertEquals(Pw3Params.LINK_NONE, Pw3Codec.link("javascript:alert(1)").type());
    }
}
