package de.lardtom.propwash.share.chat;

import de.lardtom.propwash.share.Base62;
import de.lardtom.propwash.share.LayerCodec;
import de.lardtom.propwash.share.client.chat.ShareChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class ShareChatHarness {
    private ShareChatHarness() {
    }

    public record Cell(int codepoint, Style style) {
    }

    public record Shown(FormattedCharSequence text, Matrix3x2fc pose, int x, int y, List<Cell> cells) {
        public String plain() {
            StringBuilder out = new StringBuilder();
            for (Cell cell : this.cells) {
                out.appendCodePoint(cell.codepoint());
            }
            return out.toString();
        }

        public @Nullable Style styleAt(int index) {
            int at = 0;
            for (Cell cell : this.cells) {
                if (at == index) {
                    return cell.style();
                }
                at += Character.charCount(cell.codepoint());
            }
            return null;
        }
    }

    public static String invalidCode() {
        return "PW3-" + Base62.encode(LayerCodec.frame(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x13}));
    }

    public static List<Shown> shown(Minecraft mc) {
        List<Shown> out = new ArrayList<>();
        ActiveTextCollector collector = new ActiveTextCollector() {
            private Parameters parameters = new Parameters(new Matrix3x2f());

            @Override
            public Parameters defaultParameters() {
                return this.parameters;
            }

            @Override
            public void defaultParameters(Parameters newParameters) {
                this.parameters = newParameters;
            }

            @Override
            public void accept(TextAlignment alignment, int anchorX, int y, Parameters parameters, FormattedCharSequence text) {
                List<Cell> cells = new ArrayList<>();
                text.accept((position, style, codepoint) -> {
                    cells.add(new Cell(codepoint, style));
                    return true;
                });
                out.add(new Shown(text, new Matrix3x2f(parameters.pose()), alignment.calculateLeft(anchorX, mc.font, text), y, cells));
            }

            @Override
            public void acceptScrolling(Component message, int centerX, int left, int right, int top, int bottom, Parameters parameters) {
            }
        };
        mc.gui.hud.getChat().captureClickableText(collector, mc.getWindow().getGuiScaledHeight(), mc.gui.hud.getGuiTicks(),
                ChatComponent.DisplayMode.FOREGROUND);
        return out;
    }

    public static @Nullable Shown lineWith(Minecraft mc, String text) {
        for (Shown line : shown(mc)) {
            if (line.plain().contains(text)) {
                return line;
            }
        }
        return null;
    }

    public static @Nullable Style shownStyle(Minecraft mc, String code) {
        for (Shown line : shown(mc)) {
            for (Cell cell : line.cells()) {
                if (ours(cell.style(), code)) {
                    return cell.style();
                }
            }
        }
        return null;
    }

    public static int shownCells(Minecraft mc, String code) {
        int count = 0;
        for (Shown line : shown(mc)) {
            for (Cell cell : line.cells()) {
                if (ours(cell.style(), code)) {
                    count++;
                }
            }
        }
        return count;
    }

    public static List<Cell> cells(FormattedText text) {
        List<Cell> out = new ArrayList<>();
        text.visit((style, contents) -> {
            StringDecomposer.iterateFormatted(contents, style, (position, charStyle, codepoint) -> {
                out.add(new Cell(codepoint, charStyle));
                return true;
            });
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    public static String plain(List<Cell> cells) {
        StringBuilder out = new StringBuilder();
        for (Cell cell : cells) {
            out.appendCodePoint(cell.codepoint());
        }
        return out.toString();
    }

    public static @Nullable Style styleIn(FormattedText text, String part, int offset) {
        List<Cell> cells = cells(text);
        int index = plain(cells).indexOf(part);
        if (index < 0) {
            return null;
        }
        int at = 0;
        for (Cell cell : cells) {
            if (at == index + offset) {
                return cell.style();
            }
            at += Character.charCount(cell.codepoint());
        }
        return null;
    }

    public static String hoverText(@Nullable Style style) {
        return style != null && style.getHoverEvent() instanceof HoverEvent.ShowText(Component value) ? value.getString() : "";
    }

    public static boolean ours(@Nullable Style style, String code) {
        return ShareChat.target(style).filter(target -> target.code().equals(code)).isPresent();
    }

    public static float @Nullable [] position(Minecraft mc, Predicate<Style> wanted) {
        ChatComponent chat = mc.gui.hud.getChat();
        int height = mc.getWindow().getGuiScaledHeight();
        for (Shown line : shown(mc)) {
            int width = mc.font.width(line.text());
            Vector2f start = line.pose().transformPosition(line.x(), line.y() + 4.0F, new Vector2f());
            Vector2f end = line.pose().transformPosition(line.x() + width, line.y() + 4.0F, new Vector2f());
            for (float x = start.x; x < end.x; x += 1.0F) {
                ActiveTextCollector.ClickableStyleFinder finder = new ActiveTextCollector.ClickableStyleFinder(mc.font, (int) x, (int) start.y);
                chat.captureClickableText(finder, height, mc.gui.hud.getGuiTicks(), ChatComponent.DisplayMode.FOREGROUND);
                Style style = finder.result();
                if (style != null && wanted.test(style)) {
                    return new float[]{x + 1.0F, start.y};
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<GuiMessage> messages(Minecraft mc) {
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            return List.copyOf((List<GuiMessage>) field.get(mc.gui.hud.getChat()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    public static void moveMouse(Minecraft mc, float guiX, float guiY) {
        double scaleX = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
        double scaleY = (double) mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
        mc.mouseHandler.onMove(mc.getWindow().handle(), guiX * scaleX, guiY * scaleY, 0.0, 0.0);
    }
}
