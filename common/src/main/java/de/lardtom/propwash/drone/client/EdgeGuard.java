package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.server.EdgeFence;
import de.lardtom.propwash.server.client.ClientGenerationEdge;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.joml.Vector3d;

public final class EdgeGuard {
    public static final EdgeGuard INSTANCE = new EdgeGuard();
    private static final long NOTICE_PAUSE_NANOS = 20_000_000_000L;
    private static final long TOAST_MILLIS = 5000L;

    private final Vector3d position = new Vector3d();
    private final Vector3d velocity = new Vector3d();
    private final double[] horizontal = new double[2];
    private double distance = Double.POSITIVE_INFINITY;
    private boolean warning;
    private boolean limiting;
    private long lastWarningNanos = Long.MIN_VALUE;
    private long limitedSlices;

    private EdgeGuard() {
    }

    public boolean warning() {
        return this.warning;
    }

    public boolean limiting() {
        return this.limiting;
    }

    public double distance() {
        return this.distance;
    }

    public long limitedSlices() {
        return this.limitedSlices;
    }

    void apply(Minecraft mc, DroneSim sim, long now) {
        EdgeFence fence = ClientGenerationEdge.INSTANCE.fence();
        if (fence.empty()) {
            this.distance = Double.POSITIVE_INFINITY;
            this.warning = false;
            this.limiting = false;
            return;
        }
        sim.getPosition(this.position);
        sim.getVelocity(this.velocity);
        this.horizontal[0] = this.velocity.x;
        this.horizontal[1] = this.velocity.z;
        this.distance = fence.distance(this.position.x, this.position.z);
        this.limiting = fence.limit(this.position.x, this.position.z, this.horizontal);
        if (this.limiting) {
            sim.setHorizontalVelocity(this.horizontal[0], this.horizontal[1]);
            this.limitedSlices++;
        }
        boolean wasWarning = this.warning;
        this.warning = this.limiting || this.distance <= EdgeFence.WARN_DISTANCE;
        if (this.warning && !wasWarning && (this.lastWarningNanos == Long.MIN_VALUE || now - this.lastWarningNanos > NOTICE_PAUSE_NANOS)
                && mc.player != null) {
            ThemedToast.show(mc.gui.toastManager(), PilotSession.TOAST_TOKEN, Component.translatable("propwash.drone.edge.notice"), null,
                    Palette.WARNING, TOAST_MILLIS);
        }
        if (this.warning) {
            this.lastWarningNanos = now;
        }
    }

    void reset() {
        this.distance = Double.POSITIVE_INFINITY;
        this.warning = false;
        this.limiting = false;
    }
}
