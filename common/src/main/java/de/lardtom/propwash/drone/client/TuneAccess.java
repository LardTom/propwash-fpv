package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.drone.net.TuneC2S;
import de.lardtom.propwash.freecam.FreecamPreset;
import de.lardtom.propwash.freecam.client.FreecamConfig;
import de.lardtom.propwash.freecam.client.FreecamSession;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;

public final class TuneAccess {
    public static final TuneAccess INSTANCE = new TuneAccess();

    private TuneAccess() {
    }

    public boolean available() {
        return FreecamSession.active() || PilotSession.INSTANCE.isActive() || this.heldHand() != null;
    }

    public FlightTune current() {
        Subject target = this.target();
        if (target == null) {
            return FlightTune.EMPTY;
        }
        Map<String, Float> values = TuneMapping.effective(target.tune().values(), AirframeSpec.of(target.build()));
        values.putIfAbsent(TuneMapping.CAMERA_UPTILT, CameraMount.defaultUptilt(target.build().frame()));
        LedSettings.DEFAULT.values().forEach(values::putIfAbsent);
        return new FlightTune(values);
    }

    public FlightTune defaults() {
        Subject target = this.target();
        if (target == null) {
            return FlightTune.EMPTY;
        }
        Map<String, Float> values = TuneMapping.defaults(AirframeSpec.of(target.build()));
        values.put(TuneMapping.CAMERA_UPTILT, CameraMount.defaultUptilt(target.build().frame()));
        values.putAll(LedSettings.DEFAULT.values());
        return new FlightTune(values);
    }

    public boolean apply(FlightTune tune) {
        Subject target = this.target();
        if (target == null || tune == null) {
            return false;
        }
        FlightTune result = new FlightTune(TuneMapping.applyEdit(target.tune().values(), tune.values(), AirframeSpec.of(target.build())));
        if (FreecamSession.active()) {
            return applyFreecam(result);
        }
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone != null) {
            PilotSession.INSTANCE.applyTuneLocally(result);
            ClientNet.sendToServer(new TuneC2S(drone.getId(), false, tune));
            return true;
        }
        InteractionHand hand = this.heldHand();
        if (hand == null) {
            return false;
        }
        return ClientNet.sendToServer(new TuneC2S(TuneC2S.HELD_ITEM, hand == InteractionHand.OFF_HAND, tune));
    }

    private static boolean applyFreecam(FlightTune result) {
        FreecamSession session = FreecamSession.INSTANCE;
        FreecamPreset preset = session.preset();
        FreecamConfig config = FreecamConfig.get();
        Float uptilt = result.values().get(TuneMapping.CAMERA_UPTILT);
        if (uptilt != null) {
            config.setUptilt(preset, uptilt);
        }
        config.setTune(preset, result.values());
        FreecamConfig.save();
        session.applyTune(config.tune(preset));
        return true;
    }

    private @Nullable Subject target() {
        if (FreecamSession.active()) {
            FreecamSession session = FreecamSession.INSTANCE;
            FreecamConfig config = FreecamConfig.get();
            Map<String, Float> values = new TreeMap<>(config.tune(session.preset()));
            values.put(TuneMapping.CAMERA_UPTILT, config.uptilt(session.preset()));
            return new Subject(session.build(), new FlightTune(values));
        }
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone != null) {
            return new Subject(drone.build(), drone.tune());
        }
        InteractionHand hand = this.heldHand();
        LocalPlayer player = Minecraft.getInstance().player;
        if (hand == null || player == null) {
            return null;
        }
        ItemStack stack = player.getItemInHand(hand);
        return new Subject(stack.getOrDefault(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE),
                stack.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY));
    }

    private @Nullable InteractionHand heldHand() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        if (ContentIds.is(player.getMainHandItem(), ContentIds.DRONE)) {
            return InteractionHand.MAIN_HAND;
        }
        if (ContentIds.is(player.getOffhandItem(), ContentIds.DRONE)) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private record Subject(DroneBuild build, FlightTune tune) {
    }
}
