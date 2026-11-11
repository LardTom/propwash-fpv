package de.lardtom.propwash.drone.collision;

import de.lardtom.propwash.api.drone.BlockContact;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.SurfaceResponse;

import java.util.List;

public final class ContactResponse {
    private ContactResponse() {
    }

    public static SurfaceResponse of(MoveReport report) {
        return new SurfaceResponse(report.surfaceRestitution, report.surfaceGrip, report.surfaceImpactScale);
    }

    public static double startRestitution(SurfaceResponse base, double droneRestitution) {
        double own = base.restitution();
        return restitution(Double.isNaN(own) || own < 0.0 ? droneRestitution : own);
    }

    public static double startGrip(SurfaceResponse base) {
        return grip(base.grip());
    }

    public static double startImpactScale(SurfaceResponse base) {
        return impactScale(base.impactScale());
    }

    public static SurfaceResponse finish(SurfaceResponse base, double startRestitution, double startGrip, double startImpactScale,
                                         double restitution, double grip, double impactScale) {
        double r = Double.compare(restitution, startRestitution) == 0 ? base.restitution() : restitution(restitution);
        double g = Double.compare(grip, startGrip) == 0 ? base.grip() : grip(grip);
        double s = Double.compare(impactScale, startImpactScale) == 0 ? base.impactScale() : impactScale(impactScale);
        if (Double.compare(r, base.restitution()) == 0 && Double.compare(g, base.grip()) == 0
                && Double.compare(s, base.impactScale()) == 0) {
            return base;
        }
        return new SurfaceResponse(r, g, s);
    }

    public static SurfaceResponse chain(SurfaceResponse base, double droneRestitution, List<? extends Step> steps) {
        if (steps.isEmpty()) {
            return base;
        }
        Values values = new Values(base, startRestitution(base, droneRestitution), startGrip(base), startImpactScale(base));
        for (Step step : steps) {
            step.apply(values);
        }
        return finish(base, values.startRestitution, values.startGrip, values.startImpactScale, values.restitution, values.grip,
                values.impactScale);
    }

    public static double restitution(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(BlockContact.MAX_RESTITUTION, value)) : 0.0;
    }

    public static double grip(double value) {
        return Double.isFinite(value) ? Math.max(BlockContact.MIN_GRIP, Math.min(BlockContact.MAX_GRIP, value)) : 1.0;
    }

    public static double impactScale(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(BlockContact.MAX_IMPACT_SCALE, value)) : BlockContact.MAX_IMPACT_SCALE;
    }

    @FunctionalInterface
    public interface Step {
        void apply(Values values);
    }

    public static final class Values {
        private final SurfaceResponse base;
        private final double startRestitution;
        private final double startGrip;
        private final double startImpactScale;
        private double restitution;
        private double grip;
        private double impactScale;

        Values(SurfaceResponse base, double restitution, double grip, double impactScale) {
            this.base = base;
            this.startRestitution = restitution;
            this.startGrip = grip;
            this.startImpactScale = impactScale;
            this.restitution = restitution;
            this.grip = grip;
            this.impactScale = impactScale;
        }

        public SurfaceResponse base() {
            return this.base;
        }

        public double restitution() {
            return this.restitution;
        }

        public double grip() {
            return this.grip;
        }

        public double impactScale() {
            return this.impactScale;
        }

        public void setRestitution(double value) {
            if (Double.isFinite(value)) {
                this.restitution = ContactResponse.restitution(value);
            }
        }

        public void setGrip(double value) {
            if (Double.isFinite(value)) {
                this.grip = ContactResponse.grip(value);
            }
        }

        public void setImpactScale(double value) {
            if (Double.isFinite(value)) {
                this.impactScale = ContactResponse.impactScale(value);
            }
        }
    }
}
