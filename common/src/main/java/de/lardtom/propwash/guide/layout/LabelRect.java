package de.lardtom.propwash.guide.layout;

public record LabelRect(float x0, float y0, float x1, float y1) {
    public static LabelRect of(float x, float y, float width, float height) {
        return new LabelRect(x, y, x + width, y + height);
    }

    public float width() {
        return this.x1 - this.x0;
    }

    public float height() {
        return this.y1 - this.y0;
    }

    public float area() {
        return Math.max(0.0F, this.width()) * Math.max(0.0F, this.height());
    }

    public float centerX() {
        return (this.x0 + this.x1) * 0.5F;
    }

    public float centerY() {
        return (this.y0 + this.y1) * 0.5F;
    }

    public LabelRect at(float x, float y) {
        return new LabelRect(x, y, x + this.width(), y + this.height());
    }

    public LabelRect grown(float margin) {
        return new LabelRect(this.x0 - margin, this.y0 - margin, this.x1 + margin, this.y1 + margin);
    }

    public boolean overlaps(LabelRect other, float gap) {
        return this.x0 < other.x1 + gap && this.x1 > other.x0 - gap && this.y0 < other.y1 + gap && this.y1 > other.y0 - gap;
    }

    public float overlap(LabelRect other) {
        float w = Math.min(this.x1, other.x1) - Math.max(this.x0, other.x0);
        float h = Math.min(this.y1, other.y1) - Math.max(this.y0, other.y0);
        return w > 0.0F && h > 0.0F ? w * h : 0.0F;
    }

    public boolean contains(float x, float y, float margin) {
        return x >= this.x0 - margin && x <= this.x1 + margin && y >= this.y0 - margin && y <= this.y1 + margin;
    }

    public boolean within(LabelRect outer, float tolerance) {
        return this.x0 >= outer.x0 - tolerance && this.y0 >= outer.y0 - tolerance && this.x1 <= outer.x1 + tolerance
                && this.y1 <= outer.y1 + tolerance;
    }

    public float nearestX(float x) {
        return Math.clamp(x, this.x0, this.x1);
    }

    public float nearestY(float y) {
        return Math.clamp(y, this.y0, this.y1);
    }

    public float distance(float x, float y) {
        float dx = this.nearestX(x) - x;
        float dy = this.nearestY(y) - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public boolean crosses(float ax, float ay, float bx, float by) {
        float t0 = 0.0F;
        float t1 = 1.0F;
        float dx = bx - ax;
        float dy = by - ay;
        float[] p = {-dx, dx, -dy, dy};
        float[] q = {ax - this.x0, this.x1 - ax, ay - this.y0, this.y1 - ay};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0F) {
                if (q[i] < 0.0F) {
                    return false;
                }
                continue;
            }
            float r = q[i] / p[i];
            if (p[i] < 0.0F) {
                t0 = Math.max(t0, r);
            } else {
                t1 = Math.min(t1, r);
            }
            if (t0 > t1) {
                return false;
            }
        }
        return true;
    }
}
