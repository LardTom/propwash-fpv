package de.lardtom.propwash.rx;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RxUnloadProbeTest {
    private static final BlockPos FAR = new BlockPos(481, -60, -3);
    private static final int TIMEOUT = 20 * 120;
    private static final int NEVER = Integer.MAX_VALUE / 2;
    private static final int LOADED_WITH_FAR_AREA = 2569;
    private static final int LOADED_AFTER_UNLOAD = 1521;

    private static final class World implements RxUnloadProbe.Site {
        private final int unloadDelay;
        private final int heldDelay;
        private int tick;
        private boolean forced = true;
        private boolean placed;
        private int releasedAt = -1;
        private int reloadAt = NEVER;
        private boolean indexKeepsEntry;
        private int indexReadAt = -1;

        World(int unloadDelay, int heldDelay) {
            this.unloadDelay = unloadDelay;
            this.heldDelay = heldDelay;
        }

        World reloadAfterUnload(int ticks) {
            this.reloadAt = ticks;
            return this;
        }

        World indexKeepsEntry() {
            this.indexKeepsEntry = true;
            return this;
        }

        boolean unloaded() {
            return this.releasedAt >= 0 && this.tick - this.releasedAt >= this.unloadDelay && !this.reloaded();
        }

        private boolean reloaded() {
            return this.releasedAt >= 0 && this.tick - this.releasedAt - this.unloadDelay >= this.reloadAt;
        }

        @Override
        public boolean ticking(BlockPos pos) {
            return this.forced;
        }

        @Override
        public void place(BlockPos pos) {
            this.placed = true;
        }

        @Override
        public void release(BlockPos pos) {
            this.forced = false;
            this.releasedAt = this.tick;
        }

        @Override
        public boolean chunkPresent(BlockPos pos) {
            return this.forced || this.reloaded();
        }

        @Override
        public boolean removed(BlockPos pos) {
            return this.placed && this.unloaded();
        }

        @Override
        public int loadedChunks() {
            return this.unloaded() ? LOADED_AFTER_UNLOAD : LOADED_WITH_FAR_AREA;
        }

        @Override
        public boolean held() {
            return this.releasedAt >= 0 && this.tick - this.releasedAt >= this.heldDelay;
        }

        @Override
        public boolean indexed(BlockPos pos) {
            this.indexReadAt = this.tick;
            return this.placed && (!this.unloaded() || this.indexKeepsEntry);
        }
    }

    private static @Nullable String run(World world, RxUnloadProbe probe) {
        for (world.tick = 1; world.tick < 10 * TIMEOUT; world.tick++) {
            if (probe.step(world, world.tick)) {
                assertTrue(probe.done());
                return probe.problem();
            }
        }
        return fail("Probe endet nicht");
    }

    @Test
    void chunkUnloadedRightAwayLeavesTheBlockUntouched() {
        World world = new World(0, 45);
        RxUnloadProbe probe = new RxUnloadProbe(FAR, 0, TIMEOUT);
        assertNull(run(world, probe));
        assertTrue(world.placed);
        assertEquals(LOADED_AFTER_UNLOAD, probe.loadedBefore());
    }

    @Test
    void waitsForTheRealUnloadAfterTheTicketIsGone() {
        World world = new World(300, 45);
        RxUnloadProbe probe = new RxUnloadProbe(FAR, 0, TIMEOUT);
        assertNull(run(world, probe), "Ticket weg, Chunk aber noch nicht entladen: die Probe muss auf das Entladen warten");
        assertTrue(world.unloaded());
        assertTrue(world.indexReadAt - world.releasedAt >= 300);
        assertEquals(LOADED_AFTER_UNLOAD, probe.loadedBefore());
    }

    @Test
    void waitsForTheHoldStageWhenTheChunkUnloadsFirst() {
        World world = new World(5, 400);
        RxUnloadProbe probe = new RxUnloadProbe(FAR, 0, TIMEOUT);
        assertNull(run(world, probe));
        assertTrue(world.indexReadAt - world.releasedAt >= 400);
    }

    @Test
    void entryLeftInTheIndexAfterTheUnloadIsReported() {
        World world = new World(300, 45).indexKeepsEntry();
        RxUnloadProbe probe = new RxUnloadProbe(FAR, 0, TIMEOUT);
        assertEquals("zweiter Block im Verzeichnis", run(world, probe));
        assertTrue(world.unloaded());
    }

    @Test
    void chunkThatNeverUnloadsIsReportedAfterTheTimeout() {
        World world = new World(NEVER, 45);
        RxUnloadProbe probe = new RxUnloadProbe(FAR, 0, TIMEOUT);
        assertEquals("Chunk des zweiten Blocks entlädt nicht", run(world, probe));
        assertFalse(world.unloaded());
        assertTrue(world.tick - world.releasedAt > TIMEOUT);
    }

    @Test
    void chunkLoadedAgainAfterTheUnloadIsReported() {
        World world = new World(300, 400).reloadAfterUnload(20);
        RxUnloadProbe probe = new RxUnloadProbe(FAR, 0, TIMEOUT);
        assertEquals("Chunk des zweiten Blocks wurde geladen", run(world, probe));
    }

    @Test
    void chunkThatNeverTicksIsReported() {
        World world = new World(0, 45);
        world.forced = false;
        RxUnloadProbe probe = new RxUnloadProbe(FAR, 0, TIMEOUT);
        assertEquals("Chunk des zweiten Blocks lädt nicht", run(world, probe));
        assertFalse(world.placed);
    }
}
