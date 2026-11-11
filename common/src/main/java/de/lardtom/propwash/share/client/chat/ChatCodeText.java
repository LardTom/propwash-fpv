package de.lardtom.propwash.share.client.chat;

import de.lardtom.propwash.share.ShareFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.StringDecomposer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ChatCodeText {
    private ChatCodeText() {
    }

    public record Run(Style style, String text) {
    }

    public record Decoration(int color, @Nullable ClickEvent click, HoverEvent hover) {
        public Decoration {
            Objects.requireNonNull(hover, "hover");
        }

        public Style apply(Style base) {
            ClickEvent event = this.click != null ? this.click : base.getClickEvent();
            return base.withColor(TextColor.fromRgb(this.color & 0xFFFFFF)).withUnderlined(true).withClickEvent(event).withHoverEvent(this.hover);
        }
    }

    @FunctionalInterface
    public interface Styler {
        @Nullable Decoration decorate(ChatCodeFinder.Found found, boolean copy);
    }

    public record Result(FormattedText text, List<ChatCodeFinder.Found> codes) {
    }

    public static FormattedText decorate(FormattedText message, boolean colors, Styler styler) {
        return apply(message, colors, styler).text();
    }

    public static Result apply(FormattedText message, boolean colors, Styler styler) {
        if (message == null || !ChatCodeFinder.mayContain(message.getString())) {
            return new Result(message, List.of());
        }
        List<Run> runs = runs(message, colors);
        StringBuilder plain = new StringBuilder();
        for (Run run : runs) {
            plain.append(run.text());
        }
        String text = plain.toString();
        List<ChatCodeFinder.Found> found = ChatCodeFinder.find(text);
        if (found.isEmpty()) {
            return new Result(message, List.of());
        }
        List<ChatCodeFinder.Found> used = new ArrayList<>();
        List<Decoration> decorations = new ArrayList<>();
        for (ChatCodeFinder.Found code : found) {
            Optional<Boolean> copy = senderClick(runs, code);
            if (copy.isEmpty()) {
                continue;
            }
            Decoration decoration = styler.decorate(code, copy.get());
            if (decoration != null) {
                used.add(code);
                decorations.add(decoration);
            }
        }
        if (used.isEmpty()) {
            return new Result(message, List.of());
        }
        return new Result(build(runs, used, decorations), List.copyOf(used));
    }

    public static List<Run> runs(FormattedText message, boolean colors) {
        List<Run> runs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Style[] style = {null};
        message.visit((pieceStyle, contents) -> {
            String source = colors ? contents : ChatFormatting.stripFormatting(contents);
            StringDecomposer.iterateFormatted(source == null ? "" : source, pieceStyle, (position, charStyle, codepoint) -> {
                if (style[0] != null && !style[0].equals(charStyle)) {
                    runs.add(new Run(style[0], current.toString()));
                    current.setLength(0);
                }
                style[0] = charStyle;
                current.appendCodePoint(codepoint);
                return true;
            });
            return Optional.empty();
        }, Style.EMPTY);
        if (style[0] != null && !current.isEmpty()) {
            runs.add(new Run(style[0], current.toString()));
        }
        return runs;
    }

    static Optional<Boolean> senderClick(List<Run> runs, ChatCodeFinder.Found code) {
        ClickEvent seen = null;
        boolean plain = false;
        int at = 0;
        for (Run run : runs) {
            int end = at + run.text().length();
            if (end > code.start() && at < code.end()) {
                ClickEvent event = run.style().getClickEvent();
                if (event == null) {
                    plain = true;
                } else if (seen == null) {
                    seen = event;
                } else if (!seen.equals(event)) {
                    return Optional.empty();
                }
            }
            at = end;
        }
        if (seen == null) {
            return Optional.of(false);
        }
        if (!plain && seen instanceof ClickEvent.CopyToClipboard(String value) && ShareFormat.compact(value).equals(ShareFormat.compact(code.code()))) {
            return Optional.of(true);
        }
        return Optional.empty();
    }

    private static FormattedText build(List<Run> runs, List<ChatCodeFinder.Found> codes, List<Decoration> decorations) {
        MutableComponent out = Component.empty();
        int at = 0;
        for (Run run : runs) {
            int start = at;
            int end = at + run.text().length();
            int cursor = start;
            for (int i = 0; i < codes.size(); i++) {
                ChatCodeFinder.Found code = codes.get(i);
                int from = Math.max(code.start(), start);
                int to = Math.min(code.end(), end);
                if (from >= to) {
                    continue;
                }
                if (from > cursor) {
                    out.append(Component.literal(run.text().substring(cursor - start, from - start)).setStyle(run.style()));
                }
                out.append(Component.literal(run.text().substring(from - start, to - start)).setStyle(decorations.get(i).apply(run.style())));
                cursor = to;
            }
            if (cursor < end) {
                out.append(Component.literal(run.text().substring(cursor - start)).setStyle(run.style()));
            }
            at = end;
        }
        return out;
    }
}
