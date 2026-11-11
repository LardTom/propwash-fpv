package de.lardtom.propwash.rx;

import de.lardtom.propwash.Constants;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

public final class RxUnloadProbe {
    public interface Site {
        boolean ticking(BlockPos pos);

        void place(BlockPos pos);

        void release(BlockPos pos);

        boolean chunkPresent(BlockPos pos);

        boolean removed(BlockPos pos);

        int loadedChunks();

        boolean held();

        boolean indexed(BlockPos pos);
    }

    private final BlockPos far;
    private final int timeout;
    private int stage;
    private int since;
    private boolean farLoaded;
    private int loadedBefore;
    private boolean done;
    private @Nullable String problem;

    public RxUnloadProbe(BlockPos far, int tick, int timeout) {
        this.far = far;
        this.since = tick;
        this.timeout = timeout;
    }

    public int loadedBefore() {
        return this.loadedBefore;
    }

    public boolean done() {
        return this.done;
    }

    public @Nullable String problem() {
        return this.problem;
    }

    public boolean step(Site site, int tick) {
        if (this.done) {
            return true;
        }
        switch (this.stage) {
            case 0 -> {
                if (!site.ticking(this.far)) {
                    return tick - this.since > this.timeout && this.finish("Chunk des zweiten Blocks lädt nicht");
                }
                site.place(this.far);
                site.release(this.far);
                this.next(1, tick);
            }
            case 1 -> {
                if (site.chunkPresent(this.far) || !site.removed(this.far)) {
                    return tick - this.since > this.timeout && this.finish("Chunk des zweiten Blocks entlädt nicht");
                }
                this.loadedBefore = site.loadedChunks();
                Constants.LOG.info("[Beitrittstest] RX: zweiter Block bei {} liegt jetzt in einem nicht geladenen Chunk ({} Chunks geladen, entladen nach {} Ticks)",
                        this.far.toShortString(), this.loadedBefore, tick - this.since);
                this.next(2, tick);
            }
            default -> {
                if (site.chunkPresent(this.far)) {
                    this.farLoaded = true;
                }
                boolean held = site.held();
                if (!held && tick - this.since < this.timeout) {
                    return false;
                }
                boolean indexed = site.indexed(this.far);
                String found = this.farLoaded ? "Chunk des zweiten Blocks wurde geladen" : indexed ? "zweiter Block im Verzeichnis" : null;
                return this.finish(held ? found : (found == null ? "Halten nicht erreicht" : found));
            }
        }
        return false;
    }

    private void next(int stage, int tick) {
        this.stage = stage;
        this.since = tick;
    }

    private boolean finish(@Nullable String found) {
        this.problem = found;
        this.done = true;
        return true;
    }
}
