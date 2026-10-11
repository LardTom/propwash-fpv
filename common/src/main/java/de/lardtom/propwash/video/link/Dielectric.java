package de.lardtom.propwash.video.link;

public record Dielectric(double real, double imaginary) {
    public static final double ICE_DENSITY = 0.916;

    public static Dielectric p2040(double a, double b, double c, double d, double frequencyGhz) {
        double real = a * Math.pow(frequencyGhz, b);
        double conductivity = c * Math.pow(frequencyGhz, d);
        return new Dielectric(real, 17.98 * conductivity / frequencyGhz);
    }

    public static Dielectric pureWater(double frequencyGhz, double celsius) {
        double theta = 300.0 / (celsius + 273.15) - 1.0;
        double static0 = 77.66 + 103.3 * theta;
        double first = 0.0671 * static0;
        double infinite = 3.52 - 7.52 * theta;
        double f1 = 20.20 - 146.4 * theta + 316.0 * theta * theta;
        double f2 = 39.8 * f1;
        double r1 = frequencyGhz / f1;
        double r2 = frequencyGhz / f2;
        double real = (static0 - first) / (1.0 + r1 * r1) + (first - infinite) / (1.0 + r2 * r2) + infinite;
        double imaginary = r1 * (static0 - first) / (1.0 + r1 * r1) + r2 * (first - infinite) / (1.0 + r2 * r2);
        return new Dielectric(real, imaginary);
    }

    public static Dielectric pureIce(double frequencyGhz, double celsius) {
        double kelvin = celsius + 273.15;
        double theta = 300.0 / kelvin - 1.0;
        double real = 3.1884 + 0.00091 * celsius;
        double a = (0.00504 + 0.0062 * theta) * Math.exp(-22.1 * theta);
        double tau = 335.0 / kelvin;
        double e = Math.exp(-tau);
        double b = 0.0207 / kelvin * e / ((e - 1.0) * (e - 1.0)) + 1.16E-11 * frequencyGhz * frequencyGhz
                + Math.exp(-9.963 + 0.0372 * celsius);
        return new Dielectric(real, a / frequencyGhz + b * frequencyGhz);
    }

    public static Dielectric drySnow(double densityGcm3, double frequencyGhz, double celsius) {
        Dielectric ice = pureIce(frequencyGhz, celsius);
        double real = densityGcm3 <= 0.5 ? 1.0 + 1.9 * densityGcm3 : 0.51 + 2.88 * densityGcm3;
        double iceFraction = densityGcm3 / ICE_DENSITY;
        double imaginary = 3.0 * ice.imaginary * iceFraction * real * real * (2.0 * real + 1.0)
                / ((ice.real + 2.0 * real) * (ice.real + 2.0 * real * real));
        return new Dielectric(real, imaginary);
    }

    public static Dielectric conductor(double conductivity, double frequencyGhz) {
        return new Dielectric(1.0, 17.98 * conductivity / frequencyGhz);
    }

    public double attenuationDbPerMeter(double frequencyGhz) {
        double k0 = 2.0 * Math.PI * frequencyGhz * 1.0E9 / RadioBand.SPEED_OF_LIGHT;
        return 20.0 * Math.log10(Math.E) * k0 * this.rootImaginary();
    }

    public double interfaceLossDb() {
        double reflection = this.reflectionPower();
        return -20.0 * Math.log10(1.0 - reflection);
    }

    public double reflectionPower() {
        double nr = this.rootReal();
        double ni = -this.rootImaginary();
        double numRe = 1.0 - nr;
        double numIm = -ni;
        double denRe = 1.0 + nr;
        double denIm = ni;
        return (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm);
    }

    public double thinSlabLossDb(double thicknessMeters, double frequencyGhz) {
        double k0 = 2.0 * Math.PI * frequencyGhz * 1.0E9 / RadioBand.SPEED_OF_LIGHT;
        double nr = this.rootReal();
        double ni = -this.rootImaginary();
        double gRe;
        double gIm;
        double denRe = (1.0 + nr) * (1.0 + nr) + ni * ni;
        gRe = ((1.0 - nr) * (1.0 + nr) - ni * ni) / denRe;
        gIm = (-ni * (1.0 + nr) - (1.0 - nr) * ni) / denRe;
        double g2Re = gRe * gRe - gIm * gIm;
        double g2Im = 2.0 * gRe * gIm;
        double qRe = k0 * nr * thicknessMeters;
        double qIm = k0 * ni * thicknessMeters;
        double decay = Math.exp(qIm);
        double e1Re = decay * Math.cos(qRe);
        double e1Im = -decay * Math.sin(qRe);
        double decay2 = Math.exp(2.0 * qIm);
        double e2Re = decay2 * Math.cos(2.0 * qRe);
        double e2Im = -decay2 * Math.sin(2.0 * qRe);
        double topRe = (1.0 - g2Re) * e1Re + g2Im * e1Im;
        double topIm = (1.0 - g2Re) * e1Im - g2Im * e1Re;
        double botRe = 1.0 - (g2Re * e2Re - g2Im * e2Im);
        double botIm = -(g2Re * e2Im + g2Im * e2Re);
        double magnitude = Math.sqrt((topRe * topRe + topIm * topIm) / (botRe * botRe + botIm * botIm));
        return -20.0 * Math.log10(magnitude);
    }

    private double rootReal() {
        double modulus = Math.hypot(this.real, this.imaginary);
        return Math.sqrt(0.5 * (modulus + this.real));
    }

    private double rootImaginary() {
        double modulus = Math.hypot(this.real, this.imaginary);
        return Math.sqrt(Math.max(0.0, 0.5 * (modulus - this.real)));
    }
}
