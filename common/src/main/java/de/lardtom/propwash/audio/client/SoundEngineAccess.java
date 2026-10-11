package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.mixin.audio.client.SoundEngineAccessor;
import de.lardtom.propwash.mixin.audio.client.SoundManagerAccessor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.jspecify.annotations.Nullable;

public final class SoundEngineAccess {
    private SoundEngineAccess() {
    }

    public static @Nullable ChannelAccess.ChannelHandle handle(SoundManager soundManager, SoundInstance instance) {
        try {
            SoundEngine engine = ((SoundManagerAccessor) (Object) soundManager).propwash$soundEngine();
            return ((SoundEngineAccessor) (Object) engine).propwash$instanceToChannel().get(instance);
        } catch (RuntimeException | LinkageError e) {
            AudioErrors.report("handle", e);
            return null;
        }
    }

    public static boolean engineLoaded(SoundManager soundManager) {
        try {
            SoundEngine engine = ((SoundManagerAccessor) (Object) soundManager).propwash$soundEngine();
            return ((SoundEngineAccessor) (Object) engine).propwash$loaded();
        } catch (RuntimeException | LinkageError e) {
            AudioErrors.report("engine", e);
            return false;
        }
    }
}
