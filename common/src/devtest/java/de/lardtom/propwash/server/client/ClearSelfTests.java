package de.lardtom.propwash.server.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.server.DroneClear;
import de.lardtom.propwash.server.RangeManager;
import de.lardtom.propwash.server.ViewAnchors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class ClearSelfTests {
    static final String NAME = "server: /propwash clear entfernt alle Drohnen, FPV endet sauber";
    private static final String PROBE = "probe";
    private static final int START_TICKS = 300;
    private static final int END_TICKS = 200;
    private static final int INSPECT_INTERVAL = 5;

    private ClearSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(NAME, 900, ClearSelfTests::step);
    }

    private static final class Probe {
        private int phase;
        private int since;
        private @Nullable CompletableFuture<Void> pending;
        private volatile int flownId = -1;
        private volatile @Nullable UUID player;
        private volatile int before = -1;
        private volatile int result = Integer.MIN_VALUE;
        private volatile DroneClear.@Nullable Tally tally;
        private volatile @Nullable String serverProblem = "noch nicht geprüft";
        private ItemStack head = ItemStack.EMPTY;
        private ItemStack slot = ItemStack.EMPTY;
        private int selected;
        private volatile boolean saved;
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Probe probe = (Probe) ctx.state().get(PROBE);
        if (probe == null) {
            if (ctx.tick() < DronePiloting.REMOTE_DEBOUNCE_TICKS + 2) {
                return StepResult.pending();
            }
            Probe created = new Probe();
            ctx.state().put(PROBE, created);
            created.pending = ctx.onServer(server -> setup(server, created));
            created.since = ctx.tick();
            return StepResult.pending();
        }
        CompletableFuture<Void> pending = probe.pending;
        if (pending != null) {
            if (pending.isCompletedExceptionally()) {
                return finish(ctx, probe, StepResult.fail("Server-Schritt fehlgeschlagen: " + pending.exceptionNow()));
            }
            if (!pending.isDone()) {
                return StepResult.pending();
            }
            probe.pending = null;
        }
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        switch (probe.phase) {
            case 0 -> {
                QuadcopterEntity drone = session.drone();
                if (!session.isFpv() || drone == null || drone.getId() != probe.flownId || mc.getCameraEntity() != drone) {
                    return ctx.tick() - probe.since > START_TICKS
                            ? finish(ctx, probe, StepResult.fail("keine FPV-Sitzung: FPV " + session.isFpv() + ", Drohne "
                            + (drone == null ? "-" : drone.getId()) + " statt " + probe.flownId + ", Kamera " + mc.getCameraEntity()))
                            : StepResult.pending();
                }
                mc.gui.hud.getChat().clearMessages(false);
                probe.phase = 1;
                probe.pending = ctx.onServer(server -> clear(server, probe));
                return StepResult.pending();
            }
            case 1 -> {
                if (probe.before < 2) {
                    return finish(ctx, probe, StepResult.fail("vor dem Befehl nur " + probe.before + " Drohnen geladen"));
                }
                if (probe.result != probe.before) {
                    return finish(ctx, probe, StepResult.fail("Befehl meldet " + probe.result + " statt " + probe.before + " Drohnen"));
                }
                probe.phase = 2;
                probe.since = ctx.tick();
                return StepResult.pending();
            }
            case 2 -> {
                List<String> problems = clientProblems(mc, session, probe);
                if (probe.serverProblem != null) {
                    problems.add("Server: " + probe.serverProblem);
                }
                if (problems.isEmpty()) {
                    probe.phase = 3;
                    return finish(ctx, probe, StepResult.pass());
                }
                if (ctx.tick() - probe.since > END_TICKS) {
                    return finish(ctx, probe, StepResult.fail(String.join("; ", problems)));
                }
                if ((ctx.tick() - probe.since) % INSPECT_INTERVAL == 0) {
                    probe.pending = ctx.onServer(server -> inspect(server, probe));
                }
                return StepResult.pending();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static List<String> clientProblems(Minecraft mc, PilotSession session, Probe probe) {
        List<String> problems = new ArrayList<>();
        if (session.isActive()) {
            problems.add("FPV-Sitzung läuft noch");
        }
        if (mc.getCameraEntity() != mc.player) {
            problems.add("Kamera nicht beim Spieler: " + mc.getCameraEntity());
        }
        int drones = 0;
        if (mc.level != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof QuadcopterEntity) {
                    drones++;
                }
            }
        }
        if (drones > 0) {
            problems.add(drones + " Drohnen am Client");
        }
        List<String> chat = chat();
        String removed = Component.translatable(DroneClear.REMOVED).getString();
        if (!chat.contains(removed)) {
            problems.add("Chat ohne „" + removed + "“: " + chat);
        }
        DroneClear.Tally tally = probe.tally;
        String done = tally == null ? "?" : tally.message().getString();
        if (!chat.contains(done)) {
            problems.add("Chat ohne „" + done + "“: " + chat);
        }
        return problems;
    }

    private static List<String> chat() {
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

    private static StepResult finish(SelfTest.StepContext ctx, Probe probe, StepResult result) {
        if (probe.saved) {
            probe.saved = false;
            ctx.onServer(server -> restore(server, probe));
        }
        return result;
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void setup(MinecraftServer server, Probe probe) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        probe.player = player.getUUID();
        probe.head = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        probe.slot = player.getInventory().getItem(0).copy();
        probe.selected = player.getInventory().getSelectedSlot();
        probe.saved = true;
        int x = player.getBlockX() + 3;
        int z = player.getBlockZ() + 3;
        QuadcopterEntity flown = spawn(level, x, z);
        spawn(level, x + 2, z);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(flown));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(flown));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, flown, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
        probe.flownId = flown.getId();
    }

    private static QuadcopterEntity spawn(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        drone.snapTo(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        return drone;
    }

    private static void clear(MinecraftServer server, Probe probe) {
        List<QuadcopterEntity> drones = DroneClear.loaded(server);
        probe.before = drones.size();
        probe.tally = DroneClear.tally(drones);
        AtomicInteger result = new AtomicInteger(Integer.MIN_VALUE);
        server.getCommands().performPrefixedCommand(player(server).createCommandSourceStack()
                .withCallback((success, value) -> result.set(success ? value : -1)), "propwash clear");
        probe.result = result.get();
        inspect(server, probe);
    }

    private static void inspect(MinecraftServer server, Probe probe) {
        UUID id = probe.player;
        ServerPlayer player = id == null ? null : server.getPlayerList().getPlayer(id);
        List<String> problems = new ArrayList<>();
        int left = DroneClear.loaded(server).size();
        if (left != 0) {
            problems.add(left + " Drohnen geladen");
        }
        if (id != null && DronePiloting.pilotedBy(id).isPresent()) {
            problems.add("Spieler fliegt noch");
        }
        if (id != null && RangeManager.link(id).isPresent()) {
            problems.add("Pilot-Verbindung besteht");
        }
        if (player != null && ViewAnchors.isAnchored(player)) {
            problems.add("Sichtanker an der Drohne");
        }
        probe.serverProblem = problems.isEmpty() ? null : String.join(", ", problems);
    }

    private static void restore(MinecraftServer server, Probe probe) {
        ServerPlayer player = player(server);
        player.setItemSlot(EquipmentSlot.HEAD, probe.head);
        player.getInventory().setItem(0, probe.slot);
        player.getInventory().setSelectedSlot(probe.selected);
        player.connection.send(new ClientboundSetHeldSlotPacket(probe.selected));
    }
}
