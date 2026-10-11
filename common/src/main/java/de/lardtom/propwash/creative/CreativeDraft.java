package de.lardtom.propwash.creative;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartInfo;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class CreativeDraft {
    private final DroneBuild installed;
    private final CreativeSpec original;
    private CreativeSpec spec;
    private int revision;
    private @Nullable DroneBuild cachedBuild;
    private int cachedRevision = -1;
    private CreativeLimits.@Nullable Note lastNote;

    public CreativeDraft(DroneBuild installed) {
        this.installed = installed;
        this.original = installed.creative();
        this.spec = installed.creative();
    }

    public DroneBuild installed() {
        return this.installed;
    }

    public CreativeSpec spec() {
        return this.spec;
    }

    public CreativeSpec original() {
        return this.original;
    }

    public int revision() {
        return this.revision;
    }

    public boolean dirty() {
        return !this.spec.equals(this.original);
    }

    public DroneBuild build() {
        if (this.cachedBuild == null || this.cachedRevision != this.revision) {
            this.cachedBuild = this.installed.withCreative(this.spec);
            this.cachedRevision = this.revision;
        }
        return this.cachedBuild;
    }

    public double value(CreativeParam param) {
        return CreativeFlight.value(this.build(), param);
    }

    public double base(CreativeParam param) {
        return CreativeFlight.baseValue(this.build().withCreative(this.spec.without(param)), param);
    }

    public boolean overridden(CreativeParam param) {
        return this.spec.has(param);
    }

    public Optional<CreativeLimits.Note> lastNote() {
        return Optional.ofNullable(this.lastNote);
    }

    public Optional<CreativeLimits.Note> set(CreativeParam param, double requested) {
        if (!Double.isFinite(requested)) {
            return Optional.empty();
        }
        double applied = param.clamp(requested);
        CreativeLimits.@Nullable Note note = Math.abs(applied - requested) > CreativeLimits.tolerance(param)
                ? new CreativeLimits.Note(CreativeLimits.Type.CLAMPED, param.key(), param, requested, applied) : null;
        double base = this.base(param);
        CreativeSpec next = Math.abs(applied - base) <= CreativeLimits.tolerance(param) ? this.spec.without(param) : this.spec.with(param, applied);
        this.replace(next);
        this.lastNote = note;
        return Optional.ofNullable(note);
    }

    public Optional<CreativeLimits.Note> step(CreativeParam param, int steps) {
        return this.set(param, this.value(param) + steps * param.step());
    }

    public void reset(CreativeParam param) {
        this.replace(this.spec.without(param));
    }

    public void reset(List<CreativeParam> params) {
        this.replace(this.spec.without(params));
    }

    public Optional<String> basePart(PartKind kind) {
        return this.spec.base(kind).filter(key -> CreativeLimits.resolves(kind, key) && !key.equals(installedPart(kind).key()));
    }

    public void basePart(PartKind kind, @Nullable String key) {
        if (key == null || key.equals(installedPart(kind).key())) {
            this.replace(this.spec.withoutBase(kind));
        } else if (CreativeLimits.resolves(kind, key)) {
            this.replace(this.spec.withBase(kind, key));
        }
    }

    public PartInfo installedPart(PartKind kind) {
        return switch (kind) {
            case FRAME -> this.installed.frame();
            case STACK -> this.installed.stack();
            case MOTOR -> this.installed.motor();
            case PROP -> this.installed.prop();
            case BATTERY -> this.installed.battery();
            case VIDEO -> this.installed.video();
            case ACCESSORY -> throw new IllegalArgumentException("Zubehör hat keine Basis");
        };
    }

    public List<CreativeLimits.Note> load(CreativeSpec template) {
        CreativeLimits.Result result = CreativeLimits.sanitize(template);
        CreativeSpec spec = result.spec();
        for (PartKind kind : CreativeSpec.BASE_KINDS) {
            if (spec.base(kind).filter(key -> key.equals(installedPart(kind).key())).isPresent()) {
                spec = spec.withoutBase(kind);
            }
        }
        this.replace(spec);
        return result.notes();
    }

    public void resetAll() {
        this.replace(CreativeSpec.EMPTY);
    }

    public void revert() {
        this.replace(this.original);
    }

    public List<CreativeCheck.Warning> warnings() {
        return CreativeCheck.check(this.build());
    }

    private void replace(CreativeSpec next) {
        if (!next.equals(this.spec)) {
            this.spec = next;
            this.revision++;
        }
        this.lastNote = null;
    }
}
