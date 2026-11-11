package de.lardtom.propwash.share.chat;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.share.ShareCommands;
import de.lardtom.propwash.share.ShareDevTables;
import de.lardtom.propwash.share.ShareVectors;
import de.lardtom.propwash.share.client.ShareConfigTab;
import de.lardtom.propwash.share.client.chat.ChatPreview;
import de.lardtom.propwash.share.client.chat.ChatPreviewImage;
import de.lardtom.propwash.share.client.chat.ShareChat;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class ShareChatSelfTests {
    public static final String STEP = "share: chat – Codes in fremden Chatzeilen (PW3, PW2, PW1, Formatcodes) klickbar mit Vorschau, ungültig nur mit Hinweis, "
            + "Klick öffnet den Import, Umschalt-Klick kopiert, Schalter aus";
    public static final String LANGUAGE_STEP = "share: chat – Vorschau formatiert Zahlen nach einem Sprachwechsel in der neuen Sprache";
    public static final String SENDER = "Alex";
    public static final String NAMED = ShareVectors.NAMED_WHOOP;
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String BEFORE = "before";
    private static final String TIPS = "tips";
    private static final String AT = "at";
    private static final String MESSAGE = "message";
    private static final String SYSTEM = "system";
    private static final String BROKEN = "broken";
    private static final String ENABLED = "enabled";
    private static final String INVENTORY = "inventory";
    private static final int HOVER_TICKS = 10;
    private static final byte[] SIGNATURE = filled();

    private ShareChatSelfTests() {
    }

    private static byte[] filled() {
        byte[] bytes = new byte[MessageSignature.BYTES];
        Arrays.fill(bytes, (byte) 7);
        return bytes;
    }

    public static void register() {
        SelfTest.clientStep(STEP, 1400, ShareChatSelfTests::chatStep);
        SelfTest.clientStep(LANGUAGE_STEP, 40, ctx -> languageStep());
        ShareChatScreenshots.register();
    }

    private static StepResult languageStep() {
        ShareDevTables.ensure();
        LanguageManager languages = Minecraft.getInstance().getLanguageManager();
        String saved = languages.getSelected();
        try {
            languages.setSelected("en_us");
            String english = thrustToWeight(ShareChat.preview(NAMED, false));
            languages.setSelected("de_de");
            String german = thrustToWeight(ShareChat.preview(NAMED, false));
            if (english == null || german == null) {
                return StepResult.fail("Vorschau ohne Zeile Schub/Gewicht: englisch " + english + ", deutsch " + german);
            }
            return StepResult.check(english.contains(".") && german.contains(",") && !german.contains("."),
                    "Schub/Gewicht nach dem Sprachwechsel: englisch „" + english + "“, deutsch „" + german + "“");
        } finally {
            languages.setSelected(saved);
            ShareChat.clearCache();
        }
    }

    private static @Nullable String thrustToWeight(@Nullable ChatPreview preview) {
        if (preview == null) {
            return null;
        }
        for (Component line : preview.details()) {
            if (line.getContents() instanceof TranslatableContents text && text.getKey().equals(ChatPreview.KEY + "weight") && text.getArgs().length == 2) {
                return String.valueOf(text.getArgs()[1]);
            }
        }
        return null;
    }

    static Component playerLine(String sender, String text) {
        return Component.translatable("chat.type.text", Component.literal(sender), Component.literal(text));
    }

    public static Component inject(Minecraft mc, String text) {
        Component line = playerLine(SENDER, text);
        mc.gui.hud.getChat().addPlayerMessage(line, new MessageSignature(SIGNATURE), GuiMessageTag.chatNotSecure());
        return line;
    }

    private static StepResult chatStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        try {
            return switch (phase) {
                case 0 -> setup(ctx, mc);
                case 1 -> lines(ctx, mc);
                case 2 -> hover(ctx, mc);
                case 3 -> hovered(ctx, mc);
                case 4 -> clickOpen(ctx, mc);
                case 5 -> copyAndSurvival(ctx, mc);
                case 6 -> survival(ctx, mc);
                default -> optionOff(ctx, mc);
            };
        } catch (RuntimeException e) {
            restore(ctx, mc);
            throw e;
        }
    }

    private static StepResult next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static StepResult fail(SelfTest.StepContext ctx, Minecraft mc, String reason) {
        restore(ctx, mc);
        return StepResult.fail(reason);
    }

    private static void restore(SelfTest.StepContext ctx, Minecraft mc) {
        Object enabled = ctx.state().get(ENABLED);
        if (enabled instanceof Boolean value) {
            ShareChat.setEnabled(value);
        }
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        ctx.onServer(server -> server.getPlayerList().getPlayers().forEach(player -> player.setGameMode(GameType.CREATIVE)));
    }

    private static StepResult setup(SelfTest.StepContext ctx, Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return StepResult.pending();
        }
        ShareDevTables.ensure();
        ctx.state().put(ENABLED, ShareChat.enabled());
        ShareChat.setEnabled(true);
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        mc.gui.hud.getChat().clearMessages(false);
        ShareChat.clearCache();
        ctx.state().put(BEFORE, ShareChat.decodes());
        ctx.state().put(INVENTORY, occupied(mc));
        Component first = inject(mc, "schau mal: " + NAMED + " – fliegt super");
        ctx.state().put(MESSAGE, first);
        Component system = Component.literal("§7[Server] §aalt: §l" + ShareVectors.PW2_NAMED + "§r und " + ShareVectors.PW1_A);
        ctx.state().put(SYSTEM, system);
        mc.gui.hud.getChat().addServerSystemMessage(system);
        Component broken = Component.literal("kaputt: " + ShareChatHarness.invalidCode() + " und falsch: " + ShareVectors.P10_CHECKSUM);
        ctx.state().put(BROKEN, broken);
        mc.gui.hud.getChat().addClientSystemMessage(broken);
        return next(ctx, 1);
    }

    private static StepResult lines(SelfTest.StepContext ctx, Minecraft mc) {
        List<String> errors = new ArrayList<>();
        int decodes = ShareChat.decodes() - (int) ctx.state().get(BEFORE);
        Component original = (Component) ctx.state().get(MESSAGE);
        FormattedText player = ShareChat.display(original);
        int shownCode = ShareChatHarness.shownCells(mc, NAMED);
        if (shownCode != NAMED.length()) {
            errors.add("Code im Chat nicht ganz als Link gezeigt: " + shownCode + " von " + NAMED.length() + " Zeichen, Zeilen "
                    + ShareChatHarness.shown(mc).stream().map(ShareChatHarness.Shown::plain).toList());
        }
        Style code = ShareChatHarness.styleIn(player, NAMED, 0);
        Style last = ShareChatHarness.styleIn(player, NAMED, NAMED.length() - 1);
        Style sender = ShareChatHarness.styleIn(player, SENDER, 0);
        if (!ShareChatHarness.ours(code, NAMED) || !ShareChatHarness.ours(last, NAMED)) {
            errors.add("Code nicht klickbar: " + code);
        }
        if (code == null || !code.isUnderlined() || code.getColor() == null || code.getColor().getValue() != (ShareChat.COLOR & 0xFFFFFF)) {
            errors.add("Code nicht unterstrichen und farbig: " + code);
        }
        if (sender == null || sender.getClickEvent() != null || sender.getHoverEvent() != null || sender.isUnderlined()) {
            errors.add("Absender verändert: " + sender);
        }
        String hover = ShareChatHarness.hoverText(code);
        String frame = Component.translatable(ChatPreview.KEY + "frame", "").getString().trim();
        for (String needed : List.of(ShareVectors.NAMED_WHOOP_NAME, frame, Component.translatable(ChatPreview.KEY + "hint.open").getString())) {
            if (!hover.contains(needed)) {
                errors.add("Vorschau ohne „" + needed + "“: " + hover.replace('\n', '|'));
            }
        }
        FormattedText system = ShareChat.display((Component) ctx.state().get(SYSTEM));
        for (String legacy : List.of(ShareVectors.PW2_NAMED, ShareVectors.PW1_A)) {
            Style style = ShareChatHarness.styleIn(system, legacy, 0);
            if (!ShareChatHarness.ours(style, legacy) || ShareChatHarness.shownCells(mc, legacy) == 0) {
                errors.add(legacy.substring(0, 3) + " nicht klickbar: " + style);
            }
        }
        Style gray = ShareChatHarness.styleIn(system, "[Server]", 0);
        ShareChatHarness.Shown server = ShareChatHarness.lineWith(mc, "[Server]");
        Style shownGray = server == null ? null : server.styleAt(0);
        TextColor grayColor = TextColor.fromLegacyFormat(ChatFormatting.GRAY);
        if (gray == null || shownGray == null || !grayColor.equals(gray.getColor()) || !grayColor.equals(shownGray.getColor())
                || ShareChatHarness.shown(mc).stream().anyMatch(line -> line.plain().contains("§"))) {
            errors.add("Formatcodes der Systemzeile verändert: " + (server == null ? "Zeile fehlt" : server.plain() + " " + shownGray));
        }
        FormattedText broken = ShareChat.display((Component) ctx.state().get(BROKEN));
        String invalid = ShareChatHarness.invalidCode();
        Style bad = ShareChatHarness.styleIn(broken, invalid, 0);
        String invalidText = ShareChat.invalidText().getString();
        if (bad == null || bad.getClickEvent() != null || !ShareChatHarness.hoverText(bad).equals(invalidText) || !bad.isUnderlined()) {
            errors.add("ungültiger Code nicht nur mit „" + invalidText + "“: " + bad);
        }
        Style checksum = ShareChatHarness.styleIn(broken, ShareVectors.P10_CHECKSUM, 0);
        if (checksum == null || checksum.getHoverEvent() != null || checksum.getClickEvent() != null || checksum.isUnderlined()) {
            errors.add("Code mit falscher Prüfsumme erkannt: " + checksum);
        }
        GuiMessage stored = ShareChatHarness.messages(mc).stream().filter(message -> message.content() == original).findFirst().orElse(null);
        if (stored == null || stored.signature() == null || !Arrays.equals(stored.signature().bytes(), SIGNATURE) || stored.tag() == null) {
            errors.add("Nachricht selbst verändert (Inhalt, Signatur oder Kennzeichen): " + stored);
        }
        if (decodes != 4) {
            errors.add("erwartet 4 Dekodierungen (PW3, PW2, PW1, ungültig), gezählt " + decodes);
        }
        if (!errors.isEmpty()) {
            return fail(ctx, mc, String.join("; ", errors));
        }
        ctx.log("Zeilen: PW3, PW2 und PW1 klickbar, ungültiger Code nur mit Hinweis, Prüfsumme falsch nicht erkannt, Nachricht und Signatur unverändert, "
                + decodes + " Dekodierungen");
        mc.gui.setScreen(new ChatScreen("", false));
        return next(ctx, 2);
    }

    private static StepResult hover(SelfTest.StepContext ctx, Minecraft mc) {
        if (!(mc.gui.screen() instanceof ChatScreen)) {
            return fail(ctx, mc, "Chat öffnet nicht: " + mc.gui.screen());
        }
        float[] at = ShareChatHarness.position(mc, style -> ShareChatHarness.ours(style, NAMED));
        if (at == null) {
            return fail(ctx, mc, "Code im offenen Chat nicht unter dem Zeiger zu finden");
        }
        ShareChatHarness.moveMouse(mc, at[0], at[1]);
        ctx.state().put(AT, at);
        ctx.state().put(TIPS, ShareChat.tooltips());
        ctx.state().put(BEFORE, ShareChat.decodes());
        return next(ctx, 3);
    }

    private static StepResult hovered(SelfTest.StepContext ctx, Minecraft mc) {
        if (since(ctx) < HOVER_TICKS) {
            return StepResult.pending();
        }
        int tips = ShareChat.tooltips() - (int) ctx.state().get(TIPS);
        int decodes = ShareChat.decodes() - (int) ctx.state().get(BEFORE);
        if (tips < 3) {
            return fail(ctx, mc, "Vorschau-Tooltip nicht gezeichnet (" + tips + " Bilder mit Tooltip in " + HOVER_TICKS + " Ticks)");
        }
        if (decodes != 0) {
            return fail(ctx, mc, "beim Darüberfahren neu dekodiert: " + decodes);
        }
        Style style = ShareChatHarness.shownStyle(mc, NAMED);
        List<ClientTooltipComponent> tooltip = ShareChat.tooltip(style, mc.font, mc.getWindow().getGuiScaledWidth());
        if (tooltip == null || tooltip.stream().noneMatch(ChatPreviewImage.class::isInstance) || tooltip.size() < 8) {
            return fail(ctx, mc, "Tooltip ohne Drohnenbild oder zu kurz: " + (tooltip == null ? "keiner" : tooltip.size() + " Teile"));
        }
        ctx.log("Tooltip in " + tips + " Bildern gezeichnet, " + tooltip.size() + " Teile mit Drohnenbild, keine neue Dekodierung");
        float[] at = (float[]) ctx.state().get(AT);
        boolean handled = mc.gui.screen().mouseClicked(new MouseButtonEvent(at[0], at[1], new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
        if (!handled) {
            return fail(ctx, mc, "Klick auf den Code nicht angenommen");
        }
        return next(ctx, 4);
    }

    private static StepResult clickOpen(SelfTest.StepContext ctx, Minecraft mc) {
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return since(ctx) < 40 ? StepResult.pending() : fail(ctx, mc, "Klick öffnet den Import nicht: " + mc.gui.screen());
        }
        ShareConfigTab tab = ShareConfigTab.active();
        if (tab == null || !NAMED.equals(tab.state().text()) || !tab.state().decoded().ok()) {
            return since(ctx) < 40 ? StepResult.pending() : fail(ctx, mc, "Import-Tab ohne den Code: " + (tab == null ? "kein Tab" : tab.state().text()));
        }
        int inventory = occupied(mc);
        if (tab.lastResult() != null || inventory != (int) ctx.state().get(INVENTORY)) {
            return fail(ctx, mc, "Import hat ohne Bestätigung etwas übernommen: " + tab.lastResult());
        }
        ctx.log("Klick öffnet den Tab Sharecode mit dem Code, nichts übernommen");
        screen.discardAndClose();
        return next(ctx, 5);
    }

    private static StepResult copyAndSurvival(SelfTest.StepContext ctx, Minecraft mc) {
        if (!(mc.gui.screen() instanceof ChatScreen chat)) {
            return fail(ctx, mc, "nach dem Import nicht zurück im Chat: " + mc.gui.screen());
        }
        mc.keyboardHandler.setClipboard("");
        Style style = ShareChatHarness.shownStyle(mc, NAMED);
        if (!ShareChat.click(style, true, chat) || !NAMED.equals(mc.keyboardHandler.getClipboard()) || mc.gui.screen() != chat) {
            return fail(ctx, mc, "Umschalt-Klick kopiert nicht: " + mc.keyboardHandler.getClipboard());
        }
        mc.gui.hud.getChat().addClientSystemMessage(ShareCommands.codeLine(ShareVectors.PW1_A_PW3));
        Style own = ShareChatHarness.shownStyle(mc, ShareVectors.PW1_A_PW3);
        mc.keyboardHandler.setClipboard("");
        if (!ShareChat.target(own).map(ShareChat.Target::copy).orElse(false)
                || !ShareChatHarness.hoverText(own).contains(Component.translatable(ChatPreview.KEY + "hint.copy").getString())
                || !ShareChat.click(own, false, chat) || !ShareVectors.PW1_A_PW3.equals(mc.keyboardHandler.getClipboard())) {
            return fail(ctx, mc, "eigene Export-Zeile: Klick kopiert nicht mit Vorschau: " + own);
        }
        ctx.log("Umschalt-Klick kopiert, eigene Export-Zeile behält „Klick kopiert“ und bekommt die Vorschau");
        mc.gui.setScreen(null);
        ctx.onServer(server -> server.getPlayerList().getPlayers().forEach(player -> player.setGameMode(GameType.SURVIVAL)));
        return next(ctx, 6);
    }

    private static StepResult survival(SelfTest.StepContext ctx, Minecraft mc) {
        if (mc.player == null || mc.player.hasInfiniteMaterials()) {
            return since(ctx) < 200 ? StepResult.pending() : fail(ctx, mc, "Spieler wechselt nicht in den Überlebensmodus");
        }
        ChatPreview preview = ShareChat.preview(NAMED, false);
        Component shopping = preview == null ? null : preview.shoppingLine();
        Style style = ShareChatHarness.shownStyle(mc, NAMED);
        List<ClientTooltipComponent> tooltip = ShareChat.tooltip(style, mc.font, mc.getWindow().getGuiScaledWidth());
        String expected = Component.translatable(ChatPreview.KEY + "shopping.missing", 9).getString();
        if (shopping == null || tooltip == null || !shopping.getString().contains(expected.substring(0, expected.indexOf('9')).trim())) {
            return fail(ctx, mc, "Überleben: Zeile mit fehlenden Teilen fehlt: " + (shopping == null ? "keine" : shopping.getString()));
        }
        ctx.log("Überleben: " + shopping.getString());
        ctx.onServer(server -> server.getPlayerList().getPlayers().forEach(player -> player.setGameMode(GameType.CREATIVE)));
        return next(ctx, 7);
    }

    private static StepResult optionOff(SelfTest.StepContext ctx, Minecraft mc) {
        if (mc.player == null || !mc.player.hasInfiniteMaterials()) {
            return since(ctx) < 200 ? StepResult.pending() : fail(ctx, mc, "Spieler wechselt nicht zurück in den Kreativmodus");
        }
        ShareChat.setEnabled(false);
        Style off = ShareChatHarness.shownStyle(mc, NAMED);
        boolean textThere = ShareChatHarness.shown(mc).stream().anyMatch(line -> line.plain().contains(NAMED.substring(0, 12)));
        ShareChat.setEnabled(true);
        Style on = ShareChatHarness.shownStyle(mc, NAMED);
        if (off != null || !textThere || !ShareChatHarness.ours(on, NAMED)) {
            return fail(ctx, mc, "Schalter „" + Component.translatable("propwash.share.chat.setting").getString() + "“ wirkt nicht: aus " + off + ", an " + on);
        }
        ctx.log("Schalter aus: Zeilen ohne Link, wieder an: Link zurück");
        restore(ctx, mc);
        mc.gui.hud.getChat().clearMessages(false);
        return StepResult.pass();
    }

    private static int occupied(Minecraft mc) {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            count += mc.player.getInventory().getItem(i).getCount();
        }
        return count;
    }
}
