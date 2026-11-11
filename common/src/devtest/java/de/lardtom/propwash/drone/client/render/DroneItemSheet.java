package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneModule;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

final class DroneItemSheet {
    static final String DIR = "drone_item_sheet/";
    private static final int OFFSET_X = -60;
    private static final int OFFSET_Z = -60;
    private static final int WALL_Z = 4;
    private static final int SETTLE_TICKS = 25;
    private static final int WAIT_TICKS = 200;
    private static final float YAW = 0.0F;
    private static final float PITCH = 24.0F;
    private static final int FOV = 70;
    private static final String FUTURE = "future";
    private static final String INDEX = "index";
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String SHOT = "shot";
    private static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final List<BlockPos> BLOCKS = new ArrayList<>();
    private static final List<Integer> ENTITIES = new ArrayList<>();
    private static final List<Sample> SAMPLES = new ArrayList<>();
    private static int frameId = -1;
    private static int standId = -1;
    private static int groundId = -1;
    private static int savedFov = -1;

    private DroneItemSheet() {
    }

    record Sample(String name, ItemStack stack) {
    }

    static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        SelfTest.setupStep("bildschirmfotos (Drohnen-Item): Kontaktbogen vorbereiten", 600, DroneItemSheet::prepareStep);
        SelfTest.clientStep("bildschirmfotos (Drohnen-Item): jede Drohne in Hand, Slot, Rüstungsständer, Item-Rahmen und am Boden", 20 * 60 * 20,
                DroneItemSheet::shotStep);
        SelfTest.setupStep("bildschirmfotos (Drohnen-Item): aufräumen", 200, DroneItemSheet::cleanupStep);
    }

    static ItemStack drone(DroneBuild build) {
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        return stack;
    }

    static List<Sample> samples() {
        List<Sample> out = new ArrayList<>();
        Set<FrameType> covered = new HashSet<>();
        for (DroneBuild preset : DroneBuild.PRESETS) {
            out.add(new Sample("propwash_" + preset.frame().id().getPath(), drone(preset)));
            covered.add(preset.frame());
        }
        for (DronePreset preset : ApiExtensions.registeredPresets()) {
            ItemStack stack = PropwashApi.builds().createDrone(preset.data());
            if (!stack.isEmpty()) {
                out.add(new Sample(preset.id().getNamespace() + "_" + preset.id().getPath(), stack));
                covered.add(DroneItemRenderer.Look.of(stack).build().frame());
            }
        }
        for (FrameType frame : FrameType.values()) {
            if (!frame.builtin() && !covered.contains(frame)) {
                PartsRenderSelfTests.buildFor(frame).ifPresent(build ->
                        out.add(new Sample(frame.id().getNamespace() + "_" + frame.id().getPath(), drone(build))));
            }
        }
        for (AccessoryType type : AccessoryType.values()) {
            if (!type.builtin()) {
                out.add(new Sample(type.id().getNamespace() + "_freestyle_" + type.id().getPath(), drone(DroneBuild.FREESTYLE.withAccessory(type))));
                out.add(new Sample(type.id().getNamespace() + "_long_range_" + type.id().getPath(), drone(DroneBuild.LONG_RANGE.withAccessory(type))));
            }
        }
        return out;
    }

    static String describe(ItemStack stack) {
        DroneItemRenderer.Look look = DroneItemRenderer.Look.of(stack);
        DroneItemRenderer.Fitted slot = DroneItemRenderer.fitted(look);
        DroneItemRenderer.Fitted wall = DroneItemRenderer.wallFitted(look);
        DroneItemRenderer.Fitted ground = DroneItemRenderer.groundFitted(look);
        return String.format(Locale.ROOT, "%s: Slot %.3f (Passfaktor %.2f), Rahmen %.3f, Boden %.3f", look.build().frame().id(), slot.scale(),
                slot.fit(), wall.scale(), ground.scale());
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static StepResult awaitServer(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail(what + ": keine Server-Aktion");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static void setBlock(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos fixed = pos.immutable();
        level.setBlockAndUpdate(fixed, state);
        BLOCKS.add(fixed);
    }

    private static void build(ServerLevel level, BlockPos base) {
        BlockState stone = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState wall = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        for (int x = -4; x <= 4; x++) {
            for (int z = -2; z <= WALL_Z; z++) {
                setBlock(level, base.offset(x, -1, z), stone);
            }
            for (int y = 0; y < 4; y++) {
                setBlock(level, base.offset(x, y, WALL_Z), wall);
            }
        }
        ItemFrame frame = new ItemFrame(level, base.offset(0, 1, WALL_Z - 1), Direction.NORTH);
        level.addFreshEntity(frame);
        frameId = frame.getId();
        ENTITIES.add(frameId);
        ArmorStand stand = new ArmorStand(level, base.getX() - 0.6, base.getY(), base.getZ() + 2.6);
        stand.setYRot(180.0F);
        stand.setYHeadRot(180.0F);
        stand.setYBodyRot(180.0F);
        stand.setShowArms(true);
        stand.setNoBasePlate(true);
        stand.setNoGravity(true);
        stand.setRightArmPose(new Rotations(-18.0F, 0.0F, 0.0F));
        level.addFreshEntity(stand);
        standId = stand.getId();
        ENTITIES.add(standId);
        ItemEntity ground = new ItemEntity(level, base.getX() + 1.6, base.getY(), base.getZ() + 2.2, drone(DroneBuild.FREESTYLE), 0.0, 0.0, 0.0);
        ground.setNeverPickUp();
        ground.setUnlimitedLifetime();
        ground.setNoGravity(true);
        level.addFreshEntity(ground);
        groundId = ground.getId();
        ENTITIES.add(groundId);
    }

    private static StepResult prepareStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            StepResult resized = Screenshots.resizeWindow(ctx);
            if (resized.status() != StepResult.Status.PASS) {
                return resized;
            }
            Screenshots.hideMarker(true);
            savedFov = mc.options.fov().get();
            SAMPLES.clear();
            SAMPLES.addAll(samples());
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                int x = player.getBlockX() + OFFSET_X;
                int z = player.getBlockZ() + OFFSET_Z;
                for (int cx = -1; cx <= 1; cx++) {
                    for (int cz = -1; cz <= 1; cz++) {
                        level.getChunk((x >> 4) + cx, (z >> 4) + cz);
                    }
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos base = new BlockPos(x, y, z);
                BASE.set(base);
                BLOCKS.clear();
                ENTITIES.clear();
                player.setGameMode(GameType.CREATIVE);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.getInventory().clearContent();
                player.getInventory().setSelectedSlot(0);
                player.connection.send(new ClientboundSetHeldSlotPacket(0));
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), YAW, PITCH, true);
                build(level, base);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Kontaktbogen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        ctx.log(SAMPLES.size() + " Drohnen für den Kontaktbogen");
        return StepResult.pass();
    }

    private static boolean arrived(Minecraft mc, ItemStack stack) {
        if (mc.player == null || mc.level == null || !ItemStack.isSameItemSameComponents(mc.player.getMainHandItem(), stack)) {
            return false;
        }
        return mc.level.getEntity(frameId) instanceof ItemFrame frame && ItemStack.isSameItemSameComponents(frame.getItem(), stack)
                && mc.level.getEntity(standId) instanceof ArmorStand stand && ItemStack.isSameItemSameComponents(stand.getMainHandItem(), stack)
                && mc.level.getEntity(groundId) instanceof ItemEntity ground && ItemStack.isSameItemSameComponents(ground.getItem(), stack);
    }

    private static void turn(LocalPlayer player) {
        player.setYRot(YAW);
        player.setXRot(PITCH);
        player.yRotO = YAW;
        player.xRotO = PITCH;
        player.setYHeadRot(YAW);
        player.yHeadRotO = YAW;
        player.setYBodyRot(YAW);
        player.yBodyRotO = YAW;
    }

    @SuppressWarnings("unchecked")
    private static StepResult capture(SelfTest.StepContext ctx, String file) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(SHOT)) {
            state.put(SHOT, Screenshots.capture(file));
            return StepResult.pending();
        }
        CompletableFuture<String> capture = (CompletableFuture<String>) state.get(SHOT);
        if (!capture.isDone()) {
            return StepResult.pending();
        }
        state.remove(SHOT);
        if (capture.isCompletedExceptionally()) {
            return StepResult.fail("Bildschirmfoto " + file + ": " + capture.exceptionNow());
        }
        return StepResult.pass();
    }

    private static StepResult shotStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(INDEX, 0);
        if (index >= SAMPLES.size()) {
            ctx.log(SAMPLES.size() + " Bilder unter " + Screenshots.dir().resolve(DIR));
            return StepResult.pass();
        }
        Sample sample = SAMPLES.get(index);
        BlockPos base = BASE.get();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                ItemStack stack = sample.stack();
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    player.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                    player.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, Set.of(), YAW, PITCH, true);
                    if (level.getEntity(frameId) instanceof ItemFrame frame) {
                        frame.setItem(stack.copy(), false);
                    }
                    if (level.getEntity(standId) instanceof ArmorStand stand) {
                        stand.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                    }
                    if (level.getEntity(groundId) instanceof ItemEntity ground) {
                        ground.setItem(stack.copy());
                        ground.setDeltaMovement(Vec3.ZERO);
                    }
                }));
                state.put(SINCE, ctx.tick());
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = awaitServer(ctx, "Drohne verteilen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!arrived(mc, sample.stack())) {
                    return ctx.tick() - (int) state.get(SINCE) > WAIT_TICKS ? StepResult.fail("Drohne kam nicht an: " + sample.name())
                            : StepResult.pending();
                }
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.fov().set(FOV);
                if (mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
                Screenshots.clearOverlays(mc);
                ctx.log(String.format(Locale.ROOT, "%02d %s – %s", index, sample.name(), describe(sample.stack())));
                state.put(SINCE, ctx.tick());
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            default -> {
                if (mc.player != null) {
                    mc.player.setDeltaMovement(Vec3.ZERO);
                    turn(mc.player);
                }
                if (ctx.tick() - (int) state.get(SINCE) < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                StepResult shot = capture(ctx, DIR + String.format(Locale.ROOT, "%02d_%s", index, sample.name()));
                if (shot.status() == StepResult.Status.PASS) {
                    state.put(INDEX, index + 1);
                    state.put(PHASE, 0);
                    return StepResult.pending();
                }
                return shot;
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            Screenshots.hideMarker(false);
            List<BlockPos> blocks = List.copyOf(BLOCKS);
            List<Integer> entities = List.copyOf(ENTITIES);
            BlockPos base = BASE.get();
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                for (int id : entities) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) {
                        entity.discard();
                    }
                }
                for (BlockPos pos : blocks) {
                    level.setBlockAndUpdate(pos, base != null && pos.getY() < base.getY() ? Blocks.GRASS_BLOCK.defaultBlockState()
                            : Blocks.AIR.defaultBlockState());
                }
                player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }));
            BLOCKS.clear();
            ENTITIES.clear();
            SAMPLES.clear();
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        return Screenshots.restoreWindow(ctx);
    }
}
