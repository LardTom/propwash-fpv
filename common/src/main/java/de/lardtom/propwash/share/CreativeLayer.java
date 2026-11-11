package de.lardtom.propwash.share;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.creative.CreativeSpec;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class CreativeLayer {
    private CreativeLayer() {
    }

    public static BitWriter write(CreativeSpec spec, List<String> heads) {
        BitWriter out = new BitWriter();
        int mask = 0;
        for (int i = 0; i < Pw3Params.CREATIVE_BASE_KINDS.size(); i++) {
            if (spec.base(Pw3Params.CREATIVE_BASE_KINDS.get(i)).isPresent()) {
                mask |= 1 << (Pw3Params.CREATIVE_BASE_BITS - 1 - i);
            }
        }
        out.bits(mask, Pw3Params.CREATIVE_BASE_BITS);
        for (PartKind kind : Pw3Params.CREATIVE_BASE_KINDS) {
            spec.base(kind).ifPresent(key -> writeBase(out, key, heads));
        }
        List<Map.Entry<String, Double>> indexed = new ArrayList<>();
        List<Map.Entry<String, Double>> literal = new ArrayList<>();
        for (Map.Entry<String, Double> entry : spec.values().entrySet()) {
            (Pw3Params.creativeIndex(entry.getKey()) > 0 ? indexed : literal).add(entry);
        }
        indexed.sort(Comparator.comparingInt(e -> Pw3Params.creativeIndex(e.getKey())));
        out.golomb(indexed.size(), Pw3Params.K_CREATIVE_COUNT);
        int previous = 0;
        for (Map.Entry<String, Double> entry : indexed) {
            int index = Pw3Params.creativeIndex(entry.getKey());
            out.golomb(index - previous - 1L, Pw3Params.K_CREATIVE_GAP);
            previous = index;
            writeValue(out, entry.getValue());
        }
        out.golomb(literal.size(), Pw3Params.K_CREATIVE_LITERAL_COUNT);
        for (Map.Entry<String, Double> entry : literal) {
            LayerCodec.writeText(out, entry.getKey());
            writeValue(out, entry.getValue());
        }
        return out;
    }

    static void writeBase(BitWriter out, String key, List<String> heads) {
        int colon = key.indexOf(':');
        String namespace = colon < 0 ? ShareTables.PROPWASH : key.substring(0, colon);
        String path = colon < 0 ? key : key.substring(colon + 1);
        boolean canonical = colon < 0 || !namespace.equals(ShareTables.PROPWASH);
        int head = heads.indexOf(namespace);
        if (canonical && head >= 0 && IdText.encodable(path, Pw3Params.MAX_PATH_CHARS)) {
            out.golomb(head + 1L, Pw3Params.K_CREATIVE_NAMESPACE);
            IdText.write(out, path, Pw3Params.MAX_PATH_CHARS);
            return;
        }
        out.golomb(0, Pw3Params.K_CREATIVE_NAMESPACE);
        LayerCodec.writeText(out, key);
    }

    static void writeValue(BitWriter out, double value) {
        long[] decimal = decimal(value);
        if (decimal == null) {
            out.bit(true);
            out.bits(Double.doubleToRawLongBits(value), 64);
            return;
        }
        out.bit(false);
        out.golomb(decimal[1], Pw3Params.K_CREATIVE_DECIMALS);
        long mantissa = decimal[0];
        out.golomb(mantissa >= 0 ? 2 * mantissa : -2 * mantissa - 1, Pw3Params.K_CREATIVE_VALUE);
    }

    static long[] decimal(double value) {
        if (!Double.isFinite(value)) {
            return null;
        }
        BigDecimal number = new BigDecimal(Double.toString(value)).stripTrailingZeros();
        if (number.scale() < 0) {
            number = number.setScale(0);
        }
        if (number.scale() > Pw3Params.MAX_CREATIVE_DECIMALS || number.unscaledValue().bitLength() >= Pw3Params.MAX_CREATIVE_MANTISSA_BITS) {
            return null;
        }
        long mantissa = number.unscaledValue().longValueExact();
        if (number(mantissa, number.scale()) != value) {
            return null;
        }
        return new long[]{mantissa, number.scale()};
    }

    static double number(long mantissa, int decimals) {
        return BigDecimal.valueOf(mantissa, decimals).doubleValue() + 0.0;
    }

    public static CreativeSpec read(BitReader in, List<String> heads, ShareParsed parsed) throws ShareFormatException {
        int mask = (int) in.bits(Pw3Params.CREATIVE_BASE_BITS);
        EnumMap<PartKind, String> base = new EnumMap<>(PartKind.class);
        for (int i = 0; i < Pw3Params.CREATIVE_BASE_KINDS.size(); i++) {
            if ((mask & (1 << (Pw3Params.CREATIVE_BASE_BITS - 1 - i))) != 0) {
                base.put(Pw3Params.CREATIVE_BASE_KINDS.get(i), readBase(in, heads));
            }
        }
        TreeMap<String, Double> values = new TreeMap<>();
        int indexed = in.golombInt(Pw3Params.K_CREATIVE_COUNT, Pw3Params.MAX_CREATIVE_VALUES);
        long index = 0;
        for (int i = 0; i < indexed; i++) {
            index += in.golomb(Pw3Params.K_CREATIVE_GAP) + 1;
            if (index > Integer.MAX_VALUE) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            double value = readValue(in);
            if (index > Pw3Params.CREATIVE_KEYS.size()) {
                parsed.skipOnce("creative_index:" + index);
                continue;
            }
            values.put(Pw3Params.CREATIVE_KEYS.get((int) index - 1), value);
        }
        int literal = in.golombInt(Pw3Params.K_CREATIVE_LITERAL_COUNT, Pw3Params.MAX_CREATIVE_VALUES - indexed);
        for (int i = 0; i < literal; i++) {
            String key = LayerCodec.readText(in, Pw3Params.MAX_CREATIVE_KEY_CHARS);
            if (key.isEmpty() || key.length() > Pw3Params.MAX_CREATIVE_KEY_CHARS) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            double value = readValue(in);
            if (values.containsKey(key)) {
                parsed.skipOnce("creative_duplicate:" + key);
            }
            values.put(key, value);
        }
        return new CreativeSpec(base, values);
    }

    static String readBase(BitReader in, List<String> heads) throws ShareFormatException {
        int head = in.golombInt(Pw3Params.K_CREATIVE_NAMESPACE, heads.size());
        if (head == 0) {
            String key = LayerCodec.readText(in, Pw3Params.MAX_CREATIVE_KEY_CHARS);
            if (key.isEmpty() || key.length() > Pw3Params.MAX_CREATIVE_KEY_CHARS) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            return key;
        }
        String namespace = heads.get(head - 1);
        String path = IdText.read(in, Pw3Params.MAX_PATH_CHARS);
        String key = namespace.equals(ShareTables.PROPWASH) ? path : namespace + ":" + path;
        if (key.length() > Pw3Params.MAX_CREATIVE_KEY_CHARS) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        return key;
    }

    static double readValue(BitReader in) throws ShareFormatException {
        if (in.bit()) {
            double value = Double.longBitsToDouble(in.bits(64));
            if (!Double.isFinite(value)) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            return value + 0.0;
        }
        int decimals = in.golombInt(Pw3Params.K_CREATIVE_DECIMALS, Pw3Params.MAX_CREATIVE_DECIMALS);
        long zigzag = in.golomb(Pw3Params.K_CREATIVE_VALUE);
        if (zigzag >= 1L << Pw3Params.MAX_CREATIVE_MANTISSA_BITS) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        long mantissa = (zigzag >>> 1) ^ -(zigzag & 1);
        return number(mantissa, decimals);
    }
}
