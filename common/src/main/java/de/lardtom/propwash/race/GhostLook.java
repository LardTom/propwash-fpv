package de.lardtom.propwash.race;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.Accessories;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record GhostLook(DroneBuild build, DronePaint paint) {
    public static final int VERSION = 1;
    public static final int MAX_BYTES = 2048;
    private static final int MAX_ACCESSORIES = 8;
    private static final List<GhostLook> PRESETS = DroneBuild.PRESETS.stream().map(build -> new GhostLook(build, DronePaint.DEFAULT)).toList();
    private static final GhostLook DEFAULT_PRESET = new GhostLook(DroneBuild.FREESTYLE, DronePaint.DEFAULT);

    public GhostLook {
        build = build == null ? DroneBuild.FREESTYLE : build;
        paint = paint == null ? DronePaint.DEFAULT : paint;
    }

    public static GhostLook of(DroneBuild build, DronePaint paint) {
        return new GhostLook(build, paint);
    }

    public boolean matches(DroneBuild build, DronePaint paint) {
        return this.build.equals(build) && this.paint.equals(paint == null ? DronePaint.DEFAULT : paint);
    }

    public static GhostLook preset(String frameKey) {
        for (GhostLook preset : PRESETS) {
            if (preset.build().frame().key().equals(frameKey)) {
                return preset;
            }
        }
        return DEFAULT_PRESET;
    }

    public static boolean hasPreset(String frameKey) {
        for (GhostLook preset : PRESETS) {
            if (preset.build().frame().key().equals(frameKey)) {
                return true;
            }
        }
        return false;
    }

    public byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(96);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeUTF(this.build.frame().key());
            out.writeUTF(this.build.stack().key());
            out.writeUTF(this.build.motor().key());
            out.writeUTF(this.build.prop().key());
            out.writeUTF(this.build.video().key());
            out.writeUTF(this.build.battery().key());
            List<AccessoryType> accessories = this.build.accessories().list();
            out.writeByte(accessories.size());
            for (AccessoryType accessory : accessories) {
                out.writeUTF(accessory.key());
            }
            out.writeUTF(this.paint.isDefault() ? "" : this.paint.toText());
        } catch (IOException e) {
            throw new IllegalStateException("Aussehen des Geists nicht kodierbar", e);
        }
        return bytes.toByteArray();
    }

    public static Optional<GhostLook> decode(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) {
            return Optional.empty();
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readUnsignedByte() != VERSION) {
                return Optional.empty();
            }
            FrameType frame = part(FrameType.class, PartKind.FRAME, in.readUTF());
            StackType stack = part(StackType.class, PartKind.STACK, in.readUTF());
            MotorType motor = part(MotorType.class, PartKind.MOTOR, in.readUTF());
            PropType prop = part(PropType.class, PartKind.PROP, in.readUTF());
            VideoType video = part(VideoType.class, PartKind.VIDEO, in.readUTF());
            BatteryType battery = part(BatteryType.class, PartKind.BATTERY, in.readUTF());
            int count = in.readUnsignedByte();
            if (count > MAX_ACCESSORIES) {
                return Optional.empty();
            }
            List<AccessoryType> accessories = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String key = in.readUTF();
                AccessoryType type = byKey(AccessoryType.values(), key);
                if (type == null) {
                    Identifier id = PartIndex.idOfKey(key);
                    if (id == null || PartIndex.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
                        return Optional.empty();
                    }
                    continue;
                }
                accessories.add(type);
            }
            String paintText = in.readUTF();
            if (frame == null || stack == null || motor == null || prop == null || video == null || battery == null) {
                return Optional.empty();
            }
            DronePaint paint = paintText.isEmpty() ? DronePaint.DEFAULT : DronePaint.fromText(paintText).orElse(DronePaint.DEFAULT);
            DroneBuild build = new DroneBuild(frame, stack, motor, prop, video, battery, Accessories.of(accessories));
            return Optional.of(new GhostLook(build, paint));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static <E extends PartInfo> E part(Class<E> type, PartKind kind, String key) {
        return PartIndex.resolveOrMissing(type, kind, key).orElse(null);
    }

    static <E extends PartInfo> E byKey(E[] values, String key) {
        for (E value : values) {
            if (value.key().equals(key)) {
                return value;
            }
        }
        return null;
    }
}
