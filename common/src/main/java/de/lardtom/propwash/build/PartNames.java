package de.lardtom.propwash.build;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.Locale;

public final class PartNames {
    public static final String MISSING_NAME = "propwash.parts.missing_name";
    public static final String MISSING_PART = "propwash.parts.missing_part";
    public static final String MISSING_SHORT = "propwash.parts.missing_short";

    private PartNames() {
    }

    public static boolean own(Identifier id) {
        return PartIndex.DEFAULT_NAMESPACE.equals(id.getNamespace());
    }

    public static MutableComponent name(PartInfo part) {
        if (part.missing()) {
            return Component.translatable(MISSING_PART, part.id().toString());
        }
        return Component.translatable(part.translationKey());
    }

    public static String frameShortKey(Identifier frame) {
        return own(frame) ? "propwash.content.frame_short." + frame.getPath() : frame.getNamespace() + ".frame_short." + frame.getPath();
    }

    public static MutableComponent frameShort(FrameType frame) {
        if (frame.missing()) {
            return Component.translatable(MISSING_SHORT, frame.id().toString());
        }
        String key = frameShortKey(frame.id());
        if (own(frame.id()) || Language.getInstance().has(key)) {
            return Component.translatable(key);
        }
        return Component.translatable(frame.translationKey());
    }

    public static MutableComponent frameShortByKey(String frameKey) {
        return PartIndex.byKey(FrameType.class, frameKey).map(PartNames::frameShort).orElseGet(() -> {
            Identifier id = PartIndex.idOfKey(frameKey);
            if (id == null) {
                return Component.literal(frameKey);
            }
            return own(id) ? Component.translatable(frameShortKey(id)) : Component.translatable(MISSING_SHORT, id.toString());
        });
    }

    public static MutableComponent motorMount(MountSize.Motor mount) {
        return mount(mount.id(), "propwash.content.mount.motor.", ".mount.motor.");
    }

    public static MutableComponent stackMount(MountSize.Stack mount) {
        return mount(mount.id(), "propwash.content.mount.stack.", ".mount.stack.");
    }

    public static MutableComponent propMount(Identifier mount) {
        return mount(mount, "propwash.parts.mount.prop.", ".mount.prop.");
    }

    public static MutableComponent chemistry(BatteryType.Chemistry chemistry) {
        Identifier id = chemistry.id();
        if (own(id)) {
            return Component.translatable("propwash.content.tooltip.chemistry." + id.getPath());
        }
        return Component.translatableWithFallback(id.getNamespace() + ".chemistry." + id.getPath(), fallback(id));
    }

    public static String helpPrefix(Identifier part) {
        return own(part) ? "propwash.help." + part.getPath() : part.getNamespace() + ".help." + part.getPath();
    }

    private static MutableComponent mount(Identifier id, String ownPrefix, String foreignSuffix) {
        if (own(id)) {
            return Component.translatable(ownPrefix + id.getPath());
        }
        return Component.translatableWithFallback(id.getNamespace() + foreignSuffix + id.getPath(), fallback(id));
    }

    private static String fallback(Identifier id) {
        return id.getPath().toUpperCase(Locale.ROOT).replace('_', ' ');
    }
}
