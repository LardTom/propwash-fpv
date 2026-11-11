package de.lardtom.propwash.dev;

import com.mojang.authlib.GameProfile;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.operator.OperatorServer;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.osd.SpectatorCountServer;
import de.lardtom.propwash.server.SpectatorLinks;
import de.lardtom.propwash.video.SpectateServer;
import de.lardtom.propwash.video.net.SpectateC2S;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class SpecCountJoinCheck {
    public static final int MODE_PILOT = 1;
    public static final int MODE_WATCH = 2;
    public static final String SPECTATE = "PwSpectate";
    public static final String GOGGLES = "PwBrille";
    public static final String GIMBAL = "PwGimbal";
    public static final String TRANSMITTER = "PwSender";
    public static final DroneBuild BUILD = DroneBuild.FREESTYLE;
    public static final List<Integer> EXPECTED = List.of(1, 2, 1, 2);
    public static final int SETTLE_TICKS = 60;
    public static final int STEP_TICKS = 80;
    public static final int WATCH_TIMEOUT_TICKS = 20 * 120;
    private static final UUID SPECTATE_ID = UUID.fromString("00000000-0000-4000-8000-00000005c001");
    private static final UUID GOGGLES_ID = UUID.fromString("00000000-0000-4000-8000-00000005c002");
    private static final UUID GIMBAL_ID = UUID.fromString("00000000-0000-4000-8000-00000005c003");
    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<Step> STEPS = new ArrayList<>();
    private static int stage;
    private static int mark = -1;
    private static int step;
    private static int sentAtStart;
    private static int startedAt;
    private static boolean finished;
    private static boolean cleaned;
    private static @Nullable UUID player;
    private static @Nullable QuadcopterEntity drone;

    private record Step(String label, Consumer<MinecraftServer> action, int expected, boolean gimbal) {
    }

    private SpecCountJoinCheck() {
    }

    public static boolean watchMode() {
        return JoinTestEnv.specCount() == MODE_WATCH;
    }

    static void tick(MinecraftServer server, int tick) {
        if (cleaned) {
            return;
        }
        try {
            advance(server, tick);
        } catch (RuntimeException e) {
            Constants.LOG.error("[Beitrittstest] Zuschauerzahl: Stufe {} fehlgeschlagen", stage, e);
            fail("Ausnahme in Stufe " + stage + ": " + e);
            finished = true;
        }
    }

    public static @Nullable String failure() {
        if (!finished) {
            return "nicht fertig (Stufe " + stage + ", Schritt " + step + " von " + STEPS.size() + ")";
        }
        return FAILURES.isEmpty() ? null : String.join(" | ", FAILURES);
    }

    private static void advance(MinecraftServer server, int tick) {
        ServerPlayer real = real(server);
        if (stage > 0 && real == null) {
            cleanup(server);
            return;
        }
        int waited = mark < 0 ? 0 : tick - mark;
        switch (stage) {
            case 0 -> {
                if (real == null) {
                    return;
                }
                if (mark < 0) {
                    mark = tick;
                    return;
                }
                if (waited < SETTLE_TICKS) {
                    return;
                }
                player = real.getUUID();
                sentAtStart = SpectatorCountServer.sent();
                startedAt = tick;
                drone = watchMode() ? transmitter(real) : flown(real);
                if (drone == null) {
                    fail("keine Drohne");
                    finished = true;
                    stage = 9;
                    return;
                }
                stage = 1;
                mark = tick;
            }
            case 1 -> {
                QuadcopterEntity target = require();
                boolean ready = watchMode() ? SpectateServer.watching(real) == target : DronePiloting.pilotedBy(real.getUUID()).orElse(null) == target;
                if (!ready) {
                    if (waited > WATCH_TIMEOUT_TICKS) {
                        fail(watchMode() ? real.getPlainTextName() + " schaut nicht zu" : real.getPlainTextName() + " fliegt nicht");
                        finished = true;
                        stage = 9;
                    }
                    return;
                }
                if (waited < SETTLE_TICKS) {
                    return;
                }
                int before = SpectatorCountServer.count(server, target);
                int wanted = watchMode() ? 1 : 0;
                Constants.LOG.info("[Beitrittstest] Zuschauerzahl: {} {} Drohne {}, Zahl {} (erwartet {})", real.getPlainTextName(),
                        watchMode() ? "schaut mit der Brille zu," : "fliegt im FPV,", target.getId(), before, wanted);
                if (before != wanted) {
                    fail("Zahl vor den Zuschauern " + before + " statt " + wanted);
                }
                joinViewers(server, target);
                plan(target);
                step = 0;
                stage = 2;
                mark = tick - STEP_TICKS;
            }
            case 2 -> {
                if (waited < STEP_TICKS) {
                    return;
                }
                QuadcopterEntity target = require();
                if (step > 0) {
                    check(server, target, STEPS.get(step - 1));
                }
                if (step < STEPS.size()) {
                    Step next = STEPS.get(step);
                    next.action().accept(server);
                    step++;
                    mark = tick;
                    return;
                }
                int sent = SpectatorCountServer.sent() - sentAtStart;
                Constants.LOG.info("[Beitrittstest] Zuschauerzahl: {} Pakete an {} für {} Änderungen in {} Ticks", sent, real.getPlainTextName(),
                        EXPECTED.size(), tick - startedAt);
                if (sent != EXPECTED.size()) {
                    fail(sent + " Pakete statt " + EXPECTED.size());
                }
                finished = true;
                stage = 3;
                mark = tick;
            }
            default -> {
            }
        }
    }

    private static void plan(QuadcopterEntity target) {
        STEPS.clear();
        UUID id = target.getUUID();
        if (watchMode()) {
            STEPS.add(new Step(GIMBAL + " wird Operator am Gimbal", server -> operate(server, target), 1, true));
            STEPS.add(new Step(GOGGLES + " schaltet die Brille auf den Kanal", server -> request(server, GOGGLES_ID, SpectateC2S.watch(id)), 2, false));
            STEPS.add(new Step(GOGGLES + " schaltet weg", server -> request(server, GOGGLES_ID, SpectateC2S.STOP), 1, false));
            STEPS.add(new Step(GOGGLES + " schaltet zurück", server -> request(server, GOGGLES_ID, SpectateC2S.watch(id)), 2, false));
            return;
        }
        STEPS.add(new Step(SPECTATE + " schaut über die Kanalliste zu", server -> request(server, SPECTATE_ID, SpectateC2S.watch(id)), 1, false));
        STEPS.add(new Step(GOGGLES + " schaltet die Brille auf den Kanal", server -> request(server, GOGGLES_ID, SpectateC2S.watch(id)), 2, false));
        STEPS.add(new Step(GIMBAL + " wird Operator am Gimbal", server -> operate(server, target), 2, true));
        STEPS.add(new Step(GOGGLES + " schaltet weg", server -> request(server, GOGGLES_ID, SpectateC2S.STOP), 1, false));
        STEPS.add(new Step(GOGGLES + " schaltet zurück", server -> request(server, GOGGLES_ID, SpectateC2S.watch(id)), 2, false));
    }

    private static void check(MinecraftServer server, QuadcopterEntity target, Step done) {
        int count = SpectatorCountServer.count(server, target);
        List<String> watching = new ArrayList<>();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (SpectateServer.watching(other) == target || SpectatorLinks.watched(other) == target) {
                watching.add(other.getPlainTextName() + (SpectateServer.watching(other) == target ? "" : " (nur Bild)"));
            }
        }
        Constants.LOG.info("[Beitrittstest] Zuschauerzahl: {}: Drohne {} zählt {} (erwartet {}), Bild bei {}, Operator {}", done.label(), target.getId(), count,
                done.expected(), watching, OperatorServer.operator(target).map(ServerPlayer::getPlainTextName).orElse("keiner"));
        if (count != done.expected()) {
            fail(done.label() + ": Zahl " + count + " statt " + done.expected());
        }
        ServerPlayer gimbal = server.getPlayerList().getPlayer(GIMBAL_ID);
        if (done.gimbal() && (gimbal == null || SpectatorLinks.watched(gimbal) != target)) {
            fail(GIMBAL + " hat als Operator kein Bild der Drohne");
        }
    }

    private static @Nullable QuadcopterEntity flown(ServerPlayer real) {
        BlockPos ground = real.blockPosition();
        CovFlightJoinSupport.clearAbove(real.level(), ground, 5, 12, null);
        real.teleportTo(real.level(), ground.getX() + 0.5, ground.getY(), ground.getZ() - 3.5, Set.<Relative>of(), 0.0F, 0.0F, true);
        return CovFlightJoinSupport.startFpv(real, BUILD, ground.getX() + 0.5, ground.getY() + 0.05, ground.getZ() + 0.5, 0.0F, "Zuschauerzahl");
    }

    private static @Nullable QuadcopterEntity transmitter(ServerPlayer real) {
        ServerLevel level = real.level();
        BlockPos ground = real.blockPosition();
        CovFlightJoinSupport.clearAbove(level, ground, 5, 12, null);
        real.setGameMode(GameType.CREATIVE);
        DronePiloting.pilotedBy(real.getUUID()).ifPresent(DronePiloting::release);
        real.teleportTo(level, ground.getX() + 0.5, ground.getY(), ground.getZ() - 3.5, Set.<Relative>of(), 0.0F, 0.0F, true);
        real.setItemSlot(EquipmentSlot.HEAD, goggles(ContentIds.GOGGLES_CREATIVE));
        QuadcopterEntity spawned = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (spawned == null) {
            return null;
        }
        spawned.snapTo(ground.getX() + 0.5, ground.getY() + 0.05, ground.getZ() + 0.5, 0.0F, 0.0F);
        spawned.setAttitude(new Quaternionf().rotationY((float) Math.toRadians(180.0)));
        spawned.setBuild(BUILD);
        spawned.setBatteryCharge(1.0F);
        level.addFreshEntity(spawned);
        SpectateServer.simulateTransmitter(spawned, TRANSMITTER);
        Constants.LOG.info("[Beitrittstest] Zuschauerzahl: Drohne {} sendet als {} auf Kanal {}, {} trägt die Kreativ-Brille", spawned.getId(), TRANSMITTER,
                SpectateServer.channelOf(spawned), real.getPlainTextName());
        return spawned;
    }

    private static void joinViewers(MinecraftServer server, QuadcopterEntity target) {
        VideoSystem system = target.build().video().system;
        Identifier matching = system == VideoSystem.ANALOG ? ContentIds.GOGGLES_ANALOG : system == VideoSystem.DIGITAL ? ContentIds.GOGGLES_DIGITAL
                : ContentIds.GOGGLES_CREATIVE;
        if (!watchMode()) {
            join(server, SPECTATE_ID, SPECTATE, target.position().add(2.5, 0.0, 2.0), goggles(ContentIds.GOGGLES_CREATIVE));
        }
        join(server, GOGGLES_ID, GOGGLES, target.position().add(-2.5, 0.0, 2.0), goggles(matching));
        join(server, GIMBAL_ID, GIMBAL, target.position().add(0.0, 0.0, 3.5), ItemStack.EMPTY);
    }

    private static void join(MinecraftServer server, UUID id, String name, Vec3 at, ItemStack head) {
        ServerPlayer sim = server.getPlayerList().getPlayer(id);
        if (sim == null) {
            GameProfile profile = new GameProfile(id, name);
            sim = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, sim, CommonListenerCookie.createInitial(profile, false));
        }
        sim.teleportTo(server.overworld(), at.x, at.y, at.z, Set.<Relative>of(), 180.0F, 0.0F, true);
        sim.setItemSlot(EquipmentSlot.HEAD, head);
        Constants.LOG.info("[Beitrittstest] Zuschauerzahl: {} ist auf dem Server{}", name, head.isEmpty() ? " (ohne Brille)" : " mit " + head.getHoverName().getString());
    }

    @SuppressWarnings("unchecked")
    private static void request(MinecraftServer server, UUID id, SpectateC2S payload) {
        ServerPlayer sim = server.getPlayerList().getPlayer(id);
        if (sim == null) {
            fail("Spieler " + id + " fehlt");
            return;
        }
        for (PropwashNet.C2S<?> c2s : PropwashNet.freezeC2S()) {
            if (c2s.type().equals(SpectateC2S.TYPE)) {
                ((PropwashNet.C2S<SpectateC2S>) c2s).handler().handle(payload, new ServerPayloadContext(sim));
                return;
            }
        }
        fail("SpectateC2S nicht angemeldet");
    }

    private static void operate(MinecraftServer server, QuadcopterEntity target) {
        ServerPlayer gimbal = server.getPlayerList().getPlayer(GIMBAL_ID);
        if (gimbal == null || !OperatorServer.bind(target, gimbal)) {
            fail(GIMBAL + " wird nicht Operator");
        }
    }

    private static ItemStack goggles(Identifier id) {
        return ContentIds.item(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static QuadcopterEntity require() {
        QuadcopterEntity target = drone;
        if (target == null || target.isRemoved()) {
            throw new IllegalStateException("Drohne fehlt");
        }
        return target;
    }

    private static @Nullable ServerPlayer real(MinecraftServer server) {
        if (player != null) {
            ServerPlayer known = server.getPlayerList().getPlayer(player);
            return known == null || known.hasDisconnected() ? null : known;
        }
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            UUID id = candidate.getUUID();
            if (!id.equals(SPECTATE_ID) && !id.equals(GOGGLES_ID) && !id.equals(GIMBAL_ID)) {
                return candidate;
            }
        }
        return null;
    }

    private static void cleanup(MinecraftServer server) {
        cleaned = true;
        QuadcopterEntity target = drone;
        if (target != null) {
            OperatorServer.unbind(target);
            SpectateServer.simulateTransmitter(target, null);
        }
        for (UUID id : List.of(SPECTATE_ID, GOGGLES_ID, GIMBAL_ID)) {
            ServerPlayer sim = server.getPlayerList().getPlayer(id);
            if (sim != null) {
                sim.connection.onDisconnect(new DisconnectionDetails(Component.literal("Beitrittstest Zuschauerzahl beendet")));
            }
        }
        if (target != null && !target.isRemoved()) {
            target.discard();
        }
        Constants.LOG.info("[Beitrittstest] Zuschauerzahl: aufgeräumt, {}", failure() == null ? "bestanden" : "Befund: " + failure());
    }

    private static void fail(String reason) {
        FAILURES.add(reason);
        Constants.LOG.error("[Beitrittstest] Zuschauerzahl: {}", reason);
    }
}
