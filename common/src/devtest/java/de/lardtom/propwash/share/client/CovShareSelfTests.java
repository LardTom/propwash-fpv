package de.lardtom.propwash.share.client;

import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.ImportTarget;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.client.OsdLayoutSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.apiimpl.client.ClientApiImpl;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintCost;
import de.lardtom.propwash.build.PaintSwatch;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdLayoutCodec;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.share.Pw3Carry;
import de.lardtom.propwash.share.Pw3Codec;
import de.lardtom.propwash.share.Pw3Params;
import de.lardtom.propwash.share.ShareCommands;
import de.lardtom.propwash.share.ShareContent;
import de.lardtom.propwash.share.ShareDecoded;
import de.lardtom.propwash.share.ShareDevTables;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareOsd;
import de.lardtom.propwash.share.ShareServer;
import de.lardtom.propwash.share.ShareService;
import de.lardtom.propwash.share.ShareTable;
import de.lardtom.propwash.share.ShareTableJson;
import de.lardtom.propwash.share.ShareTables;
import de.lardtom.propwash.share.ShareVectors;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class CovShareSelfTests {
    public static final String GROUP = "share: Abdeckung cov-pw3 – ";
    static final String P3 = "PW3-2qPtCOkzoxBOhgMjapC4t6uesyMUi5NVglyKJqEgUBbJ006KypJFTwxBPV2G7IDh7nhPm5lPwL31h5SzJjmmH2u8ygEEEeMIGkvcOE251edzGBz2wX5LuS5yokD";
    static final String EXAMPLE_OSD = "propwash_example:ammo";
    private static final String KEY = ShareCommands.KEY;
    private static final String FUTURE = "cov_future";
    private static final String SAVED_INPUT = "cov_saved_input";
    private static final String SAVED_SUBSTITUTES = "cov_saved_substitutes";
    private static final String SAVED_OSD = "cov_saved_osd";
    private static final String SAVED_TABLES = "cov_saved_tables";
    private static final String CODE = "cov_code";
    private static final String BEFORE = "cov_before";
    private static final String AFTER = "cov_after";
    private static final String COUNT = "cov_count";
    private static final String DONE = "cov_done";
    private static final String KEYS = "cov_keys";
    private static final String OSD_JSON = "cov_osd_json";
    private static final AtomicInteger IMPORTS = new AtomicInteger();
    private static final List<String> IMPORT_LOG = new CopyOnWriteArrayList<>();
    private static volatile boolean listening;

    private CovShareSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Export-Häkchen nach ShareSettings.reset(): OSD aus, Lack, Tuning und Name an; ohne Namen ist der Name-Schalter gesperrt",
                200, CovShareSelfTests.defaultsStep());
        SelfTest.clientStep(GROUP + "unfertige Drohne: ohne Akku kein Code, Hinweis no_result, „In die Zwischenablage“ gesperrt", 900,
                CovShareSelfTests.unfinishedStep());
        SelfTest.clientStep(GROUP + "Häkchen Add-on-Infos: bei Propwash- und JMP-Teilen keine Zeile, addonInfo steht auf an", 200,
                CovShareSelfTests.addonInfoStep());
        SelfTest.clientStep(GROUP + "Add-on ohne Teile-Tabelle: Zeile no_table, Code liest sich zum selben Bauplan", 200, CovShareSelfTests.noTableStep());
        SelfTest.clientStep(GROUP + "Rundlauf mit allen Ebenen: Werkbank-Export samt Name, OSD wie im Code, Kopfzeile PW3, Flugwerte wie die Analyse", 1200,
                CovShareSelfTests.roundTripStep());
        SelfTest.clientStep(GROUP + "Add-ons in diesem Code: mit Just More Parts P2 als OK mit Version, keine Warnung", 200, CovShareSelfTests.addonsStep());
        SelfTest.clientStep(GROUP + "Überleben: Einkaufsliste mit Teilen und Summe, nach dem Einlegen vollständig, Lack kostet Farbstoffe", 2200,
                CovShareSelfTests.shoppingStep());
        SelfTest.clientStep(GROUP + "nur einzelne Ebenen: Tuning bzw. Lack übernehmen ändert nur diese Ebene, Videokanal und Tasten bleiben", 2200,
                CovShareSelfTests.singleLayersStep());
        SelfTest.clientStep(GROUP + "OSD-Layout übernehmen wirkt nur lokal: Layout wie im Code, Drohne gleich, keine Import-Anfrage", 900,
                CovShareSelfTests.osdLocalStep());
        SelfTest.clientStep(GROUP + "Zuschauermodus: Fenster, Tab und /propwash code import abgelehnt, nichts ändert sich", 1200,
                CovShareSelfTests.spectatorStep());
        SelfTest.clientStep(GROUP + "Prüfsumme falsch: nur Lack, Tuning und OSD gehen ohne „Teile trotzdem übernehmen“", 1200,
                CovShareSelfTests.mismatchLayersStep());
        SelfTest.clientStep(GROUP + "Befehl export: Code mit Kopier-Klick, Standard ohne OSD, parts,paint,name genau so, Fehler no_drone und bad_layer", 300,
                CovShareSelfTests.exportCommandStep());
        SelfTest.clientStep(GROUP + "Befehl import im Überleben: Tuning und Lack auf die gehaltene Drohne, Teile bleiben, ohne Farbstoffe missing_dyes", 300,
                CovShareSelfTests.importCommandStep());
        SelfTest.clientStep(GROUP + "genau ein Konfigurator-Tab „Sharecode“ und ein Werkbank-Knopf", 40, CovShareSelfTests::tabCountStep);
        SelfTest.clientStep(GROUP + "Kamerawinkel im Sharecode: Deadcat 35°, eigene Drohne 42° mit Tuning, ohne Tuning Rahmenstandard", 2400,
                CovShareSelfTests.cameraStep());
        SelfTest.clientStep(GROUP + "OSD-Eintrag eines Add-ons (propwash_example:ammo) im Code", 200, CovShareSelfTests.addonOsdStep());
        SelfTest.clientStep(GROUP + "Deadcat-Code im Tab: Tuning und OSD auf eine Propwash-Drohne, Teile bleiben", 900, CovShareSelfTests.otherLayersStep());
        SelfTest.clientStep(GROUP + "Deadcat-Code im Tab: Lack auf eine Propwash-Drohne, Teile, Name und Tuning bleiben", 900,
                CovShareSelfTests.otherLayersPaintStep());
        SelfTest.clientStep(GROUP + "Deadcat-Code im Werkbank-Fenster: nur Lack und Tuning, Teile bleiben", 1200, CovShareSelfTests.otherLayersBenchStep());
        SelfTest.clientStep(GROUP + "Deadcat-Code per Befehl import im Überleben: Lack und Tuning auf die gehaltene Drohne, Teile bleiben", 300,
                CovShareSelfTests.otherLayersCommandStep());
    }

    static SelfTest.ClientStep addonOsdStep() {
        return CovShareScript.create()
                .then("OSD mit Add-on-Eintrag", 100, (ctx, s, age) -> {
                    OsdConfig saved = OsdClient.CONFIG.get().copy();
                    s.put(SAVED_OSD, saved);
                    OsdConfig config = saved.copy();
                    config.elements.put(EXAMPLE_OSD, new OsdConfig.Slot(true, 4, 6));
                    OsdConfig.sanitize(config);
                    OsdClient.CONFIG.set(config);
                    String code = ShareService.encode(DroneData.of(ShareScene.freestyle()), EnumSet.of(ShareLayer.OSD), true,
                            ShareScreen.osdJson(EnumSet.of(ShareLayer.OSD)));
                    ShareDecoded decoded = ShareService.decode(code);
                    if (!decoded.ok() || decoded.require().osd().isEmpty()) {
                        return StepResult.fail("Code ohne OSD-Ebene: " + decoded.status());
                    }
                    ShareOsd osd = decoded.require().osd().get();
                    if (!osd.json().contains(EXAMPLE_OSD)) {
                        return StepResult.fail("OSD-Ebene ohne " + EXAMPLE_OSD + " (" + osd.json().length() + " Zeichen)");
                    }
                    OsdConfig moved = OsdClient.CONFIG.get().copy();
                    moved.elements.put(EXAMPLE_OSD, new OsdConfig.Slot(false, 20, 3));
                    OsdConfig.sanitize(moved);
                    OsdClient.CONFIG.set(moved);
                    if (!ShareScreen.applyOsd(osd)) {
                        return StepResult.fail("OSD-Ebene lässt sich nicht anwenden");
                    }
                    OsdConfig.Slot slot = OsdClient.CONFIG.get().elements.get(EXAMPLE_OSD);
                    if (slot == null || !slot.enabled || slot.col != 4 || slot.row != 6) {
                        return StepResult.fail(EXAMPLE_OSD + " nach dem Anwenden " + (slot == null ? "fehlt" : slot.enabled + " " + slot.col + "," + slot.row));
                    }
                    ctx.log(EXAMPLE_OSD + " im Code und nach dem Anwenden wieder bei 4,6");
                    return StepResult.pass();
                })
                .cleanup(ctx -> restoreOsdConfig(ctx.state()))::tick;
    }

    private static SelfTest.ClientStep defaultsStep() {
        DroneData named = ShareSelfTests.freestyle();
        DroneData unnamed = new DroneData(named.build(), named.paint(), named.tune(), Optional.empty());
        return CovShareScript.create()
                .then("Einstellungen zurücksetzen", 20, (ctx, s, age) -> {
                    s.put(SAVED_INPUT, ShareSettings.lastInput());
                    ShareSettings.reset();
                    List<String> errors = new ArrayList<>();
                    if (!ShareSettings.exportLayers().equals(ShareLayer.DEFAULT_EXPORT)) {
                        errors.add("Export-Ebenen nach reset " + ShareSettings.exportLayers());
                    }
                    if (ShareSettings.exports(ShareLayer.OSD) || !ShareSettings.exports(ShareLayer.PAINT) || !ShareSettings.exports(ShareLayer.TUNE)
                            || !ShareSettings.exports(ShareLayer.NAME)) {
                        errors.add("Häkchen falsch: OSD " + ShareSettings.exports(ShareLayer.OSD) + ", Lack " + ShareSettings.exports(ShareLayer.PAINT)
                                + ", Tuning " + ShareSettings.exports(ShareLayer.TUNE) + ", Name " + ShareSettings.exports(ShareLayer.NAME));
                    }
                    if (!ShareSettings.addonInfo()) {
                        errors.add("addonInfo nach reset aus");
                    }
                    Minecraft.getInstance().gui.setScreen(ShareHarness.window(ShareHarness.context(Optional.of(named), true), ShareScreen.Mode.EXPORT));
                    return check(errors);
                })
                .then("Fenster mit benannter Drohne", 100, (ctx, s, age) -> {
                    ShareScreen screen = shown(s);
                    if (screen == null) {
                        return CovShareScript.wait(s, "Export-Fenster erscheint nicht");
                    }
                    List<String> errors = new ArrayList<>();
                    for (ShareLayer layer : ShareScreen.EXPORT_TOGGLES) {
                        FlatButton toggle = button(screen, ShareScreen.toggleKey(layer));
                        if (toggle == null || !toggle.active) {
                            errors.add("Schalter " + layer.key() + " fehlt oder gesperrt");
                        }
                    }
                    Set<ShareLayer> layers = layersOf(screen.exportCode());
                    if (!layers.equals(EnumSet.of(ShareLayer.PARTS, ShareLayer.PAINT, ShareLayer.TUNE, ShareLayer.NAME))) {
                        errors.add("Code beim ersten Öffnen mit " + layers);
                    }
                    ctx.log("frisch geöffnet: " + layers + ", " + screen.exportCode().length() + " Zeichen");
                    s.put(BEFORE, screen);
                    Minecraft.getInstance().gui.setScreen(ShareHarness.window(ShareHarness.context(Optional.of(unnamed), true), ShareScreen.Mode.EXPORT));
                    return check(errors);
                })
                .then("Fenster mit Drohne ohne Namen", 100, (ctx, s, age) -> {
                    ShareScreen screen = shown(s);
                    if (screen == null || screen == s.get(BEFORE)) {
                        return CovShareScript.wait(s, "zweites Export-Fenster erscheint nicht");
                    }
                    List<String> errors = new ArrayList<>();
                    if (screen.exportable(ShareLayer.NAME)) {
                        errors.add("Name ohne Namen exportierbar");
                    }
                    FlatButton name = button(screen, ShareScreen.toggleKey(ShareLayer.NAME));
                    String tooltip = name == null ? "–" : tooltipKey(name);
                    if (name == null || name.active) {
                        errors.add("Name-Schalter nicht gesperrt");
                    }
                    if (!"propwash.share.export.toggle.name.none".equals(tooltip)) {
                        errors.add("Name-Schalter erklärt nicht: " + tooltip);
                    }
                    Set<ShareLayer> layers = layersOf(screen.exportCode());
                    if (!layers.equals(EnumSet.of(ShareLayer.PARTS, ShareLayer.PAINT, ShareLayer.TUNE))) {
                        errors.add("Code ohne Namen mit " + layers);
                    }
                    ctx.log("ohne Namen: Schalter gesperrt, Hinweis " + tooltip + ", Code " + layers);
                    return check(errors);
                })
                .cleanup(ctx -> {
                    Minecraft.getInstance().gui.setScreen(null);
                    ShareSettings.reset();
                    ShareSettings.lastInput((String) ctx.state().getOrDefault(SAVED_INPUT, ""));
                })::tick;
    }

    private static SelfTest.ClientStep unfinishedStep() {
        return CovShareScript.create()
                .server("Werkbank mit Freestyle-Drohne", (server, out) -> {
                    ServerPlayer player = openBench(server, GameType.CREATIVE);
                    ImportResult result = PropwashApi.builds().applyImport(player, workbench(DroneData.of(ShareScene.freestyle()), ImportLayer.PARTS));
                    if (result.status() != ImportStatus.APPLIED) {
                        throw new IllegalStateException("Freestyle-Drohne nicht in der Werkbank: " + result);
                    }
                })
                .then("Export-Fenster öffnen", 400, (ctx, s, age) -> {
                    WorkbenchContext context = benchContext();
                    if (context == null || context.result().isEmpty()) {
                        return CovShareScript.wait(s, "Werkbank ohne fertige Drohne");
                    }
                    ShareScreen.open(context, ShareScreen.Mode.EXPORT, false);
                    return StepResult.pass();
                })
                .then("Code mit Akku", 200, (ctx, s, age) -> {
                    ShareScreen screen = shown(s);
                    if (screen == null || screen.exportCode().isEmpty()) {
                        return CovShareScript.wait(s, "kein Code mit allen Teilen");
                    }
                    ctx.log("mit Akku: " + screen.exportCode().length() + " Zeichen");
                    return StepResult.pass();
                })
                .server("Akku entnehmen", (server, out) -> {
                    ServerPlayer player = ShareSelfTests.player(server);
                    if (!(player.containerMenu instanceof DroneWorkbenchMenu menu)) {
                        throw new IllegalStateException("Werkbank nicht offen");
                    }
                    menu.getSlot(BuildSlots.BATTERY).set(ItemStack.EMPTY);
                    menu.broadcastChanges();
                })
                .then("ohne Akku", 300, (ctx, s, age) -> {
                    ShareScreen screen = ShareScreen.active();
                    if (screen == null || !screen.exportCode().isEmpty() || screen.context().result().isPresent()) {
                        return CovShareScript.wait(s, "Export-Fenster zeigt nach dem Entnehmen noch einen Code");
                    }
                    List<String> errors = new ArrayList<>();
                    String status = keyOf(screen.exportStatus());
                    if (!"propwash.share.export.no_result".equals(status)) {
                        errors.add("Statuszeile " + status);
                    }
                    FlatButton copy = screen.copyButton();
                    if (copy == null || copy.active) {
                        errors.add("„In die Zwischenablage“ nicht gesperrt");
                    }
                    if (screen.copy()) {
                        errors.add("Kopieren ohne Code liefert true");
                    }
                    ctx.log("ohne Akku: Code leer, Status " + status + ", Kopierknopf aktiv " + (copy != null && copy.active));
                    return check(errors);
                })
                .cleanup(CovShareSelfTests::closeAll)::tick;
    }

    private static SelfTest.ClientStep addonInfoStep() {
        return CovShareScript.create()
                .then("Propwash-Drohne", 60, (ctx, s, age) -> {
                    if (age == 0) {
                        s.put(SAVED_INPUT, ShareSettings.lastInput());
                        ShareSettings.reset();
                        if (!ShareSettings.addonInfo()) {
                            return StepResult.fail("ShareSettings.addonInfo nach reset aus");
                        }
                        openExport(DroneData.of(ShareScene.freestyle()));
                    }
                    ShareScreen screen = shown(s);
                    if (screen == null) {
                        return CovShareScript.wait(s, "Export-Fenster erscheint nicht");
                    }
                    if (screen.infoMatters() || button(screen, "propwash.share.export.addon_info") != null) {
                        return StepResult.fail("Zeile Add-on-Infos bei einer Propwash-Drohne");
                    }
                    s.put(BEFORE, screen);
                    return StepResult.pass();
                })
                .then("JMP-Drohne", 60, (ctx, s, age) -> {
                    if (!ShareVectors.jmpLoaded()) {
                        ctx.log("Just More Parts nicht geladen: nur die Propwash-Drohne geprüft");
                        return StepResult.pass();
                    }
                    if (age == 0) {
                        openExport(DroneData.of(ShareVectors.jmpBuild()));
                    }
                    ShareScreen screen = shown(s);
                    if (screen == null || screen == s.get(BEFORE)) {
                        return CovShareScript.wait(s, "Export-Fenster der JMP-Drohne erscheint nicht");
                    }
                    if (screen.infoMatters() || button(screen, "propwash.share.export.addon_info") != null) {
                        return StepResult.fail("Zeile Add-on-Infos bei einer JMP-Drohne");
                    }
                    ctx.log("Just More Parts " + Services.PLATFORM.modVersion(ShareVectors.JMP).orElse("?") + ": keine Zeile Add-on-Infos");
                    s.put(BEFORE, screen);
                    return StepResult.pass();
                })
                .then("Gegenprobe Beispiel-Add-on", 60, (ctx, s, age) -> {
                    if (!ShareDevTables.active()) {
                        ctx.log("Beispiel-Add-on aus: keine Gegenprobe");
                        return StepResult.pass();
                    }
                    if (age == 0) {
                        openExport(DroneData.of(ShareScene.freestyle().withPart(PartKind.FRAME, Identifier.fromNamespaceAndPath(ShareDevTables.EXAMPLE,
                                "race_stretch_5"))));
                    }
                    ShareScreen screen = shown(s);
                    if (screen == null || screen == s.get(BEFORE)) {
                        return CovShareScript.wait(s, "Export-Fenster mit Beispiel-Teil erscheint nicht");
                    }
                    boolean row = button(screen, "propwash.share.export.addon_info") != null;
                    ctx.log("Gegenprobe " + ShareDevTables.EXAMPLE + " ohne Verzeichniseintrag: infoMatters " + screen.infoMatters() + ", Zeile " + row);
                    return StepResult.check(screen.infoMatters() && row, "Gegenprobe: Zeile fehlt bei einem Add-on ohne Verzeichniseintrag");
                })
                .cleanup(ctx -> {
                    Minecraft.getInstance().gui.setScreen(null);
                    ShareSettings.reset();
                    ShareSettings.lastInput((String) ctx.state().getOrDefault(SAVED_INPUT, ""));
                })::tick;
    }

    private static SelfTest.ClientStep noTableStep() {
        Identifier frame = Identifier.fromNamespaceAndPath(ShareDevTables.EXAMPLE, "race_stretch_5");
        DroneData data = DroneData.of(ShareScene.freestyle().withPart(PartKind.FRAME, frame));
        return CovShareScript.create()
                .then("Tabelle des Beispiels abschalten", 40, (ctx, s, age) -> {
                    if (!ShareDevTables.active() || PropwashApi.parts().get(frame).isEmpty()) {
                        ctx.log("Beispiel-Add-on aus: kein Teil ohne Tabelle verfügbar");
                        s.put(DONE, true);
                        return StepResult.pass();
                    }
                    ShareTables current = ShareTables.current();
                    s.put(SAVED_TABLES, current);
                    ShareTables.install(withoutTable(current, ShareDevTables.EXAMPLE));
                    openExport(data);
                    return StepResult.pass();
                })
                .then("Export-Fenster", 100, (ctx, s, age) -> {
                    if (s.containsKey(DONE)) {
                        return StepResult.pass();
                    }
                    ShareScreen screen = shown(s);
                    if (screen == null || screen.exportCode().isEmpty()) {
                        return CovShareScript.wait(s, "Export-Fenster ohne Code");
                    }
                    List<String> errors = new ArrayList<>();
                    String tag = "no_table:" + ShareDevTables.EXAMPLE;
                    if (ShareLines.find(screen.exportLines(), tag, true).isEmpty()) {
                        errors.add("Zeile " + tag + " fehlt: " + tags(screen.exportLines()));
                    }
                    ShareDecoded decoded = ShareService.decode(screen.exportCode());
                    if (!decoded.ok() || !decoded.require().build().equals(data.build())) {
                        errors.add("Code liest sich nicht zum selben Bauplan: " + decoded.status() + " " + decoded.content().map(ShareContent::build));
                    }
                    ctx.log("ohne Tabelle: " + screen.exportCode().length() + " Zeichen, Zeile " + tag + ", Rahmen " + decoded.content().map(c -> c.build()
                            .frame()).orElse(null));
                    return check(errors);
                })
                .cleanup(ctx -> {
                    Object saved = ctx.state().get(SAVED_TABLES);
                    if (saved instanceof ShareTables tables) {
                        ShareTables.install(tables);
                    }
                    Minecraft.getInstance().gui.setScreen(null);
                })::tick;
    }

    private static SelfTest.ClientStep roundTripStep() {
        DroneData original = ShareSelfTests.freestyle();
        return CovShareScript.create()
                .then("Code mit allen Ebenen", 20, (ctx, s, age) -> {
                    saveOsd(s);
                    OsdConfig config = OsdClient.CONFIG.get().copy();
                    config.move(OsdElement.SPEED, 4, 9);
                    config.slot(OsdElement.SPEED).enabled = !config.slot(OsdElement.SPEED).enabled;
                    config.move(OsdElement.LINK_QUALITY, 2, 2);
                    String json = OsdLayoutCodec.encode(config);
                    s.put(OSD_JSON, json);
                    EnumSet<ShareLayer> all = EnumSet.complementOf(EnumSet.of(ShareLayer.CREATIVE));
                    String code = ShareService.encode(original, all, true, Optional.of(json));
                    s.put(CODE, code);
                    ctx.log("Code mit allen Ebenen: " + code.length() + " Zeichen, " + layersOf(code));
                    return StepResult.check(layersOf(code).equals(all), "Code ohne alle Ebenen: " + layersOf(code));
                })
                .server("Werkbank", (server, out) -> openBench(server, GameType.CREATIVE))
                .then("Import-Fenster", 400, CovShareSelfTests::openImportWindow)
                .then("einfügen und übernehmen", 200, (ctx, s, age) -> {
                    ShareScreen screen = shown(s);
                    if (screen == null) {
                        return CovShareScript.wait(s, "Import-Fenster erscheint nicht");
                    }
                    screen.setInput((String) s.get(CODE));
                    List<String> errors = new ArrayList<>();
                    ShareLines.Line head = ShareLines.status(screen.state().decoded());
                    Object[] args = head.text().getContents() instanceof TranslatableContents t ? t.getArgs() : new Object[0];
                    if (!"propwash.share.import.readable".equals(keyOf(head.text())) || args.length == 0 || !"PW3".equals(String.valueOf(args[0]))) {
                        errors.add("Kopfzeile nennt nicht PW3: " + keyOf(head.text()) + " " + List.of(args));
                    }
                    Optional<BuildStats> expected = PropwashApi.builds().analyze(original.build());
                    if (expected.isEmpty() || !screen.state().stats().equals(expected)) {
                        errors.add("Flugwerte der Vorschau " + screen.state().stats() + " statt " + expected);
                    }
                    if (!errors.isEmpty()) {
                        return check(errors);
                    }
                    ShareSettings.setApply(EnumSet.of(ShareLayer.PARTS, ShareLayer.PAINT, ShareLayer.TUNE, ShareLayer.OSD));
                    CompletableFuture<ImportResult> future = screen.apply();
                    if (future == null) {
                        return StepResult.fail("Übernehmen schickt nichts: " + screen.chosenLayers());
                    }
                    s.put(FUTURE, future);
                    ctx.log("Kopfzeile " + head.text().getString() + ", Flugwerte gleich der Analyse");
                    return StepResult.pass();
                })
                .then("Ergebnis", 400, (ctx, s, age) -> {
                    ImportResult result = result(s);
                    if (result == null) {
                        return CovShareScript.wait(s, "keine Antwort auf den Import");
                    }
                    if (result.status() != ImportStatus.APPLIED) {
                        return StepResult.fail("Import " + result);
                    }
                    if (!sameJson(OsdLayoutCodec.encode(OsdClient.CONFIG.get()), (String) s.get(OSD_JSON))) {
                        return StepResult.fail("OSD-Layout des Clients gleicht nicht dem Code");
                    }
                    ShareSettings.setExport(ShareLayer.DEFAULT_EXPORT);
                    Objects.requireNonNull(ShareScreen.active()).switchMode(ShareScreen.Mode.EXPORT);
                    return StepResult.pass();
                })
                .then("Werkbank-Export", 400, (ctx, s, age) -> {
                    ShareScreen screen = ShareScreen.active();
                    if (screen == null || screen.mode() != ShareScreen.Mode.EXPORT || screen.exportCode().isEmpty()) {
                        return CovShareScript.wait(s, "Werkbank liefert keinen Code");
                    }
                    ShareDecoded decoded = ShareService.decode(screen.exportCode());
                    if (!decoded.ok()) {
                        return StepResult.fail("Export unlesbar: " + decoded.status());
                    }
                    ShareContent content = decoded.require();
                    List<String> errors = new ArrayList<>();
                    if (!content.build().equals(original.build())) {
                        errors.add("Bauplan " + content.build());
                    }
                    if (!content.paint().equals(Optional.of(original.paint()))) {
                        errors.add("Lack " + content.paint());
                    }
                    if (!content.name().equals(original.name())) {
                        errors.add("Name " + content.name() + " statt " + original.name());
                    }
                    TuneSpec tune = content.tune().orElse(TuneSpec.EMPTY);
                    for (Map.Entry<String, Float> entry : original.tune().only(Pw3Params.TUNE_GROUPS).values().entrySet()) {
                        Float value = tune.values().get(entry.getKey());
                        if (value == null || Math.abs(value - entry.getValue()) > 1.0E-4F) {
                            errors.add("Tuning " + entry.getKey() + " = " + value);
                        }
                    }
                    ctx.log("Werkbank-Export " + screen.exportCode().length() + " Zeichen, Name " + content.name().orElse("–") + ", Ebenen " + content.layers());
                    return check(errors);
                })
                .cleanup(ctx -> {
                    restoreOsd(ctx.state());
                    closeAll(ctx);
                })::tick;
    }

    private static SelfTest.ClientStep addonsStep() {
        return CovShareScript.create()
                .then("P2 einfügen", 100, (ctx, s, age) -> {
                    Optional<String> jmp = Services.PLATFORM.modVersion(ShareVectors.JMP);
                    if (jmp.isEmpty()) {
                        ctx.log("Just More Parts nicht geladen: Fall nur im Lauf mit JMP");
                        return StepResult.pass();
                    }
                    if (age == 0) {
                        s.put(SAVED_INPUT, ShareSettings.lastInput());
                        Minecraft.getInstance().gui.setScreen(ShareHarness.window(ShareHarness.context(Optional.empty(), true), ShareScreen.Mode.IMPORT));
                    }
                    ShareScreen screen = shown(s);
                    if (screen == null) {
                        return CovShareScript.wait(s, "Import-Fenster erscheint nicht");
                    }
                    screen.setInput(ShareVectors.P2_JMP);
                    List<String> errors = new ArrayList<>();
                    List<ShareImportState.Addon> addons = screen.state().addons();
                    Optional<ShareImportState.Addon> addon = addons.stream().filter(a -> a.key().equals(ShareVectors.JMP)).findFirst();
                    if (addon.isEmpty()) {
                        errors.add("keine Zeile für Just More Parts: " + addons);
                    } else {
                        if (addon.get().problem()) {
                            errors.add("Just More Parts mit Problem " + addon.get().kind());
                        }
                        if (!addon.get().required().equals(Optional.of("1.0.0"))) {
                            errors.add("nötig " + addon.get().required());
                        }
                        if (!addon.get().installed().equals(jmp)) {
                            errors.add("installiert " + addon.get().installed() + " statt " + jmp.get());
                        }
                    }
                    for (ShareImportState.Addon other : addons) {
                        if (other.problem()) {
                            errors.add(other.key() + " mit " + other.kind());
                        }
                    }
                    for (ShareLines.Line line : screen.infoLines()) {
                        if (line.tag().startsWith(ShareLines.TAG_MISMATCH)) {
                            errors.add("Mismatch-Zeile");
                        }
                        if (line.tag().startsWith(ShareLines.TAG_ADDON) && !ShareLines.ICON_OK.equals(line.icon())) {
                            errors.add("Symbol " + line.icon() + " bei " + line.tag());
                        }
                    }
                    List<String> shown = new ArrayList<>();
                    for (ShareLines.Line line : screen.infoLines()) {
                        if (line.tag().startsWith(ShareLines.TAG_ADDON)) {
                            shown.add(line.text().getString());
                        }
                    }
                    ctx.log("Add-ons in diesem Code: " + shown);
                    return check(errors);
                })
                .cleanup(ctx -> {
                    Minecraft.getInstance().gui.setScreen(null);
                    ShareSettings.lastInput((String) ctx.state().getOrDefault(SAVED_INPUT, ""));
                })::tick;
    }

    private static SelfTest.ClientStep shoppingStep() {
        List<PaintSwatch> swatches = PaintSwatch.dyes();
        DroneData data = DroneData.of(ShareScene.freestyle()).withPaint(PaintSpec.DEFAULT.with("frame", swatches.get(0).rgb()).with("motors",
                swatches.get(1).rgb()));
        BuildSpec build = data.build();
        return CovShareScript.create()
                .server("Überleben mit Rahmen und zwei Motoren", (server, out) -> {
                    ServerPlayer player = openBench(server, GameType.SURVIVAL);
                    player.getInventory().add(PropwashApi.partItems().stack(build.frame()));
                    player.getInventory().add(PropwashApi.partItems().stack(build.motor()).copyWithCount(2));
                    player.containerMenu.broadcastChanges();
                })
                .then("Import-Fenster", 400, CovShareSelfTests::openImportWindow)
                .then("Einkaufsliste", 400, (ctx, s, age) -> {
                    ShareScreen screen = shown(s);
                    if (screen == null) {
                        return CovShareScript.wait(s, "Import-Fenster erscheint nicht");
                    }
                    if (!s.containsKey(CODE)) {
                        String code = ShareService.encode(data, EnumSet.of(ShareLayer.PAINT), true, Optional.empty());
                        s.put(CODE, code);
                        screen.setInput(code);
                    }
                    ShoppingList shopping = screen.shopping();
                    int total = 0;
                    for (ShareServer.Slot slot : ShareServer.slots(build)) {
                        total += slot.count();
                    }
                    int expected = total - 3;
                    if (shopping == null || shopping.missingPieces() != expected) {
                        return CovShareScript.wait(s, "Einkaufsliste " + (shopping == null ? "fehlt" : shopping.missingPieces() + " fehlend") + ", erwartet " + expected);
                    }
                    List<String> errors = new ArrayList<>();
                    if (ShareLines.find(screen.infoLines(), ShareLines.TAG_SHOPPING + build.frame() + ":have", true).isEmpty()) {
                        errors.add("Rahmen nicht als vorhanden: " + tags(screen.infoLines()));
                    }
                    Optional<ShareLines.Line> motors = ShareLines.find(screen.infoLines(), ShareLines.TAG_SHOPPING + build.motor() + ":missing", true);
                    if (motors.isEmpty() || !List.of(argsOf(motors.get().text())).toString().startsWith("[2, 4")) {
                        errors.add("Motoren nicht als 2 von 4 fehlend: " + motors.map(m -> List.of(argsOf(m.text()))));
                    }
                    Optional<ShareLines.Line> sum = line(screen.infoLines(), "propwash.share.shopping.total");
                    if (sum.isEmpty() || !String.valueOf(argsOf(sum.get().text())[0]).equals(String.valueOf(expected))) {
                        errors.add("Summe „Es fehlen N Teile“ falsch: " + sum.map(l -> l.text().getString()));
                    }
                    if (!errors.isEmpty()) {
                        return check(errors);
                    }
                    ShareSettings.setApply(EnumSet.of(ShareLayer.PARTS));
                    CompletableFuture<ImportResult> future = screen.apply();
                    if (future == null) {
                        return StepResult.fail("Übernehmen schickt nichts");
                    }
                    s.put(FUTURE, future);
                    s.put(COUNT, expected);
                    ctx.log("Einkaufsliste: Rahmen vorhanden, Motoren 2 von 4 fehlen, Summe " + sum.get().text().getString());
                    return StepResult.pass();
                })
                .then("ohne Teile abgelehnt", 300, (ctx, s, age) -> {
                    ImportResult result = result(s);
                    if (result == null) {
                        return CovShareScript.wait(s, "keine Antwort");
                    }
                    if (result.status() != ImportStatus.MISSING_PARTS || result.missingParts().size() != (int) s.get(COUNT)) {
                        return StepResult.fail("ohne Teile: " + result.status() + " mit " + result.missingParts().size() + " Teilen statt " + s.get(COUNT));
                    }
                    CovShareScript.shared(s).put("missing", List.copyOf(result.missingParts()));
                    return StepResult.pass();
                })
                .server("fehlende Teile geben", (server, out) -> {
                    ServerPlayer player = ShareSelfTests.player(server);
                    @SuppressWarnings("unchecked")
                    List<Identifier> missing = (List<Identifier>) out.get("missing");
                    for (Identifier id : missing) {
                        ShareServer.give(player, PropwashApi.partItems().stack(id));
                    }
                    player.containerMenu.broadcastChanges();
                })
                .then("Liste vollständig", 400, (ctx, s, age) -> {
                    ShareScreen screen = ShareScreen.active();
                    if (screen == null) {
                        return StepResult.fail("Fenster geschlossen");
                    }
                    if (line(screen.infoLines(), "propwash.share.shopping.complete").isEmpty()) {
                        return CovShareScript.wait(s, "„Alle Teile vorhanden“ fehlt: " + line(screen.infoLines(), "propwash.share.shopping.total")
                                .map(l -> l.text().getString()));
                    }
                    if (age < 20) {
                        return StepResult.pending();
                    }
                    ShareSettings.setApply(EnumSet.of(ShareLayer.PARTS, ShareLayer.PAINT));
                    CompletableFuture<ImportResult> future = screen.apply();
                    if (future == null) {
                        return CovShareScript.wait(s, "Übernehmen schickt nichts");
                    }
                    s.put(FUTURE, future);
                    return StepResult.pass();
                })
                .then("ohne Farbstoffe", 300, (ctx, s, age) -> {
                    ImportResult result = result(s);
                    if (result == null) {
                        return CovShareScript.wait(s, "keine Antwort");
                    }
                    if (result.status() == ImportStatus.BUSY) {
                        return retry(s);
                    }
                    PaintCost cost = PaintCost.of(DronePaint.DEFAULT, Conversions.paint(data.paint()));
                    if (result.status() != ImportStatus.MISSING_DYES || !result.missingDyes().equals(cost.dyes()) || !cost.custom().isEmpty()) {
                        return StepResult.fail("ohne Farbstoffe: " + result + ", Lack-Kosten " + cost.dyes());
                    }
                    CovShareScript.shared(s).put("dyes", cost.dyes());
                    ctx.log("ohne Farbstoffe: MISSING_DYES " + result.missingDyes() + " wie die Lack-Kosten");
                    return StepResult.pass();
                })
                .server("Farbstoffe geben", (server, out) -> {
                    ServerPlayer player = ShareSelfTests.player(server);
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> dyes = (Map<String, Integer>) out.get("dyes");
                    for (Map.Entry<String, Integer> dye : dyes.entrySet()) {
                        player.getInventory().add(new ItemStack(dyeItem(dye.getKey()), dye.getValue()));
                    }
                    player.containerMenu.broadcastChanges();
                })
                .then("mit Farbstoffen übernehmen", 300, (ctx, s, age) -> {
                    ShareScreen screen = ShareScreen.active();
                    if (screen == null) {
                        return StepResult.fail("Fenster geschlossen");
                    }
                    if (age < 25) {
                        return StepResult.pending();
                    }
                    CompletableFuture<ImportResult> future = screen.apply();
                    if (future == null) {
                        return CovShareScript.wait(s, "Übernehmen schickt nichts");
                    }
                    s.put(FUTURE, future);
                    return StepResult.pass();
                })
                .then("Lack übernommen", 300, (ctx, s, age) -> {
                    ImportResult result = result(s);
                    if (result == null) {
                        return CovShareScript.wait(s, "keine Antwort");
                    }
                    if (result.status() == ImportStatus.BUSY) {
                        return retry(s);
                    }
                    return StepResult.check(result.status() == ImportStatus.APPLIED, "mit Farbstoffen: " + result);
                })
                .server("Farbstoffe verbraucht", (server, out) -> {
                    ServerPlayer player = ShareSelfTests.player(server);
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> dyes = (Map<String, Integer>) out.get("dyes");
                    for (String dye : dyes.keySet()) {
                        int left = player.getInventory().countItem(dyeItem(dye));
                        if (left != 0) {
                            throw new IllegalStateException(dye + ": " + left + " Farbstoffe übrig");
                        }
                    }
                    if (!(player.containerMenu instanceof DroneWorkbenchMenu menu) || !menu.paint().equals(Conversions.paint(data.paint()))) {
                        throw new IllegalStateException("Lack nicht an der Werkbank-Drohne");
                    }
                })
                .cleanup(CovShareSelfTests::closeAll)::tick;
    }

    private static SelfTest.ClientStep singleLayersStep() {
        DroneData base = ShareSelfTests.freestyle();
        DroneData held = base.withTune(new TuneSpec(Map.of("rates.roll.rcRate", 1.25F, "pid.roll.p", 47.0F, "vtx.channel", 3.0F)))
                .withName("Ebenen-Probe");
        String tuneCode = ShareService.encode(base.withTune(new TuneSpec(Map.of("rates.roll.rcRate", 1.37F))), EnumSet.of(ShareLayer.TUNE), true,
                Optional.empty());
        String paintCode = ShareService.encode(base.withPaint(PaintSpec.DEFAULT.with("frame", 0x123456)), EnumSet.of(ShareLayer.PAINT), true,
                Optional.empty());
        return CovShareScript.create()
                .server("Drohne in die Hand", (server, out) -> giveHeld(server, held, GameType.CREATIVE))
                .then("Tab öffnen", 200, (ctx, s, age) -> {
                    if (PropwashClientApi.heldDrone().filter(d -> d.name().equals(held.name())).isEmpty() || age < 15) {
                        return CovShareScript.wait(s, "Drohne nicht in der Hand");
                    }
                    s.put(SAVED_INPUT, ShareSettings.lastInput());
                    s.put(KEYS, keyMappings());
                    s.put(BEFORE, PropwashClientApi.heldDrone().get());
                    ShareSettings.lastInput(tuneCode);
                    Minecraft.getInstance().gui.setScreen(new TabHarnessScreen(new ShareConfigTab()));
                    return StepResult.pass();
                })
                .then("Tuning übernehmen", 200, (ctx, s, age) -> sendTab(s, ShareConfigTab::sendTune))
                .then("nur Tuning geändert", 400, (ctx, s, age) -> {
                    StepResult answer = applied(s, ShareConfigTab::sendTune);
                    if (answer.status() != StepResult.Status.PASS) {
                        return answer;
                    }
                    DroneData before = (DroneData) s.get(BEFORE);
                    Optional<DroneData> now = PropwashClientApi.heldDrone();
                    Float roll = now.map(d -> d.tune().values().get("rates.roll.rcRate")).orElse(null);
                    if (roll == null || Math.abs(roll - 1.37F) > 1.0E-4F) {
                        return CovShareScript.wait(s, "Tuning nicht auf der Drohne: " + now.map(DroneData::tune));
                    }
                    DroneData after = now.get();
                    List<String> errors = new ArrayList<>();
                    if (!after.build().equals(before.build()) || !after.paint().equals(before.paint()) || !after.name().equals(before.name())) {
                        errors.add("Teile, Lack oder Name geändert: " + after);
                    }
                    if (after.tune().values().containsKey("pid.roll.p")) {
                        errors.add("fehlender Tuning-Wert nicht auf Standard: pid.roll.p = " + after.tune().values().get("pid.roll.p"));
                    }
                    if (!Objects.equals(after.tune().values().get("vtx.channel"), before.tune().values().get("vtx.channel"))) {
                        errors.add("Videokanal " + before.tune().values().get("vtx.channel") + " → " + after.tune().values().get("vtx.channel"));
                    }
                    if (!keyMappings().equals(s.get(KEYS))) {
                        errors.add("Tastenbelegung geändert");
                    }
                    s.put(AFTER, after);
                    ctx.log("Tuning übernommen: rcRate 1.37, pid.roll.p Standard, Videokanal " + after.tune().values().get("vtx.channel") + ", Tasten gleich");
                    if (errors.isEmpty()) {
                        Objects.requireNonNull(ShareConfigTab.active()).setInput(paintCode);
                    }
                    return check(errors);
                })
                .then("Lack übernehmen", 300, (ctx, s, age) -> age < 20 ? StepResult.pending() : sendTab(s, ShareConfigTab::sendPaint))
                .then("nur Lack geändert", 400, (ctx, s, age) -> {
                    StepResult answer = applied(s, ShareConfigTab::sendPaint);
                    if (answer.status() != StepResult.Status.PASS) {
                        return answer;
                    }
                    Optional<DroneData> now = PropwashClientApi.heldDrone();
                    if (!now.flatMap(d -> d.paint().color("frame")).equals(Optional.of(0x123456))) {
                        return CovShareScript.wait(s, "Lack nicht auf der Drohne");
                    }
                    DroneData before = (DroneData) s.get(AFTER);
                    DroneData after = now.get();
                    List<String> errors = new ArrayList<>();
                    if (!after.tune().equals(before.tune())) {
                        errors.add("Tuning durch „Lack übernehmen“ geändert: " + before.tune() + " → " + after.tune());
                    }
                    if (!after.build().equals(before.build()) || !after.name().equals(before.name())) {
                        errors.add("Teile oder Name geändert");
                    }
                    if (!keyMappings().equals(s.get(KEYS))) {
                        errors.add("Tastenbelegung geändert");
                    }
                    Minecraft.getInstance().gui.setScreen(null);
                    return check(errors);
                })
                .server("Werkbank mit Drohne", (server, out) -> {
                    ServerPlayer player = openBench(server, GameType.CREATIVE);
                    ImportResult result = PropwashApi.builds().applyImport(player, workbench(base, ImportLayer.PARTS, ImportLayer.PAINT, ImportLayer.TUNE));
                    if (result.status() != ImportStatus.APPLIED) {
                        throw new IllegalStateException("Drohne nicht in der Werkbank: " + result);
                    }
                })
                .then("Import-Fenster", 400, CovShareSelfTests::openImportWindow)
                .then("Werkbank nur TUNE", 300, (ctx, s, age) -> {
                    ShareScreen screen = shown(s);
                    if (screen == null || screen.context().result().isEmpty()) {
                        return CovShareScript.wait(s, "Import-Fenster ohne Werkbank-Drohne");
                    }
                    if (age < 15) {
                        return StepResult.pending();
                    }
                    s.put(BEFORE, screen.context().result().get());
                    screen.setInput(tuneCode);
                    ShareSettings.setApply(EnumSet.of(ShareLayer.TUNE));
                    return startApply(s, screen, EnumSet.of(ShareLayer.TUNE));
                })
                .then("Werkbank-Ergebnis nur Tuning", 400, (ctx, s, age) -> {
                    StepResult answer = windowApplied(s);
                    if (answer.status() != StepResult.Status.PASS) {
                        return answer;
                    }
                    Optional<DroneData> result = Objects.requireNonNull(ShareScreen.active()).context().result();
                    Float roll = result.map(d -> d.tune().values().get("rates.roll.rcRate")).orElse(null);
                    if (roll == null || Math.abs(roll - 1.37F) > 1.0E-4F) {
                        return CovShareScript.wait(s, "Werkbank-Drohne ohne neues Tuning: " + result.map(DroneData::tune));
                    }
                    DroneData before = (DroneData) s.get(BEFORE);
                    List<String> errors = new ArrayList<>();
                    if (!result.get().build().equals(before.build()) || !result.get().paint().equals(before.paint())) {
                        errors.add("Werkbank: Teile oder Lack durch nur TUNE geändert");
                    }
                    if (result.get().tune().values().containsKey("pid.roll.p")) {
                        errors.add("Werkbank: pid.roll.p nicht auf Standard");
                    }
                    s.put(AFTER, result.get());
                    return check(errors);
                })
                .then("Werkbank nur PAINT", 300, (ctx, s, age) -> {
                    ShareScreen screen = ShareScreen.active();
                    if (screen == null) {
                        return StepResult.fail("Fenster geschlossen");
                    }
                    if (age < 25) {
                        return StepResult.pending();
                    }
                    screen.setInput(paintCode);
                    ShareSettings.setApply(EnumSet.of(ShareLayer.PAINT));
                    return startApply(s, screen, EnumSet.of(ShareLayer.PAINT));
                })
                .then("Werkbank-Ergebnis nur Lack", 400, (ctx, s, age) -> {
                    StepResult answer = windowApplied(s);
                    if (answer.status() != StepResult.Status.PASS) {
                        return answer;
                    }
                    Optional<DroneData> result = Objects.requireNonNull(ShareScreen.active()).context().result();
                    if (!result.flatMap(d -> d.paint().color("frame")).equals(Optional.of(0x123456))) {
                        return CovShareScript.wait(s, "Werkbank-Drohne ohne neuen Lack");
                    }
                    DroneData before = (DroneData) s.get(AFTER);
                    return StepResult.check(result.get().tune().equals(before.tune()) && result.get().build().equals(before.build()),
                            "Werkbank: nur PAINT ändert Tuning oder Teile: " + before.tune() + " → " + result.get().tune());
                })
                .cleanup(ctx -> {
                    ShareSettings.setApply(ShareSettings.DEFAULT_APPLY);
                    ShareSettings.lastInput((String) ctx.state().getOrDefault(SAVED_INPUT, ""));
                    closeAll(ctx);
                })::tick;
    }

    private static SelfTest.ClientStep osdLocalStep() {
        DroneData held = ShareSelfTests.freestyle().withName("OSD-Probe");
        return CovShareScript.create()
                .server("Drohne in die Hand", (server, out) -> {
                    listen();
                    giveHeld(server, held, GameType.CREATIVE);
                })
                .then("Tab mit Deadcat-Code", 200, (ctx, s, age) -> {
                    if (PropwashClientApi.heldDrone().filter(d -> d.name().equals(held.name())).isEmpty() || age < 15) {
                        return CovShareScript.wait(s, "Drohne nicht in der Hand");
                    }
                    saveOsd(s);
                    s.put(SAVED_INPUT, ShareSettings.lastInput());
                    ShareSettings.lastInput(P3);
                    Minecraft.getInstance().gui.setScreen(new TabHarnessScreen(new ShareConfigTab()));
                    return StepResult.pass();
                })
                .then("OSD-Layout übernehmen", 200, (ctx, s, age) -> {
                    ShareConfigTab tab = ShareConfigTab.active();
                    if (tab == null || tab.frames() < 3) {
                        return CovShareScript.wait(s, "Tab erscheint nicht");
                    }
                    Optional<ShareOsd> osd = tab.state().content().flatMap(ShareContent::osd);
                    if (osd.isEmpty()) {
                        return StepResult.fail("Deadcat-Code ohne OSD-Ebene: " + tab.state().decoded().status());
                    }
                    OsdConfig expected = OsdLayoutCodec.decode(osd.get().json(), OsdClient.CONFIG.get());
                    s.put(OSD_JSON, OsdLayoutCodec.encode(expected));
                    s.put(BEFORE, PropwashClientApi.heldDrone().orElseThrow());
                    s.put(COUNT, IMPORTS.get());
                    if (!tab.applyOsd()) {
                        return StepResult.fail("applyOsd liefert false");
                    }
                    if (!sameJson(OsdLayoutCodec.encode(OsdClient.CONFIG.get()), (String) s.get(OSD_JSON))) {
                        return StepResult.fail("OSD-Layout des Clients gleicht nicht dem Code");
                    }
                    return StepResult.pass();
                })
                .pause(40)
                .then("Drohne und Server unberührt", 40, (ctx, s, age) -> {
                    List<String> errors = new ArrayList<>();
                    Optional<DroneData> after = PropwashClientApi.heldDrone();
                    if (!after.equals(Optional.of(s.get(BEFORE)))) {
                        errors.add("Drohne geändert: " + s.get(BEFORE) + " → " + after);
                    }
                    if (IMPORTS.get() != (int) s.get(COUNT)) {
                        errors.add("Import-Anfrage beim Server: " + IMPORT_LOG);
                    }
                    if (ClientApiImpl.PENDING.size() != 0) {
                        errors.add(ClientApiImpl.PENDING.size() + " offene Import-Anfragen");
                    }
                    if (!errors.isEmpty()) {
                        return check(errors);
                    }
                    ShareConfigTab tab = Objects.requireNonNull(ShareConfigTab.active());
                    CompletableFuture<ImportResult> future = tab.sendTune();
                    if (future == null) {
                        return StepResult.fail("Gegenprobe: „Tuning übernehmen“ schickt nichts");
                    }
                    s.put(FUTURE, future);
                    return StepResult.pass();
                })
                .then("Gegenprobe zählt die Anfrage", 300, (ctx, s, age) -> {
                    ImportResult result = result(s);
                    if (result == null) {
                        return CovShareScript.wait(s, "keine Antwort auf die Gegenprobe");
                    }
                    if (IMPORTS.get() != (int) s.get(COUNT) + 1) {
                        return CovShareScript.wait(s, "Gegenprobe nicht gezählt: " + IMPORTS.get() + " statt " + ((int) s.get(COUNT) + 1));
                    }
                    ctx.log("applyOsd: Layout gleich dem Code, Drohne unverändert, 0 Import-Anfragen; Gegenprobe Tuning " + result.status() + " zählt 1");
                    return StepResult.pass();
                })
                .cleanup(ctx -> {
                    restoreOsd(ctx.state());
                    ShareSettings.lastInput((String) ctx.state().getOrDefault(SAVED_INPUT, ""));
                    closeAll(ctx);
                })::tick;
    }

    private static SelfTest.ClientStep spectatorStep() {
        DroneData held = ShareSelfTests.freestyle().withName("Zuschauer-Probe");
        DroneData code = ShareSelfTests.freestyle().withTune(new TuneSpec(Map.of("rates.roll.rcRate", 1.55F))).withPaint(PaintSpec.DEFAULT.with("frame",
                0x445566));
        String text = ShareService.encode(code, EnumSet.of(ShareLayer.PAINT, ShareLayer.TUNE), true, Optional.empty());
        return CovShareScript.create()
                .server("Zuschauer an der Werkbank", (server, out) -> {
                    ServerPlayer player = giveHeld(server, held, GameType.CREATIVE);
                    player.setGameMode(GameType.SPECTATOR);
                    openMenu(player);
                    out.put("inventory", inventory(player));
                })
                .then("Zuschauer", 300, (ctx, s, age) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null || !mc.player.isSpectator() || age < 20) {
                        return CovShareScript.wait(s, "Spieler nicht im Zuschauermodus");
                    }
                    s.put(BEFORE, PropwashClientApi.heldDrone().orElse(null));
                    ShareSettings.lastInput(text);
                    WorkbenchContext context = benchContext();
                    if (context != null) {
                        ShareScreen.open(context, ShareScreen.Mode.IMPORT, false);
                        s.put(DONE, "Fenster");
                    } else if (age < 120) {
                        return CovShareScript.wait(s, "Werkbank im Zuschauermodus nicht offen");
                    } else {
                        s.put(FUTURE, PropwashClientApi.sendImport(new ImportRequest(code, EnumSet.of(ImportLayer.PAINT, ImportLayer.TUNE), Pw3Params.TUNE_GROUPS,
                                ImportTarget.WORKBENCH)));
                        s.put(DONE, "Anfrage ohne offene Werkbank");
                    }
                    return StepResult.pass();
                })
                .then("Fenster übernehmen", 300, (ctx, s, age) -> {
                    if (s.containsKey(FUTURE)) {
                        return StepResult.pass();
                    }
                    ShareScreen screen = shown(s);
                    if (screen == null) {
                        return CovShareScript.wait(s, "Import-Fenster erscheint nicht");
                    }
                    screen.setInput(text);
                    ShareSettings.setApply(EnumSet.of(ShareLayer.PAINT, ShareLayer.TUNE));
                    CompletableFuture<ImportResult> future = screen.apply();
                    if (future == null) {
                        return CovShareScript.wait(s, "Übernehmen schickt nichts");
                    }
                    s.put(FUTURE, future);
                    return StepResult.pass();
                })
                .then("Fenster abgelehnt", 300, (ctx, s, age) -> {
                    ImportResult result = result(s);
                    if (result == null) {
                        return CovShareScript.wait(s, "keine Antwort");
                    }
                    ctx.log(s.get(DONE) + ": " + result.status());
                    if (result.status() != ImportStatus.NOT_ALLOWED && !(result.status() == ImportStatus.NO_TARGET && !"Fenster".equals(s.get(DONE)))) {
                        return StepResult.fail("Import im Zuschauermodus: " + result);
                    }
                    s.remove(FUTURE);
                    Minecraft.getInstance().gui.setScreen(new TabHarnessScreen(new ShareConfigTab()));
                    return StepResult.pass();
                })
                .then("Tab", 300, (ctx, s, age) -> {
                    ShareConfigTab tab = ShareConfigTab.active();
                    if (tab == null || tab.frames() < 3 || age < 25) {
                        return CovShareScript.wait(s, "Tab erscheint nicht");
                    }
                    if (!s.containsKey(FUTURE)) {
                        CompletableFuture<ImportResult> future = tab.sendTune();
                        if (future == null) {
                            return StepResult.fail("„Tuning übernehmen“ schickt nichts");
                        }
                        s.put(FUTURE, future);
                    }
                    ImportResult result = result(s);
                    if (result == null) {
                        return CovShareScript.wait(s, "keine Antwort auf den Tab");
                    }
                    if (result.status() == ImportStatus.BUSY) {
                        s.remove(FUTURE);
                        return CovShareScript.wait(s, "BUSY");
                    }
                    ctx.log("Tab „Tuning übernehmen“: " + result.status());
                    return StepResult.check(result.status() == ImportStatus.NOT_ALLOWED, "Tab im Zuschauermodus: " + result);
                })
                .server("Befehl und Zustand", (server, out) -> {
                    ServerPlayer player = ShareSelfTests.player(server);
                    Capture capture = new Capture();
                    int code2 = execute(server, player, capture, "propwash code import " + text);
                    if (code2 != ShareCommands.FAILURE || !capture.failureKeys().contains("propwash.api.import.not_allowed")) {
                        throw new IllegalStateException("Befehl im Zuschauermodus: " + code2 + " " + capture.failureKeys() + " " + capture.successKeys());
                    }
                    if (!inventory(player).equals(out.get("inventory"))) {
                        throw new IllegalStateException("Inventar geändert: " + out.get("inventory") + " → " + inventory(player));
                    }
                    if (player.containerMenu instanceof DroneWorkbenchMenu menu && !menu.partsEmpty()) {
                        throw new IllegalStateException("Werkbank nicht mehr leer");
                    }
                    out.put("command", "FAILURE mit propwash.api.import.not_allowed");
                })
                .then("gehaltene Drohne unverändert", 40, (ctx, s, age) -> {
                    Object before = s.get(BEFORE);
                    Optional<DroneData> now = PropwashClientApi.heldDrone();
                    ctx.log("Befehl: " + CovShareScript.shared(s).get("command") + "; Inventar und Werkbank unverändert");
                    return StepResult.check(before != null && now.equals(Optional.of(before)), "Drohne geändert: " + before + " → " + now);
                })
                .cleanup(ctx -> {
                    ShareSettings.setApply(ShareSettings.DEFAULT_APPLY);
                    ShareSettings.lastInput("");
                    closeAll(ctx);
                })::tick;
    }

    static SelfTest.ClientStep otherLayersStep() {
        DroneData held = DroneData.of(ShareScene.freestyle()).withName("Deadcat-Probe");
        return CovShareScript.create()
                .server("Propwash-Drohne in die Hand", (server, out) -> giveHeld(server, held, GameType.CREATIVE))
                .then("Tab mit Deadcat-Code", 200, (ctx, s, age) -> {
                    if (PropwashClientApi.heldDrone().filter(d -> d.name().equals(held.name())).isEmpty() || age < 15) {
                        return CovShareScript.wait(s, "Drohne nicht in der Hand");
                    }
                    saveOsd(s);
                    s.put(SAVED_INPUT, ShareSettings.lastInput());
                    s.put(BEFORE, PropwashClientApi.heldDrone().get());
                    ShareSettings.lastInput(P3);
                    Minecraft.getInstance().gui.setScreen(new TabHarnessScreen(new ShareConfigTab()));
                    return StepResult.pass();
                })
                .then("Tuning übernehmen", 200, (ctx, s, age) -> {
                    ShareConfigTab tab = ShareConfigTab.active();
                    if (tab == null || tab.frames() < 3) {
                        return CovShareScript.wait(s, "Tab erscheint nicht");
                    }
                    ShareDecoded decoded = tab.state().decoded();
                    boolean jmp = ShareVectors.jmpLoaded();
                    if (!decoded.ok() || !jmp && decoded.partsKnown()) {
                        return StepResult.fail("Deadcat-Code: " + decoded.status() + ", Teile bekannt " + decoded.partsKnown());
                    }
                    ctx.log("Deadcat-Code: " + (jmp ? "mit" : "ohne") + " Just More Parts, unbekannte Teile " + decoded.unknownParts().size());
                    return sendTab(s, ShareConfigTab::sendTune);
                })
                .then("Tuning auf der Drohne", 400, (ctx, s, age) -> {
                    StepResult answer = applied(s, ShareConfigTab::sendTune);
                    if (answer.status() != StepResult.Status.PASS) {
                        return answer;
                    }
                    Optional<DroneData> now = PropwashClientApi.heldDrone();
                    Float roll = now.map(d -> d.tune().values().get("rates.roll.rcRate")).orElse(null);
                    if (roll == null || Math.abs(roll - 1.2F) > 1.0E-4F) {
                        return CovShareScript.wait(s, "Deadcat-Tuning nicht auf der Drohne: " + now.map(DroneData::tune));
                    }
                    DroneData before = (DroneData) s.get(BEFORE);
                    if (!now.get().build().equals(before.build()) || !now.get().name().equals(before.name())) {
                        return StepResult.fail("Teile oder Name geändert: " + now.get());
                    }
                    ShareConfigTab tab = Objects.requireNonNull(ShareConfigTab.active());
                    Optional<ShareOsd> osd = tab.state().content().flatMap(ShareContent::osd);
                    if (osd.isEmpty() || !tab.applyOsd()) {
                        return StepResult.fail("OSD übernehmen klappt nicht");
                    }
                    String expected = OsdLayoutCodec.encode(OsdLayoutCodec.decode(osd.get().json(), OsdClient.CONFIG.get()));
                    if (!sameJson(OsdLayoutCodec.encode(OsdClient.CONFIG.get()), expected)) {
                        return StepResult.fail("OSD des Clients gleicht nicht dem Code");
                    }
                    List<String> labels = new ArrayList<>();
                    for (GuiEventListener child : Minecraft.getInstance().gui.screen().children()) {
                        if (child instanceof FlatButton button) {
                            labels.add(keyOf(button.getMessage()));
                        }
                    }
                    ctx.log("Tuning (rcRate 1.2) und OSD übernommen, Teile und Name gleich; Knöpfe im Tab " + labels);
                    return StepResult.pass();
                })
                .cleanup(ctx -> {
                    restoreOsd(ctx.state());
                    ShareSettings.lastInput((String) ctx.state().getOrDefault(SAVED_INPUT, ""));
                    closeAll(ctx);
                })::tick;
    }

    static SelfTest.ClientStep otherLayersPaintStep() {
        DroneData held = DroneData.of(ShareScene.freestyle()).withName("Deadcat-Lack");
        return CovShareScript.create()
                .server("Propwash-Drohne in die Hand", (server, out) -> giveHeld(server, held, GameType.CREATIVE))
                .then("Tab mit Deadcat-Code", 200, (ctx, s, age) -> {
                    if (PropwashClientApi.heldDrone().filter(d -> d.name().equals(held.name())).isEmpty() || age < 15) {
                        return CovShareScript.wait(s, "Drohne nicht in der Hand");
                    }
                    StepResult code = deadcatCode(ctx, s);
                    if (code.status() != StepResult.Status.PASS) {
                        return code;
                    }
                    s.put(SAVED_INPUT, ShareSettings.lastInput());
                    s.put(BEFORE, PropwashClientApi.heldDrone().get());
                    ShareSettings.lastInput(P3);
                    Minecraft.getInstance().gui.setScreen(new TabHarnessScreen(new ShareConfigTab()));
                    return StepResult.pass();
                })
                .then("Lack übernehmen", 200, (ctx, s, age) -> sendTab(s, ShareConfigTab::sendPaint))
                .then("Lack auf der Drohne", 400, (ctx, s, age) -> {
                    StepResult answer = applied(s, ShareConfigTab::sendPaint);
                    if (answer.status() != StepResult.Status.PASS) {
                        return answer;
                    }
                    ShareContent code = (ShareContent) s.get(CODE);
                    Optional<DroneData> now = PropwashClientApi.heldDrone();
                    if (now.isEmpty() || !samePaint(now.get().paint(), code.paint().orElseThrow())) {
                        return CovShareScript.wait(s, "Deadcat-Lack nicht auf der Drohne: " + now.map(DroneData::paint));
                    }
                    DroneData before = (DroneData) s.get(BEFORE);
                    if (!now.get().build().equals(before.build()) || !now.get().name().equals(before.name()) || !now.get().tune().equals(before.tune())) {
                        return StepResult.fail("Teile, Name oder Tuning durch „Lack übernehmen“ geändert: " + now.get());
                    }
                    ctx.log("Tab: Lack des Deadcat-Codes auf der Propwash-Drohne, Teile, Name und Tuning gleich");
                    return StepResult.pass();
                })
                .cleanup(ctx -> {
                    ShareSettings.lastInput((String) ctx.state().getOrDefault(SAVED_INPUT, ""));
                    closeAll(ctx);
                })::tick;
    }

    static SelfTest.ClientStep otherLayersBenchStep() {
        return CovShareScript.create()
                .server("Werkbank mit Propwash-Drohne", (server, out) -> {
                    ServerPlayer player = openBench(server, GameType.CREATIVE);
                    ImportResult result = PropwashApi.builds().applyImport(player, workbench(DroneData.of(ShareScene.freestyle()), ImportLayer.PARTS));
                    if (result.status() != ImportStatus.APPLIED) {
                        throw new IllegalStateException("Drohne nicht in der Werkbank: " + result);
                    }
                })
                .then("Import-Fenster", 400, CovShareSelfTests::openImportWindow)
                .then("nur Lack und Tuning", 300, (ctx, s, age) -> {
                    ShareScreen screen = shown(s);
                    if (screen == null || screen.context().result().isEmpty()) {
                        return CovShareScript.wait(s, "Import-Fenster ohne Werkbank-Drohne");
                    }
                    if (age < 15) {
                        return StepResult.pending();
                    }
                    StepResult code = deadcatCode(ctx, s);
                    if (code.status() != StepResult.Status.PASS) {
                        return code;
                    }
                    s.put(SAVED_SUBSTITUTES, ShareSettings.substitutes());
                    ShareSettings.substitutes(false);
                    s.put(BEFORE, screen.context().result().get());
                    screen.setInput(P3);
                    if (screen.state().usingSubstitutes()) {
                        return StepResult.fail("Ersatzteile trotz ausgeschaltetem Schalter");
                    }
                    ShareSettings.setApply(EnumSet.of(ShareLayer.PAINT, ShareLayer.TUNE));
                    return startApply(s, screen, EnumSet.of(ShareLayer.PAINT, ShareLayer.TUNE));
                })
                .then("Werkbank-Ergebnis", 400, (ctx, s, age) -> {
                    StepResult answer = windowApplied(s);
                    if (answer.status() != StepResult.Status.PASS) {
                        return answer;
                    }
                    ShareContent code = (ShareContent) s.get(CODE);
                    Optional<DroneData> result = Objects.requireNonNull(ShareScreen.active()).context().result();
                    Float roll = result.map(d -> d.tune().values().get("rates.roll.rcRate")).orElse(null);
                    if (roll == null || Math.abs(roll - 1.2F) > 1.0E-4F || !samePaint(result.get().paint(), code.paint().orElseThrow())) {
                        return CovShareScript.wait(s, "Werkbank-Drohne ohne Lack oder Tuning des Codes: " + result);
                    }
                    DroneData before = (DroneData) s.get(BEFORE);
                    if (!result.get().build().equals(before.build())) {
                        return StepResult.fail("Werkbank: Teile durch nur Lack und Tuning geändert: " + result.get().build());
                    }
                    ctx.log("Werkbank-Fenster: Lack und Tuning (rcRate 1.2) des Deadcat-Codes übernommen, Teile gleich");
                    return StepResult.pass();
                })
                .cleanup(ctx -> {
                    ShareSettings.setApply(ShareSettings.DEFAULT_APPLY);
                    ShareSettings.substitutes((boolean) ctx.state().getOrDefault(SAVED_SUBSTITUTES, false));
                    closeAll(ctx);
                })::tick;
    }

    static SelfTest.ClientStep otherLayersCommandStep() {
        DroneData held = DroneData.of(ShareScene.freestyle()).withName("Deadcat-Befehl");
        return CovShareScript.create()
                .then("Deadcat-Code", 20, CovShareSelfTests::deadcatCode)
                .server("Befehl import im Überleben", (server, out) -> {
                    ShareContent code = ShareService.decode(P3).require();
                    ServerPlayer player = giveHeld(server, held.withPaint(code.paint().orElseThrow()), GameType.SURVIVAL);
                    Capture capture = new Capture();
                    int result = execute(server, player, capture, "propwash code import " + P3);
                    Optional<DroneData> now = ShareServer.held(player);
                    Float roll = now.map(d -> d.tune().values().get("rates.roll.rcRate")).orElse(null);
                    if (result != ShareCommands.SUCCESS || !capture.successKeys().contains(KEY + "held") || roll == null
                            || Math.abs(roll - 1.2F) > 1.0E-4F || !now.get().build().equals(held.build())) {
                        throw new IllegalStateException("import im Überleben: " + result + " " + capture.failureKeys() + ", Drohne " + now);
                    }
                })
                .then("Ergebnis", 20, (ctx, s, age) -> {
                    ctx.log("Befehl import im Überleben: Lack und Tuning (rcRate 1.2) des Deadcat-Codes auf der Propwash-Drohne, Teile gleich");
                    return StepResult.pass();
                })
                .cleanup(CovShareSelfTests::closeAll)::tick;
    }

    private static StepResult deadcatCode(SelfTest.StepContext ctx, Map<String, Object> s, int age) {
        return deadcatCode(ctx, s);
    }

    private static StepResult deadcatCode(SelfTest.StepContext ctx, Map<String, Object> s) {
        ShareDecoded decoded = ShareService.decode(P3);
        if (!decoded.ok() || decoded.require().paint().isEmpty() || decoded.require().tune().isEmpty() || !ShareVectors.jmpLoaded() && decoded.partsKnown()) {
            return StepResult.fail("Deadcat-Code: " + decoded.status() + ", Teile bekannt " + decoded.partsKnown());
        }
        ctx.log("Deadcat-Code: " + (ShareVectors.jmpLoaded() ? "mit" : "ohne") + " Just More Parts, unbekannte Teile " + decoded.unknownParts().size());
        s.put(CODE, decoded.require());
        return StepResult.pass();
    }

    private static boolean samePaint(PaintSpec a, PaintSpec b) {
        return Conversions.paint(a).equals(Conversions.paint(b));
    }

    private static SelfTest.ClientStep mismatchLayersStep() {
        return CovShareScript.create()
                .then("Code vorbereiten", 20, (ctx, s, age) -> {
                    saveOsd(s);
                    OsdConfig config = OsdClient.CONFIG.get().copy();
                    config.move(OsdElement.SPEED, 6, 10);
                    String json = OsdLayoutCodec.encode(config);
                    s.put(OSD_JSON, json);
                    s.put(CODE, mismatchCode(json));
                    return StepResult.pass();
                })
                .server("Werkbank mit Drohne", (server, out) -> {
                    ServerPlayer player = openBench(server, GameType.CREATIVE);
                    ImportResult result = PropwashApi.builds().applyImport(player, workbench(DroneData.of(ShareScene.freestyle()), ImportLayer.PARTS));
                    if (result.status() != ImportStatus.APPLIED) {
                        throw new IllegalStateException("Drohne nicht in der Werkbank: " + result);
                    }
                })
                .then("Import-Fenster", 400, CovShareSelfTests::openImportWindow)
                .then("nur Lack, Tuning und OSD", 300, (ctx, s, age) -> {
                    ShareScreen screen = shown(s);
                    if (screen == null || screen.context().result().isEmpty()) {
                        return CovShareScript.wait(s, "Import-Fenster ohne Werkbank-Drohne");
                    }
                    screen.setInput((String) s.get(CODE));
                    ShareDecoded decoded = screen.state().decoded();
                    List<String> errors = new ArrayList<>();
                    if (!decoded.ok() || !decoded.partsLocked()) {
                        return StepResult.fail("Probe-Code ohne falsche Prüfsumme: " + decoded.status());
                    }
                    if (ShareLines.find(screen.infoLines(), ShareLines.TAG_MISMATCH, true).isEmpty()) {
                        errors.add("Banner fehlt");
                    }
                    ShareSettings.setApply(EnumSet.of(ShareLayer.PAINT, ShareLayer.TUNE, ShareLayer.OSD));
                    if (!screen.chosenLayers().equals(EnumSet.of(ShareLayer.PAINT, ShareLayer.TUNE, ShareLayer.OSD)) || screen.state().forced()) {
                        errors.add("gewählte Ebenen " + screen.chosenLayers() + ", bestätigt " + screen.state().forced());
                    }
                    if (!errors.isEmpty()) {
                        return check(errors);
                    }
                    s.put(BEFORE, screen.context().result().get());
                    CompletableFuture<ImportResult> future = screen.apply();
                    if (future == null) {
                        return StepResult.fail("Übernehmen ohne Bestätigung schickt nichts");
                    }
                    s.put(FUTURE, future);
                    return StepResult.pass();
                })
                .then("übernommen ohne Rückfrage", 400, (ctx, s, age) -> {
                    StepResult answer = windowApplied(s);
                    if (answer.status() != StepResult.Status.PASS) {
                        return answer;
                    }
                    Optional<DroneData> result = Objects.requireNonNull(ShareScreen.active()).context().result();
                    if (!result.flatMap(d -> d.paint().color("frame")).equals(Optional.of(0x2D2D80))) {
                        return CovShareScript.wait(s, "Lack nicht an der Werkbank-Drohne: " + result.map(DroneData::paint));
                    }
                    List<String> errors = new ArrayList<>();
                    Float roll = result.get().tune().values().get("rates.roll.rcRate");
                    if (roll == null || Math.abs(roll - 1.11F) > 1.0E-4F) {
                        errors.add("Tuning " + result.get().tune());
                    }
                    if (!sameJson(OsdLayoutCodec.encode(OsdClient.CONFIG.get()), (String) s.get(OSD_JSON))) {
                        errors.add("OSD nicht übernommen");
                    }
                    DroneData before = (DroneData) s.get(BEFORE);
                    if (!result.get().build().equals(before.build())) {
                        errors.add("Teile trotz Sperre geändert");
                    }
                    ctx.log("Prüfsumme falsch: Lack, Tuning und OSD ohne Rückfrage APPLIED, Name " + result.get().name().orElse("–") + " (hängt an den Teilen)");
                    return check(errors);
                })
                .cleanup(ctx -> {
                    restoreOsd(ctx.state());
                    ShareSettings.setApply(ShareSettings.DEFAULT_APPLY);
                    ShareSettings.lastInput("");
                    closeAll(ctx);
                })::tick;
    }

    private static SelfTest.ClientStep exportCommandStep() {
        DroneData held = ShareSelfTests.freestyle();
        return CovShareScript.create()
                .server("export", (server, out) -> {
                    ServerPlayer player = giveHeld(server, held, GameType.CREATIVE);
                    List<String> errors = new ArrayList<>();
                    Capture plain = new Capture();
                    int result = execute(server, player, plain, "propwash code export");
                    String code = plain.copyValue();
                    if (result != ShareCommands.SUCCESS || code == null) {
                        errors.add("export ohne Argument: " + result + ", Kopier-Klick " + code + " " + plain.successKeys());
                    } else {
                        Set<ShareLayer> layers = layersOf(code);
                        if (!layers.equals(EnumSet.of(ShareLayer.PARTS, ShareLayer.PAINT, ShareLayer.TUNE, ShareLayer.NAME))) {
                            errors.add("export ohne Argument mit " + layers);
                        }
                        if (!plain.successKeys().contains(KEY + "export") || !plain.plainTexts().contains(code)) {
                            errors.add("Rückmeldung ohne Kopfzeile oder Code: " + plain.successKeys());
                        }
                        out.put("plain", code.length() + " Zeichen " + layers);
                    }
                    Capture chosen = new Capture();
                    result = execute(server, player, chosen, "propwash code export parts,paint,name");
                    String second = chosen.copyValue();
                    if (result != ShareCommands.SUCCESS || second == null || !layersOf(second).equals(EnumSet.of(ShareLayer.PARTS, ShareLayer.PAINT,
                            ShareLayer.NAME))) {
                        errors.add("export parts,paint,name: " + result + " " + (second == null ? "kein Code" : layersOf(second)));
                    }
                    Capture bad = new Capture();
                    result = execute(server, player, bad, "propwash code export parts,foo");
                    if (result != ShareCommands.FAILURE || !bad.failureKeys().contains(KEY + "bad_layer")) {
                        errors.add("export parts,foo: " + result + " " + bad.failureKeys());
                    }
                    player.getInventory().clearContent();
                    Capture none = new Capture();
                    result = execute(server, player, none, "propwash code export");
                    if (result != ShareCommands.FAILURE || !none.failureKeys().contains(KEY + "no_drone")) {
                        errors.add("export ohne Drohne: " + result + " " + none.failureKeys());
                    }
                    if (!errors.isEmpty()) {
                        throw new IllegalStateException(String.join("; ", errors));
                    }
                })
                .then("Ergebnis", 20, (ctx, s, age) -> {
                    ctx.log("export: " + CovShareScript.shared(s).get("plain") + ", Kopier-Klick CopyToClipboard, parts,paint,name genau so, Fehler bad_layer "
                            + "und no_drone");
                    return StepResult.pass();
                })
                .cleanup(CovShareSelfTests::closeAll)::tick;
    }

    private static SelfTest.ClientStep importCommandStep() {
        List<PaintSwatch> swatches = PaintSwatch.dyes();
        DroneData held = DroneData.of(ShareScene.freestyle()).withTune(new TuneSpec(Map.of("rates.roll.rcRate", 1.25F))).withName("Befehl-Probe");
        PaintSpec paint = PaintSpec.DEFAULT.with("frame", swatches.get(2).rgb()).with("motors", swatches.get(3).rgb());
        DroneData wanted = held.withPaint(paint).withTune(new TuneSpec(Map.of("rates.roll.rcRate", 1.42F)));
        String code = ShareService.encode(wanted, EnumSet.of(ShareLayer.PAINT, ShareLayer.TUNE), true, Optional.empty());
        return CovShareScript.create()
                .server("Überleben mit Drohne", (server, out) -> {
                    ServerPlayer player = giveHeld(server, held, GameType.SURVIVAL);
                    List<String> errors = new ArrayList<>();
                    Capture without = new Capture();
                    int result = execute(server, player, without, "propwash code import " + code);
                    if (result != ShareCommands.FAILURE || !without.failureKeys().contains(KEY + "missing_dyes") || !without.failureKeys().contains(KEY + "held")) {
                        errors.add("ohne Farbstoffe: " + result + " " + without.failureKeys() + " " + without.successKeys());
                    }
                    out.put("without", ShareServer.held(player).map(d -> "Tuning nach dem Fehlschlag " + d.tune().values().get("rates.roll.rcRate"))
                            .orElse("–"));
                    PaintCost cost = PaintCost.of(DronePaint.DEFAULT, Conversions.paint(paint));
                    for (Map.Entry<String, Integer> dye : cost.dyes().entrySet()) {
                        player.getInventory().add(new ItemStack(dyeItem(dye.getKey()), dye.getValue()));
                    }
                    Capture with = new Capture();
                    result = execute(server, player, with, "propwash code import " + code);
                    if (result != ShareCommands.SUCCESS || !with.successKeys().contains(KEY + "held") || !with.successKeys().contains(KEY + "parts_at_workbench")) {
                        errors.add("mit Farbstoffen: " + result + " " + with.successKeys() + " " + with.failureKeys());
                    }
                    Optional<DroneData> now = ShareServer.held(player);
                    if (now.isEmpty() || !now.get().paint().equals(paint) || !Objects.equals(now.get().tune().values().get("rates.roll.rcRate"), 1.42F)
                            || !now.get().build().equals(held.build())) {
                        errors.add("gehaltene Drohne danach " + now);
                    }
                    for (String dye : cost.dyes().keySet()) {
                        if (player.getInventory().countItem(dyeItem(dye)) != 0) {
                            errors.add("Farbstoff " + dye + " nicht verbraucht");
                        }
                    }
                    if (!errors.isEmpty()) {
                        throw new IllegalStateException(String.join("; ", errors));
                    }
                    out.put("cost", cost.dyes().toString());
                })
                .then("Ergebnis", 20, (ctx, s, age) -> {
                    Map<String, Object> out = CovShareScript.shared(s);
                    ctx.log("import im Überleben: ohne Farbstoffe missing_dyes (" + out.get("without") + "), mit " + out.get("cost")
                            + " Tuning und Lack auf der Drohne, Teile gleich, Hinweis parts_at_workbench");
                    return StepResult.pass();
                })
                .cleanup(CovShareSelfTests::closeAll)::tick;
    }

    private static StepResult tabCountStep(SelfTest.StepContext ctx) {
        String title = Component.translatable("propwash.share.config.title").getString();
        List<String> share = new ArrayList<>();
        List<String> all = new ArrayList<>();
        for (ConfigTab tab : ConfigTabs.create()) {
            all.add(tab.id());
            if (tab.title().getString().equals(title) || tab.id().contains("sharecode")) {
                share.add(tab.id() + " „" + tab.title().getString() + "“");
            }
        }
        int buttons = 0;
        for (WorkbenchExtension extension : PropwashClientApi.workbenchExtensions()) {
            if (extension.id().getPath().contains("share") || extension.getClass().getSimpleName().contains("Share")) {
                buttons++;
            }
        }
        ctx.log("Just More Parts " + Services.PLATFORM.modVersion(ShareVectors.JMP).orElse("nicht geladen") + ": Tabs „" + title + "“ " + share
                + ", Werkbank-Knöpfe Sharecode " + buttons + ", alle Tabs " + all.size());
        return StepResult.check(share.size() == 1 && buttons == 1, "Tabs „Sharecode“ " + share + ", Werkbank-Knöpfe " + buttons);
    }

    private static SelfTest.ClientStep cameraStep() {
        DroneData plain = DroneData.of(ShareScene.freestyle()).withName("Kamera-Probe");
        DroneData tilted = plain.withTune(new TuneSpec(Map.of(TuneSpec.CAMERA_UPTILT, 42.0F))).withName("Kamera-42");
        return CovShareScript.create()
                .server("Drohne in die Hand", (server, out) -> giveHeld(server, plain, GameType.CREATIVE))
                .then("Tab mit Deadcat-Code", 200, (ctx, s, age) -> {
                    if (PropwashClientApi.heldDrone().filter(d -> d.name().equals(plain.name())).isEmpty() || age < 15) {
                        return CovShareScript.wait(s, "Drohne nicht in der Hand");
                    }
                    s.put(SAVED_INPUT, ShareSettings.lastInput());
                    ShareSettings.lastInput(P3);
                    ctx.log("Deadcat-Code P3 (Just More Parts " + (ShareVectors.jmpLoaded() ? "geladen" : "nicht geladen, Teile unbekannt") + ")");
                    Minecraft.getInstance().gui.setScreen(new TabHarnessScreen(new ShareConfigTab()));
                    return StepResult.pass();
                })
                .then("Tuning übernehmen", 200, (ctx, s, age) -> sendTab(s, ShareConfigTab::sendTune))
                .then("Kamerawinkel 35°", 400, (ctx, s, age) -> {
                    StepResult answer = applied(s, ShareConfigTab::sendTune);
                    if (answer.status() != StepResult.Status.PASS) {
                        return answer;
                    }
                    float uptilt = heldUptilt();
                    if (Math.abs(uptilt - 35.0F) > 1.0E-3F) {
                        return CovShareScript.wait(s, "Kamerawinkel der Drohne " + uptilt + "°");
                    }
                    Minecraft.getInstance().gui.setScreen(null);
                    ctx.log("Deadcat-Code mit Tuning: Kamerawinkel der Drohne 35°");
                    return StepResult.pass();
                })
                .server("eigene Drohne mit 42°", (server, out) -> giveHeld(server, tilted, GameType.CREATIVE))
                .then("mit Tuning exportieren", 200, (ctx, s, age) -> {
                    if (PropwashClientApi.heldDrone().filter(d -> d.name().equals(tilted.name())).isEmpty() || age < 15) {
                        return CovShareScript.wait(s, "Drohne mit 42° nicht in der Hand");
                    }
                    if (Math.abs(heldUptilt() - 42.0F) > 1.0E-3F) {
                        return StepResult.fail("eigene Drohne hat " + heldUptilt() + "° statt 42°");
                    }
                    ShareSettings.setExport(EnumSet.of(ShareLayer.TUNE));
                    String code = ShareService.encode(PropwashClientApi.heldDrone().get(), ShareSettings.exportLayers(), true, Optional.empty());
                    Float exported = ShareService.decode(code).content().flatMap(ShareContent::tune).map(t -> t.values().get(TuneSpec.CAMERA_UPTILT)).orElse(null);
                    if (exported == null || Math.abs(exported - 42.0F) > 1.0E-3F) {
                        return StepResult.fail("Export mit Tuning ohne 42°: " + exported);
                    }
                    s.put(CODE, code);
                    ShareSettings.setExport(ShareLayer.DEFAULT_EXPORT);
                    return StepResult.pass();
                })
                .server("Werkbank", (server, out) -> openBench(server, GameType.CREATIVE))
                .then("Import-Fenster", 400, CovShareSelfTests::openImportWindow)
                .then("mit Tuning importieren", 300, (ctx, s, age) -> {
                    ShareScreen screen = shown(s);
                    if (screen == null) {
                        return CovShareScript.wait(s, "Import-Fenster erscheint nicht");
                    }
                    screen.setInput((String) s.get(CODE));
                    ShareSettings.setApply(EnumSet.of(ShareLayer.PARTS, ShareLayer.TUNE));
                    return startApply(s, screen, EnumSet.of(ShareLayer.PARTS, ShareLayer.TUNE));
                })
                .then("42° in der Werkbank", 400, (ctx, s, age) -> {
                    StepResult answer = windowApplied(s);
                    if (answer.status() != StepResult.Status.PASS) {
                        return answer;
                    }
                    float uptilt = benchUptilt();
                    if (Math.abs(uptilt - 42.0F) > 1.0E-3F) {
                        return CovShareScript.wait(s, "Werkbank-Drohne mit " + uptilt + "°");
                    }
                    Minecraft.getInstance().gui.setScreen(null);
                    return StepResult.pass();
                })
                .server("frische Werkbank", (server, out) -> {
                    ServerPlayer player = ShareSelfTests.player(server);
                    player.closeContainer();
                    player.level().removeBlock(ShareSelfTests.benchPos(player), false);
                    openBench(server, GameType.CREATIVE);
                })
                .then("Import-Fenster ohne Tuning", 400, CovShareSelfTests::openImportWindow)
                .then("ohne Tuning importieren", 300, (ctx, s, age) -> {
                    ShareScreen screen = shown(s);
                    if (screen == null) {
                        return CovShareScript.wait(s, "Import-Fenster erscheint nicht");
                    }
                    if (screen.context().result().isPresent()) {
                        return CovShareScript.wait(s, "Werkbank nicht leer");
                    }
                    screen.setInput((String) s.get(CODE));
                    ShareSettings.setApply(EnumSet.of(ShareLayer.PARTS));
                    return startApply(s, screen, EnumSet.of(ShareLayer.PARTS));
                })
                .then("Rahmenstandard in der Werkbank", 400, (ctx, s, age) -> {
                    StepResult answer = windowApplied(s);
                    if (answer.status() != StepResult.Status.PASS) {
                        return answer;
                    }
                    Optional<DroneData> result = Objects.requireNonNull(ShareScreen.active()).context().result();
                    if (result.isEmpty()) {
                        return CovShareScript.wait(s, "Werkbank ohne Drohne");
                    }
                    DroneBuild build = Conversions.build(result.get().build()).orElseThrow();
                    float standard = CameraMount.defaultUptilt(build.frame());
                    float uptilt = benchUptilt();
                    if (Math.abs(uptilt - standard) > 1.0E-3F) {
                        return StepResult.fail("ohne Tuning " + uptilt + "° statt Rahmenstandard " + standard + "°");
                    }
                    ctx.log("eigene Drohne 42°: mit Tuning 42°, ohne Tuning Rahmenstandard " + standard + "°");
                    return StepResult.pass();
                })
                .cleanup(ctx -> {
                    ShareSettings.setApply(ShareSettings.DEFAULT_APPLY);
                    ShareSettings.setExport(ShareLayer.DEFAULT_EXPORT);
                    ShareSettings.lastInput((String) ctx.state().getOrDefault(SAVED_INPUT, ""));
                    closeAll(ctx);
                })::tick;
    }

    private static float heldUptilt() {
        return PropwashClientApi.heldDrone().flatMap(d -> Conversions.build(d.build()).map(b -> CameraMount.uptilt(b, d.tune().values())))
                .orElse(Float.NaN);
    }

    private static float benchUptilt() {
        WorkbenchContext context = benchContext();
        ShareScreen screen = ShareScreen.active();
        Optional<DroneData> result = screen != null ? screen.context().result() : context == null ? Optional.empty() : context.result();
        return result.flatMap(d -> Conversions.build(d.build()).map(b -> CameraMount.uptilt(b, d.tune().values()))).orElse(Float.NaN);
    }

    static String mismatchCode(String osdJson) {
        String json;
        try (InputStream in = ShareTables.class.getResourceAsStream(ShareTables.TABLE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Tabelle von Propwash fehlt");
            }
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        String a = "{\"id\": \"m2207_1750\"";
        String b = "{\"id\": \"m2306_1860\"";
        String swapped = json.replace(a, "\u0000").replace(b, a).replace("\u0000", b);
        ShareTableJson.Parsed parsed = ShareTableJson.parse(ShareTables.PROPWASH, swapped);
        ShareTable fake = parsed.table().orElseThrow(() -> new IllegalStateException("Probe-Tabelle ungültig: " + parsed.problems()));
        ShareTables tables = ShareTables.builder().installed(fake).directory(ShareTables.current().directory()).parts(ShareTables.REGISTERED).build();
        ShareContent content = new ShareContent(ShareScene.freestyle(), Optional.of(PaintSpec.DEFAULT.with("frame", 0x2D2D80)),
                Optional.of(new TuneSpec(Map.of("rates.roll.rcRate", 1.11F))), Optional.of(new ShareOsd(OsdLayoutCodec.VERSION, osdJson)),
                Optional.of("Prüfsummen-Probe"));
        return Pw3Codec.encode(content, tables, Pw3Codec.Options.NO_INFO, Pw3Carry.EMPTY);
    }

    static ShareTables withoutTable(ShareTables current, String namespace) {
        ShareTables.Builder builder = ShareTables.builder().directory(current.directory()).parts(ShareTables.REGISTERED);
        for (ShareTable table : current.installedTables().values()) {
            if (!table.namespace().equals(namespace)) {
                builder.installed(table);
            }
        }
        List<String> namespaces = new ArrayList<>(current.installedTables().keySet());
        namespaces.add(namespace);
        for (String ns : namespaces) {
            current.version(ns).ifPresent(v -> builder.version(ns, v));
            current.mod(ns).ifPresent(m -> builder.mod(ns, m));
        }
        return builder.build();
    }

    private static StepResult openImportWindow(SelfTest.StepContext ctx, Map<String, Object> s, int age) {
        WorkbenchContext context = benchContext();
        if (context == null) {
            return CovShareScript.wait(s, "Werkbank erscheint nicht: " + Minecraft.getInstance().gui.screen());
        }
        if (age < 10) {
            return StepResult.pending();
        }
        ShareScreen.open(context, ShareScreen.Mode.IMPORT, false);
        s.remove(FUTURE);
        return StepResult.pass();
    }

    private static StepResult startApply(Map<String, Object> s, ShareScreen screen, Set<ShareLayer> expected) {
        if (!screen.chosenLayers().equals(expected)) {
            return StepResult.fail("gewählte Ebenen " + screen.chosenLayers() + " statt " + expected);
        }
        CompletableFuture<ImportResult> future = screen.apply();
        if (future == null) {
            return CovShareScript.wait(s, "Übernehmen schickt nichts");
        }
        s.put(FUTURE, future);
        return StepResult.pass();
    }

    private static StepResult windowApplied(Map<String, Object> s) {
        ImportResult result = result(s);
        if (result == null) {
            return CovShareScript.wait(s, "keine Antwort auf den Import");
        }
        if (result.status() == ImportStatus.BUSY) {
            ShareScreen screen = ShareScreen.active();
            CompletableFuture<ImportResult> again = screen == null ? null : screen.apply();
            if (again != null) {
                s.put(FUTURE, again);
            }
            return CovShareScript.wait(s, "Import BUSY");
        }
        if (result.status() != ImportStatus.APPLIED && result.status() != ImportStatus.UNCHANGED) {
            return StepResult.fail("Import " + result);
        }
        return StepResult.pass();
    }

    private interface TabAction {
        @Nullable CompletableFuture<ImportResult> send(ShareConfigTab tab);
    }

    private static StepResult sendTab(Map<String, Object> s, TabAction action) {
        ShareConfigTab tab = ShareConfigTab.active();
        if (tab == null || tab.frames() < 3) {
            return CovShareScript.wait(s, "Tab erscheint nicht");
        }
        CompletableFuture<ImportResult> future = action.send(tab);
        if (future == null) {
            return CovShareScript.wait(s, "Tab schickt nichts");
        }
        s.put(FUTURE, future);
        return StepResult.pass();
    }

    private static StepResult applied(Map<String, Object> s, TabAction action) {
        ImportResult result = result(s);
        if (result == null) {
            return CovShareScript.wait(s, "keine Antwort vom Server");
        }
        if (result.status() == ImportStatus.BUSY) {
            ShareConfigTab tab = ShareConfigTab.active();
            CompletableFuture<ImportResult> again = tab == null ? null : action.send(tab);
            if (again != null) {
                s.put(FUTURE, again);
            }
            return CovShareScript.wait(s, "BUSY");
        }
        if (result.status() != ImportStatus.APPLIED) {
            return StepResult.fail("Antwort " + result);
        }
        return StepResult.pass();
    }

    private static StepResult retry(Map<String, Object> s) {
        ShareScreen screen = ShareScreen.active();
        CompletableFuture<ImportResult> again = screen == null ? null : screen.apply();
        if (again != null) {
            s.put(FUTURE, again);
        }
        return CovShareScript.wait(s, "BUSY");
    }

    @SuppressWarnings("unchecked")
    private static @Nullable ImportResult result(Map<String, Object> s) {
        CompletableFuture<ImportResult> future = (CompletableFuture<ImportResult>) s.get(FUTURE);
        return future != null && future.isDone() ? future.getNow(null) : null;
    }

    static @Nullable WorkbenchContext benchContext() {
        WorkbenchContext context = ShareWorkbenchExtension.lastContext();
        Minecraft mc = Minecraft.getInstance();
        return context != null && context.screen() == mc.gui.screen() ? context : null;
    }

    private static @Nullable ShareScreen shown(Map<String, Object> s) {
        ShareScreen screen = ShareScreen.active();
        if (screen == null || Minecraft.getInstance().gui.screen() != screen || screen.frames() < 3) {
            return null;
        }
        return screen;
    }

    private static void openExport(DroneData data) {
        Minecraft.getInstance().gui.setScreen(ShareHarness.window(ShareHarness.context(Optional.of(data), true), ShareScreen.Mode.EXPORT));
    }

    static @Nullable FlatButton button(Screen screen, String key) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof FlatButton button && key.equals(keyOf(button.getMessage()))) {
                return button;
            }
        }
        return null;
    }

    static String tooltipKey(AbstractWidget widget) {
        try {
            Field holderField = AbstractWidget.class.getDeclaredField("tooltip");
            holderField.setAccessible(true);
            Object holder = holderField.get(widget);
            Tooltip tooltip = holder instanceof WidgetTooltipHolder h ? h.get() : null;
            if (tooltip == null) {
                return "–";
            }
            Field message = Tooltip.class.getDeclaredField("message");
            message.setAccessible(true);
            return keyOf((Component) message.get(tooltip));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "Fehler " + e;
        }
    }

    static String keyOf(@Nullable Component component) {
        if (component == null) {
            return "–";
        }
        return component.getContents() instanceof TranslatableContents t ? t.getKey() : component.getString();
    }

    static Object[] argsOf(Component component) {
        return component.getContents() instanceof TranslatableContents t ? t.getArgs() : new Object[0];
    }

    private static Optional<ShareLines.Line> line(List<ShareLines.Line> lines, String key) {
        for (ShareLines.Line line : lines) {
            if (key.equals(keyOf(line.text()))) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }

    static List<String> tags(List<ShareLines.Line> lines) {
        List<String> out = new ArrayList<>();
        for (ShareLines.Line line : lines) {
            if (!line.tag().isEmpty()) {
                out.add(line.tag());
            }
        }
        return out;
    }

    static Set<ShareLayer> layersOf(String code) {
        ShareDecoded decoded = ShareService.decode(code);
        EnumSet<ShareLayer> out = EnumSet.noneOf(ShareLayer.class);
        out.addAll(decoded.layers());
        return out;
    }

    static boolean sameJson(String a, String b) {
        return JsonParser.parseString(a).equals(JsonParser.parseString(b));
    }

    private static Map<String, String> keyMappings() {
        Map<String, String> out = new LinkedHashMap<>();
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            out.put(mapping.getName(), mapping.saveString());
        }
        return out;
    }

    private static void saveOsd(Map<String, Object> s) {
        s.put(SAVED_OSD, PropwashClientApi.osd().current());
    }

    private static void restoreOsd(Map<String, Object> s) {
        Object saved = s.get(SAVED_OSD);
        if (saved instanceof OsdLayoutSpec spec) {
            PropwashClientApi.osd().apply(spec);
        }
    }

    private static void restoreOsdConfig(Map<String, Object> s) {
        Object saved = s.get(SAVED_OSD);
        if (saved instanceof OsdConfig config) {
            OsdClient.CONFIG.set(config);
        }
    }

    static void listen() {
        if (listening) {
            return;
        }
        synchronized (CovShareSelfTests.class) {
            if (listening) {
                return;
            }
            listening = true;
            PropwashEvents.BUILD_IMPORTED.register((player, request, result) -> {
                IMPORTS.incrementAndGet();
                IMPORT_LOG.add(request.target() + " " + request.layers() + " → " + result.status());
            });
        }
    }

    static ImportRequest workbench(DroneData data, ImportLayer... layers) {
        EnumSet<ImportLayer> set = EnumSet.noneOf(ImportLayer.class);
        set.addAll(List.of(layers));
        return new ImportRequest(data, set, Pw3Params.TUNE_GROUPS, ImportTarget.WORKBENCH);
    }

    static ServerPlayer openBench(MinecraftServer server, GameType mode) {
        ServerPlayer player = ShareSelfTests.player(server);
        player.closeContainer();
        player.setGameMode(mode);
        if (mode != GameType.CREATIVE) {
            player.getInventory().clearContent();
        }
        openMenu(player);
        return player;
    }

    static void openMenu(ServerPlayer player) {
        Block block = BuiltInRegistries.BLOCK.getValue(Constants.id("drone_workbench"));
        BlockPos pos = ShareSelfTests.benchPos(player);
        player.level().setBlockAndUpdate(pos, block.defaultBlockState());
        MenuProvider provider = player.level().getBlockState(pos).getMenuProvider(player.level(), pos);
        if (provider == null || player.openMenu(provider).isEmpty()) {
            throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
        }
    }

    static ServerPlayer giveHeld(MinecraftServer server, DroneData data, GameType mode) {
        ServerPlayer player = ShareSelfTests.player(server);
        player.closeContainer();
        player.setGameMode(mode);
        player.getInventory().clearContent();
        player.getInventory().setItem(player.getInventory().getSelectedSlot(), PropwashApi.builds().createDrone(data));
        player.containerMenu.broadcastChanges();
        return player;
    }

    static List<String> inventory(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                out.add(i + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + "×" + stack.getCount() + PropwashApi.builds().read(stack).map(d -> " " + d)
                        .orElse(""));
            }
        }
        return out;
    }

    static Item dyeItem(String dye) {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(dye + "_dye"));
    }

    static int execute(MinecraftServer server, ServerPlayer player, Capture capture, String command) {
        CommandSourceStack source = server.createCommandSourceStack().withEntity(player).withPosition(player.position()).withSource(capture);
        try {
            return server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            capture.failures.add(Component.literal(e.getMessage()));
            return -1;
        }
    }

    private static void closeAll(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        ctx.onServer(server -> {
            ServerPlayer player = ShareSelfTests.player(server);
            player.closeContainer();
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent();
            player.level().removeBlock(ShareSelfTests.benchPos(player), false);
        });
    }

    static StepResult check(List<String> errors) {
        return StepResult.check(errors.isEmpty(), String.join("; ", errors));
    }

    static final class Capture implements CommandSource {
        final List<Component> messages = new CopyOnWriteArrayList<>();
        final List<Component> failures = new CopyOnWriteArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            this.messages.add(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        List<String> successKeys() {
            List<String> out = new ArrayList<>();
            for (Component message : this.messages) {
                collectKeys(message, out);
            }
            return out;
        }

        List<String> failureKeys() {
            List<String> out = new ArrayList<>();
            for (Component message : this.messages) {
                collectKeys(message, out);
            }
            for (Component message : this.failures) {
                out.add(message.getString());
            }
            return out;
        }

        List<String> plainTexts() {
            List<String> out = new ArrayList<>();
            for (Component message : this.messages) {
                out.add(message.getString());
            }
            return out;
        }

        @Nullable String copyValue() {
            for (Component message : this.messages) {
                String value = copyValue(message);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }

        static @Nullable String copyValue(Component component) {
            if (component.getStyle().getClickEvent() instanceof ClickEvent.CopyToClipboard copy) {
                return copy.value();
            }
            for (Component sibling : component.getSiblings()) {
                String value = copyValue(sibling);
                if (value != null) {
                    return value;
                }
            }
            if (component.getContents() instanceof TranslatableContents t) {
                for (Object arg : t.getArgs()) {
                    if (arg instanceof Component inner) {
                        String value = copyValue(inner);
                        if (value != null) {
                            return value;
                        }
                    }
                }
            }
            return null;
        }

        private static void collectKeys(Component component, List<String> out) {
            if (component.getContents() instanceof TranslatableContents t) {
                out.add(t.getKey());
                for (Object arg : t.getArgs()) {
                    if (arg instanceof Component inner) {
                        collectKeys(inner, out);
                    }
                }
            }
            for (Component sibling : component.getSiblings()) {
                collectKeys(sibling, out);
            }
        }
    }
}
