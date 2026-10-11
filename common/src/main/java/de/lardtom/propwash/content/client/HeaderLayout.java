package de.lardtom.propwash.content.client;

import java.util.List;

public final class HeaderLayout {
    private HeaderLayout() {
    }

    public record Result(int shown, int[] variants, boolean more, int width) {
        public int variant(int entry) {
            return this.variants[entry];
        }
    }

    public static Result fit(int space, int gap, int moreWidth, int maxShown, List<int[]> widths) {
        int count = widths.size();
        for (int shown = Math.min(count, Math.max(0, maxShown)); shown > 0; shown--) {
            boolean more = shown < count;
            int[] variants = new int[shown];
            int total = total(widths, variants, gap) + (more ? moreWidth + gap : 0);
            while (total > space) {
                int pick = -1;
                int widest = -1;
                for (int i = 0; i < shown; i++) {
                    int[] options = widths.get(i);
                    if (variants[i] + 1 < options.length && options[variants[i] + 1] < options[variants[i]] && options[variants[i]] > widest) {
                        widest = options[variants[i]];
                        pick = i;
                    }
                }
                if (pick < 0) {
                    break;
                }
                variants[pick]++;
                total = total(widths, variants, gap) + (more ? moreWidth + gap : 0);
            }
            if (total <= space) {
                return new Result(shown, variants, more, total);
            }
        }
        boolean more = count > 0;
        int width = more ? moreWidth + gap : 0;
        return new Result(0, new int[0], more, width);
    }

    private static int total(List<int[]> widths, int[] variants, int gap) {
        int total = 0;
        for (int i = 0; i < variants.length; i++) {
            total += widths.get(i)[variants[i]] + gap;
        }
        return total;
    }
}
