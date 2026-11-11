package de.lardtom.propwash.rx.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxBlockEntity;
import de.lardtom.propwash.rx.RxConfigC2S;
import de.lardtom.propwash.rx.RxIndex;
import de.lardtom.propwash.rx.RxLinkState;
import de.lardtom.propwash.rx.RxMapping;
import de.lardtom.propwash.rx.RxModule;
import de.lardtom.propwash.rx.RxServer;
import de.lardtom.propwash.rx.RxServerChecks;
import de.lardtom.propwash.rx.RxSettings;
import de.lardtom.propwash.rx.RxSource;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public final class RxSelfTests {
    public static final String GROUP = "rx: ";
    static final int SITE_X = 1400;
    static final int SITE_Z = -1400;
    static final int THROTTLE_AXIS = 2;
    static final int AXES = 8;
    static final int FAR_BLOCKS = 640;
    static final int STABLE_TICKS = 40;
    private static final String RUN = "rx_run";
    static final AtomicReference<BlockPos> SITE = new AtomicReference<>();
    static final AtomicReference<Saved> SAVED = new AtomicReference<>();
    static final AtomicReference<String> SERVER = new AtomicReference<>();
    static final AtomicReference<BlockPos> EDGE = new AtomicReference<>();
    static final AtomicReference<BlockPos> FAR = new AtomicReference<>();
    static final AtomicInteger DRONE = new AtomicInteger(-1);
    static final AtomicInteger COUNT = new AtomicInteger();
    static final AtomicBoolean NEIGHBOR = new AtomicBoolean();
    static final AtomicBoolean LOADED = new AtomicBoolean();
    private static @Nullable SimulatedRadio radio;

    private RxSelfTests() {
    }

    record Saved(Vec3 position, float yaw, float pitch, ItemStack head, ItemStack hand, GameType mode) {
    }

    interface Body {
        StepResult run(SelfTest.StepContext ctx, Run run);
    }

    record Phase(String name, int timeout, Body body) {
    }

    static final class Run {
        int index;
        int since;
        int stable = -1;
        int last = Integer.MIN_VALUE;
        @Nullable CompletableFuture<Void> future;
        final List<String> notes = new ArrayList<>();

        int waited(SelfTest.StepContext ctx) {
            return ctx.tick() - this.since;
        }
    }

    static Phase phase(String name, int timeout, Body body) {
        return new Phase(name, timeout, body);
    }

    static Phase[] fromSite(Phase... phases) {
        Phase[] all = new Phase[phases.length + 2];
        all[0] = phase("Ausgangslage am Testplatz", 400, (ctx, run) -> {
            Minecraft mc = Minecraft.getInstance();
            if (run.waited(ctx) == 0) {
                mc.options.keyShift.setDown(false);
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                radio();
                throttle(1000);
                radio().setMicros(ProfileDefaults.AUX_ARM, 1000);
            }
            return server(ctx, run, RxSelfTests::baseline);
        });
        all[1] = phase("Spieler am Testplatz", 400, (ctx, run) -> {
            Minecraft mc = Minecraft.getInstance();
            boolean ready = mc.player != null && !PilotSession.INSTANCE.isActive() && entity(rx()) != null
                    && mc.player.position().distanceTo(Vec3.atBottomCenterOf(site())) < 0.2 && ContentIds.isRemote(mc.player.getMainHandItem());
            return until(ready && run.waited(ctx) > 10, "Spieler, Funke oder Block fehlt");
        });
        System.arraycopy(phases, 0, all, 2, phases.length);
        return all;
    }

    static void baseline(MinecraftServer server) {
        ServerPlayer player = player(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        if (player.level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone) {
            drone.discard();
        }
        if (SITE.get() == null) {
            prepareSite(server);
            ServerLevel level = player.level();
            level.setBlock(rx(), RxModule.RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(lamp(), Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        }
        ServerLevel level = player.level();
        if (level.getBlockEntity(rx()) instanceof RxBlockEntity entity && !entity.settings().ownedBy(player.getUUID())) {
            entity.apply(new RxSettings(player.getUUID(), RxSource.GAS, -1.0F, 1.0F, 64));
        }
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneModule.REMOTE.get()));
        teleport(player, Vec3.atBottomCenterOf(site()), -90.0F, 20.0F);
    }

    static SelfTest.ClientStep flowFromSite(Phase... phases) {
        return flow(fromSite(phases));
    }

    static SelfTest.ClientStep flow(Phase... phases) {
        return ctx -> {
            Run run = (Run) ctx.state().computeIfAbsent(RUN, key -> {
                Run fresh = new Run();
                fresh.since = ctx.tick();
                return fresh;
            });
            if (run.index >= phases.length) {
                if (!run.notes.isEmpty()) {
                    ctx.log(String.join("; ", run.notes));
                }
                return StepResult.pass();
            }
            Phase phase = phases[run.index];
            StepResult result;
            try {
                result = phase.body().run(ctx, run);
            } catch (RuntimeException e) {
                return StepResult.fail(phase.name() + ": " + e);
            }
            if (result.status() == StepResult.Status.FAIL) {
                return StepResult.fail(phase.name() + ": " + result.message());
            }
            if (result.status() == StepResult.Status.PENDING) {
                return run.waited(ctx) > phase.timeout() ? StepResult.fail(phase.name() + ": nach " + phase.timeout() + " Ticks nicht erreicht"
                        + (result.message().isEmpty() ? "" : " (" + result.message() + ")")) : StepResult.pending();
            }
            run.index++;
            run.since = ctx.tick() + 1;
            run.future = null;
            run.stable = -1;
            run.last = Integer.MIN_VALUE;
            return StepResult.pending();
        };
    }

    static StepResult server(SelfTest.StepContext ctx, Run run, Consumer<MinecraftServer> action) {
        if (run.future == null) {
            SERVER.set(null);
            run.future = ctx.onServer(action);
            return StepResult.pending();
        }
        if (!run.future.isDone()) {
            return StepResult.pending();
        }
        if (run.future.isCompletedExceptionally()) {
            return StepResult.fail(String.valueOf(run.future.exceptionNow()));
        }
        String problem = SERVER.getAndSet(null);
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    static StepResult check(SelfTest.StepContext ctx, Run run, Function<MinecraftServer, @Nullable String> check) {
        return server(ctx, run, server -> SERVER.set(check.apply(server)));
    }

    static StepResult until(boolean condition, String what) {
        return condition ? StepResult.pass() : new StepResult(StepResult.Status.PENDING, what);
    }

    static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler");
        }
        return players.getFirst();
    }

    static BlockPos site() {
        BlockPos site = SITE.get();
        if (site == null) {
            throw new IllegalStateException("Testplatz fehlt");
        }
        return site;
    }

    static BlockPos rx() {
        return site().east(2);
    }

    static BlockPos lamp() {
        return site().east(3);
    }

    static BlockPos foreign() {
        return site().south(2);
    }

    static void teleport(ServerPlayer player, Vec3 feet, float yaw, float pitch) {
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(player.level(), feet.x, feet.y, feet.z, Set.of(), yaw, pitch, true);
    }

    static BlockPos ground(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    static void prepareSite(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        SAVED.compareAndSet(null, new Saved(player.position(), player.getYRot(), player.getXRot(), player.getItemBySlot(EquipmentSlot.HEAD).copy(),
                player.getMainHandItem().copy(), player.gameMode()));
        player.setGameMode(GameType.CREATIVE);
        BlockPos spawn = level.getRespawnData().pos();
        BlockPos site = ground(level, spawn.getX() + SITE_X, spawn.getZ() + SITE_Z);
        SITE.set(site);
        for (int x = -4; x <= 6; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 0; y <= 4; y++) {
                    level.setBlock(site.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                level.setBlock(site.offset(x, -1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        teleport(player, Vec3.atBottomCenterOf(site), -90.0F, 20.0F);
    }

    static void placeByHand(MinecraftServer server) {
        ServerPlayer player = player(server);
        BlockPos support = rx().below();
        ItemStack stack = new ItemStack(RxModule.RX_BLOCK.item().get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(support).add(0.0, 0.5, 0.0);
        InteractionResult result = stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, support,
                false)));
        if (!result.consumesAction() || !(player.level().getBlockState(rx()).getBlock() instanceof RxBlock)) {
            SERVER.set("RX-Block nicht gesetzt: " + result);
            return;
        }
        player.level().setBlock(lamp(), Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneModule.REMOTE.get()));
        if (!RxIndex.of(player.level()).contains(rx())) {
            SERVER.set("Server-Verzeichnis ohne den gesetzten Block");
        }
    }

    static SimulatedRadio radio() {
        SimulatedRadio current = radio;
        if (current == null) {
            current = new SimulatedRadio("propwash-rx-selftest", "Propwash-Testfunke (RX)", ProfileDefaults.radio("Propwash-Testfunke", AXES), AXES, 0);
            for (int axis = 0; axis < AXES; axis++) {
                current.setMicros(axis, axis == THROTTLE_AXIS || axis >= ProfileDefaults.AUX_ARM ? 1000 : 1500);
            }
            radio = current;
        }
        TransmitterInput.INSTANCE.simulate(current);
        return current;
    }

    static void throttle(int micros) {
        radio().setMicros(THROTTLE_AXIS, micros);
    }

    static @Nullable RxBlockEntity entity(BlockPos pos) {
        return RxClient.blockEntity(pos);
    }

    static int power(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        BlockState state = mc.level == null ? Blocks.AIR.defaultBlockState() : mc.level.getBlockState(pos);
        return state.getBlock() instanceof RxBlock ? state.getValue(RxBlock.POWER) : -1;
    }

    static @Nullable RxLinkState link(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        BlockState state = mc.level == null ? Blocks.AIR.defaultBlockState() : mc.level.getBlockState(pos);
        return state.getBlock() instanceof RxBlock ? state.getValue(RxBlock.LINK) : null;
    }

    static boolean lit(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        BlockState state = mc.level == null ? Blocks.AIR.defaultBlockState() : mc.level.getBlockState(pos);
        return state.is(Blocks.REDSTONE_LAMP) && state.getValue(RedstoneLampBlock.LIT);
    }

    static String describe(BlockPos pos) {
        return "Stärke " + power(pos) + ", " + link(pos);
    }

    static void lookAt(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
    }

    static boolean aimsAt(Minecraft mc, BlockPos pos) {
        return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    static void clickUse(Minecraft mc) {
        KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
    }

    static StepResult useOn(Minecraft mc, Run run, SelfTest.StepContext ctx, BlockPos pos) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return StepResult.pending();
        }
        lookAt(player, Vec3.atCenterOf(pos).add(0.0, -0.2, 0.0));
        if (!aimsAt(mc, pos) || run.waited(ctx) < 3) {
            return new StepResult(StepResult.Status.PENDING, "Ziel " + mc.hitResult);
        }
        clickUse(mc);
        return StepResult.pass();
    }

    static StepResult stable(SelfTest.StepContext ctx, Run run, int value, String what) {
        if (value != run.last) {
            run.last = value;
            run.stable = ctx.tick();
            return new StepResult(StepResult.Status.PENDING, what + " " + value);
        }
        return ctx.tick() - run.stable >= STABLE_TICKS ? StepResult.pass() : new StepResult(StepResult.Status.PENDING, what + " " + value);
    }

    static StepResult half(SelfTest.StepContext ctx, Run run, String from) {
        throttle(1500);
        int power = power(rx());
        if (power == RxMapping.CENTRE && link(rx()) == RxLinkState.LINKED && lit(lamp())) {
            StepResult stable = stable(ctx, run, power, "Stärke");
            if (stable.status() == StepResult.Status.PASS) {
                run.notes.add("50 % " + from + ": Stärke " + power);
            }
            return stable;
        }
        return new StepResult(StepResult.Status.PENDING, from + ": " + describe(rx()) + ", Lampe " + lit(lamp()));
    }

    static @Nullable String serverHalf(MinecraftServer server) {
        ServerLevel level = player(server).level();
        int power = level.getBlockState(rx()).getValue(RxBlock.POWER);
        if (power != RxMapping.CENTRE) {
            return "Server: Stärke " + power + " statt " + RxMapping.CENTRE;
        }
        return level.getBlockState(lamp()).getValue(RedstoneLampBlock.LIT) ? null : "Server: Lampe aus";
    }

    static void bindDirect(ServerLevel level, BlockPos pos, UUID owner, int range) {
        if (level.getBlockEntity(pos) instanceof RxBlockEntity entity) {
            entity.apply(new RxSettings(owner, RxSource.GAS, -1.0F, 1.0F, range));
        }
    }

    public static void register() {
        RxModelSelfTests.register();
        SelfTest.clientStep(GROUP + "Block setzen, mit der Test-Funke binden, Gas anlernen und Endpunkte setzen", 1600, flow(
                phase("Testplatz", 400, (ctx, run) -> server(ctx, run, RxSelfTests::prepareSite)),
                phase("Block setzen", 400, (ctx, run) -> server(ctx, run, RxSelfTests::placeByHand)),
                phase("Block beim Client", 400, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    radio();
                    throttle(1000);
                    boolean ready = mc.player != null && entity(rx()) != null && ContentIds.isRemote(mc.player.getMainHandItem())
                            && mc.player.position().distanceTo(Vec3.atBottomCenterOf(site())) < 0.2 && lit(lamp()) == false;
                    return until(ready && run.waited(ctx) > 10, "Block oder Funke fehlt");
                }),
                phase("Rechtsklick mit der Funke", 200, (ctx, run) -> useOn(Minecraft.getInstance(), run, ctx, rx())),
                phase("Fenster offen und gebunden", 300, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    RxBlockEntity entity = entity(rx());
                    boolean bound = mc.player != null && entity != null && entity.settings().ownedBy(mc.player.getUUID());
                    return until(mc.gui.screen() instanceof RxScreen && bound, "Fenster " + mc.gui.screen() + ", " + (entity == null ? "-"
                            : entity.settings()));
                }),
                phase("Gas anlernen", 300, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (!(mc.gui.screen() instanceof RxScreen screen)) {
                        return StepResult.fail("Fenster zu");
                    }
                    int waited = run.waited(ctx);
                    if (waited == 0) {
                        screen.startLearning();
                    }
                    throttle(waited < 6 ? 1000 : waited < 9 ? 1300 : waited < 12 ? 1650 : 2000);
                    RxBlockEntity entity = entity(rx());
                    return until(entity != null && RxSource.GAS.equals(entity.settings().source()) && !screen.learning(),
                            "Quelle " + (entity == null ? "-" : entity.settings().source()));
                }),
                phase("0 % bei 1100 µs übernehmen", 200, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    throttle(1100);
                    if (!(mc.gui.screen() instanceof RxScreen screen)) {
                        return StepResult.fail("Fenster zu");
                    }
                    if (run.waited(ctx) == 8) {
                        screen.takeLow();
                    }
                    RxBlockEntity entity = entity(rx());
                    return until(entity != null && Math.abs(entity.settings().low() + 0.8F) < 0.02F, "0 % " + (entity == null ? "-"
                            : entity.settings().low()));
                }),
                phase("100 % bei 1900 µs übernehmen", 200, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    throttle(1900);
                    if (!(mc.gui.screen() instanceof RxScreen screen)) {
                        return StepResult.fail("Fenster zu");
                    }
                    if (run.waited(ctx) == 8) {
                        screen.takeHigh();
                    }
                    RxBlockEntity entity = entity(rx());
                    if (entity != null && Math.abs(entity.settings().high() - 0.8F) < 0.02F) {
                        run.notes.add("Gas angelernt, Endpunkte " + entity.settings().low() + " / " + entity.settings().high());
                        mc.gui.setScreen(null);
                        return StepResult.pass();
                    }
                    return new StepResult(StepResult.Status.PENDING, "100 % " + (entity == null ? "-" : entity.settings().high()));
                })));
        SelfTest.clientStep(GROUP + "50 % Gas – Stärke 8 von unten und von oben, die Lampe daneben leuchtet", 1200, flowFromSite(
                phase("0 %", 300, (ctx, run) -> {
                    throttle(1000);
                    return until(power(rx()) == 0 && link(rx()) == RxLinkState.LINKED && !lit(lamp()) && run.waited(ctx) > 10, describe(rx()));
                }),
                phase("50 % von 0 % kommend", 300, (ctx, run) -> half(ctx, run, "von unten")),
                phase("Server sieht dasselbe", 200, (ctx, run) -> check(ctx, run, RxSelfTests::serverHalf)),
                phase("100 %", 300, (ctx, run) -> {
                    throttle(2000);
                    return until(power(rx()) == 15 && run.waited(ctx) > 10, describe(rx()));
                }),
                phase("50 % von 100 % kommend", 300, (ctx, run) -> half(ctx, run, "von oben")),
                phase("Server sieht dasselbe von oben", 200, (ctx, run) -> check(ctx, run, RxSelfTests::serverHalf)),
                phase("wieder 100 %", 300, (ctx, run) -> {
                    throttle(2000);
                    if (power(rx()) == 15) {
                        run.notes.add("0 % → 0, 50 % → 8 von unten und von oben mit Lampe, 100 % → 15");
                        return StepResult.pass();
                    }
                    return new StepResult(StepResult.Status.PENDING, describe(rx()));
                })));
        SelfTest.clientStep(GROUP + "Reichweite – außer Reichweite hält der Block, zurück folgt er (auch 140 Blöcke weg)", 2400, flowFromSite(
                phase("Gas 100 % in Reichweite", 300, (ctx, run) -> {
                    throttle(2000);
                    return until(power(rx()) == 15 && link(rx()) == RxLinkState.LINKED, describe(rx()));
                }),
                phase("Reichweite 16", 200, (ctx, run) -> {
                    if (run.waited(ctx) == 0) {
                        ClientNet.sendToServer(RxConfigC2S.range(rx(), 16));
                    }
                    RxBlockEntity entity = entity(rx());
                    return until(entity != null && entity.settings().range() == 16, "Reichweite " + (entity == null ? "-" : entity.settings().range()));
                }),
                phase("30 Blöcke weg", 200, (ctx, run) -> server(ctx, run, server -> teleport(player(server), Vec3.atBottomCenterOf(site().west(30)),
                        -90.0F, 0.0F))),
                phase("Verbindung verloren", 300, (ctx, run) -> until(link(rx()) == RxLinkState.LOST, describe(rx()))),
                phase("Gas 0 % außer Reichweite", 200, (ctx, run) -> {
                    throttle(1000);
                    if (run.waited(ctx) < 40) {
                        return new StepResult(StepResult.Status.PENDING, describe(rx()));
                    }
                    return power(rx()) == 15 && link(rx()) == RxLinkState.LOST ? StepResult.pass() : StepResult.fail("hält nicht: " + describe(rx()));
                }),
                phase("zurück", 200, (ctx, run) -> server(ctx, run, server -> teleport(player(server), Vec3.atBottomCenterOf(site()), -90.0F, 20.0F))),
                phase("folgt wieder", 300, (ctx, run) -> until(power(rx()) == 0 && link(rx()) == RxLinkState.LINKED, describe(rx()))),
                phase("Reichweite 128", 200, (ctx, run) -> {
                    if (run.waited(ctx) == 0) {
                        ClientNet.sendToServer(RxConfigC2S.range(rx(), 128));
                    }
                    RxBlockEntity entity = entity(rx());
                    return until(entity != null && entity.settings().range() == 128, "Reichweite " + (entity == null ? "-" : entity.settings().range()));
                }),
                phase("140 Blöcke weg", 200, (ctx, run) -> server(ctx, run, server -> teleport(player(server), Vec3.atBottomCenterOf(site().west(140)),
                        -90.0F, 0.0F))),
                phase("weit weg: außer Reichweite oder ohne Block-Ticks", 400, (ctx, run) -> {
                    StepResult away = check(ctx, run, server -> {
                        ServerLevel level = player(server).level();
                        if (!RxServer.active(level, rx())) {
                            return null;
                        }
                        return level.getBlockState(rx()).getValue(RxBlock.LINK) == RxLinkState.LOST ? null : "warte";
                    });
                    if (away.status() == StepResult.Status.FAIL && away.message().equals("warte")) {
                        run.future = null;
                        return new StepResult(StepResult.Status.PENDING, "noch in Reichweite");
                    }
                    return away;
                }),
                phase("Gas 100 % weit weg", 300, (ctx, run) -> {
                    throttle(2000);
                    return until(run.waited(ctx) > 80, "warten");
                }),
                phase("Server: Block hält 0", 200, (ctx, run) -> check(ctx, run, server -> {
                    ServerLevel level = player(server).level();
                    LevelChunk chunk = level.getChunkSource().getChunkNow(rx().getX() >> 4, rx().getZ() >> 4);
                    if (chunk == null) {
                        return null;
                    }
                    BlockState state = chunk.getBlockState(rx());
                    return state.getBlock() instanceof RxBlock && state.getValue(RxBlock.POWER) == 0 ? null : "140 Blöcke weg geändert: " + state;
                })),
                phase("zurück", 200, (ctx, run) -> server(ctx, run, server -> teleport(player(server), Vec3.atBottomCenterOf(site()), -90.0F, 20.0F))),
                phase("folgt wieder mit 100 %", 400, (ctx, run) -> {
                    if (power(rx()) == 15 && link(rx()) == RxLinkState.LINKED) {
                        run.notes.add("30 Blöcke bei Reichweite 16: hält 15, zurück folgt; 140 Blöcke: hält 0, zurück folgt mit 15");
                        return StepResult.pass();
                    }
                    return new StepResult(StepResult.Status.PENDING, describe(rx()));
                })));
        SelfTest.clientStep(GROUP + "Block in einem Chunk, den der Test entlädt – Zahl der geladenen Chunks und Zustand bleiben", 3000, flowFromSite(
                phase("Chunk laden", 200, (ctx, run) -> server(ctx, run, server -> {
                    ServerLevel level = player(server).level();
                    BlockPos far = ground(level, site().getX(), site().getZ() + FAR_BLOCKS);
                    FAR.set(far);
                    level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
                })),
                phase("Block setzen", 600, (ctx, run) -> {
                    StepResult placed = check(ctx, run, server -> {
                        ServerLevel level = player(server).level();
                        BlockPos far = FAR.get();
                        if (!RxServer.active(level, far)) {
                            return "noch nicht aktiv";
                        }
                        level.setBlock(far, RxModule.RX_BLOCK.block().get().defaultBlockState().setValue(RxBlock.POWER, 5)
                                .setValue(RxBlock.LINK, RxLinkState.LINKED), Block.UPDATE_ALL);
                        bindDirect(level, far, player(server).getUUID(), 128);
                        level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
                        return null;
                    });
                    if (placed.status() == StepResult.Status.FAIL && placed.message().contains("noch nicht aktiv")) {
                        run.future = null;
                        return StepResult.pending();
                    }
                    return placed;
                }),
                phase("Chunk entladen", 600, (ctx, run) -> {
                    StepResult unloaded = check(ctx, run, server -> {
                        ServerLevel level = player(server).level();
                        BlockPos far = FAR.get();
                        COUNT.set(level.getChunkSource().getLoadedChunksCount());
                        return level.getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4) ? "noch geladen" : null;
                    });
                    if (unloaded.status() == StepResult.Status.FAIL && unloaded.message().contains("noch geladen")) {
                        run.future = null;
                        return StepResult.pending();
                    }
                    return unloaded;
                }),
                phase("Zahl der Chunks ruhig", 1200, (ctx, run) -> {
                    StepResult counted = server(ctx, run, server -> COUNT.set(player(server).level().getChunkSource().getLoadedChunksCount()));
                    if (counted.status() != StepResult.Status.PASS) {
                        return counted;
                    }
                    run.future = null;
                    return stable(ctx, run, COUNT.get(), "Chunks");
                }),
                phase("Gas bewegen", 400, (ctx, run) -> {
                    int waited = run.waited(ctx);
                    if (waited == 0) {
                        LOADED.set(false);
                        run.notes.add(COUNT.get() + " Chunks geladen");
                    }
                    throttle(waited % 40 < 20 ? 2000 : 1000);
                    StepResult watched = server(ctx, run, server -> {
                        ServerLevel level = player(server).level();
                        BlockPos far = FAR.get();
                        if (level.getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4)) {
                            LOADED.set(true);
                        }
                        if (level.getChunkSource().getLoadedChunksCount() != COUNT.get()) {
                            SERVER.set("Chunks " + COUNT.get() + " → " + level.getChunkSource().getLoadedChunksCount());
                        }
                    });
                    if (watched.status() == StepResult.Status.FAIL) {
                        return watched;
                    }
                    if (watched.status() == StepResult.Status.PASS) {
                        run.future = null;
                    }
                    if (LOADED.get()) {
                        return StepResult.fail("Chunk des entladenen Blocks wurde geladen");
                    }
                    return until(waited > 120, "Gas läuft");
                }),
                phase("Zustand nach dem Wiederladen", 800, (ctx, run) -> {
                    StepResult read = check(ctx, run, server -> {
                        ServerLevel level = player(server).level();
                        BlockPos far = FAR.get();
                        level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
                        if (!RxServer.active(level, far)) {
                            return "noch nicht aktiv";
                        }
                        BlockState state = level.getBlockState(far);
                        level.setBlock(far, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
                        return state.getBlock() instanceof RxBlock && state.getValue(RxBlock.POWER) == 5 ? null : "Zustand geändert: " + state;
                    });
                    if (read.status() == StepResult.Status.FAIL && read.message().contains("noch nicht aktiv")) {
                        run.future = null;
                        return StepResult.pending();
                    }
                    if (read.status() == StepResult.Status.PASS) {
                        run.notes.add("Chunk blieb entladen, Zahl gleich, Stärke 5 gehalten");
                    }
                    return read;
                })));
        SelfTest.clientStep(GROUP + "Block am Rand des geladenen Bereichs – Gas ändert nichts, kein Nachbar-Chunk wird geladen, Pilot kommt näher und "
                + "der Block folgt", 3600, flowFromSite(
                phase("Zahl der Chunks ruhig", 1200, (ctx, run) -> {
                    StepResult counted = server(ctx, run, server -> COUNT.set(player(server).level().getChunkSource().getLoadedChunksCount()));
                    if (counted.status() != StepResult.Status.PASS) {
                        return counted;
                    }
                    run.future = null;
                    return stable(ctx, run, COUNT.get(), "Chunks");
                }),
                phase("Randchunk suchen und Block setzen", 200, (ctx, run) -> check(ctx, run, RxSelfTests::placeAtEdge)),
                phase("Gas bewegen", 400, (ctx, run) -> {
                    int waited = run.waited(ctx);
                    throttle(waited % 40 < 20 ? 1000 : 2000);
                    StepResult watched = server(ctx, run, server -> SERVER.set(edgeProblem(server)));
                    if (watched.status() == StepResult.Status.FAIL) {
                        return watched;
                    }
                    if (watched.status() == StepResult.Status.PASS) {
                        run.future = null;
                    }
                    if (waited > 120) {
                        throttle(2000);
                        return StepResult.pass();
                    }
                    return new StepResult(StepResult.Status.PENDING, "Gas läuft");
                }),
                phase("Zustand bleibt", 200, (ctx, run) -> check(ctx, run, server -> {
                    String problem = edgeProblem(server);
                    if (problem != null) {
                        return problem;
                    }
                    ServerLevel level = player(server).level();
                    LevelChunk chunk = level.getChunkSource().getChunkNow(EDGE.get().getX() >> 4, EDGE.get().getZ() >> 4);
                    BlockState state = chunk == null ? null : chunk.getBlockState(EDGE.get());
                    if (state == null || !(state.getBlock() instanceof RxBlock) || state.getValue(RxBlock.POWER) != 3
                            || state.getValue(RxBlock.LINK) != RxLinkState.LINKED) {
                        return "Randblock verändert: " + state;
                    }
                    return null;
                })),
                phase("Pilot kommt näher", 200, (ctx, run) -> server(ctx, run, server -> {
                    ServerPlayer player = player(server);
                    BlockPos edge = EDGE.get();
                    run.notes.add("Randchunk " + (edge.getX() >> 4) + "," + (edge.getZ() >> 4) + " geladen ohne Block-Ticks, Block bei x=" + edge.getX()
                            + ", " + COUNT.get() + " Chunks, Nachbar " + (NEIGHBOR.get() ? "geladen" : "nicht geladen") + " – unverändert");
                    teleport(player, Vec3.atBottomCenterOf(ground(player.level(), edge.getX() - 30, edge.getZ())), -90.0F, 0.0F);
                })),
                phase("Block folgt", 600, (ctx, run) -> {
                    throttle(2000);
                    StepResult followed = check(ctx, run, server -> {
                        ServerLevel level = player(server).level();
                        BlockPos edge = EDGE.get();
                        if (!RxServer.active(level, edge)) {
                            return "warte";
                        }
                        BlockState state = level.getBlockState(edge);
                        return state.getValue(RxBlock.POWER) == 15 && state.getValue(RxBlock.LINK) == RxLinkState.LINKED ? null : "warte";
                    });
                    if (followed.status() == StepResult.Status.FAIL && followed.message().equals("warte")) {
                        run.future = null;
                        return new StepResult(StepResult.Status.PENDING, "Randblock noch nicht nachgezogen");
                    }
                    if (followed.status() == StepResult.Status.PASS) {
                        run.notes.add("nach dem Näherkommen Stärke 15");
                    }
                    return followed;
                }),
                phase("aufräumen", 200, (ctx, run) -> server(ctx, run, server -> {
                    ServerPlayer player = player(server);
                    BlockPos edge = EDGE.get();
                    if (edge != null && RxServer.active(player.level(), edge)) {
                        player.level().setBlock(edge, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                    teleport(player, Vec3.atBottomCenterOf(site()), -90.0F, 20.0F);
                }))));
        SelfTest.clientStep(GROUP + "FPV-Sitzung mit Gas – Drohne und Block folgen derselben Funke", 2400, flowFromSite(
                phase("Drohne und FPV-Start", 300, (ctx, run) -> {
                    radio();
                    throttle(1000);
                    radio().setMicros(ProfileDefaults.AUX_ARM, 1000);
                    return server(ctx, run, RxSelfTests::startFpv);
                }),
                phase("FPV aktiv", 300, (ctx, run) -> until(PilotSession.INSTANCE.isFpv() && run.waited(ctx) > 10, "keine FPV-Sitzung")),
                phase("scharf schalten", 300, (ctx, run) -> {
                    throttle(1000);
                    radio().setMicros(ProfileDefaults.AUX_ARM, run.waited(ctx) < 10 ? 1000 : 2000);
                    return until(PilotSession.INSTANCE.armed(), "nicht scharf");
                }),
                phase("Gas 80 %", 400, (ctx, run) -> {
                    QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                    if (drone == null) {
                        return StepResult.fail("Drohne weg");
                    }
                    if (run.waited(ctx) == 0) {
                        run.notes.add(String.format(java.util.Locale.ROOT, "Start y %.2f", drone.getY()));
                        ctx.state().put("rx_y0", drone.getY());
                    }
                    throttle(1800);
                    double rise = drone.getY() - (double) ctx.state().get("rx_y0");
                    int power = power(rx());
                    if (rise > 1.0 && power >= 12 && link(rx()) == RxLinkState.LINKED) {
                        run.notes.add(String.format(java.util.Locale.ROOT, "Drohne %.1f m gestiegen, Block Stärke %d", rise, power));
                        return StepResult.pass();
                    }
                    return new StepResult(StepResult.Status.PENDING, String.format(java.util.Locale.ROOT, "Anstieg %.2f, %s", rise, describe(rx())));
                }),
                phase("landen und beenden", 400, (ctx, run) -> {
                    throttle(1000);
                    radio().setMicros(ProfileDefaults.AUX_ARM, 1000);
                    if (run.waited(ctx) == 10) {
                        ctx.onServer(server -> DronePiloting.pilotedBy(player(server).getUUID()).ifPresent(DronePiloting::release));
                    }
                    return until(!PilotSession.INSTANCE.isActive() && run.waited(ctx) > 12, "Sitzung läuft");
                }),
                phase("Drohne entfernen", 200, (ctx, run) -> server(ctx, run, server -> {
                    if (player(server).level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone) {
                        drone.discard();
                    }
                    player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneModule.REMOTE.get()));
                }))));
        SelfTest.clientStep(GROUP + "Fenster bei 320 × 240 – alle Bedienelemente erreichbar, nichts überlappt", 1200, flowFromSite(
                phase("Fenster 640 × 480, Skala 2", 200, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    Window window = mc.getWindow();
                    if (run.waited(ctx) == 0) {
                        ctx.state().put("rx_window", new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()});
                        float density = Math.max(1.0F, window.getPixelDensity());
                        window.setWindowed(Math.round(640 / density), Math.round(480 / density));
                    }
                    mc.options.guiScale().set(2);
                    mc.resizeGui();
                    return until(window.getGuiScaledWidth() == 320 && window.getGuiScaledHeight() == 240, "GUI " + window.getGuiScaledWidth() + "x"
                            + window.getGuiScaledHeight());
                }),
                phase("Fenster öffnen", 100, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (run.waited(ctx) == 0) {
                        mc.gui.setScreen(new RxScreen(rx(), false));
                    }
                    return until(mc.gui.screen() instanceof RxScreen && run.waited(ctx) > 3, "Fenster " + mc.gui.screen());
                }),
                phase("Bedienelemente prüfen", 40, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (!(mc.gui.screen() instanceof RxScreen screen)) {
                        return StepResult.fail("Fenster zu");
                    }
                    String problem = layoutProblem(screen);
                    if (problem != null) {
                        return StepResult.fail(problem);
                    }
                    run.notes.add("GUI " + screen.width + "x" + screen.height + ": " + screen.contentWidgets().size() + " Bedienelemente im Inhalt, "
                            + "Bildlauf " + (screen.contentScroll() != null && screen.contentScroll().scrollable() ? "ja" : "nein"));
                    mc.gui.setScreen(null);
                    return StepResult.pass();
                }),
                phase("Fenster zurück", 200, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    int[] saved = (int[]) ctx.state().get("rx_window");
                    if (saved != null && run.waited(ctx) == 0) {
                        mc.options.guiScale().set(saved[2]);
                        mc.getWindow().setWindowed(saved[0], saved[1]);
                        mc.resizeGui();
                    }
                    return until(run.waited(ctx) > 5, "warten");
                })));
        SelfTest.clientStep(GROUP + "Fremder Block – als Operator Fenster ohne Übernahme, Schleichen + Rechtsklick löst die eigene Bindung nach "
                + "Bestätigung", 1500, flowFromSite(
                phase("fremden Block setzen", 200, (ctx, run) -> server(ctx, run, server -> {
                    ServerLevel level = player(server).level();
                    level.setBlock(foreign(), RxModule.RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
                    bindDirect(level, foreign(), RxServerChecks.STRANGER, 64);
                })),
                phase("fremder Block beim Client", 200, (ctx, run) -> {
                    RxBlockEntity entity = entity(foreign());
                    return until(entity != null && entity.settings().ownedBy(RxServerChecks.STRANGER) && run.waited(ctx) > 5, "fremder Block fehlt");
                }),
                phase("Rechtsklick auf den fremden Block", 200, (ctx, run) -> useOn(Minecraft.getInstance(), run, ctx, foreign())),
                phase("Operator sieht das Fenster, Besitzer bleibt", 100, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    boolean operator = mc.player != null && RxClient.operator(mc.player);
                    if (run.waited(ctx) < 20) {
                        return StepResult.pending();
                    }
                    boolean window = mc.gui.screen() instanceof RxScreen screen && screen.pos().equals(foreign());
                    if (window != operator) {
                        return StepResult.fail(operator ? "kein Fenster für den Operator" : "Fenster für fremden Block ohne Recht");
                    }
                    mc.gui.setScreen(null);
                    RxBlockEntity entity = entity(foreign());
                    if (entity == null || !entity.settings().ownedBy(RxServerChecks.STRANGER)) {
                        return StepResult.fail("fremder Block übernommen: " + (entity == null ? "-" : entity.settings()));
                    }
                    run.notes.add("fremder Block: " + (operator ? "Operator sieht das Fenster" : "kein Fenster") + ", Besitzer bleibt");
                    return StepResult.pass();
                }),
                phase("Schleichen + Rechtsklick auf den eigenen Block", 200, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.options.keyShift.setDown(true);
                    if (mc.player == null || !mc.player.isShiftKeyDown()) {
                        return new StepResult(StepResult.Status.PENDING, "schleicht nicht");
                    }
                    return useOn(mc, run, ctx, rx());
                }),
                phase("Bestätigung im Fenster", 200, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.options.keyShift.setDown(false);
                    if (mc.gui.screen() instanceof RxScreen screen && screen.confirming()) {
                        RxBlockEntity entity = entity(rx());
                        if (entity == null || !entity.settings().bound()) {
                            return StepResult.fail("Bindung ohne Bestätigung gelöst");
                        }
                        screen.confirmUnbind();
                        return StepResult.pass();
                    }
                    return new StepResult(StepResult.Status.PENDING, "Fenster " + mc.gui.screen());
                }),
                phase("Bindung gelöst", 300, (ctx, run) -> {
                    RxBlockEntity entity = entity(rx());
                    boolean unbound = entity != null && !entity.settings().bound() && power(rx()) == 0 && link(rx()) == RxLinkState.NONE;
                    if (unbound) {
                        run.notes.add("eigener Block: Bestätigung, dann ungebunden mit Stärke 0");
                    }
                    return until(unbound, entity == null ? "-" : entity.settings() + ", " + describe(rx()));
                })));
        RxOutputSelfTests.register();
        CreativeRxSelfTests.register();
        SelfTest.clientStep(GROUP + "Aufräumen", 600, flow(phase("aufräumen", 400, (ctx, run) -> {
            Minecraft mc = Minecraft.getInstance();
            if (run.waited(ctx) == 0) {
                mc.options.keyShift.setDown(false);
                if (mc.gui.screen() instanceof RxScreen) {
                    mc.gui.setScreen(null);
                }
                TransmitterInput.INSTANCE.simulate(null);
                radio = null;
            }
            return server(ctx, run, RxSelfTests::tearDown);
        })));
        RxShowcase.register();
    }

    static void startFpv(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        if (level.getEntity(DRONE.get()) instanceof QuadcopterEntity old) {
            old.discard();
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 at = Vec3.atBottomCenterOf(site().west(3)).add(0.0, 0.05, 0.0);
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.RACER);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht in die Welt gesetzt");
        }
        drone.resetTo(at, new Quaternionf());
        DRONE.set(drone.getId());
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            SERVER.set("FPV-Start verweigert: " + result);
        }
    }

    static @Nullable String placeAtEdge(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        ChunkPos center = player.chunkPosition();
        int k = 0;
        while (k < 48 && level.getChunkSource().hasChunk(center.x() + k + 1, center.z())) {
            k++;
        }
        int edgeX = center.x() + k;
        if (k == 0) {
            return "kein geladener Chunk östlich des Spielers";
        }
        if (level.shouldTickBlocksAt(ChunkPos.pack(edgeX, center.z()))) {
            return "äußerster geladener Chunk " + edgeX + "," + center.z() + " tickt Blöcke";
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(edgeX, center.z());
        if (chunk == null) {
            return "Randchunk nicht vollständig geladen";
        }
        int x = edgeX * 16 + 15;
        int z = center.z() * 16 + 8;
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
        BlockPos pos = new BlockPos(x, y, z);
        NEIGHBOR.set(level.getChunkSource().hasChunk(edgeX + 1, center.z()));
        COUNT.set(level.getChunkSource().getLoadedChunksCount());
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        level.setBlock(pos, RxModule.RX_BLOCK.block().get().defaultBlockState(), flags);
        if (!(level.getBlockEntity(pos) instanceof RxBlockEntity entity)) {
            return "Randblock ohne Block-Entity";
        }
        entity.apply(new RxSettings(player.getUUID(), RxSource.GAS, -1.0F, 1.0F, 128));
        level.setBlock(pos, level.getBlockState(pos).setValue(RxBlock.POWER, 3).setValue(RxBlock.LINK, RxLinkState.LINKED), flags);
        EDGE.set(pos);
        String problem = edgeProblem(server);
        return problem == null && !RxIndex.of(level).contains(pos) ? "Randblock nicht im Verzeichnis" : problem;
    }

    static @Nullable String edgeProblem(MinecraftServer server) {
        ServerLevel level = player(server).level();
        BlockPos edge = EDGE.get();
        if (edge == null) {
            return "kein Randblock";
        }
        int cx = edge.getX() >> 4;
        int cz = edge.getZ() >> 4;
        if (level.shouldTickBlocksAt(edge)) {
            return "Randchunk tickt inzwischen Blöcke";
        }
        if (level.getChunkSource().hasChunk(cx + 1, cz) != NEIGHBOR.get()) {
            return "Nachbar-Chunk " + (cx + 1) + "," + cz + " " + (NEIGHBOR.get() ? "entladen" : "geladen");
        }
        int count = level.getChunkSource().getLoadedChunksCount();
        return count == COUNT.get() ? null : "Chunks " + COUNT.get() + " → " + count;
    }

    static @Nullable String layoutProblem(RxScreen screen) {
        List<AbstractWidget> widgets = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible) {
                widgets.add(widget);
            }
        }
        ScreenRectangle bounds = new ScreenRectangle(0, 0, screen.width, screen.height);
        var scroll = screen.contentScroll();
        for (AbstractWidget widget : widgets) {
            if (widget.getX() < 0 || widget.getX() + widget.getWidth() > screen.width) {
                return "Bedienelement außerhalb der Breite: " + widget.getMessage().getString();
            }
            boolean content = screen.contentWidgets().contains(widget);
            if (!content && (widget.getY() < 0 || widget.getY() + widget.getHeight() > screen.height)) {
                return "Bedienelement außerhalb der Höhe: " + widget.getMessage().getString();
            }
            if (content && scroll != null && widget.getY() + widget.getHeight() > scroll.view().top() + scroll.contentHeight() + 2) {
                return "Bedienelement unter dem Inhalt: " + widget.getMessage().getString();
            }
        }
        List<ScreenRectangle> shown = new ArrayList<>();
        for (AbstractWidget widget : widgets) {
            ScreenRectangle rect = widget.getRectangle();
            if (scroll != null && screen.contentWidgets().contains(widget)) {
                rect = rect.intersection(scroll.view());
            }
            shown.add(rect);
        }
        for (int i = 0; i < widgets.size(); i++) {
            for (int j = i + 1; j < widgets.size(); j++) {
                ScreenRectangle a = shown.get(i);
                ScreenRectangle b = shown.get(j);
                if (a != null && b != null && a.intersects(b)) {
                    return "überlappt: " + widgets.get(i).getMessage().getString() + " und " + widgets.get(j).getMessage().getString();
                }
            }
        }
        return bounds.width() >= 320 ? null : "GUI zu schmal: " + bounds.width();
    }

    static void tearDown(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        BlockPos site = SITE.get();
        if (site != null) {
            for (BlockPos pos : List.of(rx(), lamp(), foreign())) {
                if (RxServer.active(level, pos)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        if (level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone) {
            drone.discard();
        }
        Saved saved = SAVED.getAndSet(null);
        if (saved != null) {
            player.setItemSlot(EquipmentSlot.HEAD, saved.head());
            player.setItemInHand(InteractionHand.MAIN_HAND, saved.hand());
            player.setGameMode(saved.mode());
            teleport(player, saved.position(), saved.yaw(), saved.pitch());
        }
    }

    public static RxScreen.View sampleView() {
        Minecraft mc = Minecraft.getInstance();
        UUID owner = mc.player == null ? RxServerChecks.STRANGER : mc.player.getUUID();
        String name = mc.player == null ? "Pilot" : mc.player.getPlainTextName();
        return new RxScreen.View(new RxSettings(owner, RxSource.GAS, -0.8F, 0.8F, 64), name, 11, RxLinkState.LINKED);
    }

    public static RxScreen previewScreen() {
        return RxScreen.preview(sampleView(), false);
    }

    public static RxScreen confirmScreen() {
        return RxScreen.preview(sampleView(), true);
    }
}
