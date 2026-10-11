package de.lardtom.propwash.race;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

public final class MessageWrap {
    private MessageWrap() {
    }

    public static List<String> wrap(String text, double maxWidth, ToDoubleFunction<String> width) {
        List<String> lines = new ArrayList<>();
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            return lines;
        }
        if (!(maxWidth > 0.0) || width.applyAsDouble(trimmed) <= maxWidth) {
            lines.add(trimmed);
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : trimmed.split(" +")) {
            if (line.isEmpty()) {
                line.append(word);
                continue;
            }
            String candidate = line + " " + word;
            if (width.applyAsDouble(candidate) <= maxWidth) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
