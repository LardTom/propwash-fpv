package de.lardtom.propwash.interact.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.interact.BreakRules;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record DroneBreakClearS2C(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<DroneBreakClearS2C> TYPE = new Type<>(Constants.id("drone_break_clearance"));
    public static final StreamCodec<ByteBuf, DroneBreakClearS2C> CODEC = CustomPacketPayload.codec(DroneBreakClearS2C::write,
            DroneBreakClearS2C::read);

    public DroneBreakClearS2C {
        if (entries.size() > DroneBreakClearC2S.MAX_POSITIONS) {
            throw new IllegalArgumentException("Zu viele Blöcke in einer Freigabe: " + entries.size());
        }
        entries = List.copyOf(entries);
    }

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.entries.size());
        for (Entry entry : this.entries) {
            buf.writeLong(entry.pos().asLong());
            buf.writeByte(entry.outcome().ordinal());
        }
    }

    private static DroneBreakClearS2C read(ByteBuf buf) {
        int count = VarInt.read(buf);
        if (count < 0 || count > DroneBreakClearC2S.MAX_POSITIONS) {
            throw new IllegalArgumentException("Ungültige Zahl an Blöcken: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos pos = BlockPos.of(buf.readLong());
            entries.add(new Entry(pos, BreakRules.Outcome.byId(buf.readUnsignedByte())));
        }
        return new DroneBreakClearS2C(entries);
    }

    @Override
    public Type<DroneBreakClearS2C> type() {
        return TYPE;
    }

    public record Entry(BlockPos pos, BreakRules.Outcome outcome) {
        public Entry {
            pos = pos.immutable();
        }

        public boolean cleared() {
            return this.outcome == BreakRules.Outcome.BREAK;
        }
    }
}
