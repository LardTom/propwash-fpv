package de.lardtom.propwash.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.guide.layout.CalloutLayout;
import de.lardtom.propwash.guide.layout.LabelRect;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneFraming;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneProjection;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.sim.AirframeSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LessonLabelsTest {
    private static final float[][] VIEWPORTS = {{616.0F, 256.0F}, {456.0F, 166.0F}, {748.0F, 378.0F}, {1496.0F, 756.0F}};
    private static final float LEFT = 12.0F;
    private static final float TOP = 40.0F;
    private static final int PAD = 16;
    private static final int TOP_RESERVE = 22;
    private static final float FRAME = 1.0F;
    private static final float MAX_STEP = 30.0F;
    private static final int MAX_MOVES_PER_LABEL = 6;
    private static final float OWNER_MARGIN = 0.45F;
    private static final float FLIP_TICKS = 80.0F;
    private static final String BLOCK_PREFIX = "block:";

    record Box(String id, LabelRect rect, float[] min, float[] max, boolean block) {
        boolean contains(ScenePos p, float margin) {
            return p.x() >= this.min[0] - margin && p.x() <= this.max[0] + margin && p.y() >= this.min[1] - margin && p.y() <= this.max[1] + margin
                    && p.z() >= this.min[2] - margin && p.z() <= this.max[2] + margin;
        }

        float volume() {
            return (this.max[0] - this.min[0]) * (this.max[1] - this.min[1]) * (this.max[2] - this.min[2]);
        }
    }

    static List<Box> boxes(SceneState state, SceneProjection projection) {
        List<Box> boxes = new ArrayList<>();
        float[] point = new float[3];
        for (ActorState actor : state.visibleActors()) {
            float presence = Math.max(0.0F, actor.presence());
            if (presence <= 0.05F) {
                continue;
            }
            ScenePos pos = actor.position();
            float yaw = 0.0F;
            float hx;
            float hz;
            float y0;
            float y1;
            switch (actor.spec()) {
                case ActorSpec.Drone drone -> {
                    float scale = drone.scale() * presence;
                    float radius = Math.clamp((float) AirframeSpec.of(drone.build()).hitboxWidth * 0.5F, 0.05F, 0.25F) * scale;
                    float half = 0.04F * scale;
                    float tilt = (float) Math.toRadians(Math.min(90.0F, Math.max(Math.abs(actor.pitch()), Math.abs(actor.roll()))));
                    float vertical = Math.max(half * 1.2F, radius * (float) Math.sin(tilt));
                    hx = radius;
                    hz = radius;
                    y0 = pos.y() + half - vertical;
                    y1 = pos.y() + half + vertical;
                }
                case ActorSpec.Gate gate -> {
                    GateSize size = GateSize.byKey(gate.size());
                    yaw = -actor.yaw();
                    hx = size.width() * 0.5F * presence;
                    hz = size.depth() * 0.5F * presence;
                    y0 = pos.y();
                    y1 = pos.y() + size.height() * presence;
                }
                case ActorSpec.Pilot pilot -> {
                    hx = 0.3F * presence;
                    hz = 0.3F * presence;
                    y0 = pos.y();
                    y1 = pos.y() + (actor.crouching() ? 1.5F : 1.8F) * presence;
                }
                case ActorSpec.Item item -> {
                    float half = 0.28F * item.scale() * presence;
                    hx = half;
                    hz = half;
                    y0 = pos.y() + 0.12F * item.scale() * presence - half;
                    y1 = pos.y() + 0.12F * item.scale() * presence + half;
                }
                case ActorSpec.Remote remote -> {
                    float scale = remote.scale() * presence;
                    yaw = -actor.yaw();
                    hx = 0.42F * scale;
                    hz = 0.22F * scale;
                    y0 = pos.y();
                    y1 = pos.y() + 0.92F * scale;
                }
                case ActorSpec.Block block -> {
                    yaw = -actor.yaw();
                    hx = 0.5F * presence;
                    hz = 0.5F * presence;
                    y0 = pos.y();
                    y1 = pos.y() + presence;
                }
            }
            float cos = (float) Math.cos(Math.toRadians(yaw));
            float sin = (float) Math.sin(Math.toRadians(yaw));
            float[] screen = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            float[] min = {Float.MAX_VALUE, y0, Float.MAX_VALUE};
            float[] max = {-Float.MAX_VALUE, y1, -Float.MAX_VALUE};
            for (int i = 0; i < 8; i++) {
                float lx = (i & 1) == 0 ? -hx : hx;
                float lz = (i & 2) == 0 ? -hz : hz;
                float ly = (i & 4) == 0 ? y0 : y1;
                float wx = pos.x() + lx * cos + lz * sin;
                float wz = pos.z() - lx * sin + lz * cos;
                projection.project(wx, ly, wz, point);
                screen[0] = Math.min(screen[0], point[0]);
                screen[1] = Math.min(screen[1], point[1]);
                screen[2] = Math.max(screen[2], point[0]);
                screen[3] = Math.max(screen[3], point[1]);
                min[0] = Math.min(min[0], wx);
                min[2] = Math.min(min[2], wz);
                max[0] = Math.max(max[0], wx);
                max[2] = Math.max(max[2], wz);
            }
            boxes.add(new Box(actor.id(), new LabelRect(screen[0], screen[1], screen[2], screen[3]), min, max, false));
        }
        for (SceneState.BlockView block : state.blocks()) {
            if (block.at().y() < 1 || block.scale() < 0.5F) {
                continue;
            }
            float bx = block.at().x();
            float by = block.at().y() + block.lift();
            float bz = block.at().z();
            float[] screen = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int i = 0; i < 8; i++) {
                projection.project(bx + (i & 1), by + ((i >> 2) & 1), bz + ((i >> 1) & 1), point);
                screen[0] = Math.min(screen[0], point[0]);
                screen[1] = Math.min(screen[1], point[1]);
                screen[2] = Math.max(screen[2], point[0]);
                screen[3] = Math.max(screen[3], point[1]);
            }
            boxes.add(new Box(BLOCK_PREFIX + block.at().x() + "," + block.at().y() + "," + block.at().z(),
                    new LabelRect(screen[0], screen[1], screen[2], screen[3]), new float[]{bx, by, bz}, new float[]{bx + 1.0F, by + 1.0F, bz + 1.0F}, true));
        }
        return boxes;
    }

    static Box owner(ScenePos anchor, List<Box> boxes) {
        Box best = null;
        for (Box box : boxes) {
            if (box.contains(anchor, OWNER_MARGIN) && (best == null || box.volume() < best.volume())) {
                best = box;
            }
        }
        return best;
    }

    private static JsonObject english() throws IOException {
        for (Path candidate : List.of(Path.of("common/src/lang/guide/en_us.json"), Path.of("src/lang/guide/en_us.json"))) {
            if (Files.isRegularFile(candidate)) {
                try (Reader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        }
        throw new IOException("Sprachquellen der Flugschule nicht gefunden");
    }

    static int charWidth(char c) {
        return switch (c) {
            case 'i', '!', '.', ',', ':', ';', '|', '\'' -> 2;
            case 'l', '`' -> 3;
            case 't', 'I', '[', ']', ' ', '(', ')', '"', '*' -> 4;
            case 'f', 'k', '<', '>', '{', '}' -> 5;
            default -> 6;
        };
    }

    static int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += charWidth(text.charAt(i));
        }
        return w;
    }

    static List<String> wrap(String text, int max) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && width(next) > max) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    static List<CalloutLayout.Size> sizes(String text, float areaWidth, int k) {
        int scaled = (int) (areaWidth / k);
        int maxWidth = Math.min(Math.max(80, Math.min(170, scaled / 3)), Math.max(40, scaled - 2 * PAD - 12));
        List<CalloutLayout.Size> sizes = new ArrayList<>();
        for (int wrap : new int[]{maxWidth, Math.max(80, Math.round(maxWidth * 0.7F))}) {
            List<String> lines = wrap(text, wrap);
            int textWidth = 0;
            for (String line : lines) {
                textWidth = Math.max(textWidth, width(line));
            }
            CalloutLayout.Size size = new CalloutLayout.Size((textWidth + 12) * k, (lines.size() * 10 + 8) * k);
            if (!sizes.contains(size)) {
                sizes.add(size);
            }
            if (wrap <= 80) {
                break;
            }
        }
        return sizes;
    }

    static List<CalloutLayout.Obstacle> hints(SceneState state, SceneProjection projection, LabelRect view, JsonObject lang, int k) {
        List<CalloutLayout.Obstacle> hints = new ArrayList<>();
        float bottom = view.y1() - 8.0F;
        float height = 18.0F * k;
        float[] point = new float[3];
        for (SceneState.KeyHintView hint : state.keyHints()) {
            if (hint.alpha() <= 0.02F) {
                continue;
            }
            String text = lang.has(hint.textKey()) ? lang.get(hint.textKey()).getAsString() : hint.textKey();
            float shown = (5 + hint.keys().size() * 24 + 19 + width(text)) * k;
            float x0;
            float y0;
            if (hint.anchor() != null) {
                projection.project(hint.anchor().x(), hint.anchor().y(), hint.anchor().z(), point);
                x0 = Math.round(point[0] - shown / 2.0F);
                y0 = Math.round(point[1] - height - 4.0F);
            } else {
                x0 = view.x0() + (view.width() - shown) / 2.0F;
                y0 = bottom - height;
                bottom = y0 - 4.0F;
            }
            x0 = Math.clamp(x0, view.x0() + 4.0F, Math.max(view.x0() + 4.0F, view.x1() - 4.0F - shown));
            y0 = Math.clamp(y0 + Math.round((1.0F - hint.alpha()) * 6.0F), view.y0() + 4.0F, Math.max(view.y0() + 4.0F, view.y1() - 4.0F - height));
            hints.add(new CalloutLayout.Obstacle("hint", new LabelRect(x0, y0, x0 + shown, y0 + height), CalloutLayout.Kind.HINT));
        }
        return hints;
    }

    private record Report(int frames, int labels, long moves, int overlaps, int covers, int foreign, int ambiguous, int outside, int hintHits,
                          int jumps, float fastest, int busiest, String worst, int transit, int flips, int endAmbiguous) {
    }

    private static boolean sameSpot(LabelRect a, LabelRect b) {
        return Math.abs(a.x0() - b.x0()) < 1.0F && Math.abs(a.y0() - b.y0()) < 1.0F;
    }

    private static Report play(Scene scene, SceneFraming framing, List<SceneState> states, float w, float h, JsonObject lang, boolean turning) {
        return play(scene, framing, states, w, h, lang, turning, false);
    }

    private static Report play(Scene scene, SceneFraming framing, List<SceneState> states, float w, float h, JsonObject lang, boolean turning,
                               boolean actors) {
        int k = w > 1000.0F ? 2 : 1;
        LabelRect view = new LabelRect(LEFT, TOP, LEFT + w, TOP + h);
        LabelRect area = new LabelRect(view.x0() + PAD, view.y0() + TOP_RESERVE, view.x1() - PAD, view.y1() - PAD);
        CalloutLayout layout = new CalloutLayout();
        Map<String, float[]> last = new HashMap<>();
        Map<String, Integer> movesPerLabel = new HashMap<>();
        Map<String, LabelRect> lastTarget = new HashMap<>();
        int frames = 0;
        int overlaps = 0;
        int covers = 0;
        int foreign = 0;
        int ambiguous = 0;
        int outside = 0;
        int hintHits = 0;
        int jumps = 0;
        int transit = 0;
        int flips = 0;
        int endAmbiguous = 0;
        Map<String, Cue.Side> sides = new HashMap<>();
        Map<String, Cue.Side> leftSide = new HashMap<>();
        Map<String, Float> leftAt = new HashMap<>();
        String worst = "";
        float fastest = 0.0F;
        float[] point = new float[3];
        for (int frame = 0; frame < states.size(); frame++) {
            float t = frame * FRAME;
            SceneState state = states.get(frame);
            float yaw = turning ? 35.0F * (float) Math.sin(t * 0.013F) : 0.0F;
            float pitch = turning ? 8.0F * (float) Math.sin(t * 0.009F) : 0.0F;
            float zoom = turning ? 1.0F + 0.25F * (float) Math.sin(t * 0.011F) : 1.0F;
            SceneProjection projection = framing.projection(state.camera().withOffsets(yaw, pitch, 1.0F), state.time(),
                    SceneProjection.basePixelsPerBlock(w, h, scene.viewRadius()), view.x0(), view.y0(), w, h, zoom);
            List<CalloutLayout.Label> labels = new ArrayList<>();
            List<Box> boxes = actors ? boxes(state, projection) : List.of();
            for (SceneState.CalloutView callout : state.callouts()) {
                if (callout.alpha() <= 0.02F) {
                    continue;
                }
                projection.project(callout.anchor().x(), callout.anchor().y(), callout.anchor().z(), point);
                String text = lang.has(callout.textKey()) ? lang.get(callout.textKey()).getAsString() : callout.textKey();
                Box own = actors ? owner(callout.anchor(), boxes) : null;
                labels.add(new CalloutLayout.Label(callout.layoutId(), point[0], point[1], callout.side(), sizes(text, w, k), own != null ? own.id() : null,
                        own != null ? own.rect() : null, callout.age() > SceneEvaluator.FADE_TICKS && callout.alpha() < 1.0F));
            }
            List<CalloutLayout.Obstacle> hints = hints(state, projection, view, lang, k);
            List<CalloutLayout.Obstacle> obstacles = new ArrayList<>();
            for (Box box : boxes) {
                obstacles.add(new CalloutLayout.Obstacle(box.id(), box.rect(), box.block() ? CalloutLayout.Kind.BLOCK : CalloutLayout.Kind.ACTOR));
            }
            obstacles.addAll(hints);
            List<CalloutLayout.Placement> placed = layout.update(view, area, FRAME, false, k, labels, obstacles);
            boolean lastFrame = frame == states.size() - 1;
            frames++;
            String where = scene.id() + " t=" + t + " " + (int) w + "x" + (int) h;
            for (int a = 0; a < placed.size(); a++) {
                CalloutLayout.Placement p = placed.get(a);
                LabelRect target = p.target();
                boolean fits = target.width() <= area.width() && target.height() <= area.height();
                if (fits && !target.within(area, 0.5F)) {
                    outside++;
                }
                boolean anchorShown = view.contains(p.anchorX(), p.anchorY(), 0.0F);
                if (anchorShown && target.contains(p.anchorX(), p.anchorY(), 0.0F)) {
                    covers++;
                    worst = where + " verdeckt Anker " + p.id();
                }
                if (anchorShown && p.gliding() && p.alpha() >= 0.999F && p.box().contains(p.anchorX(), p.anchorY(), 0.0F)
                        && !sameSpot(p.box(), p.from()) && !sameSpot(p.box(), target)) {
                    transit++;
                    worst = where + " gleitet über seinen Anker " + p.id();
                }
                Cue.Side before = sides.put(p.id(), p.side());
                if (before != null && before != p.side()) {
                    if (p.side() == leftSide.get(p.id()) && t - leftAt.get(p.id()) < FLIP_TICKS) {
                        flips++;
                        worst = where + " hin und her " + p.id();
                    }
                    leftSide.put(p.id(), before);
                    leftAt.put(p.id(), t);
                }
                if (lastFrame && !p.gliding()) {
                    for (CalloutLayout.Placement other : placed) {
                        if (other != p && view.contains(other.anchorX(), other.anchorY(), 0.0F)
                                && target.distance(other.anchorX(), other.anchorY()) + 40.0F * k < target.distance(p.anchorX(), p.anchorY())) {
                            endAmbiguous++;
                            worst = where + " am Ende näher an " + other.id() + ": " + p.id();
                        }
                    }
                }
                for (int b = a + 1; b < placed.size(); b++) {
                    if (target.overlaps(placed.get(b).target(), 0.0F)) {
                        overlaps++;
                        worst = where + " " + p.id() + "/" + placed.get(b).id();
                    }
                }
                for (CalloutLayout.Placement other : placed) {
                    if (other != p && view.contains(other.anchorX(), other.anchorY(), 0.0F) && target.contains(other.anchorX(), other.anchorY(), 0.0F)) {
                        foreign++;
                        worst = where + " verdeckt fremden Anker " + other.id() + ": " + p.id();
                    }
                    if (other != p && view.contains(other.anchorX(), other.anchorY(), 0.0F) && !p.gliding()
                            && target.distance(other.anchorX(), other.anchorY()) + 40.0F * k < target.distance(p.anchorX(), p.anchorY())) {
                        ambiguous++;
                    }
                }
                for (CalloutLayout.Obstacle hint : hints) {
                    if (target.overlaps(hint.rect(), 0.0F)) {
                        hintHits++;
                        worst = where + " Tastenhinweis über " + p.id();
                    }
                }
                float[] previousBox = last.get(p.id());
                if (previousBox != null && p.alpha() >= 0.999F && previousBox[2] >= 0.999F) {
                    float dx = p.box().x0() - previousBox[0];
                    float dy = p.box().y0() - previousBox[1];
                    float step = (float) Math.sqrt(dx * dx + dy * dy);
                    fastest = Math.max(fastest, step / FRAME / k);
                    if (step > MAX_STEP * k) {
                        jumps++;
                        worst = where + " Sprung " + p.id() + " " + step;
                    }
                }
                LabelRect previous = lastTarget.get(p.id());
                if (previous != null && !previous.equals(target)) {
                    movesPerLabel.merge(p.id(), 1, Integer::sum);
                }
                lastTarget.put(p.id(), target);
                last.put(p.id(), new float[]{p.box().x0(), p.box().y0(), p.alpha()});
            }
            last.keySet().retainAll(placed.stream().map(CalloutLayout.Placement::id).toList());
            lastTarget.keySet().retainAll(placed.stream().map(CalloutLayout.Placement::id).toList());
        }
        int busiest = movesPerLabel.values().stream().max(Integer::compare).orElse(0);
        return new Report(frames, (int) layout.stats().placements(), layout.stats().moves(), overlaps, covers, foreign, ambiguous, outside,
                hintHits, jumps, fastest, busiest, worst, transit, flips, endAmbiguous);
    }

    @Test
    void labelsStayCalmInEveryLessonWhileTheViewTurns() throws IOException {
        JsonObject lang = english();
        List<String> problems = new ArrayList<>();
        Map<String, String> summary = new LinkedHashMap<>();
        for (Scene scene : CalloutRulesTest.allScenes()) {
            SceneFraming framing = SceneFraming.of(scene);
            List<SceneState> states = new ArrayList<>();
            for (float t = 0.0F; t <= scene.duration(); t += FRAME) {
                states.add(SceneEvaluator.evaluate(scene, t));
            }
            for (float[] viewport : VIEWPORTS) {
                for (boolean actors : new boolean[]{false, true}) {
                    for (boolean turning : new boolean[]{false, true}) {
                        Report report = play(scene, framing, states, viewport[0], viewport[1], lang, turning, actors);
                        String key = scene.id() + " " + (int) viewport[0] + "x" + (int) viewport[1] + (actors ? " mit Objekten" : "") + (turning ? " gedreht" : "");
                        summary.put(key, String.format(Locale.ROOT, "%d Hinweise, %d Umzüge, max %d je Hinweis, schnellstens %.1f px/Tick, fremde Anker %d, mehrdeutig %d, über eigenen Anker %d, hin und her %d, am Ende mehrdeutig %d",
                                report.labels(), report.moves(), report.busiest(), report.fastest(), report.foreign(), report.ambiguous(), report.transit(),
                                report.flips(), report.endAmbiguous()));
                        boolean calm = !turning && (report.flips() > 0 || report.endAmbiguous() > 0);
                        if (report.overlaps() > 0 || report.covers() > 0 || report.foreign() > 0 || report.ambiguous() > 0 || report.outside() > 0 || report.hintHits() > 0
                                || report.jumps() > 0 || report.busiest() > MAX_MOVES_PER_LABEL || report.transit() > 0 || calm) {
                            problems.add(key + ": Überlappung " + report.overlaps() + ", Anker verdeckt " + report.covers() + ", fremder Anker verdeckt " + report.foreign() + ", näher an fremdem Anker "
                                    + report.ambiguous() + ", außerhalb "
                                    + report.outside() + ", Tastenhinweis " + report.hintHits() + ", Sprünge " + report.jumps() + ", Umzüge je Hinweis "
                                    + report.busiest() + ", über eigenen Anker " + report.transit() + ", hin und her " + report.flips() + ", am Ende mehrdeutig "
                                    + report.endAmbiguous() + " (" + report.worst() + ")");
                        }
                    }
                }
            }
        }
        String text = String.join("\n", summary.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).toList());
        String report = System.getenv("PROPWASH_LABEL_REPORT");
        if (report != null && !report.isBlank()) {
            Files.writeString(Path.of(report), text + "\n\nProbleme:\n" + String.join("\n", problems));
        }
        assertTrue(problems.isEmpty(), problems.size() + " Probleme:\n" + String.join("\n", problems));
    }
}
