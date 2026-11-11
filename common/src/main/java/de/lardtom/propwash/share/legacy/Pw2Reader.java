package de.lardtom.propwash.share.legacy;

import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.share.BitReader;
import de.lardtom.propwash.share.LayerCodec;
import de.lardtom.propwash.share.Pw3Params;
import de.lardtom.propwash.share.ShareFormatException;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareParsed;
import de.lardtom.propwash.share.ShareStatus;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class Pw2Reader {
    private static final List<AccessorySlot> ACCESSORY_SLOTS = List.of(AccessorySlot.TOP, AccessorySlot.BOTTOM, AccessorySlot.FRONT);

    private Pw2Reader() {
    }

    public static ShareParsed read(CharSequence symbols) throws ShareFormatException {
        BitReader in = LayerCodec.openFrame(symbols);
        boolean paint = in.bit();
        boolean tune = in.bit();
        boolean osd = in.bit();
        boolean name = in.bit();
        boolean extensions = in.bit();
        ShareParsed parsed = new ShareParsed();
        parsed.layers.add(ShareLayer.PARTS);
        readParts(in, parsed);
        if (paint) {
            parsed.layers.add(ShareLayer.PAINT);
            LayerCodec.readPaint(in, parsed);
        }
        if (tune) {
            parsed.layers.add(ShareLayer.TUNE);
            LayerCodec.readTune(in, parsed, Pw2Tables.TUNE_KEYS);
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
            LayerCodec.skipExtensions(in, parsed);
        }
        if (!in.paddingOnly()) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        return parsed;
    }

    private static void readParts(BitReader in, ShareParsed parsed) throws ShareFormatException {
        Identifier[] ids = new Identifier[6];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = readRef(in, Pw2Tables.KINDS.get(i), parsed);
        }
        int count = in.golombInt(0, Pw2Tables.MAX_ACCESSORIES);
        for (int i = 0; i < count; i++) {
            int slot = in.golombInt(0, Integer.MAX_VALUE);
            Identifier id = readRef(in, PartKind.ACCESSORY, parsed);
            if (slot >= ACCESSORY_SLOTS.size()) {
                parsed.skipped.add("accessory_slot:" + slot);
                continue;
            }
            AccessorySlot key = ACCESSORY_SLOTS.get(slot);
            if (parsed.accessories.containsKey(key)) {
                parsed.skipped.add("accessory_slot_duplicate:" + slot);
            }
            parsed.accessories.put(key, id);
        }
        parsed.parts = ids;
    }

    private static Identifier readRef(BitReader in, PartKind kind, ShareParsed parsed) throws ShareFormatException {
        int value = in.golombInt(Pw2Tables.K_PART, Integer.MAX_VALUE);
        if (value == 0) {
            String namespace = LayerCodec.readText(in, Pw3Params.MAX_NAMESPACE_CHARS);
            String path = LayerCodec.readText(in, Pw3Params.MAX_PATH_CHARS);
            if (!validNamespace(namespace) || !validPath(path) || namespace.equals(Pw2Tables.UNKNOWN_NAMESPACE)) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            return Identifier.fromNamespaceAndPath(namespace, path);
        }
        List<String> table = Pw2Tables.parts(kind);
        if (value <= table.size()) {
            return Identifier.parse(table.get(value - 1));
        }
        String path = Pw2Tables.kindKey(kind) + "." + value;
        parsed.skipOnce("part_index:" + path);
        return Identifier.fromNamespaceAndPath(Pw2Tables.UNKNOWN_NAMESPACE, path);
    }

    static boolean validNamespace(String namespace) {
        return namespace.length() >= 1 && namespace.length() <= Pw3Params.MAX_NAMESPACE_CHARS && onlyIdChars(namespace) && !namespace.equals("..");
    }

    static boolean validPath(String path) {
        return path.length() >= 1 && path.length() <= Pw3Params.MAX_PATH_CHARS && onlyIdChars(path);
    }

    private static boolean onlyIdChars(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
