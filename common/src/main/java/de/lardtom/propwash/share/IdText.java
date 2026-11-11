package de.lardtom.propwash.share;

public final class IdText {
    private IdText() {
    }

    public static boolean encodable(String text, int max) {
        if (text == null || text.isEmpty() || text.length() > max) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Pw3Params.ID_ALPHABET.indexOf(text.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    public static long bits(String text) {
        return BitWriter.golombLength(text.length() - 1L, Pw3Params.K_ID_TEXT) + 6L * text.length();
    }

    public static void write(BitWriter out, String text, int max) {
        if (!encodable(text, max)) {
            throw new ShareException(ShareStatus.CORRUPT, "propwash.share.error.part_id", String.valueOf(text));
        }
        out.golomb(text.length() - 1L, Pw3Params.K_ID_TEXT);
        for (int i = 0; i < text.length(); i++) {
            out.bits(Pw3Params.ID_ALPHABET.indexOf(text.charAt(i)), 6);
        }
    }

    public static String read(BitReader in, int max) throws ShareFormatException {
        long length = in.golomb(Pw3Params.K_ID_TEXT) + 1;
        if (length * 6 > in.remaining()) {
            throw new ShareFormatException(ShareStatus.TRUNCATED);
        }
        if (length > max) {
            throw new ShareFormatException(ShareStatus.CORRUPT);
        }
        StringBuilder out = new StringBuilder((int) length);
        for (int i = 0; i < length; i++) {
            int symbol = (int) in.bits(6);
            if (symbol >= Pw3Params.ID_ALPHABET.length()) {
                throw new ShareFormatException(ShareStatus.CORRUPT);
            }
            out.append(Pw3Params.ID_ALPHABET.charAt(symbol));
        }
        return out.toString();
    }
}
