package de.lardtom.propwash.capture;

import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;

import java.util.function.Consumer;

public final class TrackingDiff {
    private TrackingDiff() {
    }

    public static void difference(ChunkTrackingView from, ChunkTrackingView to, Consumer<ChunkPos> onEnter, Consumer<ChunkPos> onLeave) {
        if (from.equals(to)) {
            return;
        }
        if (from instanceof ChunkTrackingView.Positioned && to instanceof ChunkTrackingView.Positioned) {
            ChunkTrackingView.difference(from, to, onEnter, onLeave);
            return;
        }
        from.forEach(pos -> {
            if (!to.contains(pos)) {
                onLeave.accept(pos);
            }
        });
        to.forEach(pos -> {
            if (!from.contains(pos)) {
                onEnter.accept(pos);
            }
        });
    }
}
