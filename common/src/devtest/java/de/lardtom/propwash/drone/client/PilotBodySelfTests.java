package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotBodyAlert;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.rescue.FailsafeAction;
import de.lardtom.propwash.rescue.client.RescueConfigs;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class PilotBodySelfTests {
    private static final String PREFIX = "pilotenkörper: ";
    private static final int OFFSET_Z = -40;
    private static final int HALF = 5;
    private static final int CLEAR = 10;
    private static final int DROP_HEIGHT = 6;
    private static final double DRONE_DZ = 4.0;
    private static final double HOVER_HEIGHT = 3.0;
    private static final double HOME_DX = -12.0;
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final AtomicReference<BlockPos> CENTER = new AtomicReference<>();
    private static final AtomicReference<UUID> DRONE = new AtomicReference<>();
    private static final AtomicReference<UUID> HUSK = new AtomicReference<>();
    private static final AtomicReference<Snap> SNAP = new AtomicReference<>();
    private static final AtomicReference<CompletableFuture<Void>> POLL = new AtomicReference<>();
    private static final AtomicReference<String> PROBLEM = new AtomicReference<>();
    private static final Map<BlockPos, BlockState> ORIGINAL = new LinkedHashMap<>();
    private static final Saved SAVED = new Saved();
    private static double startY;
    private static int hits0;
    private static int sounds0;
    private static int flash0;

    private record Snap(float health, float maxHealth, boolean onGround, double x, double y, double z, boolean alive, String lastDamage,
                        boolean huskTargets, boolean hurtByHusk, boolean onFire, int air, boolean dronePresent, boolean droneArmed,
                        boolean dronePiloted, boolean dronePlanned) {
    }

    private static final class Saved {
        boolean taken;
        @Nullable GameType gameMode;
        @Nullable Difficulty difficulty;
        int invulnerableRule;
        boolean keepInventory;
        ItemStack head = ItemStack.EMPTY;
        ItemStack hand = ItemStack.EMPTY;
        double[] pose = new double[5];
        @Nullable FailsafeAction failsafe;
    }

    private PilotBodySelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + "Aufbau – Plattform, Überlebensmodus, Start der FPV-Sitzung mitten in der Luft", 400,
                PilotBodySelfTests::setupStep);
        SelfTest.clientStep(PREFIX + "Körper fällt, landet, nimmt Fallschaden – roter Rand, Ton und Hinweis im FPV", 300,
                PilotBodySelfTests::fallStep);
        SelfTest.clientStep(PREFIX + "Körper steht ohne Eingaben still, Server kennt seine Lage", 120, PilotBodySelfTests::standStep);
        SelfTest.clientStep(PREFIX + "Wüstenzombie nimmt den Piloten ins Visier – Schaden, Rückstoß, Hinweis", 700,
                PilotBodySelfTests::huskStep);
        SelfTest.clientStep(PREFIX + "Lava – Körper brennt und nimmt Schaden", 300, PilotBodySelfTests::lavaStep);
        SelfTest.clientStep(PREFIX + "Ertrinken – Hinweis Luft, dann Schaden", 500, PilotBodySelfTests::drownStep);
        SelfTest.clientStep(PREFIX + "Welt-Regel „Unverwundbare Piloten“ – an: kein Schaden, aus: wieder Schaden", 200,
                PilotBodySelfTests::ruleStep);
        SelfTest.clientStep(PREFIX + "Tod im Flug – Pilot verlässt FPV, Failsafe-Heimflug übernimmt der Server", 900,
                ctx -> deathStep(ctx, FailsafeAction.RETURN_HOME));
        SelfTest.clientStep(PREFIX + "Tod im Flug mit Failsafe „Absinken“ – Drohne entschärft, kein Heimflug", 900,
                ctx -> deathStep(ctx, FailsafeAction.DROP));
        SelfTest.clientStep(PREFIX + "Aufräumen", 300, PilotBodySelfTests::cleanupStep);
    }

    private static ServerPlayer serverPlayer(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static @Nullable QuadcopterEntity serverDrone(ServerLevel level) {
        UUID id = DRONE.get();
        return id != null && level.getEntity(id) instanceof QuadcopterEntity drone && !drone.isRemoved() ? drone : null;
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        ctx.state().remove(FUTURE);
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static @Nullable StepResult server(SelfTest.StepContext ctx, String what, Consumer<MinecraftServer> action) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                action.accept(server);
                SNAP.set(snapshot(server));
            }));
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static void poll(SelfTest.StepContext ctx) {
        CompletableFuture<Void> pending = POLL.get();
        if (pending == null || pending.isDone()) {
            POLL.set(ctx.onServer(server -> SNAP.set(snapshot(server))));
        }
    }

    private static Snap snapshot(MinecraftServer server) {
        ServerPlayer player = serverPlayer(server);
        ServerLevel level = player.level();
        DamageSource last = player.getLastDamageSource();
        String damage = last == null ? "" : last.typeHolder().unwrapKey().map(key -> key.identifier().getPath()).orElse("?");
        Entity husk = HUSK.get() == null ? null : level.getEntity(HUSK.get());
        boolean targets = husk instanceof Mob mob && mob.getTarget() == player;
        boolean hurtByHusk = husk != null && player.getLastHurtByMob() == husk;
        QuadcopterEntity drone = serverDrone(level);
        return new Snap(player.getHealth(), player.getMaxHealth(), player.onGround(), player.getX(), player.getY(), player.getZ(),
                player.isAlive(), damage, targets, hurtByHusk, player.isOnFire(), player.getAirSupply(), drone != null,
                drone != null && drone.isArmed(), drone != null && drone.pilot().isPresent(), drone != null && drone.returnPlan() != null);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState old = level.getBlockState(pos);
        if (old.equals(state)) {
            return;
        }
        ORIGINAL.putIfAbsent(pos.immutable(), old);
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static void heal(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.damageCooldownTime = 0;
    }

    private static void markHints() {
        hits0 = PilotBodyHud.alert().hits();
        sounds0 = PilotBodyHud.hitSounds();
        flash0 = PilotBodyHud.flashFrames();
    }

    private static boolean hinted() {
        return PilotBodyHud.alert().hits() > hits0 && PilotBodyHud.hitSounds() > sounds0 && PilotBodyHud.flashFrames() > flash0;
    }

    private static void teleportBody(ServerPlayer player, BlockPos center, double dy) {
        player.teleportTo(player.level(), center.getX() + 0.5, center.getY() + dy, center.getZ() + 0.5, Set.of(), 180.0F, 0.0F, false);
        player.resetFallDistance();
    }

    private static void startFpv(MinecraftServer server, double dy) {
        ServerPlayer player = serverPlayer(server);
        ServerLevel level = player.level();
        BlockPos center = CENTER.get();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        QuadcopterEntity old = serverDrone(level);
        if (old != null) {
            old.discard();
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5 + DRONE_DZ, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        DRONE.set(drone.getUUID());
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        player.setGameMode(GameType.SURVIVAL);
        heal(player);
        teleportBody(player, center, dy);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static boolean inFpvWithTestDrone() {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        return session.isFpv() && session.sim() != null && drone != null && drone.getUUID().equals(DRONE.get());
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (phase(ctx) == 0) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            PROBLEM.set(null);
            StepResult wait = server(ctx, "Aufbau", server -> {
                ServerPlayer player = serverPlayer(server);
                ServerLevel level = player.level();
                if (!SAVED.taken) {
                    SAVED.taken = true;
                    SAVED.gameMode = player.gameMode();
                    SAVED.difficulty = level.getDifficulty();
                    SAVED.invulnerableRule = WorldRules.get(server, WorldRuleSpecs.INVULNERABLE_PILOTS);
                    SAVED.keepInventory = server.getGameRules().get(GameRules.KEEP_INVENTORY);
                    SAVED.head = player.getItemBySlot(EquipmentSlot.HEAD).copy();
                    SAVED.hand = player.getInventory().getItem(0).copy();
                    SAVED.pose = new double[]{player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()};
                }
                server.setDifficulty(Difficulty.NORMAL, true);
                WorldRules.set(server, WorldRuleSpecs.INVULNERABLE_PILOTS, 0);
                WorldRules.set(server, GameRules.KEEP_INVENTORY, true);
                BlockPos center = new BlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ() + OFFSET_Z);
                CENTER.set(center);
                for (int dx = -HALF; dx <= HALF; dx++) {
                    for (int dz = -HALF; dz <= HALF + 2; dz++) {
                        level.getChunk((center.getX() + dx) >> 4, (center.getZ() + dz) >> 4);
                        set(level, center.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState());
                        for (int dy = 0; dy < CLEAR; dy++) {
                            set(level, center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                startFpv(server, DROP_HEIGHT);
            });
            if (wait != null) {
                return wait;
            }
            markHints();
            next(ctx, 1);
            return StepResult.pending();
        }
        if (!inFpvWithTestDrone() || mc.player == null) {
            return since(ctx) > 200 ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
        }
        startY = mc.player.getY();
        double above = startY - CENTER.get().getY();
        ctx.log(String.format(Locale.ROOT, "FPV gestartet, Körper %.2f Blöcke über der Plattform", above));
        return StepResult.check(above > DROP_HEIGHT - 2.0, String.format(Locale.ROOT,
                "Körper nicht mehr in der Luft, als die Sitzung begann (%.2f über der Plattform)", above));
    }

    private static StepResult fallStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        BlockPos center = CENTER.get();
        if (player == null || center == null) {
            return StepResult.fail("kein Spieler oder kein Testfeld");
        }
        if (!inFpvWithTestDrone()) {
            return StepResult.fail("FPV-Sitzung beim Fallen beendet");
        }
        poll(ctx);
        Snap snap = SNAP.get();
        boolean clientLanded = player.onGround() && Math.abs(player.getY() - center.getY()) < 0.05;
        boolean serverLanded = snap != null && snap.onGround() && Math.abs(snap.y() - center.getY()) < 0.05;
        boolean fallDamage = snap != null && snap.health() < snap.maxHealth() - 0.5F && "fall".equals(snap.lastDamage());
        boolean hint = hinted();
        boolean cleared = ctx.state().containsKey("cleared");
        if (!ctx.state().containsKey("shot")) {
            Screenshots.clearOverlays(mc);
            ctx.state().put("cleared", true);
        }
        if (hint && cleared && Screenshots.enabled() && !ctx.state().containsKey("shot") && PilotBodyHud.flashFrames() - flash0 >= 3) {
            ctx.state().put("shot", Screenshots.capture("pilot_body_hit"));
        }
        CompletableFuture<?> shot = (CompletableFuture<?>) ctx.state().get("shot");
        boolean shotDone = !Screenshots.enabled() || shot != null && shot.isDone();
        if (clientLanded && serverLanded && fallDamage && hint && shotDone) {
            ctx.log(String.format(Locale.ROOT, "aus %.2f Blöcken gefallen, Gesundheit %.1f/%.1f, Treffer-Hinweise %d, Rand-Bilder %d",
                    startY - center.getY(), snap.health(), snap.maxHealth(), PilotBodyHud.alert().hits() - hits0,
                    PilotBodyHud.flashFrames() - flash0));
            return StepResult.pass();
        }
        if (ctx.tick() > 240) {
            return StepResult.fail(String.format(Locale.ROOT,
                    "Client gelandet %s (y %.2f), Server gelandet %s (y %s), Fallschaden %s (%s), Hinweis %s",
                    clientLanded, player.getY() - center.getY(), serverLanded,
                    snap == null ? "?" : String.format(Locale.ROOT, "%.2f", snap.y() - center.getY()), fallDamage,
                    snap == null ? "?" : snap.health() + " " + snap.lastDamage(), hint));
        }
        return StepResult.pending();
    }

    private static StepResult standStep(SelfTest.StepContext ctx) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !inFpvWithTestDrone()) {
            return StepResult.fail("FPV-Sitzung verloren");
        }
        if (ctx.tick() == 0) {
            ctx.state().put("pos", player.position());
            return StepResult.pending();
        }
        poll(ctx);
        if (ctx.tick() < 60) {
            return StepResult.pending();
        }
        Vec3 start = (Vec3) ctx.state().get("pos");
        Vec3 now = player.position();
        Snap snap = SNAP.get();
        if (snap == null) {
            return StepResult.pending();
        }
        double moved = now.distanceTo(start);
        double gap = Math.hypot(Math.hypot(snap.x() - now.x, snap.z() - now.z), snap.y() - now.y);
        ctx.log(String.format(Locale.ROOT, "in 3 s bewegt %.4f, Abstand Client/Server %.4f", moved, gap));
        if (moved > 0.02 || !player.onGround()) {
            return StepResult.fail(String.format(Locale.ROOT, "Körper bewegt sich ohne Eingaben (%.3f Blöcke)", moved));
        }
        return StepResult.check(gap < 0.05, String.format(Locale.ROOT, "Server kennt die Lage des Körpers nicht (%.3f Blöcke daneben)", gap));
    }

    private static StepResult huskStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        BlockPos center = CENTER.get();
        if (player == null || center == null) {
            return StepResult.fail("kein Spieler");
        }
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = server(ctx, "Wüstenzombie", server -> {
                    ServerPlayer target = serverPlayer(server);
                    ServerLevel level = target.level();
                    heal(target);
                    Mob husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
                    if (husk == null) {
                        throw new IllegalStateException("Wüstenzombie nicht erzeugt");
                    }
                    husk.snapTo(center.getX() + 3.5, center.getY(), center.getZ() + 0.5, 90.0F, 0.0F);
                    husk.setPersistenceRequired();
                    level.addFreshEntity(husk);
                    HUSK.set(husk.getUUID());
                });
                if (wait != null) {
                    return wait;
                }
                markHints();
                ctx.state().put("pos", player.position());
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (!inFpvWithTestDrone()) {
                    return StepResult.fail("FPV-Sitzung beim Angriff beendet");
                }
                poll(ctx);
                Snap snap = SNAP.get();
                Vec3 start = (Vec3) ctx.state().get("pos");
                double pushed = Math.hypot(player.getX() - start.x, player.getZ() - start.z);
                ctx.state().merge("pushed", pushed, (a, b) -> Math.max((double) a, (double) b));
                if (snap != null && snap.huskTargets()) {
                    ctx.state().put("targeted", true);
                }
                boolean targeted = ctx.state().containsKey("targeted");
                boolean hurt = snap != null && snap.hurtByHusk() && snap.health() < snap.maxHealth() - 0.5F;
                double maxPushed = (double) ctx.state().get("pushed");
                if (targeted && hurt && hinted() && maxPushed > 0.15) {
                    ctx.log(String.format(Locale.ROOT, "Gesundheit %.1f/%.1f, Körper um %.2f Blöcke weggestoßen", snap.health(),
                            snap.maxHealth(), maxPushed));
                    next(ctx, 2);
                    return StepResult.pending();
                }
                if (since(ctx) > 600) {
                    return StepResult.fail(String.format(Locale.ROOT, "im Visier %s, verletzt %s, Hinweis %s, Rückstoß %.2f", targeted, hurt,
                            hinted(), maxPushed));
                }
                return StepResult.pending();
            }
            default -> {
                StepResult wait = server(ctx, "Wüstenzombie entfernen", server -> {
                    ServerPlayer target = serverPlayer(server);
                    Entity husk = HUSK.get() == null ? null : target.level().getEntity(HUSK.get());
                    if (husk != null) {
                        husk.discard();
                    }
                    HUSK.set(null);
                    heal(target);
                    teleportBody(target, center, 0.0);
                });
                return wait != null ? wait : StepResult.pass();
            }
        }
    }

    private static StepResult lavaStep(SelfTest.StepContext ctx) {
        BlockPos center = CENTER.get();
        if (center == null) {
            return StepResult.fail("kein Testfeld");
        }
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = server(ctx, "Lava", server -> {
                    ServerPlayer player = serverPlayer(server);
                    heal(player);
                    set(player.level(), center.below(), Blocks.LAVA.defaultBlockState());
                });
                if (wait != null) {
                    return wait;
                }
                markHints();
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (!inFpvWithTestDrone()) {
                    return StepResult.fail("FPV-Sitzung in der Lava beendet");
                }
                poll(ctx);
                Snap snap = SNAP.get();
                boolean burned = snap != null && snap.health() < snap.maxHealth() - 0.5F && snap.onFire()
                        && ("lava".equals(snap.lastDamage()) || snap.lastDamage().contains("fire"));
                if (burned && hinted()) {
                    ctx.log(String.format(Locale.ROOT, "Gesundheit %.1f/%.1f nach %d Ticks, Schaden %s", snap.health(), snap.maxHealth(),
                            since(ctx), snap.lastDamage()));
                    next(ctx, 2);
                    return StepResult.pending();
                }
                if (since(ctx) > 200) {
                    return StepResult.fail("kein Lavaschaden: " + snap);
                }
                return StepResult.pending();
            }
            default -> {
                StepResult wait = server(ctx, "Lava entfernen", server -> {
                    ServerPlayer player = serverPlayer(server);
                    player.level().setBlock(center.below(), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
                    teleportBody(player, center, 0.0);
                    heal(player);
                });
                return wait != null ? wait : StepResult.pass();
            }
        }
    }

    private static StepResult drownStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos center = CENTER.get();
        if (center == null) {
            return StepResult.fail("kein Testfeld");
        }
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = server(ctx, "Wassersäule", server -> {
                    ServerPlayer player = serverPlayer(server);
                    ServerLevel level = player.level();
                    for (int dy = 0; dy < 3; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                BlockState state = dx == 0 && dz == 0 ? Blocks.WATER.defaultBlockState() : Blocks.GLASS.defaultBlockState();
                                set(level, center.offset(dx, dy, dz), state);
                            }
                        }
                    }
                    heal(player);
                    player.setAirSupply(30);
                });
                if (wait != null) {
                    return wait;
                }
                markHints();
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (!inFpvWithTestDrone()) {
                    return StepResult.fail("FPV-Sitzung unter Wasser beendet");
                }
                poll(ctx);
                if (PilotBodyHud.lastNotice() == PilotBodyAlert.Notice.AIR) {
                    ctx.state().putIfAbsent("air", ctx.tick());
                }
                Integer airSeen = (Integer) ctx.state().get("air");
                if (!ctx.state().containsKey("shot")) {
                    Screenshots.clearOverlays(mc);
                }
                if (airSeen != null && Screenshots.enabled() && !ctx.state().containsKey("shot") && ctx.tick() - airSeen >= 10) {
                    ctx.state().put("shot", Screenshots.capture("pilot_body_air"));
                }
                CompletableFuture<?> shot = (CompletableFuture<?>) ctx.state().get("shot");
                boolean shotDone = !Screenshots.enabled() || shot != null && shot.isDone();
                Snap snap = SNAP.get();
                if (snap != null && snap.health() < 8.0F) {
                    return StepResult.fail("Körper ertrinkt, ohne dass der Schritt es bemerkt: " + snap);
                }
                boolean drowned = snap != null && "drown".equals(snap.lastDamage()) && snap.health() < snap.maxHealth() - 0.5F;
                if (airSeen != null && drowned && hinted() && shotDone) {
                    ctx.log(String.format(Locale.ROOT, "Hinweis Luft nach %d Ticks, Ertrinkschaden: Gesundheit %.1f/%.1f",
                            airSeen - (int) ctx.state().get(SINCE), snap.health(), snap.maxHealth()));
                    next(ctx, 2);
                    return StepResult.pending();
                }
                if (since(ctx) > 400) {
                    return StepResult.fail("Hinweis Luft " + (airSeen != null) + ", ertrunken " + drowned + ", " + snap);
                }
                return StepResult.pending();
            }
            default -> {
                StepResult wait = server(ctx, "Wasser entfernen", server -> {
                    ServerPlayer player = serverPlayer(server);
                    ServerLevel level = player.level();
                    for (int dy = 0; dy < 3; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                            }
                        }
                    }
                    teleportBody(player, center, 0.0);
                    heal(player);
                });
                return wait != null ? wait : StepResult.pass();
            }
        }
    }

    private static StepResult ruleStep(SelfTest.StepContext ctx) {
        if (!inFpvWithTestDrone()) {
            return StepResult.fail("keine FPV-Sitzung");
        }
        StepResult wait = server(ctx, "Welt-Regel", server -> {
            ServerPlayer player = serverPlayer(server);
            ServerLevel level = player.level();
            List<String> problems = new ArrayList<>();
            heal(player);
            WorldRules.set(server, WorldRuleSpecs.INVULNERABLE_PILOTS, 1);
            float max = player.getMaxHealth();
            for (DamageSource source : List.of(level.damageSources().generic(), level.damageSources().lava(), level.damageSources().drown(),
                    level.damageSources().fall(), level.damageSources().starve())) {
                if (player.hurtServer(level, source, 4.0F)) {
                    problems.add("Regel an, trotzdem Schaden durch " + source.getMsgId());
                }
            }
            if (player.getHealth() < max) {
                problems.add("Regel an, Gesundheit " + player.getHealth());
            }
            WorldRules.set(server, WorldRuleSpecs.INVULNERABLE_PILOTS, 0);
            player.damageCooldownTime = 0;
            if (!player.hurtServer(level, level.damageSources().generic(), 1.0F) || player.getHealth() >= max) {
                problems.add("Regel aus, kein Schaden");
            }
            heal(player);
            PROBLEM.set(problems.isEmpty() ? null : String.join("; ", problems));
        });
        if (wait != null) {
            return wait;
        }
        String problem = PROBLEM.get();
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static StepResult deathStep(SelfTest.StepContext ctx, FailsafeAction action) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        BlockPos center = CENTER.get();
        if (center == null) {
            return StepResult.fail("kein Testfeld");
        }
        switch (phase(ctx)) {
            case 0 -> {
                LocalPlayer self = mc.player;
                if (self != null && self.isDeadOrDying()) {
                    if (!ctx.state().containsKey("respawn")) {
                        ctx.state().put("respawn", true);
                        self.respawn();
                    }
                    return since(ctx) > 200 ? StepResult.fail("kein Wiedereinstieg vor dem Schritt") : StepResult.pending();
                }
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                if (SAVED.failsafe == null) {
                    SAVED.failsafe = RescueConfigs.get().failsafeAction;
                }
                RescueConfigs.get().failsafeAction = action;
                StepResult wait = server(ctx, "Neue Sitzung", server -> startFpv(server, 0.0));
                if (wait != null) {
                    return wait;
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (!inFpvWithTestDrone()) {
                    return since(ctx) > 200 ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
                }
                if (!ctx.state().containsKey("fpvAt")) {
                    ctx.state().put("fpvAt", ctx.tick());
                }
                boolean armSwitch = ctx.tick() - (int) ctx.state().get("fpvAt") >= 10;
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), armSwitch, StabMode.ANGLE);
                DroneSim sim = session.sim();
                if (armSwitch && sim != null && sim.telemetry().armed) {
                    next(ctx, 2);
                } else if (since(ctx) > 160) {
                    return StepResult.fail("nicht scharf: Sperren " + (sim == null ? "?" : sim.telemetry().armBlockers + " "
                            + sim.telemetry().lastDisarmReason));
                }
                return StepResult.pending();
            }
            case 2 -> {
                DroneSim sim = session.sim();
                if (sim == null) {
                    return StepResult.fail("Sitzung beim Abheben verloren");
                }
                Telemetry t = sim.telemetry();
                double height = session.comPosition(new Vector3d()).y - center.getY();
                double hover = Math.clamp(t.hoverThrottleStick, 0.2, 0.6);
                float throttle = (float) Math.clamp(hover + 0.06 * (HOVER_HEIGHT + 0.3 - height) - 0.05 * t.verticalSpeed, 0.05, 0.8);
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
                if (!t.armed) {
                    return StepResult.fail("beim Abheben entschärft: " + t.lastDisarmReason);
                }
                if (height >= HOVER_HEIGHT - 0.5 && Math.abs(t.verticalSpeed) < 0.8 && since(ctx) > 30) {
                    if (mc.level != null) {
                        RescueController.INSTANCE.setHome(new Vec3(center.getX() + 0.5 + HOME_DX, center.getY(), center.getZ() + 0.5),
                                mc.level.dimension(), true);
                    }
                    next(ctx, 3);
                } else if (since(ctx) > 300) {
                    return StepResult.fail(String.format(Locale.ROOT, "steigt nicht auf %.1f m (%.2f)", HOVER_HEIGHT, height));
                }
                return StepResult.pending();
            }
            case 3 -> {
                StepResult wait = server(ctx, "Pilot stirbt", server -> {
                    ServerPlayer player = serverPlayer(server);
                    player.kill(player.level());
                });
                if (wait != null) {
                    return wait;
                }
                ctx.state().put("killed", ctx.tick());
                next(ctx, 4);
                return StepResult.pending();
            }
            case 4 -> {
                poll(ctx);
                if (session.isActive()) {
                    return since(ctx) > 40 ? StepResult.fail("Pilot bleibt nach dem Tod in der Sitzung") : StepResult.pending();
                }
                ctx.state().putIfAbsent("left", since(ctx));
                Snap snap = SNAP.get();
                if (snap == null || snap.alive() || snap.dronePiloted()) {
                    return since(ctx) > 80 ? StepResult.fail("Server gibt die Drohne nicht frei: " + snap) : StepResult.pending();
                }
                if (action == FailsafeAction.RETURN_HOME) {
                    if (!snap.dronePlanned()) {
                        return since(ctx) > 80 ? StepResult.fail("kein Heimflug ohne Pilot nach dem Tod: " + snap) : StepResult.pending();
                    }
                } else {
                    if (since(ctx) < 40) {
                        return StepResult.pending();
                    }
                    if (snap.dronePlanned() || snap.droneArmed()) {
                        return StepResult.fail("Failsafe „Absinken“, aber Drohne " + (snap.dronePlanned() ? "fliegt heim" : "bleibt scharf"));
                    }
                }
                ctx.log(String.format(Locale.ROOT, "FPV nach %d Ticks verlassen, Drohne %s", (int) ctx.state().get("left"),
                        action == FailsafeAction.RETURN_HOME ? "fliegt allein heim" : "entschärft"));
                session.clearTestInput();
                next(ctx, 5);
                return StepResult.pending();
            }
            case 5 -> {
                LocalPlayer player = mc.player;
                if (player != null && player.isDeadOrDying()) {
                    if (!ctx.state().containsKey("respawn")) {
                        ctx.state().put("respawn", true);
                        player.respawn();
                    }
                    return since(ctx) > 200 ? StepResult.fail("kein Wiedereinstieg") : StepResult.pending();
                }
                if (player == null || !player.isAlive()) {
                    return StepResult.pending();
                }
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                next(ctx, 6);
                return StepResult.pending();
            }
            default -> {
                StepResult wait = server(ctx, "Nach dem Tod zurück aufs Testfeld", server -> {
                    ServerPlayer player = serverPlayer(server);
                    QuadcopterEntity drone = serverDrone(player.level());
                    if (drone != null) {
                        drone.discard();
                    }
                    player.setGameMode(GameType.SURVIVAL);
                    heal(player);
                    teleportBody(player, center, 0.0);
                });
                return wait != null ? wait : StepResult.pass();
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (phase(ctx) == 0) {
            PilotSession.INSTANCE.clearTestInput();
            if (SAVED.failsafe != null) {
                RescueConfigs.get().failsafeAction = SAVED.failsafe;
                SAVED.failsafe = null;
            }
            if (mc.player != null && mc.player.isDeadOrDying()) {
                mc.player.respawn();
            }
            StepResult wait = server(ctx, "Aufräumen", server -> {
                ServerPlayer player = serverPlayer(server);
                ServerLevel level = player.level();
                DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
                QuadcopterEntity drone = serverDrone(level);
                if (drone != null) {
                    drone.discard();
                }
                Entity husk = HUSK.get() == null ? null : level.getEntity(HUSK.get());
                if (husk != null) {
                    husk.discard();
                }
                List<Map.Entry<BlockPos, BlockState>> blocks = new ArrayList<>(ORIGINAL.entrySet());
                for (int i = blocks.size() - 1; i >= 0; i--) {
                    level.setBlock(blocks.get(i).getKey(), blocks.get(i).getValue(), Block.UPDATE_CLIENTS);
                }
                ORIGINAL.clear();
                if (SAVED.taken) {
                    WorldRules.set(server, WorldRuleSpecs.INVULNERABLE_PILOTS, SAVED.invulnerableRule);
                    WorldRules.set(server, GameRules.KEEP_INVENTORY, SAVED.keepInventory);
                    if (SAVED.difficulty != null) {
                        server.setDifficulty(SAVED.difficulty, true);
                    }
                    player.setItemSlot(EquipmentSlot.HEAD, SAVED.head);
                    player.getInventory().setItem(0, SAVED.hand);
                    heal(player);
                    if (player.isAlive()) {
                        player.teleportTo(level, SAVED.pose[0], SAVED.pose[1], SAVED.pose[2], Set.of(), (float) SAVED.pose[3],
                                (float) SAVED.pose[4], true);
                    }
                    if (SAVED.gameMode != null) {
                        player.setGameMode(SAVED.gameMode);
                    }
                    SAVED.taken = false;
                }
                DRONE.set(null);
                HUSK.set(null);
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 1);
            return StepResult.pending();
        }
        if (mc.gui.screen() != null && mc.player != null && mc.player.isAlive()) {
            mc.gui.setScreen(null);
        }
        if (PilotSession.INSTANCE.isActive()) {
            return since(ctx) > 150 ? StepResult.fail("Sitzung endet nicht") : StepResult.pending();
        }
        return StepResult.pass();
    }
}
