package de.lardtom.propwash.guide.scene;

import java.util.Objects;

public final class ScenePlayback {
    public static final float RESTART_WINDOW_TICKS = 20.0F;
    public static final float MAX_FRAME_TICKS = 5.0F;

    private final Scene scene;
    private float time;
    private boolean playing = true;
    private float speed = 1.0F;

    public ScenePlayback(Scene scene) {
        this.scene = Objects.requireNonNull(scene, "scene");
    }

    public Scene scene() {
        return this.scene;
    }

    public float time() {
        return this.time;
    }

    public boolean playing() {
        return this.playing;
    }

    public float speed() {
        return this.speed;
    }

    public void speed(float speed) {
        this.speed = Math.clamp(speed, 0.25F, 4.0F);
    }

    public boolean finished() {
        return this.time >= this.scene.duration();
    }

    public int step() {
        return this.scene.stepAt(this.time);
    }

    public void advance(float ticks) {
        if (!this.playing || !(ticks > 0.0F) || !Float.isFinite(ticks)) {
            return;
        }
        this.time = Math.min(this.scene.duration(), this.time + Math.min(ticks, MAX_FRAME_TICKS) * this.speed);
        if (this.finished()) {
            this.playing = false;
        }
    }

    public void play() {
        if (this.finished()) {
            this.time = 0.0F;
        }
        this.playing = true;
    }

    public void pause() {
        this.playing = false;
    }

    public void toggle() {
        if (this.playing) {
            this.pause();
        } else {
            this.play();
        }
    }

    public void seek(float time) {
        this.time = Float.isFinite(time) ? Math.clamp(time, 0.0F, this.scene.duration()) : 0.0F;
        if (this.finished()) {
            this.playing = false;
        }
    }

    public void jumpTo(int step) {
        this.seek(this.scene.stepStart(Math.clamp(step, 0, this.scene.stepCount() - 1)));
    }

    public void next() {
        int step = this.step();
        if (step >= this.scene.stepCount() - 1) {
            this.seek(this.scene.duration());
            return;
        }
        this.jumpTo(step + 1);
    }

    public void previous() {
        if (this.finished()) {
            this.jumpTo(this.scene.stepCount() - 1);
            return;
        }
        int step = this.step();
        float into = this.time - this.scene.stepStart(step);
        if (into > RESTART_WINDOW_TICKS || step == 0) {
            this.jumpTo(step);
        } else {
            this.jumpTo(step - 1);
        }
    }

    public void replay() {
        this.time = 0.0F;
        this.playing = true;
    }

    public SceneState evaluate() {
        return SceneEvaluator.evaluate(this.scene, this.time);
    }
}
