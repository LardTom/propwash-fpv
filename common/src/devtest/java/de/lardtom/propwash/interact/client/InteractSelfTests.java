package de.lardtom.propwash.interact.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseInventoryScreen;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.interact.CargoReleaseReason;
import de.lardtom.propwash.interact.DroneCargo;
import de.lardtom.propwash.interact.DroneStrikes;
import de.lardtom.propwash.interact.GripperSpec;
import de.lardtom.propwash.interact.InteractRules;
import de.lardtom.propwash.interact.ItemWeights;
import de.lardtom.propwash.mixin.drone.ItemEntityAccessor;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class InteractSelfTests {
    private static final DroneBuild PLAIN = DroneBuild.FREESTYLE;
    private static final DroneBuild GRIPPER = DroneBuild.FREESTYLE.withAccessory(AccessoryType.GRIPPER);
    private static final int ARENA_DX = 34;
    private static final int ARENA_BACK = 7;
    private static final int ARENA_AHEAD = 12;
    private static final int ARENA_SIDE = 5;
    private static final int ARENA_HEIGHT = 7;
    private static final double PLAYER_BACK = 4.5;
    private static final double COW_AHEAD = 3.5;
    private static final double WALL_AHEAD = 3.0;
    private static final double SIDESTEP = 2.0;
    static final double HOVER_HEIGHT = 0.5;
    private static final double HOLD_GAIN = 0.3;
    private static final double HOLD_DAMPING = 0.1;
    private static final double SETTLE_HEIGHT = 0.15;
    private static final double SETTLE_SPEED = 0.4;
    private static final double SHOT_HEIGHT = 1.35;
    private static final double SHOT_AHEAD = 2.1;
    private static final double SHOT_SIDE = 1.1;
    private static final double PUSH_SPEED = 12.0;
    private static final double CRASH_SPEED = 15.0;
    private static final int IRON = 8;
    private static final int COBBLE = 64;
    private static final int FOREIGN = 3;
    private static final UUID STRANGER = UUID.fromString("00000000-0000-4000-8000-00000000beef");
    private static final int GOLD = 3;
    private static final int APPLES = 4;
    static final String PHASE = "phase";
    static final String MARK = "mark";
    static final String FUTURE = "future";
    private static final String SHOT = "shot";
    private static final String CRASHES = "crashes";
    private static final String FOV = "fov";
    private static final String TRACK = "track";
    private static final float FACING_YAW = -90.0F;
    static final AtomicInteger DRONE_ID = new AtomicInteger(-1);
    private static final AtomicInteger COW_ID = new AtomicInteger(-1);
    static final AtomicReference<int @Nullable []> ARENA = new AtomicReference<>();
    static final AtomicReference<String> SERVER_REPORT = new AtomicReference<>("");
    private static final AtomicReference<CargoReleaseReason> RELEASED = new AtomicReference<>();
    private static final List<Strike> STRIKES = new CopyOnWriteArrayList<>();
    private static volatile double hoverTarget = HOVER_HEIGHT;

    private InteractSelfTests() {
    }

    public static void register() {
        DroneStrikes.STRUCK.register((drone, target, strike, hurt) ->
                STRIKES.add(new Strike(target.getId(), strike.damage(), strike.knockback(), hurt, target.getDeltaMovement().x)));
        DroneCargo.CHANGED.register((drone, grams, released) -> {
            if (released != null) {
                RELEASED.set(released);
            }
        });
        SelfTest.clientStep("interact: Spielregel propwash:drone_hits_entities registriert und beim Client", 100, InteractSelfTests::ruleStep);
        SelfTest.clientStep("interact: Shift-Tooltip des Greifers nennt die Tragkraft", 20, ctx -> tooltipStep());
        SelfTest.clientStep("interact: Behälter wiegen mit Inhalt, die Tragkraft folgt der Schwerkraft-Regel", 20, ctx -> weightStep());
        SelfTest.clientStep("interact: Drohne prallt an einer Kuh ab, verletzt sie und stößt sie weg", 900, ctx -> strikeStep(ctx, true));
        SelfTest.clientStep("interact: Spielregel aus – die Drohne fliegt durch die Kuh", 900, ctx -> strikeStep(ctx, false));
        if (Screenshots.enabled()) {
            SelfTest.clientStep("interact: Fenster für Bildschirmfotos", 100, ctx -> {
                StepResult result = Screenshots.resizeWindow(ctx);
                if (result.status() == StepResult.Status.PASS) {
                    Screenshots.hideMarker(true);
                }
                return result;
            });
        }
        SelfTest.clientStep("interact: Greifer nimmt Items auf, die Fracht macht die Drohne schwerer", 900, InteractSelfTests::pickupStep);
        SelfTest.clientStep("interact: Greifer wirft auf Tastendruck ab und greift nicht sofort wieder zu", 300, InteractSelfTests::dropStep);
        SelfTest.clientStep("interact: Landen und Entschärfen setzt die Fracht ab", 600, InteractSelfTests::landStep);
        SelfTest.clientStep("interact: Absturz an einer Mauer verliert die Fracht", 900, InteractSelfTests::crashStep);
        SelfTest.clientStep("interact: Aufräumen", 300, InteractSelfTests::cleanupStep);
        if (Screenshots.enabled()) {
            Screenshots.step("bildschirmfotos: Shift-Tooltip des Greifers", "tooltip_gripper", 30, new TooltipScene());
            SelfTest.clientStep("interact: Fenster zurück", 5, ctx -> {
                Screenshots.hideMarker(false);
                return Screenshots.restoreWindow(ctx);
            });
        }
    }

    private static StepResult ruleStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                Identifier id = BuiltInRegistries.GAME_RULE.getKey(InteractRules.rule());
                if (id == null || !"propwash:drone_hits_entities".equals(id.toString())) {
                    throw new IllegalStateException("Spielregel nicht registriert: " + id);
                }
                if (!InteractRules.dronesHitEntities(server)) {
                    throw new IllegalStateException("Standardwert ist nicht an");
                }
                SERVER_REPORT.set(InteractRules.rule().id() + " = " + server.getGameRules().getAsString(
                        InteractRules.rule()));
            }));
            return StepResult.pending();
        }
        StepResult server = await(ctx, "Spielregel");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        if (!ClientInteractRules.dronesHitEntities()) {
            return ctx.tick() > 80 ? StepResult.fail("Client kennt die Spielregel nicht (keine Welt-Regeln vom Server)") : StepResult.pending();
        }
        ctx.log(SERVER_REPORT.get() + ", beim Client angekommen");
        return StepResult.pass();
    }

    private static StepResult tooltipStep() {
        Minecraft mc = Minecraft.getInstance();
        Item item = BuiltInRegistries.ITEM.getValue(AccessoryType.GRIPPER_ID);
        ItemStack stack = new ItemStack(item);
        List<Component> lines = new ArrayList<>();
        try {
            TooltipShift.override(true);
            item.appendHoverText(stack, Item.TooltipContext.of(mc.level), TooltipDisplay.DEFAULT, lines::add, TooltipFlag.NORMAL);
        } finally {
            TooltipShift.override(null);
        }
        Set<String> keys = new HashSet<>();
        collectKeys(lines, keys);
        List<String> missing = new ArrayList<>();
        for (String key : List.of("propwash.help.gripper.summary", "propwash.content.tooltip.payload", "propwash.help.gripper.use.3")) {
            if (!keys.contains(key)) {
                missing.add(key);
            }
        }
        return StepResult.check(missing.isEmpty(), "Zeilen fehlen im Shift-Tooltip: " + missing + " (gefunden " + keys + ")");
    }

    private static StepResult weightStep() {
        ItemStack emptyBox = new ItemStack(Items.SHULKER_BOX);
        ItemStack fullBox = new ItemStack(Items.SHULKER_BOX);
        fullBox.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.IRON_INGOT, 10))));
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStackTemplate(Items.IRON_INGOT, 16))));
        ItemStack boxWithBundle = new ItemStack(Items.SHULKER_BOX);
        boxWithBundle.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(bundle.copy())));
        double empty = DroneCargo.gramsEach(emptyBox);
        double full = DroneCargo.gramsEach(fullBox);
        double inBundle = DroneCargo.gramsEach(bundle);
        double nested = DroneCargo.gramsEach(boxWithBundle);
        double box = ItemWeights.SINGLE_GRAMS;
        List<String> wrong = new ArrayList<>();
        if (empty != box) {
            wrong.add("leere Shulker-Kiste " + empty + " g");
        }
        if (full != box + 128 * ItemWeights.BLOCK_GRAMS + 10 * ItemWeights.SMALL_GRAMS) {
            wrong.add("volle Shulker-Kiste " + full + " g");
        }
        if (inBundle != ItemWeights.SINGLE_GRAMS + 16 * ItemWeights.SMALL_GRAMS) {
            wrong.add("Bündel " + inBundle + " g");
        }
        if (nested != box + inBundle) {
            wrong.add("Kiste mit Bündel " + nested + " g");
        }
        double light = DroneCargo.capacityGrams(GRIPPER, 0.8);
        double normal = DroneCargo.capacityGrams(GRIPPER, 1.0);
        double heavy = DroneCargo.capacityGrams(DroneBuild.WHOOP.withAccessory(AccessoryType.GRIPPER), 1.5);
        if (normal != GripperSpec.MAX_PAYLOAD_GRAMS || light != normal || heavy != 0.0) {
            wrong.add("Tragkraft " + light + " / " + normal + " g, Whoop bei 150 % " + heavy + " g");
        }
        return StepResult.check(wrong.isEmpty(), String.join(", ", wrong));
    }

    private static void collectKeys(List<Component> lines, Set<String> keys) {
        for (Component line : lines) {
            collect(line, keys);
        }
    }

    private static void collect(Component component, Set<String> keys) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            keys.add(translatable.getKey());
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component nested) {
                    collect(nested, keys);
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            collect(sibling, keys);
        }
    }

    private static StepResult strikeStep(SelfTest.StepContext ctx, boolean rule) {
        PilotSession session = PilotSession.INSTANCE;
        String what = rule ? "Treffer" : "Spielregel aus";
        switch (phase(ctx)) {
            case 0 -> {
                if (ctx.tick() < DronePiloting.REMOTE_DEBOUNCE_TICKS + 2) {
                    return StepResult.pending();
                }
                STRIKES.clear();
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    server.getGameRules().set(InteractRules.rule(), rule, server);
                    ServerPlayer player = player(server);
                    int[] arena = arena(player);
                    ServerLevel level = player.level();
                    Vec3 start = start(arena);
                    Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
                    if (cow == null) {
                        throw new IllegalStateException("Kuh nicht erzeugt");
                    }
                    cow.snapTo(start.x + COW_AHEAD, arena[1], start.z, 90.0F, 0.0F);
                    cow.setNoAi(true);
                    cow.setPersistenceRequired();
                    level.addFreshEntity(cow);
                    COW_ID.set(cow.getId());
                    fly(player, PLAIN, start);
                }));
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                return started(ctx, what, rule ? null : Boolean.FALSE);
            }
            case 2, 3 -> {
                return climb(ctx, what);
            }
            case 4 -> {
                hover(session);
                if (!settled(session, ctx)) {
                    return since(ctx) > 200 ? StepResult.fail(what + ": schwebt nicht ruhig auf " + HOVER_HEIGHT + " m") : StepResult.pending();
                }
                ctx.state().put(CRASHES, session.crashCount());
                push(session.sim(), PUSH_SPEED, 0.0, 0.0);
                next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                hover(session);
                QuadcopterEntity drone = session.drone();
                Entity cow = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(COW_ID.get());
                if (drone == null || cow == null) {
                    return StepResult.fail(what + ": Drohne oder Kuh fehlt beim Client");
                }
                @SuppressWarnings("unchecked")
                List<String> track = (List<String>) ctx.state().computeIfAbsent(TRACK, k -> new ArrayList<String>());
                if (track.size() < 60) {
                    DroneSim tracked = session.sim();
                    track.add(String.format(Locale.ROOT, "%d:%.2f/%.2f%s", since(ctx), drone.getX() - cow.getBoundingBox().minX,
                            drone.getY() - ARENA.get()[1], tracked != null && tracked.telemetry().armed ? "" : "d"));
                }
                if (rule) {
                    if (STRIKES.isEmpty()) {
                        return since(ctx) > 80 ? StepResult.fail("kein Treffer gemeldet (Drohne bei x=" + fmt(drone.getX() - cow.getX())
                                + " vor der Kuh)") : StepResult.pending();
                    }
                    if (since(ctx) < 30) {
                        return StepResult.pending();
                    }
                    Strike strike = STRIKES.getFirst();
                    if (strike.target() != COW_ID.get() || !strike.hurt() || !(strike.damage() > 0.0F)) {
                        return StepResult.fail("Treffer falsch: " + strike);
                    }
                    if (!(strike.pushX() > 0.0)) {
                        return StepResult.fail("Kuh nicht in Flugrichtung weggestoßen: " + strike);
                    }
                    if (drone.getX() > cow.getX()) {
                        return StepResult.fail("Drohne ist durch die Kuh geflogen, Abstand zur Kuh/Höhe je Tick: " + track);
                    }
                    if (session.crashCount() <= (int) ctx.state().get(CRASHES)) {
                        return StepResult.fail("die Drohne hat den Aufprall nicht wie an einem Block gespürt (kein Absturz gezählt)");
                    }
                    ctx.state().put(FUTURE, ctx.onServer(server -> {
                        Entity serverCow = player(server).level().getEntity(COW_ID.get());
                        if (!(serverCow instanceof Cow c) || !(c.getHealth() < c.getMaxHealth())) {
                            throw new IllegalStateException("Kuh unverletzt");
                        }
                        SERVER_REPORT.set(String.format(Locale.ROOT, "Kuh %.1f/%.1f HP", c.getHealth(), c.getMaxHealth()));
                    }));
                    ctx.log(String.format(Locale.ROOT, "Treffer: %.2f HP, Rückstoß %.2f, Kuh +x %.2f, Abstürze %d", strike.damage(),
                            strike.knockback(), strike.pushX(), session.crashCount()));
                } else {
                    if (since(ctx) < 50) {
                        if (!STRIKES.isEmpty()) {
                            return StepResult.fail("Treffer trotz ausgeschalteter Spielregel: " + STRIKES.getFirst());
                        }
                        return StepResult.pending();
                    }
                    if (drone.getX() < cow.getX() + 0.45) {
                        return StepResult.fail("Drohne ist trotz Spielregel aus nicht durch die Kuh geflogen (dx=" + fmt(drone.getX() - cow.getX()) + ")");
                    }
                    ctx.state().put(FUTURE, ctx.onServer(server -> {
                        Entity serverCow = player(server).level().getEntity(COW_ID.get());
                        if (!(serverCow instanceof Cow c) || c.getHealth() < c.getMaxHealth()) {
                            throw new IllegalStateException("Kuh verletzt trotz Spielregel aus");
                        }
                        SERVER_REPORT.set("Kuh unverletzt");
                    }));
                    ctx.log("Spielregel aus: Drohne " + fmt(drone.getX() - cow.getX()) + " Blöcke hinter der Kuh, kein Treffer");
                }
                next(ctx);
                return StepResult.pending();
            }
            case 6 -> {
                StepResult server = await(ctx, what);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                ctx.log(SERVER_REPORT.get());
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                return stop(ctx, server -> {
                    discard(server, COW_ID.getAndSet(-1));
                    if (!rule) {
                        server.getGameRules().set(InteractRules.rule(), true, server);
                    }
                });
            }
        }
    }

    private static StepResult pickupStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        switch (phase(ctx)) {
            case 0 -> {
                if (ctx.tick() < DronePiloting.REMOTE_DEBOUNCE_TICKS + 2) {
                    return StepResult.pending();
                }
                RELEASED.set(null);
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    int[] arena = arena(player);
                    fly(player, GRIPPER, start(arena));
                }));
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                return started(ctx, "Greifer", null);
            }
            case 2, 3 -> {
                return climb(ctx, "Greifer");
            }
            case 4 -> {
                hover(session);
                if (!settled(session, ctx)) {
                    return since(ctx) > 200 ? StepResult.fail("Greifer: schwebt nicht ruhig auf " + HOVER_HEIGHT + " m") : StepResult.pending();
                }
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity drone = serverDrone(server);
                    spawnItems(drone, new ItemStack(Items.IRON_INGOT, IRON), 0.0);
                    spawnItems(drone, new ItemStack(Items.COBBLESTONE, COBBLE), 0.35);
                    spawnItems(drone, new ItemStack(Items.EMERALD, FOREIGN), -0.3).setTarget(STRANGER);
                }));
                next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                hover(session);
                QuadcopterEntity drone = session.drone();
                if (drone == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                double expected = IRON * 10.0 + 6 * 50.0;
                if (Math.abs(drone.cargoGrams() - expected) > 0.5) {
                    return since(ctx) > 200 ? StepResult.fail("Fracht beim Client " + drone.cargoGrams() + " g statt " + expected + " g") : StepResult.pending();
                }
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity serverDrone = serverDrone(server);
                    List<ItemStack> stacks = serverDrone.cargoHold().stacks();
                    int iron = count(stacks, Items.IRON_INGOT);
                    int cobble = count(stacks, Items.COBBLESTONE);
                    double capacity = DroneCargo.capacityGrams((ServerLevel) serverDrone.level(), serverDrone.build());
                    List<ItemEntity> left = serverDrone.level().getEntitiesOfClass(ItemEntity.class, serverDrone.getBoundingBox().inflate(2.0),
                            e -> e.getItem().is(Items.COBBLESTONE));
                    int cobbleLeft = left.stream().mapToInt(e -> e.getItem().getCount()).sum();
                    List<ItemEntity> foreign = serverDrone.level().getEntitiesOfClass(ItemEntity.class, serverDrone.getBoundingBox().inflate(2.0),
                            e -> e.getItem().is(Items.EMERALD));
                    int foreignLeft = foreign.stream().mapToInt(e -> e.getItem().getCount()).sum();
                    if (iron != IRON || cobble != 6 || stacks.size() != 2 || count(stacks, Items.EMERALD) != 0) {
                        throw new IllegalStateException("Fracht " + stacks);
                    }
                    if (foreignLeft != FOREIGN || foreign.stream().anyMatch(e -> !STRANGER.equals(((ItemEntityAccessor) e).propwash$target()))) {
                        throw new IllegalStateException("Smaragde eines anderen Spielers: " + foreignLeft + " liegen statt " + FOREIGN);
                    }
                    foreign.forEach(Entity::discard);
                    if (capacity != GripperSpec.MAX_PAYLOAD_GRAMS || serverDrone.cargoHold().grams() > capacity) {
                        throw new IllegalStateException("Tragkraft " + capacity + ", Fracht " + serverDrone.cargoHold().grams());
                    }
                    if (cobbleLeft != COBBLE - 6) {
                        throw new IllegalStateException("liegen gebliebener Bruchstein " + cobbleLeft);
                    }
                    SERVER_REPORT.set(String.format(Locale.ROOT, "Fracht %d Eisen + %d Bruchstein = %.0f g von %.0f g, %d Bruchstein liegen",
                            iron, cobble, serverDrone.cargoHold().grams(), capacity, cobbleLeft));
                }));
                next(ctx);
                return StepResult.pending();
            }
            case 6 -> {
                hover(session);
                StepResult server = await(ctx, "Fracht");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                DroneSim sim = session.sim();
                QuadcopterEntity drone = session.drone();
                if (sim == null || drone == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                double base = AirframeSpec.of(GRIPPER).massKg;
                if (Math.abs(sim.params().massKg - (base + drone.cargoGrams() / 1000.0)) > 0.003) {
                    return since(ctx) > 40 ? StepResult.fail(String.format(Locale.ROOT, "Flugmodell-Masse %.3f kg statt %.3f kg",
                            sim.params().massKg, base + drone.cargoGrams() / 1000.0)) : StepResult.pending();
                }
                if (!sim.telemetry().armed) {
                    return StepResult.fail("beim Aufnehmen entschärft (" + sim.telemetry().lastDisarmReason + ")");
                }
                ctx.log(SERVER_REPORT.get() + String.format(Locale.ROOT, ", Flugmodell %.3f kg -> %.3f kg, Schwebegas %.0f %%", base,
                        sim.params().massKg, 100.0 * sim.telemetry().hoverThrottleStick));
                next(ctx);
                return StepResult.pending();
            }
            case 7 -> {
                hover(session);
                if (!Screenshots.enabled()) {
                    return StepResult.pass();
                }
                hoverTarget = SHOT_HEIGHT;
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity serverDrone = serverDrone(server);
                    ServerPlayer player = player(server);
                    player.teleportTo(player.level(), serverDrone.getX() + SHOT_AHEAD, ARENA.get()[1], serverDrone.getZ() - SHOT_SIDE, Set.of(),
                            90.0F, 0.0F, true);
                }));
                next(ctx);
                return StepResult.pending();
            }
            case 8 -> {
                hover(session);
                StepResult server = await(ctx, "Bildschirmfoto");
                if (server.status() != StepResult.Status.PASS) {
                    hoverTarget = HOVER_HEIGHT;
                    return server;
                }
                if (!settled(session, ctx)) {
                    return since(ctx) > 200 ? fail(ctx, "schwebt für das Bildschirmfoto nicht ruhig") : StepResult.pending();
                }
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                hover(session);
                StepResult result = shot(ctx, "drone_gripper_carry");
                if (result.status() != StepResult.Status.PENDING) {
                    hoverTarget = HOVER_HEIGHT;
                }
                return result;
            }
        }
    }

    private static StepResult dropStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (drone == null || sim == null || drone.getId() != DRONE_ID.get()) {
            return StepResult.fail("keine Greifer-Sitzung aus dem vorigen Schritt");
        }
        hover(session);
        switch (phase(ctx)) {
            case 0 -> {
                RELEASED.set(null);
                KeyMapping.click(InputConstants.getKey(CargoKeys.DROP.saveString()));
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (RELEASED.get() != CargoReleaseReason.COMMAND || drone.cargoGrams() > 0.0F) {
                    return since(ctx) > 80 ? StepResult.fail("Taste „Greifer öffnen“ wirft nicht ab (" + RELEASED.get() + ", "
                            + drone.cargoGrams() + " g)") : StepResult.pending();
                }
                if (sim.params() != AirframeSpec.of(GRIPPER)) {
                    return since(ctx) > 40 ? StepResult.fail("Flugmodell nach dem Abwurf nicht wieder leicht") : StepResult.pending();
                }
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (since(ctx) < 40) {
                    if (drone.cargoGrams() > 0.0F) {
                        return StepResult.fail("greift die eben abgeworfenen Items sofort wieder");
                    }
                    return StepResult.pending();
                }
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity serverDrone = serverDrone(server);
                    List<ItemEntity> items = serverDrone.level().getEntitiesOfClass(ItemEntity.class, serverDrone.getBoundingBox().inflate(3.0));
                    int iron = 0;
                    int cobble = 0;
                    for (ItemEntity item : items) {
                        iron += item.getItem().is(Items.IRON_INGOT) ? item.getItem().getCount() : 0;
                        cobble += item.getItem().is(Items.COBBLESTONE) ? item.getItem().getCount() : 0;
                    }
                    if (iron != IRON || cobble != COBBLE || !serverDrone.cargoHold().isEmpty()) {
                        throw new IllegalStateException("nach dem Abwurf am Boden: " + iron + " Eisen, " + cobble + " Bruchstein");
                    }
                    SERVER_REPORT.set("am Boden " + iron + " Eisen, " + cobble + " Bruchstein, Greifer leer");
                }));
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                StepResult server = await(ctx, "Abwurf");
                if (server.status() == StepResult.Status.PASS) {
                    ctx.log(SERVER_REPORT.get());
                }
                return server;
            }
        }
    }

    private static StepResult landStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (drone == null || sim == null || drone.getId() != DRONE_ID.get()) {
            return StepResult.fail("keine Greifer-Sitzung aus dem vorigen Schritt");
        }
        switch (phase(ctx)) {
            case 0 -> {
                hover(session);
                RELEASED.set(null);
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity serverDrone = serverDrone(server);
                    shift(serverDrone, SIDESTEP);
                    spawnItems(serverDrone, new ItemStack(Items.GOLD_INGOT, GOLD), 0.0);
                }));
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                hover(session);
                if (Math.abs(drone.cargoGrams() - GOLD * 10.0) > 0.5) {
                    return since(ctx) > 200 ? StepResult.fail("Gold nicht aufgenommen (" + drone.cargoGrams() + " g)") : StepResult.pending();
                }
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                if (RELEASED.get() != CargoReleaseReason.DISARMED || drone.cargoGrams() > 0.0F) {
                    return since(ctx) > 200 ? StepResult.fail("nach dem Entschärfen nicht abgesetzt (" + RELEASED.get() + ", "
                            + drone.cargoGrams() + " g, scharf " + sim.telemetry().armed + ")") : StepResult.pending();
                }
                ctx.log(String.format(Locale.ROOT, "abgesetzt %d Ticks nach dem Entschärfen, %.2f m über dem Boden", since(ctx),
                        drone.getY() - ARENA.get()[1]));
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("unerwartete Phase");
            }
        }
    }

    private static StepResult crashStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (drone == null || sim == null || drone.getId() != DRONE_ID.get()) {
            return StepResult.fail("keine Greifer-Sitzung aus dem vorigen Schritt");
        }
        switch (phase(ctx)) {
            case 0 -> {
                RELEASED.set(null);
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity serverDrone = serverDrone(server);
                    shift(serverDrone, -2.0 * SIDESTEP);
                    wall((ServerLevel) serverDrone.level(), serverDrone.position(), true);
                }));
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since(ctx) >= 10, StabMode.ANGLE);
                if (!sim.telemetry().armed) {
                    return since(ctx) > 90 ? StepResult.fail("nicht wieder scharf: " + sim.telemetry().armBlockers) : StepResult.pending();
                }
                ctx.state().put("ground", drone.getY());
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                Telemetry t = sim.telemetry();
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) Math.min(1.0, t.hoverThrottleStick + 0.15)), true, StabMode.ANGLE);
                if (drone.getY() - ARENA.get()[1] < HOVER_HEIGHT) {
                    return since(ctx) > 300 ? StepResult.fail("steigt nicht wieder") : StepResult.pending();
                }
                ctx.state().put(FUTURE, ctx.onServer(server -> spawnItems(serverDrone(server), new ItemStack(Items.APPLE, APPLES), 0.0)));
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                hover(session);
                if (drone.cargoGrams() <= 0.0F || !settled(session, ctx)) {
                    return since(ctx) > 200 ? StepResult.fail("Äpfel nicht aufgenommen (" + drone.cargoGrams() + " g)") : StepResult.pending();
                }
                push(sim, CRASH_SPEED, 0.0, 0.0);
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                hover(session);
                if (RELEASED.get() != CargoReleaseReason.CRASH || drone.cargoGrams() > 0.0F) {
                    return since(ctx) > 120 ? StepResult.fail("Absturz verliert die Fracht nicht (" + RELEASED.get() + ", " + drone.cargoGrams()
                            + " g, Abstürze " + session.crashCount() + ")") : StepResult.pending();
                }
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity serverDrone = serverDrone(server);
                    int apples = serverDrone.level().getEntitiesOfClass(ItemEntity.class, serverDrone.getBoundingBox().inflate(4.0),
                            e -> e.getItem().is(Items.APPLE)).stream().mapToInt(e -> e.getItem().getCount()).sum();
                    if (apples != APPLES) {
                        throw new IllegalStateException(apples + " Äpfel am Boden statt " + APPLES);
                    }
                }));
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                StepResult server = await(ctx, "Absturz");
                if (server.status() == StepResult.Status.PASS) {
                    ctx.log("Absturz: Fracht verloren, alle " + APPLES + " Äpfel liegen am Boden");
                }
                return server;
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (phase(ctx) == 0) {
            next(ctx);
        }
        return stop(ctx, server -> {
            server.getGameRules().set(InteractRules.rule(), true, server);
            discard(server, COW_ID.getAndSet(-1));
            int[] arena = ARENA.get();
            if (arena == null) {
                return;
            }
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            AABB box = arenaBox(arena);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
                item.discard();
            }
            clear(level, arena);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
    }

    static StepResult stop(SelfTest.StepContext ctx, Consumer<MinecraftServer> after) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        if (!state.containsKey("stopping")) {
            state.put("stopping", ctx.tick());
            session.clearTestInput();
            if (session.isActive()) {
                session.requestStop();
            }
            return StepResult.pending();
        }
        if (session.isActive()) {
            return ctx.tick() - (int) state.get("stopping") > 100 && ctx.tick() > 250 ? StepResult.fail("Sitzung endet nicht") : StepResult.pending();
        }
        if (!state.containsKey("discarded")) {
            state.put("discarded", ctx.onServer(server -> {
                discard(server, DRONE_ID.getAndSet(-1));
                after.accept(server);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get("discarded");
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen: " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    static StepResult started(SelfTest.StepContext ctx, String what, @Nullable Boolean rule) {
        StepResult server = await(ctx, what + " (Start)");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        if (!session.isLineOfSight() || drone == null || drone.getId() != DRONE_ID.get() || session.sim() == null) {
            return since(ctx) > 200 ? StepResult.fail(what + ": keine Sichtflug-Sitzung") : StepResult.pending();
        }
        if (rule != null && ClientInteractRules.dronesHitEntities() != rule) {
            return since(ctx) > 100 ? StepResult.fail(what + ": Spielregel kommt nicht beim Client an") : StepResult.pending();
        }
        next(ctx);
        return StepResult.pending();
    }

    static StepResult climb(SelfTest.StepContext ctx, String what) {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        int[] arena = ARENA.get();
        if (sim == null || drone == null || arena == null) {
            return StepResult.fail(what + ": Sitzung verloren");
        }
        Telemetry t = sim.telemetry();
        if (phase(ctx) == 2) {
            boolean arm = since(ctx) >= 10;
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), arm, StabMode.ANGLE);
            if (!t.armed) {
                return since(ctx) > 80 ? StepResult.fail(what + ": nicht scharf, Sperren " + t.armBlockers) : StepResult.pending();
            }
            next(ctx);
            return StepResult.pending();
        }
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) Math.min(1.0, t.hoverThrottleStick + 0.15)), true, StabMode.ANGLE);
        if (!t.armed) {
            return StepResult.fail(what + ": beim Steigen entschärft (" + t.lastDisarmReason + ")");
        }
        if (drone.getY() - arena[1] < HOVER_HEIGHT) {
            return since(ctx) > 300 ? StepResult.fail(what + ": steigt nicht") : StepResult.pending();
        }
        next(ctx);
        return StepResult.pending();
    }

    static boolean settled(PilotSession session, SelfTest.StepContext ctx) {
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        int[] arena = ARENA.get();
        if (sim == null || drone == null || arena == null || since(ctx) < 10) {
            return false;
        }
        Vector3d velocity = new Vector3d();
        sim.getVelocity(velocity);
        return Math.abs(drone.getY() - arena[1] - hoverTarget) < SETTLE_HEIGHT && Math.abs(velocity.y) < SETTLE_SPEED
                && Math.hypot(velocity.x, velocity.z) < SETTLE_SPEED;
    }

    static void hover(PilotSession session) {
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        int[] arena = ARENA.get();
        if (sim == null || drone == null || arena == null) {
            return;
        }
        Vector3d velocity = new Vector3d();
        sim.getVelocity(velocity);
        double error = hoverTarget - (drone.getY() - arena[1]);
        double throttle = sim.telemetry().hoverThrottleStick + HOLD_GAIN * error - HOLD_DAMPING * velocity.y;
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) Math.max(0.0, Math.min(1.0, throttle))), true, StabMode.ANGLE);
    }

    private static StepResult fail(SelfTest.StepContext ctx, String message) {
        hoverTarget = HOVER_HEIGHT;
        return StepResult.fail(message);
    }

    static StepResult shot(SelfTest.StepContext ctx, String file) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FOV)) {
            state.put(FOV, mc.options.fov().get());
            mc.options.fov().set(30);
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            Screenshots.clearOverlays(mc);
            state.put(MARK, ctx.tick());
            return StepResult.pending();
        }
        if (since(ctx) < 20) {
            return StepResult.pending();
        }
        if (!state.containsKey(SHOT)) {
            state.put(SHOT, Screenshots.capture(file));
            return StepResult.pending();
        }
        CompletableFuture<?> shot = (CompletableFuture<?>) state.get(SHOT);
        if (!shot.isDone()) {
            return StepResult.pending();
        }
        mc.options.fov().set((Integer) state.get(FOV));
        if (mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        if (shot.isCompletedExceptionally()) {
            return StepResult.fail("Bildschirmfoto " + file + ": " + shot.exceptionNow());
        }
        ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(file + ".png"));
        return StepResult.pass();
    }

    static int[] arena(ServerPlayer player) {
        int[] existing = ARENA.get();
        ServerLevel level = player.level();
        int[] arena = existing;
        if (arena == null) {
            int x = player.getBlockX() + ARENA_DX;
            int z = player.getBlockZ();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            arena = new int[]{x, y, z};
            ARENA.set(arena);
        }
        clear(level, arena);
        Vec3 start = start(arena);
        player.teleportTo(level, start.x - PLAYER_BACK, arena[1], start.z - 1.5, Set.of(), FACING_YAW, 10.0F, true);
        return arena;
    }

    static Vec3 start(int[] arena) {
        return new Vec3(arena[0] + 0.5, arena[1], arena[2] + 0.5);
    }

    private static AABB arenaBox(int[] arena) {
        return new AABB(arena[0] - ARENA_BACK, arena[1] - 1, arena[2] - ARENA_SIDE, arena[0] + ARENA_AHEAD + 1, arena[1] + ARENA_HEIGHT,
                arena[2] + ARENA_SIDE + 1);
    }

    private static void clear(ServerLevel level, int[] arena) {
        for (int dx = -ARENA_BACK; dx <= ARENA_AHEAD; dx++) {
            for (int dz = -ARENA_SIDE; dz <= ARENA_SIDE; dz++) {
                BlockPos floor = new BlockPos(arena[0] + dx, arena[1] - 1, arena[2] + dz);
                if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
                    level.setBlockAndUpdate(floor, Blocks.GRASS_BLOCK.defaultBlockState());
                }
                for (int dy = 0; dy < ARENA_HEIGHT; dy++) {
                    BlockPos pos = new BlockPos(arena[0] + dx, arena[1] + dy, arena[2] + dz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    private static void wall(ServerLevel level, Vec3 drone, boolean place) {
        int x = (int) Math.floor(drone.x + WALL_AHEAD);
        int[] arena = ARENA.get();
        int y0 = arena == null ? (int) Math.floor(drone.y) : arena[1];
        int z = (int) Math.floor(drone.z);
        for (int dz = -2; dz <= 2; dz++) {
            for (int dy = 0; dy < 4; dy++) {
                level.setBlockAndUpdate(new BlockPos(x, y0 + dy, z + dz), place ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
        }
    }

    static void fly(ServerPlayer player, DroneBuild build, Vec3 pos) {
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        drone.snapTo(pos.x, pos.y, pos.z, FACING_YAW, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, upright());
        DRONE_ID.set(drone.getId());
        player.setGameMode(GameType.CREATIVE);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, DronePiloting.decideMode(player, drone).mode());
        if (!drone.isFlownBy(player.getUUID())) {
            throw new IllegalStateException("Sichtflug nicht gestartet: " + result);
        }
    }

    static Quaternionf upright() {
        return new Quaternionf().rotationY((float) Math.toRadians(-FACING_YAW) + (float) Math.PI);
    }

    private static void shift(QuadcopterEntity drone, double dz) {
        drone.resetTo(drone.position().add(0.0, 0.0, dz), upright());
    }

    private static ItemEntity spawnItems(QuadcopterEntity drone, ItemStack stack, double offsetX) {
        ServerLevel level = (ServerLevel) drone.level();
        int[] arena = ARENA.get();
        double y = arena == null ? drone.getY() - 1.0 : arena[1];
        ItemEntity item = new ItemEntity(level, drone.getX() + offsetX, y, drone.getZ(), stack, 0.0, 0.0, 0.0);
        item.setNoPickUpDelay();
        level.addFreshEntity(item);
        return item;
    }

    private static int count(List<ItemStack> stacks, Item item) {
        int sum = 0;
        for (ItemStack stack : stacks) {
            if (stack.is(item)) {
                sum += stack.getCount();
            }
        }
        return sum;
    }

    static void push(@Nullable DroneSim sim, double vx, double vy, double vz) {
        if (sim == null) {
            return;
        }
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = vx;
        snapshot.vy = vy;
        snapshot.vz = vz;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
    }

    static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    static QuadcopterEntity serverDrone(MinecraftServer server) {
        if (!(player(server).level().getEntity(DRONE_ID.get()) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Drohne auf dem Server verschwunden");
        }
        return drone;
    }

    static void discard(MinecraftServer server, int id) {
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.discard();
            } else if (entity != null) {
                entity.discard();
            }
        }
        int[] arena = ARENA.get();
        if (arena != null) {
            ServerLevel level = player(server).level();
            wall(level, start(arena), false);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, arenaBox(arena))) {
                item.discard();
            }
        }
    }

    static StepResult await(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail(what + ": kein Serverauftrag");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    static void next(SelfTest.StepContext ctx) {
        ctx.state().put(PHASE, phase(ctx) + 1);
        ctx.state().put(MARK, ctx.tick());
    }

    static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record Strike(int target, float damage, float knockback, boolean hurt, double pushX) {
    }

    private static final class TooltipScene implements Screenshots.Scene {
        private static final int SLOT = 9;
        private @Nullable CompletableFuture<Void> placed;
        private volatile @Nullable ItemStack previous;
        private volatile @Nullable GameType previousMode;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.placed == null) {
                this.placed = ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.closeContainer();
                    this.previousMode = player.gameMode();
                    player.setGameMode(GameType.SURVIVAL);
                    this.previous = player.getInventory().getItem(SLOT).copy();
                    player.getInventory().setItem(SLOT, new ItemStack(BuiltInRegistries.ITEM.getValue(AccessoryType.GRIPPER_ID)));
                });
                return StepResult.pending();
            }
            if (this.placed.isCompletedExceptionally()) {
                return StepResult.fail("Tooltip-Szene: " + this.placed.exceptionNow().getMessage());
            }
            ItemStack shown = mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getItem(SLOT);
            if (!this.placed.isDone() || !BuiltInRegistries.ITEM.getKey(shown.getItem()).equals(AccessoryType.GRIPPER_ID)
                    || mc.player.hasInfiniteMaterials()) {
                return ctx.tick() > 200 ? StepResult.fail("Tooltip-Szene: Greifer oder Überlebensmodus kommt nicht an") : StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ShowcaseInventoryScreen)) {
                if (ctx.tick() > 400) {
                    return StepResult.fail("Tooltip-Szene: Inventar öffnet nicht");
                }
                TooltipShift.override(true);
                mc.gui.setScreen(new ShowcaseInventoryScreen(mc.player, SLOT));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            TooltipShift.override(null);
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ShowcaseInventoryScreen) {
                mc.gui.setScreen(null);
            }
            ItemStack restore = this.previous;
            GameType mode = this.previousMode;
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.getInventory().setItem(SLOT, restore == null ? ItemStack.EMPTY : restore);
                if (mode != null) {
                    player.setGameMode(mode);
                }
            });
            this.placed = null;
            this.previous = null;
            this.previousMode = null;
        }
    }
}
