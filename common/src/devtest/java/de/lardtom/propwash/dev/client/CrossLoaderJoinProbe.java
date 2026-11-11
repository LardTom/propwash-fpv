package de.lardtom.propwash.dev.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.CrossLoaderJoinCheck;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class CrossLoaderJoinProbe {
    public static final String REPORT = "crossloader drone ";
    private static final int STEP_TICKS = 20 * 180;
    private static final int REVERT_WAIT_TICKS = 20;
    private static final int SETTLE_TICKS = 40;
    private static final int SEEN_WAIT_TICKS = 20 * 20;
    private static final Map<UUID, String> SEEN = new TreeMap<>();
    private static @Nullable String before;

    private CrossLoaderJoinProbe() {
    }

    public static void before() {
        before = fingerprint();
    }

    public static void observe(int tick, Minecraft mc) {
        if (tick % 10 != 0 || mc.level == null) {
            return;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof QuadcopterEntity drone) {
                SEEN.put(drone.getUUID(), CrossLoaderJoinCheck.data(drone));
            }
        }
    }

    public static String report() {
        StringBuilder out = new StringBuilder();
        SEEN.forEach((id, data) -> {
            out.append(REPORT).append(id).append(' ').append(CrossLoaderJoinCheck.digest(data)).append('\n');
            Constants.LOG.info("[Beitrittstest] gemischt: Client sah Drohne {} mit Daten {} ({})", id, CrossLoaderJoinCheck.digest(data), data);
        });
        return out.toString();
    }

    public static SelfTest.Step singleplayerStep() {
        return new SelfTest.Step("Beitrittstest Einzelspieler nach dem Fabric-Server: Register zurückgesetzt", STEP_TICKS, CrossLoaderJoinProbe::singleplayer);
    }

    private static SelfTest.StepResult singleplayer(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (ctx.tick() < REVERT_WAIT_TICKS) {
            return SelfTest.StepResult.pending();
        }
        if (!state.containsKey("created")) {
            String problem = changed("nach dem Trennen");
            if (problem != null) {
                return SelfTest.StepResult.fail(problem);
            }
            state.put("created", true);
            SelfTestRunner.createWorld(mc);
            return SelfTest.StepResult.pending();
        }
        Screen screen = mc.gui.screen();
        if (screen instanceof ConfirmScreen confirm && ctx.tick() % 20 == 0) {
            SelfTestRunner.pressButton(confirm, CommonComponents.GUI_YES.getString());
        }
        if (state.containsKey("left")) {
            return mc.level == null ? SelfTest.StepResult.pass() : SelfTest.StepResult.pending();
        }
        if (mc.player == null || mc.level == null || screen != null) {
            return SelfTest.StepResult.pending();
        }
        int inWorld = (int) state.merge("inWorld", 1, (a, b) -> (int) a + (int) b);
        if (inWorld < SETTLE_TICKS) {
            return SelfTest.StepResult.pending();
        }
        if (!state.containsKey("drone")) {
            String problem = changed("in der Einzelspieler-Welt");
            if (problem != null) {
                return SelfTest.StepResult.fail(problem);
            }
            ctx.log("Register und Serializer-Nummern gleich wie vor dem Beitritt");
            state.put("drone", spawnDrone(ctx.server().getPlayerList().getPlayer(mc.player.getUUID())));
            state.put("droneAt", inWorld);
            return SelfTest.StepResult.pending();
        }
        UUID id = (UUID) state.get("drone");
        if (mc.level.getEntity(id) instanceof QuadcopterEntity drone) {
            ctx.log("Einzelspieler zeigt die Drohne: " + CrossLoaderJoinCheck.data(drone));
            state.put("left", true);
            mc.disconnectFromWorld(Component.literal("Propwash join test"));
            return SelfTest.StepResult.pending();
        }
        return inWorld - (int) state.get("droneAt") > SEEN_WAIT_TICKS
                ? SelfTest.StepResult.fail("Drohne erscheint im Einzelspieler nicht beim Client")
                : SelfTest.StepResult.pending();
    }

    private static UUID spawnDrone(@Nullable ServerPlayer player) {
        if (player == null) {
            throw new IllegalStateException("Spieler fehlt am integrierten Server");
        }
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        BlockPos at = player.blockPosition().offset(2, 1, 0);
        drone.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        return drone.getUUID();
    }

    private static @Nullable String changed(String when) {
        String now = fingerprint();
        return now.equals(before) ? null : "Register oder Serializer-Nummern " + when + " anders als vor dem Beitritt: " + before + " gegen " + now;
    }

    private static String fingerprint() {
        long entries = 0;
        long hash = 1125899906842597L;
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            for (Map.Entry<? extends ResourceKey<?>, ?> entry : registry.entrySet()) {
                entries++;
                hash = mix(hash, entry.getKey().identifier().toString().hashCode(), rawId(registry, entry.getValue()));
            }
        }
        int[] serializers = {
                EntityDataSerializers.getSerializedId(CoreComponents.DRONE_BUILD_SERIALIZER),
                EntityDataSerializers.getSerializedId(CoreComponents.FLIGHT_TUNE_SERIALIZER),
                EntityDataSerializers.getSerializedId(CoreComponents.PART_WEAR_SERIALIZER)
        };
        for (int id : serializers) {
            hash = mix(hash, id, id);
        }
        return entries + " Einträge, Prüfsumme " + Long.toHexString(hash) + ", Serializer " + Arrays.toString(serializers);
    }

    private static long mix(long hash, int a, int b) {
        return (hash * 31 + a) * 31 + b;
    }

    @SuppressWarnings("unchecked")
    private static <T> int rawId(Registry<T> registry, Object value) {
        return registry.getId((T) value);
    }
}
