package de.lardtom.propwash.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

public record PaintCost(Map<String, Integer> dyes, List<Integer> custom, int changes) {
    public static final PaintCost FREE = new PaintCost(Map.of(), List.of(), 0);

    public PaintCost {
        dyes = Collections.unmodifiableMap(new LinkedHashMap<>(dyes));
        custom = List.copyOf(custom);
    }

    public static PaintCost of(DronePaint from, DronePaint to) {
        DronePaint before = from == null ? DronePaint.DEFAULT : from;
        DronePaint after = to == null ? DronePaint.DEFAULT : to;
        Map<String, Integer> dyes = new LinkedHashMap<>();
        for (PaintSwatch swatch : PaintSwatch.dyes()) {
            dyes.put(swatch.dye(), 0);
        }
        List<Integer> custom = new ArrayList<>();
        int changes = 0;
        for (PaintSlot slot : PaintSlot.values()) {
            OptionalInt next = after.color(slot);
            OptionalInt previous = before.color(slot);
            if (next.equals(previous)) {
                continue;
            }
            changes++;
            if (next.isEmpty()) {
                continue;
            }
            Optional<PaintSwatch> swatch = PaintSwatch.byColor(next.getAsInt());
            if (swatch.isPresent()) {
                dyes.merge(swatch.get().dye(), 1, Integer::sum);
            } else {
                custom.add(next.getAsInt());
            }
        }
        dyes.values().removeIf(count -> count == 0);
        if (dyes.isEmpty() && custom.isEmpty() && changes == 0) {
            return FREE;
        }
        return new PaintCost(dyes, custom, changes);
    }

    public boolean free() {
        return this.dyes.isEmpty() && this.custom.isEmpty();
    }

    public int total() {
        int total = this.custom.size();
        for (int count : this.dyes.values()) {
            total += count;
        }
        return total;
    }

    public Optional<Map<String, Integer>> plan(Map<String, Integer> available) {
        Map<String, Integer> left = new LinkedHashMap<>();
        for (PaintSwatch swatch : PaintSwatch.dyes()) {
            left.put(swatch.dye(), Math.max(0, available.getOrDefault(swatch.dye(), 0)));
        }
        Map<String, Integer> use = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : this.dyes.entrySet()) {
            int have = left.getOrDefault(entry.getKey(), 0);
            if (have < entry.getValue()) {
                return Optional.empty();
            }
            left.put(entry.getKey(), have - entry.getValue());
            use.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        for (int color : this.custom) {
            String best = null;
            long bestDistance = Long.MAX_VALUE;
            for (PaintSwatch swatch : PaintSwatch.dyes()) {
                if (left.getOrDefault(swatch.dye(), 0) <= 0) {
                    continue;
                }
                long distance = PaintSwatch.distance(color, swatch.rgb());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = swatch.dye();
                }
            }
            if (best == null) {
                return Optional.empty();
            }
            left.merge(best, -1, Integer::sum);
            use.merge(best, 1, Integer::sum);
        }
        return Optional.of(Collections.unmodifiableMap(use));
    }
}
