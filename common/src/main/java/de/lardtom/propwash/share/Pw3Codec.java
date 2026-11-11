package de.lardtom.propwash.share;

import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
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
import java.util.OptionalInt;

public final class Pw3Codec {
    private Pw3Codec() {
    }

    public enum InfoMode {
        AUTO,
        ALWAYS,
        NEVER
    }

    public record Options(InfoMode info) {
        public static final Options DEFAULT = new Options(InfoMode.AUTO);
        public static final Options NO_INFO = new Options(InfoMode.NEVER);

        public Options {
            Objects.requireNonNull(info, "info");
        }
    }

    record Ref(String position, PartKind kind, Identifier id) {
    }

    record Value(int number, String path) {
        String text(String position) {
            return position + "=" + this.number + (this.path == null ? "" : "/" + this.path);
        }
    }

    record Head(int shortId, String namespace, boolean hasTable, int checksum, Optional<ShareVersion> version, Optional<Pw3Carry.Info> info) {
    }

    record Unknown(String key, PartKind kind, int number, String path) {
    }

    public static String encode(ShareContent content, ShareTables tables) {
        return encode(content, tables, Options.DEFAULT, Pw3Carry.EMPTY);
    }

    public static String encode(ShareContent content, ShareTables tables, Options options, Pw3Carry carry) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(tables, "tables");
        Objects.requireNonNull(options, "options");
        carry = Objects.requireNonNullElse(carry, Pw3Carry.EMPTY);
        List<Ref> refs = refs(content.build());
        LinkedHashMap<String, List<Ref>> groups = new LinkedHashMap<>();
        for (Ref ref : refs) {
            groups.computeIfAbsent(keyOf(ref.id()), key -> new ArrayList<>()).add(ref);
        }
        if (groups.size() > Pw3Params.MAX_NAMESPACES) {
            throw new ShareException(ShareStatus.TOO_LARGE, "propwash.share.error.too_many_namespaces", groups.size(), Pw3Params.MAX_NAMESPACES);
        }
        List<String> keys = new ArrayList<>(groups.keySet());
        List<Head> heads = new ArrayList<>();
        Map<String, Value> values = new LinkedHashMap<>();
        for (String key : keys) {
            List<Ref> group = groups.get(key);
            boolean carried = false;
            for (Ref ref : group) {
                carried |= ref.id().getNamespace().equals(Pw3Params.UNKNOWN_NAMESPACE);
            }
            if (carried) {
                heads.add(carriedHead(key, group, tables, carry, values));
            } else {
                heads.add(freshHead(key, group, tables, options, values));
            }
        }
        BitWriter out = new BitWriter();
        out.bit(content.paint().isPresent()).bit(content.tune().isPresent()).bit(content.osd().isPresent()).bit(content.name().isPresent())
                .bit(content.creative().isPresent());
        out.golomb(heads.size() - 1L, Pw3Params.K_NAMESPACE_COUNT);
        for (Head head : heads) {
            writeHead(out, head);
        }
        int indexBits = LayerCodec.indexBits(heads.size());
        int accessories = refs.size() - 6;
        for (int i = 0; i < 6; i++) {
            writeRef(out, refs.get(i), keys, heads, values, indexBits);
        }
        out.golomb(accessories, Pw3Params.K_ACCESSORY_COUNT);
        for (int i = 6; i < refs.size(); i++) {
            Ref ref = refs.get(i);
            AccessorySlot slot = AccessorySlot.byKey(ref.position().substring("accessory:".length())).orElseThrow();
            out.golomb(Pw3Params.accessoryIndex(slot), Pw3Params.K_ACCESSORY_SLOT);
            writeRef(out, ref, keys, heads, values, indexBits);
        }
        content.paint().ifPresent(paint -> LayerCodec.writePaint(out, paint));
        content.tune().ifPresent(tune -> LayerCodec.writeTune(out, tune));
        content.osd().ifPresent(osd -> LayerCodec.writeOsd(out, osd));
        content.name().ifPresent(name -> LayerCodec.writeName(out, name));
        writeExtensions(out, content, keys);
        byte[] frame = LayerCodec.frame(out.toByteArray());
        if (frame.length > Pw3Params.MAX_FRAME_BYTES) {
            throw new ShareException(ShareStatus.TOO_LARGE, "propwash.share.error.too_large", frame.length, Pw3Params.MAX_FRAME_BYTES);
        }
        return Pw3Params.PREFIX + Base62.encode(frame);
    }

    static List<Ref> refs(BuildSpec build) {
        List<Ref> refs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            PartKind kind = Pw3Params.KINDS.get(i);
            refs.add(new Ref(kind.key(), kind, build.part(kind)));
        }
        for (AccessorySlot slot : build.accessories().keySet()) {
            if (!Pw3Params.ACCESSORY_SLOTS.contains(slot)) {
                throw new ShareException(ShareStatus.CORRUPT, "propwash.share.error.accessory_slot", slot.key());
            }
        }
        for (AccessorySlot slot : Pw3Params.ACCESSORY_SLOTS) {
            build.accessory(slot).ifPresent(id -> refs.add(new Ref("accessory:" + slot.key(), PartKind.ACCESSORY, id)));
        }
        return refs;
    }

    static String keyOf(Identifier id) {
        String namespace = id.getNamespace();
        if (namespace.equals(Pw3Params.UNKNOWN_NAMESPACE)) {
            return parseUnknown(id).map(Unknown::key)
                    .orElseThrow(() -> new ShareException(ShareStatus.CORRUPT, "propwash.share.error.part_id", id.toString()));
        }
        if (namespace.equals("pw2.unknown") || namespace.startsWith("pw1.unknown.")) {
            throw new ShareException(ShareStatus.NOT_CONVERTIBLE, "propwash.share.error.legacy_unknown", id.toString());
        }
        if (!namespace.equals(ShareTables.PROPWASH) && (!Pw3Params.writableNamespace(namespace) || !IdText.encodable(namespace, Pw3Params.MAX_NAMESPACE_CHARS))) {
            throw new ShareException(ShareStatus.CORRUPT, "propwash.share.error.namespace", id.toString());
        }
        return namespace;
    }

    public static Identifier unknownId(String key, PartKind kind, int number, String path) {
        String tail = number > 0 ? Integer.toString(number) : "0/" + path;
        return Identifier.fromNamespaceAndPath(Pw3Params.UNKNOWN_NAMESPACE, key + "/" + kind.key() + "/" + tail);
    }

    public static Optional<Unknown> parseUnknown(Identifier id) {
        if (!id.getNamespace().equals(Pw3Params.UNKNOWN_NAMESPACE)) {
            return Optional.empty();
        }
        String[] parts = id.getPath().split("/", 4);
        if (parts.length < 3) {
            return Optional.empty();
        }
        Optional<PartKind> kind = PartKind.byKey(parts[1]).filter(k -> k.key().equals(parts[1]));
        if (kind.isEmpty() || !Pw3Params.validNamespace(parts[0])) {
            return Optional.empty();
        }
        if (parts.length == 3) {
            String digits = parts[2];
            if (digits.isEmpty() || digits.length() > 10 || digits.startsWith("0") || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
                return Optional.empty();
            }
            long number = Long.parseLong(digits);
            return number <= Pw3Params.MAX_NUMBER ? Optional.of(new Unknown(parts[0], kind.get(), (int) number, null)) : Optional.empty();
        }
        if (!parts[2].equals("0") || !Pw3Params.validPath(parts[3])) {
            return Optional.empty();
        }
        return Optional.of(new Unknown(parts[0], kind.get(), 0, parts[3]));
    }

    public static String display(Identifier id) {
        Optional<Unknown> unknown = parseUnknown(id);
        if (unknown.isPresent()) {
            Unknown u = unknown.get();
            return "?" + u.kind().key() + "." + (u.number() > 0 ? Integer.toString(u.number()) : u.path());
        }
        if (id.getNamespace().equals("pw2.unknown")) {
            return "?" + id.getPath();
        }
        if (id.getNamespace().startsWith("pw1.unknown.")) {
            return "?" + id.getNamespace().substring("pw1.unknown.".length()) + ":" + id.getPath();
        }
        return id.toString();
    }

    public static boolean unknownMarker(Identifier id) {
        String namespace = id.getNamespace();
        return namespace.equals(Pw3Params.UNKNOWN_NAMESPACE) || namespace.equals("pw2.unknown") || namespace.startsWith("pw1.unknown.");
    }

    private static Head carriedHead(String key, List<Ref> group, ShareTables tables, Pw3Carry carry, Map<String, Value> values) {
        Pw3Carry.Head head = carry.head(key)
                .orElseThrow(() -> new ShareException(ShareStatus.UNKNOWN_ENTRIES, "propwash.share.error.unknown_entries", key));
        List<String> now = new ArrayList<>();
        Map<String, Value> local = new LinkedHashMap<>();
        Optional<ShareTable> table = head.hasTable() && head.namespace().isPresent() ? tables.table(head.namespace().get()) : Optional.empty();
        for (Ref ref : group) {
            Value value;
            Optional<Unknown> unknown = parseUnknown(ref.id());
            if (unknown.isPresent()) {
                if (unknown.get().kind() != ref.kind()) {
                    throw new ShareException(ShareStatus.UNKNOWN_ENTRIES, "propwash.share.error.unknown_entries", key);
                }
                value = new Value(unknown.get().number(), unknown.get().path());
            } else if (!head.hasTable()) {
                value = new Value(0, ref.id().getPath());
            } else {
                int number = table.map(t -> t.number(ref.kind(), ref.id().getPath())).orElse(0);
                value = number > 0 ? new Value(number, null) : new Value(0, ref.id().getPath());
            }
            if (!head.hasTable() && value.number() > 0) {
                throw new ShareException(ShareStatus.UNKNOWN_ENTRIES, "propwash.share.error.unknown_entries", key);
            }
            local.put(ref.position(), value);
            now.add(value.text(ref.position()));
        }
        if (!now.equals(head.refs())) {
            throw new ShareException(ShareStatus.UNKNOWN_ENTRIES, "propwash.share.error.unknown_entries", key);
        }
        values.putAll(local);
        return new Head(head.shortId(), head.namespace().orElse(null), head.hasTable(), head.checksum(), head.version(), head.info());
    }

    private static Head freshHead(String namespace, List<Ref> group, ShareTables tables, Options options, Map<String, Value> values) {
        int shortId = namespace.equals(ShareTables.PROPWASH) ? ShareTables.PROPWASH_SHORT_ID : tables.shortIdOf(namespace);
        Optional<ShareTable> table = tables.table(namespace);
        List<TableChecksum.Line> lines = new ArrayList<>();
        Optional<ShareVersion> since = Optional.empty();
        for (Ref ref : group) {
            String path = ref.id().getPath();
            int number = table.map(t -> t.number(ref.kind(), path)).orElse(0);
            if (number > 0) {
                values.put(ref.position(), new Value(number, null));
                lines.add(new TableChecksum.Line(ref.kind(), number, path));
                Optional<ShareVersion> entrySince = ShareVersion.parse(table.get().entries(ref.kind()).get(number - 1).since());
                if (entrySince.isPresent() && (since.isEmpty() || entrySince.get().compareTo(since.get()) > 0)) {
                    since = entrySince;
                }
            } else {
                if (!IdText.encodable(path, Pw3Params.MAX_PATH_CHARS) || !Pw3Params.validPath(path)) {
                    throw new ShareException(ShareStatus.CORRUPT, "propwash.share.error.part_id", ref.id().toString());
                }
                values.put(ref.position(), new Value(0, path));
            }
        }
        Optional<ShareVersion> version = since;
        if (version.isEmpty() && !tables.isFrozen()) {
            version = tables.version(namespace).flatMap(ShareVersion::parse);
        }
        Optional<Pw3Carry.Info> info = Optional.empty();
        boolean wanted = switch (options.info()) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> tables.directory().byNamespace(namespace).isEmpty() && !namespace.equals(ShareTables.PROPWASH);
        };
        if (wanted && !tables.isFrozen()) {
            info = tables.mod(namespace).flatMap(Pw3Codec::info);
        }
        int checksum = table.isPresent() ? TableChecksum.of(lines) : 0;
        return new Head(shortId, shortId == 0 ? namespace : null, table.isPresent(), checksum, version, info);
    }

    public static Optional<Pw3Carry.Info> info(ModMeta meta) {
        String name = meta.name() == null ? "" : meta.name().strip();
        if (name.length() > Pw3Params.MAX_ADDON_NAME_CHARS) {
            name = name.substring(0, Pw3Params.MAX_ADDON_NAME_CHARS).strip();
        }
        if (name.isEmpty()) {
            return Optional.empty();
        }
        Link link = link(meta.homepage());
        return Optional.of(new Pw3Carry.Info(name, link.type(), link.value()));
    }

    public record Link(int type, Optional<String> value) {
    }

    public static Link link(String homepage) {
        if (homepage == null || homepage.isBlank()) {
            return new Link(Pw3Params.LINK_NONE, Optional.empty());
        }
        String url = homepage.strip();
        String lower = url.toLowerCase(Locale.ROOT);
        for (String prefix : List.of("https://modrinth.com/mod/", "https://www.modrinth.com/mod/")) {
            if (lower.startsWith(prefix)) {
                String slug = slug(lower.substring(prefix.length()));
                if (Pw3Params.validSlug(slug)) {
                    return new Link(Pw3Params.LINK_MODRINTH, Optional.of(slug));
                }
            }
        }
        for (String prefix : List.of("https://www.curseforge.com/minecraft/mc-mods/", "https://curseforge.com/minecraft/mc-mods/")) {
            if (lower.startsWith(prefix)) {
                String slug = slug(lower.substring(prefix.length()));
                if (Pw3Params.validSlug(slug)) {
                    return new Link(Pw3Params.LINK_CURSEFORGE, Optional.of(slug));
                }
            }
        }
        if (lower.startsWith("https://")) {
            String rest = url.substring("https://".length());
            if (printableLink(rest)) {
                return new Link(Pw3Params.LINK_HTTPS, Optional.of(rest));
            }
        }
        return new Link(Pw3Params.LINK_NONE, Optional.empty());
    }

    private static String slug(String rest) {
        int end = rest.length();
        for (char stop : new char[]{'/', '?', '#'}) {
            int at = rest.indexOf(stop);
            if (at >= 0 && at < end) {
                end = at;
            }
        }
        return rest.substring(0, end);
    }

    static boolean printableLink(String text) {
        if (text.isEmpty() || text.length() > Pw3Params.MAX_LINK_CHARS) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x21 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    private static void writeHead(BitWriter out, Head head) {
        out.golomb(head.shortId(), Pw3Params.K_SHORT_ID);
        if (head.shortId() == 0) {
            IdText.write(out, head.namespace(), Pw3Params.MAX_NAMESPACE_CHARS);
        }
        out.bit(head.hasTable());
        if (head.hasTable()) {
            out.bits(head.checksum() & TableChecksum.MASK, Pw3Params.TABLE_CHECKSUM_BITS);
        }
        out.bit(head.version().isPresent());
        head.version().ifPresent(version -> {
            out.golomb(version.major(), Pw3Params.K_VERSION).golomb(version.minor(), Pw3Params.K_VERSION).golomb(version.patch(), Pw3Params.K_VERSION);
            out.bit(version.suffix().isPresent());
            version.suffix().ifPresent(suffix -> LayerCodec.writeText(out, suffix));
        });
        out.bit(head.info().isPresent());
        head.info().ifPresent(info -> {
            LayerCodec.writeText(out, info.name());
            int type = info.link().isPresent() ? info.linkType() : Pw3Params.LINK_NONE;
            out.bits(type, 2);
            switch (type) {
                case Pw3Params.LINK_MODRINTH, Pw3Params.LINK_CURSEFORGE -> IdText.write(out, info.link().get(), Pw3Params.MAX_SLUG_CHARS);
                case Pw3Params.LINK_HTTPS -> LayerCodec.writeText(out, info.link().get());
                default -> {
                }
            }
        });
    }

    private static void writeRef(BitWriter out, Ref ref, List<String> keys, List<Head> heads, Map<String, Value> values, int indexBits) {
        String key = keyOf(ref.id());
        int index = keys.indexOf(key);
        out.bits(index, indexBits);
        Value value = values.get(ref.position());
        if (heads.get(index).hasTable()) {
            out.golomb(value.number(), Pw3Params.K_PART);
            if (value.number() == 0) {
                IdText.write(out, value.path(), Pw3Params.MAX_PATH_CHARS);
            }
        } else {
            IdText.write(out, value.path(), Pw3Params.MAX_PATH_CHARS);
        }
    }

    record ReadHead(int shortId, Optional<String> namespace, boolean hasTable, int checksum, Optional<ShareVersion> version,
                    Optional<Pw3Carry.Info> info) {
        String key() {
            return this.namespace.orElse(Pw3Params.SHORT_KEY_PREFIX + this.shortId);
        }
    }

    record ReadRef(String position, PartKind kind, int head, int number, String path) {
    }

    public static ShareDecoded decode(CharSequence symbols, ShareTables tables) throws ShareFormatException {
        BitReader in = LayerCodec.openFrame(symbols);
        boolean paint = in.bit();
        boolean tune = in.bit();
        boolean osd = in.bit();
        boolean name = in.bit();
        boolean extensions = in.bit();
        int count = in.golombInt(Pw3Params.K_NAMESPACE_COUNT, Pw3Params.MAX_NAMESPACES - 1) + 1;
        List<ReadHead> heads = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ReadHead head = readHead(in, tables);
            if (keys.contains(head.key())) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            keys.add(head.key());
            heads.add(head);
        }
        int indexBits = LayerCodec.indexBits(count);
        List<ReadRef> refs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            PartKind kind = Pw3Params.KINDS.get(i);
            refs.add(readRef(in, kind.key(), kind, heads, indexBits));
        }
        int accessories = in.golombInt(Pw3Params.K_ACCESSORY_COUNT, Pw3Params.MAX_ACCESSORIES);
        ShareParsed parsed = new ShareParsed();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < accessories; i++) {
            int slot = in.golombInt(Pw3Params.K_ACCESSORY_SLOT, Integer.MAX_VALUE);
            slots.add(slot);
            refs.add(readRef(in, "accessory:" + slot, PartKind.ACCESSORY, heads, indexBits));
        }
        parsed.layers.add(ShareLayer.PARTS);
        if (paint) {
            parsed.layers.add(ShareLayer.PAINT);
            LayerCodec.readPaint(in, parsed);
        }
        if (tune) {
            parsed.layers.add(ShareLayer.TUNE);
            LayerCodec.readTune(in, parsed, Pw3Params.TUNE_KEYS);
        }
        if (osd) {
            parsed.layers.add(ShareLayer.OSD);
            LayerCodec.readOsd(in, parsed);
        }
        if (name) {
            parsed.layers.add(ShareLayer.NAME);
            LayerCodec.readName(in, parsed);
        }
        if (extensions) {
            readExtensions(in, parsed, keys);
        }
        if (!in.paddingOnly()) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        boolean[] used = new boolean[count];
        Identifier[] ids = new Identifier[refs.size()];
        for (int i = 0; i < refs.size(); i++) {
            ReadRef ref = refs.get(i);
            used[ref.head()] = true;
            ids[i] = resolve(ref, heads.get(ref.head()), tables, parsed);
        }
        parsed.parts = new Identifier[]{ids[0], ids[1], ids[2], ids[3], ids[4], ids[5]};
        for (int i = 0; i < accessories; i++) {
            int slot = slots.get(i);
            if (slot >= Pw3Params.ACCESSORY_SLOTS.size()) {
                parsed.skipped.add("accessory_slot:" + slot);
                continue;
            }
            AccessorySlot key = Pw3Params.ACCESSORY_SLOTS.get(slot);
            if (parsed.accessories.containsKey(key)) {
                parsed.skipped.add("accessory_slot_duplicate:" + slot);
            }
            parsed.accessories.put(key, ids[6 + i]);
        }
        List<NamespaceReport> reports = new ArrayList<>();
        Map<String, Pw3Carry.Head> carried = new LinkedHashMap<>();
        for (int h = 0; h < count; h++) {
            ReadHead head = heads.get(h);
            if (!used[h]) {
                parsed.skipped.add("namespace_unused:" + h);
            }
            reports.add(report(h, head, refs, tables, used[h]));
            boolean unknown = false;
            List<String> texts = new ArrayList<>();
            for (int i = 0; i < refs.size(); i++) {
                ReadRef ref = refs.get(i);
                if (ref.head() != h) {
                    continue;
                }
                unknown |= ids[i].getNamespace().equals(Pw3Params.UNKNOWN_NAMESPACE);
                texts.add(new Value(ref.number(), ref.path()).text(position(ref)));
            }
            if (unknown) {
                carried.put(head.key(), new Pw3Carry.Head(head.shortId(), head.namespace(), head.hasTable(),
                        head.checksum(), head.version(), head.info(), texts));
            }
        }
        return ShareFormat.finish(parsed, Pw3Params.FORMAT, tables, reports, new Pw3Carry(carried), true);
    }

    static void writeExtensions(BitWriter out, ShareContent content, List<String> keys) {
        if (content.creative().isEmpty()) {
            return;
        }
        BitWriter layer = CreativeLayer.write(content.creative().get(), keys);
        out.golomb(1, Pw3Params.K_EXTENSION_COUNT);
        out.golomb(Pw3Params.CREATIVE_EXTENSION, Pw3Params.K_EXTENSION_LAYER);
        out.golomb(layer.length(), Pw3Params.K_EXTENSION_BITS);
        out.append(layer);
    }

    static void readExtensions(BitReader in, ShareParsed parsed, List<String> keys) throws ShareFormatException {
        int count = in.golombInt(Pw3Params.K_EXTENSION_COUNT, Pw3Params.MAX_EXTENSIONS);
        for (int i = 0; i < count; i++) {
            int extension = in.golombInt(Pw3Params.K_EXTENSION_LAYER, 1024);
            long bits = in.golomb(Pw3Params.K_EXTENSION_BITS);
            if (bits > in.remaining()) {
                throw new ShareFormatException(ShareStatus.TRUNCATED);
            }
            if (extension != Pw3Params.CREATIVE_EXTENSION) {
                in.skip(bits);
                parsed.skipped.add("layer:" + (Pw3Params.FIRST_EXTENSION_LAYER + extension));
                continue;
            }
            if (parsed.creative != null) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            long start = in.position();
            try {
                parsed.creative = CreativeLayer.read(in, keys, parsed);
            } catch (ShareFormatException e) {
                throw new ShareFormatException(e.status() == ShareStatus.TRUNCATED ? ShareStatus.CORRUPT : e.status());
            }
            long used = in.position() - start;
            if (used > bits) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            in.skip(bits - used);
            parsed.layers.add(ShareLayer.CREATIVE);
        }
    }

    private static String position(ReadRef ref) {
        if (ref.kind() != PartKind.ACCESSORY) {
            return ref.position();
        }
        int slot = Integer.parseInt(ref.position().substring("accessory:".length()));
        return slot < Pw3Params.ACCESSORY_SLOTS.size() ? "accessory:" + Pw3Params.ACCESSORY_SLOTS.get(slot).key() : ref.position();
    }

    private static ReadHead readHead(BitReader in, ShareTables tables) throws ShareFormatException {
        long shortValue = in.golomb(Pw3Params.K_SHORT_ID);
        if (shortValue > Pw3Params.MAX_NUMBER) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        int shortId = (int) shortValue;
        Optional<String> namespace;
        if (shortId == 0) {
            String text = IdText.read(in, Pw3Params.MAX_NAMESPACE_CHARS);
            if (!Pw3Params.writableNamespace(text) || text.equals(ShareTables.PROPWASH)) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            namespace = Optional.of(text);
        } else {
            namespace = tables.namespaceOfShort(shortId);
        }
        boolean hasTable = in.bit();
        int checksum = hasTable ? (int) in.bits(Pw3Params.TABLE_CHECKSUM_BITS) : 0;
        Optional<ShareVersion> version = Optional.empty();
        if (in.bit()) {
            long major = in.golomb(Pw3Params.K_VERSION);
            long minor = in.golomb(Pw3Params.K_VERSION);
            long patch = in.golomb(Pw3Params.K_VERSION);
            if (major > Pw3Params.MAX_NUMBER || minor > Pw3Params.MAX_NUMBER || patch > Pw3Params.MAX_NUMBER) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            Optional<String> suffix = Optional.empty();
            if (in.bit()) {
                String text = LayerCodec.readText(in, Pw3Params.MAX_VERSION_SUFFIX_CHARS);
                if (!ShareVersion.validSuffix(text)) {
                    throw new ShareFormatException(ShareStatus.CORRUPT);
                }
                suffix = Optional.of(text);
            }
            version = Optional.of(new ShareVersion(major, minor, patch, suffix));
        }
        Optional<Pw3Carry.Info> info = Optional.empty();
        if (in.bit()) {
            String name = LayerCodec.readText(in, Pw3Params.MAX_ADDON_NAME_CHARS);
            if (name.isBlank() || name.length() > Pw3Params.MAX_ADDON_NAME_CHARS) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            int type = (int) in.bits(2);
            Optional<String> link = switch (type) {
                case Pw3Params.LINK_MODRINTH, Pw3Params.LINK_CURSEFORGE -> {
                    String slug = IdText.read(in, Pw3Params.MAX_SLUG_CHARS);
                    if (!Pw3Params.validSlug(slug)) {
                        throw new ShareFormatException(ShareStatus.CORRUPT);
                    }
                    yield Optional.of(slug);
                }
                case Pw3Params.LINK_HTTPS -> {
                    String text = LayerCodec.readText(in, Pw3Params.MAX_LINK_CHARS);
                    if (!printableLink(text)) {
                        throw new ShareFormatException(ShareStatus.CORRUPT);
                    }
                    yield Optional.of(text);
                }
                default -> Optional.empty();
            };
            info = Optional.of(new Pw3Carry.Info(name, type, link));
        }
        return new ReadHead(shortId, namespace, hasTable, checksum, version, info);
    }

    private static ReadRef readRef(BitReader in, String position, PartKind kind, List<ReadHead> heads, int indexBits) throws ShareFormatException {
        int index = (int) in.bits(indexBits);
        if (index >= heads.size()) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        ReadHead head = heads.get(index);
        if (head.hasTable()) {
            int number = in.golombInt(Pw3Params.K_PART, Pw3Params.MAX_NUMBER);
            if (number > 0) {
                return new ReadRef(position, kind, index, number, null);
            }
        }
        String path = IdText.read(in, Pw3Params.MAX_PATH_CHARS);
        if (!Pw3Params.validPath(path)) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        return new ReadRef(position, kind, index, 0, path);
    }

    private static Identifier resolve(ReadRef ref, ReadHead head, ShareTables tables, ShareParsed parsed) throws ShareFormatException {
        try {
            if (head.namespace().isEmpty()) {
                return unknownId(head.key(), ref.kind(), ref.number(), ref.path());
            }
            String namespace = head.namespace().get();
            if (ref.number() == 0) {
                return Identifier.fromNamespaceAndPath(namespace, ref.path());
            }
            Optional<ShareTable.Entry> entry = tables.table(namespace).flatMap(table -> table.entry(ref.kind(), ref.number()));
            if (entry.isPresent()) {
                return Identifier.fromNamespaceAndPath(namespace, entry.get().id());
            }
            parsed.skipOnce("part_index:" + namespace + "/" + ref.kind().key() + "." + ref.number());
            return unknownId(namespace, ref.kind(), ref.number(), null);
        } catch (RuntimeException e) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
    }

    private static NamespaceReport report(int index, ReadHead head, List<ReadRef> refs, ShareTables tables, boolean used) {
        Optional<String> installedVersion = head.namespace().flatMap(tables::version);
        NamespaceReport.Result result;
        if (head.namespace().isEmpty() || !tables.installed(head.namespace().get())) {
            result = NamespaceReport.Result.MISSING;
        } else if (!head.hasTable()) {
            result = NamespaceReport.Result.NO_TABLE;
        } else {
            Optional<ShareTable> table = tables.table(head.namespace().get());
            List<TableChecksum.Line> lines = new ArrayList<>();
            boolean newer = table.isEmpty();
            for (ReadRef ref : refs) {
                if (ref.head() == index && ref.number() > 0 && table.isPresent()) {
                    Optional<ShareTable.Entry> entry = table.get().entry(ref.kind(), ref.number());
                    if (entry.isEmpty()) {
                        newer = true;
                    } else {
                        lines.add(new TableChecksum.Line(ref.kind(), ref.number(), entry.get().id()));
                    }
                }
            }
            if (newer) {
                result = NamespaceReport.Result.NEWER;
            } else {
                result = TableChecksum.of(lines) == head.checksum() ? NamespaceReport.Result.MATCH : NamespaceReport.Result.MISMATCH;
            }
        }
        Optional<Pw3Carry.Info> info = head.info();
        return new NamespaceReport(index, head.namespace(), head.shortId(), head.hasTable(),
                head.hasTable() ? OptionalInt.of(head.checksum()) : OptionalInt.empty(), head.version().map(ShareVersion::toString),
                info.map(Pw3Carry.Info::name), info.map(Pw3Carry.Info::linkType).orElse(Pw3Params.LINK_NONE), info.flatMap(Pw3Carry.Info::link),
                installedVersion, result, used);
    }
}
