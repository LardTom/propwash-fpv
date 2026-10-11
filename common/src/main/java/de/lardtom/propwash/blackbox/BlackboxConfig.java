package de.lardtom.propwash.blackbox;

import java.util.List;

public final class BlackboxConfig {
    public static final List<Integer> STORAGE_MEGABYTES = List.of(128, 256, 512, 1024, 2048, 4096);
    public static final int DEFAULT_STORAGE_MEGABYTES = 512;

    public boolean enabled = false;
    public BlackboxRate rate = BlackboxRate.FULL;
    public int storageMegabytes = DEFAULT_STORAGE_MEGABYTES;

    public BlackboxConfig() {
    }

    public static void sanitize(BlackboxConfig config) {
        if (config.rate == null) {
            config.rate = BlackboxRate.FULL;
        }
        if (!STORAGE_MEGABYTES.contains(config.storageMegabytes)) {
            config.storageMegabytes = DEFAULT_STORAGE_MEGABYTES;
        }
    }

    public BlackboxConfig copy() {
        BlackboxConfig copy = new BlackboxConfig();
        copy.enabled = this.enabled;
        copy.rate = this.rate;
        copy.storageMegabytes = this.storageMegabytes;
        return copy;
    }

    public long maxBytes() {
        return (long) this.storageMegabytes << 20;
    }
}
