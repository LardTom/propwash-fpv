package de.lardtom.propwash.sim;

final class RotorAero {
    static final double FLEX_LOSS = 0.04;
    static final double FLEX_LOAD_CAP = 4.0;

    private RotorAero() {
    }

    static double zeroThrustSpeed(double zeroThrustPerOmega, double omega) {
        return Math.max(ModelConstants.V0_MIN, zeroThrustPerOmega * omega);
    }

    static double inflowRatio(double axialSpeed, double zeroThrustPerOmega, double omega) {
        return axialSpeed / zeroThrustSpeed(zeroThrustPerOmega, omega);
    }

    static double inducedPerOmega(double kThrust, double discArea) {
        return discArea > 0.0 && kThrust > 0.0 ? Math.sqrt(kThrust / (2.0 * ModelConstants.RHO0 * discArea)) : 0.0;
    }

    static double axialThrustFactor(double lambda) {
        if (lambda >= 0.0) {
            return Math.max(0.0, 1.0 - lambda);
        }
        return 1.0 + ModelConstants.DESCENT_GAIN * Math.min(-lambda, ModelConstants.DESCENT_CAP);
    }

    static double axialThrustFactor(AirframeSpec p, double lambda, double omega, double crossSpeed) {
        double w = Math.abs(omega);
        return axialThrustFactor(lambda, zeroThrustSpeed(p.zeroThrustPerOmega, w), p.inducedPerOmega * w,
                inducedCoupling(crossSpeed, p.hoverInducedVelocity));
    }

    static double axialThrustFactor(double lambda, double pitchSpeed, double inducedSpeed, double coupling) {
        if (lambda <= 0.0 || lambda >= 1.0 || !(inducedSpeed > 0.0) || !(coupling > 0.0)) {
            return axialThrustFactor(lambda);
        }
        double r = pitchSpeed / inducedSpeed;
        if (!(r > ModelConstants.MOMENTUM_MIN_RATIO) || r > ModelConstants.MOMENTUM_MAX_RATIO) {
            return axialThrustFactor(lambda);
        }
        double linear = 1.0 - lambda;
        double k = 1.0 / (r - 1.0);
        double s = lambda * r;
        double b = s + k;
        double c = (r - s) * k;
        double u = 2.0 * c / (b + Math.sqrt(b * b + 4.0 * c));
        double momentum = Math.max(0.0, (r - s - u) * k);
        return linear + coupling * (momentum - linear);
    }

    static double inducedCoupling(double crossSpeed, double hoverInducedVelocity) {
        if (!(crossSpeed > 0.0)) {
            return 1.0;
        }
        if (!(hoverInducedVelocity > 0.0)) {
            return 0.0;
        }
        double mu = crossSpeed / hoverInducedVelocity;
        double mu2 = mu * mu;
        double q = Math.sqrt(mu2 * mu2 + 4.0);
        return 4.0 / (q * (q + mu2));
    }

    static double torqueFactor(double lambda) {
        if (lambda >= 0.0) {
            return Math.max(ModelConstants.Q_MIN, 1.0 - ModelConstants.Q_CLIMB * lambda);
        }
        return 1.0 + ModelConstants.Q_DESCENT * Math.min(-lambda, ModelConstants.DESCENT_CAP);
    }

    static double ductFactor(boolean ducted, double gain, double lambda) {
        if (!ducted) {
            return 1.0;
        }
        if (lambda <= 0.0) {
            return gain;
        }
        return 1.0 + (gain - 1.0) * SimMath.clamp01(1.0 - lambda / ModelConstants.DUCT_FADE);
    }

    static double flexFactor(AirframeSpec p, double omega) {
        if (p.propFlex == 0.0) {
            return 1.0;
        }
        double load = omega / p.hoverOmega;
        double excess = Math.min(Math.max(0.0, load * load - 1.0), FLEX_LOAD_CAP);
        return 1.0 - FLEX_LOSS * p.propFlex * excess / FLEX_LOAD_CAP;
    }

    static double translationalLiftFactor(double crossSpeed, double hoverInducedVelocity, double gain, double scale) {
        double mu = crossSpeed / hoverInducedVelocity;
        double mu2 = mu * mu;
        return 1.0 + scale * gain * mu2 / (1.0 + mu2);
    }

    static double thrust(AirframeSpec p, double omega, double axialSpeed, double crossSpeed, double rhoRatio, double tlScale) {
        double lambda = inflowRatio(axialSpeed, p.zeroThrustPerOmega, omega);
        double t = p.kThrust * rhoRatio * ductFactor(p.ducted, p.ductGain, lambda) * axialThrustFactor(p, lambda, omega, crossSpeed)
                * translationalLiftFactor(crossSpeed, p.hoverInducedVelocity, p.translationalLiftGain, tlScale) * omega * omega;
        return p.propFlex == 0.0 ? t : t * flexFactor(p, omega);
    }

    static double airTorque(AirframeSpec p, double omega, double axialSpeed, double rhoRatio) {
        double lambda = inflowRatio(axialSpeed, p.zeroThrustPerOmega, omega);
        return p.kTorque * rhoRatio * torqueFactor(lambda) * omega * omega;
    }

    static double hForceMagnitude(double thrust, double crossSpeed, double omega, double radius) {
        return ModelConstants.KAPPA_H * thrust * crossSpeed / (omega * radius + ModelConstants.H_TIP_OFFSET);
    }
}
