package de.lardtom.propwash.video.client;

import de.lardtom.propwash.video.link.ObstructionScan;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

public final class BarrierVideoProbe {
    private BarrierVideoProbe() {
    }

    public static double lossDb(ClientLevel level, Vec3 from, Vec3 to) {
        ClientBlockLoss blocks = new ClientBlockLoss();
        blocks.bind(level);
        ObstructionScan scan = new ObstructionScan();
        scan.start(from.x, from.y, from.z, to.x, to.y, to.z);
        return scan.evaluate(blocks);
    }
}
