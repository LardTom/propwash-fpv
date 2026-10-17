package de.lardtom.propwash.interact.client;

import com.mojang.serialization.DynamicOps;
import de.lardtom.propwash.accessory.AccessoryContents;
import de.lardtom.propwash.accessory.AccessoryMenu;
import de.lardtom.propwash.accessory.AccessoryPayload;
import de.lardtom.propwash.accessory.AddonEvents;
import de.lardtom.propwash.accessory.client.AccessoryActionInput;
import de.lardtom.propwash.accessory.client.AccessoryClient;
import de.lardtom.propwash.accessory.client.AccessoryScreen;
import de.lardtom.propwash.accessory.client.AddonCamera;
import de.lardtom.propwash.accessory.client.AddonFlightClient;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.drone.ObstacleScan;
import de.lardtom.propwash.api.event.EntityHit;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentBlocks;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.WorkbenchLayout;
import de.lardtom.propwash.content.client.DroneWorkbenchScreen;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAccessories;
import de.lardtom.propwash.dev.example.client.ExampleAccessoriesClient;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.DroneServiceMenu;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.DroneServiceScreen;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AddonApiSelfTests {
    private static final String GROUP = "addon-api: ";
    private static final AtomicInteger PLACED = new AtomicInteger(-1);
    private static final AtomicReference<String> REPORT = new AtomicReference<>("");
    private static final AtomicReference<@Nullable BlockPos> BENCH = new AtomicReference<>();
    private static final AtomicReference<ItemStack> PREVIOUS_HEAD = new AtomicReference<>(ItemStack.EMPTY);
    private static final int SNOWBALLS = 8;
    private static final int WIND_CHARGES = 2;
    private static final double CINE_HEIGHT = 3.0;
    private static final String COUNT = "count";
    private static final String PUSHES = "pushes";
    private static final String ACTIONS = "actions";
    private static final String MIN_FORWARD = "minForward";
    private static final String START = "start";
    private static final String TRAVEL = "travel";
    private static final String LAST = "last";
    private static final String MAX_ROLL = "maxRoll";
    private static final String MAX_TILT = "maxTilt";
    private static final String FRAMES = "frames";
    private static final String HUD = "hud";
    private static final String SHOT = "addonShot";

    private AddonApiSelfTests() {
    }

    public static void register() {
        if (Screenshots.enabled()) {
            SelfTest.setupStep(GROUP + "Fenster für Bildschirmfotos", 100, ctx -> {
                StepResult result = Screenshots.resizeWindow(ctx);
                if (result.status() == StepResult.Status.PASS) {
                    Screenshots.hideMarker(true);
                    Minecraft.getInstance().getTutorial().setStep(TutorialSteps.NONE);
                }
                return result;
            });
        }
        SelfTest.clientStep(GROUP + "Version 1.6, Halterung vorne, Tasten und Beispiel-Zubehör", 20, ctx -> basicsStep());
        SelfTest.clientStep(GROUP + "Zubehör-Inventar füllen, am Item speichern und beim Client sehen", 300, AddonApiSelfTests::inventoryStep);
        SelfTest.clientStep(GROUP + "zwei Fenster und ein Schuss teilen sich das Zubehör-Inventar", 100, AddonApiSelfTests::sharedInventoryStep);
        SelfTest.clientStep(GROUP + "Wartung öffnet das Fenster „Zubehör“", 400, AddonApiSelfTests::menuStep);
        SelfTest.clientStep(GROUP + "Treffer unten mit Speer, vorne ohne, Schaden-Event", 200, AddonApiSelfTests::hitStep);
        SelfTest.clientStep(GROUP + "Drohnen-Abfragen, Hindernis-Scan und Vorhalt", 200, AddonApiSelfTests::queryStep);
        SelfTest.clientStep(GROUP + "Zubehör-Aktion 1 feuert am Server, der Rückstoß kommt im Flug an", 1500, AddonApiSelfTests::fireStep);
        SelfTest.clientStep(GROUP + "Kino-Flughilfe, Umkreisen auf Aktion 2, Gimbal und HUD im FPV", 3000, AddonApiSelfTests::cineStep);
        if (Screenshots.enabled()) {
            SelfTest.clientStep(GROUP + "Werkbank mit Zubehör vorne (Bildschirmfoto)", 600, AddonApiSelfTests::workbenchStep);
        }
        SelfTest.clientStep(GROUP + "Aufräumen", 400, AddonApiSelfTests::cleanupStep);
        if (Screenshots.enabled()) {
            SelfTest.setupStep(GROUP + "Fenster zurück", 5, ctx -> {
                Screenshots.hideMarker(false);
                return Screenshots.restoreWindow(ctx);
            });
        }
    }

    private static DroneBuild launcherBuild() {
        return DroneBuild.FREESTYLE.withAccessory(type(ExampleAccessories.LAUNCHER)).withAccessory(type(ExampleAccessories.SPEAR));
    }

    private static DroneBuild cineBuild() {
        return DroneBuild.FREESTYLE.withAccessory(type(ExampleAccessories.GIMBAL_CAM)).withAccessory(type(ExampleAccessories.LAUNCHER));
    }

    private static AccessoryType type(Identifier id) {
        return AccessoryType.byId(id).orElseThrow(() -> new IllegalStateException("Beispiel-Zubehör fehlt: " + id));
    }

    private static StepResult basicsStep() {
        List<String> errors = new ArrayList<>();
        if (!PropwashApi.isCompatible("1.6") || PropwashApi.isCompatible("1.7")) {
            errors.add("API-Version " + PropwashApi.API_VERSION);
        }
        Optional<AccessoryType> launcher = AccessoryType.byId(ExampleAccessories.LAUNCHER);
        Optional<AccessoryType> spear = AccessoryType.byId(ExampleAccessories.SPEAR);
        Optional<AccessoryType> gimbal = AccessoryType.byId(ExampleAccessories.GIMBAL_CAM);
        if (launcher.map(t -> t.mount != AccessoryMount.FRONT).orElse(true) || spear.map(t -> t.mount != AccessoryMount.BOTTOM).orElse(true)
                || gimbal.map(t -> t.mount != AccessoryMount.TOP).orElse(true)) {
            return StepResult.fail("Beispiel-Zubehör fehlt oder an falscher Halterung: " + launcher + " " + spear + " " + gimbal);
        }
        DroneBuild build = cineBuild().withAccessory(spear.get());
        if (!build.problems().isEmpty()) {
            errors.add("Freestyle mit Zubehör vorne nicht baubar: " + build.problems());
        }
        if (DroneBuild.WHOOP.frame().accessoryMounts.contains(AccessoryMount.FRONT)
                || !DroneBuild.FREESTYLE.frame().accessoryMounts.contains(AccessoryMount.FRONT)) {
            errors.add("Halterungen vorne falsch verteilt");
        }
        int front = BuildSlots.accessorySlot(AccessoryMount.FRONT);
        if (front != BuildSlots.COUNT - 1 || WorkbenchLayout.partX(front) != 30 || WorkbenchLayout.partY(front) != 64) {
            errors.add("Werkbank-Slot vorne " + front + " bei " + WorkbenchLayout.partX(front) + "," + WorkbenchLayout.partY(front));
        }
        Identifier sprite = Identifier.fromNamespaceAndPath("propwash", "textures/gui/sprites/gui/slot/accessory_front.png");
        if (Minecraft.getInstance().getResourceManager().getResource(sprite).isEmpty()) {
            errors.add("Slot-Symbol fehlt: " + sprite);
        }
        if (PropwashApi.accessories().behavior(ExampleAccessories.LAUNCHER).map(AccessoryBehavior::slots).orElse(0) != ExampleAccessories.LAUNCHER_SLOTS) {
            errors.add("Werfer ohne 4 Slots");
        }
        if (!PropwashApi.flight().assistIds().contains(ExampleAccessories.CINE)) {
            errors.add("Flughilfe nicht angemeldet: " + PropwashApi.flight().assistIds());
        }
        for (int i = 1; i <= 4; i++) {
            KeyMapping key = KeyMapping.get("key.propwash.accessory_action_" + i);
            if (key == null || key.isUnbound() || SwitchFunction.accessory(i).accessoryNumber() != i) {
                errors.add("Taste/Schalter Zubehör-Aktion " + i);
            }
        }
        for (String key : List.of("propwash.input.switch.accessory_1", "propwash.content.workbench.slot.accessory_front",
                "propwash.accessory.menu.title", "key.propwash.accessory_action_4", "part.propwash_example.pellet_launcher")) {
            if (!Language.getInstance().has(key)) {
                errors.add("Text fehlt: " + key);
            }
        }
        double plain = AirframeSpec.of(DroneBuild.FREESTYLE).massKg;
        double loaded = AirframeSpec.of(DroneBuild.FREESTYLE.withAccessory(launcher.get())).massKg;
        if (Math.abs(loaded - plain - 0.038) > 0.001) {
            errors.add(String.format(Locale.ROOT, "Werfer wiegt %.1f g statt 38 g", (loaded - plain) * 1000.0));
        }
        return errors.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", errors));
    }

    private static StepResult inventoryStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                ctx.state().put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = InteractSelfTests.player(server);
                    int[] arena = InteractSelfTests.arena(player);
                    QuadcopterEntity drone = place(player.level(), launcherBuild(), InteractSelfTests.start(arena));
                    AccessoryInventory inventory = PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT)
                            .orElseThrow(() -> new IllegalStateException("kein Inventar am Werfer"));
                    if (inventory.size() != ExampleAccessories.LAUNCHER_SLOTS) {
                        throw new IllegalStateException("Inventar mit " + inventory.size() + " Slots");
                    }
                    if (PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).isPresent()) {
                        throw new IllegalStateException("Speer hat ein Inventar");
                    }
                    inventory.set(0, new ItemStack(Items.SNOWBALL, SNOWBALLS));
                    inventory.set(1, new ItemStack(Items.WIND_CHARGE, WIND_CHARGES));
                    ItemStack item = drone.toStack();
                    List<ItemStack> stored = PropwashApi.accessories().contents(item, AccessorySlot.FRONT);
                    if (stored.size() < 2 || stored.get(0).getCount() != SNOWBALLS || !stored.get(1).is(Items.WIND_CHARGE)) {
                        throw new IllegalStateException("Item trägt den Inhalt nicht: " + stored);
                    }
                    QuadcopterEntity copy = DroneModule.DRONE.get().create(player.level(), EntitySpawnReason.COMMAND);
                    if (copy == null) {
                        throw new IllegalStateException("Kopie nicht erzeugt");
                    }
                    copy.loadFromStack(item);
                    if (!copy.accessoryContents().equals(drone.accessoryContents())) {
                        throw new IllegalStateException("loadFromStack verliert den Inhalt");
                    }
                    copy.discard();
                    DynamicOps<Tag> ops = player.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
                    Tag tag = AccessoryContents.CODEC.encodeStart(ops, drone.accessoryContents()).getOrThrow();
                    AccessoryContents back = AccessoryContents.CODEC.parse(ops, tag).getOrThrow();
                    if (!back.equals(drone.accessoryContents())) {
                        throw new IllegalStateException("Speichern/Laden verliert den Inhalt: " + back);
                    }
                    double grams = AccessoryPayload.payloadGrams(drone);
                    if (!(grams > 0.0)) {
                        throw new IllegalStateException("Beladung ohne Gewicht");
                    }
                    REPORT.set(String.format(Locale.ROOT, "Beladung %.0f g (8 Schneebälle, 2 Windkugeln)", grams));
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = InteractSelfTests.await(ctx, "Inventar");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (mc.level == null || !(mc.level.getEntity(PLACED.get()) instanceof QuadcopterEntity drone)) {
                    return InteractSelfTests.since(ctx) > 100 ? StepResult.fail("Drohne beim Client fehlt") : StepResult.pending();
                }
                List<ItemStack> contents = PropwashApi.accessories().contents(drone, AccessorySlot.FRONT);
                if (contents.isEmpty() || contents.get(0).getCount() != SNOWBALLS) {
                    return InteractSelfTests.since(ctx) > 100 ? StepResult.fail("Inhalt kommt nicht beim Client an: " + contents) : StepResult.pending();
                }
                double unladen = AirframeSpec.of(drone.build()).massKg;
                double laden = AirframeSpec.of(AccessoryPayload.flightBuild(drone)).massKg;
                if (!(laden > unladen + 0.01)) {
                    return StepResult.fail(String.format(Locale.ROOT, "Flugmodell ohne Beladung: %.3f kg gegen %.3f kg", laden, unladen));
                }
                ctx.log(REPORT.get() + String.format(Locale.ROOT, ", Flugmodell %.0f g statt %.0f g", laden * 1000.0, unladen * 1000.0));
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase");
            }
        }
    }

    private static StepResult sharedInventoryStep(SelfTest.StepContext ctx) {
        if (InteractSelfTests.phase(ctx) == 0) {
            ctx.state().put(InteractSelfTests.FUTURE, ctx.onServer(server -> REPORT.set(sharedInventory(server))));
            InteractSelfTests.next(ctx);
            return StepResult.pending();
        }
        StepResult server = InteractSelfTests.await(ctx, "Geteiltes Inventar");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        ctx.log(REPORT.get());
        return StepResult.pass();
    }

    private static String sharedInventory(MinecraftServer server) {
        ServerPlayer player = InteractSelfTests.player(server);
        QuadcopterEntity drone = placed(server);
        AccessoryContents saved = drone.accessoryContents();
        List<ItemStack> pockets = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            pockets.add(player.getInventory().getItem(i).copy());
        }
        try {
            AccessoryInventory inventory = PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT)
                    .orElseThrow(() -> new IllegalStateException("kein Inventar am Werfer"));
            for (int i = 0; i < inventory.size(); i++) {
                inventory.set(i, ItemStack.EMPTY);
            }
            inventory.set(0, new ItemStack(Items.SNOWBALL, 16));
            player.getInventory().clearContent();
            AccessoryMenu first = new AccessoryMenu(91, player.getInventory(), drone);
            AccessoryMenu second = new AccessoryMenu(92, player.getInventory(), drone);
            first.getSlot(0).getItem();
            second.getSlot(0).getItem();
            first.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
            second.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
            int got = player.getInventory().countItem(Items.SNOWBALL);
            if (got != 16 || !inventory.get(0).isEmpty()) {
                throw new IllegalStateException("zwei Fenster im selben Tick: Spieler hat " + got + " Schneebälle aus 16, Drohne noch "
                        + inventory.get(0).getCount());
            }
            player.getInventory().clearContent();
            inventory.set(0, new ItemStack(Items.SNOWBALL, 16));
            inventory.set(1, new ItemStack(Items.WIND_CHARGE, 2));
            first.getSlot(1).getItem();
            ItemStack shot = inventory.take(0, 1);
            if (shot.getCount() != 1) {
                throw new IllegalStateException("Schuss nahm " + shot);
            }
            first.clicked(1, 0, ContainerInput.QUICK_MOVE, player);
            if (inventory.get(0).getCount() != 15 || !inventory.get(1).isEmpty() || player.getInventory().countItem(Items.WIND_CHARGE) != 2) {
                throw new IllegalStateException("Schuss zwischen zwei Klicks: Platz 0 hat " + inventory.get(0).getCount() + " statt 15, Platz 1 "
                        + inventory.get(1) + ", Spieler " + player.getInventory().countItem(Items.WIND_CHARGE) + " Windkugeln");
            }
            if (AccessoryMenu.storable(new ItemStack(Items.SHULKER_BOX)) || AccessoryMenu.storable(drone.toStack())
                    || !AccessoryMenu.storable(new ItemStack(Items.SNOWBALL))) {
                throw new IllegalStateException("Fenster nimmt Shulkerkiste oder beladene Drohne an oder lehnt Schneebälle ab");
            }
            first.removed(player);
            second.removed(player);
            return "16 Schneebälle über zwei Fenster genau einmal entnommen, Schuss zwischen zwei Klicks bleibt verbraucht (15), "
                    + "Shulkerkiste und beladene Drohne abgelehnt";
        } finally {
            drone.setAccessoryContents(saved);
            for (int i = 0; i < pockets.size(); i++) {
                player.getInventory().setItem(i, pockets.get(i));
            }
        }
    }

    private static StepResult menuStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = InteractSelfTests.player(server);
                    QuadcopterEntity drone = placed(server);
                    Services.PLATFORM.openMenu(player, new SimpleMenuProvider((id, inventory, p) -> new DroneServiceMenu(id, inventory, drone),
                            Component.translatable("container.propwash.drone_service")), ByteBufCodecs.VAR_INT, drone.getId());
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = InteractSelfTests.await(ctx, "Wartung");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!(mc.gui.screen() instanceof DroneServiceScreen screen)) {
                    return InteractSelfTests.since(ctx) > 100 ? StepResult.fail("Wartung nicht offen: " + mc.gui.screen()) : StepResult.pending();
                }
                Component label = Component.translatable("propwash.accessory.service.button");
                boolean found = screen.children().stream().anyMatch(w -> w instanceof AbstractWidget widget && widget.getMessage().equals(label));
                if (!found) {
                    return InteractSelfTests.since(ctx) > 40 ? StepResult.fail("Knopf „Zubehör“ fehlt in der Wartung") : StepResult.pending();
                }
                if (Screenshots.enabled() && !state.containsKey(SHOT)) {
                    if (InteractSelfTests.since(ctx) < 20) {
                        Screenshots.parkMouse(mc);
                        Screenshots.clearOverlays(mc);
                        return StepResult.pending();
                    }
                    state.put(SHOT, capture("addon_api_service_button"));
                    return StepResult.pending();
                }
                if (state.containsKey(SHOT) && !((CompletableFuture<?>) state.get(SHOT)).isDone()) {
                    return StepResult.pending();
                }
                state.remove(SHOT);
                if (mc.gameMode != null) {
                    mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, DroneServiceMenu.ACCESSORY_BUTTON);
                }
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (!(mc.gui.screen() instanceof AccessoryScreen screen)) {
                    return InteractSelfTests.since(ctx) > 100 ? StepResult.fail("Fenster „Zubehör“ öffnet nicht: " + mc.gui.screen()) : StepResult.pending();
                }
                AccessoryMenu menu = screen.getMenu();
                if (!menu.rows().equals(List.of(AccessoryMount.FRONT)) || menu.size(AccessoryMount.FRONT) != ExampleAccessories.LAUNCHER_SLOTS) {
                    return StepResult.fail("Zeilen " + menu.rows());
                }
                ItemStack first = menu.getSlot(0).getItem();
                if (!first.is(Items.SNOWBALL) || first.getCount() != SNOWBALLS) {
                    return InteractSelfTests.since(ctx) > 60 ? StepResult.fail("Slot 0 zeigt " + first) : StepResult.pending();
                }
                if (menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)) || !menu.getSlot(2).mayPlace(new ItemStack(Items.ARROW))) {
                    return StepResult.fail("Slot-Filter des Werfers greift nicht");
                }
                if (Screenshots.enabled() && !state.containsKey(SHOT)) {
                    if (InteractSelfTests.since(ctx) < 20) {
                        Screenshots.parkMouse(mc);
                        Screenshots.clearOverlays(mc);
                        return StepResult.pending();
                    }
                    state.put(SHOT, capture("addon_api_accessory_menu"));
                    return StepResult.pending();
                }
                if (state.containsKey(SHOT) && !((CompletableFuture<?>) state.get(SHOT)).isDone()) {
                    return StepResult.pending();
                }
                if (mc.player != null) {
                    mc.player.closeContainer();
                }
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase");
            }
        }
    }

    private static StepResult hitStep(SelfTest.StepContext ctx) {
        if (InteractSelfTests.phase(ctx) == 0) {
            ctx.state().put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
                ServerPlayer player = InteractSelfTests.player(server);
                QuadcopterEntity drone = placed(server);
                ServerLevel level = player.level();
                drone.setBuild(launcherBuild());
                Vector3f forward = drone.orientation().transform(new Vector3f(0.0F, 0.0F, -1.0F));
                Zombie below = zombie(level, drone.position().add(0.0, -2.2, 0.0));
                Zombie ahead = zombie(level, drone.position().add(forward.x * 0.7, -0.8, forward.z * 0.7));
                try {
                    int spears = ExampleAccessories.SPEAR_HITS.get();
                    EntityHit under = AddonEvents.entityHit(drone, player, below, new Vec3(0.0, -6.0, 0.0), 6.0, new Vec3(0.0, -1.0, 0.0), 2.0F, 0.3F);
                    if (!under.slot().equals(Optional.of(AccessorySlot.BOTTOM)) || !under.accessory().equals(Optional.of(ExampleAccessories.SPEAR))) {
                        throw new IllegalStateException("Treffer von unten an " + under.slot() + " / " + under.accessory() + " (Rahmenpunkt " + under.framePoint() + ")");
                    }
                    if (ExampleAccessories.SPEAR_HITS.get() != spears + 1 || Math.abs(under.damage() - (2.0F + 0.6F * 6.0F)) > 1.0E-3) {
                        throw new IllegalStateException("Speer-Schaden " + under.damage());
                    }
                    EntityHit front = AddonEvents.entityHit(drone, player, ahead, new Vec3(forward.x * 5.0, 0.0, forward.z * 5.0), 5.0,
                            new Vec3(forward.x, 0.0, forward.z), 1.5F, 0.3F);
                    if (!front.slot().equals(Optional.of(AccessorySlot.FRONT)) || front.damage() != 1.5F) {
                        throw new IllegalStateException("Treffer vorne an " + front.slot() + " mit " + front.damage() + " (Rahmenpunkt " + front.framePoint() + ")");
                    }
                    int events = ExampleAccessories.DAMAGE_EVENTS.get();
                    if (drone.hurtServer(level, level.damageSources().cactus(), 2.0F) || ExampleAccessories.LAST_DAMAGE.get() != -1.0F) {
                        throw new IllegalStateException("Kaktus-Schaden nicht abgebrochen");
                    }
                    drone.setBuild(cineBuild());
                    drone.hurtServer(level, level.damageSources().generic(), 2.0F);
                    if (ExampleAccessories.DAMAGE_EVENTS.get() != events + 2 || ExampleAccessories.LAST_DAMAGE.get() != 1.0F || drone.isRemoved()) {
                        throw new IllegalStateException("Schaden-Event: " + ExampleAccessories.LAST_DAMAGE.get() + ", " + (ExampleAccessories.DAMAGE_EVENTS.get() - events));
                    }
                    REPORT.set(String.format(Locale.ROOT, "unten %s %.1f Schaden, vorne %s %.1f", under.slot().orElseThrow().key(), under.damage(),
                            front.slot().orElseThrow().key(), front.damage()));
                } finally {
                    drone.setBuild(launcherBuild());
                    below.discard();
                    ahead.discard();
                }
            }));
            InteractSelfTests.next(ctx);
            return StepResult.pending();
        }
        StepResult server = InteractSelfTests.await(ctx, "Treffer");
        if (server.status() == StepResult.Status.PASS) {
            ctx.log(REPORT.get());
        }
        return server;
    }

    private static StepResult queryStep(SelfTest.StepContext ctx) {
        if (InteractSelfTests.phase(ctx) == 0) {
            ctx.state().put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
                ServerPlayer player = InteractSelfTests.player(server);
                QuadcopterEntity drone = placed(server);
                ServerLevel level = player.level();
                List<DroneView> near = PropwashApi.drones().dronesNear(level, drone.position(), 4.0, false);
                if (near.isEmpty() || near.getFirst().entity() != drone || near.getFirst().flying()) {
                    throw new IllegalStateException("dronesNear: " + near);
                }
                if (!PropwashApi.drones().dronesNear(level, drone.position(), 4.0, true).isEmpty()) {
                    throw new IllegalStateException("abgestellte Drohne gilt als fliegend");
                }
                DroneView view = near.getFirst();
                if (view.massKg() < 0.5 || !view.accessory(AccessorySlot.FRONT).equals(Optional.of(ExampleAccessories.LAUNCHER))) {
                    throw new IllegalStateException("Ansicht " + view);
                }
                Vector3f f = drone.orientation().transform(new Vector3f(0.0F, 0.0F, -1.0F));
                Vec3 center = drone.position();
                List<BlockPos> wall = new ArrayList<>();
                for (int d = -1; d <= 1; d++) {
                    for (int h = -1; h <= 2; h++) {
                        BlockPos pos = BlockPos.containing(center.x + f.x * 5.0 - f.z * d, center.y + h, center.z + f.z * 5.0 + f.x * d);
                        wall.add(pos);
                        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                    }
                }
                try {
                    ObstacleScan scan = PropwashApi.drones().scan(drone, 12.0);
                    double ahead = scan.distance(SensorDirection.FORWARD);
                    if (ahead < 3.8 || ahead > 5.6 || scan.distance(SensorDirection.UP) < 11.9 || scan.distance(SensorDirection.DOWN) > 1.0) {
                        throw new IllegalStateException("Scan " + scan);
                    }
                    if (PropwashApi.drones().aim(player.getEyePosition(), drone, 1.5, 0.03).isEmpty()) {
                        throw new IllegalStateException("kein Vorhalt");
                    }
                    REPORT.set(String.format(Locale.ROOT, "Hindernis vorne %.2f m, unten %.2f m, oben frei", ahead, scan.distance(SensorDirection.DOWN)));
                } finally {
                    for (BlockPos pos : wall) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }));
            InteractSelfTests.next(ctx);
            return StepResult.pending();
        }
        StepResult server = InteractSelfTests.await(ctx, "Abfragen");
        if (server.status() == StepResult.Status.PASS) {
            ctx.log(REPORT.get());
        }
        return server;
    }

    private static StepResult fireStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
                    discardPlaced(server);
                    ServerPlayer player = InteractSelfTests.player(server);
                    int[] arena = InteractSelfTests.arena(player);
                    InteractSelfTests.fly(player, launcherBuild(), InteractSelfTests.start(arena));
                    QuadcopterEntity drone = InteractSelfTests.serverDrone(server);
                    AccessoryInventory inventory = PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT).orElseThrow();
                    inventory.set(0, new ItemStack(Items.SNOWBALL, SNOWBALLS));
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                return InteractSelfTests.started(ctx, "Werfer", null);
            }
            case 2, 3 -> {
                return InteractSelfTests.climb(ctx, "Werfer");
            }
            case 4 -> {
                InteractSelfTests.hover(session);
                if (!InteractSelfTests.settled(session, ctx)) {
                    return InteractSelfTests.since(ctx) > 300 ? StepResult.fail("Werfer: schwebt nicht ruhig") : StepResult.pending();
                }
                QuadcopterEntity drone = session.drone();
                if (drone == null || PropwashApi.accessories().contents(drone, AccessorySlot.FRONT).isEmpty()) {
                    return InteractSelfTests.since(ctx) > 400 ? StepResult.fail("Munition beim Client fehlt") : StepResult.pending();
                }
                state.put(COUNT, ExampleAccessories.FIRED.get());
                state.put(PUSHES, AccessoryClient.pushesApplied());
                state.put(ACTIONS, ExampleAccessories.ACTIONS.get());
                state.put(MIN_FORWARD, 0.0);
                AccessoryActionInput.INSTANCE.setTestBits(1);
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                InteractSelfTests.hover(session);
                DroneSim sim = session.sim();
                QuadcopterEntity drone = session.drone();
                if (sim == null || drone == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                Vector3d v = new Vector3d();
                sim.getVelocity(v);
                Vector3f f = new Quaternionf(drone.orientation()).transform(new Vector3f(0.0F, 0.0F, -1.0F));
                double forward = v.x * f.x + v.z * f.z;
                state.put(MIN_FORWARD, Math.min((double) state.get(MIN_FORWARD), forward));
                boolean fired = ExampleAccessories.FIRED.get() > (int) state.get(COUNT);
                boolean pushed = AccessoryClient.pushesApplied() > (int) state.get(PUSHES);
                if (!fired || !pushed || InteractSelfTests.since(ctx) < 12) {
                    return InteractSelfTests.since(ctx) > 120 ? StepResult.fail("Schuss " + fired + ", Rückstoß beim Client " + pushed) : StepResult.pending();
                }
                AccessoryActionInput.INSTANCE.setTestBits(0);
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 6 -> {
                InteractSelfTests.hover(session);
                QuadcopterEntity drone = session.drone();
                if (drone == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                List<ItemStack> contents = PropwashApi.accessories().contents(drone, AccessorySlot.FRONT);
                int left = contents.isEmpty() ? 0 : contents.get(0).getCount();
                boolean released = ExampleAccessories.ACTIONS.get() >= (int) state.get(ACTIONS) + 2;
                if (left != SNOWBALLS - 1 || !released) {
                    return InteractSelfTests.since(ctx) > 100 ? StepResult.fail("Rest " + left + " Schneebälle, Loslassen gemeldet " + released) : StepResult.pending();
                }
                double minForward = (double) state.get(MIN_FORWARD);
                if (minForward > -0.3) {
                    return StepResult.fail(String.format(Locale.ROOT, "Rückstoß zu schwach: %.2f m/s nach hinten", -minForward));
                }
                state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity serverDrone = InteractSelfTests.serverDrone(server);
                    List<Snowball> balls = serverDrone.level().getEntitiesOfClass(Snowball.class, serverDrone.getBoundingBox().inflate(40.0));
                    REPORT.set(String.format(Locale.ROOT, "%s abgefeuert, %d Schneebälle in der Luft, Rückstoß %.2f m/s, %d übrig",
                            ExampleAccessories.LAST_SHOT.get(), balls.size(), -minForward, left));
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 7 -> {
                InteractSelfTests.hover(session);
                StepResult server = InteractSelfTests.await(ctx, "Schuss");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                ctx.log(REPORT.get());
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            default -> {
                AccessoryActionInput.INSTANCE.setTestBits(null);
                return InteractSelfTests.stop(ctx, server -> {
                });
            }
        }
    }

    private static StepResult cineStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        int[] arena = InteractSelfTests.ARENA.get();
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = InteractSelfTests.player(server);
                    int[] spot = InteractSelfTests.arena(player);
                    flyFpv(player, cineBuild(), InteractSelfTests.start(spot));
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = InteractSelfTests.await(ctx, "FPV-Start");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                DroneSim sim = session.sim();
                if (!session.isFpv() || sim == null || session.drone() == null || session.drone().getId() != InteractSelfTests.DRONE_ID.get()) {
                    return InteractSelfTests.since(ctx) > 200 ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                if (!sim.telemetry().posHoldReady) {
                    return InteractSelfTests.since(ctx) > 600 ? StepResult.fail("kein GPS-Fix: " + sim.telemetry().gpsSatellites + " Satelliten") : StepResult.pending();
                }
                if (AddonFlightClient.INSTANCE.assist() == null) {
                    return StepResult.fail("Kino-Flughilfe nicht angehängt");
                }
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                DroneSim sim = session.sim();
                if (sim == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), InteractSelfTests.since(ctx) >= 10, StabMode.ANGLE);
                if (!sim.telemetry().armed) {
                    return InteractSelfTests.since(ctx) > 100 ? StepResult.fail("nicht scharf: " + sim.telemetry().armBlockers) : StepResult.pending();
                }
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                QuadcopterEntity drone = session.drone();
                DroneSim sim = session.sim();
                if (drone == null || sim == null || arena == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.85F), true, StabMode.ANGLE);
                if (drone.getY() - arena[1] < CINE_HEIGHT) {
                    return InteractSelfTests.since(ctx) > 400 ? StepResult.fail("steigt nicht (Kino: " + sim.telemetry().assistLabel + ")") : StepResult.pending();
                }
                state.put(START, drone.position());
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                QuadcopterEntity drone = session.drone();
                DroneSim sim = session.sim();
                if (drone == null || sim == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.5F), true, StabMode.ANGLE);
                if (InteractSelfTests.since(ctx) < 80) {
                    state.put(START, drone.position());
                    return StepResult.pending();
                }
                Telemetry t = sim.telemetry();
                if (!"CINE".equals(t.assistLabel) || !"CINE".equals(OsdHud.sampler().data().assistLabel)) {
                    return StepResult.fail("Modus " + t.assistLabel + " / OSD " + OsdHud.sampler().data().assistLabel);
                }
                Vec3 start = (Vec3) state.get(START);
                double drift = Math.hypot(drone.getX() - start.x, drone.getZ() - start.z);
                if (InteractSelfTests.since(ctx) < 140) {
                    return drift > 2.5 ? StepResult.fail(String.format(Locale.ROOT, "hält die Position nicht: %.2f m", drift)) : StepResult.pending();
                }
                if (Math.abs(drone.getY() - start.y) > 1.5) {
                    return StepResult.fail(String.format(Locale.ROOT, "hält die Höhe nicht: %.2f m", drone.getY() - start.y));
                }
                ctx.log(String.format(Locale.ROOT, "Kino hält: Drift %.2f m, Höhe %+.2f m", drift, drone.getY() - start.y));
                state.put(MAX_ROLL, 0.0);
                state.put(MAX_TILT, 0.0);
                state.put(FRAMES, AddonCamera.appliedFrames());
                state.put(HUD, ExampleAccessoriesClient.HUD_FRAMES.get());
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                QuadcopterEntity drone = session.drone();
                DroneSim sim = session.sim();
                if (drone == null || sim == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                session.setTestInput(new StickPositions(0.8F, 0.0F, 0.0F, 0.5F), true, StabMode.ANGLE);
                double roll = Math.abs(sim.telemetry().rollDeg);
                Vector3f left = new Vector3f(-1.0F, 0.0F, 0.0F);
                mc.gameRenderer.mainCamera().rotation().transform(left);
                state.put(MAX_ROLL, Math.max((double) state.get(MAX_ROLL), roll));
                if (roll > 4.0) {
                    state.put(MAX_TILT, Math.max((double) state.get(MAX_TILT), Math.abs(left.y)));
                }
                if (Screenshots.enabled() && InteractSelfTests.since(ctx) == 60) {
                    state.put(SHOT, capture("addon_api_cine_hud"));
                }
                if (InteractSelfTests.since(ctx) < 100 || state.containsKey(SHOT) && !((CompletableFuture<?>) state.get(SHOT)).isDone()) {
                    return StepResult.pending();
                }
                double maxRoll = (double) state.get(MAX_ROLL);
                double maxTilt = (double) state.get(MAX_TILT);
                int frames = AddonCamera.appliedFrames() - (int) state.get(FRAMES);
                int hud = ExampleAccessoriesClient.HUD_FRAMES.get() - (int) state.get(HUD);
                if (maxRoll < 4.0) {
                    return StepResult.fail(String.format(Locale.ROOT, "Drohne legt sich nicht in die Kurve: %.1f°", maxRoll));
                }
                if (maxTilt > 0.05 || frames < 20) {
                    return StepResult.fail(String.format(Locale.ROOT, "Gimbal: Horizont um %.3f geneigt, %d Bilder", maxTilt, frames));
                }
                if (hud < 20) {
                    return StepResult.fail("HUD-Element zeichnet nicht: " + hud);
                }
                ctx.log(String.format(Locale.ROOT, "Rahmen bis %.1f° gerollt, Kamera-Horizont %.3f, %d Gimbal-Bilder, %d HUD-Bilder", maxRoll, maxTilt,
                        frames, hud));
                state.remove(SHOT);
                AccessoryActionInput.INSTANCE.setTestBits(0b10);
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 6 -> {
                QuadcopterEntity drone = session.drone();
                DroneSim sim = session.sim();
                if (drone == null || sim == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.5F), true, StabMode.ANGLE);
                if (InteractSelfTests.since(ctx) >= 6) {
                    AccessoryActionInput.INSTANCE.setTestBits(0);
                }
                if (!"ORBIT".equals(sim.telemetry().assistLabel)) {
                    return InteractSelfTests.since(ctx) > 60 ? StepResult.fail("Aktion 2 startet das Umkreisen nicht: " + sim.telemetry().assistLabel)
                            : StepResult.pending();
                }
                state.put(START, drone.position());
                state.put(LAST, drone.position());
                state.put(TRAVEL, 0.0);
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 7 -> {
                QuadcopterEntity drone = session.drone();
                if (drone == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.5F), true, StabMode.ANGLE);
                AccessoryActionInput.INSTANCE.setTestBits(0);
                Vec3 last = (Vec3) state.get(LAST);
                state.put(TRAVEL, (double) state.get(TRAVEL) + Math.hypot(drone.getX() - last.x, drone.getZ() - last.z));
                state.put(LAST, drone.position());
                if (InteractSelfTests.since(ctx) < 200) {
                    return StepResult.pending();
                }
                Vec3 start = (Vec3) state.get(START);
                double travel = (double) state.get(TRAVEL);
                if (travel < 4.0 || Math.abs(drone.getY() - start.y) > 2.0) {
                    return StepResult.fail(String.format(Locale.ROOT, "Umkreisen: %.1f m Weg, Höhe %+.2f m", travel, drone.getY() - start.y));
                }
                ctx.log(String.format(Locale.ROOT, "Umkreisen: %.1f m in 10 s, Höhe %+.2f m", travel, drone.getY() - start.y));
                AccessoryActionInput.INSTANCE.setTestBits(0b10);
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 8 -> {
                DroneSim sim = session.sim();
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.5F), true, StabMode.ANGLE);
                if (InteractSelfTests.since(ctx) >= 6) {
                    AccessoryActionInput.INSTANCE.setTestBits(0);
                }
                if (sim == null || !"CINE".equals(sim.telemetry().assistLabel) && !"STOP".equals(sim.telemetry().assistLabel)) {
                    return InteractSelfTests.since(ctx) > 60 ? StepResult.fail("Aktion 2 beendet das Umkreisen nicht: " + (sim == null ? "-"
                            : sim.telemetry().assistLabel) + ", Umschaltungen " + ExampleAccessories.ORBIT_TOGGLES.get() + ", Aktionen "
                            + AccessoryActionInput.INSTANCE.bits()) : StepResult.pending();
                }
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            default -> {
                AccessoryActionInput.INSTANCE.setTestBits(null);
                return InteractSelfTests.stop(ctx, server -> InteractSelfTests.player(server).setItemSlot(EquipmentSlot.HEAD, PREVIOUS_HEAD.get()));
            }
        }
    }

    private static StepResult workbenchStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = InteractSelfTests.player(server);
                    ServerLevel level = player.level();
                    BlockPos pos = player.blockPosition().offset(2, 0, 0);
                    BlockState bench = ContentBlocks.DRONE_WORKBENCH.block().get().defaultBlockState();
                    level.setBlockAndUpdate(pos, bench);
                    BENCH.set(pos);
                    if (player.openMenu(bench.getMenuProvider(level, pos)).isEmpty() || !(player.containerMenu instanceof DroneWorkbenchMenu menu)) {
                        throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
                    }
                    DroneBuild build = cineBuild().withAccessory(type(ExampleAccessories.SPEAR));
                    put(menu, player, BuildSlots.FRAME, ContentItems.stack(build.frame()));
                    put(menu, player, BuildSlots.STACK, ContentItems.stack(build.stack()));
                    put(menu, player, BuildSlots.VIDEO, ContentItems.stack(build.video()));
                    put(menu, player, BuildSlots.BATTERY, Batteries.stackOf(build.battery(), 0.9F));
                    for (int i = 0; i < DroneBuild.MOTOR_COUNT; i++) {
                        put(menu, player, BuildSlots.MOTOR + i, ContentItems.stack(build.motor()));
                        put(menu, player, BuildSlots.PROP + i, ContentItems.stack(build.prop()));
                    }
                    for (AccessoryMount mount : AccessoryMount.values()) {
                        build.accessories().at(mount).ifPresent(type -> put(menu, player, BuildSlots.accessorySlot(mount), ContentItems.stack(type)));
                    }
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = InteractSelfTests.await(ctx, "Werkbank");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen) || screen.getMenu().resultPreview().isEmpty()) {
                    return InteractSelfTests.since(ctx) > 200 ? StepResult.fail("Werkbank zeigt kein Ergebnis") : StepResult.pending();
                }
                if (InteractSelfTests.since(ctx) < 40) {
                    Screenshots.parkMouse(mc);
                    return StepResult.pending();
                }
                if (!state.containsKey(SHOT)) {
                    state.put(SHOT, capture("addon_api_workbench_front"));
                    return StepResult.pending();
                }
                if (!((CompletableFuture<?>) state.get(SHOT)).isDone()) {
                    return StepResult.pending();
                }
                if (mc.player != null) {
                    mc.player.closeContainer();
                }
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase");
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        AccessoryActionInput.INSTANCE.setTestBits(null);
        if (InteractSelfTests.phase(ctx) == 0) {
            InteractSelfTests.next(ctx);
        }
        return InteractSelfTests.stop(ctx, server -> {
            discardPlaced(server);
            BlockPos bench = BENCH.getAndSet(null);
            if (bench != null) {
                InteractSelfTests.player(server).level().setBlockAndUpdate(bench, Blocks.AIR.defaultBlockState());
            }
        });
    }

    private static void put(DroneWorkbenchMenu menu, ServerPlayer player, int slot, ItemStack stack) {
        menu.setCarried(stack);
        menu.clicked(slot, 0, ContainerInput.PICKUP, player);
        if (!menu.getCarried().isEmpty()) {
            throw new IllegalStateException("Slot " + slot + " nahm " + stack + " nicht an");
        }
    }

    private static Zombie zombie(ServerLevel level, Vec3 pos) {
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (zombie == null) {
            throw new IllegalStateException("Zombie nicht erzeugt");
        }
        zombie.setNoAi(true);
        zombie.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static QuadcopterEntity place(ServerLevel level, DroneBuild build, Vec3 pos) {
        discardPlaced(level.getServer());
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        drone.snapTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, InteractSelfTests.upright());
        PLACED.set(drone.getId());
        return drone;
    }

    private static QuadcopterEntity placed(MinecraftServer server) {
        if (!(InteractSelfTests.player(server).level().getEntity(PLACED.get()) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("abgestellte Test-Drohne fehlt");
        }
        return drone;
    }

    private static void discardPlaced(MinecraftServer server) {
        int id = PLACED.getAndSet(-1);
        if (id >= 0 && InteractSelfTests.player(server).level().getEntity(id) instanceof QuadcopterEntity drone) {
            drone.discard();
        }
    }

    private static void flyFpv(ServerPlayer player, DroneBuild build, Vec3 pos) {
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        drone.snapTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, InteractSelfTests.upright());
        InteractSelfTests.DRONE_ID.set(drone.getId());
        player.setGameMode(GameType.CREATIVE);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElseThrow();
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        PREVIOUS_HEAD.set(player.getItemBySlot(EquipmentSlot.HEAD).copy());
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static CompletableFuture<String> capture(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshots.clearOverlays(mc);
        Screenshots.parkMouse(mc);
        return Screenshots.capture(name);
    }
}
