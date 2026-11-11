package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Passage;
import de.lardtom.propwash.video.PilotFeed;
import de.lardtom.propwash.video.client.WatchedPilot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ScoreAreaRenderer {
    public static final double RANGE = 64.0;
    public static final double BUILD_RANGE = 32.0;
    public static final float ACTIVE = 0.85F;
    public static final float BUILD = 0.6F;
    public static final float NEAR = 0.5F;
    public static final float GATE_FILL = FreeformGateRenderer.FILL_ALPHA;
    public static final float AREA_FILL = 0.10F;
    public static final float EDGE_ALPHA = 0.55F;
    public static final float EDGE = 0.04F;
    private static final double LIFT = 0.002;
    private static final float MIN_STRENGTH = 0.01F;
    private static int framesDrawn;
    private static List<Drawn> lastDrawn = List.of();
    private static boolean lastBuilding;
    private static @Nullable Vec3 lastCamera;

    public enum Kind {
        OPENING,
        AREA
    }

    public record Drawn(int entityId, Kind kind, float strength, int passages, int color) {
    }

    private record Item(GateGeometry geometry, Kind kind, float strength, int color, int entityId) {
    }

    private ScoreAreaRenderer() {
    }

    public static int framesDrawn() {
        return framesDrawn;
    }

    public static List<Drawn> lastDrawn() {
        return lastDrawn;
    }

    public static boolean lastBuilding() {
        return lastBuilding;
    }

    public static @Nullable Vec3 lastCamera() {
        return lastCamera;
    }

    public static boolean enabled() {
        return RaceConfig.CONFIG.get().scoreAreas;
    }

    static boolean building(Minecraft minecraft) {
        return GateEditor.active() || minecraft.player != null && GateMounting.holdsTool(minecraft.player);
    }

    static int activeIndex(GateEntity gate) {
        PilotFeed watched = WatchedPilot.feed();
        if (watched != null) {
            if (!gate.inCourse() || gate.courseId() != watched.courseId()) {
                return GateEntity.NO_INDEX;
            }
            LapTracker.Phase phase = watched.phase();
            if (phase == LapTracker.Phase.RACING) {
                return watched.nextGateIndex();
            }
            return phase == LapTracker.Phase.IDLE || phase == LapTracker.Phase.COUNTDOWN ? watched.startGateIndex() : GateEntity.NO_INDEX;
        }
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        if (!gate.inCourse() || gate.courseId() != tracker.courseId()) {
            return GateEntity.NO_INDEX;
        }
        LapTracker.Phase phase = tracker.tracker().phase();
        if (phase == LapTracker.Phase.RACING) {
            return tracker.nextGateIndex();
        }
        if (phase == LapTracker.Phase.IDLE || phase == LapTracker.Phase.COUNTDOWN) {
            return tracker.startGateIndex();
        }
        return GateEntity.NO_INDEX;
    }

    public static boolean active(GateEntity gate) {
        int index = activeIndex(gate);
        return index != GateEntity.NO_INDEX && gate.hasPass(index);
    }

    static boolean activeReversed(GateEntity gate) {
        int index = activeIndex(gate);
        return index != GateEntity.NO_INDEX && gate.passReversed(index);
    }

    public static boolean scored(GateGeometry geometry) {
        if (geometry.gate() || geometry.freeform() || geometry.stages().isEmpty()) {
            return false;
        }
        CourseElement kind = geometry.scoringKind();
        return kind == CourseElement.POLE || kind == CourseElement.FLAG;
    }

    static void submit(SubmitNodeCollector collector, PoseStack poseStack, LevelRenderState levelState) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = levelState.cameraRenderState.pos;
        boolean watching = FpvView.spectating();
        if (minecraft.level == null || camera == null || minecraft.gui.hud.isHidden() || watching && WatchedPilot.feed() == null) {
            lastDrawn = List.of();
            return;
        }
        boolean shown = enabled();
        GateEditor editor = GateEditor.current();
        if (!shown && editor == null) {
            lastDrawn = List.of();
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float age = minecraft.level.getGameTime() + partialTick;
        Vec3 viewer = watching ? null : FreeformGateRenderer.viewer(partialTick);
        boolean building = !watching && building(minecraft);
        lastBuilding = building;
        lastCamera = camera;
        List<Item> items = new ArrayList<>();
        AABB area = new AABB(camera, camera).inflate(RANGE);
        for (GateEntity gate : minecraft.level.getEntitiesOfClass(GateEntity.class, area, gate -> !gate.isRemoved())) {
            boolean edited = editor != null && editor.gate() == gate;
            GateGeometry geometry = edited && editor.pending() != null && editor.status() == GateEditor.Status.FREE ? editor.pending()
                    : gate.geometry();
            if (geometry.freeform()) {
                continue;
            }
            boolean active = active(gate);
            int color = gate.inCourse() ? GateRenderer.lightColor(gate, age) : FreeformGateRenderer.IDLE_COLOR;
            if (geometry.gate()) {
                if (shown && active) {
                    items.add(new Item(geometry, Kind.OPENING, ACTIVE, color, gate.getId()));
                }
                continue;
            }
            if (!scored(geometry)) {
                continue;
            }
            float strength = 0.0F;
            if (edited) {
                strength = ACTIVE;
            } else if (shown) {
                if (active) {
                    strength = ACTIVE;
                }
                if (building && camera.distanceToSqr(geometry.centerX(), geometry.centerY(), geometry.centerZ()) <= BUILD_RANGE * BUILD_RANGE) {
                    strength = Math.max(strength, BUILD);
                }
                if (viewer != null) {
                    strength = Math.max(strength, NEAR * FreeformGateRenderer.approach(geometry, viewer));
                }
            }
            if (strength > MIN_STRENGTH) {
                if (active && !edited) {
                    items.add(new Item(geometry.withReverse(activeReversed(gate)), Kind.AREA, strength, color, gate.getId()));
                } else {
                    if (gate.flownForward() || edited) {
                        items.add(new Item(geometry, Kind.AREA, strength, color, gate.getId()));
                    }
                    if (gate.flownReversed() && !edited) {
                        items.add(new Item(geometry.withReverse(true), Kind.AREA, strength, color, gate.getId()));
                    }
                }
            }
        }
        if (items.isEmpty()) {
            lastDrawn = List.of();
            return;
        }
        List<Drawn> drawn = new ArrayList<>(items.size());
        for (Item item : items) {
            drawn.add(new Drawn(item.entityId(), item.kind(), item.strength(), item.kind() == Kind.OPENING ? 1 : item.geometry().stages().size(),
                    item.color()));
        }
        lastDrawn = List.copyOf(drawn);
        framesDrawn++;
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        collector.order(1).submitCustomGeometry(poseStack, GateRenderer.glowType(), (pose, buffer) -> {
            for (Item item : items) {
                if (item.kind() == Kind.OPENING) {
                    opening(pose, buffer, item.geometry(), ARGB.color(GATE_FILL * item.strength(), item.color()));
                } else {
                    for (Passage passage : item.geometry().stages()) {
                        area(pose, buffer, passage, item.color(), item.strength());
                    }
                }
            }
        });
        poseStack.popPose();
    }

    static void opening(PoseStack.Pose pose, VertexConsumer buffer, GateGeometry gate, int tint) {
        double inner = gate.innerHalfWidth();
        double bottom = gate.openingFloor();
        double top = gate.height() - gate.frame();
        if (inner <= 0.0 || top <= bottom) {
            return;
        }
        GateRenderer.quad(pose, buffer,
                x(gate, -inner, bottom), y(gate, -inner, bottom), z(gate, -inner, bottom), tint,
                x(gate, inner, bottom), y(gate, inner, bottom), z(gate, inner, bottom), tint,
                x(gate, inner, top), y(gate, inner, top), z(gate, inner, top), tint,
                x(gate, -inner, top), y(gate, -inner, top), z(gate, -inner, top), tint);
    }

    private static float x(GateGeometry gate, double side, double up) {
        return (float) gate.worldX(side, up, 0.0);
    }

    private static float y(GateGeometry gate, double side, double up) {
        return (float) gate.worldY(side, up, 0.0);
    }

    private static float z(GateGeometry gate, double side, double up) {
        return (float) gate.worldZ(side, up, 0.0);
    }

    static void area(PoseStack.Pose pose, VertexConsumer buffer, Passage passage, int color, float strength) {
        double s0 = passage.side0();
        double s1 = passage.side1();
        double u0 = passage.up0();
        double u1 = passage.up1();
        if (s1 <= s0 || u1 <= u0) {
            return;
        }
        int fill = ARGB.color(AREA_FILL * strength, color);
        int edge = ARGB.color(EDGE_ALPHA * strength, color);
        rect(pose, buffer, passage, s0, u0, s1, u1, fill);
        double e = Math.min(EDGE, Math.min(s1 - s0, u1 - u0) * 0.25);
        rect(pose, buffer, passage, s0, u0, s1, u0 + e, edge);
        rect(pose, buffer, passage, s0, u1 - e, s1, u1, edge);
        rect(pose, buffer, passage, s0, u0 + e, s0 + e, u1 - e, edge);
        rect(pose, buffer, passage, s1 - e, u0 + e, s1, u1 - e, edge);
    }

    private static void rect(PoseStack.Pose pose, VertexConsumer buffer, Passage p, double s0, double u0, double s1, double u1, int color) {
        GateRenderer.quad(pose, buffer,
                px(p, s0, u0), py(p, s0, u0), pz(p, s0, u0), color,
                px(p, s1, u0), py(p, s1, u0), pz(p, s1, u0), color,
                px(p, s1, u1), py(p, s1, u1), pz(p, s1, u1), color,
                px(p, s0, u1), py(p, s0, u1), pz(p, s0, u1), color);
    }

    private static float px(Passage p, double s, double u) {
        return (float) (p.ox() + p.sx() * s + p.ux() * u + p.nx() * LIFT);
    }

    private static float py(Passage p, double s, double u) {
        return (float) (p.oy() + p.sy() * s + p.uy() * u + p.ny() * LIFT);
    }

    private static float pz(Passage p, double s, double u) {
        return (float) (p.oz() + p.sz() * s + p.uz() * u + p.nz() * LIFT);
    }

    public static @Nullable Drawn drawnFor(int entityId) {
        for (Drawn drawn : lastDrawn) {
            if (drawn.entityId() == entityId) {
                return drawn;
            }
        }
        return null;
    }
}
