package de.lardtom.propwash.video.record.external;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class LoginShell {
    public static final long TIMEOUT_MILLIS = 4_000L;
    private static final Set<String> SHELLS = Set.of("sh", "bash", "zsh", "dash", "ksh", "fish");
    private static final Pattern PROGRAM = Pattern.compile("[A-Za-z0-9._-]+");
    private static final long MAX_OUTPUT = 65_536L;

    private LoginShell() {
    }

    public static Optional<Path> find(String program, Map<String, String> env) {
        return find(program, env, TIMEOUT_MILLIS);
    }

    public static Optional<Path> find(String program, Map<String, String> env, long timeoutMillis) {
        if (FfmpegCodec.Platform.current() == FfmpegCodec.Platform.WINDOWS || !PROGRAM.matcher(program).matches()) {
            return Optional.empty();
        }
        Path shell = shell(env);
        if (shell == null) {
            return Optional.empty();
        }
        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("propwash-shell", ".txt");
            ProcessBuilder builder = new ProcessBuilder(shell.toString(), "-l", "-c", "command -v " + program);
            builder.environment().clear();
            builder.environment().putAll(env);
            builder.redirectOutput(output.toFile());
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();
            process.getOutputStream().close();
            if (!process.waitFor(Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS)) {
                return Optional.empty();
            }
            if (Files.size(output) > MAX_OUTPUT) {
                return Optional.empty();
            }
            return parse(Files.readAllLines(output, StandardCharsets.UTF_8), program);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException ignored) {
                }
            }
        }
    }

    static Optional<Path> parse(List<String> lines, String program) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i).strip();
            if (line.isEmpty()) {
                continue;
            }
            try {
                Path path = Path.of(line);
                if (path.isAbsolute() && path.getFileName() != null && path.getFileName().toString().equals(program)) {
                    return Optional.of(path);
                }
            } catch (InvalidPathException ignored) {
            }
        }
        return Optional.empty();
    }

    private static Path shell(Map<String, String> env) {
        String text = env.get("SHELL");
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            Path shell = Path.of(text.strip());
            Path name = shell.getFileName();
            if (!shell.isAbsolute() || name == null || !SHELLS.contains(name.toString()) || !Files.isExecutable(shell)) {
                return null;
            }
            return shell;
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
