package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PaintPreset;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

final class ItemFrameShowcase {
    private static final int OFFSET_X = 40;
    private static final int OFFSET_Z = 40;
    private static final int WALL_Z = 5;
    private static final int WALL_LEFT = -8;
    private static final int WALL_RIGHT = 4;
    private static final int WALL_HEIGHT = 4;
    private static final int ROOF_X = 8;
    private static final int ROOF_Y = 3;
    private static final int ADDON_DRONES_LEFT = 12;
    private static final int ADDON_PARTS_LEFT = 21;
    private static final int ADDON_COLUMNS = 8;
    private static final int EMPTY_HOTBAR_SLOT = 8;
    private static final String FUTURE = "future";
    private static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final List<Integer> FRAMES = new ArrayList<>();
    private static final List<BlockPos> BLOCKS = new ArrayList<>();
    private static int savedFov = -1;

    private ItemFrameShowcase() {
    }

    static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        SelfTest.setupStep("bildschirmfotos (Item-Rahmen): Wand mit Item-Rahmen aufbauen", 400, ItemFrameShowcase::prepareStep);
        Screenshots.step("bildschirmfotos (Item-Rahmen): Wand mit Drohnen, gedrehten Rahmen, Leuchtrahmen und Rahmen-Teilen",
                "item_frames_wall", 30, new View(new Vec3(-2.0, 2.2, -2.6), new Vec3(-2.0, 2.0, WALL_Z - 0.5), 55));
        Screenshots.step("bildschirmfotos (Item-Rahmen): kleine Drohnen (Whoop, Cinewhoop, Race)", "item_frames_small_drones", 30,
                new View(new Vec3(-2.0, 3.5, 1.6), new Vec3(-2.0, 3.5, WALL_Z - 0.5), 40));
        Screenshots.step("bildschirmfotos (Item-Rahmen): große Drohnen (Freestyle, Long Range, Deadcat, X-Class)", "item_frames_large_drones", 30,
                new View(new Vec3(1.5, 3.5, 1.2), new Vec3(1.5, 3.5, WALL_Z - 0.5), 40));
        Screenshots.step("bildschirmfotos (Item-Rahmen): gedrehte Rahmen, Leuchtrahmen, unsichtbarer Rahmen", "item_frames_rotated_glow", 30,
                new View(new Vec3(0.0, 2.5, -0.2), new Vec3(0.0, 2.5, WALL_Z - 0.5), 50));
        Screenshots.step("bildschirmfotos (Item-Rahmen): Rahmen-Teile", "item_frames_frame_parts", 30,
                new View(new Vec3(0.0, 1.7, -0.2), new Vec3(0.0, 1.5, WALL_Z - 0.5), 50));
        Screenshots.step("bildschirmfotos (Item-Rahmen): übrige Teile", "item_frames_other_parts", 30,
                new View(new Vec3(0.0, 1.62, -0.2), new Vec3(0.0, 0.5, WALL_Z - 0.5), 50));
        Screenshots.step("bildschirmfotos (Item-Rahmen): schräg von der Seite", "item_frames_side", 30,
                new View(new Vec3(3.6, 3.0, 2.3), new Vec3(-1.0, 2.6, WALL_Z - 0.6), 50));
        Screenshots.step("bildschirmfotos (Item-Rahmen): Item-Rahmen am Boden", "item_frames_floor", 30,
                new View(new Vec3(ROOF_X, 2.2, 0.0), new Vec3(ROOF_X, 0.05, 1.0), 55));
        Screenshots.step("bildschirmfotos (Item-Rahmen): Item-Rahmen an der Decke", "item_frames_ceiling", 30,
                new View(new Vec3(ROOF_X, 1.7, 0.0), new Vec3(ROOF_X, ROOF_Y - 0.05, 1.0), 55));
        Screenshots.step("bildschirmfotos (Item-Rahmen): Drohnen aus Addons", "item_frames_addon_drones", 30,
                new View(new Vec3(ADDON_DRONES_LEFT + 3.5, 2.0, -0.6), new Vec3(ADDON_DRONES_LEFT + 3.5, 2.0, WALL_Z - 0.5), 55));
        Screenshots.step("bildschirmfotos (Item-Rahmen): Rahmen-Teile aus Addons", "item_frames_addon_frames", 30,
                new View(new Vec3(ADDON_PARTS_LEFT + 3.5, 2.0, -0.6), new Vec3(ADDON_PARTS_LEFT + 3.5, 2.0, WALL_Z - 0.5), 55));
        Screenshots.step("bildschirmfotos (Item-Rahmen): Drohnen im Regal", "drone_shelf", 30,
                new View(new Vec3(WALL_LEFT + 2.5, 2.7, 1.6), new Vec3(WALL_LEFT + 2.5, 2.4, WALL_Z - 0.5), 50));
        SelfTest.setupStep("bildschirmfotos (Item-Rahmen): aufräumen", 200, ItemFrameShowcase::cleanupStep);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static BlockPos base() {
        BlockPos pos = BASE.get();
        if (pos == null) {
            throw new IllegalStateException("Item-Rahmen-Wand nicht aufgebaut");
        }
        return pos;
    }

