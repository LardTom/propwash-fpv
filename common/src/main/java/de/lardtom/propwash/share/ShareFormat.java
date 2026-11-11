package de.lardtom.propwash.share;

import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.share.legacy.Pw1Reader;
import de.lardtom.propwash.share.legacy.Pw1Tables;
import de.lardtom.propwash.share.legacy.Pw2Reader;
import de.lardtom.propwash.share.legacy.Pw2Tables;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ShareFormat {
    public static final String TUNE_PRESET = "PWT1";
    public static final String DRONE_EDITOR = "PWD1";

    private ShareFormat() {
    }

    public record Detected(ShareStatus status, String format, String symbols) {
    }

    public static String compact(String code) {
        StringBuilder text = new StringBuilder(code.length());
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                text.append(c);
            }
        }
        return text.toString();
    }

    public static Detected detect(String code) {
        if (code == null) {
            return new Detected(ShareStatus.EMPTY, "", "");
        }
        if (code.length() > Pw3Params.HARD_INPUT_LIMIT) {
            return new Detected(ShareStatus.TOO_LONG, "", "");
        }
        String compact = compact(code);
        if (compact.isEmpty()) {
            return new Detected(ShareStatus.EMPTY, "", "");
        }
        if (compact.length() > Pw3Params.MAX_INPUT_CHARS) {
            return new Detected(ShareStatus.TOO_LONG, "", "");
        }
        String upper = compact.toUpperCase(Locale.ROOT);
        if (upper.startsWith(Pw3Params.FORMAT)) {
            return new Detected(ShareStatus.OK, Pw3Params.FORMAT, withoutDashes(compact, Pw3Params.FORMAT.length()));
        }
        if (upper.startsWith(Pw2Tables.FORMAT)) {
            return new Detected(ShareStatus.OK, Pw2Tables.FORMAT, withoutDashes(compact, Pw2Tables.FORMAT.length()));
        }
        if (upper.startsWith(Pw1Tables.FORMAT)) {
            return new Detected(ShareStatus.OK, Pw1Tables.FORMAT, withoutDashes(upper, Pw1Tables.FORMAT.length()));
        }
        if (upper.startsWith(TUNE_PRESET)) {
            return new Detected(ShareStatus.OTHER_CODE, TUNE_PRESET, "");
        }
        if (upper.startsWith(DRONE_EDITOR)) {
            return new Detected(ShareStatus.OK, DRONE_EDITOR, compact);
        }
        if (newerFormat(upper)) {
            return new Detected(ShareStatus.UNSUPPORTED_VERSION, "", "");
        }
        if (base62(compact)) {
            return new Detected(ShareStatus.OK, Pw3Params.FORMAT, compact);
        }
        return new Detected(ShareStatus.INVALID_PREFIX, "", "");
    }

    private static String withoutDashes(String text, int from) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '-') {
                out.append(c);
            }
        }
        return out.toString();
    }

    static boolean base62(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Base62.ALPHABET.indexOf(text.charAt(i)) < 0) {
                return false;
            }
        }
        return !text.isEmpty();
    }

    private static boolean newerFormat(String upper) {
        int end = 2;
        while (end < upper.length() && end < 5 && upper.charAt(end) >= '0' && upper.charAt(end) <= '9') {
            end++;
        }
        return upper.startsWith("PW") && end > 2 && Integer.parseInt(upper.substring(2, end)) > 3;
    }

    public static ShareDecoded decode(String code) {
        return decode(code, ShareTables.current());
    }

    public static ShareDecoded decode(String code, ShareTables tables) {
        return decode(code, tables, true);
    }

    public static ShareDecoded decode(String code, ShareTables tables, boolean renames) {
        Detected detected = detect(code);
        if (!detected.status().ok()) {
            return ShareDecoded.failed(detected.status(), detected.format());
        }
        ShareDecoded result = decodeDetected(detected, tables, renames);
        if (!result.ok() && !detected.symbols().equals(compactOrEmpty(code)) && base62(compactOrEmpty(code))) {
            ShareDecoded bare = decodeDetected(new Detected(ShareStatus.OK, Pw3Params.FORMAT, compactOrEmpty(code)), tables, renames);
            if (bare.ok()) {
                return bare;
            }
        }
        if (!result.ok() && detected.format().equals(Pw3Params.FORMAT) && detected.symbols().equals(compactOrEmpty(code))) {
            return ShareDecoded.failed(ShareStatus.INVALID_PREFIX);
        }
        return result;
    }

    private static String compactOrEmpty(String code) {
        return code == null ? "" : compact(code);
    }

    private static ShareDecoded decodeDetected(Detected detected, ShareTables tables, boolean renames) {
        try {
            return switch (detected.format()) {
                case Pw3Params.FORMAT -> Pw3Codec.decode(detected.symbols(), tables);
                case Pw2Tables.FORMAT -> finish(Pw2Reader.read(detected.symbols()), Pw2Tables.FORMAT, tables, List.of(), Pw3Carry.EMPTY, renames);
                case Pw1Tables.FORMAT -> finish(Pw1Reader.read(detected.symbols()), Pw1Tables.FORMAT, tables, List.of(), Pw3Carry.EMPTY, renames);
                case DRONE_EDITOR -> finish(CreativeShare.readPwd1(detected.symbols()), DRONE_EDITOR, tables, List.of(), Pw3Carry.EMPTY, renames);
                default -> ShareDecoded.failed(ShareStatus.INVALID_PREFIX);
            };
        } catch (ShareFormatException e) {
            return ShareDecoded.failed(e.status(), detected.format());
        } catch (RuntimeException e) {
            return ShareDecoded.failed(ShareStatus.CORRUPT, detected.format());
        }
    }

    static ShareDecoded finish(ShareParsed parsed, String format, ShareTables tables, List<NamespaceReport> reports, Pw3Carry carry, boolean renames)
            throws ShareFormatException {
        if (parsed.parts == null) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        Map<Identifier, ShareDecoded.Rename> renamed = new LinkedHashMap<>();
        Identifier[] ids = parsed.parts.clone();
        for (int i = 0; i < ids.length; i++) {
            ids[i] = rename(ids[i], tables, renames, renamed);
        }
        EnumMap<AccessorySlot, Identifier> accessories = new EnumMap<>(AccessorySlot.class);
        for (Map.Entry<AccessorySlot, Identifier> entry : parsed.accessories.entrySet()) {
            accessories.put(entry.getKey(), rename(entry.getValue(), tables, renames, renamed));
        }
        BuildSpec build = new BuildSpec(ids[0], ids[1], ids[2], ids[3], ids[4], ids[5], accessories);
        ShareContent content = new ShareContent(build, Optional.ofNullable(parsed.paint), Optional.ofNullable(parsed.tune),
                Optional.ofNullable(parsed.osd), Optional.ofNullable(parsed.name), Optional.ofNullable(parsed.creative));
        LinkedHashSet<Identifier> unknown = new LinkedHashSet<>();
        LinkedHashSet<Identifier> wrong = new LinkedHashSet<>();
        LinkedHashSet<Identifier> inactive = new LinkedHashSet<>();
        for (int i = 0; i < 6; i++) {
            classify(ids[i], Pw3Params.KINDS.get(i), tables, unknown, wrong, inactive);
        }
        for (AccessorySlot slot : Pw3Params.ACCESSORY_SLOTS) {
            Identifier id = accessories.get(slot);
            if (id != null) {
                classify(id, PartKind.ACCESSORY, tables, unknown, wrong, inactive);
            }
        }
        return new ShareDecoded(ShareStatus.OK, format, Optional.of(content), parsed.layers, reports, List.copyOf(unknown), List.copyOf(wrong),
                List.copyOf(renamed.values()), parsed.skipped, List.copyOf(inactive), carry);
    }

    private static Identifier rename(Identifier id, ShareTables tables, boolean renames, Map<Identifier, ShareDecoded.Rename> renamed) {
        if (!renames || Pw3Codec.unknownMarker(id)) {
            return id;
        }
        Identifier resolved = tables.replacement(id);
        if (!resolved.equals(id)) {
            renamed.putIfAbsent(id, new ShareDecoded.Rename(id, resolved));
        }
        return resolved;
    }

    private static void classify(Identifier id, PartKind expected, ShareTables tables, LinkedHashSet<Identifier> unknown,
                                 LinkedHashSet<Identifier> wrong, LinkedHashSet<Identifier> inactive) {
        if (Pw3Codec.unknownMarker(id)) {
            unknown.add(id);
            return;
        }
        Optional<PartKind> kind;
        boolean active;
        try {
            kind = tables.parts().kind(id);
            active = tables.parts().active(id);
        } catch (RuntimeException e) {
            kind = Optional.empty();
            active = true;
        }
        if (kind.isEmpty()) {
            unknown.add(id);
        } else if (kind.get() != expected) {
            wrong.add(id);
        } else if (!active) {
            inactive.add(id);
        }
    }

    public static String encode(ShareContent content) {
        return Pw3Codec.encode(content, ShareTables.current());
    }
}
