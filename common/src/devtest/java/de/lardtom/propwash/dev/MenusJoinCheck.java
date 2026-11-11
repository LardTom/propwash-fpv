package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.AccessoryBehaviors;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.dev.example.ExampleDispenser;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class MenusJoinCheck {
    public static final String RESULT = "menus drones=";
    public static final int SETTLE_TICKS = 60;
    public static final double RADIUS = 2.0;
    public static final int MAX_DRONES = 8;
    private static final Set<String> OWN = Set.of(Constants.MOD_ID, ExampleDispenser.DISPENSER.getNamespace());
    private static final Map<UUID, Integer> JOINED = new HashMap<>();
    private static final Set<UUID> PLACED = new HashSet<>();

    public record Case(DroneBuild build, String label) {
    }

    private MenusJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int joined = JOINED.computeIfAbsent(player.getUUID(), id -> tick);
            if (tick - joined < SETTLE_TICKS || !PLACED.add(player.getUUID())) {
                continue;
            }
            try {
                place(player);
            } catch (RuntimeException e) {
                Constants.LOG.error("[Beitrittstest] Menüs: Aufbau für {} fehlgeschlagen", player.getPlainTextName(), e);
                player.sendSystemMessage(Component.literal(RESULT + "fail " + e.getMessage()));
            }
        }
    }

    public static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        Optional<AccessoryType> example = JoinTestEnv.exampleAddon() ? AccessoryType.byId(ExampleDispenser.DISPENSER) : Optional.empty();
        out.add(new Case(example.map(DroneBuild.FREESTYLE::withAccessory).orElse(DroneBuild.FREESTYLE),
                example.map(type -> "propwash+" + type.id()).orElse("propwash")));
        Map<String, AccessoryType> accessories = new LinkedHashMap<>();
        for (AccessoryType type : sorted(AccessoryType.all(), AccessoryType::id)) {
            String namespace = type.id().getNamespace();
            if (!OWN.contains(namespace) && type.unladen() == type && AccessoryBehaviors.INSTANCE.slots(type) > 0) {
                accessories.putIfAbsent(namespace, type);
            }
        }
        accessories.values().forEach(type -> out.add(new Case(DroneBuild.FREESTYLE.withAccessory(type), "accessory " + type.id())));
        Map<String, FrameType> frames = new LinkedHashMap<>();
        for (FrameType frame : sorted(FrameType.all(), FrameType::id)) {
            String namespace = frame.id().getNamespace();
            if (!OWN.contains(namespace)) {
                frames.putIfAbsent(namespace, frame);
            }
        }
        DroneBuild base = DroneBuild.FREESTYLE;
        frames.values().forEach(frame -> out.add(new Case(new DroneBuild(frame, base.stack(), base.motor(), base.prop(), base.video(), base.battery()),
                "frame " + frame.id())));
        return out.size() > MAX_DRONES ? List.copyOf(out.subList(0, MAX_DRONES)) : out;
    }

    private static <T> List<T> sorted(List<T> values, Function<T, ?> key) {
        List<T> copy = new ArrayList<>(values);
        copy.sort(Comparator.comparing(value -> key.apply(value).toString()));
        return copy;
    }

    private static void place(ServerPlayer player) {
        ServerLevel level = player.level();
        List<Case> cases = cases();
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < cases.size(); i++) {
            Case entry = cases.get(i);
            double angle = Math.PI * 2.0 * i / cases.size();
            double x = player.getX() + RADIUS * Math.cos(angle);
            double z = player.getZ() + RADIUS * Math.sin(angle);
            QuadcopterEntity drone = spawn(level, entry.build(), x, player.getY(), z);
            ids.append(' ').append(drone.getId());
            Constants.LOG.info("[Beitrittstest] Menüs: Drohne {} ({}) für {} bei {}", drone.getId(), entry.label(), player.getPlainTextName(),
                    String.format(Locale.ROOT, "%.1f %.1f %.1f", x, player.getY(), z));
        }
        player.sendSystemMessage(Component.literal(RESULT + cases.size() + ids));
    }

    private static QuadcopterEntity spawn(ServerLevel level, DroneBuild build, double x, double y, double z) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.snapTo(x, y, z, 0.0F, 0.0F);
        drone.setBuild(build);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        return drone;
    }
}
