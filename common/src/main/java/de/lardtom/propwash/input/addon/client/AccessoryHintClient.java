package de.lardtom.propwash.input.addon.client;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.PartNames;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.addon.AccessoryActionHints;
import de.lardtom.propwash.input.client.FunctionKeys;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.input.client.KeyNames;
import de.lardtom.propwash.input.client.RawInput;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.InputLabels;
import de.lardtom.propwash.ui.client.FirstUseHints;
import de.lardtom.propwash.ui.client.theme.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class AccessoryHintClient {
    public static final String TITLE_KEY = "propwash.input.accessory_hint.title";
    public static final String ACTION_KEY = "propwash.input.accessory_hint.action";
    public static final String OR_KEY = "propwash.input.accessory_hint.or";
    public static final String KEY_KEY = "propwash.input.accessory_hint.key";
    public static final String UNBOUND_KEY = "propwash.input.accessory_hint.unbound";
    public static final int DELAY_TICKS = 10;

    private static int droneId = -1;
    private static int activeTicks;
    private static boolean checked;
    private static int shownCount;
    private static boolean muted;

    private AccessoryHintClient() {
    }

    static void init() {
        if (!AccessoryActionHints.CONFIG.isLoaded()) {
            AccessoryActionHints.CONFIG.load();
        }
    }

    public static int shownCount() {
        return shownCount;
    }

    public static void mute(boolean value) {
        muted = value;
    }

    public static void forgetAll() {
        AccessoryActionHints.Seen seen = seen();
        seen.players.remove(FirstUseHints.playerKey());
        AccessoryActionHints.CONFIG.save();
    }

    public static boolean fresh(AccessoryType type) {
        return AccessoryActionHints.fresh(seen(), FirstUseHints.playerKey(), type.unladen().id());
    }

    static void tick(Minecraft minecraft) {
        QuadcopterEntity drone = AddonSwitchClient.pilotDrone();
        if (drone == null) {
            droneId = -1;
            activeTicks = 0;
            checked = false;
            return;
        }
        if (drone.getId() != droneId) {
            droneId = drone.getId();
            activeTicks = 0;
            checked = false;
        }
        activeTicks++;
        if (checked || activeTicks < DELAY_TICKS || minecraft.player == null || muted) {
            return;
        }
        checked = true;
        for (Component line : due(drone.build().accessories().byMount().values())) {
            minecraft.gui.hud.getChat().addClientSystemMessage(line);
        }
    }

    public static List<Component> due(Collection<AccessoryType> mounted) {
        List<Component> lines = new ArrayList<>();
        AccessoryActionHints.Seen seen = seen();
        String player = FirstUseHints.playerKey();
        boolean changed = false;
        for (AccessoryActionHints.Named named : AccessoryActionHints.named(mounted)) {
            if (!AccessoryActionHints.fresh(seen, player, named.accessory())) {
                continue;
            }
            AccessoryType type = AccessoryType.byId(named.accessory()).orElse(null);
            lines.addAll(message(type, named));
            changed |= AccessoryActionHints.shown(seen, player, named.accessory());
            shownCount++;
        }
        if (changed) {
            AccessoryActionHints.CONFIG.save();
        }
        return lines;
    }

    public static List<Component> message(@Nullable AccessoryType type, AccessoryActionHints.Named named) {
        List<Component> lines = new ArrayList<>();
        Component name = type == null ? Component.literal(named.accessory().toString()) : PartNames.name(type);
        lines.add(Component.translatable(TITLE_KEY, name).withStyle(style -> style.withColor(TextColor.fromRgb(Palette.ACCENT & 0xFFFFFF))));
        for (Map.Entry<Integer, String> action : named.actions().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            lines.add(Component.translatable(ACTION_KEY, action.getKey(), Component.translatable(action.getValue()), binding(action.getKey())));
        }
        return lines;
    }

    public static Component binding(int number) {
        SwitchFunction function = SwitchFunction.accessory(number);
        Component device = deviceBinding(function);
        KeyMapping key = FunctionKeys.mapping(function);
        Component keyText = key == null || key.isUnbound() ? null : Component.translatable(KEY_KEY, KeyNames.of(key));
        if (device != null && keyText != null) {
            return Component.translatable(OR_KEY, device, keyText);
        }
        if (device != null) {
            return device;
        }
        if (keyText != null) {
            return keyText;
        }
        KeyMapping configurator = KeyMapping.get(KeyDefaults.CONFIGURATOR.mappingName());
        Component open = configurator == null || configurator.isUnbound() ? Component.translatable("propwash.input.key.unbound") : KeyNames.of(configurator);
        MutableComponent text = Component.translatable(UNBOUND_KEY, open);
        return text.withStyle(ChatFormatting.YELLOW);
    }

    private static @Nullable Component deviceBinding(SwitchFunction function) {
        RawInput raw = TransmitterInput.INSTANCE.raw();
        if (raw == null) {
            return null;
        }
        DeviceProfile profile = InputConfig.get().profile(raw.device().guid());
        if (profile == null) {
            return null;
        }
        SwitchAssignment binding = profile.binding(function);
        return binding.bound() ? InputLabels.bindingFor(binding, profile, raw) : null;
    }

    private static AccessoryActionHints.Seen seen() {
        if (!AccessoryActionHints.CONFIG.isLoaded()) {
            AccessoryActionHints.CONFIG.load();
        }
        return AccessoryActionHints.CONFIG.get();
    }
}
