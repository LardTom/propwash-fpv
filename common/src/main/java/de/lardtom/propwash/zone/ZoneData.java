package de.lardtom.propwash.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.Constants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class ZoneData extends SavedData {
    private static final Codec<ZoneData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("next_id", 1).forGetter(data -> data.nextId),
            Zone.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(ZoneData::all)
    ).apply(i, ZoneData::new));

    public static final SavedDataType<ZoneData> TYPE = new SavedDataType<>(Constants.id("zones"), ZoneData::new, CODEC, null);

    private final Map<Integer, Zone> zones = new TreeMap<>();
    private final Map<String, List<Zone>> byDimension = new HashMap<>();
    private int nextId = 1;
    private int version;

    public ZoneData() {
    }

    private ZoneData(int nextId, List<Zone> list) {
        List<Zone> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> Integer.compare(a.id(), b.id()));
        Map<String, Integer> perDimension = new HashMap<>();
        for (Zone zone : sorted) {
            if (zone.id() > 0 && !this.zones.containsKey(zone.id()) && ZoneCheck.problem(zone) == null
                    && perDimension.merge(zone.dimension(), 1, Integer::sum) <= ZoneLimits.MAX_ZONES) {
                this.zones.put(zone.id(), zone);
            }
        }
        int highest = this.zones.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        this.nextId = Math.max(nextId, highest + 1);
    }

    public static ZoneData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Zone> all() {
        return List.copyOf(this.zones.values());
    }

    public List<Zone> in(String dimension) {
        List<Zone> cached = this.byDimension.get(dimension);
        if (cached != null) {
            return cached;
        }
        List<Zone> out = new ArrayList<>();
        for (Zone zone : this.zones.values()) {
            if (zone.dimension().equals(dimension)) {
                out.add(zone);
            }
        }
        List<Zone> frozen = List.copyOf(out);
        this.byDimension.put(dimension, frozen);
        return frozen;
    }

    public @Nullable Zone get(int id) {
        return this.zones.get(id);
    }

    public Optional<Zone> byName(String dimension, String name) {
        for (Zone zone : this.zones.values()) {
            if (zone.dimension().equals(dimension) && ZoneCheck.sameName(zone.name(), name)) {
                return Optional.of(zone);
            }
        }
        return Optional.empty();
    }

    public int version() {
        return this.version;
    }

    public int nextId() {
        return this.nextId;
    }

    public Zone put(Zone zone) {
        Zone stored = zone.id() == Zone.NEW ? zone.withId(this.nextId++) : zone;
        this.zones.put(stored.id(), stored);
        this.changed();
        return stored;
    }

    public @Nullable Zone remove(int id) {
        Zone removed = this.zones.remove(id);
        if (removed != null) {
            this.changed();
        }
        return removed;
    }

    private void changed() {
        this.byDimension.clear();
        this.version++;
        this.setDirty();
    }
}
