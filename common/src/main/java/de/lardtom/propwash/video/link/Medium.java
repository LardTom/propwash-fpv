package de.lardtom.propwash.video.link;

import org.jspecify.annotations.Nullable;

public final class Medium {
    public static final Medium AIR = new Medium(RfMaterial.AIR, null, 0.0);

    private final RfMaterial material;
    private final double @Nullable [] boxes;
    private final double reflectivity;

    public Medium(RfMaterial material, double @Nullable [] boxes, double reflectivity) {
        if (boxes != null && boxes.length % 6 != 0) {
            throw new IllegalArgumentException("boxes need six values each");
        }
        this.material = material;
        this.boxes = boxes == null || boxes.length == 0 ? null : boxes.clone();
        this.reflectivity = Math.clamp(reflectivity, 0.0, 1.0);
    }

    public static Medium full(RfMaterial material) {
        return new Medium(material, null, material == RfMaterial.AIR ? 0.0 : 1.0);
    }

    public RfMaterial material() {
        return this.material;
    }

    public boolean fullBlock() {
        return this.boxes == null;
    }

    public double @Nullable [] boxes() {
        return this.boxes;
    }

    public double reflectivity() {
        return this.reflectivity;
    }

    public boolean empty() {
        return this.material == RfMaterial.AIR;
    }
}
