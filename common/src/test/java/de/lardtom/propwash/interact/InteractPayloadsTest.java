package de.lardtom.propwash.interact;

import de.lardtom.propwash.interact.net.CargoReleaseC2S;
import de.lardtom.propwash.interact.net.DroneBreakC2S;
import de.lardtom.propwash.interact.net.DroneBreakClearC2S;
import de.lardtom.propwash.interact.net.DroneBreakClearS2C;
import de.lardtom.propwash.interact.net.DroneBreakDeniedS2C;
import de.lardtom.propwash.interact.net.DroneStrikeC2S;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractPayloadsTest {
    @Test
    void strikeRoundTrips() {
        for (int axis = 0; axis < 3; axis++) {
            for (boolean positive : new boolean[]{false, true}) {
                DroneStrikeC2S payload = new DroneStrikeC2S(42, 3, 1234567, 12.5F, -1.0F, 0.25F, axis, positive);
                ByteBuf buf = Unpooled.buffer();
                DroneStrikeC2S.CODEC.encode(buf, payload);
                assertEquals(payload, DroneStrikeC2S.CODEC.decode(buf));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new DroneStrikeC2S(1, 0, 2, 0.0F, 0.0F, 0.0F, 3, true));
        ByteBuf bad = Unpooled.buffer();
        DroneStrikeC2S.CODEC.encode(bad, new DroneStrikeC2S(1, 0, 2, 0.0F, 0.0F, 0.0F, 2, true));
        bad.setByte(bad.writerIndex() - 1, 9);
        assertThrows(IllegalArgumentException.class, () -> DroneStrikeC2S.CODEC.decode(bad));
    }

    @Test
    void clientsMayOnlyAskForCommandOrCrashDrops() {
        for (CargoReleaseReason reason : new CargoReleaseReason[]{CargoReleaseReason.COMMAND, CargoReleaseReason.CRASH}) {
            CargoReleaseC2S payload = new CargoReleaseC2S(7, 1, reason);
            ByteBuf buf = Unpooled.buffer();
            CargoReleaseC2S.CODEC.encode(buf, payload);
            assertEquals(payload, CargoReleaseC2S.CODEC.decode(buf));
        }
        ByteBuf forged = Unpooled.buffer();
        CargoReleaseC2S.CODEC.encode(forged, new CargoReleaseC2S(7, 1, CargoReleaseReason.REMOVED));
        assertThrows(IllegalArgumentException.class, () -> CargoReleaseC2S.CODEC.decode(forged));
    }

    @Test
    void breakRequestRoundTrips() {
        BlockPos pos = new BlockPos(-30_000_000 + 5, -64, 29_999_999);
        for (int axis = 0; axis < 3; axis++) {
            for (boolean positive : new boolean[]{false, true}) {
                DroneBreakC2S payload = new DroneBreakC2S(42, 3, pos, 25.5F, -1.0F, 0.25F, axis, positive);
                ByteBuf buf = Unpooled.buffer();
                DroneBreakC2S.CODEC.encode(buf, payload);
                assertEquals(payload, DroneBreakC2S.CODEC.decode(buf));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new DroneBreakC2S(1, 0, pos, 0.0F, 0.0F, 0.0F, 3, true));
        ByteBuf bad = Unpooled.buffer();
        DroneBreakC2S.CODEC.encode(bad, new DroneBreakC2S(1, 0, pos, 0.0F, 0.0F, 0.0F, 2, true));
        bad.setByte(bad.writerIndex() - 1, 7);
        assertThrows(IllegalArgumentException.class, () -> DroneBreakC2S.CODEC.decode(bad));
    }

    @Test
    void breakDenialCarriesTheReason() {
        BlockPos pos = new BlockPos(12, 70, -8);
        for (BreakRules.Outcome outcome : BreakRules.Outcome.values()) {
            if (outcome == BreakRules.Outcome.BREAK) {
                assertThrows(IllegalArgumentException.class, () -> new DroneBreakDeniedS2C(pos, outcome));
                continue;
            }
            DroneBreakDeniedS2C payload = new DroneBreakDeniedS2C(pos, outcome);
            ByteBuf buf = Unpooled.buffer();
            DroneBreakDeniedS2C.CODEC.encode(buf, payload);
            assertEquals(payload, DroneBreakDeniedS2C.CODEC.decode(buf));
        }
        ByteBuf forged = Unpooled.buffer();
        DroneBreakDeniedS2C.CODEC.encode(forged, new DroneBreakDeniedS2C(pos, BreakRules.Outcome.TOO_WEAK));
        forged.setByte(forged.writerIndex() - 1, BreakRules.Outcome.BREAK.ordinal());
        assertEquals(BreakRules.Outcome.PROTECTED, DroneBreakDeniedS2C.CODEC.decode(forged).outcome());
    }

    @Test
    void clearanceRequestAndAnswerRoundTrip() {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < DroneBreakClearC2S.MAX_POSITIONS; i++) {
            positions.add(new BlockPos(i - 16, 64 + i, -3 * i));
        }
        DroneBreakClearC2S request = new DroneBreakClearC2S(42, 3, positions);
        ByteBuf buf = Unpooled.buffer();
        DroneBreakClearC2S.CODEC.encode(buf, request);
        assertEquals(request, DroneBreakClearC2S.CODEC.decode(buf));
        List<BlockPos> tooMany = new ArrayList<>(positions);
        tooMany.add(BlockPos.ZERO);
        assertThrows(IllegalArgumentException.class, () -> new DroneBreakClearC2S(42, 3, tooMany));
        ByteBuf forged = Unpooled.buffer();
        DroneBreakClearC2S.CODEC.encode(forged, new DroneBreakClearC2S(42, 3, List.of()));
        forged.setByte(forged.writerIndex() - 1, DroneBreakClearC2S.MAX_POSITIONS + 1);
        assertThrows(IllegalArgumentException.class, () -> DroneBreakClearC2S.CODEC.decode(forged));
        List<DroneBreakClearS2C.Entry> entries = new ArrayList<>();
        BreakRules.Outcome[] outcomes = BreakRules.Outcome.values();
        for (int i = 0; i < outcomes.length; i++) {
            entries.add(new DroneBreakClearS2C.Entry(new BlockPos(i, 70, -i), outcomes[i]));
        }
        DroneBreakClearS2C answer = new DroneBreakClearS2C(entries);
        ByteBuf back = Unpooled.buffer();
        DroneBreakClearS2C.CODEC.encode(back, answer);
        DroneBreakClearS2C decoded = DroneBreakClearS2C.CODEC.decode(back);
        assertEquals(answer, decoded);
        assertTrue(decoded.entries().getFirst().cleared());
        assertEquals(1, decoded.entries().stream().filter(DroneBreakClearS2C.Entry::cleared).count());
    }
}
