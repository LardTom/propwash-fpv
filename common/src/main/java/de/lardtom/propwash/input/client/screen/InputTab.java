package de.lardtom.propwash.input.client.screen;

import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.client.DeviceInfo;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.RawInput;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import org.jspecify.annotations.Nullable;

abstract class InputTab extends ConfigTab {
    private @Nullable InputEditSession session;
    private @Nullable ConfigTabHost sessionHost;
    private String deviceSignature = "";
    private boolean pendingRebuild;

    protected final InputEditSession session() {
        InputEditSession current = this.session;
        if (current == null || this.sessionHost != this.host) {
            current = InputEditSession.of(this.host);
            this.session = current;
            this.sessionHost = this.host;
        }
        return current;
    }

    protected final InputConfig working() {
        return this.session().working();
    }

    protected final boolean dirty() {
        return this.session().dirty();
    }

    protected final @Nullable RawInput raw() {
        return TransmitterInput.INSTANCE.raw();
    }

    protected final @Nullable DeviceProfile profile(@Nullable RawInput raw) {
        if (raw == null) {
            return null;
        }
        DeviceInfo device = raw.device();
        return this.working().profileOrCreate(device.guid(), device.name(), device.gamepadByDefault(), raw.axisCount());
    }

    protected final void markChanged() {
        this.session().markDirty();
        if (this.host != null) {
            this.host.changed();
        }
    }

    protected final void rebuild() {
        if (this.host != null) {
            this.host.rebuild();
        }
    }

    protected final void rebuildLater() {
        this.pendingRebuild = true;
    }

    protected boolean requestsPreview() {
        return false;
    }

    @Override
    public void tick() {
        if (this.requestsPreview()) {
            TransmitterInput.INSTANCE.requestPreview(this.working().selectedGuid);
        }
        if (this.pendingRebuild) {
            this.pendingRebuild = false;
            this.rebuild();
            return;
        }
        if (this.session().reloadIfStale()) {
            this.rebuild();
            return;
        }
        String signature = signature();
        if (!signature.equals(this.deviceSignature)) {
            this.deviceSignature = signature;
            this.rebuild();
        }
    }

    protected final void rememberSignature() {
        this.deviceSignature = signature();
    }

    private static String signature() {
        StringBuilder sb = new StringBuilder();
        for (DeviceInfo device : TransmitterInput.INSTANCE.devices()) {
            sb.append(device.id()).append(':').append(device.guid()).append(';');
        }
        DeviceInfo open = TransmitterInput.INSTANCE.openDevice();
        sb.append('|').append(open == null ? "" : open.guid());
        return sb.toString();
    }

    @Override
    public void save() {
        InputEditSession current = this.session;
        if (current != null) {
            current.save();
        }
    }
}
