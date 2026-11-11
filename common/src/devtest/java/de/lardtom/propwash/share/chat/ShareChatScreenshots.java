package de.lardtom.propwash.share.chat;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.share.ShareDevTables;
import de.lardtom.propwash.share.ShareVectors;
import de.lardtom.propwash.share.client.chat.ShareChat;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ShareChatScreenshots {
    public static final String GROUP = "bildschirmfotos: Sharecode im Chat ";
    public static final String FOLDER = "share-chat/";
    private static final String GERMAN = "de_de";
    private static final String STATE = "share_chat_shots";
    private static final int[][] WINDOWS = {{640, 480, 2}, {854, 480, 2}};
    private static final int WINDOW_TICKS = 80;
    private static final int OPEN_TICKS = 3;
    private static final int SETTLE_TICKS = 14;
    private static final int STEP_TICKS = 4000;

    private ShareChatScreenshots() {
    }

    record Shot(String name, String text, String code) {
    }

    static List<Shot> shots() {
        return List.of(new Shot("named", "schau mal: " + ShareVectors.NAMED_WHOOP + " – fliegt super", ShareVectors.NAMED_WHOOP),
                new Shot("missing_addon", "mit Add-on: " + ShareVectors.missingCode(), ShareVectors.missingCode()));
    }

    public static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        String list = "(Tooltip mit Vorschau, fehlendes Add-on) bei 640x480 und 854x480";
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
        private boolean enabled = true;
        private int @Nullable [] saved;
        private @Nullable CompletableFuture<String> shot;

        private Run(String language) {
            this.language = language;
        }

        private void restore(Minecraft mc) {
            int[] value = this.saved;
            if (value != null) {
                closeAll(mc);
                Screenshots.clearOverlays(mc);
                mc.options.guiScale().set(value[2]);
                mc.getWindow().setWindowed(value[0], value[1]);
                mc.resizeGui();
                ShareChat.setEnabled(this.enabled);
                this.saved = null;
            }
        }

        private static void closeAll(Minecraft mc) {
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
        }

        private StepResult advance(SelfTest.StepContext ctx, Minecraft mc) {
            Window window = mc.getWindow();
            switch (this.stage) {
                case 0 -> {
                    ShareDevTables.ensure();
                    this.saved = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
                    this.enabled = ShareChat.enabled();
                    ShareChat.setEnabled(true);
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
                    Screenshots.clearOverlays(mc);
                    ShareChatSelfTests.inject(mc, this.shots.get(this.index).text());
                    mc.gui.setScreen(new ChatScreen("", false));
                    this.since = ctx.tick();
                    this.stage = 4;
                    return StepResult.pending();
                }
                case 4 -> {
                    if (ctx.tick() - this.since < OPEN_TICKS) {
                        return StepResult.pending();
                    }
                    String code = this.shots.get(this.index).code();
                    float[] at = ShareChatHarness.position(mc, style -> ShareChatHarness.ours(style, code));
                    if (at == null) {
                        return StepResult.fail("Code für das Bildschirmfoto " + this.shots.get(this.index).name() + " nicht im Chat zu finden");
                    }
                    ShareChatHarness.moveMouse(mc, at[0], at[1]);
                    this.since = ctx.tick();
                    this.stage = 5;
                    return StepResult.pending();
                }
                case 5 -> {
                    if (ctx.tick() - this.since < SETTLE_TICKS) {
                        return StepResult.pending();
                    }
                    int[] size = WINDOWS[this.window];
                    String name = FOLDER + "share_chat_" + this.shots.get(this.index).name() + "_" + size[0] + "x" + size[1] + "_" + this.language;
                    this.shot = Screenshots.capture(name);
                    this.written.add(name);
                    this.stage = 6;
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
