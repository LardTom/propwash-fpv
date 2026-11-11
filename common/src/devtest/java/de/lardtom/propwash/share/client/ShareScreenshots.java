package de.lardtom.propwash.share.client;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.share.ShareDevTables;
import de.lardtom.propwash.share.ShareVectors;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.theme.Metrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class ShareScreenshots {
    public static final String GROUP = "bildschirmfotos: Sharecode ";
    public static final String FOLDER = "share/";
    private static final String GERMAN = "de_de";
    private static final String STATE = "share_shots";
    private static final int[][] WINDOWS = {{640, 480, 2}, {854, 480, 2}};
    private static final int WINDOW_TICKS = 80;
    private static final int SETTLE_TICKS = 12;
    private static final int STEP_TICKS = 6000;

    private ShareScreenshots() {
    }

    record Shot(String name, Supplier<Screen> screen, String scrollTo) {
        Shot(String name, Supplier<Screen> screen) {
            this(name, screen, "");
        }
    }

    static void scrollTo(Minecraft mc, String tag) {
        if (tag.isEmpty() || !(mc.gui.screen() instanceof ShareScreen screen)) {
            return;
        }
        for (ShareLines.Placed entry : screen.placed()) {
            if (entry.line().tag().startsWith(tag)) {
                screen.scrollInfo(screen.infoScroll() + entry.y0() - screen.infoArea()[1] - 2 * Metrics.LINE_HEIGHT);
                return;
            }
        }
    }

    static List<Shot> shots() {
        List<Shot> out = new ArrayList<>();
        out.add(new Shot("export", () -> ShareHarness.window(ShareHarness.context(Optional.of(ShareSelfTests.freestyle()), true), ShareScreen.Mode.EXPORT)));
        out.add(new Shot("import_pw3", () -> window(ShareSelfTests.freestyleCode(), true)));
        out.add(new Shot("import_pw2", () -> window(ShareSelfTests.PW2_NAMED, true)));
        out.add(new Shot("missing_addons", () -> window(ShareSelfTests.P2_JMP, true)));
        out.add(new Shot("missing_link", () -> window(ShareVectors.missingCode(), true)));
        out.add(new Shot("checksum", () -> window(ShareVectors.mismatchCode(), true)));
        out.add(new Shot("substitutes", () -> {
            ShareSettings.substitutes(true);
            return window(ShareSelfTests.P2_JMP, true);
        }, ShareLines.TAG_SWAP));
        out.add(new Shot("shopping_survival", () -> {
            ShareSettings.substitutes(false);
            return window(ShareVectors.missingCode(), false);
        }, ShareLines.TAG_SHOPPING));
        out.add(new Shot("free_link", () -> window(ShareSelfTests.P8_FREE_LINK, true)));
        out.add(new Shot("tab", () -> {
            ShareSettings.lastInput(ShareSelfTests.P2_JMP);
            return null;
        }));
        return out;
    }

    private static ShareScreen window(String code, boolean creative) {
        ShareSettings.lastInput(code);
        return ShareHarness.window(ShareHarness.context(Optional.empty(), creative), ShareScreen.Mode.IMPORT);
    }

    public static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        String list = "(Export, Import PW3 und PW2, fehlende Add-ons, Prüfsumme falsch, Ersatzteile, Einkaufsliste, freie Adresse, Tab) bei 640x480 und 854x480";
        SelfTest.clientStep(GROUP + "englisch " + list, STEP_TICKS, ctx -> run(ctx, "en"));
        String back = GROUP + "Sprache zurückstellen";
        SelfTest.switchStep(GROUP + "Sprache Deutsch", back, 2400, ctx -> Screenshots.language(ctx, GERMAN));
        SelfTest.clientStep(GROUP + "deutsch " + list, STEP_TICKS, ctx -> run(ctx, "de"));
        SelfTest.clientStep(back, 2400, Screenshots::restoreLanguage);
    }

    private static StepResult run(SelfTest.StepContext ctx, String language) {
        Run run = (Run) ctx.state().computeIfAbsent(STATE, key -> new Run(language));
        try {
            StepResult result = run.advance(ctx, Minecraft.getInstance());
            if (result.status() != StepResult.Status.PENDING) {
                run.restore(Minecraft.getInstance());
            }
            return result;
        } catch (RuntimeException e) {
            run.restore(Minecraft.getInstance());
            throw e;
        }
    }

    private static final class Run {
        private final String language;
        private final List<Shot> shots = shots();
        private final List<String> written = new ArrayList<>();
        private int stage;
        private int since;
        private int window;
        private int index;
        private int @Nullable [] saved;
        private @Nullable CompletableFuture<String> shot;
        private String savedInput = "";

        private Run(String language) {
            this.language = language;
        }

        private void restore(Minecraft mc) {
            int[] value = this.saved;
            if (value != null) {
                closeAll(mc);
                mc.options.guiScale().set(value[2]);
                mc.getWindow().setWindowed(value[0], value[1]);
                mc.resizeGui();
                this.saved = null;
                ShareSettings.lastInput(this.savedInput);
                ShareSettings.substitutes(false);
            }
        }

        private static void closeAll(Minecraft mc) {
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            } else if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
        }

        private StepResult advance(SelfTest.StepContext ctx, Minecraft mc) {
            Window window = mc.getWindow();
            switch (this.stage) {
                case 0 -> {
                    ShareDevTables.ensure();
                    this.saved = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
                    this.savedInput = ShareSettings.lastInput();
                    closeAll(mc);
                    this.stage = 1;
                    return StepResult.pending();
                }
                case 1 -> {
                    if (this.window >= WINDOWS.length) {
                        ctx.log(this.written.size() + " Bildschirmfotos: " + String.join(", ", this.written));
                        return StepResult.pass();
                    }
                    int[] size = WINDOWS[this.window];
                    float density = Math.max(1.0F, window.getPixelDensity());
                    window.setWindowed(Math.round(size[0] / density), Math.round(size[1] / density));
                    this.since = ctx.tick();
                    this.stage = 2;
                    return StepResult.pending();
                }
                case 2 -> {
                    int[] size = WINDOWS[this.window];
                    boolean sized = window.getWidth() == size[0] && window.getHeight() == size[1];
                    if (!sized && ctx.tick() - this.since < WINDOW_TICKS) {
                        return StepResult.pending();
                    }
                    if (!sized) {
                        return StepResult.fail("Fenster wird nicht " + size[0] + "x" + size[1] + ": " + window.getWidth() + "x" + window.getHeight());
                    }
                    mc.options.guiScale().set(size[2]);
                    mc.resizeGui();
                    this.index = 0;
                    this.stage = 3;
                    return StepResult.pending();
                }
                case 3 -> {
                    if (this.index >= this.shots.size()) {
                        this.window++;
                        this.stage = 1;
                        return StepResult.pending();
                    }
                    closeAll(mc);
                    Shot shot = this.shots.get(this.index);
                    Screen screen = shot.screen().get();
                    if (screen == null) {
                        if (!PropwashClientApi.openConfigTab(ShareConfigTab.ID, null)) {
                            return StepResult.fail("Tab für das Bildschirmfoto lässt sich nicht öffnen");
                        }
                    } else {
                        mc.gui.setScreen(screen);
                    }
                    Screenshots.clearOverlays(mc);
                    Screenshots.parkMouse(mc);
                    this.since = ctx.tick();
                    this.stage = 4;
                    return StepResult.pending();
                }
                case 4 -> {
                    if (ctx.tick() - this.since == SETTLE_TICKS - 4) {
                        scrollTo(mc, this.shots.get(this.index).scrollTo());
                    }
                    if (ctx.tick() - this.since < SETTLE_TICKS) {
                        return StepResult.pending();
                    }
                    Screenshots.clearOverlays(mc);
                    int[] size = WINDOWS[this.window];
                    String name = FOLDER + "share_" + this.shots.get(this.index).name() + "_" + size[0] + "x" + size[1] + "_" + this.language;
                    this.shot = Screenshots.capture(name);
                    this.written.add(name);
                    this.stage = 5;
                    return StepResult.pending();
                }
                default -> {
                    CompletableFuture<String> pending = this.shot;
                    if (pending != null && !pending.isDone()) {
                        return StepResult.pending();
                    }
                    if (pending != null && pending.isCompletedExceptionally()) {
                        return StepResult.fail("Bildschirmfoto " + this.written.getLast() + ": " + pending.exceptionNow());
                    }
                    this.index++;
                    this.stage = 3;
                    return StepResult.pending();
                }
            }
        }
    }
}
