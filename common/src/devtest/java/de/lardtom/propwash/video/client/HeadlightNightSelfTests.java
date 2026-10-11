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
import de.lardtom.propwash.video.HeadlightCheck;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class HeadlightNightSelfTests {
    static final LedSettings LIGHT_ON = new LedSettings(LedMode.STATIC, LedColor.WHITE, 100, true);
    static final LedSettings LIGHT_OFF = LIGHT_ON.withHeadlight(false);
    private static final int OFFSET_X = -48;
    private static final float PITCH = 12.0F;
    private static final int GRID_X = 32;
    private static final int GRID_Y = 18;
    private static final double SAME = 1.0;

    private HeadlightNightSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("video: FPV-Scheinwerfer bei Nacht im Freien hellt nur auf, prüft sein Bild und lässt sich abschalten", 2600,
                HeadlightNightSelfTests::step);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Night s = (Night) ctx.state().computeIfAbsent("night", key -> new Night());
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

    private static StepResult run(SelfTest.StepContext ctx, Night s) {
        Minecraft mc = Minecraft.getInstance();
        Headlight light = Headlight.INSTANCE;
        HeadlightPass pass = HeadlightPass.INSTANCE;
        if (s.pending != null) {
            if (s.pending.isCompletedExceptionally()) {
                return StepResult.fail(s.phase + ": Server-Aktion fehlgeschlagen: " + s.pending.exceptionNow());
            }
            if (!s.pending.isDone()) {
                return StepResult.pending();
            }
            s.pending = null;
        }
        if (s.shot != null) {
            if (!s.shot.isDone()) {
                return StepResult.pending();
            }
            if (s.shot.isCompletedExceptionally()) {
                return StepResult.fail(s.phase + ": Bildschirmfoto nicht lesbar: " + s.shot.exceptionNow());
            }
        }
        if (ctx.tick() > 2500) {
            return StepResult.fail("Zeit abgelaufen in Phase " + s.phase + " (Licht " + light.rawLight() + ", Dunkelheit " + light.darkness()
                    + ", aktiv " + light.active() + ", Shader " + light.usesShaderPass() + ", Prüfungen " + pass.checksPassed() + "/"
                    + pass.checksFailed() + ", letzte " + pass.lastCheck() + ", Fehler " + pass.failureDetail() + ")");
        }
        QuadcopterEntity drone = s.droneId >= 0 && mc.level != null && mc.level.getEntity(s.droneId) instanceof QuadcopterEntity found ? found : null;
        if (mc.player != null && s.phase != Phase.SPAWN) {
            aim(mc.player);
        }
        switch (s.phase) {
            case SPAWN -> {
                s.clouds = mc.options.cloudStatus().get();
                mc.options.cloudStatus().set(CloudStatus.OFF);
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                    s.hudHidden = true;
                }
                s.ledLighting = VideoSettings.get().ledLighting;
                VideoSettings.get().ledLighting = true;
                pass.retryAll();
                s.pending = ctx.onServer(server -> spawn(server, s));
                s.enter(Phase.OFF, ctx.tick());
                return StepResult.pending();
            }
            case OFF -> {
                if (drone == null || mc.player == null || mc.level == null || !LIGHT_OFF.equals(settingsOf(drone))) {
                    return StepResult.pending();
                }
                VideoState.INSTANCE.attachTestSource(drone);
                VideoState.INSTANCE.overrideTestReceiver(true, VideoSystem.CREATIVE);
                boolean placed = s.feet != null && mc.player.position().distanceTo(s.feet) < 0.3;
                boolean night = mc.level.getMaxLocalRawBrightness(drone.blockPosition()) <= 6;
                if (!placed || !night) {
                    s.since = ctx.tick();
                    return StepResult.pending();
                }
                if (ctx.tick() - s.since < 60) {
                    return StepResult.pending();
                }
                if (light.active()) {
                    return StepResult.fail("Scheinwerfer leuchtet, obwohl er in der Drohne aus ist");
                }
                if (s.off == null) {
                    s.shot = capture();
                    s.off = s.shot;
                    return StepResult.pending();
                }
                s.shot = null;
                s.pending = ctx.onServer(server -> setLeds(server, s, LIGHT_ON));
                s.enter(Phase.ON, ctx.tick());
                return StepResult.pending();
            }
            case ON -> {
                if (drone == null || !LIGHT_ON.equals(settingsOf(drone))) {
                    return StepResult.pending();
                }
                if (s.on == null) {
                    if (!light.active() || !light.usesShaderPass()) {
                        return ctx.tick() - s.since > 300 ? StepResult.fail("Scheinwerfer nachts im Freien nicht über den Shader-Weg aktiv (Licht "
                                + light.rawLight() + ", Dunkelheit " + light.darkness() + ", Overlay " + light.usesOverlay() + ")") : StepResult.pending();
                    }
                    if (s.passedAtOn < 0) {
                        s.passedAtOn = pass.checksPassed();
                        s.failedAt = pass.checksFailed();
                        pass.restartChecks();
                        return StepResult.pending();
                    }
                    if (pass.checksFailed() > s.failedAt || pass.failure() != null) {
                        return StepResult.fail("Bildprüfung hält das richtige Bild für kaputt: " + pass.lastCheck() + " " + pass.failureDetail());
                    }
                    if (pass.checksPassed() <= s.passedAtOn) {
                        return StepResult.pending();
                    }
                    HeadlightCheck.Result check = pass.lastCheck();
                    if (check == null || check.verdict() != HeadlightCheck.Verdict.PASS || check.brightened() <= 0) {
                        return StepResult.fail("Bildprüfung ohne aufgehellte Bildpunkte: " + check);
                    }
                    ctx.log("Bildprüfung mit Licht: " + check);
                    s.shot = capture();
                    s.on = s.shot;
                    return StepResult.pending();
                }
                s.shot = null;
                Grid off = s.off.join();
                Grid on = s.on.join();
                StepResult compared = compareLit(ctx, off, on, "LEDs an");
                if (compared.status() == StepResult.Status.FAIL) {
                    return compared;
                }
                VideoSettings.get().ledLighting = false;
                s.framesAt = -1L;
                s.enter(Phase.PERSONAL_OFF, ctx.tick());
                return StepResult.pending();
            }
            case PERSONAL_OFF -> {
                if (s.personal == null) {
                    if (ctx.tick() - s.since < 10) {
                        return StepResult.pending();
                    }
                    if (light.active()) {
                        return StepResult.fail("LED-Ausleuchtung im FPV-Bild ist aus, der Scheinwerfer leuchtet trotzdem");
                    }
                    long frames = pass.framesDrawn() + HeadlightOverlay.framesDrawn();
                    if (s.framesAt < 0L) {
                        s.framesAt = frames;
                        return StepResult.pending();
                    }
                    if (ctx.tick() - s.since < 30) {
                        return StepResult.pending();
                    }
                    if (frames != s.framesAt) {
                        return StepResult.fail("LED-Ausleuchtung aus, aber Shader-Pass oder Overlay zeichnen weiter (" + (frames - s.framesAt) + " Frames)");
                    }
                    s.shot = capture();
                    s.personal = s.shot;
                    return StepResult.pending();
                }
                s.shot = null;
                StepResult same = compareSame(ctx, s.off.join(), s.personal.join(), "LED-Ausleuchtung aus");
                if (same.status() == StepResult.Status.FAIL) {
                    return same;
                }
                VideoSettings.get().ledLighting = true;
                pass.simulateFault(HeadlightPass.Fault.NAN_PROJECTION);
                s.passedAtFault = -1;
                s.enter(Phase.NAN, ctx.tick());
                return StepResult.pending();
            }
            case NAN -> {
                if (s.nan == null) {
                    if (!light.usesShaderPass()) {
                        return ctx.tick() - s.since > 200 ? StepResult.fail("Shader-Weg kommt nach dem Wiedereinschalten nicht zurück") : StepResult.pending();
                    }
                    if (s.passedAtFault < 0) {
                        s.passedAtFault = pass.checksPassed();
                        s.failedAt = pass.checksFailed();
                        pass.restartChecks();
                        return StepResult.pending();
                    }
                    if (pass.failure() != null || pass.checksFailed() > s.failedAt) {
                        return StepResult.fail("NaN in der Projektion macht das Bild kaputt: " + pass.lastCheck() + " " + pass.failureDetail());
                    }
                    if (pass.checksPassed() <= s.passedAtFault) {
                        return StepResult.pending();
                    }
                    ctx.log("Bildprüfung mit NaN-Projektion: " + pass.lastCheck());
                    s.shot = capture();
                    s.nan = s.shot;
                    return StepResult.pending();
                }
                s.shot = null;
                StepResult same = compareSame(ctx, s.off.join(), s.nan.join(), "NaN-Projektion");
                if (same.status() == StepResult.Status.FAIL) {
                    return same;
                }
                pass.simulateFault(HeadlightPass.Fault.BLACK_SCENE);
                pass.restartChecks();
                s.logsAtBroken = pass.failureLogs();
                s.enter(Phase.BROKEN, ctx.tick());
                return StepResult.pending();
            }
            case BROKEN -> {
                if (s.broken == null) {
                    if (pass.failure() == null) {
                        return ctx.tick() - s.since > 300 ? StepResult.fail("Kaputtes Bild wird nicht erkannt: " + pass.lastCheck()) : StepResult.pending();
                    }
                    if (pass.failure() != VideoPass.Failure.CHECK) {
                        return StepResult.fail("falscher Grund beim Abschalten: " + pass.failure() + " " + pass.failureDetail());
                    }
                    if (s.overlayAt < 0) {
                        ctx.log("Bildprüfung mit schwarzem Bild: " + pass.lastCheck() + " → " + pass.failureDetail());
                        pass.retry();
                        if (pass.failure() != VideoPass.Failure.CHECK) {
                            return StepResult.fail("Abschaltung nach der Bildprüfung hält ein erneutes Verbinden nicht aus");
                        }
                        s.overlayAt = HeadlightOverlay.framesDrawn();
                        s.since = ctx.tick();
                        return StepResult.pending();
                    }
                    if (ctx.tick() - s.since < 20) {
                        return StepResult.pending();
                    }
                    if (pass.holdsResources()) {
                        return StepResult.fail("Shader-Weg hält nach dem Abschalten noch Render-Ziele");
                    }
                    if (!light.usesOverlay() || HeadlightOverlay.framesDrawn() - s.overlayAt < 5) {
                        return StepResult.fail("nach dem Abschalten übernimmt die Aufhellung im Overlay nicht (Frames "
                                + (HeadlightOverlay.framesDrawn() - s.overlayAt) + ")");
                    }
                    int logs = pass.failureLogs() - s.logsAtBroken;
                    if (logs != 1) {
                        return StepResult.fail("Abschalten schreibt " + logs + " Logzeilen statt einer");
                    }
                    s.shot = capture();
                    s.broken = s.shot;
                    return StepResult.pending();
                }
                s.shot = null;
                Grid off = s.off.join();
                Grid overlay = s.broken.join();
                double worst = worstDarkening(off, overlay);
                int cell = darkestCell(off, overlay);
                ctx.log(String.format(Locale.ROOT, "Ersatz-Aufhellung nach dem Abschalten: Kegel %.1f → %.1f, stärkste Abdunklung %.2f in Feld %d/%d",
                        off.cone(), overlay.cone(), worst, cell % GRID_X, cell / GRID_X));
                if (worst > SAME) {
                    return StepResult.fail(String.format(Locale.ROOT, "Bild nach dem Abschalten dunkler als ohne Licht (%.1f)", worst));
                }
                if (overlay.cone() < off.cone() + 1.0) {
                    return StepResult.fail(String.format(Locale.ROOT, "Ersatz-Aufhellung wirkt nicht: %.1f → %.1f", off.cone(), overlay.cone()));
                }
                return StepResult.pass();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static StepResult compareLit(SelfTest.StepContext ctx, Grid off, Grid on, String what) {
        double worst = worstDarkening(off, on);
        double sky = skyChange(off, on);
        int cell = darkestCell(off, on);
        ctx.log(String.format(Locale.ROOT, "%s: Kegel %.1f → %.1f, Himmel ±%.2f, stärkste Abdunklung %.2f in Feld %d/%d, Bild %.1f → %.1f", what, off.cone(),
                on.cone(), sky, worst, cell % GRID_X, cell / GRID_X, off.mean(), on.mean()));
        if (worst > SAME) {
            return StepResult.fail(String.format(Locale.ROOT, "%s: Bild stellenweise dunkler als ohne Licht (%.2f)", what, worst));
        }
        if (sky > SAME) {
            return StepResult.fail(String.format(Locale.ROOT, "%s: Himmel verändert sich (%.2f)", what, sky));
        }
        if (on.cone() < off.cone() * 1.5 + 3.0) {
            return StepResult.fail(String.format(Locale.ROOT, "%s: Boden im Kegel kaum heller: %.1f → %.1f", what, off.cone(), on.cone()));
        }
        return StepResult.pass();
    }

    private static StepResult compareSame(SelfTest.StepContext ctx, Grid off, Grid other, String what) {
        double change = 0.0;
        int at = 0;
        for (int i = 0; i < off.cells.length; i++) {
            double delta = Math.abs(other.cells[i] - off.cells[i]);
            if (delta > change) {
                change = delta;
                at = i;
            }
        }
        ctx.log(String.format(Locale.ROOT, "%s: größte Abweichung zum Bild ohne Licht %.2f in Feld %d/%d (%.1f → %.1f)", what, change, at % GRID_X,
                at / GRID_X, off.cells[at], other.cells[at]));
        return StepResult.check(change <= SAME, String.format(Locale.ROOT, "%s: Bild weicht vom Bild ohne Licht ab (%.2f)", what, change));
    }

    private static double worstDarkening(Grid off, Grid on) {
        return off.cells[darkestCell(off, on)] - on.cells[darkestCell(off, on)];
    }

    private static int darkestCell(Grid off, Grid on) {
        int at = 0;
        double worst = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < off.cells.length; i++) {
            double delta = off.cells[i] - on.cells[i];
            if (delta > worst) {
                worst = delta;
                at = i;
            }
        }
        return at;
    }

    private static double skyChange(Grid off, Grid on) {
        double change = 0.0;
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < GRID_X; x++) {
                int i = y * GRID_X + x;
                change = Math.max(change, Math.abs(on.cells[i] - off.cells[i]));
            }
        }
        return change;
    }

    private static void aim(LocalPlayer player) {
        player.setYRot(0.0F);
        player.setXRot(PITCH);
        player.yRotO = 0.0F;
        player.xRotO = PITCH;
        player.setYHeadRot(0.0F);
        player.yHeadRotO = 0.0F;
    }

    private static CompletableFuture<Grid> capture() {
        CompletableFuture<Grid> done = new CompletableFuture<>();
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (NativeImage pixels = image) {
                done.complete(Grid.of(pixels));
            } catch (RuntimeException exception) {
                done.completeExceptionally(exception);
            }
        });
        return done;
    }

    private static LedSettings settingsOf(QuadcopterEntity drone) {
        return LedSettings.of(drone.tune().values());
    }

    private static void spawn(MinecraftServer server, Night s) {
        ServerPlayer player = firstPlayer(server);
        ServerLevel level = player.level();
        s.home = player.position();
        s.homeYaw = player.getYRot();
        s.homePitch = player.getXRot();
        Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
        if (clock.isEmpty()) {
            throw new IllegalStateException("Welt ohne Uhr");
        }
        s.clockTicks = server.clockManager().getInstance(clock.get()).totalTicks();
        server.clockManager().moveToTimeMarker(clock.get(), ClockTimeMarkers.MIDNIGHT);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        BlockPos base = BlockPos.containing(s.home).offset(OFFSET_X, 0, 0);
        Vec3 at = new Vec3(base.getX() + 0.5, s.home.y + 0.1, base.getZ() + 0.5);
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.FREESTYLE.withAccessory(AccessoryType.LED_STRIP));
        drone.setTune(new FlightTune(LIGHT_OFF.values()));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        s.droneId = drone.getId();
        Vec3 feet = new Vec3(at.x, s.home.y, at.z + 1.5);
        s.feet = feet;
        player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), 0.0F, PITCH, true);
    }

    private static void setLeds(MinecraftServer server, Night s, LedSettings settings) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(s.droneId) instanceof QuadcopterEntity drone) {
                drone.setTune(new FlightTune(settings.values()));
            }
        }
    }

    private static void cleanup(SelfTest.StepContext ctx, Night s) {
        if (s.cleaned) {
            return;
        }
        s.cleaned = true;
        Minecraft mc = Minecraft.getInstance();
        HeadlightPass.INSTANCE.simulateFault(HeadlightPass.Fault.NONE);
        HeadlightPass.INSTANCE.retryAll();
        VideoSettings.get().ledLighting = s.ledLighting;
        if (s.clouds != null) {
            mc.options.cloudStatus().set(s.clouds);
        }
        if (s.hudHidden && mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        VideoState.INSTANCE.attachTestSource(null);
        VideoState.INSTANCE.overrideTestReceiver(false, null);
        long ticks = s.clockTicks;
        ctx.onServer(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = s.droneId >= 0 ? level.getEntity(s.droneId) : null;
                if (entity != null) {
                    entity.discard();
                }
            }
            ServerPlayer player = firstPlayer(server);
            ServerLevel level = player.level();
            Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
            if (ticks >= 0L && clock.isPresent()) {
                server.clockManager().setTotalTicks(clock.get(), ticks);
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
        OFF,
        ON,
        PERSONAL_OFF,
        NAN,
        BROKEN
    }

    record Grid(double[] cells) {
        static Grid of(NativeImage image) {
            int width = image.getWidth();
            int height = image.getHeight();
            double[] cells = new double[GRID_X * GRID_Y];
            for (int gy = 0; gy < GRID_Y; gy++) {
                for (int gx = 0; gx < GRID_X; gx++) {
                    int x0 = gx * width / GRID_X;
                    int x1 = (gx + 1) * width / GRID_X;
                    int y0 = gy * height / GRID_Y;
                    int y1 = (gy + 1) * height / GRID_Y;
                    double sum = 0.0;
                    int count = 0;
                    for (int y = y0; y < y1; y += 2) {
                        for (int x = x0; x < x1; x += 2) {
                            int pixel = image.getPixel(x, y);
                            sum += 0.299 * ARGB.red(pixel) + 0.587 * ARGB.green(pixel) + 0.114 * ARGB.blue(pixel);
                            count++;
                        }
                    }
                    cells[gy * GRID_X + gx] = count == 0 ? 0.0 : sum / count;
                }
            }
            return new Grid(cells);
        }

        double cone() {
            double sum = 0.0;
            int count = 0;
            for (int gy = GRID_Y / 2; gy < GRID_Y * 3 / 4; gy++) {
                for (int gx = GRID_X * 3 / 8; gx < GRID_X * 5 / 8; gx++) {
                    sum += this.cells[gy * GRID_X + gx];
                    count++;
                }
            }
            return sum / count;
        }

        double mean() {
            double sum = 0.0;
            for (double cell : this.cells) {
                sum += cell;
            }
            return sum / this.cells.length;
        }
    }

    private static final class Night {
        private Phase phase = Phase.SPAWN;
        private int since;
        private volatile int droneId = -1;
        private volatile @Nullable Vec3 home;
        private volatile @Nullable Vec3 feet;
        private volatile float homeYaw;
        private volatile float homePitch;
        private volatile long clockTicks = -1L;
        private @Nullable CloudStatus clouds;
        private boolean ledLighting = true;
        private boolean hudHidden;
        private @Nullable CompletableFuture<Void> pending;
        private @Nullable CompletableFuture<Grid> shot;
        private @Nullable CompletableFuture<Grid> off;
        private @Nullable CompletableFuture<Grid> on;
        private @Nullable CompletableFuture<Grid> personal;
        private @Nullable CompletableFuture<Grid> nan;
        private @Nullable CompletableFuture<Grid> broken;
        private int passedAtOn = -1;
        private int passedAtFault = -1;
        private int failedAt;
        private int logsAtBroken;
        private long framesAt = -1L;
        private long overlayAt = -1L;
        private boolean cleaned;

        private void enter(Phase next, int tick) {
            this.phase = next;
            this.since = tick;
        }
    }
}
