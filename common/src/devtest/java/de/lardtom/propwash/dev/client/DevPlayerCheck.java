package de.lardtom.propwash.dev.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class DevPlayerCheck {
    public static final String PROPERTY = "propwash.devplayer";
    public static final String FILE = "screenshots/propwash-dev-player.png";
    private static final String LOADED = "skin_loaded";
    private static final String SHOT = "skin_shot";
    private static final String FOV = "skin_fov";
    private static final int LOAD_TICKS = 400;
    private static final int SETTLE_TICKS = 20;
    private static final float PITCH = 12.0F;
    private static final int SHOT_FOV = 50;

    private DevPlayerCheck() {
    }

    public static void register() {
        String name = System.getProperty(PROPERTY, "");
        if (name.isBlank()) {
            return;
        }
        SelfTest.clientStep("dev: Skin des Dev-Spielers in der dritten Person", LOAD_TICKS + 200, ctx -> check(ctx, name));
    }

    @SuppressWarnings("unchecked")
    private static StepResult check(SelfTest.StepContext ctx, String name) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return StepResult.pending();
        }
        String actual = player.getGameProfile().name();
        if (!actual.equalsIgnoreCase(name)) {
            return StepResult.fail("Spieler heißt " + actual + " statt " + name);
        }
        PlayerSkin skin = player.getSkin();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(LOADED)) {
            if (!(skin.body() instanceof ClientAsset.DownloadedTexture)) {
                return ctx.tick() < LOAD_TICKS ? StepResult.pending()
                        : StepResult.fail("Skin von " + name + " nicht geladen, Standard-Skin " + skin.body().id());
            }
            state.put(LOADED, ctx.tick());
            Screenshots.hideMarker(true);
            Screenshots.clearOverlays(mc);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            state.put(FOV, mc.options.fov().get());
            mc.options.fov().set(SHOT_FOV);
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
        }
        pose(player);
        if (ctx.tick() - (int) state.get(LOADED) < SETTLE_TICKS) {
            return StepResult.pending();
        }
        if (!state.containsKey(SHOT)) {
            state.put(SHOT, capture(mc));
            return StepResult.pending();
        }
        CompletableFuture<Path> shot = (CompletableFuture<Path>) state.get(SHOT);
        if (!shot.isDone()) {
            return StepResult.pending();
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.fov().set((int) state.get(FOV));
        if (mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        Screenshots.hideMarker(false);
        if (shot.isCompletedExceptionally()) {
            return StepResult.fail("Bildschirmfoto: " + shot.exceptionNow());
        }
        ctx.log("Dev-Spieler " + actual + " (" + player.getUUID() + "), Skin " + skin.body().id() + ", Modell " + skin.model()
                + (skin.cape() != null ? ", mit Umhang" : "") + (skin.secure() ? ", signiert" : "") + ", Bildschirmfoto " + shot.join());
        return StepResult.pass();
    }

    private static void pose(LocalPlayer player) {
        float yaw = player.getYRot();
        player.setXRot(PITCH);
        player.xRotO = PITCH;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.setYBodyRot(yaw);
        player.yBodyRotO = yaw;
    }

    private static CompletableFuture<Path> capture(Minecraft mc) {
        CompletableFuture<Path> done = new CompletableFuture<>();
        Path file = mc.gameDirectory.toPath().resolve(FILE).toAbsolutePath().normalize();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            done.completeExceptionally(e);
            return done;
        }
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                image.writeToFile(file);
                done.complete(file);
            } catch (Exception e) {
                done.completeExceptionally(e);
            }
        }));
        return done;
    }
}
