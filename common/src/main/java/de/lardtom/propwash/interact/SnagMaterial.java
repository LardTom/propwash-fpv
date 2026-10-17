package de.lardtom.propwash.interact;

public enum SnagMaterial {
    WEB("web", 12.0, 4.0, 4.0, 1.0, 0.02),
    STRING("string", 3.0, 1.2, 0.0, 2.0, 0.03),
    FOLIAGE("foliage", 1.0, 0.25, 0.0, 0.3, 0.005);

    public static final SnagMaterial[] VALUES = values();
    public static final int COUNT = VALUES.length;

    private final String id;
    private final double linear;
    private final double quadratic;
    private final double hold;
    private final double propLoad;
    private final double propWear;

    SnagMaterial(String id, double linear, double quadratic, double hold, double propLoad, double propWear) {
        this.id = id;
        this.linear = linear;
        this.quadratic = quadratic;
        this.hold = hold;
        this.propLoad = propLoad;
        this.propWear = propWear;
    }

    public String id() {
        return this.id;
    }

    public double linear() {
        return this.linear;
    }

    public double quadratic() {
        return this.quadratic;
    }

    public double hold() {
        return this.hold;
    }

    public double propLoad() {
        return this.propLoad;
    }

    public double propWear() {
        return this.propWear;
    }

    public static double maxPropWear() {
        double max = 0.0;
        for (SnagMaterial m : VALUES) {
            max = Math.max(max, m.propWear);
        }
        return max;
    }
}
