package de.lardtom.propwash.api.build;

import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record BuildSpec(Identifier frame, Identifier stack, Identifier motor, Identifier prop, Identifier video, Identifier battery,
                        Map<AccessorySlot, Identifier> accessories) {
    public BuildSpec {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(motor, "motor");
        Objects.requireNonNull(prop, "prop");
        Objects.requireNonNull(video, "video");
        Objects.requireNonNull(battery, "battery");
        EnumMap<AccessorySlot, Identifier> copy = new EnumMap<>(AccessorySlot.class);
        for (Map.Entry<AccessorySlot, Identifier> entry : Objects.requireNonNullElse(accessories, Map.<AccessorySlot, Identifier>of()).entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "slot"), Objects.requireNonNull(entry.getValue(), "accessory"));
        }
        accessories = Collections.unmodifiableMap(copy);
    }

    public static BuildSpec of(Identifier frame, Identifier stack, Identifier motor, Identifier prop, Identifier video, Identifier battery) {
        return new BuildSpec(frame, stack, motor, prop, video, battery, Map.of());
    }

    public Identifier part(PartKind kind) {
        return switch (kind) {
            case FRAME -> this.frame;
            case STACK -> this.stack;
            case MOTOR -> this.motor;
            case PROP -> this.prop;
            case VIDEO -> this.video;
            case BATTERY -> this.battery;
            case ACCESSORY -> throw new IllegalArgumentException("Zubehör über accessory(slot) abfragen");
        };
    }

    public Optional<Identifier> accessory(AccessorySlot slot) {
        return Optional.ofNullable(this.accessories.get(slot));
    }

    public BuildSpec withPart(PartKind kind, Identifier id) {
        return switch (kind) {
            case FRAME -> new BuildSpec(id, this.stack, this.motor, this.prop, this.video, this.battery, this.accessories);
            case STACK -> new BuildSpec(this.frame, id, this.motor, this.prop, this.video, this.battery, this.accessories);
            case MOTOR -> new BuildSpec(this.frame, this.stack, id, this.prop, this.video, this.battery, this.accessories);
            case PROP -> new BuildSpec(this.frame, this.stack, this.motor, id, this.video, this.battery, this.accessories);
            case VIDEO -> new BuildSpec(this.frame, this.stack, this.motor, this.prop, id, this.battery, this.accessories);
            case BATTERY -> new BuildSpec(this.frame, this.stack, this.motor, this.prop, this.video, id, this.accessories);
            case ACCESSORY -> throw new IllegalArgumentException("Zubehör über withAccessory(slot, id) setzen");
        };
    }

    public BuildSpec withAccessory(AccessorySlot slot, Identifier id) {
        EnumMap<AccessorySlot, Identifier> copy = new EnumMap<>(AccessorySlot.class);
        copy.putAll(this.accessories);
        copy.put(slot, id);
        return new BuildSpec(this.frame, this.stack, this.motor, this.prop, this.video, this.battery, copy);
    }

    public BuildSpec withoutAccessory(AccessorySlot slot) {
        if (!this.accessories.containsKey(slot)) {
            return this;
        }
        EnumMap<AccessorySlot, Identifier> copy = new EnumMap<>(AccessorySlot.class);
        copy.putAll(this.accessories);
        copy.remove(slot);
        return new BuildSpec(this.frame, this.stack, this.motor, this.prop, this.video, this.battery, copy);
    }

    public List<Identifier> allParts() {
        List<Identifier> out = new ArrayList<>(List.of(this.frame, this.stack, this.motor, this.prop, this.video, this.battery));
        out.addAll(this.accessories.values());
        return List.copyOf(out);
    }
}
