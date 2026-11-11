package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.accessory.MountPoses;
import de.lardtom.propwash.accessory.client.AccessoryActionInput;
import de.lardtom.propwash.accessory.client.AccessoryClient;
import de.lardtom.propwash.accessory.dispense.DispenseMath;
import de.lardtom.propwash.accessory.dispense.OwnShots;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.DispenseResult;
import de.lardtom.propwash.api.drone.CameraPose;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.BuildAccessImpl;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleDispenser;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.Knockback;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.render.DroneRenderState;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.addon.client.AccessoryHintClient;
import de.lardtom.propwash.input.client.screen.AddonSwitchRows;
import de.lardtom.propwash.sim.DroneSim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AccessoryGapsSelfTests {
    public static final String B1 = "addon-api-17: B1 – ";
    public static final String B2 = "addon-api-17: B2 – ";
    public static final String B3 = "addon-api-17: B3 – ";
    public static final String B4 = "addon-api-17: B4 – ";
    private static final String FUTURE = InteractSelfTests.FUTURE;
    private static final String COUNT = "count";
    private static final String PUSHES = "pushes";
    private static final String MIN_FORWARD = "minForward";
    private static final String SHOWN = "shown";
    private static final String CHAT = "chat";
    private static final String ROUND = "round";
    private static final String ORIGIN = "origin";
    private static final String MAX_SHIFT = "maxShift";
    private static final String MAX_SPEED = "maxSpeed";
    private static final String FIRED = "fired";
    private static final String FORWARDED = "forwarded";
    private static final String BOOSTS = "boosts";
    private static final double OWN_SHOT_GAP = 0.6;
    private static final double OWN_SHOT_AHEAD = 5.0;
    private static final int OWN_SHOT_LATE = 40;
    private static final double CATCH_UP = 0.8;
    private static final double TNT_PUSH = 5.0;
    private static final double BLAST_DISTANCE = 1.6;
    private static final double STILL = 0.3;
    private static final double MOVED = 0.5;
    private static final double QUIET = 1.0;
    private static final int MEASURE_TICKS = 40;
    private static final double ENDERMAN_AHEAD = 3.0;
    private static final double AIR = 0.5;
    private static final double MOB_HEIGHT = 1.1;
    private static final double MOB_SIDE = 3.0;
    private static final AtomicReference<String> REPORT = new AtomicReference<>("");
    private static final List<Integer> SPAWNED = new CopyOnWriteArrayList<>();
    private static final AtomicInteger PARKED = new AtomicInteger(-1);
    private static final AtomicInteger SHOT = new AtomicInteger(-1);
    private static final AtomicReference<@Nullable Entity> SHOT_ENTITY = new AtomicReference<>();
    private static final List<String> TRACE = new CopyOnWriteArrayList<>();
    private static final AtomicInteger PASSED_BEFORE = new AtomicInteger();
    private static final AtomicInteger DAMAGES_BEFORE = new AtomicInteger();
    private static final AtomicReference<@Nullable UUID> SKELETON = new AtomicReference<>();
    private static final AtomicReference<@Nullable Difficulty> DIFFICULTY = new AtomicReference<>();
    private static final AtomicReference<@Nullable GameType> MODE = new AtomicReference<>();

    private AccessoryGapsSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(B1 + "Werfer am Server: Pfeil, Windkugel, Wurftrank, consume(false)", 300, ctx -> serverStep(ctx, AccessoryGapsSelfTests::dispenseFirst));
        SelfTest.clientStep(B1 + "Werfer am Server: TNT, Knochenmehl, Eimer verweigert, triggerAction ohne Client, Aktion 5 → false", 300,
                ctx -> serverStep(ctx, AccessoryGapsSelfTests::dispenseSecond));
        SelfTest.clientStep(B1 + "Abenteuermodus: TNT und Knochenmehl DENIED, Pfeil fliegt", 300, ctx -> serverStep(ctx, AccessoryGapsSelfTests::denied));
        SelfTest.clientStep(B1 + "eigener Pfeil und eigene Windkugel, die die Drohne einholt (Gegenwind, schneller Vorwärtsflug), treffen sie nie, "
                + "Schaden durch eigene Geschosse zerlegt sie nicht", 600, AccessoryGapsSelfTests::ownShotStep);
        SelfTest.clientStep(B1 + "Schlag eines Zombies und Pfeil eines Skeletts: DRONE_DAMAGED mit dem Mob als Verursacher", 400,
                AccessoryGapsSelfTests::mobStep);
        SelfTest.clientStep(B1 + "Windkugel im Flug: Rückstoß im Flugmodell messbar", 1500, AccessoryGapsSelfTests::windStep);
        DispenseRecoilSelfTests.registerB1(B1);
        SelfTest.clientStep(B1 + "TNT im Vorwärtsflug fällt mit dem Tempo der Drohne", 1500, AccessoryGapsSelfTests::tntStep);
        SelfTest.clientStep(B1 + "Zubehör-Aktion 1 heißt „Werfer feuern“, Hinweis beim ersten Flug genau einmal", 1800, AccessoryGapsSelfTests::hintStep);
        SelfTest.clientStep(B1 + "Aufräumen", 300, AccessoryGapsSelfTests::cleanupStep);
        SelfTest.clientStep(B2 + "Speer und Rakete an der Drohne sichtbar, nach dem Boost leer", 1500, AccessoryGapsSelfTests::displayStep);
        SelfTest.clientStep(B2 + "Aufräumen", 300, AccessoryGapsSelfTests::cleanupStep);
        AccessoryFpvSelfTests.register(B2);
        AccessoryDisplayShowcase.register(B2);
        SelfTest.clientStep(B3 + "Windkugel neben schwebender Drohne: ohne Skala kein Versatz, Skala 1 über 0,5 m, abgebrochen kein Versatz", 3000,
                AccessoryGapsSelfTests::knockbackStep);
        DispenseRecoilSelfTests.registerB3(B3);
        SelfTest.clientStep(B3 + "Aufräumen", 300, AccessoryGapsSelfTests::cleanupStep);
        SelfTest.clientStep(B4 + "loaded(level) listet alle geladenen Drohnen wie die Entity-Suche", 300, ctx -> serverStep(ctx, AccessoryGapsSelfTests::loaded));
        SelfTest.clientStep(B4 + "cameraPose zeigt beim Schweben vor einem Enderman auf dessen Kopf, leer ohne Kamera", 300,
                ctx -> serverStep(ctx, AccessoryGapsSelfTests::camera));
        SelfTest.clientStep(B4 + "Aufräumen", 300, AccessoryGapsSelfTests::cleanupStep);
    }

    static boolean ready() {
        return JoinTestEnv.exampleAddon() && AccessoryType.byId(ExampleDispenser.DISPENSER).isPresent();
    }

    static AccessoryType type(Identifier id) {
        return AccessoryType.byId(id).orElseThrow(() -> new IllegalStateException("Beispiel-Zubehör fehlt: " + id));
    }

    static DroneBuild dispenserBuild() {
        return DroneBuild.FREESTYLE.withAccessory(type(ExampleDispenser.DISPENSER));
    }

    static DroneBuild displayBuild() {
        return dispenserBuild().withAccessory(type(ExampleDispenser.SPEAR_HOLDER)).withAccessory(type(ExampleDispenser.BOOSTER));
    }

    static QuadcopterEntity hover(ServerLevel level, DroneBuild build, Vec3 ground) {
        QuadcopterEntity drone = park(level, build, ground.add(0.0, AIR, 0.0));
        drone.setNoGravity(true);
        return drone;
    }

    static QuadcopterEntity park(ServerLevel level, DroneBuild build, Vec3 pos) {
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
        SPAWNED.add(drone.getId());
        return drone;
    }

    private static void keepParked(QuadcopterEntity drone) {
        int previous = PARKED.getAndSet(drone.getId());
        if (previous >= 0 && previous != drone.getId() && drone.level().getEntity(previous) instanceof QuadcopterEntity old) {
            old.discard();
        }
    }

    private static QuadcopterEntity parked(MinecraftServer server) {
        if (!(InteractSelfTests.player(server).level().getEntity(PARKED.get()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            throw new IllegalStateException("abgestellte Werfer-Drohne fehlt");
        }
        return drone;
    }

    private static Vec3 forward(QuadcopterEntity drone) {
        Vector3f f = new Quaternionf(drone.orientation()).transform(new Vector3f(0.0F, 0.0F, -1.0F));
        return new Vec3(f.x, f.y, f.z).normalize();
    }

    private static AccessoryInventory front(QuadcopterEntity drone) {
        return PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT)
                .orElseThrow(() -> new IllegalStateException("Werfer ohne Inventar"));
    }

    static DispenseResult fire(QuadcopterEntity drone, ServerPlayer actor) {
        int before = ExampleDispenser.SHOTS.get();
        boolean pressed = PropwashApi.accessories().triggerAction(drone, ExampleDispenser.FIRE, true, actor);
        PropwashApi.accessories().triggerAction(drone, ExampleDispenser.FIRE, false, actor);
        if (!pressed || ExampleDispenser.SHOTS.get() == before) {
            throw new IllegalStateException("triggerAction(drone, 1, true, actor) löste den Werfer nicht aus (" + pressed + ")");
        }
        return ExampleDispenser.LAST_RESULT.get();
    }

    private static <T extends Entity> T expect(DispenseResult result, DispenseResult.Status status, Class<T> type, String what) {
        if (result.status() != status) {
            throw new IllegalStateException(what + ": " + result.status() + " statt " + status);
        }
        Entity entity = result.entity().orElse(null);
        if (!type.isInstance(entity)) {
            throw new IllegalStateException(what + ": Entity " + entity + " statt " + type.getSimpleName());
        }
        return type.cast(entity);
    }

    private static StepResult serverStep(SelfTest.StepContext ctx, ServerCheck check) {
        if (!ready()) {
            ctx.log("Beispiel-Add-on aus, übersprungen");
            return StepResult.pass();
        }
        if (InteractSelfTests.phase(ctx) == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> REPORT.set(check.run(server))));
            InteractSelfTests.next(ctx);
            return StepResult.pending();
        }
        StepResult server = InteractSelfTests.await(ctx, "Server");
        if (server.status() == StepResult.Status.PASS) {
            ctx.log(REPORT.get());
        }
        return server;
    }

    private static String dispenseFirst(MinecraftServer server) {
        ServerPlayer player = InteractSelfTests.player(server);
        int[] arena = InteractSelfTests.arena(player);
        ServerLevel level = player.level();
        MODE.compareAndSet(null, player.gameMode());
        player.setGameMode(GameType.CREATIVE);
        ExampleDispenser.consume(true);
        ExampleDispenser.speedScale(1.0);
        QuadcopterEntity drone = hover(level, dispenserBuild(), InteractSelfTests.start(arena));
        keepParked(drone);
        AccessoryInventory inventory = front(drone);
        Vec3 forward = forward(drone);
        List<String> out = new ArrayList<>();
        inventory.set(0, new ItemStack(Items.ARROW, 2));
        DispenseResult arrowResult = fire(drone, player);
        Arrow arrow = expect(arrowResult, DispenseResult.Status.FIRED, Arrow.class, "Pfeil");
        if (arrow.getOwner() != player) {
            throw new IllegalStateException("Besitzer des Pfeils ist " + arrow.getOwner() + " statt des Spielers");
        }
        Vec3 v = arrow.getDeltaMovement();
        double along = v.dot(forward);
        if (along < 0.9 || along > 1.3 || v.subtract(forward.scale(along)).length() > 0.35) {
            throw new IllegalStateException("Pfeil fliegt nicht nach vorn: " + v + " (vorn " + forward + ")");
        }
        if (Math.abs(arrowResult.recoilNs() - 0.02 * 22.0) > 1.0E-6 || inventory.get(0).getCount() != 1) {
            throw new IllegalStateException("Rückstoß " + arrowResult.recoilNs() + " N·s, Rest " + inventory.get(0));
        }
        if (!OwnShots.protects(drone, arrow)) {
            throw new IllegalStateException("eigener Pfeil ist nicht vor Treffern an der eigenen Drohne geschützt");
        }
        arrow.discard();
        out.add(String.format(Locale.ROOT, "Pfeil %.2f B/t nach vorn, Besitzer Spieler, Rückstoß %.2f N·s", along, arrowResult.recoilNs()));
        ExampleDispenser.consume(false);
        DispenseResult free = fire(drone, player);
        ExampleDispenser.consume(true);
        free.entity().ifPresent(Entity::discard);
        if (free.status() != DispenseResult.Status.FIRED || inventory.get(0).getCount() != 1) {
            throw new IllegalStateException("consume(false): " + free.status() + ", Rest " + inventory.get(0));
        }
        out.add("consume(false) feuert ohne Verbrauch (1 Pfeil bleibt)");
        inventory.set(0, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING));
        DispenseResult potionResult = fire(drone, player);
        ThrownSplashPotion potion = expect(potionResult, DispenseResult.Status.FIRED, ThrownSplashPotion.class, "Wurftrank");
        double potionAlong = potion.getDeltaMovement().dot(forward);
        potion.discard();
        if (!inventory.get(0).isEmpty() || potionAlong < 1.1) {
            throw new IllegalStateException("Wurftrank: Rest " + inventory.get(0) + ", Tempo " + potionAlong);
        }
        out.add(String.format(Locale.ROOT, "Wurftrank %.2f B/t, Rückstoß %.2f N·s", potionAlong, potionResult.recoilNs()));
        inventory.set(0, new ItemStack(Items.WIND_CHARGE));
        drone.setDeltaMovement(Vec3.ZERO);
        DispenseResult windResult = fire(drone, player);
        WindCharge wind = expect(windResult, DispenseResult.Status.FIRED, WindCharge.class, "Windkugel");
        wind.discard();
        double back = drone.getDeltaMovement().dot(forward);
        if (Math.abs(windResult.recoilNs() - (0.05 * 20.0 + DispenseMath.WIND_CHARGE_BLAST_NS)) > 1.0E-6 || back > -0.05) {
            throw new IllegalStateException("Windkugel: Rückstoß " + windResult.recoilNs() + " N·s, abgestellte Drohne " + back + " B/t nach vorn");
        }
        out.add(String.format(Locale.ROOT, "Windkugel Rückstoß %.2f N·s, abgestellte Drohne %.2f m/s zurück", windResult.recoilNs(), -back * 20.0));
        drone.setDeltaMovement(Vec3.ZERO);
        drone.resetTo(InteractSelfTests.start(arena).add(0.0, AIR, 0.0), InteractSelfTests.upright());
        return String.join("; ", out);
    }

    private static String dispenseSecond(MinecraftServer server) {
        ServerPlayer player = InteractSelfTests.player(server);
        ServerLevel level = player.level();
        QuadcopterEntity drone = parked(server);
        AccessoryInventory inventory = front(drone);
        Vec3 forward = forward(drone);
        List<String> out = new ArrayList<>();
        inventory.set(0, new ItemStack(Items.TNT));
        DispenseResult tntResult = fire(drone, player);
        PrimedTnt tnt = expect(tntResult, DispenseResult.Status.PLACED, PrimedTnt.class, "TNT");
        Vec3 tv = tnt.getDeltaMovement();
        Entity owner = tnt.getOwner();
        tnt.discard();
        if (Math.abs(tv.y - 0.2) > 0.01 || tv.horizontalDistance() > 0.03 || owner != player || !inventory.get(0).isEmpty()) {
            throw new IllegalStateException("TNT: Auswurf " + tv + ", Besitzer " + owner + ", Rest " + inventory.get(0));
        }
        out.add(String.format(Locale.ROOT, "TNT gezündet, Auswurf %.2f B/t hoch", tv.y));
        Vec3 muzzle = MountPoses.of(drone, AccessoryMount.FRONT).orElseThrow().front();
        BlockPos target = BlockPos.containing(muzzle).relative(DispenseMath.nearest(forward));
        level.setBlockAndUpdate(target.below(), Blocks.FARMLAND.defaultBlockState());
        level.setBlockAndUpdate(target, Blocks.WHEAT.defaultBlockState());
        try {
            inventory.set(0, new ItemStack(Items.BONE_MEAL, 2));
            DispenseResult meal = fire(drone, player);
            int age = level.getBlockState(target).is(Blocks.WHEAT) ? level.getBlockState(target).getValue(CropBlock.AGE) : -1;
            if (meal.status() != DispenseResult.Status.USED || age <= 0 || inventory.get(0).getCount() != 1) {
                throw new IllegalStateException("Knochenmehl: " + meal.status() + ", Weizen-Alter " + age + ", Rest " + inventory.get(0));
            }
            out.add("Knochenmehl USED, Weizen Alter " + age);
            inventory.set(0, new ItemStack(Items.WATER_BUCKET));
            DispenseResult bucket = fire(drone, player);
            boolean water = !level.getFluidState(target).isEmpty() || !level.getFluidState(BlockPos.containing(muzzle)).isEmpty();
            if (bucket.status() != DispenseResult.Status.REFUSED || !inventory.get(0).is(Items.WATER_BUCKET) || water) {
                throw new IllegalStateException("Wassereimer: " + bucket.status() + ", Rest " + inventory.get(0) + ", Wasser " + water);
            }
            out.add("Wassereimer REFUSED, bleibt im Slot");
        } finally {
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(target.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        }
        if (PropwashApi.accessories().triggerAction(drone, 5, true, player)) {
            throw new IllegalStateException("triggerAction mit Aktion 5 liefert true");
        }
        QuadcopterEntity plain = hover(level, DroneBuild.FREESTYLE, drone.position().add(0.0, -AIR, 2.0));
        boolean bare = PropwashApi.accessories().triggerAction(plain, 1, true, player);
        plain.discard();
        if (bare) {
            throw new IllegalStateException("triggerAction an einer Drohne ohne Zubehör liefert true");
        }
        out.add("triggerAction am Server ohne Client-Paket feuert, Aktion 5 → false, ohne Zubehör → false");
        return String.join("; ", out);
    }

    private static String denied(MinecraftServer server) {
        ServerPlayer player = InteractSelfTests.player(server);
        ServerLevel level = player.level();
        QuadcopterEntity drone = PARKED.get() >= 0 && level.getEntity(PARKED.get()) instanceof QuadcopterEntity existing && !existing.isRemoved()
                ? existing : null;
        if (drone == null) {
            int[] arena = InteractSelfTests.arena(player);
            MODE.compareAndSet(null, player.gameMode());
            drone = hover(level, dispenserBuild(), InteractSelfTests.start(arena));
            keepParked(drone);
        }
        AccessoryInventory inventory = front(drone);
        Vec3 muzzle = MountPoses.of(drone, AccessoryMount.FRONT).orElseThrow().front();
        BlockPos target = BlockPos.containing(muzzle).relative(DispenseMath.nearest(forward(drone)));
        int before = level.getEntitiesOfClass(PrimedTnt.class, drone.getBoundingBox().inflate(4.0)).size();
        player.setGameMode(GameType.ADVENTURE);
        try {
            inventory.set(0, new ItemStack(Items.TNT));
            DispenseResult tnt = fire(drone, player);
            int primed = level.getEntitiesOfClass(PrimedTnt.class, drone.getBoundingBox().inflate(4.0)).size();
            if (tnt.status() != DispenseResult.Status.DENIED || !inventory.get(0).is(Items.TNT) || primed != before) {
                throw new IllegalStateException("TNT im Abenteuermodus: " + tnt.status() + ", Rest " + inventory.get(0));
            }
            level.setBlockAndUpdate(target.below(), Blocks.FARMLAND.defaultBlockState());
            level.setBlockAndUpdate(target, Blocks.WHEAT.defaultBlockState());
            inventory.set(0, new ItemStack(Items.BONE_MEAL));
            DispenseResult meal = fire(drone, player);
            int age = level.getBlockState(target).getValue(CropBlock.AGE);
            if (meal.status() != DispenseResult.Status.DENIED || age != 0 || inventory.get(0).getCount() != 1) {
                throw new IllegalStateException("Knochenmehl im Abenteuermodus: " + meal.status() + ", Alter " + age);
            }
            inventory.set(0, new ItemStack(Items.ARROW));
            DispenseResult arrow = fire(drone, player);
            arrow.entity().ifPresent(Entity::discard);
            if (arrow.status() != DispenseResult.Status.FIRED) {
                throw new IllegalStateException("Pfeil im Abenteuermodus: " + arrow.status());
            }
        } finally {
            player.setGameMode(GameType.CREATIVE);
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(target.below(), Blocks.GRASS_BLOCK.defaultBlockState());
            inventory.set(0, ItemStack.EMPTY);
        }
        return "Abenteuermodus: TNT DENIED, Knochenmehl DENIED (Weizen unverändert), Pfeil FIRED (Geschosse ohne Bau-Rechte); Spawnschutz prüft der Server-Rauchtest";
    }

    private static StepResult ownShotStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = InteractSelfTests.player(server);
                    int[] arena = InteractSelfTests.arena(player);
                    ServerLevel level = player.level();
                    MODE.compareAndSet(null, player.gameMode());
                    player.setGameMode(GameType.CREATIVE);
                    QuadcopterEntity drone = hover(level, dispenserBuild(), InteractSelfTests.start(arena));
                    keepParked(drone);
                    front(drone).set(0, new ItemStack(Items.ARROW, 4));
                    ExampleDispenser.speedScale(0.0);
                    Arrow arrow = expect(fire(drone, player), DispenseResult.Status.FIRED, Arrow.class, "Pfeil");
                    SHOT.set(arrow.getId());
                    SHOT_ENTITY.set(arrow);
                    TRACE.clear();
                    PASSED_BEFORE.set(OwnShots.passed());
                    approach(drone, arrow);
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1, 3, 5, 7, 9 -> {
                StepResult server = InteractSelfTests.await(ctx, "Pfeil");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (InteractSelfTests.since(ctx) < 4) {
                    ctx.onServer(server -> {
                        Entity traced = SHOT_ENTITY.get();
                        if (traced != null) {
                            TRACE.add(String.format(Locale.ROOT, "t%d x%.3f v%.3f %s p%d", traced.tickCount, traced.getX(), traced.getDeltaMovement().x,
                                    traced.isRemoved() ? traced.getRemovalReason() : "-", OwnShots.passed() - PASSED_BEFORE.get()));
                        }
                    });
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity drone = parked(server);
                    Entity arrow = SHOT_ENTITY.get();
                    int passed = OwnShots.passed() - PASSED_BEFORE.get();
                    if (passed < 1 || drone.isRemoved()) {
                        throw new IllegalStateException("eigener Pfeil traf die Drohne (durchgelassen " + passed + ", Drohne weg " + drone.isRemoved() + ")");
                    }
                    if (arrow == null || arrow.getX() > drone.getBoundingBox().minX) {
                        throw new IllegalStateException("Pfeil flog nicht durch die Hülle: " + (arrow == null ? "fehlt" : arrow.position() + ", entfernt "
                                + arrow.getRemovalReason() + ", Drohne " + drone.getBoundingBox() + ", Verlauf " + TRACE));
                    }
                    REPORT.set(String.format(Locale.ROOT, "eigener Pfeil durch die Hülle: %.2f Blöcke hinter der Drohne", drone.getBoundingBox().minX - arrow.getX()));
                    arrow.discard();
                    ServerPlayer player = InteractSelfTests.player(server);
                    Arrow late = expect(fire(drone, player), DispenseResult.Status.FIRED, Arrow.class, "zweiter Pfeil");
                    late.setNoGravity(true);
                    late.setDeltaMovement(Vec3.ZERO);
                    SHOT.set(late.getId());
                    PASSED_BEFORE.set(OwnShots.passed());
                    REPORT.set(REPORT.get() + ", durchgelassen " + passed + "-mal");
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 4, 8 -> {
                if (InteractSelfTests.since(ctx) < OWN_SHOT_LATE) {
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity drone = parked(server);
                    if (!(drone.level().getEntity(SHOT.get()) instanceof Projectile late) || late.isRemoved()) {
                        throw new IllegalStateException("spätes eigenes Geschoss fehlt");
                    }
                    approach(drone, late);
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 6 -> {
                if (InteractSelfTests.since(ctx) < 8) {
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity drone = caughtUpWith("Pfeil", server);
                    REPORT.set(REPORT.get() + String.format(Locale.ROOT, "; zweiter Pfeil nach %d Ticks eingeholt: durch die Hülle, Drohne bleibt", OWN_SHOT_LATE));
                    ServerPlayer player = InteractSelfTests.player(server);
                    front(drone).set(0, new ItemStack(Items.WIND_CHARGE));
                    WindCharge wind = expect(fire(drone, player), DispenseResult.Status.FIRED, WindCharge.class, "Windkugel");
                    Vec3 ahead = drone.getBoundingBox().getCenter().add(forward(drone).scale(OWN_SHOT_AHEAD));
                    wind.setPos(ahead.x, ahead.y, ahead.z);
                    wind.setDeltaMovement(Vec3.ZERO);
                    wind.accelerationPower = 0.0;
                    wind.needsSync = true;
                    SHOT.set(wind.getId());
                    PASSED_BEFORE.set(OwnShots.passed());
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 10 -> {
                if (InteractSelfTests.since(ctx) < 8) {
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity drone = caughtUpWith("Windkugel", server);
                    REPORT.set(REPORT.get() + String.format(Locale.ROOT, "; Windkugel %.0f Blöcke voraus, nach %d Ticks eingeholt: durch die Hülle, Drohne bleibt",
                            OWN_SHOT_AHEAD, OWN_SHOT_LATE));
                    REPORT.set(REPORT.get() + "; " + ownDamage(server, drone));
                    PARKED.set(-1);
                    ExampleDispenser.speedScale(1.0);
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            default -> {
                StepResult server = InteractSelfTests.await(ctx, "Ablauf");
                if (server.status() == StepResult.Status.PASS) {
                    ctx.log(REPORT.get());
                }
                return server;
            }
        }
    }

    private static QuadcopterEntity caughtUpWith(String what, MinecraftServer server) {
        ServerLevel level = InteractSelfTests.player(server).level();
        int passed = OwnShots.passed() - PASSED_BEFORE.get();
        Entity shot = level.getEntity(SHOT.get());
        if (!(level.getEntity(PARKED.get()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            ExampleDispenser.speedScale(1.0);
            PARKED.set(-1);
            throw new IllegalStateException("eigener " + what + " nach " + OWN_SHOT_LATE + " Ticks eingeholt traf die Drohne: Drohne zerbrochen (durchgelassen "
                    + passed + ", Geschoss " + (shot == null ? "weg" : shot.position()) + ")");
        }
        if (passed < 1) {
            throw new IllegalStateException("eigener " + what + " nach " + OWN_SHOT_LATE + " Ticks nicht durch die Hülle geflogen (Geschoss "
                    + (shot == null ? "weg" : shot.position()) + ", Drohne " + drone.getBoundingBox() + ")");
        }
        if (shot != null) {
            shot.discard();
        }
        return drone;
    }

    private static String ownDamage(MinecraftServer server, QuadcopterEntity drone) {
        ServerPlayer player = InteractSelfTests.player(server);
        ServerLevel level = player.level();
        front(drone).set(0, new ItemStack(Items.ARROW));
        Arrow arrow = expect(fire(drone, player), DispenseResult.Status.FIRED, Arrow.class, "Pfeil für den Schaden");
        boolean arrowHurt = drone.hurtServer(level, level.damageSources().arrow(arrow, player), 2.0F);
        arrow.discard();
        if (drone.isRemoved()) {
            throw new IllegalStateException("Schaden 2,0 durch den eigenen Pfeil (Verursacher Spieler) zerlegt die Drohne");
        }
        front(drone).set(0, new ItemStack(Items.WIND_CHARGE));
        WindCharge wind = expect(fire(drone, player), DispenseResult.Status.FIRED, WindCharge.class, "Windkugel für den Schaden");
        boolean windHurt = drone.hurtServer(level, level.damageSources().windCharge(wind, player), 1.0F);
        wind.discard();
        if (drone.isRemoved()) {
            throw new IllegalStateException("Schaden 1,0 durch die eigene Windkugel (Verursacher Spieler) zerlegt die Drohne");
        }
        if (arrowHurt || windHurt) {
            throw new IllegalStateException("Schaden durch eigene Geschosse kam an (Pfeil " + arrowHurt + ", Windkugel " + windHurt + ")");
        }
        Vec3 at = drone.getBoundingBox().getCenter();
        WindCharge thrown = new WindCharge(player, level, at.x, at.y, at.z);
        boolean thrownHurt = drone.hurtServer(level, level.damageSources().windCharge(thrown, player), 1.0F);
        thrown.discard();
        if (!thrownHurt || !drone.isRemoved()) {
            throw new IllegalStateException("Windkugel aus der Hand des Spielers zerlegt die Drohne nicht mehr (" + thrownHurt + ")");
        }
        return "Schaden durch eigenen Pfeil 2,0 und eigene Windkugel 1,0 (Verursacher Spieler) kommt nicht an, Drohne bleibt; "
                + "dieselbe Windkugel aus der Hand des Spielers zerlegt sie wie ein Schlag";
    }

    private static void approach(QuadcopterEntity drone, Projectile shot) {
        AABB box = drone.getBoundingBox();
        Vec3 forward = forward(drone);
        Vec3 center = box.getCenter();
        double reach = Math.abs(forward.x) * box.getXsize() / 2.0 + Math.abs(forward.z) * box.getZsize() / 2.0;
        Vec3 start = center.add(forward.scale(reach + OWN_SHOT_GAP));
        shot.setNoGravity(true);
        shot.setPos(start.x, center.y, start.z);
        shot.setDeltaMovement(forward.scale(-CATCH_UP));
        if (shot instanceof AbstractHurtingProjectile hurting) {
            hurting.accelerationPower = 0.0;
        }
        shot.needsSync = true;
    }

    private static StepResult mobStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = InteractSelfTests.player(server);
                    int[] arena = InteractSelfTests.arena(player);
                    ServerLevel level = player.level();
                    if (level.getDifficulty() == Difficulty.PEACEFUL) {
                        DIFFICULTY.compareAndSet(null, Difficulty.PEACEFUL);
                        server.setDifficulty(Difficulty.EASY, true);
                    }
                    QuadcopterEntity drone = hover(level, dispenserBuild(), InteractSelfTests.start(arena).add(0.0, MOB_HEIGHT, MOB_SIDE));
                    keepParked(drone);
                    Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                    if (zombie == null) {
                        throw new IllegalStateException("Zombie nicht erzeugt");
                    }
                    zombie.setNoAi(true);
                    zombie.snapTo(drone.getX() - 1.2, arena[1], drone.getZ(), -90.0F, 0.0F);
                    level.addFreshEntity(zombie);
                    SPAWNED.add(zombie.getId());
                    int before = ExampleDispenser.DAMAGES.get();
                    boolean hit = zombie.doHurtTarget(level, drone);
                    String cause = ExampleDispenser.LAST_DAMAGE_CAUSE.get();
                    if (ExampleDispenser.DAMAGES.get() <= before || !cause.contains(zombie.getUUID().toString()) || !cause.startsWith("minecraft:zombie")) {
                        throw new IllegalStateException("Zombie-Schlag (" + hit + ") ohne DRONE_DAMAGED mit Zombie als Verursacher: " + cause);
                    }
                    zombie.discard();
                    Skeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
                    if (skeleton == null) {
                        throw new IllegalStateException("Skelett nicht erzeugt");
                    }
                    skeleton.setNoAi(true);
                    skeleton.snapTo(drone.getX() - 5.0, arena[1], drone.getZ(), -90.0F, 0.0F);
                    level.addFreshEntity(skeleton);
                    SPAWNED.add(skeleton.getId());
                    SKELETON.set(skeleton.getUUID());
                    Arrow arrow = new Arrow(level, skeleton, new ItemStack(Items.ARROW), null);
                    Vec3 aim = drone.getBoundingBox().getCenter().subtract(arrow.position());
                    arrow.setNoGravity(true);
                    arrow.shoot(aim.x, aim.y, aim.z, 1.6F, 0.0F);
                    DAMAGES_BEFORE.set(ExampleDispenser.DAMAGES.get());
                    level.addFreshEntity(arrow);
                    SPAWNED.add(arrow.getId());
                    SHOT.set(arrow.getId());
                    REPORT.set("Zombie: " + cause.substring(0, cause.indexOf(' ')) + " als Verursacher");
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = InteractSelfTests.await(ctx, "Mobs");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                UUID skeleton = SKELETON.get();
                String cause = ExampleDispenser.LAST_DAMAGE_CAUSE.get();
                if (ExampleDispenser.DAMAGES.get() <= DAMAGES_BEFORE.get() || skeleton == null || !cause.contains(skeleton.toString())) {
                    return InteractSelfTests.since(ctx) > 160 ? StepResult.fail("Pfeil des Skeletts ohne DRONE_DAMAGED mit Skelett als Verursacher: " + cause + "; "
                            + mobDiagnosis(ctx.server())) : StepResult.pending();
                }
                ctx.log(REPORT.get() + "; Skelett-Pfeil: " + cause.substring(0, cause.indexOf(' ')) + " als Verursacher");
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase " + InteractSelfTests.phase(ctx));
            }
        }
    }

    private static String mobDiagnosis(@Nullable MinecraftServer server) {
        if (server == null) {
            return "kein Server";
        }
        ServerLevel level = InteractSelfTests.player(server).level();
        Entity arrow = level.getEntity(SHOT.get());
        Entity drone = level.getEntity(PARKED.get());
        return "Pfeil " + (arrow == null ? "weg" : arrow.position() + " Tempo " + arrow.getDeltaMovement()) + ", Drohne "
                + (drone == null ? "weg" : drone.getBoundingBox()) + ", Schäden " + (ExampleDispenser.DAMAGES.get() - DAMAGES_BEFORE.get());
    }

    private static StepResult windStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                state.put(FUTURE, ctx.onServer(server -> startFlight(server, dispenserBuild(), new ItemStack(Items.WIND_CHARGE, 2), null, null)));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                return InteractSelfTests.started(ctx, "Windkugel", null);
            }
            case 2, 3 -> {
                return InteractSelfTests.climb(ctx, "Windkugel");
            }
            case 4 -> {
                InteractSelfTests.hover(session);
                if (!InteractSelfTests.settled(session, ctx)) {
                    return InteractSelfTests.since(ctx) > 300 ? StepResult.fail("schwebt nicht ruhig") : StepResult.pending();
                }
                QuadcopterEntity drone = session.drone();
                if (drone == null || PropwashApi.accessories().contents(drone, AccessorySlot.FRONT).isEmpty()) {
                    return InteractSelfTests.since(ctx) > 400 ? StepResult.fail("Windkugeln beim Client fehlen") : StepResult.pending();
                }
                state.put(COUNT, ExampleDispenser.SHOTS.get());
                state.put(PUSHES, AccessoryClient.pushesApplied());
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
                double forward = forwardSpeed(sim, drone);
                state.put(MIN_FORWARD, Math.min((double) state.get(MIN_FORWARD), forward));
                boolean fired = ExampleDispenser.SHOTS.get() > (int) state.get(COUNT);
                boolean pushed = AccessoryClient.pushesApplied() > (int) state.get(PUSHES);
                if (fired) {
                    AccessoryActionInput.INSTANCE.setTestBits(0);
                }
                if (!fired || !pushed || InteractSelfTests.since(ctx) < 15) {
                    return InteractSelfTests.since(ctx) > 150 ? StepResult.fail("Schuss " + fired + ", Rückstoß beim Client " + pushed) : StepResult.pending();
                }
                DispenseResult result = ExampleDispenser.LAST_RESULT.get();
                double minForward = (double) state.get(MIN_FORWARD);
                if (result.status() != DispenseResult.Status.FIRED || !(result.entity().orElse(null) instanceof WindCharge)) {
                    return StepResult.fail("Windkugel: " + result.status() + " " + result.entity());
                }
                if (minForward > -1.0) {
                    return StepResult.fail(String.format(Locale.ROOT, "Rückstoß der Windkugel zu schwach: %.2f m/s zurück", -minForward));
                }
                ctx.log(String.format(Locale.ROOT, "Windkugel FIRED, Rückstoß %.2f N·s, im Flugmodell %.2f m/s zurück", result.recoilNs(), -minForward));
                AccessoryActionInput.INSTANCE.setTestBits(null);
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

    private static StepResult tntStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                state.put(FUTURE, ctx.onServer(server -> startFlight(server, dispenserBuild(), new ItemStack(Items.TNT), null, null)));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                return InteractSelfTests.started(ctx, "TNT", null);
            }
            case 2, 3 -> {
                return InteractSelfTests.climb(ctx, "TNT");
            }
            case 4 -> {
                InteractSelfTests.hover(session);
                QuadcopterEntity drone = session.drone();
                if (!InteractSelfTests.settled(session, ctx) || drone == null || PropwashApi.accessories().contents(drone, AccessorySlot.FRONT).isEmpty()) {
                    return InteractSelfTests.since(ctx) > 400 ? StepResult.fail("schwebt nicht ruhig oder TNT fehlt beim Client") : StepResult.pending();
                }
                Vec3 f = forward(drone);
                InteractSelfTests.push(session.sim(), f.x * TNT_PUSH, 0.0, f.z * TNT_PUSH);
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                InteractSelfTests.hover(session);
                if (InteractSelfTests.since(ctx) < 6) {
                    return StepResult.pending();
                }
                state.put(COUNT, ExampleDispenser.SHOTS.get());
                AccessoryActionInput.INSTANCE.setTestBits(1);
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 6 -> {
                InteractSelfTests.hover(session);
                if (ExampleDispenser.SHOTS.get() <= (int) state.get(COUNT)) {
                    return InteractSelfTests.since(ctx) > 100 ? StepResult.fail("TNT nicht abgeworfen") : StepResult.pending();
                }
                AccessoryActionInput.INSTANCE.setTestBits(null);
                state.put(FUTURE, ctx.onServer(server -> {
                    DispenseResult result = ExampleDispenser.LAST_RESULT.get();
                    PrimedTnt tnt = expect(result, DispenseResult.Status.PLACED, PrimedTnt.class, "TNT im Flug");
                    Vec3 droneSpeed = ExampleDispenser.LAST_DRONE_VELOCITY.get();
                    Vec3 tntSpeed = ExampleDispenser.LAST_ENTITY_MOTION.get().scale(20.0);
                    tnt.discard();
                    double drone2d = droneSpeed.horizontalDistance();
                    double gap = new Vec3(tntSpeed.x - droneSpeed.x, 0.0, tntSpeed.z - droneSpeed.z).length();
                    if (drone2d < 2.0 || gap > 1.5) {
                        throw new IllegalStateException(String.format(Locale.ROOT, "TNT %.2f m/s, Drohne %.2f m/s, Abstand %.2f m/s", tntSpeed.horizontalDistance(),
                                drone2d, gap));
                    }
                    REPORT.set(String.format(Locale.ROOT, "TNT PLACED im Flug: TNT %.2f m/s waagerecht, Drohne %.2f m/s (Abweichung %.2f m/s)",
                            tntSpeed.horizontalDistance(), drone2d, gap));
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 7 -> {
                InteractSelfTests.hover(session);
                StepResult server = InteractSelfTests.await(ctx, "TNT");
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

    private static StepResult hintStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        String name = Component.translatable(ExampleDispenser.actionKey(ExampleDispenser.DISPENSER, ExampleDispenser.FIRE)).getString();
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                AccessoryHintClient.forgetAll();
                AccessoryHintClient.mute(false);
                state.put(SHOWN, AccessoryHintClient.shownCount());
                state.put(CHAT, chatLines(mc, name));
                state.put(FUTURE, ctx.onServer(server -> startFlight(server, dispenserBuild(), ItemStack.EMPTY, null, null)));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1, 4 -> {
                return InteractSelfTests.started(ctx, "Hinweis", null);
            }
            case 2 -> {
                int shown = AccessoryHintClient.shownCount() - (int) state.get(SHOWN);
                int chat = chatLines(mc, name) - (int) state.get(CHAT);
                if (shown < 1 || chat < 1) {
                    return InteractSelfTests.since(ctx) > 150 ? StepResult.fail("Hinweis beim ersten Flug fehlt: " + shown + " gezählt, " + chat + " Zeilen „" + name + "“")
                            : StepResult.pending();
                }
                if (shown != 1 || chat != 1) {
                    return StepResult.fail("Hinweis beim ersten Flug " + shown + "-mal gezählt, " + chat + " Zeilen „" + name + "“");
                }
                String row = AddonSwitchRows.title(SwitchFunction.ACCESSORY_1, Component.translatable(SwitchFunction.ACCESSORY_1.translationKey())).getString();
                if (!row.contains(name)) {
                    return StepResult.fail("Zubehör-Aktion 1 heißt im Tab „Modi“ „" + row + "“ ohne „" + name + "“");
                }
                REPORT.set("Tab „Modi“: „" + row + "“, Hinweis: " + chat(mc).stream().filter(line -> line.contains(name)).findFirst().orElse(""));
                PilotSession.INSTANCE.clearTestInput();
                PilotSession.INSTANCE.requestStop();
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                if (PilotSession.INSTANCE.isActive()) {
                    return InteractSelfTests.since(ctx) > 150 ? StepResult.fail("erster Flug endet nicht") : StepResult.pending();
                }
                state.put(SHOWN, AccessoryHintClient.shownCount());
                state.put(CHAT, chatLines(mc, name));
                state.put(FUTURE, ctx.onServer(server -> startFlight(server, dispenserBuild(), ItemStack.EMPTY, null, null)));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                if (InteractSelfTests.since(ctx) < AccessoryHintClient.DELAY_TICKS + 40) {
                    return StepResult.pending();
                }
                int shown = AccessoryHintClient.shownCount() - (int) state.get(SHOWN);
                int chat = chatLines(mc, name) - (int) state.get(CHAT);
                if (shown != 0 || chat != 0) {
                    return StepResult.fail("Hinweis beim zweiten Flug noch einmal: " + shown + " gezählt, " + chat + " Zeilen");
                }
                ctx.log(REPORT.get() + "; zweiter Flug ohne Hinweis");
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            default -> {
                return InteractSelfTests.stop(ctx, server -> {
                });
            }
        }
    }

    private static StepResult displayStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                state.put(FUTURE, ctx.onServer(server -> startFlight(server, displayBuild(), ItemStack.EMPTY, new ItemStack(Items.IRON_SPEAR),
                        new ItemStack(Items.FIREWORK_ROCKET))));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                return InteractSelfTests.started(ctx, "Anzeige", null);
            }
            case 2, 3 -> {
                return InteractSelfTests.climb(ctx, "Anzeige");
            }
            case 4 -> {
                InteractSelfTests.hover(session);
                QuadcopterEntity drone = session.drone();
                DroneRenderState shown = drone == null ? null : extract(drone);
                if (!InteractSelfTests.settled(session, ctx) || shown == null || !spearAndRocket(shown, true)) {
                    return InteractSelfTests.since(ctx) > 400 ? StepResult.fail("Speer und Rakete nicht im Render-Zustand: " + describe(shown)) : StepResult.pending();
                }
                REPORT.set("vorher " + describe(shown));
                state.put(BOOSTS, ExampleDispenser.BOOSTS.get());
                state.put(ORIGIN, drone.position());
                AccessoryActionInput.INSTANCE.setTestBits(1 << (ExampleDispenser.BOOST - 1));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                InteractSelfTests.hover(session);
                if (ExampleDispenser.BOOSTS.get() <= (int) state.get(BOOSTS)) {
                    return InteractSelfTests.since(ctx) > 100 ? StepResult.fail("Boost löst nicht aus") : StepResult.pending();
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
                DroneRenderState shown = extract(drone);
                boolean empty = PropwashApi.accessories().contents(drone, AccessorySlot.TOP).stream().allMatch(ItemStack::isEmpty);
                if (!empty || shown == null || !spearAndRocket(shown, false)) {
                    return InteractSelfTests.since(ctx) > 150 ? StepResult.fail("nach dem Boost: Inhalt oben leer " + empty + ", " + describe(shown))
                            : StepResult.pending();
                }
                double moved = drone.position().subtract((Vec3) state.get(ORIGIN)).horizontalDistance();
                ctx.log(REPORT.get() + "; nach dem Boost " + describe(shown) + String.format(Locale.ROOT, ", bis dahin %.2f m geschoben", moved));
                AccessoryActionInput.INSTANCE.setTestBits(null);
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

    static @Nullable DroneRenderState extract(QuadcopterEntity drone) {
        EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(drone, 1.0F);
        return state instanceof DroneRenderState result ? result : null;
    }

    static boolean spearAndRocket(DroneRenderState state, boolean rocket) {
        ItemStack spear = state.accessoryDisplay.shown(AccessoryMount.BOTTOM, 0);
        ItemStack top = state.accessoryDisplay.shown(AccessoryMount.TOP, 0);
        boolean spearShown = spear != null && spear.is(Items.IRON_SPEAR) && state.accessoryDisplay.count(AccessoryMount.BOTTOM) == 1;
        boolean rocketShown = top != null && top.is(Items.FIREWORK_ROCKET) && state.accessoryDisplay.count(AccessoryMount.TOP) == 1;
        boolean topEmpty = state.accessoryDisplay.count(AccessoryMount.TOP) == 0;
        return spearShown && (rocket ? rocketShown : topEmpty) && state.accessoryDisplay.count(AccessoryMount.FRONT) == 0;
    }

    static String describe(@Nullable DroneRenderState state) {
        if (state == null) {
            return "kein Render-Zustand";
        }
        return "unten " + state.accessoryDisplay.count(AccessoryMount.BOTTOM) + " (" + state.accessoryDisplay.shown(AccessoryMount.BOTTOM, 0) + "), oben "
                + state.accessoryDisplay.count(AccessoryMount.TOP) + " (" + state.accessoryDisplay.shown(AccessoryMount.TOP, 0) + "), vorne "
                + state.accessoryDisplay.count(AccessoryMount.FRONT);
    }

    private static StepResult knockbackStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        ExampleDispenser.KnockbackMode[] rounds = {ExampleDispenser.KnockbackMode.OFF, ExampleDispenser.KnockbackMode.SCALE,
                ExampleDispenser.KnockbackMode.CANCEL};
        int round = (int) state.getOrDefault(ROUND, 0);
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                state.put(FUTURE, ctx.onServer(server -> startFlight(server, dispenserBuild(), ItemStack.EMPTY, null, null)));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                return InteractSelfTests.started(ctx, "Stoß", null);
            }
            case 2, 3 -> {
                return InteractSelfTests.climb(ctx, "Stoß");
            }
            case 4 -> {
                InteractSelfTests.hover(session);
                QuadcopterEntity drone = session.drone();
                if (!InteractSelfTests.settled(session, ctx) || drone == null) {
                    return InteractSelfTests.since(ctx) > 400 ? StepResult.fail("schwebt nicht ruhig (Runde " + rounds[round] + ")") : StepResult.pending();
                }
                ExampleDispenser.knockback(rounds[round]);
                InteractSelfTests.push(session.sim(), 0.0, 0.0, 0.0);
                state.put(ORIGIN, drone.position());
                state.put(MAX_SHIFT, 0.0);
                state.put(MAX_SPEED, 0.0);
                state.put(FIRED, Knockback.fired());
                state.put(FORWARDED, Knockback.forwarded());
                state.put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity serverDrone = InteractSelfTests.serverDrone(server);
                    ServerLevel level = (ServerLevel) serverDrone.level();
                    Vec3 at = serverDrone.getBoundingBox().getCenter().subtract(forward(serverDrone).scale(BLAST_DISTANCE));
                    WindCharge source = new WindCharge(level, at.x, at.y, at.z, Vec3.ZERO);
                    level.explode(source, null, AbstractWindCharge.EXPLOSION_DAMAGE_CALCULATOR, at.x, at.y, at.z, 1.2F, false,
                            Level.ExplosionInteraction.TRIGGER);
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                InteractSelfTests.hover(session);
                QuadcopterEntity drone = session.drone();
                if (drone == null) {
                    return StepResult.fail("Sitzung verloren (Runde " + rounds[round] + ")");
                }
                StepResult blast = InteractSelfTests.await(ctx, "Windkugel");
                if (blast.status() == StepResult.Status.FAIL) {
                    return blast;
                }
                double shift = drone.position().subtract((Vec3) state.get(ORIGIN)).horizontalDistance();
                state.put(MAX_SHIFT, Math.max((double) state.get(MAX_SHIFT), shift));
                DroneSim sim = session.sim();
                if (sim != null) {
                    Vector3d v = new Vector3d();
                    sim.getVelocity(v);
                    state.put(MAX_SPEED, Math.max((double) state.get(MAX_SPEED), Math.hypot(v.x, v.z)));
                }
                if (InteractSelfTests.since(ctx) < MEASURE_TICKS) {
                    return StepResult.pending();
                }
                double max = (double) state.get(MAX_SHIFT);
                double speed = (double) state.get(MAX_SPEED);
                int fired = Knockback.fired() - (int) state.get(FIRED);
                int forwarded = Knockback.forwarded() - (int) state.get(FORWARDED);
                boolean expectMove = rounds[round] == ExampleDispenser.KnockbackMode.SCALE;
                if (fired < 1) {
                    return StepResult.fail("DRONE_KNOCKBACK nicht ausgelöst (Runde " + rounds[round] + ", " + ExampleDispenser.LAST_KNOCKBACK.get() + ")");
                }
                if (expectMove ? max <= MOVED || forwarded < 1 : max >= STILL || speed >= QUIET || forwarded != 0) {
                    return StepResult.fail(String.format(Locale.ROOT, "Runde %s: Versatz %.2f m, höchstens %.2f m/s, weitergeleitet %d", rounds[round], max, speed,
                            forwarded));
                }
                String line = String.format(Locale.ROOT, "%s: Versatz %.2f m, höchstens %.2f m/s, weitergeleitet %d (%s)", rounds[round], max, speed, forwarded,
                        ExampleDispenser.LAST_KNOCKBACK.get());
                REPORT.set(round == 0 ? line : REPORT.get() + "; " + line);
                if (round + 1 >= rounds.length) {
                    ExampleDispenser.knockback(ExampleDispenser.KnockbackMode.SCALE);
                    ctx.log(REPORT.get());
                    InteractSelfTests.next(ctx);
                    return StepResult.pending();
                }
                state.put(ROUND, round + 1);
                state.put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity serverDrone = InteractSelfTests.serverDrone(server);
                    int[] arena = InteractSelfTests.ARENA.get();
                    if (arena == null) {
                        throw new IllegalStateException("Arena fehlt");
                    }
                    serverDrone.resetTo(InteractSelfTests.start(arena).add(0.0, InteractSelfTests.HOVER_HEIGHT, 0.0), InteractSelfTests.upright());
                }));
                state.put(InteractSelfTests.PHASE, 3);
                state.put(InteractSelfTests.MARK, ctx.tick());
                return StepResult.pending();
            }
            default -> {
                ExampleDispenser.knockback(ExampleDispenser.KnockbackMode.SCALE);
                return InteractSelfTests.stop(ctx, server -> {
                });
            }
        }
    }

    private static String loaded(MinecraftServer server) {
        ServerPlayer player = InteractSelfTests.player(server);
        int[] arena = InteractSelfTests.arena(player);
        ServerLevel level = player.level();
        Vec3 start = InteractSelfTests.start(arena);
        List<QuadcopterEntity> drones = new ArrayList<>();
        for (int i = -1; i <= 1; i++) {
            drones.add(park(level, DroneBuild.FREESTYLE, start.add(2.0, 0.0, 2.0 * i)));
        }
        List<Entity> loaded = PropwashApi.drones().loaded(level);
        for (QuadcopterEntity drone : drones) {
            if (!loaded.contains(drone)) {
                throw new IllegalStateException("loaded(level) ohne neue Drohne " + drone.getId());
            }
        }
        boolean mutable;
        try {
            loaded.add(player);
            mutable = true;
        } catch (UnsupportedOperationException expected) {
            mutable = false;
        }
        if (mutable) {
            throw new IllegalStateException("loaded(level) ist veränderlich");
        }
        List<? extends QuadcopterEntity> searched = level.getEntities(DroneModule.DRONE.get(), drone -> !drone.isRemoved());
        if (loaded.size() != searched.size() || !loaded.containsAll(searched)) {
            throw new IllegalStateException("loaded(level) " + loaded.size() + " Drohnen, Entity-Suche " + searched.size());
        }
        drones.getFirst().discard();
        List<Entity> after = PropwashApi.drones().loaded(level);
        if (after.contains(drones.getFirst()) || !after.contains(drones.get(1)) || after.size() != loaded.size() - 1) {
            throw new IllegalStateException("nach dem Entfernen: " + after.size() + " statt " + (loaded.size() - 1));
        }
        long nanos = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            PropwashApi.drones().loaded(level);
        }
        double micros = (System.nanoTime() - nanos) / 1000.0 / 1000.0;
        for (QuadcopterEntity drone : drones) {
            drone.discard();
        }
        if (!PropwashApi.drones().loaded(level).stream().noneMatch(drones::contains)) {
            throw new IllegalStateException("entfernte Drohnen bleiben in loaded(level)");
        }
        return String.format(Locale.ROOT, "loaded(level): %d Drohnen wie die Entity-Suche, unveränderlich, entfernte fehlen sofort, %.2f µs je Aufruf",
                loaded.size(), micros);
    }

    private static String camera(MinecraftServer server) {
        ServerPlayer player = InteractSelfTests.player(server);
        int[] arena = InteractSelfTests.arena(player);
        ServerLevel level = player.level();
        Vec3 start = InteractSelfTests.start(arena);
        Enderman enderman = EntityTypes.ENDERMAN.create(level, EntitySpawnReason.COMMAND);
        if (enderman == null) {
            throw new IllegalStateException("Enderman nicht erzeugt");
        }
        enderman.setNoAi(true);
        enderman.snapTo(start.x + ENDERMAN_AHEAD, start.y, start.z, 90.0F, 0.0F);
        level.addFreshEntity(enderman);
        SPAWNED.add(enderman.getId());
        Vec3 head = new Vec3(enderman.getX(), enderman.getEyeY(), enderman.getZ());
        QuadcopterEntity drone = park(level, dispenserBuild(), start.add(0.0, 1.0, 0.0));
        drone.setNoGravity(true);
        try {
            CameraPose pose = PropwashApi.drones().cameraPose(drone).orElseThrow(() -> new IllegalStateException("cameraPose leer"));
            double ahead = head.x - pose.eye().x;
            double wanted = head.y - ahead * pose.look().y / pose.look().x;
            drone.resetTo(drone.position().add(0.0, wanted - pose.eye().y, 0.0), InteractSelfTests.upright());
            pose = PropwashApi.drones().cameraPose(drone).orElseThrow();
            Vec3 toHead = head.subtract(pose.eye());
            double distance = toHead.length();
            double dot = pose.look().dot(toHead.normalize());
            boolean clear = level.clip(new ClipContext(pose.eye(), head, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, drone)).getType()
                    == HitResult.Type.MISS;
            if (!(dot > 1.0 - 0.025 / distance) || !clear) {
                throw new IllegalStateException(String.format(Locale.ROOT, "Blick verfehlt den Kopf: cos %.5f bei %.2f m, frei %s", dot, distance, clear));
            }
            double vfov = BuildAccessImpl.cameraView(drone.build()).verticalFovDeg();
            if (Math.abs(pose.verticalFovDegrees() - vfov) > 1.0E-6 || pose.up().y < 0.5 || Math.abs(pose.look().length() - 1.0) > 1.0E-6) {
                throw new IllegalStateException("Sichtfeld " + pose.verticalFovDegrees() + " statt " + vfov + ", oben " + pose.up());
            }
            double uptilt = Math.toDegrees(Math.asin(pose.look().y));
            drone.resetTo(drone.position(), InteractSelfTests.upright().rotateY((float) Math.toRadians(30.0)));
            CameraPose turned = PropwashApi.drones().cameraPose(drone).orElseThrow();
            Vec3 toHeadTurned = head.subtract(turned.eye());
            if (turned.look().dot(toHeadTurned.normalize()) > 1.0 - 0.025 / toHeadTurned.length()) {
                throw new IllegalStateException("um 30° gedreht schaut die Kamera noch auf den Kopf");
            }
            if (PropwashApi.drones().cameraPose(enderman).isPresent()) {
                throw new IllegalStateException("cameraPose an einem Enderman nicht leer");
            }
            VideoType missing = (VideoType) PartIndex.missing(PartKind.VIDEO, Identifier.fromNamespaceAndPath("propwash_example", "no_camera"));
            DroneBuild build = drone.build();
            drone.setBuild(new DroneBuild(build.frame(), build.stack(), build.motor(), build.prop(), missing, build.battery(), build.accessories()));
            if (PropwashApi.drones().cameraPose(drone).isPresent()) {
                throw new IllegalStateException("cameraPose ohne Kamera nicht leer");
            }
            return String.format(Locale.ROOT, "cameraPose: Uptilt %.1f°, Sichtfeld %.1f° senkrecht, Blick auf den Enderman-Kopf (cos %.5f bei %.2f m, Grenze %.5f), "
                    + "um 30° gedreht vorbei, leer ohne Kamera und an anderen Entities", uptilt, pose.verticalFovDegrees(), dot, distance, 1.0 - 0.025 / distance);
        } finally {
            enderman.discard();
            drone.discard();
        }
    }

    static void startFlight(MinecraftServer server, DroneBuild build, ItemStack ammo, @Nullable ItemStack spear, @Nullable ItemStack rocket) {
        InteractSelfTests.discard(server, InteractSelfTests.DRONE_ID.getAndSet(-1));
        ServerPlayer player = InteractSelfTests.player(server);
        if (player.level().getEntity(PARKED.getAndSet(-1)) instanceof QuadcopterEntity old) {
            old.discard();
        }
        int[] arena = InteractSelfTests.arena(player);
        MODE.compareAndSet(null, player.gameMode());
        ExampleDispenser.consume(true);
        ExampleDispenser.speedScale(1.0);
        InteractSelfTests.fly(player, build, InteractSelfTests.start(arena));
        QuadcopterEntity drone = InteractSelfTests.serverDrone(server);
        if (!ammo.isEmpty()) {
            front(drone).set(0, ammo);
        }
        if (spear != null) {
            PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).orElseThrow(() -> new IllegalStateException("Speer-Halterung ohne Inventar"))
                    .set(0, spear);
        }
        if (rocket != null) {
            PropwashApi.accessories().inventory(drone, AccessorySlot.TOP).orElseThrow(() -> new IllegalStateException("Booster ohne Inventar"))
                    .set(0, rocket);
        }
    }

    private static double forwardSpeed(DroneSim sim, QuadcopterEntity drone) {
        Vector3d v = new Vector3d();
        sim.getVelocity(v);
        Vector3f f = new Quaternionf(drone.orientation()).transform(new Vector3f(0.0F, 0.0F, -1.0F));
        return v.x * f.x + v.z * f.z;
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        AccessoryActionInput.INSTANCE.setTestBits(null);
        ExampleDispenser.consume(true);
        ExampleDispenser.speedScale(1.0);
        ExampleDispenser.knockback(ExampleDispenser.KnockbackMode.SCALE);
        if (PilotSession.INSTANCE.isActive()) {
            return InteractSelfTests.stop(ctx, AccessoryGapsSelfTests::clean);
        }
        if (!ctx.state().containsKey(FUTURE)) {
            ctx.state().put(FUTURE, ctx.onServer(AccessoryGapsSelfTests::clean));
            return StepResult.pending();
        }
        return InteractSelfTests.await(ctx, "Aufräumen");
    }

    private static void clean(MinecraftServer server) {
        InteractSelfTests.discard(server, InteractSelfTests.DRONE_ID.getAndSet(-1));
        for (ServerLevel level : server.getAllLevels()) {
            for (Integer id : SPAWNED) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
        SPAWNED.clear();
        PARKED.set(-1);
        SHOT.set(-1);
        SHOT_ENTITY.set(null);
        ServerPlayer player = InteractSelfTests.player(server);
        GameType mode = MODE.getAndSet(null);
        if (mode != null) {
            player.setGameMode(mode);
        }
        Difficulty difficulty = DIFFICULTY.getAndSet(null);
        if (difficulty != null) {
            server.setDifficulty(difficulty, true);
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

    @FunctionalInterface
    private interface ServerCheck {
        String run(MinecraftServer server);
    }
}
