package de.lardtom.propwash.input;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

public final class DeviceChooser {
    private DeviceChooser() {
    }

    public static int pick(List<Candidate> devices, @Nullable String preferredGuid) {
        return pick(devices, preferredGuid, null, i -> false);
    }

    public static int pick(List<Candidate> devices, @Nullable String preferredGuid, @Nullable String sessionGuid, IntPredicate blocked) {
        return pick(devices, preferredGuid, sessionGuid, blocked, guid -> -1);
    }

    public static int pick(List<Candidate> devices, @Nullable String preferredGuid, @Nullable String sessionGuid, IntPredicate blocked,
                           ToIntFunction<String> usage) {
        if (devices.isEmpty() || InputConfig.withoutDevice(preferredGuid)) {
            return -1;
        }
        int preferred = indexOf(devices, preferredGuid, blocked);
        if (preferred >= 0) {
            return preferred;
        }
        if (sessionGuid != null && !sessionGuid.isEmpty()) {
            return indexOf(devices, sessionGuid, blocked);
        }
        int best = -1;
        int bestUsage = Integer.MIN_VALUE;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < devices.size(); i++) {
            if (blocked.test(i)) {
                continue;
            }
            int used = Math.max(-1, usage.applyAsInt(devices.get(i).guid()));
            int score = score(devices.get(i));
            if (used > bestUsage || used == bestUsage && score > bestScore) {
                bestUsage = used;
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    public static int score(Candidate device) {
        if (device.radio()) {
            return 3;
        }
        return device.gamepad() ? 2 : 1;
    }

    private static int indexOf(List<Candidate> devices, @Nullable String guid, IntPredicate blocked) {
        if (guid == null || guid.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < devices.size(); i++) {
            if (guid.equals(devices.get(i).guid()) && !blocked.test(i)) {
                return i;
            }
        }
        return -1;
    }

    public record Candidate(String guid, String name, boolean gamepad, boolean radio) {
        public Candidate(String guid, String name, boolean gamepad) {
            this(guid, name, gamepad, ProfileDefaults.looksLikeRadio(name));
        }
    }
}
