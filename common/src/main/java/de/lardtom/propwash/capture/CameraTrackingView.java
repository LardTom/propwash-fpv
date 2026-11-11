package de.lardtom.propwash.capture;

import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public record CameraTrackingView(ChunkTrackingView.Positioned base, List<CameraArea> areas) implements ChunkTrackingView {
    public CameraTrackingView {
        areas = List.copyOf(areas);
    }

    public static ChunkTrackingView of(ChunkPos center, int viewDistance, List<CameraArea> areas) {
        ChunkTrackingView.Positioned base = new ChunkTrackingView.Positioned(center, viewDistance);
        return areas.isEmpty() ? base : new CameraTrackingView(base, areas);
    }

    public static @Nullable ChunkPos center(ChunkTrackingView view) {
        if (view instanceof ChunkTrackingView.Positioned positioned) {
            return positioned.center();
        }
        if (view instanceof CameraTrackingView camera) {
            return camera.base.center();
        }
        return null;
    }

    public static List<CameraArea> areas(ChunkTrackingView view) {
        return view instanceof CameraTrackingView camera ? camera.areas : List.of();
    }

    @Override
    public boolean contains(int chunkX, int chunkZ, boolean includeNeighbors) {
        if (this.base.contains(chunkX, chunkZ, includeNeighbors)) {
            return true;
        }
        for (CameraArea area : this.areas) {
            if (area.contains(chunkX, chunkZ, includeNeighbors)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void forEach(Consumer<ChunkPos> consumer) {
        this.base.forEach(consumer);
        for (int i = 0; i < this.areas.size(); i++) {
            int index = i;
            this.areas.get(i).forEach(pos -> {
                if (!this.base.contains(pos.x(), pos.z()) && !this.inEarlier(index, pos)) {
                    consumer.accept(pos);
                }
            });
        }
    }

    private boolean inEarlier(int index, ChunkPos pos) {
        for (int i = 0; i < index; i++) {
            if (this.areas.get(i).contains(pos.x(), pos.z(), true)) {
                return true;
            }
        }
        return false;
    }
}
