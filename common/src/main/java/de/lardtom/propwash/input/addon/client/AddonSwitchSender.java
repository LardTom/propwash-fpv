package de.lardtom.propwash.input.addon.client;

import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.addon.AddonInputMath;
import de.lardtom.propwash.input.addon.AddonInputs;
import de.lardtom.propwash.input.net.AddonAxisC2S;
import de.lardtom.propwash.input.net.AddonSwitchC2S;
import de.lardtom.propwash.network.client.ClientNet;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AddonSwitchSender {
    static final long AXIS_INTERVAL_NANOS = 1_000_000_000L / AddonAxisC2S.PER_SECOND;

    private final Set<Identifier> sentOn = new HashSet<>();
    private final Map<Identifier, Short> sentAxes = new HashMap<>();
    private int droneId = -1;
    private int epoch = Integer.MIN_VALUE;
    private int revision = -1;
    private long lastAxisNanos;
    private int switchPackets;
    private int axisPackets;

    void reset() {
        this.sentOn.clear();
        this.sentAxes.clear();
        this.droneId = -1;
        this.epoch = Integer.MIN_VALUE;
        this.revision = -1;
    }

    int switchPackets() {
        return this.switchPackets;
    }

    int axisPackets() {
        return this.axisPackets;
    }

    void tick(@Nullable QuadcopterEntity drone) {
        if (drone == null) {
            if (this.droneId != -1) {
                this.reset();
            }
            return;
        }
        boolean full = drone.getId() != this.droneId || drone.resetEpoch() != this.epoch || AddonServerFeatures.revision() != this.revision;
        if (full) {
            this.reset();
            this.droneId = drone.getId();
            this.epoch = drone.resetEpoch();
            this.revision = AddonServerFeatures.revision();
        }
        this.sendSwitches(drone, full);
        this.sendAxes(drone, full);
    }

    private void sendSwitches(QuadcopterEntity drone, boolean full) {
        List<AddonSwitchC2S.Entry> changes = new ArrayList<>();
        for (SwitchSpec spec : AddonInputs.switches()) {
            if (!spec.serverSync() || !AddonServerFeatures.knowsSwitch(spec.id())) {
                continue;
            }
            boolean on = AddonSwitchClient.localActive(spec.id());
            if (full || on != this.sentOn.contains(spec.id())) {
                changes.add(new AddonSwitchC2S.Entry(spec.id(), on));
            }
        }
        for (int start = 0; start < changes.size(); start += AddonSwitchC2S.MAX_ENTRIES) {
            List<AddonSwitchC2S.Entry> chunk = changes.subList(start, Math.min(changes.size(), start + AddonSwitchC2S.MAX_ENTRIES));
            if (!ClientNet.sendToServer(new AddonSwitchC2S(drone.getId(), drone.resetEpoch(), chunk))) {
                return;
            }
            this.switchPackets++;
            for (AddonSwitchC2S.Entry entry : chunk) {
                if (entry.on()) {
                    this.sentOn.add(entry.id());
                } else {
                    this.sentOn.remove(entry.id());
                }
            }
        }
    }

    private void sendAxes(QuadcopterEntity drone, boolean full) {
        long now = Util.getNanos();
        if (!full && now - this.lastAxisNanos < AXIS_INTERVAL_NANOS) {
            return;
        }
        List<AddonAxisC2S.Entry> changes = new ArrayList<>();
        for (AxisSpec spec : AddonInputs.axes()) {
            if (!spec.serverSync() || !AddonServerFeatures.knowsAxis(spec.id())) {
                continue;
            }
            short value = AddonInputMath.quantize(AddonSwitchClient.localAxis(spec.id(), spec.center()));
            Short sent = this.sentAxes.get(spec.id());
            if ((full || sent == null || sent != value) && changes.size() < AddonAxisC2S.MAX_ENTRIES) {
                changes.add(new AddonAxisC2S.Entry(spec.id(), value));
            }
        }
        if (changes.isEmpty()) {
            return;
        }
        if (ClientNet.sendToServer(new AddonAxisC2S(drone.getId(), drone.resetEpoch(), changes))) {
            this.axisPackets++;
            this.lastAxisNanos = now;
            for (AddonAxisC2S.Entry entry : changes) {
                this.sentAxes.put(entry.id(), entry.value());
            }
        }
    }
}
