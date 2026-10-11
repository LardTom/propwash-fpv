package de.lardtom.propwash.interact.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record DroneBreakClearC2S(int droneId, int epoch, List<BlockPos> positions) implements CustomPacketPayload {
    public static final int MAX_POSITIONS = 32;
    public static final Type<DroneBreakClearC2S> TYPE = new Type<>(Constants.id("drone_break_clear"));
    public static final StreamCodec<ByteBuf, DroneBreakClearC2S> CODEC = CustomPacketPayload.codec(DroneBreakClearC2S::write,
            DroneBreakClearC2S::read);

    public DroneBreakClearC2S {
        if (positions.size() > MAX_POSITIONS) {
            throw new IllegalArgumentException("Zu viele Blöcke in einer Freigabe-Anfrage: " + positions.size());
        }
        positions = positions.stream().map(BlockPos::immutable).toList();
    }

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.droneId);
        VarInt.write(buf, this.epoch);
        VarInt.write(buf, this.positions.size());
        for (BlockPos pos : this.positions) {
            buf.writeLong(pos.asLong());
        }
    }

    private static DroneBreakClearC2S read(ByteBuf buf) {
        int droneId = VarInt.read(buf);
        int epoch = VarInt.read(buf);
        int count = VarInt.read(buf);
        if (count < 0 || count > MAX_POSITIONS) {
            throw new IllegalArgumentException("Ungültige Zahl an Blöcken: " + count);
        }
        List<BlockPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(BlockPos.of(buf.readLong()));
        }
        return new DroneBreakClearC2S(droneId, epoch, positions);
    }

    @Override
    public Type<DroneBreakClearC2S> type() {
        return TYPE;
    }
}
