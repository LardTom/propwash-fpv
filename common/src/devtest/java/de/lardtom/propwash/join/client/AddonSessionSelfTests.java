package de.lardtom.propwash.join.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import de.lardtom.propwash.api.registry.ExtensionTab;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.apiimpl.ExtensionTabs;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartAvailability;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.DevBootstrap;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.client.ExampleConfigTab;
import de.lardtom.propwash.dev.example.client.ExampleScene;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.join.AddonInfo;
import de.lardtom.propwash.join.AddonSession;
import de.lardtom.propwash.join.ClientOnlyAddons;
import de.lardtom.propwash.join.JoinCheck;
import de.lardtom.propwash.join.JoinInfo;
import de.lardtom.propwash.join.JoinReport;
import de.lardtom.propwash.join.JoinText;
import de.lardtom.propwash.join.LocalJoinInfo;
import de.lardtom.propwash.join.PartRef;
import de.lardtom.propwash.join.SessionItems;
import de.lardtom.propwash.network.PartsManifestPayload;
import de.lardtom.propwash.parts.AddonItems;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class AddonSessionSelfTests {
    private static final String PREFIX = "addon-sitzung: ";

    private AddonSessionSelfTests() {
    }

    public static void register() {
        if (!JoinTestEnv.exampleAddon()) {
            return;
        }
        SelfTest.clientStep(PREFIX + "Beispiel-Addon nur beim Client: Teile, Tabs, Presets, Lektionen und Werkbank inaktiv, Hinweis", 60,
                AddonSessionSelfTests::inactiveStep);
        SelfTest.clientStep(PREFIX + "inaktive Items bleiben aus Kreativ-Paketen und Importen heraus", 40, AddonSessionSelfTests::guardStep);
        SelfTest.clientStep(PREFIX + "Trennen macht alles wieder aktiv", 20, AddonSessionSelfTests::clearStep);
        if (Screenshots.enabled()) {
            Screenshots.step(PREFIX + "Bildschirmfoto Hinweis nach dem Beitritt", "addon_inactive_toast", 30, new ToastScene());
            Screenshots.step(PREFIX + "Bildschirmfoto Übersicht mit inaktivem Add-on", "join_addon_inactive", 10, new InactiveReportScene());
            SelfTest.switchStep(PREFIX + "Sprache Deutsch", PREFIX + "Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
            Screenshots.step(PREFIX + "Bildschirmfoto Hinweis nach dem Beitritt deutsch", "addon_inactive_toast_de", 30, new ToastScene());
            SelfTest.clientStep(PREFIX + "Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        }
    }

    public static ClientOnlyAddons exampleOnlyHere() {
        JoinInfo local = LocalJoinInfo.get();
        List<AddonInfo> serverAddons = new ArrayList<>(local.addons());
        serverAddons.removeIf(addon -> addon.modId().equals(ExampleAddon.NAMESPACE));
        JoinInfo server = new JoinInfo(local.propwashVersion(), local.protocol(), local.builtinFingerprint(), serverAddons);
        List<PartRef> clientParts = JoinCheck.parts(PartsManifestPayload.local());
        List<PartRef> serverParts = new ArrayList<>(clientParts);
        serverParts.removeIf(part -> part.namespace().equals(ExampleAddon.NAMESPACE));
        return ClientOnlyAddons.compute(server, local, serverParts, clientParts);
    }

    private static StepResult inactiveStep(SelfTest.StepContext ctx) {
        boolean showcase = ExampleAddon.showcase();
        ExampleAddon.showcase(true);
        try {
            return inactiveChecks(ctx);
        } finally {
            ExampleAddon.showcase(showcase);
        }
    }

    private static StepResult inactiveChecks(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return StepResult.fail("keine Welt");
        }
        Optional<ExtensionTab> exampleTab = ExtensionTabs.INSTANCE.all().stream().filter(tab -> tab.namespace().equals(ExampleAddon.NAMESPACE)).findFirst();
        if (exampleTab.isEmpty() || ContentTab.extensionContents(exampleTab.get()).isEmpty()) {
            return StepResult.fail("Kreativ-Tab des Beispiel-Addons fehlt schon vorher");
        }
        int extensionsBefore = ExampleExtensions.count();
        boolean presetBefore = ApiExtensions.registeredPresets().stream().anyMatch(preset -> preset.id().equals(ExampleAddon.PRESET_XCLASS));
        Scene scene = GuideScenes.byId(ExampleScene.ID);
        boolean sceneListed = GuideScenes.all().stream().anyMatch(s -> s.id().equals(ExampleScene.ID));
        boolean configTab = ConfigTabs.create().stream().anyMatch(tab -> tab.id().equals(ExampleConfigTab.ID));
        if (PartIndex.byId(ExampleAddon.MOTOR_2806).isEmpty() || !presetBefore || extensionsBefore == 0 || scene == null || !sceneListed) {
            return StepResult.fail("Beispiel-Addon vorher nicht vollständig: Teil " + PartIndex.byId(ExampleAddon.MOTOR_2806).isPresent() + ", Preset "
                    + presetBefore + ", Werkbank " + extensionsBefore + ", Lektion " + (scene != null) + "/" + sceneListed);
        }
        ClientOnlyAddons inactive = exampleOnlyHere();
        if (!inactive.covers(ExampleAddon.NAMESPACE) || inactive.addons().stream().noneMatch(addon -> addon.modId().equals(ExampleAddon.NAMESPACE))) {
            return StepResult.fail("Beispiel-Addon nicht als nur beim Client erkannt: " + inactive);
        }
        AddonSession.activate(inactive);
        try {
            List<String> problems = new ArrayList<>();
            if (PartIndex.byId(ExampleAddon.MOTOR_2806).isPresent() || PartIndex.registeredById(ExampleAddon.MOTOR_2806).isEmpty()) {
                problems.add("Teile-Register filtert nicht");
            }
            for (PartInfo part : PartIndex.all()) {
                if (part.id().getNamespace().equals(ExampleAddon.NAMESPACE)) {
                    problems.add("Teil sichtbar: " + part.id());
                    break;
                }
            }
            if (PartIndex.chemistry(ExampleAddon.CHEMISTRY_LIHV).isPresent()) {
                problems.add("Zellchemie sichtbar");
            }
            for (ItemStack stack : ContentTab.contents()) {
                if (!SessionItems.available(stack)) {
                    problems.add("Haupt-Tab zeigt " + stack.getHoverName().getString());
                    break;
                }
            }
            if (!ContentTab.extensionContents(exampleTab.get()).isEmpty()) {
                problems.add("eigener Kreativ-Tab nicht leer");
            }
            if (ApiExtensions.registeredPresets().stream().anyMatch(preset -> preset.id().getNamespace().equals(ExampleAddon.NAMESPACE))
                    || PropwashApi.builds().presets().stream().map(DronePreset::id).anyMatch(ExampleAddon.PRESET_XCLASS::equals)) {
                problems.add("Preset sichtbar");
            }
            if (ExampleExtensions.count() != 0) {
                problems.add("Werkbank-Erweiterungen sichtbar: " + ExampleExtensions.count());
            }
            if (GuideScenes.byId(ExampleScene.ID) != null
                    || GuideScenes.all().stream().anyMatch(s -> s.id().startsWith(ExampleAddon.NAMESPACE + ":"))) {
                problems.add("Lektion sichtbar");
            }
            if (ConfigTabs.create().stream().anyMatch(tab -> tab.id().startsWith(ExampleAddon.NAMESPACE + ":"))
                    || PartAvailability.activeNamespacedId(ExampleConfigTab.ID)) {
                problems.add("Konfigurator-Tab sichtbar (vorher angemeldet: " + configTab + ")");
            }
            if (PropwashClientApi.isAddonActive(ExampleAddon.NAMESPACE) || !PropwashClientApi.isAddonActive("propwash")) {
                problems.add("isAddonActive falsch");
            }
            String creative = creativeProblem(mc, exampleTab.get(), true);
            if (creative != null) {
                problems.add(creative);
            }
            JoinClient.announce(mc);
            ThemedToast toast = ThemedToast.find(mc.gui.toastManager(), JoinClient.SESSION_TOAST);
            if (toast == null || toast.message() == null || !toast.message().getString().contains(DevBootstrap.EXAMPLE_NAME)) {
                problems.add("kein Hinweis nach dem Beitritt: " + (toast == null ? "-" : toast.message()));
            } else {
                ctx.log("Hinweis: " + toast.title().getString() + " – " + toast.message().getString());
                toast.hide();
            }
            ctx.log("inaktiv: " + inactive.sortedNamespaces() + ", " + inactive.parts() + " Teile, Werkbank vorher " + extensionsBefore);
            return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
        } finally {
            AddonSession.clear();
        }
    }

    private static StepResult guardStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return StepResult.fail("keine Welt");
        }
        Item motor = AddonItems.INSTANCE.item(ExampleAddon.MOTOR_2806).orElse(null);
        if (motor == null) {
            return StepResult.fail("Beispiel-Motor ohne Item");
        }
        ItemStack drone = PropwashApi.builds().createDrone(ExampleAddon.xclass());
        AddonSession.activate(exampleOnlyHere());
        try {
            List<String> problems = new ArrayList<>();
            ItemStack stack = new ItemStack(motor);
            if (!BuiltInRegistries.ITEM.getKey(motor).getNamespace().equals(ExampleAddon.NAMESPACE) || SessionItems.available(stack)) {
                problems.add("Item gilt als verfügbar");
            }
            String tooltip = tooltip(mc, stack);
            if (!tooltip.contains(Component.translatable("propwash.join.session.unavailable").getString())) {
                problems.add("Tooltip ohne Hinweis: " + tooltip);
            }
            String droneTooltip = tooltip(mc, drone);
            if (SessionItems.unavailable(drone).isEmpty() || !droneTooltip.contains(ExampleAddon.NAMESPACE + ":")) {
                problems.add("Drohne mit Beispiel-Teilen ohne Hinweis: " + droneTooltip);
            }
            int slot = emptyHotbarSlot(mc);
            if (slot < 0) {
                ctx.log("kein freier Platz in der Schnellleiste, Kreativ-Prüfung übersprungen");
            } else {
                mc.player.getInventory().setItem(slot, stack.copy());
                mc.gameMode.handleCreativeModeItemAdd(stack.copy(), 36 + slot);
                if (!mc.player.getInventory().getItem(slot).isEmpty()) {
                    problems.add("inaktives Item blieb in der Schnellleiste");
                    mc.player.getInventory().setItem(slot, ItemStack.EMPTY);
                }
                if (ThemedToast.find(mc.gui.toastManager(), JoinClient.BLOCKED_TOAST) == null) {
                    problems.add("kein Hinweis beim Aussortieren");
                }
            }
            CompletableFuture<ImportResult> result = PropwashClientApi.sendCreateDrone(ExampleAddon.xclass());
            if (!result.isDone()) {
                problems.add("Import ging an den Server");
            } else {
                ImportResult value = result.join();
                if (value.status() != ImportStatus.UNKNOWN_PARTS || value.missingParts().isEmpty()
                        || value.missingParts().stream().anyMatch(id -> !id.getNamespace().equals(ExampleAddon.NAMESPACE))) {
                    problems.add("Import-Ergebnis " + value);
                }
            }
            return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
        } finally {
            AddonSession.clear();
            ThemedToast blocked = ThemedToast.find(mc.gui.toastManager(), JoinClient.BLOCKED_TOAST);
            if (blocked != null) {
                blocked.hide();
            }
        }
    }

    private static StepResult clearStep(SelfTest.StepContext ctx) {
        boolean showcase = ExampleAddon.showcase();
        ExampleAddon.showcase(true);
        try {
            return clearChecks();
        } finally {
            ExampleAddon.showcase(showcase);
        }
    }

    private static StepResult clearChecks() {
        Minecraft mc = Minecraft.getInstance();
        AddonSession.activate(exampleOnlyHere());
        AddonSession.clear();
        Optional<ExtensionTab> exampleTab = ExtensionTabs.INSTANCE.all().stream().filter(tab -> tab.namespace().equals(ExampleAddon.NAMESPACE)).findFirst();
        List<String> problems = new ArrayList<>();
        if (PartIndex.byId(ExampleAddon.MOTOR_2806).isEmpty()) {
            problems.add("Teil fehlt");
        }
        if (exampleTab.isEmpty() || ContentTab.extensionContents(exampleTab.get()).isEmpty()) {
            problems.add("Kreativ-Tab leer");
        }
        if (ApiExtensions.registeredPresets().stream().noneMatch(preset -> preset.id().equals(ExampleAddon.PRESET_XCLASS))) {
            problems.add("Preset fehlt");
        }
        if (ExampleExtensions.count() == 0 || GuideScenes.byId(ExampleScene.ID) == null || !PartAvailability.activeNamespacedId(ExampleConfigTab.ID)) {
            problems.add("Werkbank, Lektion oder Konfigurator-Tab fehlt");
        }
        if (exampleTab.isPresent() && mc.player != null) {
            String creative = creativeProblem(mc, exampleTab.get(), false);
            if (creative != null) {
                problems.add(creative);
            }
        }
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }

    public static String sessionReport(Minecraft mc) {
        ClientOnlyAddons current = AddonSession.current();
        StringBuilder out = new StringBuilder();
        out.append("session_inactive=").append(String.join(",", current.sortedNamespaces())).append('\n');
        out.append("session_addons=").append(String.join(",", current.names())).append('\n');
        out.append("session_parts=").append(current.parts()).append('\n');
        int parts = 0;
        for (PartInfo part : PartIndex.all()) {
            if (current.covers(part.id().getNamespace())) {
                parts++;
            }
        }
        out.append("visible_inactive_parts=").append(parts).append('\n');
        int scenes = 0;
        for (Scene scene : GuideScenes.all()) {
            int colon = scene.id().indexOf(':');
            if (colon > 0 && current.covers(scene.id().substring(0, colon))) {
                scenes++;
            }
        }
        out.append("visible_inactive_scenes=").append(scenes).append('\n');
        int extensions = 0;
        for (WorkbenchExtension extension : PropwashClientApi.workbenchExtensions()) {
            if (current.covers(extension.id().getNamespace())) {
                extensions++;
            }
        }
        out.append("visible_inactive_workbench=").append(extensions).append('\n');
        int configTabs = 0;
        for (ConfigTab tab : ConfigTabs.create()) {
            int colon = tab.id().indexOf(':');
            if (colon > 0 && current.covers(tab.id().substring(0, colon))) {
                configTabs++;
            }
        }
        out.append("visible_inactive_config_tabs=").append(configTabs).append('\n');
        int creative = -1;
        if (mc.player != null && mc.level != null) {
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
            creative = 0;
            for (ItemStack stack : CreativeModeTabs.searchTab().getDisplayItems()) {
                if (!SessionItems.available(stack)) {
                    creative++;
                }
            }
            int tabs = 0;
            for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
                Identifier key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
                String path = key == null ? "" : key.getPath();
                if (key != null && (current.covers(key.getNamespace())
                        || path.startsWith(ExtensionTabs.PATH_PREFIX) && current.covers(path.substring(ExtensionTabs.PATH_PREFIX.length())))) {
                    tabs++;
                }
            }
            out.append("visible_inactive_tabs=").append(tabs).append('\n');
        }
        out.append("visible_inactive_creative=").append(creative).append('\n');
        ThemedToast toast = ThemedToast.find(mc.gui.toastManager(), JoinClient.SESSION_TOAST);
        out.append("toast=").append(toast == null ? "-" : toast.title().getString() + " – " + (toast.message() == null ? "" : toast.message().getString()))
                .append('\n');
        return out.toString();
    }

    private static @Nullable String creativeProblem(Minecraft mc, ExtensionTab tab, boolean inactive) {
        if (mc.player == null || mc.level == null) {
            return "keine Welt";
        }
        CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
        CreativeModeTab creativeTab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(tab.tabId());
        if (creativeTab == null) {
            return "Kreativ-Tab " + tab.tabId() + " nicht im Register";
        }
        boolean listed = CreativeModeTabs.tabs().contains(creativeTab);
        int inSearch = 0;
        for (ItemStack stack : CreativeModeTabs.searchTab().getDisplayItems()) {
            if (exampleContent(stack)) {
                inSearch++;
            }
        }
        if (inactive && (listed || inSearch > 0)) {
            return "Kreativ-Inventar zeigt das Beispiel-Addon (Tab " + listed + ", Suche " + inSearch + ")";
        }
        if (!inactive && (!listed || inSearch == 0)) {
            return "Kreativ-Inventar ohne Beispiel-Addon nach dem Trennen (Tab " + listed + ", Suche " + inSearch + ")";
        }
        return null;
    }

    private static boolean exampleContent(ItemStack stack) {
        if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(ExampleAddon.NAMESPACE)) {
            return true;
        }
        DroneBuild build = stack.get(CoreComponents.DRONE_BUILD.get());
        return build != null && build.parts().stream().anyMatch(part -> part.id().getNamespace().equals(ExampleAddon.NAMESPACE));
    }

    private static int emptyHotbarSlot(Minecraft mc) {
        for (int i = 8; i >= 0; i--) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static String tooltip(Minecraft mc, ItemStack stack) {
        StringBuilder out = new StringBuilder();
        for (Component line : stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)) {
            out.append(line.getString()).append(" | ");
        }
        return out.toString();
    }

    private static final class ExampleExtensions {
        static int count() {
            int count = 0;
            for (WorkbenchExtension extension : PropwashClientApi.workbenchExtensions()) {
                if (extension.id().getNamespace().equals(ExampleAddon.NAMESPACE)) {
                    count++;
                }
            }
            return count;
        }
    }

    public static JoinReport inactiveReport() {
        JoinInfo local = LocalJoinInfo.get();
        AddonInfo jmp = new AddonInfo("justmoreparts", "Propwash: Just More Parts", "1.1.0", "https://modrinth.com/mod/propwash-just-more-parts",
                List.of());
        List<AddonInfo> clientAddons = new ArrayList<>(local.addons());
        clientAddons.removeIf(addon -> addon.modId().equals(jmp.modId()));
        JoinInfo server = new JoinInfo(local.propwashVersion(), local.protocol(), local.builtinFingerprint(), List.of(jmp));
        JoinInfo client = new JoinInfo(local.propwashVersion(), local.protocol(), local.builtinFingerprint(), clientAddons);
        List<PartRef> serverParts = new ArrayList<>();
        for (String id : List.of("m1404_3800", "m2207_1950", "p5_1_3b")) {
            serverParts.add(new PartRef("motor", jmp.modId() + ":" + id, "1"));
        }
        List<PartRef> clientParts = new ArrayList<>();
        for (String id : List.of("m2806_1300", "xclass_13", "p13_7_2b_carbon")) {
            clientParts.add(new PartRef("motor", ExampleAddon.NAMESPACE + ":" + id, "1"));
        }
        return JoinReport.compute(server, client, serverParts, clientParts);
    }

    private static final class InactiveReportScene implements Screenshots.Scene {
        private boolean scrolled;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            JoinReport report = inactiveReport();
            if (!report.blocking() || report.lines(false).stream().noneMatch(line -> line.inactive() == 3)) {
                return StepResult.fail("Beispielbericht falsch: " + report);
            }
            mc.gui.setScreen(new JoinMismatchScreen(new TitleScreen(), report, JoinText.disconnect(report), true));
            Screenshots.parkMouse(mc);
            this.scrolled = false;
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!this.scrolled && mc.gui.screen() instanceof JoinMismatchScreen screen) {
                this.scrolled = true;
                screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0.0, -20.0);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }

    private static final class ToastScene implements Screenshots.Scene {
        private boolean shown;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(null);
            AddonSession.activate(exampleOnlyHere());
            this.shown = false;
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            if (!this.shown) {
                this.shown = true;
                JoinClient.announce(Minecraft.getInstance());
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            AddonSession.clear();
            Minecraft.getInstance().gui.toastManager().clear();
        }
    }
}
