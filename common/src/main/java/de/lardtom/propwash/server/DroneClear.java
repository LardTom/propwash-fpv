package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.rescue.AutonomousReturns;
import de.lardtom.propwash.rescue.ReturnPlan;
import de.lardtom.propwash.video.SpectateServer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class DroneClear {
    public static final String DONE = "propwash.server.command.clear.done";
    public static final String DONE_ONE = "propwash.server.command.clear.done.one";
    public static final String NONE = "propwash.server.command.clear.none";
    public static final String REMOVED = "propwash.drone.removed";

    private DroneClear() {
    }

    public record Result(Tally tally, int pilots, int returns, int spectators, int notified) {
    }

    public static final class Tally {
        private final Map<String, Integer> counts = new TreeMap<>();
        private int total;

        public Tally add(String dimension) {
            this.counts.merge(dimension, 1, Integer::sum);
            this.total++;
            return this;
        }

        public int total() {
            return this.total;
        }

        public int count(String dimension) {
            return this.counts.getOrDefault(dimension, 0);
        }

        public String text() {
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, Integer> entry : this.counts.entrySet()) {
                if (!out.isEmpty()) {
                    out.append(", ");
                }
                out.append(entry.getKey()).append(": ").append(entry.getValue());
            }
            return out.toString();
        }

        public String key() {
            return this.total == 1 ? DONE_ONE : DONE;
        }

        public Component message() {
            return this.total == 1 ? Component.translatable(this.key(), this.text()) : Component.translatable(this.key(), this.total, this.text());
        }
    }

    public static List<QuadcopterEntity> loaded(MinecraftServer server) {
        List<QuadcopterEntity> drones = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            drones.addAll(level.getEntities(DroneModule.DRONE.get(), drone -> !drone.isRemoved()));
        }
        return drones;
    }

    public static List<QuadcopterEntity> among(Collection<? extends Entity> targets) {
        Set<QuadcopterEntity> drones = Collections.newSetFromMap(new IdentityHashMap<>());
        List<QuadcopterEntity> ordered = new ArrayList<>();
        for (Entity entity : targets) {
            if (entity instanceof QuadcopterEntity drone && !drone.isRemoved() && drones.add(drone)) {
                ordered.add(drone);
            }
        }
        return ordered;
    }

    public static Tally tally(Collection<QuadcopterEntity> drones) {
        Tally tally = new Tally();
        for (QuadcopterEntity drone : drones) {
            if (!drone.isRemoved()) {
                tally.add(dimension(drone));
            }
        }
        return tally;
    }

    public static int run(CommandSourceStack source, List<QuadcopterEntity> drones) {
        Result result = clear(source.getServer(), drones);
        Tally tally = result.tally();
        if (tally.total() == 0) {
            source.sendFailure(Component.translatable(NONE));
            Constants.LOG.info("/propwash clear von {}: keine Drohnen in geladenen Bereichen", source.getTextName());
            return 0;
        }
        Component message = tally.message();
        source.sendSuccess(() -> message, true);
        Constants.LOG.info("/propwash clear von {}: entfernt {} ({}), Pilot-Sitzungen beendet {}, Heimflüge beendet {}, Zuschauer abgemeldet {}, Spieler benachrichtigt {}",
                source.getTextName(), tally.total(), tally.text(), result.pilots(), result.returns(), result.spectators(), result.notified());
        return tally.total();
    }

    public static Result clear(MinecraftServer server, Collection<QuadcopterEntity> drones) {
        Tally tally = new Tally();
        Set<UUID> notify = new LinkedHashSet<>();
        int pilots = 0;
        int returns = 0;
        int spectators = 0;
        for (QuadcopterEntity drone : drones) {
            if (drone.isRemoved()) {
                continue;
            }
            String dimension = dimension(drone);
            UUID pilot = drone.pilot().orElse(null);
            ReturnPlan plan = drone.returnPlan();
            if (pilot != null) {
                pilots++;
                notify.add(pilot);
            }
            if (plan != null || AutonomousReturns.flying(drone)) {
                returns++;
                if (plan != null) {
                    notify.add(plan.owner());
                }
            }
            drone.setReturnPlan(null);
            spectators += unwatch(server, drone);
            drone.discard();
            if (drone.pilot().isPresent()) {
                drone.setPilot(null);
            }
            tally.add(dimension);
        }
        int notified = 0;
        for (UUID id : notify) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && !player.hasDisconnected()) {
                player.sendSystemMessage(Component.translatable(REMOVED).withStyle(ChatFormatting.GOLD));
                notified++;
            }
        }
        return new Result(tally, pilots, returns, spectators, notified);
    }

    private static int unwatch(MinecraftServer server, QuadcopterEntity drone) {
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (SpectateServer.watching(player) == drone || SpectatorLinks.watched(player) == drone) {
                SpectatorLinks.unwatch(player);
                count++;
            }
        }
        return count;
    }

    private static String dimension(QuadcopterEntity drone) {
        return drone.level().dimension().identifier().toShortString();
    }
}
