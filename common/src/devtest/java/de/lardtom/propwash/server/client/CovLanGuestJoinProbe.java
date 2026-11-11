package de.lardtom.propwash.server.client;

import com.mojang.brigadier.tree.CommandNode;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class CovLanGuestJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 90;
    private static final String START = "cov_lan_start";
    private static final String STAGE = "cov_lan_stage";
    private static final String SINCE = "cov_lan_since";
    private static final String LINES = "cov_lan_lines";

    private CovLanGuestJoinProbe() {
    }

    private static void line(Map<String, Object> state, String text) {
        state.put(LINES, state.getOrDefault(LINES, "") + text + "\n");
    }

    private static void stage(Map<String, Object> state, int stage, int tick) {
        state.put(STAGE, stage);
        state.put(SINCE, tick);
    }

    private static @Nullable WorldRulesTab tab(Minecraft mc) {
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return null;
        }
        ConfigTab active = screen.activeTab();
        return active instanceof WorldRulesTab tab ? tab : null;
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        int since = (int) state.getOrDefault(SINCE, tick);
        if (tick - start > TIMEOUT_TICKS) {
            line(state, "cov_languest timeout at stage " + stage);
            return close(mc, state);
        }
        if (mc.player == null || mc.getConnection() == null) {
            return null;
        }
        switch (stage) {
            case 0 -> {
                if (tick - start < 60) {
                    return null;
                }
                boolean operator = mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
                line(state, "cov_languest operator=" + (operator ? "yes" : "no"));
                line(state, "cov_languest rules_editable=" + ClientWorldRules.INSTANCE.editable());
                stage(state, 1, tick);
            }
            case 1 -> {
                ConfigTabs.open(null, WorldRulesTab.ID);
                WorldRulesTab tab = tab(mc);
                String result = null;
                if (tab != null) {
                    boolean readOnly = tab.readOnly() && tab.rows().stream().noneMatch(row -> row.widget() != null) && !ClientWorldRules.INSTANCE.editable();
                    result = "cov_languest world_rules=" + (readOnly ? "readonly" : "editable") + " (Tab nur lesbar " + tab.readOnly() + ", Regler "
                            + tab.rows().stream().filter(row -> row.widget() != null).count() + ")";
                }
                if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                    screen.discardAndClose();
                }
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                if (result != null) {
                    line(state, result);
                    stage(state, 2, tick);
                } else if (tick - since > 200) {
                    line(state, "cov_languest world_rules=missing");
                    stage(state, 2, tick);
                }
            }
            case 2 -> {
                CommandNode<?> propwash = mc.getConnection().getCommands().getRoot().getChild("propwash");
                boolean inTree = propwash != null && propwash.getChild("clear") != null;
                line(state, "cov_languest clear_in_tree=" + (inTree ? "yes" : "no") + (propwash == null ? " (kein propwash)" : ""));
                mc.gui.hud.getChat().clearMessages(false);
                mc.getConnection().sendCommand("propwash clear");
                stage(state, 3, tick);
            }
            case 3 -> {
                String here = Component.translatable("command.context.here").getString();
                List<String> chat = CovClearSelfTests.chat();
                String done = Component.translatable("propwash.server.command.clear.done", 1, "").getString();
                boolean refused = chat.stream().anyMatch(text -> text.contains(here) || text.contains(Component.translatable("command.unknown.command").getString()));
                boolean cleared = chat.stream().anyMatch(text -> text.startsWith(done.substring(0, Math.min(8, done.length()))));
                if (refused || tick - since > 100) {
                    line(state, "cov_languest clear_refused=" + (refused && !cleared ? "yes" : "no") + " chat=" + chat);
                    return close(mc, state);
                }
            }
            default -> {
                return close(mc, state);
            }
        }
        return null;
    }

    private static String close(Minecraft mc, Map<String, Object> state) {
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        return (String) state.getOrDefault(LINES, "");
    }
}
