package de.lardtom.propwash.ui.client.theme;

import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ToastTextTest {
    private static final String TITLE = "propwash.input.toast.connected.radio";
    private static final String MESSAGE = "propwash.input.toast.not_set_up";
    private static final Map<String, String> EN = Map.of(TITLE, "Radio connected", MESSAGE, "%s (not set up yet)");
    private static final Map<String, String> DE = Map.of(TITLE, "Funke verbunden", MESSAGE, "%s (noch nicht eingerichtet)");
    private static final int ROOM = ThemedToast.textWidth(854);

    private final float[] advance = {5.0F};
    private final Font font = font(this.advance);
    private Language previous;

    @BeforeEach
    void remember() {
        this.previous = Language.getInstance();
    }

    @AfterEach
    void restore() {
        Language.inject(this.previous);
    }

    private static ToastText toast() {
        return new ToastText(Component.translatable(TITLE), Component.translatable(MESSAGE, Component.literal("Radiomaster Pocket")));
    }

    @Test
    void toastShownBeforeTheLanguageLoadsShowsTheTextOnceItHasLoaded() {
        Language.inject(language(Map.of()));
        ToastText toast = toast();
        ToastText.Layout early = toast.layout(this.font, ROOM, true);
        assertEquals(List.of(TITLE), text(early.titleLines()));
        assertEquals(List.of(MESSAGE), text(early.messageLines()));
        Language.inject(language(EN));
        ToastText.Layout loaded = toast.layout(this.font, ROOM, true);
        assertEquals(List.of("Radio connected"), text(loaded.titleLines()));
        assertEquals(List.of("Radiomaster Pocket (not set up yet)"), text(loaded.messageLines()));
    }

    @Test
    void switchingTheLanguageSplitsAgain() {
        Language.inject(language(EN));
        ToastText toast = toast();
        assertEquals(List.of("Radio connected"), text(toast.layout(this.font, ROOM, true).titleLines()));
        Language.inject(language(DE));
        assertEquals(List.of("Funke verbunden"), text(toast.layout(this.font, ROOM, true).titleLines()));
    }

    @Test
    void sameLanguageAndWidthKeepTheSplit() {
        Language.inject(language(EN));
        ToastText toast = toast();
        ToastText.Layout first = toast.layout(this.font, ROOM, true);
        assertSame(first, toast.layout(this.font, ROOM, true));
        ToastText.Layout narrow = toast.layout(this.font, 60, true);
        assertNotSame(first, narrow);
        assertEquals(List.of("Radio", "connected"), text(narrow.titleLines()));
    }

    @Test
    void splitWhileResourcesLoadIsNotKept() {
        Language.inject(language(EN));
        ToastText toast = toast();
        ToastText.Layout loading = toast.layout(this.font, ROOM, false);
        this.advance[0] = 10.0F;
        ToastText.Layout loaded = toast.layout(this.font, ROOM, true);
        assertNotSame(loading, loaded);
        assertEquals(widest(loaded), loaded.widest());
        assertEquals(Math.max(160, widest(loaded) + 20), ThemedToast.boxWidth(loaded));
        this.advance[0] = 5.0F;
        assertSame(loaded, toast.layout(this.font, ROOM, true));
    }

    @Test
    void newTextDropsTheOldLines() {
        Language.inject(language(EN));
        ToastText toast = toast();
        toast.layout(this.font, ROOM, true);
        toast.set(Component.literal("Gamepad connected"), null);
        ToastText.Layout after = toast.layout(this.font, ROOM, true);
        assertEquals(List.of("Gamepad connected"), text(after.titleLines()));
        assertEquals(List.of(), after.messageLines());
    }

    private int widest(ToastText.Layout layout) {
        int widest = 0;
        for (FormattedCharSequence line : layout.titleLines()) {
            widest = Math.max(widest, this.font.width(line));
        }
        for (FormattedCharSequence line : layout.messageLines()) {
            widest = Math.max(widest, this.font.width(line));
        }
        return widest;
    }

    private static List<String> text(List<FormattedCharSequence> lines) {
        List<String> out = new ArrayList<>();
        for (FormattedCharSequence line : lines) {
            StringBuilder builder = new StringBuilder();
            line.accept((index, style, codepoint) -> {
                builder.appendCodePoint(codepoint);
                return true;
            });
            out.add(builder.toString());
        }
        return out;
    }

    private Language language(Map<String, String> texts) {
        Language base = this.previous;
        return new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                return texts.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(String key) {
                return texts.containsKey(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return base.getVisualOrder(text);
            }
        };
    }

    private static Font font(float[] advance) {
        GlyphInfo info = () -> advance[0];
        BakedGlyph glyph = new BakedGlyph() {
            @Override
            public GlyphInfo info() {
                return info;
            }

            @Override
            public TextRenderable.@Nullable Styled createGlyph(float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset) {
                return null;
            }
        };
        GlyphSource source = new GlyphSource() {
            @Override
            public BakedGlyph getGlyph(int codepoint) {
                return glyph;
            }

            @Override
            public BakedGlyph getRandomGlyph(RandomSource random, int width) {
                return glyph;
            }
        };
        EffectGlyph effect = (x0, y0, x1, y1, depth, color, shadowColor, shadowOffset) -> null;
        return new Font(new Font.Provider() {
            @Override
            public GlyphSource glyphs(FontDescription description) {
                return source;
            }

            @Override
            public EffectGlyph effect() {
                return effect;
            }
        });
    }
}
