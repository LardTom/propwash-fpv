package de.lardtom.propwash.interact;

import de.lardtom.propwash.interact.client.BreakEffects;
import net.minecraft.server.level.ParticleStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreakEffectsTest {
    @Test
    void shardCountFollowsTheParticleSettingAndTheHitShare() {
        assertEquals(BreakEffects.SHARDS_PER_BLOCK, BreakEffects.count(ParticleStatus.ALL, 1.0));
        assertEquals(BreakEffects.SHARDS_PER_BLOCK / 2, BreakEffects.count(ParticleStatus.DECREASED, 1.0));
        int minimal = BreakEffects.count(ParticleStatus.MINIMAL, 1.0);
        assertTrue(minimal >= 3 && minimal <= BreakEffects.SHARDS_PER_BLOCK / 5, "Minimal: wenige, aber sichtbar – " + minimal);
        assertTrue(BreakEffects.count(ParticleStatus.ALL, 0.1) < BreakEffects.count(ParticleStatus.ALL, 0.5));
        assertEquals(BreakEffects.SHARDS_PER_BLOCK, BreakEffects.count(ParticleStatus.ALL, Double.NaN));
        assertEquals(BreakEffects.SHARDS_PER_BLOCK, BreakEffects.count(ParticleStatus.ALL, 0.0), "ohne Stoßmodell (Regel 0 %) fliegen trotzdem alle Scherben");
    }

    @Test
    void volumeGrowsWithTheLostSpeed() {
        assertEquals(BreakEffects.MIN_VOLUME, BreakEffects.volume(0.0), 0.0F);
        assertEquals(BreakEffects.MIN_VOLUME, BreakEffects.volume(Double.NaN), 0.0F);
        assertTrue(BreakEffects.volume(2.0) < BreakEffects.volume(5.0));
        assertEquals(BreakEffects.MAX_VOLUME, BreakEffects.volume(60.0), 0.0F);
    }
}
