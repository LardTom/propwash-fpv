package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.drone.PilotRules;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class GogglesBlackout {
    private static long frames;

    private GogglesBlackout() {
    }

    public static @Nullable Mismatch current() {
        Minecraft minecraft = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        if (minecraft.player == null || !session.isLineOfSight()) {
            return null;
        }
        QuadcopterEntity drone = session.drone();
        if (drone == null) {
            return null;
        }
        VideoSystem goggles = ContentIds.wornGogglesSystem(minecraft.player).orElse(null);
        VideoSystem video = drone.build().video().system;
        return PilotRules.decide(goggles, video).blind() ? new Mismatch(goggles, video) : null;
    }

    public static boolean active() {
        return current() != null;
    }

    public static long frames() {
        return frames;
    }

    static void drawn() {
        frames++;
    }

    public record Mismatch(VideoSystem goggles, VideoSystem video) {
    }
}
