package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StackGateJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final int AWAY = 24;
    public static final int REPORT_EVERY = 100;
    public static final float TURN = 45.0F;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    public record Ladder(GateEntity bottom, GateEntity top, int course) {
    }

    private static final class Track {
        final int joined;
        int built = -1;
        int course = GateEntity.NO_COURSE;
        int bottom = -1;
        boolean accepted;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private StackGateJoinCheck() {
    }

    public static InteractionResult use(ServerPlayer player, GateSize size, int x, int groundY, int z, float yaw) {
        ItemStack previous = player.getMainHandItem().copy();
        float previousYaw = player.getYRot();
        ItemStack stack = new ItemStack(RaceModule.gateItem(size).orElseThrow());
        try {
            player.setYRot(yaw);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockPos ground = new BlockPos(x, groundY - 1, z);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false);
            return stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setYRot(previousYaw);
        }
    }

    public static GateEntity place(ServerPlayer player, GateSize size, int x, int groundY, int z, float yaw) {
        InteractionResult result = use(player, size, x, groundY, z, yaw);
        if (!result.consumesAction()) {
            throw new IllegalStateException("Gate bei " + x + "," + z + " nicht platziert: " + result);
        }
        List<GateEntity> found = player.level().getEntitiesOfClass(GateEntity.class, new AABB(new BlockPos(x, groundY, z)).inflate(0.4),
                gate -> !gate.isRemoved() && gate.getY() < groundY + 0.1);
        if (found.size() != 1) {
            throw new IllegalStateException("Gate bei " + x + "," + z + " nicht gefunden: " + found.size());
        }
        return found.getFirst();
    }

    public static GateEntity stacked(ServerPlayer player, GateEntity target, GateSize size, float yaw) {
        return stacked(player, target, size, yaw, 0.0F);
    }

    public static GateEntity stacked(ServerPlayer player, GateEntity target, GateSize size, float yaw, float turn) {
        ItemStack previous = player.getMainHandItem().copy();
        float previousYaw = player.getYRot();
        GatePlacement.Preference preference = GateMounting.preference(player, false);
        InteractionResult result;
        try {
            player.setYRot(yaw);
            GateMounting.setPreference(player, turn == 0.0F ? GatePlacement.Preference.DEFAULT
                    : GatePlacement.Preference.DEFAULT.turnedBy(GateMount.Surface.FLOOR, turn));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.gateItem(size).orElseThrow()));
            result = target.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setYRot(previousYaw);
            GateMounting.setPreference(player, preference);
        }
        if (!result.consumesAction()) {
            throw new IllegalStateException("Gate " + size + " nicht gestapelt: " + result);
        }
        GateEntity above = target.stack().getLast();
        if (above == target || above.size() != size) {
            throw new IllegalStateException("gestapeltes Gate nicht gefunden");
        }
        return above;
    }

    public static Ladder buildLadder(ServerPlayer player, int x, int groundY, int z) {
        return buildLadder(player, x, groundY, z, 0.0F);
    }

    public static Ladder buildLadder(ServerPlayer player, int x, int groundY, int z, float turn) {
        GateEntity bottom = place(player, GateSize.MEDIUM, x, groundY, z, 0.0F);
        GateEntity top = stacked(player, bottom, GateSize.MEDIUM, 0.0F, turn);
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        CourseToolItem.useOnGate(tool, player, bottom);
        CourseToolItem.useOnGate(tool, player, top);
        int course = CourseToolItem.courseOf(tool);
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course data = registry == null ? null : registry.course(course);
        if (data == null || data.gates.size() != 2 || bottom.gateIndex() != 0 || top.gateIndex() != 1) {
            throw new IllegalStateException("Leiter nicht als Kurs angelegt: " + (data == null ? "kein Kurs" : data.gates.size() + " Gates"));
        }
        registry.setLaps(data, 1);
        bottom.refreshColumn();
        return new Ladder(bottom, top, course);
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            if (track.built < 0) {
                if (tick - track.joined >= SETTLE_TICKS) {
                    start(server, player, track, tick);
                }
                continue;
            }
            watch(player, track, tick);
        }
    }

    private static void start(MinecraftServer server, ServerPlayer player, Track track, int tick) {
        track.built = tick;
        ServerLevel level = player.level();
        int x = player.getBlockX() + AWAY;
        int z = player.getBlockZ();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        try {
            WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
            boolean turned = JoinTestEnv.stackGateTurned();
            Ladder ladder = buildLadder(player, x, ground, z, turned ? TURN : 0.0F);
            track.course = ladder.course();
            track.bottom = ladder.bottom().getId();
            GateEntity bottom = ladder.bottom();
            if (turned) {
                turn(player, ladder, ground);
            }
            player.teleportTo(level, bottom.getX(), ground, bottom.getZ() - 7.0, Set.of(), 0.0F, 0.0F, true);
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                Constants.LOG.error("[Beitrittstest] Gate-Stapel: Drohne nicht erzeugt");
                return;
            }
            drone.snapTo(bottom.getX(), ground + 1.0, bottom.getZ() - 4.0, 0.0F, 0.0F);
            drone.setAttitude(new Quaternionf());
            drone.setBuild(DroneBuild.FREESTYLE);
            drone.setBatteryCharge(1.0F);
            level.addFreshEntity(drone);
            ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.setItemSlot(EquipmentSlot.HEAD, goggles);
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.getInventory().setItem(0, remote);
            player.getInventory().setSelectedSlot(0);
            player.connection.send(new ClientboundSetHeldSlotPacket(0));
            DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
            Constants.LOG.info("[Beitrittstest] Gate-Stapel: Leiter als Kurs {} bei {} {} {} gebaut, unten {} (Öffnung ab {}), oben {}, FPV {}",
                    track.course, x, ground, z, bottom.base(), bottom.geometry().openingFloor(), ladder.top().base(), result);
        } catch (RuntimeException e) {
            Constants.LOG.error("[Beitrittstest] Gate-Stapel: Bau fehlgeschlagen", e);
        }
    }

    private static void turn(ServerPlayer player, Ladder ladder, int ground) {
        GateEntity bottom = ladder.bottom();
        GateEntity top = ladder.top();
        player.teleportTo(player.level(), bottom.getX(), ground, bottom.getZ() - 3.0, Set.of(), 0.0F, 0.0F, true);
        float placed = GateTilt.wrap(top.geometry().heading() - bottom.geometry().heading());
        GateMounting.Result key = GateMounting.rotate(player, top, false, TURN);
        float keyed = GateTilt.wrap(top.geometry().heading() - bottom.geometry().heading());
        GateMounting.Result menu = GateMounting.edit(player, top, bottom.geometry().heading() + TURN, 0.0F, 0.0F, top.openingWidth(),
                top.openingHeight());
        bottom.refreshColumn();
        Constants.LOG.info("[Beitrittstest] Gate-Stapel: oberes Gate beim Stapeln {}°, Dreh-Taste {} auf {}°, Gate-Menü {} auf {}°, Höhe {} (Balken {}), "
                        + "Fuß {}, unten {}", placed, key, keyed, menu, GateTilt.wrap(top.geometry().heading() - bottom.geometry().heading()),
                top.getY(), bottom.getY() + bottom.height(), top.base(), bottom.base());
    }

    private static void watch(ServerPlayer player, Track track, int tick) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(track.course);
        if (course == null || track.accepted) {
            return;
        }
        if ((tick - track.built) % REPORT_EVERY == 0 && player.level().getEntity(track.bottom) instanceof GateEntity bottom) {
            Constants.LOG.info("[Beitrittstest] Gate-Stapel: nach {} Ticks unten {} (Öffnung ab {}), Runden {}", tick - track.built, bottom.base(),
                    bottom.geometry().openingFloor(), registry.board(course).times());
        }
        List<Leaderboard.Entry> entries = new ArrayList<>(registry.board(course).entries());
        entries.addAll(registry.board(course).assistedEntries());
        for (Leaderboard.Entry entry : entries) {
            if (entry.player().equals(player.getUUID())) {
                track.accepted = true;
                GateEntity bottom = player.level().getEntity(track.bottom) instanceof GateEntity gate ? gate : null;
                Constants.LOG.info("[Beitrittstest] Gate-Stapel: Runde von {} angenommen: {} ms auf Kurs {}{}, unten {}",
                        player.getPlainTextName(), entry.nanos() / 1_000_000L, course.id, entry.assisted() ? " (mit Flughilfe)" : "",
                        bottom == null ? "-" : bottom.base() == GateStacking.Base.LEGS ? "Gate auf Beinen" : bottom.base());
                return;
            }
        }
    }
}
