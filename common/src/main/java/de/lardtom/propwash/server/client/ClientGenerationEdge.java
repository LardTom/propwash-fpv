package de.lardtom.propwash.server.client;

import de.lardtom.propwash.server.EdgeFence;
import de.lardtom.propwash.server.EdgeMask;
import de.lardtom.propwash.server.GenerationEdgeS2C;

public final class ClientGenerationEdge {
    public static final ClientGenerationEdge INSTANCE = new ClientGenerationEdge();

    private GenerationEdgeS2C payload = GenerationEdgeS2C.OFF;
    private EdgeMask mask = EdgeMask.NONE;
    private EdgeFence fence = EdgeFence.NONE;
    private int updates;

    private ClientGenerationEdge() {
    }

    public boolean restricted() {
        return this.payload.restricted();
    }

    public EdgeMask mask() {
        return this.mask;
    }

    public EdgeFence fence() {
        return this.fence;
    }

    public int updates() {
        return this.updates;
    }

    void accept(GenerationEdgeS2C payload) {
        this.payload = payload;
        this.mask = payload.mask();
        this.fence = EdgeFence.of(this.mask);
        this.updates++;
    }

    public void clear() {
        this.payload = GenerationEdgeS2C.OFF;
        this.mask = EdgeMask.NONE;
        this.fence = EdgeFence.NONE;
    }
}
