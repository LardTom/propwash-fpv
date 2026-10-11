package de.lardtom.propwash.video.client.record;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.widget.TextField;
import de.lardtom.propwash.video.record.EncoderChoice;
import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.external.FfmpegCodec;
import de.lardtom.propwash.video.record.external.FfmpegLocator;
import de.lardtom.propwash.video.record.external.HardwareEncoders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class FfmpegSelfTests {
    public static final String PREFIX = "video: ffmpeg";
    public static final String FAKE_VERSION = "0.0-selftest";
    private static final String PHASE = "phase";
    private static final String FAKE = "fake";
    private static final String SAVED = "saved";
    private static final int WAIT_TICKS = 900;

    private FfmpegSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + " – Abschnitt im Tab Action-Cam: Status, Pfadfeld, Testen und Anleitung", 1200, FfmpegSelfTests::sectionStep);
        SelfTest.clientStep(PREFIX + " – eigener Pfad: Testen, nicht ausführbar, falscher Pfad, abgelegte Datei und Speichern", 4000, FfmpegSelfTests::customPathStep);
    }

    static @Nullable ActionCamTab openTab() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ConfiguratorScreen screen)) {
            ConfigTabs.open(null, "action_cam");
            return null;
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !"action_cam".equals(active.id())) {
            screen.selectTab("action_cam");
            return null;
        }
        return active instanceof ActionCamTab tab ? tab : null;
    }

    static void closeTab() {
        if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
    }

    static List<String> texts(ActionCamTab tab) {
        return tab.ffmpegLines().stream().map(line -> line.text().getString()).toList();
    }

    private static List<String> buttons() {
        List<String> out = new ArrayList<>();
        if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractWidget widget) {
                    out.add(widget.getMessage().getString());
                }
            }
        }
        return out;
    }

    private static StepResult fail(String reason) {
        closeTab();
        return StepResult.fail(reason);
    }

    private static StepResult sectionStep(SelfTest.StepContext ctx) {
        ActionCamTab tab = openTab();
        if (tab == null) {
            return ctx.tick() > 200 ? fail("Tab „Action-Cam“ öffnet nicht") : StepResult.pending();
        }
        HardwareEncoders.Status status = tab.ffmpegStatus();
        if (status.state() == HardwareEncoders.State.CHECKING) {
            return ctx.tick() > WAIT_TICKS ? fail("Prüfung von ffmpeg endet nicht") : StepResult.pending();
        }
        List<String> lines = texts(tab);
        ctx.log("ffmpeg im Tab: " + status.state() + ", Quelle " + status.source() + ", " + status.ffmpeg() + ", Version „" + status.version()
                + "“, Probe " + Math.round(status.fps()) + " B/s (" + tab.probeFormat().label() + ") | PATH der Sitzung: " + System.getenv("PATH"));
        ctx.log("Zeilen im Abschnitt: " + lines);
        if (lines.isEmpty() || !lines.getFirst().contains("ffmpeg")) {
            return fail("Abschnitt ohne ffmpeg-Zeile: " + lines);
        }
        String first = lines.getFirst();
        switch (status.state()) {
            case READY -> {
                if (!first.contains(String.valueOf(status.ffmpeg())) || !first.contains(status.version()) || status.codec() == null
                        || !first.contains(status.codec().label()) || !first.contains("frames/s")) {
                    return fail("Zeile „gefunden“ ohne Pfad, Version, Encoder oder Testwert: " + first);
                }
            }
            case NO_FFMPEG -> {
                if (!first.contains("not found") || !first.contains("Guide")) {
                    return fail("Zeile „nicht gefunden“ ohne Hinweis auf die Anleitung: " + first);
                }
            }
            case NO_HARDWARE, UNUSABLE -> {
                if (!first.contains(String.valueOf(status.ffmpeg()))) {
                    return fail("Zeile ohne Pfad: " + first);
                }
            }
            default -> {
                return fail("unerwarteter Zustand " + status.state());
            }
        }
        TextField field = tab.pathField();
        List<String> buttons = buttons();
        if (field == null || !buttons.contains("Test") || !buttons.contains("Guide") || !buttons.contains(field.getMessage().getString())) {
            return fail("Pfadfeld, „Test“ oder „Guide“ fehlen: " + buttons);
        }
        if (!ActionCamTab.GUIDE_URL.startsWith("https://propwashfpv.com/docs/guide/#")) {
            return fail("Anleitung zeigt nicht auf die Website: " + ActionCamTab.GUIDE_URL);
        }
        String missing = untranslated();
        if (missing != null) {
            return fail("Text fehlt in der Sprachdatei: " + missing);
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen configurator = minecraft.gui.screen();
        tab.openGuide();
        if (!(minecraft.gui.screen() instanceof ConfirmLinkScreen link)) {
            return fail("„Guide“ öffnet keine Rückfrage zum Link");
        }
        link.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
        if (minecraft.gui.screen() != configurator || openTab() == null) {
            return fail("nach der Rückfrage zum Link ist der Tab „Action-Cam“ nicht mehr offen");
        }
        closeTab();
        return StepResult.pass();
    }

    private static @Nullable String untranslated() {
        RecordingFormat format = RecordingFormat.of(1080, 60);
        Path path = Path.of("ffmpeg");
        List<Component> texts = new ArrayList<>();
        HardwareEncoders.Trial trial = new HardwareEncoders.Trial(FfmpegCodec.VIDEOTOOLBOX, 0, 200.0, "");
        for (HardwareEncoders.State state : HardwareEncoders.State.values()) {
            HardwareEncoders.Status status = new HardwareEncoders.Status(state, state == HardwareEncoders.State.NO_HARDWARE ? "" : "x", path,
                    FfmpegCodec.VIDEOTOOLBOX, 0, 200.0, "1.0", FfmpegLocator.Source.KNOWN, FfmpegLocator.CustomPath.MISSING, List.of(trial));
            for (EncoderChoice choice : EncoderChoice.values()) {
                ActionCamTab.ffmpegLines(status, format, choice, true).forEach(line -> texts.add(line.text()));
            }
            for (FfmpegLocator.CustomPath custom : FfmpegLocator.CustomPath.values()) {
                HardwareEncoders.Status found = new HardwareEncoders.Status(state, "x", path, null, 0, 0.0, "", null, custom, List.of());
                ActionCamTab.ffmpegLines(found, format, EncoderChoice.AUTO, false).forEach(line -> texts.add(line.text()));
                HardwareEncoders.Status bare = new HardwareEncoders.Status(state, "x", null, null, 0, 0.0, "", null, custom, List.of());
                ActionCamTab.ffmpegLines(bare, format, EncoderChoice.AUTO, false).forEach(line -> texts.add(line.text()));
            }
            texts.add(RecordingHud.encoderLine(true, status));
            texts.add(RecordingHud.encoderLine(false, status));
            texts.add(RecordingHud.encoderShort(false, status));
            texts.add(ActionCamTab.encoderStatus(status, 4));
        }
        for (String key : List.of("section.ffmpeg", "ffmpeg.path", "ffmpeg.path.hint", "ffmpeg.path.tooltip", "ffmpeg.test", "ffmpeg.test.tooltip",
                "ffmpeg.guide", "ffmpeg.guide.tooltip")) {
            texts.add(Component.translatable("propwash.video.record.tab." + key));
        }
        texts.add(Component.translatable("propwash.video.record.hud.format", "1080p60", "CPU"));
        for (Component text : texts) {
            String shown = text.getString();
            if (shown.contains("propwash.video")) {
                return shown;
            }
        }
        return null;
    }

    private static Path fakeFfmpeg() throws IOException {
        Path dir = Files.createTempDirectory("propwash-ffmpeg-selftest");
        Path file = Files.writeString(dir.resolve("ffmpeg"), """
                #!/bin/sh
                case "$1" in
                -version) echo 'ffmpeg version %s Copyright (c) the FFmpeg developers'; exit 0 ;;
                esac
                echo 'Encoders:'
                echo ' V....D libx264              libx264 H.264'
                exit 0
                """.formatted(FAKE_VERSION));
        if (!file.toFile().setExecutable(true)) {
            throw new IOException("Datei lässt sich nicht ausführbar machen");
        }
        return file;
    }

    private static void delete(@Nullable Path file) {
        if (file == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(file.getParent())) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
        }
    }

    private static StepResult finish(SelfTest.StepContext ctx, @Nullable String failure) {
        if (ctx.state().get(SAVED) instanceof RecordingConfig saved) {
            RecordingConfig.CONFIG.set(saved);
        }
        closeTab();
        delete((Path) ctx.state().get(FAKE));
        HardwareEncoders.reset();
        return failure == null ? StepResult.pass() : StepResult.fail(failure);
    }

    private static StepResult customPathStep(SelfTest.StepContext ctx) {
        if (FfmpegCodec.Platform.current() == FfmpegCodec.Platform.WINDOWS) {
            ctx.log("übersprungen: das Test-Programm ist ein Shell-Skript");
            return StepResult.pass();
        }
        Minecraft minecraft = Minecraft.getInstance();
        int phase = (int) ctx.state().getOrDefault(PHASE, 0);
        if (phase == 0 && !ctx.state().containsKey(SAVED)) {
            ctx.state().put(SAVED, RecordingConfig.get().copy());
            RecordingConfig.CONFIG.set(new RecordingConfig());
            closeTab();
            return StepResult.pending();
        }
        ActionCamTab tab = openTab();
        if (tab == null) {
            return ctx.tick() > 200 && phase == 0 ? finish(ctx, "Tab „Action-Cam“ öffnet nicht") : StepResult.pending();
        }
        TextField field = tab.pathField();
        if (field == null) {
            return finish(ctx, "kein Pfadfeld");
        }
        HardwareEncoders.Status status = tab.ffmpegStatus();
        if (status.state() == HardwareEncoders.State.CHECKING) {
            return ctx.tick() > 3500 ? finish(ctx, "Prüfung von ffmpeg endet nicht (Phase " + phase + ")") : StepResult.pending();
        }
        Path fake = (Path) ctx.state().get(FAKE);
        switch (phase) {
            case 0 -> {
                try {
                    fake = fakeFfmpeg();
                } catch (IOException e) {
                    return finish(ctx, "Test-Programm: " + e.getMessage());
                }
                ctx.state().put(FAKE, fake);
                field.setValue("\"" + fake + "\"");
                List<String> lines = texts(tab);
                if (lines.stream().noneMatch(line -> line.contains("Press “Test”"))) {
                    return finish(ctx, "kein Hinweis auf „Test“ nach der Eingabe: " + lines);
                }
                if (fake.equals(tab.ffmpegStatus().ffmpeg())) {
                    return finish(ctx, "Pfad wurde schon beim Tippen geprüft");
                }
                tab.test();
                ctx.state().put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                List<String> lines = texts(tab);
                ctx.log("eigener Pfad: " + status.state() + ", Quelle " + status.source() + ", Version „" + status.version() + "“ | " + lines);
                if (status.state() != HardwareEncoders.State.NO_HARDWARE || !fake.equals(status.ffmpeg())
                        || status.source() != FfmpegLocator.Source.CUSTOM || !FAKE_VERSION.equals(status.version()) || status.customMissing()) {
                    return finish(ctx, "eigener Pfad nicht übernommen: " + status);
                }
                if (lines.isEmpty() || !lines.getFirst().contains(FAKE_VERSION) || !lines.getFirst().contains(fake.toString())
                        || lines.stream().anyMatch(line -> line.contains("Press “Test”"))) {
                    return finish(ctx, "Zeile ohne Version und Pfad des eigenen ffmpeg: " + lines);
                }
                if (!fake.toFile().setExecutable(false)) {
                    return finish(ctx, "Ausführungsrecht lässt sich nicht entziehen");
                }
                tab.test();
                ctx.state().put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                List<String> lines = texts(tab);
                boolean restored = fake.toFile().setExecutable(true);
                ctx.log("nicht ausführbar: " + status.state() + ", " + status.custom() + " | " + lines);
                if (!restored || status.custom() != FfmpegLocator.CustomPath.NOT_EXECUTABLE || fake.equals(status.ffmpeg())
                        || lines.stream().noneMatch(line -> line.contains("chmod +x"))) {
                    return finish(ctx, "nicht ausführbare Datei nicht gemeldet: " + status + " | " + lines);
                }
                field.setValue(fake.resolveSibling("missing").toString());
                if (tab.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0))) {
                    return finish(ctx, "Enter prüft, obwohl das Pfadfeld nicht den Fokus hat");
                }
                field.setFocused(true);
                if (!tab.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0)) || fake.equals(tab.ffmpegStatus().ffmpeg())) {
                    return finish(ctx, "Enter im Pfadfeld prüft den Pfad nicht");
                }
                field.setFocused(false);
                ctx.state().put(PHASE, 3);
                return StepResult.pending();
            }
            case 3 -> {
                List<String> lines = texts(tab);
                ctx.log("falscher Pfad: " + status.state() + ", Quelle " + status.source() + ", " + status.ffmpeg() + " | " + lines);
                if (!status.customMissing() || fake.equals(status.ffmpeg())
                        || lines.stream().noneMatch(line -> line.contains("no program at the entered path"))) {
                    return finish(ctx, "falscher Pfad nicht gemeldet: " + status + " | " + lines);
                }
                field.setValue("");
                if (!(minecraft.gui.screen() instanceof ConfiguratorScreen screen)) {
                    return finish(ctx, "Konfigurator geschlossen");
                }
                screen.onFilesDrop(List.of(fake));
                if (!fake.toString().equals(field.getValue())) {
                    return finish(ctx, "abgelegte Datei steht nicht im Pfadfeld: " + field.getValue());
                }
                ctx.state().put(PHASE, 4);
                return StepResult.pending();
            }
            case 4 -> {
                if (status.state() != HardwareEncoders.State.NO_HARDWARE || !fake.equals(status.ffmpeg())) {
                    return finish(ctx, "abgelegte Datei nicht geprüft: " + status);
                }
                if (!(minecraft.gui.screen() instanceof ConfiguratorScreen screen)) {
                    return finish(ctx, "Konfigurator geschlossen");
                }
                screen.saveAndClose();
                if (!fake.toString().equals(RecordingConfig.get().ffmpegPath)) {
                    return finish(ctx, "Pfad nicht gespeichert: „" + RecordingConfig.get().ffmpegPath + "“");
                }
                HardwareEncoders.Settings settings = ActionCamRecorder.hardwareSettings(minecraft);
                if (!settings.enabled() || !fake.toString().equals(settings.ffmpegPath())) {
                    return finish(ctx, "Aufnahme nutzt den gespeicherten Pfad nicht: " + settings);
                }
                HardwareEncoders.Status recording = ActionCamRecorder.hardwareStatus(minecraft, tab.probeFormat());
                if (!fake.equals(recording.ffmpeg()) || HardwareEncoders.factory(recording).isPresent()) {
                    return finish(ctx, "Aufnahme prüft ein anderes ffmpeg: " + recording);
                }
                String line = RecordingHud.encoderLine(false, recording).getString();
                ctx.log("gespeichert und von der Aufnahme übernommen; HUD-Zeile ohne Hardware-Encoder: " + line);
                if (!line.contains("Propwash (CPU)") || !line.contains("no usable hardware encoder")) {
                    return finish(ctx, "HUD-Zeile nennt den Grund nicht: " + line);
                }
                return finish(ctx, null);
            }
            default -> {
                return finish(ctx, null);
            }
        }
    }
}
