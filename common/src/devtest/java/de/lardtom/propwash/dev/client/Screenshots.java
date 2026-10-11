package de.lardtom.propwash.dev.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

public final class Screenshots {
    public static final String PROPERTY = "propwash.screenshots";
    public static final int TARGET_WIDTH = 1920;
    public static final int TARGET_HEIGHT = 1080;
    public static final int GUI_SCALE = 3;
    private static final String DIR = System.getProperty(PROPERTY, "");
    private static final String READY = "shot_ready";
    private static final String AVAILABLE = "shot_available";
    private static final String SHOT = "shot_future";
    private static final String SIZE = "shot_size";
    private static final String RELOAD = "language_reload";
    private static final int STEP_TICKS = 1200;
    private static boolean suppressMarker;
    private static int @Nullable [] savedWindow;
    private static @Nullable String savedLanguage;

    private Screenshots() {
    }

    public static boolean enabled() {
        return SelfTest.enabled() && !DIR.isBlank();
    }

    public static boolean markerHidden() {
        return suppressMarker;
    }

    public static void hideMarker(boolean hidden) {
        suppressMarker = hidden;
    }

    public static Path dir() {
        return Path.of(DIR);
    }

    public static void parkMouse(Minecraft mc) {
        mc.mouseHandler.onMove(mc.getWindow().handle(), 1.0, 1.0, 0.0, 0.0);
    }

    public static void clearOverlays(Minecraft mc) {
        mc.gui.hud.getChat().clearMessages(false);
        mc.gui.hud.setOverlayMessage(Component.empty(), false);
        mc.gui.hud.clearTitles();
        mc.gui.toastManager().clear();
    }

    public static int downscale(int width, int height) {
        for (int factor = 3; factor > 1; factor--) {
            if (width % factor == 0 && height % factor == 0 && width / factor >= TARGET_WIDTH) {
                return factor;
            }
        }
        return 1;
    }

