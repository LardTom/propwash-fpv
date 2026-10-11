package de.lardtom.propwash.guide.scene;

import de.lardtom.propwash.race.FreeformRegion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SceneFraming {
    public static final float MARGIN_SIDE = 0.12F;
    public static final float MARGIN_TOP = 0.11F;
    public static final float MARGIN_BOTTOM = 0.17F;
    public static final float MIN_FIT = 0.45F;
    public static final float MAX_FIT = 3.0F;
    public static final float NEARBY = 1.3F;
    public static final float BLOCK_NEARBY = 0.6F;
    public static final float DRONE_VISUAL_SCALE = 1.75F;
    public static final float DRONE_MARGIN_MM = 20.0F;
    public static final float SAMPLE_TICKS = 2.0F;
    public static final float WINDOW_TICKS = 24.0F;
    public static final float NEW_ACTOR_TICKS = 40.0F;
    private static final float PILOT_HEIGHT = 2.35F;
    private static final float PILOT_HALF = 0.3F;
    private static final float MOVE_EPSILON = 1.0E-4F;
    private static final float HANG_DROP = 1.0F;
    private static final float FLAT_HALF_DEPTH = 0.25F;

    private final float[][] samples;

    private SceneFraming(float[][] samples) {
        this.samples = samples;
    }

    public static SceneFraming of(Scene scene) {
        int count = Math.max(1, (int) Math.ceil(scene.duration() / SAMPLE_TICKS) + 1);
        float[][] samples = new float[count][];
        Map<String, Float> firstSeen = new HashMap<>();
        Map<String, ScenePos> lastPos = new HashMap<>();
        for (int i = 0; i < count; i++) {
            float t = Math.min(scene.duration(), i * SAMPLE_TICKS);
            SceneState state = SceneEvaluator.evaluate(scene, t);
            List<float[]> boxes = new ArrayList<>();
            CameraSpec camera = state.camera();
            float nearby = scene.viewRadius() / Math.max(0.1F, camera.zoom()) * NEARBY;
            for (ActorState actor : state.visibleActors()) {
                String id = actor.id();
                float since = t - firstSeen.computeIfAbsent(id, k -> t);
                ScenePos before = lastPos.put(id, actor.position());
                boolean moving = before != null && distanceSq(before, actor.position()) > MOVE_EPSILON;
                boolean near = distanceSq(actor.position(), camera.target()) <= nearby * nearby;
                if (actor.spec() instanceof ActorSpec.Drone || moving || since < NEW_ACTOR_TICKS || near) {
                    boxes.add(box(actor.spec(), actor.position(), actor.yaw()));
                }
            }
            for (SceneState.BlockView block : state.blocks()) {
                BlockPlacement at = block.at();
                float blockNear = nearby * BLOCK_NEARBY;
                if (at.y() >= 1 && block.scale() > 0.5F && distanceSq(at.center(), camera.target()) <= blockNear * blockNear) {
                    boxes.add(new float[]{at.x(), at.y(), at.z(), at.x() + 1.0F, at.y() + 1.0F, at.z() + 1.0F});
                }
            }
            for (SceneState.CalloutView callout : state.callouts()) {
                if (callout.alpha() > 0.0F) {
                    boxes.add(point(callout.anchor()));
                }
            }
            for (SceneState.KeyHintView hint : state.keyHints()) {
                if (hint.anchor() != null && hint.alpha() > 0.0F) {
                    boxes.add(point(hint.anchor()));
                }
            }
            for (SceneState.HighlightView highlight : state.highlights()) {
                if (highlight.alpha() > 0.0F) {
                    boxes.add(new float[]{highlight.min().x(), highlight.min().y(), highlight.min().z(),
                            highlight.max().x(), highlight.max().y(), highlight.max().z()});
                }
            }
            float[] flat = new float[boxes.size() * 6];
            for (int b = 0; b < boxes.size(); b++) {
                System.arraycopy(boxes.get(b), 0, flat, b * 6, 6);
            }
            samples[i] = flat;
        }
        return new SceneFraming(samples);
    }

    private static float distanceSq(ScenePos a, ScenePos b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        float dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static float[] point(ScenePos p) {
        return new float[]{p.x(), p.y(), p.z(), p.x(), p.y(), p.z()};
    }

    private static float[] box(ActorSpec spec, ScenePos p, float yaw) {
        return switch (spec) {
            case ActorSpec.Drone drone -> around(p, droneRadius(drone), 0.2F * drone.scale());
            case ActorSpec.Pilot _ -> around(p, PILOT_HALF, PILOT_HEIGHT);
            case ActorSpec.Gate gate -> gateBox(gate, p, yaw);
            case ActorSpec.Item item -> around(p, 0.3F * item.scale(), 0.6F * item.scale());
            case ActorSpec.Block _ -> around(p, 0.5F, 1.0F);
            case ActorSpec.Remote remote -> around(p, 0.45F * remote.scale(), 0.9F * remote.scale());
        };
    }

    private static float[] gateBox(ActorSpec.Gate gate, ScenePos p, float yaw) {
        if (gate.isFreeform()) {
            FreeformRegion region = FreeformRegion.decode(gate.region());
            float half = region == null ? 0.5F : Math.max(region.width(), region.height()) * 0.5F;
            float up = region == null || region.horizontal() ? 0.5F : region.height() * 0.5F;
            return new float[]{p.x() - half, p.y() - up, p.z() - half, p.x() + half, p.y() + up, p.z() + half};
        }
        if (gate.isElement()) {
            float reach = gate.element().equals("cube") ? 2.0F : 1.6F;
            float height = gate.openingHeight() > 0.0F ? gate.openingHeight() + 0.5F : 4.0F;
            return around(p, reach, height + 0.8F);
        }
        float half = gate.outerWidth(gateHalf(gate.size()) * 2.0F) * 0.5F;
        float height = gate.outerHeight(gateHeight(gate.size()));
        return switch (gate.mount()) {
            case "hanging" -> around(p, half, height + HANG_DROP);
            case "wall_dive", "floor_flat", "ceiling_flat" -> {
                double radians = Math.toRadians(yaw);
                float fx = (float) -Math.sin(radians) * height;
                float fz = (float) Math.cos(radians) * height;
                float rx = (float) Math.abs(Math.cos(radians)) * half;
                float rz = (float) Math.abs(Math.sin(radians)) * half;
                yield new float[]{p.x() + Math.min(0.0F, fx) - rx, p.y() - FLAT_HALF_DEPTH, p.z() + Math.min(0.0F, fz) - rz,
                        p.x() + Math.max(0.0F, fx) + rx, p.y() + FLAT_HALF_DEPTH, p.z() + Math.max(0.0F, fz) + rz};
            }
            default -> around(p, half, height);
        };
    }

    private static float gateHalf(String size) {
        return switch (size) {
            case "small" -> 1.5F;
            case "large" -> 3.0F;
            default -> 2.0F;
        };
    }

    private static float gateHeight(String size) {
        return "small".equals(size) ? 3.0F : 4.0F;
    }

    public static float droneRadius(ActorSpec.Drone drone) {
        float mm = drone.build().frame().wheelbaseMm * 0.5F + drone.build().prop().diameterMm * 0.5F + DRONE_MARGIN_MM;
        return mm * DRONE_VISUAL_SCALE / 1000.0F * drone.scale();
    }

    private static float[] around(ScenePos p, float half, float height) {
        return new float[]{p.x() - half, p.y(), p.z() - half, p.x() + half, p.y() + height, p.z() + half};
    }

    public int sampleCount() {
        return this.samples.length;
    }

    public SceneProjection projection(CameraSpec camera, float time, float basePixelsPerBlock, float left, float top, float width, float height,
                                      float userZoom) {
        float zoom = userZoom > 0.0F && Float.isFinite(userZoom) ? userZoom : 1.0F;
        SceneProjection raw = new SceneProjection(camera, basePixelsPerBlock, left + width / 2.0F, top + height / 2.0F);
        float[] fit = this.fit(raw, time, left, top, width, height);
        float scale = fit[0] * zoom;
        return new SceneProjection(camera, basePixelsPerBlock * scale, fit[3] - fit[1] * scale, fit[4] - fit[2] * scale);
    }

    public float[] fit(SceneProjection raw, float time, float left, float top, float width, float height) {
        float[] identity = {1.0F, 0.0F, 0.0F, raw.centerX(), raw.centerY()};
        if (!(width > 0.0F) || !(height > 0.0F) || !Float.isFinite(time)) {
            return identity;
        }
        float availLeft = left + MARGIN_SIDE * width;
        float availRight = left + width - MARGIN_SIDE * width;
        float availTop = top + MARGIN_TOP * height;
        float availBottom = top + height - MARGIN_BOTTOM * height;
        float[] full = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        List<float[]> partial = new ArrayList<>();
        float[] out = new float[3];
        int first = Math.max(0, (int) Math.floor((time - WINDOW_TICKS) / SAMPLE_TICKS));
        int last = Math.min(this.samples.length - 1, (int) Math.ceil((time + WINDOW_TICKS) / SAMPLE_TICKS));
        for (int i = first; i <= last; i++) {
            float distance = Math.abs(i * SAMPLE_TICKS - time);
            float weight = distance <= WINDOW_TICKS * 0.5F ? 1.0F : Math.clamp(2.0F - 2.0F * distance / WINDOW_TICKS, 0.0F, 1.0F);
            if (weight <= 0.0F) {
                continue;
            }
            float[] boxes = this.samples[i];
            for (int b = 0; b < boxes.length; b += 6) {
                float[] bounds = weight >= 1.0F ? full : new float[]{Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, weight};
                for (int corner = 0; corner < 8; corner++) {
                    float x = boxes[b + ((corner & 1) == 0 ? 0 : 3)];
                    float y = boxes[b + ((corner & 2) == 0 ? 1 : 4)];
                    float z = boxes[b + ((corner & 4) == 0 ? 2 : 5)];
                    raw.project(x, y, z, out);
                    bounds[0] = Math.min(bounds[0], out[0] - raw.centerX());
                    bounds[1] = Math.min(bounds[1], out[1] - raw.centerY());
                    bounds[2] = Math.max(bounds[2], out[0] - raw.centerX());
                    bounds[3] = Math.max(bounds[3], out[1] - raw.centerY());
                }
                if (weight < 1.0F) {
                    partial.add(bounds);
                }
            }
        }
        if (full[0] > full[2]) {
            if (partial.isEmpty()) {
                return identity;
            }
            float[] strongest = partial.getFirst();
            for (float[] p : partial) {
                if (p[4] > strongest[4]) {
                    strongest = p;
                }
            }
            System.arraycopy(strongest, 0, full, 0, 4);
        }
        for (float[] p : partial) {
            float w = p[4];
            full[0] = Math.min(full[0], full[0] + (p[0] - full[0]) * w);
            full[1] = Math.min(full[1], full[1] + (p[1] - full[1]) * w);
            full[2] = Math.max(full[2], full[2] + (p[2] - full[2]) * w);
            full[3] = Math.max(full[3], full[3] + (p[3] - full[3]) * w);
        }
        float spanX = Math.max(1.0F, full[2] - full[0]);
        float spanY = Math.max(1.0F, full[3] - full[1]);
        float factor = Math.clamp(Math.min((availRight - availLeft) / spanX, (availBottom - availTop) / spanY), MIN_FIT, MAX_FIT);
        return new float[]{factor, (full[0] + full[2]) / 2.0F, (full[1] + full[3]) / 2.0F, (availLeft + availRight) / 2.0F,
                (availTop + availBottom) / 2.0F};
    }
}
