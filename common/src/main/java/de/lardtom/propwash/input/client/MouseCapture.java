package de.lardtom.propwash.input.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLPen;
import org.lwjgl.sdl.SDLTimer;
import org.lwjgl.sdl.SDLTouch;
import org.lwjgl.sdl.SDLVideo;

import java.util.Arrays;

public final class MouseCapture {
    public static final MouseCapture INSTANCE = new MouseCapture();
    private static final int MAX_EVENTS = 4096;
    private static final long[] NO_TIMES = new long[0];
    private static final float[] NO_COUNTS = new float[0];

    private final long[] times = new long[MAX_EVENTS];
    private final float[] xs = new float[MAX_EVENTS];
    private final float[] ys = new float[MAX_EVENTS];
    private int events;
    private float flightX;
    private float flightY;
    private float previewX;
    private float previewY;
    private float wheel;
    private boolean acceptFlight;
    private boolean acceptWheel;
    private Frame frame = Frame.EMPTY;
    private long windowHandle;
    private int windowId;
    private float simulatedX;
    private float simulatedY;
    private long simulatedAt;

    private MouseCapture() {
    }

    public static long now() {
        return SDLTimer.SDL_GetTicksNS();
    }

    public void onMotion(long timestamp, int eventWindow, int which, float xrel, float yrel) {
        if (which == SDLTouch.SDL_TOUCH_MOUSEID || which == SDLPen.SDL_PEN_MOUSEID || !Float.isFinite(xrel) || !Float.isFinite(yrel)) {
            return;
        }
        if (!this.ownWindow(eventWindow)) {
            return;
        }
        this.previewX += xrel;
        this.previewY += yrel;
        if (this.acceptFlight) {
            this.record(timestamp, xrel, yrel);
        }
    }

    public boolean onWheel(int eventWindow, float y, boolean flipped) {
        if (!this.acceptWheel || !Float.isFinite(y) || !this.ownWindow(eventWindow)) {
            return false;
        }
        this.wheel += flipped ? -y : y;
        return true;
    }

    public void inject(float dx, float dy, float notches) {
        if (Float.isFinite(dx) && Float.isFinite(dy)) {
            this.previewX += dx;
            this.previewY += dy;
            this.record(now(), dx, dy);
        }
        if (Float.isFinite(notches)) {
            this.wheel += notches;
        }
    }

    public void simulateVelocity(float countsPerSecondX, float countsPerSecondY) {
        this.simulatedX = Float.isFinite(countsPerSecondX) ? countsPerSecondX : 0.0F;
        this.simulatedY = Float.isFinite(countsPerSecondY) ? countsPerSecondY : 0.0F;
        this.simulatedAt = 0L;
    }

    private void simulate(long now) {
        if (this.simulatedX == 0.0F && this.simulatedY == 0.0F) {
            this.simulatedAt = 0L;
            return;
        }
        long since = this.simulatedAt;
        this.simulatedAt = now;
        if (since == 0L || now <= since) {
            return;
        }
        long span = Math.min(now - since, 100_000_000L);
        float seconds = span / 1.0E9F;
        for (int i = 0; i < 8; i++) {
            float x = this.simulatedX * seconds / 8.0F;
            float y = this.simulatedY * seconds / 8.0F;
            this.previewX += x;
            this.previewY += y;
            this.record(now - span + span * (2L * i + 1L) / 16L, x, y);
        }
    }

    private void record(long timestamp, float dx, float dy) {
        this.flightX += dx;
        this.flightY += dy;
        if (this.events < MAX_EVENTS) {
            this.times[this.events] = timestamp;
            this.xs[this.events] = dx;
            this.ys[this.events] = dy;
            this.events++;
        } else {
            this.xs[MAX_EVENTS - 1] += dx;
            this.ys[MAX_EVENTS - 1] += dy;
        }
    }

    Frame take() {
        long now = now();
        this.simulate(now);
        int count = this.events;
        Frame taken = new Frame(this.flightX, this.flightY, this.wheel, this.previewX, this.previewY,
                count == 0 ? NO_TIMES : Arrays.copyOf(this.times, count), count == 0 ? NO_COUNTS : Arrays.copyOf(this.xs, count),
                count == 0 ? NO_COUNTS : Arrays.copyOf(this.ys, count), now);
        this.events = 0;
        this.flightX = 0.0F;
        this.flightY = 0.0F;
        this.wheel = 0.0F;
        this.previewX = 0.0F;
        this.previewY = 0.0F;
        this.frame = taken;
        return taken;
    }

    void accept(boolean flight, boolean wheelThrottle) {
        this.acceptFlight = flight;
        this.acceptWheel = flight && wheelThrottle;
    }

    public boolean flying() {
        return this.acceptFlight;
    }

    public boolean wheelThrottle() {
        return this.acceptWheel;
    }

    public Frame lastFrame() {
        return this.frame;
    }

    private boolean ownWindow(int eventWindow) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return false;
        }
        long handle = minecraft.getWindow().handle();
        if (handle != this.windowHandle) {
            this.windowHandle = handle;
            this.windowId = handle == 0L ? 0 : SDLVideo.SDL_GetWindowID(handle);
        }
        return this.windowId != 0 && eventWindow == this.windowId;
    }

    public record Frame(float dx, float dy, float wheel, float previewX, float previewY, long[] times, float[] xs, float[] ys, long now) {
        public static final Frame EMPTY = new Frame(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, NO_TIMES, NO_COUNTS, NO_COUNTS, 0L);

        public boolean moved() {
            return this.dx != 0.0F || this.dy != 0.0F || this.wheel != 0.0F;
        }

        public int events() {
            return this.times.length;
        }
    }
}