    private static void select(ServerPlayer player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        player.connection.send(new ClientboundSetHeldSlotPacket(slot));
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

    private static Vec3 at(BlockPos base, Vec3 offset) {
        return new Vec3(base.getX() + 0.5 + offset.x, base.getY() + offset.y, base.getZ() + 0.5 - offset.z);
    }

    private static BlockPos cell(BlockPos base, int x, int y, int z) {
        return base.offset(x, y, -z);
    }

    private static ItemStack drone(DroneBuild build) {
        ItemStack stack = ContentTab.presetDrone(build);
        return stack.isEmpty() ? DroneItemSelfTests.stack(build) : stack;
    }

    private static ItemStack painted(DroneBuild build, PaintPreset preset) {
        ItemStack stack = drone(build);
        PaintItems.store(stack, preset.paint());
        return stack;
    }

    private static Optional<FrameType> exampleFrame(Predicate<FrameType> accepts) {
        return PartsRenderShowcase.exampleFrame(accepts);
    }

    private static ItemStack exampleDrone(Predicate<FrameType> accepts) {
        return exampleFrame(accepts).flatMap(PartsRenderSelfTests::buildFor).map(ItemFrameShowcase::drone).orElse(ItemStack.EMPTY);
    }

    private static ItemStack exampleXClass() {
        ItemStack stack = PropwashApi.builds().createDrone(ExampleAddon.xclass());
        return stack.isEmpty() ? exampleDrone(frame -> frame.definition().role() == FrameRole.X_CLASS) : stack;
    }

    private static ItemStack exampleFramePart(Predicate<FrameType> accepts) {
        return exampleFrame(accepts).map(ContentItems::stack).orElse(ItemStack.EMPTY);
    }

    private static List<ItemStack> addonDrones() {
        List<ItemStack> stacks = new ArrayList<>();
        Set<FrameType> covered = new HashSet<>();
        for (DronePreset preset : ApiExtensions.registeredPresets()) {
            ItemStack stack = PropwashApi.builds().createDrone(preset.data());
            if (!stack.isEmpty()) {
                stacks.add(stack);
                covered.add(DroneItemRenderer.Look.of(stack).build().frame());
            }
        }
        for (FrameType frame : FrameType.values()) {
            if (!frame.builtin() && !covered.contains(frame)) {
                PartsRenderSelfTests.buildFor(frame).map(ItemFrameShowcase::drone).ifPresent(stacks::add);
            }
        }
        return stacks;
    }

    private static List<ItemStack> addonFrameParts() {
        List<ItemStack> stacks = new ArrayList<>();
        for (FrameType frame : FrameType.values()) {
            ItemStack stack = frame.builtin() ? ItemStack.EMPTY : ContentItems.stack(frame);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private static void addonWall(ServerLevel level, BlockPos base, int left, List<ItemStack> items) {
        BlockState wall = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        for (int x = left; x < left + ADDON_COLUMNS; x++) {
            for (int y = 0; y < WALL_HEIGHT; y++) {
                setBlock(level, cell(base, x, y, WALL_Z), wall);
            }
        }
        int slots = Math.min(items.size(), ADDON_COLUMNS * WALL_HEIGHT);
        if (slots == 0) {
            return;
        }
        int columns = Math.min(ADDON_COLUMNS, slots);
        int rows = (slots + columns - 1) / columns;
        int firstColumn = left + (ADDON_COLUMNS - columns) / 2;
        int topRow = WALL_HEIGHT - 1 - (WALL_HEIGHT - rows) / 2;
        for (int i = 0; i < slots; i++) {
            wallFrame(level, base, firstColumn + i % columns, topRow - i / columns, items.get(i));
        }
    }

    private static ItemStack customWhoop() {
        ItemStack stack = drone(DroneBuild.WHOOP.withAccessory(AccessoryType.LED_STRIP));
        stack.set(CoreComponents.PART_WEAR.get(), PartWear.PRISTINE.withProps(new float[]{0.0F, 0.3F, 1.0F, 1.0F}));
        PaintItems.store(stack, PaintPreset.RACE_TEAM.paint());
        return stack;
    }

    private static void setBlock(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos fixed = pos.immutable();
        level.setBlockAndUpdate(fixed, state);
        BLOCKS.add(fixed);
    }

    private static void frame(ServerLevel level, BlockPos pos, Direction facing, ItemStack item, int rotation, boolean glow, boolean invisible) {
        if (item.isEmpty()) {
            throw new IllegalStateException("leeres Item für den Item-Rahmen bei " + pos);
        }
        ItemFrame frame = glow ? new GlowItemFrame(level, pos, facing) : new ItemFrame(level, pos, facing);
        frame.setItem(item.copy(), false);
        frame.setRotation(rotation);
        frame.setInvisible(invisible);
        if (!frame.survives()) {
            throw new IllegalStateException("Item-Rahmen hält nicht bei " + pos + " (" + facing + ")");
        }
        level.addFreshEntity(frame);
        FRAMES.add(frame.getId());
    }

    private static void wallFrame(ServerLevel level, BlockPos base, int x, int y, ItemStack item) {
        frame(level, cell(base, x, y, WALL_Z - 1), Direction.SOUTH, item, 0, false, false);
    }

    private static void buildGallery(ServerLevel level, BlockPos base) {
        BlockState wall = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        for (int x = WALL_LEFT; x <= WALL_RIGHT; x++) {
            for (int y = 0; y < WALL_HEIGHT; y++) {
                setBlock(level, cell(base, x, y, WALL_Z), wall);
            }
        }
        wallFrame(level, base, -3, 3, drone(DroneBuild.WHOOP));
        wallFrame(level, base, -2, 3, drone(DroneBuild.CINEWHOOP));
        wallFrame(level, base, -1, 3, drone(DroneBuild.RACER));
        wallFrame(level, base, 0, 3, drone(DroneBuild.FREESTYLE));
        wallFrame(level, base, 1, 3, drone(DroneBuild.LONG_RANGE));
        wallFrame(level, base, 2, 3, exampleDrone(frame -> frame.definition().shape() == FrameShape.DEADCAT));
        wallFrame(level, base, 3, 3, exampleXClass());
        BlockPos row = cell(base, 0, 2, WALL_Z - 1);
        frame(level, cell(row, -3, 0, 0), Direction.SOUTH, drone(DroneBuild.FREESTYLE), 1, false, false);
        frame(level, cell(row, -2, 0, 0), Direction.SOUTH, drone(DroneBuild.FREESTYLE), 2, false, false);
        frame(level, cell(row, -1, 0, 0), Direction.SOUTH, drone(DroneBuild.FREESTYLE), 4, false, false);
        frame(level, row, Direction.SOUTH, painted(DroneBuild.RACER, PaintPreset.NEON), 0, true, false);
        frame(level, cell(row, 1, 0, 0), Direction.SOUTH, customWhoop(), 0, true, false);
        frame(level, cell(row, 2, 0, 0), Direction.SOUTH, drone(DroneBuild.LONG_RANGE), 0, false, true);
        frame(level, cell(row, 3, 0, 0), Direction.SOUTH, drone(DroneBuild.CINEWHOOP), 7, true, false);
        wallFrame(level, base, -3, 1, ContentItems.stack(FrameType.WHOOP_65));
        wallFrame(level, base, -2, 1, ContentItems.stack(FrameType.CINEWHOOP_3));
        wallFrame(level, base, -1, 1, ContentItems.stack(FrameType.RACE_5));
        wallFrame(level, base, 0, 1, ContentItems.stack(FrameType.FREESTYLE_5));
        wallFrame(level, base, 1, 1, ContentItems.stack(FrameType.LONG_RANGE_7));
        wallFrame(level, base, 2, 1, exampleFramePart(frame -> frame.definition().shape() == FrameShape.DEADCAT));
        wallFrame(level, base, 3, 1, exampleFramePart(frame -> frame.definition().role() == FrameRole.X_CLASS));
        wallFrame(level, base, -4, 1, exampleFramePart(frame -> frame.id().equals(ExampleAddon.FRAME_STRETCH)));
        wallFrame(level, base, -4, 0, PropwashApi.partItems().stack(ExampleAddon.PROP_13));
        wallFrame(level, base, -3, 0, ContentItems.stack(MotorType.M2207_1750));
        wallFrame(level, base, -2, 0, ContentItems.stack(PropType.P51_3B));
        wallFrame(level, base, -1, 0, ContentItems.stack(StackType.STACK_30));
        wallFrame(level, base, 0, 0, ContentItems.stack(BatteryType.B6S_1300));
        wallFrame(level, base, 1, 0, ContentItems.stack(VideoType.DIGITAL_HD));
        wallFrame(level, base, 2, 0, ContentItems.stack(AccessoryType.ACTION_CAM_HEAVY));
        wallFrame(level, base, 3, 0, ContentItems.stack(AccessoryType.LED_STRIP));
        for (int x = ROOF_X - 1; x <= ROOF_X + 1; x++) {
            for (int z = 0; z <= 3; z++) {
                setBlock(level, cell(base, x, ROOF_Y, z), wall);
            }
            setBlock(level, cell(base, x, ROOF_Y - 1, 3), wall);
            setBlock(level, cell(base, x, ROOF_Y - 2, 3), wall);
            setBlock(level, cell(base, x, ROOF_Y - 3, 3), wall);
        }
        frame(level, cell(base, ROOF_X - 1, 0, 1), Direction.UP, drone(DroneBuild.WHOOP), 0, false, false);
        frame(level, cell(base, ROOF_X, 0, 1), Direction.UP, drone(DroneBuild.FREESTYLE), 0, false, false);
        frame(level, cell(base, ROOF_X + 1, 0, 1), Direction.UP, ContentItems.stack(FrameType.FREESTYLE_5), 1, false, false);
        frame(level, cell(base, ROOF_X - 1, 0, 2), Direction.UP, exampleXClass(), 2, false, false);
        frame(level, cell(base, ROOF_X + 1, 0, 2), Direction.UP, drone(DroneBuild.LONG_RANGE), 0, true, false);
        frame(level, cell(base, ROOF_X - 1, ROOF_Y - 1, 1), Direction.DOWN, drone(DroneBuild.CINEWHOOP), 0, false, false);
        frame(level, cell(base, ROOF_X, ROOF_Y - 1, 1), Direction.DOWN, drone(DroneBuild.FREESTYLE), 0, false, false);
        frame(level, cell(base, ROOF_X + 1, ROOF_Y - 1, 1), Direction.DOWN, ContentItems.stack(FrameType.LONG_RANGE_7), 0, false, false);
        frame(level, cell(base, ROOF_X, ROOF_Y - 1, 2), Direction.DOWN, painted(DroneBuild.RACER, PaintPreset.NEON), 2, true, false);
        addonWall(level, base, ADDON_DRONES_LEFT, addonDrones());
        addonWall(level, base, ADDON_PARTS_LEFT, addonFrameParts());
        BlockState shelf = Blocks.SPRUCE_SHELF.defaultBlockState().setValue(ShelfBlock.FACING, Direction.SOUTH);
        List<List<ItemStack>> shelves = List.of(
                List.of(drone(DroneBuild.WHOOP), drone(DroneBuild.FREESTYLE), drone(DroneBuild.LONG_RANGE)),
                List.of(drone(DroneBuild.CINEWHOOP), painted(DroneBuild.RACER, PaintPreset.NEON), exampleXClass()),
                List.of(ContentItems.stack(FrameType.FREESTYLE_5), ContentItems.stack(MotorType.M2207_1750), ContentItems.stack(PropType.P51_3B)),
                List.of(ContentItems.stack(BatteryType.B6S_1300), ContentItems.stack(StackType.STACK_30), ContentItems.stack(VideoType.DIGITAL_HD)));
        for (int i = 0; i < shelves.size(); i++) {
            BlockPos pos = cell(base, WALL_LEFT + 1 + i, 2, WALL_Z - 1);
            setBlock(level, pos, shelf);
            if (level.getBlockEntity(pos) instanceof ShelfBlockEntity entity) {
                List<ItemStack> items = shelves.get(i);
                for (int slot = 0; slot < items.size(); slot++) {
                    entity.setItem(slot, items.get(slot).copy());
                }
                entity.setChanged();
            } else {
                throw new IllegalStateException("Regal ohne Block-Entity bei " + pos);
            }
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
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                int x = player.getBlockX() + OFFSET_X;
                int z = player.getBlockZ() + OFFSET_Z;
                level.getChunk(x >> 4, z >> 4);
                level.getChunk((x + WALL_LEFT) >> 4, (z - WALL_Z) >> 4);
                level.getChunk((x + ROOF_X + 1) >> 4, (z - WALL_Z) >> 4);
                level.getChunk((x + ADDON_PARTS_LEFT + ADDON_COLUMNS) >> 4, (z - WALL_Z) >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos base = new BlockPos(x, y, z);
                BASE.set(base);
                FRAMES.clear();
                BLOCKS.clear();
                player.setGameMode(GameType.CREATIVE);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.getInventory().clearContent();
                select(player, EMPTY_HOTBAR_SLOT);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(level, x + 0.5, y + 1.0, z + 0.5, Set.of(), 180.0F, 0.0F, true);
                buildGallery(level, base);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Item-Rahmen-Wand");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        if (mc.level == null || FRAMES.isEmpty()) {
            return StepResult.pending();
        }
        for (int id : FRAMES) {
            if (!(mc.level.getEntity(id) instanceof ItemFrame frame) || frame.getItem().isEmpty()) {
                return ctx.tick() > 380 ? StepResult.fail("Item-Rahmen kam nicht beim Client an: " + id) : StepResult.pending();
            }
        }
        ctx.log(FRAMES.size() + " Item-Rahmen, " + BLOCKS.size() + " Blöcke");
        return StepResult.pass();
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
            List<Integer> frames = List.copyOf(FRAMES);
            List<BlockPos> blocks = List.copyOf(BLOCKS);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                for (int id : frames) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) {
                        entity.discard();
                    }
                }
                for (BlockPos pos : blocks) {
                    if (level.getBlockEntity(pos) instanceof ShelfBlockEntity shelf) {
                        shelf.clearContent();
                    }
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                select(player, 0);
            }));
            FRAMES.clear();
            BLOCKS.clear();
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        return Screenshots.restoreWindow(ctx);
    }

    private record View(Vec3 eye, Vec3 target, int fov) implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            BlockPos base = base();
            Vec3 eye = at(base, this.eye);
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(player.level(), eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), 0.0F, 0.0F, true);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Blickpunkt");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.player == null || mc.player.getEyePosition().distanceTo(eye) > 0.05) {
                return ctx.tick() > 200 ? StepResult.fail("Spieler kam nicht am Blickpunkt an") : StepResult.pending();
            }
            mc.options.fov().set(this.fov);
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.setDeltaMovement(Vec3.ZERO);
                look(mc.player, at(base(), this.target));
            }
        }
    }
}
