package de.lardtom.propwash.share.client.chat;

import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartNames;
import de.lardtom.propwash.join.SafeText;
import de.lardtom.propwash.share.Pw3Params;
import de.lardtom.propwash.share.ShareCommands;
import de.lardtom.propwash.share.ShareContent;
import de.lardtom.propwash.share.ShareDecoded;
import de.lardtom.propwash.share.ShareTables;
import de.lardtom.propwash.share.client.ShareImportState;
import de.lardtom.propwash.share.client.ShareLines;
import de.lardtom.propwash.share.client.ShoppingList;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ChatPreview {
    public static final String KEY = "propwash.share.chat.";
    private static final int SHOPPING_REFRESH_TICKS = 10;
    private static final int NAME_CHARS = Pw3Params.MAX_NAME_CHARS;

    private final String code;
    private final ShareImportState state = new ShareImportState();
    private List<Component> details = List.of();
    private Optional<DroneData> drone = Optional.empty();
    private long shoppingTick = Long.MIN_VALUE;
    private @Nullable Component shopping;

    private ChatPreview(String code) {
        this.code = code;
    }

    public static ChatPreview decode(String code) {
        ChatPreview preview = new ChatPreview(code);
        try {
            preview.state.update(code);
            preview.details = preview.buildDetails();
            preview.drone = preview.buildDrone();
        } catch (RuntimeException e) {
            preview.details = List.of();
            preview.drone = Optional.empty();
        }
        return preview;
    }

    public String code() {
        return this.code;
    }

    public ShareDecoded decoded() {
        return this.state.decoded();
    }

    public ShareImportState state() {
        return this.state;
    }

    public boolean valid() {
        return this.state.decoded().ok() && !this.details.isEmpty();
    }

    public Optional<DroneData> drone() {
        return this.drone;
    }

    public Component title() {
        ShareDecoded decoded = this.state.decoded();
        Optional<String> name = decoded.content().flatMap(ShareContent::name).map(text -> SafeText.clean(text, NAME_CHARS)).filter(text -> !text.isBlank());
        Component head = name.<Component>map(Component::literal).orElseGet(() -> Component.translatable(KEY + "unnamed"));
        return ThemeText.colored(Component.translatable(KEY + "title", head, decoded.format()), Palette.ACCENT);
    }

    public List<Component> details() {
        return this.details;
    }

    public List<Component> lines(boolean copy) {
        List<Component> out = new ArrayList<>(this.details);
        Component shoppingLine = this.shoppingLine();
        if (shoppingLine != null) {
            out.add(shoppingLine);
        }
        out.add(hint(copy));
        return out;
    }

    public Component hoverText(boolean copy) {
        MutableComponent text = Component.empty().append(this.title());
        for (Component line : this.details) {
            text.append("\n").append(line);
        }
        return text.append("\n").append(hint(copy));
    }

    public static Component hint(boolean copy) {
        return ThemeText.colored(Component.translatable(KEY + (copy ? "hint.copy" : "hint.open")), Palette.TEXT_FAINT);
    }

    private Optional<DroneData> buildDrone() {
        ShareDecoded decoded = this.state.decoded();
        if (!decoded.ok() || !decoded.unknownParts().isEmpty() || !decoded.wrongKind().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(decoded.require().toDroneData());
    }

    private List<Component> buildDetails() {
        ShareDecoded decoded = this.state.decoded();
        if (!decoded.ok()) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        if (decoded.legacy()) {
            lines.add(ThemeText.colored(Component.translatable("propwash.share.legacy", decoded.format()), Palette.TEXT_MUTED));
        }
        BuildSpec build = decoded.require().build();
        PartIndex.byId(FrameType.class, build.frame()).filter(frame -> !frame.missing())
                .ifPresent(frame -> lines.add(line("class", Palette.TEXT, PartNames.frameShort(frame))));
        lines.add(line("frame", this.partColor(build.frame()), this.name(build.frame())));
        lines.add(line("motors", this.partColor(build.motor()), 4, this.name(build.motor())));
        lines.add(line("props", this.partColor(build.prop()), 4, this.name(build.prop())));
        lines.add(line("battery", this.partColor(build.battery()), this.name(build.battery())));
        if (!build.accessories().isEmpty()) {
            MutableComponent list = Component.empty();
            boolean first = true;
            for (Identifier id : build.accessories().values()) {
                if (!first) {
                    list.append(", ");
                }
                list.append(this.name(id));
                first = false;
            }
            lines.add(line("accessories", Palette.TEXT, list));
        }
        decoded.require().creative().ifPresent(spec -> lines.add(ThemeText.colored(ShareCommands.creativeLine(spec), Palette.ACCENT)));
        this.state.stats().ifPresent(stats -> lines.add(weight(stats)));
        if (!decoded.unknownParts().isEmpty()) {
            lines.add(ThemeText.colored(Component.translatable(KEY + "unknown", decoded.unknownParts().size()), Palette.ERROR));
        }
        List<ShareImportState.Addon> addons = this.state.addons().stream().filter(addon -> !ShareTables.PROPWASH.equals(addon.key())).toList();
        if (!addons.isEmpty()) {
            lines.add(ThemeText.colored(Component.translatable(KEY + "addons"), Palette.TEXT_MUTED));
            for (ShareImportState.Addon addon : addons) {
                lines.add(Component.literal("  ").append(ThemeText.colored(ShareLines.addonText(addon), ShareLines.addonColor(addon))));
            }
        }
        return List.copyOf(lines);
    }

    private static Component weight(BuildStats stats) {
        Locale locale = ShareCommands.locale(ShareImportState.language());
        return line("weight", Palette.TEXT, ShareCommands.number(stats.massGrams(), 0, locale), ShareCommands.number(stats.thrustToWeight(), 1, locale));
    }

    private MutableComponent name(Identifier id) {
        return ShareLines.partName(id, this.state);
    }

    private int partColor(Identifier id) {
        ShareDecoded decoded = this.state.decoded();
        if (decoded.unknownParts().contains(id)) {
            return Palette.ERROR;
        }
        return decoded.wrongKind().contains(id) || decoded.inactiveParts().contains(id) ? Palette.WARNING : Palette.TEXT;
    }

    private static Component line(String key, int color, Object... args) {
        return ThemeText.colored(Component.translatable(KEY + key, args), color);
    }

    public @Nullable Component shoppingLine() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ShareDecoded decoded = this.state.decoded();
        if (player == null || player.hasInfiniteMaterials() || !decoded.ok()) {
            this.shopping = null;
            this.shoppingTick = Long.MIN_VALUE;
            return null;
        }
        long tick = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        if (this.shopping == null || tick < this.shoppingTick || tick - this.shoppingTick >= SHOPPING_REFRESH_TICKS) {
            this.shoppingTick = tick;
            this.shopping = shopping(decoded, player);
        }
        return this.shopping;
    }

    private static Component shopping(ShareDecoded decoded, LocalPlayer player) {
        List<Identifier> unknown = new ArrayList<>(decoded.unknownParts());
        unknown.addAll(decoded.wrongKind());
        ShoppingList list = ShoppingList.of(decoded.require().build(), player.getInventory(), null, unknown);
        int missing = list.missingPieces();
        return missing == 0 ? ThemeText.colored(Component.translatable(KEY + "shopping.complete"), Palette.SUCCESS)
                : ThemeText.colored(Component.translatable(KEY + "shopping.missing", missing), Palette.WARNING);
    }
}
