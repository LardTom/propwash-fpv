package de.lardtom.propwash.build;

import java.util.EnumMap;
import java.util.Map;

public enum PaintPreset {
    CARBON_CLASSIC("carbon_classic", PaintSwatch.CARBON, PaintSwatch.BLACK, PaintSwatch.GUNMETAL, PaintSwatch.GRAY, PaintSwatch.GRAY,
            PaintSwatch.CARBON, PaintSwatch.BLACK, PaintSwatch.BLACK, PaintSwatch.GRAY, PaintSwatch.GUNMETAL),
    NEON("neon", PaintSwatch.CARBON, PaintSwatch.LIME, PaintSwatch.MAGENTA, PaintSwatch.YELLOW, PaintSwatch.LIGHT_BLUE,
            PaintSwatch.CARBON, PaintSwatch.MAGENTA, PaintSwatch.LIME, PaintSwatch.YELLOW, PaintSwatch.LIGHT_BLUE),
    RACE_TEAM("race_team", PaintSwatch.WHITE, PaintSwatch.RED, PaintSwatch.RED, PaintSwatch.RED, PaintSwatch.WHITE,
            PaintSwatch.CARBON, PaintSwatch.WHITE, PaintSwatch.RED, PaintSwatch.WHITE, PaintSwatch.RED),
    ARCTIC("arctic", PaintSwatch.ALUMINIUM, PaintSwatch.WHITE, PaintSwatch.ALUMINIUM, PaintSwatch.LIGHT_BLUE, PaintSwatch.WHITE,
            PaintSwatch.CARBON, PaintSwatch.WHITE, PaintSwatch.WHITE, PaintSwatch.LIGHT_BLUE, PaintSwatch.ALUMINIUM);

    private final String key;
    private final DronePaint paint;

    PaintPreset(String key, PaintSwatch frame, PaintSwatch tpu, PaintSwatch motors, PaintSwatch frontProps, PaintSwatch rearProps,
                PaintSwatch stack, PaintSwatch battery, PaintSwatch camera, PaintSwatch antenna, PaintSwatch accessories) {
        this.key = key;
        Map<PaintSlot, Integer> colors = new EnumMap<>(PaintSlot.class);
        colors.put(PaintSlot.FRAME, frame.rgb());
        colors.put(PaintSlot.TPU, tpu.rgb());
        colors.put(PaintSlot.MOTORS, motors.rgb());
        colors.put(PaintSlot.PROP_FRONT_LEFT, frontProps.rgb());
        colors.put(PaintSlot.PROP_FRONT_RIGHT, frontProps.rgb());
        colors.put(PaintSlot.PROP_REAR_LEFT, rearProps.rgb());
        colors.put(PaintSlot.PROP_REAR_RIGHT, rearProps.rgb());
        colors.put(PaintSlot.STACK, stack.rgb());
        colors.put(PaintSlot.BATTERY, battery.rgb());
        colors.put(PaintSlot.CAMERA, camera.rgb());
        colors.put(PaintSlot.ANTENNA, antenna.rgb());
        colors.put(PaintSlot.ACCESSORIES, accessories.rgb());
        this.paint = DronePaint.of(colors);
    }

    public String key() {
        return this.key;
    }

    public DronePaint paint() {
        return this.paint;
    }

    public String translationKey() {
        return "propwash.paint.preset." + this.key;
    }
}
