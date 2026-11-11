package de.lardtom.propwash.guard.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.input.client.screen.AddonSwitchRows;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.osd.client.OsdConfigTab;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class CovNoclipMenuSelfTests {
    public static final String NAME = "ui-scale: Abdeckung – alle Konfigurator-Tabs mit den Seiten „Funktionen“, „Befehle“, „Add-ons“ und dem OSD-Editor"
            + " öffnen ohne Ausnahme (auch mit Noclip 0.1.x)";
    private static final String VIEWS = "cov_menu_views";
    private static final String INDEX = "cov_menu_index";
    private static final String MARK = "cov_menu_mark";
    private static final String LOG = "cov_menu_log";
    private static final int SETTLE = 4;

    private CovNoclipMenuSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(NAME, 2400, CovNoclipMenuSelfTests::step);
    }

    private record View(String tab, String page) {
        String label() {
            return this.page.isEmpty() ? this.tab : this.tab + " / " + this.page;
        }
    }

    private static List<View> views(ConfiguratorScreen screen) {
        List<View> out = new ArrayList<>();
        for (ConfigTab tab : screen.tabs()) {
            out.add(new View(tab.id(), ""));
            if (tab instanceof ModesTab) {
                out.add(new View(tab.id(), "commands"));
                if (AddonSwitchRows.available()) {
                    out.add(new View(tab.id(), "addons"));
                }
                out.add(new View(tab.id(), "flight"));
            }
            if (tab instanceof OsdConfigTab) {
                out.add(new View(tab.id(), "options"));
                out.add(new View(tab.id(), "editor"));
            }
        }
        return out;
    }

    private static void show(ConfiguratorScreen screen, View view) {
        screen.selectTab(view.tab());
        ConfigTab active = screen.activeTab();
        if (active instanceof ModesTab modes) {
            switch (view.page()) {
                case "commands" -> modes.showCommands(true);
                case "addons" -> modes.showAddons();
                case "flight" -> modes.showCommands(false);
                default -> {
                }
            }
        }
        if (active instanceof OsdConfigTab osd) {
            OsdConfigTab.Page wanted = view.page().equals("options") ? OsdConfigTab.Page.OPTIONS : OsdConfigTab.Page.STYLE;
            if (osd.page() != wanted) {
                osd.switchPage();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static StepResult step(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        boolean noclip = Services.PLATFORM.isModLoaded(CovGuardSelfTests.NOCLIP);
        try {
            if (ctx.tick() == 0) {
                mc.gui.setScreen(null);
                ConfigTabs.open(null, null);
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                return ctx.tick() > 100 ? StepResult.fail("Konfigurator öffnet nicht oder schließt unerwartet: " + mc.gui.screen()) : StepResult.pending();
            }
            List<View> views = (List<View>) ctx.state().computeIfAbsent(VIEWS, key -> views(screen));
            List<String> log = (List<String>) ctx.state().computeIfAbsent(LOG, key -> new ArrayList<String>());
            int index = (int) ctx.state().getOrDefault(INDEX, -1);
            if (index >= 0 && ctx.tick() - (int) ctx.state().get(MARK) < SETTLE) {
                return StepResult.pending();
            }
            if (index >= 0) {
                List<LayoutAudit.Finding> findings = LayoutProbe.audit(mc, screen);
                String entry = views.get(index).label() + ": " + findings.size() + " Befunde";
                if (!findings.isEmpty()) {
                    entry += " (zuerst " + findings.getFirst().describe() + ")";
                }
                log.add(entry);
            }
            index++;
            if (index >= views.size()) {
                screen.discardAndClose();
                ctx.log((noclip ? "mit Noclip " : "ohne Noclip ") + views.size() + " Ansichten ohne Ausnahme: " + String.join("; ", log));
                return StepResult.pass();
            }
            show(screen, views.get(index));
            ctx.state().put(INDEX, index);
            ctx.state().put(MARK, ctx.tick());
            return StepResult.pending();
        } catch (RuntimeException | LinkageError e) {
            Constants.LOG.error("[Selbsttest] {}", NAME, e);
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            return StepResult.fail("Ausnahme beim Öffnen der Menüs" + (noclip ? " mit Noclip" : "") + ": " + e);
        }
    }
}
