package de.lardtom.propwash.server.client;

import com.mojang.brigadier.tree.CommandNode;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.server.CovLogCapture;
import de.lardtom.propwash.server.DroneClear;
import de.lardtom.propwash.server.RangeManager;
import de.lardtom.propwash.server.ViewAnchors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class CovClearSelfTests {
    static final String GROUP = "server: Abdeckung /propwash clear – ";
    private static final int FAR_BLOCKS = 640;
    private static final BlockPos NETHER_SPOT = new BlockPos(56, 90, 56);
    private static final int STABLE_TICKS = 40;
    private static final String PHASE = "cov_clear_phase";
    private static final String MARK = "cov_clear_mark";
    private static final String FUTURE = "cov_clear_future";
    private static final AtomicReference<String> SERVER = new AtomicReference<>();
    private static final AtomicReference<UUID> FAR = new AtomicReference<>();
    private static final AtomicReference<BlockPos> FAR_POS = new AtomicReference<>();
    private static final AtomicReference<DroneClear.Tally> TALLY = new AtomicReference<>();
    private static final AtomicInteger COUNT = new AtomicInteger();
    private static final AtomicReference<String> TEXT = new AtomicReference<>("");
    private static final List<Vec3> PARKED = new ArrayList<>();
    private static final AtomicReference<Saved> LOS = new AtomicReference<>();
    private static final AtomicReference<Boolean> ANCHORED = new AtomicReference<>();
    private static final AtomicReference<Boolean> CHEATS = new AtomicReference<>();
    private static final AtomicReference<UUID> CHEATS_DRONE = new AtomicReference<>();

    record Saved(ItemStack head, ItemStack slot, int selected) {
    }

    private CovClearSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "drei abgestellte Drohnen, eine im Nether mit Forceload, eine im entladenen Chunk: Zählung je Dimension, keine Drops, "
                + "Log-Zeile mit Name, kein Chunk geladen", 3000, CovClearSelfTests::allStep);
        SelfTest.clientStep(GROUP + "im Sichtflug: Sitzung endet, kein Sichtanker, im Chat „Your drone was removed.“", 900, CovClearSelfTests::losStep);
        SelfTest.clientStep(GROUP + "Einzelspielerwelt ohne Cheats: Befehl nicht im Befehlsbaum und nicht ausführbar", 900, CovClearSelfTests::noCheatsStep);
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx) {
        ctx.log("Phase " + phase(ctx) + " fertig nach " + since(ctx) + " Ticks");
        ctx.state().put(PHASE, phase(ctx) + 1);
        ctx.state().put(MARK, ctx.tick());
        ctx.state().remove(FUTURE);
    }

    private static void status(SelfTest.StepContext ctx, String text) {
        if (since(ctx) % 200 >= 198) {
            ctx.log("Phase " + phase(ctx) + " nach " + since(ctx) + " Ticks: " + text);
        }
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    private static @Nullable StepResult server(SelfTest.StepContext ctx, java.util.function.Consumer<MinecraftServer> action) {
        Object existing = ctx.state().get(FUTURE);
        if (existing == null) {
            SERVER.set(null);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                try {
                    action.accept(server);
                } catch (RuntimeException e) {
                    SERVER.set("Ausnahme: " + e);
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) existing;
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        String problem = SERVER.getAndSet(null);
        return problem == null ? null : StepResult.fail(problem);
    }

    static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    static QuadcopterEntity spawn(ServerLevel level, Vec3 at) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        return drone;
    }

    static List<String> chat() {
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            List<?> messages = (List<?>) field.get(Minecraft.getInstance().gui.hud.getChat());
            List<String> out = new ArrayList<>();
            for (Object message : messages) {
                if (message instanceof GuiMessage gui) {
                    out.add(gui.content().getString());
                }
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of("? " + e);
        }
    }

    private static boolean droneOrPart(ItemStack stack) {
        return stack.is(DroneModule.DRONE_ITEM.get()) || stack.has(CoreComponents.PART.get())
                || PartIndex.registeredById(BuiltInRegistries.ITEM.getKey(stack.getItem())).isPresent();
    }

    private static StepResult allStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        switch (phase(ctx)) {
            case 0 -> {
                StepResult result = server(ctx, server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = server.overworld();
                    PARKED.clear();
                    for (int i = 0; i < 3; i++) {
                        int x = player.getBlockX() + 3 + 2 * i;
                        int z = player.getBlockZ() - 4;
                        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                        QuadcopterEntity drone = spawn(level, new Vec3(x + 0.5, y, z + 0.5));
                        PARKED.add(drone.position());
                    }
                    ServerLevel nether = server.getLevel(Level.NETHER);
                    if (nether == null) {
                        throw new IllegalStateException("kein Nether");
                    }
                    nether.setChunkForced(NETHER_SPOT.getX() >> 4, NETHER_SPOT.getZ() >> 4, true);
                    nether.getChunk(NETHER_SPOT.getX() >> 4, NETHER_SPOT.getZ() >> 4);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            nether.setBlock(NETHER_SPOT.offset(dx, -1, dz), Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
                            for (int h = 0; h < 3; h++) {
                                nether.setBlock(NETHER_SPOT.offset(dx, h, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                            }
                        }
                    }
                    spawn(nether, Vec3.atBottomCenterOf(NETHER_SPOT));
                    int fx = player.getBlockX() + FAR_BLOCKS;
                    int fz = player.getBlockZ() + FAR_BLOCKS;
                    level.setChunkForced(fx >> 4, fz >> 4, true);
                    level.getChunk(fx >> 4, fz >> 4);
                    int fy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, fx, fz);
                    FAR.set(spawn(level, new Vec3(fx + 0.5, fy, fz + 0.5)).getUUID());
                    FAR_POS.set(new BlockPos(fx, fy, fz));
                    level.setChunkForced(fx >> 4, fz >> 4, false);
                });
                if (result == null) {
                    next(ctx);
                    return StepResult.pending();
                }
                return result;
            }
            case 1 -> {
                StepResult result = server(ctx, server -> {
                    ServerLevel level = server.overworld();
                    BlockPos far = FAR_POS.get();
                    boolean gone = level.getEntity(FAR.get()) == null && !level.getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4);
                    SERVER.set(gone ? null : "warte");
                    COUNT.set(level.getChunkSource().getLoadedChunksCount());
                    TEXT.set("ferne Drohne " + (level.getEntity(FAR.get()) == null ? "weg" : "da") + ", Chunk " + (level.getChunkSource().hasChunk(
                            far.getX() >> 4, far.getZ() >> 4) ? "geladen" : "entladen") + ", " + COUNT.get() + " Chunks");
                });
                status(ctx, TEXT.get());
                if (result == null) {
                    state.put("cov_clear_count", COUNT.get());
                    next(ctx);
                    return StepResult.pending();
                }
                if (result.status() == StepResult.Status.FAIL && "warte".equals(result.message())) {
                    return since(ctx) > 1200 ? StepResult.fail("ferner Chunk entlädt nicht") : StepResult.pending();
                }
                return result;
            }
            case 2 -> {
                StepResult result = server(ctx, server -> COUNT.set(server.overworld().getChunkSource().getLoadedChunksCount()));
                if (result != null) {
                    return result;
                }
                int count = COUNT.get();
                status(ctx, count + " Chunks");
                Object last = state.get("cov_clear_count");
                if (last == null || (int) last != count || !state.containsKey("cov_clear_stable")) {
                    state.put("cov_clear_count", count);
                    state.put("cov_clear_stable", ctx.tick());
                    return since(ctx) > 1200 ? StepResult.fail("Zahl der Chunks wird nicht ruhig") : StepResult.pending();
                }
                if (ctx.tick() - (int) state.getOrDefault("cov_clear_stable", ctx.tick()) < STABLE_TICKS) {
                    return StepResult.pending();
                }
                mc.gui.hud.getChat().clearMessages(false);
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                StepResult result = server(ctx, server -> SERVER.set(clearAll(server)));
                if (result != null) {
                    return result;
                }
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                DroneClear.Tally tally = TALLY.get();
                String wanted = tally == null ? "?" : tally.message().getString();
                List<String> chat = chat();
                if (!chat.contains(wanted)) {
                    return since(ctx) > 100 ? StepResult.fail("Chat ohne „" + wanted + "“: " + chat) : StepResult.pending();
                }
                ctx.log("Chat: „" + wanted + "“");
                next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                StepResult result = server(ctx, server -> {
                    ServerLevel level = server.overworld();
                    BlockPos far = FAR_POS.get();
                    level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
                    Entity drone = level.getEntity(FAR.get());
                    if (drone == null) {
                        SERVER.set("warte");
                        return;
                    }
                    drone.discard();
                    level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
                    ServerLevel nether = server.getLevel(Level.NETHER);
                    if (nether != null) {
                        nether.setChunkForced(NETHER_SPOT.getX() >> 4, NETHER_SPOT.getZ() >> 4, false);
                    }
                });
                if (result == null) {
                    ctx.log("ferne Drohne nach dem Wiederladen noch da, entfernt");
                    return StepResult.pass();
                }
                if (result.status() == StepResult.Status.FAIL && "warte".equals(result.message())) {
                    return since(ctx) > 600 ? StepResult.fail("ferne Drohne nach dem Wiederladen nicht da") : StepResult.pending();
                }
                return result;
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static @Nullable String clearAll(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = server.overworld();
        int chunksBefore = level.getChunkSource().getLoadedChunksCount();
        List<QuadcopterEntity> loaded = DroneClear.loaded(server);
        UUID far = FAR.get();
        if (loaded.stream().anyMatch(drone -> drone.getUUID().equals(far))) {
            return "ferne Drohne zählt als geladen";
        }
        DroneClear.Tally tally = DroneClear.tally(loaded);
        TALLY.set(tally);
        if (tally.count("the_nether") != 1 || tally.count("overworld") < 3) {
            return "vor dem Befehl: " + tally.text();
        }
        AtomicInteger result = new AtomicInteger(Integer.MIN_VALUE);
        List<String> log;
        try (CovLogCapture capture = CovLogCapture.propwash()) {
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withCallback((success, value) -> result.set(success ? value : -1)),
                    "propwash clear");
            log = capture.matching("/propwash clear von " + player.getPlainTextName() + ": entfernt");
        }
        if (result.get() != loaded.size()) {
            return "/propwash clear meldet " + result.get() + " statt " + loaded.size();
        }
        if (!DroneClear.loaded(server).isEmpty()) {
            return DroneClear.loaded(server).size() + " Drohnen noch geladen";
        }
        String expected = "/propwash clear von " + player.getPlainTextName() + ": entfernt " + tally.total() + " (" + tally.text() + ")";
        if (log.size() != 1 || !log.getFirst().startsWith(expected)) {
            return "Log-Zeile „" + expected + " …“ fehlt: " + log;
        }
        for (Vec3 at : PARKED) {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(6.0))) {
                if (droneOrPart(item.getItem())) {
                    return "Drop nach /propwash clear: " + item.getItem();
                }
            }
        }
        BlockPos farPos = FAR_POS.get();
        int chunksAfter = level.getChunkSource().getLoadedChunksCount();
        if (level.getChunkSource().hasChunk(farPos.getX() >> 4, farPos.getZ() >> 4) || chunksAfter != chunksBefore) {
            return "Chunks geladen: ferner Chunk " + level.getChunkSource().hasChunk(farPos.getX() >> 4, farPos.getZ() >> 4) + ", Zahl " + chunksBefore + " → "
                    + chunksAfter;
        }
        return null;
    }

    private static StepResult losStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        PilotSession session = PilotSession.INSTANCE;
        switch (phase(ctx)) {
            case 0 -> {
                if (ctx.tick() < DronePiloting.REMOTE_DEBOUNCE_TICKS + 2) {
                    return StepResult.pending();
                }
                StepResult result = server(ctx, server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    LOS.set(new Saved(player.getItemBySlot(EquipmentSlot.HEAD).copy(), player.getInventory().getItem(0).copy(),
                            player.getInventory().getSelectedSlot()));
                    int x = player.getBlockX() + 3;
                    int z = player.getBlockZ() + 2;
                    QuadcopterEntity drone = spawn(level, new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5));
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
                    remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                    player.getInventory().setItem(0, remote);
                    player.getInventory().setSelectedSlot(0);
                    player.connection.send(new ClientboundSetHeldSlotPacket(0));
                    DronePiloting.StartResult started = DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT);
                    if (started != DronePiloting.StartResult.STARTED) {
                        SERVER.set("Sichtflug-Start verweigert: " + started);
                    }
                });
                if (result == null) {
                    next(ctx);
                    return StepResult.pending();
                }
                return result;
            }
            case 1 -> {
                if (!session.isActive() || session.isFpv() || session.mode() != PilotMode.LINE_OF_SIGHT) {
                    return since(ctx) > 300 ? finishLos(ctx, StepResult.fail("kein Sichtflug: aktiv " + session.isActive() + ", FPV " + session.isFpv()))
                            : StepResult.pending();
                }
                if (since(ctx) < 20) {
                    return StepResult.pending();
                }
                mc.gui.hud.getChat().clearMessages(false);
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                StepResult result = server(ctx, server -> {
                    ServerPlayer player = player(server);
                    ANCHORED.set(ViewAnchors.isAnchored(player));
                    AtomicInteger cleared = new AtomicInteger(Integer.MIN_VALUE);
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withCallback((success, value) -> cleared.set(success ? value : -1)),
                            "propwash clear");
                    if (cleared.get() < 1) {
                        SERVER.set("/propwash clear meldet " + cleared.get());
                    }
                });
                if (result == null) {
                    next(ctx);
                    return StepResult.pending();
                }
                return result.status() == StepResult.Status.PENDING ? result : finishLos(ctx, result);
            }
            case 3 -> {
                String removed = Component.translatable(DroneClear.REMOVED).getString();
                List<String> problems = new ArrayList<>();
                if (session.isActive()) {
                    problems.add("Sichtflug läuft noch");
                }
                if (mc.getCameraEntity() != mc.player) {
                    problems.add("Kamera nicht beim Spieler");
                }
                if (!chat().contains(removed)) {
                    problems.add("Chat ohne „" + removed + "“: " + chat());
                }
                if (!problems.isEmpty()) {
                    return since(ctx) > 200 ? finishLos(ctx, StepResult.fail(String.join("; ", problems))) : StepResult.pending();
                }
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                StepResult result = server(ctx, server -> {
                    ServerPlayer player = player(server);
                    List<String> problems = new ArrayList<>();
                    if (ViewAnchors.isAnchored(player)) {
                        problems.add("Sichtanker an der Drohne");
                    }
                    if (DronePiloting.pilotedBy(player.getUUID()).isPresent() || RangeManager.link(player.getUUID()).isPresent()) {
                        problems.add("Pilot-Sitzung oder Verbindung besteht");
                    }
                    if (!DroneClear.loaded(server).isEmpty()) {
                        problems.add("Drohnen geladen");
                    }
                    SERVER.set(problems.isEmpty() ? null : String.join(", ", problems));
                });
                if (result == null) {
                    ctx.log("Sichtflug beendet, Sichtanker vorher " + ANCHORED.get() + " und danach weg, Chat „"
                            + Component.translatable(DroneClear.REMOVED).getString() + "“");
                    return finishLos(ctx, StepResult.pass());
                }
                return result.status() == StepResult.Status.PENDING ? result : finishLos(ctx, result);
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static StepResult finishLos(SelfTest.StepContext ctx, StepResult result) {
        Map<String, Object> state = ctx.state();
        Saved saved = LOS.getAndSet(null);
        ctx.onServer(server -> {
            ServerPlayer player = player(server);
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            if (saved != null) {
                player.setItemSlot(EquipmentSlot.HEAD, saved.head());
                player.getInventory().setItem(0, saved.slot());
                player.getInventory().setSelectedSlot(saved.selected());
                player.connection.send(new ClientboundSetHeldSlotPacket(saved.selected()));
            }
        });
        state.put(PHASE, 99);
        return result;
    }

    private static StepResult noCheatsStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        switch (phase(ctx)) {
            case 0 -> {
                StepResult result = server(ctx, server -> {
                    if (!(server instanceof IntegratedServer integrated)) {
                        SERVER.set("kein integrierter Server");
                        return;
                    }
                    ServerPlayer player = player(server);
                    CHEATS.set(server.getWorldData().isAllowCommands());
                    ServerLevel level = player.level();
                    int x = player.getBlockX() - 3;
                    int z = player.getBlockZ() + 3;
                    CHEATS_DRONE.set(spawn(level, new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5)).getUUID());
                    integrated.setWorldAllowCommands(false);
                });
                if (result == null) {
                    next(ctx);
                    return StepResult.pending();
                }
                return result;
            }
            case 1 -> {
                if (mc.player == null || mc.getConnection() == null || mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                    return since(ctx) > 200 ? restoreCheats(ctx, StepResult.fail("Spieler behält Cheats")) : StepResult.pending();
                }
                CommandNode<?> propwash = mc.getConnection().getCommands().getRoot().getChild("propwash");
                if (propwash != null && propwash.getChild("clear") != null) {
                    return since(ctx) > 200 ? restoreCheats(ctx, StepResult.fail("„propwash clear“ steht ohne Cheats im Befehlsbaum des Clients"))
                            : StepResult.pending();
                }
                if (since(ctx) < 10) {
                    return StepResult.pending();
                }
                state.put("cov_cheats_tree", propwash == null ? "ohne propwash" : "propwash ohne clear");
                mc.gui.hud.getChat().clearMessages(false);
                mc.getConnection().sendCommand("propwash clear");
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                String here = Component.translatable("command.context.here").getString();
                List<String> chat = chat();
                boolean refused = chat.stream().anyMatch(line -> line.contains(here) || line.contains(Component.translatable("command.unknown.command").getString()));
                if (!refused) {
                    return since(ctx) > 100 ? restoreCheats(ctx, StepResult.fail("keine Fehlermeldung für /propwash clear ohne Cheats: " + chat)) : StepResult.pending();
                }
                state.put("cov_cheats_chat", chat);
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                StepResult result = server(ctx, server -> {
                    UUID id = CHEATS_DRONE.get();
                    Entity drone = server.overworld().getEntity(id);
                    SERVER.set(drone == null || drone.isRemoved() ? "Drohne trotz fehlender Cheats entfernt" : null);
                });
                if (result == null) {
                    ctx.log("ohne Cheats: Befehlsbaum " + state.get("cov_cheats_tree") + ", Chat " + state.get("cov_cheats_chat") + ", Drohne bleibt");
                    return restoreCheats(ctx, StepResult.pass());
                }
                return result.status() == StepResult.Status.PENDING ? result : restoreCheats(ctx, result);
            }
            case 4 -> {
                if (mc.player == null || !mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                    return since(ctx) > 200 ? StepResult.fail("Cheats kommen nicht zurück") : StepResult.pending();
                }
                return (StepResult) state.get("cov_cheats_result");
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static StepResult restoreCheats(SelfTest.StepContext ctx, StepResult result) {
        Map<String, Object> state = ctx.state();
        boolean cheats = CHEATS.get() == null || CHEATS.get();
        UUID drone = CHEATS_DRONE.getAndSet(null);
        ctx.onServer(server -> {
            if (server instanceof IntegratedServer integrated) {
                integrated.setWorldAllowCommands(cheats);
            }
            if (drone != null && server.overworld().getEntity(drone) instanceof QuadcopterEntity quad) {
                quad.discard();
            }
        });
        state.put("cov_cheats_result", result);
        state.put(PHASE, 4);
        state.put(MARK, ctx.tick());
        return StepResult.pending();
    }
}
