package de.lardtom.propwash.ui.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.server.client.WorldRulesTab;
import de.lardtom.propwash.ui.SettingsSearch;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.search.SearchBar;
import de.lardtom.propwash.ui.client.search.SettingsIndex;
import de.lardtom.propwash.ui.client.tab.SettingsTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public final class CrashDisarmPlacementSelfTests {
    private static final String RULE_KEY = "gamerule.propwash.crash_disarm";

    private CrashDisarmPlacementSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("ui: Nach Absturz abschalten nur im Tab Welt-Regeln, die Suche führt dorthin", 400, CrashDisarmPlacementSelfTests::step);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ConfiguratorScreen screen)) {
            if (ctx.tick() == 0) {
                ConfigTabs.open(null, SettingsTab.ID);
            }
            return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
        }
        SearchBar search = screen.search();
        if (search == null) {
            return close(screen, StepResult.fail("Konfigurator ohne Suchfeld"));
        }
        String rule = Component.translatable(RULE_KEY).getString();
        String folded = rule.toLowerCase(Locale.ROOT);
        search.focus();
        search.type("crash disarm");
        SettingsIndex index = search.index();
        if (index == null) {
            return close(screen, StepResult.fail("Suchindex fehlt"));
        }
        for (SettingsSearch.Entry entry : index.entries()) {
            String label = entry.label().toLowerCase(Locale.ROOT);
            if (!WorldRulesTab.ID.equals(entry.tabId()) && label.contains(folded)) {
                return close(screen, StepResult.fail("„" + entry.label() + "“ steht noch im Tab " + entry.tabId()));
            }
        }
        for (String query : List.of("crash disarm", "disarm on crash", "absturz")) {
            search.type(query);
            List<SettingsSearch.Hit> hits = search.hits();
            if (hits.isEmpty()) {
                return close(screen, StepResult.fail("„" + query + "“ findet nichts"));
            }
            int ruleHit = -1;
            for (int i = 0; i < hits.size(); i++) {
                SettingsSearch.Entry entry = hits.get(i).entry();
                if (entry.label().equals(rule)) {
                    if (!WorldRulesTab.ID.equals(entry.tabId())) {
                        return close(screen, StepResult.fail("„" + query + "“ führt in den Tab " + entry.tabId()));
                    }
                    if (ruleHit < 0) {
                        ruleHit = i;
                    }
                }
            }
            if (ruleHit < 0) {
                return close(screen, StepResult.fail("„" + query + "“ findet die Welt-Regel nicht: " + hits.getFirst().entry().label()));
            }
            if (query.equals("disarm on crash") && ruleHit != 0) {
                return close(screen, StepResult.fail("„disarm on crash“ zeigt die Welt-Regel nicht zuerst: " + hits.getFirst().entry().label()));
            }
            if (query.equals("crash disarm")) {
                search.pick(ruleHit);
                ConfigTab active = screen.activeTab();
                AbstractWidget target = screen.highlighted();
                if (!(active instanceof WorldRulesTab) || target == null) {
                    return close(screen, StepResult.fail("Treffer öffnet nicht den Tab Welt-Regeln: " + (active == null ? "-" : active.id())));
                }
                if (!target.visible || target.getY() < screen.contentArea().top() || target.getY() + target.getHeight() > screen.contentArea().bottom()) {
                    return close(screen, StepResult.fail("Regel ist nach dem Sprung nicht im Bild (y " + target.getY() + ")"));
                }
                search.focus();
            }
        }
        ctx.log("ui: „" + rule + "“ nur im Tab Welt-Regeln, Suche (crash disarm, disarm on crash, absturz) springt dorthin");
        return close(screen, StepResult.pass());
    }

    private static StepResult close(ConfiguratorScreen screen, StepResult result) {
        screen.discardAndClose();
        return result;
    }
}
