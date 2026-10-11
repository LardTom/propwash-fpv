package de.lardtom.propwash.interact.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.interact.BreakRules;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DroneBreakDeniedS2C(BlockPos pos, BreakRules.Outcome outcome) implements CustomPacketPayload {
    public static final Type<DroneBreakDeniedS2C> TYPE = new Type<>(Constants.id("drone_break_denied"));
    public static final StreamCodec<ByteBuf, DroneBreakDeniedS2C> CODEC = CustomPacketPayload.codec(DroneBreakDeniedS2C::write,
            DroneBreakDeniedS2C::read);

    public DroneBreakDeniedS2C {
        pos = pos.immutable();
        if (outcome == BreakRules.Outcome.BREAK) {
            throw new IllegalArgumentException("Kein Grund für eine Ablehnung");
        }
    }

    private void write(ByteBuf buf) {
        buf.writeLong(this.pos.asLong());
        buf.writeByte(this.outcome.ordinal());
    }

    private static DroneBreakDeniedS2C read(ByteBuf buf) {
        BlockPos pos = BlockPos.of(buf.readLong());
        BreakRules.Outcome outcome = BreakRules.Outcome.byId(buf.readUnsignedByte());
        return new DroneBreakDeniedS2C(pos, outcome == BreakRules.Outcome.BREAK ? BreakRules.Outcome.PROTECTED : outcome);
    }

    @Override
    public Type<DroneBreakDeniedS2C> type() {
        return TYPE;
    }
}
