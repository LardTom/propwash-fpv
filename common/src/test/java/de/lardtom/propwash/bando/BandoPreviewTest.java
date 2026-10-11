package de.lardtom.propwash.bando;

import de.lardtom.propwash.bando.plan.BandoGenerator;
import de.lardtom.propwash.bando.plan.BandoPlan;
import de.lardtom.propwash.bando.plan.BandoVariant;
import de.lardtom.propwash.bando.plan.Mat;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BandoPreviewTest {
    private static final String ENV = "PROPWASH_BANDO_PREVIEW";
    private static final int CELL = 16;
    private static final Map<Mat, Color> COLORS = new EnumMap<>(Mat.class);

    static {
        COLORS.put(Mat.FOUNDATION, new Color(0x6E6A64));
        COLORS.put(Mat.FLOOR, new Color(0x9C968C));
        COLORS.put(Mat.WALL, new Color(0xB0764E));
        COLORS.put(Mat.WALL_ALT, new Color(0x8E9AA2));
        COLORS.put(Mat.COLUMN, new Color(0x45484C));
        COLORS.put(Mat.BEAM, new Color(0x5A5E64));
        COLORS.put(Mat.BRACE, new Color(0x2E2E2E));
        COLORS.put(Mat.ROOF, new Color(0x5F8A82));
        COLORS.put(Mat.ROOF_STEP, new Color(0x6B4A3A));
        COLORS.put(Mat.CATWALK, new Color(0xC2A060));
        COLORS.put(Mat.RAIL, new Color(0x3A2A1A));
        COLORS.put(Mat.PIPE, new Color(0xC07040));
        COLORS.put(Mat.TANK, new Color(0xA8A49C));
        COLORS.put(Mat.STACK, new Color(0x8A4A3A));
        COLORS.put(Mat.TRIM, new Color(0xE0DCD0));
        COLORS.put(Mat.CHAIN, new Color(0x222222));
        COLORS.put(Mat.LADDER, new Color(0xA07840));
        COLORS.put(Mat.WINDOW, new Color(0xA0D0E0));
        COLORS.put(Mat.DEBRIS, new Color(0x7A7A7A));
        COLORS.put(Mat.DEBRIS_LOW, new Color(0x8A8A8A));
        COLORS.put(Mat.CARGO, new Color(0xD8C048));
        COLORS.put(Mat.FOLIAGE, new Color(0x3C8A2E));
        COLORS.put(Mat.VINE, new Color(0x4AA03A));
        COLORS.put(Mat.MOSS, new Color(0x5C9A3C));
        COLORS.put(Mat.FLOAT, new Color(0x2E7A2E));
        COLORS.put(Mat.WATER, new Color(0x3060C0));
        COLORS.put(Mat.CHEST, new Color(0xFF3010));
        COLORS.put(Mat.YARD, new Color(0xB8A88A));
    }

    @Test
    void renderPreviews() throws IOException {
        String dir = System.getenv(ENV);
        assumeTrue(dir != null && !dir.isBlank(), "nur mit " + ENV);
        Path out = Path.of(dir);
        Files.createDirectories(out);
        int seeds = Integer.parseInt(System.getenv().getOrDefault(ENV + "_SEEDS", "3"));
        for (BandoVariant variant : BandoVariant.values()) {
            for (int s = 0; s < seeds; s++) {
                long seed = 1000L * variant.ordinal() + s * 7919L + 17L;
                BandoPlan plan = BandoGenerator.generate(variant, seed);
                BufferedImage a = render(plan, 0);
                BufferedImage b = render(plan, 2);
                BufferedImage both = new BufferedImage(a.getWidth() + b.getWidth(), Math.max(a.getHeight(), b.getHeight()), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = both.createGraphics();
                g.setColor(new Color(0xEEF2F5));
                g.fillRect(0, 0, both.getWidth(), both.getHeight());
                g.drawImage(a, 0, 0, null);
                g.drawImage(b, a.getWidth(), 0, null);
                g.setColor(Color.BLACK);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
                g.drawString(variant.key() + " seed " + seed + " " + plan.sizeX() + "x" + plan.sizeY() + "x" + plan.sizeZ()
                        + (plan.hasChest() ? " chest" : ""), 8, 20);
                g.dispose();
                ImageIO.write(both, "png", out.resolve(String.format(Locale.ROOT, "%s_%d.png", variant.key(), s)).toFile());
            }
        }
    }

    private record Cube(int x, int y, int z, Color color, boolean flat) {
    }

    static BufferedImage render(BandoPlan plan, int turns) {
        int sx = plan.sizeX();
        int sz = plan.sizeZ();
        int rx = (turns & 1) == 0 ? sx : sz;
        int rz = (turns & 1) == 0 ? sz : sx;
        List<Cube> cubes = new ArrayList<>();
        for (int y = 0; y < plan.sizeY(); y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    Mat mat = plan.mat(x, y, z);
                    int vx = turns == 0 ? x : sx - 1 - x;
                    int vz = turns == 0 ? z : sz - 1 - z;
                    if (mat == Mat.EMPTY || mat == Mat.AIR) {
                        if (y == plan.ground() && mat == Mat.EMPTY) {
                            cubes.add(new Cube(vx, y, vz, new Color(0xA8C890), true));
                        }
                        continue;
                    }
                    Color c = COLORS.getOrDefault(mat, Color.MAGENTA);
                    cubes.add(new Cube(vx, y, vz, c, mat == Mat.MOSS || mat == Mat.DEBRIS_LOW || mat == Mat.FLOAT));
                }
            }
        }
        cubes.sort(Comparator.comparingInt(c -> c.x() + c.y() + c.z()));
        int half = CELL / 2;
        int quarter = CELL / 4;
        int width = (rx + rz) * half + 40;
        int height = (rx + rz) * quarter + plan.sizeY() * (CELL * 3 / 5) + 60;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(0xEEF2F5));
        g.fillRect(0, 0, width, height);
        int ox = rz * half + 20;
        int oy = plan.sizeY() * (CELL * 3 / 5) + 30;
        int ch = CELL * 3 / 5;
        for (Cube c : cubes) {
            int px = ox + (c.x() - c.z()) * half;
            int py = oy + (c.x() + c.z()) * quarter - c.y() * ch;
            int h = c.flat() ? ch / 4 : ch;
            Polygon top = new Polygon(new int[]{px, px + half, px, px - half}, new int[]{py - h, py - h + quarter, py - h + 2 * quarter, py - h + quarter}, 4);
            Polygon left = new Polygon(new int[]{px - half, px, px, px - half}, new int[]{py - h + quarter, py - h + 2 * quarter, py + 2 * quarter, py + quarter}, 4);
            Polygon right = new Polygon(new int[]{px, px + half, px + half, px}, new int[]{py - h + 2 * quarter, py - h + quarter, py + quarter, py + 2 * quarter}, 4);
            g.setColor(c.color());
            g.fillPolygon(top);
            g.setColor(c.color().darker());
            g.fillPolygon(left);
            g.setColor(c.color().darker().darker());
            g.fillPolygon(right);
        }
        g.dispose();
        return image;
    }
}