    public static CompletableFuture<String> capture(String name) {
        CompletableFuture<String> done = new CompletableFuture<>();
        Path file = dir().resolve(name + ".png").normalize();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            done.completeExceptionally(e);
            return done;
        }
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        int factor = downscale(target.width, target.height);
        String size = (target.width / factor) + "x" + (target.height / factor);
        Screenshot.takeScreenshot(target, factor, image -> Util.ioPool().execute(() -> {
            try (image) {
                image.writeToFile(file);
                done.complete(size);
            } catch (Exception e) {
                done.completeExceptionally(e);
            }
        }));
        return done;
    }

    public static void step(String name, String file, int settleTicks, Scene scene) {
        SelfTest.clientStep(name, STEP_TICKS, ctx -> run(ctx, file, settleTicks, scene));
    }

    public static void optionalStep(String name, String file, int settleTicks, BooleanSupplier available, Scene scene) {
        SelfTest.clientStep(name, STEP_TICKS, ctx -> {
            if (!(boolean) ctx.state().computeIfAbsent(AVAILABLE, key -> available.getAsBoolean())) {
                ctx.log("Bildschirmfoto " + file + " übersprungen (Teile nicht geladen)");
                return StepResult.pass();
            }
            return run(ctx, file, settleTicks, scene);
        });
    }

    @SuppressWarnings("unchecked")
    private static StepResult run(SelfTest.StepContext ctx, String file, int settleTicks, Scene scene) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(READY)) {
            StepResult prepared;
            try {
                prepared = scene.prepare(ctx);
            } catch (RuntimeException e) {
                scene.finish(ctx);
                return StepResult.fail("Szene: " + e);
            }
            if (prepared.status() == StepResult.Status.FAIL) {
                scene.finish(ctx);
                return prepared;
            }
            if (prepared.status() == StepResult.Status.PENDING) {
                return prepared;
            }
            state.put(READY, ctx.tick());
            clearOverlays(mc);
            return StepResult.pending();
        }
        scene.hold(ctx);
        if (ctx.tick() - (int) state.get(READY) < settleTicks) {
            return StepResult.pending();
        }
        if (!state.containsKey(SHOT)) {
            state.put(SHOT, capture(file));
            return StepResult.pending();
        }
        CompletableFuture<String> shot = (CompletableFuture<String>) state.get(SHOT);
        if (!shot.isDone()) {
            return StepResult.pending();
        }
        scene.finish(ctx);
        if (shot.isCompletedExceptionally()) {
            return StepResult.fail("Bildschirmfoto " + file + ": " + shot.exceptionNow());
        }
        state.put(SIZE, shot.join());
        ctx.log("Bildschirmfoto " + dir().resolve(file + ".png") + " (" + shot.join() + ")");
        return StepResult.pass();
    }

    public static StepResult resizeWindow(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (ctx.tick() == 0) {
            float density = Math.max(1.0F, window.getPixelDensity());
            ctx.state().put("window", new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()});
            window.setWindowed(Math.round(TARGET_WIDTH / density), Math.round(TARGET_HEIGHT / density));
            return StepResult.pending();
        }
        boolean sized = window.getWidth() >= TARGET_WIDTH && window.getHeight() >= TARGET_HEIGHT;
        if (!sized && ctx.tick() < 60) {
            return StepResult.pending();
        }
        mc.options.guiScale().set(GUI_SCALE);
        mc.resizeGui();
        if (savedWindow == null) {
            savedWindow = (int[]) ctx.state().get("window");
        }
        ctx.log("Fenster " + window.getWidth() + "x" + window.getHeight() + " (Dichte " + window.getPixelDensity() + "), GUI "
                + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + ", vorher " + savedWindow[0] + "x" + savedWindow[1]
                + " mit GUI-Skala " + savedWindow[2]);
        return StepResult.pass();
    }

    public static StepResult restoreWindow(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int[] saved = savedWindow;
        if (saved != null) {
            mc.options.guiScale().set(saved[2]);
            mc.getWindow().setWindowed(saved[0], saved[1]);
            mc.resizeGui();
            savedWindow = null;
            ctx.log("Fenster zurück auf " + saved[0] + "x" + saved[1] + " mit GUI-Skala " + saved[2] + ", jetzt " + mc.getWindow().getScreenWidth()
                    + "x" + mc.getWindow().getScreenHeight());
        }
        return StepResult.pass();
    }

    public static StepResult language(SelfTest.StepContext ctx, String code) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(RELOAD)) {
            String current = mc.getLanguageManager().getSelected();
            if (savedLanguage == null) {
                savedLanguage = current;
            }
            if (code.equals(current)) {
                return StepResult.pass();
            }
            mc.gui.setScreen(null);
            mc.getLanguageManager().setSelected(code);
            mc.options.languageCode = code;
            state.put(RELOAD, mc.reloadResourcePacks());
            return StepResult.pending();
        }
        CompletableFuture<?> reload = (CompletableFuture<?>) state.get(RELOAD);
        if (!reload.isDone() || mc.gui.overlay() != null) {
            return StepResult.pending();
        }
        if (reload.isCompletedExceptionally()) {
            return StepResult.fail("Sprache " + code + ": " + reload.exceptionNow());
        }
        ctx.log("Sprache " + code + " aktiv");
        return StepResult.pass();
    }

    public static StepResult restoreLanguage(SelfTest.StepContext ctx) {
        String saved = savedLanguage;
        if (saved == null) {
            return StepResult.pass();
        }
        StepResult result = language(ctx, saved);
        if (result.status() != StepResult.Status.PENDING) {
            savedLanguage = null;
        }
        return result;
    }

    public static Scene guiScale(int scale, Scene scene) {
        return new Scene() {
            @Override
            public StepResult prepare(SelfTest.StepContext ctx) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.options.guiScale().get() != scale) {
                    mc.options.guiScale().set(scale);
                    mc.resizeGui();
                }
                return scene.prepare(ctx);
            }

            @Override
            public void hold(SelfTest.StepContext ctx) {
                scene.hold(ctx);
            }

            @Override
            public void finish(SelfTest.StepContext ctx) {
                scene.finish(ctx);
                Minecraft mc = Minecraft.getInstance();
                mc.options.guiScale().set(GUI_SCALE);
                mc.resizeGui();
            }
        };
    }

    public interface Scene {
        StepResult prepare(SelfTest.StepContext ctx);

        default void hold(SelfTest.StepContext ctx) {
        }

        default void finish(SelfTest.StepContext ctx) {
        }
    }
}
