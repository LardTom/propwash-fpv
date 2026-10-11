package de.lardtom.propwash.content.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.ContentBlocks;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.race.RaceModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

final class GroundItemsShowcase {
    private static final int OFFSET_X = -60;
    private static final int OFFSET_Z = 60;
    private static final int COLUMNS = 10;
    private static final int ROWS = 4;
    private static final int PAGE_PITCH = 16;
    private static final double GRID_SPACING = 1.0;
    private static final int REFERENCE_Z = -8;
    private static final int EMPTY_HOTBAR_SLOT = 8;
    private static final int SETTLE_TICKS = 40;
    private static final double NEAR = 40.0;
    private static final int ARRIVE_TICKS = 400;
    private static final String FUTURE = "future";
    private static final String PAGE = "page";
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String SHOT = "shot";
    private static final String ARRIVE = "arrive";
    private static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final List<Integer> ENTITIES = new ArrayList<>();
    private static final Map<Integer, Vec3> SPOTS = new HashMap<>();
    private static final List<BlockPos> BLOCKS = new ArrayList<>();
    private static final List<Page> PAGES = new ArrayList<>();
    private static int savedFov = -1;

    private GroundItemsShowcase() {
    }

    private record Page(String file, Vec3 eye, Vec3 target, int fov) {
    }

