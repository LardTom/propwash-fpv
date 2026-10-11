package de.lardtom.propwash.build;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DronePaintTest {
    private static final DronePaint SAMPLE = DronePaint.DEFAULT.with(PaintSlot.FRAME, 0x2A2C30).with(PaintSlot.PROP_REAR_LEFT, 0xFF8800)
            .with(PaintSlot.CAMERA, PaintSwatch.LIME.rgb());

    @Test
    void emptyPaintIsTheDefault() {
        assertSame(DronePaint.DEFAULT, DronePaint.of(Map.of()));
        assertTrue(DronePaint.DEFAULT.isDefault());
        assertSame(DronePaint.DEFAULT, SAMPLE.without(PaintSlot.FRAME).without(PaintSlot.PROP_REAR_LEFT).without(PaintSlot.CAMERA));
    }

    @Test
    void colorsAreMaskedToRgb() {
        DronePaint paint = DronePaint.DEFAULT.with(PaintSlot.MOTORS, 0xFF123456);
        assertEquals(OptionalInt.of(0x123456), paint.color(PaintSlot.MOTORS));
    }

    @Test
    void propsCanBePaintedTogetherAndIndividually() {
        DronePaint all = DronePaint.DEFAULT.withAllProps(0x00FF00);
        for (int i = 0; i < PaintSlot.PROPS; i++) {
            assertEquals(OptionalInt.of(0x00FF00), all.color(PaintSlot.prop(i)));
            assertEquals(OptionalInt.of(0x00FF00), all.color(PaintChannel.PROP, i));
        }
        DronePaint one = all.with(PaintSlot.PROP_FRONT_RIGHT, 0xFF0000);
        assertEquals(OptionalInt.of(0xFF0000), one.color(PaintChannel.PROP, 1));
        assertEquals(OptionalInt.of(0x00FF00), one.color(PaintChannel.PROP, 0));
        assertTrue(one.withoutProps().isDefault());
    }

    @Test
    void clipboardTextRoundTrips() {
        String text = SAMPLE.toText();
        assertTrue(text.startsWith(DronePaint.CLIPBOARD_PREFIX));
        assertEquals(Optional.of(SAMPLE), DronePaint.fromText(text));
        assertEquals(Optional.of(DronePaint.DEFAULT), DronePaint.fromText(DronePaint.DEFAULT.toText()));
        assertEquals(Optional.of(SAMPLE), DronePaint.fromText("  " + text.toUpperCase().replace("PROPWASH-PAINT:", "propwash-paint:") + "\n"));
    }

    @Test
    void brokenClipboardTextIsRejected() {
        assertFalse(DronePaint.fromText(null).isPresent());
        assertFalse(DronePaint.fromText("hallo").isPresent());
        assertFalse(DronePaint.fromText("propwash-paint:frame=#12345").isPresent());
        assertFalse(DronePaint.fromText("propwash-paint:wings=#123456").isPresent());
        assertFalse(DronePaint.fromText("propwash-paint:frame").isPresent());
        assertFalse(DronePaint.fromText("propwash-paint:" + "x".repeat(DronePaint.MAX_TEXT_LENGTH)).isPresent());
    }

    @Test
    void hexParsing() {
        assertEquals(OptionalInt.of(0xFF8800), DronePaint.parseHex("#ff8800"));
        assertEquals(OptionalInt.of(0xFF8800), DronePaint.parseHex("FF8800"));
        assertEquals(OptionalInt.of(0xFF8800), DronePaint.parseHex("#F80"));
        assertTrue(DronePaint.parseHex("#GG0000").isEmpty());
        assertTrue(DronePaint.parseHex("").isEmpty());
        assertEquals("#0a0b0c", DronePaint.hex(0x0A0B0C));
    }

    @Test
    void everyChannelHasASlotAndEveryPropPositionIsDistinct() {
        for (PaintChannel channel : PaintChannel.values()) {
            assertEquals(channel, PaintSlot.of(channel, 0).channel());
        }
        for (int i = 0; i < PaintSlot.PROPS; i++) {
            assertEquals(i, PaintSlot.prop(i).propPosition());
        }
        assertEquals(12, PaintSlot.COUNT);
    }

    @Test
    void tintReproducesTheColourAtTheReferenceGrey() {
        for (PaintChannel channel : PaintChannel.values()) {
            int tint = channel.tint(0x404040);
            int grey = channel.reference();
            int shown = ((tint >> 16) & 0xFF) * grey / 255;
            assertEquals(0x40, shown, 1.0);
            assertEquals(0xFFFFFF, channel.tint(0xFFFFFF));
        }
    }

    @Test
    void presetsPaintEverySlotWithPaletteColours() {
        for (PaintPreset preset : PaintPreset.values()) {
            assertEquals(PaintSlot.COUNT, preset.paint().count());
            for (int color : preset.paint().colors().values()) {
                assertTrue(PaintSwatch.byColor(color).isPresent(), preset + " nutzt eine Farbe außerhalb der Palette");
            }
        }
    }

    @Test
    void everyDyeSwatchMatchesOneVanillaDye() {
        assertEquals(16, PaintSwatch.dyes().size());
        assertEquals(5, PaintSwatch.finishes().size());
        for (PaintSwatch swatch : PaintSwatch.values()) {
            assertTrue(PaintSwatch.byDye(swatch.dye()).isPresent(), swatch + " kostet keinen bekannten Farbstoff");
        }
    }
}
