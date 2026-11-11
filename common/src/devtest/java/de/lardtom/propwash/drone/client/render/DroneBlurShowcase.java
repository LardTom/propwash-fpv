package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PaintPreset;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class DroneBlurShowcase {
    static final String TAG = "propwash_deko_blur";
    static final String SUMMON_BLUR = "summon minecraft:item_display %s %s %s "
            + "{item:{id:\"propwash:drone\",components:{\"minecraft:custom_model_data\":{strings:[\"blur\"]}}},Tags:[\"" + TAG + "\"]}";
    static final String SUMMON_STILL = "summon minecraft:item_display %s %s %s {item:{id:\"propwash:drone\"},Tags:[\"" + TAG + "\"]}";
    static final String MODIFY = "item modify entity @s weapon.mainhand "
            + "{type:\"minecraft:set_custom_model_data\",strings:{values:[\"blur\"],mode:\"replace_all\"}}";
    private static final int OFFSET_X = -40;
    private static final int OFFSET_Z = -40;
    private static final int WALL_Z = 5;
    private static final int WALL_HALF = 4;
    private static final int WALL_HEIGHT = 4;
    private static final int HAND_SLOT = 0;
    private static final int EMPTY_SLOT = 8;
    private static final int SETTLE_TICKS = 20;
    private static final String FUTURE = "future";
    private static final Vec3 EYE = new Vec3(0.0, 2.0, -2.0);
    private static final Vec3 TARGET = new Vec3(0.0, 1.8, WALL_Z - 0.5);
    private static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final List<Integer> FRAMES = new ArrayList<>();
    private static final List<Integer> DISPLAYS = new ArrayList<>();
    private static final List<Integer> GROUND = new ArrayList<>();
    private static final List<BlockPos> BLOCKS = new ArrayList<>();
    private static int savedFov = -1;

    private DroneBlurShowcase() {
    }

    static void register() {
        SelfTest.setupStep("drone-render: Deko-Blur: Item-Rahmen, item_display, Boden-Item und Drohne in der Hand aufbauen", 400,
                DroneBlurShowcase::prepareStep);
        SelfTest.clientStep("drone-render: Deko-Blur: Item-Rahmen, item_display, Boden und Hand zeichnen drehende Props in der Welt", 400,
                DroneBlurShowcase::worldStep);
        if (Screenshots.enabled()) {
            Screenshots.step("bildschirmfotos (Deko-Blur): Item-Rahmen, item_display und Boden-Item mit drehenden Props", "drone_item_blur", 30,
                    new View(EYE, TARGET, 60));
            Screenshots.step("bildschirmfotos (Deko-Blur): item_display statisch und mit \"blur\" aus der Nähe", "drone_item_blur_display", 30,
                    new View(new Vec3(0.0, 2.6, -0.8), new Vec3(0.0, 1.2, 2.0), 55));
        }
        SelfTest.setupStep("drone-render: Deko-Blur: aufräumen", 200, DroneBlurShowcase::cleanupStep);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static BlockPos base() {
        BlockPos pos = BASE.get();
        if (pos == null) {
            throw new IllegalStateException("Deko-Blur-Wand nicht aufgebaut");
        }
        return pos;
    }

    private static Vec3 at(BlockPos base, Vec3 offset) {
        return new Vec3(base.getX() + 0.5 + offset.x, base.getY() + offset.y, base.getZ() + 0.5 - offset.z);
    }

    private static BlockPos cell(BlockPos base, int x, int y, int z) {
        return base.offset(x, y, -z);
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

    private static ItemStack blurred(DroneBuild build) {
        return DroneItemBlurSelfTests.withStrings(DroneItemSelfTests.stack(build), List.of(DroneItemRenderer.BLUR));
    }

    private static void setBlock(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos fixed = pos.immutable();
        level.setBlockAndUpdate(fixed, state);
        BLOCKS.add(fixed);
    }

    private static void frame(ServerLevel level, BlockPos base, int x, int y, ItemStack item, int rotation, boolean glow) {
        BlockPos pos = cell(base, x, y, WALL_Z - 1);
        ItemFrame frame = glow ? new GlowItemFrame(level, pos, Direction.SOUTH) : new ItemFrame(level, pos, Direction.SOUTH);
        frame.setItem(item.copy(), false);
        frame.setRotation(rotation);
        if (!frame.survives()) {
            throw new IllegalStateException("Item-Rahmen hält nicht bei " + pos);
        }
        level.addFreshEntity(frame);
        FRAMES.add(frame.getId());
    }

    private static void command(MinecraftServer server, CommandSourceStack source, String command) {
        AtomicInteger result = new AtomicInteger(-1);
        List<String> failures = new ArrayList<>();
        CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                failures.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return false;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        server.getCommands().performPrefixedCommand(source.withSource(sink).withCallback((success, value) -> result.set(success ? 1 : 0)), command);
        if (result.get() != 1) {
            throw new IllegalStateException("Befehl scheitert: /" + command + " " + failures);
        }
    }

    private static void build(MinecraftServer server, ServerPlayer player, ServerLevel level, BlockPos base) {
        BlockState quartz = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        for (int x = -WALL_HALF; x <= WALL_HALF; x++) {
            for (int y = 0; y < WALL_HEIGHT; y++) {
                setBlock(level, cell(base, x, y, WALL_Z), quartz);
            }
        }
        ItemStack neon = blurred(DroneBuild.RACER);
        PaintItems.store(neon, PaintPreset.NEON.paint());
        frame(level, base, -3, 2, DroneItemSelfTests.stack(DroneBuild.FREESTYLE), 0, false);
        frame(level, base, -2, 2, blurred(DroneBuild.FREESTYLE), 0, false);
        frame(level, base, -1, 2, neon, 0, true);
        frame(level, base, 1, 2, blurred(DroneBuild.WHOOP), 0, false);
        frame(level, base, 2, 2, blurred(DroneBuild.CINEWHOOP), 0, false);
        frame(level, base, 3, 2, blurred(DroneBuild.LONG_RANGE), 1, false);
        ItemStack addon = PropwashApi.builds().createDrone(ExampleAddon.xclass());
        if (!addon.isEmpty()) {
            frame(level, base, -3, 3, DroneItemBlurSelfTests.withStrings(addon, List.of(DroneItemRenderer.BLUR)), 0, false);
        }
        frame(level, base, 3, 3, blurred(DroneBuild.FREESTYLE), 2, true);
        BlockPos pedestal = cell(base, 3, 0, 2);
        setBlock(level, pedestal, quartz);
        ItemEntity dropped = new ItemEntity(level, pedestal.getX() + 0.5, pedestal.getY() + 1.0, pedestal.getZ() + 0.5, blurred(DroneBuild.RACER));
        dropped.setDeltaMovement(Vec3.ZERO);
        dropped.setNeverPickUp();
        dropped.setUnlimitedLifetime();
        level.addFreshEntity(dropped);
        GROUND.add(dropped.getId());
        CommandSourceStack source = server.createCommandSourceStack().withLevel(level);
        Vec3 still = at(base, new Vec3(-1.0, 1.2, 2.0));
        Vec3 spinning = at(base, new Vec3(1.0, 1.2, 2.0));
        command(server, source, String.format(Locale.ROOT, SUMMON_STILL, still.x, still.y, still.z));
        command(server, source, String.format(Locale.ROOT, SUMMON_BLUR, spinning.x, spinning.y, spinning.z));
        for (Entity entity : level.getEntities(EntityTypes.ITEM_DISPLAY, e -> e.entityTags().contains(TAG))) {
            DISPLAYS.add(entity.getId());
        }
        if (DISPLAYS.size() != 2) {
            throw new IllegalStateException(DISPLAYS.size() + " statt 2 item_display mit Tag " + TAG);
        }
        player.getInventory().setItem(HAND_SLOT, DroneItemSelfTests.stack(DroneBuild.RACER));
        select(player, HAND_SLOT);
        command(server, source.withEntity(player).withPosition(player.position()), MODIFY);
        if (!DroneItemRenderer.Look.of(player.getMainHandItem()).blur()) {
            throw new IllegalStateException("/" + MODIFY + " setzt kein \"blur\": " + player.getMainHandItem().getComponents());
        }
        select(player, EMPTY_SLOT);
    }

    private static StepResult prepareStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            if (Screenshots.enabled()) {
                StepResult resized = Screenshots.resizeWindow(ctx);
                if (resized.status() != StepResult.Status.PASS) {
                    return resized;
                }
                Screenshots.hideMarker(true);
            }
            savedFov = mc.options.fov().get();
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                int x = player.getBlockX() + OFFSET_X;
                int z = player.getBlockZ() + OFFSET_Z;
                level.getChunk(x >> 4, z >> 4);
                level.getChunk((x - WALL_HALF) >> 4, (z - WALL_Z) >> 4);
                level.getChunk((x + WALL_HALF) >> 4, (z - WALL_Z) >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos base = new BlockPos(x, y, z);
                BASE.set(base);
                FRAMES.clear();
                DISPLAYS.clear();
                GROUND.clear();
                BLOCKS.clear();
                player.setGameMode(GameType.CREATIVE);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.getInventory().clearContent();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                Vec3 eye = at(base, EYE);
                player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), 180.0F, 0.0F, true);
                build(server, player, level, base);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Deko-Blur-Wand");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        if (mc.level == null || FRAMES.isEmpty()) {
            return StepResult.pending();
        }
        String missing = missingOnClient(mc);
        if (missing != null) {
            return ctx.tick() > 380 ? StepResult.fail(missing + " kam nicht beim Client an") : StepResult.pending();
        }
        ctx.log(FRAMES.size() + " Item-Rahmen, " + DISPLAYS.size() + " item_display, " + GROUND.size() + " Boden-Item, " + BLOCKS.size()
                + " Blöcke; /" + MODIFY + " setzt \"blur\" auf die Drohne in der Hand");
        return StepResult.pass();
    }

    private static @Nullable String missingOnClient(Minecraft mc) {
        if (mc.level == null) {
            return "Welt";
        }
        for (int id : FRAMES) {
            if (!(mc.level.getEntity(id) instanceof ItemFrame frame) || frame.getItem().isEmpty()) {
                return "Item-Rahmen " + id;
            }
        }
        for (int id : DISPLAYS) {
            if (!(mc.level.getEntity(id) instanceof Display.ItemDisplay display) || display.getSlot(0).get().isEmpty()) {
                return "item_display " + id;
            }
        }
        for (int id : GROUND) {
            if (!(mc.level.getEntity(id) instanceof ItemEntity item) || item.getItem().isEmpty()) {
                return "Boden-Item " + id;
            }
        }
        return null;
    }

    private static int blurredDisplays(Minecraft mc) {
        int count = 0;
        for (int id : DISPLAYS) {
            if (mc.level != null && mc.level.getEntity(id) instanceof Display.ItemDisplay display
                    && DroneItemRenderer.Look.of(display.getSlot(0).get()).blur()) {
                count++;
            }
        }
        return count;
    }

    private static String counts() {
        int[] c = DroneItemRenderer.blurredSubmits;
        return String.format(Locale.ROOT, "Item-Rahmen %d, item_display/Hand %d, Boden %d, Slot %d", c[DroneItemRenderer.Mode.WALL.ordinal()],
                c[DroneItemRenderer.Mode.SLOT.ordinal()], c[DroneItemRenderer.Mode.GROUND.ordinal()], c[DroneItemRenderer.Mode.ICON.ordinal()]);
    }

    private static StepResult worldStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (mc.player == null || mc.level == null) {
            return StepResult.pending();
        }
        BlockPos base = base();
        Vec3 eye = at(base, EYE);
        if (!state.containsKey(FUTURE)) {
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                select(player, EMPTY_SLOT);
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
        boolean hand = state.containsKey("hand");
        mc.player.setDeltaMovement(Vec3.ZERO);
        look(mc.player, hand ? mc.player.getEyePosition().add(0.0, 10.0, 0.01) : at(base, TARGET));
        if (mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        if (!state.containsKey("since")) {
            int slot = hand ? HAND_SLOT : EMPTY_SLOT;
            boolean ready = mc.player.getEyePosition().distanceTo(eye) <= 0.05 && mc.player.getInventory().getSelectedSlot() == slot
                    && (!hand || DroneItemRenderer.Look.of(mc.player.getMainHandItem()).blur());
            if (!ready) {
                return ctx.tick() > 300 ? StepResult.fail(hand ? "Drohne mit \"blur\" kam nicht in die Hand" : "Spieler kam nicht am Blickpunkt an")
                        : StepResult.pending();
            }
            if (!hand && blurredDisplays(mc) != 1) {
                return StepResult.fail(blurredDisplays(mc) + " statt 1 item_display mit \"blur\" beim Client");
            }
            Arrays.fill(DroneItemRenderer.blurredSubmits, 0);
            state.put("since", ctx.tick());
            return StepResult.pending();
        }
        if (ctx.tick() - (int) state.get("since") < SETTLE_TICKS) {
            return StepResult.pending();
        }
        int[] c = DroneItemRenderer.blurredSubmits;
        int wall = c[DroneItemRenderer.Mode.WALL.ordinal()];
        int slot = c[DroneItemRenderer.Mode.SLOT.ordinal()];
        int ground = c[DroneItemRenderer.Mode.GROUND.ordinal()];
        if (!hand) {
            if (wall == 0 || slot == 0 || ground == 0) {
                return StepResult.fail("Drohnen mit \"blur\" nicht überall gezeichnet (" + SETTLE_TICKS + " Ticks, leere Hand): " + counts());
            }
            ctx.log("Szene mit leerer Hand, " + SETTLE_TICKS + " Ticks: " + counts());
            state.put("hand", true);
            state.remove("since");
            state.put(FUTURE, ctx.onServer(s -> select(player(s), HAND_SLOT)));
            return StepResult.pending();
        }
        if (slot == 0 || wall != 0 || ground != 0) {
            return StepResult.fail("Drohne mit \"blur\" in der Hand, Blick nach oben: " + counts());
        }
        ctx.log("Hand mit /item modify, Blick nach oben, " + SETTLE_TICKS + " Ticks: " + counts());
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
            List<Integer> entities = new ArrayList<>(FRAMES);
            entities.addAll(DISPLAYS);
            entities.addAll(GROUND);
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
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
                player.getInventory().setItem(HAND_SLOT, ItemStack.EMPTY);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                select(player, 0);
            }));
            FRAMES.clear();
            DISPLAYS.clear();
            GROUND.clear();
            BLOCKS.clear();
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        return Screenshots.enabled() ? Screenshots.restoreWindow(ctx) : StepResult.pass();
    }

    private record View(Vec3 eye, Vec3 target, int fov) implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            Vec3 eye = at(base(), this.eye);
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    select(player, EMPTY_SLOT);
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
