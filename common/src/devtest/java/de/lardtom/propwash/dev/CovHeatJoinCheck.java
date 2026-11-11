package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.BuildAnalysis;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CovHeatJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final double SHARE = 0.8;
    public static final DroneBuild BUILD = DroneBuild.CINEWHOOP;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static boolean restored;

    private static final class Track {
        final int joined;
        boolean started;
        double padY;
        double highest = Double.NEGATIVE_INFINITY;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private CovHeatJoinCheck() {
    }

    public static int gravityPercent() {
        return (int) Math.round(SHARE * BuildAnalysis.of(BUILD).thrustToWeight() * 100.0);
    }

    static void tick(MinecraftServer server, int tick) {
        boolean anyone = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            anyone = true;
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            if (!track.started) {
                if (tick - track.joined >= SETTLE_TICKS) {
                    track.started = true;
                    restored = false;
                    WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, gravityPercent());
                    WorldRules.set(server, WorldRuleSpecs.BATTERY_SCALE, 0);
                    BlockPos ground = player.blockPosition();
                    CovFlightJoinSupport.clearAbove(player.level(), ground, 4, 30, null);
                    track.padY = ground.getY();
                    player.teleportTo(player.level(), ground.getX() + 0.5, ground.getY(), ground.getZ() - 3.5, Set.of(), 0.0F, 0.0F, true);
                    Constants.LOG.info("[Beitrittstest] Motor-Hitze: Schwerkraft {} % ({} × S/G {}), Akkulaufzeit unendlich", gravityPercent(), SHARE,
                            String.format(Locale.ROOT, "%.2f", BuildAnalysis.of(BUILD).thrustToWeight()));
                    CovFlightJoinSupport.startFpv(player, BUILD, ground.getX() + 0.5, ground.getY() + 0.05, ground.getZ() + 0.5, 0.0F, "Motor-Hitze");
                }
                continue;
            }
            QuadcopterEntity drone = DronePiloting.pilotedBy(player.getUUID()).orElse(null);
            if (drone != null && tick % 10 == 0) {
                double height = drone.getY() - track.padY;
                track.highest = Math.max(track.highest, height);
                Constants.LOG.info("[Beitrittstest] Motor-Hitze: Drohne {} m über dem Boden (höchste {} m), scharf {}", String.format(Locale.ROOT, "%.2f", height),
                        String.format(Locale.ROOT, "%.2f", track.highest), drone.isArmed());
            }
        }
        if (!anyone && !TRACKS.isEmpty() && !restored) {
            restored = true;
            WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
            WorldRules.set(server, WorldRuleSpecs.BATTERY_SCALE, WorldRuleSpecs.BATTERY_SCALE.defaultValue());
            Constants.LOG.info("[Beitrittstest] Motor-Hitze: Schwerkraft und Akkulaufzeit zurück auf Standard");
        }
    }
}
