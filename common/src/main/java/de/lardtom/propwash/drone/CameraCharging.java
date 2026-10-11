package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.ActionCamSpec;
import de.lardtom.propwash.drone.net.CameraChargeC2S;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.video.record.CamPower;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class CameraCharging {
    public static final int INTERVAL_TICKS = 10;

    private CameraCharging() {
    }

    public static Optional<ActionCamSpec> ownBatteryCamera(QuadcopterEntity drone) {
        return drone.build().accessories().camera().flatMap(AccessoryType::camera).filter(ActionCamSpec::ownBattery);
    }

    static void onCameraCharge(CameraChargeC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        if (!(player.level().getEntity(payload.entityId()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return;
        }
        if (!drone.isFlownBy(player.getUUID()) || ownBatteryCamera(drone).isEmpty() || !Float.isFinite(payload.charge())) {
            return;
        }
        float value = Math.max(0.0F, Math.min(CamPower.FULL, payload.charge()));
        if (value < drone.cameraCharge()) {
            drone.setCameraCharge(value);
        }
    }

    static void tick(QuadcopterEntity drone) {
        if (!drone.hasBattery() || drone.isArmed()) {
            return;
        }
        Optional<ActionCamSpec> camera = ownBatteryCamera(drone);
        if (camera.isEmpty() || drone.cameraCharge() >= CamPower.FULL) {
            return;
        }
        float[] next = CamPower.usbCharge(drone.cameraCharge(), drone.batteryCharge(), camera.get(), drone.build().battery(),
                INTERVAL_TICKS / 20.0);
        if (next[0] != drone.cameraCharge()) {
            drone.setCameraCharge(next[0]);
            drone.setBatteryCharge(next[1]);
        }
    }
}
