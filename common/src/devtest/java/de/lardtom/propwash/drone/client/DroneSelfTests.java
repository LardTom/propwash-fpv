package de.lardtom.propwash.drone.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.core.BoundDrone;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.CraftNames;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.drone.net.CraftNameC2S;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.server.ServerRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DroneServiceMenu;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.client.render.QuadcopterRenderer;
import de.lardtom.propwash.video.client.VideoConfig;
import de.lardtom.propwash.video.client.VideoSettings;
import de.lardtom.propwash.video.client.VideoState;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class DroneSelfTests {
    private static final AtomicInteger DRONE_ID = new AtomicInteger(-1);
    private static final String FUTURE = "future";
    private static final String RESULT = "result";
    private static final String START_Y = "startY";
    private static final double CLIMB_TARGET = 8.0;
    private static final String FRAMES = "frames";
    private static final String HOLD_FRAMES = "hold_frames";
    private static final String HOLD_CAMERA = "hold_camera";
    private static final String HOLD_START = "hold_start";
    private static final double BODY_VIEW_DISTANCE = 4.0;
    private static final long BODY_FRAMES = 10L;
    private static final String MOVED = "moved";
    private static final String MOVED_TICK = "movedTick";
    private static final String WAIT_UNTIL = "waitUntil";
    private static final String CAMERA_LATENCY = "cameraLatency";
    private static final String CAMERA_WORST = "cameraWorst";
    private static final String CAMERA_MOVING = "cameraMoving";
    private static final int CAMERA_TEST_LATENCY_MS = 40;
    private static final int CAMERA_SETTLE_TICKS = 5;
    private static final int CAMERA_SAMPLE_TICKS = 30;
    private static final AtomicReference<ItemStack> BOUND_GOGGLES = new AtomicReference<>(ItemStack.EMPTY);

    private DroneSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("drone: Tuning-Zuordnung", 20, DroneSelfTests::tuneStep);
        SelfTest.clientStep("drone: Topspeed realistisch, Spannungseinbruch aus", 20, DroneSelfTests::topSpeedStep);
        SelfTest.clientStep("drone: keine doppelten Standardtasten", 20, DroneSelfTests::keyConflictStep);
        SelfTest.clientStep("drone: Verbindungsqualität vor dem Server-Limit", 20, DroneSelfTests::linkFadeStep);
        SelfTest.clientStep("drone: Funken-Tooltip zeigt die geltende Reichweite", 20, DroneSelfTests::remoteRangeTooltipStep);
        SelfTest.clientStep("drone: Drohne aus dem Item platzieren", 200, DroneSelfTests::placeStep);
        SelfTest.clientStep("drone: Funke und Brille binden", 100, DroneSelfTests::bindStep);
        SelfTest.clientStep("drone: Name per Namensschild und Konfigurator", 200, DroneSelfTests::craftNameStep);
        SelfTest.clientStep("drone: Kreativ hebt die Funkreichweite auf", 100, DroneSelfTests::freeRangeStep);
        SelfTest.clientStep("drone: Funke beidhändig in der Ego-Ansicht", 100, DroneSelfTests::remoteHandsStep);
        SelfTest.clientStep("drone: Funke beidhändig in der dritten Person", 160, DroneSelfTests::remoteThirdPersonStep);
        SelfTest.clientStep("drone: Sichtflug starten", 200, DroneSelfTests::losStep);
        SelfTest.clientStep("drone: Stopp per Funke", 200, DroneSelfTests::stopRemoteStep);
        SelfTest.clientStep("drone: FPV mit gebundener Brille", 200, DroneSelfTests::fpvStep);
        SelfTest.clientStep("drone: Effekte des Piloten bleiben aus dem FPV-Bild", 200, DroneSelfTests::pilotEffectsStep);
        SelfTest.clientStep("drone: Scharf schalten und steigen", 600, DroneSelfTests::climbStep);
        SelfTest.clientStep("drone: FPV-Bild hängt nur an der Frame-Zeit", 120, DroneSelfTests::cameraClockStep);
        SelfTest.clientStep("drone: Welt-Regel Schwerkraft wirkt im Flug", 300, GravitySelfTests::flightStep);
        SelfTest.clientStep("drone: ALTHOLD und POSHOLD im Flug (Schalter und Tasten)", 900, HoldSelfTests::holdStep);
        SelfTest.clientStep("drone: Absturz wird erkannt", 600, DroneSelfTests::crashStep);
        SelfTest.clientStep("drone: Absturz beschädigt alle Bauteile", 200, ctx -> WearSelfTests.crashWearStep(ctx, DRONE_ID.get()));
        SelfTest.clientStep("drone: Pilotenkörper aus der Drohne sichtbar", 200, DroneSelfTests::bodyStep);
        SelfTest.clientStep("drone: FPV – Pilot schaut der kreisenden Drohne nicht nach", 300, PilotLookSelfTests::fpvCircleStep);
        SelfTest.clientStep("drone: Brille abgesetzt – weiter im Sichtflug", 100, DroneSelfTests::gogglesOffStep);
        SelfTest.clientStep("drone: Sichtflug – Blick folgt der kreisenden Drohne", 250, PilotLookSelfTests::losCircleStep);
        SelfTest.clientStep("drone: Brille wieder auf – Blick bleibt im FPV stehen", 150,
                ctx -> PilotLookSelfTests.gogglesOnStep(ctx, DroneSelfTests::fpvGoggles));
        SelfTest.clientStep("drone: Brille erneut abgesetzt – weiter im Sichtflug", 100, DroneSelfTests::gogglesOffStep);
        SelfTest.clientStep("drone: Sichtflug – Blick findet die Drohne sofort wieder", 100, PilotLookSelfTests::losResumeStep);
        CrashFallSelfTests.registerPiloted(DRONE_ID::get);
        SelfTest.clientStep("drone: Steuerung beenden", 200, DroneSelfTests::stopStep);
        CrashFallSelfTests.registerUnpiloted(DRONE_ID::get);
        SelfTest.clientStep("drone: Akku tauschen", 200, DroneSelfTests::batteryStep);
        SelfTest.clientStep("drone: Aufheben behält Akku, Id und Tuning", 200, DroneSelfTests::pickupStep);
        SelfTest.clientStep("drone: kein Nachladen", 300, DroneSelfTests::noRechargeStep);
        SelfTest.clientStep("drone: Aufräumen", 100, DroneSelfTests::cleanupStep);
        PlayLoopSelfTests.register();
        LedSwitchSelfTests.register();
        FlightModeSelfTests.register();
        Mode3dSelfTests.register();
        SelfTest.afterLeaveStep("drone: keine Sitzung nach dem Verlassen", 20,
                ctx -> StepResult.check(!PilotSession.INSTANCE.isActive(), "PilotSession nach dem Verlassen noch aktiv"));
    }

    private static StepResult tuneStep(SelfTest.StepContext ctx) {
        AirframeSpec params = AirframeSpec.of(DroneBuild.RACER);
        Map<String, Float> defaults = TuneMapping.defaults(params);
        if (!TuneMapping.normalize(defaults, params).isEmpty()) {
            return StepResult.fail("Standardwerte werden nicht zu einem leeren Tuning normalisiert");
        }
        FlightTune edited = new FlightTune(defaults).with("pid.roll.p", defaults.get("pid.roll.p") + 7.0F);
        FlightTune stored = DronePiloting.sanitize(FlightTune.EMPTY, edited, DroneBuild.RACER);
        if (stored.get(TuneMapping.PID_SOURCE, -1.0F) != 1.0F || Math.abs(stored.get("pid.roll.p", 0.0F) - defaults.get("pid.roll.p") - 7.0F) > 1.0E-3F) {
            return StepResult.fail("PID-Änderung setzt pid.source nicht auf manuell: " + stored.values());
        }
        return StepResult.pass();
    }

    private static StepResult topSpeedStep(SelfTest.StepContext ctx) {
        double[][] ranges = {{130.0, 150.0}, {160.0, 180.0}, {100.0, 120.0}};
        DroneBuild[] builds = {DroneBuild.FREESTYLE, DroneBuild.RACER, DroneBuild.LONG_RANGE};
        for (int k = 0; k < builds.length; k++) {
            double top = BuildAnalysis.of(builds[k]).topSpeedAcroKmh();
            if (top < ranges[k][0] || top > ranges[k][1]) {
                return StepResult.fail("Topspeed " + builds[k].frame().name() + " " + top + " km/h außerhalb " + ranges[k][0] + "–" + ranges[k][1]);
            }
        }
        RuleValues rules = RuleValues.DEFAULTS.with(WorldRuleSpecs.BATTERY_SAG, 0).with(WorldRuleSpecs.BATTERY_SCALE, 0);
        DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), PhysicsRules.of(rules).toSimTuning(), 1L);
        sim.reset(0.0, 100.0, 0.0, 0.0);
        sim.setBatteryCharge(0.3);
        sim.step(0.01, new PilotInput(), new SimEnvironment(), new de.lardtom.propwash.sim.StepResult());
        double resting = sim.telemetry().cellVoltageResting;
        double full = sim.params().chemistry.fullCellVoltage;
        if (Math.abs(resting - full) > 1.0E-6) {
            return StepResult.fail("Spannungseinbruch aus: Ruhespannung " + resting + " V statt " + full + " V");
        }
        return StepResult.pass();
    }

    private static StepResult keyConflictStep(SelfTest.StepContext ctx) {
        Map<String, List<String>> byKey = new HashMap<>();
        for (KeyMapping mapping : ClientRegistrar.keyMappings()) {
            InputConstants.Key key = mapping.getDefaultKey();
            if (key.equals(InputConstants.UNKNOWN)) {
                continue;
            }
            List<String> previous = byKey.computeIfAbsent(key.getName(), k -> new ArrayList<>());
            for (String other : previous) {
                if (!KeyDefaults.sharesKey(other, mapping.getName())) {
                    return StepResult.fail("Standardtaste " + key.getName() + " doppelt: " + other + " und " + mapping.getName());
                }
            }
            previous.add(mapping.getName());
        }
        return StepResult.pass();
    }

    private static StepResult linkFadeStep(SelfTest.StepContext ctx) {
        float nearLimit = PilotSession.linkStrengthAt(155.0, 160.0);
        float inside = PilotSession.linkStrengthAt(100.0, 160.0);
        float unlimited = PilotSession.linkStrengthAt(700.0, Double.POSITIVE_INFINITY);
        if (nearLimit > 0.0F || inside < 1.0F || unlimited < 1.0F) {
            return StepResult.fail("Verbindungsqualität falsch: " + nearLimit + " / " + inside + " / " + unlimited);
        }
        return StepResult.pass();
    }

    private static StepResult remoteRangeTooltipStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return StepResult.pending();
        }
        boolean free = RadioLink.freeRange(mc.player);
        if (RadioLink.LOCAL_FREE_RANGE.getAsBoolean() != free) {
            return StepResult.fail("LOCAL_FREE_RANGE folgt nicht dem Spieler: " + free);
        }
        List<String> keys = new ArrayList<>();
        TooltipShift.override(true);
        try {
            ItemStack remote = new ItemStack(ContentIds.item(ContentIds.REMOTE).orElseThrow());
            remote.getItem().appendHoverText(remote, Item.TooltipContext.of(mc.level), TooltipDisplay.DEFAULT, line -> {
                if (line.getContents() instanceof TranslatableContents translatable) {
                    keys.add(translatable.getKey());
                }
            }, TooltipFlag.NORMAL);
        } finally {
            TooltipShift.override(null);
        }
        boolean showsFree = keys.contains("propwash.help.remote.range_free_now") || keys.contains("propwash.help.remote.range_free_server");
        boolean showsFixed = keys.contains("propwash.help.remote.range");
        if (free && (!showsFree || showsFixed)) {
            return StepResult.fail("Kreativ ohne feste Funkgrenze, Tooltip zeigt: " + keys);
        }
        if (!free && (showsFree || !showsFixed)) {
            return StepResult.fail("Überleben mit fester Funkgrenze, Tooltip zeigt: " + keys);
        }
        return StepResult.pass();
    }

    private static StepResult pilotEffectsStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400));
                player.setRemainingFireTicks(100);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Effekte setzen fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone() || mc.player == null || !mc.player.hasEffect(MobEffects.NIGHT_VISION) || ctx.tick() < 20) {
            return StepResult.pending();
        }
        if (!FpvView.active()) {
            return StepResult.fail("keine FPV-Sitzung aktiv");
        }
        PlayerRenderState player = mc.gameRenderer.gameRenderState().levelRenderState.playerRenderState;
        LightmapRenderState lightmap = mc.gameRenderer.gameRenderState().lightmapRenderState;
        String problem = null;
        if (lightmap.nightVisionEffectIntensity != 0.0F || lightmap.darknessEffectScale != 0.0F) {
            problem = "Nachtsicht/Dunkelheit im Lightmap: " + lightmap.nightVisionEffectIntensity + " / " + lightmap.darknessEffectScale;
        } else if (player.isOnFire || player.blockOverlay != null || player.waterOverlay != null || player.nauseaEffectIntensity != 0.0F) {
            problem = "Bildschirmeffekte des Piloten im FPV-Bild";
        }
        ctx.onServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
            p.removeEffect(MobEffects.NIGHT_VISION);
            p.clearFire();
        });
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static StepResult placeStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            AtomicReference<String> result = new AtomicReference<>("");
            ctx.state().put(RESULT, result);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    throw new IllegalStateException("kein Spieler auf dem Server");
                }
                ServerPlayer player = players.getFirst();
                ServerLevel level = player.level();
                int x = player.getBlockX() + 3;
                int z = player.getBlockZ();
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos ground = new BlockPos(x, top - 1, z);
                ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
                stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
                stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                Vec3 click = new Vec3(x + 0.5, top, z + 0.5);
                InteractionResult use = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(click, Direction.UP, ground, false)));
                if (!use.consumesAction()) {
                    throw new IllegalStateException("useOn lieferte " + use);
                }
                List<QuadcopterEntity> drones = level.getEntitiesOfClass(QuadcopterEntity.class, new AABB(ground).inflate(2.0));
                if (drones.isEmpty()) {
                    throw new IllegalStateException("keine Drohne nach useOn");
                }
                QuadcopterEntity drone = drones.getFirst();
                if (!drone.build().equals(DroneBuild.FREESTYLE)) {
                    throw new IllegalStateException("Build nicht übernommen: " + drone.build());
                }
                if (!player.getMainHandItem().isEmpty() && !player.hasInfiniteMaterials()) {
                    throw new IllegalStateException("Item wurde nicht verbraucht");
                }
                DRONE_ID.set(drone.getId());
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Platzieren fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone() || mc.level == null) {
            return StepResult.pending();
        }
        Entity entity = mc.level.getEntity(DRONE_ID.get());
        return entity instanceof QuadcopterEntity ? StepResult.pass() : StepResult.pending();
    }

    private static ServerPlayer serverPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static QuadcopterEntity serverDrone(ServerPlayer player) {
        if (!(player.level().getEntity(DRONE_ID.get()) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Drohne auf dem Server verschwunden");
        }
        return drone;
    }

    private static ItemStack remoteStack() {
        return new ItemStack(DroneModule.REMOTE.get());
    }

    private static void holdRemote(ServerPlayer player, QuadcopterEntity drone) {
        ItemStack held = player.getMainHandItem();
        if (!ContentIds.isRemote(held)) {
            held = remoteStack();
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }
        held.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
    }

    private static StepResult serverStep(SelfTest.StepContext ctx, String what, Consumer<MinecraftServer> action) {
        if (!ctx.state().containsKey(FUTURE)) {
            ctx.state().put(FUTURE, ctx.onServer(action));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult bindStep(SelfTest.StepContext ctx) {
        if (DRONE_ID.get() < 0) {
            return StepResult.fail("keine Drohne aus dem vorigen Schritt");
        }
        return serverStep(ctx, "Binden fehlgeschlagen", server -> {
            ServerPlayer player = serverPlayer(server);
            QuadcopterEntity drone = serverDrone(player);
            player.setShiftKeyDown(false);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            ItemStack remote = remoteStack();
            player.setItemInHand(InteractionHand.MAIN_HAND, remote);
            drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
            BoundDrone bound = remote.get(CoreComponents.BOUND_DRONE.get());
            if (bound == null || !bound.drone().equals(drone.getUUID())) {
                throw new IllegalStateException("Funke nicht gebunden: " + bound);
            }
            Optional<Item> analog = ContentIds.item(ContentIds.GOGGLES_ANALOG);
            if (analog.isPresent() && drone.build().video().system == VideoSystem.DIGITAL) {
                ItemStack goggles = new ItemStack(analog.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, goggles);
                drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
                if (goggles.has(CoreComponents.BOUND_DRONE.get())) {
                    throw new IllegalStateException("analoge Brille an digitale Drohne gebunden");
                }
                player.setItemSlot(EquipmentSlot.HEAD, goggles);
                ItemStack other = remoteStack();
                player.setItemInHand(InteractionHand.MAIN_HAND, other);
                drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                if (other.has(CoreComponents.BOUND_DRONE.get())) {
                    throw new IllegalStateException("Funke bindet mit analoger Brille an eine digitale Drohne");
                }
            }
            Optional<Item> creative = ContentIds.item(ContentIds.GOGGLES_CREATIVE);
            if (creative.isPresent()) {
                ItemStack goggles = new ItemStack(creative.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, goggles);
                drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
                if (goggles.has(CoreComponents.BOUND_DRONE.get())) {
                    throw new IllegalStateException("Kreativ-Brille wird noch gebunden");
                }
                BOUND_GOGGLES.set(goggles.copy());
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        });
    }

    private static StepResult craftNameStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (DRONE_ID.get() < 0) {
            return StepResult.fail("keine Drohne aus dem vorigen Schritt");
        }
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = serverPlayer(server);
                QuadcopterEntity drone = serverDrone(player);
                player.setShiftKeyDown(false);
                ItemStack remote = remoteStack();
                remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                player.getInventory().setItem(8, remote);
                ItemStack tag = new ItemStack(Items.NAME_TAG);
                tag.set(DataComponents.CUSTOM_NAME, Component.literal("Testflitzer"));
                player.setItemInHand(InteractionHand.MAIN_HAND, tag);
                InteractionResult result = drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
                if (!result.consumesAction() || drone.getCustomName() == null || !"Testflitzer".equals(drone.getCustomName().getString())) {
                    throw new IllegalStateException("Namensschild benennt die Drohne nicht: " + result + " / " + drone.getCustomName());
                }
                BoundDrone bound = player.getInventory().getItem(8).get(CoreComponents.BOUND_DRONE.get());
                if (bound == null || !"Testflitzer".equals(bound.name())) {
                    throw new IllegalStateException("gebundene Funke kennt den neuen Namen nicht: " + bound);
                }
                ItemStack held = new ItemStack(DroneModule.DRONE_ITEM.get());
                held.set(CoreComponents.DRONE_ID.get(), drone.getUUID());
                CraftNames.renameStack(player, held, "  Mein   Quad§c  ");
                Component heldName = held.get(DataComponents.CUSTOM_NAME);
                if (heldName == null || !"Mein Quad".equals(heldName.getString())) {
                    throw new IllegalStateException("Name des Drohnen-Items falsch bereinigt: " + heldName);
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.getInventory().setItem(8, ItemStack.EMPTY);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(future.exceptionNow().getMessage());
        }
        if (!future.isDone() || mc.level == null || !(mc.level.getEntity(DRONE_ID.get()) instanceof QuadcopterEntity drone)) {
            return StepResult.pending();
        }
        if (!ctx.state().containsKey("sent")) {
            if (drone.getCustomName() == null || !"Testflitzer".equals(drone.getCustomName().getString())) {
                return StepResult.pending();
            }
            ctx.state().put("sent", ClientNet.sendToServer(new CraftNameC2S(drone.getId(), false, "Konfig-Name")));
            return StepResult.pending();
        }
        if (!Boolean.TRUE.equals(ctx.state().get("sent"))) {
            return StepResult.fail("CraftNameC2S nicht gesendet");
        }
        if (!ctx.state().containsKey("cleared")) {
            if (drone.getCustomName() == null || !"Konfig-Name".equals(drone.getCustomName().getString())) {
                return StepResult.pending();
            }
            ctx.state().put("cleared", ClientNet.sendToServer(new CraftNameC2S(drone.getId(), false, "")));
            return StepResult.pending();
        }
        return drone.getCustomName() == null ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult freeRangeStep(SelfTest.StepContext ctx) {
        return serverStep(ctx, "Funkreichweite", server -> {
            ServerPlayer player = serverPlayer(server);
            ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD).copy();
            try {
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setGameMode(GameType.SURVIVAL);
                if (RadioLink.freeRange(player)) {
                    throw new IllegalStateException("Überleben ohne Kreativ-Brille hat freie Reichweite");
                }
                if (RadioLink.releaseRange(Double.POSITIVE_INFINITY, false) != RadioLink.RELEASE_RANGE) {
                    throw new IllegalStateException("Funk-Grenze fehlt im Überleben");
                }
                Optional<Item> creative = ContentIds.item(ContentIds.GOGGLES_CREATIVE);
                if (creative.isPresent()) {
                    player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(creative.get()));
                    if (!RadioLink.freeRange(player)) {
                        throw new IllegalStateException("Kreativ-Brille hebt die Funk-Grenze nicht auf");
                    }
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                }
                player.setGameMode(GameType.CREATIVE);
                if (!RadioLink.freeRange(player)) {
                    throw new IllegalStateException("Kreativmodus hebt die Funk-Grenze nicht auf");
                }
                double limit = ServerRules.maxPilotDistance(player);
                double release = RadioLink.releaseRange(limit, RadioLink.freeRange(player));
                if (ServerRules.unlimitedRange(player) && !(release > 5000.0)) {
                    throw new IllegalStateException("Kreativ trotz unbegrenzter Server-Reichweite begrenzt: " + release);
                }
                if (RadioLink.quality(3000.0, limit, true) < 1.0F) {
                    throw new IllegalStateException("Funkqualität fällt in 3000 Blöcken trotz freier Reichweite");
                }
            } finally {
                player.setGameMode(GameType.CREATIVE);
                player.setItemSlot(EquipmentSlot.HEAD, head);
            }
        });
    }

    private static StepResult remoteFirstPersonCheck() {
        FirstPersonHandsAndItemsRenderState hands = new FirstPersonHandsAndItemsRenderState();
        AvatarRenderState avatar = new AvatarRenderState();
        ItemStack remote = remoteStack();
        hands.mainHandItem = remote;
        hands.offHandItem = new ItemStack(Items.TORCH);
        if (RemoteFirstPerson.held(hands, avatar) != remote) {
            return StepResult.fail("Ego-Ansicht: Funke mit belegter Nebenhand nicht beidhändig");
        }
        hands.isScoping = true;
        if (!RemoteFirstPerson.held(hands, avatar).isEmpty()) {
            return StepResult.fail("Ego-Ansicht: Funke beim Fernrohr beidhändig");
        }
        hands.isScoping = false;
        avatar.isUsingItem = true;
        if (!RemoteFirstPerson.held(hands, avatar).isEmpty()) {
            return StepResult.fail("Ego-Ansicht: Funke beidhändig, während die Nebenhand etwas benutzt");
        }
        avatar.isUsingItem = false;
        hands.mainHandItem = ItemStack.EMPTY;
        hands.offHandItem = remote;
        if (RemoteFirstPerson.held(hands, avatar) != remote) {
            return StepResult.fail("Ego-Ansicht: Funke in der Nebenhand bei leerer Haupthand nicht beidhändig");
        }
        hands.mainHandItem = new ItemStack(Items.TORCH);
        if (!RemoteFirstPerson.held(hands, avatar).isEmpty()) {
            return StepResult.fail("Ego-Ansicht: Funke in der Nebenhand beidhändig, obwohl die Haupthand etwas hält");
        }
        return StepResult.pass();
    }

    private static StepResult remoteHandsStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            StepResult logic = remoteFirstPersonCheck();
            if (logic.status() != StepResult.Status.PASS) {
                return logic;
            }
            ctx.state().put("frames", RemoteFirstPerson.frames());
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = serverPlayer(server);
                player.setItemInHand(InteractionHand.MAIN_HAND, remoteStack());
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Funke in die Hand geben fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone() || mc.player == null || !ContentIds.isRemote(mc.player.getMainHandItem()) || mc.player.getOffhandItem().isEmpty()
                || ctx.tick() < 10) {
            return StepResult.pending();
        }
        int drawn = RemoteFirstPerson.frames() - (int) ctx.state().get("frames");
        if (drawn < 3) {
            if (ctx.tick() < 90) {
                return StepResult.pending();
            }
            ctx.onServer(server -> serverPlayer(server).setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));
            return StepResult.fail("Funke mit belegter Nebenhand nicht beidhändig gezeichnet (" + drawn + " Frames)");
        }
        ctx.onServer(server -> serverPlayer(server).setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));
        return StepResult.pass();
    }

    private static StepResult remoteThirdPersonStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            StepResult logic = remoteHoldCheck(mc);
            if (logic.status() != StepResult.Status.PASS) {
                return logic;
            }
            ctx.state().put(HOLD_CAMERA, mc.options.getCameraType());
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            ctx.onServer(server -> serverPlayer(server).setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH)));
            return StepResult.pending();
        }
        if (mc.player == null || !ContentIds.isRemote(mc.player.getMainHandItem()) || mc.player.getOffhandItem().isEmpty()) {
            return ctx.tick() < 60 ? StepResult.pending() : finishThirdPerson(mc, ctx, StepResult.fail("Funke und Fackel nicht in den Händen"));
        }
        ctx.state().putIfAbsent(HOLD_FRAMES, RemoteHoldPose.frames());
        ctx.state().putIfAbsent(HOLD_START, ctx.tick());
        int drawn = RemoteHoldPose.frames() - (int) ctx.state().get(HOLD_FRAMES);
        if (drawn >= 3 && ctx.tick() - (int) ctx.state().get(HOLD_START) >= 10) {
            return finishThirdPerson(mc, ctx, StepResult.pass());
        }
        if (ctx.tick() < 140) {
            return StepResult.pending();
        }
        return finishThirdPerson(mc, ctx, StepResult.fail("Funke in der dritten Person nicht mittig gezeichnet (" + drawn + " Frames)"));
    }

    private static StepResult finishThirdPerson(Minecraft mc, SelfTest.StepContext ctx, StepResult result) {
        Object saved = ctx.state().get(HOLD_CAMERA);
        mc.options.setCameraType(saved instanceof CameraType camera ? camera : CameraType.FIRST_PERSON);
        ctx.onServer(server -> serverPlayer(server).setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));
        return result;
    }

    private static StepResult remoteHoldCheck(Minecraft mc) {
        AvatarRenderState state = new AvatarRenderState();
        state.rightHandItemStack = remoteStack();
        state.leftHandItemStack = ItemStack.EMPTY;
        if (RemoteHoldPose.holdingArm(state) != HumanoidArm.RIGHT) {
            return StepResult.fail("Funke rechts bei freier linker Hand nicht beidhändig");
        }
        if (RemoteHoldPose.hidesItemIn(state, HumanoidArm.LEFT) || RemoteHoldPose.hidesItemIn(state, HumanoidArm.RIGHT)) {
            return StepResult.fail("leere Hand oder Funke selbst ausgeblendet");
        }
        state.leftHandItemStack = new ItemStack(Items.TORCH);
        if (RemoteHoldPose.holdingArm(state) != HumanoidArm.RIGHT || !RemoteHoldPose.hidesItemIn(state, HumanoidArm.LEFT)) {
            return StepResult.fail("Funke in der Haupthand nicht beidhändig oder Nebenhand-Item nicht ausgeblendet");
        }
        state.rightHandItemStack = ItemStack.EMPTY;
        state.leftHandItemStack = remoteStack();
        if (RemoteHoldPose.holdingArm(state) != HumanoidArm.LEFT) {
            return StepResult.fail("Funke links bei freier rechter Hand nicht beidhändig");
        }
        state.rightHandItemStack = new ItemStack(Items.TORCH);
        if (RemoteHoldPose.holdingArm(state) != null || RemoteHoldPose.hidesItemIn(state, HumanoidArm.RIGHT)) {
            return StepResult.fail("Funke in der Nebenhand beidhändig, obwohl die Haupthand etwas hält");
        }
        state.rightHandItemStack = ItemStack.EMPTY;
        state.isVisuallySwimming = true;
        if (RemoteHoldPose.holdingArm(state) != null) {
            return StepResult.fail("Funke beim Schwimmen beidhändig");
        }
        HumanoidRenderState mob = new HumanoidRenderState();
        mob.rightHandItemStack = remoteStack();
        if (RemoteHoldPose.holdingArm(mob) != null) {
            return StepResult.fail("Nicht-Spieler hält die Funke beidhändig (Rüstungsständer und Mobs nutzen das Display)");
        }
        AvatarRenderState pilot = new AvatarRenderState();
        pilot.rightHandItemStack = remoteStack();
        pilot.leftHandItemStack = ItemStack.EMPTY;
        PlayerModel model = mc.getEntityRenderDispatcher().getRenderer(pilot).getModel();
        model.setupAnim(pilot);
        if (Math.abs(model.rightArm.xRot - model.leftArm.xRot) > 1.0E-4F || Math.abs(model.rightArm.yRot + model.leftArm.yRot) > 1.0E-4F
                || model.rightArm.xRot > -0.6F || model.rightArm.yRot >= 0.0F) {
            return StepResult.fail(String.format(Locale.ROOT, "Armhaltung nicht symmetrisch nach vorn: rechts %.2f/%.2f, links %.2f/%.2f",
                    model.rightArm.xRot, model.rightArm.yRot, model.leftArm.xRot, model.leftArm.yRot));
        }
        PoseStack pose = new PoseStack();
        model.root().translateAndRotate(pose);
        RemoteHoldPose.transform(model, pose);
        Matrix4f matrix = pose.last().pose();
        Vector3f centre = matrix.transformPosition(0.0F, 0.0F, 0.0F, new Vector3f()).mul(16.0F);
        Vector3f side = matrix.transformDirection(1.0F, 0.0F, 0.0F, new Vector3f()).normalize();
        Vector3f top = matrix.transformDirection(0.0F, 1.0F, 0.0F, new Vector3f()).normalize();
        Vector3f face = matrix.transformDirection(0.0F, 0.0F, 1.0F, new Vector3f()).normalize();
        if (Math.abs(centre.x) > 0.05F || centre.z > -3.0F || centre.y < 3.0F || centre.y > 10.0F) {
            return StepResult.fail(String.format(Locale.ROOT, "Funke nicht mittig vor Brust/Bauch: (%.2f, %.2f, %.2f) px", centre.x, centre.y, centre.z));
        }
        if (Math.abs(side.y) > 0.02F || side.x > -0.99F) {
            return StepResult.fail(String.format(Locale.ROOT, "Funke verdreht: Querachse (%.2f, %.2f, %.2f)", side.x, side.y, side.z));
        }
        if (top.y > -0.3F || top.z > 0.0F || face.y > -0.5F || face.z < 0.3F) {
            return StepResult.fail(String.format(Locale.ROOT, "Neigung falsch: Oberkante (%.2f, %.2f, %.2f), Front (%.2f, %.2f, %.2f)",
                    top.x, top.y, top.z, face.x, face.y, face.z));
        }
        return StepResult.pass();
    }

    private static StepResult losStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = serverPlayer(server);
                QuadcopterEntity drone = serverDrone(player);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                holdRemote(player, drone);
                player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
                if (!drone.isFlownBy(player.getUUID()) || drone.pilotMode() != PilotMode.LINE_OF_SIGHT) {
                    throw new IllegalStateException("Sichtflug nicht gestartet: Pilot=" + drone.pilot() + ", Modus=" + drone.pilotMode());
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Funke startet nicht: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone() || !session.isLineOfSight() || mc.player == null) {
            return ctx.tick() > 150 ? StepResult.fail("keine Sichtflug-Sitzung auf dem Client") : StepResult.pending();
        }
        QuadcopterEntity drone = session.drone();
        if (drone == null) {
            return StepResult.fail("Sitzung ohne Drohne");
        }
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
        if (mc.getCameraEntity() != mc.player) {
            return StepResult.fail("Kamera im Sichtflug nicht beim Spieler");
        }
        if (VideoState.INSTANCE.active()) {
            return StepResult.fail("Videoverbindung im Sichtflug aktiv");
        }
        if (QuadcopterRenderer.isOwnFpvDrone(drone)) {
            return StepResult.fail("eigene Drohne im Sichtflug ausgeblendet");
        }
        if (!ctx.state().containsKey(MOVED)) {
            int id = drone.getId();
            ctx.state().put(MOVED, ctx.onServer(server -> {
                ServerPlayer player = serverPlayer(server);
                if (player.level().getEntity(id) instanceof QuadcopterEntity serverDrone) {
                    Vec3 side = player.position().add(0.0, 0.2, 0.0).add(player.getLookAngle().yRot((float) (Math.PI / 2.0)).multiply(3.0, 0.0, 3.0));
                    serverDrone.resetTo(side, serverDrone.orientation());
                }
            }));
            ctx.state().put(MOVED_TICK, ctx.tick());
            return StepResult.pending();
        }
        if (ctx.tick() - (int) ctx.state().get(MOVED_TICK) < 20) {
            return StepResult.pending();
        }
        Vector3d target = session.comPosition(new Vector3d());
        Vec3 eye = mc.player.getEyePosition();
        Vec3 wanted = new Vec3(target.x - eye.x, target.y - eye.y, target.z - eye.z).normalize();
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, wanted.dot(mc.player.getLookAngle())))));
        if (angle > 5.0) {
            return ctx.tick() > 180 ? StepResult.fail(String.format(Locale.ROOT, "Blick folgt der Drohne nicht (%.1f°)", angle))
                    : StepResult.pending();
        }
        ctx.log(String.format(Locale.ROOT, "Blick auf die Drohne (%.1f° Abweichung)", angle));
        return StepResult.pass();
    }

    private static StepResult stopRemoteStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        if (ctx.tick() == 0) {
            if (!session.isActive()) {
                return StepResult.fail("keine Sitzung zum Beenden");
            }
            session.clearTestInput();
            PilotView.onUseKeyWhilePiloting();
            return StepResult.pending();
        }
        if (session.isActive()) {
            return StepResult.fail("Sitzung läuft nach dem Funken-Klick weiter");
        }
        Entity entity = mc.level == null ? null : mc.level.getEntity(DRONE_ID.get());
        if (!(entity instanceof QuadcopterEntity drone) || drone.pilot().isPresent()) {
            return ctx.tick() > 100 ? StepResult.fail("Server hat die Pilotenrolle nicht gelöst") : StepResult.pending();
        }
        if (!ctx.state().containsKey(FUTURE)) {
            AtomicReference<String> result = new AtomicReference<>("");
            ctx.state().put(RESULT, result);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = serverPlayer(server);
                QuadcopterEntity serverDrone = serverDrone(player);
                if (serverDrone.pilotMode() != PilotMode.NONE) {
                    throw new IllegalStateException("Pilotenmodus nach dem Stopp: " + serverDrone.pilotMode());
                }
                player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
                if (serverDrone.pilot().isPresent()) {
                    throw new IllegalStateException("zweiter Klick innerhalb der Entprellzeit startet erneut");
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (!ctx.state().containsKey(WAIT_UNTIL)) {
            ctx.state().put(WAIT_UNTIL, ctx.tick() + DronePiloting.REMOTE_DEBOUNCE_TICKS + 2);
        }
        return ctx.tick() >= (int) ctx.state().get(WAIT_UNTIL) ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult fpvStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        if (ctx.tick() == 0) {
            AtomicReference<String> result = new AtomicReference<>("");
            ctx.state().put(RESULT, result);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = serverPlayer(server);
                QuadcopterEntity drone = serverDrone(player);
                player.setItemSlot(EquipmentSlot.HEAD, fpvGoggles(drone));
                holdRemote(player, drone);
                player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
                if (!drone.isFlownBy(player.getUUID())) {
                    throw new IllegalStateException("Pilotenrolle verweigert");
                }
                result.set(drone.pilotMode().name());
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("FPV-Start fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (!PilotMode.FPV.name().equals(result(ctx))) {
            return StepResult.fail("mit gebundener Brille kein FPV: " + result(ctx));
        }
        QuadcopterEntity drone = session.drone();
        if (drone == null || drone.getId() != DRONE_ID.get() || !session.isFpv() || mc.getCameraEntity() != drone) {
            return StepResult.pending();
        }
        return StepResult.pass();
    }

    static ItemStack fpvGoggles(QuadcopterEntity drone) {
        ItemStack goggles = BOUND_GOGGLES.get();
        if (goggles.isEmpty()) {
            goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        }
        return goggles.copy();
    }

    private static StepResult gogglesOffStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        if (!session.isActive()) {
            return StepResult.fail("keine aktive Sitzung");
        }
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> serverPlayer(server).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY)));
            return StepResult.pending();
        }
        if (session.isLineOfSight() && mc.getCameraEntity() == mc.player) {
            ctx.log("Brille abgesetzt nach " + ctx.tick() + " Ticks im Sichtflug");
            return StepResult.pass();
        }
        return ctx.tick() > 40 ? StepResult.fail("nach dem Absetzen der Brille kein Sichtflug") : StepResult.pending();
    }

    private static StepResult batteryStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = serverPlayer(server);
                QuadcopterEntity drone = serverDrone(player);
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                player.getInventory().clearContent();
                drone.insertBattery(BatteryType.B6S_1300, 0.8F);
                player.setShiftKeyDown(true);
                ItemStack swap = Batteries.stackOf(BatteryType.B6S_1100, 0.5F);
                player.setItemInHand(InteractionHand.MAIN_HAND, swap);
                drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
                if (drone.build().battery() != BatteryType.B6S_1100 || Math.abs(drone.batteryCharge() - 0.5F) > 0.01F) {
                    throw new IllegalStateException("Schnelltausch fehlgeschlagen: " + drone.build().battery() + " " + drone.batteryCharge());
                }
                boolean oldFound = false;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (Batteries.typeOf(stack).orElse(null) == BatteryType.B6S_1300 && Math.abs(Batteries.charge(stack) - 0.8F) < 0.01F) {
                        oldFound = true;
                    }
                }
                if (!oldFound) {
                    throw new IllegalStateException("alter Akku mit Ladestand nicht im Inventar");
                }
                ItemStack whoop = Batteries.stackOf(BatteryType.B1S_300_HV, 1.0F);
                player.setItemInHand(InteractionHand.MAIN_HAND, whoop);
                drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
                if (drone.build().battery() != BatteryType.B6S_1100 || whoop.isEmpty()) {
                    throw new IllegalStateException("unpassender Akku wurde eingesetzt");
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
                if (!(player.containerMenu instanceof DroneServiceMenu menu)) {
                    throw new IllegalStateException("Wartungsmenü nicht geöffnet: " + player.containerMenu);
                }
                menu.quickMoveStack(player, 0);
                player.closeContainer();
                player.setShiftKeyDown(false);
                if (drone.hasBattery()) {
                    throw new IllegalStateException("Akku über das Menü nicht entnommen");
                }
                if (DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT) != DronePiloting.StartResult.NO_BATTERY) {
                    throw new IllegalStateException("Start ohne Akku nicht verweigert");
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        Entity entity = mc.level == null ? null : mc.level.getEntity(DRONE_ID.get());
        if (entity instanceof QuadcopterEntity drone && !drone.hasBattery()) {
            return StepResult.pass();
        }
        return ctx.tick() > 60 ? StepResult.fail("Client sieht die Drohne weiter mit Akku") : StepResult.pending();
    }

    private static StepResult pickupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = serverPlayer(server);
                QuadcopterEntity drone = serverDrone(player);
                UUID id = drone.getUUID();
                FlightTune tune = FlightTune.EMPTY.with("pid.roll.p", 55.0F);
                drone.setTune(tune);
                player.getInventory().clearContent();
                player.setShiftKeyDown(false);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
                if (!drone.isRemoved()) {
                    throw new IllegalStateException("Drohne nicht aufgehoben");
                }
                ItemStack item = ItemStack.EMPTY;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    if (ContentIds.is(player.getInventory().getItem(slot), ContentIds.DRONE)) {
                        item = player.getInventory().getItem(slot);
                    }
                }
                if (item.isEmpty() || !id.equals(item.get(CoreComponents.DRONE_ID.get()))
                        || !Boolean.FALSE.equals(item.get(CoreComponents.BATTERY_INSTALLED.get()))
                        || !tune.equals(item.get(CoreComponents.FLIGHT_TUNE.get()))) {
                    throw new IllegalStateException("Drohnen-Item unvollständig: " + item.getComponents());
                }
                ItemStack placed = item.copy();
                player.getInventory().clearContent();
                player.setItemInHand(InteractionHand.MAIN_HAND, placed);
                ServerLevel level = player.level();
                int x = player.getBlockX() + 3;
                int z = player.getBlockZ();
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos ground = new BlockPos(x, top - 1, z);
                placed.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(x + 0.5, top, z + 0.5), Direction.UP, ground, false)));
                if (!(level.getEntity(id) instanceof QuadcopterEntity again)) {
                    throw new IllegalStateException("Wieder aufgestellte Drohne hat eine neue Id");
                }
                DRONE_ID.set(again.getId());
                if (again.hasBattery()) {
                    throw new IllegalStateException("Drohne ohne Akku hat nach dem Aufstellen wieder einen Akku");
                }
                again.insertBattery(BatteryType.B6S_1300, 1.0F);
                ItemStack remote = remoteStack();
                remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(again));
                player.setItemInHand(InteractionHand.MAIN_HAND, remote);
                DronePiloting.StartResult result = DronePiloting.tryStart(player, again, DronePiloting.decideMode(player, again).mode());
                if (result != DronePiloting.StartResult.STARTED) {
                    throw new IllegalStateException("Funke startet die wieder aufgestellte Drohne nicht: " + result);
                }
                DronePiloting.release(again);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(future.exceptionNow().getMessage());
        }
        return future.isDone() && !PilotSession.INSTANCE.isActive() ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult noRechargeStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> serverDrone(serverPlayer(server)).setBatteryCharge(0.4F)));
            return StepResult.pending();
        }
        if (ctx.tick() < 110) {
            return StepResult.pending();
        }
        if (!ctx.state().containsKey(RESULT)) {
            AtomicReference<String> result = new AtomicReference<>("");
            ctx.state().put(RESULT, result);
            ctx.state().put(FUTURE, ctx.onServer(server -> result.set(Float.toString(serverDrone(serverPlayer(server)).batteryCharge()))));
            return StepResult.pending();
        }
        if (!future(ctx).isDone()) {
            return StepResult.pending();
        }
        String charge = result(ctx);
        if (charge.isEmpty() || Math.abs(Float.parseFloat(charge) - 0.4F) > 1.0E-4F) {
            return StepResult.fail("Ladestand hat sich ohne Ladegerät verändert: " + charge);
        }
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        return serverStep(ctx, "Aufräumen fehlgeschlagen", server -> {
            int id = DRONE_ID.get();
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    e.discard();
                }
            }
            ServerPlayer player = serverPlayer(server);
            player.getInventory().clearContent();
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setShiftKeyDown(false);
        });
    }

    private static StepResult climbStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        if (sim == null) {
            return StepResult.fail("keine aktive Sitzung");
        }
        int tick = ctx.tick();
        if (tick < 10) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
            return StepResult.pending();
        }
        if (tick < 30) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
            if (tick == 29 && !sim.telemetry().armed) {
                return StepResult.fail("nicht scharf geschaltet, Sperren=" + sim.telemetry().armBlockers);
            }
            if (sim.telemetry().armed && !ctx.state().containsKey(START_Y)) {
                ctx.state().put(START_Y, session.comPosition(new Vector3d()).y);
            }
            return StepResult.pending();
        }
        if (!sim.telemetry().armed) {
            return StepResult.fail("während des Steigens entschärft: " + sim.telemetry().lastDisarmReason);
        }
        double startY = (double) ctx.state().getOrDefault(START_Y, session.comPosition(new Vector3d()).y);
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.72F), true, StabMode.ANGLE);
        double height = session.comPosition(new Vector3d()).y - startY;
        if (height < CLIMB_TARGET) {
            return StepResult.pending();
        }
        QuadcopterEntity drone = session.drone();
        if (drone == null) {
            return StepResult.fail("Drohne verloren");
        }
        if (!ctx.state().containsKey(FUTURE)) {
            AtomicReference<String> serverY = new AtomicReference<>("");
            ctx.state().put(RESULT, serverY);
            int id = drone.getId();
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                Entity entity = player.level().getEntity(id);
                serverY.set(entity == null ? "" : Double.toString(entity.getY()));
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        String serverY = result(ctx);
        if (serverY.isEmpty()) {
            return StepResult.fail("Drohne auf dem Server nicht gefunden");
        }
        double clientStartFeet = startY + AirframeSpec.of(drone.build()).hitboxFootOffset;
        if (Double.parseDouble(serverY) < clientStartFeet + 2.0) {
            return tick > 500 ? StepResult.fail("Server übernimmt die Flugposition nicht: y=" + serverY) : StepResult.pending();
        }
        ctx.log(String.format(java.util.Locale.ROOT, "gestiegen um %.1f m, Server y=%s", height, serverY));
        return StepResult.pass();
    }

    private static StepResult cameraClockStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        VideoConfig video = VideoSettings.get();
        if (ctx.tick() == 0) {
            ctx.state().put(CAMERA_LATENCY, video.creativeLatencyMs);
            ctx.state().put(CAMERA_WORST, new double[2]);
            ctx.state().put(CAMERA_MOVING, new AtomicInteger());
            video.creativeLatencyMs = CAMERA_TEST_LATENCY_MS;
        }
        if (sim == null || !session.isFpv()) {
            return restoreCameraLatency(ctx, StepResult.fail("keine FPV-Sitzung"));
        }
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.35F), true, StabMode.ANGLE);
        if (ctx.tick() < CAMERA_SETTLE_TICKS) {
            return StepResult.pending();
        }
        Vector3d position = new Vector3d();
        Quaternionf rotation = new Quaternionf();
        if (!FpvView.cameraPose(position, rotation)) {
            return restoreCameraLatency(ctx, StepResult.fail("keine FPV-Kamerapose"));
        }
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 shown = camera.position();
        double offset = Math.sqrt(position.distanceSquared(shown.x, shown.y, shown.z));
        Quaternionf delta = new Quaternionf(rotation).conjugate().mul(camera.rotation());
        double angle = 2.0 * Math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z);
        double[] worst = (double[]) ctx.state().get(CAMERA_WORST);
        worst[0] = Math.max(worst[0], offset);
        worst[1] = Math.max(worst[1], angle);
        AtomicInteger moving = (AtomicInteger) ctx.state().get(CAMERA_MOVING);
        if (sim.telemetry().speed > 1.0) {
            moving.incrementAndGet();
        }
        if (ctx.tick() < CAMERA_SETTLE_TICKS + CAMERA_SAMPLE_TICKS) {
            return StepResult.pending();
        }
        long latencyMs = VideoState.INSTANCE.latencyNanosAt(session.physicsFrameNanos()) / 1_000_000L;
        String summary = String.format(Locale.ROOT, "Latenz %d ms, %d Ticks in Bewegung, Abweichung %.2e m / %.2e rad",
                latencyMs, moving.get(), worst[0], worst[1]);
        if (latencyMs < CAMERA_TEST_LATENCY_MS / 2) {
            return restoreCameraLatency(ctx, StepResult.fail("Testlatenz greift nicht: " + summary));
        }
        if (moving.get() < CAMERA_SAMPLE_TICKS / 2) {
            return restoreCameraLatency(ctx, StepResult.fail("Drohne bewegt sich nicht: " + summary));
        }
        if (worst[0] > 1.0E-6 || worst[1] > 1.0E-5) {
            return restoreCameraLatency(ctx, StepResult.fail("Kamera hängt an der Uhrzeit statt an der Frame-Zeit: " + summary));
        }
        ctx.log(summary);
        return restoreCameraLatency(ctx, StepResult.pass());
    }

    private static StepResult restoreCameraLatency(SelfTest.StepContext ctx, StepResult result) {
        Object latency = ctx.state().get(CAMERA_LATENCY);
        if (latency instanceof Integer value) {
            VideoSettings.get().creativeLatencyMs = value;
        }
        return result;
    }

    private static StepResult crashStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        if (sim == null) {
            return StepResult.fail("keine aktive Sitzung");
        }
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
        if (ctx.tick() < 5) {
            return StepResult.pending();
        }
        if (sim.telemetry().armed) {
            return ctx.tick() > 40 ? StepResult.fail("Entschärfen per Schalter wirkt nicht") : StepResult.pending();
        }
        boolean crashed = session.crashCount() > 0 || sim.telemetry().crashCount > 0;
        if (!crashed) {
            return StepResult.pending();
        }
        if (!sim.telemetry().grounded && sim.telemetry().speed > 0.5) {
            return StepResult.pending();
        }
        ctx.log(String.format(java.util.Locale.ROOT, "Absturz mit %.1f m/s erkannt", sim.telemetry().lastImpactSpeed));
        return StepResult.pass();
    }

    private static StepResult bodyStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        if (drone == null || mc.player == null) {
            return StepResult.fail("keine aktive Sitzung");
        }
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
        if (ctx.tick() == 0) {
            int id = drone.getId();
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                if (!(player.level().getEntity(id) instanceof QuadcopterEntity serverDrone)) {
                    throw new IllegalStateException("Drohne auf dem Server verschwunden");
                }
                Vec3 target = player.position().add(BODY_VIEW_DISTANCE, 0.6, 0.0);
                serverDrone.resetTo(target, new Quaternionf().rotationY((float) (Math.PI / 2.0)));
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Drohne nicht vor den Piloten gesetzt: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (!ctx.state().containsKey(FRAMES)) {
            ctx.state().put(FRAMES, PilotBodyPose.posedFrames());
            return StepResult.pending();
        }
        long frames = PilotBodyPose.posedFrames() - (long) ctx.state().get(FRAMES);
        if (frames < BODY_FRAMES) {
            return ctx.tick() > 180 ? StepResult.fail("Pilotenkörper aus Sicht der Drohne nicht gezeichnet (" + frames + " Frames, Kamera "
                    + (mc.getCameraEntity() == drone ? "an der Drohne" : "nicht an der Drohne") + ")") : StepResult.pending();
        }
        if (!PilotBodyPose.lastNameHidden()) {
            return StepResult.fail("Namensschild über dem eigenen Körper sichtbar");
        }
        boolean goggles = !ContentIds.wornGoggles(mc.player).isEmpty();
        if (goggles && !PilotBodyPose.lastHeadItemVisible()) {
            return StepResult.fail("Brille wird nicht als Modell auf dem Kopf des Piloten gezeichnet");
        }
        ctx.log("Pilotenkörper in " + frames + " Frames gezeichnet" + (goggles ? ", Brille auf dem Kopf" : ""));
        return StepResult.pass();
    }

    private static StepResult stopStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        if (ctx.tick() == 0) {
            session.clearTestInput();
            if (!session.isActive()) {
                return StepResult.fail("keine aktive Sitzung zum Beenden");
            }
            session.requestStop();
            return StepResult.pending();
        }
        if (session.isActive()) {
            return StepResult.fail("Sitzung läuft nach dem Beenden weiter");
        }
        if (mc.player == null || mc.getCameraEntity() != mc.player) {
            return StepResult.pending();
        }
        Entity entity = mc.level == null ? null : mc.level.getEntity(DRONE_ID.get());
        if (entity instanceof QuadcopterEntity drone && drone.pilot().isPresent()) {
            return StepResult.pending();
        }
        return StepResult.pass();
    }

    private static CompletableFuture<?> future(SelfTest.StepContext ctx) {
        return (CompletableFuture<?>) ctx.state().get(FUTURE);
    }

    @SuppressWarnings("unchecked")
    private static String result(SelfTest.StepContext ctx) {
        Object value = ctx.state().get(RESULT);
        return value instanceof AtomicReference<?> ref ? String.valueOf(((AtomicReference<String>) ref).get()) : "";
    }
}
