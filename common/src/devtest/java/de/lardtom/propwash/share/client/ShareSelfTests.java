package de.lardtom.propwash.share.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.content.client.DroneWorkbenchScreen;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.client.ExampleAddonClient;
import de.lardtom.propwash.platform.ModMeta;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.share.AddonDirectory;
import de.lardtom.propwash.share.MissingAddons;
import de.lardtom.propwash.share.NamespaceReport;
import de.lardtom.propwash.share.Pw3Carry;
import de.lardtom.propwash.share.Pw3Codec;
import de.lardtom.propwash.share.ShareCommands;
import de.lardtom.propwash.share.ShareContent;
import de.lardtom.propwash.share.ShareConvert;
import de.lardtom.propwash.share.ShareDecoded;
import de.lardtom.propwash.share.ShareDevTables;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareService;
import de.lardtom.propwash.share.ShareStatus;
import de.lardtom.propwash.share.ShareTable;
import de.lardtom.propwash.share.ShareTableJson;
import de.lardtom.propwash.share.ShareTables;
import de.lardtom.propwash.share.ShareVectors;
import de.lardtom.propwash.share.Substitutes;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ShareSelfTests {
    public static final String GROUP = "share: ";
    public static final String P1 = ShareVectors.P1;
    public static final String P2_JMP = ShareVectors.P2_JMP;
    public static final String P8_FREE_LINK = ShareVectors.P8_FREE_LINK;
    public static final String P10_CHECKSUM = ShareVectors.P10_CHECKSUM;
    public static final String PW1_A = ShareVectors.PW1_A;
    public static final String PW1_A_PW3 = ShareVectors.PW1_A_PW3;
    public static final String PW2_NAMED = ShareVectors.PW2_NAMED;
    public static final String PW2_NAMED_PW3 = ShareVectors.PW2_NAMED_PW3;
    public static final String MISSING_NAMESPACE = ShareVectors.MISSING_NAMESPACE;
    public static final String MISSING_SLUG = ShareVectors.MISSING_SLUG;
    public static final String MISSING_VERSION = ShareVectors.MISSING_VERSION;

    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String SERVER = "server";
    private static final String CODE = "code";
    private static final String SAVED = "saved";
    private static final String SCREEN = "screen";
    private static final String SCALE = "scale";
    private static final int REQUEST_GAP_TICKS = 14;
    private static final AtomicReference<String> SERVER_ERROR = new AtomicReference<>();
    private static final AtomicInteger COMMAND_RESULT = new AtomicInteger();

    private ShareSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Tabellen geladen – Propwash und Beispiel-Add-on (deprecated, replaced_by, fallback, ohne Kurzkennung), keine Meldungen "
                + "im strikten Modus", 40, ShareSelfTests::tablesStep);
        SelfTest.clientStep(GROUP + "Werkbank-Knopf, Konfigurator-Tab und Flugschul-Szene angemeldet", 200, ShareSelfTests::registrationStep);
        SelfTest.clientStep(GROUP + "Werkbank → Code → Import in die Werkbank (Kreativ, kleinste GUI)", 1400, ShareSelfTests::workbenchStep);
        SelfTest.clientStep(GROUP + "Werkbank-Kopf – Knopf eines Add-ons bleibt neben „Sharecode“ sichtbar, „Sharecode“ weicht auf „Code“ und </> aus", 500,
                ShareSelfTests::addonHeaderStep);
        SelfTest.clientStep(GROUP + "alter Code – PW1 und PW2 mit Hinweis, „Als PW3 kopieren“ ergibt die Golden-Umrechnung", 400, ShareSelfTests::legacyStep);
        SelfTest.clientStep(GROUP + "fehlendes Add-on – propwash_missing mit Modrinth-Link und Kurzkennung 2 ohne JMP, Einkaufsliste mit „braucht …“, "
                + "„Mit Ersatzteilen erzeugen“ legt eine Drohne ins Inventar", 1800, ShareSelfTests::missingStep);
        SelfTest.clientStep(GROUP + "Zubehör eines fehlenden Add-ons – Gimbal vorn, Box oben und Licht unten ohne passenden Ersatz fallen weg, Rahmen bis Akku "
                + "kommen aus dem fallback, Vorschau und „Mit Ersatzteilen erzeugen“ ohne das Zubehör", 1200, accessoryWithoutAddonStep());
        SelfTest.clientStep(GROUP + "Prüfsumme falsch – Banner, Teile gesperrt, „Teile trotzdem übernehmen“ wirkt", 1200, ShareSelfTests::mismatchStep);
        SelfTest.clientStep(GROUP + "freie Adresse – Linkart 3 nur als Text mit Hinweis, nicht klickbar", 400, ShareSelfTests::freeLinkStep);
        SelfTest.clientStep(GROUP + "Konfigurator-Tab – Export, Import, Tuning und Lack auf die gehaltene Drohne", 1200, ShareSelfTests::tabStep);
        SelfTest.clientStep(GROUP + "Befehle export, info, give, convert und import … substitutes", 300, ShareSelfTests::commandStep);
        SelfTest.clientStep(GROUP + "ein Sharecode-Knopf, mit Just More Parts PW3-Export einer JMP-Drohne gleich Vektor P2", 300,
                ShareSelfTests::shareButtonStep);
        SelfTest.clientStep(GROUP + "Scrollleiste der Import-Liste lässt sich ziehen (kleinste GUI)", 200, ShareSelfTests::scrollbarStep);
        SelfTest.clientStep(GROUP + "aufräumen", 40, ShareSelfTests::cleanupStep);
        ShareScreenshots.register();
    }

    public static DroneData freestyle() {
        return new DroneData(ShareScene.freestyle(), PaintSpec.DEFAULT.with("frame", 0x1B1D22).with("motors", 0xE8B622),
                new TuneSpec(Map.of("rates.roll.rcRate", 1.25F, "pid.roll.p", 47.0F)), Optional.of("Propwash Selbsttest"));
    }

    public static String freestyleCode() {
        return ShareService.encode(freestyle(), ShareLayer.DEFAULT_EXPORT, true, Optional.empty());
    }

    private static StepResult tablesStep(SelfTest.StepContext ctx) {
        List<String> errors = new ArrayList<>();
        boolean example = ShareDevTables.ensure();
        ShareTables tables = ShareTables.current();
        if (tables.installedTable(ShareTables.PROPWASH).isEmpty()) {
            errors.add("Tabelle von Propwash fehlt");
        }
        if (ShareDevTables.active()) {
            Optional<ShareTable> table = tables.installedTable(ShareDevTables.EXAMPLE);
            if (!example || table.isEmpty()) {
                errors.add("Tabelle des Beispiel-Add-ons fehlt");
            } else {
                ShareTable t = table.get();
                if (t.shortId().isPresent() && t.shortId().getAsInt() != 0 || tables.shortIdOf(ShareDevTables.EXAMPLE) != 0) {
                    errors.add("Beispiel-Add-on hat eine Kurzkennung");
                }
                Identifier old = Identifier.fromNamespaceAndPath(ShareDevTables.EXAMPLE, "old_stretch_5");
                Identifier current = Identifier.fromNamespaceAndPath(ShareDevTables.EXAMPLE, "race_stretch_5");
                if (!tables.replacement(old).equals(current)) {
                    errors.add("replaced_by wirkt nicht: " + tables.replacement(old));
                }
                if (!tables.fallback(current).equals(Optional.of(Constants.id("race_5")))) {
                    errors.add("fallback fehlt: " + tables.fallback(current));
                }
                ShareContent content = ShareContent.of(ShareScene.freestyle().withPart(PartKind.FRAME, old));
                ShareDecoded decoded = ShareService.decode(Pw3Codec.encode(content, tables));
                if (!decoded.ok() || !decoded.require().build().frame().equals(current) || decoded.renamed().isEmpty()) {
                    errors.add("gestrichenes Teil nicht umbenannt: " + decoded.status() + " " + decoded.renamed());
                }
            }
        }
        if (!tables.problems().isEmpty()) {
            errors.add("Meldungen: " + tables.problems());
        }
        ctx.log("Tabellen: " + tables.installedTables().keySet() + ", Verzeichnis " + tables.directory().addons().size() + " Add-ons, Meldungen "
                + tables.problems().size() + ", Neuladen mit Beispiel " + ShareDevTables.installs());
        return StepResult.check(errors.isEmpty(), String.join("; ", errors));
    }

    private static StepResult registrationStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            List<String> errors = new ArrayList<>();
            if (PropwashClientApi.workbenchExtensions().stream().noneMatch(e -> e.id().equals(ShareWorkbenchExtension.ID))) {
                errors.add("Werkbank-Erweiterung fehlt");
            }
            ShareConfigTab tab = new ShareConfigTab();
            if (!ShareConfigTab.ID.equals(tab.id()) || tab.order() != ShareConfigTab.ORDER || tab.readOnly()) {
                errors.add("Konfigurator-Tab falsch: " + tab.id());
            }
            for (String key : ShareScene.create().translationKeys()) {
                if (!Language.getInstance().has(key)) {
                    errors.add("Szenentext fehlt: " + key);
                }
            }
            List<String> keys = new ArrayList<>(ShareWorkbenchExtension.LABEL_KEYS);
            keys.addAll(List.of("propwash.share.title", "propwash.share.export.toggle.name.none", ShareStatus.CHECKSUM.translationKey(),
                    ShareLayer.OSD.translationKey(), "propwash.share.addons.title", "propwash.share.link.modrinth"));
            for (String key : keys) {
                if (!Language.getInstance().has(key)) {
                    errors.add("Text fehlt: " + key);
                }
            }
            for (Identifier icon : ShareLines.ICONS) {
                if (mc.getResourceManager().getResource(Identifier.fromNamespaceAndPath(icon.getNamespace(), "textures/gui/sprites/" + icon.getPath()
                        + ".png")).isEmpty()) {
                    errors.add("Symbol fehlt: " + icon);
                }
            }
            if (!errors.isEmpty()) {
                return StepResult.fail(String.join("; ", errors));
            }
            if (!PropwashClientApi.openFlightSchoolScene(ShareScene.ID, null)) {
                return StepResult.fail("Flugschul-Szene " + ShareScene.ID + " lässt sich nicht öffnen");
            }
            state.put(PHASE, 1);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        if (ctx.tick() - (int) state.get(SINCE) < 40) {
            return StepResult.pending();
        }
        boolean themed = ThemedScreen.active();
        String screen = String.valueOf(mc.gui.screen());
        mc.gui.setScreen(null);
        return StepResult.check(themed, "Flugschul-Szene nicht im Theme: " + screen);
    }

    private static StepResult workbenchStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                SERVER_ERROR.set(null);
                state.put(SERVER, ctx.onServer(server -> openBench(server, GameType.CREATIVE)));
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = awaitServer(ctx);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                WorkbenchContext context = ShareWorkbenchExtension.lastContext();
                if (context == null || context.screen() != mc.gui.screen()) {
                    return timeout(ctx, 400, "Werkbank ruft die Sharecode-Erweiterung nicht auf: " + mc.gui.screen());
                }
                if (!context.creative()) {
                    return StepResult.fail("Selbsttest-Welt nicht im Kreativmodus");
                }
                ctx.log("Werkbank-Kopf: " + context.headerSpace() + " px frei, Knöpfe " + (context.screen() instanceof DroneWorkbenchScreen bench
                        ? headerLabels(bench) : List.of()));
                ShareScreen.open(context, ShareScreen.Mode.IMPORT, false);
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                ShareScreen screen = ShareScreen.active();
                if (screen == null || mc.gui.screen() != screen || screen.frames() < 3) {
                    return timeout(ctx, 600, "Sharecode-Fenster erscheint nicht: " + mc.gui.screen());
                }
                if (!ThemedScreen.active()) {
                    return StepResult.fail("Sharecode-Fenster nicht im Theme");
                }
                String code = freestyleCode();
                if (!code.startsWith("PW3-")) {
                    return StepResult.fail("Export schreibt kein PW3: " + code);
                }
                state.put(CODE, code);
                screen.setInput(code);
                if (!screen.state().decoded().partsKnown() || !screen.state().buildable()) {
                    return StepResult.fail("eingefügter Code nicht übernehmbar: " + screen.state().decoded().status());
                }
                ShareSettings.setApply(EnumSet.of(ShareLayer.PARTS, ShareLayer.PAINT, ShareLayer.TUNE));
                CompletableFuture<ImportResult> future = screen.apply();
                if (future == null) {
                    return StepResult.fail("Übernehmen schickt keine Anfrage: " + screen.chosenLayers());
                }
                state.put(FUTURE, future);
                state.put(PHASE, 3);
                return StepResult.pending();
            }
            case 3 -> {
                ImportResult result = result(state);
                if (result == null) {
                    return timeout(ctx, 900, "keine Antwort auf den Import in die Werkbank");
                }
                if (result.status() != ImportStatus.APPLIED) {
                    return StepResult.fail("Import in die Werkbank: " + result);
                }
                ShareScreen screen = ShareScreen.active();
                if (screen == null) {
                    return StepResult.fail("Sharecode-Fenster nach dem Import geschlossen");
                }
                screen.switchMode(ShareScreen.Mode.EXPORT);
                state.put(PHASE, 4);
                return StepResult.pending();
            }
            case 4 -> {
                ShareScreen screen = ShareScreen.active();
                if (screen == null || screen.mode() != ShareScreen.Mode.EXPORT || screen.exportCode().isEmpty()) {
                    return timeout(ctx, 1200, "Werkbank liefert nach dem Import keine fertige Drohne");
                }
                ShareDecoded decoded = ShareService.decode(screen.exportCode());
                DroneData expected = freestyle();
                if (!decoded.ok() || !"PW3".equals(decoded.format()) || !decoded.require().build().equals(expected.build())
                        || !decoded.require().paint().equals(Optional.of(expected.paint()))) {
                    return StepResult.fail("Export der Werkbank passt nicht zum Import: " + decoded.status() + " " + decoded.content());
                }
                Float roll = decoded.require().tune().map(t -> t.values().get("rates.roll.rcRate")).orElse(null);
                if (roll == null || Math.abs(roll - 1.25F) > 1.0E-4F) {
                    return StepResult.fail("Tuning nicht in der Werkbank-Drohne: " + decoded.require().tune());
                }
                if (!String.join("", screen.exportRows()).equals(screen.exportCode()) || screen.exportRows().stream().anyMatch(r -> r.contains(" "))) {
                    return StepResult.fail("Code-Kasten zeigt den Code nicht unverändert in Zeilen: " + screen.exportRows());
                }
                String small = smallGui(ctx, mc, screen);
                if (small != null) {
                    return StepResult.fail(small);
                }
                if (!screen.copy() || !mc.keyboardHandler.getClipboard().equals(screen.exportCode())) {
                    return StepResult.fail("Kopieren in die Zwischenablage klappt nicht");
                }
                screen.onClose();
                state.put(PHASE, 5);
                return StepResult.pending();
            }
            default -> {
                WorkbenchContext context = ShareWorkbenchExtension.lastContext();
                if (context == null || mc.gui.screen() != context.screen()) {
                    return timeout(ctx, 1300, "„Zurück“ führt nicht zur Werkbank: " + mc.gui.screen());
                }
                closeBench(ctx);
                return StepResult.pass();
            }
        }
    }

    private static StepResult addonHeaderStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            SERVER_ERROR.set(null);
            ExampleAddon.showcase(true);
            ExampleAddonClient.sizeButton(false);
            state.put(SERVER, ctx.onServer(server -> openBench(server, GameType.CREATIVE)));
            state.put(PHASE, 1);
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx);
        if (server.status() != StepResult.Status.PASS) {
            return server.status() == StepResult.Status.FAIL ? endHeader(ctx, server) : server;
        }
        WorkbenchContext share = ShareWorkbenchExtension.lastContext();
        WorkbenchContext example = ExampleAddonClient.lastContext();
        if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen) || share == null || share.screen() != screen) {
            StepResult wait = timeout(ctx, 400, "Werkbank ruft die Sharecode-Erweiterung nicht auf: " + mc.gui.screen());
            return wait.status() == StepResult.Status.FAIL ? endHeader(ctx, wait) : wait;
        }
        int space = share.headerSpace();
        Component full = Component.translatable(ShareWorkbenchExtension.LABEL_KEY);
        Component shortLabel = Component.translatable(ShareWorkbenchExtension.SHORT_KEY);
        if (phase == 1) {
            if (example == null || example.screen() != screen) {
                StepResult wait = timeout(ctx, 400, "Werkbank ruft den Knopf des Beispiel-Add-ons nicht auf");
                return wait.status() == StepResult.Status.FAIL ? endHeader(ctx, wait) : wait;
            }
            Component addon = Component.translatable("propwash_example.workbench.button");
            Component addonShort = Component.translatable("propwash_example.workbench.button.short");
            List<String> header = headerLabels(screen);
            ctx.log("Werkbank-Kopf mit Add-on: " + space + " px frei, Knöpfe " + header);
            int need = WorkbenchContext.ICON_BUTTON_SIZE + WorkbenchContext.BUTTON_GAP + example.buttonWidth(addonShort) + WorkbenchContext.BUTTON_GAP;
            if (space >= need && !header.contains(addon.getString()) && !header.contains(addonShort.getString())) {
                return endHeader(ctx, StepResult.fail("Knopf des Add-ons hinter „⋯“, obwohl er neben </> passt (" + space + " px, nötig " + need + "): "
                        + header));
            }
            if (!header.contains(full.getString()) && !header.contains(shortLabel.getString()) && !header.contains(iconLabel())) {
                return endHeader(ctx, StepResult.fail("Sharecode-Knopf fehlt im Werkbank-Kopf: " + header));
            }
            String overlap = headerOverlap(screen);
            if (overlap != null) {
                return endHeader(ctx, StepResult.fail(overlap));
            }
            ExampleAddon.showcase(false);
            screen.resize(screen.width, screen.height);
            state.put(PHASE, 2);
            return StepResult.pending();
        }
        List<String> header = headerLabels(screen);
        ctx.log("Werkbank-Kopf ohne Add-on: " + space + " px frei, Knöpfe " + header);
        String expected = space >= share.buttonWidth(full) + WorkbenchContext.BUTTON_GAP ? full.getString()
                : space >= share.buttonWidth(shortLabel) + WorkbenchContext.BUTTON_GAP ? shortLabel.getString() : iconLabel();
        if (!header.equals(List.of(expected))) {
            return endHeader(ctx, StepResult.fail("Sharecode-Knopf allein: erwartet " + expected + " bei " + space + " px, gezeigt " + header));
        }
        return endHeader(ctx, StepResult.pass());
    }

    private static StepResult endHeader(SelfTest.StepContext ctx, StepResult result) {
        ExampleAddon.showcase(false);
        ExampleAddonClient.sizeButton(true);
        closeBench(ctx);
        return result;
    }

    private static String iconLabel() {
        return "[" + ShareWorkbenchExtension.ICON + "]";
    }

    private static List<String> headerLabels(DroneWorkbenchScreen screen) {
        List<FlatButton> buttons = new ArrayList<>(screen.extensionButtons());
        buttons.sort(Comparator.comparingInt(FlatButton::getX));
        List<String> labels = new ArrayList<>();
        for (FlatButton button : buttons) {
            labels.add(button.icon() != null ? "[" + button.icon() + "]" : button.getMessage().getString());
        }
        return labels;
    }

    private static @Nullable String headerOverlap(DroneWorkbenchScreen screen) {
        List<FlatButton> buttons = new ArrayList<>(screen.extensionButtons());
        buttons.sort(Comparator.comparingInt(FlatButton::getX));
        int right = screen.titleEnd() + DroneWorkbenchScreen.TITLE_GAP;
        for (FlatButton button : buttons) {
            if (button.getX() < right) {
                return "Knöpfe im Werkbank-Kopf überlappen (Titel oder Nachbar): " + button.getMessage().getString() + " bei " + button.getX()
                        + ", frei ab " + right;
            }
            right = button.getX() + button.getWidth();
        }
        return null;
    }

    private static StepResult legacyStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            List<String> errors = new ArrayList<>();
            for (String[] pair : List.of(new String[]{PW1_A, PW1_A_PW3}, new String[]{PW2_NAMED, PW2_NAMED_PW3})) {
                ShareConvert.Conversion conversion = ShareConvert.toPw3(pair[0]);
                if (!conversion.ok() || !conversion.code().equals(Optional.of(pair[1]))) {
                    errors.add("Umrechnung " + pair[0].substring(0, 4) + ": " + conversion);
                }
            }
            if (!errors.isEmpty()) {
                return StepResult.fail(String.join("; ", errors));
            }
            state.put(SAVED, ShareSettings.lastInput());
            mc.gui.setScreen(ShareHarness.window(ShareHarness.context(Optional.empty(), true), ShareScreen.Mode.IMPORT));
            state.put(PHASE, 1);
            return StepResult.pending();
        }
        ShareScreen screen = ShareScreen.active();
        if (screen == null || mc.gui.screen() != screen || screen.frames() < 3) {
            return timeout(ctx, 200, "Sharecode-Fenster erscheint nicht: " + mc.gui.screen());
        }
        List<String> errors = new ArrayList<>();
        for (String[] pair : List.of(new String[]{PW1_A, PW1_A_PW3}, new String[]{PW2_NAMED, PW2_NAMED_PW3})) {
            String format = pair[0].substring(0, 3);
            screen.setInput(pair[0]);
            ShareDecoded decoded = screen.state().decoded();
            if (!decoded.ok() || !decoded.legacy() || !format.equals(decoded.format())) {
                errors.add(format + " nicht als alter Code gelesen: " + decoded.status() + " " + decoded.format());
                continue;
            }
            if (ShareLines.find(screen.infoLines(), ShareLines.TAG_LEGACY, true).isEmpty()) {
                errors.add(format + ": Hinweis auf PW3 fehlt");
            }
            Optional<ShareLines.Line> copy = ShareLines.find(screen.infoLines(), ShareLines.TAG_COPY_PW3, true);
            if (copy.isEmpty() || !copy.get().clickable()) {
                errors.add(format + ": „Als PW3 kopieren“ fehlt");
                continue;
            }
            mc.keyboardHandler.setClipboard("");
            copy.get().action().run();
            if (!mc.keyboardHandler.getClipboard().equals(pair[1])) {
                errors.add(format + ": „Als PW3 kopieren“ ergibt " + mc.keyboardHandler.getClipboard() + " statt " + pair[1]);
            }
        }
        screen.setInput((String) state.get(SAVED));
        mc.gui.setScreen(null);
        return StepResult.check(errors.isEmpty(), String.join("; ", errors));
    }

    private static StepResult missingStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                if (PropwashApi.parts().get(ShareVectors.jmpBuild().frame()).isPresent()) {
                    ctx.log("Just More Parts ist geladen: Fall Kurzkennung 2 ohne JMP übersprungen");
                }
                SERVER_ERROR.set(null);
                state.put(SERVER, ctx.onServer(server -> openBench(server, GameType.SURVIVAL)));
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = awaitServer(ctx);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                WorkbenchContext context = ShareWorkbenchExtension.lastContext();
                if (context == null || context.screen() != mc.gui.screen()) {
                    return timeout(ctx, 400, "Werkbank erscheint nicht: " + mc.gui.screen());
                }
                if (context.creative()) {
                    return timeout(ctx, 400, "Spieler nicht im Überlebensmodus");
                }
                ShareScreen.open(context, ShareScreen.Mode.IMPORT, false);
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                ShareScreen screen = ShareScreen.active();
                if (screen == null || mc.gui.screen() != screen || screen.frames() < 3) {
                    return timeout(ctx, 600, "Sharecode-Fenster erscheint nicht: " + mc.gui.screen());
                }
                List<String> errors = new ArrayList<>();
                String missing = ShareVectors.missingCode();
                screen.setInput(missing);
                state.put(PHASE, 3);
                state.put(SINCE, ctx.tick());
                state.put(CODE, missing);
                return errors.isEmpty() ? StepResult.pending() : StepResult.fail(String.join("; ", errors));
            }
            case 3 -> {
                ShareScreen screen = ShareScreen.active();
                if (screen == null) {
                    return StepResult.fail("Sharecode-Fenster geschlossen");
                }
                if (ctx.tick() - (int) state.get(SINCE) < 15) {
                    return StepResult.pending();
                }
                List<String> errors = new ArrayList<>();
                checkMissing(screen, MISSING_NAMESPACE, MissingAddons.MODRINTH + MISSING_SLUG, MISSING_VERSION, errors);
                if (screen.state().decoded().ok()) {
                    Identifier frame = screen.state().decoded().require().build().frame();
                    if (ShareLines.find(screen.infoLines(), ShareLines.TAG_SHOPPING + frame + ":needs", true).isEmpty()) {
                        errors.add("Einkaufsliste ohne „braucht …“ für " + frame + ": " + tags(screen.infoLines()));
                    }
                }
                if (!screen.addonHintVisible()) {
                    errors.add("Add-on-Hinweis nach dem Öffnen nicht ganz zu sehen");
                }
                Optional<ShareLines.Line> link = ShareLines.find(screen.infoLines(), ShareLines.TAG_LINK + MissingAddons.MODRINTH + MISSING_SLUG, true);
                if (link.isPresent()) {
                    link.get().action().run();
                    if (!(mc.gui.screen() instanceof ConfirmLinkScreen)) {
                        errors.add("Link-Knopf öffnet keinen Bestätigungsdialog: " + mc.gui.screen());
                    }
                    mc.gui.setScreen(screen);
                }
                if (PropwashApi.parts().get(ShareVectors.jmpBuild().frame()).isEmpty()) {
                    screen.setInput(P2_JMP);
                    checkMissing(screen, "justmoreparts", jmpModrinth(), "1.0.0", errors);
                    Identifier frame = ShareVectors.jmpBuild().frame();
                    String name = screen.state().unknownPart(frame).flatMap(MissingAddons.Part::name).orElse("");
                    if (name.isBlank()) {
                        errors.add("JMP-Teil ohne Namen aus dem Verzeichnis-Abzug");
                    }
                    if (ShareLines.find(screen.infoLines(), ShareLines.TAG_SHOPPING + frame + ":needs", true).isEmpty()) {
                        errors.add("Einkaufsliste ohne „braucht …“ für JMP: " + tags(screen.infoLines()));
                    }
                    if (!screen.state().substitutesFound()) {
                        errors.add("kein Ersatz für die JMP-Drohne");
                    }
                }
                if (!errors.isEmpty()) {
                    return StepResult.fail(String.join("; ", errors));
                }
                screen.onClose();
                closeBench(ctx);
                state.put(PHASE, 4);
                state.put(SINCE, ctx.tick());
                return StepResult.pending();
            }
            case 4 -> {
                if (ctx.tick() - (int) state.get(SINCE) < 10) {
                    return StepResult.pending();
                }
                SERVER_ERROR.set(null);
                state.put(SERVER, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.setGameMode(GameType.CREATIVE);
                    player.getInventory().clearContent();
                    player.containerMenu.broadcastChanges();
                }));
                state.put(PHASE, 5);
                return StepResult.pending();
            }
            case 5 -> {
                StepResult server = awaitServer(ctx);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (mc.player == null || !mc.player.hasInfiniteMaterials()) {
                    return timeout(ctx, 900, "Spieler nicht zurück im Kreativmodus");
                }
                if (!PropwashClientApi.openConfigTab(ShareConfigTab.ID, null)) {
                    return StepResult.fail("Tab propwash:sharecode lässt sich nicht öffnen");
                }
                state.put(PHASE, 6);
                return StepResult.pending();
            }
            case 6 -> {
                ShareConfigTab tab = ShareConfigTab.active();
                if (tab == null || tab.frames() < 3) {
                    return timeout(ctx, 1100, "Tab erscheint nicht: " + mc.gui.screen());
                }
                tab.setInput(ShareVectors.missingCode());
                Optional<ShareLines.Line> create = ShareLines.find(tab.detailLines(), ShareLines.TAG_CREATE, true);
                if (create.isEmpty()) {
                    return StepResult.fail("„Mit Ersatzteilen erzeugen“ fehlt im Tab: " + tags(tab.detailLines()));
                }
                CompletableFuture<ImportResult> future = tab.createSubstituted();
                if (future == null) {
                    return StepResult.fail("„Mit Ersatzteilen erzeugen“ schickt nichts: " + tab.state().substitutes().method());
                }
                state.put(FUTURE, future);
                state.put(PHASE, 7);
                return StepResult.pending();
            }
            default -> {
                ImportResult result = result(state);
                if (result == null) {
                    return timeout(ctx, 1700, "keine Antwort auf „Mit Ersatzteilen erzeugen“");
                }
                if (result.status() != ImportStatus.APPLIED) {
                    return StepResult.fail("„Mit Ersatzteilen erzeugen“: " + result);
                }
                Optional<DroneData> created = inventoryDrone(mc, data -> data.build().allParts().stream().noneMatch(id -> id.getNamespace()
                        .equals(MISSING_NAMESPACE)));
                if (created.isEmpty()) {
                    return timeout(ctx, 1750, "keine Drohne mit Ersatzteilen im Inventar");
                }
                ctx.log("Ersatz-Drohne im Inventar: " + created.get().build().frame() + ", Name " + created.get().name().orElse("–"));
                closeConfigurator(mc);
                return StepResult.pass();
            }
        }
    }

    private static final String MOUNTS = "propwash_mounts";
    private static final int MOUNTS_SHORT_ID = 90;
    private static final String MOUNTS_TABLE = "{\"format\": 1, \"namespace\": \"" + MOUNTS + "\", \"parts\": {"
            + "\"frame\": [{\"id\": \"cine_frame\", \"since\": \"1.0.0\", \"fallback\": \"propwash:long_range_7\"}],"
            + "\"stack\": [{\"id\": \"nav_stack\", \"since\": \"1.0.0\", \"fallback\": \"propwash:stack_30\"}],"
            + "\"motor\": [{\"id\": \"m4114_320\", \"since\": \"1.0.0\", \"fallback\": \"propwash:m2807_1300\"}],"
            + "\"prop\": [{\"id\": \"fold_9\", \"since\": \"1.0.0\", \"fallback\": \"propwash:p7_2b\"}],"
            + "\"video\": [{\"id\": \"cam_link\", \"since\": \"1.0.0\", \"fallback\": \"propwash:digital_hd\"}],"
            + "\"battery\": [{\"id\": \"smart_6s\", \"since\": \"1.0.0\", \"fallback\": \"propwash:b6s_4000_lion\"}],"
            + "\"accessory\": [{\"id\": \"belly_light\", \"since\": \"1.0.0\", \"fallback\": \"propwash:action_cam_light\"},"
            + " {\"id\": \"gimbal\", \"since\": \"1.0.0\", \"fallback\": \"propwash:action_cam_heavy\"},"
            + " {\"id\": \"top_box\", \"since\": \"1.0.0\"}]}}";
    private static final String SAVED_TABLES = "saved_tables";
    private static final String SAVED_SUBSTITUTES = "saved_substitutes";
    private static final String SAVED_INPUT = "saved_input";

    private static Identifier mounts(String path) {
        return Identifier.fromNamespaceAndPath(MOUNTS, path);
    }

    static BuildSpec mountsBuild() {
        return new BuildSpec(mounts("cine_frame"), mounts("nav_stack"), mounts("m4114_320"), mounts("fold_9"), mounts("cam_link"), mounts("smart_6s"),
                Map.of(AccessorySlot.FRONT, mounts("gimbal"), AccessorySlot.TOP, mounts("top_box"), AccessorySlot.BOTTOM, mounts("belly_light")));
    }

    static BuildSpec mountsStandIns() {
        return BuildSpec.of(Constants.id("long_range_7"), Constants.id("stack_30"), Constants.id("m2807_1300"), Constants.id("p7_2b"),
                Constants.id("digital_hd"), Constants.id("b6s_4000_lion"));
    }

    private static ShareTables withMountsInDirectory(ShareTables current, boolean installed) {
        ShareTable table = ShareTableJson.parse(MOUNTS, MOUNTS_TABLE).table().orElseThrow(() -> new IllegalStateException("Probe-Tabelle ungültig"));
        List<AddonDirectory.Addon> addons = new ArrayList<>(current.directory().addons());
        addons.add(new AddonDirectory.Addon(MOUNTS, MOUNTS_SHORT_ID, Map.of("en_us", "Propwash Mounts", "de_de", "Propwash-Halterungen"),
                Optional.of("propwash-mounts"), Optional.empty(), Optional.of("1.0.0"), Optional.of(table)));
        ShareTables.PartLookup parts = current.parts();
        ShareTables.Builder builder = ShareTables.builder().directory(new AddonDirectory(addons, current.directory().problems()));
        for (ShareTable t : current.installedTables().values()) {
            builder.installed(t);
            current.version(t.namespace()).ifPresent(v -> builder.version(t.namespace(), v));
            current.mod(t.namespace()).ifPresent(m -> builder.mod(t.namespace(), m));
        }
        if (installed) {
            builder.installed(table).version(MOUNTS, "1.0.0").parts(id -> id.getNamespace().equals(MOUNTS) ? table.find(id).map(ShareTable.Located::kind)
                    : parts.kind(id));
        } else {
            builder.parts(parts);
        }
        return builder.build();
    }

    private static SelfTest.ClientStep accessoryWithoutAddonStep() {
        return CovShareScript.create()
                .then("Verzeichnis-Eintrag " + MOUNTS + " ohne das Add-on", 40, (ctx, s, age) -> {
                    ShareTables current = ShareTables.current();
                    s.put(SAVED_TABLES, current);
                    s.put(SAVED_SUBSTITUTES, ShareSettings.substitutes());
                    s.put(SAVED_INPUT, ShareSettings.lastInput());
                    s.put(CODE, Pw3Codec.encode(new ShareContent(mountsBuild(), Optional.empty(), Optional.empty(), Optional.empty(),
                            Optional.of("Gimbal-Probe")), withMountsInDirectory(current, true)));
                    ShareTables.install(withMountsInDirectory(current, false));
                    ShareSettings.substitutes(true);
                    return StepResult.pass();
                })
                .server("Kreativ, Inventar leer", (server, out) -> {
                    ServerPlayer player = player(server);
                    player.closeContainer();
                    player.setGameMode(GameType.CREATIVE);
                    player.getInventory().clearContent();
                    player.containerMenu.broadcastChanges();
                })
                .then("Tab öffnen", 300, (ctx, s, age) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null || !mc.player.hasInfiniteMaterials()) {
                        return CovShareScript.wait(s, "Spieler nicht im Kreativmodus");
                    }
                    return PropwashClientApi.openConfigTab(ShareConfigTab.ID, null) ? StepResult.pass()
                            : StepResult.fail("Tab " + ShareConfigTab.ID + " lässt sich nicht öffnen");
                })
                .then("Import ohne Add-on", 300, (ctx, s, age) -> {
                    ShareConfigTab tab = ShareConfigTab.active();
                    if (tab == null || tab.frames() < 3) {
                        return CovShareScript.wait(s, "Tab erscheint nicht: " + Minecraft.getInstance().gui.screen());
                    }
                    tab.setInput((String) s.get(CODE));
                    ShareImportState state = tab.state();
                    List<String> errors = new ArrayList<>();
                    if (!state.decoded().ok()) {
                        return StepResult.fail("Code nicht lesbar: " + state.decoded().status());
                    }
                    BuildSpec wanted = mountsStandIns();
                    Substitutes.Result result = state.substitutes();
                    if (!state.substitutesFound() || result.method() != Substitutes.Method.FALLBACK || result.preset().isPresent()) {
                        errors.add("Ersatz nicht über fallback: " + result.method() + " " + result.preset() + " " + result.build());
                    }
                    if (!state.substituted().map(ShareContent::build).equals(Optional.of(wanted))) {
                        errors.add("Ersatz-Bauplan " + state.substituted().map(ShareContent::build) + " statt " + wanted);
                    }
                    if (!state.preview().map(DroneData::build).equals(Optional.of(wanted))) {
                        errors.add("Vorschau zeigt " + state.preview().map(DroneData::build) + " statt der Drohne ohne Zubehör");
                    }
                    String removed = Component.translatable("propwash.share.substitutes.removed").getString();
                    List<String> swaps = new ArrayList<>();
                    for (ShareLines.Line line : tab.detailLines()) {
                        if (line.tag().equals(ShareLines.TAG_SWAP)) {
                            swaps.add(line.text().getString());
                        }
                    }
                    for (Map.Entry<AccessorySlot, Identifier> entry : mountsBuild().accessories().entrySet()) {
                        String position = "accessory:" + entry.getKey().key();
                        boolean dropped = result.swaps().stream().anyMatch(swap -> swap.position().equals(position) && swap.from().equals(entry.getValue())
                                && swap.to().isEmpty());
                        if (!dropped) {
                            errors.add(entry.getValue() + " nicht weggelassen: " + result.swaps());
                        }
                        boolean listed = state.missing().stream().anyMatch(line -> line.key().equals(MOUNTS) && line.parts().stream().anyMatch(part -> part.id()
                                .equals(entry.getValue()) && part.kind() == PartKind.ACCESSORY));
                        if (!listed) {
                            errors.add(entry.getValue() + " fehlt in der Add-on-Zeile: " + state.missing());
                        }
                    }
                    if (swaps.stream().filter(text -> text.contains(removed)).count() != 3 || swaps.size() != 9) {
                        errors.add("Ersatzteil-Zeilen: " + swaps);
                    }
                    if (!errors.isEmpty()) {
                        return StepResult.fail(String.join("; ", errors));
                    }
                    CompletableFuture<ImportResult> future = tab.createSubstituted();
                    if (future == null) {
                        return StepResult.fail("„Mit Ersatzteilen erzeugen“ schickt nichts");
                    }
                    s.put(FUTURE, future);
                    ctx.log("Import ohne " + MOUNTS + ": " + swaps.size() + " Ersatzteil-Zeilen, davon 3 „" + removed + "“, Vorschau " + wanted.frame());
                    return StepResult.pass();
                })
                .then("Drohne im Inventar", 400, (ctx, s, age) -> {
                    ImportResult result = result(s);
                    if (result == null) {
                        return CovShareScript.wait(s, "keine Antwort auf „Mit Ersatzteilen erzeugen“");
                    }
                    if (result.status() != ImportStatus.APPLIED) {
                        return StepResult.fail("„Mit Ersatzteilen erzeugen“: " + result);
                    }
                    Optional<DroneData> created = inventoryDrone(Minecraft.getInstance(), data -> data.name().equals(Optional.of("Gimbal-Probe")));
                    if (created.isEmpty()) {
                        return CovShareScript.wait(s, "keine Drohne „Gimbal-Probe“ im Inventar");
                    }
                    if (!created.get().build().equals(mountsStandIns())) {
                        return StepResult.fail("erzeugte Drohne " + created.get().build() + " statt " + mountsStandIns());
                    }
                    ctx.log("Drohne ohne Zubehör im Inventar: " + created.get().build().frame() + ", Zubehör " + created.get().build().accessories().size());
                    closeConfigurator(Minecraft.getInstance());
                    return StepResult.pass();
                })
                .pause(REQUEST_GAP_TICKS * 3)
                .cleanup(ctx -> {
                    Object saved = ctx.state().get(SAVED_TABLES);
                    if (saved instanceof ShareTables tables) {
                        ShareTables.install(tables);
                    }
                    ShareSettings.substitutes((boolean) ctx.state().getOrDefault(SAVED_SUBSTITUTES, false));
                    ShareSettings.lastInput((String) ctx.state().getOrDefault(SAVED_INPUT, ""));
                    closeConfigurator(Minecraft.getInstance());
                })::tick;
    }

    private static void checkMissing(ShareScreen screen, String namespace, String link, String version, List<String> errors) {
        ShareImportState state = screen.state();
        if (!state.decoded().ok()) {
            errors.add(namespace + ": Code nicht lesbar " + state.decoded().status());
            return;
        }
        Optional<ShareImportState.Addon> addon = state.addons().stream().filter(a -> a.key().equals(namespace)).findFirst();
        if (addon.isEmpty() || addon.get().kind().filter(k -> k == MissingAddons.Kind.MISSING).isEmpty()) {
            errors.add(namespace + ": Zeile „nicht installiert“ fehlt: " + state.addons());
            return;
        }
        if (!addon.get().required().equals(Optional.of(version))) {
            errors.add(namespace + ": Version " + addon.get().required() + " statt " + version);
        }
        if (ShareLines.find(screen.infoLines(), ShareLines.TAG_ADDON + namespace, true).isEmpty()) {
            errors.add(namespace + ": Zeile im Fenster fehlt");
        }
        Optional<ShareLines.Line> button = ShareLines.find(screen.infoLines(), ShareLines.TAG_LINK + link, true);
        if (button.isEmpty() || !button.get().clickable()) {
            errors.add(namespace + ": Link-Knopf " + link + " fehlt: " + tags(screen.infoLines()));
        }
    }

    private static List<String> tags(List<ShareLines.Line> lines) {
        List<String> out = new ArrayList<>();
        for (ShareLines.Line line : lines) {
            if (!line.tag().isEmpty()) {
                out.add(line.tag());
            }
        }
        return out;
    }

    private static StepResult mismatchStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                SERVER_ERROR.set(null);
                state.put(SERVER, ctx.onServer(server -> openBench(server, GameType.CREATIVE)));
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = awaitServer(ctx);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                WorkbenchContext context = ShareWorkbenchExtension.lastContext();
                if (context == null || context.screen() != mc.gui.screen()) {
                    return timeout(ctx, 400, "Werkbank erscheint nicht: " + mc.gui.screen());
                }
                ShareScreen.open(context, ShareScreen.Mode.IMPORT, false);
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                ShareScreen screen = ShareScreen.active();
                if (screen == null || mc.gui.screen() != screen || screen.frames() < 3) {
                    return timeout(ctx, 600, "Sharecode-Fenster erscheint nicht: " + mc.gui.screen());
                }
                screen.setInput(ShareVectors.mismatchCode());
                ShareDecoded decoded = screen.state().decoded();
                List<String> errors = new ArrayList<>();
                boolean mismatch = decoded.namespaces().stream().anyMatch(r -> r.result() == NamespaceReport.Result.MISMATCH);
                if (!decoded.ok() || !mismatch || !decoded.partsLocked()) {
                    return StepResult.fail("Probe-Code ohne falsche Prüfsumme: " + decoded.status() + " " + decoded.namespaces());
                }
                if (ShareLines.find(screen.infoLines(), ShareLines.TAG_MISMATCH, true).isEmpty()) {
                    errors.add("Banner fehlt");
                }
                ShareSettings.setApply(EnumSet.of(ShareLayer.PARTS));
                if (screen.chosenLayers().contains(ShareLayer.PARTS)) {
                    errors.add("Teile nicht gesperrt");
                }
                Optional<ShareLines.Line> force = ShareLines.find(screen.infoLines(), ShareLines.TAG_FORCE, true);
                if (force.isEmpty()) {
                    errors.add("„Teile trotzdem übernehmen“ fehlt");
                } else {
                    force.get().action().run();
                }
                if (!screen.chosenLayers().contains(ShareLayer.PARTS)) {
                    errors.add("„Teile trotzdem übernehmen“ wirkt nicht: " + screen.state().forced());
                }
                if (!errors.isEmpty()) {
                    return StepResult.fail(String.join("; ", errors));
                }
                CompletableFuture<ImportResult> future = screen.apply();
                if (future == null) {
                    return StepResult.fail("Übernehmen schickt nach „Teile trotzdem übernehmen“ nichts");
                }
                state.put(FUTURE, future);
                state.put(PHASE, 3);
                return StepResult.pending();
            }
            default -> {
                ImportResult result = result(state);
                if (result == null) {
                    return timeout(ctx, 1100, "keine Antwort auf den Import");
                }
                ShareSettings.setApply(ShareSettings.DEFAULT_APPLY);
                closeBench(ctx);
                return StepResult.check(result.status() == ImportStatus.APPLIED, "Import nach „Teile trotzdem übernehmen“: " + result);
            }
        }
    }

    private static StepResult freeLinkStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(PHASE)) {
            state.put(SAVED, ShareSettings.lastInput());
            mc.gui.setScreen(ShareHarness.window(ShareHarness.context(Optional.empty(), true), ShareScreen.Mode.IMPORT));
            state.put(PHASE, 1);
            return StepResult.pending();
        }
        ShareScreen screen = ShareScreen.active();
        if (screen == null || mc.gui.screen() != screen || screen.frames() < 3) {
            return timeout(ctx, 200, "Sharecode-Fenster erscheint nicht: " + mc.gui.screen());
        }
        screen.setInput(P8_FREE_LINK);
        List<String> errors = new ArrayList<>();
        Optional<ShareLines.Line> untrusted = ShareLines.find(screen.infoLines(), ShareLines.TAG_UNTRUSTED, true);
        if (untrusted.isEmpty() || untrusted.get().clickable() || !untrusted.get().text().getString().contains("example.org/free-addon")) {
            errors.add("freie Adresse nicht als Text mit Hinweis: " + tags(screen.infoLines()));
        }
        for (ShareLines.Line line : screen.infoLines()) {
            if (line.tag().startsWith(ShareLines.TAG_LINK) && line.tag().contains("example.org")) {
                errors.add("freie Adresse klickbar: " + line.tag());
            }
        }
        if (ShareClient.openLink(screen, "https://example.org/free-addon")) {
            errors.add("openLink nimmt eine freie Adresse an");
        }
        screen.setInput((String) state.get(SAVED));
        mc.gui.setScreen(null);
        return StepResult.check(errors.isEmpty(), String.join("; ", errors));
    }

    private static StepResult tabStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                mc.gui.setScreen(null);
                SERVER_ERROR.set(null);
                state.put(SERVER, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.closeContainer();
                    player.setGameMode(GameType.CREATIVE);
                    ItemStack drone = PropwashApi.builds().createDrone(DroneData.of(ShareScene.freestyle()).withName("Tab-Probe"));
                    player.getInventory().setItem(player.getInventory().getSelectedSlot(), drone);
                    player.containerMenu.broadcastChanges();
                }));
                state.put(PHASE, 1);
                state.put(SINCE, ctx.tick());
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = awaitServer(ctx);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (PropwashClientApi.heldDrone().isEmpty() || ctx.tick() - (int) state.get(SINCE) < REQUEST_GAP_TICKS) {
                    return timeout(ctx, 200, "Drohne kommt nicht in der Hand an");
                }
                state.put(SAVED, ShareSettings.lastInput());
                if (!PropwashClientApi.openConfigTab(ShareConfigTab.ID, null)) {
                    return StepResult.fail(ShareConfigTab.ID + " öffnet keinen Tab");
                }
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                ShareConfigTab tab = ShareConfigTab.active();
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || tab == null || tab.frames() < 3) {
                    return timeout(ctx, 300, "Tab erscheint nicht: " + mc.gui.screen());
                }
                if (screen.activeTab() != tab) {
                    return StepResult.fail("Tab " + ShareConfigTab.ID + " nicht aktiv: " + screen.activeTab());
                }
                List<String> errors = new ArrayList<>();
                tab.setInput(P1);
                if (!tab.state().decoded().partsKnown()) {
                    errors.add("P1 im Tab nicht lesbar: " + tab.state().decoded().status());
                }
                tab.setInput(P10_CHECKSUM);
                if (tab.state().decoded().status() != ShareStatus.CHECKSUM) {
                    errors.add("P10 im Tab: " + tab.state().decoded().status());
                }
                if (!tab.copyHeld() || !mc.keyboardHandler.getClipboard().startsWith("PW3-")) {
                    errors.add("Tab kopiert die gehaltene Drohne nicht als PW3: " + tab.exportStatus());
                } else {
                    tab.setInput(mc.keyboardHandler.getClipboard());
                    if (!tab.state().decoded().ok() || !tab.state().decoded().require().build().equals(ShareScene.freestyle())) {
                        errors.add("Export des Tabs liest sich nicht zurück: " + tab.state().decoded().status());
                    }
                }
                if (!errors.isEmpty()) {
                    return StepResult.fail(String.join("; ", errors));
                }
                closeConfigurator(mc);
                DroneData data = freestyle().withTune(new TuneSpec(Map.of("rates.roll.rcRate", 1.37F))).withPaint(PaintSpec.DEFAULT.with("frame", 0x123456));
                ShareSettings.lastInput(ShareService.encode(data, EnumSet.of(ShareLayer.PAINT, ShareLayer.TUNE), true, Optional.empty()));
                mc.gui.setScreen(new TabHarnessScreen(new ShareConfigTab()));
                state.put(PHASE, 7);
                return StepResult.pending();
            }
            case 7 -> {
                ShareConfigTab tab = ShareConfigTab.active();
                if (!(mc.gui.screen() instanceof TabHarnessScreen screen) || screen.frames() < 3 || tab == null) {
                    return timeout(ctx, 400, "Tab-Fenster ohne Pause erscheint nicht: " + mc.gui.screen());
                }
                CompletableFuture<ImportResult> future = tab.sendTune();
                if (future == null) {
                    return StepResult.fail("„Tuning übernehmen“ schickt nichts");
                }
                state.put(FUTURE, future);
                state.put(PHASE, 3);
                return StepResult.pending();
            }
            case 3 -> {
                ImportResult result = result(state);
                if (result == null) {
                    return timeout(ctx, 600, "keine Antwort auf „Tuning übernehmen“");
                }
                if (result.status() == ImportStatus.BUSY && ctx.tick() < 500) {
                    ShareConfigTab tab = ShareConfigTab.active();
                    CompletableFuture<ImportResult> retry = tab == null ? null : tab.sendTune();
                    if (retry != null) {
                        state.put(FUTURE, retry);
                    }
                    return StepResult.pending();
                }
                if (result.status() != ImportStatus.APPLIED) {
                    return StepResult.fail("„Tuning übernehmen“: " + result);
                }
                state.put(SINCE, ctx.tick());
                state.put(PHASE, 4);
                return StepResult.pending();
            }
            case 4 -> {
                Float roll = PropwashClientApi.heldDrone().map(d -> d.tune().values().get("rates.roll.rcRate")).orElse(null);
                if (roll == null || Math.abs(roll - 1.37F) > 1.0E-4F || ctx.tick() - (int) state.get(SINCE) < REQUEST_GAP_TICKS) {
                    return timeout(ctx, 800, "Tuning nicht auf der gehaltenen Drohne: " + PropwashClientApi.heldDrone().map(DroneData::tune));
                }
                ShareConfigTab tab = ShareConfigTab.active();
                if (tab == null) {
                    return StepResult.fail("Tab geschlossen");
                }
                CompletableFuture<ImportResult> future = tab.sendPaint();
                if (future == null) {
                    return timeout(ctx, 850, "„Lack übernehmen“ schickt nichts");
                }
                state.put(FUTURE, future);
                state.put(PHASE, 5);
                return StepResult.pending();
            }
            case 5 -> {
                ImportResult result = result(state);
                if (result == null) {
                    return timeout(ctx, 1000, "keine Antwort auf „Lack übernehmen“");
                }
                if (result.status() == ImportStatus.BUSY) {
                    state.put(SINCE, ctx.tick());
                    state.put(PHASE, 4);
                    return StepResult.pending();
                }
                if (result.status() != ImportStatus.APPLIED) {
                    return StepResult.fail("„Lack übernehmen“: " + result);
                }
                state.put(PHASE, 6);
                return StepResult.pending();
            }
            default -> {
                Optional<Integer> frame = PropwashClientApi.heldDrone().flatMap(d -> d.paint().color("frame"));
                if (!frame.equals(Optional.of(0x123456))) {
                    return timeout(ctx, 1150, "Lack nicht auf der gehaltenen Drohne: " + frame);
                }
                ShareSettings.lastInput((String) state.get(SAVED));
                mc.gui.setScreen(null);
                return StepResult.pass();
            }
        }
    }

    private static StepResult commandStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(PHASE)) {
            SERVER_ERROR.set(null);
            COMMAND_RESULT.set(0);
            String freestyle = freestyleCode();
            state.put(SERVER, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.setGameMode(GameType.CREATIVE);
                player.getInventory().clearContent();
                player.getInventory().setItem(player.getInventory().getSelectedSlot(), PropwashApi.builds().createDrone(freestyle()));
                var dispatcher = server.getCommands().getDispatcher();
                var console = server.createCommandSourceStack();
                var asPlayer = server.createCommandSourceStack().withEntity(player).withPosition(player.position());
                try {
                    int export = dispatcher.execute("propwash code export parts,paint,tune,name", asPlayer);
                    int info = dispatcher.execute("propwash code info " + P2_JMP, console);
                    int broken = dispatcher.execute("propwash code info " + P10_CHECKSUM, console);
                    int convert = dispatcher.execute("propwash code convert " + PW2_NAMED, console);
                    player.getInventory().clearContent();
                    int give = dispatcher.execute("propwash code give " + freestyle + " " + player.getScoreboardName(), console);
                    boolean given = hasDrone(player, data -> data.build().equals(freestyle().build()) && data.name().equals(freestyle().name()));
                    player.getInventory().clearContent();
                    int imported = dispatcher.execute("propwash code import " + ShareVectors.missingCode() + " substitutes", asPlayer);
                    boolean substituted = hasDrone(player, data -> data.build().allParts().stream().noneMatch(id -> id.getNamespace()
                            .equals(MISSING_NAMESPACE)));
                    int plain = dispatcher.execute("propwash code import " + ShareVectors.missingCode(), asPlayer);
                    if (export != ShareCommands.SUCCESS || info != ShareCommands.SUCCESS || broken != ShareCommands.FAILURE
                            || convert != ShareCommands.SUCCESS || give != 1 || !given || imported != 1 || !substituted || plain != ShareCommands.FAILURE) {
                        SERVER_ERROR.set("export " + export + ", info " + info + ", P10 " + broken + ", convert " + convert + ", give " + give + " (Drohne "
                                + given + "), import substitutes " + imported + " (Ersatz " + substituted + "), import ohne Ersatz " + plain);
                    }
                    COMMAND_RESULT.set(1);
                } catch (Exception e) {
                    SERVER_ERROR.set("Befehl warf: " + e);
                }
            }));
            state.put(PHASE, 1);
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx);
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        return StepResult.check(COMMAND_RESULT.get() == 1, "Befehle liefen nicht");
    }

    private static StepResult shareButtonStep(SelfTest.StepContext ctx) {
        int buttons = 0;
        for (WorkbenchExtension extension : PropwashClientApi.workbenchExtensions()) {
            if (extension.id().getPath().contains("share") || extension.getClass().getSimpleName().contains("Share")) {
                buttons++;
            }
        }
        Optional<String> jmp = Services.PLATFORM.modVersion(ShareVectors.JMP);
        if (jmp.isEmpty()) {
            ctx.log("Just More Parts nicht geladen, Sharecode-Knöpfe " + buttons);
            return StepResult.check(buttons == 1, "Sharecode-Knöpfe ohne JMP: " + buttons);
        }
        List<String> errors = new ArrayList<>();
        if (buttons != 1) {
            errors.add(buttons + " Sharecode-Knöpfe");
        }
        String code = ShareService.encode(DroneData.of(ShareVectors.jmpBuild()), EnumSet.of(ShareLayer.PARTS), true, Optional.empty());
        if (!P2_JMP.equals(code)) {
            errors.add("PW3-Export der JMP-Drohne " + code + " statt " + P2_JMP);
        }
        ctx.log("Just More Parts " + jmp.get() + ": Sharecode-Knöpfe " + buttons + ", Export " + code);
        return StepResult.check(errors.isEmpty(), String.join("; ", errors));
    }

    private static StepResult scrollbarStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(PHASE)) {
            state.put(SAVED, ShareSettings.lastInput());
            state.put(SCALE, mc.options.guiScale().get());
            mc.options.guiScale().set(6);
            mc.resizeGui();
            ShareSettings.lastInput(P2_JMP);
            mc.gui.setScreen(ShareHarness.window(ShareHarness.context(Optional.empty(), false), ShareScreen.Mode.IMPORT));
            state.put(PHASE, 1);
            return StepResult.pending();
        }
        ShareScreen screen = ShareScreen.active();
        if (screen == null || mc.gui.screen() != screen || screen.frames() < 3) {
            return timeout(ctx, 150, "Sharecode-Fenster erscheint nicht: " + mc.gui.screen());
        }
        List<String> errors = new ArrayList<>();
        for (LayoutAudit.Finding finding : LayoutProbe.scrollbars(mc, screen)) {
            errors.add(finding.describe());
        }
        int[] area = screen.infoArea();
        double x = area[2] - 1.5;
        MouseButtonInfo left = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
        screen.scrollInfo(0);
        boolean pressed = screen.mouseClicked(new MouseButtonEvent(x, area[1] + 2, left), false);
        boolean dragged = screen.mouseDragged(new MouseButtonEvent(x, area[3] - 1, left), 0.0, area[3] - area[1]);
        int after = screen.infoScroll();
        screen.mouseReleased(new MouseButtonEvent(x, area[3] - 1, left));
        ctx.log("GUI " + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight() + ", Liste " + area[0] + "," + area[1] + " bis "
                + area[2] + "," + area[3] + ", Klick " + pressed + ", Ziehen " + dragged + ", Versatz 0 → " + after);
        if (!pressed || !dragged || after <= 0) {
            errors.add("Ziehen an der Scrollleiste: Klick " + pressed + ", Ziehen " + dragged + ", Versatz 0 → " + after);
        }
        ShareSettings.lastInput((String) state.get(SAVED));
        mc.gui.setScreen(null);
        mc.options.guiScale().set((int) state.get(SCALE));
        mc.resizeGui();
        return StepResult.check(errors.isEmpty(), String.join("; ", errors));
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            Minecraft.getInstance().gui.setScreen(null);
            closeBench(ctx);
            ShareSettings.reset();
            return StepResult.pending();
        }
        return ctx.tick() < 5 ? StepResult.pending() : StepResult.pass();
    }

    static Optional<DroneData> inventoryDrone(Minecraft mc, java.util.function.Predicate<DroneData> wanted) {
        if (mc.player == null) {
            return Optional.empty();
        }
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            Optional<DroneData> data = PropwashApi.builds().read(mc.player.getInventory().getItem(i));
            if (data.isPresent() && wanted.test(data.get())) {
                return data;
            }
        }
        return Optional.empty();
    }

    static boolean hasDrone(ServerPlayer player, java.util.function.Predicate<DroneData> wanted) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            Optional<DroneData> data = PropwashApi.builds().read(player.getInventory().getItem(i));
            if (data.isPresent() && wanted.test(data.get())) {
                return true;
            }
        }
        return false;
    }

    static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    static BlockPos benchPos(ServerPlayer player) {
        return player.blockPosition().offset(2, 0, 2);
    }

    static void openBench(MinecraftServer server, GameType mode) {
        try {
            ServerPlayer player = player(server);
            player.closeContainer();
            player.setGameMode(mode);
            Block block = BuiltInRegistries.BLOCK.getValue(Constants.id("drone_workbench"));
            BlockPos pos = benchPos(player);
            player.level().setBlockAndUpdate(pos, block.defaultBlockState());
            MenuProvider provider = player.level().getBlockState(pos).getMenuProvider(player.level(), pos);
            if (provider == null || player.openMenu(provider).isEmpty()) {
                SERVER_ERROR.set("Werkbank-Menü nicht geöffnet");
            }
        } catch (RuntimeException e) {
            SERVER_ERROR.set("Werkbank nicht aufgestellt: " + e);
        }
    }

    static void closeBench(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ShareScreen) {
            mc.gui.setScreen(null);
        }
        ctx.onServer(server -> {
            ServerPlayer player = player(server);
            player.closeContainer();
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent();
            player.level().removeBlock(benchPos(player), false);
        });
    }

    static void closeConfigurator(Minecraft mc) {
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        } else {
            mc.gui.setScreen(null);
        }
    }

    @SuppressWarnings("unchecked")
    static StepResult awaitServer(SelfTest.StepContext ctx) {
        CompletableFuture<Void> future = (CompletableFuture<Void>) ctx.state().get(SERVER);
        if (future == null) {
            return StepResult.pass();
        }
        if (!future.isDone()) {
            return timeout(ctx, 200, "Server antwortet nicht");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Aktion fehlgeschlagen");
        }
        String error = SERVER_ERROR.get();
        if (error != null) {
            return StepResult.fail(error);
        }
        ctx.state().remove(SERVER);
        return StepResult.pass();
    }

    @SuppressWarnings("unchecked")
    static @Nullable ImportResult result(Map<String, Object> state) {
        CompletableFuture<ImportResult> future = (CompletableFuture<ImportResult>) state.get(FUTURE);
        return future != null && future.isDone() ? future.getNow(null) : null;
    }

    static StepResult timeout(SelfTest.StepContext ctx, int limit, String reason) {
        return ctx.tick() >= limit ? StepResult.fail(reason) : StepResult.pending();
    }

    private static @Nullable String smallGui(SelfTest.StepContext ctx, Minecraft mc, ShareScreen screen) {
        int saved = mc.options.guiScale().get();
        List<ShareLayer> switched = new ArrayList<>();
        for (ShareLayer layer : ShareScreen.EXPORT_TOGGLES) {
            if (!ShareSettings.exports(layer)) {
                ShareSettings.toggleExport(layer);
                switched.add(layer);
            }
        }
        mc.options.guiScale().set(6);
        mc.resizeGui();
        try {
            String gui = mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight();
            int shown = screen.codeRowsShown();
            ctx.log("Sharecode-Fenster bei GUI " + gui + ": " + screen.exportRows().size() + " Codezeilen, " + shown + " sichtbar, Werte "
                    + screen.statsHeight() + " px hoch, " + (screen.singleColumn() ? "eine Spalte" : "zwei Spalten"));
            if (screen.exportRows().size() > shown) {
                int[] center = screen.codeCenter();
                screen.mouseScrolled(center[0], center[1], 0.0, -1.0);
                if (screen.codeScroll() != 1) {
                    return "Code-Kasten bei GUI " + gui + " scrollt nicht mit dem Mausrad (" + screen.exportRows().size() + " Zeilen, " + shown
                            + " sichtbar)";
                }
            }
            if (screen.statsHeight() <= Metrics.LINE_HEIGHT) {
                return "Werte der Drohne bei GUI " + gui + " ohne Platz: " + screen.statsHeight() + " px";
            }
            return null;
        } finally {
            switched.forEach(ShareSettings::toggleExport);
            mc.options.guiScale().set(saved);
            mc.resizeGui();
        }
    }

    static @Nullable Screen screen(Map<String, Object> state) {
        return (Screen) state.get(SCREEN);
    }

    static String jmpModrinth() {
        return MissingAddons.MODRINTH + ShareTables.current().directory().byNamespace(ShareVectors.JMP).flatMap(AddonDirectory.Addon::modrinth).orElse("?");
    }

    static AddonDirectory directory() {
        return ShareTables.current().directory();
    }
}
