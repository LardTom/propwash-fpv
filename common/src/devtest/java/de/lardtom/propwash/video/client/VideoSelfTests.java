package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.video.AnalogBreakup;
import de.lardtom.propwash.video.EffectParams;
import de.lardtom.propwash.video.client.record.ActionCamSelfTests;
import de.lardtom.propwash.video.client.record.FfmpegSelfTests;
import de.lardtom.propwash.video.client.record.FlashbackCamSelfTests;
import de.lardtom.propwash.video.client.record.RecordingPerfSelfTests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class VideoSelfTests {
    private static final double DRONE_DISTANCE = 10.0;
    private static final int SETTLE_TICKS = 30;
    private static final long NANOS_PER_MS = 1_000_000L;

    private static @Nullable Scenario scenario;

    private VideoSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("video: Posen-Puffer interpoliert", 20, VideoSelfTests::poseStep);
        SelfTest.clientStep("video: Posen-Puffer interpoliert nicht über einen Reset", 20, VideoSelfTests::cutStep);
        SelfTest.clientStep("video: Signal für Drohne mit Brille", 600, VideoSelfTests::signalStep);
        SelfTest.clientStep("video: Störstufen je Signalstärke, Link-Anzeige bei Verlust", 400, VideoSelfTests::breakupStep);
        SelfTest.clientStep("video: Shader-Pass oder Ersatz-Overlay", 240, VideoSelfTests::passStep);
        SelfTest.clientStep("video: Rauschtextur des Ersatz-Overlays", 20, VideoSelfTests::overlayNoiseStep);
        SelfTest.clientStep("video: Shader-Knopf nur nach einem Fehler", 20, VideoSelfTests::retryButtonStep);
        HeadlightSelfTests.register();
        SelfTest.afterLeaveStep("video: Zustand nach dem Verlassen zurückgesetzt", 40, VideoSelfTests::afterLeaveStep);
        ActionCamSelfTests.register();
        RecordingPerfSelfTests.register();
        FfmpegSelfTests.register();
        FlashbackCamSelfTests.register();
        SpectateSelfTests.register();
        ShadowSelfTests.register();
        CameraFovSelfTests.register();
        AspectSelfTests.register();
        VideoTabSelfTests.register();
    }

    private static StepResult retryButtonStep(SelfTest.StepContext ctx) {
        List<AbstractWidget> widgets = new ArrayList<>();
        ConfigTabHost host = new ConfigTabHost() {
            @Override
            public <T extends AbstractWidget> T addWidget(T widget) {
                widgets.add(widget);
                return widget;
            }

            @Override
            public Font font() {
                return Minecraft.getInstance().font;
            }

            @Override
            public Minecraft minecraft() {
                return Minecraft.getInstance();
            }

            @Override
            public Screen screen() {
                return Minecraft.getInstance().gui.screen();
            }

            @Override
            public void rebuild() {
            }

            @Override
            public void changed() {
            }
        };
        VideoState.INSTANCE.overrideTestReceiver(true, VideoSystem.ANALOG);
        try {
            new VideoConfigTab().attach(host, 0, 0, 480, 280);
        } finally {
            VideoState.INSTANCE.overrideTestReceiver(false, null);
        }
        String retry = Component.translatable("propwash.video.config.retry").getString();
        boolean shown = widgets.stream().anyMatch(w -> w.getMessage().getString().equals(retry));
        boolean failed = VideoPass.INSTANCE.failure() != null;
        return StepResult.check(shown == failed, "Knopf „Shader neu versuchen“ sichtbar: " + shown + ", Shader-Fehler: " + failed);
    }

    private static StepResult poseStep(SelfTest.StepContext ctx) {
        VideoState state = VideoState.INSTANCE;
        state.clearPoses();
        long t0 = 5_000_000_000L;
        Quaternionf start = new Quaternionf();
        Quaternionf end = new Quaternionf().rotationY((float) (Math.PI / 2.0));
        state.record(t0, 0.0, 64.0, 0.0, start);
        state.record(t0 + 20 * NANOS_PER_MS, 2.0, 66.0, 4.0, end);
        state.record(t0 + 10 * NANOS_PER_MS, 99.0, 99.0, 99.0, start);
        Vector3d position = new Vector3d();
        Quaternionf orientation = new Quaternionf();
        try {
            if (state.bufferedPoses() != 2) {
                return StepResult.fail("Puffer enthält " + state.bufferedPoses() + " statt 2 Posen (ältere Zeitstempel nicht ignoriert?)");
            }
            if (!state.sample(t0 + 10 * NANOS_PER_MS, position, orientation)) {
                return StepResult.fail("sample() lieferte false trotz gefülltem Puffer");
            }
            Quaternionf expected = new Quaternionf().rotationY((float) (Math.PI / 4.0));
            if (position.distance(1.0, 65.0, 2.0) > 1.0E-6 || Math.abs(Math.abs(orientation.dot(expected)) - 1.0F) > 1.0E-4F) {
                return StepResult.fail("Interpolation falsch: " + position + " " + orientation);
            }
            state.sample(t0 - 1L, position, orientation);
            if (position.distance(0.0, 64.0, 0.0) > 1.0E-9) {
                return StepResult.fail("vor der ältesten Pose nicht auf die älteste geklemmt: " + position);
            }
            state.sample(t0 + 1_000 * NANOS_PER_MS, position, orientation);
            if (position.distance(2.0, 66.0, 4.0) > 1.0E-9) {
                return StepResult.fail("nach der neuesten Pose nicht auf die neueste geklemmt: " + position);
            }
        } finally {
            state.clearPoses();
        }
        return StepResult.check(!state.sample(t0, position, orientation), "sample() nach dem Leeren nicht false");
    }

    private static StepResult cutStep(SelfTest.StepContext ctx) {
        VideoState state = VideoState.INSTANCE;
        state.clearPoses();
        long t0 = 7_000_000_000L;
        Quaternionf level = new Quaternionf();
        Quaternionf turned = new Quaternionf().rotationY((float) Math.PI);
        Vector3d position = new Vector3d();
        Quaternionf orientation = new Quaternionf();
        try {
            state.record(t0, 0.0, 64.0, 0.0, level);
            state.record(t0 + 10 * NANOS_PER_MS, 10.0, 64.0, 0.0, level);
            state.cut(t0 + 15 * NANOS_PER_MS);
            position.set(-1.0, -1.0, -1.0);
            if (state.sample(t0 + 16 * NANOS_PER_MS, position, orientation) || position.x != -1.0) {
                return StepResult.fail("sample() nach dem Schnitt ohne neue Pose lieferte die alte Pose: " + position);
            }
            state.record(t0 + 20 * NANOS_PER_MS, 500.0, 80.0, -300.0, turned);
            state.sample(t0 + 14 * NANOS_PER_MS, position, orientation);
            if (position.distance(10.0, 64.0, 0.0) > 1.0E-9) {
                return StepResult.fail("vor dem Schnitt nicht die letzte alte Pose: " + position);
            }
            state.sample(t0 + 15 * NANOS_PER_MS, position, orientation);
            if (position.distance(500.0, 80.0, -300.0) > 1.0E-9 || Math.abs(Math.abs(orientation.dot(turned)) - 1.0F) > 1.0E-4F) {
                return StepResult.fail("ab dem Schnitt nicht die neue Pose: " + position + " " + orientation);
            }
        } finally {
            state.clearPoses();
        }
        return StepResult.pass();
    }

    private static StepResult signalStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        VideoState state = VideoState.INSTANCE;
        if (ctx.tick() == 0) {
            Scenario created = new Scenario();
            scenario = created;
            created.spawn = ctx.onServer(server -> spawn(server, created));
            return StepResult.pending();
        }
        Scenario s = scenario;
        if (s == null) {
            return StepResult.fail("kein Testaufbau");
        }
        if (s.spawn.isCompletedExceptionally()) {
            return StepResult.fail("Drohne spawnen fehlgeschlagen: " + s.spawn.exceptionNow().getMessage());
        }
        if (!s.spawn.isDone() || minecraft.level == null) {
            return StepResult.pending();
        }
        QuadcopterEntity drone = clientDrone(minecraft, s);
        if (drone == null) {
            return ctx.tick() > 200 ? StepResult.fail("Drohne " + s.droneId + " auf dem Client nicht sichtbar") : StepResult.pending();
        }
        switch (s.phase) {
            case SPAWNING -> {
                if (!s.gogglesEquipped) {
                    ctx.log("Analog-Brille nicht registriert, Empfänger wird simuliert");
                    state.overrideTestReceiver(true, VideoSystem.ANALOG);
                } else if (minecraft.player == null || !ContentIds.wornGogglesSystem(minecraft.player).equals(Optional.of(VideoSystem.ANALOG))) {
                    return ctx.tick() > 200 ? StepResult.fail("Brille im Kopf-Slot kommt auf dem Client nicht an") : StepResult.pending();
                }
                state.attachTestSource(drone);
                s.enter(Phase.CONNECTING, ctx.tick());
                return StepResult.pending();
            }
            case CONNECTING -> {
                if (ctx.tick() - s.phaseTick < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                long latencyMs = state.latencyNanos() / NANOS_PER_MS;
                if (state.status() != VideoState.LinkStatus.CONNECTED || state.system() != VideoSystem.ANALOG
                        || state.receiver() != VideoSystem.ANALOG) {
                    return StepResult.fail("Verbindung falsch: status=" + state.status() + " system=" + state.system() + " brille=" + state.receiver());
                }
                if (state.signalQuality() < 0.9F) {
                    return StepResult.fail("Signal bei " + DRONE_DISTANCE + " Blöcken zu schwach: " + state.signalQuality()
                            + " (Dämpfung " + state.obstructionDb() + " dB, Entfernung " + state.distanceBlocks() + ")");
                }
                if (latencyMs < VideoType.ANALOG_RACE.latencyMs - 1 || latencyMs > VideoType.ANALOG_RACE.latencyMs + 8) {
                    return StepResult.fail("Latenz unplausibel: " + latencyMs + " ms");
                }
                s.clearQuality = state.signalQuality();
                ctx.log("Verbindung steht: Signal " + state.signalQuality() + ", Latenz " + latencyMs + " ms, " + state.raysTraced() + " Strahlen");
                s.wall = ctx.onServer(server -> buildWall(server, s));
                s.enter(Phase.WALL, ctx.tick());
                return StepResult.pending();
            }
            case WALL -> {
                if (s.wall == null || !s.wall.isDone()) {
                    return StepResult.pending();
                }
                if (s.wall.isCompletedExceptionally()) {
                    return StepResult.fail("Wand bauen fehlgeschlagen: " + s.wall.exceptionNow().getMessage());
                }
                if (s.wallBlocks.isEmpty() || minecraft.level.getBlockState(s.wallBlocks.getFirst()).isAir()) {
                    return StepResult.pending();
                }
                if (s.wallSeenTick < 0) {
                    s.wallSeenTick = ctx.tick();
                }
                if (ctx.tick() - s.wallSeenTick < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                if (state.obstructionDb() < 20.0 || state.signalQuality() > 0.5F || state.signalQuality() >= s.clearQuality) {
                    return StepResult.fail("Wand dämpft nicht: " + state.obstructionDb() + " dB, Signal " + state.signalQuality());
                }
                ctx.log("Wand: " + Math.round(state.obstructionDb()) + " dB Dämpfung, Signal " + state.signalQuality());
                s.mismatch = ctx.onServer(server -> {
                    clearWall(server, s);
                    QuadcopterEntity serverDrone = serverDrone(server, s);
                    if (serverDrone != null) {
                        serverDrone.setBuild(new DroneBuild(DroneBuild.RACER.frame(), DroneBuild.RACER.stack(), DroneBuild.RACER.motor(),
                                DroneBuild.RACER.prop(), VideoType.DIGITAL_HD, DroneBuild.RACER.battery()));
                    }
                });
                s.enter(Phase.MISMATCH, ctx.tick());
                return StepResult.pending();
            }
            case MISMATCH -> {
                if (drone.build().video() != VideoType.DIGITAL_HD) {
                    return StepResult.pending();
                }
                if (ctx.tick() - s.phaseTick < 10) {
                    return StepResult.pending();
                }
                if (state.status() != VideoState.LinkStatus.WRONG_SYSTEM || state.system() != null || state.signalQuality() != 0.0F
                        || !state.noSignal()) {
                    return StepResult.fail("Analog-Brille mit Digital-Sender: status=" + state.status() + " system=" + state.system()
                            + " signal=" + state.signalQuality() + " keinSignal=" + state.noSignal());
                }
                s.restore = ctx.onServer(server -> {
                    QuadcopterEntity serverDrone = serverDrone(server, s);
                    if (serverDrone != null) {
                        serverDrone.setBuild(DroneBuild.RACER);
                    }
                });
                s.enter(Phase.DONE, ctx.tick());
                return StepResult.pass();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static final int BREAKUP_CASE_TICKS = 14;
    private static final BreakupCase[] BREAKUP_CASES = {
            new BreakupCase(0.95F, VideoSystem.ANALOG, "Analog klar"),
            new BreakupCase(0.70F, VideoSystem.ANALOG, "Analog Stufe 2"),
            new BreakupCase(0.48F, VideoSystem.ANALOG, "Analog Stufe 3"),
            new BreakupCase(0.25F, VideoSystem.ANALOG, "Analog Stufe 4"),
            new BreakupCase(0.0F, VideoSystem.ANALOG, "Analog Stufe 5"),
            new BreakupCase(0.25F, VideoSystem.DIGITAL, "Digital schwach"),
            new BreakupCase(0.0F, VideoSystem.DIGITAL, "Digital verloren")};

    private static StepResult breakupStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Scenario s = scenario;
        if (s == null || s.phase != Phase.DONE) {
            return StepResult.fail("kein Testaufbau aus dem Signal-Schritt");
        }
        QuadcopterEntity drone = clientDrone(minecraft, s);
        Integer start = (Integer) ctx.state().get("start");
        if (start == null) {
            if (drone == null || drone.build().video() != VideoType.ANALOG_RACE || !VideoState.INSTANCE.active()) {
                return ctx.tick() > 120 ? StepResult.fail("Drohne nicht zurück auf Analog gestellt") : StepResult.pending();
            }
            ctx.state().put("start", ctx.tick());
            return StepResult.pending();
        }
        int elapsed = ctx.tick() - start;
        int index = elapsed / BREAKUP_CASE_TICKS;
        if (index >= BREAKUP_CASES.length) {
            restoreReceiver(s);
            return StepResult.pass();
        }
        BreakupCase current = BREAKUP_CASES[index];
        VideoBreakupProbe.force(current.quality, current.system);
        if (elapsed % BREAKUP_CASE_TICKS != BREAKUP_CASE_TICKS - 1) {
            return StepResult.pending();
        }
        ctx.log(current.name + ": " + VideoBreakupProbe.describe());
        String problem = checkBreakup(index);
        if (problem != null) {
            restoreReceiver(s);
            return StepResult.fail(current.name + ": " + problem + " (" + VideoBreakupProbe.describe() + ")");
        }
        return StepResult.pending();
    }

    private static void restoreReceiver(Scenario s) {
        VideoBreakupProbe.clear();
        if (!s.gogglesEquipped) {
            VideoState.INSTANCE.overrideTestReceiver(true, VideoSystem.ANALOG);
        }
    }

    private static @Nullable String checkBreakup(int index) {
        VideoState state = VideoState.INSTANCE;
        EffectParams p = state.params();
        boolean indicator = VideoBreakupProbe.linkIndicatorShown();
        if (state.status() != VideoState.LinkStatus.CONNECTED) {
            return "nicht verbunden: " + state.status();
        }
        return switch (index) {
            case 0 -> p.noise > 0.05F || p.desaturation > 0.2F || indicator ? "bei starkem Signal nicht sauber" : null;
            case 1 -> VideoBreakupProbe.analogStage() != AnalogBreakup.Stage.GLITCHES || p.noise > 0.05F || p.desaturation > 0.2F
                    ? "Stufe 2 muss farbig und ohne Schnee sein" : null;
            case 2 -> VideoBreakupProbe.analogStage() != AnalogBreakup.Stage.COLOUR_LOSS || p.desaturation < 0.25F || p.noise > 0.1F
                    ? "Stufe 3 muss Farbe verlieren, ohne schon zu rauschen" : null;
            case 3 -> VideoBreakupProbe.analogStage() != AnalogBreakup.Stage.SNOW || p.desaturation < 0.95F || p.noise < 0.15F
                    ? "Stufe 4 muss schwarz-weiß mit Schnee sein" : null;
            case 4 -> VideoBreakupProbe.analogStage() != AnalogBreakup.Stage.STATIC || p.noSignal < 0.999F || p.blackout() || !indicator
                    ? "Stufe 5 muss voller Schnee mit Link-Anzeige sein" : null;
            case 5 -> p.noise > 0.0F || p.blockiness <= 0.0F || p.resolution >= 1.0F
                    ? "digital schwach muss blockig ohne Schnee sein" : null;
            case 6 -> !p.blackout() || !indicator ? "digital verloren muss schwarz mit Link-Anzeige sein" : null;
            default -> null;
        };
    }

    private record BreakupCase(float quality, VideoSystem system, String name) {
    }

    private static StepResult passStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Scenario s = scenario;
        if (s == null || s.phase != Phase.DONE) {
            if (ctx.tick() == 0) {
                cleanup(ctx, s);
            }
            return StepResult.fail("kein Testaufbau aus dem Signal-Schritt");
        }
        QuadcopterEntity drone = clientDrone(minecraft, s);
        if (ctx.tick() == 0) {
            s.framesAtStart = VideoPass.INSTANCE.framesDrawn();
            s.passStartTick = -1;
        }
        if (drone == null || drone.build().video() != VideoType.ANALOG_RACE) {
            if (ctx.tick() > 100) {
                return finish(ctx, s, StepResult.fail("Drohne nicht zurück auf Analog gestellt"));
            }
            return StepResult.pending();
        }
        if (s.passStartTick < 0) {
            s.passStartTick = ctx.tick();
            s.framesAtStart = VideoPass.INSTANCE.framesDrawn();
        }
        VideoPass pass = VideoPass.INSTANCE;
        VideoPass.Failure failure = pass.failure();
        if (failure == VideoPass.Failure.ERROR) {
            return finish(ctx, s, StepResult.fail("Render-Pass warf: " + pass.failureDetail()));
        }
        if (failure == VideoPass.Failure.COMPILE) {
            ctx.log("Pipeline nicht verfügbar, Ersatz-Overlay aktiv: " + pass.overlayFallback());
            return finish(ctx, s, StepResult.check(pass.overlayFallback(), "weder Shader-Pass noch Ersatz-Overlay aktiv"));
        }
        VideoPass.Failure reason = pass.overlayReason(Util.getNanos());
        if (reason == VideoPass.Failure.CONFIG || reason == VideoPass.Failure.SHADER_PACK) {
            ctx.log("Ersatz-Overlay aktiv (" + reason + ")");
            return finish(ctx, s, StepResult.pass());
        }
        int waited = ctx.tick() - s.passStartTick;
        if (pass.framesDrawn() > s.framesAtStart + 5) {
            if (!pass.hookSeen()) {
                return finish(ctx, s, StepResult.fail("Pass zeichnete ohne Hook"));
            }
            ctx.log("Shader-Pass aktiv: " + (pass.framesDrawn() - s.framesAtStart) + " Frames");
            return finish(ctx, s, StepResult.pass());
        }
        if (waited > 120) {
            return finish(ctx, s, StepResult.fail("Shader-Pass zeichnet nicht: hook=" + pass.hookSeen() + " frames=" + pass.framesDrawn()
                    + " grund=" + reason + " aktiv=" + VideoState.INSTANCE.active()));
        }
        return StepResult.pending();
    }

    private static StepResult overlayNoiseStep(SelfTest.StepContext ctx) {
        if (!VideoOverlay.ensureNoiseTexture()) {
            return StepResult.fail("Rauschtextur für das Ersatz-Overlay nicht angelegt");
        }
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(VideoOverlay.NOISE_TEXTURE);
        if (!(texture instanceof DynamicTexture dynamic)) {
            return StepResult.fail("Rauschtextur nicht registriert: " + texture.getClass().getSimpleName());
        }
        NativeImage pixels = dynamic.getPixels();
        if (pixels.getWidth() != VideoOverlay.NOISE_SIZE || pixels.getHeight() != VideoOverlay.NOISE_SIZE) {
            return StepResult.fail("Rauschtextur hat " + pixels.getWidth() + "x" + pixels.getHeight() + " Pixel");
        }
        int darkest = 255;
        int brightest = 0;
        for (int i = 0; i < 256; i++) {
            int pixel = pixels.getPixel(i * 7 % VideoOverlay.NOISE_SIZE, i * 13 % VideoOverlay.NOISE_SIZE);
            int grey = ARGB.green(pixel);
            darkest = Math.min(darkest, grey);
            brightest = Math.max(brightest, grey);
            if (ARGB.alpha(pixel) != 255) {
                return StepResult.fail("Rauschtextur nicht deckend: Alpha " + ARGB.alpha(pixel));
            }
        }
        return StepResult.check(brightest - darkest > 128, "Rauschtextur ohne Kontrast: " + darkest + ".." + brightest);
    }

    private static StepResult afterLeaveStep(SelfTest.StepContext ctx) {
        VideoState state = VideoState.INSTANCE;
        if (state.active()) {
            return StepResult.fail("VideoState nach dem Verlassen noch aktiv");
        }
        if (state.bufferedPoses() != 0) {
            return StepResult.fail("Posen-Puffer nach dem Verlassen nicht leer: " + state.bufferedPoses());
        }
        return StepResult.check(!VideoPass.INSTANCE.holdsResources(), "Render-Ziele des Video-Pass nach dem Verlassen nicht freigegeben");
    }

    private static StepResult finish(SelfTest.StepContext ctx, Scenario s, StepResult result) {
        cleanup(ctx, s);
        return result;
    }

    private static void cleanup(SelfTest.StepContext ctx, @Nullable Scenario s) {
        VideoBreakupProbe.clear();
        VideoState.INSTANCE.attachTestSource(null);
        VideoState.INSTANCE.overrideTestReceiver(false, null);
        scenario = null;
        if (s == null) {
            return;
        }
        ctx.onServer(server -> {
            clearWall(server, s);
            QuadcopterEntity drone = serverDrone(server, s);
            if (drone != null) {
                drone.discard();
            }
            ServerPlayer player = firstPlayer(server);
            if (player != null && s.previousHead != null) {
                player.setItemSlot(EquipmentSlot.HEAD, s.previousHead);
            }
        });
    }

    private static void spawn(MinecraftServer server, Scenario s) {
        ServerPlayer player = firstPlayer(server);
        if (player == null) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        Vec3 eye = player.getEyePosition();
        drone.snapTo(eye.x, eye.y - drone.getBbHeight() * 0.5, eye.z + DRONE_DISTANCE, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.RACER);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        s.droneId = drone.getId();
        s.eye = eye;
        Optional<Item> goggles = ContentIds.item(ContentIds.GOGGLES_ANALOG);
        if (goggles.isPresent()) {
            s.previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(goggles.get()));
            s.gogglesEquipped = true;
        }
    }

    private static void buildWall(MinecraftServer server, Scenario s) {
        ServerPlayer player = firstPlayer(server);
        if (player == null || s.eye == null) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        ServerLevel level = player.level();
        int cx = (int) Math.floor(s.eye.x);
        int cy = (int) Math.floor(s.eye.y);
        int cz = (int) Math.floor(s.eye.z);
        List<BlockPos> placed = new ArrayList<>();
        for (int dz = 3; dz <= 8; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos pos = new BlockPos(cx + dx, cy + dy, cz + dz);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                        placed.add(pos.immutable());
                    }
                }
            }
        }
        BlockPos center = new BlockPos(cx, cy, cz + 5);
        placed.remove(center);
        placed.addFirst(center);
        s.wallBlocks = placed;
    }

    private static void clearWall(MinecraftServer server, Scenario s) {
        ServerPlayer player = firstPlayer(server);
        if (player == null || s.wallBlocks.isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        for (BlockPos pos : s.wallBlocks) {
            if (level.getBlockState(pos).is(Blocks.STONE)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        s.wallBlocks = List.of();
    }

    private static @Nullable ServerPlayer firstPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.getFirst();
    }

    private static @Nullable QuadcopterEntity serverDrone(MinecraftServer server, Scenario s) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(s.droneId);
            if (entity instanceof QuadcopterEntity drone) {
                return drone;
            }
        }
        return null;
    }

    private static @Nullable QuadcopterEntity clientDrone(Minecraft minecraft, Scenario s) {
        if (minecraft.level == null || s.droneId < 0) {
            return null;
        }
        return minecraft.level.getEntity(s.droneId) instanceof QuadcopterEntity drone ? drone : null;
    }

    private enum Phase {
        SPAWNING,
        CONNECTING,
        WALL,
        MISMATCH,
        DONE
    }

    private static final class Scenario {
        private CompletableFuture<Void> spawn = CompletableFuture.completedFuture(null);
        private @Nullable CompletableFuture<Void> wall;
        private @Nullable CompletableFuture<Void> mismatch;
        private @Nullable CompletableFuture<Void> restore;
        private volatile int droneId = -1;
        private volatile @Nullable Vec3 eye;
        private volatile boolean gogglesEquipped;
        private @Nullable ItemStack previousHead;
        private volatile List<BlockPos> wallBlocks = List.of();
        private Phase phase = Phase.SPAWNING;
        private int phaseTick;
        private int wallSeenTick = -1;
        private float clearQuality;
        private long framesAtStart;
        private int passStartTick = -1;

        private void enter(Phase next, int tick) {
            this.phase = next;
            this.phaseTick = tick;
        }
    }
}
