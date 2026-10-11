package de.lardtom.propwash.creative;

import de.lardtom.propwash.creative.net.CreativeEditC2S;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.core.component.DataComponentType;

public final class CreativeModule {
    public static final RegHolder<DataComponentType<CreativeSpec>> SPEC = PropwashRegistries.component("creative_spec",
            b -> b.persistent(CreativeCodecs.CODEC).networkSynchronized(CreativeCodecs.STREAM_CODEC).cacheEncoding());

    private static boolean initialized;

    private CreativeModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashNet.registerC2S(CreativeEditC2S.TYPE, CreativeEditC2S.CODEC, CreativeEdits::onEdit);
    }
}
