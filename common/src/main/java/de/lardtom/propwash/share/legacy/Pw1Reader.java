package de.lardtom.propwash.share.legacy;

import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.share.LayerCodec;
import de.lardtom.propwash.share.Pw3Params;
import de.lardtom.propwash.share.ShareFormatException;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareOsd;
import de.lardtom.propwash.share.ShareParsed;
import de.lardtom.propwash.share.ShareStatus;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.CRC32;

public final class Pw1Reader {
    private static final List<AccessorySlot> ACCESSORY_SLOTS = List.of(AccessorySlot.TOP, AccessorySlot.BOTTOM, AccessorySlot.FRONT);
    private static final int[] VALUES = new int[128];

    static {
        Arrays.fill(VALUES, -1);
        for (int i = 0; i < Pw1Tables.ALPHABET.length(); i++) {
            VALUES[Pw1Tables.ALPHABET.charAt(i)] = i;
        }
        VALUES['O'] = 0;
        VALUES['I'] = 1;
        VALUES['L'] = 1;
    }

    private Pw1Reader() {
    }

    public static ShareParsed read(CharSequence upperSymbols) throws ShareFormatException {
        byte[] frame = base32(upperSymbols);
        if (frame.length > Pw3Params.MAX_FRAME_BYTES) {
            throw new ShareFormatException(ShareStatus.TOO_LARGE);
        }
        if (frame.length < Pw1Tables.MIN_FRAME_BYTES) {
            throw new ShareFormatException(ShareStatus.TRUNCATED);
        }
        int bodyEnd = frame.length - 4;
        CRC32 crc = new CRC32();
        crc.update(frame, 0, bodyEnd);
        long stored = ((long) (frame[bodyEnd] & 0xFF) << 24) | ((long) (frame[bodyEnd + 1] & 0xFF) << 16)
                | ((long) (frame[bodyEnd + 2] & 0xFF) << 8) | (frame[bodyEnd + 3] & 0xFF);
        if (crc.getValue() != stored) {
            throw new ShareFormatException(ShareStatus.CHECKSUM);
        }
        int version = frame[0] & 0xFF;
        int flags = frame[1] & 0xFF;
        if (version != Pw1Tables.FRAME_VERSION || (flags & ~Pw1Tables.FLAG_DEFLATE) != 0) {
            throw new ShareFormatException(ShareStatus.UNSUPPORTED_VERSION);
        }
        byte[] payload = Arrays.copyOfRange(frame, 2, bodyEnd);
        if ((flags & Pw1Tables.FLAG_DEFLATE) != 0) {
            payload = LayerCodec.inflate(payload, null, Pw1Tables.MAX_PAYLOAD_BYTES);
        } else if (payload.length > Pw1Tables.MAX_PAYLOAD_BYTES) {
            throw new ShareFormatException(ShareStatus.TOO_LARGE);
        }
        return parse(payload);
    }

