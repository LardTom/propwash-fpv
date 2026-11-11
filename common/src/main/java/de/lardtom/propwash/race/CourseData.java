package de.lardtom.propwash.race;

import de.lardtom.propwash.api.race.CourseMode;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CourseData(int id, String dimension, List<Gate> gates, String name, int laps, @Nullable UUID owner, String ownerName, @Nullable UUID key,
                         CourseMode mode, CreativeFcRule creativeFc, boolean raceLine) {
    public static final int MAX_GATES = 256;
    public static final int MAX_OWNER_NAME = 32;
    private static final int FLAG_TILT = 1;
    private static final int FLAG_SIZE = 2;
    private static final int FLAG_KIND = 4;
    private static final int FLAG_REGION = 8;
    private static final int FLAG_SCORE = 16;
    private static final int FLAG_REVERSE = 32;
    public static final int MAX_BATCH_BYTES = 512 * 1024;
    private static final int REVERSE_HASH = 3001;
    private static final int POINT_TO_POINT_HASH = 4001;
    private static final int HEAD_KEY = 1;
    private static final int HEAD_POINT_TO_POINT = 2;
    private static final int HEAD_CREATIVE_SHIFT = 2;
    private static final int HEAD_CREATIVE_MASK = 3;
    private static final int HEAD_NO_RACE_LINE = 16;
    private static final double PLACE_EPSILON = 1.0E-3;
    public static final StreamCodec<FriendlyByteBuf, CourseData> STREAM_CODEC = StreamCodec.of(CourseData::write, CourseData::read);

    public CourseData {
        gates = List.copyOf(gates);
        name = CourseNames.clean(name);
        laps = CourseLaps.stored(laps);
        ownerName = ownerName == null ? "" : ownerName.length() > MAX_OWNER_NAME ? ownerName.substring(0, MAX_OWNER_NAME) : ownerName;
        mode = mode == null ? CourseMode.LAPS : mode;
        creativeFc = creativeFc == null ? CreativeFcRule.WORLD : creativeFc;
    }

    public CourseData(int id, String dimension, List<Gate> gates, String name, int laps, @Nullable UUID owner, String ownerName, @Nullable UUID key,
                      CourseMode mode, CreativeFcRule creativeFc) {
        this(id, dimension, gates, name, laps, owner, ownerName, key, mode, creativeFc, true);
    }

    public CourseData(int id, String dimension, List<Gate> gates, String name, int laps, @Nullable UUID owner, String ownerName, @Nullable UUID key,
                      CourseMode mode) {
        this(id, dimension, gates, name, laps, owner, ownerName, key, mode, CreativeFcRule.WORLD);
    }

    public CourseData(int id, String dimension, List<Gate> gates, String name, int laps, @Nullable UUID owner, String ownerName, @Nullable UUID key) {
        this(id, dimension, gates, name, laps, owner, ownerName, key, CourseMode.LAPS);
    }

    public CourseData(int id, String dimension, List<Gate> gates, String name, int laps, @Nullable UUID owner, String ownerName) {
        this(id, dimension, gates, name, laps, owner, ownerName, null);
    }

    public CourseData(int id, String dimension, List<Gate> gates) {
        this(id, dimension, gates, "", CourseLaps.UNSET, null, "");
    }

    public CourseData withGates(List<Gate> next) {
        return new CourseData(this.id, this.dimension, next, this.name, this.laps, this.owner, this.ownerName, this.key, this.mode, this.creativeFc, this.raceLine);
    }

    public CourseData withMode(CourseMode next) {
        return new CourseData(this.id, this.dimension, this.gates, this.name, this.laps, this.owner, this.ownerName, this.key, next, this.creativeFc, this.raceLine);
    }

    public CourseData withCreativeFc(CreativeFcRule next) {
        return new CourseData(this.id, this.dimension, this.gates, this.name, this.laps, this.owner, this.ownerName, this.key, this.mode, next, this.raceLine);
    }

    public CourseData withRaceLine(boolean next) {
        return new CourseData(this.id, this.dimension, this.gates, this.name, this.laps, this.owner, this.ownerName, this.key, this.mode, this.creativeFc, next);
    }

    public boolean pointToPoint() {
        return PointToPoint.active(this.mode, this.gates.size());
    }

    public boolean finish(int index) {
        return PointToPoint.finish(this.mode, this.gates.size(), index);
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

    public List<CourseElement> scoringKinds() {
        List<CourseElement> kinds = new ArrayList<>(this.gates.size());
        for (Gate gate : this.gates) {
            kinds.add(gate.scoringKind());
        }
        return kinds;
    }

    public record Gate(int index, double x, double y, double z, float yaw, GateSize size, GateMount mount, boolean flipped, GateTilt tilt,
                       double width, double height, CourseElement kind, int variant, @Nullable FreeformRegion region, ScoreArea score,
                       boolean reverse) {
        public Gate {
            kind = kind == null ? CourseElement.GATE : kind;
            score = (score == null ? ScoreArea.DEFAULT : score).forCourse(kind);
            if (kind != CourseElement.FREEFORM) {
                region = null;
            } else if (region == null) {
                region = FreeformRegion.single(FreeformRegion.AXIS_Z);
            }
            variant = kind.variant(variant);
            boolean element = !kind.gate();
            mount = !kind.mountable() || mount == null ? GateMount.STANDING : mount;
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
                    double width, double height, CourseElement kind, int variant, @Nullable FreeformRegion region, ScoreArea score) {
            this(index, x, y, z, yaw, size, mount, flipped, tilt, width, height, kind, variant, region, score, false);
        }

        public Gate(int index, double x, double y, double z, float yaw, GateSize size, GateMount mount, boolean flipped, GateTilt tilt,
                    double width, double height, CourseElement kind, int variant, @Nullable FreeformRegion region) {
            this(index, x, y, z, yaw, size, mount, flipped, tilt, width, height, kind, variant, region, ScoreArea.DEFAULT);
        }

        public Gate withReverse(boolean next) {
            return next == this.reverse ? this : new Gate(this.index, this.x, this.y, this.z, this.yaw, this.size, this.mount, this.flipped, this.tilt,
                    this.width, this.height, this.kind, this.variant, this.region, this.score, next);
        }

        public boolean samePlace(Gate other) {
            return other != null && this.kind == other.kind && this.variant == other.variant && Math.abs(this.x - other.x) < PLACE_EPSILON
                    && Math.abs(this.y - other.y) < PLACE_EPSILON && Math.abs(this.z - other.z) < PLACE_EPSILON
                    && Math.abs(GateTilt.wrap(this.yaw - other.yaw)) < 0.01F && this.mount == other.mount && this.size == other.size;
        }

        public Gate(int index, double x, double y, double z, float yaw, GateSize size, GateMount mount, boolean flipped, GateTilt tilt,
                    double width, double height, CourseElement kind, int variant) {
            this(index, x, y, z, yaw, size, mount, flipped, tilt, width, height, kind, variant, null);
        }

        public CourseElement scoringKind() {
            return this.kind == CourseElement.GATE_POLE ? this.score.role().scoringKind() : this.kind;
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
            return this.placed().withReverse(this.reverse);
        }

        private GateGeometry placed() {
            if (this.region != null) {
                return GateGeometry.freeform(this.x, this.y, this.z, this.region, this.variant);
            }
            if (!this.kind.gate()) {
                return GateGeometry.element(this.x, this.y, this.z, this.yaw, this.kind, this.variant, this.width, this.height, this.tilt, this.mount,
                        this.score);
            }
            if (this.tilt.none() && this.standardSize()) {
                return GateGeometry.of(this.x, this.y, this.z, this.yaw, this.size, this.mount, this.flipped);
            }
            return GateGeometry.of(this.x, this.y, this.z, this.yaw, this.size, this.mount, this.flipped, this.width, this.height, this.tilt);
        }
    }

    public int firstPass(int index) {
        if (index < 0 || index >= this.gates.size()) {
            return -1;
        }
        Gate gate = this.gates.get(index);
        for (int i = 0; i < index; i++) {
            if (this.gates.get(i).samePlace(gate)) {
                return i;
            }
        }
        return index;
    }

    public List<GateGeometry> geometry() {
        List<GateGeometry> list = new ArrayList<>(this.gates.size());
        for (Gate gate : this.gates) {
            list.add(gate.geometry());
        }
        return list;
    }

    public static boolean yawShapesLayout(CourseElement kind, @Nullable FreeformRegion region) {
        return kind != CourseElement.FREEFORM || region == null || !region.horizontal();
    }

    public int layoutHash() {
        int hash = 17;
        for (Gate gate : this.gates) {
            hash = hash * 31 + gate.index;
            hash = hash * 31 + Long.hashCode(Math.round(gate.x * 100.0));
            hash = hash * 31 + Long.hashCode(Math.round(gate.y * 100.0));
            hash = hash * 31 + Long.hashCode(Math.round(gate.z * 100.0));
            hash = hash * 31 + (yawShapesLayout(gate.kind, gate.region) ? Math.round(gate.yaw * 10.0F) : 0);
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
            if (!gate.score.isDefault()) {
                hash = hash * 31 + 2000 + gate.score.pack();
            }
            if (gate.reverse) {
                hash = hash * 31 + REVERSE_HASH;
            }
        }
        if (this.mode == CourseMode.POINT_TO_POINT) {
            hash = hash * 31 + POINT_TO_POINT_HASH;
        }
        return hash;
    }

    public int estimatedBytes() {
        int total = 57 + this.dimension.length() + this.name.length() * 3 + this.ownerName.length() * 3;
        for (Gate gate : this.gates) {
            total += gate.score.isDefault() ? 48 : 52;
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
        buf.writeByte((data.key != null ? HEAD_KEY : 0) | (data.mode == CourseMode.POINT_TO_POINT ? HEAD_POINT_TO_POINT : 0)
                | data.creativeFc.ordinal() << HEAD_CREATIVE_SHIFT | (data.raceLine ? 0 : HEAD_NO_RACE_LINE));
        if (data.key != null) {
            buf.writeUUID(data.key);
        }
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
                    | (gate.region == null ? 0 : FLAG_REGION) | (gate.score.isDefault() ? 0 : FLAG_SCORE) | (gate.reverse ? FLAG_REVERSE : 0);
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
            if ((flags & FLAG_SCORE) != 0) {
                buf.writeVarInt(gate.score.pack());
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
        int head = buf.readUnsignedByte();
        UUID key = (head & HEAD_KEY) != 0 ? buf.readUUID() : null;
        CourseMode mode = (head & HEAD_POINT_TO_POINT) != 0 ? CourseMode.POINT_TO_POINT : CourseMode.LAPS;
        CreativeFcRule creativeFc = CreativeFcRule.byOrdinal(head >> HEAD_CREATIVE_SHIFT & HEAD_CREATIVE_MASK);
        boolean raceLine = (head & HEAD_NO_RACE_LINE) == 0;
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
            ScoreArea score = (flags & FLAG_SCORE) != 0 ? ScoreArea.unpack(buf.readVarInt()) : ScoreArea.DEFAULT;
            gates.add(new Gate(index, x, y, z, yaw, size, GateMount.unpackMount(mount), GateMount.unpackFlipped(mount), tilt, width, height, kind,
                    variant, region, score, (flags & FLAG_REVERSE) != 0));
        }
        return new CourseData(id, dimension, gates, name, laps, owner, ownerName, key, mode, creativeFc, raceLine);
    }
}
