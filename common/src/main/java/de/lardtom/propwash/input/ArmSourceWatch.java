package de.lardtom.propwash.input;

public final class ArmSourceWatch {
    private RadioSource source = RadioSource.NONE;
    private String guid = "";
    private SwitchAssignment binding = SwitchAssignment.none();
    private int revision;

    public int revision() {
        return this.revision;
    }

    public boolean observe(RadioSource source, String guid, SwitchAssignment binding) {
        RadioSource s = source == null ? RadioSource.NONE : source;
        String g = guid == null ? "" : guid;
        SwitchAssignment b = binding == null || s == RadioSource.NONE ? SwitchAssignment.none() : binding;
        if (s == this.source && g.equals(this.guid) && b.equals(this.binding)) {
            return false;
        }
        this.source = s;
        this.guid = g;
        this.binding = b.copy();
        this.revision++;
        return true;
    }
}
