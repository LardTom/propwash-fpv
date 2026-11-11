package de.lardtom.propwash.share;

import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class LayerCodec {
    private static final double[] SCALES = {1.0, 10.0, 100.0, 1000.0};
    private static final byte[] OSD_DICTIONARY = Pw3Params.osdDictionary();

    private LayerCodec() {
    }

    public static int frameChecksum(byte[] data, int length) {
        CRC32 crc = new CRC32();
        crc.update(data, 0, length);
        return (int) (crc.getValue() & 0xFFFF);
    }

    public static byte[] frame(byte[] payload) {
        byte[] frame = Arrays.copyOf(payload, payload.length + Pw3Params.CHECKSUM_BYTES);
        int check = frameChecksum(payload, payload.length);
        frame[payload.length] = (byte) (check >>> 8);
        frame[payload.length + 1] = (byte) check;
        return frame;
    }

    public static BitReader openFrame(CharSequence symbols) throws ShareFormatException {
        byte[] frame = Base62.decode(symbols);
        if (frame.length > Pw3Params.MAX_FRAME_BYTES) {
            throw new ShareFormatException(ShareStatus.TOO_LARGE);
        }
        if (frame.length < Pw3Params.MIN_FRAME_BYTES) {
            throw new ShareFormatException(ShareStatus.TRUNCATED);
        }
        int body = frame.length - Pw3Params.CHECKSUM_BYTES;
        int stored = ((frame[body] & 0xFF) << 8) | (frame[body + 1] & 0xFF);
        if (frameChecksum(frame, body) != stored) {
            throw new ShareFormatException(ShareStatus.CHECKSUM);
        }
        return new BitReader(frame, body);
    }

    public static int indexBits(int count) {
        return count <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(count - 1);
    }

    public static void writePaint(BitWriter out, PaintSpec paint) {
        for (String slot : paint.colors().keySet()) {
            if (Pw3Params.paintBit(slot) < 0) {
                throw new ShareException(ShareStatus.CORRUPT, "propwash.share.error.paint_slot", slot);
            }
        }
        int length = 0;
        for (int i = 0; i < Pw3Params.PAINT_SLOTS.size(); i++) {
            if (paint.color(Pw3Params.PAINT_SLOTS.get(i)).isPresent()) {
                length = i + 1;
            }
        }
        out.golomb(length, Pw3Params.K_PAINT_SLOTS);
        for (int i = 0; i < length; i++) {
            out.bit(paint.color(Pw3Params.PAINT_SLOTS.get(i)).isPresent());
        }
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            Optional<Integer> color = paint.color(Pw3Params.PAINT_SLOTS.get(i));
            if (color.isEmpty()) {
                continue;
            }
            int rgb = color.get() & 0xFFFFFF;
            if (!seen.isEmpty()) {
                int at = seen.indexOf(rgb);
                out.bit(at < 0);
                if (at >= 0) {
                    out.bits(at, indexBits(seen.size()));
                    continue;
                }
            }
            seen.add(rgb);
            int table = Pw3Params.COLORS.indexOf(rgb);
            out.bit(table < 0);
            if (table >= 0) {
                out.golomb(table, Pw3Params.K_COLOR);
            } else {
                out.bits(rgb, 24);
            }
        }
    }

    public static void readPaint(BitReader in, ShareParsed parsed) throws ShareFormatException {
        int length = in.golombInt(Pw3Params.K_PAINT_SLOTS, Pw3Params.MAX_PAINT_SLOT_BITS);
        boolean[] present = new boolean[length];
        for (int i = 0; i < length; i++) {
            present[i] = in.bit();
        }
        List<Integer> seen = new ArrayList<>();
        LinkedHashMap<String, Integer> colors = new LinkedHashMap<>();
        for (int i = 0; i < length; i++) {
            if (!present[i]) {
                continue;
            }
            int rgb;
            if (!seen.isEmpty() && !in.bit()) {
                int at = (int) in.bits(indexBits(seen.size()));
                if (at >= seen.size()) {
                    throw new ShareFormatException(ShareStatus.CORRUPT);
                }
                rgb = seen.get(at);
            } else {
                if (in.bit()) {
                    rgb = (int) in.bits(24);
                } else {
                    int table = in.golombInt(Pw3Params.K_COLOR, Integer.MAX_VALUE);
                    if (table >= Pw3Params.COLORS.size()) {
                        throw new ShareFormatException(ShareStatus.CORRUPT);
                    }
                    rgb = Pw3Params.COLORS.get(table);
                }
                seen.add(rgb);
            }
            if (i < Pw3Params.PAINT_SLOTS.size()) {
                colors.put(Pw3Params.PAINT_SLOTS.get(i), rgb);
            } else {
                parsed.skipped.add("paint_slot:" + i);
            }
        }
        parsed.paint = new PaintSpec(colors);
    }

    public static void writeTune(BitWriter out, TuneSpec tune) {
        List<Map.Entry<String, Float>> indexed = new ArrayList<>();
        List<Map.Entry<String, Float>> literal = new ArrayList<>();
        for (Map.Entry<String, Float> entry : tune.values().entrySet()) {
            if (!Pw3Params.sharedTuneKey(entry.getKey())) {
                continue;
            }
            if (Pw3Params.tuneIndex(entry.getKey()) == 0 && (entry.getKey().isEmpty() || entry.getKey().length() > Pw3Params.MAX_TUNE_KEY_CHARS)) {
                throw new ShareException(ShareStatus.CORRUPT, "propwash.share.error.tune_key", entry.getKey());
            }
            (Pw3Params.tuneIndex(entry.getKey()) > 0 ? indexed : literal).add(entry);
        }
        indexed.sort(Comparator.comparingInt(e -> Pw3Params.tuneIndex(e.getKey())));
        literal.sort(Map.Entry.comparingByKey());
        List<Integer> seen = new ArrayList<>();
        out.golomb(indexed.size(), Pw3Params.K_TUNE_COUNT);
        int previous = 0;
        for (Map.Entry<String, Float> entry : indexed) {
            int index = Pw3Params.tuneIndex(entry.getKey());
            out.golomb(index - previous - 1L, Pw3Params.K_TUNE_GAP);
            previous = index;
            writeValue(out, entry.getValue(), seen);
        }
        out.golomb(literal.size(), Pw3Params.K_TUNE_LITERAL_COUNT);
        for (Map.Entry<String, Float> entry : literal) {
            writeText(out, entry.getKey());
            writeValue(out, entry.getValue(), seen);
        }
    }

    private static void writeValue(BitWriter out, float value, List<Integer> seen) {
        int raw = Float.floatToRawIntBits(value);
        int at = seen.indexOf(raw);
        if (at >= 0) {
            out.bits(0b110, 3);
            out.bits(at, indexBits(seen.size()));
            return;
        }
        seen.add(raw);
        for (int scale = 0; scale < SCALES.length; scale++) {
            long q = scaled(value, scale);
            if (q == Long.MIN_VALUE) {
                continue;
            }
            switch (scale) {
                case 0 -> out.bits(0b0, 1);
                case 1 -> out.bits(0b1110, 4);
                case 2 -> out.bits(0b10, 2);
                default -> out.bits(0b11110, 5);
            }
            out.golomb(q >= 0 ? 2 * q : -2 * q - 1, Pw3Params.K_VALUE);
            return;
        }
        out.bits(0b11111, 5);
        out.bits(raw & 0xFFFFFFFFL, 32);
    }

    public static long scaled(float value, int scale) {
        if (!Float.isFinite(value)) {
            return Long.MIN_VALUE;
        }
        double factor = SCALES[scale];
        double product = (double) value * factor;
        if (Math.abs(product) >= 2147483647.5) {
            return Long.MIN_VALUE;
        }
        long q = Math.round(product);
        if (Math.abs(q) >= (1L << 31)) {
            return Long.MIN_VALUE;
        }
        float back = (float) (q / factor);
        return Float.floatToRawIntBits(back) == Float.floatToRawIntBits(value) ? q : Long.MIN_VALUE;
    }

    public static void readTune(BitReader in, ShareParsed parsed, List<String> tuneKeys) throws ShareFormatException {
        int indexed = in.golombInt(Pw3Params.K_TUNE_COUNT, Pw3Params.MAX_TUNE_ENTRIES);
        TreeMap<String, Float> values = new TreeMap<>();
        List<Integer> seen = new ArrayList<>();
        long index = 0;
        for (int i = 0; i < indexed; i++) {
            index += in.golomb(Pw3Params.K_TUNE_GAP) + 1;
            if (index > Integer.MAX_VALUE) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            float value = readValue(in, seen);
            if (index > tuneKeys.size()) {
                parsed.skipped.add("tune_index:" + index);
                continue;
            }
            putTune(values, tuneKeys.get((int) index - 1), value, parsed);
        }
        int literal = in.golombInt(Pw3Params.K_TUNE_LITERAL_COUNT, Pw3Params.MAX_TUNE_ENTRIES - indexed);
        for (int i = 0; i < literal; i++) {
            String key = readText(in, Pw3Params.MAX_TUNE_KEY_CHARS);
            if (key.isEmpty() || key.length() > Pw3Params.MAX_TUNE_KEY_CHARS) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            putTune(values, key, readValue(in, seen), parsed);
        }
        parsed.tune = new TuneSpec(values);
    }

    private static void putTune(TreeMap<String, Float> values, String key, float value, ShareParsed parsed) {
        if (!Pw3Params.sharedTuneKey(key)) {
            parsed.skipped.add("tune_key:" + key);
            return;
        }
        if (values.containsKey(key)) {
            parsed.skipped.add("tune_duplicate:" + key);
        }
        values.put(key, value);
    }

    private static float readValue(BitReader in, List<Integer> seen) throws ShareFormatException {
        int scale;
        if (!in.bit()) {
            scale = 0;
        } else if (!in.bit()) {
            scale = 2;
        } else if (!in.bit()) {
            if (seen.isEmpty()) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            int at = (int) in.bits(indexBits(seen.size()));
            if (at >= seen.size()) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            return Float.intBitsToFloat(seen.get(at));
        } else if (!in.bit()) {
            scale = 1;
        } else if (!in.bit()) {
            scale = 3;
        } else {
            int raw = (int) in.bits(32);
            float value = Float.intBitsToFloat(raw);
            if (!Float.isFinite(value)) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            seen.add(raw);
            return value;
        }
        long zigzag = in.golomb(Pw3Params.K_VALUE);
        if (zigzag > 0xFFFFFFFFL) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        long q = (zigzag >>> 1) ^ -(zigzag & 1);
        float value = (float) (q / SCALES[scale]);
        seen.add(Float.floatToRawIntBits(value));
        return value;
    }

    public static void writeOsd(BitWriter out, ShareOsd osd) {
        out.golomb(osd.version() - 1L, Pw3Params.K_OSD_VERSION);
        byte[] text = osd.json().getBytes(StandardCharsets.UTF_8);
        byte[] packed = deflate(text, OSD_DICTIONARY);
        long packedBits = BitWriter.golombLength(packed.length, Pw3Params.K_OSD_BYTES) + 8L * packed.length;
        if (packedBits < textBits(osd.json())) {
            out.bit(true);
            out.golomb(packed.length, Pw3Params.K_OSD_BYTES);
            out.bytes(packed);
        } else {
            out.bit(false);
            writeText(out, osd.json());
        }
    }

    public static void readOsd(BitReader in, ShareParsed parsed) throws ShareFormatException {
        long version = in.golomb(Pw3Params.K_OSD_VERSION) + 1;
        if (version > Integer.MAX_VALUE) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        String json;
        if (in.bit()) {
            int length = in.golombInt(Pw3Params.K_OSD_BYTES, Integer.MAX_VALUE);
            if (8L * length > in.remaining()) {
                throw new ShareFormatException(ShareStatus.TRUNCATED);
            }
            byte[] packed = new byte[length];
            for (int i = 0; i < length; i++) {
                packed[i] = (byte) in.bits(8);
            }
            json = utf8(inflate(packed, OSD_DICTIONARY, Pw3Params.MAX_OSD_INFLATED_BYTES));
        } else {
            json = readText(in, Pw3Params.MAX_OSD_CHARS);
        }
        if (json.length() > Pw3Params.MAX_OSD_CHARS) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        parsed.osd = new ShareOsd((int) version, json);
    }

    public static void writeName(BitWriter out, String name) {
        writeText(out, name);
    }

    public static void readName(BitReader in, ShareParsed parsed) throws ShareFormatException {
        String text = readText(in, Pw3Params.MAX_NAME_CHARS * 4).strip();
        if (text.isEmpty() || text.length() > Pw3Params.MAX_NAME_CHARS) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        parsed.name = text;
    }

    public static void skipExtensions(BitReader in, ShareParsed parsed) throws ShareFormatException {
        int count = in.golombInt(Pw3Params.K_EXTENSION_COUNT, Pw3Params.MAX_EXTENSIONS);
        for (int i = 0; i < count; i++) {
            int layer = Pw3Params.FIRST_EXTENSION_LAYER + in.golombInt(Pw3Params.K_EXTENSION_LAYER, 1024);
            long bits = in.golomb(Pw3Params.K_EXTENSION_BITS);
            in.skip(bits);
            parsed.skipped.add("layer:" + layer);
        }
    }

    static int textMode(String text) {
        boolean table = true;
        boolean ascii = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            table &= Pw3Params.TEXT_ALPHABET.indexOf(c) >= 0;
            ascii &= c >= 0x20 && c <= 0x7E;
        }
        return table ? 0 : ascii ? 1 : 2;
    }

    public static long textBits(String text) {
        return switch (textMode(text)) {
            case 0 -> 1 + BitWriter.golombLength(text.length(), Pw3Params.K_TEXT) + 6L * text.length();
            case 1 -> 2 + BitWriter.golombLength(text.length(), Pw3Params.K_TEXT) + 7L * text.length();
            default -> {
                int bytes = text.getBytes(StandardCharsets.UTF_8).length;
                yield 2 + BitWriter.golombLength(bytes, Pw3Params.K_TEXT) + 8L * bytes;
            }
        };
    }

    public static void writeText(BitWriter out, String text) {
        switch (textMode(text)) {
            case 0 -> {
                out.bits(0b0, 1).golomb(text.length(), Pw3Params.K_TEXT);
                for (int i = 0; i < text.length(); i++) {
                    out.bits(Pw3Params.TEXT_ALPHABET.indexOf(text.charAt(i)), 6);
                }
            }
            case 1 -> {
                out.bits(0b10, 2).golomb(text.length(), Pw3Params.K_TEXT);
                for (int i = 0; i < text.length(); i++) {
                    out.bits(text.charAt(i) - 0x20, 7);
                }
            }
            default -> {
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                out.bits(0b11, 2).golomb(bytes.length, Pw3Params.K_TEXT).bytes(bytes);
            }
        }
    }

    public static String readText(BitReader in, int maxSymbols) throws ShareFormatException {
        int mode = in.bit() ? (in.bit() ? 2 : 1) : 0;
        int width = mode == 0 ? 6 : mode == 1 ? 7 : 8;
        long count = in.golomb(Pw3Params.K_TEXT);
        if (count * width > in.remaining()) {
            throw new ShareFormatException(ShareStatus.TRUNCATED);
        }
        if (count > (long) maxSymbols * (mode == 2 ? 4 : 1)) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        int n = (int) count;
        if (mode == 2) {
            byte[] bytes = new byte[n];
            for (int i = 0; i < n; i++) {
                bytes[i] = (byte) in.bits(8);
            }
            return utf8(bytes);
        }
        StringBuilder out = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            int symbol = (int) in.bits(width);
            if (mode == 0) {
                if (symbol >= Pw3Params.TEXT_ALPHABET.length()) {
                    throw new ShareFormatException(ShareStatus.CORRUPT);
                }
                out.append(Pw3Params.TEXT_ALPHABET.charAt(symbol));
            } else {
                if (symbol > 0x7E - 0x20) {
                    throw new ShareFormatException(ShareStatus.CORRUPT);
                }
                out.append((char) (symbol + 0x20));
            }
        }
        return out.toString();
    }

    public static String utf8(byte[] bytes) throws ShareFormatException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
    }

    public static byte[] deflate(byte[] payload, byte[] dictionary) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setStrategy(Deflater.DEFAULT_STRATEGY);
            if (dictionary != null) {
                deflater.setDictionary(dictionary);
            }
            deflater.setInput(payload);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int n = deflater.deflate(buffer);
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    public static byte[] inflate(byte[] data, byte[] dictionary, int limit) throws ShareFormatException {
        Inflater inflater = new Inflater(true);
        try {
            if (dictionary != null) {
                inflater.setDictionary(dictionary);
            }
            byte[] input = Arrays.copyOf(data, data.length + 1);
            inflater.setInput(input);
            byte[] out = new byte[limit + 1];
            int total = 0;
            while (!inflater.finished()) {
                if (total >= out.length) {
                    throw new ShareFormatException(ShareStatus.TOO_LARGE);
                }
                int n = inflater.inflate(out, total, out.length - total);
                if (n == 0 && !inflater.finished()) {
                    throw new ShareFormatException(ShareStatus.CORRUPT);
                }
                total += n;
            }
            if (total > limit) {
                throw new ShareFormatException(ShareStatus.TOO_LARGE);
            }
            if (inflater.getRemaining() > 1) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            return Arrays.copyOf(out, total);
        } catch (DataFormatException e) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        } finally {
            inflater.end();
        }
    }
}
