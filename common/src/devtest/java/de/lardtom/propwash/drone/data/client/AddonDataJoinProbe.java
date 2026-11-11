package de.lardtom.propwash.drone.data.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.dev.AddonDataJoinCheck;
import de.lardtom.propwash.dev.example.ExampleMission;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.data.AddonData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public final class AddonDataJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 60;
    private static final String START = "addondata_start";
    private static final String FIRST = "addondata_first";
    private static final double RANGE = 16.0;

    private AddonDataJoinProbe() {
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        if (ExampleMission.mission() == null) {
            return "addondata keys=missing\n";
        }
        QuadcopterEntity drone = find(mc);
        if (drone == null) {
            return tick - start > TIMEOUT_TICKS ? "addondata tracking=missing\n" : null;
        }
        ExampleMission.Mission mission = PropwashApi.droneData().get(drone, ExampleMission.mission());
        if (!mission.name().isEmpty()) {
            state.putIfAbsent(FIRST, mission.name());
        }
        if (!AddonDataJoinCheck.SECOND.equals(mission.name())) {
            return tick - start > TIMEOUT_TICKS ? "addondata tracking=" + (mission.name().isEmpty() ? "missing" : mission.name()) + "\n" : null;
        }
        AddonData synced = drone.addonData();
        ExampleMission.ServiceLog log = PropwashApi.droneData().get(drone, ExampleMission.log());
        boolean hidden = !synced.contains(ExampleMission.LOG_ID) && ExampleMission.ServiceLog.EMPTY.equals(log);
        return "addondata tracking=" + mission.name() + " waypoints=" + mission.waypoints().size() + " flights=" + mission.flights() + "\n"
                + "addondata first=" + state.getOrDefault(FIRST, "-") + "\n"
                + "addondata none=" + (hidden ? "hidden" : "leaked changes=" + log.pilotChanges()) + "\n"
                + "addondata synced=" + synced.ids() + " bytes=" + synced.bytes() + "\n";
    }

    private static @Nullable QuadcopterEntity find(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof QuadcopterEntity drone && drone.distanceTo(mc.player) < RANGE
                    && !PropwashApi.droneData().get(drone, ExampleMission.mission()).name().isEmpty()) {
                return drone;
            }
        }
        return null;
    }
}
