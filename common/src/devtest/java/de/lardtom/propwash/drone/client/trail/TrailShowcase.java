package de.lardtom.propwash.drone.client.trail;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseTour;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.render.DroneRenderConfigTab;
import de.lardtom.propwash.drone.led.LedColor;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.WorldRulesSelfTests;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class TrailShowcase {
    private static final String FUTURE = "trail_shot_future";
    private static final String STAGE = "trail_shot_stage";
    private static final String MARK = "trail_shot_mark";
    private static final int SITE_X = -60;
    private static final int SITE_Z = 70;
    private static final int PILLAR = 2;
    private static final double EDGE = 0.99;
    private static final float UPTILT = 12.0F;
    private static final int MIN_SAMPLES = 30;
    private static final AtomicInteger OWN = new AtomicInteger(-1);
    private static final AtomicLong CLOCK = new AtomicLong(-1L);
    private static final AtomicReference<Saved> SAVED = new AtomicReference<>();
    private static final List<Placed> PLACED = new CopyOnWriteArrayList<>();
    private static @Nullable Boolean savedEnabled;

    private record Saved(int rule, Vec3 pos, float yaw, float pitch, ItemStack head, ItemStack hand) {
    }

    private record Placed(BlockPos pos, BlockState state) {
    }

    private TrailShowcase() {
    }

    public static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        Screenshots.step("bildschirmfotos: Drohnen-Trails – mehrere Drohnen bei Tag", "trails_several_drones", 30,
                new FieldScene(true, true, false, ClockTimeMarkers.NOON));
        Screenshots.step("bildschirmfotos: Drohnen-Trails – Welt-Regel aus", "trails_rule_off", 10,
                new FieldScene(false, true, false, ClockTimeMarkers.NOON));
        Screenshots.step("bildschirmfotos: Drohnen-Trails – persönlicher Schalter aus", "trails_personal_off", 10,
                new FieldScene(true, false, false, ClockTimeMarkers.NOON));
        Screenshots.step("bildschirmfotos: Drohnen-Trails – Tab Darstellung", "configurator_display_trails", 20, new TabScene(true));
        Screenshots.step("bildschirmfotos: Drohnen-Trails – Tab Darstellung bei Welt-Regel aus", "configurator_display_trails_rule_off", 20,
                new TabScene(false));
        Screenshots.step("bildschirmfotos: Drohnen-Trails – Welt-Regeln mit Tooltip", "configurator_world_rules_drone_trails", 30,
                WorldRulesSelfTests.tabScene(WorldRuleSpecs.DRONE_TRAILS, 1));
        Screenshots.step("bildschirmfotos: Drohnen-Trails – FPV-Bild mit den Trails anderer Drohnen bei Tag", "trails_fpv_day", 30,
                new FieldScene(true, true, true, ClockTimeMarkers.NOON));
        Screenshots.step("bildschirmfotos: Drohnen-Trails – FPV-Bild mit den Trails anderer Drohnen bei Nacht", "trails_fpv_night", 30,
                new FieldScene(true, true, true, ClockTimeMarkers.MIDNIGHT));
        SelfTest.clientStep("bildschirmfotos: Drohnen-Trails – aufräumen", 400, TrailShowcase::cleanupStep);
    }

    private static BlockPos site() {
        return ShowcaseTour.base().offset(SITE_X, 0, SITE_Z);
    }

    private static void prepareSite(MinecraftServer server, boolean rule, boolean fpv, ResourceKey<ClockTimeMarker> time) {
        ServerPlayer player = TrailSelfTests.player(server);
        ServerLevel level = player.level();
        SAVED.compareAndSet(null, new Saved(WorldRules.get(server, WorldRuleSpecs.DRONE_TRAILS), player.position(), player.getYRot(),
                player.getXRot(), player.getItemBySlot(EquipmentSlot.HEAD).copy(), player.getMainHandItem().copy()));
        WorldRules.set(server, WorldRuleSpecs.DRONE_TRAILS, rule ? 1 : 0);
        Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
        if (clock.isPresent()) {
            CLOCK.compareAndSet(-1L, server.clockManager().getInstance(clock.get()).totalTicks());
            server.clockManager().moveToTimeMarker(clock.get(), time);
        }
        BlockPos site = site();
        if (PLACED.isEmpty()) {
            for (int y = 0; y < PILLAR; y++) {
                place(level, site.above(y), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        if (!fpv) {
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.teleportTo(level, site.getX() + 0.5, site.getY(), site.getZ() - 5.5, Set.of(), 0.0F, -14.0F, true);
        } else if (player.distanceToSqr(site.getX() + 0.5, site.getY(), site.getZ() - 1.5) > 4.0) {
            player.teleportTo(level, site.getX() + 0.5, site.getY(), site.getZ() - 1.5, Set.of(), 0.0F, 10.0F, true);
        }
    }

    private static void spawnFlights(MinecraftServer server) {
        if (TrailFlights.active()) {
            return;
        }
        ServerLevel level = TrailSelfTests.player(server).level();
        BlockPos site = site();
        if (!level.isLoaded(site.offset(0, 0, 30))) {
            throw new IllegalStateException("Testfeld noch nicht geladen");
        }
        Vec3 c = Vec3.atBottomCenterOf(site);
        TrailFlights.spawn(level, new TrailFlights.Path(c.add(-5.0, 4.5, 14.0), 3.5, 3.0, 7.0, 0.6, 0.0, false), DroneBuild.WHOOP,
                DronePaint.of(Map.of(PaintSlot.FRAME, TrailSelfTests.PAINT_RED)));
        QuadcopterEntity magenta = TrailFlights.spawn(level, new TrailFlights.Path(c.add(6.0, 6.0, 18.0), 5.0, 3.5, 12.0, 1.0, 1.5, false),
                DroneBuild.FREESTYLE.withAccessory(AccessoryType.LED_STRIP), DronePaint.DEFAULT);
        magenta.setTune(TrailSelfTests.ledTune(LedColor.MAGENTA));
        TrailFlights.spawn(level, new TrailFlights.Path(c.add(0.0, 8.5, 30.0), 13.0, 7.0, 22.0, 1.5, 3.0, false), DroneBuild.RACER,
                DronePaint.DEFAULT);
        QuadcopterEntity green = TrailFlights.spawn(level, new TrailFlights.Path(c.add(-10.0, 5.0, 25.0), 6.0, 6.0, 10.0, 0.8, 4.5, true),
                DroneBuild.FREESTYLE.withAccessory(AccessoryType.LED_STRIP), DronePaint.DEFAULT);
        green.setTune(TrailSelfTests.ledTune(TrailSelfTests.LED));
        TrailFlights.spawn(level, new TrailFlights.Path(c.add(9.0, 3.5, 34.0), 4.0, 4.0, 15.0, 0.5, 5.0, true), DroneBuild.CINEWHOOP,
                DronePaint.of(Map.of(PaintSlot.FRAME, 0xFFD020)));
    }

    private static void startFpv(MinecraftServer server) {
        ServerPlayer player = TrailSelfTests.player(server);
        ServerLevel level = player.level();
        if (level.getEntity(OWN.get()) instanceof QuadcopterEntity existing && existing.isFlownBy(player.getUUID())) {
            return;
        }
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        BlockPos site = site();
        QuadcopterEntity drone = level.getEntity(OWN.get()) instanceof QuadcopterEntity existing ? existing : null;
        Vec3 at = new Vec3(site.getX() + 0.5, site.getY() + PILLAR + 0.02, site.getZ() + EDGE);
        if (drone == null) {
            drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("Drohne nicht erzeugt");
            }
            drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
            drone.setBuild(DroneBuild.FREESTYLE);
            drone.setBatteryCharge(1.0F);
            drone.setTune(new FlightTune(Map.of(TuneMapping.CAMERA_UPTILT, UPTILT)));
            if (!level.addFreshEntity(drone)) {
                throw new IllegalStateException("Drohne nicht in die Welt gesetzt");
            }
            OWN.set(drone.getId());
        }
        drone.resetTo(at, new Quaternionf().rotationY((float) Math.PI));
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState before = level.getBlockState(pos);
        if (before.equals(state)) {
            return;
        }
        PLACED.add(new Placed(pos.immutable(), before));
        level.setBlockAndUpdate(pos, state);
    }

    private static void tearDown(MinecraftServer server) {
        ServerPlayer player = TrailSelfTests.player(server);
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        if (level.getEntity(OWN.getAndSet(-1)) instanceof QuadcopterEntity drone) {
            drone.discard();
        }
        TrailFlights.clear(server);
        for (int i = PLACED.size() - 1; i >= 0; i--) {
            Placed placed = PLACED.get(i);
            level.setBlockAndUpdate(placed.pos(), placed.state());
        }
        PLACED.clear();
        long ticks = CLOCK.getAndSet(-1L);
        Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
        if (ticks >= 0L && clock.isPresent()) {
            server.clockManager().setTotalTicks(clock.get(), ticks);
        }
        Saved saved = SAVED.getAndSet(null);
        if (saved != null) {
            WorldRules.set(server, WorldRuleSpecs.DRONE_TRAILS, saved.rule());
            player.setItemSlot(EquipmentSlot.HEAD, saved.head());
            player.setItemSlot(EquipmentSlot.MAINHAND, saved.hand());
            player.teleportTo(level, saved.pos().x, saved.pos().y, saved.pos().z, Set.of(), saved.yaw(), saved.pitch(), true);
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            PilotSession.INSTANCE.clearTestInput();
            state.put(FUTURE, ctx.onServer(TrailShowcase::tearDown));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        restoreEnabled();
        if (PilotSession.INSTANCE.isActive()) {
            return ctx.tick() > 300 ? StepResult.fail("FPV-Sitzung der Trail-Bilder endet nicht") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static void setEnabled(boolean enabled) {
        if (savedEnabled == null) {
            savedEnabled = TrailConfig.get().enabled;
        }
        if (TrailConfig.get().enabled != enabled) {
            TrailClient.setEnabled(enabled);
        }
    }

    private static void restoreEnabled() {
        Boolean saved = savedEnabled;
        if (saved != null) {
            TrailClient.setEnabled(saved);
            savedEnabled = null;
        }
    }

    private static final class FieldScene implements Screenshots.Scene {
        private final boolean rule;
        private final boolean personal;
        private final boolean fpv;
        private final ResourceKey<ClockTimeMarker> time;

        private FieldScene(boolean rule, boolean personal, boolean fpv, ResourceKey<ClockTimeMarker> time) {
            this.rule = rule;
            this.personal = personal;
            this.fpv = fpv;
            this.time = time;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            int stage = (int) state.getOrDefault(STAGE, 0);
            PilotSession session = PilotSession.INSTANCE;
            TrailFlights.armOnClient(true);
            switch (stage) {
                case 0 -> {
                    setEnabled(this.personal);
                    boolean rule = this.rule;
                    boolean fpv = this.fpv;
                    ResourceKey<ClockTimeMarker> time = this.time;
                    state.put(FUTURE, ctx.onServer(server -> prepareSite(server, rule, fpv, time)));
                    advance(state, 1, ctx.tick());
                }
                case 1 -> {
                    StepResult waiting = await(ctx, "Testfeld");
                    if (waiting != null) {
                        return waiting;
                    }
                    if (ctx.tick() - (int) state.get(MARK) < 10) {
                        return StepResult.pending();
                    }
                    state.put(FUTURE, ctx.onServer(TrailShowcase::spawnFlights));
                    advance(state, 2, ctx.tick());
                }
                case 2 -> {
                    CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                    if (future.isCompletedExceptionally()) {
                        if (ctx.tick() > 200) {
                            return StepResult.fail("Drohnen: " + future.exceptionNow().getMessage());
                        }
                        state.put(FUTURE, ctx.onServer(TrailShowcase::spawnFlights));
                        return StepResult.pending();
                    }
                    if (!future.isDone()) {
                        return StepResult.pending();
                    }
                    if (this.fpv) {
                        QuadcopterEntity current = session.drone();
                        boolean own = session.isFpv() && current != null && current.getId() == OWN.get();
                        if (!own && session.isActive()) {
                            return ctx.tick() > 300 ? StepResult.fail("vorige Flugsitzung endet nicht") : StepResult.pending();
                        }
                        if (!own) {
                            state.put(FUTURE, ctx.onServer(TrailShowcase::startFpv));
                        }
                    }
                    advance(state, 3, ctx.tick());
                }
                default -> {
                    StepResult waiting = await(ctx, "FPV-Start");
                    if (waiting != null) {
                        return waiting;
                    }
                    if (this.fpv) {
                        QuadcopterEntity current = session.drone();
                        if (!session.isFpv() || current == null || current.getId() != OWN.get()) {
                            return ctx.tick() > 400 ? StepResult.fail("keine FPV-Sitzung für die Trail-Bilder") : StepResult.pending();
                        }
                    } else if (session.isActive()) {
                        return ctx.tick() > 400 ? StepResult.fail("Flugsitzung endet nicht") : StepResult.pending();
                    }
                    if (this.rule != TrailClient.ruleOn()) {
                        return ctx.tick() > 400 ? StepResult.fail("Welt-Regel kam nicht an") : StepResult.pending();
                    }
                    if (this.rule && this.personal) {
                        int ready = 0;
                        for (int id : TrailFlights.ids()) {
                            if (TrailRenderer.drawn(id) && TrailRenderer.samples(id) >= MIN_SAMPLES) {
                                ready++;
                            }
                        }
                        if (ready < 4) {
                            return ctx.tick() > 500 ? StepResult.fail("nur " + ready + " Trails fertig") : StepResult.pending();
                        }
                    } else if (TrailRenderer.lastTrailsDrawn() != 0 || ctx.tick() - (int) state.get(MARK) < 20) {
                        return ctx.tick() > 400 ? StepResult.fail("Trails trotz Abschaltung gezeichnet") : StepResult.pending();
                    }
                    ctx.log("Trail-Bild: " + TrailRenderer.lastTrailsDrawn() + " Trails, " + TrailRenderer.lastQuads() + " Vierecke");
                    return StepResult.pass();
                }
            }
            return StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            TrailFlights.armOnClient(true);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ctx.state().remove(STAGE);
        }
    }

    private static final class TabScene implements Screenshots.Scene {
        private final boolean rule;
        private @Nullable CycleButton<Boolean> toggle;

        private TabScene(boolean rule) {
            this.rule = rule;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                setEnabled(true);
                boolean rule = this.rule;
                state.put(FUTURE, ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.DRONE_TRAILS, rule ? 1 : 0)));
                return StepResult.pending();
            }
            StepResult waiting = await(ctx, "Welt-Regel");
            if (waiting != null) {
                return waiting;
            }
            if (TrailClient.ruleOn() != this.rule) {
                return ctx.tick() > 200 ? StepResult.fail("Welt-Regel kam nicht an") : StepResult.pending();
            }
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                ConfigTabs.open(null, "drone_render");
                return StepResult.pending();
            }
            ConfigTab active = screen.activeTab();
            if (!(active instanceof DroneRenderConfigTab tab)) {
                screen.selectTab("drone_render");
                return ctx.tick() > 200 ? StepResult.fail("Tab Darstellung öffnet nicht") : StepResult.pending();
            }
            this.toggle = tab.trailToggle();
            if (this.toggle == null || this.toggle.active != this.rule || tab.trailRuleHintShown() == this.rule) {
                return ctx.tick() > 200 ? StepResult.fail("Schalter Drohnen-Trails im falschen Zustand") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            CycleButton<Boolean> b = this.toggle;
            Minecraft mc = Minecraft.getInstance();
            if (b == null || !b.visible) {
                Screenshots.parkMouse(mc);
                return;
            }
            double x = (b.getX() + 0.75 * b.getWidth()) * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            double y = (b.getY() + 0.5 * b.getHeight()) * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
            mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0.0, 0.0);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            this.toggle = null;
            Screenshots.parkMouse(Minecraft.getInstance());
        }
    }

    private static @Nullable StepResult await(SelfTest.StepContext ctx, String what) {
        Object value = ctx.state().get(FUTURE);
        if (!(value instanceof CompletableFuture<?> future)) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static void advance(Map<String, Object> state, int stage, int tick) {
        state.put(STAGE, stage);
        state.put(MARK, tick);
    }
}
