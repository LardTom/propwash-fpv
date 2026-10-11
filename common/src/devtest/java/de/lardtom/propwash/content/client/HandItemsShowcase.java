package de.lardtom.propwash.content.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

final class HandItemsShowcase {
    private static final int OFFSET_X = 70;
    private static final int OFFSET_Z = -70;
    private static final int LINEUP_Z = 10;
    private static final int STANDS_PER_LINEUP = 7;
    private static final double STAND_SPACING = 0.9;
    private static final double HALF_HORIZONTAL_FOV_TAN = 0.829;
    private static final int LINEUP_FOV = 50;
    private static final int SETTLE_TICKS = 30;
    private static final int WAIT_TICKS = 200;
    private static final float FIRST_PERSON_YAW = 90.0F;
    private static final float FIRST_PERSON_PITCH = 6.0F;
    private static final int FIRST_PERSON_FOV = 70;
    private static final float THIRD_PERSON_YAW = -90.0F;
    private static final float THIRD_PERSON_PITCH = -20.0F;
    private static final int THIRD_PERSON_FOV = 45;
    private static final float THIRD_PERSON_BODY_TURN = 35.0F;
    private static final double LINEUP_EYE_HEIGHT = 2.2;
    private static final String FUTURE = "future";
    private static final String INDEX = "index";
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String SHOT = "shot";
    private static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final List<Integer> STANDS = new ArrayList<>();
    private static final List<BlockPos> BLOCKS = new ArrayList<>();
    private static final List<Lineup> LINEUPS = new ArrayList<>();
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int savedFov = -1;

    private HandItemsShowcase() {
    }

    private record Shot(String file, ItemStack main, ItemStack off, boolean thirdPerson) {
        float yaw() {
            return this.thirdPerson ? THIRD_PERSON_YAW : FIRST_PERSON_YAW;
        }

        float pitch() {
            return this.thirdPerson ? THIRD_PERSON_PITCH : FIRST_PERSON_PITCH;
        }
    }

    private record Lineup(String file, List<ItemStack> items) {
    }

