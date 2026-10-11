package de.lardtom.propwash.join;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class SafeText {
    public static final int MAX_URL = 256;

    private SafeText() {
    }

    public static String clean(String text, int max) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (int i = 0; i < text.length() && count < max; ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0xA7) {
                if (i < text.length()) {
                    i += Character.charCount(text.codePointAt(i));
                }
                continue;
            }
            if (cp == '\n' || cp == '\r' || cp == '\t') {
                out.append(' ');
                count++;
            } else if (allowed(cp)) {
                out.appendCodePoint(Character.isWhitespace(cp) ? ' ' : cp);
                count++;
            }
        }
        return out.toString().replaceAll(" {2,}", " ").strip();
    }

    private static boolean allowed(int cp) {
        if (cp < 0x20 || cp == 0x7F) {
            return false;
        }
        if (cp >= 0x80 && cp < 0xA0) {
            return false;
        }
        if (cp >= 0x202A && cp <= 0x202E || cp >= 0x2066 && cp <= 0x2069 || cp == 0x200E || cp == 0x200F) {
            return false;
        }
        int type = Character.getType(cp);
        return type != Character.PRIVATE_USE && type != Character.SURROGATE && type != Character.UNASSIGNED && type != Character.FORMAT;
    }

    public static String url(String text) {
        if (text == null) {
            return "";
        }
        String value = text.strip();
        if (value.isEmpty() || value.length() > MAX_URL) {
            return "";
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x20 || c >= 0x7F || c == '"' || c == '<' || c == '>' || c == '\\' || c == '`') {
                return "";
            }
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return "";
            }
            String lower = scheme.toLowerCase(Locale.ROOT);
            if (!lower.equals("https") && !lower.equals("http")) {
                return "";
            }
            if (uri.getRawUserInfo() != null || uri.getHost() == null || uri.getHost().isBlank() || !uri.getHost().contains(".")) {
                return "";
            }
            return value;
        } catch (URISyntaxException e) {
            return "";
        }
    }

    public static String shortUrl(String url) {
        String value = url;
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        if (value.startsWith("www.")) {
            value = value.substring(4);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static String namespace(String text) {
        if (text == null || text.isEmpty() || text.length() > 64) {
            return "";
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.')) {
                return "";
            }
        }
        return text;
    }
}