    static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        SelfTest.setupStep("bildschirmfotos (Boden-Items): alle Items und Vanilla-Vergleich auf den Boden legen", 600,
                GroundItemsShowcase::prepareStep);
        SelfTest.clientStep("bildschirmfotos (Boden-Items): Seiten aufnehmen", 20 * 60 * 6, GroundItemsShowcase::shootStep);
        SelfTest.setupStep("bildschirmfotos (Boden-Items): aufräumen", 200, GroundItemsShowcase::cleanupStep);
    }

    private static List<ItemStack> vanilla() {
        return List.of(new ItemStack(Items.STONE), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.CARROT));
    }

    private static void addonItem(List<ItemStack> into, String id) {
        BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).ifPresent(item -> into.add(new ItemStack(item)));
    }

    private static List<List<ItemStack>> referenceRows() {
        List<ItemStack> drones = new ArrayList<>(vanilla());
        for (DroneBuild build : List.of(DroneBuild.WHOOP, DroneBuild.CINEWHOOP, DroneBuild.RACER, DroneBuild.FREESTYLE, DroneBuild.LONG_RANGE)) {
            drones.add(ItemSizeSelfTests.drone(build));
        }
        ItemStack xclass = ItemSizeSelfTests.exampleXClass();
        if (!xclass.isEmpty()) {
            drones.add(xclass);
        }
        List<ItemStack> parts = new ArrayList<>(vanilla().subList(0, 2));
        parts.add(ContentItems.stack(FrameType.WHOOP_65));
        parts.add(ContentItems.stack(FrameType.FREESTYLE_5));
        parts.add(ContentItems.stack(PropType.P51_3B));
        parts.add(ContentItems.stack(PropType.P7_2B));
        parts.add(ContentItems.stack(MotorType.M2207_1750));
        parts.add(ContentItems.stack(StackType.STACK_30));
        parts.add(ContentItems.stack(StackType.AIO_20));
        parts.add(ContentItems.stack(VideoType.DIGITAL_HD));
        List<ItemStack> batteries = new ArrayList<>(vanilla());
        batteries.add(ContentItems.stack(BatteryType.B1S_300_HV));
        batteries.add(ContentItems.stack(BatteryType.B4S_650));
        batteries.add(ContentItems.stack(BatteryType.B6S_1100));
        batteries.add(ContentItems.stack(BatteryType.B6S_1300));
        batteries.add(ContentItems.stack(BatteryType.B6S_4000_LION));
        addonItem(batteries, "justmoreparts:b1s_750_hv");
        addonItem(batteries, "justmoreparts:b8s_5000");
        List<ItemStack> gear = new ArrayList<>(vanilla().subList(0, 2));
        gear.add(new ItemStack(ContentItems.GOGGLES_DIGITAL.get()));
        gear.add(new ItemStack(ContentItems.GOGGLES_ANALOG.get()));
        gear.add(new ItemStack(DroneModule.REMOTE.get()));
        gear.add(ContentItems.stack(AccessoryType.ACTION_CAM_HEAVY));
        gear.add(new ItemStack(RaceModule.GATE_SMALL.get()));
        gear.add(new ItemStack(RaceModule.GATE_MEDIUM.get()));
        gear.add(new ItemStack(RaceModule.COURSE_TOOL.get()));
        gear.add(new ItemStack(ContentBlocks.BATTERY_CHARGER.item().get()));
        gear.add(new ItemStack(ContentBlocks.DRONE_WORKBENCH.item().get()));
        return List.of(drones, parts, batteries, gear);
    }

    private static int namespaceRank(String label) {
        if (label.startsWith(Constants.MOD_ID + ":")) {
            return 0;
        }
        return label.startsWith(ExampleAddon.NAMESPACE + ":") ? 1 : 2;
    }

    private static List<List<ItemStack>> gridPages() {
        List<ItemSizeSelfTests.Sample> samples = new ArrayList<>(ItemSizeSelfTests.samples());
        samples.sort(Comparator.comparingInt((ItemSizeSelfTests.Sample sample) -> namespaceRank(sample.label()))
                .thenComparing(ItemSizeSelfTests.Sample::label));
        List<ItemStack> all = new ArrayList<>();
        for (ItemSizeSelfTests.Sample sample : samples) {
            all.add(sample.stack());
        }
        int perPage = COLUMNS * ROWS - 2;
        List<List<ItemStack>> pages = new ArrayList<>();
        for (int start = 0; start < all.size(); start += perPage) {
            List<ItemStack> page = new ArrayList<>();
            page.add(new ItemStack(Items.STONE));
            page.add(new ItemStack(Items.IRON_SWORD));
            page.addAll(all.subList(start, Math.min(all.size(), start + perPage)));
            pages.add(page);
        }
        return pages;
    }

    private static void drop(ServerLevel level, double x, double y, double z, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, x, y, z, stack.copy(), 0.0, 0.0, 0.0);
        entity.setNeverPickUp();
        entity.setUnlimitedLifetime();
        level.addFreshEntity(entity);
        ENTITIES.add(entity.getId());
        SPOTS.put(entity.getId(), new Vec3(x, y, z));
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

    private static void build(ServerLevel level, BlockPos base) {
        List<List<ItemStack>> pages = gridPages();
        PAGES.clear();
        double y = base.getY() + 0.05;
        for (int p = 0; p < pages.size(); p++) {
            BlockPos origin = base.offset(p * PAGE_PITCH, 0, 0);
            floor(level, origin.offset(-1, 0, -1), COLUMNS + 2, ROWS + 2);
            List<ItemStack> items = pages.get(p);
            for (int i = 0; i < items.size(); i++) {
                int column = i % COLUMNS;
                int row = i / COLUMNS;
                drop(level, origin.getX() + 0.5 + column * GRID_SPACING, y, origin.getZ() + 0.5 + (ROWS - 1 - row) * GRID_SPACING, items.get(i));
            }
            double centerX = origin.getX() + 0.5 + (COLUMNS - 1) * GRID_SPACING * 0.5;
            PAGES.add(new Page(String.format("ground_items_%02d", p + 1), new Vec3(centerX, base.getY() + 3.4, origin.getZ() + ROWS + 3.4),
                    new Vec3(centerX, base.getY(), origin.getZ() + ROWS * 0.5 - 0.2), 60));
        }
        List<List<ItemStack>> rows = referenceRows();
        String[] names = {"ground_items_compare_drones", "ground_items_compare_parts", "ground_items_compare_batteries", "ground_items_compare_gear"};
        for (int r = 0; r < rows.size(); r++) {
            List<ItemStack> items = rows.get(r);
            BlockPos origin = base.offset(r * 14, 0, REFERENCE_Z);
            floor(level, origin.offset(-1, 0, -2), 12, 8);
            double spacing = 0.8;
            for (int i = 0; i < items.size(); i++) {
                drop(level, origin.getX() + 0.5 + i * spacing, y, origin.getZ() + 0.5, items.get(i));
            }
            double centerX = origin.getX() + 0.5 + (items.size() - 1) * spacing * 0.5;
            double distance = ((items.size() - 1) * spacing + 1.2) / 2.05;
            PAGES.add(new Page(names[r], new Vec3(centerX, base.getY() + 1.62, origin.getZ() + 0.5 + distance),
                    new Vec3(centerX, base.getY() + 0.1, origin.getZ() + 0.5), 60));
        }
    }

    private static ServerPlayer player(net.minecraft.server.MinecraftServer server) {
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
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                int x = player.getBlockX() + OFFSET_X;
                int z = player.getBlockZ() + OFFSET_Z;
                int span = Math.max(8, (gridPages().size() * PAGE_PITCH + COLUMNS) / 16 + 2);
                for (int cx = -2; cx <= span; cx++) {
                    for (int cz = -2; cz <= 2; cz++) {
                        level.getChunk((x >> 4) + cx, (z >> 4) + cz);
                    }
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos base = new BlockPos(x, y, z);
                BASE.set(base);
                ENTITIES.clear();
                SPOTS.clear();
                BLOCKS.clear();
                player.setGameMode(GameType.CREATIVE);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.getInventory().clearContent();
                player.getInventory().setSelectedSlot(EMPTY_HOTBAR_SLOT);
                player.connection.send(new ClientboundSetHeldSlotPacket(EMPTY_HOTBAR_SLOT));
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(level, x + 0.5, y + 3.0, z - 4.0, Set.of(), 0.0F, 30.0F, true);
                build(level, base);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Boden-Items");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        if (mc.level == null || ENTITIES.isEmpty()) {
            return StepResult.pending();
        }
        BlockPos base = BASE.get();
        int missing = missing(mc, new Vec3(base.getX() + 0.5, base.getY(), base.getZ() - 4.0));
        if (missing > 0) {
            return ctx.tick() > 580 ? StepResult.fail(missing + " Boden-Items kamen nicht beim Client an") : StepResult.pending();
        }
        ctx.log(ENTITIES.size() + " Items am Boden, " + PAGES.size() + " Ansichten");
        return StepResult.pass();
    }

    private static int missing(Minecraft mc, Vec3 around) {
        int missing = 0;
        for (int id : ENTITIES) {
            Vec3 spot = SPOTS.get(id);
            boolean near = spot != null && Math.hypot(spot.x - around.x, spot.z - around.z) <= NEAR;
            if (near && (mc.level == null || !(mc.level.getEntity(id) instanceof ItemEntity))) {
                missing++;
            }
        }
        return missing;
    }

    private static void look(LocalPlayer player, Vec3 target) {
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
        player.yHeadRotO = yaw;
        player.setYBodyRot(yaw);
        player.yBodyRotO = yaw;
    }

    @SuppressWarnings("unchecked")
    private static StepResult shootStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(PAGE, 0);
        if (index >= PAGES.size()) {
            ctx.log(PAGES.size() + " Bildschirmfotos der Boden-Items");
            return StepResult.pass();
        }
        Page page = PAGES.get(index);
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                Vec3 eye = page.eye();
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(player.level(), eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), 0.0F, 0.0F, true);
                }));
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = awaitServer(ctx, "Blickpunkt");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (mc.player == null || mc.player.getEyePosition().distanceTo(page.eye()) > 0.05) {
                    return StepResult.pending();
                }
                int missing = missing(mc, page.target());
                if (missing > 0) {
                    state.putIfAbsent(ARRIVE, ctx.tick());
                    return ctx.tick() - (int) state.get(ARRIVE) > ARRIVE_TICKS
                            ? StepResult.fail(page.file() + ": " + missing + " Boden-Items kamen nicht beim Client an") : StepResult.pending();
                }
                state.remove(ARRIVE);
                mc.options.fov().set(page.fov());
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
                Screenshots.clearOverlays(mc);
                state.put(SINCE, ctx.tick());
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (mc.player != null) {
                    mc.player.setDeltaMovement(Vec3.ZERO);
                    look(mc.player, page.target());
                }
                if (ctx.tick() - (int) state.get(SINCE) < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                state.put(SHOT, Screenshots.capture(page.file()));
                state.put(PHASE, 3);
                return StepResult.pending();
            }
            default -> {
                if (mc.player != null) {
                    look(mc.player, page.target());
                }
                CompletableFuture<String> shot = (CompletableFuture<String>) state.get(SHOT);
                if (!shot.isDone()) {
                    return StepResult.pending();
                }
                if (shot.isCompletedExceptionally()) {
                    return StepResult.fail("Bildschirmfoto " + page.file() + ": " + shot.exceptionNow());
                }
                ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(page.file() + ".png") + " (" + shot.join() + ")");
                state.put(PAGE, index + 1);
                state.put(PHASE, 0);
                return StepResult.pending();
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            Screenshots.hideMarker(false);
            List<Integer> entities = List.copyOf(ENTITIES);
            List<BlockPos> blocks = List.copyOf(BLOCKS);
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
                    level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
                }
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.getInventory().setSelectedSlot(0);
                player.connection.send(new ClientboundSetHeldSlotPacket(0));
            }));
            ENTITIES.clear();
            SPOTS.clear();
            BLOCKS.clear();
            PAGES.clear();
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        return Screenshots.restoreWindow(ctx);
    }
}
