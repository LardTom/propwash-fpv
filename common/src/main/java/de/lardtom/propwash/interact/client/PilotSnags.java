package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.interact.SnagBlocks;
import de.lardtom.propwash.interact.SnagMaterial;
import de.lardtom.propwash.interact.SnagMath;
import de.lardtom.propwash.interact.SnagSampler;
import de.lardtom.propwash.interact.SnagWear;
import de.lardtom.propwash.interact.net.SnagWearC2S;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class PilotSnags {
    public static final PilotSnags INSTANCE = new PilotSnags();
    public static final Object TOAST_TOKEN = new Object();
    private static final long TOAST_MILLIS = 3500L;
    private static final long SEND_NANOS = 1_000_000_000L;
    private static final double CAUGHT_SECONDS = 0.4;
    private static final double FREE_SECONDS = 2.0;

    private final SnagSampler sampler = new SnagSampler();
    private final SnagBlocks blocks = new SnagBlocks();
    private final SnagWear wear = new SnagWear();
    private long lastSendNanos;
    private double caughtSeconds;
    private double freeSeconds = FREE_SECONDS;
    private boolean hinted;
    private int catches;
    private int wearPackets;
    private double peakHold;
    private int touchSlices;

    private PilotSnags() {
    }

    public void reset() {
        this.sampler.reset();
        this.wear.clear();
        this.caughtSeconds = 0.0;
        this.freeSeconds = FREE_SECONDS;
        this.hinted = false;
        this.peakHold = 0.0;
    }

    public void beforeStep(QuadcopterEntity drone, DroneSim sim, SimEnvironment env, long sliceNanos) {
        boolean touching = this.sampler.sample(sim, this.blocks.at(drone.level()), env.snag);
        this.blocks.at(null);
        double seconds = sliceNanos * 1.0E-9;
        if (touching) {
            this.touchSlices++;
            this.wear.add(this.sampler, sim.telemetry(), sim.params().omegaMax, seconds);
            this.peakHold = Math.max(this.peakHold, env.snag.hold);
        }
        boolean caught = touching && SnagMath.caught(sim.telemetry().speed, env.snag.hold);
        if (caught) {
            this.caughtSeconds += seconds;
            this.freeSeconds = 0.0;
        } else {
            this.caughtSeconds = 0.0;
            this.freeSeconds += seconds;
            if (this.freeSeconds >= FREE_SECONDS) {
                this.hinted = false;
            }
        }
    }

    public void afterFrame(QuadcopterEntity drone, int epoch) {
        if (!this.hinted && this.caughtSeconds >= CAUGHT_SECONDS) {
            this.hinted = true;
            this.catches++;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ThemedToast.show(mc.gui.toastManager(), TOAST_TOKEN, Component.translatable("propwash.interact.snag.caught"),
                        Component.translatable("propwash.interact.snag.caught.hint"), Palette.WARNING, TOAST_MILLIS);
            }
        }
        long now = Util.getNanos();
        if (!this.wear.any() || now - this.lastSendNanos < SEND_NANOS) {
            return;
        }
        this.lastSendNanos = now;
        float[] amounts = this.wear.drain();
        if (ClientWorldRules.INSTANCE.crashDamage() && ClientBus.inSession()
                && ClientNet.sendToServer(new SnagWearC2S(drone.getId(), epoch, amounts))) {
            this.wearPackets++;
        }
    }

    public boolean touching() {
        return this.sampler.touching();
    }

    public double exposure(SnagMaterial material) {
        return this.sampler.exposure(material);
    }

    public int catches() {
        return this.catches;
    }

    public int wearPackets() {
        return this.wearPackets;
    }

    public int touchSlices() {
        return this.touchSlices;
    }

    public double peakHold() {
        return this.peakHold;
    }
}
