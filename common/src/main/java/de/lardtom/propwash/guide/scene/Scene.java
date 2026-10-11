package de.lardtom.propwash.guide.scene;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class Scene {
    public static final String KEY_PREFIX = "propwash.guide.scene.";
    public static final String BUILTIN_NAMESPACE = "propwash";
    public static final float SAME_ANCHOR = 0.5F;
    private static final Pattern ID = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_]+");

    private final String id;
    private final String icon;
    private final ItemMatch items;
    private final float viewRadius;
    private final CameraSpec camera;
    private final List<SceneStep> steps;
    private final float[] stepStarts;
    private final int[] cueOffsets;
    private final float[][] cueEnds;

    public Scene(String id, String icon, ItemMatch items, float viewRadius, CameraSpec camera, List<SceneStep> steps) {
        this.id = canonicalId(Objects.requireNonNull(id, "id"));
        this.icon = Objects.requireNonNull(icon, "icon");
        this.items = Objects.requireNonNull(items, "items");
        this.camera = Objects.requireNonNull(camera, "camera");
        this.steps = List.copyOf(steps);
        if (this.steps.isEmpty()) {
            throw new IllegalArgumentException("Szene " + id + " ohne Schritte");
        }
        if (!(viewRadius > 0.0F) || !Float.isFinite(viewRadius)) {
            throw new IllegalArgumentException("Sichtradius muss > 0 sein");
        }
        this.viewRadius = viewRadius;
        Set<String> names = new LinkedHashSet<>();
        for (SceneStep step : this.steps) {
            if (!names.add(step.name())) {
                throw new IllegalArgumentException("Doppelter Schritt " + step.name() + " in " + id);
            }
        }
        this.stepStarts = new float[this.steps.size() + 1];
        this.cueOffsets = new int[this.steps.size() + 1];
        for (int i = 0; i < this.steps.size(); i++) {
            this.stepStarts[i + 1] = this.stepStarts[i] + this.steps.get(i).duration();
            this.cueOffsets[i + 1] = this.cueOffsets[i] + this.steps.get(i).cues().size();
        }
        this.cueEnds = cueEnds(this.steps, this.stepStarts);
    }

    private static float[][] cueEnds(List<SceneStep> steps, float[] starts) {
        float[][] ends = new float[steps.size()][];
        List<int[]> callouts = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            SceneStep step = steps.get(i);
            ends[i] = new float[step.cues().size()];
            for (int j = 0; j < step.cues().size(); j++) {
                TimedCue timed = step.cues().get(j);
                ends[i][j] = starts[i] + timed.end(step.duration());
                if (timed.cue() instanceof Cue.Callout) {
                    callouts.add(new int[]{i, j});
                }
            }
        }
        for (int[] older : callouts) {
            Cue.Callout callout = (Cue.Callout) steps.get(older[0]).cues().get(older[1]).cue();
            float start = starts[older[0]] + steps.get(older[0]).cues().get(older[1]).start();
            for (int[] newer : callouts) {
                Cue.Callout other = (Cue.Callout) steps.get(newer[0]).cues().get(newer[1]).cue();
                float otherStart = starts[newer[0]] + steps.get(newer[0]).cues().get(newer[1]).start();
                if (otherStart > start && otherStart < ends[older[0]][older[1]] && supersedes(other, callout)) {
                    ends[older[0]][older[1]] = otherStart;
                }
            }
        }
        return ends;
    }

    public static boolean supersedes(Cue.Callout newer, Cue.Callout older) {
        if (newer.side() != older.side() || !Objects.equals(newer.anchor().actor(), older.anchor().actor())) {
            return false;
        }
        ScenePos a = newer.anchor().offset();
        ScenePos b = older.anchor().offset();
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        float dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz < SAME_ANCHOR * SAME_ANCHOR;
    }

    public static String canonicalId(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Ungültige Szenen-Id: " + id);
        }
        String builtin = BUILTIN_NAMESPACE + ":";
        return id.startsWith(builtin) ? id.substring(builtin.length()) : id;
    }

    public static String namespaceOf(String id) {
        String canonical = canonicalId(id);
        int colon = canonical.indexOf(':');
        return colon < 0 ? BUILTIN_NAMESPACE : canonical.substring(0, colon);
    }

    public static String keyPrefix(String id) {
        String canonical = canonicalId(id);
        int colon = canonical.indexOf(':');
        if (colon < 0) {
            return KEY_PREFIX + canonical + ".";
        }
        return canonical.substring(0, colon) + ".guide.scene." + canonical.substring(colon + 1) + ".";
    }

    public String id() {
        return this.id;
    }

    public String namespace() {
        return namespaceOf(this.id);
    }

    public boolean builtin() {
        return this.id.indexOf(':') < 0;
    }

    public String keyPrefix() {
        return keyPrefix(this.id);
    }

    public String icon() {
        return this.icon;
    }

    public ItemMatch items() {
        return this.items;
    }

    public float viewRadius() {
        return this.viewRadius;
    }

    public CameraSpec camera() {
        return this.camera;
    }

    public List<SceneStep> steps() {
        return this.steps;
    }

    public float duration() {
        return this.stepStarts[this.steps.size()];
    }

    public int stepCount() {
        return this.steps.size();
    }

    public float stepStart(int step) {
        return this.stepStarts[Math.clamp(step, 0, this.steps.size())];
    }

    public float stepEnd(int step) {
        return this.stepStart(Math.clamp(step, 0, this.steps.size() - 1) + 1);
    }

    public float cueEnd(int step, int cue) {
        return this.cueEnds[step][cue];
    }

    public int cueId(int step, int cue) {
        return this.cueOffsets[step] + cue;
    }

    public int stepAt(float time) {
        int last = this.steps.size() - 1;
        for (int i = last; i > 0; i--) {
            if (time >= this.stepStarts[i]) {
                return i;
            }
        }
        return 0;
    }

    public String titleKey() {
        return this.keyPrefix() + "title";
    }

    public String descriptionKey() {
        return this.keyPrefix() + "description";
    }

    public String stepTitleKey(int step) {
        return this.keyPrefix() + "step." + this.steps.get(step).name() + ".title";
    }

    public String stepTextKey(int step) {
        return this.keyPrefix() + "step." + this.steps.get(step).name() + ".text";
    }

    public List<String> translationKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(this.titleKey());
        keys.add(this.descriptionKey());
        for (int i = 0; i < this.steps.size(); i++) {
            keys.add(this.stepTitleKey(i));
            keys.add(this.stepTextKey(i));
            for (TimedCue timed : this.steps.get(i).cues()) {
                switch (timed.cue()) {
                    case Cue.Callout callout -> keys.add(callout.textKey());
                    case Cue.Keys hint -> keys.add(hint.textKey());
                    default -> {
                    }
                }
            }
        }
        return keys.stream().distinct().toList();
    }
}
