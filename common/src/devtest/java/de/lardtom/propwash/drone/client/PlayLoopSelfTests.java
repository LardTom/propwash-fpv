package de.lardtom.propwash.drone.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.BatteryChargerBlock;
import de.lardtom.propwash.content.BatteryChargerBlockEntity;
import de.lardtom.propwash.content.ChargerState;
import de.lardtom.propwash.content.ContentBlocks;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.ContentMaterials;
import de.lardtom.propwash.content.CraftingMaterial;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.content.Recycling;
import de.lardtom.propwash.core.BoundDrone;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.DroneServiceMenu;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

final class PlayLoopSelfTests {
    private static final int SLOT_GOGGLES = 0;
    private static final int SLOT_REMOTE = 1;
    private static final int SLOT_BATTERY = 2;
    private static final int SLOT_WRONG_BATTERY = 3;
    private static final int SLOT_PROPS = 7;
    private static final int SLOT_WRONG_PROPS = 8;
    private static final float SWAP_CHARGE = 0.5F;
    private static final double MAX_LOOK_ERROR = 10.0;
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final AtomicReference<UUID> DRONE_UUID = new AtomicReference<>();
    private static final AtomicReference<BlockPos> CHARGER = new AtomicReference<>();
    private static final String PHASE = "phase";
    private static final String FUTURE = "future";
    private static final String RESULT = "result";
    private static final String SINCE = "since";
    private static final String START = "start";
    private static final String WORST = "worst";
    private static final String TURNED = "turned";

    private PlayLoopSelfTests() {
    }

    static void register() {
        SelfTest.clientStep("drone: Spielablauf – Drohne setzen und anvisieren", 200, PlayLoopSelfTests::setupStep);
        SelfTest.clientStep("drone: Spielablauf – Rechtsklick mit der Brille prüft nur, sie bleibt ungebunden in der Hand", 120,
                ctx -> bindStep(ctx, SLOT_GOGGLES, "Brille"));
        SelfTest.clientStep("drone: Spielablauf – Funke per Rechtsklick binden", 120, ctx -> bindStep(ctx, SLOT_REMOTE, "Funke"));
        SelfTest.clientStep("drone: Spielablauf – Brille aufsetzen, Rechtsklick mit der Funke startet FPV", 240, PlayLoopSelfTests::fpvStep);
        SelfTest.clientStep("drone: Spielablauf – Rechtsklick beendet den Flug", 160, PlayLoopSelfTests::stopStep);
        SelfTest.clientStep("drone: Spielablauf – ohne Brille Sichtflug, Blick folgt immer der Drohne", 400, PlayLoopSelfTests::losStep);
        SelfTest.clientStep("drone: Spielablauf – Shift-Rechtsklick tauscht den Akku", 400, PlayLoopSelfTests::swapStep);
        SelfTest.clientStep("drone: Spielablauf – Wartungsmenü tauscht beschädigte Props, zu große werden abgelehnt", 400, PlayLoopSelfTests::propServiceStep);
        SelfTest.clientStep("drone: Spielablauf – Werkbank behält den Zustand, kaputte Teile werden recycelt", 100,
                PlayLoopSelfTests::workbenchWearStep);
        SelfTest.clientStep("drone: Spielablauf – Ladegerät lädt den alten Akku", 200, PlayLoopSelfTests::chargerStep);
        SelfTest.clientStep("drone: Spielablauf – Aufräumen", 100, PlayLoopSelfTests::cleanupStep);
    }

