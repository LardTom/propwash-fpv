package de.lardtom.propwash.trick;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class TrickScore {
    public static final double CONFIRM_SECONDS = 0.45;
    public static final double COMBO_WINDOW = 3.0;
    public static final double COMBO_STEP = 0.5;
    public static final double COMBO_MAX = 4.0;
    public static final double REPEAT_FACTOR = 0.6;
    public static final double NEAR_CLOSE = 1.0;
    public static final double NEAR_CLOSE_BONUS = 0.5;
    public static final double NEAR = 2.0;
    public static final double NEAR_BONUS = 0.25;
    public static final double CLEAN_BONUS = 0.2;
    public static final double CLEAN_MAX_RATE = 300.0;
    public static final double CLEAN_MIN_UP = 0.3;
    public static final int MAX_PENDING = 8;
    public static final double CHAIN_GAP = 0.5;
    public static final double CHAIN_BONUS = 0.25;
    public static final int POPUP_TYPES = 3;
    public static final double POPUP_MERGE_SECONDS = 2.0;

    public enum Popup {
        NONE,
        TRICK,
        COMBO,
        LOST
    }

    private final Trick[] pending = new Trick[MAX_PENDING];
    private int pendingCount;
    private final EnumMap<TrickType, Integer> comboUses = new EnumMap<>(TrickType.class);
    private final List<Trick> awarded = new ArrayList<>();
    private int comboCount;
    private int comboPoints;
    private double comboLast;
    private int flightScore;
    private int bestCombo;
    private int bestComboPoints;
    private int tricks;
    private Popup popup = Popup.NONE;
    private TrickType popupType = TrickType.FLIP;
    private final TrickType[] popupTypes = new TrickType[POPUP_TYPES];
    private int popupTypeCount;
    private int popupTricks;
    private boolean popupChain;
    private boolean batchOpen;
    private double lastEnd = Double.NaN;
    private int chain;
    private int bestChain;
    private int popupPoints;
    private int popupCombo;
    private boolean popupClean;
    private boolean popupNear;
    private double popupTime;
    private double now;
    private boolean wasArmed;
    private int lastCrash;
    private boolean hasLast;

    public TrickScore() {
    }

    public void reset() {
        this.resetFlight();
        this.awarded.clear();
        this.wasArmed = false;
        this.hasLast = false;
        this.now = 0.0;
    }

    public void resetFlight() {
        this.pendingCount = 0;
        this.clearCombo();
        this.flightScore = 0;
        this.bestCombo = 0;
        this.bestComboPoints = 0;
        this.tricks = 0;
        this.popup = Popup.NONE;
        this.popupTypeCount = 0;
        this.popupTricks = 0;
        this.bestChain = 0;
        this.endChain();
    }

    private void endChain() {
        this.lastEnd = Double.NaN;
        this.chain = 0;
    }

    private void clearCombo() {
        this.comboCount = 0;
        this.comboPoints = 0;
        this.comboUses.clear();
    }

    public void update(TrickSample s, double upY, double tumbleRate, boolean contact, List<Trick> found) {
        this.awarded.clear();
        if (!Double.isFinite(s.time)) {
            return;
        }
        this.now = s.time;
        if (s.armed && !this.wasArmed) {
            this.resetFlight();
        }
        boolean crashed = this.hasLast && s.crashCount > this.lastCrash;
        boolean disarmed = this.wasArmed && !s.armed;
        this.wasArmed = s.armed;
        this.lastCrash = s.crashCount;
        this.hasLast = true;
        if (crashed || disarmed) {
            this.breakCombo();
            return;
        }
        if (!s.armed) {
            return;
        }
        if (contact) {
            this.pendingCount = 0;
        }
        for (Trick trick : found) {
            if (this.pendingCount < MAX_PENDING) {
                this.pending[this.pendingCount++] = trick;
            }
        }
        boolean clean = tumbleRate < CLEAN_MAX_RATE && upY >= CLEAN_MIN_UP;
        this.batchOpen = false;
        int kept = 0;
        for (int i = 0; i < this.pendingCount; i++) {
            Trick trick = this.pending[i];
            if (this.now - trick.time() >= CONFIRM_SECONDS) {
                this.award(trick, clean);
            } else {
                this.pending[kept++] = trick;
            }
        }
        for (int i = kept; i < this.pendingCount; i++) {
            this.pending[i] = null;
        }
        this.pendingCount = kept;
        if (this.comboCount > 0 && this.pendingCount == 0 && this.now - this.comboLast > COMBO_WINDOW) {
            this.bank();
        }
    }

    private void award(Trick trick, boolean clean) {
        this.awarded.add(trick);
        TrickType type = trick.type();
        if (type.line()) {
            this.awardLine(trick);
            return;
        }
        if (this.comboCount > 0 && trick.time() - this.comboLast > COMBO_WINDOW) {
            this.bank();
        }
        int uses = this.comboUses.getOrDefault(type, 0);
        double style = 1.0;
        boolean near = false;
        if (type.proximityStyle()) {
            double clearance = trick.minClearance();
            if (clearance <= NEAR_CLOSE) {
                style += NEAR_CLOSE_BONUS;
                near = true;
            } else if (clearance <= NEAR) {
                style += NEAR_BONUS;
                near = true;
            }
        }
        boolean cleanRecovery = type.recovers() && clean;
        if (cleanRecovery) {
            style += CLEAN_BONUS;
        }
        boolean linked = Double.isFinite(this.lastEnd) && trick.start() - this.lastEnd <= CHAIN_GAP;
        this.chain = linked ? this.chain + 1 : 1;
        this.bestChain = Math.max(this.bestChain, this.chain);
        this.lastEnd = Double.isFinite(this.lastEnd) ? Math.max(this.lastEnd, trick.time()) : trick.time();
        if (linked) {
            style += CHAIN_BONUS;
        }
        int points = round5(type.points(trick.magnitude(), trick.minClearance()) * Math.pow(REPEAT_FACTOR, uses) * style);
        this.comboUses.put(type, uses + 1);
        this.comboCount++;
        this.comboPoints += points;
        this.comboLast = Math.max(this.comboLast, trick.time());
        if (this.comboCount == 1) {
            this.comboLast = trick.time();
        }
        this.flightScore += points;
        this.tricks++;
        boolean merge = (this.batchOpen || linked) && this.popup == Popup.TRICK && this.now - this.popupTime < POPUP_MERGE_SECONDS;
        if (merge) {
            this.popupTime = this.now;
            this.popupType = type;
            this.popupPoints += points;
            this.popupCombo = this.comboCount;
            this.popupTricks++;
            if (this.popupTypeCount < POPUP_TYPES) {
                this.popupTypes[this.popupTypeCount++] = type;
            } else {
                System.arraycopy(this.popupTypes, 1, this.popupTypes, 0, POPUP_TYPES - 1);
                this.popupTypes[POPUP_TYPES - 1] = type;
            }
            this.popupClean &= cleanRecovery;
            this.popupNear |= near;
        } else {
            this.show(Popup.TRICK, type, points, this.comboCount);
            this.popupTypes[0] = type;
            this.popupTypeCount = 1;
            this.popupTricks = 1;
            this.popupClean = cleanRecovery;
            this.popupNear = near;
            this.batchOpen = true;
        }
        this.popupChain = this.chain >= 2;
    }

    private void awardLine(Trick trick) {
        TrickType type = trick.type();
        int uses = this.comboUses.getOrDefault(type, 0);
        int points = round5(type.points(trick.magnitude(), Double.POSITIVE_INFINITY) * Math.pow(REPEAT_FACTOR, uses));
        this.comboUses.put(type, uses + 1);
        this.comboPoints += points;
        this.flightScore += points;
        this.tricks++;
        if (this.comboCount == 0) {
            this.comboCount = 1;
            this.comboLast = trick.time();
        }
        if (this.popup == Popup.TRICK && this.now - this.popupTime < POPUP_MERGE_SECONDS) {
            this.popupTime = this.now;
            this.popupType = type;
            this.popupPoints += points;
            if (this.popupTypeCount < POPUP_TYPES) {
                this.popupTypes[this.popupTypeCount++] = type;
            } else {
                System.arraycopy(this.popupTypes, 1, this.popupTypes, 0, POPUP_TYPES - 1);
                this.popupTypes[POPUP_TYPES - 1] = type;
            }
        } else {
            this.show(Popup.TRICK, type, points, this.comboCount);
            this.popupTypes[0] = type;
            this.popupTypeCount = 1;
            this.popupTricks = 1;
            this.batchOpen = true;
        }
    }

    private void bank() {
        int count = this.comboCount;
        if (count <= 0) {
            return;
        }
        int bonus = round5(this.comboPoints * (multiplier(count) - 1.0));
        this.flightScore += bonus;
        int total = this.comboPoints + bonus;
        if (count > this.bestCombo || (count == this.bestCombo && total > this.bestComboPoints)) {
            this.bestCombo = count;
            this.bestComboPoints = total;
        }
        if (count >= 2) {
            this.show(Popup.COMBO, this.popupType, bonus, count);
        }
        this.clearCombo();
    }

    private void breakCombo() {
        this.pendingCount = 0;
        this.endChain();
        if (this.comboCount >= 2) {
            this.show(Popup.LOST, this.popupType, 0, this.comboCount);
        }
        this.clearCombo();
    }

    private void show(Popup kind, TrickType type, int points, int combo) {
        this.popup = kind;
        this.popupType = type;
        this.popupPoints = points;
        this.popupCombo = combo;
        this.popupTime = this.now;
        this.popupClean = false;
        this.popupNear = false;
        this.popupChain = false;
        this.popupTypeCount = 0;
        this.popupTricks = 0;
    }

    public static double multiplier(int count) {
        return Math.min(COMBO_MAX, 1.0 + COMBO_STEP * Math.max(0, count - 1));
    }

    public static int round5(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 0;
        }
        return (int) Math.round(value / 5.0) * 5;
    }

    public int flightScore() {
        return this.flightScore;
    }

    public int bestCombo() {
        return this.bestCombo;
    }

    public int bestComboPoints() {
        return this.bestComboPoints;
    }

    public List<Trick> awarded() {
        return this.awarded;
    }

    public int tricks() {
        return this.tricks;
    }

    public int comboCount() {
        return this.comboCount;
    }

    public int comboPoints() {
        return this.comboPoints;
    }

    public int pendingCount() {
        return this.pendingCount;
    }

    public Popup popup() {
        return this.popup;
    }

    public TrickType popupType() {
        return this.popupType;
    }

    public int popupPoints() {
        return this.popupPoints;
    }

    public int popupCombo() {
        return this.popupCombo;
    }

    public boolean popupClean() {
        return this.popupClean;
    }

    public boolean popupNear() {
        return this.popupNear;
    }

    public boolean popupChain() {
        return this.popupChain;
    }

    public int popupTypeCount() {
        return this.popupTypeCount;
    }

    public TrickType popupType(int index) {
        return this.popupTypes[index];
    }

    public int popupTricks() {
        return this.popupTricks;
    }

    public int chain() {
        return this.chain;
    }

    public int bestChain() {
        return this.bestChain;
    }

    public double popupAge() {
        return this.popup == Popup.NONE ? Double.POSITIVE_INFINITY : Math.max(0.0, this.now - this.popupTime);
    }
}
