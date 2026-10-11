package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.video.CameraShake;
import de.lardtom.propwash.video.DisplayLatency;
import de.lardtom.propwash.video.LatencyChain;
import de.lardtom.propwash.video.client.VideoConfig;
import de.lardtom.propwash.video.client.VideoSettings;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FpvFeelSelfTests {
    public static final String PREFIX = "kamera: ";
    public static final String SHOT_DIR = "camera_shake/";
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final double HOVER_HEIGHT = 3.0;
    private static final double LOW_HEIGHT = 1.5;
    private static final double PILLAR_HEIGHT = 0.9;
    private static final double DESCENT_FROM = 12.0;
    private static final int[] SYSTEM_LATENCIES_MS = {12, 26, 30};
    private static final int SPEED_HOVER_TICKS = 40;
    private static final int SPEED_SETTLE_TICKS = 24;
    private static final int SPEED_RUN_TICKS = 40;
    private static final double SPEED_MIN = 18.0;
    private static final double SPEED_MIN_RATIO = 2.5;
    private static final double SPEED_SHAKE_RATIO = 1.6;
    private static final double SPEED_SHAKE_RMS_DEG = 0.03;
    private static final double SPEED_SHAKE_MAX_DEG = 0.08;
    private static final double SPEED_TIMING_MEAN_MS = 0.08;
    private static final double SPEED_TIMING_MAX_MS = 0.2;
    private static final double SPEED_RATE_RMS_DPS = 1.5;
    private static final double BENT_SHAKE_RATIO = 2.5;
    private static final double BENT_HOVER_RMS_DEG = 0.035;
    private static final String WEAR = "wear";
    private static final PartWear LIGHT_WEAR = PartWear.PRISTINE.withProps(new float[]{0.95F, 0.95F, 0.95F, 0.95F})
            .withMotor(0, 0.97F).withMotor(1, 0.97F).withMotor(2, 0.97F).withMotor(3, 0.97F);
    private static final PartWear BENT_PROP = PartWear.PRISTINE.withProp(PartWear.FRONT_LEFT, 0.5F);
    private static final SpeedProbe SPEED_PROBE = new SpeedProbe();
    private static final Quaternionf BODY = new Quaternionf();
    private static final Quaternionf EXPECTED = new Quaternionf();
    private static final Vector3d COM = new Vector3d();

    private static volatile int droneId = -1;
    private static volatile double groundY;
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;
    private static final List<BlockPos> WALL = new CopyOnWriteArrayList<>();

    private FpvFeelSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + "Testdrohne im FPV (Freestyle, HD-Videomodul)", 400, FpvFeelSelfTests::startStep);
        SelfTest.clientStep(PREFIX + "Latenzkette Knüppel bis Bild gemessen, Videolatenz genau einmal", 900, FpvFeelSelfTests::latencyStep);
        SelfTest.clientStep(PREFIX + "Vibration nur im Bild, mit Drehzahl, Gasstoß und Prop-Wash", 1200, FpvFeelSelfTests::shakeStep);
        SelfTest.clientStep(PREFIX + "Treffer und Laufwackeln des Piloten kippen das FPV-Bild nicht", 200, FpvFeelSelfTests::steadyStep);
        if (Screenshots.enabled()) {
            SelfTest.clientStep("bildschirmfotos: Kamera-Vibration aus und an (Bildfolge)", 900, FpvFeelSelfTests::shotStep);
        }
        SelfTest.clientStep(PREFIX + "bei hohem Tempo bleibt das Bild ruhig (Vollgas, Motorzittern und Posen-Zeit)", 1500, FpvFeelSelfTests::speedStep);
        SelfTest.clientStep(PREFIX + "leicht abgenutzte Teile (ab 90 %) lassen das Bild bei Vollgas ruhig", 1800, ctx -> speedStep(ctx, LIGHT_WEAR, true));
        SelfTest.clientStep(PREFIX + "ein verbogener Prop ist bei Vollgas im Bild zu sehen, im Schweben kaum", 1800, ctx -> speedStep(ctx, BENT_PROP, false));
        SelfTest.clientStep(PREFIX + "Testdrohne freigegeben", 200, FpvFeelSelfTests::releaseStep);
        ClientBus.FRAME_START.register((mc, delta) -> SPEED_PROBE.frame());
    }

    private static StepResult startStep(SelfTest.StepContext ctx) {
        setShake(0.0F);
        if (!ctx.state().containsKey(FUTURE)) {
            PilotSession.INSTANCE.clearTestInput();
            ctx.state().put(FUTURE, ctx.onServer(FpvFeelSelfTests::spawnAndFly));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Drohne nicht gestartet: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        if (drone != null && drone.getId() == droneId && session.isFpv() && session.sim() != null && VideoState.INSTANCE.active()) {
            if (drone.build().video() != VideoType.DIGITAL_HD) {
                return StepResult.fail("Freestyle-Testdrohne hat nicht das HD-Videomodul: " + drone.build().video());
            }
            return StepResult.pass();
        }
        return ctx.tick() > 300 ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
    }

    private static StepResult latencyStep(SelfTest.StepContext ctx) {
        StepResult flying = requireFlight();
        if (flying != null) {
            return flying;
        }
        int phase = (int) ctx.state().getOrDefault(PHASE, 0);
        VideoState video = VideoState.INSTANCE;
        LatencyChain chain = video.latencyChain();
        if (phase == 0) {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, true, StabMode.ANGLE);
            ctx.state().putIfAbsent(SINCE, ctx.tick());
            if (ctx.tick() - (int) ctx.state().get(SINCE) < 10) {
                return StepResult.pending();
            }
            ctx.state().put(PHASE, 1);
            ctx.state().put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        hold(HOVER_HEIGHT, 0.0F);
        int since = ctx.tick() - (int) ctx.state().get(SINCE);
        if (phase == 1) {
            if (since < 60) {
                return StepResult.pending();
            }
            chain.reset();
            ctx.state().put(PHASE, 2);
            ctx.state().put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        if (since < 80) {
            return StepResult.pending();
        }
        Telemetry flight = telemetry();
        if (flight == null || !flight.armed || flight.grounded) {
            return StepResult.fail("Testdrohne nicht in der Luft (scharf " + (flight != null && flight.armed) + ", Höhe " + height(flight) + " m)");
        }
        DisplayLatency display = video.displayLatency();
        ctx.log("Latenzkette (Mittel/Max in ms): " + chain.summary());
        ctx.log(String.format(Locale.ROOT, "Darstellungsweg: Pipeline %.2f ms + Bildaufbau %.2f ms (%.0f Hz) = Ausgleich %.2f ms",
                display.pipelineNanos() * 1.0E-6, display.scanoutNanos() * 1.0E-6, display.refreshHz(), display.compensationNanos() * 1.0E-6));
        double input = chain.meanMs(LatencyChain.Part.INPUT_AGE);
        double pending = chain.meanMs(LatencyChain.Part.SIM_PENDING);
        double pipeline = chain.meanMs(LatencyChain.Part.PIPELINE);
        double scanout = chain.meanMs(LatencyChain.Part.SCANOUT);
        double compensation = display.compensationNanos() * 1.0E-6;
        for (int ms : SYSTEM_LATENCIES_MS) {
            double before = input + pending + ms + pipeline + scanout;
            double applied = Math.max(0.0, ms - compensation);
            double after = input + Math.max(pending, applied) + pipeline + scanout;
            ctx.log(String.format(Locale.ROOT, "Videosystem %d ms: vorher %.1f ms, nachher %.1f ms (Videoverzögerung im Spiel %.1f ms)", ms, before, after, applied));
        }
        if (chain.frames() < 40) {
            return StepResult.fail("zu wenige gemessene Bilder: " + chain.frames());
        }
        double system = chain.meanMs(LatencyChain.Part.VIDEO_SYSTEM);
        if (Math.abs(system - VideoType.DIGITAL_HD.latencyMs) > 0.5) {
            return StepResult.fail(String.format(Locale.ROOT, "Videolatenz des HD-Moduls %.2f ms statt %d ms", system, VideoType.DIGITAL_HD.latencyMs));
        }
        if (chain.maxMs(LatencyChain.Part.SIM_PENDING) >= DroneSim.SUBSTEP_NANOS * 1.0E-6) {
            return StepResult.fail(String.format(Locale.ROOT, "Sim-Rest %.3f ms über einem Substep", chain.maxMs(LatencyChain.Part.SIM_PENDING)));
        }
        double applied = chain.meanMs(LatencyChain.Part.VIDEO_APPLIED);
        if (applied > system - scanout + 1.0E-3) {
            return StepResult.fail(String.format(Locale.ROOT, "Videoverzögerung im Spiel %.2f ms zieht den Bildaufbau (%.2f ms) nicht ab", applied, scanout));
        }
        if (!(pipeline > 0.0)) {
            return StepResult.fail("Darstellungsweg nicht gemessen");
        }
        if (!(chain.meanMs(LatencyChain.Part.TOTAL) < chain.meanMs(LatencyChain.Part.TOTAL_UNCOMPENSATED))) {
            return StepResult.fail("Gesamtlatenz nicht kleiner als ohne Ausgleich");
        }
        long frame = PilotSession.INSTANCE.physicsFrameNanos();
        long expected = frame - display.applied(video.latencyNanosAt(frame));
        if (Math.abs(video.lastSampleNanos() - expected) > 2_000_000L) {
            return StepResult.fail("Kamera zeigt die Pose von " + (frame - video.lastSampleNanos()) / 1.0E6 + " ms statt " + (frame - expected) / 1.0E6 + " ms");
        }
        return StepResult.pass();
    }

    private static StepResult shakeStep(SelfTest.StepContext ctx) {
        StepResult flying = requireFlight();
        if (flying != null) {
            setShake(0.0F);
            return flying;
        }
        int phase = (int) ctx.state().getOrDefault(PHASE, 0);
        int since = ctx.tick() - (int) ctx.state().getOrDefault(SINCE, ctx.tick());
        Telemetry t = telemetry();
        switch (phase) {
            case 0 -> {
                setShake(0.0F);
                hold(HOVER_HEIGHT, 0.0F);
                return next(ctx, 1);
            }
            case 1 -> {
                hold(HOVER_HEIGHT, 0.0F);
                if (since >= 10) {
                    accumulate(ctx, "off");
                }
                if (since < 40) {
                    return StepResult.pending();
                }
                double off = maxDeviation(ctx, "off");
                ctx.log(String.format(Locale.ROOT, "Vibration 0 %%: größte Abweichung Kamera zu Rahmen %.5f°", off));
                if (off > 1.0E-3) {
                    return fail(String.format(Locale.ROOT, "Kamera weicht bei 0 %% um %.4f° vom Rahmen ab", off));
                }
                setShake(CameraShake.DEFAULT_INTENSITY);
                return next(ctx, 2);
            }
            case 2 -> {
                hold(HOVER_HEIGHT, 0.0F);
                if (since >= 10) {
                    accumulate(ctx, "hover");
                }
                if (since < 50) {
                    return StepResult.pending();
                }
                return next(ctx, 3);
            }
            case 3 -> {
                PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 1.0F), true, StabMode.ANGLE);
                if (since >= 1) {
                    accumulate(ctx, "punch");
                }
                if (since < 10) {
                    return StepResult.pending();
                }
                return next(ctx, 4);
            }
            case 4 -> {
                hold(DESCENT_FROM, 0.0F);
                if (since < 240 && !settled(t, DESCENT_FROM)) {
                    return StepResult.pending();
                }
                return next(ctx, 5);
            }
            case 5 -> {
                boolean catching = since >= 20 || height(t) < HOVER_HEIGHT + 3.0;
                float stick = (float) ((catching ? 1.0 : 0.15) * hoverStick(t));
                PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, stick), true, StabMode.ANGLE);
                accumulate(ctx, "descent");
                ctx.state().merge("wash", t == null ? 0.0 : Math.max(t.propWash, t.propWashVrs), (a, b) -> Math.max((double) a, (double) b));
                ctx.state().merge("buffet", FpvShake.INSTANCE.model().buffetLevel(), (a, b) -> Math.max((double) a, (double) b));
                ctx.state().merge("sink", t == null ? 0.0 : -t.verticalSpeed, (a, b) -> Math.max((double) a, (double) b));
                if (since < 36 && height(t) > HOVER_HEIGHT) {
                    return StepResult.pending();
                }
                ctx.log(String.format(Locale.ROOT, "Sinkflug von %.1f m: schnellstes Sinken %.1f m/s, Prop-Wash bis %.2f, Buffet bis %.3f°", DESCENT_FROM,
                        (double) ctx.state().getOrDefault("sink", 0.0), (double) ctx.state().getOrDefault("wash", 0.0),
                        (double) ctx.state().getOrDefault("buffet", 0.0)));
                QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                if (drone != null) {
                    double x = Math.floor(drone.getX()) + 0.5;
                    double z = Math.floor(drone.getZ()) + 0.5;
                    ctx.state().put("probeX", x);
                    ctx.state().put("probeZ", z);
                    BlockPos beside = BlockPos.containing(x + 1.0, groundY, z);
                    ctx.state().put("pillar", beside);
                    ctx.onServer(server -> placeBlocks(server, beside, 0, 2, true, false));
                }
                return next(ctx, 6);
            }
            case 6 -> {
                hold(HOVER_HEIGHT, 0.0F);
                QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                BlockPos pillar = (BlockPos) ctx.state().get("pillar");
                if (drone == null || pillar == null) {
                    return StepResult.fail("keine Drohne für die Enge-Prüfung");
                }
                if (drone.level().getBlockState(pillar).isAir()) {
                    return since < 100 ? StepResult.pending() : StepResult.fail("Säule für die Enge-Prüfung nicht gesetzt");
                }
                double x = (double) ctx.state().get("probeX");
                double z = (double) ctx.state().get("probeZ");
                int wheelbase = drone.build().frame().wheelbaseMm;
                double near = FpvShake.INSTANCE.probe(drone.level(), x, groundY + PILLAR_HEIGHT, z, wheelbase);
                double free = FpvShake.INSTANCE.probe(drone.level(), x, groundY + PILLAR_HEIGHT + 3.0, z, wheelbase);
                ctx.state().put("confinement", near);
                ctx.state().put("free", free);
                ctx.state().put("wallBuffet", CameraShake.buffetDegrees(1.0, 0.0, near));
                ctx.onServer(FpvFeelSelfTests::clearBlocks);
                return next(ctx, 7);
            }
            default -> {
                hold(HOVER_HEIGHT, 0.0F);
                if (since < 30) {
                    return StepResult.pending();
                }
                double hover = rms(ctx, "hover");
                double punch = rms(ctx, "punch");
                double descent = rms(ctx, "descent");
                double wash = (double) ctx.state().getOrDefault("wash", 0.0);
                double buffet = (double) ctx.state().getOrDefault("buffet", 0.0);
                double confinement = (double) ctx.state().getOrDefault("confinement", 0.0);
                double free = (double) ctx.state().getOrDefault("free", -1.0);
                double wallBuffet = (double) ctx.state().getOrDefault("wallBuffet", 0.0);
                ctx.log(String.format(Locale.ROOT, "Vibration 100 %%: RMS Schweben %.4f°, Gasstoß %.4f°, Sinkflug %.4f° (Prop-Wash bis %.2f, Buffet bis %.3f°); Enge neben der Säule %.2f (Buffet bei Schwebedrehzahl %.3f°), frei %.2f",
                        hover, punch, descent, wash, buffet, confinement, wallBuffet, free));
                setShake(0.0F);
                if (!(confinement >= 0.25) || !(wallBuffet > 0.01)) {
                    return StepResult.fail(String.format(Locale.ROOT, "neben der Säule keine Enge erkannt (%.2f, Buffet %.4f°)", confinement, wallBuffet));
                }
                if (free != 0.0) {
                    return StepResult.fail(String.format(Locale.ROOT, "Enge %.2f über der Säule im Freien", free));
                }
                if (!(descent > hover) || !(buffet > 0.05)) {
                    return StepResult.fail(String.format(Locale.ROOT, "Sinkflug in den eigenen Prop-Wash ohne Buffet (RMS %.4f°, Buffet %.3f°, Prop-Wash %.2f)", descent, buffet, wash));
                }
                if (!(hover > 0.002)) {
                    return StepResult.fail(String.format(Locale.ROOT, "keine Vibration im Schwebeflug (%.5f°)", hover));
                }
                if (hover > CameraShake.MAX_TOTAL_DEG) {
                    return StepResult.fail(String.format(Locale.ROOT, "Vibration im Schwebeflug zu groß (%.3f°)", hover));
                }
                if (!(punch > 1.5 * hover)) {
                    return StepResult.fail(String.format(Locale.ROOT, "Gasstoß (%.4f°) nicht deutlich stärker als Schweben (%.4f°)", punch, hover));
                }
                if (wash > 0.05 && !(buffet > 0.0)) {
                    return StepResult.fail("Prop-Wash ohne Buffet im Bild");
                }
                return StepResult.pass();
            }
        }
    }

    private static StepResult shotStep(SelfTest.StepContext ctx) {
        StepResult flying = requireFlight();
        if (flying != null) {
            setShake(0.0F);
            return flying;
        }
        int phase = (int) ctx.state().getOrDefault(PHASE, 0);
        int since = ctx.tick() - (int) ctx.state().getOrDefault(SINCE, ctx.tick());
        Minecraft mc = Minecraft.getInstance();
        if (phase == 0) {
            setShake(0.0F);
            Screenshots.clearOverlays(mc);
            QuadcopterEntity drone = PilotSession.INSTANCE.drone();
            if (drone != null) {
                Vector3d forward = new Vector3d(0.0, 0.0, -1.0);
                drone.orientation().transform(forward);
                boolean alongZ = Math.abs(forward.x) > Math.abs(forward.z);
                double sign = alongZ ? Math.signum(forward.x) : Math.signum(forward.z);
                BlockPos wall = alongZ ? BlockPos.containing(drone.getX() + 8.0 * sign, groundY, drone.getZ())
                        : BlockPos.containing(drone.getX(), groundY, drone.getZ() + 8.0 * sign);
                ctx.onServer(server -> placeBlocks(server, wall, 5, 6, !alongZ, true));
            }
            return next(ctx, 1);
        }
        if (phase == 1) {
            hold(LOW_HEIGHT, 0.0F);
            boolean settled = since >= 40 && Math.abs(height(telemetry()) - LOW_HEIGHT) < 0.3;
            return !settled && since < 300 ? StepResult.pending() : next(ctx, 2);
        }
        String[] names = {"off_1", "off_2", "off_3", "on_1", "on_2", "on_3", "on_punch_1", "on_punch_2"};
        int shot = phase - 2;
        if (shot >= names.length) {
            setShake(0.0F);
            hold(HOVER_HEIGHT, 0.0F);
            return StepResult.pass();
        }
        boolean on = names[shot].startsWith("on");
        boolean punch = names[shot].contains("punch");
        setShake(on ? CameraShake.DEFAULT_INTENSITY : 0.0F);
        if (punch) {
            PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 1.0F), true, StabMode.ANGLE);
        } else {
            hold(LOW_HEIGHT, 0.0F);
        }
        String key = "shot" + shot;
        if (!ctx.state().containsKey(key)) {
            if (since < (shot == 3 ? 8 : shot == 6 ? 2 : 1)) {
                return StepResult.pending();
            }
            ctx.state().put("dev" + shot, deviation());
            ctx.state().put(key, Screenshots.capture(SHOT_DIR + "fpv_camera_shake_" + names[shot]));
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<String> future = (CompletableFuture<String>) ctx.state().get(key);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            setShake(0.0F);
            return StepResult.fail("Bildschirmfoto " + names[shot] + ": " + future.exceptionNow());
        }
        ctx.log(String.format(Locale.ROOT, "Bildschirmfoto %s: Kamera %.4f° neben dem Rahmen, Höhe %.2f m, am Boden %s", names[shot],
                (double) ctx.state().get("dev" + shot), height(telemetry()), telemetry() != null && telemetry().grounded));
        return next(ctx, phase + 1);
    }

    private static StepResult steadyStep(SelfTest.StepContext ctx) {
        StepResult flying = requireFlight();
        if (flying != null) {
            return flying;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        hold(HOVER_HEIGHT, 0.0F);
        mc.player.hurtDuration = 10;
        mc.player.hurtTime = 10;
        if (ctx.tick() < 3) {
            return StepResult.pending();
        }
        CameraEntityRenderState entity = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.entityRenderState;
        float hurt = entity.hurtTime;
        float bob = entity.bob;
        mc.player.hurtTime = 0;
        if (hurt >= 0.0F || bob != 0.0F) {
            return StepResult.fail(String.format(Locale.ROOT, "FPV-Bild übernimmt Treffer-Neigung %.2f oder Laufwackeln %.3f des Piloten", hurt, bob));
        }
        ctx.log(String.format(Locale.ROOT, "Pilot getroffen (hurtTime 10), FPV-Kamera: Neigung aus (%.1f), Wackeln %.1f", hurt, bob));
        return StepResult.pass();
    }

    private static StepResult speedStep(SelfTest.StepContext ctx) {
        return speedStep(ctx, PartWear.PRISTINE, true);
    }

    private static StepResult speedStep(SelfTest.StepContext ctx, PartWear wear, boolean calm) {
        StepResult flying = requireFlight();
        if (flying != null) {
            SPEED_PROBE.active = false;
            setShake(0.0F);
            return flying;
        }
        DroneSim sim = PilotSession.INSTANCE.sim();
        if (!ctx.state().containsKey(WEAR)) {
            ctx.state().put(WEAR, ctx.onServer(server -> setWear(server, wear)));
            return StepResult.pending();
        }
        CompletableFuture<?> worn = (CompletableFuture<?>) ctx.state().get(WEAR);
        if (worn.isCompletedExceptionally()) {
            return fail("Verschleiß nicht gesetzt: " + worn.exceptionNow().getMessage());
        }
        if (!worn.isDone() || sim == null || !wear.equals(sim.wear())) {
            return ctx.tick() > 200 ? fail("Verschleiß kam nicht bei der Sim des Piloten an") : StepResult.pending();
        }
        int phase = (int) ctx.state().getOrDefault(PHASE, -1);
        int since = ctx.tick() - (int) ctx.state().getOrDefault(SINCE, ctx.tick());
        Telemetry t = telemetry();
        switch (phase) {
            case -1 -> {
                if (t != null && t.armed) {
                    return next(ctx, 0);
                }
                PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, true, StabMode.ANGLE);
                ctx.state().putIfAbsent(SINCE, ctx.tick());
                if (since > 100) {
                    return fail("Testdrohne schaltet nicht scharf");
                }
                return StepResult.pending();
            }
            case 0 -> {
                setShake(CameraShake.DEFAULT_INTENSITY);
                hold(HOVER_HEIGHT, 0.0F, height(t) > HOVER_HEIGHT + 8.0 ? 8.0 : 3.0);
                if (since < 900 && !(since >= 30 && settled(t, HOVER_HEIGHT))) {
                    ctx.state().putIfAbsent(SINCE, ctx.tick());
                    return StepResult.pending();
                }
                if (!settled(t, HOVER_HEIGHT)) {
                    return fail(String.format(Locale.ROOT, "Testdrohne schwebt nicht auf %.1f m (Höhe %.1f m)", HOVER_HEIGHT, height(t)));
                }
                SPEED_PROBE.start();
                return next(ctx, 1);
            }
            case 1 -> {
                hold(HOVER_HEIGHT, 0.0F);
                if (since < SPEED_HOVER_TICKS) {
                    return StepResult.pending();
                }
                ctx.state().put("hover", SPEED_PROBE.stop());
                return next(ctx, 2);
            }
            case 2 -> {
                PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 1.0F), true, StabMode.ANGLE);
                if (since < SPEED_SETTLE_TICKS) {
                    return StepResult.pending();
                }
                SPEED_PROBE.start();
                return next(ctx, 3);
            }
            case 3 -> {
                PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 1.0F), true, StabMode.ANGLE);
                if (since < SPEED_RUN_TICKS) {
                    return StepResult.pending();
                }
                ctx.state().put("fast", SPEED_PROBE.stop());
                return next(ctx, 4);
            }
            default -> {
                PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) hoverStick(t)), true, StabMode.ANGLE);
                setShake(0.0F);
                SpeedStats hover = (SpeedStats) ctx.state().get("hover");
                SpeedStats fast = (SpeedStats) ctx.state().get("fast");
                ctx.log("Schweben: " + hover.summary());
                ctx.log("Vollgas:  " + fast.summary());
                if (hover.frames < 60 || fast.frames < 60) {
                    return StepResult.fail("zu wenige gemessene Bilder: Schweben " + hover.frames + ", Vollgas " + fast.frames);
                }
                if (fast.meanSpeed() < SPEED_MIN || fast.meanRatio() < SPEED_MIN_RATIO) {
                    return StepResult.fail(String.format(Locale.ROOT, "nicht schnell genug für die Prüfung: %.1f m/s, Drehzahl %.2f × Schweben",
                            fast.meanSpeed(), fast.meanRatio()));
                }
                if (!(hover.shakeRms() > 0.002)) {
                    return StepResult.fail(String.format(Locale.ROOT, "keine Vibration im Schwebeflug (%.5f°)", hover.shakeRms()));
                }
                if (!calm) {
                    if (hover.shakeRms() > BENT_HOVER_RMS_DEG) {
                        return StepResult.fail(String.format(Locale.ROOT, "beschädigtes Teil schüttelt schon im Schweben mit %.4f° RMS", hover.shakeRms()));
                    }
                    if (fast.shakeRms() < BENT_SHAKE_RATIO * hover.shakeRms()) {
                        return StepResult.fail(String.format(Locale.ROOT, "beschädigtes Teil bei Vollgas nicht zu sehen: %.4f° RMS, im Schweben %.4f°",
                                fast.shakeRms(), hover.shakeRms()));
                    }
                    return StepResult.pass();
                }
                if (fast.shakeRms() > Math.max(SPEED_SHAKE_RATIO * hover.shakeRms(), SPEED_SHAKE_RMS_DEG)) {
                    return StepResult.fail(String.format(Locale.ROOT, "Kamera zittert bei Vollgas mit %.4f° RMS, im Schweben %.4f° (Faktor %.1f)",
                            fast.shakeRms(), hover.shakeRms(), fast.shakeRms() / hover.shakeRms()));
                }
                if (fast.shakeMax > SPEED_SHAKE_MAX_DEG) {
                    return StepResult.fail(String.format(Locale.ROOT, "Kamera weicht bei Vollgas um bis zu %.3f° vom Rahmen ab", fast.shakeMax));
                }
                if (fast.timingMeanMs() > SPEED_TIMING_MEAN_MS || fast.timingMaxMs() > SPEED_TIMING_MAX_MS) {
                    return StepResult.fail(String.format(Locale.ROOT, "gezeigte Pose liegt im Mittel %.3f ms, höchstens %.3f ms neben ihrer Zeit (%.1f mm bei %.1f m/s)",
                            fast.timingMeanMs(), fast.timingMaxMs(), fast.alongMax * 1000.0, fast.meanSpeed()));
                }
                if (fast.rateRms() > SPEED_RATE_RMS_DPS) {
                    return StepResult.fail(String.format(Locale.ROOT, "Drohne schwingt bei Vollgas: Drehrate der gezeigten Pose %.2f °/s RMS", fast.rateRms()));
                }
                return StepResult.pass();
            }
        }
    }

    private static void setWear(MinecraftServer server, PartWear wear) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(droneId) instanceof QuadcopterEntity drone) {
                drone.setWear(wear);
                return;
            }
        }
        throw new IllegalStateException("Testdrohne nicht auf dem Server");
    }

    private static StepResult releaseStep(SelfTest.StepContext ctx) {
        SPEED_PROBE.active = false;
        setShake(0.0F);
        if (!ctx.state().containsKey(FUTURE)) {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            ctx.state().put(FUTURE, ctx.onServer(FpvFeelSelfTests::release));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        PilotSession.INSTANCE.clearTestInput();
        return future.isCompletedExceptionally() ? StepResult.fail("Freigabe: " + future.exceptionNow()) : StepResult.pass();
    }

    private static StepResult next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult fail(String message) {
        setShake(0.0F);
        return StepResult.fail(message);
    }

    private static @Nullable StepResult requireFlight() {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        if (drone == null || drone.getId() != droneId || !session.isFpv() || session.sim() == null) {
            return StepResult.fail("Testdrohne nicht im FPV (Schritt „Testdrohne im FPV“ mit auswählen)");
        }
        return null;
    }

    private static @Nullable Telemetry telemetry() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        return sim == null ? null : sim.telemetry();
    }

    private static double height(@Nullable Telemetry t) {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        return drone == null ? 0.0 : drone.getY() - groundY;
    }

    private static boolean settled(@Nullable Telemetry t, double targetHeight) {
        return t != null && Math.abs(height(t) - targetHeight) < 0.35 && Math.abs(t.verticalSpeed) < 0.6;
    }

    private static double hoverStick(@Nullable Telemetry t) {
        return t == null ? 0.3 : Math.max(0.15, Math.min(0.6, t.hoverThrottleStick));
    }

    private static void hold(double targetHeight, float roll) {
        hold(targetHeight, roll, 3.0);
    }

    private static void hold(double targetHeight, float roll, double maxDescent) {
        Telemetry t = telemetry();
        double hover = hoverStick(t);
        double error = targetHeight - height(t);
        double vs = t == null ? 0.0 : t.verticalSpeed;
        double wanted = Math.max(-maxDescent, Math.min(3.0, 1.2 * error));
        float throttle = (float) Math.max(0.05, Math.min(0.9, hover + 0.06 * (wanted - vs)));
        PilotSession.INSTANCE.setTestInput(new StickPositions(roll, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
    }

    public static void muteShake() {
        FpvShake.INSTANCE.testIntensity(0.0F);
    }

    private static void setShake(float intensity) {
        if (intensity == 0.0F) {
            muteShake();
            return;
        }
        VideoConfig config = VideoSettings.get();
        if (config.cameraShake != intensity) {
            VideoConfig copy = config.copy();
            copy.cameraShake = intensity;
            VideoSettings.CONFIG.set(copy);
        }
        FpvShake.INSTANCE.testIntensity(-1.0F);
    }

    private static double deviation() {
        if (!FpvView.displayedBody(COM, BODY)) {
            return 0.0;
        }
        EXPECTED.set(BODY).rotateX((float) Math.toRadians(PilotSession.INSTANCE.cameraUptiltDeg()));
        Quaternionf camera = Minecraft.getInstance().gameRenderer.mainCamera().rotation();
        double ax = -EXPECTED.x;
        double ay = -EXPECTED.y;
        double az = -EXPECTED.z;
        double aw = EXPECTED.w;
        double bx = camera.x();
        double by = camera.y();
        double bz = camera.z();
        double bw = camera.w();
        double rw = aw * bw - ax * bx - ay * by - az * bz;
        double rx = aw * bx + ax * bw + ay * bz - az * by;
        double ry = aw * by - ax * bz + ay * bw + az * bx;
        double rz = aw * bz + ax * by - ay * bx + az * bw;
        return Math.toDegrees(2.0 * Math.atan2(Math.sqrt(rx * rx + ry * ry + rz * rz), Math.abs(rw)));
    }

    private static void accumulate(SelfTest.StepContext ctx, String name) {
        double d = deviation();
        ctx.state().merge(name + ".sum", d * d, (a, b) -> (double) a + (double) b);
        ctx.state().merge(name + ".n", 1, (a, b) -> (int) a + (int) b);
        ctx.state().merge(name + ".max", d, (a, b) -> Math.max((double) a, (double) b));
    }

    private static double rms(SelfTest.StepContext ctx, String name) {
        int n = (int) ctx.state().getOrDefault(name + ".n", 0);
        return n == 0 ? 0.0 : Math.sqrt((double) ctx.state().get(name + ".sum") / n);
    }

    private static double maxDeviation(SelfTest.StepContext ctx, String name) {
        return (double) ctx.state().getOrDefault(name + ".max", 0.0);
    }

    private static double angleBetween(Quaternionf a, Quaternionf b) {
        double ax = -a.x;
        double ay = -a.y;
        double az = -a.z;
        double aw = a.w;
        double rw = aw * b.w - ax * b.x - ay * b.y - az * b.z;
        double rx = aw * b.x + ax * b.w + ay * b.z - az * b.y;
        double ry = aw * b.y - ax * b.z + ay * b.w + az * b.x;
        double rz = aw * b.z + ax * b.y - ay * b.x + az * b.w;
        return Math.toDegrees(2.0 * Math.atan2(Math.sqrt(rx * rx + ry * ry + rz * rz), Math.abs(rw)));
    }

    private static final class SpeedStats {
        int frames;
        int timed;
        int turned;
        double shakeSum;
        double shakeMax;
        double timingSum;
        double timingAbsSum;
        double timingMax;
        double alongMax;
        double rateSum;
        double speedSum;
        double ratioSum;

        double shakeRms() {
            return this.frames == 0 ? 0.0 : Math.sqrt(this.shakeSum / this.frames);
        }

        double timingMeanMs() {
            return this.timed == 0 ? 0.0 : this.timingAbsSum / this.timed * 1000.0;
        }

        double timingSignedMs() {
            return this.timed == 0 ? 0.0 : this.timingSum / this.timed * 1000.0;
        }

        double timingMaxMs() {
            return this.timingMax * 1000.0;
        }

        double rateRms() {
            return this.turned == 0 ? 0.0 : Math.sqrt(this.rateSum / this.turned);
        }

        double meanSpeed() {
            return this.frames == 0 ? 0.0 : this.speedSum / this.frames;
        }

        double meanRatio() {
            return this.frames == 0 ? 0.0 : this.ratioSum / this.frames;
        }

        String summary() {
            return String.format(Locale.ROOT,
                    "%d Bilder, %.1f m/s, Drehzahl %.2f × Schweben; Kamera zu Rahmen RMS %.4f° / größte %.4f°; Posen-Zeitfehler Mittel %.3f ms (mit Vorzeichen %+.3f ms) / größter %.3f ms = %.1f mm; Drehrate der gezeigten Pose RMS %.3f °/s",
                    this.frames, this.meanSpeed(), this.meanRatio(), this.shakeRms(), this.shakeMax, this.timingMeanMs(), this.timingSignedMs(),
                    this.timingMaxMs(), this.alongMax * 1000.0, this.rateRms());
        }
    }

    private static final class SpeedProbe {
        boolean active;
        private SpeedStats stats = new SpeedStats();
        private final Vector3d com = new Vector3d();
        private final Vector3d velocity = new Vector3d();
        private final Vector3d previousCom = new Vector3d();
        private final Vector3d previousVelocity = new Vector3d();
        private final Vector3d shown = new Vector3d();
        private final Quaternionf body = new Quaternionf();
        private final Quaternionf previousBody = new Quaternionf();
        private long previousNanos = Long.MIN_VALUE;
        private long previousSample = Long.MIN_VALUE;

        void start() {
            this.stats = new SpeedStats();
            this.previousNanos = Long.MIN_VALUE;
            this.previousSample = Long.MIN_VALUE;
            this.active = true;
        }

        SpeedStats stop() {
            this.active = false;
            return this.stats;
        }

        void frame() {
            if (!this.active) {
                return;
            }
            PilotSession session = PilotSession.INSTANCE;
            DroneSim sim = session.sim();
            if (sim == null || !session.isFpv() || !FpvView.displayedBody(this.shown, this.body)) {
                return;
            }
            long now = session.physicsFrameNanos();
            long sample = VideoState.INSTANCE.lastSampleNanos();
            session.comPosition(this.com);
            sim.getVelocity(this.velocity);
            boolean known = this.previousNanos != Long.MIN_VALUE && now > this.previousNanos;
            if (known && sample != this.previousSample) {
                double frameSeconds = (now - this.previousNanos) * 1.0E-9;
                double accX = (this.velocity.x - this.previousVelocity.x) / frameSeconds;
                double accY = (this.velocity.y - this.previousVelocity.y) / frameSeconds;
                double accZ = (this.velocity.z - this.previousVelocity.z) / frameSeconds;
                boolean usePrevious = sample <= this.previousNanos;
                Vector3d fromCom = usePrevious ? this.previousCom : this.com;
                Vector3d fromVelocity = usePrevious ? this.previousVelocity : this.velocity;
                double tau = ((usePrevious ? this.previousNanos : now) - sample) * 1.0E-9;
                double speed = fromVelocity.length();
                SpeedStats s = this.stats;
                s.frames++;
                s.speedSum += speed;
                Telemetry t = sim.telemetry();
                double hoverRpm = sim.params().hoverOmega * 60.0 / (2.0 * Math.PI);
                double rpm = 0.25 * (Math.abs(t.motorRpm[0]) + Math.abs(t.motorRpm[1]) + Math.abs(t.motorRpm[2]) + Math.abs(t.motorRpm[3]));
                s.ratioSum += hoverRpm > 1.0 ? rpm / hoverRpm : 0.0;
                double shake = deviation();
                s.shakeSum += shake * shake;
                s.shakeMax = Math.max(s.shakeMax, shake);
                if (speed > 1.0 && tau >= 0.0 && tau < 0.05) {
                    double ex = fromCom.x - fromVelocity.x * tau + 0.5 * accX * tau * tau;
                    double ey = fromCom.y - fromVelocity.y * tau + 0.5 * accY * tau * tau;
                    double ez = fromCom.z - fromVelocity.z * tau + 0.5 * accZ * tau * tau;
                    double along = ((this.shown.x - ex) * fromVelocity.x + (this.shown.y - ey) * fromVelocity.y + (this.shown.z - ez) * fromVelocity.z) / speed;
                    double timing = along / speed;
                    s.timed++;
                    s.timingSum += timing;
                    s.timingAbsSum += Math.abs(timing);
                    s.timingMax = Math.max(s.timingMax, Math.abs(timing));
                    s.alongMax = Math.max(s.alongMax, Math.abs(along));
                }
                if (this.previousSample != Long.MIN_VALUE && sample > this.previousSample) {
                    double rate = angleBetween(this.previousBody, this.body) / ((sample - this.previousSample) * 1.0E-9);
                    s.turned++;
                    s.rateSum += rate * rate;
                }
            }
            if (sample != this.previousSample) {
                this.previousBody.set(this.body);
                this.previousSample = sample;
            }
            this.previousNanos = now;
            this.previousCom.set(this.com);
            this.previousVelocity.set(this.velocity);
        }
    }

    private static void spawnAndFly(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        ServerPlayer player = players.getFirst();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        for (ServerLevel other : server.getAllLevels()) {
            if (droneId >= 0 && other.getEntity(droneId) instanceof QuadcopterEntity old) {
                old.discard();
            }
        }
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        Vec3 feet = player.position();
        BlockPos spot = BlockPos.containing(feet.x, feet.y, feet.z + 5.0);
        groundY = feet.y;
        drone.snapTo(spot.getX() + 0.5, feet.y, spot.getZ() + 0.5, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setCustomName(Component.literal("Kamera Test"));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        droneId = drone.getId();
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        previousHand = player.getMainHandItem().copy();
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void placeBlocks(MinecraftServer server, BlockPos origin, int halfWidth, int height, boolean alongX, boolean checker) {
        ServerLevel level = server.overworld();
        for (int w = -halfWidth + (checker ? 0 : halfWidth); w <= halfWidth - (checker ? 0 : halfWidth); w++) {
            for (int dy = 0; dy < height; dy++) {
                BlockPos pos = alongX ? origin.offset(w, dy, 0) : origin.offset(0, dy, w);
                if (!level.getBlockState(pos).isAir()) {
                    continue;
                }
                level.setBlockAndUpdate(pos, checker
                        ? Blocks.CONCRETE.pick(((w + dy) & 1) == 0 ? DyeColor.BLACK : DyeColor.WHITE).defaultBlockState()
                        : Blocks.STONE_BRICKS.defaultBlockState());
                WALL.add(pos.immutable());
            }
        }
    }

    private static void clearBlocks(MinecraftServer server) {
        for (BlockPos pos : WALL) {
            server.overworld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        WALL.clear();
    }

    private static void release(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            ServerPlayer player = players.getFirst();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(droneId) instanceof QuadcopterEntity drone) {
                    drone.discard();
                }
            }
            ItemStack head = previousHead;
            if (head != null) {
                player.setItemSlot(EquipmentSlot.HEAD, head);
            }
            ItemStack hand = previousHand;
            if (hand != null) {
                player.setItemSlot(EquipmentSlot.MAINHAND, hand);
            }
        }
        clearBlocks(server);
        previousHead = null;
        previousHand = null;
        droneId = -1;
    }
}
