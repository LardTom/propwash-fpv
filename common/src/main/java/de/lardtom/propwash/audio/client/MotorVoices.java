package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.sim.DroneSim;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MotorVoices {
    public static final int SAMPLE_VOICE_LIMIT = 8;

    private static final int STREAMING_RETRY_TICKS = 2400;
    private static final long POOL_REFRESH_NANOS = 2_000_000_000L;
    private static final Comparator<DroneVoice> BY_PRIORITY = Comparator.comparingDouble(DroneVoice::priority);
    private static final Int2ObjectOpenHashMap<DroneVoice> VOICES = new Int2ObjectOpenHashMap<>();
    private static final List<DroneVoice> RANKED = new ArrayList<>();

    private static long tick;
    private static long synthBlockedUntil;
    private static boolean listenerKnown;
    private static double listenerX;
    private static double listenerY;
    private static double listenerZ;
    private static double listenerVx;
    private static double listenerVy;
    private static double listenerVz;
    private static int synthVoices;
    private static int sampleVoices;
    private static int streamingMax = StreamingPool.UNKNOWN;
    private static long poolReadNanos;
    private static boolean poolRead;

    private MotorVoices() {
    }

    static void onDroneTick(QuadcopterEntity drone) {
        if (!drone.level().isClientSide()) {
            return;
        }
        DroneVoice voice = VOICES.get(drone.getId());
        if (voice == null || voice.drone != drone) {
            if (voice != null) {
                voice.stopInstances(Minecraft.getInstance().getSoundManager());
            }
            voice = new DroneVoice(drone);
            VOICES.put(drone.getId(), voice);
            return;
        }
        voice.observe();
    }

    static void onTickEnd(Minecraft minecraft) {
        try {
            tickVoices(minecraft);
        } catch (RuntimeException | LinkageError e) {
            AudioErrors.report("voices", e);
        }
    }

    static void onFrame(Minecraft minecraft, DeltaTracker deltaTracker) {
        if (VOICES.isEmpty()) {
            return;
        }
        try {
            frameVoices(deltaTracker);
        } catch (RuntimeException | LinkageError e) {
            AudioErrors.report("frame", e);
        }
    }

    public static void clear() {
        try {
            SoundManager soundManager = Minecraft.getInstance().getSoundManager();
            for (DroneVoice voice : VOICES.values()) {
                voice.stopInstances(soundManager);
            }
        } catch (RuntimeException | LinkageError e) {
            AudioErrors.report("clear", e);
        }
        VOICES.clear();
        RANKED.clear();
        AudioPump.clear();
        LocatorBeeper.reset();
        listenerKnown = false;
        synthVoices = 0;
        sampleVoices = 0;
    }

    public static int synthVoices() {
        return synthVoices;
    }

    public static int sampleVoices() {
        return sampleVoices;
    }

    public static int trackedDrones() {
        return VOICES.size();
    }

    static @Nullable DroneVoice voiceOf(int entityId) {
        return VOICES.get(entityId);
    }

    public static DroneVoice.Kind kindOf(int entityId) {
        DroneVoice voice = VOICES.get(entityId);
        return voice == null ? DroneVoice.Kind.NONE : voice.kind;
    }

    public static @Nullable MotorStream streamOf(int entityId) {
        DroneVoice voice = VOICES.get(entityId);
        if (voice == null || voice.synthInstance() == null) {
            return null;
        }
        return voice.synthInstance().stream();
    }

    static @Nullable MotorSoundInstance synthOf(int entityId) {
        DroneVoice voice = VOICES.get(entityId);
        return voice == null ? null : voice.synthInstance();
    }

    static boolean silenced(int entityId) {
        DroneVoice voice = VOICES.get(entityId);
        return voice != null && voice.silenced();
    }

    public static boolean audible(int entityId) {
        DroneVoice voice = VOICES.get(entityId);
        if (voice == null) {
            return false;
        }
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        MotorSoundInstance synth = voice.synthInstance();
        MotorSampleSoundInstance sample = voice.sampleInstance();
        return (synth != null && soundManager.isActive(synth)) || (sample != null && soundManager.isActive(sample));
    }

    public static int synthLimit(AudioConfig config) {
        if (!config.synthesizer) {
            return 0;
        }
        return Math.min(config.voiceLimit, StreamingPool.synthCap(streamingMax()));
    }

    public static int streamingMax() {
        long now = System.nanoTime();
        if (!poolRead || now - poolReadNanos >= POOL_REFRESH_NANOS) {
            poolRead = true;
            poolReadNanos = now;
            try {
                streamingMax = StreamingPool.parseMax(Minecraft.getInstance().getSoundManager().getChannelDebugString());
            } catch (RuntimeException | LinkageError e) {
                streamingMax = StreamingPool.UNKNOWN;
                AudioErrors.report("pool", e);
            }
        }
        return streamingMax;
    }

    static void onStopCommand(SoundManager soundManager, @Nullable Identifier sound, @Nullable SoundSource source) {
        for (DroneVoice voice : VOICES.values()) {
            voice.onStopCommand(soundManager, sound, source);
        }
    }

    static boolean synthAllowed(long now) {
        return now >= synthBlockedUntil && AudioConfig.get().synthesizer;
    }

    static void streamingUnavailable(long now) {
        synthBlockedUntil = now + STREAMING_RETRY_TICKS;
        AudioErrors.note("streaming", "Eigener Audio-Stream wurde nicht angefragt, nutze Sample-Ersatz für den Motorsound");
    }

    private static void tickVoices(Minecraft minecraft) {
        if (minecraft.level == null) {
            if (!VOICES.isEmpty()) {
                clear();
            }
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        SoundManager soundManager = minecraft.getSoundManager();
        updateListener(soundManager);
        ObjectIterator<DroneVoice> it = VOICES.values().iterator();
        while (it.hasNext()) {
            DroneVoice voice = it.next();
            if (voice.isStale(minecraft.level)) {
                voice.stopInstances(soundManager);
                it.remove();
            }
        }
        if (VOICES.isEmpty()) {
            synthVoices = 0;
            sampleVoices = 0;
            tick++;
            return;
        }
        LocatorBeeper.tick(minecraft, VOICES.values(), tick);
        AudioConfig config = AudioConfig.get();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity piloted = session.isFpv() ? session.drone() : null;
        DroneSim sim = piloted != null ? session.sim() : null;
        RANKED.clear();
        for (DroneVoice voice : VOICES.values()) {
            boolean pilotView = piloted == voice.drone;
            voice.updateTargets(pilotView, pilotView ? sim : null, config, listenerX, listenerY, listenerZ, listenerVx, listenerVy, listenerVz);
            if (voice.wantsSound()) {
                RANKED.add(voice);
            } else {
                voice.apply(DroneVoice.Kind.NONE, soundManager, tick);
            }
        }
        RANKED.sort(BY_PRIORITY);
        int synthBudget = synthLimit(config);
        int sampleBudget = SAMPLE_VOICE_LIMIT;
        int synths = 0;
        int samples = 0;
        for (DroneVoice voice : RANKED) {
            DroneVoice.Kind want;
            if (synthBudget > 0 && tick >= synthBlockedUntil && tick >= voice.synthRetryTick) {
                want = DroneVoice.Kind.SYNTH;
                synthBudget--;
            } else if (sampleBudget > 0) {
                want = DroneVoice.Kind.SAMPLE;
                sampleBudget--;
            } else {
                want = DroneVoice.Kind.NONE;
            }
            voice.apply(want, soundManager, tick);
            voice.afterUpdate();
            if (voice.kind == DroneVoice.Kind.SYNTH) {
                synths++;
            } else if (voice.kind == DroneVoice.Kind.SAMPLE) {
                samples++;
            }
        }
        synthVoices = synths;
        sampleVoices = samples;
        tick++;
    }

    private static void updateListener(SoundManager soundManager) {
        Vec3 position = soundManager.getListenerTransform().position();
        if (listenerKnown) {
            double vx = (position.x - listenerX) / 0.05;
            double vy = (position.y - listenerY) / 0.05;
            double vz = (position.z - listenerZ) / 0.05;
            if (vx * vx + vy * vy + vz * vz > 150.0 * 150.0) {
                vx = 0.0;
                vy = 0.0;
                vz = 0.0;
            }
            listenerVx += 0.5 * (vx - listenerVx);
            listenerVy += 0.5 * (vy - listenerVy);
            listenerVz += 0.5 * (vz - listenerVz);
        }
        listenerX = position.x;
        listenerY = position.y;
        listenerZ = position.z;
        listenerKnown = true;
    }

    private static void frameVoices(DeltaTracker deltaTracker) {
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        Vec3 listener = Minecraft.getInstance().getSoundManager().getListenerTransform().position();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity piloted = session.isFpv() ? session.drone() : null;
        for (DroneVoice voice : VOICES.values()) {
            if (voice.kind == DroneVoice.Kind.NONE) {
                continue;
            }
            if (voice.pilotView()) {
                if (piloted == voice.drone) {
                    DroneSim sim = session.sim();
                    if (sim != null) {
                        voice.pushPilotTelemetry(sim.telemetry());
                    }
                }
                continue;
            }
            voice.frame(voice.drone.getPosition(partialTick), listener);
        }
    }
}
