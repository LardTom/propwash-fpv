package de.lardtom.propwash.guide.client.render;

import de.lardtom.propwash.guide.scene.ScenePos;

public record ScreenBox(String actor, float x0, float y0, float x1, float y1, ScenePos min, ScenePos max) {
    public boolean contains(ScenePos point, float margin) {
        return point.x() >= this.min.x() - margin && point.x() <= this.max.x() + margin
                && point.y() >= this.min.y() - margin && point.y() <= this.max.y() + margin
                && point.z() >= this.min.z() - margin && point.z() <= this.max.z() + margin;
    }

    public float volume() {
        return (this.max.x() - this.min.x()) * (this.max.y() - this.min.y()) * (this.max.z() - this.min.z());
    }

    public float overlap(float bx0, float by0, float bx1, float by1) {
        float w = Math.min(this.x1, bx1) - Math.max(this.x0, bx0);
        float h = Math.min(this.y1, by1) - Math.max(this.y0, by0);
        return w > 0.0F && h > 0.0F ? w * h : 0.0F;
    }
}