    private static ServerPlayer serverPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static QuadcopterEntity serverDrone(ServerPlayer player) {
        if (!(player.level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Drohne auf dem Server verschwunden");
        }
        return drone;
    }

    private static QuadcopterEntity clientDrone(Minecraft mc) {
        return mc.level != null && mc.level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone ? drone : null;
    }

    private static void select(ServerPlayer player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        player.connection.send(new ClientboundSetHeldSlotPacket(slot));
    }

    private static void clickUse(Minecraft mc) {
        KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
    }

    private static void turn(LocalPlayer player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
    }

    private static void lookAt(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        turn(player, (float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private static boolean aimsAt(Minecraft mc, Entity entity) {
        return mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == entity;
    }

    private static boolean aimsAtSky(Minecraft mc) {
        return mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS;
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

    private static StepResult serverCheck(SelfTest.StepContext ctx, String what, Function<MinecraftServer, String> check) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            AtomicReference<String> result = new AtomicReference<>(null);
            state.put(RESULT, result);
            state.put(FUTURE, ctx.onServer(server -> result.set(check.apply(server))));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        String problem = ((AtomicReference<String>) state.get(RESULT)).get();
        state.remove(FUTURE);
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            mc.options.keyShift.setDown(false);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = serverPlayer(server);
                ServerLevel level = player.level();
                player.closeContainer();
                player.getInventory().clearContent();
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.setShiftKeyDown(false);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                int x = player.getBlockX() - 12;
                int z = player.getBlockZ();
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                player.teleportTo(level, x + 0.5, top, z + 0.5, Set.of(), 0.0F, 30.0F, true);
                ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
                stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z + 2) - 1, z + 2);
                InteractionResult placed = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false)));
                if (!placed.consumesAction()) {
                    throw new IllegalStateException("Drohne nicht platziert: " + placed);
                }
                List<QuadcopterEntity> drones = level.getEntitiesOfClass(QuadcopterEntity.class, new AABB(ground.above()).inflate(1.0));
                if (drones.size() != 1) {
                    throw new IllegalStateException("erwartet eine Drohne, gefunden " + drones.size());
                }
                QuadcopterEntity drone = drones.getFirst();
                if (drone.build().battery() != BatteryType.B6S_1300 || !drone.hasBattery()) {
                    throw new IllegalStateException("FREESTYLE ohne 6S-1300-Akku: " + drone.build().battery());
                }
                DRONE.set(drone.getId());
                DRONE_UUID.set(drone.getUUID());
                Inventory inventory = player.getInventory();
                inventory.clearContent();
                inventory.setItem(SLOT_GOGGLES, ContentIds.item(ContentIds.GOGGLES_DIGITAL).map(ItemStack::new).orElseThrow());
                inventory.setItem(SLOT_REMOTE, new ItemStack(DroneModule.REMOTE.get()));
                inventory.setItem(SLOT_BATTERY, Batteries.stackOf(BatteryType.B6S_1100, SWAP_CHARGE));
                inventory.setItem(SLOT_WRONG_BATTERY, Batteries.stackOf(BatteryType.B1S_300_HV, 1.0F));
                select(player, SLOT_GOGGLES);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufbau: " + future.exceptionNow().getMessage());
        }
        QuadcopterEntity drone = clientDrone(mc);
        if (!future.isDone() || drone == null || mc.player == null) {
            return StepResult.pending();
        }
        lookAt(mc.player, drone.getBoundingBox().getCenter());
        if (!ContentIds.isGoggles(mc.player.getMainHandItem()) || !aimsAt(mc, drone)) {
            return ctx.tick() > 180 ? StepResult.fail("Drohne nicht im Fadenkreuz: " + mc.hitResult + ", Hand " + mc.player.getMainHandItem())
                    : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static StepResult bindStep(SelfTest.StepContext ctx, int slot, String what) {
        Minecraft mc = Minecraft.getInstance();
        QuadcopterEntity drone = clientDrone(mc);
        if (drone == null || mc.player == null) {
            return StepResult.fail("keine Drohne vor dem Spieler");
        }
        switch (phase(ctx)) {
            case 0 -> {
                mc.player.getInventory().setSelectedSlot(slot);
                lookAt(mc.player, drone.getBoundingBox().getCenter());
                ItemStack held = mc.player.getMainHandItem();
                boolean right = slot == SLOT_GOGGLES ? ContentIds.isGoggles(held) : ContentIds.isRemote(held);
                if (!right || !aimsAt(mc, drone)) {
                    return ctx.tick() > 80 ? StepResult.fail(what + " nicht in der Hand oder Drohne nicht anvisiert") : StepResult.pending();
                }
                clickUse(mc);
                next(ctx, 1);
                return StepResult.pending();
            }
            default -> {
                lookAt(mc.player, drone.getBoundingBox().getCenter());
                if (since(ctx) < 5) {
                    return StepResult.pending();
                }
                UUID expected = DRONE_UUID.get();
                StepResult check = serverCheck(ctx, what + " binden", server -> {
                    ItemStack stack = serverPlayer(server).getInventory().getItem(slot);
                    BoundDrone bound = stack.get(CoreComponents.BOUND_DRONE.get());
                    if (slot == SLOT_GOGGLES) {
                        if (!ContentIds.isGoggles(stack) || !ContentIds.wornGoggles(serverPlayer(server)).isEmpty()) {
                            return "Rechtsklick auf die Drohne setzt die Brille auf, statt sie zu prüfen";
                        }
                        return bound == null ? null : "Brille wird noch gebunden: " + bound;
                    }
                    return bound != null && bound.drone().equals(expected) ? null : what + " nach dem Rechtsklick nicht gebunden: " + bound;
                });
                if (check.status() == StepResult.Status.FAIL && since(ctx) < 60) {
                    return StepResult.pending();
                }
                return check;
            }
        }
    }

    private static StepResult fpvStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        switch (phase(ctx)) {
            case 0 -> {
                mc.player.getInventory().setSelectedSlot(SLOT_GOGGLES);
                turn(mc.player, 0.0F, -80.0F);
                if (!ContentIds.isGoggles(mc.player.getMainHandItem()) || !aimsAtSky(mc)) {
                    return ctx.tick() > 60 ? StepResult.fail("Brille nicht in der Hand oder Blick nicht in den Himmel") : StepResult.pending();
                }
                clickUse(mc);
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                turn(mc.player, 0.0F, -80.0F);
                ItemStack worn = ContentIds.wornGoggles(mc.player);
                if (worn.isEmpty()) {
                    return since(ctx) > 60 ? StepResult.fail("Rechtsklick mit der Brille setzt sie nicht auf") : StepResult.pending();
                }
                if (ContentIds.boundDrone(worn).isPresent()) {
                    return StepResult.fail("aufgesetzte Brille trägt eine Bindung");
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                mc.player.getInventory().setSelectedSlot(SLOT_REMOTE);
                turn(mc.player, 0.0F, -80.0F);
                if (!ContentIds.isRemote(mc.player.getMainHandItem()) || !aimsAtSky(mc) || since(ctx) < DronePiloting.REMOTE_DEBOUNCE_TICKS + 2) {
                    return since(ctx) > 80 ? StepResult.fail("Funke nicht in der Hand") : StepResult.pending();
                }
                clickUse(mc);
                next(ctx, 3);
                return StepResult.pending();
            }
            default -> {
                QuadcopterEntity drone = session.drone();
                if (!session.isFpv() || drone == null || !drone.getUUID().equals(DRONE_UUID.get()) || mc.getCameraEntity() != drone) {
                    return since(ctx) > 150 ? StepResult.fail("Rechtsklick mit der Funke startet kein FPV (Modus " + session.mode()
                            + ", Kamera " + mc.getCameraEntity() + ")") : StepResult.pending();
                }
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                ctx.log("FPV nach " + since(ctx) + " Ticks, Brille auf dem Kopf, Funke in der Hand");
                return StepResult.pass();
            }
        }
    }

    private static StepResult stopStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        switch (phase(ctx)) {
            case 0 -> {
                if (!session.isActive()) {
                    return StepResult.fail("keine Sitzung zum Beenden");
                }
                session.clearTestInput();
                clickUse(mc);
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                QuadcopterEntity drone = clientDrone(mc);
                if (session.isActive() || drone == null || drone.pilot().isPresent() || mc.getCameraEntity() != mc.player) {
                    return since(ctx) > 100 ? StepResult.fail("Rechtsklick beendet den Flug nicht (Sitzung " + session.mode() + ")")
                            : StepResult.pending();
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            default -> {
                return since(ctx) > PilotView.USE_LATCH_TICKS + DronePiloting.REMOTE_DEBOUNCE_TICKS + 2 && !PilotView.useLatched()
                        ? StepResult.pass() : StepResult.pending();
            }
        }
    }

    private static double lookError(Minecraft mc, PilotSession session) {
        Vector3d target = session.comPosition(new Vector3d());
        Vec3 eye = mc.player.getEyePosition();
        Vec3 wanted = new Vec3(target.x - eye.x, target.y - eye.y, target.z - eye.z).normalize();
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, wanted.dot(mc.player.getLookAngle())))));
    }

    private static StepResult losStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        Map<String, Object> state = ctx.state();
        switch (phase(ctx)) {
            case 0 -> {
                if (!state.containsKey(FUTURE)) {
                    state.put(FUTURE, ctx.onServer(server -> {
                        ServerPlayer player = serverPlayer(server);
                        ItemStack goggles = player.getItemBySlot(EquipmentSlot.HEAD).copy();
                        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        player.getInventory().setItem(SLOT_GOGGLES, goggles);
                    }));
                }
                if (!ContentIds.wornGoggles(mc.player).isEmpty()) {
                    return ctx.tick() > 60 ? StepResult.fail("Brille lässt sich nicht absetzen") : StepResult.pending();
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                mc.player.getInventory().setSelectedSlot(SLOT_REMOTE);
                turn(mc.player, 0.0F, -80.0F);
                if (!ContentIds.isRemote(mc.player.getMainHandItem()) || !aimsAtSky(mc)) {
                    return since(ctx) > 60 ? StepResult.fail("Funke nicht in der Hand") : StepResult.pending();
                }
                clickUse(mc);
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (!session.isLineOfSight()) {
                    return since(ctx) > 120 ? StepResult.fail("ohne Brille startet kein Sichtflug (Modus " + session.mode() + ")")
                            : StepResult.pending();
                }
                if (mc.getCameraEntity() != mc.player || !mc.options.getCameraType().isFirstPerson()) {
                    return StepResult.fail("Sichtflug nicht aus der Ego-Perspektive des Spielers");
                }
                if (!PilotView.active()) {
                    return StepResult.fail("Maus-Sperre im Sichtflug nicht aktiv");
                }
                state.put(START, session.comPosition(new Vector3d()));
                state.put(WORST, 0.0);
                next(ctx, 3);
                return StepResult.pending();
            }
            default -> {
                int t = since(ctx);
                if (!session.isLineOfSight()) {
                    return StepResult.fail("Sichtflug endete nach " + t + " Ticks");
                }
                if (t < 20) {
                    session.setTestInput(StickPositions.IDLE, true, StabMode.ANGLE);
                } else if (t < 55) {
                    session.setTestInput(new StickPositions(0.25F, 0.0F, 0.0F, 0.4F), true, StabMode.ANGLE);
                } else {
                    session.setTestInput(new StickPositions(-0.3F, 0.0F, 0.2F, 0.32F), true, StabMode.ANGLE);
                }
                double error = lookError(mc, session);
                boolean afterTurn = state.containsKey(TURNED) && ctx.tick() - (int) state.get(TURNED) < 15;
                if (t >= 10 && !afterTurn) {
                    state.put(WORST, Math.max((double) state.get(WORST), error));
                    if (error > MAX_LOOK_ERROR) {
                        return StepResult.fail(String.format(Locale.ROOT, "Blick verliert die Drohne: %.1f° nach %d Ticks", error, t));
                    }
                }
                if (t == 45) {
                    mc.player.turn(600.0, -200.0);
                    state.put(TURNED, ctx.tick());
                }
                if (t < 110) {
                    return StepResult.pending();
                }
                Vector3d start = (Vector3d) state.get(START);
                double moved = session.comPosition(new Vector3d()).distance(start);
                if (moved < 2.0) {
                    return StepResult.fail(String.format(Locale.ROOT, "Drohne hat sich im Sichtflug kaum bewegt (%.1f m)", moved));
                }
                ctx.log(String.format(Locale.ROOT, "Sichtflug: %.1f m geflogen, größte Blickabweichung %.1f°, nach Mausruck wieder auf der Drohne",
                        moved, (double) state.get(WORST)));
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                return StepResult.pass();
            }
        }
    }

    private static StepResult swapStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        switch (phase(ctx)) {
            case 0 -> {
                if (session.isActive()) {
                    session.clearTestInput();
                    clickUse(mc);
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (session.isActive() || PilotView.useLatched()) {
                    return since(ctx) > 120 ? StepResult.fail("Sichtflug endet nicht per Rechtsklick") : StepResult.pending();
                }
                StepResult landed = serverCheck(ctx, "Landung", server -> {
                    QuadcopterEntity drone = serverDrone(serverPlayer(server));
                    return drone.onGround() && drone.pilot().isEmpty() && !drone.isArmed() ? null : "fliegt noch";
                });
                if (landed.status() == StepResult.Status.FAIL) {
                    if (since(ctx) > 200) {
                        return StepResult.fail("Drohne landet nicht");
                    }
                    return StepResult.pending();
                }
                if (landed.status() == StepResult.Status.PENDING) {
                    return landed;
                }
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = serverPlayer(server);
                    QuadcopterEntity drone = serverDrone(player);
                    Vec3 stand = drone.position().add(0.0, 0.0, -2.0);
                    player.teleportTo(player.level(), stand.x, stand.y, stand.z, Set.of(), 0.0F, 40.0F, true);
                }));
                ctx.state().put(PHASE, 2);
                ctx.state().put(SINCE, ctx.tick());
                return StepResult.pending();
            }
            case 2 -> {
                CompletableFuture<?> moved = (CompletableFuture<?>) ctx.state().get(FUTURE);
                QuadcopterEntity drone = clientDrone(mc);
                if (moved == null || !moved.isDone() || drone == null) {
                    return StepResult.pending();
                }
                mc.player.getInventory().setSelectedSlot(SLOT_BATTERY);
                mc.options.keyShift.setDown(true);
                lookAt(mc.player, drone.getBoundingBox().getCenter());
                if (!mc.player.isShiftKeyDown() || !aimsAt(mc, drone) || Batteries.typeOf(mc.player.getMainHandItem()).orElse(null) != BatteryType.B6S_1100
                        || since(ctx) < 5) {
                    return since(ctx) > 100 ? StepResult.fail("Schleichen mit Akku auf die Drohne nicht möglich: Schleichen="
                            + mc.player.isShiftKeyDown() + ", Ziel=" + mc.hitResult) : StepResult.pending();
                }
                clickUse(mc);
                next(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                keepAiming(mc);
                if (since(ctx) < 5) {
                    return StepResult.pending();
                }
                StepResult swapped = serverCheck(ctx, "Akkutausch", server -> {
                    ServerPlayer player = serverPlayer(server);
                    QuadcopterEntity drone = serverDrone(player);
                    if (drone.build().battery() != BatteryType.B6S_1100 || Math.abs(drone.batteryCharge() - SWAP_CHARGE) > 0.01F) {
                        return "Shift-Rechtsklick tauscht nicht: " + drone.build().battery() + " " + drone.batteryCharge();
                    }
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        if (Batteries.typeOf(player.getInventory().getItem(i)).orElse(null) == BatteryType.B6S_1300) {
                            return null;
                        }
                    }
                    return "alter Akku nicht im Inventar";
                });
                if (swapped.status() == StepResult.Status.FAIL && since(ctx) < 60) {
                    return StepResult.pending();
                }
                if (swapped.status() != StepResult.Status.PASS) {
                    return swapped;
                }
                next(ctx, 4);
                return StepResult.pending();
            }
            case 4 -> {
                keepAiming(mc);
                mc.player.getInventory().setSelectedSlot(SLOT_WRONG_BATTERY);
                if (since(ctx) < DronePiloting.REJECT_COOLDOWN_TICKS + 2
                        || Batteries.typeOf(mc.player.getMainHandItem()).orElse(null) != BatteryType.B1S_300_HV) {
                    return StepResult.pending();
                }
                clickUse(mc);
                next(ctx, 5);
                return StepResult.pending();
            }
            default -> {
                keepAiming(mc);
                if (since(ctx) < 10) {
                    return StepResult.pending();
                }
                StepResult rejected = serverCheck(ctx, "unpassender Akku", server -> {
                    ServerPlayer player = serverPlayer(server);
                    QuadcopterEntity drone = serverDrone(player);
                    if (drone.build().battery() != BatteryType.B6S_1100) {
                        return "1S-Akku wurde in die 5-Zoll-Drohne gesetzt: " + drone.build().battery();
                    }
                    ItemStack kept = player.getInventory().getItem(SLOT_WRONG_BATTERY);
                    return Batteries.typeOf(kept).orElse(null) == BatteryType.B1S_300_HV ? null : "unpassender Akku verschwand: " + kept;
                });
                if (rejected.status() != StepResult.Status.PENDING) {
                    mc.options.keyShift.setDown(false);
                }
                return rejected;
            }
        }
    }

    private static final PartWear DAMAGED_PROPS = PartWear.PRISTINE.withProp(0, 0.0F).withProp(1, 0.5F).withProp(3, 0.8F).withMotor(2, 0.6F);

    private static StepResult propServiceStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        QuadcopterEntity drone = clientDrone(mc);
        if (mc.player == null || drone == null) {
            return StepResult.fail("keine Drohne vor dem Spieler");
        }
        switch (phase(ctx)) {
            case 0 -> {
                StepResult prepared = serverCheck(ctx, "Props vorbereiten", server -> {
                    ServerPlayer player = serverPlayer(server);
                    QuadcopterEntity serverDrone = serverDrone(player);
                    serverDrone.setWear(DAMAGED_PROPS);
                    player.setGameMode(GameType.SURVIVAL);
                    Inventory inventory = player.getInventory();
                    inventory.setItem(SLOT_PROPS, ContentItems.stack(PropType.P51_3B).copyWithCount(2));
                    inventory.setItem(SLOT_WRONG_PROPS, ContentItems.stack(PropType.P7_2B).copyWithCount(4));
                    select(player, SLOT_WRONG_PROPS);
                    return serverDrone.build().prop() == PropType.P51_3B ? null : "Freestyle ohne 5-Zoll-Props: " + serverDrone.build().prop();
                });
                if (prepared.status() == StepResult.Status.PASS) {
                    next(ctx, 1);
                    return StepResult.pending();
                }
                return prepared;
            }
            case 1 -> {
                mc.player.getInventory().setSelectedSlot(SLOT_WRONG_PROPS);
                keepAiming(mc);
                boolean ready = mc.player.isShiftKeyDown() && aimsAt(mc, drone) && drone.wear().equals(DAMAGED_PROPS)
                        && DroneAssembly.part(mc.player.getMainHandItem()).orElse(null) == PropType.P7_2B
                        && since(ctx) >= DronePiloting.REJECT_COOLDOWN_TICKS + 2;
                if (!ready) {
                    return since(ctx) > 120 ? StepResult.fail("Schleichen mit Props auf die Drohne nicht möglich: Hand " + mc.player.getMainHandItem()
                            + ", Ziel " + mc.hitResult + ", Zustand " + drone.wear()) : StepResult.pending();
                }
                clickUse(mc);
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (!(mc.gui.screen() instanceof DroneServiceScreen)) {
                    keepAiming(mc);
                    return since(ctx) > 100 ? StepResult.fail("Shift-Rechtsklick mit Props öffnet das Wartungsmenü nicht: " + mc.gui.screen())
                            : StepResult.pending();
                }
                mc.options.keyShift.setDown(false);
                next(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                StepResult swapped = serverCheck(ctx, "Props im Wartungsmenü tauschen", PlayLoopSelfTests::serviceMenuSwap);
                if (swapped.status() == StepResult.Status.PASS) {
                    next(ctx, 4);
                    return StepResult.pending();
                }
                return swapped;
            }
            default -> {
                boolean synced = drone.wear().prop(PartWear.FRONT_LEFT) == 1.0F && drone.wear().prop(PartWear.FRONT_RIGHT) == 1.0F;
                if (!synced || mc.gui.screen() != null) {
                    return since(ctx) > 60 ? StepResult.fail("Client sieht den Tausch nicht oder das Menü bleibt offen: " + drone.wear() + ", " + mc.gui.screen())
                            : StepResult.pending();
                }
                return StepResult.pass();
            }
        }
    }

    private static String serviceMenuSwap(MinecraftServer server) {
        ServerPlayer player = serverPlayer(server);
        QuadcopterEntity serverDrone = serverDrone(player);
        if (!(player.containerMenu instanceof DroneServiceMenu menu)) {
            return "Wartungsmenü auf dem Server nicht offen: " + player.containerMenu;
        }
        boolean hadBattery = serverDrone.hasBattery();
        int wrongSlot = DroneServiceMenu.HOTBAR_START + SLOT_WRONG_PROPS;
        int propSlot = DroneServiceMenu.HOTBAR_START + SLOT_PROPS;
        menu.clicked(wrongSlot, 0, ContainerInput.PICKUP, player);
        menu.clicked(DroneServiceMenu.PROP_START + PartWear.FRONT_LEFT, 0, ContainerInput.PICKUP, player);
        if (!serverDrone.wear().equals(DAMAGED_PROPS) || serverDrone.build().prop() != PropType.P51_3B) {
            return "zu große Props wurden verbaut: " + serverDrone.wear() + " " + serverDrone.build().prop();
        }
        if (DroneAssembly.part(menu.getCarried()).orElse(null) != PropType.P7_2B || menu.getCarried().getCount() != 4) {
            return "abgelehnte Props verändert: " + menu.getCarried();
        }
        menu.clicked(wrongSlot, 0, ContainerInput.PICKUP, player);
        menu.clicked(propSlot, 0, ContainerInput.PICKUP, player);
        menu.clicked(DroneServiceMenu.PROP_START + PartWear.FRONT_LEFT, 0, ContainerInput.PICKUP, player);
        if (serverDrone.wear().prop(PartWear.FRONT_LEFT) != 1.0F || menu.getCarried().getCount() != 1) {
            return "Prop vorne links nicht getauscht: " + serverDrone.wear() + ", in der Hand " + menu.getCarried();
        }
        int free = -1;
        for (int slot = DroneServiceMenu.INV_START; slot < DroneServiceMenu.INV_END && free < 0; slot++) {
            if (!menu.getSlot(slot).hasItem()) {
                free = slot;
            }
        }
        if (free < 0) {
            return "kein freier Inventarplatz";
        }
        menu.clicked(free, 0, ContainerInput.PICKUP, player);
        menu.clicked(free, 0, ContainerInput.QUICK_MOVE, player);
        PartWear wear = serverDrone.wear();
        float[] expected = {1.0F, 1.0F, 1.0F, 0.8F};
        for (int position = 0; position < PartWear.POSITIONS; position++) {
            if (wear.prop(position) != expected[position]) {
                return "Props nach dem Tausch " + wear + ", erwartet vorne links und vorne rechts neu";
            }
        }
        if (wear.motor(2) != 0.6F || serverDrone.hasBattery() != hadBattery) {
            return "Wartungsmenü hat Motor oder Akku verändert: " + wear;
        }
        if (!menu.getCarried().isEmpty() || menu.getSlot(free).hasItem()) {
            return "neue Props nicht verbraucht: " + menu.getCarried() + " / " + menu.getSlot(free).getItem();
        }
        boolean broken = false;
        boolean cracked = false;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (DroneAssembly.part(stack).orElse(null) == PropType.P51_3B) {
                broken |= PartCondition.of(stack) == 0.0F;
                cracked |= PartCondition.of(stack) == 0.5F;
            }
        }
        if (!broken || !cracked) {
            return "alte Props nicht zurück im Inventar (kaputt " + broken + ", angeknackst " + cracked + ")";
        }
        player.closeContainer();
        return null;
    }

    private static StepResult workbenchWearStep(SelfTest.StepContext ctx) {
        return serverCheck(ctx, "Werkbank und Recycling", server -> {
            ServerPlayer player = serverPlayer(server);
            player.setGameMode(GameType.CREATIVE);
            QuadcopterEntity drone = serverDrone(player);
            PartWear wear = PartWear.PRISTINE.withProp(3, 0.8F).withMotor(2, 0.6F).withVideo(0.7F).withArm(1, 0.5F).withStack(0.9F)
                    .withBattery(0.3F);
            drone.setWear(wear);
            ItemStack battery = drone.batteryStack();
            if (PartCondition.of(battery) != 0.3F) {
                return "Akku aus der Drohne ohne Zustand: " + battery;
            }
            ItemStack item = drone.toStack();
            if (!DroneAssembly.wear(item).equals(wear)) {
                return "Drohnen-Item verliert den Zustand: " + DroneAssembly.wear(item);
            }
            List<ItemStack> parts = DroneAssembly.disassemble(item);
            if (PartCondition.of(parts.get(BuildSlots.PROP + 3)) != 0.8F || PartCondition.of(parts.get(BuildSlots.MOTOR + 2)) != 0.6F
                    || PartCondition.of(parts.get(BuildSlots.VIDEO)) != 0.7F || PartCondition.of(parts.get(BuildSlots.FRAME)) != 0.5F
                    || PartCondition.of(parts.get(BuildSlots.BATTERY)) != 0.3F || PartCondition.worn(parts.get(BuildSlots.PROP))) {
                return "Zerlegen verliert den Zustand der Teile";
            }
            SimpleContainer bench = new SimpleContainer(BuildSlots.COUNT);
            for (int slot = 0; slot < BuildSlots.COUNT; slot++) {
                bench.setItem(slot, parts.get(slot));
            }
            PartWear rebuilt = DroneAssembly.wear(DroneAssembly.assemble(bench));
            for (int slot = 0; slot < PartWear.SLOTS; slot++) {
                if (rebuilt.slot(slot) != wear.slot(slot)) {
                    return "Zusammenbau verliert Platz " + slot + ": " + rebuilt;
                }
            }
            ItemStack brokenMotor = PartCondition.with(ContentItems.stack(MotorType.M2207_1750), 0.0F);
            List<ItemStack> yields = Recycling.recycle(brokenMotor);
            if (yields.size() != 1 || !yields.getFirst().is(ContentMaterials.item(CraftingMaterial.COPPER_COIL).get())) {
                return "kaputter Motor ergibt keine Kupferspule: " + yields;
            }
            if (Recycling.accepts(PartCondition.with(ContentItems.stack(PropType.P51_3B), 0.0F))
                    || Recycling.accepts(PartCondition.with(ContentItems.stack(MotorType.M2207_1750), 0.4F))) {
                return "Props oder intakte Teile werden recycelt";
            }
            drone.setWear(PartWear.PRISTINE);
            return null;
        });
    }

    private static void keepAiming(Minecraft mc) {
        QuadcopterEntity drone = clientDrone(mc);
        if (drone != null && mc.player != null) {
            mc.options.keyShift.setDown(true);
            lookAt(mc.player, drone.getBoundingBox().getCenter());
        }
    }

    private static StepResult chargerStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return StepResult.fail("kein Spieler");
        }
        mc.options.keyShift.setDown(false);
        switch (phase(ctx)) {
            case 0 -> {
                if (!ctx.state().containsKey(FUTURE)) {
                    ctx.state().put(FUTURE, ctx.onServer(server -> {
                        ServerPlayer player = serverPlayer(server);
                        BlockPos pos = player.blockPosition().offset(-1, 0, 2);
                        BlockState state = ContentBlocks.BATTERY_CHARGER.block().get().defaultBlockState()
                                .setValue(BatteryChargerBlock.FACING, Direction.NORTH);
                        player.level().setBlockAndUpdate(pos, state);
                        CHARGER.set(pos);
                    }));
                    return StepResult.pending();
                }
                CompletableFuture<?> placed = (CompletableFuture<?>) ctx.state().get(FUTURE);
                if (placed.isCompletedExceptionally()) {
                    return StepResult.fail("Ladegerät setzen: " + placed.exceptionNow().getMessage());
                }
                BlockPos pos = CHARGER.get();
                if (!placed.isDone() || pos == null || !mc.level.getBlockState(pos).hasProperty(BatteryChargerBlock.STATE)) {
                    return StepResult.pending();
                }
                int slot = -1;
                for (int i = 0; i < Inventory.getSelectionSize(); i++) {
                    if (Batteries.typeOf(mc.player.getInventory().getItem(i)).orElse(null) == BatteryType.B6S_1300) {
                        slot = i;
                    }
                }
                if (slot < 0) {
                    return StepResult.fail("alter Akku liegt nicht in der Schnellleiste");
                }
                mc.player.getInventory().setSelectedSlot(slot);
                lookAt(mc.player, Vec3.atBottomCenterOf(pos).add(0.0, 0.25, 0.0));
                if (mc.player.isShiftKeyDown() || !(mc.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(pos)
                        || since(ctx) < 5) {
                    return since(ctx) > 120 ? StepResult.fail("Ladegerät nicht anvisiert: " + mc.hitResult) : StepResult.pending();
                }
                clickUse(mc);
                next(ctx, 1);
                return StepResult.pending();
            }
            default -> {
                if (since(ctx) < 5) {
                    return StepResult.pending();
                }
                StepResult inserted = serverCheck(ctx, "Ladegerät", server -> {
                    ServerLevel level = serverPlayer(server).level();
                    BlockPos pos = CHARGER.get();
                    if (!(level.getBlockEntity(pos) instanceof BatteryChargerBlockEntity charger)) {
                        return "Ladegerät fehlt";
                    }
                    if (Batteries.typeOf(charger.getItem(0)).orElse(null) != BatteryType.B6S_1300) {
                        return "Rechtsklick legt den Akku nicht ein: " + charger.getItem(0);
                    }
                    ChargerState state = level.getBlockState(pos).getValue(BatteryChargerBlock.STATE);
                    return state == ChargerState.IDLE ? "Ladegerät lädt nicht (IDLE)" : null;
                });
                if (inserted.status() == StepResult.Status.FAIL && since(ctx) < 60) {
                    return StepResult.pending();
                }
                return inserted;
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            mc.options.keyShift.setDown(false);
            PilotSession.INSTANCE.clearTestInput();
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = serverPlayer(server);
                ServerLevel level = player.level();
                Entity entity = level.getEntity(DRONE.getAndSet(-1));
                if (entity instanceof QuadcopterEntity drone) {
                    if (drone.pilot().isPresent()) {
                        DronePiloting.release(drone);
                    }
                    drone.discard();
                }
                BlockPos pos = CHARGER.getAndSet(null);
                if (pos != null) {
                    if (level.getBlockEntity(pos) instanceof BatteryChargerBlockEntity charger) {
                        charger.clearContent();
                    }
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
                player.getInventory().clearContent();
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setShiftKeyDown(false);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen: " + future.exceptionNow().getMessage());
        }
        return future.isDone() && !PilotSession.INSTANCE.isActive() ? StepResult.pass() : StepResult.pending();
    }
}
