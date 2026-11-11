package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.rescue.AutonomousReturns;
import de.lardtom.propwash.rescue.RescueSettings;
import de.lardtom.propwash.rescue.ReturnPlan;
import de.lardtom.propwash.video.SpectateServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class CovWatchRescueJoinCheck {
    public static final String PILOT = MultiJoinCheck.PILOT;
    public static final int SETTLE_TICKS = 60;
    public static final int WATCH_BEFORE_RESCUE = 120;
    public static final int RESCUE_TIMEOUT = 20 * 90;
    private static final UUID SENDER = UUID.fromString("00000000-0000-4000-8000-0000000c0c01");
    private static int stage;
    private static int mark;
    private static int watchedFrom = -1;
    private static int rescueTicks;
    private static int rescueWatched;
    private static @Nullable UUID drone;
    private static @Nullable UUID watcherId;
    private static BlockPos home = BlockPos.ZERO;
    private static double loopX;
    private static double loopY;
    private static double loopZ;

    private CovWatchRescueJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        ServerPlayer watcher = watcherId == null ? server.getPlayerList().getPlayers().stream().findFirst().orElse(null)
                : server.getPlayerList().getPlayer(watcherId);
        try {
            step(server, watcher, tick);
        } catch (RuntimeException e) {
            Constants.LOG.error("[Beitrittstest] Zuschauen mit GPS Rescue: Schritt {} fehlgeschlagen", stage, e);
            stage = 9;
        }
    }

    private static @Nullable QuadcopterEntity drone(MinecraftServer server) {
        UUID id = drone;
        return id != null && server.overworld().getEntity(id) instanceof QuadcopterEntity quad && !quad.isRemoved() ? quad : null;
    }

    private static void step(MinecraftServer server, @Nullable ServerPlayer watcher, int tick) {
        switch (stage) {
            case 0 -> {
                if (watcher == null) {
                    mark = tick;
                    return;
                }
                if (tick - mark < SETTLE_TICKS) {
                    return;
                }
                watcherId = watcher.getUUID();
                build(server, watcher);
                stage = 1;
                mark = tick;
            }
            case 1 -> {
                QuadcopterEntity flown = drone(server);
                if (flown == null) {
                    Constants.LOG.error("[Beitrittstest] Zuschauen mit GPS Rescue: Drohne verschwunden vor dem Rescue");
                    stage = 9;
                    return;
                }
                fly(flown, tick);
                if (watcher != null && SpectateServer.watching(watcher) == flown) {
                    if (watchedFrom < 0) {
                        watchedFrom = tick;
                        Constants.LOG.info("[Beitrittstest] Zuschauen mit GPS Rescue: {} schaut der Drohne von {} zu", watcher.getPlainTextName(), PILOT);
                    }
                    if (tick - watchedFrom >= WATCH_BEFORE_RESCUE) {
                        rescue(server, flown);
                        stage = 2;
                        mark = tick;
                    }
                }
            }
            case 2 -> {
                QuadcopterEntity flown = drone(server);
                if (flown == null) {
                    Constants.LOG.error("[Beitrittstest] Zuschauen mit GPS Rescue: Drohne im Rescue verschwunden");
                    stage = 9;
                    return;
                }
                rescueTicks++;
                if (watcher != null && SpectateServer.watching(watcher) == flown) {
                    rescueWatched++;
                }
                boolean running = flown.returnPlan() != null || AutonomousReturns.flying(flown);
                double distance = Math.hypot(flown.getX() - (home.getX() + 0.5), flown.getZ() - (home.getZ() + 0.5));
                if (rescueTicks % 100 == 0) {
                    Constants.LOG.info("[Beitrittstest] Zuschauen mit GPS Rescue: Rescue läuft {} Ticks, Phase {}, {} m vom Homepoint, {} m hoch",
                            rescueTicks, AutonomousReturns.phase(flown), String.format(Locale.ROOT, "%.1f", distance),
                            String.format(Locale.ROOT, "%.1f", flown.getY() - home.getY()));
                }
                if (!running || tick - mark > RESCUE_TIMEOUT) {
                    boolean landed = !running && distance <= 3.0 && Math.abs(flown.getY() - home.getY()) < 1.5;
                    Constants.LOG.info("[Beitrittstest] Zuschauen mit GPS Rescue: Rescue {} nach {} Ticks, {} m vom Homepoint, Höhe {} über dem Ziel, "
                                    + "Zuschauer sah {} von {} Ticks zu – {}", running ? "nicht fertig" : "beendet", rescueTicks,
                            String.format(Locale.ROOT, "%.1f", distance), String.format(Locale.ROOT, "%.1f", flown.getY() - home.getY()), rescueWatched,
                            rescueTicks, landed ? "gelandet am Homepoint" : "NICHT am Homepoint gelandet");
                    server.overworld().setBlock(home.below().west(), (landed ? Blocks.EMERALD_BLOCK : Blocks.REDSTONE_BLOCK).defaultBlockState(),
                            Block.UPDATE_ALL);
                    stage = 3;
                    mark = tick;
                }
            }
            case 3 -> {
                if (watcher == null) {
                    QuadcopterEntity flown = drone(server);
                    if (flown != null) {
                        SpectateServer.simulateTransmitter(flown, null);
                        flown.discard();
                    }
                    Constants.LOG.info("[Beitrittstest] Zuschauen mit GPS Rescue: Zuschauer gegangen, Drohne entfernt");
                    stage = 9;
                }
            }
            default -> {
            }
        }
    }

    private static void build(MinecraftServer server, ServerPlayer watcher) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        int x = spawn.getX() + 12;
        int z0 = spawn.getZ() + 6;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z0 - 6);
        home = new BlockPos(x, top, z0 - 6);
        level.setBlock(home.below(), Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(home.below().west(), Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        QuadcopterEntity created = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (created == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        created.snapTo(x + 0.5, top, z0 - 6 + 0.5, 0.0F, 0.0F);
        created.setAttitude(new Quaternionf());
        created.setBuild(DroneBuild.RACER);
        created.setBatteryCharge(1.0F);
        level.addFreshEntity(created);
        drone = created.getUUID();
        SpectateServer.simulateTransmitter(created, PILOT);
        SpectateServer.refreshSoon();
        loopX = x;
        loopY = top + 3.5;
        loopZ = z0;
        server.getPlayerList().op(watcher.nameAndId());
        watcher.setGameMode(GameType.CREATIVE);
        watcher.setItemSlot(EquipmentSlot.HEAD, ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new)
                .orElseThrow(() -> new IllegalStateException("Analog-Brille fehlt")));
        int watcherTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x - 8, z0 + 10);
        watcher.teleportTo(level, x - 8.5, watcherTop, z0 + 10.5, Set.of(), -90.0F, 10.0F, true);
        Constants.LOG.info("[Beitrittstest] Zuschauen mit GPS Rescue: Sender simuliert als {}, Drohne {} fliegt eine Runde, Homepoint {} (Goldblock), "
                + "{} mit Analog-Brille", PILOT, created.getUUID(), home.toShortString(), watcher.getPlainTextName());
    }

    private static void fly(QuadcopterEntity flown, int tick) {
        double t = tick / 20.0 * 12.0 / 72.0 * Math.PI * 2.0;
        double x = loopX + 4.0 + 6.0 * Math.sin(t);
        double z = loopZ + 10.0 - 14.0 * Math.cos(t);
        float yaw = (float) Math.toDegrees(Math.atan2(-6.0 * Math.cos(t), 14.0 * Math.sin(t)));
        flown.snapTo(x, loopY, z, yaw, 0.0F);
        flown.setAttitude(new Quaternionf().rotationY((float) Math.toRadians(-yaw)));
    }

    private static void rescue(MinecraftServer server, QuadcopterEntity flown) {
        Vec3 target = Vec3.atBottomCenterOf(home);
        flown.setDeltaMovement(Vec3.ZERO);
        flown.setReturnPlan(ReturnPlan.of(SENDER, target.x, target.y, target.z, false, new RescueSettings()));
        server.overworld().setBlock(home.below().west(), Blocks.LAPIS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        Constants.LOG.info("[Beitrittstest] Zuschauen mit GPS Rescue: Server löst GPS Rescue aus bei {} (Homepoint {}, {} m entfernt)",
                String.format(Locale.ROOT, "%.1f %.1f %.1f", flown.getX(), flown.getY(), flown.getZ()), home.toShortString(),
                String.format(Locale.ROOT, "%.1f", Math.hypot(flown.getX() - target.x, flown.getZ() - target.z)));
    }
}
