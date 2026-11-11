package de.lardtom.propwash.drone.operator.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.operator.OperatorChannel;
import de.lardtom.propwash.drone.operator.net.OperatorInputC2S;
import de.lardtom.propwash.drone.operator.net.OperatorRoleS2C;
import de.lardtom.propwash.drone.operator.net.OperatorStateS2C;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class OperatorClient {
    public static final Object TOAST_TOKEN = new Object();
    private static final long TOAST_MILLIS = 4000L;
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final KeyMapping[] ACTION_KEYS = new KeyMapping[OperatorChannel.ACTIONS];

    private static int operatorDrone = -1;
    private static @Nullable UUID operatorUuid;
    private static String droneName = "";
    private static int pilotDrone = -1;
    private static String operatorName = "";
    private static boolean viewSeen;
    private static int sent;
    private static int roles;
    private static float @Nullable [] testAxes;
    private static int testBits;
    private static boolean keysResolved;

    private OperatorClient() {
    }

    public record State(short[] axes, int actions, long nanos) {
        public double axis(int index) {
            return index >= 0 && index < this.axes.length ? OperatorChannel.axis(this.axes[index]) : 0.0;
        }
    }

    public static void init() {
        ClientNet.handle(OperatorStateS2C.TYPE, (payload, context) -> onState(payload));
        ClientNet.handle(OperatorRoleS2C.TYPE, (payload, context) -> onRole(context.minecraft(), payload));
        ClientBus.TICK_END.register(OperatorClient::onTick);
        ClientBus.DISCONNECT.register(minecraft -> reset());
    }

    public static double axis(@Nullable Entity drone, int index) {
        State state = drone == null ? null : STATES.get(drone.getId());
        return state == null ? 0.0 : state.axis(index);
    }

    public static boolean action(@Nullable Entity drone, int number) {
        State state = drone == null ? null : STATES.get(drone.getId());
        return state != null && OperatorChannel.action(state.actions(), number);
    }

    public static void feed(Entity drone, DroneSim sim) {
        for (int i = 0; i < OperatorChannel.AXES; i++) {
            sim.setOperatorAxis(i, axis(drone, i));
        }
    }

    public static @Nullable State state(int entityId) {
        return STATES.get(entityId);
    }

    public static boolean operating() {
        return operatorDrone >= 0 && OperatorView.watching(operatorDrone);
    }

    public static boolean bound() {
        return operatorDrone >= 0;
    }

    public static int operatorDrone() {
        return operatorDrone;
    }

    public static int pilotDrone() {
        return pilotDrone;
    }

    public static String operatorName() {
        return operatorName;
    }

    public static int sent() {
        return sent;
    }

    public static int roles() {
        return roles;
    }

    public static void testInput(float @Nullable [] axes, int bits) {
        testAxes = axes == null ? null : axes.clone();
        testBits = bits;
    }

    static void onState(OperatorStateS2C payload) {
        STATES.put(payload.entityId(), new State(payload.axes(), payload.actions(), System.nanoTime()));
    }

    static void onRole(Minecraft minecraft, OperatorRoleS2C payload) {
        roles++;
        int id = payload.entityId();
        switch (payload.role()) {
            case OPERATOR -> {
                operatorDrone = id;
                operatorUuid = payload.drone();
                droneName = payload.name();
                viewSeen = false;
                OperatorView.begin(minecraft, payload.drone(), id);
                toast(minecraft, Component.translatable("propwash.drone.operator.bound", payload.name()), Component.translatable("propwash.drone.operator.leave_hint"),
                        Palette.SUCCESS);
            }
            case PILOT -> {
                pilotDrone = id;
                operatorName = payload.name();
                toast(minecraft, Component.translatable("propwash.drone.operator.pilot", payload.name()), null, Palette.INFO);
            }
            case NONE -> {
                if (id == operatorDrone) {
                    operatorDrone = -1;
                    operatorUuid = null;
                    OperatorView.end(minecraft, id, payload.drone());
                    toast(minecraft, Component.translatable("propwash.drone.operator.ended", payload.name()), null, Palette.INFO);
                }
                if (id == pilotDrone) {
                    pilotDrone = -1;
                    operatorName = "";
                    toast(minecraft, Component.translatable("propwash.drone.operator.pilot_ended", payload.name()), null, Palette.INFO);
                }
                STATES.remove(id);
            }
        }
    }

    static void onTick(Minecraft minecraft) {
        if (operatorDrone < 0) {
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }
        boolean watching = OperatorView.watching(operatorDrone);
        if (watching) {
            viewSeen = true;
        } else if (viewSeen || !OperatorView.connecting(operatorDrone)) {
            int id = operatorDrone;
            UUID drone = operatorUuid;
            ClientNet.sendToServer(OperatorInputC2S.leave(id));
            operatorDrone = -1;
            operatorUuid = null;
            if (OperatorView.end(minecraft, id, drone)) {
                toast(minecraft, Component.translatable("propwash.drone.operator.ended", droneName), null, Palette.INFO);
            }
            return;
        }
        if (!watching) {
            return;
        }
        OperatorTestInput.Input property = testAxes == null ? OperatorTestInput.current() : null;
        short[] axes = property != null ? property.axes() : axes(minecraft);
        int bits = property != null ? property.actions() : bits(minecraft);
        if (ClientNet.sendToServer(new OperatorInputC2S(operatorDrone, axes, bits))) {
            sent++;
        }
    }

    private static short[] axes(Minecraft minecraft) {
        short[] out = new short[OperatorChannel.AXES];
        float[] test = testAxes;
        if (test != null) {
            for (int i = 0; i < out.length && i < test.length; i++) {
                out[i] = OperatorChannel.quantize(test[i]);
            }
            return out;
        }
        if (minecraft.gui.screen() != null) {
            return out;
        }
        StickPositions sticks = RadioState.INSTANCE.sticks();
        if (sticks == null) {
            return out;
        }
        StickPositions clamped = sticks.clamped();
        out[0] = OperatorChannel.quantize(clamped.roll());
        out[1] = OperatorChannel.quantize(clamped.pitch());
        out[2] = OperatorChannel.quantize(clamped.yaw());
        out[3] = OperatorChannel.quantize(clamped.throttle());
        return out;
    }

    private static int bits(Minecraft minecraft) {
        if (testAxes != null) {
            return OperatorChannel.sanitize(testBits);
        }
        if (minecraft.gui.screen() != null) {
            return 0;
        }
        resolveKeys();
        int bits = 0;
        RadioState radio = RadioState.INSTANCE;
        for (int i = 0; i < OperatorChannel.ACTIONS; i++) {
            KeyMapping key = ACTION_KEYS[i];
            if (key != null && key.isDown() || radio.isActive(SwitchFunction.accessory(i + 1))) {
                bits |= 1 << i;
            }
        }
        return bits;
    }

    private static void resolveKeys() {
        if (keysResolved) {
            return;
        }
        keysResolved = true;
        for (int i = 0; i < OperatorChannel.ACTIONS && i < KeyDefaults.ACCESSORY_ACTIONS.size(); i++) {
            String name = KeyDefaults.mappingName(KeyDefaults.ACCESSORY_ACTIONS.get(i).name());
            for (KeyMapping mapping : ClientRegistrar.keyMappings()) {
                if (mapping.getName().equals(name)) {
                    ACTION_KEYS[i] = mapping;
                    break;
                }
            }
        }
    }

    private static void toast(Minecraft minecraft, Component title, @Nullable Component message, int accent) {
        if (minecraft.player != null) {
            ThemedToast.show(minecraft.gui.toastManager(), TOAST_TOKEN, title, message, accent, TOAST_MILLIS);
        }
    }

    static void reset() {
        STATES.clear();
        operatorDrone = -1;
        operatorUuid = null;
        droneName = "";
        pilotDrone = -1;
        operatorName = "";
        viewSeen = false;
        testAxes = null;
        testBits = 0;
    }
}
