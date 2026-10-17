package de.lardtom.propwash.apiimpl.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildAccess;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.CameraView;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.ImportTarget;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.client.FlightSchoolScenes;
import de.lardtom.propwash.api.client.OsdLayoutSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.registry.ExtensionTab;
import de.lardtom.propwash.api.registry.TabSection;
import de.lardtom.propwash.content.ContentBlocks;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.client.DroneWorkbenchScreen;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.client.ExampleAddonClient;
import de.lardtom.propwash.dev.example.client.ExampleConfigTab;
import de.lardtom.propwash.dev.example.client.ExampleImportScreen;
import de.lardtom.propwash.dev.example.client.ExampleScene;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.client.GuideOpener;
import de.lardtom.propwash.guide.client.HoldToOpen;
import de.lardtom.propwash.guide.client.render.SceneAssets;
import de.lardtom.propwash.guide.client.render.SceneRenderer;
import de.lardtom.propwash.guide.client.screen.SceneIndexScreen;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scenes.SceneKit;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ApiSelfTests {
    static final String PHASE = "phase";
    private static final String SINCE = "since";
    static final String SERVER = "server";
    static final String IMPORT = "import";
    private static final String IMPORTS = "imports";
    private static final String SCENE_STEP = "scene_step";
    private static final String FRAMES = "frames";
    private static final String FAILED = "failed";
    private static final String HINTS = "hints";
    private static final long REQUEST_GAP_NANOS = 1_600_000_000L;
    private static final List<Identifier> BUILTIN_FRAMES = List.of(pw("whoop_65"), pw("cinewhoop_3"), pw("freestyle_5"), pw("race_5"),
            pw("long_range_7"));
    static final BuildSpec FREESTYLE = BuildSpec.of(pw("freestyle_5"), pw("stack_30"), pw("m2207_1750"), pw("p51_3b"),
            pw("digital_hd"), pw("b6s_1300"));
    private static final Identifier UNKNOWN = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "does_not_exist");
    static final AtomicInteger CONTAINER = new AtomicInteger(-1);
    static final AtomicReference<DroneData> SERVER_READ = new AtomicReference<>();
    static final AtomicReference<String> SERVER_ERROR = new AtomicReference<>();
    private static final AtomicInteger ASSEMBLED_BEFORE = new AtomicInteger();
    private static long lastRequest;

    private ApiSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("api: Version, Register und Beispiel-Teile", 60, ctx -> registryStep());
        SelfTest.clientStep("api: Baupläne prüfen, analysieren, Standard-Tuning", 60, ctx -> buildStep());
        SelfTest.clientStep("api: Drohnen-Item lesen, erzeugen, Tuning und Lack setzen", 60, ctx -> itemStep());
        SelfTest.clientStep("api: Presets und Kreativ-Einträge", 60, ctx -> creativeStep());
        SelfTest.clientStep("api: eigener Kreativ-Tab des Beispiels hinter dem Propwash-Tab", 20, ctx -> extensionTabStep());
        SelfTest.clientStep("api: OSD-Layout exportieren und wieder übernehmen", 60, ctx -> osdStep());
        SelfTest.clientStep("api: Import in die Werkbank, Rundreise und Events (Kreativ)", 900, ApiSelfTests::workbenchImportStep);
        SelfTest.clientStep("api: Import nur Tuning-Gruppen und Lack auf die gehaltene Drohne", 700, ApiSelfTests::heldDroneStep);
        SelfTest.clientStep("api: Ratenbegrenzung, fehlendes Ziel und unbekannte Teile", 500, ApiSelfTests::limitStep);
        SelfTest.clientStep("api: Werkbank-Knopf, Konfigurator-Tab und Flugschul-Szene des Beispiels", 2400, ApiSelfTests::extensionStep);
        SelfTest.clientStep("api: Flugschule über die Halten-Taste auch für Addon-Teile", 600, ApiSelfTests::flightSchoolStep);
        ApiAdditionSelfTests.register();
        ApiClientSelfTests.register();
        SelfTest.clientStep("api: Aufräumen", 200, ApiSelfTests::cleanupStep);
    }

    private static StepResult registryStep() {
        List<String> errors = new ArrayList<>();
        if (!PropwashApi.isCompatible("1.0") || !PropwashApi.isCompatible("1.1") || !PropwashApi.isCompatible("1.2") || !PropwashApi.isCompatible("1.3")
                || !PropwashApi.isCompatible("1.4") || !PropwashApi.isCompatible("1.5") || !PropwashApi.isCompatible("1.6")
                || PropwashApi.isCompatible("1.7") || PropwashApi.isCompatible("2.0") || !"1.6.0".equals(PropwashApi.API_VERSION)) {
            errors.add("Versionsprüfung falsch");
        }
        try {
            PropwashApi.requireVersion(ExampleAddon.NAMESPACE, ExampleAddon.REQUIRED_API);
        } catch (IllegalStateException e) {
            errors.add("requireVersion wirft: " + e.getMessage());
        }
        for (Identifier id : ExampleAddon.allParts()) {
            Optional<PartDefinition> definition = PropwashApi.parts().get(id);
            if (definition.isEmpty()) {
                errors.add("Teil fehlt: " + id);
                continue;
            }
            if (PropwashApi.parts().isBuiltin(id)) {
                errors.add("Beispiel-Teil gilt als eingebaut: " + id);
            }
            if (PropwashApi.partItems().item(id).isEmpty()) {
                errors.add("Item fehlt: " + id);
            } else if (!Optional.of(id).equals(PropwashApi.partItems().partOf(PropwashApi.partItems().stack(id)))) {
                errors.add("partOf(stack) liefert nicht " + id);
            }
            if (!Language.getInstance().has("part." + id.getNamespace() + "." + id.getPath())) {
                errors.add("Name fehlt: " + id);
            }
            if (!ExampleAddon.REGISTERED.contains(id)) {
                errors.add("PART_REGISTERED kam nicht für " + id);
            }
        }
        expectKind(errors, ExampleAddon.FRAME_DEADCAT, PartKind.FRAME);
        expectKind(errors, ExampleAddon.FRAME_XCLASS, PartKind.FRAME);
        expectKind(errors, ExampleAddon.FRAME_STRETCH, PartKind.FRAME);
        expectKind(errors, ExampleAddon.STACK_8S, PartKind.STACK);
        expectKind(errors, ExampleAddon.MOTOR_4214, PartKind.MOTOR);
        expectKind(errors, ExampleAddon.PROP_13, PartKind.PROP);
        expectKind(errors, ExampleAddon.BATTERY_8S, PartKind.BATTERY);
        Optional<MotorDefinition> motor = PropwashApi.parts().get(MotorDefinition.class, ExampleAddon.MOTOR_2806);
        if (motor.isEmpty() || motor.get().kv() != 1300 || motor.get().statorDiameterMm() != 28.0 || motor.get().statorHeightMm() != 6.5) {
            errors.add("Motor 2806,5 falsch: " + motor);
        }
        List<Identifier> frames = PropwashApi.parts().all(PartKind.FRAME).stream().map(PartDefinition::id).toList();
        if (frames.size() < BUILTIN_FRAMES.size() + 3 || !frames.subList(0, BUILTIN_FRAMES.size()).equals(BUILTIN_FRAMES)) {
            errors.add("Rahmen-Reihenfolge: eingebaute zuerst erwartet, ist " + frames);
        }
        Optional<CellChemistry> chemistry = PropwashApi.parts().chemistry(ExampleAddon.CHEMISTRY_LIHV);
        if (chemistry.isEmpty() || chemistry.get().fullCellVoltage() != 4.45) {
            errors.add("Zellchemie aus JSON fehlt: " + chemistry);
        }
        if (PropwashApi.parts().chemistries().stream().noneMatch(c -> c.id().equals(pw("lipo")))) {
            errors.add("eingebaute Chemie LiPo fehlt in chemistries()");
        }
        if (!PropwashApi.parts().isBuiltin(pw("freestyle_5")) || PropwashApi.parts().get(UNKNOWN).isPresent()) {
            errors.add("isBuiltin/get für eingebaute oder unbekannte Teile falsch");
        }
        if (!PropwashApi.parts().isFrozen() || PropwashApi.parts().fingerprint().isEmpty()) {
            errors.add("Register nicht eingefroren oder ohne Fingerabdruck");
        }
        if (ExampleAddon.FROZEN.get() != 1) {
            errors.add("REGISTRY_FROZEN kam " + ExampleAddon.FROZEN.get() + "-mal statt einmal");
        }
        if (ExampleAddon.bundledCount() < ExampleAddon.JSON_PARTS.size()) {
            errors.add("loadBundled meldete " + ExampleAddon.bundledCount() + " Definitionen");
        }
        if (!PropwashApi.partItems().stack(UNKNOWN).isEmpty()) {
            errors.add("stack(unbekannt) ist nicht leer");
        }
        PartDefinition existing = PropwashApi.parts().get(ExampleAddon.MOTOR_2806).orElse(null);
        if (existing != null) {
            expectThrows(errors, "Anmelden nach dem Einfrieren", () -> PropwashApi.parts().register(existing));
        }
        return StepResult.check(errors.isEmpty(), String.join("; ", errors));
    }

    private static StepResult buildStep() {
        List<String> errors = new ArrayList<>();
        BuildAccess builds = PropwashApi.builds();
        BuildCheck xclass = builds.check(ExampleAddon.xclass().build());
        if (!xclass.buildable()) {
            errors.add("X-Class nicht baubar: " + xclass);
        }
        if (!builds.check(ExampleAddon.deadcat().build()).buildable() || !builds.check(ExampleAddon.stretched().build()).buildable()) {
            errors.add("Deadcat oder Stretched-X nicht baubar");
        }
        BuildCheck unknown = builds.check(FREESTYLE.withPart(PartKind.FRAME, UNKNOWN));
        if (unknown.known() || !unknown.unknownParts().equals(List.of(UNKNOWN))) {
            errors.add("unbekanntes Teil nicht gemeldet: " + unknown);
        }
        BuildCheck slot = builds.check(FREESTYLE.withAccessory(AccessorySlot.TOP, pw("led_strip")));
        if (!slot.problems().equals(List.of("accessory_mount"))) {
            errors.add("LED-Streifen oben nicht als Problem gemeldet: " + slot);
        }
        BuildCheck mount = builds.check(ExampleAddon.xclass().build().withPart(PartKind.MOTOR, pw("m2207_1750")));
        if (!mount.problems().contains("motor_mount")) {
            errors.add("falsche Motorhalterung nicht gemeldet: " + mount);
        }
        for (String problem : mount.problems()) {
            if (!Language.getInstance().has(BuildCheck.translationKey(problem))) {
                errors.add("Text fehlt: " + BuildCheck.translationKey(problem));
            }
        }
        Optional<BuildStats> stats = builds.analyze(ExampleAddon.xclass().build());
        if (stats.isEmpty() || !(stats.get().massGrams() > 2000.0) || !(stats.get().thrustToWeight() > 1.5)
                || !Double.isFinite(stats.get().hoverFlightTimeMin())) {
            errors.add("Analyse der X-Class unplausibel: " + stats);
        }
        stats.ifPresent(s -> s.warnings().forEach(w -> {
            if (!Language.getInstance().has(BuildStats.warningTranslationKey(w))) {
                errors.add("Text fehlt: " + BuildStats.warningTranslationKey(w));
            }
        }));
        if (builds.analyze(FREESTYLE.withPart(PartKind.PROP, UNKNOWN)).isPresent()) {
            errors.add("Analyse mit unbekanntem Teil nicht leer");
        }
        TuneSpec defaults = builds.defaultTune(ExampleAddon.xclass().build());
        if (!defaults.values().containsKey("rates.roll.rcRate") || !Float.valueOf(20.0F).equals(defaults.values().get("camera.uptiltDeg"))) {
            errors.add("Standard-Tuning der X-Class unvollständig: " + defaults.values().keySet());
        }
        if (!Float.valueOf(30.0F).equals(builds.defaultTune(ExampleAddon.deadcat().build()).values().get(TuneSpec.CAMERA_UPTILT))) {
            errors.add("Kamerawinkel der Deadcat nicht aus der Definition");
        }
        Optional<CameraView.UptiltRange> deadcatMount = builds.uptiltRange(ExampleAddon.deadcat().build().frame());
        if (deadcatMount.isEmpty() || deadcatMount.get().defaultDeg() != 30.0 || !deadcatMount.get().contains(0.0) || !deadcatMount.get().contains(50.0)) {
            errors.add("Kamerahalter der Beispiel-Deadcat falsch: " + deadcatMount);
        }
        Optional<CameraView> steep = builds.cameraView(ExampleAddon.deadcat().build(), 75.0);
        if (steep.isEmpty() || steep.get().uptiltDeg() != 50.0 || steep.get().horizontalFovDeg() != 125.0) {
            errors.add("Kamera der Beispiel-Deadcat bei 75° nicht auf den Halter begrenzt oder Sichtfeld nicht vom Videomodul: "
                    + steep.map(v -> v.uptiltDeg() + "° / " + v.horizontalFovDeg() + "°").orElse("leer"));
        }
        return StepResult.check(errors.isEmpty(), String.join("; ", errors));
    }

    private static StepResult itemStep() {
        List<String> errors = new ArrayList<>();
        BuildAccess builds = PropwashApi.builds();
        DroneData data = ExampleAddon.xclass();
        ItemStack drone = builds.createDrone(data);
        Optional<DroneData> read = builds.read(drone);
        if (!builds.isDrone(drone) || read.isEmpty()) {
            return StepResult.fail("createDrone liefert kein lesbares Drohnen-Item: " + drone);
        }
        if (!read.get().build().equals(data.build()) || !read.get().paint().equals(data.paint()) || !read.get().name().equals(data.name())) {
            errors.add("Rundreise createDrone → read verliert Daten: " + read.get());
        }
        for (Map.Entry<String, Float> entry : data.tune().values().entrySet()) {
            if (!entry.getValue().equals(read.get().tune().values().get(entry.getKey()))) {
                errors.add("Tuning " + entry.getKey() + " fehlt nach der Rundreise: " + read.get().tune().values());
            }
        }
        ItemStack retuned = builds.withTune(drone, new TuneSpec(Map.of("pid.throttleMid", 0.6F)));
        TuneSpec tune = builds.read(retuned).map(DroneData::tune).orElse(TuneSpec.EMPTY);
        if (!Float.valueOf(0.6F).equals(tune.values().get("pid.throttleMid")) || tune.values().containsKey("pid.throttleExpo")
                || !builds.read(drone).map(DroneData::tune).orElse(TuneSpec.EMPTY).values().containsKey("pid.throttleExpo")) {
            errors.add("withTune ersetzt das Tuning nicht auf einer Kopie: " + tune.values());
        }
        ItemStack plain = builds.withPaint(drone, PaintSpec.DEFAULT);
        if (!builds.read(plain).map(d -> d.paint().isDefault()).orElse(false) || builds.read(drone).map(d -> d.paint().isDefault()).orElse(true)) {
            errors.add("withPaint setzt die Lackierung nicht auf einer Kopie");
        }
        ItemStack dirt = new ItemStack(Items.DIRT);
        if (builds.isDrone(dirt) || builds.read(dirt).isPresent()) {
            errors.add("Erde gilt als Drohne");
        }
        expectThrows(errors, "createDrone mit unbekanntem Teil", () -> builds.createDrone(DroneData.of(FREESTYLE.withPart(PartKind.MOTOR, UNKNOWN))));
        expectThrows(errors, "createDrone mit unpassendem Motor",
                () -> builds.createDrone(DroneData.of(data.build().withPart(PartKind.MOTOR, pw("m2207_1750")))));
        expectThrows(errors, "withTune auf Erde", () -> builds.withTune(dirt, TuneSpec.EMPTY));
        return StepResult.check(errors.isEmpty(), String.join("; ", errors));
    }

    private static StepResult creativeStep() {
        List<String> errors = new ArrayList<>();
        List<DronePreset> presets = PropwashApi.builds().presets();
        List<Identifier> ids = presets.stream().map(DronePreset::id).toList();
        List<Identifier> builtin = List.of(pw("whoop"), pw("cinewhoop"), pw("freestyle"), pw("racer"), pw("long_range"));
        if (ids.size() < 6 || !ids.subList(0, 5).equals(builtin) || !ids.contains(ExampleAddon.PRESET_XCLASS)) {
            errors.add("Presets falsch: " + ids);
        }
        for (DronePreset preset : presets) {
            if (!Language.getInstance().has(preset.translationKey())) {
                errors.add("Preset-Name fehlt: " + preset.translationKey());
            }
        }
        expectThrows(errors, "Preset doppelt", () -> PropwashApi.builds().registerPreset(DronePreset.of(ExampleAddon.PRESET_XCLASS,
                ExampleAddon.xclass())));
        expectThrows(errors, "Preset im Namensraum propwash", () -> PropwashApi.builds().registerPreset(DronePreset.of(pw("fremd"),
                ExampleAddon.xclass())));
        ExtensionTab tab = PropwashApi.extensionTab(ExampleAddon.NAMESPACE);
        boolean before = ExampleAddon.showcase();
        try {
            ExampleAddon.showcase(false);
            if (containsExampleDrone(tab.collect()) || !tab.contents().isEmpty()) {
                errors.add("versteckte Kreativ-Einträge erscheinen trotzdem: " + tab.contents().size());
            }
            ExampleAddon.showcase(true);
            if (!containsExampleDrone(tab.collect()) || !containsExampleDrone(tab.contents())) {
                errors.add("Kreativ-Eintrag des Beispiels fehlt im eigenen Tab");
            }
            if (containsExampleDrone(PropwashApi.creativeTab().collect()) || containsExampleDrone(ContentTab.contents())) {
                errors.add("Kreativ-Eintrag des Beispiels steht im Propwash-Tab");
            }
        } finally {
            ExampleAddon.showcase(before);
        }
        return StepResult.check(errors.isEmpty(), String.join("; ", errors));
    }

    private static StepResult extensionTabStep() {
        List<String> errors = new ArrayList<>();
        ExtensionTab tab = PropwashApi.extensionTab(ExampleAddon.NAMESPACE);
        if (PropwashApi.extensionTab(ExampleAddon.NAMESPACE) != tab || !PropwashApi.extensionTabs().contains(tab)) {
            errors.add("extensionTab liefert nicht immer denselben Tab");
        }
        if (!tab.tabId().equals(pw("main-" + ExampleAddon.NAMESPACE)) || !tab.registered()) {
            errors.add("Beispiel-Tab falsch angemeldet: " + tab);
        }
        CreativeModeTab registered = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(tab.tabId());
        if (registered == null) {
            errors.add("Beispiel-Tab fehlt im Register: " + tab.tabId());
        } else {
            Language language = Language.getInstance();
            String title = "Propwash: " + language.getOrDefault(tab.nameKey());
            if (!language.has(tab.nameKey())) {
                errors.add("Tab-Name fehlt: " + tab.nameKey());
            }
            if (!title.equals(registered.getDisplayName().getString())) {
                errors.add("Tab-Titel „" + registered.getDisplayName().getString() + "“ statt „" + title + "“");
            }
            Optional<Item> icon = PropwashApi.partItems().item(ExampleAddon.FRAME_XCLASS);
            if (icon.isEmpty() || !registered.getIconItem().is(icon.get()) || !tab.iconStack().is(icon.get())) {
                errors.add("Tab-Symbol ist nicht der X-Class-Rahmen: " + registered.getIconItem());
            }
        }
        if (tab.sections().size() != TabSection.values().length
                || !tab.sections().subList(0, ExampleAddon.TAB_ORDER.size()).equals(ExampleAddon.TAB_ORDER)) {
            errors.add("Reihenfolge der Abschnitte falsch: " + tab.sections());
        }
        expectThrows(errors, "Tab im Namensraum propwash", () -> PropwashApi.extensionTab("propwash"));
        expectThrows(errors, "Tab mit ungültigem Namensraum", () -> PropwashApi.extensionTab("Kein Namensraum"));
        expectThrows(errors, "neuer Tab nach dem Einfrieren", () -> PropwashApi.extensionTab("propwash_zu_spaet"));
        List<Identifier> own = new ArrayList<>();
        for (ExtensionTab extension : PropwashApi.extensionTabs()) {
            if (extension.registered()) {
                own.add(extension.tabId());
            }
        }
        List<Identifier> sorted = Services.PLATFORM.sortedCreativeTabs();
        if (!sorted.isEmpty()) {
            int main = sorted.indexOf(ContentTab.MAIN.id());
            if (main < 0 || main + 1 + own.size() > sorted.size() || !sorted.subList(main + 1, main + 1 + own.size()).equals(own)) {
                errors.add("Addon-Tabs stehen nicht direkt hinter dem Propwash-Tab: " + sorted);
            }
        }
        List<Identifier> byId = new ArrayList<>(own);
        byId.add(ContentTab.MAIN.id());
        byId.sort(Comparator.comparing(Identifier::getNamespace).thenComparing(Identifier::getPath));
        if (!byId.getFirst().equals(ContentTab.MAIN.id()) || !byId.subList(1, byId.size()).equals(own)) {
            errors.add("Ids der Addon-Tabs sortieren nicht hinter propwash:main: " + byId);
        }
        return StepResult.check(errors.isEmpty(), String.join("; ", errors));
    }

    private static boolean containsExampleDrone(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            Optional<DroneData> data = PropwashApi.builds().read(stack);
            if (data.isPresent() && data.get().build().frame().equals(ExampleAddon.FRAME_XCLASS)) {
                return true;
            }
        }
        return false;
    }

    private static StepResult osdStep() {
        OsdConfig saved = OsdClient.CONFIG.get().copy();
        List<String> errors = new ArrayList<>();
        try {
            OsdLayoutSpec current = PropwashClientApi.osd().current();
            OsdLayoutSpec defaults = PropwashClientApi.osd().defaults();
            if (current.version() != OsdLayoutSpec.CURRENT_VERSION || !current.json().contains("\"elements\"")) {
                errors.add("OSD-Layout ohne Version oder Elemente: " + current);
            }
            if (!PropwashClientApi.osd().apply(defaults) || !PropwashClientApi.osd().current().json().equals(defaults.json())) {
                errors.add("Standard-Layout lässt sich nicht übernehmen");
            }
            if (!PropwashClientApi.osd().apply(current) || !PropwashClientApi.osd().current().json().equals(current.json())) {
                errors.add("eigenes Layout übersteht die Rundreise nicht");
            }
            if (OsdClient.CONFIG.get().hideVanillaHud != saved.hideVanillaHud || OsdClient.CONFIG.get().analogBreakup != saved.analogBreakup) {
                errors.add("Spielervorlieben wurden vom Layout überschrieben");
            }
            if (PropwashClientApi.osd().apply(new OsdLayoutSpec(1, "{kaputt"))) {
                errors.add("kaputtes JSON wurde übernommen");
            }
        } finally {
            OsdClient.CONFIG.set(saved);
        }
        return StepResult.check(errors.isEmpty(), String.join("; ", errors));
    }

    private static StepResult workbenchImportStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                state.put(SERVER, ctx.onServer(ApiSelfTests::openBench));
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = awaitServer(ctx);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!(mc.gui.screen() instanceof DroneWorkbenchScreen) || !requestReady()) {
                    return timeout(ctx, 300, "Werkbank-Screen fehlt: " + mc.gui.screen());
                }
                if (ClientApiImpl.openWorkbench() != CONTAINER.get()) {
                    return fail(ctx, "Container-Id des Clients " + ClientApiImpl.openWorkbench() + " statt " + CONTAINER.get());
                }
                state.put(IMPORTS, ExampleAddon.IMPORTS.get());
                state.put(IMPORT, send(PropwashClientApi.sendImport(ImportRequest.wholeBuild(ExampleAddon.deadcat()))));
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                ImportResult result = result(state);
                if (result == null) {
                    return timeout(ctx, 500, "keine Antwort auf den Werkbank-Import");
                }
                if (result.status() != ImportStatus.APPLIED || !result.applied().contains(ImportLayer.PARTS)) {
                    return fail(ctx, "Werkbank-Import: " + result);
                }
                if (ExampleAddon.IMPORTS.get() <= (int) state.get(IMPORTS) || ExampleAddon.LAST_IMPORT.get() == null
                        || ExampleAddon.LAST_IMPORT.get().status() != ImportStatus.APPLIED) {
                    return fail(ctx, "BUILD_IMPORTED kam nicht");
                }
                state.put(SERVER, ctx.onServer(server -> readResult(player(server))));
                state.put(PHASE, 3);
                return StepResult.pending();
            }
            case 3 -> {
                StepResult server = awaitServer(ctx);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                DroneData exported = SERVER_READ.get();
                if (exported == null || !exported.build().equals(ExampleAddon.deadcat().build())) {
                    return fail(ctx, "Werkbank baut nicht die importierte Deadcat: " + exported + " " + SERVER_ERROR.get());
                }
                if (!requestReady()) {
                    return StepResult.pending();
                }
                state.put(IMPORT, send(PropwashClientApi.sendImport(ImportRequest.wholeBuild(exported))));
                state.put(PHASE, 4);
                return StepResult.pending();
            }
            case 4 -> {
                ImportResult result = result(state);
                if (result == null) {
                    return timeout(ctx, 700, "keine Antwort auf den zweiten Werkbank-Import");
                }
                if (!result.status().success()) {
                    return fail(ctx, "Export → Import derselben Drohne scheitert: " + result);
                }
                ASSEMBLED_BEFORE.set(ExampleAddon.ASSEMBLED.get());
                state.put(SERVER, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    readResult(player);
                    if (player.containerMenu instanceof DroneWorkbenchMenu menu) {
                        menu.clicked(DroneWorkbenchMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, player);
                    }
                }));
                state.put(PHASE, 5);
                return StepResult.pending();
            }
            default -> {
                StepResult server = awaitServer(ctx);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                DroneData again = SERVER_READ.get();
                if (again == null || !again.build().equals(ExampleAddon.deadcat().build())) {
                    return fail(ctx, "nach der Rundreise steht eine andere Drohne in der Werkbank: " + again);
                }
                if (ExampleAddon.ASSEMBLED.get() <= ASSEMBLED_BEFORE.get()) {
                    return fail(ctx, "DRONE_ASSEMBLED kam beim Entnehmen nicht");
                }
                ctx.log("Werkbank-Import: Deadcat übernommen, Rundreise gleich, Events " + ExampleAddon.IMPORTS.get() + " Importe, "
                        + ExampleAddon.ASSEMBLED.get() + " zusammengebaut, " + ExampleAddon.DISASSEMBLED.get() + " zerlegt");
                closeBench(ctx);
                return StepResult.pass();
            }
        }
    }

    private static StepResult heldDroneStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                state.put(SERVER, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.setGameMode(GameType.CREATIVE);
                    DroneData start = new DroneData(FREESTYLE, PaintSpec.DEFAULT.with("frame", 0xFF0000),
                            new TuneSpec(Map.of("rates.rcDeadband", 0.05F, "pid.angleLimitDeg", 45.0F)), Optional.empty());
                    player.setItemInHand(InteractionHand.MAIN_HAND, PropwashApi.builds().createDrone(start));
                }));
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = awaitServer(ctx);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                Optional<DroneData> held = PropwashClientApi.heldDrone();
                if (held.isEmpty() || !requestReady()) {
                    return timeout(ctx, 200, "Client sieht die Drohne in der Hand nicht");
                }
                if (!PropwashClientApi.tune().available() || !Float.valueOf(45.0F).equals(PropwashClientApi.tune().current().values().get(
                        "pid.angleLimitDeg")) || PropwashClientApi.tune().defaults().values().isEmpty()) {
                    return fail(ctx, "tune() sieht das Tuning der gehaltenen Drohne nicht: " + PropwashClientApi.tune().current().values());
                }
                ImportRequest request = new ImportRequest(ExampleAddon.xclass(), EnumSet.of(ImportLayer.TUNE),
                        EnumSet.of(TuneGroup.RATES, TuneGroup.THROTTLE), ImportTarget.HELD_DRONE);
                state.put(IMPORT, send(PropwashClientApi.sendImport(request)));
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                ImportResult result = result(state);
                if (result == null) {
                    return timeout(ctx, 400, "keine Antwort auf den Tuning-Import");
                }
                if (result.status() != ImportStatus.APPLIED || !result.applied().equals(EnumSet.of(ImportLayer.TUNE))) {
                    return fail(ctx, "Tuning-Import: " + result);
                }
                state.put(SERVER, ctx.onServer(server -> readHeld(player(server))));
                state.put(PHASE, 3);
                return StepResult.pending();
            }
            case 3 -> {
                StepResult server = awaitServer(ctx);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                DroneData held = SERVER_READ.get();
                if (held == null) {
                    return fail(ctx, "gehaltene Drohne verschwunden");
                }
                Map<String, Float> values = held.tune().values();
                if (!Float.valueOf(0.02F).equals(values.get("rates.rcDeadband")) || !Float.valueOf(0.35F).equals(values.get("pid.throttleMid"))
                        || !Float.valueOf(45.0F).equals(values.get("pid.angleLimitDeg"))) {
                    return fail(ctx, "nur Rates und Gaskurve hätten sich ändern dürfen: " + values);
                }
                if (!held.build().equals(FREESTYLE) || held.paint().color("frame").orElse(0) != 0xFF0000) {
                    return fail(ctx, "Tuning-Import änderte Bauplan oder Lack: " + held);
                }
                if (!requestReady()) {
                    return StepResult.pending();
                }
                state.put(IMPORT, send(PropwashClientApi.sendImport(ImportRequest.paintOnHeldDrone(ExampleAddon.xclass()))));
                state.put(PHASE, 4);
                return StepResult.pending();
            }
            case 4 -> {
                ImportResult result = result(state);
                if (result == null) {
                    return timeout(ctx, 600, "keine Antwort auf den Lack-Import");
                }
                if (result.status() != ImportStatus.APPLIED) {
                    return fail(ctx, "Lack-Import (Kreativ, gratis): " + result);
                }
                state.put(SERVER, ctx.onServer(server -> readHeld(player(server))));
                state.put(PHASE, 5);
                return StepResult.pending();
            }
            default -> {
                StepResult server = awaitServer(ctx);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                DroneData held = SERVER_READ.get();
                if (held == null || !held.paint().equals(ExampleAddon.xclass().paint()) || !held.build().equals(FREESTYLE)) {
                    return fail(ctx, "Lack-Import übernahm die Lackierung nicht genau: " + held);
                }
                ctx.onServer(done -> player(done).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
                return StepResult.pass();
            }
        }
    }

    private static StepResult limitStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                if (!requestReady()) {
                    return StepResult.pending();
                }
                List<CompletableFuture<ImportResult>> burst = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    burst.add(ClientApiImpl.sendWorkbenchImport(-1, ImportRequest.wholeBuild(ExampleAddon.deadcat())));
                }
                lastRequest = System.nanoTime();
                state.put(IMPORT, burst);
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                @SuppressWarnings("unchecked")
                List<CompletableFuture<ImportResult>> burst = (List<CompletableFuture<ImportResult>>) state.get(IMPORT);
                if (!burst.stream().allMatch(CompletableFuture::isDone)) {
                    return timeout(ctx, 300, "keine Antworten auf die Anfrage-Serie");
                }
                List<ImportStatus> statuses = burst.stream().map(f -> f.join().status()).toList();
                if (!statuses.equals(List.of(ImportStatus.NO_TARGET, ImportStatus.NO_TARGET, ImportStatus.BUSY))) {
                    return StepResult.fail("Ratenbegrenzung: erwartet zwei NO_TARGET und ein BUSY, ist " + statuses);
                }
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (!requestReady()) {
                    return StepResult.pending();
                }
                DroneData broken = DroneData.of(FREESTYLE.withPart(PartKind.FRAME, UNKNOWN));
                state.put(IMPORT, send(PropwashClientApi.sendImport(ImportRequest.tuneOnHeldDrone(broken))));
                state.put(PHASE, 3);
                return StepResult.pending();
            }
            default -> {
                ImportResult result = result(state);
                if (result == null) {
                    return timeout(ctx, 450, "keine Antwort auf den Import mit unbekanntem Teil");
                }
                return StepResult.check(result.status() == ImportStatus.UNKNOWN_PARTS && result.missingParts().equals(List.of(UNKNOWN)),
                        "unbekanntes Teil: " + result);
            }
        }
    }

    private static StepResult extensionStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                ExampleAddon.showcase(true);
                ExampleAddonClient.registerTab();
                if (PropwashClientApi.workbenchExtensions().stream().noneMatch(e -> e.id().equals(ExampleAddonClient.WORKBENCH_BUTTON))) {
                    return fail(ctx, "Werkbank-Erweiterung des Beispiels nicht angemeldet");
                }
                if (GuideScenes.all().stream().noneMatch(s -> s.id().equals(ExampleScene.ID))) {
                    return fail(ctx, "sichtbare Beispiel-Szene fehlt in der Flugschule");
                }
                state.put(SERVER, ctx.onServer(ApiSelfTests::openBench));
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = awaitServer(ctx);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                WorkbenchContext context = ExampleAddonClient.lastContext();
                if (!(mc.gui.screen() instanceof DroneWorkbenchScreen) || context == null || context.screen() != mc.gui.screen()) {
                    return timeout(ctx, 300, "Werkbank ruft die Erweiterung nicht auf: " + mc.gui.screen());
                }
                if (context.containerId() != CONTAINER.get() || !context.creative() || context.result().isPresent()) {
                    return fail(ctx, "WorkbenchContext falsch: Container " + context.containerId() + ", kreativ " + context.creative());
                }
                context.openChild(parent -> new ExampleImportScreen(parent, context));
                state.put(SINCE, ctx.tick());
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                ExampleImportScreen screen = ExampleImportScreen.active();
                if (screen == null || mc.gui.screen() != screen || screen.frames() < 3 || !requestReady()) {
                    return timeout(ctx, 500, "Kind-Fenster des Beispiels erscheint nicht: " + mc.gui.screen());
                }
                if (!ThemedScreen.active() || !screen.check().buildable()) {
                    return fail(ctx, "Kind-Fenster nicht im Theme oder X-Class nicht baubar: " + screen.check());
                }
                state.put(IMPORT, send(screen.importLayers(EnumSet.allOf(ImportLayer.class))));
                state.put(PHASE, 3);
                return StepResult.pending();
            }
            case 3 -> {
                ImportResult result = result(state);
                if (result == null) {
                    return timeout(ctx, 800, "keine Antwort auf den Import aus dem Kind-Fenster");
                }
                if (result.status() != ImportStatus.APPLIED) {
                    return fail(ctx, "Import aus dem Kind-Fenster: " + result);
                }
                ExampleImportScreen screen = ExampleImportScreen.active();
                if (screen != null) {
                    screen.onClose();
                }
                state.put(PHASE, 4);
                return StepResult.pending();
            }
            case 4 -> {
                WorkbenchContext context = ExampleAddonClient.lastContext();
                if (!(mc.gui.screen() instanceof DroneWorkbenchScreen) || context == null || context.result().isEmpty()) {
                    return timeout(ctx, 900, "Werkbank zeigt nach „Zurück“ keine fertige X-Class: " + mc.gui.screen());
                }
                if (!context.result().get().build().frame().equals(ExampleAddon.FRAME_XCLASS)
                        || !context.slot(PartKind.FRAME, 0).equals(Optional.of(ExampleAddon.FRAME_XCLASS))
                        || !context.slot(PartKind.MOTOR, 3).equals(Optional.of(ExampleAddon.MOTOR_4214))) {
                    return fail(ctx, "Werkbank-Slots zeigen nicht die X-Class: " + context.result());
                }
                closeBench(ctx);
                state.put(PHASE, 45);
                return StepResult.pending();
            }
            case 45 -> {
                if (mc.gui.screen() != null || mc.player == null || mc.player.containerMenu != mc.player.inventoryMenu) {
                    return timeout(ctx, 1000, "Werkbank schließt sich nicht: " + mc.gui.screen());
                }
                if (!PropwashClientApi.openConfigTab(ExampleConfigTab.ID, null)) {
                    return fail(ctx, "openConfigTab findet den Beispiel-Tab nicht");
                }
                state.put(PHASE, 5);
                return StepResult.pending();
            }
            case 5 -> {
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                    return timeout(ctx, 1100, "Konfigurator öffnet sich nicht");
                }
                ConfigTab active = screen.activeTab();
                if (!(active instanceof ExampleConfigTab tab)) {
                    screen.selectTab(ExampleConfigTab.ID);
                    return timeout(ctx, 1100, "Beispiel-Tab lässt sich nicht wählen: " + (active == null ? null : active.id()));
                }
                if (screen.extractedFrames(ExampleConfigTab.ID) < 3) {
                    return StepResult.pending();
                }
                OsdConfig saved = OsdClient.CONFIG.get().copy();
                boolean ok = tab.copyOsd() && tab.pasteOsd();
                OsdClient.CONFIG.set(saved);
                screen.discardAndClose();
                if (!ok) {
                    return fail(ctx, "Beispiel-Tab: OSD-Layout über die Zwischenablage klappt nicht: " + tab.status());
                }
                state.put(PHASE, 6);
                return StepResult.pending();
            }
            case 6 -> {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                    return StepResult.pending();
                }
                Scene scene = GuideScenes.byId(ExampleScene.ID);
                if (scene == null || !"propwash_example".equals(scene.namespace())) {
                    return fail(ctx, "Beispiel-Szene fehlt oder hat keinen Namensraum");
                }
                List<String> missing = new ArrayList<>();
                for (String key : scene.translationKeys()) {
                    if (!key.startsWith("propwash_example.guide.scene.frames.")) {
                        missing.add("falscher Schlüssel " + key);
                    } else if (!Language.getInstance().has(key)) {
                        missing.add(key);
                    }
                }
                if (!missing.isEmpty()) {
                    return fail(ctx, "Szenen-Texte: " + missing);
                }
                if (!PropwashClientApi.openFlightSchoolScene(ExampleScene.ID, null)) {
                    return fail(ctx, "openFlightSchoolScene findet die Beispiel-Szene nicht");
                }
                state.put(FAILED, SceneRenderer.failedDrawables());
                state.put(SCENE_STEP, 0);
                state.put(PHASE, 7);
                return StepResult.pending();
            }
            case 7 -> {
                if (!(mc.gui.screen() instanceof SceneScreen screen) || !screen.scene().id().equals(ExampleScene.ID)) {
                    return timeout(ctx, 1400, "Szenen-Player öffnet die Beispiel-Szene nicht: " + mc.gui.screen());
                }
                int step = (int) state.get(SCENE_STEP);
                Scene scene = screen.scene();
                if (step >= scene.stepCount()) {
                    screen.onClose();
                    state.put(PHASE, 8);
                    return StepResult.pending();
                }
                if (!state.containsKey(FRAMES)) {
                    screen.playback().seek((scene.stepStart(step) + scene.stepEnd(step)) * 0.5F);
                    screen.playback().pause();
                    state.put(FRAMES, screen.frames());
                    return StepResult.pending();
                }
                if (screen.frames() < (long) state.get(FRAMES) + 2) {
                    return timeout(ctx, 2000, "Beispiel-Szene wird nicht gezeichnet");
                }
                if (SceneRenderer.failedDrawables() > (long) state.get(FAILED)) {
                    return fail(ctx, "Beispiel-Szene zeichnet ein Element mit Fehler (siehe Log), Schritt " + scene.steps().get(step).name());
                }
                state.remove(FRAMES);
                state.put(SCENE_STEP, step + 1);
                return StepResult.pending();
            }
            default -> {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                ExampleAddon.showcase(false);
                if (GuideScenes.all().stream().anyMatch(s -> s.id().equals(ExampleScene.ID))) {
                    return StepResult.fail("versteckte Beispiel-Szene erscheint weiter in der Flugschule");
                }
                if (ConfigTabs.create().stream().anyMatch(t -> ExampleConfigTab.ID.equals(t.id()))) {
                    return StepResult.fail("versteckter Beispiel-Tab erscheint weiter im Konfigurator");
                }
                return StepResult.pass();
            }
        }
    }

    private static StepResult flightSchoolStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        ItemStack prop = PropwashApi.partItems().stack(ExampleAddon.PROP_13);
        if (phase == 0) {
            if (mc.player == null) {
                return StepResult.fail("kein Spieler");
            }
            List<String> errors = schoolLinkErrors();
            if (!errors.isEmpty()) {
                return StepResult.fail(String.join("; ", errors));
            }
            state.put(HINTS, HoldToOpen.hintFrames());
            HoldToOpen.testOverride(prop, true);
            mc.gui.setScreen(new InventoryScreen(mc.player));
            state.put(PHASE, 1);
            return StepResult.pending();
        }
        if (phase == 1) {
            List<Scene> expected = GuideOpener.scenesFor(prop);
            if (mc.gui.screen() instanceof SceneIndexScreen index) {
                HoldToOpen.testOverride(null, null);
                boolean right = expected.size() > 1 && index.shownScenes().equals(expected);
                boolean hinted = HoldToOpen.hintFrames() > (long) state.get(HINTS);
                index.onClose();
                boolean back = mc.gui.screen() instanceof AbstractContainerScreen<?>;
                mc.gui.setScreen(null);
                if (!right || !hinted || !back) {
                    return StepResult.fail("Halten über " + ExampleAddon.PROP_13 + " öffnet falsch (Szenen " + index.shownScenes().size() + "/"
                            + expected.size() + ", Hinweis " + hinted + ", zurück " + back + ")");
                }
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            if (mc.gui.screen() instanceof SceneScreen screen) {
                HoldToOpen.testOverride(null, null);
                String id = screen.scene().id();
                screen.onClose();
                mc.gui.setScreen(null);
                return StepResult.fail("Halten über " + ExampleAddon.PROP_13 + " öffnet nur die Szene " + id + " statt der Übersicht");
            }
            if (ctx.tick() > 300) {
                HoldToOpen.testOverride(null, null);
                mc.gui.setScreen(null);
                return StepResult.fail("Halten über " + ExampleAddon.PROP_13 + " öffnet keine Flugschul-Szene (Inventar-Bilder " + HoldToOpen.frames() + ")");
            }
            return StepResult.pending();
        }
        if (phase == 2) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
                return StepResult.pending();
            }
            ItemStack battery = PropwashApi.partItems().stack(ExampleAddon.BATTERY_8S);
            if (PropwashClientApi.openFlightSchool(new ItemStack(Items.STICK), null) || mc.gui.screen() != null) {
                mc.gui.setScreen(null);
                return StepResult.fail("openFlightSchool öffnet ein Fenster für ein Item ohne Szene");
            }
            if (!PropwashClientApi.openFlightSchool(battery, null) || !(mc.gui.screen() instanceof SceneIndexScreen index)
                    || index.shownScenes().stream().noneMatch(scene -> scene.id().equals("battery"))) {
                mc.gui.setScreen(null);
                return StepResult.fail("openFlightSchool zeigt für " + ExampleAddon.BATTERY_8S + " nicht die Akku-Szene: " + mc.gui.screen());
            }
            index.onClose();
            boolean closed = mc.gui.screen() == null;
            mc.gui.setScreen(null);
            return StepResult.check(closed, "Flugschul-Übersicht schließt nicht zurück ins Spiel");
        }
        return StepResult.fail("unbekannte Phase " + phase);
    }

    private static List<String> schoolLinkErrors() {
        List<String> errors = new ArrayList<>();
        List<Identifier> repairMotors = List.of(ExampleAddon.MOTOR_2806, ExampleAddon.MOTOR_4214);
        int checked = 0;
        for (PartDefinition definition : PropwashApi.parts().all()) {
            Identifier id = definition.id();
            if (!ExampleAddon.NAMESPACE.equals(id.getNamespace())) {
                continue;
            }
            ItemStack stack = PropwashApi.partItems().stack(id);
            if (stack.isEmpty()) {
                errors.add(id + " ohne Item");
                continue;
            }
            checked++;
            List<String> expected = new ArrayList<>(List.of(FlightSchoolScenes.DRONE_BUILD, FlightSchoolScenes.SURVIVAL));
            switch (definition.kind()) {
                case BATTERY -> expected.add(FlightSchoolScenes.BATTERY);
                case STACK -> expected.add(FlightSchoolScenes.CONFIGURATOR);
                case PROP -> expected.add(FlightSchoolScenes.REPAIR);
                case VIDEO -> expected.add(FlightSchoolScenes.VIDEO);
                default -> {
                }
            }
            if (repairMotors.contains(id)) {
                expected.add(FlightSchoolScenes.REPAIR);
            }
            List<String> scenes = PropwashClientApi.flightSchoolScenes(stack);
            if (!scenes.containsAll(expected) || scenes.contains(ExampleScene.ID)) {
                errors.add(id + ": " + scenes + " statt " + expected);
            }
        }
        if (checked < ExampleAddon.JAVA_PARTS.size() + ExampleAddon.JSON_PARTS.size()) {
            errors.add("nur " + checked + " Beispiel-Teile geprüft");
        }
        ExampleAddon.showcase(true);
        try {
            for (Identifier frame : List.of(ExampleAddon.FRAME_STRETCH, ExampleAddon.FRAME_DEADCAT, ExampleAddon.FRAME_XCLASS)) {
                List<String> scenes = PropwashClientApi.flightSchoolScenes(PropwashApi.partItems().stack(frame));
                if (!scenes.contains(ExampleScene.ID)) {
                    errors.add(frame + " öffnet die sichtbare Beispiel-Szene nicht: " + scenes);
                }
            }
        } finally {
            ExampleAddon.showcase(false);
        }
        if (!PropwashClientApi.flightSchoolScenes(new ItemStack(Items.STICK)).isEmpty()) {
            errors.add("Stock hat Flugschul-Szenen");
        }
        if (!PropwashClientApi.flightSchoolScenes(SceneAssets.item(SceneKit.WORKBENCH))
                .contains(FlightSchoolScenes.DRONE_BUILD)) {
            errors.add("Werkbank öffnet die Werkbank-Szene nicht");
        }
        return errors;
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            ExampleAddon.showcase(false);
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            closeBench(ctx);
            return StepResult.pending();
        }
        return ctx.tick() < 5 ? StepResult.pending() : StepResult.pass();
    }

    static void openBench(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        player.setGameMode(GameType.CREATIVE);
        player.closeContainer();
        BlockPos pos = benchPos(player);
        BlockState bench = ContentBlocks.DRONE_WORKBENCH.block().get().defaultBlockState();
        level.setBlockAndUpdate(pos, bench);
        if (player.openMenu(bench.getMenuProvider(level, pos)).isEmpty() || !(player.containerMenu instanceof DroneWorkbenchMenu menu)) {
            throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
        }
        for (int slot = 0; slot < DroneWorkbenchMenu.PART_SLOTS; slot++) {
            menu.getSlot(slot).set(ItemStack.EMPTY);
        }
        CONTAINER.set(menu.containerId);
    }

    static void readResult(ServerPlayer player) {
        SERVER_READ.set(null);
        SERVER_ERROR.set(null);
        if (!(player.containerMenu instanceof DroneWorkbenchMenu menu)) {
            SERVER_ERROR.set("Werkbank-Menü geschlossen");
            return;
        }
        Optional<DroneData> read = PropwashApi.builds().read(menu.resultPreview());
        if (read.isEmpty()) {
            SERVER_ERROR.set("kein Ergebnis, Prüfung: " + menu.check());
        }
        SERVER_READ.set(read.orElse(null));
    }

    static void readHeld(ServerPlayer player) {
        SERVER_READ.set(PropwashApi.builds().read(player.getMainHandItem()).orElse(null));
    }

    static void closeBench(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ExampleImportScreen) {
            mc.gui.setScreen(null);
        }
        ctx.onServer(server -> {
            ServerPlayer player = player(server);
            if (player.containerMenu instanceof DroneWorkbenchMenu menu) {
                for (int slot = 0; slot < DroneWorkbenchMenu.PART_SLOTS; slot++) {
                    menu.getSlot(slot).set(ItemStack.EMPTY);
                }
            }
            player.closeContainer();
            player.getInventory().clearContent();
            player.level().removeBlock(benchPos(player), false);
        });
    }

    static CompletableFuture<ImportResult> send(CompletableFuture<ImportResult> future) {
        lastRequest = System.nanoTime();
        return future;
    }

    static boolean requestReady() {
        return System.nanoTime() - lastRequest > REQUEST_GAP_NANOS;
    }

    @SuppressWarnings("unchecked")
    static ImportResult result(Map<String, Object> state) {
        CompletableFuture<ImportResult> future = (CompletableFuture<ImportResult>) state.get(IMPORT);
        return future != null && future.isDone() && !future.isCompletedExceptionally() ? future.join() : null;
    }

    static StepResult awaitServer(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(SERVER);
        if (future == null) {
            return StepResult.pass();
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return fail(ctx, "Server: " + future.exceptionNow().getMessage());
        }
        return StepResult.pass();
    }

    static StepResult timeout(SelfTest.StepContext ctx, int ticks, String reason) {
        return ctx.tick() > ticks ? fail(ctx, reason) : StepResult.pending();
    }

    static StepResult fail(SelfTest.StepContext ctx, String reason) {
        ExampleAddon.showcase(false);
        closeBench(ctx);
        return StepResult.fail(reason);
    }

    private static void expectKind(List<String> errors, Identifier id, PartKind kind) {
        Optional<PartDefinition> definition = PropwashApi.parts().get(id);
        if (definition.isPresent() && definition.get().kind() != kind) {
            errors.add(id + " ist " + definition.get().kind() + " statt " + kind);
        }
    }

    private static void expectThrows(List<String> errors, String what, Runnable action) {
        try {
            action.run();
            errors.add(what + " wirft keine Ausnahme");
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
    }

    static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    static BlockPos benchPos(ServerPlayer player) {
        return player.blockPosition().offset(2, 0, -2);
    }

    static Identifier pw(String path) {
        return Identifier.fromNamespaceAndPath("propwash", path);
    }
}
