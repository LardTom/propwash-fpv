package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.led.LedColor;
import de.lardtom.propwash.drone.led.LedMode;
import de.lardtom.propwash.drone.led.LedSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class HeadlightSelfTests {
    private static final int ROOM_OFFSET_X = 40;
    private static final int ROOM_WIDTH = 7;
    private static final int ROOM_HEIGHT = 5;
    private static final int ROOM_LENGTH = 7;
    private static final LedSettings LIGHT_ON = new LedSettings(LedMode.STATIC, LedColor.WHITE, 100, true);
    private static final LedSettings LIGHT_OFF = LIGHT_ON.withHeadlight(false);

    private HeadlightSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("video: FPV-Scheinwerfer bleibt bei Tag aus und hellt im Dunkeln auf", 1400, HeadlightSelfTests::step);
        HeadlightNightSelfTests.register();
        SelfTest.afterLeaveStep("video: FPV-Scheinwerfer nach dem Verlassen zurückgesetzt", 40, ctx -> {
            if (Headlight.INSTANCE.active() || Headlight.INSTANCE.equipped()) {
                return StepResult.fail("Scheinwerfer nach dem Verlassen noch aktiv");
            }
            return StepResult.check(!HeadlightPass.INSTANCE.holdsResources(), "Render-Ziele des Scheinwerfers nach dem Verlassen nicht freigegeben");
        });
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Scenario s = (Scenario) ctx.state().computeIfAbsent("scenario", key -> new Scenario());
        try {
            StepResult result = run(ctx, s);
            if (result.status() != StepResult.Status.PENDING) {
                cleanup(ctx, s);
            }
            return result;
        } catch (RuntimeException exception) {
            cleanup(ctx, s);
            throw exception;
        }
    }

    private static StepResult run(SelfTest.StepContext ctx, Scenario s) {
        Minecraft mc = Minecraft.getInstance();
        VideoState video = VideoState.INSTANCE;
        Headlight light = Headlight.INSTANCE;
        if (s.pending != null) {
            if (s.pending.isCompletedExceptionally()) {
                return StepResult.fail(s.phase + ": Server-Aktion fehlgeschlagen: " + s.pending.exceptionNow());
            }
            if (!s.pending.isDone()) {
                return StepResult.pending();
            }
            s.pending = null;
        }
        if (ctx.tick() > 1350) {
            return StepResult.fail("Zeit abgelaufen in Phase " + s.phase + " (Licht " + light.rawLight() + ", Dunkelheit " + light.darkness()
                    + ", aktiv " + light.active() + ")");
        }
        QuadcopterEntity drone = s.droneId >= 0 && mc.level != null && mc.level.getEntity(s.droneId) instanceof QuadcopterEntity found ? found : null;
        switch (s.phase) {
            case SPAWN -> {
                s.pending = ctx.onServer(server -> spawn(server, s));
                s.enter(Phase.DAYLIGHT, ctx.tick());
                return StepResult.pending();
            }
            case DAYLIGHT -> {
                if (drone == null || !LIGHT_ON.equals(settingsOf(drone))) {
                    return StepResult.pending();
                }
                video.attachTestSource(drone);
                video.overrideTestReceiver(true, VideoSystem.CREATIVE);
                if (ctx.tick() - s.since < 30) {
                    return StepResult.pending();
                }
                if (!light.equipped()) {
                    return StepResult.fail("Scheinwerfer erkennt den LED-Streifen der Drohne nicht");
                }
                if (light.active() || light.darkness() > 0.05F) {
                    return StepResult.fail("Scheinwerfer leuchtet bei Tageslicht (Licht " + light.rawLight() + ", Dunkelheit " + light.darkness() + ")");
                }
                ctx.log("Tageslicht " + light.rawLight() + ": Scheinwerfer aus");
                s.pending = ctx.onServer(server -> buildRoom(server, s));
                s.enter(Phase.DARKEN, ctx.tick());
                return StepResult.pending();
            }
            case DARKEN -> {
                if (drone == null || mc.level == null || mc.player == null) {
                    return StepResult.pending();
                }
                BlockPos at = BlockPos.containing(drone.getX(), drone.getY() + 0.1, drone.getZ());
                boolean dark = mc.level.getMaxLocalRawBrightness(at) <= 1 && mc.level.getMaxLocalRawBrightness(mc.player.blockPosition().above()) <= 1;
                boolean inside = s.room != null && mc.player.position().distanceTo(s.eyeTarget()) < 3.0;
                if (!dark || !inside) {
                    s.since = ctx.tick();
                    return StepResult.pending();
                }
                aim(mc.player, s);
                if (ctx.tick() - s.since < 40) {
                    return StepResult.pending();
                }
                s.pending = ctx.onServer(server -> setLeds(server, s, LIGHT_OFF));
                s.enter(Phase.CAPTURE_OFF, ctx.tick());
                return StepResult.pending();
            }
            case CAPTURE_OFF -> {
                if (drone == null || !LIGHT_OFF.equals(settingsOf(drone)) || mc.player == null) {
                    return StepResult.pending();
                }
                aim(mc.player, s);
                if (s.off == null) {
                    if (ctx.tick() - s.since < 15) {
                        return StepResult.pending();
                    }
                    if (light.active()) {
                        return StepResult.fail("Scheinwerfer leuchtet, obwohl er in der Drohne ausgeschaltet ist");
                    }
                    s.off = capture();
                    return StepResult.pending();
                }
                if (!s.off.isDone()) {
                    return StepResult.pending();
                }
                s.pending = ctx.onServer(server -> setLeds(server, s, LIGHT_ON));
                s.enter(Phase.CAPTURE_ON, ctx.tick());
                return StepResult.pending();
            }
            case CAPTURE_ON -> {
                if (drone == null || !LIGHT_ON.equals(settingsOf(drone)) || mc.player == null) {
                    return StepResult.pending();
                }
                aim(mc.player, s);
                if (!light.active()) {
                    if (ctx.tick() - s.since > 200) {
                        return StepResult.fail("Scheinwerfer bleibt im Dunkeln aus (Licht " + light.rawLight() + ", Dunkelheit " + light.darkness()
                                + ", Stärke " + light.intensity() + ")");
                    }
                    return StepResult.pending();
                }
                if (s.on == null) {
                    if (s.framesAtOn < 0) {
                        s.framesAtOn = HeadlightPass.INSTANCE.framesDrawn() + HeadlightOverlay.framesDrawn();
                        s.since = ctx.tick();
                    }
                    if (ctx.tick() - s.since < 30) {
                        return StepResult.pending();
                    }
                    long drawn = HeadlightPass.INSTANCE.framesDrawn() + HeadlightOverlay.framesDrawn() - s.framesAtOn;
                    if (drawn < 5) {
                        return StepResult.fail("Scheinwerfer aktiv, aber weder Shader-Pass noch Overlay zeichnen (hook=" + HeadlightPass.INSTANCE.hookSeen()
                                + ", Ersatz=" + HeadlightPass.INSTANCE.fallbackNeeded() + ", Fehler=" + HeadlightPass.INSTANCE.failureDetail() + ")");
                    }
                    s.usedOverlay = light.usesOverlay();
                    s.on = capture();
                    return StepResult.pending();
                }
                if (!s.on.isDone()) {
                    return StepResult.pending();
                }
                if (s.usedOverlay) {
                    s.enter(Phase.COMPARE, ctx.tick());
                    return StepResult.pending();
                }
                s.previousRenderer = VideoSettings.get().renderer;
                VideoSettings.get().renderer = VideoConfig.EffectRenderer.OVERLAY;
                s.framesAtOverlay = HeadlightOverlay.framesDrawn();
                s.enter(Phase.CAPTURE_OVERLAY, ctx.tick());
                return StepResult.pending();
            }
            case CAPTURE_OVERLAY -> {
                if (mc.player != null) {
                    aim(mc.player, s);
                }
                if (s.overlay == null) {
                    if (ctx.tick() - s.since < 15) {
                        return StepResult.pending();
                    }
                    if (!light.usesOverlay() || HeadlightOverlay.framesDrawn() - s.framesAtOverlay < 5) {
                        return StepResult.fail("Ersatz-Aufhellung zeichnet nicht, obwohl der Shader-Weg aus ist (Overlay-Frames "
                                + (HeadlightOverlay.framesDrawn() - s.framesAtOverlay) + ")");
                    }
                    s.overlay = capture();
                    return StepResult.pending();
                }
                if (!s.overlay.isDone()) {
                    return StepResult.pending();
                }
                restoreRenderer(s);
                s.enter(Phase.COMPARE, ctx.tick());
                return StepResult.pending();
            }
            case COMPARE -> {
                double off = s.off == null ? -1.0 : s.off.join();
                double on = s.on == null ? -1.0 : s.on.join();
                if (off < 0.0 || on < 0.0) {
                    return StepResult.fail("Bildschirmfoto nicht lesbar (aus " + off + ", an " + on + ")");
                }
                ctx.log(String.format("Helligkeit im Kegel: aus %.1f, an %.1f (%s)", off, on, s.usedOverlay ? "Overlay" : "Shader-Pass"));
                if (on < off * 1.5 + 3.0) {
                    return StepResult.fail(String.format("Scheinwerfer hellt die Höhle kaum auf: aus %.1f, an %.1f", off, on));
                }
                if (s.overlay != null) {
                    double overlay = s.overlay.join();
                    ctx.log(String.format("Ersatz-Aufhellung (Shaderpack-Weg): %.1f", overlay));
                    if (overlay < off * 1.3 + 2.0) {
                        return StepResult.fail(String.format("Ersatz-Aufhellung wirkt nicht: aus %.1f, Overlay %.1f", off, overlay));
                    }
                }
                return StepResult.pass();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static void aim(LocalPlayer player, Scenario s) {
        player.setYRot(0.0F);
        player.setXRot(-8.0F);
        player.yRotO = 0.0F;
        player.xRotO = -8.0F;
        player.setYHeadRot(0.0F);
        player.yHeadRotO = 0.0F;
    }

    private static CompletableFuture<Double> capture() {
        CompletableFuture<Double> done = new CompletableFuture<>();
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (NativeImage pixels = image) {
                done.complete(coneLuma(pixels));
            } catch (RuntimeException exception) {
                done.completeExceptionally(exception);
            }
        });
        return done;
    }

    static double coneLuma(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int x0 = width * 30 / 100;
        int x1 = width * 45 / 100;
        int y0 = height * 25 / 100;
        int y1 = height * 42 / 100;
        double sum = 0.0;
        int count = 0;
        for (int y = y0; y < y1; y += 2) {
            for (int x = x0; x < x1; x += 2) {
                int pixel = image.getPixel(x, y);
                sum += 0.299 * ARGB.red(pixel) + 0.587 * ARGB.green(pixel) + 0.114 * ARGB.blue(pixel);
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private static LedSettings settingsOf(QuadcopterEntity drone) {
        return LedSettings.of(drone.tune().values());
    }

    private static void spawn(MinecraftServer server, Scenario s) {
        ServerPlayer player = firstPlayer(server);
        ServerLevel level = player.level();
        s.home = player.position();
        s.homeYaw = player.getYRot();
        s.homePitch = player.getXRot();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        Vec3 pos = player.position().add(ROOM_OFFSET_X + 1.0, 0.1, 0.5);
        drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.FREESTYLE.withAccessory(AccessoryType.LED_STRIP));
        drone.setTune(new FlightTune(LIGHT_ON.values()));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        s.droneId = drone.getId();
        s.room = BlockPos.containing(pos.x - 1.0, player.getY(), pos.z - 0.5);
    }

    private static void buildRoom(MinecraftServer server, Scenario s) {
        ServerPlayer player = firstPlayer(server);
        ServerLevel level = player.level();
        BlockPos origin = s.room;
        if (origin == null) {
            throw new IllegalStateException("kein Raum");
        }
        for (int x = -1; x <= ROOM_WIDTH; x++) {
            for (int y = -1; y <= ROOM_HEIGHT; y++) {
                for (int z = -1; z <= ROOM_LENGTH; z++) {
                    boolean shell = x < 0 || x == ROOM_WIDTH || y < 0 || y == ROOM_HEIGHT || z < 0 || z == ROOM_LENGTH;
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState wanted = shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    BlockState before = level.getBlockState(pos);
                    if (!before.equals(wanted)) {
                        s.changed.add(new Changed(pos.immutable(), before));
                        level.setBlockAndUpdate(pos, wanted);
                    }
                }
            }
        }
        Vec3 eye = s.eyeTarget();
        player.teleportTo(level, eye.x, eye.y, eye.z, Set.of(), 0.0F, -8.0F, true);
    }

    private static void setLeds(MinecraftServer server, Scenario s, LedSettings settings) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(s.droneId) instanceof QuadcopterEntity drone) {
                drone.setTune(new FlightTune(settings.values()));
            }
        }
    }

    private static void restoreRenderer(Scenario s) {
        if (s.previousRenderer != null) {
            VideoSettings.get().renderer = s.previousRenderer;
            s.previousRenderer = null;
        }
    }

    private static void cleanup(SelfTest.StepContext ctx, Scenario s) {
        if (s.cleaned) {
            return;
        }
        s.cleaned = true;
        restoreRenderer(s);
        VideoState.INSTANCE.attachTestSource(null);
        VideoState.INSTANCE.overrideTestReceiver(false, null);
        ctx.onServer(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = s.droneId >= 0 ? level.getEntity(s.droneId) : null;
                if (entity != null) {
                    entity.discard();
                }
            }
            ServerPlayer player = firstPlayer(server);
            ServerLevel level = player.level();
            for (int i = s.changed.size() - 1; i >= 0; i--) {
                Changed changed = s.changed.get(i);
                level.setBlockAndUpdate(changed.pos(), changed.state());
            }
            if (s.home != null) {
                player.teleportTo(level, s.home.x, s.home.y, s.home.z, Set.of(), s.homeYaw, s.homePitch, true);
            }
        });
    }

    private static ServerPlayer firstPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private enum Phase {
        SPAWN,
        DAYLIGHT,
        DARKEN,
        CAPTURE_OFF,
        CAPTURE_ON,
        CAPTURE_OVERLAY,
        COMPARE
    }

    private record Changed(BlockPos pos, BlockState state) {
    }

    private static final class Scenario {
        private Phase phase = Phase.SPAWN;
        private int since;
        private volatile int droneId = -1;
        private volatile @Nullable BlockPos room;
        private volatile @Nullable Vec3 home;
        private volatile float homeYaw;
        private volatile float homePitch;
        private final List<Changed> changed = new ArrayList<>();
        private @Nullable CompletableFuture<Void> pending;
        private @Nullable CompletableFuture<Double> off;
        private @Nullable CompletableFuture<Double> on;
        private @Nullable CompletableFuture<Double> overlay;
        private boolean usedOverlay;
        private long framesAtOn = -1;
        private long framesAtOverlay;
        private VideoConfig.@Nullable EffectRenderer previousRenderer;
        private boolean cleaned;

        private void enter(Phase next, int tick) {
            this.phase = next;
            this.since = tick;
        }

        private Vec3 eyeTarget() {
            BlockPos origin = this.room;
            if (origin == null) {
                return Vec3.ZERO;
            }
            return new Vec3(origin.getX() + ROOM_WIDTH * 0.5, origin.getY(), origin.getZ() + 1.5);
        }
    }
}
