package de.lardtom.propwash.video.client.record;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import net.minecraft.client.KeyMapping;

public final class RecordingKeys {
    public static final String NAME = "action_cam_record";
    public static final int DEFAULT_KEY = InputConstants.KEY_F9;
    public static final KeyMapping RECORD = ClientRegistrar.keyMapping(NAME, DEFAULT_KEY);

    private RecordingKeys() {
    }

    public static void bootstrap() {
    }
}
