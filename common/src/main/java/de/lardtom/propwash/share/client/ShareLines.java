package de.lardtom.propwash.share.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.join.SafeText;
import de.lardtom.propwash.share.MissingAddons;
import de.lardtom.propwash.share.ShareCommands;
import de.lardtom.propwash.share.ShareContent;
import de.lardtom.propwash.share.ShareDecoded;
import de.lardtom.propwash.share.ShareFormat;
import de.lardtom.propwash.share.ShareServer;
import de.lardtom.propwash.share.ShareStatus;
import de.lardtom.propwash.share.Substitutes;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ShareLines {
    public static final int INDENT = 8;
    public static final int ICON = 8;
    public static final int ICON_GAP = 3;
    public static final int ACTION_PAD = 3;
    public static final int SCROLLBAR_SPACE = Metrics.SCROLLBAR_WIDTH + 4;
    public static final Identifier ICON_OK = Constants.id("share/addon_ok");
    public static final Identifier ICON_MISSING = Constants.id("share/addon_missing");
    public static final Identifier ICON_OLDER = Constants.id("share/addon_older");
    public static final Identifier ICON_MISMATCH = Constants.id("share/addon_mismatch");
    public static final Identifier ICON_INACTIVE = Constants.id("share/addon_inactive");
    public static final Identifier ICON_UNKNOWN = Constants.id("share/addon_unknown");
    public static final Identifier ICON_LINK = Constants.id("share/link");
    public static final List<Identifier> ICONS = List.of(ICON_OK, ICON_MISSING, ICON_OLDER, ICON_MISMATCH, ICON_INACTIVE, ICON_UNKNOWN, ICON_LINK);
    public static final String TAG_ADDON = "addon:";
    public static final String TAG_LINK = "link:";
    public static final String TAG_UNTRUSTED = "untrusted:";
    public static final String TAG_COPY_PW3 = "action:copy_pw3";
    public static final String TAG_FORCE = "action:force";
    public static final String TAG_SUBSTITUTES = "action:substitutes";
    public static final String TAG_CREATE = "action:create_substitutes";
    public static final String TAG_MISMATCH = "banner:mismatch";
    public static final String TAG_LEGACY = "legacy";
    public static final String TAG_SWAP = "swap";
    public static final String TAG_NO_SUBSTITUTE = "no_substitute";
    public static final String TAG_SHOPPING = "shopping:";
    public static final String TAG_PWD1 = "pwd1";
    public static final String TAG_CREATIVE = "creative";
    public static final String TAG_CREATIVE_SURVIVAL = "creative:survival";
    public static final String TAG_CREATIVE_WORKBENCH = "creative:workbench";

    private ShareLines() {
    }

    public interface Actions {
        void copyPw3();

        void forceParts();

        void toggleSubstitutes();

        void createWithSubstitutes();

        void openLink(String url);
    }

    public record Line(Component text, int color, boolean section, int indent, @Nullable Identifier icon, @Nullable Runnable action, String tag) {
        public static Line of(Component text, int color) {
            return new Line(text, color, false, 0, null, null, "");
        }

        public static Line indented(Component text, int color) {
            return new Line(text, color, false, INDENT, null, null, "");
        }

        public static Line section(Component text) {
            return new Line(text, Palette.ACCENT, true, 0, null, null, "");
        }

        public static Line action(Component text, Runnable action, String tag) {
            return new Line(text, Palette.ACCENT, false, 0, null, action, tag);
        }

        public Line tagged(String value) {
            return new Line(this.text, this.color, this.section, this.indent, this.icon, this.action, value);
        }

        public Line withIcon(Identifier value) {
            return new Line(this.text, this.color, this.section, this.indent, value, this.action, this.tag);
        }

        public Line indentBy(int value) {
            return new Line(this.text, this.color, this.section, value, this.icon, this.action, this.tag);
        }

        public boolean clickable() {
            return this.action != null;
        }

        int textIndent() {
            return this.indent + (this.icon != null ? ICON + ICON_GAP : 0) + (this.action != null ? ACTION_PAD : 0);
        }

        int rightPad() {
            return this.action != null ? ACTION_PAD : 0;
        }
    }

    public record Placed(Line line, int x0, int y0, int x1, int y1, boolean visible) {
        public boolean contains(double x, double y) {
            return this.visible && x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
        }
    }

    public static final class Drag {
        private int x;
        private int top;
        private int height;
        private int total;
        private boolean active;
        private double grab;

        public void track(int trackX, int trackTop, int trackHeight, int contentHeight) {
            this.x = trackX;
            this.top = trackTop;
            this.height = trackHeight;
            this.total = contentHeight;
        }

        public void clear() {
            this.total = 0;
            this.active = false;
        }

        private int thumb() {
            return Math.max(12, (int) ((long) this.height * this.height / Math.max(1, this.total)));
        }

        private int thumbTop(int offset) {
            int max = this.total - this.height;
            return this.top + (int) Math.round((this.height - this.thumb()) * Math.clamp(max <= 0 ? 0.0 : (double) offset / max, 0.0, 1.0));
        }

        public boolean press(double mouseX, double mouseY, int offset) {
            if (this.total <= this.height || this.height <= 0 || mouseX < this.x - 1 || mouseX > this.x + Metrics.SCROLLBAR_WIDTH + 1
                    || mouseY < this.top || mouseY >= this.top + this.height) {
                return false;
            }
            int thumbTop = this.thumbTop(offset);
            this.grab = mouseY >= thumbTop && mouseY < thumbTop + this.thumb() ? mouseY - thumbTop : this.thumb() / 2.0;
            this.active = true;
            return true;
        }

        public boolean active() {
            return this.active;
        }

        public int offset(double mouseY) {
            int room = Math.max(1, this.height - this.thumb());
            double fraction = Math.clamp((mouseY - this.grab - this.top) / room, 0.0, 1.0);
            return (int) Math.round(fraction * (this.total - this.height));
        }

        public void release() {
            this.active = false;
        }
    }

    public static Locale locale() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? Locale.ROOT : ShareCommands.locale(minecraft.getLanguageManager().getSelected());
    }

    public static Line status(ShareDecoded decoded) {
        if (decoded.status() == ShareStatus.EMPTY) {
            return Line.of(Component.translatable("propwash.share.import.empty"), Palette.TEXT_FAINT);
        }
        if (!decoded.ok()) {
            return Line.of(ShareCommands.statusText(decoded), Palette.ERROR).tagged("status:" + decoded.status().key());
        }
        return Line.of(Component.translatable("propwash.share.import.readable", decoded.format(), ShareCommands.layerList(decoded.layers())),
                Palette.SUCCESS).tagged("status:ok");
    }

    public static List<Line> head(ShareImportState state, @Nullable Actions actions) {
        List<Line> lines = new ArrayList<>();
        ShareDecoded decoded = state.decoded();
        lines.add(status(decoded));
        if (!decoded.ok()) {
            return lines;
        }
        if (decoded.legacy()) {
            lines.add(Line.of(Component.translatable("propwash.share.legacy", decoded.format()), Palette.TEXT_MUTED).tagged(TAG_LEGACY));
        }
        if (ShareFormat.DRONE_EDITOR.equals(decoded.format())) {
            lines.add(Line.of(Component.translatable("propwash.share.pwd1.build"), Palette.TEXT_MUTED).tagged(TAG_PWD1));
        }
        if (actions != null && state.pw3().isPresent()) {
            lines.add(Line.action(Component.translatable("propwash.share.copy_pw3"), actions::copyPw3, TAG_COPY_PW3));
        }
        for (ShareImportState.Addon addon : state.addons()) {
            if (addon.mismatch()) {
                lines.add(Line.of(Component.translatable("propwash.share.addons.mismatch", literal(addon.name())), Palette.ERROR).tagged(TAG_MISMATCH));
            }
        }
        if (decoded.partsLocked()) {
            if (state.forced()) {
                lines.add(Line.of(Component.translatable("propwash.share.import_anyway.on"), Palette.WARNING));
            } else if (actions != null) {
                lines.add(Line.action(Component.translatable("propwash.share.import_anyway"), actions::forceParts, TAG_FORCE));
            }
        }
        return lines;
    }

    public static List<Line> addons(ShareImportState state, @Nullable Actions actions) {
        List<Line> lines = new ArrayList<>();
        if (!state.decoded().ok() || state.addons().isEmpty()) {
            return lines;
        }
        lines.add(Line.section(Component.translatable("propwash.share.addons.title")));
        for (ShareImportState.Addon addon : state.addons()) {
            lines.add(Line.of(addonText(addon), addonColor(addon)).withIcon(addonIcon(addon)).tagged(TAG_ADDON + addon.key()));
            if (!addon.problem()) {
                continue;
            }
            for (MissingAddons.Link link : addon.links()) {
                if (ShareCommands.clickable(link)) {
                    String url = SafeText.url(link.url());
                    if (!url.isEmpty() && actions != null) {
                        lines.add(Line.action(Component.translatable(ShareCommands.linkKey(link)), () -> actions.openLink(url), TAG_LINK + url)
                                .withIcon(ICON_LINK).indentBy(INDENT));
                    }
                } else if (!ShareCommands.clickable(link)) {
                    String shown = SafeText.clean(link.url().startsWith("https://") ? link.url().substring("https://".length()) : link.url(), 120);
                    lines.add(Line.indented(Component.translatable("propwash.share.link.untrusted", Component.literal(shown)), Palette.WARNING)
                            .tagged(TAG_UNTRUSTED + shown));
                }
            }
        }
        return lines;
    }

    private static Component literal(String text) {
        return Component.literal(SafeText.clean(text, 80));
    }

    public static Component addonText(ShareImportState.Addon addon) {
        Component name = literal(addon.name());
        String required = addon.required().orElse("?");
        if (addon.kind().isEmpty()) {
            return addon.installed().isPresent()
                    ? Component.translatable("propwash.share.addons.ok", name, required, addon.installed().get())
                    : Component.translatable("propwash.share.addons.ok_unversioned", name, required);
        }
        return switch (addon.kind().get()) {
            case MISSING -> Component.translatable("propwash.share.addons.missing", name, required);
            case OLDER -> Component.translatable("propwash.share.addons.older", name, required, addon.installed().orElse("?"));
            case MISMATCH -> Component.translatable("propwash.share.addons.mismatch_row", name);
            case INACTIVE -> Component.translatable("propwash.share.addons.inactive", name);
            case UNKNOWN_SHORT_ID -> Component.translatable("propwash.share.addons.unknown_short", addon.key().replaceAll("\\D", ""));
        };
    }

    public static int addonColor(ShareImportState.Addon addon) {
        if (addon.kind().isEmpty()) {
            return Palette.TEXT;
        }
        return switch (addon.kind().get()) {
            case MISMATCH, MISSING, UNKNOWN_SHORT_ID -> Palette.ERROR;
            case OLDER, INACTIVE -> Palette.WARNING;
        };
    }

    static Identifier addonIcon(ShareImportState.Addon addon) {
        if (addon.kind().isEmpty()) {
            return ICON_OK;
        }
        return switch (addon.kind().get()) {
            case MISSING -> ICON_MISSING;
            case OLDER -> ICON_OLDER;
            case MISMATCH -> ICON_MISMATCH;
            case INACTIVE -> ICON_INACTIVE;
            case UNKNOWN_SHORT_ID -> ICON_UNKNOWN;
        };
    }

    public static List<Line> substitutes(ShareImportState state, @Nullable Actions actions, boolean toggle, boolean create) {
        List<Line> lines = new ArrayList<>();
        if (!state.needsSubstitutes()) {
            return lines;
        }
        String language = ShareImportState.language();
        lines.add(Line.section(Component.translatable("propwash.share.substitutes.title")));
        Substitutes.Result result = state.substitutes();
        if (!state.substitutesFound()) {
            List<Component> names = new ArrayList<>();
            for (Identifier id : state.decoded().unknownParts()) {
                names.add(partName(id, state));
            }
            for (Identifier id : state.decoded().inactiveParts()) {
                names.add(partName(id, state));
            }
            lines.add(Line.indented(Component.translatable("propwash.share.substitutes.none", join(names)), Palette.ERROR).tagged(TAG_NO_SUBSTITUTE));
            return lines;
        }
        for (Substitutes.Swap swap : result.swaps()) {
            Component to = swap.to().<Component>map(id -> ShareCommands.partName(id, language))
                    .orElse(Component.translatable("propwash.share.substitutes.removed"));
            lines.add(Line.indented(Component.translatable("propwash.share.substitutes.swap", partName(swap.from(), state), to), Palette.TEXT_MUTED)
                    .tagged(TAG_SWAP));
        }
        if (actions != null && toggle) {
            boolean on = ShareSettings.substitutes();
            lines.add(Line.action(Component.translatable(on ? "propwash.share.substitutes.use.on" : "propwash.share.substitutes.use.off"),
                    actions::toggleSubstitutes, TAG_SUBSTITUTES));
        }
        if (actions != null && create) {
            lines.add(Line.action(Component.translatable("propwash.share.substitutes.create"), actions::createWithSubstitutes, TAG_CREATE));
        }
        return lines;
    }

    private static Component join(List<Component> names) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(names.get(i));
        }
        return out;
    }

    public static MutableComponent partName(Identifier id, ShareImportState state) {
        Optional<MissingAddons.Part> part = state.unknownPart(id);
        if (part.isPresent()) {
            MissingAddons.Part known = part.get();
            if (known.name().isPresent()) {
                return Component.literal(SafeText.clean(known.name().get(), 80));
            }
            if (known.number() > 0) {
                return Component.translatable("propwash.share.part.number", ShareCommands.kindName(known.kind()), known.number());
            }
        }
        return ShareCommands.partName(id, ShareImportState.language());
    }

    public static List<Line> details(ShareImportState state, @Nullable ShoppingList shopping, boolean creative) {
        List<Line> lines = new ArrayList<>();
        ShareDecoded decoded = state.decoded();
        if (!decoded.ok()) {
            return lines;
        }
        ShareContent content = decoded.require();
        content.name().ifPresent(name -> lines.add(Line.of(Component.translatable("propwash.share.import.name", Component.literal(name)), Palette.TEXT)));
        lines.add(Line.section(Component.translatable("propwash.share.section.parts")));
        for (ShareServer.Slot slot : ShareServer.slots(content.build())) {
            lines.add(Line.indented(partLine(slot, state), partColor(slot.id(), decoded)));
        }
        if (!decoded.unknownParts().isEmpty()) {
            lines.add(Line.of(Component.translatable("propwash.share.import.unknown", decoded.unknownParts().size()), Palette.ERROR));
        }
        if (!decoded.inactiveParts().isEmpty()) {
            lines.add(Line.of(Component.translatable("propwash.share.import.inactive", decoded.inactiveParts().size()), Palette.WARNING));
        }
        if (!decoded.wrongKind().isEmpty()) {
            lines.add(Line.of(Component.translatable("propwash.share.import.wrong_kind", ShareCommands.idList(decoded.wrongKind())), Palette.WARNING));
        }
        for (ShareDecoded.Rename rename : decoded.renamed()) {
            lines.add(Line.indented(Component.translatable("propwash.share.import.renamed", Component.literal(rename.from().toString()),
                    Component.literal(rename.to().toString())), Palette.TEXT_MUTED));
        }
        if (state.check().isPresent()) {
            BuildCheck check = state.check().get();
            for (String problem : check.problems()) {
                lines.add(Line.of(Component.translatable(BuildCheck.translationKey(problem)), Palette.ERROR));
            }
            for (String warning : check.warnings()) {
                lines.add(Line.of(Component.translatable(BuildCheck.translationKey(warning)), Palette.WARNING));
            }
        }
        state.stats().ifPresent(stats -> addStats(lines, stats));
        List<Line> layers = new ArrayList<>();
        content.paint().ifPresent(paint -> layers.add(Line.indented(Component.translatable("propwash.share.import.paint", paint.colors().size()),
                Palette.TEXT_MUTED)));
        content.tune().ifPresent(tune -> layers.add(Line.indented(Component.translatable("propwash.share.import.tune", tune.values().size()),
                Palette.TEXT_MUTED)));
        content.osd().ifPresent(osd -> layers.add(Line.indented(Component.translatable("propwash.share.import.osd", osd.version()), Palette.TEXT_MUTED)));
        content.creative().ifPresent(spec -> {
            layers.add(Line.indented(ShareCommands.creativeLine(spec), Palette.ACCENT).tagged(TAG_CREATIVE));
            if (!creative) {
                layers.add(Line.indented(Component.translatable("propwash.share.import.creative.survival"), Palette.WARNING).tagged(TAG_CREATIVE_SURVIVAL));
            }
        });
        if (!layers.isEmpty()) {
            lines.add(Line.section(Component.translatable("propwash.share.section.extras")));
            lines.addAll(layers);
        }
        if (creative) {
            lines.add(Line.section(Component.translatable("propwash.share.section.shopping")));
            lines.add(Line.indented(Component.translatable("propwash.share.shopping.creative"), Palette.TEXT_MUTED));
        } else if (shopping != null) {
            addShopping(lines, shopping, state);
        }
        if (!decoded.skipped().isEmpty()) {
            lines.add(Line.of(Component.translatable("propwash.share.import.skipped", Component.literal(String.join(", ", decoded.skipped()))),
                    Palette.TEXT_FAINT));
        }
        return lines;
    }

    private static MutableComponent partLine(ShareServer.Slot slot, ShareImportState state) {
        ShareDecoded decoded = state.decoded();
        MutableComponent name = partName(slot.id(), state);
        if (decoded.unknownParts().contains(slot.id())) {
            name = name.append(" ").append(Component.translatable("propwash.share.part.unknown"));
        } else if (decoded.inactiveParts().contains(slot.id())) {
            name = name.append(" ").append(Component.translatable("propwash.share.part.inactive"));
        } else if (decoded.wrongKind().contains(slot.id())) {
            name = name.append(" ").append(Component.translatable("propwash.share.part.wrong_kind"));
        }
        return ShareCommands.partLine(slot, name, "propwash.share.import.part");
    }

    private static int partColor(Identifier id, ShareDecoded decoded) {
        if (decoded.unknownParts().contains(id)) {
            return Palette.ERROR;
        }
        return decoded.wrongKind().contains(id) || decoded.inactiveParts().contains(id) ? Palette.WARNING : Palette.TEXT;
    }

    public static void addStats(List<Line> lines, BuildStats stats) {
        Locale locale = locale();
        lines.add(Line.section(Component.translatable("propwash.share.section.stats")));
        lines.add(Line.indented(Component.translatable("propwash.share.stats.mass", ShareCommands.number(stats.massGrams(), 0, locale),
                ShareCommands.number(stats.thrustToWeight(), 1, locale)), Palette.TEXT));
        lines.add(Line.indented(Component.translatable("propwash.share.stats.time", ShareCommands.number(stats.hoverFlightTimeMin(), 1, locale),
                ShareCommands.number(stats.cruiseFlightTimeMin(), 1, locale)), Palette.TEXT));
        lines.add(Line.indented(Component.translatable("propwash.share.stats.time_style", ShareCommands.number(stats.mixedFlightTimeMin(), 1, locale),
                ShareCommands.number(stats.aggressiveFlightTimeMin(), 1, locale)), Palette.TEXT));
        lines.add(Line.indented(Component.translatable("propwash.share.stats.speed", ShareCommands.number(stats.topSpeedKmh(), 0, locale),
                ShareCommands.number(stats.fullThrottleCurrentA(), 0, locale)), Palette.TEXT));
        for (String warning : stats.warnings()) {
            lines.add(Line.indented(Component.translatable(BuildStats.warningTranslationKey(warning)), Palette.WARNING));
        }
    }

    public static void addShopping(List<Line> lines, ShoppingList shopping, ShareImportState state) {
        lines.add(Line.section(Component.translatable("propwash.share.section.shopping")));
        for (ShoppingList.Entry entry : shopping.entries()) {
            MutableComponent name = partName(entry.id(), state);
            String tag = TAG_SHOPPING + entry.id();
            if (entry.unknown()) {
                Optional<MissingAddons.Line> addon = state.lineOf(entry.id());
                Component needs = addon.<Component>map(line -> line.requiredVersion().isPresent()
                        ? Component.translatable("propwash.share.shopping.needs_version", literal(line.name()), line.requiredVersion().get())
                        : literal(line.name())).orElse(Component.literal(MissingAddons.keyOf(entry.id())));
                lines.add(Line.indented(Component.translatable("propwash.share.shopping.needs_addon", entry.needed(), name, needs), Palette.ERROR)
                        .tagged(tag + ":needs"));
            } else if (entry.missing() == 0) {
                lines.add(Line.indented(Component.translatable("propwash.share.shopping.have", entry.needed(), name), Palette.SUCCESS).tagged(tag + ":have"));
            } else {
                lines.add(Line.indented(Component.translatable("propwash.share.shopping.missing", entry.missing(), entry.needed(), name), Palette.ERROR)
                        .tagged(tag + ":missing"));
            }
        }
        int missing = shopping.missingPieces();
        lines.add(Line.indented(missing == 0 ? Component.translatable("propwash.share.shopping.complete")
                : Component.translatable("propwash.share.shopping.total", missing), missing == 0 ? Palette.SUCCESS : Palette.WARNING));
    }

    public static void addPaintCost(List<Line> lines, Map<String, Integer> cost) {
        if (cost.isEmpty()) {
            lines.add(Line.indented(Component.translatable("propwash.share.paint_cost.free"), Palette.TEXT_MUTED));
            return;
        }
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            lines.add(Line.indented(Component.translatable("propwash.share.paint_cost.dye", entry.getValue(), ShareCommands.dyeName(entry.getKey())),
                    Palette.TEXT));
        }
    }

    public static List<Line> result(ImportResult result) {
        List<Line> lines = new ArrayList<>();
        int color = result.status().success() ? Palette.SUCCESS : result.status() == ImportStatus.BUSY ? Palette.WARNING : Palette.ERROR;
        lines.add(Line.of(Component.translatable(result.status().translationKey()), color).tagged("result:" + result.status().name().toLowerCase(Locale.ROOT)));
        String language = ShareImportState.language();
        for (Map.Entry<Identifier, Integer> entry : ShoppingList.count(result.missingParts()).entrySet()) {
            lines.add(Line.indented(Component.translatable("propwash.share.result.missing_part", entry.getValue(), ShareCommands.partName(entry.getKey(),
                    language)), Palette.ERROR));
        }
        for (Map.Entry<String, Integer> entry : result.missingDyes().entrySet()) {
            lines.add(Line.indented(Component.translatable("propwash.share.result.missing_dye", entry.getValue(), ShareCommands.dyeName(entry.getKey())),
                    Palette.ERROR));
        }
        return lines;
    }

    public static int height(Font font, List<Line> lines, int width) {
        int total = 0;
        for (Line line : lines) {
            total += rowsOf(font, line, width).size() * Metrics.LINE_HEIGHT + (line.section() ? 3 : 0) + (line.clickable() ? 2 : 0);
        }
        return total;
    }

    public static int textWidth(Font font, List<Line> lines, int width, int height) {
        return height(font, lines, width) > height ? width - SCROLLBAR_SPACE : width;
    }

    public static int scrollHeight(Font font, List<Line> lines, int width, int height) {
        return height(font, lines, textWidth(font, lines, width, height));
    }

    public static List<FormattedCharSequence> rowsOf(Font font, Line line, int width) {
        return font.split(line.text(), Math.max(8, width - line.textIndent() - line.rightPad()));
    }

    public static boolean fullyVisible(int row, int top, int bottom) {
        return row >= top && row + Metrics.TEXT_HEIGHT + 1 <= bottom;
    }

    public static int render(GuiGraphicsExtractor g, Font font, List<Line> lines, int x, int y, int width, int height, int scroll) {
        return render(g, font, lines, x, y, width, height, scroll, -1, -1, null);
    }

    public static int render(GuiGraphicsExtractor g, Font font, List<Line> lines, int x, int y, int width, int height, int scroll, int mouseX, int mouseY,
                             @Nullable List<Placed> placed, @Nullable Drag drag) {
        int total = render(g, font, lines, x, y, width, height, scroll, mouseX, mouseY, placed, drag != null && drag.active());
        if (drag != null) {
            drag.track(x + width - Metrics.SCROLLBAR_WIDTH, y, height, total);
        }
        return total;
    }

    public static int render(GuiGraphicsExtractor g, Font font, List<Line> lines, int x, int y, int width, int height, int scroll, int mouseX, int mouseY,
                             @Nullable List<Placed> placed) {
        return render(g, font, lines, x, y, width, height, scroll, mouseX, mouseY, placed, false);
    }

    private static int render(GuiGraphicsExtractor g, Font font, List<Line> lines, int x, int y, int width, int height, int scroll, int mouseX, int mouseY,
                              @Nullable List<Placed> placed, boolean dragging) {
        int textWidth = textWidth(font, lines, width, height);
        int total = height(font, lines, textWidth);
        if (placed != null) {
            placed.clear();
        }
        g.enableScissor(x, y, x + width, y + height);
        int row = y - scroll;
        for (Line line : lines) {
            if (line.section()) {
                row += 3;
            }
            if (line.clickable()) {
                row += 1;
            }
            List<FormattedCharSequence> rows = rowsOf(font, line, textWidth);
            int top = row;
            int bottom = row + rows.size() * Metrics.LINE_HEIGHT;
            int widest = 0;
            for (FormattedCharSequence text : rows) {
                widest = Math.max(widest, font.width(text));
            }
            int x0 = x + line.indent();
            int x1 = Math.min(x + textWidth, x + line.textIndent() + widest + line.rightPad());
            boolean visible = fullyVisible(top, y, y + height) && bottom - 1 <= y + height;
            boolean hovered = line.clickable() && mouseX >= x0 && mouseX < x1 && mouseY >= top - 1 && mouseY < bottom && mouseY >= y && mouseY < y + height;
            if (line.clickable()) {
                ThemeDraw.framedBox(g, x0, top - 1, x1, bottom, hovered ? Palette.BUTTON_HOVER : Palette.BUTTON, hovered ? Palette.ACCENT : Palette.BORDER);
            }
            if (line.icon() != null && fullyVisible(top, y, y + height)) {
                int iconX = x0 + (line.clickable() ? ACTION_PAD : 0);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, line.icon(), iconX, top, ICON, ICON);
            }
            boolean titleShown = false;
            for (FormattedCharSequence text : rows) {
                if (fullyVisible(row, y, y + height)) {
                    ThemeText.text(g, font, text, x + line.textIndent(), row, hovered ? Palette.ACCENT_HOVER : line.color());
                    titleShown = true;
                }
                row += Metrics.LINE_HEIGHT;
            }
            if (line.clickable()) {
                row += 1;
            }
            if (line.section() && titleShown && row - 1 < y + height) {
                ThemeDraw.separator(g, x, x + textWidth, row - 1);
            }
            if (placed != null) {
                placed.add(new Placed(line, x0, top - 1, x1, bottom, visible));
            }
        }
        g.disableScissor();
        if (total > height) {
            ThemeDraw.scrollbar(g, x + width - Metrics.SCROLLBAR_WIDTH, y, height, total, scroll, dragging);
        }
        return total;
    }

    public static boolean click(List<Placed> placed, double mouseX, double mouseY) {
        for (Placed entry : placed) {
            if (entry.line().clickable() && entry.contains(mouseX, mouseY)) {
                entry.line().action().run();
                return true;
            }
        }
        return false;
    }

    public static boolean hintVisible(List<Placed> placed, ShareImportState state) {
        boolean problem = state.addonProblem();
        for (Placed entry : placed) {
            String tag = entry.line().tag();
            if (tag.startsWith(TAG_ADDON) && (!problem || problemKey(state, tag.substring(TAG_ADDON.length())))) {
                return entry.visible();
            }
        }
        return state.addons().isEmpty();
    }

    private static boolean problemKey(ShareImportState state, String key) {
        for (ShareImportState.Addon addon : state.addons()) {
            if (addon.key().equals(key)) {
                return addon.problem();
            }
        }
        return false;
    }

    public static Optional<Placed> find(List<Placed> placed, String tagPrefix) {
        for (Placed entry : placed) {
            if (entry.line().tag().startsWith(tagPrefix)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public static Optional<Line> find(List<Line> lines, String tagPrefix, boolean unused) {
        for (Line line : lines) {
            if (line.tag().startsWith(tagPrefix)) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }

    public static List<String> wrapCode(Font font, String code, int width) {
        return ShareCommands.wrapCode(code, width, font::width);
    }

    public static int clampScroll(int scroll, int total, int height) {
        return Math.max(0, Math.min(scroll, Math.max(0, total - height)));
    }

    public static boolean otherCode(ShareDecoded decoded) {
        return decoded.status() == ShareStatus.OTHER_CODE && (ShareFormat.TUNE_PRESET.equals(decoded.format())
                || ShareFormat.DRONE_EDITOR.equals(decoded.format()));
    }
}
