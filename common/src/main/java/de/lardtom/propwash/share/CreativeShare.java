package de.lardtom.propwash.share;

import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.creative.CreativeShareCode;
import de.lardtom.propwash.creative.CreativeSpec;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CreativeShare {
    public static final Identifier CREATIVE_FC = Identifier.fromNamespaceAndPath(ShareTables.PROPWASH, "creative_fc");
    public static final BuildSpec PWD1_BUILD = BuildSpec.of(id("freestyle_5"), CREATIVE_FC, id("m2207_1750"), id("p51_3b"), id("digital_hd"),
            id("b6s_1300"));

    public static final Set<ShareLayer> EDITOR_EXPORT = Collections.unmodifiableSet(EnumSet.of(ShareLayer.PARTS, ShareLayer.PAINT, ShareLayer.TUNE,
            ShareLayer.NAME, ShareLayer.CREATIVE));

    public enum PasteStatus {
        OK,
        NO_VALUES,
        INVALID
    }

    public record Pasted(PasteStatus status, Optional<CreativeSpec> spec, String format) {
        public Pasted {
            Objects.requireNonNull(status, "status");
            spec = Objects.requireNonNullElse(spec, Optional.empty());
            format = Objects.requireNonNullElse(format, "");
        }

        public boolean ok() {
            return this.status == PasteStatus.OK;
        }

        public boolean legacy() {
            return ShareFormat.DRONE_EDITOR.equals(this.format);
        }
    }

    private CreativeShare() {
    }

    public static String editorCode(DroneData data, CreativeSpec spec, boolean addonInfo) {
        return ShareService.encode(data, Optional.of(spec), EDITOR_EXPORT, addonInfo, Optional.empty());
    }

    public static Pasted paste(String code) {
        return paste(code, ShareTables.current());
    }

    public static Pasted paste(String code, ShareTables tables) {
        Optional<CreativeSpec> old = CreativeShareCode.decode(code);
        if (old.isPresent()) {
            return new Pasted(PasteStatus.OK, old, ShareFormat.DRONE_EDITOR);
        }
        ShareDecoded decoded = ShareFormat.decode(code, tables);
        if (!decoded.ok()) {
            return new Pasted(PasteStatus.INVALID, Optional.empty(), decoded.format());
        }
        Optional<CreativeSpec> spec = decoded.require().creative();
        return new Pasted(spec.isPresent() ? PasteStatus.OK : PasteStatus.NO_VALUES, spec, decoded.format());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ShareTables.PROPWASH, path);
    }

    public static ShareParsed readPwd1(String code) throws ShareFormatException {
        Optional<CreativeSpec> spec = CreativeShareCode.decode(code);
        if (spec.isEmpty()) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        ShareParsed parsed = new ShareParsed();
        parsed.layers.add(ShareLayer.PARTS);
        parsed.layers.add(ShareLayer.CREATIVE);
        parsed.parts = new Identifier[]{PWD1_BUILD.frame(), PWD1_BUILD.stack(), PWD1_BUILD.motor(), PWD1_BUILD.prop(), PWD1_BUILD.video(),
                PWD1_BUILD.battery()};
        parsed.creative = spec.get();
        return parsed;
    }

    public static boolean creativeStack(BuildSpec build) {
        return CREATIVE_FC.equals(build.stack());
    }

    public static boolean creativeDrone(ShareContent content) {
        return content.creative().isPresent() || creativeStack(content.build());
    }

    public static int values(CreativeSpec spec) {
        return spec.values().size();
    }

    public static int baseParts(CreativeSpec spec) {
        return spec.base().size();
    }
}
