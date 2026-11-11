package de.lardtom.propwash.share;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.platform.ModMeta;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

public final class ShareVectors {
    public static final String P1 = "PW3-02lVAldkcxCBH";
    public static final String P2_JMP = "PW3-012k1VwVtRUzVyI1ZQauW";
    public static final String P8_FREE_LINK = "PW3-066SQPqXhiAfElYWMN0al3PTQdNnLd1pL23zjZSNGes8tmJdkOaMg9Mb5feZmT6FYeM8LpRK8";
    public static final String P10_CHECKSUM = "PW3-02lVAldkcxCHB";
    public static final String NAMED_WHOOP = "PW3-0Qrgmm3Vrwu7BmBGyzRkDxxPh7PFgND2kDVUl5KebVEk4Qm6UEyT";
    public static final String NAMED_WHOOP_NAME = "Night Ripper";
    public static final String PW1_A = "PW1-04002-GR11D-K74SB-5EDT7-JV35B-WTG22-3KEHG-P6TTZ-6CR02-2KD68-S30DT-Z64VK-AC010-SR3AC-AZ6DH-022V1-DSGPR-VV7BX-S62RV-"
            + "50446-4DKKB-WRK6C-1G000-H23NQ";
    public static final String PW1_A_PW3 = "PW3-02lFCh4hDN48p";
    public static final String PW2_NAMED = "PW20Mn79Bv4eLz4q6TdnqMsQYHBAGZbT0EmgXDhYncMcCi52Zgu";
    public static final String PW2_NAMED_PW3 = "PW3-0QqA6y3zkoXOlOkqJFF3okJKtXBIgUzD86VQIj5n883cddQ7wD3v";
    public static final String MISSING_NAMESPACE = "propwash_missing";
    public static final String MISSING_SLUG = "propwash-missing";
    public static final String MISSING_NAME = "Propwash Missing Add-on";
    public static final String MISSING_VERSION = "1.2.0";
    public static final String JMP = "justmoreparts";

    private ShareVectors() {
    }

    public static BuildSpec freestyle() {
        for (DronePreset preset : PropwashApi.builds().presets()) {
            if (preset.id().equals(Constants.id("freestyle"))) {
                return preset.data().build();
            }
        }
        return BuildSpec.of(Constants.id("freestyle_5"), Constants.id("stack_30"), Constants.id("m2207_1750"), Constants.id("p51_3b"),
                Constants.id("analog_race"), Constants.id("b6s_1300"));
    }

    public static BuildSpec jmpBuild() {
        return new BuildSpec(Identifier.fromNamespaceAndPath(JMP, "fs_5_bash"), Identifier.fromNamespaceAndPath(JMP, "stack_30_6s_60a"),
                Identifier.fromNamespaceAndPath(JMP, "m2207_1950"), Identifier.fromNamespaceAndPath(JMP, "p130_41_3b_soft"),
                Identifier.fromNamespaceAndPath(JMP, "digital_hd_pro"), Constants.id("b6s_1300"), Map.of(AccessorySlot.FRONT, Constants.id("action_cam_light")));
    }

    public static boolean jmpLoaded() {
        return PropwashApi.parts().get(jmpBuild().frame()).isPresent();
    }

    public static ShareTables missingTables() {
        ShareTables current = ShareTables.current();
        ShareTables.Builder builder = ShareTables.builder().directory(current.directory()).parts(id -> {
            if (id.getNamespace().equals(MISSING_NAMESPACE)) {
                return Optional.of(PartKind.FRAME);
            }
            return ShareTables.REGISTERED.kind(id);
        });
        current.installedTables().values().forEach(builder::installed);
        builder.version(MISSING_NAMESPACE, MISSING_VERSION).mod(MISSING_NAMESPACE, new ModMeta(MISSING_NAMESPACE, MISSING_NAME, MISSING_VERSION,
                MissingAddons.MODRINTH + MISSING_SLUG, false));
        return builder.build();
    }

    public static String missingCode() {
        BuildSpec build = freestyle().withPart(PartKind.FRAME, Identifier.fromNamespaceAndPath(MISSING_NAMESPACE, "frame_x"));
        return Pw3Codec.encode(new ShareContent(build, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("Fehlt-Probe")), missingTables(),
                Pw3Codec.Options.DEFAULT, Pw3Carry.EMPTY);
    }

    public static String mismatchCode() {
        String json;
        try (InputStream in = ShareTables.class.getResourceAsStream(ShareTables.TABLE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Tabelle von Propwash fehlt");
            }
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        String a = "{\"id\": \"m2207_1750\"";
        String b = "{\"id\": \"m2306_1860\"";
        String swapped = json.replace(a, "\u0000").replace(b, a).replace("\u0000", b);
        ShareTableJson.Parsed parsed = ShareTableJson.parse(ShareTables.PROPWASH, swapped);
        ShareTable fake = parsed.table().orElseThrow(() -> new IllegalStateException("Probe-Tabelle ungültig: " + parsed.problems()));
        ShareTables tables = ShareTables.builder().installed(fake).directory(ShareTables.current().directory()).parts(ShareTables.REGISTERED).build();
        return Pw3Codec.encode(ShareContent.of(freestyle()), tables, Pw3Codec.Options.NO_INFO, Pw3Carry.EMPTY);
    }
}
