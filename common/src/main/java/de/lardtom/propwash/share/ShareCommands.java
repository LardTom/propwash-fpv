package de.lardtom.propwash.share;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.join.SafeText;
import de.lardtom.propwash.platform.ModMeta;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.net.URI;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

public final class ShareCommands {
    public static final String ROOT = "propwash";
    public static final String NODE = "code";
    public static final String SUBSTITUTES = "substitutes";
    public static final int SUCCESS = 1;
    public static final int FAILURE = 0;
    public static final String KEY = "propwash.command.code.";

    private static final SuggestionProvider<CommandSourceStack> LAYER_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggest(
            List.of("parts,paint,tune,name", "parts,tune", "parts,paint", "parts", "parts,paint,tune", "parts,paint,tune,name,creative"), builder);

    private ShareCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(ROOT).then(node()));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal(NODE)
                .then(Commands.literal("export")
                        .executes(c -> export(c.getSource(), ShareLayer.DEFAULT_EXPORT, true))
                        .then(Commands.argument("layers", StringArgumentType.greedyString())
                                .suggests(LAYER_SUGGESTIONS)
                                .executes(c -> exportWith(c, StringArgumentType.getString(c, "layers")))))
                .then(Commands.literal("import")
                        .then(Commands.argument("code", StringArgumentType.greedyString())
                                .executes(c -> importCode(c.getSource(), StringArgumentType.getString(c, "code")))))
                .then(Commands.literal("info")
                        .then(Commands.argument("code", StringArgumentType.greedyString())
                                .executes(c -> info(c.getSource(), StringArgumentType.getString(c, "code")))))
                .then(Commands.literal("convert")
                        .then(Commands.argument("code", StringArgumentType.greedyString())
                                .executes(c -> convert(c.getSource(), StringArgumentType.getString(c, "code")))))
                .then(Commands.literal("give")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(c -> give(c.getSource(), StringArgumentType.getString(c, "code"), List.of(c.getSource().getPlayerOrException()),
                                        false))
                                .then(Commands.literal(SUBSTITUTES)
                                        .executes(c -> give(c.getSource(), StringArgumentType.getString(c, "code"),
                                                List.of(c.getSource().getPlayerOrException()), true)))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> give(c.getSource(), StringArgumentType.getString(c, "code"), EntityArgument.getPlayers(c, "targets"),
                                                false))
                                        .then(Commands.literal(SUBSTITUTES)
                                                .executes(c -> give(c.getSource(), StringArgumentType.getString(c, "code"),
                                                        EntityArgument.getPlayers(c, "targets"), true))))));
    }

    public static Locale locale(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? Locale.ROOT : locale(player.clientInformation().language());
    }

    public static String language(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? "en_us" : player.clientInformation().language();
    }

    public static Locale locale(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return Locale.ROOT;
        }
        return Locale.forLanguageTag(languageCode.replace('_', '-'));
    }

    public static String number(double value, int decimals, Locale locale) {
        if (!Double.isFinite(value)) {
            return "–";
        }
        DecimalFormat format = new DecimalFormat(decimals <= 0 ? "0" : "0." + "0".repeat(decimals), DecimalFormatSymbols.getInstance(locale));
        return format.format(value);
    }

    public static MutableComponent partName(Identifier id) {
        return partName(id, "en_us");
    }

    public static MutableComponent partName(Identifier id, String language) {
        if (Pw3Codec.unknownMarker(id)) {
            return Component.literal(Pw3Codec.display(id));
        }
        String fallback = ShareTables.current().partNames(id)
                .map(names -> names.getOrDefault(language == null ? "en_us" : language.toLowerCase(Locale.ROOT), names.get("en_us")))
                .filter(name -> name != null && !name.isBlank()).orElse(id.toString());
        return Component.translatableWithFallback("part." + id.getNamespace() + "." + id.getPath(), fallback);
    }

    public static MutableComponent dyeName(String dye) {
        if ("custom".equals(dye)) {
            return Component.translatable("propwash.share.dye.custom");
        }
        return Component.translatableWithFallback("item.minecraft." + dye + "_dye", dye);
    }

    public static MutableComponent kindName(PartKind kind) {
        return Component.translatable("propwash.share.kind." + kind.key());
    }

    public static boolean namesKind(PartKind kind, Component name) {
        String kindText = kindName(kind).getString().toLowerCase(Locale.ROOT);
        return !kindText.isBlank() && name.getString().toLowerCase(Locale.ROOT).contains(kindText);
    }

    public static MutableComponent partLine(ShareServer.Slot slot, MutableComponent name, String formatKey) {
        if (namesKind(slot.kind(), name)) {
            return slot.count() > 1 ? Component.literal(slot.count() + "× ").append(name) : name;
        }
        MutableComponent label = slot.count() > 1 ? Component.literal(slot.count() + "× ").append(kindName(slot.kind())) : kindName(slot.kind());
        return Component.translatable(formatKey, label, name);
    }

    public static MutableComponent layerList(Collection<ShareLayer> layers) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (ShareLayer layer : ShareLayer.values()) {
            if (!layers.contains(layer)) {
                continue;
            }
            if (!first) {
                out.append(", ");
            }
            out.append(Component.translatable(layer.translationKey()));
            first = false;
        }
        return out;
    }

    public static MutableComponent idList(Collection<Identifier> ids) {
        List<String> names = new ArrayList<>();
        for (Identifier id : ids) {
            names.add(Pw3Codec.display(id));
        }
        return Component.literal(String.join(", ", names));
    }

    public static MutableComponent reason(RuntimeException e) {
        if (e instanceof ShareException share) {
            Object[] args = share.args();
            Object[] shown = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                shown[i] = String.valueOf(args[i]);
            }
            return Component.translatable(share.key(), shown);
        }
        return Component.translatable(ShareException.REJECTED);
    }

    public static List<String> wrapCode(String code, int width, ToIntFunction<String> measure) {
        List<String> rows = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        String[] groups = code.split("-", -1);
        for (int i = 0; i < groups.length; i++) {
            String piece = i < groups.length - 1 ? groups[i] + "-" : groups[i];
            if (!row.isEmpty() && measure.applyAsInt(row + piece) > width) {
                rows.add(row.toString());
                row.setLength(0);
            }
            if (measure.applyAsInt(piece) <= width) {
                row.append(piece);
                continue;
            }
            for (int c = 0; c < piece.length(); c++) {
                if (!row.isEmpty() && measure.applyAsInt(row.toString() + piece.charAt(c)) > width) {
                    rows.add(row.toString());
                    row.setLength(0);
                }
                row.append(piece.charAt(c));
            }
        }
        if (!row.isEmpty()) {
            rows.add(row.toString());
        }
        return rows;
    }

    public static boolean fits(String code) {
        return code.length() <= Pw3Params.MAX_INPUT_CHARS;
    }

    public record Addon(NamespaceReport report, String name, List<MissingAddons.Link> links) {
    }

    public static String addonName(NamespaceReport report, ShareTables tables, String language) {
        Optional<String> namespace = report.namespace();
        Optional<AddonDirectory.Addon> addon = namespace.flatMap(ns -> tables.directory().byNamespace(ns));
        Optional<ModMeta> meta = namespace.flatMap(tables::mod);
        String name = addon.map(a -> a.displayName(language)).or(report::codeName).or(() -> meta.map(ModMeta::name))
                .orElse(namespace.orElse("#" + report.shortId()));
        return SafeText.clean(name, Pw3Params.MAX_ADDON_NAME_CHARS + 16);
    }

    public static List<MissingAddons.Link> links(NamespaceReport report, ShareTables tables) {
        List<MissingAddons.Link> links = new ArrayList<>();
        Optional<AddonDirectory.Addon> addon = report.namespace().flatMap(ns -> tables.directory().byNamespace(ns));
        if (addon.isPresent()) {
            addon.get().modrinth().ifPresent(slug -> links.add(new MissingAddons.Link(MissingAddons.MODRINTH + slug, true, true)));
            addon.get().curseforge().ifPresent(slug -> links.add(new MissingAddons.Link(MissingAddons.CURSEFORGE + slug, true, true)));
        } else if (report.unknownShortId()) {
            links.add(new MissingAddons.Link(AddonDirectory.ADDONS_PAGE, true, true));
        } else if (report.link().isPresent()) {
            switch (report.linkType()) {
                case Pw3Params.LINK_MODRINTH -> links.add(new MissingAddons.Link(MissingAddons.MODRINTH + report.link().get(), true, false));
                case Pw3Params.LINK_CURSEFORGE -> links.add(new MissingAddons.Link(MissingAddons.CURSEFORGE + report.link().get(), true, false));
                case Pw3Params.LINK_HTTPS -> links.add(new MissingAddons.Link("https://" + report.link().get(), false, false));
                default -> {
                }
            }
        }
        return links;
    }

    public static boolean clickable(MissingAddons.Link link) {
        if (!link.clickable()) {
            return false;
        }
        String url = link.url();
        return url.startsWith(MissingAddons.MODRINTH) || url.startsWith(MissingAddons.CURSEFORGE) || url.equals(AddonDirectory.ADDONS_PAGE);
    }

    public static String linkKey(MissingAddons.Link link) {
        if (!clickable(link)) {
            return "propwash.share.link.untrusted";
        }
        if (link.url().startsWith(MissingAddons.MODRINTH)) {
            return "propwash.share.link.modrinth";
        }
        if (link.url().startsWith(MissingAddons.CURSEFORGE)) {
            return "propwash.share.link.curseforge";
        }
        return "propwash.share.link.directory";
    }

    public static MutableComponent linkText(MissingAddons.Link link) {
        if (!clickable(link)) {
            String shown = SafeText.clean(link.url().startsWith("https://") ? link.url().substring("https://".length()) : link.url(), 120);
            return Component.translatable("propwash.share.link.untrusted", Component.literal(shown)).withStyle(ChatFormatting.GRAY);
        }
        String url = SafeText.url(link.url());
        MutableComponent label = Component.literal("[").append(Component.translatable(linkKey(link))).append("]");
        if (url.isEmpty()) {
            return label.withStyle(ChatFormatting.GRAY);
        }
        return label.withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(url))));
    }

    public static MutableComponent namespaceLine(NamespaceReport report, ShareTables tables, String language) {
        String name = addonName(report, tables, language);
        if (report.unknownShortId()) {
            return Component.translatable("propwash.share.addons.unknown_short", report.shortId());
        }
        String needed = report.minVersion().orElse("?");
        Optional<String> installed = report.installedVersion().or(() -> report.namespace().flatMap(tables::version));
        boolean present = report.namespace().map(tables::installed).orElse(false);
        if (!present) {
            return Component.translatable("propwash.share.addons.missing", name, needed);
        }
        if (report.result() == NamespaceReport.Result.NEWER) {
            return Component.translatable("propwash.share.addons.older", name, needed, installed.orElse("?"));
        }
        if (report.result() == NamespaceReport.Result.MISMATCH) {
            return Component.translatable("propwash.share.addons.mismatch", name);
        }
        return Component.translatable("propwash.share.addons.ok", name, needed, installed.orElse("–"));
    }

    private static int exportWith(CommandContext<CommandSourceStack> context, String text) throws CommandSyntaxException {
        EnumSet<ShareLayer> layers = EnumSet.of(ShareLayer.PARTS);
        for (String part : text.split("[,;\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            Optional<ShareLayer> layer = ShareLayer.byKey(part);
            if (layer.isEmpty() || layer.get() == ShareLayer.OSD) {
                context.getSource().sendFailure(Component.translatable(KEY + "bad_layer", Component.literal(SafeText.clean(part, 32))));
                return FAILURE;
            }
            layers.add(layer.get());
        }
        return export(context.getSource(), layers, false);
    }

    private static int export(CommandSourceStack source, Collection<ShareLayer> layers, boolean defaults) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<DroneData> held = ShareServer.held(player);
        if (held.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "no_drone"));
            return FAILURE;
        }
        EnumSet<ShareLayer> chosen = EnumSet.copyOf(layers);
        chosen.remove(ShareLayer.OSD);
        Optional<CreativeSpec> creative = ShareServer.heldCreative(player);
        if (defaults && creative.isPresent()) {
            chosen.add(ShareLayer.CREATIVE);
        }
        if (creative.isEmpty()) {
            chosen.remove(ShareLayer.CREATIVE);
        }
        String code;
        try {
            code = ShareService.encode(held.get(), creative, chosen, true, Optional.empty());
        } catch (RuntimeException e) {
            source.sendFailure(Component.translatable(KEY + "not_shareable", reason(e)));
            return FAILURE;
        }
        if (!fits(code)) {
            source.sendFailure(Component.translatable(KEY + "too_long", code.length(), Pw3Params.MAX_INPUT_CHARS));
            return FAILURE;
        }
        ShareContent content = ShareContent.of(held.get(), chosen, Optional.empty())
                .withCreative(chosen.contains(ShareLayer.CREATIVE) ? creative : Optional.empty());
        MutableComponent header = Component.translatable(KEY + "export", code.length(), layerList(content.layers()));
        source.sendSuccess(() -> header, false);
        source.sendSuccess(() -> codeLine(code), false);
        return SUCCESS;
    }

    public static MutableComponent codeLine(String code) {
        return Component.literal(code).withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(code))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(KEY + "click_to_copy"))));
    }

    private static Optional<ShareDecoded> decodeOrReport(CommandSourceStack source, String code) {
        ShareDecoded decoded = ShareService.decode(code);
        if (!decoded.ok()) {
            source.sendFailure(statusText(decoded));
            return Optional.empty();
        }
        return Optional.of(decoded);
    }

    public static MutableComponent statusText(ShareDecoded decoded) {
        if (decoded.status() == ShareStatus.OTHER_CODE) {
            return Component.translatable(ShareFormat.TUNE_PRESET.equals(decoded.format()) ? "propwash.share.other.pwt" : "propwash.share.other.pwd");
        }
        return Component.translatable(decoded.status().translationKey());
    }

    private static boolean reportBlocked(CommandSourceStack source, ShareDecoded decoded, boolean substitutes) {
        if (decoded.partsLocked()) {
            source.sendFailure(Component.translatable(KEY + "locked"));
            return true;
        }
        if (substitutes) {
            return false;
        }
        if (!decoded.unknownParts().isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "unknown_parts", idList(decoded.unknownParts())));
            source.sendFailure(Component.translatable(KEY + "try_substitutes").withStyle(ChatFormatting.GRAY));
            return true;
        }
        if (!decoded.inactiveParts().isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "inactive_parts", idList(decoded.inactiveParts())));
            source.sendFailure(Component.translatable(KEY + "try_substitutes").withStyle(ChatFormatting.GRAY));
            return true;
        }
        if (!decoded.wrongKind().isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "wrong_kind", idList(decoded.wrongKind())));
            return true;
        }
        return false;
    }

    static String stripSubstitutes(String text) {
        String trimmed = text.strip();
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(" " + SUBSTITUTES)) {
            return trimmed.substring(0, trimmed.length() - SUBSTITUTES.length() - 1).strip();
        }
        return trimmed;
    }

    static boolean wantsSubstitutes(String text) {
        return !stripSubstitutes(text).equals(text.strip());
    }

    private static int importCode(CommandSourceStack source, String text) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean substitutes = wantsSubstitutes(text);
        Optional<ShareDecoded> result = decodeOrReport(source, stripSubstitutes(text));
        if (result.isEmpty()) {
            return FAILURE;
        }
        ShareDecoded decoded = result.get();
        ShareContent content = decoded.require();
        if (player.isSpectator()) {
            source.sendFailure(Component.translatable("propwash.api.import.not_allowed"));
            return FAILURE;
        }
        if (player.hasInfiniteMaterials()) {
            if (reportBlocked(source, decoded, substitutes)) {
                return FAILURE;
            }
            Optional<ShareContent> chosen = substitutes ? substitute(source, decoded) : Optional.of(content);
            return chosen.map(value -> createFor(source, value, List.of(player))).orElse(FAILURE);
        }
        if (content.creative().isPresent()) {
            source.sendSuccess(() -> Component.translatable(KEY + "creative_survival").withStyle(ChatFormatting.GRAY), false);
        }
        if (ShareServer.heldLayers(content).isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "parts_at_workbench"));
            return FAILURE;
        }
        if (ShareServer.held(player).isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "no_drone"));
            return FAILURE;
        }
        ImportResult applied = ShareServer.applyToHeld(player, content);
        MutableComponent line = Component.translatable(KEY + "held", Component.translatable(applied.status().translationKey()));
        if (applied.status().success()) {
            source.sendSuccess(() -> line, false);
            source.sendSuccess(() -> Component.translatable(KEY + "parts_at_workbench").withStyle(ChatFormatting.GRAY), false);
            return SUCCESS;
        }
        source.sendFailure(line);
        if (!applied.missingDyes().isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "missing_dyes", dyes(applied.missingDyes())));
        }
        return FAILURE;
    }

    private static Optional<ShareContent> substitute(CommandSourceStack source, ShareDecoded decoded) {
        Substitutes.Result found = ShareServer.substitutes(decoded, ShareTables.current());
        if (found.build().isEmpty()) {
            source.sendFailure(Component.translatable("propwash.share.substitutes.none", idList(missing(decoded))));
            return Optional.empty();
        }
        String language = language(source);
        for (Substitutes.Swap swap : found.swaps()) {
            MutableComponent to = swap.to().map(id -> partName(id, language)).orElse(Component.translatable("propwash.share.substitutes.removed"));
            source.sendSuccess(() -> Component.translatable("propwash.share.substitutes.swap", partName(swap.from(), language), to)
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return Optional.of(decoded.require().withBuild(found.build().get()));
    }

    static List<Identifier> missing(ShareDecoded decoded) {
        List<Identifier> out = new ArrayList<>(decoded.unknownParts());
        out.addAll(decoded.inactiveParts());
        return out;
    }

    static MutableComponent dyes(Map<String, Integer> dyes) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : dyes.entrySet()) {
            if (!first) {
                out.append(", ");
            }
            out.append(entry.getValue() + "× ").append(dyeName(entry.getKey()));
            first = false;
        }
        return out;
    }

    private static int give(CommandSourceStack source, String code, Collection<ServerPlayer> targets, boolean substitutes) {
        Optional<ShareDecoded> result = decodeOrReport(source, code);
        if (result.isEmpty() || reportBlocked(source, result.get(), substitutes)) {
            return FAILURE;
        }
        Optional<ShareContent> content = substitutes ? substitute(source, result.get()) : Optional.of(result.get().require());
        return content.map(value -> createFor(source, value, targets)).orElse(FAILURE);
    }

    private static int createFor(CommandSourceStack source, ShareContent content, Collection<ServerPlayer> targets) {
        BuildCheck check = PropwashApi.builds().check(content.build());
        if (!check.buildable()) {
            source.sendFailure(Component.translatable(KEY + "not_buildable", issues(check.problems())));
            return FAILURE;
        }
        ItemStack drone;
        try {
            drone = ShareServer.createDrone(content);
        } catch (RuntimeException e) {
            source.sendFailure(Component.translatable(KEY + "not_shareable", reason(e)));
            return FAILURE;
        }
        for (ServerPlayer target : targets) {
            ShareServer.give(target, drone.copy());
        }
        Component name = content.name().<Component>map(Component::literal).orElseGet(() -> partName(content.build().frame()));
        if (targets.size() == 1) {
            Component who = targets.iterator().next().getDisplayName();
            source.sendSuccess(() -> Component.translatable(KEY + "created", name, who), true);
        } else {
            source.sendSuccess(() -> Component.translatable(KEY + "created_many", name, targets.size()), true);
        }
        content.creative().ifPresent(spec -> source.sendSuccess(() -> creativeLine(spec).withStyle(ChatFormatting.GRAY), false));
        Constants.LOG.info("Sharecode: Drohne {} an {} Spieler gegeben", content.name().orElse(content.build().frame().toString()), targets.size());
        return targets.size();
    }

    private static int convert(CommandSourceStack source, String code) {
        ShareConvert.Conversion conversion = ShareService.convert(code);
        if (!conversion.ok()) {
            if (conversion.status() == ShareStatus.NOT_CONVERTIBLE) {
                source.sendFailure(Component.translatable("propwash.share.status.not_convertible.reason",
                        Component.literal(conversion.reason().orElse("?"))));
            } else if (conversion.status() == ShareStatus.OTHER_CODE) {
                source.sendFailure(Component.translatable(ShareFormat.TUNE_PRESET.equals(conversion.format()) ? "propwash.share.other.pwt"
                        : "propwash.share.other.pwd"));
            } else {
                source.sendFailure(Component.translatable(conversion.status().translationKey()));
            }
            return FAILURE;
        }
        String pw3 = conversion.code().orElseThrow();
        if (!conversion.converted()) {
            source.sendSuccess(() -> Component.translatable(KEY + "already_pw3"), false);
        }
        source.sendSuccess(() -> Component.translatable(KEY + "converted", codeLine(pw3)), false);
        return SUCCESS;
    }

    public static MutableComponent creativeLine(CreativeSpec spec) {
        return Component.translatable("propwash.share.import.creative", CreativeShare.values(spec), CreativeShare.baseParts(spec));
    }

    static MutableComponent issues(List<String> keys) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (String key : keys) {
            if (!first) {
                out.append("; ");
            }
            out.append(Component.translatable(BuildCheck.translationKey(key)));
            first = false;
        }
        return out;
    }

    static MutableComponent warnings(List<String> keys) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (String key : keys) {
            if (!first) {
                out.append("; ");
            }
            out.append(Component.translatable(BuildStats.warningTranslationKey(key)));
            first = false;
        }
        return out;
    }

    private static int info(CommandSourceStack source, String code) {
        Optional<ShareDecoded> result = decodeOrReport(source, code);
        if (result.isEmpty()) {
            return FAILURE;
        }
        List<Component> lines = infoLines(result.get(), ShareTables.current(), language(source));
        for (Component line : lines) {
            source.sendSuccess(() -> line, false);
        }
        return SUCCESS;
    }

    public static List<Component> infoLines(ShareDecoded decoded, ShareTables tables, String language) {
        Locale locale = locale(language);
        List<Component> lines = new ArrayList<>();
        ShareContent content = decoded.require();
        lines.add(Component.translatable(KEY + "info.layers", Component.literal(decoded.format()), layerList(decoded.layers()))
                .withStyle(ChatFormatting.GOLD));
        if (decoded.legacy()) {
            lines.add(Component.translatable("propwash.share.legacy", decoded.format()).withStyle(ChatFormatting.GRAY));
        }
        if (ShareFormat.DRONE_EDITOR.equals(decoded.format())) {
            lines.add(Component.translatable("propwash.share.pwd1.build").withStyle(ChatFormatting.GRAY));
        }
        content.name().ifPresent(name -> lines.add(Component.translatable(KEY + "info.name", Component.literal(name))));
        for (NamespaceReport report : decoded.namespaces()) {
            if (!report.used()) {
                continue;
            }
            MutableComponent line = Component.translatable(KEY + "info.namespace", namespaceLine(report, tables, language),
                    Component.translatable(report.result().translationKey()));
            ChatFormatting color = switch (report.result()) {
                case MATCH, NO_TABLE -> ChatFormatting.WHITE;
                case MISMATCH -> ChatFormatting.RED;
                case NEWER, MISSING -> ChatFormatting.YELLOW;
            };
            line.withStyle(color);
            for (MissingAddons.Link link : links(report, tables)) {
                line.append(" ").append(linkText(link));
            }
            lines.add(line);
        }
        if (decoded.partsLocked()) {
            lines.add(Component.translatable(KEY + "locked").withStyle(ChatFormatting.RED));
        }
        for (ShareServer.Slot slot : ShareServer.slots(content.build())) {
            MutableComponent part = partName(slot.id(), language);
            if (decoded.unknownParts().contains(slot.id())) {
                part = part.append(" ").append(Component.translatable("propwash.share.part.unknown")).withStyle(ChatFormatting.RED);
            } else if (decoded.inactiveParts().contains(slot.id())) {
                part = part.append(" ").append(Component.translatable("propwash.share.part.inactive")).withStyle(ChatFormatting.YELLOW);
            } else if (decoded.wrongKind().contains(slot.id())) {
                part = part.append(" ").append(Component.translatable("propwash.share.part.wrong_kind")).withStyle(ChatFormatting.YELLOW);
            }
            lines.add(partLine(slot, part, KEY + "info.part"));
        }
        content.paint().ifPresent(paint -> lines.add(Component.translatable(KEY + "info.paint", paint.colors().size())));
        content.tune().ifPresent(tune -> lines.add(Component.translatable(KEY + "info.tune", tune.values().size())));
        content.osd().ifPresent(osd -> lines.add(Component.translatable(KEY + "info.osd", osd.version(), osd.json().length())));
        content.creative().ifPresent(spec -> lines.add(creativeLine(spec)));
        for (ShareDecoded.Rename rename : decoded.renamed()) {
            lines.add(Component.translatable(KEY + "info.renamed", Component.literal(rename.from().toString()), Component.literal(rename.to().toString())));
        }
        if (!decoded.skipped().isEmpty()) {
            lines.add(Component.translatable(KEY + "info.skipped", Component.literal(String.join(", ", decoded.skipped()))).withStyle(ChatFormatting.GRAY));
        }
        BuildSpec build = content.build();
        if (!decoded.partsKnown()) {
            lines.add(Component.translatable(KEY + "info.not_checked").withStyle(ChatFormatting.RED));
            Substitutes.Result found = ShareServer.substitutes(decoded, tables);
            if (found.build().isPresent()) {
                lines.add(Component.translatable(KEY + "info.substitutes", found.swaps().size()).withStyle(ChatFormatting.GRAY));
                build = found.build().get();
            } else {
                lines.add(Component.translatable("propwash.share.substitutes.none", idList(missing(decoded))).withStyle(ChatFormatting.GRAY));
                return lines;
            }
        }
        BuildCheck check = PropwashApi.builds().check(build);
        if (!check.problems().isEmpty()) {
            lines.add(Component.translatable(KEY + "info.problems", issues(check.problems())).withStyle(ChatFormatting.RED));
        } else {
            lines.add(Component.translatable(KEY + "info.buildable").withStyle(ChatFormatting.GREEN));
        }
        if (!check.warnings().isEmpty()) {
            lines.add(Component.translatable(KEY + "info.warnings", issues(check.warnings())).withStyle(ChatFormatting.YELLOW));
        }
        ShareServer.stats(content.withBuild(build)).ifPresent(stats -> {
            lines.add(Component.translatable(KEY + "info.stats", number(stats.massGrams(), 0, locale), number(stats.thrustToWeight(), 1, locale),
                    number(stats.mixedFlightTimeMin(), 1, locale), number(stats.topSpeedKmh(), 0, locale)));
            if (!stats.warnings().isEmpty()) {
                lines.add(Component.translatable(KEY + "info.analysis_warnings", warnings(stats.warnings())).withStyle(ChatFormatting.YELLOW));
            }
        });
        return lines;
    }
}
