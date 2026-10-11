package de.lardtom.propwash.guide.layout;

import de.lardtom.propwash.guide.scene.Cue;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CalloutLayout {
    public static final float GAP = 26.0F;
    public static final float OWN_GAP = 10.0F;
    public static final float ANCHOR_CLEARANCE = 4.0F;
    public static final float SPACING = 2.0F;
    public static final float GLIDE_MIN = 8.0F;
    public static final float GLIDE_MAX = 24.0F;
    public static final float GLIDE_PER_PIXEL = 0.08F;
    public static final float COOLDOWN = 12.0F;
    public static final float DRIFT = 48.0F;
    public static final float DRIFT_SHARE = 0.16F;
    public static final float OWN_SOFT = 0.3F;
    public static final float ACTOR_SOFT = 0.6F;
    public static final float MAX_DT = 10.0F;
    public static final float LOOKAHEAD = 16.0F;
    public static final float SOON = 8.0F;
    public static final float VELOCITY_TICKS = 3.0F;
    public static final float SWEEP_SHARE = 0.4F;
    public static final float MIN_SPEED = 1.5F;
    public static final float HOP_OUT = 5.0F;
    public static final float HOP_IN = 7.0F;
    private static final int PATH_SAMPLES = 12;
    private static final float[] SHIFTS = {0.0F, -1.0F, 1.0F};
    private static final float COST_SIDE = 60.0F;
    private static final float COST_VARIANT = 25.0F;
    private static final float COST_SHIFT = 40.0F;
    private static final float COST_CLAMP = 6.0F;
    private static final float COST_LABEL = 100000.0F;
    private static final float COST_HINT = 100000.0F;
    private static final float COST_ANCHOR = 50000.0F;
    private static final float COST_OWN = 4000.0F;
    private static final float COST_OTHER = 250.0F;
    private static final float COST_OTHER_ACTOR = 180.0F;
    private static final float COST_DISTANCE = 0.6F;
    private static final float COST_NARROW = 30.0F;
    private static final float COST_LEADER_CROSS = 150.0F;
    private static final float COST_TRAVEL = 0.25F;
    private static final float COST_SWITCH = 90.0F;
    private static final float COST_RETURN = 120.0F;
    private static final float RETURN_TICKS = 80.0F;
    private static final float COST_PATH = 3000.0F;
    private static final float COST_OTHER_ANCHOR = 20000.0F;
    private static final float COST_AMBIGUOUS = 500.0F;
    private static final float COST_MISLEADING = 5000.0F;
    private static final float MISLEADING = 40.0F;
    private static final float COST_TRANSIT = 2000.0F;
    private static final float SCAN = 6.0F;
    private static final float AMBIGUITY = 12.0F;
    private static final float NEAR = 14.0F;
    private static final float COST_NEAR = 15.0F;
    private static final float COST_STRETCH = 3.0F;
    private static final float KEEP_MARGIN = 20.0F;
    private static final float HARD = 10000.0F;
    private static final float SAME_SPOT = 1.0F;
    private static final float SIZE_TOLERANCE = 0.5F;

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private float clock;
    private float scale = 1.0F;
    private final List<Point> points = new ArrayList<>();
    private final List<LabelRect> transit = new ArrayList<>();
    private final Set<String> leaving = new HashSet<>();
    private @Nullable LabelRect lastView;
    private @Nullable LabelRect lastArea;
    private long frames;
    private long placements;
    private long moves;
    private long resets;
    private float fastest;

    public enum Kind {
        ACTOR,
        BLOCK,
        HINT
    }

    public record Size(float width, float height) {
    }

    public record Label(String id, float anchorX, float anchorY, Cue.Side side, List<Size> sizes, @Nullable String ownId,
                        @Nullable LabelRect own, boolean leaving) {
        public Label(String id, float anchorX, float anchorY, Cue.Side side, List<Size> sizes, @Nullable String ownId, @Nullable LabelRect own) {
            this(id, anchorX, anchorY, side, sizes, ownId, own, false);
        }

        public Label {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(side, "side");
            sizes = List.copyOf(sizes);
            if (sizes.isEmpty()) {
                throw new IllegalArgumentException("Hinweis ohne Größe: " + id);
            }
        }
    }

    public record Obstacle(String id, LabelRect rect, Kind kind) {
    }

    public record Placement(String id, LabelRect box, LabelRect target, int size, Cue.Side side, float anchorX, float anchorY,
                            boolean gliding, LabelRect from, float alpha) {
    }

    public record Stats(long frames, long placements, long moves, long resets, float fastest) {
    }

    private record Choice(float x, float y, int size, Cue.Side side, float cost, float leader) {
    }

    private record Point(String id, float x, float y, float aheadX, float aheadY) {
    }

    private record Sweep(float x0, float y0, float x1, float y1) {
        private boolean hits(LabelRect box, float clearance) {
            return box.grown(clearance).crosses(this.x0, this.y0, this.x1, this.y1);
        }
    }

    private static final class Entry {
        private final String id;
        private int size;
        private float width;
        private float height;
        private Cue.Side side;
        private float targetX;
        private float targetY;
        private float fromX;
        private float fromY;
        private float glideStart;
        private float glideLength;
        private float leader;
        private float checked = Float.NEGATIVE_INFINITY;
        private boolean stuck;
        private Cue.@Nullable Side left;
        private float switched = Float.NEGATIVE_INFINITY;
        private float lastX;
        private float lastY;
        private float anchorX;
        private float anchorY;
        private float velocityX;
        private float velocityY;
        private boolean hop;

        private Entry(String id, Choice choice, float width, float height) {
            this.id = id;
            this.size = choice.size();
            this.width = width;
            this.height = height;
            this.side = choice.side();
            this.targetX = choice.x();
            this.targetY = choice.y();
            this.fromX = choice.x();
            this.fromY = choice.y();
            this.leader = choice.leader();
            this.lastX = choice.x();
            this.lastY = choice.y();
        }

        private void follow(float x, float y, float step) {
            if (step > 0.0F) {
                float blend = Math.min(1.0F, step / VELOCITY_TICKS);
                this.velocityX += ((x - this.anchorX) / step - this.velocityX) * blend;
                this.velocityY += ((y - this.anchorY) / step - this.velocityY) * blend;
            }
            this.anchorX = x;
            this.anchorY = y;
        }

        private LabelRect target() {
            return LabelRect.of(this.targetX, this.targetY, this.width, this.height);
        }

        private float progress(float clock) {
            if (!(this.glideLength > 0.0F)) {
                return 1.0F;
            }
            float t = (clock - this.glideStart) / this.glideLength;
            return this.hop ? Math.clamp(t, 0.0F, 1.0F) : smooth(t);
        }

        private boolean faded(float clock) {
            return this.hop && clock - this.glideStart < HOP_OUT && this.progress(clock) < 1.0F;
        }

        private float alpha(float clock) {
            if (!this.hop || this.progress(clock) >= 1.0F) {
                return 1.0F;
            }
            float t = clock - this.glideStart;
            return t < HOP_OUT ? Math.clamp(1.0F - t / HOP_OUT, 0.0F, 1.0F) : Math.clamp((t - HOP_OUT) / HOP_IN, 0.0F, 1.0F);
        }

        private float shownX(float clock) {
            if (this.hop) {
                return this.faded(clock) ? this.fromX : this.targetX;
            }
            float p = this.progress(clock);
            return p >= 1.0F ? this.targetX : this.fromX + (this.targetX - this.fromX) * p;
        }

        private float shownY(float clock) {
            if (this.hop) {
                return this.faded(clock) ? this.fromY : this.targetY;
            }
            float p = this.progress(clock);
            return p >= 1.0F ? this.targetY : this.fromY + (this.targetY - this.fromY) * p;
        }

        private void startHop(float clock, float fromX, float fromY) {
            float visible = this.alpha(clock);
            this.fromX = fromX;
            this.fromY = fromY;
            this.hop = true;
            this.glideStart = clock - (1.0F - visible) * HOP_OUT;
            this.glideLength = HOP_OUT + HOP_IN;
        }

        private LabelRect shown(float clock) {
            return LabelRect.of(this.shownX(clock), this.shownY(clock), this.width, this.height);
        }
    }

    public void reset() {
        if (!this.entries.isEmpty()) {
            this.resets++;
        }
        this.entries.clear();
    }

    public Stats stats() {
        return new Stats(this.frames, this.placements, this.moves, this.resets, this.fastest);
    }

    public float clock() {
        return this.clock;
    }

    public static float smooth(float t) {
        if (!(t > 0.0F)) {
            return 0.0F;
        }
        if (t >= 1.0F) {
            return 1.0F;
        }
        return t * t * (3.0F - 2.0F * t);
    }

    public static float glideLength(float distance) {
        return Math.clamp(GLIDE_MIN + distance * GLIDE_PER_PIXEL, GLIDE_MIN, GLIDE_MAX);
    }

    public List<Placement> update(LabelRect view, LabelRect area, float dt, boolean reset, float scale, List<Label> labels,
                                  List<Obstacle> obstacles) {
        float step = Float.isFinite(dt) && dt > 0.0F ? Math.min(dt, MAX_DT) : 0.0F;
        this.scale = Float.isFinite(scale) ? Math.max(1.0F, scale) : 1.0F;
        this.clock += step;
        this.frames++;
        if (reset || !view.equals(this.lastView) || !area.equals(this.lastArea)) {
            this.reset();
        }
        this.lastView = view;
        this.lastArea = area;
        Map<String, Label> byId = new LinkedHashMap<>();
        for (Label label : labels) {
            byId.putIfAbsent(label.id(), label);
        }
        this.entries.keySet().retainAll(byId.keySet());
        this.entries.values().removeIf(entry -> !sameSize(entry, byId.get(entry.id)));
        List<Label> ordered = new ArrayList<>(byId.size());
        for (String id : this.entries.keySet()) {
            ordered.add(byId.get(id));
        }
        for (Label label : byId.values()) {
            if (!this.entries.containsKey(label.id())) {
                ordered.add(label);
            }
        }
        float drift = Math.max(DRIFT * this.scale, DRIFT_SHARE * Math.min(view.width(), view.height()));
        this.points.clear();
        this.transit.clear();
        this.leaving.clear();
        for (Label label : ordered) {
            if (label.leaving()) {
                this.leaving.add(label.id());
                continue;
            }
            if (view.contains(label.anchorX(), label.anchorY(), 0.0F)) {
                Entry known = this.entries.get(label.id());
                float vx = known != null ? known.velocityX * SOON : 0.0F;
                float vy = known != null ? known.velocityY * SOON : 0.0F;
                this.points.add(new Point(label.id(), label.anchorX(), label.anchorY(), label.anchorX() + vx, label.anchorY() + vy));
            }
        }
        List<LabelRect> higher = new ArrayList<>();
        List<Placement> result = new ArrayList<>(ordered.size());
        for (Label label : ordered) {
            float ax = Math.clamp(label.anchorX(), view.x0(), view.x1());
            float ay = Math.clamp(label.anchorY(), view.y0(), view.y1());
            boolean anchorShown = ax == label.anchorX() && ay == label.anchorY();
            List<LabelRect> blocked = this.blocked(label.id(), higher);
            Entry entry = this.entries.get(label.id());
            if (entry == null) {
                Choice choice = this.place(label, ax, ay, area, blocked, obstacles);
                Size size = label.sizes().get(choice.size());
                entry = new Entry(label.id(), choice, size.width(), size.height());
                entry.follow(label.anchorX(), label.anchorY(), 0.0F);
                this.entries.put(label.id(), entry);
                this.placements++;
            } else {
                entry.follow(label.anchorX(), label.anchorY(), step);
                this.keep(entry, label, ax, ay, anchorShown, view, area, higher, blocked, obstacles, drift);
                if (!entry.hop && entry.progress(this.clock) < 1.0F && anchorShown
                        && entry.shown(this.clock).contains(ax, ay, ANCHOR_CLEARANCE * this.scale) && !entry.target().contains(ax, ay, 0.0F)) {
                    entry.startHop(this.clock, entry.shownX(this.clock), entry.shownY(this.clock));
                }
                float x = entry.shownX(this.clock);
                float y = entry.shownY(this.clock);
                if (step > 0.0F && !entry.hop) {
                    float dx = x - entry.lastX;
                    float dy = y - entry.lastY;
                    this.fastest = Math.max(this.fastest, (float) Math.sqrt(dx * dx + dy * dy) / step);
                }
            }
            LabelRect target = entry.target();
            LabelRect shown = entry.shown(this.clock);
            entry.lastX = shown.x0();
            entry.lastY = shown.y0();
            if (!label.leaving()) {
                higher.add(target);
            }
            if (!sameSpot(target, shown)) {
                this.transit.add(shown);
            }
            boolean moving = entry.progress(this.clock) < 1.0F;
            result.add(new Placement(label.id(), shown, target, entry.size, entry.side, label.anchorX(), label.anchorY(), moving,
                    moving ? LabelRect.of(entry.fromX, entry.fromY, entry.width, entry.height) : target, entry.alpha(this.clock)));
        }
        return result;
    }

    private static boolean sameSize(Entry entry, @Nullable Label label) {
        if (label == null || entry.size >= label.sizes().size()) {
            return false;
        }
        Size size = label.sizes().get(entry.size);
        return Math.abs(size.width() - entry.width) <= SIZE_TOLERANCE && Math.abs(size.height() - entry.height) <= SIZE_TOLERANCE;
    }

    private static boolean sameSpot(LabelRect a, LabelRect b) {
        return Math.abs(a.x0() - b.x0()) < SAME_SPOT && Math.abs(a.y0() - b.y0()) < SAME_SPOT;
    }

    private List<LabelRect> blocked(String self, List<LabelRect> higher) {
        List<LabelRect> blocked = new ArrayList<>(higher);
        boolean later = false;
        for (Entry other : this.entries.values()) {
            if (later && !this.leaving.contains(other.id)) {
                blocked.add(other.target());
            } else if (other.id.equals(self)) {
                later = true;
            }
        }
        return blocked;
    }

    private Choice place(Label label, float ax, float ay, LabelRect area, List<LabelRect> blocked, List<Obstacle> obstacles) {
        Choice best = null;
        for (int size = 0; size < label.sizes().size(); size++) {
            Choice choice = this.best(label, size, ax, ay, area, blocked, obstacles, null, null);
            if (best == null || choice.cost() + COST_NARROW < best.cost()) {
                best = choice;
            }
        }
        return best;
    }

    private void keep(Entry entry, Label label, float ax, float ay, boolean anchorShown, LabelRect view, LabelRect area, List<LabelRect> higher,
                      List<LabelRect> blocked, List<Obstacle> obstacles, float drift) {
        LabelRect target = entry.target();
        Sweep sweep = this.sweep(entry, ax, ay, LOOKAHEAD, view);
        Sweep soon = this.sweep(entry, ax, ay, SOON, view);
        boolean hard = this.hard(target, ax, ay, anchorShown, area, higher, obstacles);
        boolean due = this.clock - entry.checked >= COOLDOWN;
        if (label.leaving() && !hard) {
            return;
        }
        if (hard) {
            if (entry.stuck && !due) {
                return;
            }
        } else if ((soon != null && soon.hits(target, -ANCHOR_CLEARANCE * this.scale)) || this.coversOther(label.id(), target)
                || this.ambiguous(label.id(), target, ax, ay)) {
            if (this.clock - entry.checked < COOLDOWN / 3.0F) {
                return;
            }
        } else if (!due || !this.soft(entry, target, label, ax, ay, obstacles, drift)) {
            return;
        }
        entry.checked = this.clock;
        Choice best = this.best(label, entry.size, ax, ay, area, blocked, obstacles, entry, sweep);
        if (hard && best.cost() >= HARD) {
            for (int size = 0; size < label.sizes().size(); size++) {
                if (size != entry.size) {
                    Choice other = this.best(label, size, ax, ay, area, blocked, obstacles, entry, sweep);
                    float reshape = other.cost() + COST_NARROW * Math.abs(size - entry.size);
                    if (reshape < best.cost()) {
                        best = new Choice(other.x(), other.y(), other.size(), other.side(), reshape, other.leader());
                    }
                }
            }
        }
        entry.stuck = best.cost() >= HARD;
        float current = this.cost(label, ax, ay, target, rank(label.side(), entry.side), 0, 0, 0.0F, blocked, obstacles, sweep)
                + Math.max(0.0F, target.distance(ax, ay) - entry.leader - drift) * COST_STRETCH;
        boolean better = hard ? best.cost() < current : best.cost() < HARD && best.cost() + KEEP_MARGIN < current;
        if (!better || (best.size() == entry.size && sameSpot(target, LabelRect.of(best.x(), best.y(), entry.width, entry.height)))) {
            return;
        }
        Size size = label.sizes().get(best.size());
        float fromX = entry.shownX(this.clock);
        float fromY = entry.shownY(this.clock);
        boolean hop = entry.alpha(this.clock) < 1.0F || opposite(entry.side, best.side())
                || this.crossesOwn(label, ax, ay, fromX, fromY, best.x(), best.y(), size.width(), size.height());
        entry.size = best.size();
        entry.width = size.width();
        entry.height = size.height();
        float dx = best.x() - fromX;
        float dy = best.y() - fromY;
        entry.targetX = best.x();
        entry.targetY = best.y();
        if (best.side() != entry.side) {
            entry.left = entry.side;
            entry.switched = this.clock;
        }
        entry.side = best.side();
        entry.leader = best.leader();
        if (hop) {
            entry.startHop(this.clock, fromX, fromY);
        } else {
            entry.hop = false;
            entry.fromX = fromX;
            entry.fromY = fromY;
            entry.glideStart = this.clock;
            entry.glideLength = glideLength((float) Math.sqrt(dx * dx + dy * dy) / this.scale);
        }
        this.moves++;
        for (Entry other : this.entries.values()) {
            other.stuck = false;
        }
    }

    private float switchCost(Entry entry, Cue.Side side) {
        if (side == entry.side) {
            return 0.0F;
        }
        float cost = COST_SWITCH;
        if (side == entry.left && this.clock - entry.switched < RETURN_TICKS) {
            cost += COST_RETURN;
        }
        return cost;
    }

    static boolean opposite(Cue.Side a, Cue.Side b) {
        return switch (a) {
            case LEFT -> b == Cue.Side.RIGHT;
            case RIGHT -> b == Cue.Side.LEFT;
            case ABOVE -> b == Cue.Side.BELOW;
            case BELOW -> b == Cue.Side.ABOVE;
        };
    }

    private boolean crossesOwn(Label label, float ax, float ay, float fromX, float fromY, float toX, float toY, float width, float height) {
        LabelRect own = label.own();
        float start = own != null ? share(own, LabelRect.of(fromX, fromY, width, height)) : 0.0F;
        float end = own != null ? share(own, LabelRect.of(toX, toY, width, height)) : 0.0F;
        for (int i = 1; i < PATH_SAMPLES; i++) {
            float t = (float) i / PATH_SAMPLES;
            LabelRect box = LabelRect.of(fromX + (toX - fromX) * t, fromY + (toY - fromY) * t, width, height);
            if (box.contains(ax, ay, ANCHOR_CLEARANCE * this.scale)) {
                return true;
            }
            if (own != null && share(own, box) > Math.max(OWN_SOFT, Math.max(start, end) + 0.1F)) {
                return true;
            }
        }
        return false;
    }

    private static float share(LabelRect own, LabelRect box) {
        return own.overlap(box) / Math.max(1.0F, box.area());
    }

    private boolean ambiguous(String self, LabelRect box, float ax, float ay) {
        float own = box.distance(ax, ay);
        for (Point point : this.points) {
            if (!point.id().equals(self) && box.distance(point.x(), point.y()) + AMBIGUITY * this.scale < own) {
                return true;
            }
        }
        return false;
    }

    private boolean coversOtherSoon(String self, LabelRect box) {
        for (Point point : this.points) {
            if (!point.id().equals(self) && box.contains(point.aheadX(), point.aheadY(), ANCHOR_CLEARANCE * this.scale)) {
                return true;
            }
        }
        return false;
    }

    private boolean misleading(String self, LabelRect box, float ax, float ay) {
        float own = box.distance(ax, ay);
        for (Point point : this.points) {
            if (!point.id().equals(self) && box.distance(point.x(), point.y()) + MISLEADING * this.scale < own) {
                return true;
            }
        }
        return false;
    }

    private boolean coversOther(String self, LabelRect box) {
        for (Point point : this.points) {
            if (!point.id().equals(self) && box.contains(point.x(), point.y(), ANCHOR_CLEARANCE * this.scale)) {
                return true;
            }
        }
        return false;
    }

    private boolean hard(LabelRect target, float ax, float ay, boolean anchorShown, LabelRect area, List<LabelRect> higher,
                         List<Obstacle> obstacles) {
        boolean fits = target.width() <= area.width() && target.height() <= area.height();
        if (fits && !target.within(area, 0.5F)) {
            return true;
        }
        if (anchorShown && target.contains(ax, ay, ANCHOR_CLEARANCE * this.scale)) {
            return true;
        }
        for (LabelRect other : higher) {
            if (target.overlaps(other, 0.0F)) {
                return true;
            }
        }
        for (Obstacle obstacle : obstacles) {
            if (obstacle.kind() == Kind.HINT && target.overlaps(obstacle.rect(), 0.0F)) {
                return true;
            }
        }
        return false;
    }

    private boolean soft(Entry entry, LabelRect target, Label label, float ax, float ay, List<Obstacle> obstacles, float drift) {
        if (target.distance(ax, ay) > entry.leader + drift) {
            return true;
        }
        float boxArea = Math.max(1.0F, target.area());
        LabelRect padded = target.grown(2.0F * this.scale);
        for (Obstacle obstacle : obstacles) {
            if (obstacle.kind() == Kind.HINT) {
                continue;
            }
            float covered = obstacle.rect().overlap(padded);
            if (covered <= 0.0F) {
                continue;
            }
            if (obstacle.id().equals(label.ownId())) {
                if (covered / boxArea > OWN_SOFT) {
                    return true;
                }
            } else if (obstacle.kind() == Kind.ACTOR) {
                float actorArea = Math.max(1.0F, obstacle.rect().area());
                if (actorArea < boxArea * 3.0F && covered / actorArea > ACTOR_SOFT) {
                    return true;
                }
            }
        }
        return false;
    }

    private @Nullable Sweep sweep(Entry entry, float ax, float ay, float ticks, LabelRect view) {
        float dx = entry.velocityX * ticks;
        float dy = entry.velocityY * ticks;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < MIN_SPEED * this.scale * ticks) {
            return null;
        }
        float cap = SWEEP_SHARE * Math.min(view.width(), view.height());
        if (length > cap) {
            dx *= cap / length;
            dy *= cap / length;
        }
        return new Sweep(ax, ay, Math.clamp(ax + dx, view.x0(), view.x1()), Math.clamp(ay + dy, view.y0(), view.y1()));
    }

    private Choice best(Label label, int size, float ax, float ay, LabelRect area, List<LabelRect> blocked, List<Obstacle> obstacles,
                        @Nullable Entry entry, @Nullable Sweep sweep) {
        Size dims = label.sizes().get(size);
        Cue.Side[] sides = order(label.side());
        Choice best = null;
        float fromX = entry != null ? entry.shownX(this.clock) : 0.0F;
        float fromY = entry != null ? entry.shownY(this.clock) : 0.0F;
        for (int rank = 0; rank < sides.length; rank++) {
            Cue.Side side = sides[rank];
            for (int variant = 0; variant < (label.own() != null ? 2 : 1); variant++) {
                for (int shift = 0; shift < SHIFTS.length; shift++) {
                    float[] at = candidate(side, variant == 1 ? label.own() : null, ax, ay, dims.width(), dims.height(), SHIFTS[shift], area,
                            this.scale);
                    LabelRect box = LabelRect.of(at[0], at[1], dims.width(), dims.height());
                    float cost = this.cost(label, ax, ay, box, rank, variant, shift, at[2], blocked, obstacles, sweep);
                    if (entry != null) {
                        float dx = at[0] - fromX;
                        float dy = at[1] - fromY;
                        cost += (float) Math.sqrt(dx * dx + dy * dy) * COST_TRAVEL;
                        cost += this.switchCost(entry, side);
                    }
                    if (best == null || cost < best.cost()) {
                        best = new Choice(at[0], at[1], size, side, cost, box.distance(ax, ay));
                    }
                }
            }
        }
        if (best.cost() >= HARD) {
            Choice free = this.scan(label, size, ax, ay, area, blocked, obstacles, entry, sweep, fromX, fromY);
            if (free != null && free.cost() < best.cost()) {
                best = free;
            }
        }
        return best;
    }

    private @Nullable Choice scan(Label label, int size, float ax, float ay, LabelRect area, List<LabelRect> blocked, List<Obstacle> obstacles,
                                  @Nullable Entry entry, @Nullable Sweep sweep, float fromX, float fromY) {
        Size dims = label.sizes().get(size);
        float step = SCAN * this.scale;
        float maxX = Math.max(area.x0(), area.x1() - dims.width());
        float maxY = Math.max(area.y0(), area.y1() - dims.height());
        Choice best = null;
        for (float y = area.y0(); y <= maxY + 0.5F; y += step) {
            for (float x = area.x0(); x <= maxX + 0.5F; x += step) {
                float bx = Math.min(Math.round(x), maxX);
                float by = Math.min(Math.round(y), maxY);
                LabelRect box = LabelRect.of(bx, by, dims.width(), dims.height());
                Cue.Side side = sideOf(box, ax, ay);
                float cost = this.cost(label, ax, ay, box, rank(label.side(), side), 0, SHIFTS.length, 0.0F, blocked, obstacles, sweep);
                if (entry != null) {
                    float dx = bx - fromX;
                    float dy = by - fromY;
                    cost += (float) Math.sqrt(dx * dx + dy * dy) * COST_TRAVEL;
                    cost += this.switchCost(entry, side);
                }
                if (best == null || cost < best.cost()) {
                    best = new Choice(bx, by, size, side, cost, box.distance(ax, ay));
                }
            }
        }
        return best;
    }

    static Cue.Side sideOf(LabelRect box, float ax, float ay) {
        float dx = box.centerX() - ax;
        float dy = box.centerY() - ay;
        if (Math.abs(dx) * box.height() >= Math.abs(dy) * box.width()) {
            return dx < 0.0F ? Cue.Side.LEFT : Cue.Side.RIGHT;
        }
        return dy < 0.0F ? Cue.Side.ABOVE : Cue.Side.BELOW;
    }

    private float cost(Label label, float ax, float ay, LabelRect box, int rank, int variant, int shift, float clamp, List<LabelRect> blocked,
                       List<Obstacle> obstacles, @Nullable Sweep sweep) {
        float boxArea = Math.max(1.0F, box.area());
        float cost = rank * COST_SIDE + variant * COST_VARIANT + shift * COST_SHIFT + clamp * COST_CLAMP;
        if (sweep != null && sweep.hits(box, ANCHOR_CLEARANCE * this.scale)) {
            cost += COST_PATH;
        }
        for (LabelRect moving : this.transit) {
            if (box.overlaps(moving, SPACING * this.scale)) {
                cost += COST_TRANSIT;
            }
        }
        float px = box.nearestX(ax);
        float py = box.nearestY(ay);
        for (LabelRect other : blocked) {
            if (box.overlaps(other, SPACING * this.scale)) {
                cost += COST_LABEL;
            }
            if (other.crosses(ax, ay, px, py)) {
                cost += COST_LEADER_CROSS;
            }
        }
        if (box.contains(ax, ay, ANCHOR_CLEARANCE * this.scale)) {
            cost += COST_ANCHOR;
        } else {
            cost += Math.max(0.0F, NEAR * this.scale - box.distance(ax, ay)) / this.scale * COST_NEAR;
        }
        if (this.coversOther(label.id(), box) || this.coversOtherSoon(label.id(), box)) {
            cost += COST_OTHER_ANCHOR;
        }
        if (this.ambiguous(label.id(), box, ax, ay)) {
            cost += COST_AMBIGUOUS;
            if (this.misleading(label.id(), box, ax, ay)) {
                cost += COST_MISLEADING;
            }
        }
        LabelRect padded = box.grown(2.0F * this.scale);
        for (Obstacle obstacle : obstacles) {
            if (obstacle.kind() == Kind.HINT) {
                if (box.overlaps(obstacle.rect(), SPACING * this.scale)) {
                    cost += COST_HINT;
                }
                continue;
            }
            float covered = obstacle.rect().overlap(padded);
            if (covered <= 0.0F) {
                continue;
            }
            boolean mine = obstacle.id().equals(label.ownId());
            cost += covered / boxArea * (mine ? COST_OWN : COST_OTHER);
            if (!mine && obstacle.kind() == Kind.ACTOR) {
                cost += Math.min(1.0F, covered / Math.max(1.0F, obstacle.rect().area())) * COST_OTHER_ACTOR;
            }
        }
        float dx = px - ax;
        float dy = py - ay;
        return cost + (float) Math.sqrt(dx * dx + dy * dy) * COST_DISTANCE;
    }

    static float[] candidate(Cue.Side side, @Nullable LabelRect own, float ax, float ay, float width, float height, float shift, LabelRect area,
                             float scale) {
        float gap = GAP * scale;
        float ownGap = OWN_GAP * scale;
        float lift = 8.0F * scale;
        float space = 6.0F * scale;
        float bx;
        float by;
        switch (side) {
            case LEFT -> {
                bx = (own != null ? own.x0() - ownGap : ax - gap) - width;
                by = ay - height / 2.0F - lift + shift * (height + space);
            }
            case ABOVE -> {
                bx = ax - width / 2.0F + shift * (width / 2.0F + space);
                by = (own != null ? own.y0() - ownGap : ay - gap) - height;
            }
            case BELOW -> {
                bx = ax - width / 2.0F + shift * (width / 2.0F + space);
                by = own != null ? own.y1() + ownGap : ay + gap;
            }
            default -> {
                bx = own != null ? own.x1() + ownGap : ax + gap;
                by = ay - height / 2.0F - lift + shift * (height + space);
            }
        }
        float wantedX = Math.round(bx);
        float wantedY = Math.round(by);
        float x0 = Math.clamp(wantedX, area.x0(), Math.max(area.x0(), area.x1() - width));
        float y0 = Math.clamp(wantedY, area.y0(), Math.max(area.y0(), area.y1() - height));
        return new float[]{x0, y0, Math.abs(x0 - wantedX) + Math.abs(y0 - wantedY)};
    }

    static int rank(Cue.Side preferred, Cue.Side side) {
        Cue.Side[] sides = order(preferred);
        for (int i = 0; i < sides.length; i++) {
            if (sides[i] == side) {
                return i;
            }
        }
        return sides.length;
    }

    static Cue.Side[] order(Cue.Side preferred) {
        return switch (preferred) {
            case LEFT -> new Cue.Side[]{Cue.Side.LEFT, Cue.Side.RIGHT, Cue.Side.ABOVE, Cue.Side.BELOW};
            case RIGHT -> new Cue.Side[]{Cue.Side.RIGHT, Cue.Side.LEFT, Cue.Side.ABOVE, Cue.Side.BELOW};
            case ABOVE -> new Cue.Side[]{Cue.Side.ABOVE, Cue.Side.BELOW, Cue.Side.LEFT, Cue.Side.RIGHT};
            case BELOW -> new Cue.Side[]{Cue.Side.BELOW, Cue.Side.ABOVE, Cue.Side.LEFT, Cue.Side.RIGHT};
        };
    }
}
