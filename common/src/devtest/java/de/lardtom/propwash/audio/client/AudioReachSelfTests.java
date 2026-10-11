package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.audio.MotorTimbre;
import de.lardtom.propwash.audio.SoundReach;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class AudioReachSelfTests {
    private static final DroneBuild[] BUILDS = {DroneBuild.WHOOP, DroneBuild.CINEWHOOP, DroneBuild.FREESTYLE, DroneBuild.LONG_RANGE};
    private static final String[] NAMES = {"65-mm-Whoop", "3\" Cinewhoop", "5\" Freestyle", "7\" Long Range"};
    private static final double[][] WINDOWS = {{15.0, 25.0}, {35.0, 65.0}, {60.0, 80.0}, {80.0, 120.0}};
    private static final double NEAR_A = 4.0;
    private static final double NEAR_B = 8.0;
    private static final double SEARCH_TOP = 200.0;
    private static final double BEYOND_FACTOR = 1.5;
    private static final int SEARCH_STEPS = 8;
    private static final int SETTLE_TICKS = 10;
    private static final int MEASURE_TICKS = 6;
    private static final int NEAR_MEASURE_TICKS = 24;
    private static final float[] SPREAD = {0.88F, 0.96F, 1.04F, 1.12F};
    private static final int START_TICKS = 200;
    private static final int AUDIBLE_PEAK = 2;
    private static final String PROBE = "audio.reach";

    private AudioReachSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("audio: Hörweite je Drohnengröße", 3000, AudioReachSelfTests::reachStep);
    }

    private enum Phase { NEAR_A, NEAR_B, SEARCH, BEYOND }

    private static final class Meter implements MotorAudioTap.Listener {
        private int source = -1;
        private long frames;
        private double sumSquares;
        private int peak;

        synchronized void reset(int sourceId) {
            this.source = sourceId;
            this.frames = 0L;
            this.sumSquares = 0.0;
            this.peak = 0;
        }

        @Override
        public synchronized void onPcm(int sourceId, short[] pcm, int count) {
            if (sourceId != this.source) {
                return;
            }
            for (int i = 0; i < count; i++) {
                int v = pcm[i];
                this.sumSquares += (double) v * v;
                this.peak = Math.max(this.peak, Math.abs(v));
            }
            this.frames += count;
        }

        synchronized long frames() {
            return this.frames;
        }

        synchronized int peak() {
            return this.peak;
        }

        synchronized double rms() {
            return this.frames == 0L ? 0.0 : Math.sqrt(this.sumSquares / this.frames);
        }
    }

    private static final class Probe {
        final Meter meter = new Meter();
        final AtomicInteger droneId = new AtomicInteger(-1);
        final StringBuilder report = new StringBuilder();
        int build;
        Phase phase = Phase.NEAR_A;
        @Nullable CompletableFuture<?> future;
        int phaseTick;
        double target;
        double lo;
        double hi;
        int steps;
        double rmsA;
        double rmsB;
        double voiceRange;
        boolean synthWas;
        double volumeWas;
        int limitWas;
        @Nullable String failure;
    }

    private static StepResult reachStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Probe p = (Probe) ctx.state().get(PROBE);
        if (p == null) {
            if (!SoundEngineAccess.engineLoaded(minecraft.getSoundManager())) {
                ctx.log("kein Audiogerät, Hörweiten-Messung übersprungen");
                return StepResult.pass();
            }
            p = new Probe();
            AudioConfig config = AudioConfig.get();
            p.synthWas = config.synthesizer;
            p.volumeWas = config.motorVolume;
            p.limitWas = config.voiceLimit;
            config.synthesizer = true;
            config.motorVolume = 0.8;
            config.voiceLimit = AudioConfig.MAX_VOICES;
            MotorAudioTap.listen(p.meter);
            ctx.state().put(PROBE, p);
            spawn(ctx, p);
            return StepResult.pending();
        }
        if (p.future != null) {
            if (p.future.isCompletedExceptionally()) {
                return finish(ctx, p, StepResult.fail("Server-Schritt fehlgeschlagen: " + p.future.exceptionNow().getMessage()));
            }
            if (!p.future.isDone()) {
                return StepResult.pending();
            }
            p.future = null;
            p.phaseTick = ctx.tick();
        }
        int id = p.droneId.get();
        Entity entity = minecraft.level == null || id < 0 ? null : minecraft.level.getEntity(id);
        if (!(entity instanceof QuadcopterEntity drone)) {
            return ctx.tick() - p.phaseTick > 100 ? finish(ctx, p, StepResult.fail("Drohne kam nicht beim Client an")) : StepResult.pending();
        }
        float hover = (float) MotorTimbre.of(BUILDS[p.build]).hoverOutput();
        drone.setArmed(true);
        drone.setMotorOutputs(hover * SPREAD[0], hover * SPREAD[1], hover * SPREAD[2], hover * SPREAD[3]);
        int since = ctx.tick() - p.phaseTick;
        DroneVoice voice = MotorVoices.voiceOf(id);
        if (p.phase == Phase.NEAR_A && (voice == null || voice.kind != DroneVoice.Kind.SYNTH)) {
            if (since > START_TICKS) {
                return finish(ctx, p, StepResult.fail(NAMES[p.build] + ": kein Live-Motorsound, Stimme=" + MotorVoices.kindOf(id)));
            }
            p.phaseTick = ctx.tick();
            return StepResult.pending();
        }
        if (since == SETTLE_TICKS) {
            p.meter.reset(id);
        }
        boolean near = p.phase == Phase.NEAR_A || p.phase == Phase.NEAR_B;
        if (since < SETTLE_TICKS + (near ? NEAR_MEASURE_TICKS : MEASURE_TICKS)) {
            return StepResult.pending();
        }
        DroneVoice.Kind kind = MotorVoices.kindOf(id);
        double distance = voice == null ? p.target : voice.distance();
        boolean audible = kind == DroneVoice.Kind.SYNTH ? p.meter.frames() > 0L && p.meter.peak() >= AUDIBLE_PEAK
                : kind == DroneVoice.Kind.SAMPLE && voice != null && voice.distanceGain() > 0.0F;
        switch (p.phase) {
            case NEAR_A -> {
                if (p.meter.frames() == 0L) {
                    return finish(ctx, p, StepResult.fail(NAMES[p.build] + ": kein PCM vom Motor-Stream"));
                }
                p.rmsA = p.meter.rms();
                p.phase = Phase.NEAR_B;
                move(ctx, p, NEAR_B);
            }
            case NEAR_B -> {
                p.rmsB = p.meter.rms();
                p.voiceRange = voice == null ? 0.0 : voice.range();
                p.lo = distance;
                p.hi = SEARCH_TOP;
                p.steps = 0;
                p.phase = Phase.SEARCH;
                move(ctx, p, 0.5 * (p.lo + p.hi));
            }
            case SEARCH -> {
                if (audible) {
                    p.lo = distance;
                } else {
                    p.hi = distance;
                }
                p.steps++;
                if (p.steps < SEARCH_STEPS) {
                    move(ctx, p, 0.5 * (p.lo + p.hi));
                } else {
                    p.phase = Phase.BEYOND;
                    move(ctx, p, BEYOND_FACTOR * p.hi);
                }
            }
            case BEYOND -> {
                String result = evaluate(p, kind);
                if (result != null && p.failure == null) {
                    p.failure = result;
                }
                p.build++;
                if (p.build >= BUILDS.length) {
                    ctx.log("Hörweiten (Zuhörer am Boden, Drohne darüber, Schwebegas mit Motoren bei 88–112 %):" + p.report);
                    return finish(ctx, p, p.failure == null ? StepResult.pass() : StepResult.fail(p.failure));
                }
                p.phase = Phase.NEAR_A;
                spawn(ctx, p);
            }
        }
        return StepResult.pending();
    }

    private static @Nullable String evaluate(Probe p, DroneVoice.Kind beyond) {
        String name = NAMES[p.build];
        SoundReach reach = SoundReach.of(BUILDS[p.build]);
        double nearDb = 20.0 * Math.log10(Math.max(1.0e-9, p.rmsB) / Math.max(1.0e-9, p.rmsA));
        double edge = p.lo;
        p.report.append(String.format(Locale.ROOT, "%n  %s: %.1f dB(A) in 1 m, hörbar bis %.1f Blöcke (Stimme %.1f, Modell schwebend %.1f), "
                        + "4 → 8 Blöcke %.1f dB, bei %.0f Blöcken Stimme %s",
                name, reach.levelDb(), edge, p.voiceRange, reach.hoverRange(), nearDb, BEYOND_FACTOR * p.hi, beyond));
        if (nearDb < -7.5 || nearDb > -4.5) {
            return name + ": Pegel von 4 auf 8 Blöcke " + String.format(Locale.ROOT, "%.1f", nearDb) + " dB statt rund −6 dB";
        }
        double[] window = WINDOWS[p.build];
        if (edge < window[0] || edge > window[1]) {
            return name + ": hörbar bis " + String.format(Locale.ROOT, "%.1f", edge) + " Blöcke, erwartet " + window[0] + "–" + window[1];
        }
        if (Math.abs(edge - p.voiceRange) > 0.04 * p.voiceRange + 1.5) {
            return name + ": gemessene Hörweite " + String.format(Locale.ROOT, "%.1f", edge) + " weicht von der Stimme ab ("
                    + String.format(Locale.ROOT, "%.1f", p.voiceRange) + ")";
        }
        if (beyond != DroneVoice.Kind.NONE) {
            return name + ": weit außer Hörweite läuft die Stimme weiter (" + beyond + ")";
        }
        return null;
    }

    private static void spawn(SelfTest.StepContext ctx, Probe p) {
        DroneBuild build = BUILDS[p.build];
        int previous = p.droneId.get();
        p.target = NEAR_A;
        p.droneId.set(-1);
        p.future = ctx.onServer(server -> {
            discard(server, previous);
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("EntityType.create lieferte null");
            }
            Vec3 pos = player.getEyePosition().add(0.0, NEAR_A, 0.0);
            drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            drone.setBuild(build);
            drone.setNoGravity(true);
            if (!level.addFreshEntity(drone)) {
                throw new IllegalStateException("addFreshEntity fehlgeschlagen");
            }
            p.droneId.set(drone.getId());
        });
    }

    private static void move(SelfTest.StepContext ctx, Probe p, double distance) {
        int id = p.droneId.get();
        p.target = distance;
        p.future = ctx.onServer(server -> {
            ServerPlayer player = player(server);
            if (player.level().getEntity(id) instanceof QuadcopterEntity drone) {
                drone.resetTo(player.getEyePosition().add(0.0, distance, 0.0), new Quaternionf());
            }
        });
    }

    private static void discard(MinecraftServer server, int id) {
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity) {
                entity.discard();
            }
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static StepResult finish(SelfTest.StepContext ctx, Probe p, StepResult result) {
        MotorAudioTap.release(p.meter);
        AudioConfig config = AudioConfig.get();
        config.synthesizer = p.synthWas;
        config.motorVolume = p.volumeWas;
        config.voiceLimit = p.limitWas;
        int id = p.droneId.get();
        ctx.onServer(server -> discard(server, id));
        return result;
    }
}
