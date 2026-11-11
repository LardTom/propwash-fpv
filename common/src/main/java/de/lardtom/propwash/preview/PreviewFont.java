package de.lardtom.propwash.preview;

import java.util.ArrayList;
import java.util.List;

public final class PreviewFont {
    public static final int HEIGHT = 9;
    public static final int ASCENT = 7;
    public static final int SPACING = 1;
    public static final char FALLBACK = '?';
    private static final int RADIX = 32;
    private static final int RECORD = 2 + HEIGHT;
    private static final int TABLE_SIZE = 256;
    private static final String GLYPHS = "A5ehhvhhh00B5fhhfhhf00C5eh111he00D5fhhhhhf00E5v11f11v00F5v11f11100"
            + "G5eh1thhu00H5hhhvhhh00I3722222700J5s88899600K5h95359h00L5111111v00"
            + "M5hrllhhh00N5hhjlphh00O5ehhhhhe00P5fhhf11100Q5ehhhl9m00R5fhhf59h00"
            + "S5u11eggf00T5v44444400U5hhhhhhe00V5hhhhha400W5hhhllla00X5hha4ahh00"
            + "Y5hha444400Z5vg8421v00a500eguhu00b511djhhf00c500eh1he00d5ggmphhu00"
            + "e500ehv1e00f4c2f222200g500uhhhugeh511djhhh00i1101111100j3404444452"
            + "k4119535900l2111111200m500bllhh00n500djhhh00o500ehhhe00p500djhhf11"
            + "q500mphhuggr500dj11100s500u1egf00t422f222c00u500hhhpm00v500hhha400"
            + "w500hhlla00x500ha4ah00y500hhhhugez500v842v0005ehpljhe0013232222700"
            + "25ehg842v0035fggeggf00458ca9v880055v1fgghe0065c21fhhe0075vg8422200"
            + "85ehhehhe0095ehhug8600 3000000000.1000000100-4000f00000+5044v44000"
            + "_50000000v0·2000330000?5ehg840400";
    private static final Glyph[] TABLE = table();

    private PreviewFont() {
    }

    private static Glyph[] table() {
        if (GLYPHS.length() % RECORD != 0) {
            throw new IllegalStateException("Schrifttabelle beschädigt");
        }
        Glyph[] table = new Glyph[TABLE_SIZE];
        for (int start = 0; start < GLYPHS.length(); start += RECORD) {
            char c = GLYPHS.charAt(start);
            int width = Character.digit(GLYPHS.charAt(start + 1), RADIX);
            int[] rows = new int[HEIGHT];
            for (int y = 0; y < HEIGHT; y++) {
                rows[y] = Character.digit(GLYPHS.charAt(start + 2 + y), RADIX);
            }
            if (c >= TABLE_SIZE || table[c] != null || width < 1 || width > 5) {
                throw new IllegalStateException("Schrifttabelle beschädigt bei " + c);
            }
            table[c] = new Glyph(width, rows);
        }
        return table;
    }

    public static boolean covers(int codePoint) {
        return codePoint >= 0 && codePoint < TABLE_SIZE && TABLE[codePoint] != null;
    }

    private static Glyph glyph(int codePoint) {
        return covers(codePoint) ? TABLE[codePoint] : TABLE[FALLBACK];
    }

    public static int width(String text) {
        int[] codePoints = text.codePoints().toArray();
        int width = 0;
        for (int codePoint : codePoints) {
            width += glyph(codePoint).width;
        }
        return width + Math.max(0, codePoints.length - 1) * SPACING;
    }

    public static Raster raster(String text) {
        int width = width(text);
        boolean[] ink = new boolean[width * HEIGHT];
        int x = 0;
        for (int codePoint : text.codePoints().toArray()) {
            Glyph glyph = glyph(codePoint);
            for (int y = 0; y < HEIGHT; y++) {
                int row = glyph.rows[y];
                for (int column = 0; column < glyph.width; column++) {
                    if ((row >> column & 1) != 0) {
                        ink[y * width + x + column] = true;
                    }
                }
            }
            x += glyph.width + SPACING;
        }
        return new Raster(width, HEIGHT, ink);
    }

    private record Glyph(int width, int[] rows) {
    }

    public static final class Raster {
        private final int width;
        private final int height;
        private final boolean[] ink;
        private final int[] rects;

        private Raster(int width, int height, boolean[] ink) {
            this.width = width;
            this.height = height;
            this.ink = ink;
            this.rects = merge(width, height, ink);
        }

        private static int[] merge(int width, int height, boolean[] ink) {
            List<int[]> done = new ArrayList<>();
            List<int[]> open = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                List<int[]> next = new ArrayList<>();
                int x = 0;
                while (x < width) {
                    if (!ink[y * width + x]) {
                        x++;
                        continue;
                    }
                    int start = x;
                    while (x < width && ink[y * width + x]) {
                        x++;
                    }
                    int[] rect = null;
                    for (int i = 0; i < open.size(); i++) {
                        int[] candidate = open.get(i);
                        if (candidate[0] == start && candidate[2] == x) {
                            rect = candidate;
                            open.remove(i);
                            break;
                        }
                    }
                    if (rect == null) {
                        rect = new int[]{start, y, x, y + 1};
                    } else {
                        rect[3] = y + 1;
                    }
                    next.add(rect);
                }
                done.addAll(open);
                open = next;
            }
            done.addAll(open);
            int[] flat = new int[done.size() * 4];
            for (int i = 0; i < done.size(); i++) {
                System.arraycopy(done.get(i), 0, flat, i * 4, 4);
            }
            return flat;
        }

        public int width() {
            return this.width;
        }

        public int height() {
            return this.height;
        }

        public boolean ink(int x, int y) {
            return x >= 0 && y >= 0 && x < this.width && y < this.height && this.ink[y * this.width + x];
        }

        public int inkCount() {
            int count = 0;
            for (boolean on : this.ink) {
                if (on) {
                    count++;
                }
            }
            return count;
        }

        public int rectCount() {
            return this.rects.length / 4;
        }

        public int rect(int index, int corner) {
            return this.rects[index * 4 + corner];
        }
    }
}
