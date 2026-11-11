package de.lardtom.propwash.share.client.chat;

import de.lardtom.propwash.creative.CreativeShareCode;
import de.lardtom.propwash.share.Base62;
import de.lardtom.propwash.share.LayerCodec;
import de.lardtom.propwash.share.Pw3Params;
import de.lardtom.propwash.share.ShareFormat;
import de.lardtom.propwash.share.ShareFormatException;
import de.lardtom.propwash.share.legacy.Pw1Tables;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

public final class ChatCodeFinder {
    public static final int MAX_CODES = 3;
    public static final int MAX_CANDIDATES = 8;
    public static final int MAX_CHARS = Pw3Params.MAX_INPUT_CHARS;
    private static final int PW1_TAIL_TRIES = 2;
    private static final String EDITOR_PREFIX = ShareFormat.DRONE_EDITOR + "-";
    private static final int[] PW1_VALUES = new int[128];

    static {
        Arrays.fill(PW1_VALUES, -1);
        for (int i = 0; i < Pw1Tables.ALPHABET.length(); i++) {
            PW1_VALUES[Pw1Tables.ALPHABET.charAt(i)] = i;
        }
        PW1_VALUES['O'] = 0;
        PW1_VALUES['I'] = 1;
        PW1_VALUES['L'] = 1;
    }

    private ChatCodeFinder() {
    }

    public record Found(int start, int end, String code, String format) {
    }

    public static boolean mayContain(@Nullable String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i + 2 < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == 'P' || c == 'p') && (prefix(text, i) > 0 || pwd1(text, i))) {
                return true;
            }
        }
        return false;
    }

    public static List<Found> find(@Nullable String text) {
        List<Found> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        int candidates = 0;
        int i = 0;
        while (i + 3 < text.length() && out.size() < MAX_CODES && candidates < MAX_CANDIDATES) {
            char c = text.charAt(i);
            int version = c == 'P' || c == 'p' ? prefix(text, i) : 0;
            boolean editor = version == 0 && (c == 'P' || c == 'p') && pwd1(text, i);
            if ((version > 0 || editor) && boundaryBefore(text, i)) {
                candidates++;
                Found found = editor ? pwd1Code(text, i) : version == 1 ? pw1(text, i) : base62(text, i, version);
                if (found != null) {
                    out.add(found);
                    i = found.end();
                    continue;
                }
            }
            i++;
        }
        return out;
    }

    private static int prefix(String text, int at) {
        char w = text.charAt(at + 1);
        char v = text.charAt(at + 2);
        return (w == 'W' || w == 'w') && v >= '1' && v <= '3' ? v - '0' : 0;
    }

    private static boolean pwd1(String text, int at) {
        return at + EDITOR_PREFIX.length() < text.length() && text.regionMatches(true, at, EDITOR_PREFIX, 0, EDITOR_PREFIX.length());
    }

    private static boolean base64(char c) {
        return ascii(c) || c == '-' || c == '_';
    }

    private static @Nullable Found pwd1Code(String text, int start) {
        int at = start + EDITOR_PREFIX.length();
        while (at < text.length() && at - start <= MAX_CHARS && base64(text.charAt(at))) {
            at++;
        }
        if (at - start > MAX_CHARS) {
            return null;
        }
        int shortest = at;
        while (shortest > start + EDITOR_PREFIX.length() + 1 && text.charAt(shortest - 1) == '-') {
            shortest--;
        }
        for (int end = shortest; end <= at; end++) {
            if (boundaryAfter(text, end) && CreativeShareCode.decode(text.substring(start, end)).isPresent()) {
                return new Found(start, end, text.substring(start, end), ShareFormat.DRONE_EDITOR);
            }
        }
        return null;
    }

    static boolean word(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean boundaryBefore(String text, int at) {
        return at == 0 || !word(text.charAt(at - 1));
    }

    private static boolean boundaryAfter(String text, int at) {
        return at >= text.length() || !word(text.charAt(at));
    }

    private static boolean ascii(char c) {
        return c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }

    private static @Nullable Found base62(String text, int start, int version) {
        int at = start + 3;
        if (at < text.length() && text.charAt(at) == '-') {
            at++;
        }
        int body = at;
        while (at < text.length() && at - start <= MAX_CHARS && ascii(text.charAt(at))) {
            at++;
        }
        if (at == body || at - start > MAX_CHARS || !boundaryAfter(text, at)) {
            return null;
        }
        if (!frameIntact(text.substring(body, at))) {
            return null;
        }
        return new Found(start, at, text.substring(start, at), "PW" + version);
    }

    static boolean frameIntact(String symbols) {
        if (symbols.isEmpty() || Base62.bytesFor(symbols.length()) < 0) {
            return false;
        }
        try {
            LayerCodec.openFrame(symbols);
            return true;
        } catch (ShareFormatException | RuntimeException e) {
            return false;
        }
    }

    private static @Nullable Found pw1(String text, int start) {
        int at = start + 3;
        if (at < text.length() && text.charAt(at) == '-') {
            at++;
        }
        List<Integer> ends = new ArrayList<>();
        while (true) {
            int group = at;
            while (at < text.length() && ascii(text.charAt(at))) {
                at++;
                if (at - start > MAX_CHARS) {
                    return null;
                }
            }
            if (at == group) {
                break;
            }
            ends.add(at);
            if (at + 1 < text.length() && text.charAt(at) == '-' && ascii(text.charAt(at + 1))) {
                at++;
            } else {
                break;
            }
        }
        if (ends.isEmpty() || !boundaryAfter(text, ends.getLast())) {
            return null;
        }
        for (int i = ends.size() - 1; i >= 0 && i >= ends.size() - 1 - PW1_TAIL_TRIES; i--) {
            int end = ends.get(i);
            if (pw1Intact(text.substring(start + 3, end))) {
                return new Found(start, end, text.substring(start, end), Pw1Tables.FORMAT);
            }
        }
        return null;
    }

    static boolean pw1Intact(String body) {
        String symbols = body.replace("-", "").toUpperCase(Locale.ROOT);
        if (symbols.isEmpty()) {
            return false;
        }
        byte[] frame = new byte[symbols.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (int i = 0; i < symbols.length(); i++) {
            char c = symbols.charAt(i);
            int value = c < PW1_VALUES.length ? PW1_VALUES[c] : -1;
            if (value < 0) {
                return false;
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                frame[index++] = (byte) (buffer >>> bits);
                buffer &= (1 << bits) - 1;
            }
        }
        if (buffer != 0 || frame.length < Pw1Tables.MIN_FRAME_BYTES || frame.length > Pw3Params.MAX_FRAME_BYTES) {
            return false;
        }
        int payload = frame.length - 4;
        CRC32 crc = new CRC32();
        crc.update(frame, 0, payload);
        long stored = ((long) (frame[payload] & 0xFF) << 24) | ((long) (frame[payload + 1] & 0xFF) << 16)
                | ((long) (frame[payload + 2] & 0xFF) << 8) | (frame[payload + 3] & 0xFF);
        return crc.getValue() == stored;
    }
}
