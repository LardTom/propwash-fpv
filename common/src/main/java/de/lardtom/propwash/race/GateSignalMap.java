package de.lardtom.propwash.race;

import java.util.HashMap;
import java.util.Map;

public final class GateSignalMap {
    public static final int FACES = 6;

    private final Map<Long, int[]> counts = new HashMap<>();

    public boolean hold(long emitter, int face) {
        int[] faces = this.counts.computeIfAbsent(emitter, key -> new int[FACES]);
        return faces[face]++ == 0;
    }

    public boolean release(long emitter, int face) {
        int[] faces = this.counts.get(emitter);
        if (faces == null || faces[face] == 0) {
            return false;
        }
        faces[face]--;
        if (mask(faces) == 0) {
            this.counts.remove(emitter);
        }
        return faces[face] == 0;
    }

    public int mask(long emitter) {
        int[] faces = this.counts.get(emitter);
        return faces == null ? 0 : mask(faces);
    }

    public boolean isEmpty() {
        return this.counts.isEmpty();
    }

    public int size() {
        return this.counts.size();
    }

    public Map<Long, Integer> masks() {
        Map<Long, Integer> masks = new HashMap<>();
        for (Map.Entry<Long, int[]> entry : this.counts.entrySet()) {
            masks.put(entry.getKey(), mask(entry.getValue()));
        }
        return masks;
    }

    private static int mask(int[] faces) {
        int mask = 0;
        for (int face = 0; face < FACES; face++) {
            if (faces[face] > 0) {
                mask |= 1 << face;
            }
        }
        return mask;
    }
}
