package de.lardtom.propwash.blackbox.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.blackbox.BlackboxConfig;
import de.lardtom.propwash.blackbox.BlackboxModes;
import de.lardtom.propwash.blackbox.BlackboxRecorder;
import de.lardtom.propwash.drone.CraftNames;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

public final class BlackboxSession {
    public static final BlackboxSession INSTANCE = new BlackboxSession();
    public static final Object TOAST_TOKEN = new Object();
    static final long FLUSH_TIMEOUT_NANOS = 500_000_000L;
    static final long TOAST_MILLIS = 2500L;
    static final int NAME_REFRESH_FRAMES = 40;

    private @Nullable BlackboxRecorder recorder;
    private @Nullable DroneSim attached;
    private boolean keyLatch = true;
    private String craftName = "";
    private int nameFrames;

    private BlackboxSession() {
    }

    public static Path directory(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve(Constants.MOD_ID).resolve("blackbox");
    }

    public BlackboxRecorder recorder(Minecraft minecraft) {
        BlackboxRecorder r = this.recorder;
        if (r == null) {
            r = new BlackboxRecorder(directory(minecraft), true);
            this.recorder = r;
        }
        return r;
    }

    public @Nullable BlackboxRecorder recorderIfCreated() {
        return this.recorder;
    }

    void onFrame(Minecraft minecraft, DeltaTracker deltaTracker) {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        if (sim != this.attached) {
            this.detach();
            if (sim != null) {
                BlackboxRecorder r = this.recorder(minecraft);
                sim.setFcListener(r);
                this.attached = sim;
                this.keyLatch = true;
                this.nameFrames = 0;
            }
        }
        if (this.attached == null) {
            InputKeys.drain(BlackboxKeys.TOGGLE);
            return;
        }
        BlackboxRecorder r = this.recorder(minecraft);
        BlackboxConfig config = BlackboxClient.CONFIG.get();
        boolean bound = switchBound();
        while (BlackboxKeys.TOGGLE.consumeClick()) {
            if (!config.enabled) {
                toast(minecraft, Component.translatable("propwash.blackbox.toast.off"), Palette.TEXT_MUTED);
            } else if (bound) {
                toast(minecraft, Component.translatable("propwash.blackbox.toast.switch", Component.translatable(SwitchFunction.BLACKBOX.translationKey())),
                        Palette.TEXT_MUTED);
            } else {
                this.keyLatch = !this.keyLatch;
                toast(minecraft, Component.translatable(this.keyLatch ? "propwash.blackbox.toast.resumed" : "propwash.blackbox.toast.paused"),
                        this.keyLatch ? Palette.SUCCESS : Palette.WARNING);
            }
        }
        boolean gate = bound ? RadioState.INSTANCE.isActive(SwitchFunction.BLACKBOX) : this.keyLatch;
        if (--this.nameFrames <= 0) {
            this.nameFrames = NAME_REFRESH_FRAMES;
            this.craftName = craftName(session.drone());
        }
        boolean rescue = RescueController.INSTANCE.active();
        int extra = 0;
        if (rescue) {
            extra |= BlackboxModes.GPS_RESCUE;
        }
        if (RadioState.INSTANCE.isActive(SwitchFunction.BEEPER)) {
            extra |= BlackboxModes.BEEPER;
        }
        int rssi = Math.round(Math.max(0.0F, Math.min(1.0F, session.linkStrength())) * 1023.0F);
        r.configure(config.enabled, config.rate, bound, gate, rssi, extra, rescue, this.craftName, config.maxBytes());
    }

    void onDisconnect(Minecraft minecraft) {
        this.detach();
    }

    private void detach() {
        DroneSim old = this.attached;
        BlackboxRecorder r = this.recorder;
        if (old != null && r != null && old.fcListener() == r) {
            old.setFcListener(null);
        }
        this.attached = null;
        if (r != null) {
            r.sessionEnded();
            r.flush(FLUSH_TIMEOUT_NANOS);
        }
    }

    public boolean keyLatch() {
        return this.keyLatch;
    }

    public static boolean switchBound() {
        RadioState radio = RadioState.INSTANCE;
        String guid = radio.deviceGuid();
        if (!radio.connected() || guid == null || guid.isEmpty()) {
            return false;
        }
        DeviceProfile profile = InputConfig.get().profile(guid);
        if (profile == null) {
            return false;
        }
        SwitchAssignment binding = profile.binding(SwitchFunction.BLACKBOX);
        return binding != null && binding.bound();
    }

    static String craftName(@Nullable QuadcopterEntity drone) {
        if (drone == null || drone.getCustomName() == null) {
            return "";
        }
        return CraftNames.clean(drone.getCustomName().getString());
    }

    private static void toast(Minecraft minecraft, Component message, int accent) {
        ThemedToast.show(minecraft.gui.toastManager(), TOAST_TOKEN, message, null, accent, TOAST_MILLIS);
    }
}
