package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.ImportTarget;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PaintSwatch;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.BatteryChargerBlockEntity;
import de.lardtom.propwash.content.BatteryChargerMenu;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.DroneDataReader;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.paint.PaintC2S;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.drone.DroneInteraction;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.interact.DroneCargo;
import de.lardtom.propwash.interact.DroneStrikes;
import de.lardtom.propwash.interact.net.DroneStrikeC2S;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.stats.PilotStats;
import de.lardtom.propwash.stats.PropwashStats;
import de.lardtom.propwash.stats.advancement.PropwashTriggers;
import de.lardtom.propwash.stats.net.CrashC2S;
import de.lardtom.propwash.video.net.SpectateC2S;
import io.netty.buffer.Unpooled;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FpvAdvancementCases {
    public static final List<String> NAMES = List.of("root", "drone_built", "takeoff", "fpv_flight", "battery_swapped", "battery_charged",
            "distance_1km", "distance_10km", "distance_100km", "build_limit", "dive", "veteran", "first_lap", "personal_best",
            "ghost_beaten", "own_course", "first_trick", "power_loop", "combo", "school_chapter", "school_perfect", "cargo", "painted",
            "sharecode", "spectator", "enderman", "ender_dragon", "wither");
    public static final List<String> FLOWN = List.of("root", "takeoff", "fpv_flight");
    public static final List<String> CLIENT = List.of("first_trick", "power_loop", "combo", "school_chapter", "school_perfect", "ghost_beaten");
    public static final List<String> SERVER = NAMES.stream().filter(name -> !CLIENT.contains(name)).toList();
    public static final Identifier ROOT = id("root");
    public static final String SPECTATED_PILOT = "PwFpvPilot";
    public static final int DISTANCE_CM = 10_000_000;
    public static final int CRASHES_BEFORE = 99;
    public static final double BUILD_LIMIT_Y = 321.0;
    public static final double DIVE_BLOCKS = 101.0;
    public static final double CARGO_BLOCKS = 12.0;
    public static final long SEEDED_LAP_NANOS = 9_000_000_000L;
    public static final long FLOWN_LAP_NANOS = 8_000_000_000L;
    public static final String WORKBENCH_NAME = "Werkbank Fortschritt";
    public static final String WORKBENCH_TUNE_KEY = "rates.roll.rcRate";
    private static final DronePaint WORKBENCH_PAINT = DronePaint.DEFAULT.with(PaintSlot.FRAME, PaintSwatch.LIME.rgb());
    private static final TuneSpec WORKBENCH_TUNE = new TuneSpec(Map.of(WORKBENCH_TUNE_KEY, 1.25F));
    private static final List<Assembled> ASSEMBLED = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean LISTENING = new AtomicBoolean();

    private FpvAdvancementCases() {
    }

    private record Assembled(ServerPlayer player, ItemStack drone, DroneData data) {
    }

    public static Identifier id(String name) {
        return Constants.id("fpv/" + name);
    }

    public static int loaded(MinecraftServer server) {
        int count = 0;
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            if (holder.id().getNamespace().equals(Constants.MOD_ID) && holder.id().getPath().startsWith("fpv/")) {
                count++;
            }
        }
        return count;
    }

    public static List<String> loadProblems(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        for (var holder : PropwashTriggers.all()) {
            if (!holder.isBound() || BuiltInRegistries.TRIGGER_TYPES.getValue(holder.id()) != holder.get()) {
                problems.add("Auslöser nicht angemeldet: " + holder.id());
            }
        }
        for (String name : NAMES) {
            if (server.getAdvancements().get(id(name)) == null) {
                problems.add("nicht geladen: " + name);
            }
        }
        int count = loaded(server);
        if (count != NAMES.size()) {
            problems.add(count + " statt " + NAMES.size() + " Fortschritte unter propwash:fpv/");
        }
        AdvancementHolder root = server.getAdvancements().get(ROOT);
        Optional<DisplayInfo> display = root == null ? Optional.empty() : root.value().display();
        if (display.isEmpty() || display.get().background().isEmpty()
                || !display.get().background().get().id().equals(Constants.id("gui/advancements/backgrounds/propwash_fpv"))) {
            problems.add("Wurzel ohne Hintergrund propwash_fpv");
        }
        return problems;
    }

    public static List<String> cases(MinecraftServer server, ServerPlayer player) {
        Cases run = new Cases(server, player);
        run.all();
        List<String> missing = missing(server, player, SERVER);
        if (!missing.isEmpty()) {
            run.problems.add("nicht erreicht: " + missing);
        }
        if (!run.problems.isEmpty()) {
            return run.problems;
        }
        run.notes.addFirst("OK " + run.paths + " echte Wege am Server, alle " + SERVER.size() + " Fortschritte der Server-Seite erreicht");
        return List.of(String.join(", ", run.notes));
    }

    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> boolean dispatch(ServerPlayer player, T payload) {
        for (PropwashNet.C2S<?> c2s : PropwashNet.freezeC2S()) {
            if (c2s.type().equals(payload.type())) {
                PropwashNet.C2S<T> typed = (PropwashNet.C2S<T>) c2s;
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
                typed.codec().encode(buf, payload);
                T decoded = typed.codec().decode(buf);
                typed.handler().handle(decoded, new ServerPayloadContext(player));
                return true;
            }
        }
        return false;
    }

    private interface CasePath {
        @Nullable String run();
    }

    private static final class Cases {
        final MinecraftServer server;
        final ServerPlayer player;
        final ServerLevel level;
        final Vec3 feet;
        final List<String> problems = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        final List<QuadcopterEntity> drones = new ArrayList<>();
        int paths;

        Cases(MinecraftServer server, ServerPlayer player) {
            this.server = server;
            this.player = player;
            this.level = player.level();
            this.feet = player.position();
        }

        void all() {
            Inventory inventory = this.player.getInventory();
            List<ItemStack> backup = new ArrayList<>();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                backup.add(inventory.getItem(i).copy());
            }
            ItemStack head = this.player.getItemBySlot(EquipmentSlot.HEAD).copy();
            Map<PropwashStats.Entry, Integer> stats = new LinkedHashMap<>();
            for (PropwashStats.Entry entry : PropwashStats.all()) {
                stats.put(entry, PilotStats.value(this.player, entry));
            }
            try {
                inventory.clearContent();
                this.expect("Rennen über RaceServer", List.of("first_lap", "own_course", "personal_best"), this::race);
                this.expect("Werkbank Klick", List.of("drone_built"), () -> this.workbench(ContainerInput.PICKUP));
                this.expect("Werkbank Shift-Klick", List.of("drone_built"), () -> this.workbench(ContainerInput.QUICK_MOVE));
                this.expect("Werkbank Zifferntaste", List.of("drone_built"), () -> this.workbench(ContainerInput.SWAP));
                this.expect("Werkbank Wegwerfen", List.of("drone_built"), () -> this.workbench(ContainerInput.THROW));
                this.expect("Akkutausch", List.of("battery_swapped"), this::batterySwap);
                this.expect("Ladegerät Klick", List.of("battery_charged"), () -> this.charger(ContainerInput.PICKUP));
                this.expect("Ladegerät Shift-Klick", List.of("battery_charged"), () -> this.charger(ContainerInput.QUICK_MOVE));
                this.expect("Strecke über SERVER_MOVED", List.of("distance_1km", "distance_10km", "distance_100km"), this::distance);
                this.expect("Höhe und Sturz über SERVER_MOVED", List.of("build_limit", "dive"), this::heightAndDive);
                this.expect("Crashes über stats_crash", List.of("veteran"), this::crashes);
                this.expect("Fracht über DroneCargo", List.of("cargo"), this::cargo);
                this.expect("Lackierung über PaintService", List.of("painted"), this::paint);
                this.expect("Sharecode über BuildAccessImpl", List.of("sharecode"), this::sharecode);
                this.expect("Zuschauen über SpectateServer", List.of("spectator"), this::spectate);
                this.expect("Enderman über DroneStrikes.onStrike", List.of("enderman"), this::enderman);
                this.expect("Drache und Wither mit Todesstoß", List.of("ender_dragon", "wither"), this::bosses);
            } finally {
                for (QuadcopterEntity drone : this.drones) {
                    if (!drone.isRemoved()) {
                        drone.setPilot(null);
                        drone.discard();
                    }
                }
                inventory.clearContent();
                for (int i = 0; i < backup.size(); i++) {
                    inventory.setItem(i, backup.get(i));
                }
                this.player.setItemSlot(EquipmentSlot.HEAD, head);
                this.player.containerMenu = this.player.inventoryMenu;
                stats.forEach((entry, value) -> this.player.getStats().setValue(this.player, entry.stat(), value));
            }
        }

        void expect(String label, List<String> names, CasePath path) {
            this.paths++;
            revoke(this.server, this.player, names);
            String problem;
            try {
                problem = path.run();
            } catch (RuntimeException e) {
                Constants.LOG.error("[Fortschritte] Weg {} fehlgeschlagen", label, e);
                problem = e.toString();
            }
            if (problem != null) {
                this.problems.add(label + ": " + problem);
                return;
            }
            List<String> missing = missing(this.server, this.player, names);
            if (!missing.isEmpty()) {
                this.problems.add(label + ": nicht erreicht " + missing);
            }
        }

        QuadcopterEntity drone(Vec3 at, DroneBuild build, @Nullable ServerPlayer pilot) {
            QuadcopterEntity drone = DroneModule.DRONE.get().create(this.level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("Drohne nicht erzeugt");
            }
            drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
            drone.setAttitude(new Quaternionf());
            drone.setBuild(build);
            drone.setBatteryCharge(1.0F);
            if (!this.level.addFreshEntity(drone)) {
                throw new IllegalStateException("Drohne nicht gespawnt");
            }
            this.drones.add(drone);
            if (pilot != null) {
                drone.setPilot(pilot);
            }
            return drone;
        }

        void done(QuadcopterEntity drone) {
            drone.setPilot(null);
            drone.discard();
        }

        @Nullable String race() {
            Vec3 origin = RaceDataJoinCheck.origin(this.player);
            List<GateEntity> gates = new ArrayList<>();
            CourseRegistry.Course course = RaceDataJoinCheck.build(this.player, origin, gates);
            CourseRegistry registry = CourseRegistry.get();
            try {
                if (course == null || registry == null) {
                    return "Kurs nicht angelegt";
                }
                QuadcopterEntity probe = DroneModule.DRONE.get().create(this.level, EntitySpawnReason.COMMAND);
                if (probe == null) {
                    return "Drohne nicht erzeugt";
                }
                String frame = probe.build().frame().key();
                probe.discard();
                registry.board(course).submit(this.player.getUUID(), this.player.getGameProfile().name(), frame, SEEDED_LAP_NANOS,
                        System.currentTimeMillis() - 60_000L);
                String problem = RaceDataJoinCheck.fly(course, this.player, origin, FLOWN_LAP_NANOS);
                if (problem != null) {
                    return problem;
                }
                this.notes.add("Kurs " + course.id + " eigene Runde schneller als " + SEEDED_LAP_NANOS / 1_000_000_000L + " s");
                return null;
            } finally {
                if (course != null && registry != null) {
                    registry.deleteCourse(course.id);
                }
                gates.forEach(GateEntity::discard);
            }
        }

        @Nullable String workbench(ContainerInput input) {
            listenAssembled();
            DroneWorkbenchMenu menu = new DroneWorkbenchMenu(0, this.player.getInventory());
            List<ItemEntity> thrown = new ArrayList<>();
            try {
                DroneBuild build = DroneBuild.RACER;
                ItemStack frame = ContentItems.stack(build.frame());
                PaintItems.store(frame, WORKBENCH_PAINT);
                frame.set(DataComponents.CUSTOM_NAME, Component.literal(WORKBENCH_NAME));
                put(menu, BuildSlots.FRAME, frame);
                put(menu, BuildSlots.STACK, ContentItems.stack(build.stack()));
                put(menu, BuildSlots.VIDEO, ContentItems.stack(build.video()));
                put(menu, BuildSlots.BATTERY, ContentItems.stack(build.battery()));
                for (int i = 0; i < DroneBuild.MOTOR_COUNT; i++) {
                    put(menu, BuildSlots.MOTOR + i, ContentItems.stack(build.motor()));
                    put(menu, BuildSlots.PROP + i, ContentItems.stack(build.prop()));
                }
                menu.setPendingTune(new DroneWorkbenchMenu.PendingTune(WORKBENCH_TUNE, EnumSet.of(TuneGroup.RATES)));
                if (!menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).hasItem()) {
                    return "kein Ergebnis in der Werkbank: " + menu.check();
                }
                ItemStack built = menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem().copy();
                DroneData expected = DroneDataReader.read(built);
                if (expected.build().equals(Conversions.spec(DroneBuild.FREESTYLE)) || !expected.paint().equals(Conversions.paint(WORKBENCH_PAINT))
                        || !expected.name().equals(Optional.of(WORKBENCH_NAME)) || !expected.tune().values().containsKey(WORKBENCH_TUNE_KEY)) {
                    return "Werkbank baut nicht die vorbereitete Drohne: " + expected;
                }
                ASSEMBLED.clear();
                menu.clicked(DroneWorkbenchMenu.RESULT_SLOT, 0, input, this.player);
                ItemStack received = switch (input) {
                    case PICKUP -> menu.getCarried().copy();
                    case THROW -> {
                        thrown.addAll(this.level.getEntitiesOfClass(ItemEntity.class, this.player.getBoundingBox().inflate(6.0),
                                each -> DroneAssembly.isDrone(each.getItem())));
                        yield thrown.isEmpty() ? ItemStack.EMPTY : thrown.getFirst().getItem().copy();
                    }
                    default -> droneIn(this.player.getInventory());
                };
                if (!ItemStack.isSameItemSameComponents(received, built)) {
                    return "Drohne nicht beim Spieler (" + input + "): " + describe(received);
                }
                if (ASSEMBLED.size() != 1) {
                    return ASSEMBLED.size() + " Mal DRONE_ASSEMBLED statt einmal (" + input + ")";
                }
                Assembled event = ASSEMBLED.getFirst();
                if (event.player() != this.player || !ItemStack.isSameItemSameComponents(event.drone(), built) || !event.data().equals(expected)) {
                    return "DRONE_ASSEMBLED ohne die gebaute Drohne (" + input + "): " + describe(event.drone()) + " " + event.data();
                }
                return null;
            } finally {
                thrown.forEach(ItemEntity::discard);
                menu.setCarried(ItemStack.EMPTY);
                this.player.getInventory().clearContent();
            }
        }

        @Nullable String batterySwap() {
            QuadcopterEntity drone = this.drone(this.feet.add(2.0, 0.1, 2.0), DroneBuild.FREESTYLE, null);
            try {
                ItemStack old = drone.takeBattery();
                Optional<BatteryType> type = Batteries.typeOf(old);
                if (type.isEmpty()) {
                    return "Drohne ohne Akku";
                }
                this.player.setItemSlot(EquipmentSlot.MAINHAND, Batteries.stackOf(type.get(), 1.0F));
                DroneInteractions.apply(this.player, drone, DroneInteraction.Action.QUICK_SWAP, this.player.getMainHandItem());
                return drone.hasBattery() ? null : "Akku nicht eingesetzt";
            } finally {
                this.player.getInventory().clearContent();
                this.done(drone);
            }
        }

        @Nullable String charger(ContainerInput input) {
            BatteryType type = DroneBuild.FREESTYLE.battery();
            SimpleContainer slots = new SimpleContainer(BatteryChargerMenu.SLOTS);
            slots.setItem(0, Batteries.stackOf(type, 1.0F));
            BatteryChargerMenu menu = new BatteryChargerMenu(0, this.player.getInventory(), slots,
                    new SimpleContainerData(BatteryChargerBlockEntity.DATA_COUNT));
            try {
                menu.clicked(0, 0, input, this.player);
                if (!slots.getItem(0).isEmpty()) {
                    return "Akku blieb im Ladegerät (" + input + ")";
                }
                boolean taken = input == ContainerInput.PICKUP ? Batteries.isBattery(menu.getCarried()) : carriesBattery(this.player.getInventory());
                return taken ? null : "Akku nicht beim Spieler (" + input + ")";
            } finally {
                menu.setCarried(ItemStack.EMPTY);
                menu.removed(this.player);
                this.player.getInventory().clearContent();
            }
        }

        @Nullable String distance() {
            QuadcopterEntity drone = this.drone(this.feet.add(-2.0, 2.0, 2.0), DroneBuild.FREESTYLE, this.player);
            try {
                this.player.getStats().setValue(this.player, PropwashStats.DISTANCE.stat(), DISTANCE_CM);
                this.move(drone, drone.position().add(0.5, 0.0, 0.0));
                return null;
            } finally {
                this.done(drone);
            }
        }

        @Nullable String heightAndDive() {
            Vec3 high = new Vec3(this.feet.x - 2.0, BUILD_LIMIT_Y, this.feet.z - 2.0);
            QuadcopterEntity drone = this.drone(high, DroneBuild.FREESTYLE, this.player);
            try {
                drone.setArmed(true);
                this.move(drone, high.add(0.0, 0.1, 0.0));
                this.move(drone, high.add(0.0, 0.1 - DIVE_BLOCKS, 0.0));
                return null;
            } finally {
                drone.setArmed(false);
                this.done(drone);
            }
        }

        void move(QuadcopterEntity drone, Vec3 to) {
            Vec3 from = drone.position();
            drone.snapTo(to.x, to.y, to.z, drone.getYRot(), drone.getXRot());
            QuadcopterEntity.SERVER_MOVED.fire(listener -> listener.onServerMoved(drone, this.player, from, to));
        }

        @Nullable String crashes() {
            QuadcopterEntity drone = this.drone(this.feet.add(-2.0, 0.1, -2.0), DroneBuild.FREESTYLE, this.player);
            try {
                this.player.getStats().setValue(this.player, PropwashStats.CRASHES.stat(), CRASHES_BEFORE);
                if (!dispatch(this.player, new CrashC2S(drone.getId(), 1))) {
                    return "stats_crash nicht angemeldet";
                }
                int crashes = PilotStats.value(this.player, PropwashStats.CRASHES);
                return crashes == CRASHES_BEFORE + 1 ? null : "Server zählt " + crashes + " Crashes statt " + (CRASHES_BEFORE + 1);
            } finally {
                this.done(drone);
            }
        }

        @Nullable String cargo() {
            Vec3 at = this.feet.add(0.0, 3.0, -4.0);
            QuadcopterEntity drone = this.drone(at, DroneBuild.FREESTYLE.withAccessory(AccessoryType.GRIPPER), this.player);
            List<ItemEntity> items = new ArrayList<>();
            try {
                drone.setArmed(true);
                ItemEntity item = new ItemEntity(this.level, at.x, at.y - 0.3, at.z, new ItemStack(Items.FEATHER));
                item.setNoPickUpDelay();
                item.setDeltaMovement(Vec3.ZERO);
                if (!this.level.addFreshEntity(item)) {
                    return "Fracht nicht gespawnt";
                }
                items.add(item);
                if (!DroneCargo.grab(drone, this.player) || drone.cargoHold().itemCount() != 1) {
                    return "Greifer nimmt die Fracht nicht auf: " + drone.cargoHold().itemCount();
                }
                Vec3 away = at.add(CARGO_BLOCKS, 0.0, 0.0);
                drone.snapTo(away.x, away.y, away.z, 0.0F, 0.0F);
                if (DroneCargo.releaseBy(drone, this.player) != 1) {
                    return "Greifer lässt die Fracht nicht ab";
                }
                items.addAll(this.level.getEntitiesOfClass(ItemEntity.class, new AABB(away, away).inflate(3.0),
                        each -> each.getItem().is(Items.FEATHER)));
                this.notes.add(String.format(Locale.ROOT, "Fracht %.0f Blöcke getragen", CARGO_BLOCKS));
                return null;
            } finally {
                items.forEach(ItemEntity::discard);
                drone.setArmed(false);
                this.done(drone);
            }
        }

        @Nullable String paint() {
            DroneWorkbenchMenu menu = new DroneWorkbenchMenu(0, this.player.getInventory());
            try {
                put(menu, BuildSlots.FRAME, ContentItems.stack(DroneBuild.FREESTYLE.frame()));
                this.player.getInventory().setItem(0, new ItemStack(Items.DYE.red(), 3));
                this.player.containerMenu = menu;
                DronePaint red = DronePaint.DEFAULT.with(PaintSlot.FRAME, PaintSwatch.RED.rgb()).with(PaintSlot.MOTORS, PaintSwatch.RED.rgb());
                if (!dispatch(this.player, new PaintC2S(menu.containerId, red))) {
                    return "PaintC2S nicht angemeldet";
                }
                return red.equals(menu.paint()) ? null : "Lackierung nicht angewendet: " + menu.paint();
            } finally {
                this.player.containerMenu = this.player.inventoryMenu;
                menu.setCarried(ItemStack.EMPTY);
                this.player.getInventory().clearContent();
            }
        }

        @Nullable String sharecode() {
            try {
                this.player.setItemSlot(EquipmentSlot.MAINHAND, ContentTab.presetDrone(DroneBuild.FREESTYLE));
                DroneData data = new DroneData(Conversions.spec(DroneBuild.FREESTYLE), PaintSpec.DEFAULT, TuneSpec.EMPTY, Optional.of("Fortschritt FPV"));
                ImportResult result = PropwashApi.builds().applyImport(this.player, new ImportRequest(data, EnumSet.of(ImportLayer.NAME),
                        EnumSet.noneOf(TuneGroup.class), ImportTarget.HELD_DRONE));
                return result.status() == ImportStatus.APPLIED ? null : "Import " + result;
            } finally {
                this.player.getInventory().clearContent();
            }
        }

        @Nullable String spectate() {
            ServerPlayer other = RaceDataJoinCheck.secondPilot(this.server, this.level, SPECTATED_PILOT);
            QuadcopterEntity drone = this.drone(this.feet.add(3.0, 2.0, -3.0), DroneBuild.FREESTYLE, other);
            ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
            if (goggles.isEmpty()) {
                this.done(drone);
                return "Kreativ-Brille nicht registriert";
            }
            this.player.setItemSlot(EquipmentSlot.HEAD, goggles);
            try {
                if (!dispatch(this.player, new SpectateC2S(Optional.of(drone.getUUID())))) {
                    return "SpectateC2S nicht angemeldet";
                }
                return null;
            } finally {
                dispatch(this.player, SpectateC2S.STOP);
                this.player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                this.done(drone);
            }
        }

        @Nullable String enderman() {
            Enderman enderman = spawn(this.level, EntityTypes.ENDERMAN, this.feet.add(4.0, 0.0, 0.0));
            if (enderman == null) {
                return "Enderman nicht erzeugt";
            }
            Vec3 before = enderman.position();
            float health = enderman.getHealth();
            AABB box = enderman.getBoundingBox();
            QuadcopterEntity drone = this.drone(new Vec3(box.minX - 0.5, box.minY + 1.0, before.z), DroneBuild.FREESTYLE, this.player);
            try {
                if (!dispatch(this.player, new DroneStrikeC2S(drone.getId(), drone.resetEpoch(), enderman.getId(), 4.0F, 0.0F, 0.0F, 0, true))) {
                    return "DroneStrikeC2S nicht angemeldet";
                }
                double moved = enderman.position().distanceTo(before);
                this.notes.add(String.format(Locale.ROOT, "Enderman %.1f Blöcke weg", moved));
                if (moved < 1.0 || enderman.getHealth() < health) {
                    return String.format(Locale.ROOT, "Enderman nicht teleportiert oder verletzt (%.1f Blöcke, %.1f von %.1f)", moved,
                            enderman.getHealth(), health);
                }
                return null;
            } finally {
                enderman.discard();
                this.done(drone);
            }
        }

        @Nullable String bosses() {
            QuadcopterEntity drone = this.drone(this.feet.add(2.0, 0.1, -2.0), DroneBuild.FREESTYLE, null);
            try {
                Optional<DamageSource> source = DroneStrikes.damageSource(this.level, drone, this.player);
                if (source.isEmpty()) {
                    return "Schadensart propwash:drone_strike fehlt";
                }
                for (EntityType<? extends Mob> boss : List.of(EntityTypes.WITHER, EntityTypes.ENDER_DRAGON)) {
                    Mob mob = spawn(this.level, boss, this.feet.add(0.0, 40.0, 0.0));
                    if (mob == null) {
                        return boss + " nicht erzeugt";
                    }
                    mob.setHealth(1.0F);
                    mob.hurtServer(this.level, source.get(), 20.0F);
                    boolean dying = mob instanceof EnderDragon dragon && dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING;
                    boolean dead = mob.isDeadOrDying() || dying;
                    mob.discard();
                    if (!dead) {
                        return boss + " überlebt den Todesstoß";
                    }
                }
                return null;
            } finally {
                this.done(drone);
            }
        }

        void put(DroneWorkbenchMenu menu, int slot, ItemStack stack) {
            menu.setCarried(stack);
            menu.clicked(slot, 0, ContainerInput.PICKUP, this.player);
            if (!menu.getCarried().isEmpty()) {
                throw new IllegalStateException("Werkbank-Slot " + slot + " nimmt " + stack + " nicht an");
            }
        }
    }

    private static void listenAssembled() {
        if (LISTENING.compareAndSet(false, true)) {
            PropwashEvents.DRONE_ASSEMBLED.register((player, drone, data) -> ASSEMBLED.add(new Assembled(player, drone.copy(), data)));
        }
    }

    private static ItemStack droneIn(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(DroneModule.DRONE_ITEM.get())) {
                return inventory.getItem(i).copy();
            }
        }
        return ItemStack.EMPTY;
    }

    private static String describe(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount();
    }

    private static boolean carriesBattery(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (Batteries.isBattery(inventory.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private static <T extends Mob> @Nullable T spawn(ServerLevel level, EntityType<T> type, Vec3 at) {
        T mob = type.create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            return null;
        }
        mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        mob.setNoAi(true);
        return level.addFreshEntity(mob) ? mob : null;
    }

    public static List<String> missing(MinecraftServer server, ServerPlayer player, List<String> names) {
        List<String> out = new ArrayList<>();
        for (String name : names) {
            AdvancementHolder holder = server.getAdvancements().get(id(name));
            if (holder == null || !player.getAdvancements().getOrStartProgress(holder).isDone()) {
                out.add(name);
            }
        }
        return out;
    }

    public static List<String> done(MinecraftServer server, ServerPlayer player) {
        List<String> out = new ArrayList<>(NAMES);
        out.removeAll(missing(server, player, NAMES));
        return out;
    }

    public static int revokeAll(MinecraftServer server, ServerPlayer player) {
        return revoke(server, player, NAMES);
    }

    public static int revoke(MinecraftServer server, ServerPlayer player, List<String> names) {
        int revoked = 0;
        for (String name : names) {
            AdvancementHolder holder = server.getAdvancements().get(id(name));
            if (holder == null) {
                continue;
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            for (String criterion : progress.getCompletedCriteria()) {
                if (player.getAdvancements().revoke(holder, criterion)) {
                    revoked++;
                }
            }
        }
        return revoked;
    }
}
