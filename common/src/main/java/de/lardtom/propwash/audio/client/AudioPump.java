package de.lardtom.propwash.audio.client;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.ChannelAccess;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class AudioPump {
    public static final long PERIOD_MILLIS = 5L;

    private static final Set<Entry> ENTRIES = ConcurrentHashMap.newKeySet();
    private static final AtomicLong RESTARTS = new AtomicLong();
    private static final Predicate<Entry> PUMP = AudioPump::pumpOrDrop;
    private static volatile ScheduledExecutorService executor;

    private AudioPump() {
    }

    public static void add(ChannelAccess.ChannelHandle handle, MotorStream stream) {
        if (handle == null || stream == null) {
            return;
        }
        ensureRunning();
        ENTRIES.add(new Entry(handle, stream));
    }

    public static void remove(ChannelAccess.ChannelHandle handle) {
        if (handle != null) {
            ENTRIES.removeIf(entry -> entry.handle == handle);
        }
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static long restarts() {
        return RESTARTS.get();
    }

    private static synchronized void ensureRunning() {
        if (executor != null) {
            return;
        }
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Propwash audio pump");
            thread.setDaemon(true);
            thread.setPriority(Thread.MAX_PRIORITY - 1);
            return thread;
        });
        service.scheduleAtFixedRate(AudioPump::pumpAll, PERIOD_MILLIS, PERIOD_MILLIS, TimeUnit.MILLISECONDS);
        executor = service;
    }

    private static void pumpAll() {
        try {
            if (!ENTRIES.isEmpty()) {
                ENTRIES.removeIf(PUMP);
            }
        } catch (Throwable t) {
            AudioErrors.report("pump", t);
        }
    }

    private static boolean pumpOrDrop(Entry entry) {
        if (entry.handle.isStopped() || entry.stream.isClosed()) {
            return true;
        }
        entry.handle.execute(entry.action);
        return false;
    }

    private static final class Entry {
        private final ChannelAccess.ChannelHandle handle;
        private final MotorStream stream;
        private final Consumer<Channel> action;

        private Entry(ChannelAccess.ChannelHandle handle, MotorStream stream) {
            this.handle = handle;
            this.stream = stream;
            this.action = this::service;
        }

        private void service(Channel channel) {
            try {
                channel.updateStream();
                if (this.stream.keepPlaying() && channel.stopped()) {
                    channel.play();
                    RESTARTS.incrementAndGet();
                }
            } catch (RuntimeException e) {
                AudioErrors.report("channel", e);
            }
        }
    }
}
