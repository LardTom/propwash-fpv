package de.lardtom.propwash.share;

import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.share.ShareCodes;
import de.lardtom.propwash.api.share.ShareOptions;
import de.lardtom.propwash.api.share.ShareResult;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public final class ShareCodesImpl implements ShareCodes {
    public static final ShareCodesImpl INSTANCE = new ShareCodesImpl();

    private ShareCodesImpl() {
    }

    @Override
    public ShareResult decode(String text) {
        return result(ShareService.decode(text), Optional.empty());
    }

    @Override
    public String encode(DroneData data, ShareOptions options) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(options, "options");
        EnumSet<ShareLayer> layers = EnumSet.of(ShareLayer.PARTS);
        for (ShareOptions.Layer layer : options.layers()) {
            layers.add(ShareLayer.valueOf(layer.name()));
        }
        return ShareService.encode(data, layers, options.addonInfo(), options.osdJson());
    }

    @Override
    public ShareResult convert(String legacyCode) {
        ShareConvert.Conversion conversion = ShareService.convert(legacyCode);
        if (!conversion.ok()) {
            return new ShareResult(status(conversion.status()), format(conversion.format()), Optional.empty(), Optional.empty(), List.of(), List.of(),
                    Optional.empty());
        }
        ShareResult read = result(ShareService.decode(conversion.code().orElseThrow()), conversion.code());
        return new ShareResult(read.status(), format(conversion.format()), read.data(), read.osdJson(), read.namespaces(), read.unknownParts(),
                read.convertedCode());
    }

    static ShareResult result(ShareDecoded decoded, Optional<String> converted) {
        if (decoded.ok() && ShareFormat.DRONE_EDITOR.equals(decoded.format())) {
            return new ShareResult(ShareResult.Status.OTHER_CODE, Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(),
                    Optional.empty());
        }
        if (!decoded.ok()) {
            return new ShareResult(status(decoded.status()), format(decoded.format()), Optional.empty(), Optional.empty(), List.of(), List.of(),
                    Optional.empty());
        }
        ShareTables tables = ShareTables.current();
        List<ShareResult.NamespaceInfo> namespaces = new ArrayList<>();
        for (NamespaceReport report : decoded.namespaces()) {
            Optional<AddonDirectory.Addon> addon = report.namespace().flatMap(ns -> tables.directory().byNamespace(ns));
            Optional<String> name = addon.map(a -> a.displayName("en_us")).or(report::codeName);
            Optional<String> link = Optional.empty();
            boolean trusted = false;
            if (addon.isPresent() && addon.get().modrinth().isPresent()) {
                link = Optional.of(MissingAddons.MODRINTH + addon.get().modrinth().get());
                trusted = true;
            } else if (report.link().isPresent()) {
                link = Optional.of(switch (report.linkType()) {
                    case Pw3Params.LINK_MODRINTH -> MissingAddons.MODRINTH + report.link().get();
                    case Pw3Params.LINK_CURSEFORGE -> MissingAddons.CURSEFORGE + report.link().get();
                    default -> "https://" + report.link().get();
                });
            }
            namespaces.add(new ShareResult.NamespaceInfo(report.key(), report.shortId() > 0 ? OptionalInt.of(report.shortId()) : OptionalInt.empty(),
                    report.installedVersion(), report.minVersion(), ShareResult.Check.valueOf(report.result().name()), name, link, trusted));
        }
        ShareContent content = decoded.require();
        return new ShareResult(ShareResult.Status.OK, format(decoded.format()), Optional.of(content.toDroneData()), content.osd().map(ShareOsd::json),
                namespaces, decoded.unknownParts(), converted);
    }

    static ShareResult.Status status(ShareStatus status) {
        for (ShareResult.Status value : ShareResult.Status.values()) {
            if (value.name().equals(status.name())) {
                return value;
            }
        }
        return ShareResult.Status.CORRUPT;
    }

    static Optional<ShareResult.Format> format(String format) {
        for (ShareResult.Format value : ShareResult.Format.values()) {
            if (value.name().equals(format)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
