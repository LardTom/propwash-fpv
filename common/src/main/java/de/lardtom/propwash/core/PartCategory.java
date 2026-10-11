package de.lardtom.propwash.core;

import com.mojang.serialization.Codec;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum PartCategory {
    FRAME("frame", FrameType.class, PartKind.FRAME, 1, 1),
    STACK("stack", StackType.class, PartKind.STACK, 1, 1),
    MOTOR("motor", MotorType.class, PartKind.MOTOR, DroneBuild.MOTOR_COUNT, DroneBuild.MOTOR_COUNT),
    PROP("prop", PropType.class, PartKind.PROP, DroneBuild.MOTOR_COUNT, DroneBuild.MOTOR_COUNT),
    VIDEO("video", VideoType.class, PartKind.VIDEO, 1, 1),
    BATTERY("battery", BatteryType.class, PartKind.BATTERY, 1, 1),
    ACCESSORY("accessory", AccessoryType.class, PartKind.ACCESSORY, 0, AccessoryMount.values().length);

    public static final Codec<PartCategory> CODEC = Codec.stringResolver(PartCategory::key, key -> byKey(key).orElse(null));
    public static final StreamCodec<ByteBuf, PartCategory> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Enum::ordinal);

    private static final Map<String, PartCategory> BY_KEY = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(PartCategory::key, Function.identity()));

    private final String key;
    private final Class<? extends PartInfo> partClass;
    private final PartKind kind;
    private final int requiredCount;
    private final int slotCount;

    PartCategory(String key, Class<? extends PartInfo> partClass, PartKind kind, int requiredCount, int slotCount) {
        this.key = key;
        this.partClass = partClass;
        this.kind = kind;
        this.requiredCount = requiredCount;
        this.slotCount = slotCount;
    }

    public String key() {
        return this.key;
    }

    public Class<? extends PartInfo> partClass() {
        return this.partClass;
    }

    public PartKind kind() {
        return this.kind;
    }

    public List<PartInfo> parts() {
        return PartIndex.all(this.kind);
    }

    public int requiredCount() {
        return this.requiredCount;
    }

    public int slotCount() {
        return this.slotCount;
    }

    public boolean optional() {
        return this.requiredCount == 0;
    }

    public Optional<PartInfo> resolve(String partKey) {
        return PartIndex.byKey(partKey).filter(this.partClass::isInstance);
    }

    public @Nullable PartInfo get(DroneBuild build) {
        return switch (this) {
            case FRAME -> build.frame();
            case STACK -> build.stack();
            case MOTOR -> build.motor();
            case PROP -> build.prop();
            case VIDEO -> build.video();
            case BATTERY -> build.battery();
            case ACCESSORY -> build.accessories().list().isEmpty() ? null : build.accessories().list().getFirst();
        };
    }

    public @Nullable PartInfo get(DroneBuild build, int position) {
        if (position < 0 || position >= this.slotCount) {
            throw new IndexOutOfBoundsException(this.key + " hat keinen Platz " + position);
        }
        if (this == ACCESSORY) {
            return build.accessories().at(AccessoryMount.values()[position]).orElse(null);
        }
        return this.get(build);
    }

    public DroneBuild with(DroneBuild build, PartInfo part) {
        if (!this.partClass.isInstance(part)) {
            throw new IllegalArgumentException(part + " gehört nicht zur Kategorie " + this);
        }
        return switch (this) {
            case FRAME -> new DroneBuild((FrameType) part, build.stack(), build.motor(), build.prop(), build.video(), build.battery(),
                    build.accessories(), build.creative());
            case STACK -> new DroneBuild(build.frame(), (StackType) part, build.motor(), build.prop(), build.video(), build.battery(),
                    build.accessories(), build.creative());
            case MOTOR -> new DroneBuild(build.frame(), build.stack(), (MotorType) part, build.prop(), build.video(), build.battery(),
                    build.accessories(), build.creative());
            case PROP -> new DroneBuild(build.frame(), build.stack(), build.motor(), (PropType) part, build.video(), build.battery(),
                    build.accessories(), build.creative());
            case VIDEO -> new DroneBuild(build.frame(), build.stack(), build.motor(), build.prop(), (VideoType) part, build.battery(),
                    build.accessories(), build.creative());
            case BATTERY -> build.withBattery((BatteryType) part);
            case ACCESSORY -> build.withAccessory((AccessoryType) part);
        };
    }

    public String translationKey() {
        return "propwash.part_category." + this.key;
    }

    public static Optional<PartCategory> byKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key.toLowerCase(Locale.ROOT)));
    }

    public static PartCategory of(PartInfo part) {
        return switch (part) {
            case FrameType frame -> FRAME;
            case StackType stack -> STACK;
            case MotorType motor -> MOTOR;
            case PropType prop -> PROP;
            case VideoType video -> VIDEO;
            case BatteryType battery -> BATTERY;
            case AccessoryType accessory -> ACCESSORY;
        };
    }

    public static PartCategory of(PartKind kind) {
        return switch (kind) {
            case FRAME -> FRAME;
            case STACK -> STACK;
            case MOTOR -> MOTOR;
            case PROP -> PROP;
            case VIDEO -> VIDEO;
            case BATTERY -> BATTERY;
            case ACCESSORY -> ACCESSORY;
        };
    }
}
