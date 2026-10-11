package de.lardtom.propwash.input;

import java.util.Objects;

public final class SwitchCommand {
    public String on = "";
    public String off = "";

    public SwitchCommand() {
    }

    public SwitchCommand(String on, String off) {
        this.on = on == null ? "" : on;
        this.off = off == null ? "" : off;
    }

    public boolean hasOn() {
        return this.on != null && !this.on.isEmpty();
    }

    public boolean hasOff() {
        return this.off != null && !this.off.isEmpty();
    }

    public boolean empty() {
        return !this.hasOn() && !this.hasOff();
    }

    public String text(boolean on) {
        return on ? this.on : this.off;
    }

    public SwitchCommand copy() {
        return new SwitchCommand(this.on, this.off);
    }

    public void sanitize() {
        this.on = SwitchCommands.cleanStored(this.on);
        this.off = SwitchCommands.cleanStored(this.off);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SwitchCommand c && Objects.equals(c.on, this.on) && Objects.equals(c.off, this.off);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.on, this.off);
    }

    @Override
    public String toString() {
        return "SwitchCommand[" + this.on + " | " + this.off + "]";
    }
}
