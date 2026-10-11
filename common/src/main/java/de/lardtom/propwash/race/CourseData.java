package de.lardtom.propwash.race;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CourseData(int id, String dimension, List<Gate> gates, String name, int laps, @Nullable UUID owner, String ownerName) {
    public static final int MAX_GATES = 256;
    public static final int MAX_OWNER_NAME = 32;
    private static final int FLAG_TILT = 1;
    private static final int FLAG_SIZE = 2;
    private static final int FLAG_KIND = 4;
    private static final int FLAG_REGION = 8;
    public static final int MAX_BATCH_BYTES = 512 * 1024;
    public static final StreamCodec<FriendlyByteBuf, CourseData> STREAM_CODEC = StreamCodec.of(CourseData::write, CourseData::read);

    public CourseData {
        gates = List.copyOf(gates);
        name = CourseNames.clean(name);
        laps = CourseLaps.stored(laps);
        ownerName = ownerName == null ? "" : ownerName.length() > MAX_OWNER_NAME ? ownerName.substring(0, MAX_OWNER_NAME) : ownerName;
    }

    public CourseData(int id, String dimension, List<Gate> gates) {
        this(id, dimension, gates, "", CourseLaps.UNSET, null, "");
    }

    public CourseData withGates(List<Gate> next) {
        return new CourseData(this.id, this.dimension, next, this.name, this.laps, this.owner, this.ownerName);
    }

    public boolean named() {
        return CourseNames.named(this.name);
    }

    public List<CourseElement> kinds() {
        List<CourseElement> kinds = new ArrayList<>(this.gates.size());
        for (Gate gate : this.gates) {
            kinds.add(gate.kind());
        }
        return kinds;
    }

    public record Gate(int index, double x, double y, double z, float yaw, GateSize size, GateMount mount, boolean flipped, GateTilt tilt,
                       double width, double height, CourseElement kind, int variant, @Nullable FreeformRegion region) {
        public Gate {
            kind = kind == null ? CourseElement.GATE : kind;
            if (kind != CourseElement.FREEFORM) {
                region = null;
            } else if (region == null) {
                region = FreeformRegion.single(FreeformRegion.AXIS_Z);
            }
            variant = kind.variant(variant);
            boolean element = !kind.gate();
            mount = element || mount == null ? GateMount.STANDING : mount;
            flipped = !element && GateMount.normalizeFlip(mount, flipped);
            tilt = tilt == null ? GateTilt.NONE : tilt;
            if (element && tilt.tilted()) {
                tilt = new GateTilt(tilt.yaw(), 0.0F, 0.0F);
            }
            size = size == null || element ? GateSize.MEDIUM : size;
            double standardWidth = element ? kind.outerWidth(kind.defaultWidth()) : size.width();
            double standardHeight = element ? kind.outerHeight(kind.defaultHeight()) : size.height();
            width = width > 0.0 && Double.isFinite(width) ? width : standardWidth;
            height = height > 0.0 && Double.isFinite(height) ? height : standardHeight;
            if (region != null) {
                width = region.width();
                height = region.height();
            }
        }

        public Gate(int index, double x, double y, double z, float yaw, GateSize size, GateMount mount, boolean flipped, GateTilt tilt,
                    double width, double height, CourseElement kind, int variant) {
            this(index, x, y, z, yaw, size, mount, flipped, tilt, width, height, kind, variant, null);
        }

        public Gate(int index, double x, double y, double z, float yaw, GateSize size, GateMount mount, boolean flipped, GateTilt tilt,
                    double width, double height) {
            this(index, x, y, z, yaw, size, mount, flipped, tilt, width, height, CourseElement.GATE, 0);
        }

        public Gate(int index, double x, double y, double z, float yaw, GateSize size, GateMount mount, boolean flipped) {
            this(index, x, y, z, yaw, size, mount, flipped, GateTilt.NONE, 0.0, 0.0);
        }

        public Gate(int index, double x, double y, double z, float yaw, GateSize size) {
            this(index, x, y, z, yaw, size, GateMount.STANDING, false);
        }

        public boolean standardSize() {
            if (this.region != null) {
                return true;
            }
            if (!this.kind.gate()) {
                return Math.abs(this.width - this.kind.outerWidth(this.kind.defaultWidth())) < 1.0E-6
                        && Math.abs(this.height - this.kind.outerHeight(this.kind.defaultHeight())) < 1.0E-6;
            }
            return this.size.standard(this.width, this.height);
        }

        public GateGeometry geometry() {
            if (this.region != null) {
                return GateGeometry.freeform(this.x, this.y, this.z, this.region, this.variant);
            }
            if (!this.kind.gate()) {
                return GateGeometry.element(this.x, this.y, this.z, this.yaw, this.kind, this.variant, this.width, this.height, this.tilt);
            }
            if (this.tilt.none() && this.standardSize()) {
                return GateGeometry.of(this.x, this.y, this.z, this.yaw, this.size, this.mount, this.flipped);
            }
            return GateGeometry.of(this.x, this.y, this.z, this.yaw, this.size, this.mount, this.flipped, this.width, this.height, this.tilt);
        }
    }

    public List<GateGeometry> geometry() {
        List<GateGeometry> list = new ArrayList<>(this.gates.size());
        for (Gate gate : this.gates) {
            list.add(gate.geometry());
        }
        return list;
    }

    public int layoutHash() {
        int hash = 17;
        for (Gate gate : this.gates) {
            hash = hash * 31 + gate.index;
            hash = hash * 31 + Long.hashCode(Math.round(gate.x * 100.0));
            hash = hash * 31 + Long.hashCode(Math.round(gate.y * 100.0));
            hash = hash * 31 + Long.hashCode(Math.round(gate.z * 100.0));
            hash = hash * 31 + Math.round(gate.yaw * 10.0F);
            hash = hash * 31 + gate.size.ordinal();
            if (gate.mount != GateMount.STANDING) {
                hash = hash * 31 + GateMount.pack(gate.mount, gate.flipped) + 1;
            }
            if (!gate.tilt.none()) {
                hash = hash * 31 + Math.round(gate.tilt.yaw() * 10.0F);
                hash = hash * 31 + Math.round(gate.tilt.pitch() * 10.0F);
                hash = hash * 31 + Math.round(gate.tilt.roll() * 10.0F);
            }
            if (!gate.standardSize()) {
                hash = hash * 31 + Long.hashCode(Math.round(gate.width * 100.0));
                hash = hash * 31 + Long.hashCode(Math.round(gate.height * 100.0));
            }
            if (!gate.kind.gate()) {
                hash = hash * 31 + 1000 + gate.kind.ordinal() * 16 + gate.variant;
            }
            if (gate.region != null) {
                hash = hash * 31 + gate.region.hashCode();
            }
        }
        return hash;
    }

    public int estimatedBytes() {
        int total = 40 + this.dimension.length() + this.name.length() * 3 + this.ownerName.length() * 3;
        for (Gate gate : this.gates) {
            total += 48;
            if (gate.region != null) {
                total += 16 + (gate.region.width() * gate.region.height() + 7) / 8;
            }
        }
        return total;
    }

    private static void write(FriendlyByteBuf buf, CourseData data) {
        buf.writeVarInt(data.id);
        buf.writeUtf(data.dimension, 256);
        buf.writeUtf(data.name, CourseNames.MAX * 2);
        buf.writeVarInt(data.laps + 1);
        buf.writeBoolean(data.owner != null);
        if (data.owner != null) {
            buf.writeUUID(data.owner);
        }
        buf.writeUtf(data.ownerName, MAX_OWNER_NAME);
        buf.writeVarInt(data.gates.size());
        for (Gate gate : data.gates) {
            buf.writeVarInt(gate.index);
            buf.writeDouble(gate.x);
            buf.writeDouble(gate.y);
            buf.writeDouble(gate.z);
            buf.writeFloat(gate.yaw);
            buf.writeByte(gate.size.ordinal());
            buf.writeByte(GateMount.pack(gate.mount, gate.flipped));
            int flags = (gate.tilt.none() ? 0 : FLAG_TILT) | (gate.standardSize() ? 0 : FLAG_SIZE) | (gate.kind.gate() ? 0 : FLAG_KIND)
                    | (gate.region == null ? 0 : FLAG_REGION);
            buf.writeByte(flags);
            if ((flags & FLAG_KIND) != 0) {
                buf.writeByte(gate.kind.ordinal());
                buf.writeByte(gate.variant);
            }
            if ((flags & FLAG_TILT) != 0) {
                buf.writeFloat(gate.tilt.yaw());
                buf.writeFloat(gate.tilt.pitch());
                buf.writeFloat(gate.tilt.roll());
            }
            if ((flags & FLAG_SIZE) != 0) {
                buf.writeFloat((float) gate.width);
                buf.writeFloat((float) gate.height);
            }
            if ((flags & FLAG_REGION) != 0) {
                FreeformRegion region = gate.region;
                buf.writeByte(region.axis());
                buf.writeVarInt(region.width());
                buf.writeVarInt(region.height());
                buf.writeVarInt(region.seedA());
                buf.writeVarInt(region.seedB());
                buf.writeByteArray(region.packed());
            }
        }
    }

    private static CourseData read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String dimension = buf.readUtf(256);
        String name = buf.readUtf(CourseNames.MAX * 2);
        int laps = buf.readVarInt() - 1;
        UUID owner = buf.readBoolean() ? buf.readUUID() : null;
        String ownerName = buf.readUtf(MAX_OWNER_NAME);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_GATES) {
            throw new DecoderException("Zu viele Gates im Kurs: " + count);
        }
        List<Gate> gates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int index = buf.readVarInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float yaw = buf.readFloat();
            GateSize size = GateSize.byOrdinal(buf.readByte());
            int mount = buf.readUnsignedByte();
            int flags = buf.readUnsignedByte();
            CourseElement kind = CourseElement.GATE;
            int variant = 0;
            if ((flags & FLAG_KIND) != 0) {
                kind = CourseElement.byOrdinal(buf.readUnsignedByte());
                variant = buf.readUnsignedByte();
            }
            GateTilt tilt = (flags & FLAG_TILT) != 0 ? new GateTilt(buf.readFloat(), buf.readFloat(), buf.readFloat()) : GateTilt.NONE;
            double width = 0.0;
            double height = 0.0;
            if ((flags & FLAG_SIZE) != 0) {
                if (kind.gate()) {
                    width = size.outer(buf.readFloat() - 2.0 * size.frame());
                    height = size.outer(buf.readFloat() - 2.0 * size.frame());
                } else {
                    width = kind.outerWidth(kind.innerWidth(buf.readFloat()));
                    height = kind.outerHeight(kind.innerHeight(buf.readFloat()));
                }
            }
            FreeformRegion region = null;
            if ((flags & FLAG_REGION) != 0) {
                int axis = buf.readUnsignedByte();
                int regionWidth = buf.readVarInt();
                int regionHeight = buf.readVarInt();
                int seedA = buf.readVarInt();
                int seedB = buf.readVarInt();
                byte[] packed = buf.readByteArray(FreeformRegion.MAX_BYTES);
                region = FreeformRegion.of(axis, regionWidth, regionHeight, seedA, seedB, packed);
                if (region == null) {
                    throw new DecoderException("Ungültige Freiform-Fläche in Kurs " + id);
                }
            }
            gates.add(new Gate(index, x, y, z, yaw, size, GateMount.unpackMount(mount), GateMount.unpackFlipped(mount), tilt, width, height, kind,
                    variant, region));
        }
        return new CourseData(id, dimension, gates, name, laps, owner, ownerName);
    }
}