    static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        SelfTest.setupStep("bildschirmfotos (Hand-Items): Platz vorbereiten", 600, HandItemsShowcase::prepareStep);
        SelfTest.clientStep("bildschirmfotos (Hand-Items): erste und dritte Person aufnehmen", 20 * 60 * 6, HandItemsShowcase::handStep);
        SelfTest.clientStep("bildschirmfotos (Hand-Items): Rüstungsständer-Reihen aufnehmen", 20 * 60 * 6, HandItemsShowcase::lineupStep);
        SelfTest.setupStep("bildschirmfotos (Hand-Items): aufräumen", 200, HandItemsShowcase::cleanupStep);
    }

    private static ItemStack item(String id) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static List<ItemStack> present(String... ids) {
        List<ItemStack> out = new ArrayList<>();
        for (String id : ids) {
            ItemStack stack = item(id);
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }

    private static String fileName(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String name = id.getNamespace().equals("propwash") ? id.getPath() : id.getNamespace() + "_" + id.getPath();
        DroneBuild build = stack.get(CoreComponents.DRONE_BUILD.get());
        return build == null ? name : name + "_" + build.frame().id().getPath();
    }

    private static List<Shot> shots() {
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        List<Shot> shots = new ArrayList<>();
        shots.add(new Shot("hand_first_person_vanilla", pickaxe, new ItemStack(Items.STONE), false));
        shots.add(new Shot("hand_third_person_vanilla", pickaxe, new ItemStack(Items.STONE), true));
        for (ItemStack motor : present("justmoreparts:m1103_8000", "propwash:m0802_22000", "propwash:m2207_1750", "justmoreparts:m4214_400",
                "propwash_example:m4214_400")) {
            shots.add(new Shot("hand_first_person_" + fileName(motor), motor, pickaxe, false));
            shots.add(new Shot("hand_third_person_" + fileName(motor), motor, pickaxe, true));
        }
        List<ItemStack> others = present("propwash:whoop_65", "propwash:p51_3b", "propwash:b6s_1300", "propwash:stack_30", "propwash:digital_hd",
                "propwash:action_cam_heavy");
        others.add(ItemSizeSelfTests.drone(DroneBuild.FREESTYLE));
        for (ItemStack stack : others) {
            shots.add(new Shot("hand_first_person_" + fileName(stack), stack, pickaxe, false));
        }
        return shots;
    }

    private static List<Lineup> lineups() {
        List<ItemStack> drones = new ArrayList<>();
        for (DroneBuild build : List.of(DroneBuild.WHOOP, DroneBuild.CINEWHOOP, DroneBuild.RACER, DroneBuild.FREESTYLE, DroneBuild.LONG_RANGE)) {
            drones.add(ItemSizeSelfTests.drone(build));
        }
        ItemStack xclass = ItemSizeSelfTests.exampleXClass();
        if (!xclass.isEmpty()) {
            drones.add(xclass);
        }
        Map<String, List<ItemStack>> rows = new LinkedHashMap<>();
        rows.put("motors", present("justmoreparts:m0702_23000", "propwash:m0802_22000", "justmoreparts:m1103_8000", "propwash:m1404_4600",
                "propwash:m2004_3000", "propwash:m2207_1750", "propwash:m2306_1860", "propwash:m2807_1300", "justmoreparts:m3115_900",
                "justmoreparts:m4214_400", "propwash_example:m4214_400"));
        rows.put("props", present("propwash:p31_3b", "propwash:p31_4b", "justmoreparts:p40_20_2b_pc", "propwash:p3_3b", "propwash:p51_3b",
                "propwash:p7_2b", "justmoreparts:p254_45_3b_cfn", "justmoreparts:p330_70_2b_carbon", "propwash_example:p13_7_2b_carbon"));
        rows.put("frames", present("propwash:whoop_65", "justmoreparts:whoop_85_duct", "propwash:cinewhoop_3", "propwash:race_5", "propwash:freestyle_5",
                "propwash:long_range_7", "propwash_example:deadcat_5", "justmoreparts:xclass_13", "propwash_example:xclass_13"));
        rows.put("batteries", present("justmoreparts:b1s_250_hv", "propwash:b1s_300_hv", "propwash:b4s_650", "propwash:b6s_1100", "propwash:b6s_1300",
                "propwash:b6s_4000_lion", "justmoreparts:b8s_5000", "propwash_example:b8s_4500_lion"));
        rows.put("electronics", present("propwash:aio_whoop", "propwash:aio_20", "propwash:creative_fc", "propwash:stack_30",
                "propwash_example:stack_30_8s", "propwash:analog_nano", "propwash:analog_race", "propwash:digital_lite", "propwash:digital_hd"));
        rows.put("gear", present("propwash:action_cam_light", "propwash:action_cam_heavy", "propwash:action_cam_flashback", "propwash:led_strip",
                "propwash:gripper", "propwash:goggles_analog", "propwash:goggles_digital", "propwash:remote", "propwash:course_tool"));
        rows.put("drones", drones);
        List<Lineup> lineups = new ArrayList<>();
        int perPage = STANDS_PER_LINEUP - 2;
        rows.forEach((name, items) -> {
            for (int start = 0, page = 1; start < items.size(); start += perPage, page++) {
                List<ItemStack> row = new ArrayList<>(List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.STONE)));
                row.addAll(items.subList(start, Math.min(items.size(), start + perPage)));
                lineups.add(new Lineup("hand_lineup_" + name + "_" + page, row));
            }
        });
        return lineups;
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

    private static void floor(ServerLevel level, BlockPos from, int width, int depth) {
        BlockState stone = Blocks.SMOOTH_STONE.defaultBlockState();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                BlockPos pos = from.offset(x, -1, z).immutable();
                level.setBlockAndUpdate(pos, stone);
                BLOCKS.add(pos);
            }
        }
    }

    private static BlockPos lineupOrigin() {
        return BASE.get().offset(0, 0, LINEUP_Z);
    }

    private static double lineupWidth() {
        return (STANDS_PER_LINEUP - 1) * STAND_SPACING + 1.4;
    }

    private static Vec3 lineupEye() {
        BlockPos origin = lineupOrigin();
        double distance = lineupWidth() * 0.5 / HALF_HORIZONTAL_FOV_TAN * 1.05;
        return new Vec3(origin.getX() + 0.5 + (STANDS_PER_LINEUP - 1) * STAND_SPACING * 0.5, origin.getY() + LINEUP_EYE_HEIGHT, origin.getZ() + 0.5 - distance);
    }

    private static Vec3 lineupTarget() {
        BlockPos origin = lineupOrigin();
        return new Vec3(origin.getX() + 0.5 + (STANDS_PER_LINEUP - 1) * STAND_SPACING * 0.5, origin.getY() + 0.8, origin.getZ() + 0.5);
    }

    private static void discardStands(ServerLevel level) {
        for (int id : STANDS) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        STANDS.clear();
    }

    private static void placeStands(ServerLevel level, List<ItemStack> items) {
        discardStands(level);
        BlockPos origin = lineupOrigin();
        for (int i = 0; i < items.size(); i++) {
            int slot = STANDS_PER_LINEUP - 1 - i;
            ArmorStand stand = new ArmorStand(level, origin.getX() + 0.5 + slot * STAND_SPACING, origin.getY(), origin.getZ() + 0.5);
            stand.setYRot(180.0F);
            stand.setYHeadRot(180.0F);
            stand.setYBodyRot(180.0F);
            stand.setShowArms(true);
            stand.setNoBasePlate(true);
            stand.setNoGravity(true);
            stand.setRightArmPose(new Rotations(-18.0F, 0.0F, 0.0F));
            stand.setLeftArmPose(new Rotations(0.0F, 0.0F, 0.0F));
            stand.setItemSlot(EquipmentSlot.MAINHAND, items.get(i).copy());
            level.addFreshEntity(stand);
            STANDS.add(stand.getId());
        }
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
            SHOTS.clear();
            SHOTS.addAll(shots());
            LINEUPS.clear();
            LINEUPS.addAll(lineups());
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                int x = player.getBlockX() + OFFSET_X;
                int z = player.getBlockZ() + OFFSET_Z;
                for (int cx = -2; cx <= 2; cx++) {
                    for (int cz = -2; cz <= 3; cz++) {
                        level.getChunk((x >> 4) + cx, (z >> 4) + cz);
                    }
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos base = new BlockPos(x, y, z);
                BASE.set(base);
                STANDS.clear();
                BLOCKS.clear();
                player.setGameMode(GameType.CREATIVE);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.getInventory().clearContent();
                player.getInventory().setSelectedSlot(0);
                player.connection.send(new ClientboundSetHeldSlotPacket(0));
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), FIRST_PERSON_YAW, FIRST_PERSON_PITCH, true);
                floor(level, base.offset(-2, 0, -2), 5, 5);
                floor(level, lineupOrigin().offset(-1, 0, -1), (int) Math.ceil(lineupWidth()) + 2, 3);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Hand-Items");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        ctx.log(SHOTS.size() + " Hand-Bilder, " + LINEUPS.size() + " Rüstungsständer-Reihen");
        return StepResult.pass();
    }

    private static void turn(LocalPlayer player, float yaw, float pitch) {
        turn(player, yaw, pitch, 0.0F);
    }

    private static void turn(LocalPlayer player, float yaw, float pitch, float bodyTurn) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.setYBodyRot(yaw + bodyTurn);
        player.yBodyRotO = yaw + bodyTurn;
    }

    private static void look(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        turn(player, yaw, pitch);
    }

    private static boolean holding(LocalPlayer player, Shot shot) {
        return ItemStack.isSameItemSameComponents(player.getMainHandItem(), shot.main())
                && ItemStack.isSameItemSameComponents(player.getOffhandItem(), shot.off());
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
        ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(file + ".png") + " (" + capture.join() + ")");
        return StepResult.pass();
    }

    private static StepResult handStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(INDEX, 0);
        if (index >= SHOTS.size()) {
            ctx.log(SHOTS.size() + " Bildschirmfotos der Hand-Items");
            return StepResult.pass();
        }
        Shot shot = SHOTS.get(index);
        BlockPos base = BASE.get();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.setItemSlot(EquipmentSlot.MAINHAND, shot.main().copy());
                    player.setItemSlot(EquipmentSlot.OFFHAND, shot.off().copy());
                    player.teleportTo(player.level(), base.getX() + 0.5, base.getY(), base.getZ() + 0.5, Set.of(), shot.yaw(), shot.pitch(), true);
                }));
                state.put(SINCE, ctx.tick());
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = awaitServer(ctx, "Items in die Hände");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (mc.player == null || !holding(mc.player, shot)) {
                    return ctx.tick() - (int) state.get(SINCE) > WAIT_TICKS ? StepResult.fail("Items kamen nicht in der Hand an: " + shot.file())
                            : StepResult.pending();
                }
                mc.options.setCameraType(shot.thirdPerson() ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
                mc.options.fov().set(shot.thirdPerson() ? THIRD_PERSON_FOV : FIRST_PERSON_FOV);
                if (mc.gui.hud.isHidden() != shot.thirdPerson()) {
                    mc.gui.hud.toggle();
                }
                Screenshots.clearOverlays(mc);
                state.put(SINCE, ctx.tick());
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            default -> {
                if (mc.player != null) {
                    mc.player.setDeltaMovement(Vec3.ZERO);
                    turn(mc.player, shot.yaw(), shot.pitch(), shot.thirdPerson() ? THIRD_PERSON_BODY_TURN : 0.0F);
                }
                if (ctx.tick() - (int) state.get(SINCE) < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                StepResult shotResult = capture(ctx, shot.file());
                if (shotResult.status() == StepResult.Status.PASS) {
                    state.put(INDEX, index + 1);
                    state.put(PHASE, 0);
                    return StepResult.pending();
                }
                return shotResult;
            }
        }
    }

    private static boolean standsArrived(Minecraft mc, Lineup lineup) {
        if (mc.level == null || STANDS.size() != lineup.items().size()) {
            return false;
        }
        for (int i = 0; i < STANDS.size(); i++) {
            if (!(mc.level.getEntity(STANDS.get(i)) instanceof ArmorStand stand)
                    || !ItemStack.isSameItemSameComponents(stand.getMainHandItem(), lineup.items().get(i))) {
                return false;
            }
        }
        return true;
    }

    private static StepResult lineupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(INDEX, 0);
        if (index >= LINEUPS.size()) {
            ctx.log(LINEUPS.size() + " Bildschirmfotos der Rüstungsständer-Reihen");
            return StepResult.pass();
        }
        Lineup lineup = LINEUPS.get(index);
        Vec3 eye = lineupEye();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(player.level(), eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), 0.0F, 0.0F, true);
                    placeStands(player.level(), lineup.items());
                }));
                state.put(SINCE, ctx.tick());
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = awaitServer(ctx, "Rüstungsständer");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (mc.player == null || mc.player.getEyePosition().distanceTo(eye) > 0.05 || !standsArrived(mc, lineup)) {
                    return ctx.tick() - (int) state.get(SINCE) > WAIT_TICKS ? StepResult.fail("Rüstungsständer kamen nicht an: " + lineup.file())
                            : StepResult.pending();
                }
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.fov().set(LINEUP_FOV);
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
                Screenshots.clearOverlays(mc);
                state.put(SINCE, ctx.tick());
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            default -> {
                if (mc.player != null) {
                    mc.player.setDeltaMovement(Vec3.ZERO);
                    look(mc.player, lineupTarget());
                }
                if (ctx.tick() - (int) state.get(SINCE) < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                StepResult shotResult = capture(ctx, lineup.file());
                if (shotResult.status() == StepResult.Status.PASS) {
                    state.put(INDEX, index + 1);
                    state.put(PHASE, 0);
                    return StepResult.pending();
                }
                return shotResult;
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
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            Screenshots.hideMarker(false);
            List<BlockPos> blocks = List.copyOf(BLOCKS);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                discardStands(level);
                for (BlockPos pos : blocks) {
                    level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
                }
                player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }));
            BLOCKS.clear();
            LINEUPS.clear();
            SHOTS.clear();
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        return Screenshots.restoreWindow(ctx);
    }
}
