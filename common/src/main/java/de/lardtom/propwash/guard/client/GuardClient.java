package de.lardtom.propwash.guard.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.guard.FileScan;
import de.lardtom.propwash.guard.GuardCategory;
import de.lardtom.propwash.guard.GuardReport;
import de.lardtom.propwash.guard.MixinScan;
import de.lardtom.propwash.guard.NamespaceGuard;
import de.lardtom.propwash.guard.NetworkScan;
import de.lardtom.propwash.guard.RegistryScan;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.ui.client.theme.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class GuardClient {
    public static final Identifier RELOAD_ID = Constants.id("namespace_guard");
    public static final Identifier DETAILS_ID = Constants.id("guard_details");
    public static final String HINT_KEY = "propwash.guard.hint";
    public static final String HINT_MANY_KEY = "propwash.guard.hint.many";
    public static final String DETAILS_KEY = "propwash.guard.details";
    private static boolean initialized;
    private static boolean joined;
    private static boolean pending;
    private static int hints;
    private static int reloads;
    private static int logins;

    private GuardClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientRegistrar.reloadListener(RELOAD_ID, (ResourceManagerReloadListener) GuardClient::reload);
        ClientBus.LOGIN.register(minecraft -> login());
        ClientBus.TICK_END.register(GuardClient::tick);
    }

    static void login() {
        logins++;
        if (!joined) {
            joined = true;
            pending = true;
        }
    }

    static void tick(Minecraft minecraft) {
        if (!pending || minecraft.player == null || minecraft.level == null) {
            return;
        }
        pending = false;
        Component hint = hint();
        if (hint != null) {
            minecraft.gui.hud.getChat().addClientSystemMessage(hint);
            hints++;
        }
    }

    public static void reload(ResourceManager manager) {
        long start = System.nanoTime();
        Minecraft minecraft = Minecraft.getInstance();
        GuardReport report = NamespaceGuard.report();
        NamespaceGuard.scan(GuardCategory.REGISTRY, () -> RegistryScan.run(report));
        NamespaceGuard.scan(GuardCategory.NETWORK, () -> NetworkScan.run(report, true));
        NamespaceGuard.scan(GuardCategory.ENUM, () -> MixinScan.run(report, true));
        NamespaceGuard.scan(GuardCategory.KEY_MAPPING, () -> ClientScans.keyMappings(report, minecraft));
        NamespaceGuard.scan(GuardCategory.TRANSLATION, () -> ClientScans.translations(report, minecraft.getLanguageManager().getSelected()));
        NamespaceGuard.scan(GuardCategory.ASSET, () -> FileScan.mods(report, FileScan.ASSETS));
        NamespaceGuard.scan(GuardCategory.ASSET, () -> FileScan.playerPacks(manager, PackType.CLIENT_RESOURCES));
        reloads++;
        NamespaceGuard.finish("resources", start);
    }

    public static @Nullable Component hint() {
        GuardReport report = NamespaceGuard.report();
        List<String> mods = report.mods();
        if (mods.isEmpty()) {
            return null;
        }
        MutableComponent text = mods.size() == 1
                ? Component.translatableWithFallback(HINT_KEY, "%s uses the reserved namespace propwash – this may break with the next update.",
                NamespaceGuard.name(mods.getFirst()))
                : Component.translatableWithFallback(HINT_MANY_KEY, "%s add-ons use the reserved namespace propwash – this may break with the next update.",
                mods.size());
        return text.withStyle(ChatFormatting.YELLOW).append(" ").append(detailsButton());
    }

    public static MutableComponent detailsButton() {
        Component label = Component.translatableWithFallback(DETAILS_KEY, "Details");
        return Component.literal("[").append(label).append("]").withStyle(style -> style
                .withColor(TextColor.fromRgb(Palette.ACCENT & 0xFFFFFF))
                .withClickEvent(new ClickEvent.Custom(DETAILS_ID, Optional.empty()))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatableWithFallback("propwash.guard.details.tooltip",
                        "Shows which add-on uses what"))));
    }

    public static boolean handleClick(ClickEvent event, Minecraft minecraft, @Nullable Screen parent) {
        if (event instanceof ClickEvent.Custom custom && DETAILS_ID.equals(custom.id())) {
            minecraft.gui.setScreen(new NamespaceReportScreen(parent));
            return true;
        }
        return false;
    }

    public static int hints() {
        return hints;
    }

    public static int reloads() {
        return reloads;
    }

    public static int logins() {
        return logins;
    }
}
