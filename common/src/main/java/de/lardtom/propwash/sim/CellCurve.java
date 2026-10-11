package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.build.BatteryType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CellCurve {
    private static final double[] SOC = toArray(CellChemistry.SOC_GRID);
    private static final Map<BatteryType.Chemistry, double[]> SHAPES = new ConcurrentHashMap<>();

    private CellCurve() {
    }

    private static double[] toArray(List<Double> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    private static double[] shapeOf(BatteryType.Chemistry chemistry) {
        return SHAPES.computeIfAbsent(chemistry, c -> toArray(c.definition().ocvShape()));
    }

    public static double shape(BatteryType.Chemistry chemistry, double soc) {
        double[] u = shapeOf(chemistry);
        double s = SimMath.clamp01(soc);
        int i = 1;
        while (i < SOC.length - 1 && s > SOC[i]) {
            i++;
        }
        double t = (s - SOC[i - 1]) / (SOC[i] - SOC[i - 1]);
        return u[i - 1] + (u[i] - u[i - 1]) * t;
    }

    public static double ocv(BatteryType.Chemistry chemistry, double soc) {
        double full = chemistry.fullCellVoltage;
        double empty = chemistry.emptyCellVoltage;
        double cutoff = chemistry.cutoffCellVoltage;
        if (Double.isNaN(soc)) {
            return empty;
        }
        if (soc >= 0.0) {
            return empty + (full - empty) * shape(chemistry, soc);
        }
        double s = Math.max(soc, ModelConstants.SOC_MIN);
        if (s >= -0.05) {
            return empty + (cutoff - empty) * (s / -0.05);
        }
        return cutoff + (0.8 * cutoff - cutoff) * ((s + 0.05) / -0.05);
    }
}
