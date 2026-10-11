package de.lardtom.propwash.video.record.external;

import de.lardtom.propwash.video.record.RecordingFormat;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegDetectionTest {
    private static final String GUI_PATH = "/usr/bin:/bin:/usr/sbin:/sbin";
    private static final String BANNER = "ffmpeg version 7.1.2-selftest Copyright (c) 2000-2026 the FFmpeg developers";
    private static final String SOFTWARE_ONLY = """
            case "$1" in
            -version) echo '%s'; echo 'built with test'; exit 0 ;;
            esac
            echo 'Encoders:'
            echo ' V..... = Video'
            echo ' V....D libx264              libx264 H.264'
            exit 0""".formatted(BANNER);

    private static boolean unix() {
        return FfmpegCodec.Platform.current() != FfmpegCodec.Platform.WINDOWS;
    }

    private static Path program(Path dir, String name, String body) throws IOException {
        Files.createDirectories(dir);
        Path file = Files.writeString(dir.resolve(name), "#!/bin/sh\n" + body + "\n");
        assertTrue(file.toFile().setExecutable(true));
        return file;
    }

    @Test
    void searchTellsWhereFfmpegWasFound(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(unix());
        Path custom = program(dir.resolve("custom"), "ffmpeg", "exit 0");
        Path game = program(dir.resolve("game"), "ffmpeg", "exit 0");
        Path onPath = program(dir.resolve("path"), "ffmpeg", "exit 0");
        Map<String, String> env = Map.of("PATH", onPath.getParent().toString());
        FfmpegCodec.Platform platform = FfmpegCodec.Platform.LINUX;
        FfmpegLocator.Search first = FfmpegLocator.search(custom.toString(), List.of(game.getParent()), platform, env, FfmpegLocator.ShellLookup.NONE);
        assertEquals(new FfmpegLocator.Found(custom, FfmpegLocator.Source.CUSTOM), first.found().orElseThrow());
        assertFalse(first.customMissing());
        FfmpegLocator.Search folder = FfmpegLocator.search(custom.getParent().toString(), List.of(), platform, env, FfmpegLocator.ShellLookup.NONE);
        assertEquals(new FfmpegLocator.Found(custom, FfmpegLocator.Source.CUSTOM), folder.found().orElseThrow());
        FfmpegLocator.Search second = FfmpegLocator.search("", List.of(game.getParent()), platform, env, FfmpegLocator.ShellLookup.NONE);
        assertEquals(new FfmpegLocator.Found(game, FfmpegLocator.Source.GAME_FOLDER), second.found().orElseThrow());
        FfmpegLocator.Search third = FfmpegLocator.search("", List.of(), platform, env, FfmpegLocator.ShellLookup.NONE);
        assertEquals(new FfmpegLocator.Found(onPath, FfmpegLocator.Source.PATH), third.found().orElseThrow());
        assertFalse(third.customMissing());
    }

    @Test
    void wrongCustomPathFallsBackAndSaysSo(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(unix());
        Path onPath = program(dir.resolve("path"), "ffmpeg", "exit 0");
        Map<String, String> env = Map.of("PATH", onPath.getParent().toString());
        FfmpegLocator.Search search = FfmpegLocator.search(dir.resolve("nowhere/ffmpeg").toString(), List.of(), FfmpegCodec.Platform.LINUX, env,
                FfmpegLocator.ShellLookup.NONE);
        assertEquals(new FfmpegLocator.Found(onPath, FfmpegLocator.Source.PATH), search.found().orElseThrow());
        assertTrue(search.customMissing());
        assertEquals(FfmpegLocator.CustomPath.MISSING, search.custom());
        Path plain = Files.writeString(Files.createDirectories(dir.resolve("download")).resolve("ffmpeg"), "no program");
        assertTrue(plain.toFile().setExecutable(false));
        FfmpegLocator.Search text = FfmpegLocator.search(plain.toString(), List.of(), FfmpegCodec.Platform.LINUX, env, FfmpegLocator.ShellLookup.NONE);
        assertTrue(text.customMissing());
        assertEquals(FfmpegLocator.CustomPath.NOT_EXECUTABLE, text.custom());
        assertFalse(Files.isExecutable(plain), "ein eigener Pfad wird nicht verändert");
        FfmpegLocator.Search folder = FfmpegLocator.search(plain.getParent().toString(), List.of(), FfmpegCodec.Platform.LINUX, Map.of("PATH", ""),
                FfmpegLocator.ShellLookup.NONE);
        assertEquals(FfmpegLocator.CustomPath.NOT_EXECUTABLE, folder.custom());
        assertEquals(FfmpegLocator.CustomPath.NONE,
                FfmpegLocator.search("", List.of(), FfmpegCodec.Platform.LINUX, env, FfmpegLocator.ShellLookup.NONE).custom());
        assertEquals(FfmpegLocator.CustomPath.USED,
                FfmpegLocator.search(onPath.toString(), List.of(), FfmpegCodec.Platform.LINUX, env, FfmpegLocator.ShellLookup.NONE).custom());
    }

    @Test
    void fileInTheGameFolderIsMadeExecutable(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(unix());
        Path folder = Files.createDirectories(dir.resolve("propwash/ffmpeg"));
        Path dropped = Files.writeString(folder.resolve("ffmpeg"), "#!/bin/sh\nexit 0\n");
        assertTrue(dropped.toFile().setExecutable(false));
        FfmpegLocator.Search search = FfmpegLocator.search("", List.of(folder), FfmpegCodec.Platform.LINUX, Map.of("PATH", ""),
                FfmpegLocator.ShellLookup.NONE);
        assertEquals(new FfmpegLocator.Found(dropped, FfmpegLocator.Source.GAME_FOLDER), search.found().orElseThrow());
        assertTrue(Files.isExecutable(dropped));
    }

    @Test
    void customPathAcceptsQuotesAndHome(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(unix());
        Path binary = program(dir.resolve("tools"), "ffmpeg", "exit 0");
        Map<String, String> env = Map.of("PATH", "", "HOME", dir.toString());
        FfmpegCodec.Platform platform = FfmpegCodec.Platform.LINUX;
        assertEquals("/a b/ffmpeg", FfmpegLocator.clean("  \"/a b/ffmpeg\" "));
        assertEquals("/a/ffmpeg", FfmpegLocator.clean("'/a/ffmpeg'"));
        assertEquals("", FfmpegLocator.clean(null));
        assertEquals(Optional.of(binary), FfmpegLocator.find("\"" + binary + "\"", List.of(), platform, env));
        assertEquals(Optional.of(binary), FfmpegLocator.find("~/tools/ffmpeg", List.of(), platform, env));
        assertEquals(Optional.of(binary), FfmpegLocator.find("~/tools", List.of(), platform, env));
    }

    @Test
    void knownFoldersCoverThePackageManagers() {
        Assumptions.assumeTrue(unix());
        Map<String, String> home = Map.of("HOME", "/srv/pilot");
        List<Path> mac = FfmpegLocator.known(FfmpegCodec.Platform.MAC, Map.of("HOME", "/data/pilot"));
        assertTrue(mac.containsAll(List.of(Path.of("/opt/homebrew/bin"), Path.of("/usr/local/bin"), Path.of("/opt/local/bin"),
                Path.of("/data/pilot/bin"), Path.of("/data/pilot/.local/bin"), Path.of("/data/pilot/.nix-profile/bin"),
                Path.of("/run/current-system/sw/bin"))), mac.toString());
        List<Path> linux = FfmpegLocator.known(FfmpegCodec.Platform.LINUX, home);
        assertTrue(linux.containsAll(List.of(Path.of("/usr/bin"), Path.of("/usr/local/bin"), Path.of("/snap/bin"),
                Path.of("/var/lib/flatpak/exports/bin"), Path.of("/home").resolve("linuxbrew/.linuxbrew/bin"), Path.of("/srv/pilot/.local/bin"),
                Path.of("/srv/pilot/bin"), Path.of("/nix/var/nix/profiles/default/bin"))), linux.toString());
        assertTrue(FfmpegLocator.known(FfmpegCodec.Platform.LINUX, Map.of("HOME", "relative")).stream().allMatch(Path::isAbsolute));
    }

    @Test
    void homebrewKegsWithoutLinkAreFound(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("opt/ffmpeg-full/bin"));
        Files.createDirectories(dir.resolve("opt/ffmpeg@7/bin"));
        Files.createDirectories(dir.resolve("opt/x264/bin"));
        assertEquals(List.of(dir.resolve("opt/ffmpeg-full/bin"), dir.resolve("opt/ffmpeg@7/bin")), FfmpegLocator.kegs(dir.resolve("opt")));
        assertTrue(FfmpegLocator.kegs(dir.resolve("missing")).isEmpty());
    }

    @Test
    void windowsFoldersComeFromTheEnvironment(@TempDir Path dir) throws IOException {
        Path local = dir.resolve("AppData/Local");
        Path profile = dir.resolve("pilot");
        Path programs = dir.resolve("Program Files");
        Path chocolatey = dir.resolve("ProgramData/chocolatey");
        Path build = Files.createDirectories(local.resolve("Microsoft/WinGet/Packages/Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe/ffmpeg-8.0-full_build/bin"));
        Path winget = Files.writeString(build.resolve("ffmpeg.exe"), "x");
        Files.createDirectories(local.resolve("Microsoft/WinGet/Packages/Other.Tool_x/bin"));
        Files.writeString(local.resolve("Microsoft/WinGet/Packages/Other.Tool_x/bin/ffmpeg.exe"), "x");
        assertTrue(winget.toFile().setExecutable(true));
        Map<String, String> env = new HashMap<>();
        env.put("LOCALAPPDATA", local.toString());
        env.put("USERPROFILE", profile.toString());
        env.put("ProgramFiles", programs.toString());
        env.put("ChocolateyInstall", chocolatey.toString());
        List<Path> known = FfmpegLocator.known(FfmpegCodec.Platform.WINDOWS, env);
        assertTrue(known.containsAll(List.of(local.resolve("Microsoft/WinGet/Links"), build, programs.resolve("WinGet/Links"),
                programs.resolve("ffmpeg/bin"), profile.resolve("scoop/apps/ffmpeg/current/bin"), profile.resolve("scoop/shims"),
                chocolatey.resolve("bin"))), known.toString());
        assertFalse(known.contains(local.resolve("Microsoft/WinGet/Packages/Other.Tool_x/bin")), known.toString());
        assertEquals(Optional.of(winget), FfmpegLocator.find("", List.of(), FfmpegCodec.Platform.WINDOWS, env));
        env.put("SCOOP", dir.resolve("tools/scoop").toString());
        env.remove("ChocolateyInstall");
        List<Path> moved = FfmpegLocator.known(FfmpegCodec.Platform.WINDOWS, env);
        assertTrue(moved.contains(dir.resolve("tools/scoop/shims")) && !moved.contains(profile.resolve("scoop/shims")), moved.toString());
        assertFalse(moved.contains(chocolatey.resolve("bin")), moved.toString());
        assertTrue(FfmpegLocator.known(FfmpegCodec.Platform.WINDOWS, Map.of()).isEmpty());
        List<Path> typed = FfmpegLocator.candidates(dir.resolve("my/ffmpeg").toString(), List.of(), FfmpegCodec.Platform.WINDOWS, Map.of());
        assertTrue(typed.contains(dir.resolve("my/ffmpeg.exe")), typed.toString());
    }

    @Test
    void loginShellOutputIsReadFromTheLastPathLine() {
        assertEquals(Optional.of(Path.of("/opt/tools/bin/ffmpeg")),
                LoginShell.parse(List.of("Welcome back", "/usr/bin/other", "/opt/tools/bin/ffmpeg", ""), "ffmpeg"));
        assertEquals(Optional.empty(), LoginShell.parse(List.of("alias ffmpeg='ffmpeg -hide_banner'"), "ffmpeg"));
        assertEquals(Optional.empty(), LoginShell.parse(List.of("ffmpeg", "bin/ffmpeg"), "ffmpeg"));
        assertEquals(Optional.empty(), LoginShell.parse(List.of(), "ffmpeg"));
    }

    @Test
    void loginShellFindsWhatTheProfilePutsOnThePath(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(unix() && Files.isExecutable(Path.of("/bin/sh")));
        Path home = Files.createDirectories(dir.resolve("home"));
        Path binary = program(home.resolve("tools"), "ffmpeg", "exit 0");
        Files.writeString(home.resolve(".profile"), "echo 'hello from the profile'\nPATH=\"$HOME/tools:$PATH\"\nexport PATH\n");
        Map<String, String> env = Map.of("HOME", home.toString(), "SHELL", "/bin/sh", "PATH", "/usr/bin:/bin");
        assertEquals(Optional.of(real(binary)), LoginShell.find("ffmpeg", env).map(FfmpegDetectionTest::real));
        FfmpegLocator.Search search = FfmpegLocator.search("", List.of(), FfmpegCodec.Platform.current(), Map.of("PATH", ""),
                (program, ignored) -> LoginShell.find(program, env));
        assertTrue(search.found().isPresent());
        if (search.found().get().source() == FfmpegLocator.Source.SHELL) {
            assertEquals(real(binary), real(search.found().get().path()));
        }
        assertEquals(Optional.empty(), LoginShell.find("ffmpeg; rm -rf x", env));
        assertEquals(Optional.empty(), LoginShell.find("ffmpeg", Map.of("HOME", home.toString(), "SHELL", "relative/sh")));
        assertEquals(Optional.empty(), LoginShell.find("ffmpeg", Map.of("HOME", home.toString(), "SHELL", "/bin/unknownshell")));
        assertEquals(Optional.empty(), LoginShell.find("ffmpeg", Map.of("HOME", home.toString())));
    }

    @Test
    void hangingLoginShellIsGivenUp(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(unix());
        Path shell = program(dir, "sh", "sleep 30");
        long started = System.nanoTime();
        assertEquals(Optional.empty(), LoginShell.find("ffmpeg", Map.of("SHELL", shell.toString(), "PATH", "/usr/bin:/bin"), 300L));
        assertTrue(System.nanoTime() - started < 5_000_000_000L, "login shell lookup hung");
    }

    @Test
    void versionAndEncoderListAreParsed() {
        assertEquals("9.0.2", HardwareEncoders.parseVersion("ffmpeg version 9.0.2 Copyright (c) 2000-2026 the FFmpeg developers\nbuilt with clang"));
        assertEquals("8.0-full_build-www.gyan.dev", HardwareEncoders.parseVersion("ffmpeg version 8.0-full_build-www.gyan.dev Copyright (c)"));
        assertEquals("6.1.1-3ubuntu5", HardwareEncoders.parseVersion("\r\nffmpeg version 6.1.1-3ubuntu5 Copyright\r\n"));
        String git = HardwareEncoders.parseVersion("ffmpeg version N-118193-g0123456789abcdef-20261001-with-a-very-long-suffix Copyright");
        assertTrue(git.startsWith("N-118193") && git.endsWith("…") && git.length() == HardwareEncoders.MAX_VERSION + 1, git);
        assertEquals("", HardwareEncoders.parseVersion("usage: something else"));
        Set<String> encoders = HardwareEncoders.parseEncoders("""
                Encoders:
                 V..... = Video
                 ------
                 V....D libx264              libx264 H.264 / AVC
                 V....D h264_videotoolbox    VideoToolbox H.264 Encoder (codec h264)
                 A....D aac                  AAC (Advanced Audio Coding)
                """);
        assertTrue(encoders.contains("h264_videotoolbox") && encoders.contains("libx264") && !encoders.contains("aac"), encoders.toString());
    }

    @Test
    void probeReportsVersionSourceAndWrongCustomPath(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(unix());
        Path fake = program(dir.resolve("bin"), "ffmpeg", SOFTWARE_ONLY);
        RecordingFormat format = new RecordingFormat(64, 64, 30);
        Map<String, String> env = Map.of("PATH", fake.getParent().toString());
        HardwareEncoders.Status found = HardwareEncoders.probe(new HardwareEncoders.Settings(true, "", List.of()), format, true,
                FfmpegCodec.Platform.LINUX, env, FfmpegLocator.ShellLookup.NONE);
        assertEquals(HardwareEncoders.State.NO_HARDWARE, found.state(), found.toString());
        assertEquals("7.1.2-selftest", found.version());
        assertEquals(FfmpegLocator.Source.PATH, found.source());
        assertEquals(fake, found.ffmpeg());
        assertFalse(found.customMissing());
        assertTrue(found.working().isEmpty());
        HardwareEncoders.Status wrong = HardwareEncoders.probe(new HardwareEncoders.Settings(true, dir.resolve("gone").toString(), List.of()), format,
                false, FfmpegCodec.Platform.LINUX, env, FfmpegLocator.ShellLookup.NONE);
        assertTrue(wrong.customMissing() && fake.equals(wrong.ffmpeg()), wrong.toString());
        HardwareEncoders.Status custom = HardwareEncoders.probe(new HardwareEncoders.Settings(true, fake.toString(), List.of()), format, false,
                FfmpegCodec.Platform.LINUX, Map.of("PATH", ""), FfmpegLocator.ShellLookup.NONE);
        assertEquals(FfmpegLocator.Source.CUSTOM, custom.source());
        HardwareEncoders.Status shell = HardwareEncoders.probe(new HardwareEncoders.Settings(true, "", List.of()), format, false,
                FfmpegCodec.Platform.OTHER, Map.of("PATH", "", "HOME", dir.resolve("nohome").toString()), (program, ignored) -> Optional.of(fake));
        if (shell.source() == FfmpegLocator.Source.SHELL) {
            assertEquals(fake, shell.ffmpeg());
            assertEquals(HardwareEncoders.State.NO_HARDWARE, shell.state());
        }
        HardwareEncoders.Status nothing = HardwareEncoders.probe(new HardwareEncoders.Settings(true, dir.resolve("gone").toString(), List.of()),
                format, false, FfmpegCodec.Platform.WINDOWS, Map.of(), FfmpegLocator.ShellLookup.NONE);
        assertEquals(HardwareEncoders.State.NO_FFMPEG, nothing.state());
        assertTrue(nothing.customMissing());
        assertNull(nothing.ffmpeg());
    }

    @Test
    void programThatIsNotFfmpegIsUnusable(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(unix());
        RecordingFormat format = new RecordingFormat(64, 64, 30);
        Path other = program(dir.resolve("other"), "ffmpeg", "echo 'usage: other tool'\nexit 2");
        HardwareEncoders.Status status = HardwareEncoders.probe(new HardwareEncoders.Settings(true, other.toString(), List.of()), format);
        assertEquals(HardwareEncoders.State.UNUSABLE, status.state(), status.toString());
        assertEquals(other, status.ffmpeg());
        assertFalse(status.detail().isEmpty());
        Path silent = program(dir.resolve("silent"), "ffmpeg", "exit 137");
        HardwareEncoders.Status killed = HardwareEncoders.probe(new HardwareEncoders.Settings(true, silent.toString(), List.of()), format);
        assertEquals(HardwareEncoders.State.UNUSABLE, killed.state(), killed.toString());
        assertTrue(killed.detail().contains("137"), killed.detail());
    }

    @Test
    void realFfmpegIsFoundWithoutTheShellPathAndThroughACustomPath(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(unix());
        FfmpegCodec.Platform platform = FfmpegCodec.Platform.current();
        Map<String, String> env = new HashMap<>();
        env.put("PATH", GUI_PATH);
        String home = System.getenv("HOME");
        if (home != null) {
            env.put("HOME", home);
        }
        RecordingFormat format = RecordingFormat.of(720, 30);
        HardwareEncoders.Status bare = HardwareEncoders.probe(new HardwareEncoders.Settings(true, "", List.of()), format, true, platform, env,
                FfmpegLocator.ShellLookup.NONE);
        Assumptions.assumeTrue(bare.ffmpeg() != null, "kein ffmpeg auf diesem Rechner");
        assertTrue(bare.state() == HardwareEncoders.State.READY || bare.state() == HardwareEncoders.State.NO_HARDWARE, bare.toString());
        assertFalse(bare.version().isEmpty(), bare.toString());
        assertTrue(bare.source() == FfmpegLocator.Source.KNOWN || bare.source() == FfmpegLocator.Source.PATH, bare.toString());
        Path link = Files.createSymbolicLink(Files.createDirectories(dir.resolve("my tools")).resolve("ffmpeg"), bare.ffmpeg().toRealPath());
        HardwareEncoders.Status custom = HardwareEncoders.probe(new HardwareEncoders.Settings(true, "\"" + link + "\"", List.of()), format, true,
                platform, env, FfmpegLocator.ShellLookup.NONE);
        assertEquals(link, custom.ffmpeg());
        assertEquals(FfmpegLocator.Source.CUSTOM, custom.source());
        assertEquals(bare.state(), custom.state(), custom.toString());
        assertEquals(bare.version(), custom.version());
        if (custom.state() == HardwareEncoders.State.READY) {
            assertTrue(custom.fps() >= format.fps() * HardwareEncoders.MIN_SPEED, custom.toString());
            assertFalse(custom.working().isEmpty());
            assertEquals(custom.codec(), custom.working().getFirst().codec());
        }
    }

    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path;
        }
    }
}
