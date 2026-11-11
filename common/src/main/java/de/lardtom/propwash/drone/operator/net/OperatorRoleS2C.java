package de.lardtom.propwash.drone.operator.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record OperatorRoleS2C(int entityId, UUID drone, Role role, String name) implements CustomPacketPayload {
    public static final Type<OperatorRoleS2C> TYPE = new Type<>(Constants.id("operator_role"));
    public static final StreamCodec<ByteBuf, OperatorRoleS2C> CODEC = CustomPacketPayload.codec(OperatorRoleS2C::write, OperatorRoleS2C::read);
    public static final int MAX_NAME = 64;
    private static final int MAX_NAME_BYTES = MAX_NAME * 4;

    public enum Role {
        NONE,
        OPERATOR,
        PILOT
    }

    public OperatorRoleS2C {
        drone = drone == null ? new UUID(0L, 0L) : drone;
        role = role == null ? Role.NONE : role;
        name = name == null ? "" : name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.entityId);
        UUIDUtil.STREAM_CODEC.encode(buf, this.drone);
        buf.writeByte(this.role.ordinal());
        Utf8String.write(buf, this.name, MAX_NAME_BYTES);
    }

    private static OperatorRoleS2C read(ByteBuf buf) {
        int entityId = VarInt.read(buf);
        UUID drone = UUIDUtil.STREAM_CODEC.decode(buf);
        int ordinal = buf.readUnsignedByte();
        Role role = ordinal < Role.values().length ? Role.values()[ordinal] : Role.NONE;
        return new OperatorRoleS2C(entityId, drone, role, Utf8String.read(buf, MAX_NAME_BYTES));
    }

    @Override
    public Type<OperatorRoleS2C> type() {
        return TYPE;
    }
}
