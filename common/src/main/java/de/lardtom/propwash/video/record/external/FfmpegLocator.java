package de.lardtom.propwash.video.record.external;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class FfmpegLocator {
    public static final int PACKAGE_DEPTH = 4;

    private FfmpegLocator() {
    }

    public static Optional<Path> find(String configured, List<Path> extraDirs, FfmpegCodec.Platform platform, Map<String, String> env) {
        return search(configured, extraDirs, platform, env, ShellLookup.NONE).found().map(Found::path);
    }

    public static Search search(String configured, List<Path> extraDirs, FfmpegCodec.Platform platform, Map<String, String> env,
                                ShellLookup shell) {
        CustomPath custom = clean(configured).isEmpty() ? CustomPath.NONE : CustomPath.MISSING;
        for (Found candidate : ordered(configured, extraDirs, platform, env)) {
            Path path = candidate.path();
            if (candidate.source() == Source.GAME_FOLDER && Files.isRegularFile(path) && !Files.isExecutable(path)) {
                path.toFile().setExecutable(true);
            }
            if (usable(path)) {
                return new Search(Optional.of(candidate), candidate.source() == Source.CUSTOM ? CustomPath.USED : custom);
            }
            if (candidate.source() == Source.CUSTOM && Files.isRegularFile(path)) {
                custom = CustomPath.NOT_EXECUTABLE;
            }
        }
        Optional<Path> fromShell = shell.find(executable(platform), env).filter(path -> path.isAbsolute() && usable(path));
        return new Search(fromShell.map(path -> new Found(path, Source.SHELL)), custom);
    }

    public static List<Path> candidates(String configured, List<Path> extraDirs, FfmpegCodec.Platform platform, Map<String, String> env) {
        List<Path> out = new ArrayList<>();
        for (Found candidate : ordered(configured, extraDirs, platform, env)) {
            out.add(candidate.path());
        }
        return out;
    }

    public static List<Found> ordered(String configured, List<Path> extraDirs, FfmpegCodec.Platform platform, Map<String, String> env) {
        String name = executable(platform);
        Map<Path, Source> out = new LinkedHashMap<>();
        Path custom = path(home(clean(configured), env));
        if (custom != null) {
            if (Files.isDirectory(custom)) {
                out.putIfAbsent(custom.resolve(name), Source.CUSTOM);
                out.putIfAbsent(custom.resolve("bin").resolve(name), Source.CUSTOM);
            } else {
                out.putIfAbsent(custom, Source.CUSTOM);
                Path file = custom.getFileName();
                if (platform == FfmpegCodec.Platform.WINDOWS && file != null && !file.toString().toLowerCase(Locale.ROOT).endsWith(".exe")) {
                    out.putIfAbsent(custom.resolveSibling(file + ".exe"), Source.CUSTOM);
                }
                out.putIfAbsent(custom.resolve("bin").resolve(name), Source.CUSTOM);
            }
        }
        for (Path dir : extraDirs) {
            out.putIfAbsent(dir.resolve(name), Source.GAME_FOLDER);
            out.putIfAbsent(dir.resolve("bin").resolve(name), Source.GAME_FOLDER);
        }
        String pathVariable = value(env, "PATH");
        for (String entry : pathVariable.split(File.pathSeparator)) {
            Path dir = path(entry.strip());
            if (dir != null && dir.isAbsolute()) {
                out.putIfAbsent(dir.resolve(name), Source.PATH);
            }
        }
        for (Path dir : known(platform, env)) {
            out.putIfAbsent(dir.resolve(name), Source.KNOWN);
        }
        List<Found> list = new ArrayList<>();
        out.forEach((path, source) -> list.add(new Found(path, source)));
        return list;
    }

    public static String executable(FfmpegCodec.Platform platform) {
        return platform == FfmpegCodec.Platform.WINDOWS ? "ffmpeg.exe" : "ffmpeg";
    }

    public static String clean(String configured) {
        if (configured == null) {
            return "";
        }
        String text = configured.strip();
        while (text.length() >= 2 && (text.charAt(0) == '"' || text.charAt(0) == '\'') && text.charAt(text.length() - 1) == text.charAt(0)) {
            text = text.substring(1, text.length() - 1).strip();
        }
        return text;
    }

    static List<Path> known(FfmpegCodec.Platform platform, Map<String, String> env) {
        List<Path> dirs = new ArrayList<>();
        Path home = path(value(env, "HOME"));
        switch (platform) {
            case MAC -> {
                add(dirs, "/opt/homebrew/bin", "/usr/local/bin", "/opt/local/bin");
                dirs.addAll(kegs(Path.of("/opt/homebrew/opt")));
                dirs.addAll(kegs(Path.of("/usr/local/opt")));
                userDirs(dirs, home);
                nix(dirs, home);
            }
            case LINUX, OTHER -> {
                add(dirs, "/usr/bin", "/usr/local/bin", "/bin", "/snap/bin", "/var/lib/flatpak/exports/bin");
                dirs.add(Path.of("/home").resolve("linuxbrew").resolve(".linuxbrew").resolve("bin"));
                if (home != null && home.isAbsolute()) {
                    dirs.add(home.resolve(".linuxbrew").resolve("bin"));
                    dirs.add(home.resolve(".local").resolve("share").resolve("flatpak").resolve("exports").resolve("bin"));
                }
                userDirs(dirs, home);
                nix(dirs, home);
            }
            case WINDOWS -> {
                Path local = path(value(env, "LOCALAPPDATA"));
                Path profile = path(value(env, "USERPROFILE"));
                if (local != null && local.isAbsolute()) {
                    Path winget = local.resolve("Microsoft").resolve("WinGet");
                    dirs.add(winget.resolve("Links"));
                    dirs.addAll(packages(winget.resolve("Packages"), executable(platform)));
                }
                for (String variable : List.of("ProgramFiles", "ProgramW6432", "ProgramFiles(x86)")) {
                    Path programs = path(value(env, variable));
                    if (programs != null && programs.isAbsolute()) {
                        dirs.add(programs.resolve("WinGet").resolve("Links"));
                        dirs.add(programs.resolve("ffmpeg").resolve("bin"));
                        dirs.add(programs.resolve("ffmpeg"));
                    }
                }
                Path scoop = path(value(env, "SCOOP"));
                if (scoop == null && profile != null) {
                    scoop = profile.resolve("scoop");
                }
                if (scoop != null && scoop.isAbsolute()) {
                    dirs.add(scoop.resolve("apps").resolve("ffmpeg").resolve("current").resolve("bin"));
                    dirs.add(scoop.resolve("shims"));
                }
                Path chocolatey = path(value(env, "ChocolateyInstall"));
                if (chocolatey != null && chocolatey.isAbsolute()) {
                    dirs.add(chocolatey.resolve("bin"));
                }
            }
        }
        return dirs;
    }

    static List<Path> kegs(Path opt) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(opt)) {
            return out;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(opt, "ffmpeg*")) {
            for (Path entry : entries) {
                out.add(entry.resolve("bin"));
            }
        } catch (IOException | RuntimeException ignored) {
        }
        out.sort(null);
        return out;
    }

    static List<Path> packages(Path packages, String name) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(packages)) {
            return out;
        }
        List<Path> roots = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(packages)) {
            for (Path entry : entries) {
                if (entry.getFileName().toString().toLowerCase(Locale.ROOT).contains("ffmpeg")) {
                    roots.add(entry);
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        roots.sort(null);
        for (Path root : roots) {
            try (Stream<Path> files = Files.walk(root, PACKAGE_DEPTH)) {
                files.filter(file -> file.getFileName().toString().equalsIgnoreCase(name)).map(Path::getParent).sorted().forEach(out::add);
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return out;
    }

    private static void userDirs(List<Path> dirs, Path home) {
        if (home != null && home.isAbsolute()) {
            dirs.add(home.resolve("bin"));
            dirs.add(home.resolve(".local").resolve("bin"));
        }
    }

    private static void nix(List<Path> dirs, Path home) {
        if (home != null && home.isAbsolute()) {
            dirs.add(home.resolve(".nix-profile").resolve("bin"));
        }
        add(dirs, "/run/current-system/sw/bin", "/nix/var/nix/profiles/default/bin");
    }

    private static void add(List<Path> dirs, String... names) {
        for (String name : names) {
            Path dir = path(name);
            if (dir != null) {
                dirs.add(dir);
            }
        }
    }

    private static boolean usable(Path candidate) {
        return Files.isRegularFile(candidate) && Files.isExecutable(candidate);
    }

    private static String home(String text, Map<String, String> env) {
        String home = value(env, "HOME");
        if (home.isEmpty()) {
            home = value(env, "USERPROFILE");
        }
        if (home.isEmpty()) {
            return text;
        }
        if (text.equals("~")) {
            return home;
        }
        if (text.startsWith("~/") || text.startsWith("~\\")) {
            return home + File.separator + text.substring(2);
        }
        return text;
    }

    private static String value(Map<String, String> env, String name) {
        String exact = env.get(name);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, String> entry : env.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null) {
                return entry.getValue();
            }
        }
        return "";
    }

    private static Path path(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Path.of(text);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    public enum Source {
        CUSTOM,
        GAME_FOLDER,
        PATH,
        KNOWN,
        SHELL;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public record Found(Path path, Source source) {
    }

    public enum CustomPath {
        NONE,
        USED,
        MISSING,
        NOT_EXECUTABLE;

        public boolean rejected() {
            return this == MISSING || this == NOT_EXECUTABLE;
        }
    }

    public record Search(Optional<Found> found, CustomPath custom) {
        public boolean customMissing() {
            return this.custom.rejected();
        }
    }

    @FunctionalInterface
    public interface ShellLookup {
        ShellLookup NONE = (program, env) -> Optional.empty();

        Optional<Path> find(String program, Map<String, String> env);
    }
}
