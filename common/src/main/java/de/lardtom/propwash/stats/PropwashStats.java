package de.lardtom.propwash.stats;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import de.lardtom.propwash.registry.RegList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class PropwashStats {
    public static final int PROBE_VALUE = 123_456;
    private static final RegList<Identifier> CUSTOM = PropwashRegistries.list(Registries.CUSTOM_STAT);
    private static final List<Entry> ALL = new ArrayList<>();
    public static final Entry FLIGHT_TIME = add(StatNames.FLIGHT_TIME, StatFormatter.TIME);
    public static final Entry DISTANCE = add(StatNames.DISTANCE, StatFormatter.DISTANCE);
    public static final Entry TOP_SPEED = add(StatNames.TOP_SPEED, StatFormats.SPEED);
    public static final Entry CRASHES = add(StatNames.CRASHES, StatFormatter.DEFAULT);
    public static final Entry BATTERIES_DEPLETED = add(StatNames.BATTERIES_DEPLETED, StatFormatter.DEFAULT);
    public static final Entry ARMS = add(StatNames.ARMS, StatFormatter.DEFAULT);
    public static final Entry LAPS = add(StatNames.LAPS, StatFormatter.DEFAULT);
    public static final Entry RACES_FINISHED = add(StatNames.RACES_FINISHED, StatFormatter.DEFAULT);
    private static final Map<FrameRole, Entry> CLASS_TIME = classTimes();
    private static boolean bound;

    private PropwashStats() {
    }

    static void bootstrap() {
    }

    public static Entry classTime(FrameRole role) {
        return CLASS_TIME.get(role);
    }

    public static List<Entry> all() {
        return List.copyOf(ALL);
    }

    public static synchronized boolean bound() {
        return bound;
    }

    public static synchronized void bind() {
        if (bound) {
            return;
        }
        bound = true;
        for (Entry entry : ALL) {
            Stat<Identifier> stat = entry.stat();
            if (!stat.format(PROBE_VALUE).equals(entry.formatter().format(PROBE_VALUE))) {
                Constants.LOG.warn("Statistik {} wurde vor Propwash mit einem anderen Format angelegt", entry.id());
            }
        }
    }

    private static Entry add(String name, StatFormatter formatter) {
        Entry entry = new Entry(name, CUSTOM.add(name, key -> key.identifier()), formatter);
        ALL.add(entry);
        return entry;
    }

    private static Map<FrameRole, Entry> classTimes() {
        Map<FrameRole, Entry> map = new EnumMap<>(FrameRole.class);
        for (FrameRole role : FrameRole.values()) {
            map.put(role, add(StatNames.classTime(role), StatFormatter.TIME));
        }
        return map;
    }

    public record Entry(String name, RegHolder<Identifier> holder, StatFormatter formatter) {
        public Identifier id() {
            return this.holder.id();
        }

        public String translationKey() {
            return StatNames.translationKey(this.name);
        }

        public Stat<Identifier> stat() {
            return Stats.CUSTOM.get(this.holder.get(), this.formatter);
        }
    }
}
