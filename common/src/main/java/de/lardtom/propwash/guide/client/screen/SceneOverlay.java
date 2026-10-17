package de.lardtom.propwash.guide.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.guide.client.render.SceneView;
import de.lardtom.propwash.guide.client.render.ScreenBox;
import de.lardtom.propwash.guide.layout.CalloutLayout;
import de.lardtom.propwash.guide.layout.LabelRect;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneProjection;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.input.client.StickModes;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SceneOverlay {
    private static final int PADDING = 5;
    private static final int LINE = 10;
    private static final int MARGIN = 4;
    private static final int CALLOUT_PAD = 16;
    private static final int TOP_RESERVE = 22;
    private static final int HINT_HEIGHT = 18;
    private static final float SLIDE = 6.0F;
    private static final float OWNER_MARGIN = 0.45F;
    private static final int MIN_WRAP = 80;
    private static final float NARROW_WRAP = 0.7F;

    private SceneOverlay() {
    }

    public record Placed(String textKey, int x0, int y0, int x1, int y1, float anchorX, float anchorY, @Nullable String owner, float ownerCover,
                         String id, LabelRect target, boolean gliding, LabelRect from, float fade, LabelRect layoutBox) {
    }

    public record HintBox(List<Token> tokens, Component text, int x0, int y0, int width, int scale, float alpha) {
        public LabelRect rect() {
            return new LabelRect(this.x0, this.y0, this.x0 + this.width * this.scale, this.y0 + HINT_HEIGHT * this.scale);
        }
    }

    private record Prepared(SceneState.CalloutView callout, @Nullable ScreenBox own, List<List<FormattedCharSequence>> lines,
                            List<CalloutLayout.Size> sizes) {
    }

    public static LabelRect labelArea(ScreenRectangle area) {
        return new LabelRect(area.left() + CALLOUT_PAD, area.top() + TOP_RESERVE, area.right() - CALLOUT_PAD, area.bottom() - CALLOUT_PAD);
    }

    public static List<Placed> callouts(GuiGraphicsExtractor g, Font font, SceneState state, SceneProjection projection, ScreenRectangle area,
                                        List<ScreenBox> actors, List<HintBox> hints, CalloutLayout layout, float dt, boolean reset, int scale) {
        int k = Math.max(1, scale);
        int areaWidth = area.width() / k;
        int maxWidth = Math.min(Math.max(80, Math.min(170, areaWidth / 3)), Math.max(MIN_WRAP / 2, areaWidth - 2 * CALLOUT_PAD - 2 * PADDING - 2));
        float[] point = new float[3];
        Map<String, Prepared> prepared = new HashMap<>();
        List<CalloutLayout.Label> labels = new ArrayList<>();
        for (SceneState.CalloutView callout : state.callouts()) {
            if (callout.alpha() <= 0.02F) {
                continue;
            }
            projection.project(callout.anchor().x(), callout.anchor().y(), callout.anchor().z(), point);
            ScreenBox own = owner(callout.anchor(), actors);
            Component text = StickModes.translatable(callout.textKey());
            List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
            List<CalloutLayout.Size> sizes = new ArrayList<>();
            for (int wrap : new int[]{maxWidth, Math.max(MIN_WRAP, Math.round(maxWidth * NARROW_WRAP))}) {
                List<FormattedCharSequence> lines = ThemeText.balanced(font, text, wrap);
                int textWidth = 0;
                for (FormattedCharSequence line : lines) {
                    textWidth = Math.max(textWidth, font.width(line));
                }
                CalloutLayout.Size size = new CalloutLayout.Size((textWidth + 2 * PADDING + 2) * k, (lines.size() * LINE + 2 * PADDING - 2) * k);
                if (!sizes.contains(size)) {
                    wrapped.add(lines);
                    sizes.add(size);
                }
                if (wrap <= MIN_WRAP) {
                    break;
                }
            }
            String id = callout.layoutId();
            prepared.put(id, new Prepared(callout, own, wrapped, sizes));
            labels.add(new CalloutLayout.Label(id, point[0], point[1], callout.side(), sizes, own != null ? own.actor() : null,
                    own != null ? new LabelRect(own.x0(), own.y0(), own.x1(), own.y1()) : null, leaving(callout)));
        }
        List<CalloutLayout.Obstacle> obstacles = new ArrayList<>(actors.size() + hints.size());
        for (ScreenBox box : actors) {
            obstacles.add(new CalloutLayout.Obstacle(box.actor(), new LabelRect(box.x0(), box.y0(), box.x1(), box.y1()),
                    box.actor().startsWith(SceneView.BLOCK_PREFIX) ? CalloutLayout.Kind.BLOCK : CalloutLayout.Kind.ACTOR));
        }
        for (HintBox hint : hints) {
            obstacles.add(new CalloutLayout.Obstacle("hint", hint.rect(), CalloutLayout.Kind.HINT));
        }
        LabelRect view = new LabelRect(area.left(), area.top(), area.right(), area.bottom());
        List<CalloutLayout.Placement> placements = layout.update(view, labelArea(area), dt, reset, k, labels, obstacles);
        List<Placed> placed = new ArrayList<>(placements.size());
        for (CalloutLayout.Placement placement : placements) {
            Prepared entry = prepared.get(placement.id());
            SceneState.CalloutView callout = entry.callout();
            float dotAlpha = callout.alpha();
            float alpha = dotAlpha * Math.clamp(placement.alpha(), 0.0F, 1.0F);
            float slide = (1.0F - dotAlpha) * SLIDE;
            float ox = placement.side() == Cue.Side.LEFT ? -slide : placement.side() == Cue.Side.RIGHT ? slide : 0.0F;
            float oy = placement.side() == Cue.Side.ABOVE ? -slide : placement.side() == Cue.Side.BELOW ? slide : 0.0F;
            LabelRect box = placement.box();
            int x0 = Math.round(box.x0() + ox);
            int y0 = Math.round(box.y0() + oy);
            int x1 = x0 + Math.round(box.width());
            int y1 = y0 + Math.round(box.height());
            float ax = placement.anchorX();
            float ay = placement.anchorY();
            float px = Math.clamp(ax, x0, x1);
            float py = Math.clamp(ay, y0, y1);
            boolean inside = ax >= x0 && ax <= x1 && ay >= y0 && ay <= y1;
            if (!inside) {
                GuideDraw.line(g, ax, ay, px, py, GuideDraw.fade(Palette.withAlpha(Palette.SHADOW, 0xC0), alpha));
                GuideDraw.line(g, ax, ay - 1.0F, px, py - 1.0F, GuideDraw.fade(Palette.ACCENT, alpha));
            }
            GuideDraw.dot(g, ax, ay, GuideDraw.fade(Palette.ACCENT, dotAlpha), GuideDraw.fade(Palette.SHADOW, dotAlpha));
            if (alpha <= 0.02F) {
                placed.add(new Placed(callout.textKey(), x0, y0, x1, y1, ax, ay, entry.own() != null ? entry.own().actor() : null, 0.0F,
                        placement.id(), placement.target(), placement.gliding(), placement.from(), placement.alpha(), box));
                continue;
            }
            g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, GuideDraw.fade(Palette.withAlpha(Palette.SHADOW, 0x90), alpha));
            ThemeDraw.framedBox(g, x0, y0, x1, y1, GuideDraw.fade(Palette.TOOLTIP, alpha), GuideDraw.fade(Palette.BORDER_STRONG, alpha));
            g.fill(x0, y0 + 1, x0 + 2 * k, y1 - 1, GuideDraw.fade(Palette.ACCENT, alpha));
            g.pose().pushMatrix();
            g.pose().translate(x0 + (PADDING + 2) * k, y0 + PADDING * k);
            g.pose().scale(k, k);
            int ty = 0;
            for (FormattedCharSequence line : entry.lines().get(placement.size())) {
                g.text(font, line, 0, ty, GuideDraw.fade(Palette.TEXT, alpha), false);
                ty += LINE;
            }
            g.pose().popMatrix();
            ScreenBox own = entry.own();
            float cover = own != null ? own.overlap(x0, y0, x1, y1) / Math.max(1.0F, (float) (x1 - x0) * (y1 - y0)) : 0.0F;
            placed.add(new Placed(callout.textKey(), x0, y0, x1, y1, ax, ay, own != null ? own.actor() : null, cover, placement.id(),
                    placement.target(), placement.gliding(), placement.from(), placement.alpha(), box));
        }
        return placed;
    }

    public static boolean leaving(SceneState.CalloutView callout) {
        return callout.age() > SceneEvaluator.FADE_TICKS && callout.alpha() < 1.0F;
    }

    static @Nullable ScreenBox owner(ScenePos anchor, List<ScreenBox> actors) {
        ScreenBox best = null;
        for (ScreenBox box : actors) {
            if (box.contains(anchor, OWNER_MARGIN) && (best == null || box.volume() < best.volume())) {
                best = box;
            }
        }
        return best;
    }

    public static int keyHints(GuiGraphicsExtractor g, Font font, SceneState state, SceneProjection projection, ScreenRectangle area) {
        return keyHints(g, font, state, projection, area, 1);
    }

    public static int keyHints(GuiGraphicsExtractor g, Font font, SceneState state, SceneProjection projection, ScreenRectangle area, int scale) {
        return drawKeyHints(g, font, layoutKeyHints(font, state, projection, area, scale));
    }

    public static List<HintBox> layoutKeyHints(Font font, SceneState state, SceneProjection projection, ScreenRectangle area, int scale) {
        int k = Math.max(1, scale);
        int hintHeight = HINT_HEIGHT * k;
        int bottom = area.bottom() - 8;
        List<HintBox> boxes = new ArrayList<>();
        float[] point = new float[3];
        for (SceneState.KeyHintView hint : state.keyHints()) {
            float alpha = hint.alpha();
            if (alpha <= 0.02F) {
                continue;
            }
            List<Token> tokens = tokens(hint.keys());
            Component text = Component.translatable(hint.textKey());
            int width = 5;
            for (int i = 0; i < tokens.size(); i++) {
                width += tokens.get(i).width(font);
                if (i < tokens.size() - 1) {
                    width += 4 + font.width("+") + 4;
                }
            }
            width += 6 + 1 + 6 + font.width(text) + 6;
            int shown = width * k;
            int x0;
            int y0;
            if (hint.anchor() != null) {
                projection.project(hint.anchor().x(), hint.anchor().y(), hint.anchor().z(), point);
                x0 = Math.round(point[0] - shown / 2.0F);
                y0 = Math.round(point[1] - hintHeight - 4);
            } else {
                x0 = area.left() + (area.width() - shown) / 2;
                y0 = bottom - hintHeight;
                bottom = y0 - 4;
            }
            x0 = Math.clamp(x0, area.left() + MARGIN, Math.max(area.left() + MARGIN, area.right() - MARGIN - shown));
            y0 = Math.clamp(y0 + Math.round((1.0F - alpha) * SLIDE), area.top() + MARGIN, Math.max(area.top() + MARGIN, area.bottom() - MARGIN - hintHeight));
            boxes.add(new HintBox(tokens, text, x0, y0, width, k, alpha));
        }
        return boxes;
    }

    public static int drawKeyHints(GuiGraphicsExtractor g, Font font, List<HintBox> hints) {
        for (HintBox hint : hints) {
            g.pose().pushMatrix();
            g.pose().translate(hint.x0(), hint.y0());
            g.pose().scale(hint.scale(), hint.scale());
            drawHint(g, font, hint.tokens(), hint.text(), 0, 0, hint.width(), hint.alpha());
            g.pose().popMatrix();
        }
        return hints.size();
    }

    private static void drawHint(GuiGraphicsExtractor g, Font font, List<Token> tokens, Component text, int x0, int y0, int width, float alpha) {
        int x1 = x0 + width;
        int y1 = y0 + HINT_HEIGHT;
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, GuideDraw.fade(Palette.withAlpha(Palette.SHADOW, 0x90), alpha));
        ThemeDraw.framedBox(g, x0, y0, x1, y1, GuideDraw.fade(Palette.PANEL, alpha), GuideDraw.fade(Palette.ACCENT_DIM, alpha));
        int alphaByte = Math.round(255.0F * Math.clamp(alpha, 0.0F, 1.0F));
        int x = x0 + 5;
        for (int i = 0; i < tokens.size(); i++) {
            x += tokens.get(i).draw(g, font, x, y0 + 3, alphaByte);
            if (i < tokens.size() - 1) {
                x += 4;
                g.text(font, "+", x, y0 + 5, GuideDraw.fade(Palette.TEXT_FAINT, alpha), false);
                x += font.width("+") + 4;
            }
        }
        x += 6;
        g.fill(x, y0 + 4, x + 1, y1 - 4, GuideDraw.fade(Palette.BORDER_STRONG, alpha));
        x += 7;
        g.text(font, text, x, y0 + 5, GuideDraw.fade(Palette.TEXT, alpha), false);
    }

    static List<Token> tokens(List<KeyToken> keys) {
        List<Token> tokens = new ArrayList<>();
        for (KeyToken key : keys) {
            tokens.add(Token.of(key));
        }
        return tokens;
    }

    public record Token(int mouseButton, String text) {
        static Token of(KeyToken key) {
            if (key.kind() == KeyToken.Kind.MOUSE) {
                int button = switch (key.value()) {
                    case KeyToken.MOUSE_LEFT -> InputConstants.MOUSE_BUTTON_LEFT;
                    case KeyToken.MOUSE_MIDDLE -> InputConstants.MOUSE_BUTTON_MIDDLE;
                    default -> InputConstants.MOUSE_BUTTON_RIGHT;
                };
                return new Token(button, mouseLabel(button));
            }
            KeyMapping mapping = KeyMapping.get(key.value());
            if (mapping == null) {
                return new Token(0, GuideDraw.shortKeyName(key.value()));
            }
            if (mapping.isUnbound()) {
                return new Token(0, Component.translatable("propwash.guide.key.unbound").getString());
            }
            InputConstants.Key bound;
            try {
                bound = InputConstants.getKey(mapping.saveString());
            } catch (IllegalArgumentException e) {
                bound = InputConstants.UNKNOWN;
            }
            if (bound.getType() == InputConstants.Type.MOUSE && bound.getValue() >= 1 && bound.getValue() <= 3) {
                return new Token(bound.getValue(), mouseLabel(bound.getValue()));
            }
            return new Token(0, GuideDraw.shortKeyName(mapping.getTranslatedKeyMessage().getString()));
        }

        static String mouseLabel(int button) {
            String key = switch (button) {
                case InputConstants.MOUSE_BUTTON_LEFT -> "propwash.guide.mouse.left";
                case InputConstants.MOUSE_BUTTON_MIDDLE -> "propwash.guide.mouse.middle";
                default -> "propwash.guide.mouse.right";
            };
            return Component.translatable(key).getString();
        }

        int width(Font font) {
            if (this.mouseButton > 0) {
                return GuideDraw.MOUSE_WIDTH + 3 + font.width(this.text);
            }
            return GuideDraw.keycapWidth(font, this.text);
        }

        int draw(GuiGraphicsExtractor g, Font font, int x, int y, int alpha) {
            if (this.mouseButton > 0) {
                GuideDraw.mouse(g, x, y + 1, this.mouseButton, alpha);
                g.text(font, this.text, x + GuideDraw.MOUSE_WIDTH + 3, y + 2, GuideDraw.fade(Palette.ACCENT, alpha / 255.0F), false);
                return this.width(font);
            }
            return GuideDraw.keycap(g, font, this.text, x, y, 12, alpha);
        }
    }
}
