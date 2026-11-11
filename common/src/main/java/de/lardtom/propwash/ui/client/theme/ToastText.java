package de.lardtom.propwash.ui.client.theme;

import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.List;

final class ToastText {
    private Component title;
    private @Nullable Component message;
    private @Nullable Layout layout;

    ToastText(Component title, @Nullable Component message) {
        this.title = title;
        this.message = message;
    }

    Component title() {
        return this.title;
    }

    @Nullable Component message() {
        return this.message;
    }

    void set(Component title, @Nullable Component message) {
        this.title = title;
        this.message = message;
        this.layout = null;
    }

    Layout layout(Font font, int room, boolean settled) {
        Language language = Language.getInstance();
        Layout cached = this.layout;
        if (cached != null && cached.language() == language && cached.room() == room) {
            return cached;
        }
        List<FormattedCharSequence> titleLines = font.split(this.title, room);
        List<FormattedCharSequence> messageLines = this.message == null ? List.of() : ThemeText.balanced(font, this.message, room);
        int widest = 0;
        for (FormattedCharSequence line : titleLines) {
            widest = Math.max(widest, font.width(line));
        }
        for (FormattedCharSequence line : messageLines) {
            widest = Math.max(widest, font.width(line));
        }
        Layout fresh = new Layout(language, room, titleLines, messageLines, widest);
        this.layout = settled ? fresh : null;
        return fresh;
    }

    record Layout(Language language, int room, List<FormattedCharSequence> titleLines, List<FormattedCharSequence> messageLines, int widest) {
        int lines() {
            return Math.max(1, this.titleLines.size() + this.messageLines.size());
        }
    }
}
