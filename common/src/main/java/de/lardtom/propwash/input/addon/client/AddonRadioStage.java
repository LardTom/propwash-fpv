package de.lardtom.propwash.input.addon.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.RadioFrame;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.addon.AddonInputMath;
import de.lardtom.propwash.input.addon.AddonInputs;
import de.lardtom.propwash.input.addon.AddonSwitchEvaluator;
import de.lardtom.propwash.input.addon.AxisAssignment;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class AddonRadioStage implements UnaryOperator<RadioFrame> {
    public static final AddonRadioStage INSTANCE = new AddonRadioStage();
    private static final long CAPTURE_NANOS = 300_000_000L;

    private final AddonSwitchEvaluator evaluator = new AddonSwitchEvaluator();
    private final double[] keyValues = new double[16];
    private boolean keyValuesSet;
    private @Nullable String guid;
    private long lastNanos;
    private long captureUntil;
    private volatile long keyBits;
    private boolean failed;

    private AddonRadioStage() {
    }

    public AddonSwitchEvaluator evaluator() {
        return this.evaluator;
    }

    public long keyBits() {
        return this.keyBits;
    }

    public void capture() {
        this.captureUntil = Util.getNanos() + CAPTURE_NANOS;
    }

    public void reset() {
        this.evaluator.reset();
        this.evaluator.resetKeys();
        this.keyValuesSet = false;
        this.keyBits = 0L;
        this.guid = null;
    }

    @Override
    public RadioFrame apply(RadioFrame frame) {
        try {
            return this.stage(frame);
        } catch (RuntimeException e) {
            if (!this.failed) {
                this.failed = true;
                Constants.LOG.error("Propwash: Add-on-Schalter konnten nicht ausgewertet werden", e);
            }
            return frame;
        }
    }

    private RadioFrame stage(RadioFrame frame) {
        List<SwitchSpec> switches = AddonInputs.switches();
        List<AxisSpec> axes = AddonInputs.axes();
        if (switches.isEmpty() && axes.isEmpty()) {
            return frame;
        }
        long now = Util.getNanos();
        double seconds = this.lastNanos == 0L ? 0.0 : Math.min(0.25, Math.max(0.0, (now - this.lastNanos) / 1.0E9));
        this.lastNanos = now;
        if (!this.keyValuesSet) {
            for (int i = 0; i < this.keyValues.length; i++) {
                this.keyValues[i] = i < axes.size() ? axes.get(i).center() : 0.0;
            }
            this.keyValuesSet = true;
        }
        DeviceProfile profile = profile(frame);
        String current = profile == null ? null : frame.deviceGuid();
        if (!Objects.equals(current, this.guid)) {
            this.guid = current;
            this.evaluator.reset();
        }
        boolean capture = now < this.captureUntil || capturing();
        long bits = 0L;
        if (profile != null && !capture) {
            bits = this.evaluator.evaluate(profile, switches, frame.axes(), frame.buttons());
        }
        this.keyBits = this.keyBits(switches);
        bits |= this.keyBits;
        float[] values = new float[axes.size()];
        for (int i = 0; i < axes.size() && i < this.keyValues.length; i++) {
            AxisSpec spec = axes.get(i);
            AxisAssignment assignment = profile == null ? AxisAssignment.keys() : profile.addonAxis(spec.id());
            if (!capture && assignment.readable(frame.axes())) {
                double value = AddonInputMath.axisValue(assignment, frame.axes()[assignment.index]);
                this.keyValues[i] = value;
                values[i] = (float) value;
                continue;
            }
            Optional<Api17Registries.Client.AxisKeys> keys = Api17Registries.Client.axisKeys(spec.id());
            boolean up = keys.isPresent() && down(keys.get().increase());
            boolean downKey = keys.isPresent() && down(keys.get().decrease());
            this.keyValues[i] = AddonInputMath.keyStep(this.keyValues[i], up, downKey, spec.keyRateDps(), seconds);
            values[i] = (float) this.keyValues[i];
        }
        return frame.withAddons(bits, values);
    }

    private long keyBits(List<SwitchSpec> switches) {
        boolean[] down = new boolean[switches.size()];
        int[] clicks = new int[switches.size()];
        for (int i = 0; i < switches.size(); i++) {
            Optional<KeyMapping> key = Api17Registries.Client.switchKey(switches.get(i).id());
            if (key.isEmpty() || key.get().isUnbound()) {
                continue;
            }
            down[i] = key.get().isDown();
            while (key.get().consumeClick()) {
                clicks[i]++;
            }
        }
        return this.evaluator.keys(switches, down, clicks);
    }

    private static boolean down(KeyMapping key) {
        return !key.isUnbound() && key.isDown();
    }

    private static boolean capturing() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.gui != null && minecraft.gui.screen() instanceof ReceiverWizardScreen;
    }

    private static @Nullable DeviceProfile profile(RadioFrame frame) {
        if (frame.source() != RadioSource.JOYSTICK && frame.source() != RadioSource.GAMEPAD) {
            return null;
        }
        return InputConfig.get().profile(frame.deviceGuid());
    }
}
