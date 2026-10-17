package de.lardtom.propwash.video.client;

import de.lardtom.propwash.video.FisheyeLens;
import de.lardtom.propwash.video.ImageArea;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class FpvLens {
    public static final FpvLens INSTANCE = new FpvLens();
    public static final int NO_TEST = -1;

    private volatile @Nullable FisheyeLens rendered;
    private volatile int testStrength = NO_TEST;

    private FpvLens() {
    }

    public int strengthPercent() {
        int forced = this.testStrength;
        return forced >= 0 ? forced : VideoSettings.get().lensStrength;
    }

    public double strength() {
        return this.strengthPercent() / 100.0;
    }

    public boolean fisheye() {
        return this.strengthPercent() > 0;
    }

    public @Nullable FisheyeLens lens(double horizontalDeg, ImageArea area) {
        int percent = this.strengthPercent();
        if (percent <= 0 || !VideoPass.INSTANCE.lensReady(Util.getNanos())) {
            return null;
        }
        return FisheyeLens.of(horizontalDeg, percent / 100.0, area);
    }

    public @Nullable FisheyeLens rendered() {
        return this.rendered;
    }

    void latch(@Nullable FisheyeLens lens) {
        this.rendered = lens;
    }

    public void clear() {
        this.rendered = null;
    }

    public void testStrength(int percent) {
        this.testStrength = percent < 0 ? NO_TEST : Math.min(percent, FisheyeLens.MAX_PERCENT);
    }

    public int testStrength() {
        return this.testStrength;
    }
}
