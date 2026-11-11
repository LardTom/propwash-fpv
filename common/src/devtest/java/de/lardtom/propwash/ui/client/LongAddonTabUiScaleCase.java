package de.lardtom.propwash.ui.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.UiScaleSelfTests;
import de.lardtom.propwash.server.client.WorldRulesTab;
import de.lardtom.propwash.ui.ChromeLayout;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.LayoutAudit.Finding;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.tab.PidTab;
import de.lardtom.propwash.ui.client.tab.RatesTab;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LongAddonTabUiScaleCase implements UiScaleSelfTests.Case {
    public static final String NAME = "Konfigurator mit langem Add-on-Tab";
    public static final String TAB_ID = "ui_scale_long_addon_tab";
    public static final String TITLE = "Long add-on tab";
    private static final List<String> PAGES = List.of(PidTab.ID, RatesTab.ID, WorldRulesTab.ID);
    private static final String RACE_STANDARD = "race_standard";
    private static final String OPENED = "long_addon_tab_opened";
    private static final String STANDARD_KEY = "propwash.ui.preset.pid.standard";
    private static final String STANDARD_ID = "builtin:standard";
    private static final int OPEN_TICKS = 200;
    private static final int SCROLL_STEPS = 64;
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    private static boolean registered;
    private String page = "";

    public static UiScaleSelfTests.Case create() {
        return new LongAddonTabUiScaleCase();
    }

    private static synchronized void register() {
        if (!registered) {
            registered = true;
            ConfigTabs.register(() -> ACTIVE.get() ? new LongTab() : null);
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public StepResult open(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            register();
            ACTIVE.set(true);
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ctx.onServer(server -> server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
            return StepResult.pending();
        }
        if (mc.player == null || DroneAssembly.isDrone(mc.player.getMainHandItem())) {
            return ctx.tick() > OPEN_TICKS ? StepResult.fail("Drohne bleibt in der Hand") : StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            if (!ctx.state().containsKey(OPENED)) {
                ctx.state().put(OPENED, ctx.tick());
                ConfigTabs.open(null, PidTab.ID);
            }
            return ctx.tick() > OPEN_TICKS ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
        }
        if (screen.tabs().stream().noneMatch(tab -> TAB_ID.equals(tab.id()))) {
            return StepResult.fail("Tab „" + TITLE + "“ fehlt in der Seitenleiste");
        }
        return ctx.tick() - (int) ctx.state().getOrDefault(OPENED, 0) >= 3 ? StepResult.pass() : StepResult.pending();
    }

    @Override
    public int pages(Screen screen) {
        return PAGES.size();
    }

    @Override
    public String page(Screen screen, int index) {
        String id = PAGES.get(index);
        this.page = id;
        if (screen instanceof ConfiguratorScreen configurator) {
            configurator.selectTab(id);
            configurator.setFocused(null);
            configurator.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
            if (configurator.activeTab() instanceof WorldRulesTab rules) {
                showRaceStandard(rules);
            }
        }
        return WorldRulesTab.ID.equals(id) ? id + " " + RACE_STANDARD : id;
    }

    @Override
    public List<Finding> audit(Minecraft mc, Screen screen) {
        List<Finding> findings = new ArrayList<>();
        for (Finding finding : LayoutProbe.audit(mc, screen)) {
            if (!TITLE.equals(finding.subject())) {
                findings.add(finding);
            }
        }
        if (screen instanceof ConfiguratorScreen configurator && !configurator.sidebarButtons().isEmpty()) {
            int widest = 0;
            for (ConfigTab tab : configurator.tabs()) {
                widest = Math.max(widest, mc.font.width(tab.title()));
            }
            int title = mc.font.width(TITLE);
            int expected = ChromeLayout.sidebarWidth(configurator.windowArea().width(), title);
            int actual = configurator.sidebarButtons().getFirst().getWidth() + 1;
            if (widest != title || actual != expected) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, "Seitenleiste mit Tab „" + TITLE + "“", "Seitenleiste " + actual + " statt " + expected
                        + " Punkte breit, breitester Tab-Titel " + widest + " statt " + title + " Punkte"));
            }
            if (configurator.activeTab() instanceof PidTab pid) {
                Finding standard = standard(mc, configurator, pid);
                if (standard != null && findings.stream().noneMatch(finding -> finding.subject().equals(standard.subject()))) {
                    findings.add(standard);
                }
            }
            if (WorldRulesTab.ID.equals(this.page)) {
                for (Finding button : raceStandard(mc, configurator)) {
                    if (findings.stream().noneMatch(finding -> finding.subject().equals(button.subject()))) {
                        findings.add(button);
                    }
                }
            }
        }
        return findings;
    }

    private static List<FlatButton> showRaceStandard(WorldRulesTab tab) {
        tab.revealRaceStandard();
        FlatButton adopt = tab.raceStandard().adoptButton();
        FlatButton reset = tab.raceStandard().resetButton();
        if (adopt == null || reset == null) {
            return List.of();
        }
        for (int i = 0; i < SCROLL_STEPS && !inView(tab, adopt, reset); i++) {
            double notches = adopt.getY() < tab.viewTop() ? 1.0 : -1.0;
            if (!tab.mouseScrolled(adopt.getX() + 1, tab.viewTop() + 1, 0.0, notches)) {
                break;
            }
        }
        return List.of(adopt, reset);
    }

    private static boolean inView(WorldRulesTab tab, FlatButton adopt, FlatButton reset) {
        return Math.min(adopt.getY(), reset.getY()) >= tab.viewTop() && Math.max(adopt.getBottom(), reset.getBottom()) <= tab.clipBottom();
    }

    private static List<Finding> raceStandard(Minecraft mc, ConfiguratorScreen screen) {
        String subject = "Tab Welt-Regeln, Abschnitt Rennstandard";
        if (!(screen.activeTab() instanceof WorldRulesTab tab)) {
            return List.of(new Finding(LayoutAudit.Kind.HIDDEN, subject, "Tab Welt-Regeln fehlt"));
        }
        List<FlatButton> buttons = tab.readOnly() ? List.of() : showRaceStandard(tab);
        if (buttons.isEmpty()) {
            return List.of(new Finding(LayoutAudit.Kind.HIDDEN, subject, "Knöpfe „Aktuelle Regeln übernehmen“ und „Propwash-Standard“ fehlen"));
        }
        LayoutProbe.Frame frame = LayoutProbe.capture(mc, screen);
        List<Finding> out = new ArrayList<>();
        for (FlatButton button : buttons) {
            String name = button.getMessage().getString();
            String where = "Knopf " + button.getWidth() + " Punkte für " + mc.font.width(name) + " Punkte Text, Zeilenbreite " + tab.rowWidth();
            if (frame.cuts().containsKey(name)) {
                out.add(new Finding(LayoutAudit.Kind.CUT, name, "gekürzt zu „" + frame.cuts().get(name) + "“ (" + where + ")"));
                continue;
            }
            LayoutAudit.Text drawn = null;
            for (LayoutAudit.Text text : frame.texts()) {
                if (text.value().equals(name)) {
                    drawn = text;
                    break;
                }
            }
            LayoutAudit.Box visible = drawn == null ? null : drawn.visible();
            if (drawn == null || visible == null || !visible.equals(drawn.full())) {
                out.add(new Finding(LayoutAudit.Kind.HIDDEN, name, "nach dem Scrollen zum Abschnitt nicht ganz zu sehen (" + where + ")"));
            }
        }
        return out;
    }

    private static @Nullable Finding standard(Minecraft mc, ConfiguratorScreen screen, PidTab tab) {
        String name = Component.translatable(STANDARD_KEY).getString();
        if (!tab.presets().select(STANDARD_ID)) {
            return new Finding(LayoutAudit.Kind.HIDDEN, name, "Preset nicht wählbar");
        }
        screen.setFocused(null);
        screen.keyPressed(new KeyEvent(InputConstants.KEY_END, 0, 0));
        try {
            LayoutProbe.Frame frame = LayoutProbe.capture(mc, screen);
            LayoutAudit.Text drawn = null;
            for (LayoutAudit.Text text : frame.texts()) {
                if (text.value().equals(name)) {
                    drawn = text;
                    break;
                }
            }
            int needed = mc.font.width(name);
            LayoutAudit.Box visible = drawn == null ? null : drawn.visible();
            if (frame.cuts().containsKey(name)) {
                return new Finding(LayoutAudit.Kind.CUT, name, "gekürzt zu „" + frame.cuts().get(name) + "“");
            }
            if (drawn == null || visible == null || !visible.equals(drawn.full())) {
                return new Finding(LayoutAudit.Kind.HIDDEN, name, "am Ende des Tabs nicht ganz zu sehen");
            }
            if (drawn.full().width() < needed - 1) {
                return new Finding(LayoutAudit.Kind.CUT, name, "gestaucht auf " + drawn.full().width() + " von " + needed + " Punkten");
            }
            return null;
        } finally {
            screen.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
        }
    }

    @Override
    public void close(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        ACTIVE.set(false);
    }

    private static final class LongTab extends ConfigTab {
        @Override
        public String id() {
            return TAB_ID;
        }

        @Override
        public Component title() {
            return Component.literal(TITLE);
        }

        @Override
        public int order() {
            return 5000;
        }

        @Override
        protected void build() {
        }
    }
}
