package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.sim.FpvRig;

import java.util.List;

public final class Footprints {
    private Footprints() {
    }

    public static double diameterMm(double[][] hubs, double propRadiusMm) {
        if (hubs.length == 0 || !(propRadiusMm >= 0.0)) {
            return Double.NaN;
        }
        double widest = 0.0;
        for (int i = 0; i < hubs.length; i++) {
            for (int j = i + 1; j < hubs.length; j++) {
                double dx = hubs[i][0] - hubs[j][0];
                double dz = hubs[i][2] - hubs[j][2];
                widest = Math.max(widest, Math.sqrt(dx * dx + dz * dz));
            }
        }
        return widest + 2.0 * propRadiusMm;
    }

    public static double of(FpvRig rig) {
        double[][] hubs = new double[FpvRig.SLOTS][];
        for (int slot = 0; slot < FpvRig.SLOTS; slot++) {
            hubs[slot] = rig.hubMm(slot);
        }
        return diameterMm(hubs, rig.radiusMm());
    }

    public static double of(FrameDefinition frame, PropDefinition prop) {
        double[][] hubs = new double[MotorArm.COUNT][];
        List<MotorArm> arms = frame.geometry().arms();
        if (frame.geometry().hasExplicitArms()) {
            for (int slot = 0; slot < MotorArm.COUNT; slot++) {
                hubs[slot] = new double[]{arms.get(slot).position().x(), arms.get(slot).position().y(), arms.get(slot).position().z()};
            }
        } else {
            double s = frame.wheelbaseMm() / (2.0 * Math.sqrt(2.0));
            hubs[0] = new double[]{-s, 0.0, -s};
            hubs[1] = new double[]{s, 0.0, -s};
            hubs[2] = new double[]{-s, 0.0, s};
            hubs[3] = new double[]{s, 0.0, s};
        }
        return diameterMm(hubs, prop.diameterMm() * 0.5);
    }

    public static double fromRegistry(BuildSpec spec) {
        FrameDefinition frame = PropwashApi.parts().get(FrameDefinition.class, spec.frame())
                .orElseThrow(() -> new IllegalArgumentException("Unbekannter Rahmen im Bauplan: " + spec.frame()));
        PropDefinition prop = PropwashApi.parts().get(PropDefinition.class, spec.prop())
                .orElseThrow(() -> new IllegalArgumentException("Unbekannter Prop im Bauplan: " + spec.prop()));
        return of(frame, prop);
    }
}
