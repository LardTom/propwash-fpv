package de.lardtom.propwash.video.vtx;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class VtxPowerSettings {
    public static final String POWER = "vtx.powerMw";
    public static final String AUTO = "vtx.powerAuto";
    public static final String LOW_POWER = "vtx.lowPowerDisarm";
    public static final List<String> KEYS = List.of(POWER, AUTO, LOW_POWER);

    private VtxPowerSettings() {
    }

    public enum LowPower {
        OFF,
        DISARMED,
        UNTIL_ARMED;

        public static LowPower of(float value) {
            int index = Math.round(value);
            return index == 1 ? DISARMED : index == 2 ? UNTIL_ARMED : OFF;
        }
    }

    public record Selection(boolean auto, int index) {
    }

    public static boolean isKey(String key) {
        return POWER.equals(key) || AUTO.equals(key) || LOW_POWER.equals(key);
    }

    public static Selection selection(Map<String, Float> tune, VtxTable table) {
        if (!manual(tune)) {
            return new Selection(table.supportsAuto(), table.ratedIndex());
        }
        return new Selection(false, table.nearestIndex(tune.get(POWER)));
    }

    public static boolean manual(Map<String, Float> tune) {
        Float mw = tune.get(POWER);
        return !auto(tune) && mw != null && Float.isFinite(mw) && mw >= 1.0F;
    }

    public static boolean auto(Map<String, Float> tune) {
        Float value = tune.get(AUTO);
        return value != null && Float.isFinite(value) && value >= 0.5F;
    }

    public static LowPower lowPower(Map<String, Float> tune) {
        Float value = tune.get(LOW_POWER);
        return value == null || !Float.isFinite(value) ? LowPower.OFF : LowPower.of(value);
    }

    public static Map<String, Float> withLevel(Map<String, Float> tune, int milliwatts) {
        TreeMap<String, Float> out = new TreeMap<>(tune);
        out.remove(AUTO);
        if (milliwatts > 0) {
            out.put(POWER, (float) Math.min(milliwatts, VtxTable.MAX_MW));
        } else {
            out.remove(POWER);
        }
        return out;
    }

    public static Map<String, Float> withAuto(Map<String, Float> tune) {
        TreeMap<String, Float> out = new TreeMap<>(tune);
        out.remove(POWER);
        out.put(AUTO, 1.0F);
        return out;
    }

    public static Map<String, Float> withLowPower(Map<String, Float> tune, LowPower mode) {
        TreeMap<String, Float> out = new TreeMap<>(tune);
        if (mode == LowPower.OFF) {
            out.remove(LOW_POWER);
        } else {
            out.put(LOW_POWER, (float) mode.ordinal());
        }
        return out;
    }

    public static Map<String, Float> next(Map<String, Float> tune, VtxTable table) {
        Selection current = selection(tune, table);
        if (current.auto()) {
            return withLevel(tune, table.levelMw(0));
        }
        if (current.index() >= table.maxIndex()) {
            return table.supportsAuto() ? withAuto(tune) : withLevel(tune, table.levelMw(0));
        }
        return withLevel(tune, table.levelMw(current.index() + 1));
    }

    public static void copyClean(Map<String, Float> from, Map<String, Float> to) {
        Float power = from.get(POWER);
        if (power != null && Float.isFinite(power) && power >= 1.0F) {
            to.put(POWER, (float) Math.min(Math.round(power), VtxTable.MAX_MW));
        }
        if (auto(from)) {
            to.put(AUTO, 1.0F);
        }
        LowPower low = lowPower(from);
        if (low != LowPower.OFF) {
            to.put(LOW_POWER, (float) low.ordinal());
        }
    }
}
