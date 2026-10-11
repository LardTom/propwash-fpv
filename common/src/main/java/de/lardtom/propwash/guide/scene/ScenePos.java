package de.lardtom.propwash.guide.scene;

public record ScenePos(float x, float y, float z) {
    public static final ScenePos ZERO = new ScenePos(0.0F, 0.0F, 0.0F);

    public static ScenePos of(double x, double y, double z) {
        return new ScenePos((float) x, (float) y, (float) z);
    }

    public ScenePos add(ScenePos other) {
        return new ScenePos(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public ScenePos add(float dx, float dy, float dz) {
        return new ScenePos(this.x + dx, this.y + dy, this.z + dz);
    }

    public ScenePos subtract(ScenePos other) {
        return new ScenePos(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    public ScenePos scale(float factor) {
        return new ScenePos(this.x * factor, this.y * factor, this.z * factor);
    }

    public ScenePos lerp(ScenePos to, float t) {
        return new ScenePos(this.x + (to.x - this.x) * t, this.y + (to.y - this.y) * t, this.z + (to.z - this.z) * t);
    }

    public float length() {
        return (float) Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    public float distance(ScenePos other) {
        return this.subtract(other).length();
    }

    public boolean isZero() {
        return this.x == 0.0F && this.y == 0.0F && this.z == 0.0F;
    }
}
