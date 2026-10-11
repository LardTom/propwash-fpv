package de.lardtom.propwash.input;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SwitchCommands {
    public static final int MAX_LENGTH = 256;
    public static final String PREFIX = "/";

    public enum Problem {
        NONE,
        TOO_LONG,
        ILLEGAL_CHARACTER
    }

    private SwitchCommands() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 1);
        boolean space = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isWhitespace(c)) {
                space = out.length() > 0;
                continue;
            }
            if (space) {
                out.append(' ');
                space = false;
            }
            out.append(c);
        }
        if (out.isEmpty()) {
            return "";
        }
        String text = out.toString();
        if (!text.startsWith(PREFIX)) {
            text = PREFIX + text;
        }
        return text.length() <= PREFIX.length() ? "" : text;
    }

    public static Problem problem(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return Problem.NONE;
        }
        if (normalized.length() > MAX_LENGTH) {
            return Problem.TOO_LONG;
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (!allowedCharacter(normalized.charAt(i))) {
                return Problem.ILLEGAL_CHARACTER;
            }
        }
        return Problem.NONE;
    }

    public static Problem check(String raw) {
        return problem(normalize(raw));
    }

    public static boolean allowedCharacter(int c) {
        return c != 167 && c >= 32 && c != 127;
    }

    public static String body(String normalized) {
        return normalized == null || normalized.length() <= PREFIX.length() ? "" : normalized.substring(PREFIX.length());
    }

    public static String cleanStored(String raw) {
        String normalized = normalize(raw);
        return problem(normalized) == Problem.NONE ? normalized : "";
    }

    public static Map<String, SwitchCommand> copy(Map<String, SwitchCommand> source) {
        Map<String, SwitchCommand> copy = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<String, SwitchCommand> e : source.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    copy.put(e.getKey(), e.getValue().copy());
                }
            }
        }
        return copy;
    }

    public static Map<String, SwitchCommand> sanitize(Map<String, SwitchCommand> source) {
        Map<String, SwitchCommand> clean = new LinkedHashMap<>();
        if (source == null) {
            return clean;
        }
        for (SwitchFunction function : SwitchFunction.commands()) {
            SwitchCommand command = source.get(DeviceProfile.key(function));
            if (command == null) {
                continue;
            }
            command.sanitize();
            if (!command.empty()) {
                clean.put(DeviceProfile.key(function), command);
            }
        }
        return clean;
    }

    public static SwitchCommand get(Map<String, SwitchCommand> commands, SwitchFunction function) {
        SwitchCommand command = commands == null || !function.command() ? null : commands.get(DeviceProfile.key(function));
        return command == null ? new SwitchCommand() : command;
    }

    public static void put(Map<String, SwitchCommand> commands, SwitchFunction function, SwitchCommand command) {
        if (!function.command()) {
            return;
        }
        if (command == null || command.empty()) {
            commands.remove(DeviceProfile.key(function));
        } else {
            commands.put(DeviceProfile.key(function), command.copy());
        }
    }
}
