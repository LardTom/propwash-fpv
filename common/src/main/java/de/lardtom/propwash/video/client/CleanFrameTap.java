package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class CleanFrameTap {
    private static final AtomicReference<@Nullable Consumer<RenderTarget>> NEXT = new AtomicReference<>();

    private CleanFrameTap() {
    }

    public static void request(Consumer<RenderTarget> reader) {
        NEXT.set(reader);
    }

    public static boolean pending() {
        return NEXT.get() != null;
    }

    public static void offer(RenderTarget main) {
        Consumer<RenderTarget> reader = NEXT.getAndSet(null);
        if (reader != null) {
            reader.accept(main);
        }
    }
}
