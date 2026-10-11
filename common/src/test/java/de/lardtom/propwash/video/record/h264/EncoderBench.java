package de.lardtom.propwash.video.record.h264;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class EncoderBench {
    public static void main(String[] args) throws IOException {
        Map<String, String> a = new HashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            a.put(arg.substring(0, eq), arg.substring(eq + 1));
        }
        int w = Integer.parseInt(a.getOrDefault("w", "1920"));
        int h = Integer.parseInt(a.getOrDefault("h", "1080"));
        int fps = Integer.parseInt(a.getOrDefault("fps", "60"));
        int threads = Integer.parseInt(a.getOrDefault("threads", "6"));
        long bitrate = Long.parseLong(a.getOrDefault("bitrate", "32300000"));
        int preset = Integer.parseInt(a.getOrDefault("preset", "0"));
        int frames = Integer.parseInt(a.getOrDefault("frames", "120"));
        double grain = Double.parseDouble(a.getOrDefault("grain", "0"));
        double grainHi = Double.parseDouble(a.getOrDefault("grainhi", "0.55"));
        double grainLo = Double.parseDouble(a.getOrDefault("grainlo", "0.02"));
        double grainChroma = Double.parseDouble(a.getOrDefault("grainchroma", "0.45"));
        int grainCell = Integer.parseInt(a.getOrDefault("graincell", "1"));
        int quality = Integer.parseInt(a.getOrDefault("quality", String.valueOf(EncoderSettings.DEFAULT_QUALITY)));
        double aq = Double.parseDouble(a.getOrDefault("aq", String.valueOf(EncoderSettings.DEFAULT_AQ)));
        boolean deblock = !"0".equals(a.get("deblock"));
        EncoderSettings base = EncoderSettings.of(threads, bitrate, fps, quality).withQuality(quality, aq);
        int slices = Integer.parseInt(a.getOrDefault("slices", String.valueOf(base.slices())));
        EncoderSettings settings = new EncoderSettings(base.threads(), slices, base.bitrate(), base.gop(), base.minQp(), base.maxQp(),
                deblock, quality, aq);
        int size = w * h * 3 / 2;
        byte[] frame = new byte[size];
        long bytes = 0;
        long nanos = 0;
        long qpSum = 0;
        Random random = new Random(1);
        try (InputStream in = new BufferedInputStream(new FileInputStream(a.get("in")), 1 << 22);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(a.get("out")), 1 << 22);
             OutputStream src = a.containsKey("src") ? new BufferedOutputStream(new FileOutputStream(a.get("src")), 1 << 22) : null;
             OutputStream recon = a.containsKey("recon") ? new BufferedOutputStream(new FileOutputStream(a.get("recon")), 1 << 22) : null;
             H264Encoder encoder = new H264Encoder(w, h, fps, settings)) {
            out.write(new byte[]{0, 0, 0, 1});
            out.write(encoder.sps());
            out.write(new byte[]{0, 0, 0, 1});
            out.write(encoder.pps());
            int n = 0;
            while (n < frames && in.readNBytes(frame, 0, size) == size) {
                if (grain > 0) {
                    addGrain(frame, w, h, grain, grainLo, grainHi, grainChroma, grainCell, random);
                }
                if (src != null) {
                    src.write(frame);
                }
                long t = System.nanoTime();
                EncodedFrame encoded = encoder.encode(frame, preset);
                nanos += System.nanoTime() - t;
                byte[] annexB = encoded.toAnnexB();
                out.write(annexB);
                bytes += annexB.length;
                qpSum += encoded.qp();
                if (recon != null) {
                    recon.write(encoder.reconstructionI420());
                }
                n++;
            }
            double seconds = n / (double) fps;
            System.out.printf("frames %d  encode %.1f fps  %.2f Mbit/s  avg qp %.1f  %.1f MB/min%n", n, n / (nanos * 1e-9),
                    bytes * 8 / seconds / 1e6, qpSum / (double) n, bytes / seconds * 60 / 1e6);
        }
    }

    private static void addGrain(byte[] f, int w, int h, double amp, double lo, double hi, double chroma, int cell, Random random) {
        int gw = w / cell + 2;
        int gh = h / cell + 2;
        float[] grid = new float[gw * gh * 3];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = (float) (random.nextDouble() - 0.5);
        }
        int cw = w / 2;
        int ch = h / 2;
        float[] nr = new float[w * h];
        float[] ng = new float[w * h];
        float[] nb = new float[w * h];
        float[] dk = new float[w * h];
        for (int i = 0; i < w * h; i++) {
            double l = ((f[i] & 0xFF) - 16) / 219.0;
            double t = Math.max(0, Math.min(1, (l - lo) / (hi - lo)));
            double dark = 1 - t * t * (3 - 2 * t);
            int px = i % w;
            int py = i / w;
            double g = sample(grid, gw, cell, px, py, 0);
            double cr = sample(grid, gw, cell, px, py, 1) * chroma;
            double cb = sample(grid, gw, cell, px, py, 2) * chroma;
            dk[i] = (float) (amp * dark);
            nr[i] = (float) (g + cr);
            ng[i] = (float) (g - 0.5 * (cr + cb));
            nb[i] = (float) (g + cb);
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                double ny = 0.2126 * nr[i] + 0.7152 * ng[i] + 0.0722 * nb[i];
                f[i] = clip((f[i] & 0xFF) + 219 * dk[i] * ny);
            }
        }
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                double cb = 0;
                double cr = 0;
                for (int k = 0; k < 4; k++) {
                    int i = (2 * y + (k >> 1)) * w + 2 * x + (k & 1);
                    double ny = 0.2126 * nr[i] + 0.7152 * ng[i] + 0.0722 * nb[i];
                    cb += dk[i] * (nb[i] - ny) / 1.8556;
                    cr += dk[i] * (nr[i] - ny) / 1.5748;
                }
                int u = w * h + y * cw + x;
                int v = w * h + cw * ch + y * cw + x;
                f[u] = clip((f[u] & 0xFF) + 224 * cb / 4);
                f[v] = clip((f[v] & 0xFF) + 224 * cr / 4);
            }
        }
    }

    private static double sample(float[] grid, int gw, int cell, int x, int y, int plane) {
        double fx = x / (double) cell;
        double fy = y / (double) cell;
        int ix = (int) fx;
        int iy = (int) fy;
        double tx = fx - ix;
        double ty = fy - iy;
        int base = plane * (grid.length / 3);
        double a = grid[base + iy * gw + ix];
        double b = grid[base + iy * gw + ix + 1];
        double c = grid[base + (iy + 1) * gw + ix];
        double d = grid[base + (iy + 1) * gw + ix + 1];
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
    }

    private static byte clip(double v) {
        long r = Math.round(v);
        return (byte) (r < 0 ? 0 : r > 255 ? 255 : r);
    }
}
