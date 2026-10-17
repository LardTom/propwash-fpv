package de.lardtom.propwash.rescue;

import com.mojang.authlib.GameProfile;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.rescue.net.HomePointC2S;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class HomePointChecks {
    public static final UUID PILOT_ID = UUID.fromString("00000000-0000-4000-8000-0000000d7a71");
    public static final UUID OTHER_ID = UUID.fromString("00000000-0000-4000-8000-0000000d7a72");
    private static final String NETHER = "minecraft:the_nether";

    private HomePointChecks() {
    }

    public static @Nullable String check(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos base = level.getRespawnData().pos().above(40);
        if (!level.isPositionEntityTicking(base)) {
            return "Chunk am Spawn lädt keine Entities (Server-Rauchtest hält ihn fest)";
        }
        ServerPlayer pilot = player(server, PILOT_ID, "Home_Pilot");
        ServerPlayer other = player(server, OTHER_ID, "Home_Other");
        QuadcopterEntity drone = null;
        try {
            drone = spawn(level, base);
            return run(server, level, drone, pilot, other);
        } catch (RuntimeException e) {
            return "Ausnahme: " + e;
        } finally {
            if (drone != null) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                if (!drone.isRemoved()) {
                    drone.discard();
                }
            }
        }
    }

    public static String summary() {
        return "Pilot setzt beim Scharfschalten HomePoint (x/y/z, Dimension, Spielzeit, Herkunft), fremde, kaputte, Befehls- und"
                + " Grenzwerte abgelehnt, /data get zeigt ihn, Speichern und Laden behalten ihn, /data merge und modify setzen mit"
                + " Herkunft command und neuer Spielzeit, andere Dimension, Weltgrenze, Höhe und Text abgelehnt, Zeitstempel nur"
                + " lesbar, fremde Dimension blockiert andere Änderungen nicht, /data remove löscht, Pilotwechsel setzt die Quittung zurück";
    }

    private static @Nullable String run(MinecraftServer server, ServerLevel level, QuadcopterEntity drone, ServerPlayer pilot,
                                        ServerPlayer other) {
        String dimension = HomePointCodecs.dimension(level);
        String id = drone.getUUID().toString();
        if (drone.homePoint() != null) {
            return "neue Drohne hat schon einen Homepoint: " + drone.homePoint();
        }
        pilot.snapTo(drone.getX() + 1.0, drone.getY(), drone.getZ());
        DronePiloting.StartResult started = DronePiloting.tryStart(pilot, drone, PilotMode.LINE_OF_SIGHT);
        if (started != DronePiloting.StartResult.STARTED) {
            return "Pilot darf nicht fliegen: " + started;
        }
        if (drone.homeAck() != 0) {
            return "Quittung nach dem Start " + drone.homeAck() + " statt 0";
        }

        Vec3 arm = drone.position();
        HomePoints.onSet(new HomePointC2S(drone.getId(), 5, arm.x, arm.y, arm.z, HomePoint.Source.ARM), new ServerPayloadContext(pilot));
        HomePoint armed = new HomePoint(arm.x, arm.y, arm.z, dimension, level.getGameTime(), HomePoint.Source.ARM);
        if (!armed.equals(drone.homePoint()) || drone.homeAck() != 5) {
            return "Homepoint beim Scharfschalten " + drone.homePoint() + " (Quittung " + drone.homeAck() + "), erwartet " + armed;
        }

        String problem = rejected(drone, armed, new HomePointC2S(drone.getId(), 6, arm.x + 5.0, arm.y, arm.z, HomePoint.Source.MANUAL),
                other, "von einem Spieler, der nicht fliegt");
        problem = problem != null ? problem : rejected(drone, armed,
                new HomePointC2S(drone.getId(), 6, Double.NaN, arm.y, arm.z, HomePoint.Source.MANUAL), pilot, "mit NaN");
        problem = problem != null ? problem : rejected(drone, armed,
                new HomePointC2S(drone.getId(), 6, arm.x, arm.y, arm.z, HomePoint.Source.COMMAND), pilot, "mit Herkunft command");
        problem = problem != null ? problem : rejected(drone, armed,
                new HomePointC2S(drone.getId(), 6, arm.x, arm.y, arm.z, null), pilot, "ohne Herkunft");
        problem = problem != null ? problem : rejected(drone, armed,
                new HomePointC2S(drone.getId(), 6, 3.1e7, arm.y, arm.z, HomePoint.Source.MANUAL), pilot, "außerhalb der Weltgrenze");
        problem = problem != null ? problem : rejected(drone, armed,
                new HomePointC2S(drone.getId(), 6, arm.x, 5000.0, arm.z, HomePoint.Source.MANUAL), pilot, "über der Welt");
        if (problem != null) {
            return problem;
        }

        Output get = command(server, "data get entity " + id + " HomePoint");
        String shown = String.join("\n", get.lines);
        for (String part : List.of("source: \"arm\"", "dimension: \"" + dimension + "\"", "game_time: " + armed.gameTime() + "L",
                "x: " + number(arm.x) + "d")) {
            if (!get.success || !shown.contains(part)) {
                return "/data get zeigt " + part + " nicht: " + shown;
            }
        }

        CompoundTag saved = save(drone);
        CompoundTag stored = saved.getCompoundOrEmpty(HomePointCodecs.KEY);
        if (stored.size() != 6 || !"arm".equals(stored.getStringOr("source", "")) || stored.getLongOr("game_time", -1L) != armed.gameTime()) {
            return "Speicherstand HomePoint: " + stored;
        }
        Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved),
                level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
        if (!(loaded instanceof QuadcopterEntity reloaded) || !armed.equals(reloaded.homePoint())) {
            return "nach dem Laden: " + (loaded instanceof QuadcopterEntity q ? q.homePoint() : loaded);
        }

        double nx = arm.x + 3.0;
        double nz = arm.z - 2.0;
        Output merge = command(server, String.format(Locale.ROOT, "data merge entity %s {HomePoint:{x:%sd,y:%sd,z:%sd}}", id,
                number(nx), number(arm.y), number(nz)));
        HomePoint set = new HomePoint(nx, arm.y, nz, dimension, level.getGameTime(), HomePoint.Source.COMMAND);
        if (!merge.success || !set.equals(drone.homePoint())) {
            return "/data merge setzt " + drone.homePoint() + " statt " + set + ": " + merge.lines;
        }

        Output stamp = command(server, "data merge entity " + id + " {HomePoint:{source:\"pilot\",game_time:1L}}");
        if (!stamp.success || !set.equals(drone.homePoint())) {
            return "Zeitstempel und Herkunft per /data geändert: " + drone.homePoint() + " " + stamp.lines;
        }

        problem = refused(server, drone, set, "data merge entity " + id + " {HomePoint:{x:0d,y:64d,z:0d,dimension:\"" + NETHER + "\"}}",
                "dimension");
        problem = problem != null ? problem : refused(server, drone, set, "data merge entity " + id + " {HomePoint:{x:31000000d}}", "border");
        problem = problem != null ? problem : refused(server, drone, set, "data merge entity " + id + " {HomePoint:{y:5000d}}", "between");
        problem = problem != null ? problem : refused(server, drone, set, "data merge entity " + id + " {HomePoint:{y:\"hoch\"}}", "numbers");
        problem = problem != null ? problem : refused(server, drone, set, "data merge entity " + id + " {HomePoint:{source:\"teleport\"}}",
                "numbers");
        if (problem != null) {
            return problem;
        }

        HomePoint foreign = new HomePoint(1.0, 70.0, 1.0, NETHER, 3L, HomePoint.Source.MANUAL);
        drone.setHomePoint(foreign, drone.homeAck());
        Output charge = command(server, "data merge entity " + id + " {CameraCharge:0.5f}");
        if (!charge.success || !foreign.equals(drone.homePoint())) {
            return "Homepoint in fremder Dimension blockiert /data merge oder geht verloren: " + drone.homePoint() + " " + charge.lines;
        }

        Output modify = command(server, "data modify entity " + id + " HomePoint set value {x:1.5d,y:70d,z:2.5d}");
        HomePoint modified = new HomePoint(1.5, 70.0, 2.5, dimension, level.getGameTime(), HomePoint.Source.COMMAND);
        if (!modify.success || !modified.equals(drone.homePoint())) {
            return "/data modify setzt " + drone.homePoint() + " statt " + modified + ": " + modify.lines;
        }

        Output remove = command(server, "data remove entity " + id + " HomePoint");
        if (!remove.success || drone.homePoint() != null) {
            return "/data remove lässt " + drone.homePoint() + ": " + remove.lines;
        }
        if (save(drone).contains(HomePointCodecs.KEY)) {
            return "Speicherstand ohne Homepoint enthält HomePoint";
        }

        HomePoints.onSet(new HomePointC2S(drone.getId(), 9, arm.x, arm.y, arm.z, HomePoint.Source.MANUAL), new ServerPayloadContext(pilot));
        if (drone.homeAck() != 9 || drone.homePoint() == null || drone.homePoint().source() != HomePoint.Source.MANUAL) {
            return "zweiter Homepoint vom Piloten: " + drone.homePoint() + " Quittung " + drone.homeAck();
        }
        HomePoint kept = drone.homePoint();
        DronePiloting.release(drone);
        if (drone.homeAck() != 0 || !Objects.equals(kept, drone.homePoint())) {
            return "nach dem Aussteigen: Quittung " + drone.homeAck() + ", Homepoint " + drone.homePoint();
        }
        return null;
    }

    private static @Nullable String rejected(QuadcopterEntity drone, HomePoint expected, HomePointC2S payload, ServerPlayer sender,
                                             String what) {
        HomePoints.onSet(payload, new ServerPayloadContext(sender));
        return expected.equals(drone.homePoint()) && drone.homeAck() == 5 ? null
                : "Homepoint " + what + " angenommen: " + drone.homePoint() + " Quittung " + drone.homeAck();
    }

    private static @Nullable String refused(MinecraftServer server, QuadcopterEntity drone, HomePoint expected, String command,
                                            String message) {
        Output out = command(server, command);
        String text = String.join("\n", out.lines);
        if (out.success || !text.contains(message)) {
            return "/" + command + " nicht abgelehnt oder ohne Hinweis „" + message + "“: " + out.lines;
        }
        return expected.equals(drone.homePoint()) ? null : "/" + command + " ändert trotz Fehler: " + drone.homePoint();
    }

    private static String number(double value) {
        return Double.toString(value);
    }

    private static QuadcopterEntity spawn(ServerLevel level, BlockPos base) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        drone.snapTo(base.getX() + 0.5, base.getY() + 0.25, base.getZ() + 0.5, 0.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        return drone;
    }

    private static ServerPlayer player(MinecraftServer server, UUID id, String name) {
        GameProfile profile = new GameProfile(id, name);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    private static CompoundTag save(QuadcopterEntity drone) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, drone.registryAccess());
        if (!drone.save(output)) {
            throw new IllegalStateException("Drohne nicht speicherbar");
        }
        return output.buildResult();
    }

    private static Output command(MinecraftServer server, String command) {
        Output out = new Output();
        CommandSourceStack source = server.createCommandSourceStack().withSource(out).withCallback((success, result) -> out.success = success);
        server.getCommands().performPrefixedCommand(source, command);
        return out;
    }

    private static final class Output implements CommandSource {
        private final List<String> lines = new ArrayList<>();
        private boolean success;

        @Override
        public void sendSystemMessage(Component message) {
            this.lines.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
