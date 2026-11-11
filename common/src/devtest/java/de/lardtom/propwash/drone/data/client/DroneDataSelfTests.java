package de.lardtom.propwash.drone.data.client;

import com.mojang.serialization.Codec;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.ServiceExtension;
import de.lardtom.propwash.api.drone.DroneDataAccess;
import de.lardtom.propwash.api.drone.DroneDataKey;
import de.lardtom.propwash.apiimpl.client.ServiceExtensions;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.ExampleMission;
import de.lardtom.propwash.dev.example.client.ExampleMissionClient;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DroneServiceMenu;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.DroneServiceScreen;
import de.lardtom.propwash.drone.data.AddonData;
import de.lardtom.propwash.drone.data.DroneDataComponents;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class DroneDataSelfTests {
    public static final String GROUP = "addon-api-17: ";
    public static final String SYNC_STEP = GROUP + "B6 Daten an der Drohne – Mission schreiben, aufheben und setzen, Grenzen 2 KiB/8 KiB, Sync TRACKING und NONE";
    public static final String SAVE_STEP = GROUP + "B6 fremde Daten – Drohne mit Daten eines fehlenden Add-ons entladen, speichern und neu laden → unverändert";
    public static final String SERVICE_STEP = GROUP + "B10 Wartungsknopf öffnet das Kind-Fenster, „Mehr“ bei vielen Knöpfen, PILOT_CHANGED in beiden Richtungen";
    public static final Identifier FOREIGN = Identifier.fromNamespaceAndPath("propwash_absent", "route");
    public static final int FAR_BLOCKS = 4096;
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String RESULT = "result";
    private static final String ASKED = "asked";
    private static final int BLOBS = 4;
    private static final int PROBES = 3;
    private static final List<DroneDataKey<String>> BLOB_KEYS = new ArrayList<>();
    private static final List<Identifier> PROBE_IDS = new ArrayList<>();
    private static final AtomicReference<@Nullable Screen> PROBE_PARENT = new AtomicReference<>();
    private static final AtomicReference<String> PROBE_OPENED = new AtomicReference<>("");
    private static volatile boolean probesVisible;
    private static volatile int droneId = -1;
    private static volatile int copyId = -1;
    private static volatile @Nullable UUID farDrone;
    private static volatile @Nullable CompoundTag farExpected;
    private static volatile ExampleMission.@Nullable Mission expectedMission;

    private DroneDataSelfTests() {
    }

    public static void register() {
        for (int i = 0; i < BLOBS; i++) {
            BLOB_KEYS.add(PropwashApi.droneData().register(Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "b6_blob_" + i), Codec.STRING, "",
                    DroneDataKey.Sync.NONE));
        }
        for (int i = 1; i <= PROBES; i++) {
            Identifier id = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "b10_probe_" + i);
            PROBE_IDS.add(id);
            PropwashClientApi.registerServiceExtension(new Probe(id, i));
        }
        SelfTest.clientStep(SYNC_STEP, 900, DroneDataSelfTests::syncStep);
        SelfTest.clientStep(SAVE_STEP, 2400, DroneDataSelfTests::saveStep);
        SelfTest.clientStep(SERVICE_STEP, 1200, DroneDataSelfTests::serviceStep);
        if (Screenshots.enabled()) {
            Screenshots.step("bildschirmfotos: Wartung mit Knöpfen von Add-ons", "drone_service_addons", 30, new ServiceScene(false));
            Screenshots.step("bildschirmfotos: Kind-Fenster Mission des Beispiel-Add-ons", "drone_service_addon_window", 30, new ServiceScene(true));
        }
    }

    private static final class ServiceScene implements Screenshots.Scene {
        private final boolean window;

        ServiceScene(boolean window) {
            this.window = window;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                if (ExampleMission.mission() == null) {
                    return StepResult.fail("Beispiel-Add-on aus");
                }
                ExampleMission.testing(true);
                probesVisible = !this.window;
                serverCheck(ctx, server -> {
                    String opened = openService(server);
                    QuadcopterEntity drone = serverDrone(server, droneId);
                    if (drone != null) {
                        drone.setCustomName(Component.literal("Nighthawk"));
                        ExampleMission.Mission mission = ExampleMission.around(drone.blockPosition().offset(40, 0, -25), "Turmrunde");
                        PropwashApi.droneData().set(drone, ExampleMission.mission(), new ExampleMission.Mission(mission.name(), mission.waypoints(), 3));
                    }
                    return opened;
                });
                return StepResult.pending();
            }
            String server = serverResult(ctx);
            if (server == null) {
                return waited(ctx) > 300 ? StepResult.fail("Server antwortet nicht") : StepResult.pending();
            }
            if (!server.isEmpty()) {
                return StepResult.fail(server);
            }
            Screen screen = mc.gui.screen();
            if (this.window) {
                if (screen instanceof ExampleMissionClient.MissionScreen) {
                    return StepResult.pass();
                }
                if (screen instanceof DroneServiceScreen service && !extensionButtons(service).isEmpty()) {
                    extensionButtons(service).getFirst().press();
                }
            } else if (screen instanceof DroneServiceScreen) {
                return StepResult.pass();
            }
            return ctx.tick() > 300 ? StepResult.fail("Wartung fehlt: " + screen) : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            finishService(ctx, null);
        }
    }

    private record Probe(Identifier id, int number) implements ServiceExtension {
        @Override
        public Component label() {
            return Component.literal("Probe " + this.number);
        }

        @Override
        public boolean visible(Entity drone) {
            return probesVisible;
        }

        @Override
        public void open(Entity drone, @Nullable Screen parent) {
            PROBE_OPENED.set(this.id.toString());
            PROBE_PARENT.set(parent);
            Minecraft.getInstance().gui.setScreen(parent);
        }
    }

    private static StepResult syncStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            if (ExampleMission.mission() == null) {
                return StepResult.fail("Beispiel-Add-on aus (PROPWASH_DEV_EXAMPLE), Schlüssel fehlen");
            }
            serverCheck(ctx, DroneDataSelfTests::writeAndCarry);
            return next(ctx, 1);
        }
        String server = serverResult(ctx);
        if (server == null) {
            return waited(ctx) > 300 ? finishSync(ctx, "Server antwortet nicht") : StepResult.pending();
        }
        if (!server.isEmpty()) {
            return finishSync(ctx, server);
        }
        QuadcopterEntity drone = clientDrone(mc, droneId);
        QuadcopterEntity copy = clientDrone(mc, copyId);
        ExampleMission.Mission expected = expectedMission;
        switch (phase) {
            case 1 -> {
                if (drone == null || copy == null || expected == null) {
                    return since(ctx) > 200 ? finishSync(ctx, "Drohnen am Client nicht da") : StepResult.pending();
                }
                if (!expected.equals(PropwashApi.droneData().get(drone, ExampleMission.mission()))
                        || !expected.equals(PropwashApi.droneData().get(copy, ExampleMission.mission()))) {
                    return since(ctx) > 200 ? finishSync(ctx, "TRACKING-Daten erreichen den Client nicht: "
                            + PropwashApi.droneData().get(drone, ExampleMission.mission())) : StepResult.pending();
                }
                String problem = clientView(drone);
                if (problem == null) {
                    problem = clientView(copy);
                }
                if (problem != null) {
                    return finishSync(ctx, problem);
                }
                ctx.log("Client: Mission (TRACKING) an beiden Drohnen da, Protokoll (NONE) und Daten des fehlenden Add-ons nicht, Schreiben am Client wirft");
                serverCheck(ctx, srv -> {
                    QuadcopterEntity served = serverDrone(srv, droneId);
                    if (served == null) {
                        return "Drohne am Server weg";
                    }
                    ExampleMission.Mission flown = PropwashApi.droneData().get(served, ExampleMission.mission()).flown();
                    PropwashApi.droneData().set(served, ExampleMission.mission(), flown);
                    expectedMission = flown;
                    return "";
                });
                return next(ctx, 2);
            }
            case 2 -> {
                if (drone == null || expected == null || !expected.equals(PropwashApi.droneData().get(drone, ExampleMission.mission()))) {
                    return since(ctx) > 200 ? finishSync(ctx, "geänderte Mission kommt nicht am Client an") : StepResult.pending();
                }
                serverCheck(ctx, srv -> {
                    QuadcopterEntity served = serverDrone(srv, droneId);
                    if (served == null) {
                        return "Drohne am Server weg";
                    }
                    PropwashApi.droneData().remove(served, ExampleMission.mission());
                    return served.addonData().contains(ExampleMission.MISSION_ID) ? "remove lässt die Mission stehen" : "";
                });
                return next(ctx, 3);
            }
            default -> {
                if (drone == null || drone.addonData().contains(ExampleMission.MISSION_ID)
                        || !ExampleMission.Mission.EMPTY.equals(PropwashApi.droneData().get(drone, ExampleMission.mission()))) {
                    return since(ctx) > 200 ? finishSync(ctx, "entfernte Mission bleibt am Client") : StepResult.pending();
                }
                ctx.log("Mission geändert und entfernt: Client folgt jeweils, Standard nach remove");
                return finishSync(ctx, null);
            }
        }
    }

    private static @Nullable String clientView(QuadcopterEntity drone) {
        AddonData synced = drone.addonData();
        if (!synced.ids().equals(List.of(ExampleMission.MISSION_ID))) {
            return "Client-Abgleich enthält " + synced.ids() + " statt nur der Mission";
        }
        if (!ExampleMission.ServiceLog.EMPTY.equals(PropwashApi.droneData().get(drone, ExampleMission.log()))) {
            return "NONE-Daten am Client lesbar: " + PropwashApi.droneData().get(drone, ExampleMission.log());
        }
        try {
            PropwashApi.droneData().set(drone, ExampleMission.mission(), ExampleMission.Mission.EMPTY);
            return "set am Client wirft nicht";
        } catch (IllegalStateException expected) {
        }
        try {
            PropwashApi.droneData().remove(drone, ExampleMission.mission());
            return "remove am Client wirft nicht";
        } catch (IllegalStateException expected) {
        }
        return null;
    }

    private static String writeAndCarry(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        DroneDataAccess data = PropwashApi.droneData();
        QuadcopterEntity drone = spawn(level, player.position().add(0.0, 0.5, 3.0));
        droneId = drone.getId();
        if (!ExampleMission.Mission.EMPTY.equals(data.get(drone, ExampleMission.mission())) || !drone.addonData().isEmpty()) {
            return "neue Drohne hat schon Add-on-Daten: " + drone.addonData();
        }
        ExampleMission.Mission mission = ExampleMission.around(drone.blockPosition(), "B6");
        ExampleMission.ServiceLog log = new ExampleMission.ServiceLog(2, Optional.of(player.getUUID()));
        data.set(drone, ExampleMission.mission(), mission);
        data.set(drone, ExampleMission.log(), log);
        drone.setAddonData(drone.addonData().withRaw(FOREIGN, foreign()));
        expectedMission = mission;
        if (!mission.equals(data.get(drone, ExampleMission.mission())) || !log.equals(data.get(drone, ExampleMission.log()))) {
            return "Server liest nicht, was er geschrieben hat";
        }
        AddonData before = drone.addonData();
        try {
            data.set(drone, BLOB_KEYS.getFirst(), "x".repeat(DroneDataAccess.MAX_BYTES_PER_KEY));
            return "Wert über 2 KiB angenommen";
        } catch (IllegalArgumentException expected) {
        }
        int stored = 0;
        boolean full = false;
        for (DroneDataKey<String> key : BLOB_KEYS) {
            try {
                data.set(drone, key, "y".repeat(2000));
                stored++;
            } catch (IllegalStateException e) {
                full = true;
                break;
            }
        }
        if (!full || drone.addonData().bytes() > DroneDataAccess.MAX_BYTES_PER_DRONE) {
            return "Grenze 8 KiB je Drohne greift nicht (" + stored + " Blöcke, " + drone.addonData().bytes() + " Bytes)";
        }
        for (DroneDataKey<String> key : BLOB_KEYS) {
            data.remove(drone, key);
        }
        if (!before.equals(drone.addonData())) {
            return "Daten nach abgelehnten Werten verändert";
        }
        try {
            data.set(player, ExampleMission.mission(), mission);
            return "Spieler als Drohne angenommen";
        } catch (IllegalArgumentException expected) {
        }
        if (!ExampleMission.Mission.EMPTY.equals(data.get(player, ExampleMission.mission()))) {
            return "get am Spieler liefert nicht den Standard";
        }
        ItemStack item = drone.toStack();
        AddonData onItem = item.get(DroneDataComponents.ADDON_DATA.get());
        if (!before.equals(onItem)) {
            return "Item-Komponente weicht ab: " + onItem;
        }
        if (!mission.equals(data.get(item, ExampleMission.mission())) || !log.equals(data.get(item, ExampleMission.log()))) {
            return "get(ItemStack) liest die Komponente nicht";
        }
        ItemStack picked = drone.getPickResult();
        if (picked == null || !before.equals(picked.get(DroneDataComponents.ADDON_DATA.get()))) {
            return "Pick-Block verliert die Daten";
        }
        QuadcopterEntity copy = spawn(level, player.position().add(1.5, 0.5, 3.0));
        copy.loadFromStack(item);
        copyId = copy.getId();
        if (!before.equals(copy.addonData()) || !before.toTag().equals(copy.addonData().toTag())) {
            return "aufgesetzte Drohne hat andere Daten: " + copy.addonData();
        }
        if (!mission.equals(data.get(copy, ExampleMission.mission())) || !copy.addonData().raw(FOREIGN).equals(before.raw(FOREIGN))) {
            return "aufgesetzte Drohne liest anders";
        }
        return "";
    }

    private static StepResult saveStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            if (ExampleMission.mission() == null) {
                return StepResult.fail("Beispiel-Add-on aus (PROPWASH_DEV_EXAMPLE), Schlüssel fehlen");
            }
            serverCheck(ctx, DroneDataSelfTests::spawnFar);
            return next(ctx, 1);
        }
        String server = serverResult(ctx);
        if (server == null) {
            return waited(ctx) > 400 ? finishSave(ctx, "Server antwortet nicht") : StepResult.pending();
        }
        if (server.startsWith("FAIL ")) {
            return finishSave(ctx, server.substring(5));
        }
        if (phase == 1) {
            if (since(ctx) % 20 != 0) {
                return StepResult.pending();
            }
            if (!server.equals("unloaded")) {
                if (since(ctx) > 1000) {
                    return finishSave(ctx, "Chunk mit der Drohne wird nicht entladen");
                }
                serverCheck(ctx, DroneDataSelfTests::checkUnloaded);
                return StepResult.pending();
            }
            ctx.log("Drohne entladen und Welt gespeichert, lade den Chunk neu");
            serverCheck(ctx, srv -> "loading");
            return next(ctx, 2);
        }
        if (since(ctx) % 20 != 0) {
            return StepResult.pending();
        }
        if (server.equals("loading")) {
            if (since(ctx) > 1000) {
                return finishSave(ctx, "Drohne nach dem Neuladen nicht wieder da");
            }
            serverCheck(ctx, DroneDataSelfTests::checkReloaded);
            return StepResult.pending();
        }
        ctx.log(server);
        return finishSave(ctx, null);
    }

    private static String spawnFar(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        int x = player.getBlockX() + FAR_BLOCKS;
        int z = player.getBlockZ();
        level.setChunkForced(x >> 4, z >> 4, true);
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        QuadcopterEntity drone = spawn(level, new Vec3(x + 0.5, y + 1.0, z + 0.5));
        PropwashApi.droneData().set(drone, ExampleMission.mission(), ExampleMission.around(drone.blockPosition(), "fern"));
        PropwashApi.droneData().set(drone, ExampleMission.log(), new ExampleMission.ServiceLog(7, Optional.of(player.getUUID())));
        drone.setAddonData(drone.addonData().withRaw(FOREIGN, foreign()));
        farDrone = drone.getUUID();
        farExpected = drone.addonData().toTag();
        level.setChunkForced(x >> 4, z >> 4, false);
        return "waiting";
    }

    private static String checkUnloaded(MinecraftServer server) {
        ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
        UUID uuid = farDrone;
        if (uuid == null) {
            return "FAIL keine ferne Drohne";
        }
        if (level.getEntity(uuid) != null) {
            return "waiting";
        }
        server.saveEverything(true, true, true);
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        int x = player.getBlockX() + FAR_BLOCKS;
        int z = player.getBlockZ();
        level.setChunkForced(x >> 4, z >> 4, true);
        return "unloaded";
    }

    private static String checkReloaded(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        UUID uuid = farDrone;
        CompoundTag expected = farExpected;
        if (uuid == null || expected == null) {
            return "FAIL keine ferne Drohne";
        }
        if (!(level.getEntity(uuid) instanceof QuadcopterEntity drone)) {
            return "loading";
        }
        AddonData loaded = drone.addonData();
        int x = player.getBlockX() + FAR_BLOCKS;
        int z = player.getBlockZ();
        drone.discard();
        level.setChunkForced(x >> 4, z >> 4, false);
        if (!expected.equals(loaded.toTag())) {
            return "FAIL Daten nach dem Neuladen verändert: " + loaded.toTag() + " statt " + expected;
        }
        int bytes = AddonData.bytes(expected);
        if (bytes != loaded.bytes()) {
            return "FAIL Größe " + loaded.bytes() + " statt " + bytes + " Bytes";
        }
        if (!loaded.raw(FOREIGN).equals(Optional.of(foreign()))) {
            return "FAIL Daten des fehlenden Add-ons verändert: " + loaded.raw(FOREIGN);
        }
        ExampleMission.Mission mission = PropwashApi.droneData().get(drone, ExampleMission.mission());
        if (!"fern".equals(mission.name()) || mission.waypoints().size() != 4) {
            return "FAIL Mission nach dem Neuladen: " + mission;
        }
        return "Drohne " + FAR_BLOCKS + " Blöcke entfernt entladen, Welt gespeichert, neu geladen: " + bytes + " Bytes Add-on-Daten byte-gleich, "
                + FOREIGN + " (Add-on nicht geladen) unverändert, Mission und Protokoll lesbar";
    }

    private static StepResult serviceStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            if (ExampleMission.mission() == null) {
                return StepResult.fail("Beispiel-Add-on aus (PROPWASH_DEV_EXAMPLE), Schlüssel fehlen");
            }
            ExampleMission.testing(true);
            probesVisible = false;
            PROBE_OPENED.set("");
            PROBE_PARENT.set(null);
            serverCheck(ctx, DroneDataSelfTests::openService);
            return next(ctx, 1);
        }
        String server = serverResult(ctx);
        if (server == null) {
            return waited(ctx) > 300 ? finishService(ctx, "Server antwortet nicht") : StepResult.pending();
        }
        if (!server.isEmpty() && phase != 7) {
            return finishService(ctx, server);
        }
        Screen screen = mc.gui.screen();
        switch (phase) {
            case 1 -> {
                if (!(screen instanceof DroneServiceScreen service)) {
                    return since(ctx) > 200 ? finishService(ctx, "Wartung öffnet nicht: " + screen) : StepResult.pending();
                }
                List<ServiceExtensions.ExtensionButton> buttons = extensionButtons(service);
                if (buttons.size() != 1 || !buttons.getFirst().extension().id().equals(ExampleMissionClient.BUTTON) || moreButton(service) != null) {
                    return finishService(ctx, "ohne Proben erwartet genau den Knopf des Beispiels, gefunden " + ids(buttons));
                }
                String layout = layout(mc, service);
                if (layout != null) {
                    return finishService(ctx, layout);
                }
                state.put("service", service);
                probesVisible = true;
                service.resize(service.width, service.height);
                return next(ctx, 2);
            }
            case 2 -> {
                if (!(screen instanceof DroneServiceScreen service) || service != state.get("service")) {
                    return finishService(ctx, "Wartung nach dem Neuaufbau ersetzt: " + screen);
                }
                List<ServiceExtensions.ExtensionButton> buttons = extensionButtons(service);
                ServiceExtensions.MoreButton more = moreButton(service);
                if (more == null || buttons.isEmpty() || buttons.size() > ServiceExtension.MAX_VISIBLE_BUTTONS
                        || buttons.size() + more.extensions().size() != PROBES + 1) {
                    return finishService(ctx, "mit vier Knöpfen erwartet höchstens drei und „Mehr“ mit dem Rest, gefunden " + ids(buttons) + " und "
                            + (more == null ? "kein Mehr" : more.extensions().size() + " unter Mehr"));
                }
                if (!buttons.getFirst().extension().id().equals(ExampleMissionClient.BUTTON)) {
                    return finishService(ctx, "Reihenfolge der Anmeldung nicht eingehalten: " + ids(buttons));
                }
                String layout = layout(mc, service);
                if (layout == null) {
                    layout = withAccessories(mc, service);
                }
                if (layout != null) {
                    return finishService(ctx, layout);
                }
                state.put("shown", buttons.size());
                state.put("opened", ExampleMissionClient.OPENED.get());
                buttons.getFirst().press();
                return next(ctx, 3);
            }
            case 3 -> {
                if (!(screen instanceof ExampleMissionClient.MissionScreen mission)) {
                    return since(ctx) > 100 ? finishService(ctx, "Knopf öffnet kein Kind-Fenster: " + screen) : StepResult.pending();
                }
                if (mission.parent() != state.get("service") || !(mission instanceof ThemedScreen)
                        || ExampleMissionClient.OPENED.get() != (int) state.get("opened") + 1) {
                    return finishService(ctx, "Kind-Fenster ohne die Wartung als Eltern-Fenster oder ohne Propwash-Theme");
                }
                mission.onClose();
                return next(ctx, 4);
            }
            case 4 -> {
                if (screen != state.get("service") || !(mc.player != null && mc.player.containerMenu instanceof DroneServiceMenu)) {
                    return since(ctx) > 100 ? finishService(ctx, "Zurück aus dem Kind-Fenster führt nicht in die offene Wartung: " + screen)
                            : StepResult.pending();
                }
                ServiceExtensions.MoreButton more = moreButton((DroneServiceScreen) screen);
                if (more == null) {
                    return finishService(ctx, "„Mehr“ nach der Rückkehr verschwunden");
                }
                more.press();
                return next(ctx, 5);
            }
            case 5 -> {
                if (!(screen instanceof ServiceExtensions.MoreScreen list)) {
                    return since(ctx) > 100 ? finishService(ctx, "„Mehr“ öffnet keine Liste: " + screen) : StepResult.pending();
                }
                int expected = PROBES + 1 - (int) state.get("shown");
                if (list.entries().size() != expected || list.parent() != state.get("service")) {
                    return finishService(ctx, "Liste unter „Mehr“ mit " + list.entries().size() + " statt " + expected + " Einträgen");
                }
                ServiceExtensions.ExtensionButton last = list.entries().getLast();
                state.put("probe", last.extension().id().toString());
                last.press();
                return next(ctx, 6);
            }
            case 6 -> {
                if (!PROBE_OPENED.get().equals(state.get("probe")) || PROBE_PARENT.get() != state.get("service") || screen != state.get("service")) {
                    return since(ctx) > 100 ? finishService(ctx, "Eintrag unter „Mehr“ öffnet nicht mit der Wartung als Eltern-Fenster: "
                            + PROBE_OPENED.get() + ", " + screen) : StepResult.pending();
                }
                serverCheck(ctx, DroneDataSelfTests::pilotChanges);
                return next(ctx, 7);
            }
            default -> {
                if (server.isEmpty()) {
                    return StepResult.pending();
                }
                if (!server.startsWith("OK ")) {
                    return finishService(ctx, server);
                }
                QuadcopterEntity drone = clientDrone(mc, droneId);
                ExampleMission.Mission mission = drone == null ? null : PropwashApi.droneData().get(drone, ExampleMission.mission());
                if (mission == null || mission.flights() != 1) {
                    return since(ctx) > 200 ? finishService(ctx, "Mission nach dem Pilotenwechsel nicht am Client: " + mission) : StepResult.pending();
                }
                ctx.log("Wartung: " + state.get("shown") + " Knöpfe und „Mehr“, Kind-Fenster mit Eltern-Fenster, " + server.substring(3));
                return finishService(ctx, null);
            }
        }
    }

    private static String openService(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        QuadcopterEntity drone = spawn(player.level(), player.position().add(0.0, 0.0, 3.0));
        droneId = drone.getId();
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider((containerId, inventory, p) -> new DroneServiceMenu(containerId, inventory, drone),
                DroneServiceMenu.TITLE), ByteBufCodecs.VAR_INT, drone.getId());
        return "";
    }

    private static String pilotChanges(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        QuadcopterEntity drone = serverDrone(server, droneId);
        if (drone == null) {
            return "Drohne am Server weg";
        }
        int taken = ExampleMission.PILOT_TAKEN.get();
        int left = ExampleMission.PILOT_LEFT.get();
        String uuid = player.getUUID().toString();
        drone.setPilot(player);
        String took = ExampleMission.LAST_CHANGE.get();
        drone.setPilot(null);
        String gave = ExampleMission.LAST_CHANGE.get();
        if (ExampleMission.PILOT_TAKEN.get() != taken + 1 || !took.equals("- -> " + uuid)) {
            return "PILOT_CHANGED beim Übernehmen: " + took + " (" + (ExampleMission.PILOT_TAKEN.get() - taken) + "×)";
        }
        if (ExampleMission.PILOT_LEFT.get() != left + 1 || !gave.equals(uuid + " -> -")) {
            return "PILOT_CHANGED beim Abgeben: " + gave + " (" + (ExampleMission.PILOT_LEFT.get() - left) + "×)";
        }
        ExampleMission.ServiceLog log = PropwashApi.droneData().get(drone, ExampleMission.log());
        ExampleMission.Mission mission = PropwashApi.droneData().get(drone, ExampleMission.mission());
        if (log.pilotChanges() != 2 || !log.lastPilot().equals(Optional.of(player.getUUID())) || mission.flights() != 1
                || mission.waypoints().size() != 4) {
            return "Beispiel schreibt beim Pilotenwechsel nicht: " + log + ", " + mission;
        }
        return "OK PILOT_CHANGED in beiden Richtungen (- -> Pilot, Pilot -> -), Beispiel schrieb Mission (Flug 1) und Protokoll (2 Wechsel)";
    }

    private static @Nullable String layout(Minecraft mc, DroneServiceScreen service) {
        int leftPos = (service.width - DroneServiceScreen.WIDTH) / 2;
        int topPos = (service.height - DroneServiceScreen.HEIGHT) / 2;
        int titleEnd = ThemeDraw.brandedTitleX(mc.font, leftPos + 2) + mc.font.width(service.getTitle());
        int right = leftPos + DroneServiceScreen.WIDTH - 3;
        int previous = Integer.MIN_VALUE;
        List<AbstractWidget> widgets = new ArrayList<>(extensionButtons(service));
        ServiceExtensions.MoreButton more = moreButton(service);
        if (more != null) {
            widgets.add(more);
        }
        for (AbstractWidget widget : widgets) {
            if (widget.getY() != topPos + 3 || widget.getX() <= titleEnd || widget.getRight() > right || widget.getX() < previous
                    || widget.getWidth() < Math.min(widget instanceof ServiceExtensions.MoreButton ? ServiceExtensions.COMPACT_WIDTH
                    : ServiceExtensions.MIN_WIDTH, mc.font.width(widget.getMessage()) + ServiceExtensions.PAD)) {
                return "Knopf " + widget.getMessage().getString() + " liegt nicht frei im Kopf der Wartung: x " + widget.getX() + "–"
                        + widget.getRight() + ", Titel bis " + titleEnd + ", Kopf bis " + right;
            }
            previous = widget.getRight();
        }
        return null;
    }

    private static @Nullable String withAccessories(Minecraft mc, DroneServiceScreen service) {
        int leftPos = (service.width - DroneServiceScreen.WIDTH) / 2;
        int left = ThemeDraw.brandedTitleX(mc.font, leftPos + 2) + mc.font.width(service.getTitle()) + 6;
        int accessories = mc.font.width(Component.translatable("propwash.accessory.service.button")) + 12;
        int room = leftPos + DroneServiceScreen.WIDTH - 3 - accessories - ServiceExtensions.GAP - left;
        QuadcopterEntity drone = service.getMenu().drone();
        if (drone == null) {
            return "Wartung ohne Drohne";
        }
        List<ServiceExtension> visible = ServiceExtensions.visible(drone);
        int[] natural = visible.stream().mapToInt(extension -> mc.font.width(ServiceExtensions.label(extension)) + ServiceExtensions.PAD).toArray();
        int moreWidth = mc.font.width(Component.translatable(ServiceExtensions.KEY + "more")) + ServiceExtensions.PAD;
        for (int count = 1; count <= natural.length; count++) {
            ServiceExtensions.Layout layout = ServiceExtensions.Layout.of(Arrays.copyOf(natural, count), moreWidth, room);
            boolean reachable = layout.widths().length == count || layout.more() > 0;
            if (!reachable || layout.total() > room) {
                return "mit Zubehör-Knopf (Platz " + room + ") sind " + count + " Knöpfe nicht erreichbar: " + layout.widths().length
                        + " sichtbar, Mehr " + layout.more();
            }
        }
        return null;
    }

    private static List<ServiceExtensions.ExtensionButton> extensionButtons(Screen screen) {
        List<ServiceExtensions.ExtensionButton> out = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof ServiceExtensions.ExtensionButton button) {
                out.add(button);
            }
        }
        return out;
    }

    private static ServiceExtensions.@Nullable MoreButton moreButton(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof ServiceExtensions.MoreButton button) {
                return button;
            }
        }
        return null;
    }

    private static List<String> ids(List<ServiceExtensions.ExtensionButton> buttons) {
        return buttons.stream().map(button -> button.extension().id().toString()).toList();
    }

    private static CompoundTag foreign() {
        CompoundTag route = new CompoundTag();
        route.put("points", new IntArrayTag(new int[]{4, 64, -9, 12, 70, 3}));
        route.put("stamp", new LongArrayTag(new long[]{Long.MIN_VALUE, 42L}));
        route.put("armed", ByteTag.valueOf((byte) 1));
        ListTag mixed = new ListTag();
        mixed.add(StringTag.valueOf("Kurve"));
        mixed.add(new CompoundTag());
        route.put("notes", mixed);
        route.put("empty", new ListTag());
        return route;
    }

    private static QuadcopterEntity spawn(ServerLevel level, Vec3 pos) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.RACER);
        drone.loadFromStack(stack);
        drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        level.addFreshEntity(drone);
        return drone;
    }

    private static void serverCheck(SelfTest.StepContext ctx, Function<MinecraftServer, String> check) {
        AtomicReference<@Nullable String> result = new AtomicReference<>();
        ctx.state().put(RESULT, result);
        ctx.state().put(ASKED, ctx.tick());
        ctx.state().put(FUTURE, ctx.onServer(server -> {
            try {
                result.set(check.apply(server));
            } catch (RuntimeException e) {
                result.set("Ausnahme am Server: " + e);
            }
        }));
    }

    @SuppressWarnings("unchecked")
    private static @Nullable String serverResult(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return "";
        }
        if (future.isCompletedExceptionally()) {
            return "Server: " + future.exceptionNow();
        }
        if (!future.isDone()) {
            return null;
        }
        return ((AtomicReference<String>) ctx.state().get(RESULT)).get();
    }

    private static @Nullable QuadcopterEntity clientDrone(Minecraft mc, int id) {
        Entity entity = mc.level == null || id < 0 ? null : mc.level.getEntity(id);
        return entity instanceof QuadcopterEntity drone ? drone : null;
    }

    private static @Nullable QuadcopterEntity serverDrone(MinecraftServer server, int id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                return drone;
            }
        }
        return null;
    }

    private static int waited(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(ASKED, 0);
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static StepResult next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult finishSync(SelfTest.StepContext ctx, @Nullable String problem) {
        int first = droneId;
        int second = copyId;
        ctx.onServer(server -> {
            for (int id : new int[]{first, second}) {
                QuadcopterEntity drone = serverDrone(server, id);
                if (drone != null) {
                    drone.discard();
                }
            }
        });
        droneId = -1;
        copyId = -1;
        expectedMission = null;
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static StepResult finishSave(SelfTest.StepContext ctx, @Nullable String problem) {
        UUID uuid = farDrone;
        ctx.onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            ServerLevel level = player.level();
            Entity far = uuid == null ? null : level.getEntity(uuid);
            if (far != null) {
                far.discard();
            }
            level.setChunkForced((player.getBlockX() + FAR_BLOCKS) >> 4, player.getBlockZ() >> 4, false);
        });
        farDrone = null;
        farExpected = null;
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static StepResult finishService(SelfTest.StepContext ctx, @Nullable String problem) {
        Minecraft mc = Minecraft.getInstance();
        ExampleMission.testing(false);
        probesVisible = false;
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        int id = droneId;
        ctx.onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.closeContainer();
            QuadcopterEntity drone = serverDrone(server, id);
            if (drone != null) {
                drone.discard();
            }
        });
        droneId = -1;
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }
}
