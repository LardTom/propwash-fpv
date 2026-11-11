package de.lardtom.propwash.share.client;

import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.UiScaleSelfTests;
import de.lardtom.propwash.share.ShareDevTables;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ShareUiScaleCases {
    public static final String WINDOW_IMPORT = "Sharecode-Fenster Import mit fehlendem Add-on";
    public static final String WINDOW_EXPORT = "Sharecode-Fenster Export";
    public static final String TAB = "Sharecode-Tab mit fehlendem Add-on";

    private ShareUiScaleCases() {
    }

    public static List<UiScaleSelfTests.Case> all() {
        return List.of(new Window(WINDOW_IMPORT, ShareScreen.Mode.IMPORT), new Window(WINDOW_EXPORT, ShareScreen.Mode.EXPORT), new Tab());
    }

    static boolean visible(Screen screen, FlatButton button) {
        return button.visible && button.getX() >= 0 && button.getY() >= 0 && button.getX() + button.getWidth() <= screen.width
                && button.getY() + button.getHeight() <= screen.height;
    }

    private static final class Window implements UiScaleSelfTests.Case {
        private final String name;
        private final ShareScreen.Mode mode;
        private String saved = "";

        private Window(String name, ShareScreen.Mode mode) {
            this.name = name;
            this.mode = mode;
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                ShareDevTables.ensure();
                this.saved = ShareSettings.lastInput();
                ShareSettings.lastInput(ShareSelfTests.P2_JMP);
                Optional<de.lardtom.propwash.api.build.DroneData> result = this.mode == ShareScreen.Mode.EXPORT ? Optional.of(ShareSelfTests.freestyle())
                        : Optional.empty();
                mc.gui.setScreen(ShareHarness.window(ShareHarness.context(result, false), this.mode));
                return StepResult.pending();
            }
            ShareScreen screen = ShareScreen.active();
            return screen != null && mc.gui.screen() == screen && screen.frames() >= 2 && ctx.tick() >= 3 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public List<LayoutAudit.Finding> audit(Minecraft mc, Screen screen) {
            List<LayoutAudit.Finding> findings = new ArrayList<>(LayoutProbe.audit(mc, screen));
            if (screen instanceof ShareScreen share && this.mode == ShareScreen.Mode.IMPORT) {
                FlatButton apply = share.applyButton();
                if (apply == null || !visible(screen, apply)) {
                    findings.add(new LayoutAudit.Finding(LayoutAudit.Kind.HIDDEN, this.name, "„Übernehmen“ nach dem Öffnen nicht ganz zu sehen"));
                }
                if (!share.addonHintVisible()) {
                    findings.add(new LayoutAudit.Finding(LayoutAudit.Kind.HIDDEN, this.name, "Add-on-Hinweis nach dem Öffnen nicht ganz zu sehen"));
                }
            }
            if (screen instanceof ShareScreen share && this.mode == ShareScreen.Mode.EXPORT) {
                FlatButton copy = share.copyButton();
                if (copy == null || !visible(screen, copy)) {
                    findings.add(new LayoutAudit.Finding(LayoutAudit.Kind.HIDDEN, this.name, "„In die Zwischenablage“ nach dem Öffnen nicht ganz zu sehen"));
                }
            }
            return findings;
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
            ShareSettings.lastInput(this.saved);
        }
    }

    private static final class Tab implements UiScaleSelfTests.Case {
        private String saved = "";

        @Override
        public String name() {
            return TAB;
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                ShareDevTables.ensure();
                this.saved = ShareSettings.lastInput();
                ShareSettings.lastInput(ShareSelfTests.P2_JMP);
                return PropwashClientApi.openConfigTab(ShareConfigTab.ID, null) ? StepResult.pending() : StepResult.fail("Tab lässt sich nicht öffnen");
            }
            ShareConfigTab tab = ShareConfigTab.active();
            return tab != null && mc.gui.screen() instanceof ConfiguratorScreen && tab.frames() >= 2 && ctx.tick() >= 3 ? StepResult.pass()
                    : StepResult.pending();
        }

        @Override
        public List<LayoutAudit.Finding> audit(Minecraft mc, Screen screen) {
            List<LayoutAudit.Finding> findings = new ArrayList<>(LayoutProbe.audit(mc, screen));
            ShareConfigTab tab = ShareConfigTab.active();
            if (tab != null) {
                FlatButton build = tab.buildButton();
                if (build == null || !visible(screen, build)) {
                    findings.add(new LayoutAudit.Finding(LayoutAudit.Kind.HIDDEN, TAB, "Knopf „Drohne erzeugen“/„Einkaufsliste“ nach dem Öffnen nicht ganz zu sehen"));
                }
                if (!tab.addonHintVisible()) {
                    findings.add(new LayoutAudit.Finding(LayoutAudit.Kind.HIDDEN, TAB, "Add-on-Hinweis nach dem Öffnen nicht ganz zu sehen"));
                }
            }
            return findings;
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            } else {
                mc.gui.setScreen(null);
            }
            ShareSettings.lastInput(this.saved);
        }
    }
}
