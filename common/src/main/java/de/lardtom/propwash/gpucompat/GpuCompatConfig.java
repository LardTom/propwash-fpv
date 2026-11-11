package de.lardtom.propwash.gpucompat;

public final class GpuCompatConfig {
    public GpuCompatMode mode = GpuCompatMode.AUTO;

    public void validate() {
        if (this.mode == null) {
            this.mode = GpuCompatMode.AUTO;
        }
    }

    public GpuCompatConfig copy() {
        GpuCompatConfig copy = new GpuCompatConfig();
        copy.mode = this.mode;
        return copy;
    }
}
