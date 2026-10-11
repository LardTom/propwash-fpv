package de.lardtom.propwash.guide.scene;

public record BlockPlacement(int x, int y, int z) {
    public long key() {
        return ((long) (this.x & 0x1FFFFF) << 42) | ((long) (this.y & 0x1FFFFF) << 21) | (long) (this.z & 0x1FFFFF);
    }

    public ScenePos center() {
        return new ScenePos(this.x + 0.5F, this.y + 0.5F, this.z + 0.5F);
    }

    public ScenePos top() {
        return new ScenePos(this.x + 0.5F, this.y + 1.0F, this.z + 0.5F);
    }
}
