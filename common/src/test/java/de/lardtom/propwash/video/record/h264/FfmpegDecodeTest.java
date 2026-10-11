package de.lardtom.propwash.video.record.h264;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FfmpegDecodeTest {
    private static final int WIDTH = 352;
    private static final int HEIGHT = 200;

    static Path tool(String name) {
        List<String> dirs = new ArrayList<>();
        String path = System.getenv("PATH");
        if (path != null) {
            dirs.addAll(List.of(path.split(java.io.File.pathSeparator)));
        }
        dirs.addAll(List.of("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin"));
        for (String dir : dirs) {
            if (dir.isEmpty()) {
                continue;
            }
            Path candidate = Path.of(dir, name);
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static byte[] run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread errors = new Thread(() -> copy(process.getErrorStream(), err));
        errors.start();
        copy(process.getInputStream(), out);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "timeout: " + command);
        errors.join(5000L);
        assertEquals(0, process.exitValue(), command + ": " + err.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8).trim(), "decoder messages");
        return out.toByteArray();
    }

    private static void copy(InputStream in, ByteArrayOutputStream out) {
        try (in) {
            in.transferTo(out);
        } catch (IOException ignored) {
        }
    }

    private static byte[] scene(int index) {
        int offsetX = index == 12 ? 140 : index * 7;
        int offsetY = index == 12 ? -60 : index * 2;
        byte[] frame = EncoderQualityTest.smoothScene(WIDTH, HEIGHT, offsetX, offsetY);
        if (index % 5 == 3) {
            for (int y = 60; y < 140; y++) {
                for (int x = 20; x < 120; x++) {
                    frame[y * WIDTH + x] = (byte) (18 + (x + y + index) % 3);
                }
            }
        }
        return frame;
    }

    @Test
    void ffmpegDecodesExactlyTheEncoderReconstruction(@TempDir Path dir) throws IOException, InterruptedException {
        Path ffmpeg = tool("ffmpeg");
        assumeTrue(ffmpeg != null, "ffmpeg not installed");
        EncoderSettings settings = new EncoderSettings(3, 5, 4_000_000L, 10, 12, 46, true, 24, 1.0);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        int frames = 0;
        try (H264Encoder encoder = new H264Encoder(WIDTH, HEIGHT, 30, settings)) {
            stream.write(new byte[]{0, 0, 0, 1});
            stream.write(encoder.sps());
            stream.write(new byte[]{0, 0, 0, 1});
            stream.write(encoder.pps());
            for (int i = 0; i < 24; i++) {
                int preset = i < 16 ? SliceEncoder.PRESET_QUALITY : i % 3;
                EncodedFrame frame = i == 9 ? encoder.repeat() : encoder.encode(scene(i), preset);
                stream.write(frame.toAnnexB());
                expected.write(encoder.reconstructionI420());
                frames++;
            }
        }
        Path file = dir.resolve("clip.h264");
        Files.write(file, stream.toByteArray());
        byte[] decoded = run(List.of(ffmpeg.toString(), "-v", "error", "-i", file.toString(), "-f", "rawvideo", "-pix_fmt", "yuv420p", "-"));
        assertEquals(frames * WIDTH * HEIGHT * 3 / 2, decoded.length);
        assertArrayEquals(expected.toByteArray(), decoded);
        Path ffprobe = tool("ffprobe");
        if (ffprobe != null) {
            String probe = new String(run(List.of(ffprobe.toString(), "-v", "error", "-show_entries", "stream=profile,width,height",
                    "-of", "default=noprint_wrappers=1", file.toString())), StandardCharsets.UTF_8);
            assertTrue(probe.contains("profile=Constrained Baseline"), probe);
            assertTrue(probe.contains("width=" + WIDTH) && probe.contains("height=" + HEIGHT), probe);
        }
    }
}
