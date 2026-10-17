package de.lardtom.propwash.guide.scene;

import de.lardtom.propwash.input.StickMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class SceneEvaluator {
    public static final float FADE_TICKS = 5.0F;
    public static final float DROP_HEIGHT = 1.25F;
    private static final float END_EPSILON = 1.0E-3F;

    private SceneEvaluator() {
    }

    public static SceneState evaluate(Scene scene, float time) {
        return evaluate(scene, time, StickMode.MODE_2);
    }

    public static SceneState evaluate(Scene scene, float time, StickMode mode) {
        float total = scene.duration();
        float t = Float.isFinite(time) ? Math.clamp(time, 0.0F, total) : 0.0F;
        boolean atEnd = t >= total - END_EPSILON;
        Map<Long, SceneState.BlockView> blocks = new LinkedHashMap<>();
        Map<String, ActorState> actors = new LinkedHashMap<>();
        List<Pending> pending = new ArrayList<>();
        CameraSpec camera = scene.camera();
        for (int i = 0; i < scene.stepCount(); i++) {
            SceneStep step = scene.steps().get(i);
            float offset = scene.stepStart(i);
            for (int c = 0; c < step.cues().size(); c++) {
                TimedCue timed = step.cues().get(c);
                float start = offset + timed.start();
                if (start > t) {
                    continue;
                }
                float p = timed.duration() > 0.0F ? Ease.clamp01((t - start) / timed.duration()) : 1.0F;
                Cue cue = timed.cue();
                if (cue.transientCue()) {
                    float end = scene.cueEnd(i, c);
                    boolean lastsToEnd = end >= total - END_EPSILON;
                    if (t < end || (atEnd && lastsToEnd)) {
                        float fadeIn = Ease.clamp01((t - start) / FADE_TICKS);
                        float fadeOut = lastsToEnd ? 1.0F : Ease.clamp01((end - t) / FADE_TICKS);
                        pending.add(new Pending(cue, Math.min(fadeIn, fadeOut), t - start, scene.cueId(i, c)));
                    }
                    continue;
                }
                camera = apply(cue, p, t - start, timed, blocks, actors, camera);
            }
        }
        List<SceneState.CalloutView> callouts = new ArrayList<>();
        List<SceneState.HighlightView> highlights = new ArrayList<>();
        List<SceneState.KeyHintView> keys = new ArrayList<>();
        for (Pending entry : pending) {
            switch (entry.cue()) {
                case Cue.Callout callout -> {
                    ScenePos anchor = callout.anchor().resolve(actors, mode);
                    if (anchor != null) {
                        Cue.Side side = callout.anchor().mirrored(mode) ? mirror(callout.side()) : callout.side();
                        callouts.add(new SceneState.CalloutView(callout.textKey(), anchor, side, entry.alpha(), entry.age(), entry.id()));
                    }
                }
                case Cue.Highlight highlight -> highlights.add(new SceneState.HighlightView(highlight.min(), highlight.max(), entry.alpha(), entry.age()));
                case Cue.Keys hint -> {
                    ScenePos anchor = hint.anchor() != null ? hint.anchor().resolve(actors, mode) : null;
                    if (hint.anchor() == null || anchor != null) {
                        keys.add(new SceneState.KeyHintView(hint.textKey(), hint.keys(), anchor, entry.alpha()));
                    }
                }
                default -> {
                }
            }
        }
        int step = scene.stepAt(t);
        float stepLength = scene.stepEnd(step) - scene.stepStart(step);
        float stepProgress = stepLength > 0.0F ? Ease.clamp01((t - scene.stepStart(step)) / stepLength) : 1.0F;
        return new SceneState(t, step, stepProgress, new ArrayList<>(blocks.values()), actors, callouts, highlights, keys, camera);
    }

    private static CameraSpec apply(Cue cue, float p, float age, TimedCue timed, Map<Long, SceneState.BlockView> blocks,
                                    Map<String, ActorState> actors, CameraSpec camera) {
        switch (cue) {
            case Cue.PlaceBlock place -> {
                float scale = 1.0F;
                float lift = 0.0F;
                switch (place.appear()) {
                    case DROP -> lift = (1.0F - Ease.OUT.apply(p)) * DROP_HEIGHT;
                    case POP -> scale = Math.max(0.0F, Ease.BACK.apply(p));
                    case INSTANT -> {
                    }
                }
                blocks.put(place.at().key(), new SceneState.BlockView(place.at(), place.block(), scale, lift));
            }
            case Cue.RemoveBlock remove -> {
                long key = remove.at().key();
                SceneState.BlockView existing = blocks.get(key);
                if (existing != null) {
                    if (p >= 1.0F) {
                        blocks.remove(key);
                    } else {
                        blocks.put(key, new SceneState.BlockView(existing.at(), existing.block(), existing.scale() * (1.0F - Ease.IN.apply(p)),
                                existing.lift()));
                    }
                }
            }
            case Cue.Spawn spawn -> {
                ScenePos position = spawn.position();
                ActorState actor = new ActorState(spawn.actor(), spawn.spec(), position, spawn.yaw());
                switch (spawn.appear()) {
                    case POP -> actor.presence(Math.max(0.0F, Ease.BACK.apply(p)));
                    case DROP -> actor.position(position.add(0.0F, (1.0F - Ease.OUT.apply(p)) * DROP_HEIGHT, 0.0F));
                    case INSTANT -> {
                    }
                }
                actors.put(spawn.actor(), actor);
            }
            case Cue.Despawn despawn -> {
                ActorState actor = actors.get(despawn.actor());
                if (actor != null) {
                    if (p >= 1.0F) {
                        actors.remove(despawn.actor());
                    } else {
                        actor.presence(actor.presence() * (1.0F - Ease.IN.apply(p)));
                    }
                }
            }
            case Cue.Move move -> {
                ActorState actor = actors.get(move.actor());
                if (actor != null) {
                    actor.position(path(actor.position(), move.path(), move.ease().apply(p)));
                }
            }
            case Cue.Turn turn -> {
                ActorState actor = actors.get(turn.actor());
                if (actor != null) {
                    float e = turn.ease().apply(p);
                    actor.rotation(actor.yaw() + CameraSpec.wrapDegrees(turn.yaw() - actor.yaw()) * e,
                            actor.pitch() + (turn.pitch() - actor.pitch()) * e, actor.roll() + (turn.roll() - actor.roll()) * e);
                }
            }
            case Cue.Sticks sticks -> withActor(actors, sticks.actor(), actor -> {
                float e = sticks.ease().apply(p);
                actor.sticks(actor.leftX() + (sticks.leftX() - actor.leftX()) * e, actor.leftY() + (sticks.leftY() - actor.leftY()) * e,
                        actor.rightX() + (sticks.rightX() - actor.rightX()) * e, actor.rightY() + (sticks.rightY() - actor.rightY()) * e);
            });
            case Cue.Arm arm -> withActor(actors, arm.actor(), actor -> actor.armed(arm.armed()));
            case Cue.Throttle throttle -> withActor(actors, throttle.actor(), actor -> {
                float target = Float.isNaN(throttle.throttle()) ? 0.0F : Math.clamp(throttle.throttle(), -1.0F, 1.0F);
                actor.throttle(actor.throttle() + (target - actor.throttle()) * Ease.IN_OUT.apply(p));
            });
            case Cue.AttachPart attach -> withActor(actors, attach.actor(), actor -> actor.part(attach.part(), part(attach, age, timed.duration())));
            case Cue.DetachPart detach -> withActor(actors, detach.actor(), actor -> actor.part(detach.part(), detached(detach, p)));
            case Cue.Equip equip -> withActor(actors, equip.actor(), actor -> {
                if (equip.slot() == Cue.Slot.HEAD) {
                    actor.headItem(equip.item());
                } else {
                    actor.handItem(equip.item());
                }
            });
            case Cue.Crouch crouch -> withActor(actors, crouch.actor(), actor -> actor.crouching(crouch.crouching()));
            case Cue.PropCondition wear -> withActor(actors, wear.actor(), actor -> actor.propCondition(wear.slot(), wear.condition()));
            case Cue.Look look -> withActor(actors, look.actor(),
                    actor -> actor.headPitch(actor.headPitch() + (look.headPitch() - actor.headPitch()) * look.ease().apply(p)));
            case Cue.Light light -> withActor(actors, light.actor(), actor -> actor.lit(light.lit()));
            case Cue.Camera cam -> {
                return camera.lerp(new CameraSpec(cam.target(), cam.yaw(), cam.pitch(), cam.zoom()), cam.ease().apply(p));
            }
            default -> {
            }
        }
        return camera;
    }

    private static void withActor(Map<String, ActorState> actors, String id, Consumer<ActorState> action) {
        ActorState actor = actors.get(id);
        if (actor != null) {
            action.accept(actor);
        }
    }

    static ActorState.PartState part(Cue.AttachPart attach, float age, float duration) {
        DronePart part = attach.part();
        int slots = part.slots();
        float[] progress = new float[slots];
        float stagger = Math.max(0.0F, attach.stagger());
        float flight = duration - stagger * (slots - 1);
        for (int slot = 0; slot < slots; slot++) {
            float local = age - slot * stagger;
            if (local < 0.0F) {
                progress[slot] = -1.0F;
            } else if (!(flight > 0.0F)) {
                progress[slot] = 1.0F;
            } else {
                progress[slot] = Ease.clamp01(local / flight);
            }
        }
        return new ActorState.PartState(part, true, attach.from(), progress);
    }

    static ActorState.PartState detached(Cue.DetachPart detach, float p) {
        DronePart part = detach.part();
        if (p >= 1.0F) {
            return ActorState.PartState.hidden(part);
        }
        float[] progress = new float[part.slots()];
        Arrays.fill(progress, 1.0F - Ease.clamp01(p));
        return new ActorState.PartState(part, true, detach.to(), progress);
    }

    static Cue.Side mirror(Cue.Side side) {
        return switch (side) {
            case LEFT -> Cue.Side.RIGHT;
            case RIGHT -> Cue.Side.LEFT;
            default -> side;
        };
    }

    public static ScenePos path(ScenePos from, List<ScenePos> points, float t) {
        int count = points.size() + 1;
        if (count == 2) {
            return from.lerp(points.getFirst(), t);
        }
        float scaled = Ease.clamp01(t) * (count - 1);
        int segment = Math.min((int) Math.floor(scaled), count - 2);
        float u = scaled - segment;
        ScenePos p0 = point(from, points, segment - 1);
        ScenePos p1 = point(from, points, segment);
        ScenePos p2 = point(from, points, segment + 1);
        ScenePos p3 = point(from, points, segment + 2);
        return catmullRom(p0, p1, p2, p3, u);
    }

    private static ScenePos point(ScenePos from, List<ScenePos> points, int index) {
        int clamped = Math.clamp(index, 0, points.size());
        return clamped == 0 ? from : points.get(clamped - 1);
    }

    private static ScenePos catmullRom(ScenePos p0, ScenePos p1, ScenePos p2, ScenePos p3, float u) {
        return new ScenePos(catmullRom(p0.x(), p1.x(), p2.x(), p3.x(), u), catmullRom(p0.y(), p1.y(), p2.y(), p3.y(), u),
                catmullRom(p0.z(), p1.z(), p2.z(), p3.z(), u));
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float u) {
        float u2 = u * u;
        float u3 = u2 * u;
        return 0.5F * (2.0F * p1 + (-p0 + p2) * u + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * u2 + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * u3);
    }

    private record Pending(Cue cue, float alpha, float age, int id) {
    }
}
