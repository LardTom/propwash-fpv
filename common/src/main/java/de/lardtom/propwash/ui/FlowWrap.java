package de.lardtom.propwash.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

public final class FlowWrap {
    private FlowWrap() {
    }

    public static List<String> lines(String text, ToIntFunction<String> width, int firstWidth, int firstLines, int restWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!line.isEmpty()) {
                if (width.applyAsInt(line + " " + word) <= limit(out.size(), firstWidth, firstLines, restWidth)) {
                    line.append(' ').append(word);
                    continue;
                }
                out.add(line.toString());
                line.setLength(0);
            }
            line.append(word);
            while (line.length() > 1 && width.applyAsInt(line.toString()) > limit(out.size(), firstWidth, firstLines, restWidth)) {
                int limit = limit(out.size(), firstWidth, firstLines, restWidth);
                int cut = line.length() - 1;
                while (cut > 1 && width.applyAsInt(line.substring(0, cut)) > limit) {
                    cut--;
                }
                out.add(line.substring(0, cut));
                line.delete(0, cut);
            }
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }

    public static int linesBeside(int blockHeight, int gap, int lineHeight) {
        return blockHeight <= 0 ? 0 : (blockHeight + gap + lineHeight - 1) / lineHeight;
    }

    private static int limit(int index, int firstWidth, int firstLines, int restWidth) {
        return Math.max(1, index < firstLines ? firstWidth : restWidth);
    }
}
