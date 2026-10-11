package de.lardtom.propwash.join.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.DevBootstrap;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.join.AddonInfo;
import de.lardtom.propwash.join.JoinCheck;
import de.lardtom.propwash.join.JoinInfo;
import de.lardtom.propwash.join.JoinReport;
import de.lardtom.propwash.join.JoinText;
import de.lardtom.propwash.join.LocalJoinInfo;
import de.lardtom.propwash.join.PartRef;
import de.lardtom.propwash.network.JoinInfoPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class JoinSelfTests {
    private static final String PREFIX = "beitritt: ";
    private static final AddonInfo JMP = new AddonInfo("justmoreparts", "Propwash: Just More Parts", "1.1.0",
            "https://modrinth.com/mod/propwash-just-more-parts", List.of());
    private static final AddonInfo HUD = new AddonInfo("pilot_hud_plus", "Pilot HUD Plus", "2.1.0", "", List.of());

    private JoinSelfTests() {
    }

    public static void register() {
        AddonSessionSelfTests.register();
        SelfTest.clientStep(PREFIX + "eigene Add-on-Liste mit Name, Version und Namensraum", 5, JoinSelfTests::localStep);
        SelfTest.clientStep(PREFIX + "Übersicht nach einer Absage mit Link und Kopieren", 200, JoinSelfTests::screenStep);
        SelfTest.clientStep(PREFIX + "Übersicht scrollt mit Mausrad, Bild auf/ab, Pos1/Ende, Pfeiltasten und an der Scrollleiste", 100,
                JoinSelfTests::scrollStep);
        if (Screenshots.enabled()) {
            Screenshots.step(PREFIX + "Bildschirmfoto Add-on-Übersicht", "join_addon_mismatch", 10, new ReportScene(false));
            Screenshots.step(PREFIX + "Bildschirmfoto andere Propwash-Version", "join_protocol_mismatch", 10, new ReportScene(true));
            SelfTest.switchStep(PREFIX + "Sprache Deutsch", PREFIX + "Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
            Screenshots.step(PREFIX + "Bildschirmfoto Add-on-Übersicht deutsch", "join_addon_mismatch_de", 10, new ReportScene(false));
            SelfTest.clientStep(PREFIX + "Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        }
    }

    private static StepResult localStep(SelfTest.StepContext ctx) {
        JoinInfo local = LocalJoinInfo.get();
        if (local.protocol() != Constants.PROTOCOL_VERSION) {
            return StepResult.fail("Protokoll " + local.protocol() + " statt " + Constants.PROTOCOL_VERSION);
        }
        if (local.builtinFingerprint().length() != 16 || local.propwashVersion().isBlank()) {
            return StepResult.fail("eingebaute Teile oder Version fehlen: " + local);
        }
        if (JoinTestEnv.exampleAddon()) {
            AddonInfo example = local.addon(ExampleAddon.NAMESPACE).orElse(null);
            if (example == null || !example.name().equals(DevBootstrap.EXAMPLE_NAME) || !example.namespaces().contains(ExampleAddon.NAMESPACE)
                    || !example.homepage().equals(DevBootstrap.EXAMPLE_HOMEPAGE)) {
                return StepResult.fail("Beispiel-Addon fehlt oder ist falsch beschrieben: " + example);
            }
        }
        if (local.addon(Constants.MOD_ID).isPresent()) {
            return StepResult.fail("Propwash selbst steht in der Add-on-Liste");
        }
        ByteBuf buffer = Unpooled.buffer();
        JoinInfoPayload.CODEC.encode(buffer, JoinCheck.localPayload());
        JoinInfo decoded = JoinInfoPayload.CODEC.decode(buffer).info();
        ctx.log("eigene Add-ons: " + local.addons().size() + ", Fingerabdruck " + local.builtinFingerprint());
        return StepResult.check(decoded.equals(local), "Beitrittsdaten überstehen das Netzwerk nicht: " + decoded);
    }

    public static JoinReport sampleReport(boolean protocol) {
        JoinInfo local = LocalJoinInfo.get();
        List<AddonInfo> serverAddons = new ArrayList<>();
        serverAddons.add(JMP);
        List<AddonInfo> clientAddons = new ArrayList<>(local.addons());
        clientAddons.add(HUD);
        if (clientAddons.stream().noneMatch(addon -> addon.modId().equals(ExampleAddon.NAMESPACE))) {
            clientAddons.add(new AddonInfo(ExampleAddon.NAMESPACE, DevBootstrap.EXAMPLE_NAME, DevBootstrap.EXAMPLE_VERSION,
                    DevBootstrap.EXAMPLE_HOMEPAGE, List.of(ExampleAddon.NAMESPACE)));
        }
        clientAddons.removeIf(addon -> addon.modId().equals(JMP.modId()));
        JoinInfo server = new JoinInfo(protocol ? "0.5.1" : local.propwashVersion(), protocol ? local.protocol() + 1 : local.protocol(),
                local.builtinFingerprint(), serverAddons);
        JoinInfo client = new JoinInfo(local.propwashVersion(), local.protocol(), local.builtinFingerprint(), clientAddons);
        if (protocol) {
            return JoinReport.compute(server, client, null, null);
        }
        List<PartRef> serverParts = new ArrayList<>();
        for (String id : List.of("m1404_3800", "m2207_1950", "p5_1_3b", "b6s_1300", "stack_20_45a", "frame_ducted_3")) {
            serverParts.add(new PartRef("motor", JMP.modId() + ":" + id, "1"));
        }
        List<PartRef> clientParts = new ArrayList<>();
        for (String id : List.of("m2806_1300", "xclass_13")) {
            clientParts.add(new PartRef("motor", ExampleAddon.NAMESPACE + ":" + id, "1"));
        }
        return JoinReport.compute(server, client, serverParts, clientParts);
    }

    private static StepResult scrollStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof JoinMismatchScreen screen)) {
            if (ctx.tick() > 40) {
                return StepResult.fail("Übersicht öffnet sich nicht");
            }
            JoinReport report = sampleReport(true);
            mc.gui.setScreen(new JoinMismatchScreen(new TitleScreen(), report, JoinText.disconnect(report), true));
            return StepResult.pending();
        }
        if (ctx.tick() < 4) {
            return StepResult.pending();
        }
        double limit = screen.scrollLimit();
        String size = screen.width + "x" + screen.height;
        if (!(limit > 0.0)) {
            mc.gui.setScreen(null);
            return screen.height > 300 ? StepResult.pass()
                    : StepResult.fail("Übersicht passt bei " + size + " ganz ins Fenster – nichts zu scrollen");
        }
        String failure = null;
        screen.keyPressed(new KeyEvent(InputConstants.KEY_PAGEDOWN, 0, 0));
        double paged = screen.scrollOffset();
        screen.keyPressed(new KeyEvent(InputConstants.KEY_END, 0, 0));
        double end = screen.scrollOffset();
        screen.keyPressed(new KeyEvent(InputConstants.KEY_PAGEUP, 0, 0));
        double back = screen.scrollOffset();
        screen.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
        double home = screen.scrollOffset();
        screen.setFocused(null);
        screen.keyPressed(new KeyEvent(InputConstants.KEY_DOWN, 0, 0));
        double arrow = screen.scrollOffset();
        screen.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
        screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0.0, -1.0);
        double wheel = screen.scrollOffset();
        screen.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
        ScreenRectangle bar = screen.scrollBarArea();
        double barX = bar.left() + bar.width() / 2.0;
        boolean taken = screen.mouseClicked(new MouseButtonEvent(barX, bar.bottom() - 1.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
        double clicked = screen.scrollOffset();
        screen.mouseDragged(new MouseButtonEvent(barX, bar.top() + bar.height() / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), 0.0,
                -bar.height() / 2.0);
        double dragged = screen.scrollOffset();
        screen.mouseDragged(new MouseButtonEvent(barX, bar.top(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), 0.0, -bar.height() / 2.0);
        double top = screen.scrollOffset();
        screen.mouseReleased(new MouseButtonEvent(barX, bar.top(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)));
        screen.mouseDragged(new MouseButtonEvent(barX, bar.bottom() - 1.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), 0.0, 10.0);
        double released = screen.scrollOffset();
        if (!(paged > 0.0)) {
            failure = "Bild ab bewegt nichts";
        } else if (Math.abs(end - limit) > 0.5) {
            failure = "Ende steht bei " + end + " statt " + limit;
        } else if (!(back < end)) {
            failure = "Bild auf bewegt nichts";
        } else if (home != 0.0) {
            failure = "Pos1 steht bei " + home;
        } else if (!(arrow > 0.0)) {
            failure = "Pfeil ab bewegt nichts";
        } else if (!(wheel > 0.0)) {
            failure = "Mausrad bewegt nichts";
        } else if (Math.abs(clicked - limit) > 0.5) {
            failure = "Klick ans Ende der Scrollleiste (" + barX + ", " + (bar.bottom() - 1.0) + " in " + bar + ") steht bei " + clicked + " statt " + limit + " (angenommen " + taken + ", Grenze danach " + screen.scrollLimit() + ")";
        } else if (!(dragged > 0.0 && dragged < limit)) {
            failure = "Ziehen in die Mitte steht bei " + dragged + " von " + limit;
        } else if (top != 0.0) {
            failure = "Ziehen nach oben steht bei " + top;
        } else if (released != 0.0) {
            failure = "nach dem Loslassen zieht die Leiste weiter (" + released + ")";
        }
        if (failure == null) {
            screen.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
            screen.extractRenderStateWithTooltipAndSubtitles(new GuiGraphicsExtractor(mc, new GuiRenderState(), -1, -1), -1, -1, 0.0F);
            List<ScreenRectangle> links = screen.linkAreas();
            ScreenRectangle link = links.isEmpty() ? null : links.getFirst();
            for (int i = 0; link == null && i < 40; i++) {
                screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0.0, -1.0);
                screen.extractRenderStateWithTooltipAndSubtitles(new GuiGraphicsExtractor(mc, new GuiRenderState(), -1, -1), -1, -1, 0.0F);
                links = screen.linkAreas();
                link = links.isEmpty() ? null : links.getFirst();
            }
            if (link == null) {
                failure = "kein Link in der Übersicht";
            } else {
                screen.mouseClicked(new MouseButtonEvent(link.left() + link.width() / 2.0, link.top() + link.height() / 2.0,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                if (!(mc.gui.screen() instanceof ConfirmLinkScreen)) {
                    failure = "Linksklick auf den Link öffnet die Nachfrage nicht (" + link + ")";
                }
            }
        }
        mc.gui.setScreen(null);
        if (failure != null) {
            return StepResult.fail("Übersicht bei " + size + ": " + failure);
        }
        ctx.log(String.format(Locale.ROOT, "Übersicht bei %s: Inhalt %.0f Punkte länger als die Ansicht; Bild ab %.0f, Ende %.0f, Bild auf %.0f, Pfeil %.0f, "
                + "Rad %.0f, Leiste Klick %.0f, Mitte %.0f", size, limit, paged, end, back, arrow, wheel, clicked, dragged));
        return StepResult.pass();
    }

    private static StepResult screenStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int stage = (int) state.getOrDefault("stage", 0);
        switch (stage) {
            case 0 -> {
                JoinReport report = sampleReport(false);
                if (!report.blocking() || report.lines(true).size() != 1 || report.lines(false).size() != 2
                        || report.lines(false).stream().noneMatch(line -> line.modId().equals(ExampleAddon.NAMESPACE) && line.inactive() == 2)) {
                    return StepResult.fail("Beispielbericht falsch: " + report);
                }
                mc.gui.setScreen(new JoinMismatchScreen(new TitleScreen(), report, JoinText.disconnect(report), true));
                state.put("stage", 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (!(mc.gui.screen() instanceof JoinMismatchScreen screen)) {
                    return StepResult.fail("Übersicht nicht offen: " + mc.gui.screen());
                }
                if (screen.linkCount() != 2) {
                    return StepResult.fail("Links: " + screen.linkCount() + " statt 2");
                }
                screen.copyDetails();
                String clipboard = mc.keyboardHandler.getClipboard();
                for (String needle : List.of("justmoreparts", ExampleAddon.NAMESPACE, "pilot_hud_plus", "1.1.0", "modrinth.com")) {
                    if (!clipboard.contains(needle)) {
                        return StepResult.fail("Details ohne " + needle + ": " + clipboard);
                    }
                }
                if (screen.openLink("javascript:alert(1)")) {
                    return StepResult.fail("unsicherer Link geöffnet");
                }
                if (!screen.openLink(JMP.homepage()) || !(mc.gui.screen() instanceof ConfirmLinkScreen)) {
                    return StepResult.fail("Link fragt nicht nach: " + mc.gui.screen());
                }
                mc.gui.setScreen(screen);
                screen.onClose();
                state.put("stage", 2);
                return StepResult.pending();
            }
            default -> {
                boolean back = mc.gui.screen() instanceof TitleScreen;
                mc.gui.setScreen(null);
                return StepResult.check(back, "Zurück führt nicht zum vorigen Bildschirm");
            }
        }
    }

    private record ReportScene(boolean protocol) implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            JoinReport report = sampleReport(this.protocol);
            mc.gui.setScreen(new JoinMismatchScreen(new TitleScreen(), report, JoinText.disconnect(report), true));
            Screenshots.parkMouse(mc);
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }
}
