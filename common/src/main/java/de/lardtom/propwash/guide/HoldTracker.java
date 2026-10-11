package de.lardtom.propwash.guide;

import java.util.Objects;

public final class HoldTracker {
    public static final long HOLD_MILLIS = 650L;
    public static final long HINT_DELAY_MILLIS = 350L;

    private final long holdMillis;
    private final long hintDelayMillis;
    private String target = "";
    private long hoverStart = Long.MIN_VALUE;
    private long holdStart = -1L;
    private boolean consumed;

    public HoldTracker() {
        this(HOLD_MILLIS, HINT_DELAY_MILLIS);
    }

    public HoldTracker(long holdMillis, long hintDelayMillis) {
        this.holdMillis = Math.max(1L, holdMillis);
        this.hintDelayMillis = Math.max(0L, hintDelayMillis);
    }

    public Result update(String target, boolean hasScenes, boolean keyDown, long now) {
        String key = target == null ? "" : target;
        if (!Objects.equals(key, this.target)) {
            this.target = key;
            this.hoverStart = now;
            this.holdStart = -1L;
        }
        if (!keyDown) {
            this.holdStart = -1L;
            this.consumed = false;
        }
        if (!hasScenes || key.isEmpty()) {
            this.holdStart = -1L;
            return Result.NONE;
        }
        boolean hint = keyDown || now - this.hoverStart >= this.hintDelayMillis;
        if (!keyDown || this.consumed) {
            return new Result(hint, 0.0F, false);
        }
        if (this.holdStart < 0L) {
            this.holdStart = now;
        }
        float progress = Math.min(1.0F, (now - this.holdStart) / (float) this.holdMillis);
        if (progress >= 1.0F) {
            this.consumed = true;
            this.holdStart = -1L;
            return new Result(true, 1.0F, true);
        }
        return new Result(true, progress, false);
    }

    public void reset() {
        this.target = "";
        this.hoverStart = Long.MIN_VALUE;
        this.holdStart = -1L;
        this.consumed = false;
    }

    public record Result(boolean hint, float progress, boolean fire) {
        public static final Result NONE = new Result(false, 0.0F, false);
    }
}
