package de.lardtom.propwash.video.vtx;

public final class VtxUnit {
    public static final double PROTECT_STEP_S = 4.0;

    private final VtxTable table;
    private final VtxAuto auto = new VtxAuto();
    private final VtxHeat heat;
    private boolean autoMode;
    private int selected;
    private VtxPowerSettings.LowPower lowPower = VtxPowerSettings.LowPower.OFF;
    private boolean everArmed;
    private boolean lowPowerActive;
    private boolean limitsOn = true;
    private int wanted;
    private int effective;
    private int cap;
    private double protectTimer;

    public VtxUnit(VtxTable table, double ambientC) {
        this.table = table;
        this.heat = VtxHeat.of(table, ambientC);
        this.selected = table.ratedIndex();
        this.wanted = this.selected;
        this.effective = this.selected;
        this.cap = table.maxIndex();
    }

    public VtxTable table() {
        return this.table;
    }

    public void configure(VtxPowerSettings.Selection selection, VtxPowerSettings.LowPower mode) {
        boolean wasAuto = this.autoMode;
        this.autoMode = selection.auto() && this.table.supportsAuto();
        this.selected = this.table.clamp(selection.index());
        this.lowPower = mode == null ? VtxPowerSettings.LowPower.OFF : mode;
        if (this.autoMode && !wasAuto) {
            this.auto.reset(this.effective);
        }
    }

    public int update(double dt, boolean armed, double airSpeed, double ambientC, double ratedMarginDb, boolean thermalLimits) {
        double step = Double.isFinite(dt) && dt > 0.0 ? dt : 0.0;
        if (armed) {
            this.everArmed = true;
        }
        this.limitsOn = thermalLimits;
        int base = this.autoMode ? this.auto.update(step, ratedMarginDb, this.table) : this.selected;
        boolean lowWanted = this.lowPower == VtxPowerSettings.LowPower.DISARMED
                || (this.lowPower == VtxPowerSettings.LowPower.UNTIL_ARMED && !this.everArmed);
        this.lowPowerActive = !armed && lowWanted && base > 0;
        this.wanted = this.lowPowerActive ? 0 : base;
        this.effective = thermalLimits ? Math.min(this.wanted, this.cap) : this.wanted;
        this.heat.step(step, this.table.heatW(this.effective), airSpeed, ambientC);
        this.protect(step, thermalLimits);
        return this.effective;
    }

    private void protect(double dt, boolean enabled) {
        if (!enabled) {
            this.cap = this.table.maxIndex();
            this.protectTimer = 0.0;
            return;
        }
        double t = this.heat.tempC();
        if (t >= VtxHeat.LIMIT_C) {
            if (this.cap >= this.wanted) {
                if (this.effective > 0) {
                    this.cap = this.effective - 1;
                }
                this.protectTimer = 0.0;
                return;
            }
            this.protectTimer += dt;
            if (this.protectTimer >= PROTECT_STEP_S && this.cap > 0) {
                this.cap--;
                this.protectTimer = 0.0;
            }
            return;
        }
        if (t <= VtxHeat.RELEASE_C && this.cap < this.table.maxIndex()) {
            if (this.cap >= this.wanted) {
                this.cap = this.table.maxIndex();
                this.protectTimer = 0.0;
                return;
            }
            this.protectTimer += dt;
            if (this.protectTimer >= PROTECT_STEP_S) {
                this.cap++;
                this.protectTimer = 0.0;
            }
            return;
        }
        this.protectTimer = 0.0;
    }

    public void setTemperature(double tempC) {
        this.heat.set(tempC);
    }

    public boolean auto() {
        return this.autoMode;
    }

    public int selectedIndex() {
        return this.selected;
    }

    public int effectiveIndex() {
        return this.effective;
    }

    public int effectiveMw() {
        return this.table.levelMw(this.effective);
    }

    public double gainDb() {
        return this.table.gainDb(this.effective);
    }

    public double reachFactor() {
        return this.table.reachFactor(this.effective);
    }

    public double extraW() {
        return this.table.extraW(this.effective);
    }

    public double supplyW() {
        return this.table.supplyW(this.effective);
    }

    public double tempC() {
        return this.heat.tempC();
    }

    public boolean lowPowerActive() {
        return this.lowPowerActive;
    }

    public boolean throttled() {
        return this.limitsOn && this.effective < this.wanted;
    }

    public boolean hot() {
        return this.limitsOn && this.heat.tempC() >= VtxHeat.WARN_C;
    }

    public VtxPowerSettings.LowPower lowPower() {
        return this.lowPower;
    }
}
