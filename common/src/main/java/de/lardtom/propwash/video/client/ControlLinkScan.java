package de.lardtom.propwash.video.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.video.link.ObstructionScan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ControlLinkScan {
    public static final ControlLinkScan INSTANCE = new ControlLinkScan();
    private static final int CELL_BUDGET_PER_TICK = 12_000;
    private static final double MAX_SCAN_BLOCKS = RadioLink.FULL_RANGE + RadioLink.FADE_RANGE;

    private final ObstructionScan scan = new ObstructionScan();
    private final ClientBlockLoss blockLoss = new ClientBlockLoss();
    private @Nullable QuadcopterEntity drone;
    private double lossDb;
    private boolean measured;

    private ControlLinkScan() {
    }

    public double videoBandLossDb(@Nullable QuadcopterEntity target) {
        if (target == null) {
            return 0.0;
        }
        double shared = VideoState.INSTANCE.pathLossDb(target);
        if (!Double.isNaN(shared)) {
            return shared;
        }
        return this.measured && this.drone == target ? this.lossDb : 0.0;
    }

    void onTick(Minecraft minecraft) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity target = session.drone();
        LocalPlayer player = minecraft.player;
        if (target == null || player == null || minecraft.level == null || target.level() != minecraft.level
                || !Double.isNaN(VideoState.INSTANCE.pathLossDb(target))) {
            this.reset();
            return;
        }
        if (target != this.drone) {
            this.reset();
            this.drone = target;
        }
        this.blockLoss.bind(minecraft.level);
        if (this.scan.complete()) {
            Vec3 eye = player.getEyePosition();
            Vec3 antenna = VideoState.antenna(target);
            if (eye.distanceTo(antenna) > MAX_SCAN_BLOCKS) {
                this.lossDb = 0.0;
                this.measured = true;
                return;
            }
            this.scan.start(eye.x, eye.y, eye.z, antenna.x, antenna.y, antenna.z);
        }
        if (this.scan.advance(this.blockLoss, CELL_BUDGET_PER_TICK)) {
            this.lossDb = this.scan.lossDb();
            this.measured = true;
        }
    }

    private void reset() {
        this.scan.clear();
        this.drone = null;
        this.measured = false;
        this.lossDb = 0.0;
    }
}
