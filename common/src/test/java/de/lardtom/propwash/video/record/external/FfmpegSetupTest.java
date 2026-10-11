package de.lardtom.propwash.video.record.external;

import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.RecordingQuality;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegSetupTest {
    @Test
    void platformsOfferTheirHardwareEncoders() {
        assertEquals(List.of(FfmpegCodec.VIDEOTOOLBOX), FfmpegCodec.candidates(FfmpegCodec.Platform.MAC));
        assertEquals(List.of(FfmpegCodec.NVENC, FfmpegCodec.QSV, FfmpegCodec.AMF, FfmpegCodec.MEDIA_FOUNDATION),
                FfmpegCodec.candidates(FfmpegCodec.Platform.WINDOWS));
        assertEquals(List.of(FfmpegCodec.NVENC, FfmpegCodec.QSV, FfmpegCodec.VAAPI), FfmpegCodec.candidates(FfmpegCodec.Platform.LINUX));
        assertTrue(FfmpegCodec.candidates(FfmpegCodec.Platform.OTHER).isEmpty());
    }

    @Test
    void everyCommandFeedsRawI420AndWantsAnnexBWithoutBFrames() {
        RecordingFormat format = RecordingFormat.of(1080, 60);
        for (FfmpegCodec codec : FfmpegCodec.values()) {
            for (int variant = 0; variant < codec.variants(); variant++) {
                List<String> command = codec.command("ffmpeg", format, RecordingQuality.STANDARD, variant);
                String line = String.join(" ", command);
                assertEquals("ffmpeg", command.getFirst());
                assertTrue(line.contains("-f rawvideo -pix_fmt yuv420p -video_size 1920x1080 -framerate 60"), line);
                assertTrue(line.contains("-i pipe:0"), line);
                assertTrue(line.contains("-c:v " + codec.encoder()), line);
                assertTrue(line.contains("-g 120 -bf 0"), line);
                assertTrue(line.endsWith("-f h264 pipe:1"), line);
                assertTrue(line.contains("-colorspace bt709") && line.contains("-color_range tv"), line);
                long cap = RecordingQuality.STANDARD.maxBitrate(format);
                int target = command.indexOf("-b:v");
                assertTrue(target > 0 && Long.parseLong(command.get(target + 1)) <= cap, line);
                int max = command.indexOf("-maxrate");
                assertTrue(max < 0 || Long.parseLong(command.get(max + 1)) == cap, line);
            }
        }
        String vaapi = String.join(" ", FfmpegCodec.VAAPI.command("ffmpeg", format, RecordingQuality.HIGH, 0));
        assertTrue(vaapi.indexOf("-vaapi_device") < vaapi.indexOf("-i pipe:0") && vaapi.contains("hwupload"), vaapi);
    }

    @Test
    void locatorPrefersTheConfiguredPathThenGameFolderThenPath() {
        Assumptions.assumeTrue(FfmpegCodec.Platform.current() != FfmpegCodec.Platform.WINDOWS);
        Map<String, String> env = Map.of("PATH", String.join(java.io.File.pathSeparator, "/a/bin", "relative/bin", "", "/b/bin"));
        List<Path> candidates = FfmpegLocator.candidates("/custom/ffmpeg", List.of(Path.of("/game/propwash/ffmpeg")), FfmpegCodec.Platform.MAC, env);
        assertEquals(Path.of("/custom/ffmpeg"), candidates.get(0));
        assertTrue(candidates.indexOf(Path.of("/game/propwash/ffmpeg/ffmpeg")) < candidates.indexOf(Path.of("/a/bin/ffmpeg")));
        assertTrue(candidates.indexOf(Path.of("/a/bin/ffmpeg")) < candidates.indexOf(Path.of("/b/bin/ffmpeg")));
        assertTrue(candidates.contains(Path.of("/opt/homebrew/bin/ffmpeg")));
        assertTrue(candidates.stream().allMatch(Path::isAbsolute), candidates.toString());
        List<Path> windows = FfmpegLocator.candidates("", List.of(Path.of("game")), FfmpegCodec.Platform.WINDOWS, Map.of());
        assertTrue(windows.stream().allMatch(path -> path.getFileName().toString().equals("ffmpeg.exe")), windows.toString());
    }

    @Test
    void locatorFindsAnExecutableInTheGameFolder(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(FfmpegCodec.Platform.current() != FfmpegCodec.Platform.WINDOWS);
        Path folder = Files.createDirectories(dir.resolve("propwash/ffmpeg"));
        Path binary = Files.writeString(folder.resolve("ffmpeg"), "#!/bin/sh\nexit 0\n");
        assertTrue(FfmpegLocator.find("", List.of(folder), FfmpegCodec.Platform.LINUX, Map.of("PATH", "")).isPresent() == Files.isExecutable(binary));
        assertTrue(binary.toFile().setExecutable(true));
        assertEquals(Optional.of(binary), FfmpegLocator.find("", List.of(folder), FfmpegCodec.Platform.LINUX, Map.of("PATH", "")));
        assertEquals(Optional.of(binary), FfmpegLocator.find(folder.toString(), List.of(), FfmpegCodec.Platform.LINUX, Map.of("PATH", "")));
    }
}
