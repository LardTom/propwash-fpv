package de.lardtom.propwash.server;

import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class PilotLink {
    final UUID pilotId;
    final QuadcopterEntity drone;
    @Nullable ServerPlayer pilot;
    AnchorRules.Decision decision = AnchorRules.Decision.LIMITED;
    boolean anchored;
    int viewDistance;
    int wantedViewDistance;
    double distance;
    int hiddenTicks;
    @Nullable ChunkPos anchorCenter;
    long lastSection = Long.MIN_VALUE;
    int lastMoveTick = Integer.MIN_VALUE;
    GeneratedArea.Scan area = GeneratedArea.unrestricted();
    GeneratedArea.Scan scannedArea = GeneratedArea.unrestricted();
    long areaChunk = Long.MIN_VALUE;
    int areaVersion = -1;
    int areaRadius = -1;
    @Nullable Spot droneTicket;
    @Nullable Spot viewTicket;
    @Nullable Spot lookahead;
    int lookaheadChunks;
    boolean generates;
    List<GeneratedCover.Square> cover = List.of();
    @Nullable Cover stableCover;
    long coverChunk = Long.MIN_VALUE;
    int coverVersion = -1;
    int coverRadius = -1;
    EdgeMask edge = EdgeMask.NONE;
    long edgeChunk = Long.MIN_VALUE;
    int edgeVersion = -1;
    GenerationEdgeS2C lastEdge = GenerationEdgeS2C.OFF;
    final Lookahead motion = new Lookahead();
    RangeStatusS2C lastStatus = RangeStatusS2C.IDLE;

    PilotLink(UUID pilotId, QuadcopterEntity drone) {
        this.pilotId = pilotId;
        this.drone = drone;
    }

    public UUID pilotId() {
        return this.pilotId;
    }

    public QuadcopterEntity drone() {
        return this.drone;
    }

    public @Nullable ServerPlayer pilot() {
        return this.pilot;
    }

    public AnchorRules.Decision decision() {
        return this.decision;
    }

    public boolean anchored() {
        return this.anchored;
    }

    public int viewDistance() {
        return this.anchored ? this.viewDistance : 0;
    }

    public int wantedViewDistance() {
        return this.wantedViewDistance;
    }

    public double distance() {
        return this.distance;
    }

    public GeneratedArea.Scan area() {
        return this.area;
    }

    public boolean generatesChunks() {
        return this.generates;
    }

    public double estimatedSpeed() {
        return this.motion.speedPerSecond();
    }

    public List<GeneratedCover.Square> cover() {
        Cover stable = this.stableCover;
        return stable != null ? stable.squares() : List.of();
    }

    public EdgeMask edge() {
        return this.edge;
    }

    public @Nullable ChunkPos lookahead() {
        Spot spot = this.lookahead;
        return spot != null ? spot.pos() : null;
    }

    public int lookaheadRadius() {
        Spot spot = this.lookahead;
        return spot != null ? spot.radius() : -1;
    }

    record Spot(ServerLevel level, ChunkPos pos, int radius) {
    }

    record Cover(ServerLevel level, List<GeneratedCover.Square> squares) {
    }
}