    static byte[] base32(CharSequence symbols) throws ShareFormatException {
        int count = symbols.length();
        byte[] out = new byte[count * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (int i = 0; i < count; i++) {
            char c = symbols.charAt(i);
            int value = c < VALUES.length ? VALUES[c] : -1;
            if (value < 0) {
                throw new ShareFormatException(ShareStatus.BAD_CHARACTER);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out[index++] = (byte) (buffer >>> bits);
                buffer &= (1 << bits) - 1;
            }
        }
        if (buffer != 0) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        return out;
    }

    private static ShareParsed parse(byte[] payload) throws ShareFormatException {
        Bytes reader = new Bytes(payload, 0, payload.length);
        long mask = reader.varint();
        if ((mask & ShareLayer.PARTS.mask()) == 0) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        ShareParsed parsed = new ShareParsed();
        for (int bit = 0; bit < 32; bit++) {
            if ((mask & (1L << bit)) == 0) {
                continue;
            }
            Bytes block = reader.block();
            Optional<ShareLayer> layer = ShareLayer.byBit(bit);
            if (layer.isEmpty()) {
                parsed.skipped.add("layer:" + bit);
                continue;
            }
            parsed.layers.add(layer.get());
            switch (layer.get()) {
                case PARTS -> readParts(block, parsed);
                case PAINT -> readPaint(block, parsed);
                case TUNE -> readTune(block, parsed);
                case OSD -> readOsd(block, parsed);
                case NAME -> readName(block, parsed);
            }
            if (!block.atEnd()) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
        }
        if (!reader.atEnd()) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        if (parsed.parts == null) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        return parsed;
    }

    private static void readParts(Bytes block, ShareParsed parsed) throws ShareFormatException {
        Identifier[] ids = new Identifier[6];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = readRef(block, parsed);
        }
        int count = block.u8();
        for (int i = 0; i < count; i++) {
            int slot = block.u8();
            Identifier id = readRef(block, parsed);
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

    private static Identifier readRef(Bytes block, ShareParsed parsed) throws ShareFormatException {
        long ns = block.varint();
        String namespace = null;
        if (ns == 0) {
            namespace = block.str();
            if (!Pw2Reader.validNamespace(namespace)) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
        } else if (ns < Pw1Tables.NAMESPACES.size()) {
            namespace = Pw1Tables.NAMESPACES.get((int) ns);
        }
        String path = block.str();
        if (!Pw2Reader.validPath(path)) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        if (namespace == null) {
            parsed.skipOnce("namespace:" + ns);
            return Pw1Tables.unknownNamespace(ns, path);
        }
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static void readPaint(Bytes block, ShareParsed parsed) throws ShareFormatException {
        int bits = block.u16();
        LinkedHashMap<String, Integer> colors = new LinkedHashMap<>();
        for (int i = 0; i < Pw1Tables.PAINT_SLOT_BITS; i++) {
            if ((bits & (1 << i)) == 0) {
                continue;
            }
            int rgb = (block.u8() << 16) | (block.u8() << 8) | block.u8();
            if (i < Pw3Params.PAINT_SLOTS.size()) {
                colors.put(Pw3Params.PAINT_SLOTS.get(i), rgb);
            } else {
                parsed.skipped.add("paint_slot:" + i);
            }
        }
        parsed.paint = new PaintSpec(colors);
    }

    private static void readTune(Bytes block, ShareParsed parsed) throws ShareFormatException {
        long count = block.varint();
        if (count > Pw3Params.MAX_TUNE_ENTRIES) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        TreeMap<String, Float> values = new TreeMap<>();
        for (long i = 0; i < count; i++) {
            long head = block.varint();
            long index = head >>> 1;
            boolean raw = (head & 1) != 0;
            String key;
            if (index == 0) {
                key = block.str();
                if (key.isEmpty() || key.length() > Pw3Params.MAX_TUNE_KEY_CHARS) {
                    throw new ShareFormatException(ShareStatus.CORRUPT);
                }
            } else {
                key = index >= 1 && index <= Pw1Tables.TUNE_KEYS.size() ? Pw1Tables.TUNE_KEYS.get((int) index - 1) : null;
            }
            float value = raw ? block.f32() : (float) (block.zigzag() / 1000.0);
            if (!Float.isFinite(value)) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            if (key == null) {
                parsed.skipped.add("tune_index:" + index);
                continue;
            }
            if (!Pw3Params.sharedTuneKey(key)) {
                parsed.skipped.add("tune_key:" + key);
                continue;
            }
            if (values.containsKey(key)) {
                parsed.skipped.add("tune_duplicate:" + key);
            }
            values.put(key, value);
        }
        parsed.tune = new TuneSpec(values);
    }

    private static void readOsd(Bytes block, ShareParsed parsed) throws ShareFormatException {
        long version = block.varint();
        String json = block.str();
        if (version < 1 || version > Integer.MAX_VALUE || json.length() > Pw3Params.MAX_OSD_CHARS) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        parsed.osd = new ShareOsd((int) version, json);
    }

    private static void readName(Bytes block, ShareParsed parsed) throws ShareFormatException {
        String name = block.str().strip();
        if (name.isEmpty() || name.length() > Pw3Params.MAX_NAME_CHARS) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        parsed.name = name;
    }

    private static final class Bytes {
        private final byte[] data;
        private final int end;
        private int pos;

        Bytes(byte[] data, int start, int end) {
            this.data = data;
            this.pos = start;
            this.end = end;
        }

        int remaining() {
            return this.end - this.pos;
        }

        boolean atEnd() {
            return this.pos == this.end;
        }

        int u8() throws ShareFormatException {
            if (this.pos >= this.end) {
                throw new ShareFormatException(ShareStatus.TRUNCATED);
            }
            return this.data[this.pos++] & 0xFF;
        }

        int u16() throws ShareFormatException {
            return (this.u8() << 8) | this.u8();
        }

        long u32() throws ShareFormatException {
            return ((long) this.u8() << 24) | ((long) this.u8() << 16) | ((long) this.u8() << 8) | this.u8();
        }

        long varint() throws ShareFormatException {
            long value = 0;
            for (int i = 0; i < 5; i++) {
                int b = this.u8();
                value |= (long) (b & 0x7F) << (7 * i);
                if ((b & 0x80) == 0) {
                    if (value > 0xFFFFFFFFL) {
                        throw new ShareFormatException(ShareStatus.CORRUPT);
                    }
                    return value;
                }
            }
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }

        long zigzag() throws ShareFormatException {
            long raw = this.varint();
            return (raw >>> 1) ^ -(raw & 1);
        }

        float f32() throws ShareFormatException {
            return Float.intBitsToFloat((int) this.u32());
        }

        int length() throws ShareFormatException {
            long length = this.varint();
            if (length > this.remaining()) {
                throw new ShareFormatException(ShareStatus.TRUNCATED);
            }
            return (int) length;
        }

        Bytes block() throws ShareFormatException {
            int length = this.length();
            Bytes inner = new Bytes(this.data, this.pos, this.pos + length);
            this.pos += length;
            return inner;
        }

        String str() throws ShareFormatException {
            int length = this.length();
            try {
                String text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(this.data, this.pos, length))
                        .toString();
                this.pos += length;
                return text;
            } catch (CharacterCodingException e) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
        }
    }
}
