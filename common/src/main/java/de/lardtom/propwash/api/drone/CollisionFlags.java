package de.lardtom.propwash.api.drone;

public record CollisionFlags(boolean blocks, boolean fluids, boolean entities, boolean snags) {
    public static final CollisionFlags SOLID = new CollisionFlags(true, true, true, true);
    public static final CollisionFlags GHOST = new CollisionFlags(false, false, false, false);

    public CollisionFlags withBlocks(boolean value) {
        return new CollisionFlags(value, this.fluids, this.entities, this.snags);
    }

    public CollisionFlags withFluids(boolean value) {
        return new CollisionFlags(this.blocks, value, this.entities, this.snags);
    }

    public CollisionFlags withEntities(boolean value) {
        return new CollisionFlags(this.blocks, this.fluids, value, this.snags);
    }

    public CollisionFlags withSnags(boolean value) {
        return new CollisionFlags(this.blocks, this.fluids, this.entities, value);
    }

    public CollisionFlags and(CollisionFlags other) {
        return new CollisionFlags(this.blocks && other.blocks, this.fluids && other.fluids, this.entities && other.entities,
                this.snags && other.snags);
    }

    public boolean solid() {
        return this.blocks && this.fluids && this.entities && this.snags;
    }
}
