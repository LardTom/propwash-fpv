package de.lardtom.propwash.video.record;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

public final class RecordingResources {
    public static final long MB = 1024L * 1024L;
    public static final long MIN_BUFFER = 256L * MB;
    public static final long MAX_AUTO_BUFFER = 2048L * MB;
    public static final int MAX_THREADS = 12;

    private RecordingResources() {
    }

    public static int encoderThreads(int cores) {
        return Math.max(1, Math.min(MAX_THREADS, (int) Math.round(cores * 0.6) - 2));
    }

    public static long autoBuffer(long physicalBytes, long heapBytes) {
        long basis = physicalBytes > 0L ? physicalBytes / 16L : heapBytes / 4L;
        return Math.max(MIN_BUFFER, Math.min(MAX_AUTO_BUFFER, basis));
    }

    public static long physicalMemory() {
        try {
            OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            for (String name : new String[]{"getTotalMemorySize", "getTotalPhysicalMemorySize"}) {
                try {
                    Method method = bean.getClass().getMethod(name);
                    method.setAccessible(true);
                    Object value = method.invoke(bean);
                    if (value instanceof Long bytes && bytes > 0L) {
                        return bytes;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        return 0L;
    }
}
