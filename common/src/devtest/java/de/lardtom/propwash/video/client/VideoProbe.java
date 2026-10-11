package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class VideoProbe {
    private VideoProbe() {
    }

    public static boolean transparent(BlockState state) {
        return ClientBlockLoss.classify(state).empty();
    }

    public static void attach(@Nullable QuadcopterEntity drone, @Nullable VideoSystem receiver, @Nullable VideoSystem transmitter) {
        VideoState video = VideoState.INSTANCE;
        video.attachTestSource(drone);
        video.overrideTestReceiver(drone != null, receiver);
        video.overrideTestTransmitter(transmitter);
        video.resetScanStats();
        video.relay().resetStats();
    }

    public static void systems(@Nullable VideoSystem receiver, @Nullable VideoSystem transmitter) {
        VideoState video = VideoState.INSTANCE;
        video.overrideTestReceiver(true, receiver);
        video.overrideTestTransmitter(transmitter);
    }

    public static void detach() {
        attach(null, null, null);
    }

    public static float rawQuality() {
        return VideoState.INSTANCE.rawQuality();
    }

    public static int scanRounds() {
        return VideoState.INSTANCE.scanRounds();
    }

    public static double scanLossDb() {
        return VideoState.INSTANCE.scanLossDb();
    }
}
