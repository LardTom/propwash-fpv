package de.lardtom.propwash.audio.client;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class SoundPacketLog {
    public static final int CAPACITY = 4096;
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static long sequence;

    private SoundPacketLog() {
    }

    public enum Kind {
        SOUND,
        ENTITY_SOUND,
        LEVEL_EVENT,
        EXPLOSION
    }

    public record Entry(long sequence, Kind kind, String key, Vec3 at, float pitch, int entity) {
    }

    public static synchronized void record(Kind kind, String key, Vec3 at, float pitch, int entity) {
        ENTRIES.add(new Entry(++sequence, kind, key, at, pitch, entity));
        if (ENTRIES.size() > CAPACITY) {
            ENTRIES.subList(0, ENTRIES.size() - CAPACITY).clear();
        }
    }

    public static synchronized long mark() {
        return sequence;
    }

    public static synchronized List<Entry> since(long mark) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (entry.sequence() > mark) {
                out.add(entry);
            }
        }
        return out;
    }
}
