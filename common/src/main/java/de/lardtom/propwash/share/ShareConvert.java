package de.lardtom.propwash.share;

import de.lardtom.propwash.share.legacy.Pw1Tables;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;

public final class ShareConvert {
    private ShareConvert() {
    }

    public record Conversion(ShareStatus status, String format, Optional<String> code, Optional<String> reason) {
        public Conversion {
            Objects.requireNonNull(status, "status");
            format = Objects.requireNonNullElse(format, "");
            code = Objects.requireNonNullElse(code, Optional.empty());
            reason = Objects.requireNonNullElse(reason, Optional.empty());
        }

        public boolean ok() {
            return this.status.ok();
        }

        public boolean converted() {
            return this.ok() && !this.format.equals(Pw3Params.FORMAT);
        }
    }

    public static Conversion toPw3(String code) {
        return toPw3(code, ShareTables.frozen());
    }

    public static Conversion toPw3(String code, ShareTables frozen) {
        ShareFormat.Detected detected = ShareFormat.detect(code);
        if (!detected.status().ok()) {
            return new Conversion(detected.status(), detected.format(), Optional.empty(), Optional.empty());
        }
        ShareDecoded decoded = ShareFormat.decode(code, frozen, false);
        if (!decoded.ok()) {
            return new Conversion(decoded.status(), decoded.format(), Optional.empty(), Optional.empty());
        }
        if (decoded.format().equals(Pw3Params.FORMAT)) {
            String body = ShareFormat.decode(Pw3Params.PREFIX + detected.symbols(), frozen, false).ok() ? detected.symbols() : ShareFormat.compact(code);
            return new Conversion(ShareStatus.OK, Pw3Params.FORMAT, Optional.of(Pw3Params.PREFIX + body), Optional.empty());
        }
        ShareContent content = decoded.require();
        for (Identifier id : content.build().allParts()) {
            Optional<String> reason = notConvertible(id);
            if (reason.isPresent()) {
                return new Conversion(ShareStatus.NOT_CONVERTIBLE, decoded.format(), Optional.empty(), reason);
            }
        }
        try {
            return new Conversion(ShareStatus.OK, decoded.format(), Optional.of(Pw3Codec.encode(content, frozen, Pw3Codec.Options.NO_INFO, Pw3Carry.EMPTY)),
                    Optional.empty());
        } catch (ShareException e) {
            return new Conversion(ShareStatus.NOT_CONVERTIBLE, decoded.format(), Optional.empty(), Optional.of(e.key()));
        }
    }

    static Optional<String> notConvertible(Identifier id) {
        String namespace = id.getNamespace();
        if (namespace.equals("pw2.unknown")) {
            return Optional.of("pw2_unknown:" + id.getPath());
        }
        if (Pw1Tables.unknownNamespaceIndex(id).isPresent() || namespace.startsWith(Pw1Tables.UNKNOWN_NAMESPACE_PREFIX)) {
            return Optional.of("pw1_unknown:" + namespace.substring(Pw1Tables.UNKNOWN_NAMESPACE_PREFIX.length()));
        }
        if (namespace.startsWith(Pw3Params.RESERVED_PREFIX)) {
            return Optional.of("reserved_namespace:" + namespace);
        }
        return Optional.empty();
    }
}
