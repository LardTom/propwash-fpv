package de.lardtom.propwash.input.addon;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.ActionGate;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.net.AddonAxisC2S;
import de.lardtom.propwash.input.net.AddonFeaturesS2C;
import de.lardtom.propwash.input.net.AddonSwitchC2S;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class AddonSwitchServer {
    static final ActionGate SWITCH_GATE = new ActionGate();
    static final ActionGate AXIS_GATE = new ActionGate();

    private AddonSwitchServer() {
    }

    public static AddonFeaturesS2C features() {
        List<Identifier> switches = new ArrayList<>();
        for (SwitchSpec spec : AddonInputs.switches()) {
            if (spec.serverSync()) {
                switches.add(spec.id());
            }
        }
        List<Identifier> axes = new ArrayList<>();
        for (AxisSpec spec : AddonInputs.axes()) {
            if (spec.serverSync()) {
                axes.add(spec.id());
            }
        }
        List<Identifier> rules = new ArrayList<>();
        for (Api17Registries.Registered<?> rule : Api17Registries.collisionRules()) {
            rules.add(rule.id());
        }
        return new AddonFeaturesS2C(switches, axes, rules);
    }

    static void joined(ServerPlayer player) {
        PropwashNet.sendToPlayer(player, features());
    }

    static void left(ServerPlayer player) {
        SWITCH_GATE.forget(player.getUUID());
        AXIS_GATE.forget(player.getUUID());
    }

    static void clear() {
        SWITCH_GATE.clear();
        AXIS_GATE.clear();
        AddonInputState.clearAll();
    }

    static void onSwitch(AddonSwitchC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        QuadcopterEntity drone = piloted(player, payload.droneId(), payload.epoch());
        if (drone == null || !SWITCH_GATE.allow(player.getUUID(), player.level().getGameTime())) {
            return;
        }
        for (AddonSwitchC2S.Entry entry : payload.entries()) {
            SwitchSpec spec = AddonInputs.switchSpec(entry.id());
            if (spec == null || !spec.serverSync()) {
                continue;
            }
            if (AddonInputState.setSwitch(drone, entry.id(), entry.on())) {
                fire(player, drone, entry.id(), entry.on());
            }
        }
    }

    static void onAxis(AddonAxisC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        QuadcopterEntity drone = piloted(player, payload.droneId(), payload.epoch());
        if (drone == null || !AXIS_GATE.allow(player.getUUID(), player.level().getGameTime())) {
            return;
        }
        for (AddonAxisC2S.Entry entry : payload.entries()) {
            AxisSpec spec = AddonInputs.axisSpec(entry.id());
            if (spec != null && spec.serverSync()) {
                AddonInputState.setAxis(drone, entry.id(), AddonInputMath.dequantize(entry.value()));
            }
        }
    }

    private static @Nullable QuadcopterEntity piloted(ServerPlayer player, int droneId, int epoch) {
        ServerLevel level = player.level();
        if (!(level.getEntity(droneId) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return null;
        }
        if (!drone.isFlownBy(player.getUUID()) || epoch != drone.resetEpoch()) {
            return null;
        }
        return drone;
    }

    private static void fire(ServerPlayer player, QuadcopterEntity drone, Identifier id, boolean on) {
        if (!PropwashEvents.ADDON_SWITCH_CHANGED.hasListeners()) {
            return;
        }
        try {
            PropwashEvents.ADDON_SWITCH_CHANGED.fire(listener -> listener.onAddonSwitchChanged(player, drone, id, on));
        } catch (RuntimeException e) {
            Constants.LOG.warn("Event addon_switch_changed: Listener fehlgeschlagen", e);
        }
    }
}
