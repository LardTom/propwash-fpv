package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.SignalJoinCheck;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.relay.RelayModule;
import de.lardtom.propwash.server.ViewAnchors;
import de.lardtom.propwash.server.client.ClientServerRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.server.client.ServerRulesTab;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import de.lardtom.propwash.video.link.ObstructionScan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CovSignalMemorySelfTests {
    public static final String GROUP = "signal: ";
    public static final String RANGE_STEP = GROUP + "Reichweite über offenem Gelände – FPV 350 Blöcke weg ohne Failsafe, Sicht angeheftet";
    public static final String REPEATER_STEP = GROUP + "Repeater-Strecke hinter gemerktem Gelände, unendliche Reichweite ohne Abschattung";
    public static final String STUTTER_STEP = GROUP + "Ruckler – gemerkte Chunks kosten wenig, Teleport-Flug über 2000 Blöcke mit und ohne Gedächtnis";
    public static final String CLEANUP_STEP = GROUP + "Abdeckung aufräumen";
    private static final int BASE_DX = -640;
    private static final int BASE_DZ = 640;
    private static final int WALL_AT = 200;
    private static final int DRONE_AT = 350;
    private static final int DRONE_SIDE = 70;
    private static final int REPEATER_BEFORE = 20;
    private static final double FLY_HEIGHT = 30.0;
    private static final int TIMEOUT = 1200;
    private static final int HOLD_TICKS = 60;
    private static final double GREEN = 0.6;
    private static final double MAX_SCAN_MS = 0.1;
    private static final int HOP = 64;
    private static final int HOP_TICKS = 6;
    private static final int FLIGHT_BLOCKS = 2000;
    private static final int TRAVEL_TICKS = 4;
    private static final double TRAVEL_STEP = 16.0;
    private static final AtomicReference<@Nullable Site> SITE = new AtomicReference<>();
    private static final List<Long> FRAMES = new ArrayList<>();
    private static volatile boolean recording;
    private static long lastFrame;

    private record Site(BlockPos base, BlockPos origin, Vec3 savedPos, float yaw, float pitch, GameType mode, boolean flying,
                        @Nullable ItemStack head, @Nullable ItemStack hand, int droneId, @Nullable BlockPos repeater) {
        Site withDrone(int id, ItemStack head, ItemStack hand) {
            return new Site(this.base, this.origin, this.savedPos, this.yaw, this.pitch, this.mode, this.flying, head, hand, id, this.repeater);
        }

        Site withRepeater(BlockPos pos) {
            return new Site(this.base, this.origin, this.savedPos, this.yaw, this.pitch, this.mode, this.flying, this.head, this.hand, this.droneId, pos);
        }
    }

    private CovSignalMemorySelfTests() {
    }

    public static void register() {
        ClientBus.FRAME_START.register((mc, delta) -> frame());
        SelfTest.clientStep(RANGE_STEP, 3000, CovSignalMemorySelfTests::rangeStep);
        SelfTest.clientStep(REPEATER_STEP, 3000, CovSignalMemorySelfTests::repeaterStep);
        SelfTest.clientStep(STUTTER_STEP, 4000, CovSignalMemorySelfTests::stutterStep);
        SelfTest.clientStep(CLEANUP_STEP, 1200, CovSignalMemorySelfTests::cleanupStep);
    }

    private static void frame() {
        long now = Util.getNanos();
        if (recording && lastFrame != 0L) {
            synchronized (FRAMES) {
                FRAMES.add(now - lastFrame);
            }
        }
        lastFrame = now;
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler");
        }
        return players.getFirst();
    }

    private static void teleport(ServerPlayer player, Vec3 feet) {
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.level(), feet.x, feet.y, feet.z, Set.of(), -90.0F, 0.0F, true);
    }

    private static void prepareSite(MinecraftServer server) {
        if (SITE.get() != null) {
            return;
        }
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        int x = player.getBlockX() + BASE_DX;
        int z = player.getBlockZ() + BASE_DZ;
        level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        BlockPos base = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        BlockPos origin = base.offset(WALL_AT - SignalJoinCheck.WALL_DX, 0, 0);
        level.getChunk(SectionPos.blockToSectionCoord(origin.getX() + SignalJoinCheck.WALL_DX), SectionPos.blockToSectionCoord(z));
        SITE.set(new Site(base, origin, player.position(), player.getYRot(), player.getXRot(), player.gameMode(), player.getAbilities().flying, null, null,
                -1, null));
        SignalJoinCheck.wall(level, origin, Blocks.STONE.defaultBlockState());
        player.setGameMode(GameType.CREATIVE);
        teleport(player, new Vec3(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5));
    }

    private static List<ChunkPos> wallChunks(BlockPos origin) {
        List<ChunkPos> out = new ArrayList<>();
        int x0 = SectionPos.blockToSectionCoord(origin.getX() + SignalJoinCheck.WALL_DX);
        int x1 = SectionPos.blockToSectionCoord(origin.getX() + SignalJoinCheck.WALL_DX + SignalJoinCheck.WALL_THICKNESS);
        int z0 = SectionPos.blockToSectionCoord(origin.getZ() - SignalJoinCheck.WALL_HALF_WIDTH);
        int z1 = SectionPos.blockToSectionCoord(origin.getZ() + SignalJoinCheck.WALL_HALF_WIDTH);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                out.add(new ChunkPos(cx, cz));
            }
        }
        return out;
    }

    private static boolean wallLoaded(ClientLevel level, BlockPos origin) {
        for (ChunkPos chunk : wallChunks(origin)) {
            if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null) {
                return false;
            }
        }
        return level.getBlockState(origin.offset(SignalJoinCheck.WALL_DX, SignalJoinCheck.WALL_HEIGHT / 2, 0)).is(Blocks.STONE);
    }

    private static boolean wallRemembered(ClientLevel level, BlockPos origin) {
        for (ChunkPos chunk : wallChunks(origin)) {
            if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null || !TerrainMemory.INSTANCE.remembers(level, chunk.x(), chunk.z())) {
                return false;
            }
        }
        return true;
    }

    private static String phase(SelfTest.StepContext ctx, String initial) {
        return (String) ctx.state().computeIfAbsent("cov_signal_phase", key -> initial);
    }

    private static StepResult next(SelfTest.StepContext ctx, String phase) {
        ctx.state().put("cov_signal_phase", phase);
        ctx.state().put("cov_signal_since", ctx.tick());
        ctx.state().remove("cov_signal_ready");
        return StepResult.pending();
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault("cov_signal_since", 0);
    }

    private static boolean settled(SelfTest.StepContext ctx, boolean ready, int ticks) {
        if (!ready) {
            ctx.state().remove("cov_signal_ready");
            return false;
        }
        int at = (int) ctx.state().computeIfAbsent("cov_signal_ready", key -> ctx.tick());
        return ctx.tick() - at >= ticks;
    }

    private static @Nullable StepResult await(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get("cov_signal_future");
        if (future == null) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove("cov_signal_future");
        return null;
    }

    private static void server(SelfTest.StepContext ctx, java.util.function.Consumer<MinecraftServer> action) {
        ctx.state().put("cov_signal_future", ctx.onServer(action));
    }

    private static Vec3 farAbove(Site site, int dx, int dz) {
        return new Vec3(site.base().getX() + dx + 0.5, site.base().getY() + FLY_HEIGHT, site.base().getZ() + dz + 0.5);
    }

    private static Vec3 behindWall(Site site) {
        return new Vec3(site.base().getX() + DRONE_AT + 0.5, site.origin().getY() + SignalJoinCheck.WALL_HEIGHT / 2.0, site.base().getZ() + 0.5);
    }

    private static void startFpv(MinecraftServer server) {
        Site site = SITE.get();
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null || site == null) {
            throw new IllegalStateException("Drohne oder Platz fehlt");
        }
        drone.snapTo(site.base().getX() + 2.5, site.base().getY() + 0.1, site.base().getZ() + 0.5, -90.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        ItemStack hand = player.getMainHandItem().copy();
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_DIGITAL).map(ItemStack::new).orElseThrow();
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        SITE.set(site.withDrone(drone.getId(), site.head() == null ? head : site.head(), site.hand() == null ? hand : site.hand()));
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void moveDrone(MinecraftServer server, Vec3 to) {
        Site site = SITE.get();
        if (site == null || !(player(server).level().getEntity(site.droneId()) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Testdrohne fehlt auf dem Server");
        }
        drone.resetTo(to, new Quaternionf().rotationY((float) (Math.PI / 2.0)));
    }

    private static boolean travel(SelfTest.StepContext ctx, Vec3 target) {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone == null) {
            return false;
        }
        Vec3 at = drone.position();
        double distance = at.distanceTo(target);
        if (distance < 3.0) {
            return true;
        }
        if (since(ctx) % TRAVEL_TICKS == 0 && !ctx.state().containsKey("cov_signal_future")) {
            Vec3 step = distance <= TRAVEL_STEP ? target : at.add(target.subtract(at).normalize().scale(TRAVEL_STEP));
            server(ctx, server -> moveDrone(server, step));
        }
        return false;
    }

    private static void hold(double targetY) {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        Telemetry t = sim == null ? null : sim.telemetry();
        double hover = t == null ? 0.3 : Math.clamp(t.hoverThrottleStick, 0.15, 0.6);
        double vs = t == null ? 0.0 : t.verticalSpeed;
        double h = drone == null ? targetY : drone.getY();
        double wanted = Math.clamp(1.2 * (targetY - h), -3.0, 3.0);
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) Math.clamp(hover + 0.06 * (wanted - vs), 0.05, 0.9)), true, StabMode.ANGLE);
    }

    private static @Nullable StepResult common(SelfTest.StepContext ctx, String phase, String then) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Site site = SITE.get();
        switch (phase) {
            case "site" -> {
                TerrainMemory.INSTANCE.enabled(true);
                if (site != null && site.droneId() >= 0 && PilotSession.INSTANCE.isFpv()) {
                    return next(ctx, then);
                }
                server(ctx, CovSignalMemorySelfTests::prepareSite);
                return next(ctx, "visit");
            }
            case "visit" -> {
                if (site == null || level == null) {
                    return since(ctx) > TIMEOUT ? StepResult.fail("kein Testplatz") : StepResult.pending();
                }
                if (!settled(ctx, wallLoaded(level, site.origin()), 10)) {
                    return since(ctx) > TIMEOUT ? StepResult.fail("Wand kommt beim Client nicht an") : StepResult.pending();
                }
                server(ctx, server -> teleport(player(server), new Vec3(site.base().getX() + 0.5, site.base().getY(), site.base().getZ() + 0.5)));
                return next(ctx, "forget");
            }
            case "forget" -> {
                if (site == null || level == null) {
                    return StepResult.fail("kein Testplatz");
                }
                if (!settled(ctx, wallRemembered(level, site.origin()), 10)) {
                    return since(ctx) > TIMEOUT ? StepResult.fail("Chunks der Wand werden nicht verworfen und gemerkt") : StepResult.pending();
                }
                server(ctx, CovSignalMemorySelfTests::startFpv);
                return next(ctx, "fpv");
            }
            case "fpv" -> {
                PilotSession session = PilotSession.INSTANCE;
                if (!session.isFpv() || session.sim() == null || site == null || session.drone() == null || session.drone().getId() != site.droneId()) {
                    return since(ctx) > TIMEOUT ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since(ctx) > 10, StabMode.ANGLE);
                if (since(ctx) < 20) {
                    return StepResult.pending();
                }
                return next(ctx, "climb");
            }
            case "climb" -> {
                if (site == null) {
                    return StepResult.fail("kein Testplatz");
                }
                hold(site.base().getY() + FLY_HEIGHT);
                QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                if (drone != null && Math.abs(drone.getY() - site.base().getY() - FLY_HEIGHT) < 2.0 && since(ctx) > 40) {
                    return next(ctx, then);
                }
                return since(ctx) > TIMEOUT ? StepResult.fail("Steigflug erreicht die Höhe nicht") : StepResult.pending();
            }
            default -> {
                return null;
            }
        }
    }

    private static final class TabHost implements ConfigTabHost {
        private @Nullable Screen screen;

        @Override
        public <T extends AbstractWidget> T addWidget(T widget) {
            return widget;
        }

        @Override
        public Font font() {
            return Minecraft.getInstance().font;
        }

        @Override
        public Minecraft minecraft() {
            return Minecraft.getInstance();
        }

        @Override
        public Screen screen() {
            if (this.screen == null) {
                this.screen = new Screen(Component.empty()) {
                };
            }
            return this.screen;
        }

        @Override
        public void rebuild() {
        }

        @Override
        public void changed() {
        }
    }

    private static String statusText(Minecraft mc) {
        ServerRulesTab tab = new ServerRulesTab();
        tab.attach(new TabHost(), 8, 28, 420, 300);
        LayoutProbe.Frame frame = LayoutProbe.capture(mc, graphics -> tab.extract(graphics, -1, -1, 0.0F), List.of());
        List<String> texts = new ArrayList<>();
        for (LayoutAudit.Text text : frame.texts()) {
            texts.add(text.value());
        }
        return String.join(" ", texts);
    }

    private static StepResult rangeStep(SelfTest.StepContext ctx) {
        StepResult waiting = await(ctx, "Server");
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        String phase = phase(ctx, "site");
        StepResult shared = common(ctx, phase, "far");
        if (shared != null) {
            return shared;
        }
        Site site = SITE.get();
        if (site == null || !PilotSession.INSTANCE.isFpv()) {
            return StepResult.fail("FPV-Sitzung endete in Phase " + phase);
        }
        Vec3 far = farAbove(site, DRONE_AT, -DRONE_SIDE);
        switch (phase) {
            case "far" -> {
                hold(far.y);
                if (!travel(ctx, far)) {
                    return since(ctx) > TIMEOUT ? StepResult.fail("Drohne erreicht die 350 Blöcke nicht") : StepResult.pending();
                }
                ctx.state().put("min_lq", 1.0);
                ctx.state().put("min_video", 1.0);
                ctx.state().put("failsafe", 0);
                return next(ctx, "settle");
            }
            case "settle" -> {
                hold(far.y);
                QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                boolean there = drone != null && drone.position().distanceTo(far) < 3.0;
                if (!settled(ctx, there && VideoState.INSTANCE.active() && ClientServerRules.INSTANCE.viewAnchored(), 40)) {
                    return since(ctx) > TIMEOUT ? StepResult.fail(String.format(Locale.ROOT, "Drohne kommt 350 Blöcke weg nicht an oder Sicht nicht angeheftet "
                            + "(da %s, Video %s, angeheftet %s)", there, VideoState.INSTANCE.active(), ClientServerRules.INSTANCE.viewAnchored()))
                            : StepResult.pending();
                }
                AtomicReference<Boolean> anchored = new AtomicReference<>();
                ctx.state().put("anchored", anchored);
                server(ctx, server -> anchored.set(ViewAnchors.isAnchored(player(server))));
                return next(ctx, "hold");
            }
            case "hold" -> {
                hold(far.y);
                OsdData data = OsdHud.sampler().data();
                ctx.state().put("min_lq", Math.min((double) ctx.state().get("min_lq"), data.linkStrength));
                ctx.state().put("min_video", Math.min((double) ctx.state().get("min_video"), VideoState.INSTANCE.signalQuality()));
                if (data.failsafeStage > 0) {
                    ctx.state().put("failsafe", (int) ctx.state().get("failsafe") + 1);
                }
                if (since(ctx) < HOLD_TICKS) {
                    return StepResult.pending();
                }
                String texts = statusText(mc);
                int radius = ClientServerRules.INSTANCE.rangeStatus().streamRadius();
                String expected = Component.translatable("propwash.server.tab.status.anchored", radius).getString();
                @SuppressWarnings("unchecked")
                AtomicReference<Boolean> anchored = (AtomicReference<Boolean>) ctx.state().get("anchored");
                QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                double distance = drone == null || mc.player == null ? Double.NaN : Math.hypot(drone.getX() - mc.player.getX(), drone.getZ() - mc.player.getZ());
                String log = String.format(Locale.ROOT, "Drohne %.0f Blöcke weg (Sichtweite %d), 60 Ticks: LQ min %.2f, Video min %.2f, Failsafe in %d Ticks, "
                                + "Sicht angeheftet Server %s / Client %s, Tab Server „%s“ %s", distance, mc.options.renderDistance().get(),
                        (double) ctx.state().get("min_lq"), (double) ctx.state().get("min_video"), (int) ctx.state().get("failsafe"), anchored.get(),
                        ClientServerRules.INSTANCE.viewAnchored(), expected, texts.contains(expected) ? "gefunden" : "fehlt");
                List<String> problems = new ArrayList<>();
                if ((int) ctx.state().get("failsafe") > 0) {
                    problems.add("Failsafe");
                }
                if ((double) ctx.state().get("min_lq") < GREEN || (double) ctx.state().get("min_video") < GREEN) {
                    problems.add("LQ oder Video nicht im grünen Bereich");
                }
                if (!Boolean.TRUE.equals(anchored.get()) || !ClientServerRules.INSTANCE.viewAnchored()) {
                    problems.add("Sicht nicht angeheftet");
                }
                if (!texts.contains(expected)) {
                    problems.add("Statuszeile im Tab Server fehlt");
                }
                ctx.state().put("cov_signal_log", log);
                return problems.isEmpty() ? pass(ctx, log) : StepResult.fail(String.join(", ", problems) + ": " + log);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static StepResult pass(SelfTest.StepContext ctx, String log) {
        ctx.log(log);
        return StepResult.pass();
    }

    private static double scan(ClientLevel level, Vec3 from, Vec3 to) {
        ClientBlockLoss blocks = new ClientBlockLoss();
        blocks.bind(level);
        ObstructionScan scan = new ObstructionScan();
        scan.start(from.x, from.y, from.z, to.x, to.y, to.z);
        return scan.evaluate(blocks);
    }

    private static StepResult repeaterStep(SelfTest.StepContext ctx) {
        StepResult waiting = await(ctx, "Server");
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        String phase = phase(ctx, "site");
        StepResult shared = common(ctx, phase, "behind");
        if (shared != null) {
            return shared;
        }
        Site site = SITE.get();
        ClientLevel level = mc.level;
        if (site == null || level == null || !PilotSession.INSTANCE.isFpv()) {
            return StepResult.fail("FPV-Sitzung endete in Phase " + phase);
        }
        Vec3 behind = behindWall(site);
        BlockPos repeater = BlockPos.containing(site.origin().getX() + SignalJoinCheck.WALL_DX - REPEATER_BEFORE, site.origin().getY(),
                site.origin().getZ());
        switch (phase) {
            case "behind" -> {
                hold(site.base().getY() + FLY_HEIGHT);
                server(ctx, server -> {
                    ServerLevel world = player(server).level();
                    world.getChunk(SectionPos.blockToSectionCoord(repeater.getX()), SectionPos.blockToSectionCoord(repeater.getZ()));
                    world.setBlockAndUpdate(repeater, RelayModule.SIGNAL_REPEATER.block().get().defaultBlockState());
                    SITE.set(SITE.get().withRepeater(repeater));
                });
                return next(ctx, "travel");
            }
            case "travel" -> {
                QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                Vec3 high = new Vec3(behind.x, site.base().getY() + FLY_HEIGHT, behind.z + DRONE_SIDE);
                boolean above = drone != null && drone.position().distanceTo(high) < 3.0 || ctx.state().containsKey("cov_signal_high");
                if (!above) {
                    hold(high.y);
                    travel(ctx, high);
                    return since(ctx) > TIMEOUT ? StepResult.fail("Drohne erreicht den Platz hinter der Wand nicht") : StepResult.pending();
                }
                ctx.state().put("cov_signal_high", true);
                hold(behind.y);
                if (!travel(ctx, behind)) {
                    return since(ctx) > TIMEOUT ? StepResult.fail("Drohne sinkt nicht hinter die Wand") : StepResult.pending();
                }
                return next(ctx, "measure");
            }
            case "measure" -> {
                hold(behind.y);
                QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                boolean there = drone != null && drone.position().distanceTo(behind) < 3.0;
                if (!settled(ctx, there && wallRemembered(level, site.origin()), 40)) {
                    return since(ctx) > TIMEOUT ? StepResult.fail(String.format(Locale.ROOT, "Drohne nicht hinter der Wand oder Wand nicht mehr gemerkt (da %s, gemerkt %s)",
                            there, wallRemembered(level, site.origin()))) : StepResult.pending();
                }
                Vec3 antenna = VideoState.antenna(drone);
                Vec3 relay = Vec3.atCenterOf(repeater);
                double leg = scan(level, relay, antenna);
                TerrainMemory.INSTANCE.enabled(false);
                double legOff;
                try {
                    legOff = scan(level, relay, antenna);
                } finally {
                    TerrainMemory.INSTANCE.enabled(true);
                }
                double direct = VideoState.INSTANCE.obstructionDb();
                ctx.state().put("rep_log", String.format(Locale.ROOT, "Repeater %d Blöcke vor der gemerkten Wand: Teilstrecke Repeater → Drohne %.2f dB "
                                + "(ohne Gedächtnis %.2f dB), direkte Strecke im Video %.2f dB, Signal %.2f, Repeater-Sprünge %d", REPEATER_BEFORE, leg, legOff, direct,
                        VideoState.INSTANCE.signalQuality(), VideoState.INSTANCE.relayHops()));
                if (leg < SignalMemorySelfTests.MIN_WALL_DB || legOff > 0.0) {
                    return StepResult.fail("Repeater-Strecke nicht abgeschattet: " + ctx.state().get("rep_log"));
                }
                ctx.state().put("before_quality", VideoState.INSTANCE.signalQuality());
                server(ctx, server -> {
                    WorldRules.set(server, WorldRuleSpecs.INFINITE_RANGE_ANALOG, 1);
                    WorldRules.set(server, WorldRuleSpecs.INFINITE_RANGE_DIGITAL, 1);
                });
                return next(ctx, "infinite");
            }
            case "infinite" -> {
                hold(behind.y);
                if (since(ctx) < 40) {
                    return StepResult.pending();
                }
                float quality = VideoState.INSTANCE.signalQuality();
                boolean active = VideoState.INSTANCE.active();
                OsdData data = OsdHud.sampler().data();
                String log = ctx.state().get("rep_log") + String.format(Locale.ROOT, "; Welt-Regeln „Unendliche Reichweite analog/digital“ an: Signal %.2f "
                        + "(vorher %.2f), Bild %s, KEIN VIDEO %s", quality, (float) ctx.state().get("before_quality"), active ? "da" : "weg", data.videoLost);
                ctx.state().put("rep_log", log);
                ctx.state().put("rep_ok", active && !data.videoLost && quality >= 0.95F);
                server(ctx, server -> {
                    WorldRules.set(server, WorldRuleSpecs.INFINITE_RANGE_ANALOG, WorldRuleSpecs.INFINITE_RANGE_ANALOG.defaultValue());
                    WorldRules.set(server, WorldRuleSpecs.INFINITE_RANGE_DIGITAL, WorldRuleSpecs.INFINITE_RANGE_DIGITAL.defaultValue());
                });
                return next(ctx, "reset");
            }
            case "reset" -> {
                hold(behind.y);
                if (since(ctx) < 20) {
                    return StepResult.pending();
                }
                String log = (String) ctx.state().get("rep_log") + String.format(Locale.ROOT, "; zurückgestellt: Signal %.2f", VideoState.INSTANCE.signalQuality());
                return (boolean) ctx.state().get("rep_ok") ? pass(ctx, log) : StepResult.fail("unendliche Reichweite hebt die Abschattung nicht auf: " + log);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static double percentile(long[] values, double p) {
        if (values.length == 0) {
            return Double.NaN;
        }
        long[] copy = values.clone();
        Arrays.sort(copy);
        return copy[Math.min(copy.length - 1, (int) Math.floor(p * copy.length))] / 1.0E6;
    }

    private static long[] takeFrames() {
        synchronized (FRAMES) {
            long[] out = new long[FRAMES.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = FRAMES.get(i);
            }
            FRAMES.clear();
            return out;
        }
    }

    private static StepResult stutterStep(SelfTest.StepContext ctx) {
        StepResult waiting = await(ctx, "Server");
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        String phase = phase(ctx, "start");
        switch (phase) {
            case "start" -> {
                if (PilotSession.INSTANCE.isActive()) {
                    PilotSession.INSTANCE.requestStop();
                    return since(ctx) > 200 ? StepResult.fail("Sitzung endet nicht") : StepResult.pending();
                }
                server(ctx, server -> {
                    ServerPlayer player = player(server);
                    state.put("flight_start", player.position());
                    state.put("flight_mode", player.gameMode());
                    player.setGameMode(GameType.CREATIVE);
                });
                state.put("run", 0);
                state.put("hops", 0);
                return next(ctx, "flight");
            }
            case "flight" -> {
                int run = (int) state.get("run");
                int hops = (int) state.get("hops");
                Vec3 start = (Vec3) state.get("flight_start");
                if (since(ctx) % HOP_TICKS != 0) {
                    return StepResult.pending();
                }
                if (hops == 0) {
                    TerrainMemory.INSTANCE.enabled(run != 1);
                    takeFrames();
                    recording = run > 0;
                    state.put("scans_before", TerrainMemory.INSTANCE.scans());
                }
                if (hops * HOP >= FLIGHT_BLOCKS) {
                    recording = false;
                    long[] frames = takeFrames();
                    if (run > 0) {
                        state.put("frames_" + run, frames);
                        state.put("scans_" + run, TerrainMemory.INSTANCE.scans() - (long) state.get("scans_before"));
                    }
                    state.put("run", run + 1);
                    state.put("hops", 0);
                    server(ctx, server -> teleport(player(server), start));
                    if (run + 1 > 2) {
                        TerrainMemory.INSTANCE.enabled(true);
                        return next(ctx, "judge");
                    }
                    return next(ctx, "flight");
                }
                Vec3 to = start.add((hops + 1) * HOP, 40.0, 0.0);
                server(ctx, server -> teleport(player(server), to));
                state.put("hops", hops + 1);
                return StepResult.pending();
            }
            case "judge" -> {
                if (since(ctx) < 20) {
                    return StepResult.pending();
                }
                long[] off = (long[]) state.get("frames_1");
                long[] on = (long[]) state.get("frames_2");
                double average = TerrainMemory.INSTANCE.averageScanMillis();
                double p95Off = percentile(off, 0.95);
                double p95On = percentile(on, 0.95);
                double p99Off = percentile(off, 0.99);
                double p99On = percentile(on, 0.99);
                String log = String.format(Locale.ROOT, "Teleport-Flug über %d Blöcke in %d-Blöcke-Sprüngen: ohne Gedächtnis %d Bilder, 95. Perzentil %.1f ms, 99. %.1f ms; "
                                + "mit Gedächtnis %d Bilder, 95. Perzentil %.1f ms, 99. %.1f ms, %d Chunks gemerkt; Ø %.4f ms je gemerktem Chunk (%d insgesamt, Grenze %.1f ms)",
                        FLIGHT_BLOCKS, HOP, off.length, p95Off, p99Off, on.length, p95On, p99On, (long) state.get("scans_2"), average,
                        TerrainMemory.INSTANCE.scans(), MAX_SCAN_MS);
                server(ctx, server -> {
                    ServerPlayer player = player(server);
                    if (state.get("flight_mode") instanceof GameType mode) {
                        player.setGameMode(mode);
                    }
                });
                List<String> problems = new ArrayList<>();
                if (!(average < MAX_SCAN_MS)) {
                    problems.add("gemerkter Chunk kostet im Mittel zu viel");
                }
                if ((long) state.get("scans_2") <= 0) {
                    problems.add("beim Flug mit Gedächtnis wurde nichts gemerkt");
                }
                if (!(p95On <= p95Off * 1.25 + 2.0) || !(p99On <= p99Off * 1.25 + 4.0)) {
                    problems.add("Frame-Zeiten mit Gedächtnis deutlich schlechter");
                }
                return problems.isEmpty() ? pass(ctx, log) : StepResult.fail(String.join(", ", problems) + ": " + log);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        TerrainMemory.INSTANCE.enabled(true);
        recording = false;
        if (ctx.tick() == 0) {
            PilotSession.INSTANCE.clearTestInput();
            Site site = SITE.getAndSet(null);
            ctx.state().put("cov_signal_future", ctx.onServer(server -> {
                ServerPlayer player = player(server);
                DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
                WorldRules.set(server, WorldRuleSpecs.INFINITE_RANGE_ANALOG, WorldRuleSpecs.INFINITE_RANGE_ANALOG.defaultValue());
                WorldRules.set(server, WorldRuleSpecs.INFINITE_RANGE_DIGITAL, WorldRuleSpecs.INFINITE_RANGE_DIGITAL.defaultValue());
                if (site == null) {
                    return;
                }
                ServerLevel level = player.level();
                if (site.droneId() >= 0 && level.getEntity(site.droneId()) instanceof QuadcopterEntity drone) {
                    drone.discard();
                }
                SignalJoinCheck.wall(level, site.origin(), Blocks.AIR.defaultBlockState());
                if (site.repeater() != null) {
                    level.setBlockAndUpdate(site.repeater(), Blocks.AIR.defaultBlockState());
                }
                if (site.head() != null) {
                    player.setItemSlot(EquipmentSlot.HEAD, site.head());
                }
                if (site.hand() != null) {
                    player.setItemSlot(EquipmentSlot.MAINHAND, site.hand());
                }
                player.setGameMode(site.mode());
                player.getAbilities().flying = site.flying() && player.getAbilities().mayfly;
                player.onUpdateAbilities();
                player.teleportTo(level, site.savedPos().x, site.savedPos().y, site.savedPos().z, Set.of(), site.yaw(), site.pitch(), true);
            }));
            return StepResult.pending();
        }
        StepResult waiting = await(ctx, "Aufräumen");
        if (waiting != null) {
            return waiting;
        }
        if (PilotSession.INSTANCE.isActive()) {
            return ctx.tick() > 400 ? StepResult.fail("Sitzung läuft nach dem Aufräumen weiter") : StepResult.pending();
        }
        return mc.player != null ? StepResult.pass() : StepResult.pending();
    }
}
