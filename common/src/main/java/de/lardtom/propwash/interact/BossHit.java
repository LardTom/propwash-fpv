package de.lardtom.propwash.interact;

public enum BossHit {
    SONIC_BOOM(3.0, 24.0, 5.0),
    DRAGON_WING(0.7, 0.0, 4.0),
    DRAGON_RAM(2.2, 18.0, 4.0),
    DRAGON_BREATH(0.0, 0.0, 0.0);

    private final double impactFactor;
    private final double push;
    private final double lift;

    BossHit(double impactFactor, double push, double lift) {
        this.impactFactor = impactFactor;
        this.push = push;
        this.lift = lift;
    }

    public double impactFactor() {
        return this.impactFactor;
    }

    public double push() {
        return this.push;
    }

    public double lift() {
        return this.lift;
    }
}
