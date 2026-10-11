package de.lardtom.propwash.input.client;

import de.lardtom.propwash.input.DeviceChooser;
import de.lardtom.propwash.input.DeviceNames;
import de.lardtom.propwash.input.ProfileDefaults;

public record DeviceInfo(int id, String name, String guid, boolean gamepad, int vendor, int product) {
    public DeviceChooser.Candidate candidate() {
        return new DeviceChooser.Candidate(this.guid, this.name, this.gamepad, this.radio());
    }

    public String usbId() {
        return String.format("%04x:%04x", this.vendor & 0xFFFF, this.product & 0xFFFF);
    }

    public String displayName() {
        return DeviceNames.displayName(this.name, this.vendor, this.product);
    }

    public boolean edgeTxOrOpenTx() {
        return DeviceNames.edgeTxOrOpenTx(this.name, this.vendor, this.product);
    }

    public boolean radio() {
        return this.edgeTxOrOpenTx() || ProfileDefaults.looksLikeRadio(this.name);
    }

    public boolean gamepadByDefault() {
        return this.gamepad && !this.radio();
    }
}
