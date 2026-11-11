package de.lardtom.propwash.share;

import java.math.BigInteger;
import java.util.Arrays;

public final class Base62 {
    public static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(62);
    private static final BigInteger CHUNK = BASE.pow(10);
    private static final int[] VALUES = new int[128];

    static {
        Arrays.fill(VALUES, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            VALUES[ALPHABET.charAt(i)] = i;
        }
    }

    private Base62() {
    }

    public static int symbolsFor(int bytes) {
        BigInteger limit = BigInteger.ONE.shiftLeft(8 * bytes);
        int count = (int) Math.ceil(bytes * 8 / 5.954196310386876);
        while (BASE.pow(count).compareTo(limit) < 0) {
            count++;
        }
        while (count > 0 && BASE.pow(count - 1).compareTo(limit) >= 0) {
            count--;
        }
        return count;
    }

    public static int bytesFor(int symbols) {
        int guess = (int) Math.floor(symbols * 5.954196310386876 / 8);
        for (int bytes = Math.max(0, guess - 1); bytes <= guess + 1; bytes++) {
            if (symbolsFor(bytes) == symbols) {
                return bytes;
            }
        }
        return -1;
    }

    public static String encode(byte[] data) {
        int count = symbolsFor(data.length);
        char[] out = new char[count];
        BigInteger value = new BigInteger(1, data);
        int at = count;
        while (at > 0) {
            BigInteger[] parts = value.divideAndRemainder(CHUNK);
            value = parts[0];
            long chunk = parts[1].longValueExact();
            for (int i = 0; i < 10 && at > 0; i++) {
                out[--at] = ALPHABET.charAt((int) (chunk % 62));
                chunk /= 62;
            }
        }
        return new String(out);
    }

    public static byte[] decode(CharSequence symbols) throws ShareFormatException {
        for (int i = 0; i < symbols.length(); i++) {
            char c = symbols.charAt(i);
            if (c >= 128 || VALUES[c] < 0) {
                throw new ShareFormatException(ShareStatus.BAD_CHARACTER);
            }
        }
        int bytes = bytesFor(symbols.length());
        if (bytes < 0) {
            throw new ShareFormatException(ShareStatus.TRUNCATED);
        }
        BigInteger value = BigInteger.ZERO;
        int i = 0;
        while (i < symbols.length()) {
            int take = Math.min(10, symbols.length() - i);
            long chunk = 0;
            for (int j = 0; j < take; j++) {
                chunk = chunk * 62 + VALUES[symbols.charAt(i + j)];
            }
            value = value.multiply(BASE.pow(take)).add(BigInteger.valueOf(chunk));
            i += take;
        }
        if (value.bitLength() > bytes * 8) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        byte[] raw = value.toByteArray();
        byte[] out = new byte[bytes];
        int copy = Math.min(raw.length, bytes);
        System.arraycopy(raw, raw.length - copy, out, bytes - copy, copy);
        return out;
    }
}
