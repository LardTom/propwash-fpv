package de.lardtom.propwash.input.addon.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.dev.example.ExampleSwitches;
import de.lardtom.propwash.dev.example.client.ExampleSwitchesClient;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guard.GuardServer;
import de.lardtom.propwash.guard.client.GuardClient;
import de.lardtom.propwash.input.client.screen.AddonSwitchRows;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

public final class Addon17JoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 90;
    public static final String OPEN = "a17.open";
    private static final String PHASE = "a17.phase";
    private static final String MARK = "a17.mark";
    private static final String OUT = "a17.out";
    private static final String PACKETS = "a17.packets";
    private static final String START = "a17.start";

    private Addon17JoinProbe() {
    }

    private static int phase(Map<String, Object> state) {
        return (int) state.getOrDefault(PHASE, 0);
    }

    private static void next(Map<String, Object> state, int tick) {
        state.put(PHASE, phase(state) + 1);
        state.put(MARK, tick);
    }

    private static int since(Map<String, Object> state, int tick) {
        return tick - (int) state.getOrDefault(MARK, tick);
    }

    private static StringBuilder out(Map<String, Object> state) {
        return (StringBuilder) state.computeIfAbsent(OUT, key -> new StringBuilder());
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        state.putIfAbsent(MARK, tick);
        KeyMapping ghostKey = ExampleSwitchesClient.ghostKey();
        switch (phase(state)) {
            case 0 -> {
                if (!PilotSession.INSTANCE.isActive() || PilotSession.INSTANCE.drone() == null) {
                    if (since(state, tick) > TIMEOUT_TICKS) {
                        out(state).append("addon_pilot=none\n");
                        state.put(PHASE, 99);
                    }
                    return null;
                }
                TreeSet<String> known = new TreeSet<>();
                for (Identifier id : AddonServerFeatures.switches()) {
                    known.add(id.toString());
                }
                for (Identifier id : AddonServerFeatures.axes()) {
                    known.add(id.toString());
                }
                out(state).append("addon_pilot=fpv\n").append("addon_features_received=").append(AddonServerFeatures.received()).append('\n')
                        .append("addon_features=").append(known.isEmpty() ? "none" : String.join(",", known)).append('\n');
                next(state, tick);
                state.put(OPEN, tick);
                ConfigTabs.open(null, ModesTab.ID);
                return null;
            }
            case 2 -> {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                    return null;
                }
                state.put(PACKETS, AddonSwitchClient.switchPackets());
                ghostKey.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
                KeyMapping.resetMapping();
                KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
                next(state, tick);
                return null;
            }
            case 3 -> {
                if (since(state, tick) < 40) {
                    return null;
                }
                boolean on = PropwashClientApi.input().switchActive(ExampleSwitches.GHOST);
                out(state).append("addon_switch=").append(on ? "on" : "off").append('\n')
                        .append("addon_switch_local=").append(AddonSwitchClient.localActive(ExampleSwitches.GHOST) ? "on" : "off").append('\n');
                KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
                next(state, tick);
                return null;
            }
            case 4 -> {
                if (since(state, tick) < 40) {
                    return null;
                }
                boolean on = PropwashClientApi.input().switchActive(ExampleSwitches.GHOST);
                int packets = AddonSwitchClient.switchPackets() - (int) state.get(PACKETS);
                out(state).append("addon_switch_after=").append(on ? "on" : "off").append('\n').append("addon_switch_packets=").append(packets).append('\n');
                ghostKey.setKey(InputConstants.UNKNOWN);
                KeyMapping up = ExampleSwitchesClient.tiltUp();
                up.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_H));
                KeyMapping.resetMapping();
                state.put(START, PropwashClientApi.input().axis(ExampleSwitches.GIMBAL_TILT));
                up.setDown(true);
                next(state, tick);
                return null;
            }
            case 5 -> {
                KeyMapping up = ExampleSwitchesClient.tiltUp();
                if (since(state, tick) < 20) {
                    up.setDown(true);
                    return null;
                }
                up.setDown(false);
                up.setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
                double value = PropwashClientApi.input().axis(ExampleSwitches.GIMBAL_TILT);
                double local = AddonSwitchClient.localAxis(ExampleSwitches.GIMBAL_TILT, 0.0);
                out(state).append("addon_axis=").append(String.format(Locale.ROOT, "%.2f", value)).append('\n')
                        .append("addon_axis_local=").append(local > (double) state.get(START) + 0.1 ? "moved" : "still").append('\n')
                        .append("addon_axis_packets=").append(AddonSwitchClient.axisPackets()).append('\n');
                next(state, tick);
                return null;
            }
            case 6 -> {
                if (since(state, tick) < 20) {
                    return null;
                }
                out(state).append("ns_server_hint=").append(serverHints(mc)).append('\n').append("ns_client_hint=").append(GuardClient.hints()).append('\n');
                next(state, tick);
                return null;
            }
            default -> {
                return out(state).toString();
            }
        }
    }

    public static void whileOpen(Map<String, Object> state, int tick, Minecraft mc) {
        int opened = (int) state.get(OPEN);
        if (!(mc.gui.screen() instanceof ConfiguratorScreen configurator) || !(configurator.activeTab() instanceof ModesTab modes)) {
            if (tick - opened > 100) {
                out(state).append("addon_page=missing\n");
                close(state, tick, mc);
            }
            return;
        }
        if (!modes.addonsShown()) {
            modes.showAddons();
            modes.addons().select(ExampleSwitches.GHOST);
            state.put(MARK, tick);
            return;
        }
        if (since(state, tick) < 10) {
            return;
        }
        String inactive = Component.translatable(AddonSwitchRows.INACTIVE_KEY).getString();
        List<LayoutAudit.Text> texts = LayoutProbe.capture(mc, configurator).texts();
        boolean shown = texts.stream().anyMatch(text -> text.value().contains(inactive));
        String ghost = Component.translatable(ExampleSwitches.ghost().spec().nameKey()).getString();
        boolean listed = texts.stream().anyMatch(text -> text.value().contains(ghost));
        out(state).append("addon_page=").append(!listed ? "missing" : shown ? "inactive" : "active").append('\n');
        configurator.discardAndClose();
        close(state, tick, mc);
    }

    private static void close(Map<String, Object> state, int tick, Minecraft mc) {
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        state.remove(OPEN);
        state.put(PHASE, 2);
        state.put(MARK, tick);
    }

    private static int serverHints(Minecraft mc) {
        int count = 0;
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            List<?> messages = (List<?>) field.get(mc.gui.hud.getChat());
            for (Object message : new ArrayList<>(messages)) {
                if (message instanceof GuiMessage gui && gui.content().getContents() instanceof TranslatableContents contents
                        && GuardServer.HINT_KEY.equals(contents.getKey())) {
                    count++;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
        return count;
    }
}
