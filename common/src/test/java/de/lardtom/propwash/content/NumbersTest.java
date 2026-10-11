package de.lardtom.propwash.content;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NumbersTest {
    private static String plain(Component component) {
        return assertInstanceOf(PlainTextContents.class, component.getContents()).text();
    }

    private static List<Object> decimal(Component component) {
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, component.getContents());
        assertEquals("propwash.content.number.decimal", contents.getKey());
        return Arrays.asList(contents.getArgs());
    }

    @Test
    void wholeNumbersStayPlain() {
        assertEquals("125", plain(Numbers.of(125.0, 1)));
        assertEquals("125", plain(Numbers.of(124.96, 1)));
        assertEquals("0", plain(Numbers.of(-0.01, 1)));
        assertEquals("1750", plain(Numbers.of(1750, 0)));
    }

    @Test
    void decimalsUseTheLocalizedSeparator() {
        assertEquals(List.of("4", "6"), decimal(Numbers.of(4.6, 1)));
        assertEquals(List.of("5", "1"), decimal(Numbers.of(130 / 25.4, 1)));
        assertEquals(List.of("-2", "5"), decimal(Numbers.of(-2.5, 2)));
        assertEquals(List.of("3", "05"), decimal(Numbers.of(3.05, 2)));
    }

    @Test
    void fixedKeepsTrailingZeros() {
        assertEquals(List.of("7", "0"), decimal(Numbers.fixed(7.0, 1)));
        assertEquals(List.of("3", "90"), decimal(Numbers.fixed(3.9, 2)));
        assertEquals("12", plain(Numbers.fixed(12.4, 0)));
    }

    @Test
    void minutesAreFormattedAsClock() {
        assertEquals("5:30", plain(Numbers.minutes(5.5)));
        assertEquals("0:07", plain(Numbers.minutes(7.0 / 60.0)));
        assertEquals("propwash.content.number.none",
                assertInstanceOf(TranslatableContents.class, Numbers.minutes(Double.NaN).getContents()).getKey());
    }
}
