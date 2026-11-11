package de.lardtom.propwash.share.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.share.MissingAddons;
import de.lardtom.propwash.share.NamespaceReport;
import de.lardtom.propwash.share.Pw3Carry;
import de.lardtom.propwash.share.Pw3Codec;
import de.lardtom.propwash.share.Pw3Params;
import de.lardtom.propwash.share.ShareCommands;
import de.lardtom.propwash.share.ShareContent;
import de.lardtom.propwash.share.ShareConvert;
import de.lardtom.propwash.share.ShareDecoded;
import de.lardtom.propwash.share.ShareFormat;
import de.lardtom.propwash.share.ShareServer;
import de.lardtom.propwash.share.ShareService;
import de.lardtom.propwash.share.ShareStatus;
import de.lardtom.propwash.share.ShareTables;
import de.lardtom.propwash.share.Substitutes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ShareImportState {
    private static final Substitutes.Result NONE = new Substitutes.Result(Substitutes.Method.NONE, Optional.empty(), List.of(), Optional.empty());

    private String text = "";
    private ShareDecoded decoded = ShareDecoded.failed(ShareStatus.EMPTY);
    private List<MissingAddons.Line> missing = List.of();
    private List<Addon> addons = List.of();
    private Substitutes.Result substitutes = NONE;
    private Optional<BuildCheck> check = Optional.empty();
    private Optional<BuildStats> stats = Optional.empty();
    private Optional<BuildCheck> substituteCheck = Optional.empty();
    private Optional<BuildStats> substituteStats = Optional.empty();
    private Optional<String> pw3 = Optional.empty();
    private boolean forced;
    private int revision;

    public record Addon(String key, Optional<NamespaceReport> report, Optional<MissingAddons.Kind> kind, String name, Optional<String> required,
                        Optional<String> installed, List<MissingAddons.Link> links) {
        public boolean problem() {
            return this.kind.isPresent();
        }

        public boolean mismatch() {
            return this.kind.filter(k -> k == MissingAddons.Kind.MISMATCH).isPresent();
        }
    }

    public boolean update(String input) {
        String value = Objects.requireNonNullElse(input, "");
        if (value.equals(this.text) && this.revision > 0) {
            return false;
        }
        this.text = value;
        this.revision++;
        this.forced = false;
        this.recompute();
        return true;
    }

    public void refresh() {
        this.revision++;
        this.recompute();
    }

    private void recompute() {
        ShareTables tables = ShareTables.current();
        this.decoded = ShareService.decode(this.text);
        this.missing = List.of();
        this.addons = List.of();
        this.substitutes = NONE;
        this.check = Optional.empty();
        this.stats = Optional.empty();
        this.substituteCheck = Optional.empty();
        this.substituteStats = Optional.empty();
        this.pw3 = Optional.empty();
        if (!this.decoded.ok()) {
            return;
        }
        try {
            this.missing = MissingAddons.of(this.decoded, tables, language());
        } catch (RuntimeException e) {
            Constants.LOG.debug("Sharecode-Vorschau: Add-on-Liste fehlgeschlagen", e);
        }
        this.addons = addons(this.decoded, this.missing, tables);
        if (ShareServer.needsSubstitutes(this.decoded)) {
            this.substitutes = ShareServer.substitutes(this.decoded, tables);
            this.substitutes.build().ifPresent(build -> {
                this.substituteCheck = check(build);
                this.substituteStats = analyze(this.decoded.require().withBuild(build));
            });
        }
        if (this.decoded.unknownParts().isEmpty() && this.decoded.wrongKind().isEmpty()) {
            this.check = check(this.decoded.require().build());
            this.stats = analyze(this.decoded.require());
        }
        this.pw3 = pw3Text(this.text, this.decoded, tables);
    }

    private static Optional<BuildCheck> check(BuildSpec build) {
        try {
            return Optional.of(PropwashApi.builds().check(build));
        } catch (RuntimeException e) {
            Constants.LOG.debug("Sharecode-Vorschau: Prüfung fehlgeschlagen", e);
            return Optional.empty();
        }
    }

    private static Optional<BuildStats> analyze(ShareContent content) {
        try {
            return ShareServer.stats(content);
        } catch (RuntimeException e) {
            Constants.LOG.debug("Sharecode-Vorschau: Analyse fehlgeschlagen", e);
            return Optional.empty();
        }
    }

    static Optional<String> pw3Text(String text, ShareDecoded decoded, ShareTables tables) {
        if (!decoded.ok()) {
            return Optional.empty();
        }
        if (decoded.legacy()) {
            ShareConvert.Conversion conversion = ShareConvert.toPw3(text);
            return conversion.ok() ? conversion.code() : Optional.empty();
        }
        if (decoded.renamed().isEmpty() && decoded.unknownParts().stream().noneMatch(Pw3Codec::unknownMarker)) {
            return Optional.empty();
        }
        try {
            Pw3Carry carry = decoded.carry();
            String code = Pw3Codec.encode(decoded.require(), tables, Pw3Codec.Options.DEFAULT, carry);
            String compact = ShareFormat.compact(text);
            return code.equalsIgnoreCase(compact) || code.equals(Pw3Params.PREFIX + compact) ? Optional.empty() : Optional.of(code);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static List<Addon> addons(ShareDecoded decoded, List<MissingAddons.Line> missing, ShareTables tables) {
        Map<String, MissingAddons.Line> byKey = new LinkedHashMap<>();
        for (MissingAddons.Line line : missing) {
            byKey.put(line.key(), line);
        }
        String language = language();
        List<Addon> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (NamespaceReport report : decoded.namespaces()) {
            if (!report.used()) {
                continue;
            }
            String key = report.key();
            seen.add(key);
            MissingAddons.Line line = byKey.get(key);
            Optional<String> installed = report.installedVersion().or(() -> report.namespace().flatMap(tables::version));
            if (line != null) {
                out.add(new Addon(key, Optional.of(report), Optional.of(line.kind()), line.name(), line.requiredVersion().or(report::minVersion),
                        line.installedVersion().or(() -> installed), line.links()));
            } else {
                out.add(new Addon(key, Optional.of(report), Optional.empty(), ShareCommands.addonName(report, tables, language), report.minVersion(),
                        installed, ShareCommands.links(report, tables)));
            }
        }
        for (MissingAddons.Line line : missing) {
            if (!seen.contains(line.key())) {
                out.add(new Addon(line.key(), Optional.empty(), Optional.of(line.kind()), line.name(), line.requiredVersion(),
                        line.installedVersion(), line.links()));
            }
        }
        return List.copyOf(out);
    }

    public static String language() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? "en_us" : minecraft.getLanguageManager().getSelected();
    }

    public String text() {
        return this.text;
    }

    public ShareDecoded decoded() {
        return this.decoded;
    }

    public Optional<ShareContent> content() {
        return this.decoded.content();
    }

    public List<MissingAddons.Line> missing() {
        return this.missing;
    }

    public List<Addon> addons() {
        return this.addons;
    }

    public boolean addonProblem() {
        for (Addon addon : this.addons) {
            if (addon.problem()) {
                return true;
            }
        }
        return false;
    }

    public Substitutes.Result substitutes() {
        return this.substitutes;
    }

    public boolean needsSubstitutes() {
        return ShareServer.needsSubstitutes(this.decoded);
    }

    public boolean substitutesFound() {
        return this.substitutes.found() && this.substitutes.method() != Substitutes.Method.NOT_NEEDED;
    }

    public boolean usingSubstitutes() {
        return this.needsSubstitutes() && this.substitutesFound() && ShareSettings.substitutes();
    }

    public Optional<String> pw3() {
        return this.pw3;
    }

    public boolean locked() {
        return this.decoded.partsLocked() && !this.forced;
    }

    public boolean forced() {
        return this.forced;
    }

    public void force(boolean value) {
        this.forced = value;
    }

    public int revision() {
        return this.revision;
    }

    public Optional<BuildCheck> check() {
        return this.usingSubstitutes() ? this.substituteCheck : this.check;
    }

    public Optional<BuildStats> stats() {
        return this.usingSubstitutes() ? this.substituteStats : this.stats;
    }

    public Optional<BuildCheck> originalCheck() {
        return this.check;
    }

    public Optional<BuildCheck> substituteCheck() {
        return this.substituteCheck;
    }

    public boolean buildable() {
        return this.check().map(BuildCheck::buildable).orElse(false);
    }

    public Optional<ShareContent> effective() {
        if (!this.decoded.ok()) {
            return Optional.empty();
        }
        ShareContent content = this.decoded.require();
        if (this.usingSubstitutes()) {
            return this.substitutes.build().map(content::withBuild);
        }
        return Optional.of(content);
    }

    public Optional<ShareContent> substituted() {
        if (!this.decoded.ok() || !this.substitutesFound()) {
            return Optional.empty();
        }
        return this.substitutes.build().map(this.decoded.require()::withBuild);
    }

    public boolean onlyInactive() {
        return this.decoded.ok() && this.decoded.unknownParts().isEmpty() && this.decoded.wrongKind().isEmpty() && !this.decoded.inactiveParts().isEmpty();
    }

    public boolean partsReady() {
        if (!this.decoded.ok() || this.locked()) {
            return false;
        }
        if (this.usingSubstitutes()) {
            return this.substituteCheck.map(BuildCheck::buildable).orElse(false);
        }
        if (this.decoded.partsKnown()) {
            return this.buildable();
        }
        return this.onlyInactive();
    }

    public Optional<DroneData> preview() {
        Optional<ShareContent> content = this.effective();
        if (content.isEmpty()) {
            return Optional.empty();
        }
        for (Identifier id : content.get().build().allParts()) {
            if (this.decoded.unknownParts().contains(id) || this.decoded.wrongKind().contains(id)) {
                return Optional.empty();
            }
        }
        return Optional.of(content.get().toDroneData());
    }

    public Optional<MissingAddons.Part> unknownPart(Identifier id) {
        for (MissingAddons.Line line : this.missing) {
            for (MissingAddons.Part part : line.parts()) {
                if (part.id().equals(id)) {
                    return Optional.of(part);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<MissingAddons.Line> lineOf(Identifier id) {
        for (MissingAddons.Line line : this.missing) {
            for (MissingAddons.Part part : line.parts()) {
                if (part.id().equals(id)) {
                    return Optional.of(line);
                }
            }
        }
        return Optional.empty();
    }
}
