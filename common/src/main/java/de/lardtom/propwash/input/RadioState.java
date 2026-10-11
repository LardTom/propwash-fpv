package de.lardtom.propwash.input;

public final class RadioState {
    public static final RadioState INSTANCE = new RadioState();

    private volatile RadioFrame frame = RadioFrame.NONE;
    private long frameCount;
    private final ArmSourceWatch armWatch = new ArmSourceWatch();

    private RadioState() {
    }

    public boolean connected() {
        return this.frame.connected();
    }

    public String deviceName() {
        return this.frame.deviceName();
    }

    public StickPositions sticks() {
        return this.frame.sticks();
    }

    public float throttle3d() {
        return this.frame.throttle3d();
    }

    public boolean isActive(SwitchFunction function) {
        return function != null && SwitchEvaluator.isSet(this.frame.activeSwitches(), function);
    }

    public float[] rawAxes() {
        return this.frame.axes().clone();
    }

    public boolean[] rawButtons() {
        return this.frame.buttons().clone();
    }

    public RadioSource source() {
        return this.frame.source();
    }

    public String deviceGuid() {
        return this.frame.deviceGuid();
    }

    public int activeSwitches() {
        return this.frame.activeSwitches();
    }

    public int readableSwitches() {
        return this.frame.readableSwitches();
    }

    public long frameCount() {
        return this.frameCount;
    }

    public int armRevision() {
        return this.armWatch.revision();
    }

    public void publish(RadioFrame next) {
        this.publish(next, SwitchAssignment.none());
    }

    public void publish(RadioFrame next, SwitchAssignment arm) {
        RadioFrame published = next == null ? RadioFrame.NONE : next;
        this.armWatch.observe(published.source(), published.deviceGuid(), arm);
        this.frame = published;
        this.frameCount++;
    }
}
