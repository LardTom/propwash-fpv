package de.lardtom.propwash.share;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.platform.ModMeta;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MissingAddons {
    public static final String MODRINTH = "https://modrinth.com/mod/";
    public static final String CURSEFORGE = "https://www.curseforge.com/minecraft/mc-mods/";

    private MissingAddons() {
    }

    public enum Kind {
        MISSING,
        OLDER,
        MISMATCH,
        INACTIVE,
        UNKNOWN_SHORT_ID;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public String translationKey() {
            return "propwash.share.addon." + this.key();
        }
    }

    public record Link(String url, boolean clickable, boolean official) {
        public Link {
            Objects.requireNonNull(url, "url");
        }
    }

    public record Part(Identifier id, PartKind kind, Optional<String> name, int number, Optional<Identifier> fallback) {
        public Part {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            name = Objects.requireNonNullElse(name, Optional.empty());
            fallback = Objects.requireNonNullElse(fallback, Optional.empty());
        }
    }

    public record Line(Kind kind, String key, Optional<String> namespace, int shortId, String name, Optional<String> requiredVersion,
                       Optional<String> installedVersion, List<Link> links, List<Part> parts) {
        public Line {
            Objects.requireNonNull(kind, "kind");
            namespace = Objects.requireNonNullElse(namespace, Optional.empty());
            requiredVersion = Objects.requireNonNullElse(requiredVersion, Optional.empty());
            installedVersion = Objects.requireNonNullElse(installedVersion, Optional.empty());
            links = List.copyOf(links);
            parts = List.copyOf(parts);
        }

        public boolean hasClickableLink() {
            for (Link link : this.links) {
                if (link.clickable()) {
                    return true;
                }
            }
            return false;
        }
    }

    public static List<Line> of(ShareDecoded decoded, ShareTables tables, String language) {
        if (!decoded.ok()) {
            return List.of();
        }
        Map<String, Line> lines = new LinkedHashMap<>();
        Map<String, List<Part>> parts = new LinkedHashMap<>();
        for (Identifier id : positionsOf(decoded)) {
            String key = keyOf(id);
            boolean listed = decoded.unknownParts().contains(id) || decoded.inactiveParts().contains(id);
            if (listed) {
                parts.computeIfAbsent(key, k -> new ArrayList<>()).add(part(id, decoded, tables, language));
            }
        }
        for (NamespaceReport report : decoded.namespaces()) {
            Kind kind = switch (report.result()) {
                case MISSING -> report.unknownShortId() ? Kind.UNKNOWN_SHORT_ID : Kind.MISSING;
                case NEWER -> Kind.OLDER;
                case MISMATCH -> Kind.MISMATCH;
                default -> null;
            };
            if (kind == null || !report.used()) {
                continue;
            }
            lines.put(report.key(), line(kind, report.key(), report.namespace(), report.shortId(), report.codeName(), report.linkType(), report.link(),
                    report.minVersion(), tables, language, parts.getOrDefault(report.key(), List.of())));
        }
        for (Map.Entry<String, List<Part>> entry : parts.entrySet()) {
            if (lines.containsKey(entry.getKey())) {
                continue;
            }
            String key = entry.getKey();
            boolean inactive = true;
            for (Part part : entry.getValue()) {
                inactive &= decoded.inactiveParts().contains(part.id());
            }
            Optional<String> namespace = key.startsWith(Pw3Params.SHORT_KEY_PREFIX) ? Optional.empty() : Optional.of(key);
            Kind kind = inactive ? Kind.INACTIVE : namespace.map(ns -> tables.installed(ns) ? Kind.OLDER : Kind.MISSING).orElse(Kind.UNKNOWN_SHORT_ID);
            int shortId = namespace.map(tables::shortIdOf).orElseGet(() -> Integer.parseInt(key.substring(Pw3Params.SHORT_KEY_PREFIX.length())));
            lines.put(key, line(kind, key, namespace, shortId, Optional.empty(), Pw3Params.LINK_NONE, Optional.empty(), Optional.empty(), tables,
                    language, entry.getValue()));
        }
        return List.copyOf(lines.values());
    }

    private static List<Identifier> positionsOf(ShareDecoded decoded) {
        return decoded.require().build().allParts();
    }

    public static String keyOf(Identifier id) {
        return Pw3Codec.parseUnknown(id).map(Pw3Codec.Unknown::key).orElse(id.getNamespace());
    }

    private static Part part(Identifier id, ShareDecoded decoded, ShareTables tables, String language) {
        Optional<Pw3Codec.Unknown> unknown = Pw3Codec.parseUnknown(id);
        PartKind kind = unknown.map(Pw3Codec.Unknown::kind).orElseGet(() -> kindIn(decoded, id));
        Optional<String> name = tables.partNames(id).map(names -> names.getOrDefault(lang(language), names.get("en_us")));
        Optional<Identifier> fallback = tables.fallback(id);
        return new Part(id, kind, name, unknown.map(Pw3Codec.Unknown::number).orElse(0), fallback);
    }

    private static PartKind kindIn(ShareDecoded decoded, Identifier id) {
        var build = decoded.require().build();
        for (int i = 0; i < 6; i++) {
            PartKind kind = Pw3Params.KINDS.get(i);
            if (build.part(kind).equals(id)) {
                return kind;
            }
        }
        return PartKind.ACCESSORY;
    }

    private static Line line(Kind kind, String key, Optional<String> namespace, int shortId, Optional<String> codeName, int linkType,
                             Optional<String> link, Optional<String> required, ShareTables tables, String language, List<Part> parts) {
        Optional<AddonDirectory.Addon> addon = namespace.flatMap(ns -> tables.directory().byNamespace(ns));
        Optional<ModMeta> meta = namespace.flatMap(tables::mod);
        String name = addon.map(a -> a.displayName(lang(language))).or(() -> codeName).or(() -> meta.map(ModMeta::name))
                .orElse(namespace.orElse("#" + shortId));
        List<Link> links = new ArrayList<>();
        if (addon.isPresent()) {
            addon.get().modrinth().ifPresent(slug -> links.add(new Link(MODRINTH + slug, true, true)));
            addon.get().curseforge().ifPresent(slug -> links.add(new Link(CURSEFORGE + slug, true, true)));
        } else if (kind == Kind.UNKNOWN_SHORT_ID) {
            links.add(new Link(AddonDirectory.ADDONS_PAGE, true, true));
        } else if (link.isPresent()) {
            switch (linkType) {
                case Pw3Params.LINK_MODRINTH -> links.add(new Link(MODRINTH + link.get(), true, false));
                case Pw3Params.LINK_CURSEFORGE -> links.add(new Link(CURSEFORGE + link.get(), true, false));
                case Pw3Params.LINK_HTTPS -> links.add(new Link("https://" + link.get(), false, false));
                default -> {
                }
            }
        }
        Optional<String> installed = namespace.flatMap(tables::version);
        return new Line(kind, key, namespace, shortId, name, required, installed, links, parts);
    }

    private static String lang(String language) {
        return language == null ? "en_us" : language.toLowerCase(Locale.ROOT);
    }
}
