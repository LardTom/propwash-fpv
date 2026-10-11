package de.lardtom.propwash.api.part;

import net.minecraft.resources.Identifier;

public final class Mounts {
    public static final Identifier MOTOR_M08 = id("m08");
    public static final Identifier MOTOR_M14 = id("m14");
    public static final Identifier MOTOR_M22 = id("m22");
    public static final Identifier MOTOR_M28 = id("m28");
    public static final Identifier STACK_WHOOP_AIO = id("whoop_aio");
    public static final Identifier STACK_S20 = id("s20");
    public static final Identifier STACK_S30 = id("s30");
    public static final Identifier PROP_UNIVERSAL = id("universal");

    private Mounts() {
    }

    public static boolean propFits(Identifier motorPropMount, Identifier propMount) {
        return PROP_UNIVERSAL.equals(motorPropMount) || PROP_UNIVERSAL.equals(propMount) || motorPropMount.equals(propMount);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("propwash", path);
    }
}
