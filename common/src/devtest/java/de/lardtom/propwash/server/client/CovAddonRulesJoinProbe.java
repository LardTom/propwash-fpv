package de.lardtom.propwash.server.client;

import de.lardtom.propwash.dev.example.ExampleRules;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.server.WorldRuleEditC2S;
import de.lardtom.propwash.server.WorldRulesS2C;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CovAddonRulesJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 90;
    public static final String OPEN = "cov_rules_open";
    private static final String VALUE = "12";
    private static final String OTHER = "3";
    private static final String STAGE = "cov_rules_stage";
    private static final String MARK = "cov_rules_mark";
    private static final String OUT = "cov_rules_out";
    private static final String DENIED = "cov_rules_denied";

    private CovAddonRulesJoinProbe() {
    }

    private static StringBuilder out(Map<String, Object> state) {
        return (StringBuilder) state.computeIfAbsent(OUT, key -> new StringBuilder());
    }

    private static int stage(Map<String, Object> state) {
        return (int) state.getOrDefault(STAGE, 0);
    }

    private static void next(Map<String, Object> state, int tick) {
        state.put(STAGE, stage(state) + 1);
        state.put(MARK, tick);
    }

    private static String raw() {
        return ClientWorldRules.INSTANCE.raw(ExampleRules.LAUNCHER_COOLDOWN).orElse("-");
    }

    private static int denials(Minecraft mc) {
        int count = 0;
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            List<?> messages = (List<?>) field.get(mc.gui.hud.getChat());
            for (Object message : new ArrayList<>(messages)) {
                if (message instanceof GuiMessage gui && gui.content().getContents() instanceof TranslatableContents contents
                        && "propwash.server.world_rules.denied".equals(contents.getKey())) {
                    count++;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
        return count;
    }

    private static @Nullable WorldRulesTab tab(Minecraft mc) {
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            ConfigTabs.open(null, WorldRulesTab.ID);
            return null;
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !WorldRulesTab.ID.equals(active.id())) {
            screen.selectTab(WorldRulesTab.ID);
            return null;
        }
        return active instanceof WorldRulesTab rules && !rules.rows().isEmpty() ? rules : null;
    }

    public static void whileOpen(Map<String, Object> state, int tick, Minecraft mc) {
        int opened = (int) state.get(OPEN);
        WorldRulesTab rules = tab(mc);
        if (rules == null || tick - opened < 10) {
            if (tick - opened > 200) {
                out(state).append("rules_section=missing\n");
                close(state, tick, mc);
            }
            return;
        }
        WorldRulesTab.Row row = rules.row(ExampleRules.LAUNCHER_COOLDOWN).orElse(null);
        AbstractWidget widget = row == null ? null : row.widget();
        String shown = widget instanceof de.lardtom.propwash.ui.client.widget.ValueSlider slider ? Long.toString(Math.round(slider.value()))
                : widget == null ? "-" : widget.getMessage().getString();
        out(state).append("rules_section=").append(row == null ? "missing" : rules.readOnly() ? "readonly" : "editable").append('\n')
                .append("rules_tab_value=").append(shown).append('\n')
                .append("rules_widget_active=").append(widget != null && widget.active).append('\n');
        close(state, tick, mc);
    }

    private static void close(Map<String, Object> state, int tick, Minecraft mc) {
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        state.remove(OPEN);
        state.put(DENIED, denials(mc));
        ClientNet.sendToServer(new WorldRuleEditC2S(List.of(new WorldRulesS2C.Entry(ExampleRules.LAUNCHER_COOLDOWN, OTHER))));
        state.put(STAGE, 2);
        state.put(MARK, tick);
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        state.putIfAbsent(MARK, tick);
        int waited = tick - (int) state.get(MARK);
        if (!ExampleRules.registered()) {
            return "rules_example=absent\n";
        }
        switch (stage(state)) {
            case 0 -> {
                if (!VALUE.equals(raw()) && waited < TIMEOUT_TICKS) {
                    return null;
                }
                out(state).append("rules_value=").append(raw()).append('\n');
                if (mc.level != null) {
                    out(state).append("rules_client_get=").append(ExampleRules.cooldown().get(mc.level)).append('\n');
                }
                next(state, tick);
            }
            case 1 -> {
                state.put(OPEN, tick);
                ConfigTabs.open(null, WorldRulesTab.ID);
                return null;
            }
            case 2 -> {
                boolean denied = denials(mc) > (int) state.get(DENIED);
                if (!denied && waited < 100) {
                    return null;
                }
                out(state).append("rules_edit=").append(denied && VALUE.equals(raw()) ? "denied" : OTHER.equals(raw()) ? "accepted" : "no_reply")
                        .append('\n');
                ClientPacketListener connection = mc.getConnection();
                boolean node = connection != null && connection.getCommands().getRoot().getChild("gamerule") != null;
                out(state).append("gamerule_node=").append(node ? "present" : "missing").append('\n');
                if (connection != null) {
                    connection.sendCommand("gamerule " + ExampleRules.LAUNCHER_COOLDOWN + " " + OTHER);
                }
                next(state, tick);
            }
            case 3 -> {
                if (waited < 60) {
                    return null;
                }
                out(state).append("gamerule=").append(VALUE.equals(raw()) ? "denied" : "accepted").append('\n')
                        .append("rules_value_after=").append(raw()).append('\n');
                next(state, tick);
            }
            default -> {
                return out(state).toString();
            }
        }
        return null;
    }
}
