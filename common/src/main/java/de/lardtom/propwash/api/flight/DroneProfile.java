package de.lardtom.propwash.api.flight;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record DroneProfile(String frame, String stack, String motor, String prop, String video, String battery,
                           Map<String, String> accessories, double massKg) {
    public DroneProfile {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(motor, "motor");
        Objects.requireNonNull(prop, "prop");
        Objects.requireNonNull(video, "video");
        Objects.requireNonNull(battery, "battery");
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : Objects.requireNonNullElse(accessories, Map.<String, String>of()).entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "slot"), Objects.requireNonNull(entry.getValue(), "accessory"));
        }
        accessories = Collections.unmodifiableMap(copy);
        if (!(massKg > 0.0) || !Double.isFinite(massKg)) {
            throw new IllegalArgumentException("Masse ungültig: " + massKg);
        }
    }

    public Optional<String> accessory(String slotKey) {
        return Optional.ofNullable(this.accessories.get(slotKey));
    }

    public boolean hasAccessory(String accessoryId) {
        return this.accessories.containsValue(accessoryId);
    }

    public boolean has(String partId) {
        return this.frame.equals(partId) || this.stack.equals(partId) || this.motor.equals(partId) || this.prop.equals(partId)
                || this.video.equals(partId) || this.battery.equals(partId) || this.hasAccessory(partId);
    }
}
