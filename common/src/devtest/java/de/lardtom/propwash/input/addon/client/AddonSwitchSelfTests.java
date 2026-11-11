package de.lardtom.propwash.input.addon.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.client.AddonCamera;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.camera.GimbalContext;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.ForceProvider;
import de.lardtom.propwash.api.flight.ForceSink;
import de.lardtom.propwash.api.input.AddonSwitch;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleSwitches;
import de.lardtom.propwash.dev.example.client.ExampleSwitchesClient;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.AddonModeNameSelfTests;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.input.addon.AddonInputMath;
import de.lardtom.propwash.input.addon.AddonInputs;
import de.lardtom.propwash.input.addon.AxisAssignment;
import de.lardtom.propwash.input.client.FunctionKeys;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.AddonSwitchRows;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AddonSwitchSelfTests {
    public static final String GROUP = "addon-api-17: A1 – ";
    public static final String RADIO_GUID = "0300a1a1addon17000000000000000000";
    public static final int RADIO_AXES = 8;
    public static final int RADIO_BUTTONS = 12;
    public static final int GHOST_AXIS = 5;
    public static final int TILT_AXIS = 6;
    public static final String PROBE_ID = "propwash_example:addon_input_probe";
    private static final String GERMAN = "de_de";
    private static final int[][] SIZES = {{854, 480, 2}, {320, 240, 3}};
    private static final int WINDOW_TICKS = 80;
    private static final String PHASE = "phase";
    private static final String MARK = "mark";
    private static final String FUTURE = "future";
    private static final String CHECK = "check";
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final AtomicReference<String> SERVER = new AtomicReference<>("");
    private static @Nullable SimulatedRadio radio;
    private static int @Nullable [] savedWindow;
    private static final String UNKNOWN = "propwash_example:unbekannt";
    private static final String RESERVED = "propwash:ghost";
    private static final double TILT_TOLERANCE = 0.01;
    private static final InputProbe PROBE = new InputProbe();
    private static volatile boolean probing;
    private static @Nullable AxisAssignment savedTilt;
    private static boolean ownRadio;

    private AddonSwitchSelfTests() {
    }

    private static final class InputProbe implements ForceProvider {
        volatile int samples;
        volatile boolean ghost;
        volatile double tilt = Double.NaN;
        volatile boolean foreign;

        @Override
        public void apply(FlightState state, ForceSink sink) {
            if (!Minecraft.getInstance().isSameThread()) {
                return;
            }
            this.ghost = state.addonSwitch(ExampleSwitches.GHOST.toString());
            this.tilt = state.addonAxis(ExampleSwitches.GIMBAL_TILT.toString());
            this.foreign = state.addonSwitch(UNKNOWN) || state.addonAxis(UNKNOWN) != 0.0 || state.addonSwitch(RESERVED)
                    || state.addonAxis(RESERVED) != 0.0;
            this.samples++;
        }

        void clear() {
            this.samples = 0;
            this.ghost = false;
            this.tilt = Double.NaN;
            this.foreign = false;
        }

        boolean sees(boolean expectedGhost, double expectedTilt) {
            return this.samples > 0 && this.ghost == expectedGhost && Math.abs(this.tilt - expectedTilt) <= TILT_TOLERANCE && !this.foreign;
        }

        String describe() {
            return String.format(Locale.ROOT, "FlightState: Schalter %s, Achse %.3f, fremde Ids %s, %d Proben", this.ghost, this.tilt,
                    this.foreign ? "mit Wert" : "ohne Wert", this.samples);
        }
    }

    public static void register() {
        AddonModeNameSelfTests.register(GROUP);
        SelfTest.clientStep(GROUP + "Schalter „Geist“ und Achse „Gimbal-Neigung“ angemeldet, Seite „Add-ons“ im Tab „Modi“ mit Funke", 200,
                AddonSwitchSelfTests::pageStep);
        SelfTest.clientStep(GROUP + "„Schalter umlegen“ auf der Seite „Add-ons“ erkennt Kanal und Bereich, Speichern übernimmt die Belegung", 300,
                AddonSwitchSelfTests::assignStep);
        SelfTest.clientStep(GROUP + "Belegung gespeichert, Add-on aus und wieder an: Belegung noch da", 100, AddonSwitchSelfTests::keepStep);
        AddonSwitchGamepadSelfTests.register();
        AddonSwitchApiRadioSelfTests.register();
        SelfTest.clientStep(GROUP + "Schalter im Flug per Funke und Taste, Server meldet ADDON_SWITCH_CHANGED", 600, AddonSwitchSelfTests::switchStep);
        SelfTest.clientStep(GROUP + "Achse per Funke mit Totzone und per Taste mit Tastenrate, Server bekommt den Wert", 600, AddonSwitchSelfTests::axisStep);
        PropwashApi.flight().registerForces(PROBE_ID, profile -> probing ? PROBE : null);
        SelfTest.clientStep(GROUP + "Flugmodell und Gimbal sehen Schalter und Achse (FlightState#addonSwitch/#addonAxis und GimbalContext#axis)", 600,
                AddonSwitchSelfTests::flightStateStep);
        SelfTest.clientStep(GROUP + "Zubehör-Aktion 1 heißt nach dem Zubehör, Hinweis beim ersten Flug genau einmal, Ziffernblock 9 löst aus, "
                + "Rechtsklick beendet den Flug", 900, AddonSwitchSelfTests::hintStep);
        for (int[] size : SIZES) {
            SelfTest.clientStep(GROUP + "Seite „Add-ons“ bei GUI " + size[0] + "x" + size[1] + " ohne Befund", 400,
                    ctx -> layoutStep(ctx, size, "modes_addons_en_" + size[0] + "x" + size[1]));
        }
        if (Screenshots.enabled()) {
            SelfTest.clientStep(GROUP + "Bildschirmfoto Hinweis beim ersten Flug", 600, ctx -> hintShotStep(ctx, "accessory_hint_en"));
        }
        String back = GROUP + "Sprache zurückstellen";
        SelfTest.switchStep(GROUP + "Sprache Deutsch", back, 2400, ctx -> Screenshots.language(ctx, GERMAN));
        for (int[] size : SIZES) {
            SelfTest.clientStep(GROUP + "Deutsch: Seite „Add-ons“ bei GUI " + size[0] + "x" + size[1] + " ohne Befund", 400,
                    ctx -> layoutStep(ctx, size, "modes_addons_de_" + size[0] + "x" + size[1]));
        }
        if (Screenshots.enabled()) {
            SelfTest.clientStep(GROUP + "Deutsch: Bildschirmfoto Hinweis beim ersten Flug", 600, ctx -> hintShotStep(ctx, "accessory_hint_de"));
        }
        SelfTest.clientStep(back, 2400, Screenshots::restoreLanguage);
        SelfTest.clientStep(GROUP + "Aufräumen", 100, AddonSwitchSelfTests::cleanupStep);
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx) {
        ctx.state().put(PHASE, phase(ctx) + 1);
        ctx.state().put(MARK, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    private static StepResult await(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail(what + ": kein Serverauftrag");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static boolean exampleLoaded() {
        return JoinTestEnv.exampleAddon() && AddonInputs.switchSpec(ExampleSwitches.GHOST) != null;
    }

    static DeviceProfile radioProfile() {
        DeviceProfile profile = ProfileDefaults.radio("Add-on-Funke", RADIO_AXES);
        profile.setAddonBinding(ExampleSwitches.GHOST, SwitchAssignment.axisMicros(GHOST_AXIS, 1700, 2000));
        AxisAssignment tilt = AxisAssignment.axis(TILT_AXIS);
        tilt.deadband = 0.1F;
        profile.setAddonAxis(ExampleSwitches.GIMBAL_TILT, tilt);
        profile.setupDone = true;
        return profile;
    }

    static SimulatedRadio installRadio(DeviceProfile profile) {
        InputConfig next = InputConfig.get().copy();
        next.profiles.put(RADIO_GUID, profile);
        next.sanitize();
        InputConfig.commit(next);
        SimulatedRadio simulated = new SimulatedRadio(RADIO_GUID, "Add-on-Funke", profile, RADIO_AXES, RADIO_BUTTONS).useSavedProfile();
        for (int i = 0; i < RADIO_AXES; i++) {
            simulated.setAxis(i, 0.0F);
        }
        simulated.setMicros(GHOST_AXIS, 1000);
        TransmitterInput.INSTANCE.simulate(simulated);
        radio = simulated;
        return simulated;
    }

    private static void removeRadio() {
        if (radio != null) {
            TransmitterInput.INSTANCE.simulate(null);
            radio = null;
        }
        InputConfig next = InputConfig.get().copy();
        if (next.profiles.remove(RADIO_GUID) != null) {
            InputConfig.commit(next);
        }
    }

    private static StepResult pageStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!exampleLoaded()) {
            ctx.log("Beispiel-Add-on nicht geladen – übersprungen");
            return StepResult.pass();
        }
        switch (phase(ctx)) {
            case 0 -> {
                if (AddonInputs.axisSpec(ExampleSwitches.GIMBAL_TILT) == null || !AddonSwitchRows.available()) {
                    return StepResult.fail("Achse „Gimbal-Neigung“ fehlt im Register");
                }
                installRadio(radioProfile());
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (TransmitterInput.INSTANCE.raw() == null) {
                    return since(ctx) > 60 ? StepResult.fail("simulierte Funke kommt nicht an") : StepResult.pending();
                }
                ConfigTabs.open(null, ModesTab.ID);
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (!(mc.gui.screen() instanceof ConfiguratorScreen configurator) || !(configurator.activeTab() instanceof ModesTab modes)) {
                    return since(ctx) > 60 ? StepResult.fail("Tab „Modi“ öffnet nicht") : StepResult.pending();
                }
                modes.showAddons();
                if (!modes.addonsShown()) {
                    return StepResult.fail("Seite „Add-ons“ lässt sich nicht öffnen");
                }
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                if (since(ctx) < 5) {
                    return StepResult.pending();
                }
                if (!(mc.gui.screen() instanceof ConfiguratorScreen configurator) || !(configurator.activeTab() instanceof ModesTab modes)) {
                    return StepResult.fail("Konfigurator geschlossen");
                }
                String text = LayoutProbe.capture(mc, configurator).texts().stream().map(LayoutAudit.Text::value).reduce("", (a, b) -> a + "|" + b);
                String ghost = Component.translatable(ExampleSwitches.ghost().spec().nameKey()).getString();
                String tilt = Component.translatable(ExampleSwitches.tilt().spec().nameKey()).getString();
                if (!text.contains(ghost) || !text.contains(tilt)) {
                    return StepResult.fail("Seite „Add-ons“ zeigt nicht „" + ghost + "“ und „" + tilt + "“: " + text);
                }
                modes.addons().select(ExampleSwitches.GHOST);
                configurator.selectTab(ModesTab.ID);
                List<LayoutAudit.Finding> findings = LayoutProbe.audit(mc, configurator);
                configurator.discardAndClose();
                if (!findings.isEmpty()) {
                    return StepResult.fail(findings.size() + " Befunde: " + findings.getFirst().describe());
                }
                ctx.log("Seite „Add-ons“ mit „" + ghost + "“ und „" + tilt + "“, ohne Befund");
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase " + phase(ctx));
            }
        }
    }

    private static StepResult assignStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!exampleLoaded()) {
            return StepResult.pass();
        }
        int axis = RADIO_AXES - 1;
        switch (phase(ctx)) {
            case 0 -> {
                SimulatedRadio simulated = radio != null ? radio : installRadio(radioProfile());
                simulated.setMicros(axis, 1000);
                ConfigTabs.open(null, ModesTab.ID);
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (!(mc.gui.screen() instanceof ConfiguratorScreen configurator) || !(configurator.activeTab() instanceof ModesTab modes)) {
                    return since(ctx) > 60 ? StepResult.fail("Tab „Modi“ öffnet nicht") : StepResult.pending();
                }
                modes.showAddons();
                modes.addons().select(ExampleSwitches.GHOST);
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (since(ctx) < 5) {
                    return StepResult.pending();
                }
                if (!(mc.gui.screen() instanceof ConfiguratorScreen configurator) || !(configurator.activeTab() instanceof ModesTab modes)) {
                    return StepResult.fail("Konfigurator geschlossen");
                }
                String assign = Component.translatable("propwash.input.modes.assign").getString();
                for (GuiEventListener child : configurator.children()) {
                    if (child instanceof Button button && button.active && assign.equals(button.getMessage().getString())) {
                        button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                        break;
                    }
                }
                if (!modes.addons().listening()) {
                    return StepResult.fail("Knopf „" + assign + "“ fehlt oder startet das Erkennen nicht");
                }
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                if (since(ctx) < 5) {
                    return StepResult.pending();
                }
                radio.setMicros(axis, 2000);
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                if (!(mc.gui.screen() instanceof ConfiguratorScreen configurator) || !(configurator.activeTab() instanceof ModesTab modes)) {
                    return StepResult.fail("Konfigurator geschlossen");
                }
                if (modes.addons().listening()) {
                    return since(ctx) > 100 ? StepResult.fail("Kanal " + (axis + 1) + " auf 2000 µs nicht erkannt") : StepResult.pending();
                }
                configurator.saveAndClose();
                DeviceProfile saved = InputConfig.get().profile(RADIO_GUID);
                SwitchAssignment binding = saved == null ? SwitchAssignment.none() : saved.addonBinding(ExampleSwitches.GHOST);
                radio.setMicros(axis, 1500);
                installRadio(radioProfile());
                if (binding.source != SwitchSource.AXIS || binding.index != axis || binding.activeHighMicros() < 1900) {
                    return StepResult.fail("Belegung nach Speichern " + binding + " statt Kanal " + (axis + 1) + " oben");
                }
                ctx.log("„Schalter umlegen“ erkennt " + binding + ", gespeichert in input.json");
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase " + phase(ctx));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Identifier, AddonSwitch> switchMap() throws ReflectiveOperationException {
        Field field = Api17Registries.Switches.class.getDeclaredField("switches");
        field.setAccessible(true);
        return (Map<Identifier, AddonSwitch>) field.get(Api17Registries.SWITCHES);
    }

    private static StepResult keepStep(SelfTest.StepContext ctx) {
        if (!exampleLoaded()) {
            return StepResult.pass();
        }
        if (radio == null) {
            installRadio(radioProfile());
        }
        String key = ExampleSwitches.GHOST.toString();
        AddonSwitch handle = ExampleSwitches.ghost();
        try {
            Map<Identifier, AddonSwitch> map = switchMap();
            synchronized (Api17Registries.SWITCHES) {
                map.remove(ExampleSwitches.GHOST);
            }
            try {
                if (AddonInputs.switchSpec(ExampleSwitches.GHOST) != null) {
                    return StepResult.fail("Schalter nach dem Abmelden noch im Register");
                }
                InputConfig without = InputConfig.get().copy();
                without.sanitize();
                InputConfig.commit(without);
                InputConfig reread = InputConfig.CONFIG.load();
                DeviceProfile profile = reread.profile(RADIO_GUID);
                if (profile == null || !profile.switches.containsKey(key) || !profile.addonAxes.containsKey(ExampleSwitches.GIMBAL_TILT.toString())) {
                    return StepResult.fail("Belegung ohne Add-on verloren: " + (profile == null ? "kein Profil" : profile.switches.keySet()));
                }
                SwitchAssignment kept = profile.addonBinding(ExampleSwitches.GHOST);
                if (kept.index != GHOST_AXIS || kept.lowMicros() != 1700 || kept.highMicros() != 2000) {
                    return StepResult.fail("Belegung ohne Add-on verändert: " + kept);
                }
            } finally {
                synchronized (Api17Registries.SWITCHES) {
                    map.putIfAbsent(ExampleSwitches.GHOST, handle);
                }
            }
        } catch (ReflectiveOperationException e) {
            return StepResult.fail("Register nicht erreichbar: " + e);
        }
        if (AddonInputs.switchSpec(ExampleSwitches.GHOST) == null) {
            return StepResult.fail("Schalter nach dem Wiederanmelden fehlt");
        }
        InputConfig.commit(InputConfig.CONFIG.load());
        DeviceProfile again = InputConfig.get().profile(RADIO_GUID);
        if (again == null || !again.addonBinding(ExampleSwitches.GHOST).bound()) {
            return StepResult.fail("Belegung nach dem Wiederanmelden fehlt");
        }
        ctx.log("Belegung " + key + " = " + again.addonBinding(ExampleSwitches.GHOST) + " überlebt Normalisieren und Speichern ohne Add-on");
        return StepResult.pass();
    }

    static void fly(ServerPlayer player, DroneBuild build) {
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        int x = player.getBlockX() + 3;
        int z = player.getBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        drone.snapTo(x + 0.5, y + 0.1, z + 0.5, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(build);
        drone.setBatteryCharge(1.0F);
        level.addFreshEntity(drone);
        DRONE.set(drone.getId());
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(player.getInventory().getSelectedSlot(), remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (!drone.isFlownBy(player.getUUID())) {
            throw new IllegalStateException("FPV nicht gestartet: " + result);
        }
    }

    static void land(MinecraftServer server) {
        int id = DRONE.getAndSet(-1);
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.discard();
            }
        }
        ServerPlayer player = player(server);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
    }

    static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    static @Nullable QuadcopterEntity serverDrone(MinecraftServer server) {
        int id = DRONE.get();
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                return drone;
            }
        }
        return null;
    }

    private static StepResult startFlight(SelfTest.StepContext ctx, DroneBuild build) {
        if (!ctx.state().containsKey(FUTURE)) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                land(server);
                fly(player(server), build);
            }));
            return StepResult.pending();
        }
        StepResult server = await(ctx, "Flugstart");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (!PilotSession.INSTANCE.isActive() || drone == null || drone.getId() != DRONE.get()) {
            return since(ctx) > 200 ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        return StepResult.pass();
    }

    private static boolean serverDone(SelfTest.StepContext ctx, Runnable query) {
        CompletableFuture<?> check = (CompletableFuture<?>) ctx.state().get(CHECK);
        if (check == null) {
            ctx.state().put(CHECK, ctx.onServer(server -> query.run()));
            return false;
        }
        if (!check.isDone()) {
            return false;
        }
        ctx.state().remove(CHECK);
        return true;
    }

    private static boolean serverSwitch(SelfTest.StepContext ctx, boolean expected) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || !serverDone(ctx, () -> {
            QuadcopterEntity drone = serverDrone(server);
            SERVER.set(drone == null ? "keine Drohne" : String.valueOf(ExampleSwitches.ghost().active(drone)));
        })) {
            return false;
        }
        return SERVER.get().equals(String.valueOf(expected));
    }

    private static StepResult switchStep(SelfTest.StepContext ctx) {
        if (!exampleLoaded()) {
            return StepResult.pass();
        }
        Identifier ghost = ExampleSwitches.GHOST;
        KeyMapping key = ExampleSwitchesClient.ghostKey();
        switch (phase(ctx)) {
            case 0 -> {
                AccessoryHintClient.mute(true);
                if (radio == null) {
                    installRadio(radioProfile());
                }
                radio.setMicros(GHOST_AXIS, 1000);
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult started = startFlight(ctx, DroneBuild.FREESTYLE);
                if (started.status() == StepResult.Status.PASS) {
                    ctx.state().put("changes", ExampleSwitches.GHOST_CHANGES.get());
                    radio.setMicros(GHOST_AXIS, 2000);
                    next(ctx);
                    return StepResult.pending();
                }
                return started;
            }
            case 2 -> {
                boolean client = PropwashClientApi.input().switchActive(ghost);
                boolean changed = ExampleSwitches.GHOST_CHANGES.get() > (int) ctx.state().get("changes");
                if (!client || !changed || !serverSwitch(ctx, true)) {
                    return since(ctx) > 100 ? StepResult.fail("Funke 2000 µs: Client " + client + ", Ereignis " + changed + ", Server " + SERVER.get())
                            : StepResult.pending();
                }
                ctx.log("Funke 2000 µs: Geist an beim Client und am Server (" + ExampleSwitches.LAST_CHANGE.get() + ")");
                radio.setMicros(GHOST_AXIS, 1000);
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                boolean client = PropwashClientApi.input().switchActive(ghost);
                if (client || !serverSwitch(ctx, false)) {
                    return since(ctx) > 100 ? StepResult.fail("Funke 1000 µs: Client " + client + ", Server " + SERVER.get()) : StepResult.pending();
                }
                key.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
                KeyMapping.resetMapping();
                KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                boolean client = PropwashClientApi.input().switchActive(ghost);
                if (!client || !serverSwitch(ctx, true)) {
                    return since(ctx) > 100 ? StepResult.fail("Taste G: Client " + client + ", Server " + SERVER.get()) : StepResult.pending();
                }
                KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
                next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                boolean client = PropwashClientApi.input().switchActive(ghost);
                if (client || !serverSwitch(ctx, false)) {
                    return since(ctx) > 100 ? StepResult.fail("Taste G zweimal: Client " + client + ", Server " + SERVER.get()) : StepResult.pending();
                }
                key.setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
                ctx.log("Taste G schaltet Geist an und wieder aus, Server folgt; " + AddonSwitchClient.switchPackets() + " Schalter-Pakete bisher");
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase " + phase(ctx));
            }
        }
    }

    private static boolean serverAxis(SelfTest.StepContext ctx, double expected, double tolerance) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || !serverDone(ctx, () -> {
            QuadcopterEntity drone = serverDrone(server);
            SERVER.set(drone == null ? "NaN" : String.valueOf(ExampleSwitches.tilt().value(drone)));
        })) {
            return false;
        }
        try {
            return Math.abs(Double.parseDouble(SERVER.get()) - expected) <= tolerance;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static StepResult axisStep(SelfTest.StepContext ctx) {
        if (!exampleLoaded()) {
            return StepResult.pass();
        }
        Identifier tilt = ExampleSwitches.GIMBAL_TILT;
        switch (phase(ctx)) {
            case 0 -> {
                if (radio == null || !PilotSession.INSTANCE.isActive()) {
                    return StepResult.fail("keine Sitzung mit Funke aus dem vorigen Schritt");
                }
                radio.setAxis(TILT_AXIS, 0.05F);
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                double value = PropwashClientApi.input().axis(tilt);
                if (since(ctx) < 5) {
                    return StepResult.pending();
                }
                if (value != 0.0) {
                    return StepResult.fail("Totzone 10 %: 0,05 ergibt " + value);
                }
                radio.setAxis(TILT_AXIS, 0.55F);
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                double value = PropwashClientApi.input().axis(tilt);
                if (Math.abs(value - 0.5) > 0.01 || !serverAxis(ctx, 0.5, 0.01)) {
                    return since(ctx) > 100 ? StepResult.fail("0,55 mit Totzone 10 %: Client " + value + ", Server " + SERVER.get()) : StepResult.pending();
                }
                ctx.log("Funke 0,55 mit Totzone 10 % → Client " + String.format(Locale.ROOT, "%.3f", value) + ", Server " + SERVER.get());
                InputConfig next = InputConfig.get().copy();
                DeviceProfile profile = next.profile(RADIO_GUID);
                if (profile == null) {
                    return StepResult.fail("Profil der Funke fehlt");
                }
                profile.setAddonAxis(tilt, AxisAssignment.keys());
                InputConfig.commit(next);
                KeyMapping down = ExampleSwitchesClient.tiltDown();
                down.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_H));
                KeyMapping.resetMapping();
                ctx.state().put("start", PropwashClientApi.input().axis(tilt));
                ctx.state().put("packets", AddonSwitchClient.axisPackets());
                ctx.state().put("nanos", System.nanoTime());
                down.setDown(true);
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                KeyMapping down = ExampleSwitchesClient.tiltDown();
                down.setDown(true);
                long nanos = System.nanoTime() - (long) ctx.state().get("nanos");
                if (nanos < 1_000_000_000L) {
                    return StepResult.pending();
                }
                down.setDown(false);
                double start = (double) ctx.state().get("start");
                double value = PropwashClientApi.input().axis(tilt);
                double seconds = nanos / 1.0E9;
                double rate = (start - value) / seconds;
                double expected = AddonInputMath.unitsPerSecond(ExampleSwitches.TILT_KEY_RATE_DPS);
                int packets = AddonSwitchClient.axisPackets() - (int) ctx.state().get("packets");
                ctx.state().put("held", value);
                ctx.log(String.format(Locale.ROOT, "Taste H %.2f s: %.3f → %.3f, %.3f je s (erwartet %.3f), %d Achsen-Pakete", seconds, start, value, rate,
                        expected, packets));
                if (value <= -1.0 || rate < expected * 0.5 || rate > expected * 1.1) {
                    return StepResult.fail(String.format(Locale.ROOT, "Tastenrate %.3f je s, erwartet %.3f (Spielraum 0,5- bis 1,1-fach)",
                            rate, expected));
                }
                if (packets > Math.ceil(seconds * 10.0) + 2) {
                    return StepResult.fail(packets + " Achsen-Pakete in " + seconds + " s (höchstens 10 je s)");
                }
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                double held = (double) ctx.state().get("held");
                double value = PropwashClientApi.input().axis(tilt);
                if (Math.abs(value - held) > 1.0E-6) {
                    return StepResult.fail("Achse bleibt nach dem Loslassen nicht stehen: " + held + " → " + value);
                }
                if (!serverAxis(ctx, value, 2.0 * AddonInputMath.QUANTUM)) {
                    return since(ctx) > 60 ? StepResult.fail("Server hat " + SERVER.get() + " statt " + value) : StepResult.pending();
                }
                ExampleSwitchesClient.tiltDown().setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
                ctx.state().put(FUTURE, ctx.onServer(AddonSwitchSelfTests::land));
                next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                StepResult server = await(ctx, "Landen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                return PilotSession.INSTANCE.isActive() ? StepResult.pending() : StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase " + phase(ctx));
            }
        }
    }

    private static double gimbalTilt(QuadcopterEntity drone) {
        return AddonCamera.context(drone, new Quaternionf(), 0.0F, 0.0, false).axis(ExampleSwitches.GIMBAL_TILT.toString());
    }

    private static boolean gimbalForeign(QuadcopterEntity drone) {
        GimbalContext context = AddonCamera.context(drone, new Quaternionf(), 0.0F, 0.0, false);
        return context.axis(UNKNOWN) != 0.0 || context.axis(RESERVED) != 0.0;
    }

    private static StepResult flightStateDone(SelfTest.StepContext ctx, StepResult result) {
        probing = false;
        PROBE.clear();
        AxisAssignment saved = savedTilt;
        savedTilt = null;
        InputConfig next = InputConfig.get().copy();
        DeviceProfile profile = next.profile(RADIO_GUID);
        if (saved != null && profile != null) {
            profile.setAddonAxis(ExampleSwitches.GIMBAL_TILT, saved);
            InputConfig.commit(next);
        }
        if (ownRadio) {
            ownRadio = false;
            removeRadio();
            AccessoryHintClient.mute(false);
        }
        if (result.status() != StepResult.Status.PASS) {
            ctx.onServer(AddonSwitchSelfTests::land);
        }
        return result;
    }

    private static StepResult flightStateStep(SelfTest.StepContext ctx) {
        if (!exampleLoaded()) {
            return StepResult.pass();
        }
        Identifier ghost = ExampleSwitches.GHOST;
        Identifier tilt = ExampleSwitches.GIMBAL_TILT;
        switch (phase(ctx)) {
            case 0 -> {
                AccessoryHintClient.mute(true);
                ownRadio = radio == null;
                SimulatedRadio simulated = radio == null ? installRadio(radioProfile()) : radio;
                InputConfig next = InputConfig.get().copy();
                DeviceProfile profile = next.profile(RADIO_GUID);
                if (profile == null) {
                    return flightStateDone(ctx, StepResult.fail("Profil der Funke fehlt"));
                }
                savedTilt = profile.addonAxis(tilt).copy();
                AxisAssignment onRadio = AxisAssignment.axis(TILT_AXIS);
                onRadio.deadband = 0.1F;
                profile.setAddonAxis(tilt, onRadio);
                InputConfig.commit(next);
                simulated.setMicros(GHOST_AXIS, 1000);
                simulated.setAxis(TILT_AXIS, 0.0F);
                PROBE.clear();
                probing = true;
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult started = startFlight(ctx, DroneBuild.FREESTYLE);
                if (started.status() == StepResult.Status.PASS) {
                    radio.setMicros(GHOST_AXIS, 2000);
                    radio.setAxis(TILT_AXIS, 0.55F);
                    PROBE.clear();
                    next(ctx);
                    return StepResult.pending();
                }
                return started.status() == StepResult.Status.FAIL ? flightStateDone(ctx, started) : started;
            }
            case 2 -> {
                QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                if (drone == null || !PilotSession.INSTANCE.isActive()) {
                    return flightStateDone(ctx, StepResult.fail("FPV-Sitzung endete"));
                }
                boolean client = PropwashClientApi.input().switchActive(ghost);
                double clientTilt = PropwashClientApi.input().axis(tilt);
                double gimbal = gimbalTilt(drone);
                boolean clientReady = client && Math.abs(clientTilt - 0.5) <= TILT_TOLERANCE;
                if (!clientReady || !PROBE.sees(true, 0.5) || Math.abs(gimbal - 0.5) > TILT_TOLERANCE || gimbalForeign(drone)) {
                    if (since(ctx) <= 100) {
                        return StepResult.pending();
                    }
                    return flightStateDone(ctx, StepResult.fail(String.format(Locale.ROOT,
                            "Geist an, Achse 0,55 (Totzone 10 %%): Client %s/%.3f, %s, GimbalContext#axis %.3f%s", client, clientTilt, PROBE.describe(),
                            gimbal, gimbalForeign(drone) ? ", fremde Ids mit Wert" : "")));
                }
                ctx.log("Geist an, Achse 0,55: " + PROBE.describe() + String.format(Locale.ROOT, ", GimbalContext#axis %.3f", gimbal));
                radio.setMicros(GHOST_AXIS, 1000);
                radio.setAxis(TILT_AXIS, 0.0F);
                PROBE.clear();
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                if (drone == null || !PilotSession.INSTANCE.isActive()) {
                    return flightStateDone(ctx, StepResult.fail("FPV-Sitzung endete"));
                }
                boolean client = PropwashClientApi.input().switchActive(ghost);
                double gimbal = gimbalTilt(drone);
                if (client || !PROBE.sees(false, 0.0) || Math.abs(gimbal) > TILT_TOLERANCE) {
                    if (since(ctx) <= 100) {
                        return StepResult.pending();
                    }
                    return flightStateDone(ctx, StepResult.fail(String.format(Locale.ROOT, "Geist aus, Achse 0: Client %s, %s, GimbalContext#axis %.3f",
                            client, PROBE.describe(), gimbal)));
                }
                ctx.log("Geist aus, Achse 0: " + PROBE.describe() + String.format(Locale.ROOT, ", GimbalContext#axis %.3f", gimbal));
                ctx.state().put(FUTURE, ctx.onServer(AddonSwitchSelfTests::land));
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                StepResult server = await(ctx, "Landen");
                if (server.status() != StepResult.Status.PASS) {
                    return server.status() == StepResult.Status.FAIL ? flightStateDone(ctx, server) : server;
                }
                if (PilotSession.INSTANCE.isActive()) {
                    return since(ctx) > 200 ? flightStateDone(ctx, StepResult.fail("FPV-Sitzung endet nicht")) : StepResult.pending();
                }
                ctx.state().remove(FUTURE);
                return flightStateDone(ctx, StepResult.pass());
            }
            default -> {
                return flightStateDone(ctx, StepResult.fail("Phase " + phase(ctx)));
            }
        }
    }

    static List<String> chat(Minecraft mc) {
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            List<?> messages = (List<?>) field.get(mc.gui.hud.getChat());
            List<String> out = new ArrayList<>();
            for (Object message : messages) {
                if (message instanceof GuiMessage gui) {
                    out.add(gui.content().getString());
                }
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of("? " + e);
        }
    }

    static int chatLines(Minecraft mc, String needle) {
        int count = 0;
        for (String line : chat(mc)) {
            if (line.contains(needle)) {
                count++;
            }
        }
        return count;
    }

    static DroneBuild signalBuild() {
        AccessoryType light = AccessoryType.byId(ExampleSwitches.SIGNAL_LIGHT).orElseThrow();
        return DroneBuild.FREESTYLE.withAccessory(light);
    }

    private static StepResult hintStep(SelfTest.StepContext ctx) {
        if (!exampleLoaded()) {
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        Component base = Component.translatable(SwitchFunction.ACCESSORY_1.translationKey());
        String named = Component.translatable(AddonSwitchRows.NAMED_KEY, base, Component.translatable(ExampleSwitches.actionKey(1))).getString();
        String title = Component.translatable(AccessoryHintClient.TITLE_KEY,
                Component.translatable("part." + ExampleSwitches.SIGNAL_LIGHT.getNamespace() + "." + ExampleSwitches.SIGNAL_LIGHT.getPath())).getString();
        switch (phase(ctx)) {
            case 0 -> {
                AccessoryHintClient.forgetAll();
                AccessoryHintClient.mute(false);
                ctx.state().put("shown", AccessoryHintClient.shownCount());
                ctx.state().put("chat", chatLines(mc, title));
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult started = startFlight(ctx, signalBuild());
                if (started.status() == StepResult.Status.PASS) {
                    next(ctx);
                    return StepResult.pending();
                }
                return started;
            }
            case 2 -> {
                int shown = AccessoryHintClient.shownCount() - (int) ctx.state().get("shown");
                int chat = chatLines(mc, title) - (int) ctx.state().get("chat");
                if (shown < 1 || chat < 1) {
                    return since(ctx) > 100 ? StepResult.fail("Hinweis beim ersten Flug fehlt: " + shown + " gezählt, " + chat + " Zeilen „" + title + "“")
                            : StepResult.pending();
                }
                String row = AddonSwitchRows.title(SwitchFunction.ACCESSORY_1, base).getString();
                if (!row.equals(named)) {
                    return StepResult.fail("Zubehör-Aktion 1 heißt „" + row + "“ statt „" + named + "“");
                }
                String plain = AddonSwitchRows.title(SwitchFunction.ACCESSORY_3, Component.translatable(SwitchFunction.ACCESSORY_3.translationKey())).getString();
                if (!plain.equals(Component.translatable(SwitchFunction.ACCESSORY_3.translationKey()).getString())) {
                    return StepResult.fail("Zubehör-Aktion 3 ohne Namen heißt „" + plain + "“");
                }
                List<String> lines = chat(mc);
                int at = lines.indexOf(lines.stream().filter(l -> l.contains(title)).findFirst().orElse(""));
                ctx.log("Hinweis: " + String.join(" | ", at < 0 ? List.of() : lines.subList(Math.max(0, at - 2), at + 1)) + "; Tab „Modi“: „" + row + "“");
                ctx.state().put("signals", ExampleSwitches.SIGNALS.get());
                KeyMapping action = FunctionKeys.mapping(SwitchFunction.ACCESSORY_1);
                if (action == null || action.isUnbound() || !action.getDefaultKey().equals(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_NUMPAD9))) {
                    return StepResult.fail("Zubehör-Aktion 1 liegt nicht auf Ziffernblock 9: " + (action == null ? "-" : action.saveString()));
                }
                action.setDown(true);
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                KeyMapping action = FunctionKeys.mapping(SwitchFunction.ACCESSORY_1);
                if (action != null) {
                    action.setDown(true);
                }
                if (ExampleSwitches.SIGNALS.get() <= (int) ctx.state().get("signals")) {
                    return since(ctx) > 100 ? StepResult.fail("Ziffernblock 9 löst Zubehör-Aktion 1 nicht aus") : StepResult.pending();
                }
                if (action != null) {
                    action.setDown(false);
                }
                if (!PilotSession.INSTANCE.isActive()) {
                    return StepResult.fail("Ziffernblock 9 beendet den Flug");
                }
                KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                if (PilotSession.INSTANCE.isActive()) {
                    return since(ctx) > 100 ? StepResult.fail("Rechtsklick beendet den Flug mit Zubehör nicht") : StepResult.pending();
                }
                ctx.state().put("shown", AccessoryHintClient.shownCount());
                ctx.state().put("chat", chatLines(mc, title));
                ctx.state().remove(FUTURE);
                next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                StepResult started = startFlight(ctx, signalBuild());
                if (started.status() == StepResult.Status.PASS) {
                    next(ctx);
                    return StepResult.pending();
                }
                return started;
            }
            case 6 -> {
                if (since(ctx) < AccessoryHintClient.DELAY_TICKS + 40) {
                    return StepResult.pending();
                }
                int shown = AccessoryHintClient.shownCount() - (int) ctx.state().get("shown");
                int chat = chatLines(mc, title) - (int) ctx.state().get("chat");
                if (shown != 0 || chat != 0) {
                    return StepResult.fail("Hinweis beim zweiten Flug noch einmal: " + shown + " gezählt, " + chat + " Zeilen");
                }
                ctx.state().put(FUTURE, ctx.onServer(AddonSwitchSelfTests::land));
                next(ctx);
                return StepResult.pending();
            }
            case 7 -> {
                StepResult server = await(ctx, "Landen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (PilotSession.INSTANCE.isActive()) {
                    return since(ctx) > 100 ? StepResult.fail("Sitzung endet nicht") : StepResult.pending();
                }
                ctx.log("zweiter Flug ohne Hinweis, Ziffernblock 9 löste " + (ExampleSwitches.SIGNALS.get() - (int) ctx.state().get("signals"))
                        + "-mal aus, Rechtsklick beendete den Flug");
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase " + phase(ctx));
            }
        }
    }

    private static StepResult layoutStep(SelfTest.StepContext ctx, int[] size, String file) {
        if (!exampleLoaded()) {
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        int frameWidth = size[0] * size[2];
        int frameHeight = size[1] * size[2];
        switch (phase(ctx)) {
            case 0 -> {
                if (savedWindow == null) {
                    savedWindow = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
                }
                if (radio == null) {
                    installRadio(radioProfile());
                }
                mc.gui.setScreen(null);
                float density = Math.max(1.0F, window.getPixelDensity());
                window.setWindowed(Math.round(frameWidth / density), Math.round(frameHeight / density));
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                boolean sized = window.getWidth() == frameWidth && window.getHeight() == frameHeight;
                if (!sized && since(ctx) < WINDOW_TICKS) {
                    return StepResult.pending();
                }
                mc.options.guiScale().set(size[2]);
                mc.resizeGui();
                if (window.getGuiScaledWidth() != size[0] || window.getGuiScaledHeight() != size[1]) {
                    restore(mc);
                    return StepResult.fail("GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + " statt " + size[0] + "x" + size[1]);
                }
                ConfigTabs.open(null, ModesTab.ID);
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (!(mc.gui.screen() instanceof ConfiguratorScreen configurator) || !(configurator.activeTab() instanceof ModesTab modes)) {
                    if (since(ctx) > 60) {
                        restore(mc);
                        return StepResult.fail("Tab „Modi“ öffnet nicht");
                    }
                    return StepResult.pending();
                }
                modes.showAddons();
                modes.addons().select(ExampleSwitches.GHOST);
                configurator.selectTab(ModesTab.ID);
                Screenshots.parkMouse(mc);
                Screenshots.hideMarker(true);
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                if (since(ctx) < 6) {
                    return StepResult.pending();
                }
                if (!(mc.gui.screen() instanceof ConfiguratorScreen configurator)) {
                    restore(mc);
                    return StepResult.fail("Konfigurator geschlossen");
                }
                String tabs = pageTabProblem(configurator);
                if (tabs != null) {
                    restore(mc);
                    return StepResult.fail(tabs);
                }
                List<LayoutAudit.Finding> findings = LayoutProbe.audit(mc, configurator);
                ctx.state().put("findings", findings);
                if (Screenshots.enabled()) {
                    Screenshots.clearOverlays(mc);
                    ctx.state().put("shot", Screenshots.capture(file));
                }
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                @SuppressWarnings("unchecked")
                CompletableFuture<String> shot = (CompletableFuture<String>) ctx.state().get("shot");
                if (shot != null && !shot.isDone()) {
                    return StepResult.pending();
                }
                if (mc.gui.screen() instanceof ConfiguratorScreen configurator && configurator.activeTab() instanceof ModesTab modes) {
                    modes.addons().select(ExampleSwitches.GIMBAL_TILT);
                }
                next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                if (since(ctx) < 4) {
                    return StepResult.pending();
                }
                if (mc.gui.screen() instanceof ConfiguratorScreen configurator) {
                    addFindings(ctx, LayoutProbe.audit(mc, configurator), "Achse");
                }
                TransmitterInput.INSTANCE.simulate(null);
                next(ctx);
                return StepResult.pending();
            }
            case 6 -> {
                if (since(ctx) < 6 || TransmitterInput.INSTANCE.raw() != null) {
                    return since(ctx) > 60 ? StepResult.fail("Funke lässt sich nicht abziehen") : StepResult.pending();
                }
                if (mc.gui.screen() instanceof ConfiguratorScreen configurator) {
                    addFindings(ctx, LayoutProbe.audit(mc, configurator), "Tastatur");
                }
                if (radio != null) {
                    TransmitterInput.INSTANCE.simulate(radio);
                }
                next(ctx);
                return StepResult.pending();
            }
            case 7 -> {
                @SuppressWarnings("unchecked")
                CompletableFuture<String> shot = (CompletableFuture<String>) ctx.state().get("shot");
                @SuppressWarnings("unchecked")
                List<LayoutAudit.Finding> findings = (List<LayoutAudit.Finding>) ctx.state().get("findings");
                restore(mc);
                if (shot != null) {
                    if (shot.isCompletedExceptionally()) {
                        return StepResult.fail("Bildschirmfoto " + file + ": " + shot.exceptionNow());
                    }
                    ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(file + ".png") + " (" + shot.join() + ")");
                }
                if (!findings.isEmpty()) {
                    for (LayoutAudit.Finding finding : findings) {
                        Constants.LOG.warn("[Selbsttest] {}x{}: {}", size[0], size[1], finding.describe());
                    }
                    return StepResult.fail(findings.size() + " Befunde, zuerst: " + findings.getFirst().describe());
                }
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase " + phase(ctx));
            }
        }
    }

    private static @Nullable String pageTabProblem(ConfiguratorScreen configurator) {
        String addons = AddonSwitchRows.pageTitle().getString();
        Integer row = null;
        for (GuiEventListener child : configurator.children()) {
            if (child instanceof FlatButton button && button.getMessage().getString().equals(addons)) {
                row = button.getY();
            }
        }
        if (row == null) {
            return "Reiter „" + addons + "“ fehlt im Kopf des Modi-Tabs";
        }
        List<String> tabs = new ArrayList<>();
        for (GuiEventListener child : configurator.children()) {
            if (child instanceof FlatButton button && button.getY() == row) {
                tabs.add(button.getMessage().getString());
            }
        }
        List<String> expected = List.of(Component.translatable("propwash.input.modes.page.flight").getString(),
                Component.translatable("propwash.input.modes.page.commands").getString(), addons);
        return tabs.containsAll(expected) ? null : "Reiter im Kopf des Modi-Tabs heißen " + tabs + " statt " + expected;
    }

    @SuppressWarnings("unchecked")
    private static void addFindings(SelfTest.StepContext ctx, List<LayoutAudit.Finding> more, String view) {
        List<LayoutAudit.Finding> findings = new ArrayList<>((List<LayoutAudit.Finding>) ctx.state().get("findings"));
        for (LayoutAudit.Finding finding : more) {
            findings.add(new LayoutAudit.Finding(finding.kind(), finding.subject(), finding.detail() + " (" + view + ")"));
        }
        ctx.state().put("findings", findings);
    }

    private static void restore(Minecraft mc) {
        if (mc.gui.screen() instanceof ConfiguratorScreen configurator) {
            configurator.discardAndClose();
        }
        Screenshots.hideMarker(false);
        int[] saved = savedWindow;
        if (saved != null) {
            mc.options.guiScale().set(saved[2]);
            mc.getWindow().setWindowed(saved[0], saved[1]);
            mc.resizeGui();
            savedWindow = null;
        }
    }

    private static StepResult hintShotStep(SelfTest.StepContext ctx, String file) {
        if (!exampleLoaded()) {
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        switch (phase(ctx)) {
            case 0 -> {
                AccessoryHintClient.forgetAll();
                AccessoryHintClient.mute(false);
                mc.gui.hud.getChat().clearMessages(false);
                ctx.state().put("shown", AccessoryHintClient.shownCount());
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult started = startFlight(ctx, signalBuild());
                if (started.status() == StepResult.Status.PASS) {
                    next(ctx);
                    return StepResult.pending();
                }
                return started;
            }
            case 2 -> {
                if (AccessoryHintClient.shownCount() <= (int) ctx.state().get("shown")) {
                    return since(ctx) > 120 ? StepResult.fail("Hinweis fehlt") : StepResult.pending();
                }
                if (since(ctx) < 20) {
                    return StepResult.pending();
                }
                Screenshots.parkMouse(mc);
                Screenshots.hideMarker(true);
                ctx.state().put("shot", Screenshots.capture(file));
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                @SuppressWarnings("unchecked")
                CompletableFuture<String> shot = (CompletableFuture<String>) ctx.state().get("shot");
                if (!shot.isDone()) {
                    return StepResult.pending();
                }
                Screenshots.hideMarker(false);
                ctx.state().put(FUTURE, ctx.onServer(AddonSwitchSelfTests::land));
                ctx.state().put("result", shot.isCompletedExceptionally() ? "Fehler: " + shot.exceptionNow() : shot.join());
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                StepResult server = await(ctx, "Landen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (PilotSession.INSTANCE.isActive()) {
                    return since(ctx) > 100 ? StepResult.fail("Sitzung endet nicht") : StepResult.pending();
                }
                String result = (String) ctx.state().get("result");
                if (result.startsWith("Fehler")) {
                    return StepResult.fail("Bildschirmfoto " + file + ": " + result);
                }
                ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(file + ".png") + " (" + result + ")");
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase " + phase(ctx));
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!ctx.state().containsKey(FUTURE)) {
            AccessoryHintClient.mute(false);
            removeRadio();
            restore(mc);
            ctx.state().put(FUTURE, ctx.onServer(AddonSwitchSelfTests::land));
            return StepResult.pending();
        }
        StepResult server = await(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        return PilotSession.INSTANCE.isActive() && ctx.tick() < 90 ? StepResult.pending() : StepResult.pass();
    }
}
