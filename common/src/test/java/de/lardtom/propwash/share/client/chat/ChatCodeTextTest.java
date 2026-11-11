package de.lardtom.propwash.share.client.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringDecomposer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCodeTextTest {
    private static final String PW3 = ChatCodeFinderTest.PW3;
    private static final String PW2 = ChatCodeFinderTest.PW2;
    private static final Identifier OPEN = Identifier.fromNamespaceAndPath("propwash", "share_chat/open");
    private static final Identifier COPY = Identifier.fromNamespaceAndPath("propwash", "share_chat/copy");
    private static final int COLOR = 0xFFBB00;

    private record Cell(int codepoint, Style style) {
    }

    private static final ChatCodeText.Styler STYLER = (found, copy) -> new ChatCodeText.Decoration(COLOR,
            new ClickEvent.Custom(copy ? COPY : OPEN, Optional.of(StringTag.valueOf(found.code()))),
            new HoverEvent.ShowText(Component.literal("preview " + found.code())));

    private static List<Cell> cells(FormattedText text, boolean colors) {
        List<Cell> out = new ArrayList<>();
        text.visit((style, contents) -> {
            String source = colors ? contents : ChatFormatting.stripFormatting(contents);
            StringDecomposer.iterateFormatted(source, style, (position, charStyle, codepoint) -> {
                out.add(new Cell(codepoint, charStyle));
                return true;
            });
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static String plain(List<Cell> cells) {
        StringBuilder out = new StringBuilder();
        for (Cell cell : cells) {
            out.appendCodePoint(cell.codepoint());
        }
        return out.toString();
    }

    private static void assertOnlyCodesChanged(FormattedText before, FormattedText after, boolean colors) {
        List<Cell> was = cells(before, colors);
        List<Cell> now = cells(after, colors);
        assertEquals(plain(was), plain(now), "Text der Zeile");
        List<ChatCodeFinder.Found> codes = ChatCodeFinder.find(plain(was));
        int at = 0;
        for (int i = 0; i < was.size(); i++) {
            int index = at;
            boolean inCode = codes.stream().anyMatch(code -> index >= code.start() && index < code.end());
            if (inCode) {
                assertEquals(COLOR, now.get(i).style().getColor().getValue(), "Farbe im Code");
                assertTrue(now.get(i).style().isUnderlined(), "Code unterstrichen");
                assertTrue(now.get(i).style().getHoverEvent() instanceof HoverEvent.ShowText, "Vorschau am Code");
            } else {
                assertEquals(was.get(i).style(), now.get(i).style(), "Stil außerhalb des Codes an Stelle " + index);
            }
            at += Character.charCount(was.get(i).codepoint());
        }
    }

    private static ClickEvent clickAt(FormattedText text, int index, boolean colors) {
        return cells(text, colors).get(index).style().getClickEvent();
    }

    @Test
    void lineWithoutCodeStaysTheSameObject() {
        Component line = Component.literal("<Alex> hallo PW, wie geht's? PW3 fehlt");
        assertSame(line, ChatCodeText.decorate(line, true, STYLER));
        Component other = Component.literal("nichts");
        assertSame(other, ChatCodeText.decorate(other, true, STYLER));
    }

    @Test
    void codeIsUnderlinedColoredAndClickableRestUnchanged() {
        Component line = Component.literal("<Alex> ").append(Component.literal("schau: " + PW3 + " ok").withStyle(ChatFormatting.GRAY));
        FormattedText out = ChatCodeText.decorate(line, true, STYLER);
        assertNotSame(line, out);
        assertEquals(line.getString(), out.getString());
        assertOnlyCodesChanged(line, out, true);
        int start = line.getString().indexOf(PW3);
        ClickEvent click = clickAt(out, start, true);
        assertTrue(click instanceof ClickEvent.Custom custom && custom.id().equals(OPEN)
                && custom.payload().flatMap(tag -> tag.asString()).orElse("").equals(PW3));
        assertNull(clickAt(out, start - 1, true));
        assertNull(clickAt(out, start + PW3.length(), true));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY).getValue(), cells(out, true).get(start - 1).style().getColor().getValue());
    }

    @Test
    void formattingCodesInTheLineAreResolved() {
        Component line = Component.literal("§aHallo §l" + PW3 + "§r und §cRest");
        FormattedText out = ChatCodeText.decorate(line, true, STYLER);
        assertEquals("Hallo " + PW3 + " und Rest", plain(cells(out, true)));
        assertFalse(out.getString().contains("§"));
        assertOnlyCodesChanged(line, out, true);
        int start = "Hallo ".length();
        Style code = cells(out, true).get(start).style();
        assertTrue(code.isBold(), "fett aus §l bleibt");
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN).getValue(), cells(out, true).get(0).style().getColor().getValue());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED).getValue(), cells(out, true).get(plain(cells(out, true)).indexOf("Rest")).style().getColor().getValue());
    }

    @Test
    void formattingCodesWithChatColorsOff() {
        Component line = Component.literal("§a" + PW3 + "§r!");
        FormattedText out = ChatCodeText.decorate(line, false, STYLER);
        assertEquals(PW3 + "!", plain(cells(out, false)));
        assertOnlyCodesChanged(line, out, false);
    }

    @Test
    void codeSplitOverTwoStyledParts() {
        Component line = Component.literal(PW3.substring(0, 8)).append(Component.literal(PW3.substring(8)).withStyle(ChatFormatting.RED));
        FormattedText out = ChatCodeText.decorate(line, true, STYLER);
        assertOnlyCodesChanged(line, out, true);
        assertTrue(clickAt(out, 0, true) instanceof ClickEvent.Custom);
        assertTrue(clickAt(out, PW3.length() - 1, true) instanceof ClickEvent.Custom);
    }

    @Test
    void senderClickEventsWin() {
        Component command = Component.literal(PW3).withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/say hi")));
        assertSame(command, ChatCodeText.decorate(command, true, STYLER));
        Component copy = Component.literal("Code: ").append(Component.literal(PW3).withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(PW3))));
        FormattedText out = ChatCodeText.decorate(copy, true, STYLER);
        assertTrue(clickAt(out, "Code: ".length(), true) instanceof ClickEvent.Custom custom && custom.id().equals(COPY));
        Component otherCopy = Component.literal(PW3).withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard("anders")));
        assertSame(otherCopy, ChatCodeText.decorate(otherCopy, true, STYLER));
    }

    @Test
    void stylerDecidesPerCode() {
        Component line = Component.literal(PW3 + " " + PW2);
        ChatCodeText.Result result = ChatCodeText.apply(line, true, (found, copy) -> found.format().equals("PW2") ? null : STYLER.decorate(found, copy));
        assertEquals(1, result.codes().size());
        assertTrue(clickAt(result.text(), 0, true) instanceof ClickEvent.Custom);
        assertNull(clickAt(result.text(), PW3.length() + 1, true));
        assertSame(line, ChatCodeText.decorate(line, true, (found, copy) -> null));
    }

    @Test
    void invalidCodesGetOnlyAHover() {
        Component line = Component.literal(PW3);
        FormattedText out = ChatCodeText.decorate(line, true, (found, copy) -> new ChatCodeText.Decoration(0xF2665E, null,
                new HoverEvent.ShowText(Component.literal("Code ungültig"))));
        Style style = cells(out, true).getFirst().style();
        assertNull(style.getClickEvent());
        assertTrue(style.getHoverEvent() instanceof HoverEvent.ShowText text && text.value().getString().equals("Code ungültig"));
    }

    @Test
    void insertionsAndOtherStylesStay() {
        Component line = Component.literal("Alex").withStyle(style -> style.withInsertion("Alex").withItalic(true)).append(Component.literal(": " + PW3));
        FormattedText out = ChatCodeText.decorate(line, true, STYLER);
        assertOnlyCodesChanged(line, out, true);
        assertEquals("Alex", cells(out, true).getFirst().style().getInsertion());
    }
}
