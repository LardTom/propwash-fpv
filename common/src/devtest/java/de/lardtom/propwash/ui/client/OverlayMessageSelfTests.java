package de.lardtom.propwash.ui.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class OverlayMessageSelfTests {
    public static final String NAME = "ui: lange Meldungen über der Schnellleiste brechen um (falsche Brille, Deutsch und Englisch)";
    public static final String SHOT = "overlay_message_wrapped";
    private static final String DRONE = "Nighthawk Freestyle 5";
    private static final String[] VIDEO_KEYS = {"propwash.drone.bind.refused_video", "propwash.drone.session.refused_video",
            "propwash.drone.session.no_video"};
    private static final String[] NAME_KEYS = {"propwash.drone.bind.denied_other"};
    private static final int[] WIDTHS = {427, 480, 320};
    private static final int MAX_LINES = 4;
    private static final String SHOWN = "shown";
    private static final String CAPTURE = "capture";

    private OverlayMessageSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(NAME, 300, OverlayMessageSelfTests::step);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
            return StepResult.pending();
        }
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        Component message = Component.translatable(VIDEO_KEYS[1], Component.translatable("propwash.drone.video_system.analog"), DRONE,
                Component.translatable("propwash.drone.video_system.digital")).withStyle(ChatFormatting.RED);
        if (!ctx.state().containsKey(SHOWN)) {
            for (String language : new String[]{"en_us", "de_de"}) {
                JsonObject texts;
                try {
                    texts = load(language);
                } catch (Exception e) {
                    return StepResult.fail("Sprachdatei " + language + " nicht lesbar: " + e);
                }
                String analog = texts.get("propwash.drone.video_system.analog").getAsString();
                String digital = texts.get("propwash.drone.video_system.digital").getAsString();
                for (String key : all()) {
                    String pattern = texts.get(key).getAsString();
                    String text = key.endsWith("_video") ? String.format(Locale.ROOT, pattern, analog, DRONE, digital) : String.format(Locale.ROOT, pattern, DRONE);
                    int width = font.width(text);
                    StringBuilder report = new StringBuilder(language + " " + key + ": " + width + " px");
                    for (int gui : WIDTHS) {
                        int limit = OverlayMessageWrap.limit(gui);
                        List<FormattedCharSequence> lines = OverlayMessageWrap.needed(width, gui)
                                ? OverlayMessageWrap.lines(font, Component.literal(text), gui) : List.of(Component.literal(text).getVisualOrderText());
                        int widest = 0;
                        for (FormattedCharSequence line : lines) {
                            widest = Math.max(widest, font.width(line));
                        }
                        report.append(", GUI ").append(gui).append(": ").append(lines.size()).append(" Zeilen bis ").append(widest).append(" px");
                        if (widest > limit) {
                            return StepResult.fail(language + " " + key + " ist bei GUI-Breite " + gui + " mit " + widest + " px breiter als " + limit + " px");
                        }
                        if (lines.size() > MAX_LINES) {
                            return StepResult.fail(language + " " + key + " braucht bei GUI-Breite " + gui + " " + lines.size() + " Zeilen");
                        }
                    }
                    ctx.log(report.toString());
                }
            }
            mc.gui.hud.setOverlayMessage(message, false);
            ctx.state().put(SHOWN, ctx.tick());
            return StepResult.pending();
        }
        int since = ctx.tick() - (int) ctx.state().get(SHOWN);
        if (since < 4) {
            return StepResult.pending();
        }
        int width = font.width(message);
        int limit = OverlayMessageWrap.limit(guiWidth);
        boolean wraps = OverlayMessageWrap.needed(width, guiWidth);
        boolean drawn = OverlayMessageWrap.lastLimit() == limit && OverlayMessageWrap.lastWidest() <= limit
                && (wraps ? OverlayMessageWrap.lastLines() >= 2 : OverlayMessageWrap.lastLines() == 1);
        if (!drawn) {
            if (since < 40) {
                if (since % 10 == 0) {
                    mc.gui.hud.setOverlayMessage(message, false);
                }
                return StepResult.pending();
            }
            return StepResult.fail(String.format(Locale.ROOT, "Meldung mit %d px bei GUI-Breite %d: gezeichnet %d Zeilen bis %d px, Grenze %d (erwartet %d)",
                    width, guiWidth, OverlayMessageWrap.lastLines(), OverlayMessageWrap.lastWidest(), OverlayMessageWrap.lastLimit(), limit));
        }
        if (Screenshots.enabled()) {
            if (!ctx.state().containsKey(CAPTURE)) {
                ctx.state().put(CAPTURE, Screenshots.capture(SHOT));
                return StepResult.pending();
            }
            CompletableFuture<?> capture = (CompletableFuture<?>) ctx.state().get(CAPTURE);
            if (!capture.isDone()) {
                return StepResult.pending();
            }
            if (capture.isCompletedExceptionally()) {
                return StepResult.fail("Bildschirmfoto: " + capture.exceptionNow());
            }
        }
        ctx.log(String.format(Locale.ROOT, "im Spiel: %d px bei GUI-Breite %d in %d Zeilen bis %d px (Grenze %d)", width, guiWidth,
                OverlayMessageWrap.lastLines(), OverlayMessageWrap.lastWidest(), limit));
        return StepResult.pass();
    }

    private static String[] all() {
        String[] keys = new String[VIDEO_KEYS.length + NAME_KEYS.length];
        System.arraycopy(VIDEO_KEYS, 0, keys, 0, VIDEO_KEYS.length);
        System.arraycopy(NAME_KEYS, 0, keys, VIDEO_KEYS.length, NAME_KEYS.length);
        return keys;
    }

    private static JsonObject load(String language) throws Exception {
        try (InputStream in = OverlayMessageSelfTests.class.getResourceAsStream("/assets/propwash/lang/" + language + ".json")) {
            if (in == null) {
                throw new IllegalStateException("fehlt");
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
