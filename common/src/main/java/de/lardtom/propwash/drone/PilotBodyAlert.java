package de.lardtom.propwash.drone;

public final class PilotBodyAlert {
    public static final long FLASH_NANOS = 700_000_000L;
    public static final long NOTICE_NANOS = 3_000_000_000L;
    public static final long NOTICE_FADE_NANOS = 500_000_000L;
    public static final float MIN_HIT = 0.01F;
    public static final float CRITICAL_SHARE = 0.3F;
    public static final float FLASH_MIN = 0.35F;
    public static final float FLASH_MAX = 0.85F;
    public static final float FLASH_PER_SHARE = 2.0F;

    public enum Notice {
        NONE,
        HIT,
        CRITICAL,
        AIR
    }

    private boolean known;
    private float lastTotal;
    private boolean hitSeen;
    private long hitAt;
    private float hitPeak;
    private float health;
    private float maxHealth = 20.0F;
    private int air;
    private int maxAir = 300;
    private int hits;

    public void reset() {
        this.known = false;
        this.hitSeen = false;
        this.hitPeak = 0.0F;
    }

    public boolean update(float health, float absorption, float maxHealth, int air, int maxAir, long now) {
        float total = Math.max(0.0F, health) + Math.max(0.0F, absorption);
        this.health = Math.max(0.0F, health);
        this.maxHealth = maxHealth > 0.0F ? maxHealth : 20.0F;
        this.air = air;
        this.maxAir = maxAir > 0 ? maxAir : 300;
        if (!this.known) {
            this.known = true;
            this.lastTotal = total;
            return false;
        }
        float damage = this.lastTotal - total;
        this.lastTotal = total;
        if (damage < MIN_HIT) {
            return false;
        }
        float peak = Math.clamp(FLASH_MIN + FLASH_PER_SHARE * damage / this.maxHealth, FLASH_MIN, FLASH_MAX);
        this.hitPeak = Math.max(peak, this.flash(now));
        this.hitAt = now;
        this.hitSeen = true;
        this.hits++;
        return true;
    }

    public float flash(long now) {
        if (!this.hitSeen) {
            return 0.0F;
        }
        long age = now - this.hitAt;
        if (age < 0L || age >= FLASH_NANOS) {
            return 0.0F;
        }
        float left = 1.0F - (float) age / FLASH_NANOS;
        return this.hitPeak * left * left;
    }

    public Notice notice(long now) {
        if (!this.known) {
            return Notice.NONE;
        }
        if (this.critical()) {
            return Notice.CRITICAL;
        }
        if (this.air < this.maxAir) {
            return Notice.AIR;
        }
        if (this.hitSeen && now - this.hitAt >= 0L && now - this.hitAt < NOTICE_NANOS) {
            return Notice.HIT;
        }
        return Notice.NONE;
    }

    public float noticeAlpha(long now) {
        Notice notice = this.notice(now);
        if (notice != Notice.HIT) {
            return notice == Notice.NONE ? 0.0F : 1.0F;
        }
        long left = NOTICE_NANOS - (now - this.hitAt);
        return left >= NOTICE_FADE_NANOS ? 1.0F : Math.max(0.0F, (float) left / NOTICE_FADE_NANOS);
    }

    public boolean critical() {
        return this.known && this.health > 0.0F && this.health <= this.maxHealth * CRITICAL_SHARE;
    }

    public float healthShare() {
        return Math.clamp(this.health / this.maxHealth, 0.0F, 1.0F);
    }

    public int airPercent() {
        return Math.clamp(Math.round(100.0F * Math.max(0, this.air) / this.maxAir), 0, 100);
    }

    public int hits() {
        return this.hits;
    }

    public boolean known() {
        return this.known;
    }
}
