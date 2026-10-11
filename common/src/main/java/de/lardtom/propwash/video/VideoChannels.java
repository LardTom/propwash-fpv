package de.lardtom.propwash.video;

import de.lardtom.propwash.build.VideoSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

public final class VideoChannels {
    public static final int UNASSIGNED = -1;

    private static final List<Channel> ANALOG = List.of(
            new Channel("R1", 5658), new Channel("R2", 5695), new Channel("R3", 5732), new Channel("R4", 5769),
            new Channel("R5", 5806), new Channel("R6", 5843), new Channel("R7", 5880), new Channel("R8", 5917),
            new Channel("F1", 5740), new Channel("F2", 5760), new Channel("F3", 5780), new Channel("F4", 5800),
            new Channel("F5", 5820), new Channel("F6", 5840), new Channel("F7", 5860), new Channel("F8", 5880));
    private static final List<Channel> DIGITAL = List.of(
            new Channel("CH1", 5669), new Channel("CH2", 5705), new Channel("CH3", 5741), new Channel("CH4", 5769),
            new Channel("CH5", 5805), new Channel("CH6", 5840), new Channel("CH7", 5876), new Channel("CH8", 5912));
    private static final int[] ANALOG_AUTO_ORDER = {0, 1, 9, 11, 6, 7, 2, 3, 4, 5, 8, 10, 12, 13, 14, 15};
    private static final int[] DIGITAL_AUTO_ORDER = {0, 1, 2, 3, 4, 5, 6, 7};
    public static final int MAX_COUNT = ANALOG.size();

    private VideoChannels() {
    }

    public static VideoSystem tableOf(VideoSystem system) {
        return system == VideoSystem.DIGITAL ? VideoSystem.DIGITAL : VideoSystem.ANALOG;
    }

    public static List<Channel> table(VideoSystem system) {
        return tableOf(system) == VideoSystem.DIGITAL ? DIGITAL : ANALOG;
    }

    public static int count(VideoSystem system) {
        return table(system).size();
    }

    public static boolean valid(VideoSystem system, int index) {
        return index >= 0 && index < count(system);
    }

    public static int clamp(VideoSystem system, int index) {
        if (index < 0) {
            return UNASSIGNED;
        }
        return Math.min(index, count(system) - 1);
    }

    public static Channel channel(VideoSystem system, int index) {
        List<Channel> table = table(system);
        return table.get(Math.max(0, Math.min(table.size() - 1, index)));
    }

    public static String label(VideoSystem system, int index) {
        return valid(system, index) ? channel(system, index).label() : "";
    }

    public static int frequencyMhz(VideoSystem system, int index) {
        return valid(system, index) ? channel(system, index).mhz() : 0;
    }

    public static long frequencyKey(VideoSystem system, int index) {
        return ((long) tableOf(system).ordinal() << 32) | frequencyMhz(system, index);
    }

    public static int[] autoOrder(VideoSystem system) {
        return (tableOf(system) == VideoSystem.DIGITAL ? DIGITAL_AUTO_ORDER : ANALOG_AUTO_ORDER).clone();
    }

    public static int pickFree(VideoSystem system, int preferred, IntPredicate frequencyTaken) {
        int current = clamp(system, preferred);
        if (current >= 0 && !frequencyTaken.test(frequencyMhz(system, current))) {
            return current;
        }
        for (int index : autoOrder(system)) {
            if (!frequencyTaken.test(frequencyMhz(system, index))) {
                return index;
            }
        }
        return current >= 0 ? current : autoOrder(system)[0];
    }

    public static List<Integer> assignAll(VideoSystem system, List<Integer> preferred, List<Boolean> manual) {
        List<Integer> result = new ArrayList<>(preferred.size());
        List<Integer> taken = new ArrayList<>();
        for (int i = 0; i < preferred.size(); i++) {
            int wanted = clamp(system, preferred.get(i));
            result.add(wanted);
            if (manual.get(i) && wanted >= 0) {
                taken.add(frequencyMhz(system, wanted));
            }
        }
        for (int i = 0; i < preferred.size(); i++) {
            if (manual.get(i) && result.get(i) >= 0) {
                continue;
            }
            int pick = pickFree(system, result.get(i), taken::contains);
            result.set(i, pick);
            taken.add(frequencyMhz(system, pick));
        }
        return result;
    }

    public record Channel(String label, int mhz) {
    }
}
