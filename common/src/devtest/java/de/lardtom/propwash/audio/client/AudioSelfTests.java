package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.audio.AudioSounds;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class AudioSelfTests {
    private static final List<RegHolder<SoundEvent>> ALL = List.of(AudioSounds.MOTOR, AudioSounds.MOTOR_PILOT, AudioSounds.MOTOR_SAMPLE,
            AudioSounds.LOCATOR, AudioSounds.CRASH, AudioSounds.BEEP_ARM, AudioSounds.BEEP_DISARM, AudioSounds.BEEP_LOW_BATTERY,
            AudioSounds.BEEP_GATE, AudioSounds.BEEP_LAP, AudioSounds.BEEP_COUNTDOWN, AudioSounds.BEEP_GO, AudioSounds.BEEP_FINISH);
    private static final String DRONE = "audio.drone";
    private static final String SPAWN = "audio.spawn";
    private static final String STOPSOUND = "audio.stopsound";
    private static final String STOPPED_INSTANCE = "audio.stopped";
    private static final String DISARM = "audio.disarm";
    private static final String REMOVE = "audio.remove";
    private static final String ERRORS = "audio.errors";
    private static final String SYNTH_WAS = "audio.synth";
    private static final float SPIN = 0.6F;
    private static volatile int heldArmed = -1;

    private AudioSelfTests() {
    }

    public static void register() {
        ServerBus.TICK_END.register(AudioSelfTests::holdArmed);
        SelfTest.clientStep("audio: Sound-Events und Dateien", 40, AudioSelfTests::soundsStep);
        SelfTest.clientStep("audio: Pieptöne", 40, AudioSelfTests::beepStep);
        SelfTest.clientStep("audio: Motor-Stream startet und stoppt", 600, AudioSelfTests::streamStep);
        AudioReachSelfTests.register();
        SelfTest.afterLeaveStep("audio: Stimmen freigegeben", 40, ctx -> StepResult.check(
                MotorVoices.trackedDrones() == 0 && AudioPump.size() == 0,
                "nach dem Verlassen noch " + MotorVoices.trackedDrones() + " Drohnen-Stimmen und " + AudioPump.size() + " gepumpte Kanäle"));
    }

    private static StepResult soundsStep(SelfTest.StepContext ctx) {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        RandomSource random = RandomSource.create(1L);
        for (RegHolder<SoundEvent> holder : ALL) {
            if (!holder.isBound()) {
                return StepResult.fail("SoundEvent nicht registriert: " + holder.id());
            }
            WeighedSoundEvents events = soundManager.getSoundEvent(holder.id());
            if (events == null) {
                return StepResult.fail("sounds.json-Eintrag fehlt: " + holder.id());
            }
            Sound sound = events.getSound(random);
            if (sound == SoundManager.EMPTY_SOUND || sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) {
                return StepResult.fail("Ogg-Datei fehlt für " + holder.id());
            }
            boolean mustStream = holder == AudioSounds.MOTOR || holder == AudioSounds.MOTOR_PILOT;
            if (sound.shouldStream() != mustStream) {
                return StepResult.fail("stream-Flag falsch für " + holder.id());
            }
        }
        return StepResult.pass();
    }

    private static StepResult beepStep(SelfTest.StepContext ctx) {
        int errors = AudioErrors.count();
        BeepPlayer.resetMerging();
        for (Beep beep : Beep.values()) {
            int before = BeepPlayer.playedCount(beep);
            AudioHooks.beep(beep);
            if (BeepPlayer.playedCount(beep) != before + 1) {
                return StepResult.fail("Piepton nicht abgespielt: " + beep);
            }
            AudioHooks.beep(beep);
            if (BeepPlayer.playedCount(beep) != before + 1) {
                return StepResult.fail("Piepton nicht zusammengefasst: " + beep);
            }
        }
        AudioHooks.beep(null);
        BeepPlayer.resetMerging();
        return StepResult.check(AudioErrors.count() == errors, "Fehler beim Abspielen der Pieptöne (siehe Log)");
    }

    private static StepResult streamStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (!SoundEngineAccess.engineLoaded(minecraft.getSoundManager())) {
                ctx.log("kein Audiogerät, Motor-Stream-Test übersprungen");
                return StepResult.pass();
            }
            ctx.state().put(SYNTH_WAS, AudioConfig.get().synthesizer);
            AudioConfig.get().synthesizer = true;
            ctx.state().put(ERRORS, AudioErrors.count());
            AtomicInteger id = new AtomicInteger(-1);
            ctx.state().put(DRONE, id);
            ctx.state().put(SPAWN, ctx.onServer(server -> {
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    throw new IllegalStateException("kein Spieler auf dem Server");
                }
                ServerPlayer player = players.getFirst();
                ServerLevel level = player.level();
                QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                if (drone == null) {
                    throw new IllegalStateException("EntityType.create lieferte null");
                }
                Vec3 pos = player.position().add(-3.0, 1.5, 0.0);
                drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
                drone.setBuild(DroneBuild.RACER);
                drone.setArmed(true);
                drone.setMotorOutput(SPIN);
                if (!level.addFreshEntity(drone)) {
                    throw new IllegalStateException("addFreshEntity fehlgeschlagen");
                }
                heldArmed = drone.getId();
                id.set(drone.getId());
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> spawn = (CompletableFuture<?>) ctx.state().get(SPAWN);
        if (spawn == null) {
            return StepResult.pass();
        }
        if (spawn.isCompletedExceptionally()) {
            return finish(ctx, StepResult.fail("Spawn fehlgeschlagen: " + spawn.exceptionNow().getMessage()));
        }
        int id = ((AtomicInteger) ctx.state().get(DRONE)).get();
        if (id < 0 || minecraft.level == null) {
            return StepResult.pending();
        }
        CompletableFuture<?> disarm = (CompletableFuture<?>) ctx.state().get(DISARM);
        if (disarm == null) {
            Object stopTick = ctx.state().get(STOPSOUND);
            if (!(stopTick instanceof Integer stoppedAt)) {
                MotorStream stream = MotorVoices.streamOf(id);
                if (MotorVoices.kindOf(id) != DroneVoice.Kind.SYNTH || stream == null || stream.chunksRead() < 8 || !MotorVoices.audible(id)) {
                    if (ctx.tick() > 300) {
                        return finish(ctx, StepResult.fail("Motor-Stream läuft nicht: Stimme=" + MotorVoices.kindOf(id)
                                + " Chunks=" + (stream == null ? -1 : stream.chunksRead()) + " Kanäle=" + minecraft.getSoundManager().getChannelDebugString()));
                    }
                    return StepResult.pending();
                }
                ctx.log("Motor-Stream läuft (" + stream.chunksRead() + " Chunks, Pumpe " + AudioPump.size() + " Kanäle, Neustarts " + AudioPump.restarts() + ")");
                MotorSoundInstance running = MotorVoices.synthOf(id);
                if (running != null) {
                    ctx.state().put(STOPPED_INSTANCE, running);
                }
                ctx.state().put(STOPSOUND, ctx.tick());
                minecraft.getSoundManager().stop(null, SoundSource.PLAYERS);
                if (!MotorVoices.silenced(id)) {
                    return finish(ctx, StepResult.fail("/stopsound schaltet die Motorstimme nicht stumm: Stimme=" + MotorVoices.kindOf(id)));
                }
                return StepResult.pending();
            }
            Object stopped = ctx.state().get(STOPPED_INSTANCE);
            boolean oldActive = stopped instanceof MotorSoundInstance instance && minecraft.getSoundManager().isActive(instance);
            if (MotorVoices.kindOf(id) != DroneVoice.Kind.NONE || oldActive) {
                if (ctx.tick() - stoppedAt > 40) {
                    return finish(ctx, StepResult.fail("/stopsound bringt den Motor nicht zum Schweigen: Stimme=" + MotorVoices.kindOf(id)
                            + " alter Kanal aktiv=" + oldActive));
                }
                return StepResult.pending();
            }
            if (ctx.tick() - stoppedAt < 30) {
                return StepResult.pending();
            }
            ctx.state().put(DISARM, ctx.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = level.getEntity(id);
                    if (entity instanceof QuadcopterEntity drone) {
                        heldArmed = -1;
                        drone.setArmed(false);
                        drone.setMotorOutput(0.0F);
                    }
                }
                heldArmed = -1;
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> remove = (CompletableFuture<?>) ctx.state().get(REMOVE);
        if (remove == null) {
            DroneVoice.Kind kind = MotorVoices.kindOf(id);
            if (kind != DroneVoice.Kind.NONE || MotorVoices.audible(id)) {
                if (ctx.tick() > 560) {
                    return finish(ctx, StepResult.fail("Motor-Stream endet nach dem Disarm nicht: Stimme=" + kind));
                }
                return StepResult.pending();
            }
            ctx.state().put(REMOVE, ctx.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) {
                        entity.discard();
                    }
                }
            }));
            return StepResult.pending();
        }
        if (!remove.isDone() || minecraft.level.getEntity(id) != null) {
            return StepResult.pending();
        }
        int errors = (Integer) ctx.state().get(ERRORS);
        return finish(ctx, StepResult.check(AudioErrors.count() == errors, "Audio-Fehler während des Tests (siehe Log)"));
    }

    private static void holdArmed(MinecraftServer server) {
        int id = heldArmed;
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone && !drone.isRemoved()) {
                if (!drone.isArmed()) {
                    drone.setArmed(true);
                }
                if (drone.motorOutput() == 0.0F) {
                    drone.setMotorOutput(SPIN);
                }
            }
        }
    }

    private static StepResult finish(SelfTest.StepContext ctx, StepResult result) {
        if (result.status() != StepResult.Status.PASS) {
            heldArmed = -1;
        }
        Object was = ctx.state().get(SYNTH_WAS);
        if (was instanceof Boolean previous) {
            AudioConfig.get().synthesizer = previous;
        }
        return result;
    }
}
