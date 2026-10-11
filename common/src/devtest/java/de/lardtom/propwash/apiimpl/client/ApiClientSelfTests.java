package de.lardtom.propwash.apiimpl.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.client.OsdLayoutSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.content.client.DroneWorkbenchScreen;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.client.ExampleAddonClient;
import de.lardtom.propwash.dev.example.client.ExampleSizeScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ApiClientSelfTests {
    private static final String PHASE = "client_phase";

    private ApiClientSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("api: OSD-Layout prüfen und unbekannter Konfigurator-Tab (Parts-API 1.4)", 100, ApiClientSelfTests::osdStep);
        SelfTest.clientStep("api: Shift-Hilfe ohne Taste für Bildschirmfotos (Parts-API 1.4)", 100, ctx -> shiftHelpStep());
        SelfTest.clientStep("api: Werkbank-Kopf, fehlende Teile und Größenvergleich im festen Maßstab (Parts-API 1.4)", 1200,
                ApiClientSelfTests::workbenchStep);
    }

    private static StepResult osdStep(SelfTest.StepContext ctx) {
        OsdLayoutSpec defaults = PropwashClientApi.osd().defaults();
        if (!PropwashClientApi.osd().validate(defaults)) {
            return StepResult.fail("validate lehnt das Standard-Layout ab");
        }
        OsdLayoutSpec current = PropwashClientApi.osd().current();
        if (PropwashClientApi.osd().validate(new OsdLayoutSpec(OsdLayoutSpec.CURRENT_VERSION, "{\"elements\":17}"))) {
            return StepResult.fail("validate nimmt ein kaputtes Layout an");
        }
        if (!PropwashClientApi.osd().current().equals(current)) {
            return StepResult.fail("validate hat das OSD-Layout verändert");
        }
        if (PropwashClientApi.openConfigTab("propwash_example:does_not_exist", null)) {
            return StepResult.fail("openConfigTab meldet einen unbekannten Tab als geöffnet");
        }
        return StepResult.pass();
    }

    private static StepResult shiftHelpStep() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return StepResult.fail("keine Welt");
        }
        ItemStack stack = PropwashApi.partItems().stack(ExampleAddon.MOTOR_4214);
        try {
            PropwashClientApi.forceShiftHelp(false);
            int closed = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL).size();
            PropwashClientApi.forceShiftHelp(true);
            int open = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL).size();
            return StepResult.check(open > closed, "forceShiftHelp(true) zeigt keine Shift-Hilfe: " + closed + " → " + open + " Zeilen");
        } finally {
            PropwashClientApi.forceShiftHelp(null);
        }
    }

    private static StepResult workbenchStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                ExampleAddon.showcase(true);
                state.put(ApiSelfTests.SERVER, ctx.onServer(ApiSelfTests::openBench));
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                WorkbenchContext context = ExampleAddonClient.lastContext();
                if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen) || context == null || context.screen() != screen) {
                    return ApiSelfTests.timeout(ctx, 300, "Werkbank ruft die Erweiterungen nicht auf: " + mc.gui.screen());
                }
                String header = header(screen, context);
                if (header != null) {
                    return ApiSelfTests.fail(ctx, header);
                }
                List<Identifier> creative = context.missingParts(ExampleAddon.xclass());
                if (!creative.isEmpty()) {
                    return ApiSelfTests.fail(ctx, "missingParts im Kreativmodus nicht leer: " + creative);
                }
                Identifier unknown = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "does_not_exist");
                DroneData broken = new DroneData(BuildSpec.of(unknown, ExampleAddon.STACK_8S, ExampleAddon.MOTOR_4214, ExampleAddon.PROP_13,
                        ExampleAddon.VIDEO_HD, ExampleAddon.BATTERY_8S), PaintSpec.DEFAULT, TuneSpec.EMPTY, Optional.empty());
                if (!context.missingParts(broken).equals(List.of(unknown))) {
                    return ApiSelfTests.fail(ctx, "missingParts nennt den unbekannten Rahmen nicht: " + context.missingParts(broken));
                }
                context.openChild(ExampleSizeScreen::new);
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                ExampleSizeScreen size = ExampleSizeScreen.active();
                if (size == null || mc.gui.screen() != size || size.frames() < 3) {
                    return ApiSelfTests.timeout(ctx, 400, "Größenvergleich erscheint nicht: " + mc.gui.screen());
                }
                List<ExampleSizeScreen.Entry> entries = size.entries();
                if (entries.size() < 4 || entries.stream().anyMatch(e -> !(e.footprintMm() > 0.0)) || !(size.pixelsPerMillimetre() > 0.0F)) {
                    return ApiSelfTests.fail(ctx, "Größenvergleich ohne Maße: " + entries);
                }
                double freestyle = entries.getFirst().footprintMm();
                double xclass = entries.getLast().footprintMm();
                ctx.log(String.format(Locale.ROOT, "Spannweite über die Prop-Spitzen: Freestyle %.0f mm, X-Class %.0f mm, %.3f px/mm", freestyle,
                        xclass, size.pixelsPerMillimetre()));
                if (!(xclass > 2.5 * freestyle) || freestyle < 300.0 || freestyle > 500.0) {
                    return ApiSelfTests.fail(ctx, "footprintMm unplausibel: Freestyle " + freestyle + ", X-Class " + xclass);
                }
                size.onClose();
                state.put(PHASE, 3);
                return StepResult.pending();
            }
            default -> {
                if (!(mc.gui.screen() instanceof DroneWorkbenchScreen)) {
                    return ApiSelfTests.timeout(ctx, 600, "Zurück aus dem Größenvergleich führt nicht zur Werkbank: " + mc.gui.screen());
                }
                ApiSelfTests.closeBench(ctx);
                ExampleAddon.showcase(false);
                return StepResult.pass();
            }
        }
    }

    private static String header(DroneWorkbenchScreen screen, WorkbenchContext context) {
        if (context.headerSpace() <= 0 || context.headerSpace() != screen.headerSpace()) {
            return "headerSpace falsch: " + context.headerSpace() + " / " + screen.headerSpace();
        }
        List<FlatButton> buttons = screen.extensionButtons();
        if (buttons.size() < 2) {
            return "Werkbank-Kopf zeigt " + buttons.size() + " statt 2 Addon-Knöpfe";
        }
        int left = Integer.MAX_VALUE;
        boolean icon = false;
        for (FlatButton button : buttons) {
            left = Math.min(left, button.getX());
            icon |= button.icon() != null;
        }
        if (left < screen.titleEnd() + DroneWorkbenchScreen.TITLE_GAP) {
            return "Addon-Knöpfe überdecken den Titel: Titel bis " + screen.titleEnd() + ", erster Knopf bei " + left;
        }
        boolean text = ExampleAddonClient.sizeAsText(context);
        if (icon == text) {
            return "Größen-Knopf " + (icon ? "als Symbol" : "als Text") + " bei " + context.headerSpace() + " px Platz";
        }
        return null;
    }
}
