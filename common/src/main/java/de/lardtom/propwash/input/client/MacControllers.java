package de.lardtom.propwash.input.client;

import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.MfiHint;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class MacControllers {
    private static @Nullable Boolean optionShownOverride;

    private MacControllers() {
    }

    public static boolean mac() {
        return Util.getPlatform() == Util.OS.OSX;
    }

    public static @Nullable String environment() {
        return System.getenv(MfiHint.NAME);
    }

    public static MfiHint.Plan plan(InputConfig config) {
        return MfiHint.plan(mac(), environment(), config.appleGameController);
    }

    public static boolean optionShown() {
        Boolean override = optionShownOverride;
        return override != null ? override : mac();
    }

    public static void overrideOptionShown(@Nullable Boolean shown) {
        optionShownOverride = shown;
    }

    public static MfiHint.Plan shownPlan(InputConfig config) {
        return MfiHint.plan(true, environment(), config.appleGameController);
    }

    public static Component noDeviceHint(InputConfig config, boolean receiverTab) {
        Component base = Component.translatable("propwash.input.wizard.no_device_hint");
        if (!optionShown()) {
            return base;
        }
        MfiHint.Plan plan = shownPlan(config);
        Component mac = switch (plan.source()) {
            case ENVIRONMENT -> Component.translatable("propwash.input.wizard.no_device_hint.mac_env", String.valueOf(plan.environment()));
            case OPTION_ON -> Component.translatable(receiverTab ? "propwash.input.wizard.no_device_hint.mac_on.tab" : "propwash.input.wizard.no_device_hint.mac_on");
            case OPTION_OFF, NOT_MAC -> Component.translatable(receiverTab ? "propwash.input.wizard.no_device_hint.mac_off.tab"
                    : "propwash.input.wizard.no_device_hint.mac_off");
        };
        return Component.empty().append(base).append(" ").append(mac);
    }
}
