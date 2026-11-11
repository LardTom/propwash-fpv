package de.lardtom.propwash.input.addon.client;

import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.input.net.AddonFeaturesS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Set;

public final class AddonServerFeatures {
    private static volatile Set<Identifier> switches = Set.of();
    private static volatile Set<Identifier> axes = Set.of();
    private static volatile Set<Identifier> collisionRules = Set.of();
    private static volatile boolean received;
    private static volatile int revision;

    private AddonServerFeatures() {
    }

    static void accept(AddonFeaturesS2C payload) {
        switches = Set.copyOf(payload.switches());
        axes = Set.copyOf(payload.axes());
        collisionRules = Set.copyOf(payload.collisionRules());
        received = true;
        revision++;
    }

    static void reset() {
        switches = Set.of();
        axes = Set.of();
        collisionRules = Set.of();
        received = false;
        revision++;
    }

    public static boolean received() {
        return received;
    }

    public static int revision() {
        return revision;
    }

    public static Set<Identifier> switches() {
        return switches;
    }

    public static Set<Identifier> axes() {
        return axes;
    }

    public static boolean knowsSwitch(Identifier id) {
        return switches.contains(id);
    }

    public static boolean knowsAxis(Identifier id) {
        return axes.contains(id);
    }

    public static boolean knowsCollisionRule(Identifier id) {
        return collisionRules.contains(id);
    }

    public static boolean inWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.level != null && minecraft.getConnection() != null;
    }

    public static boolean inactive(SwitchSpec spec) {
        return spec.serverSync() && inWorld() && !knowsSwitch(spec.id());
    }

    public static boolean inactive(AxisSpec spec) {
        return spec.serverSync() && inWorld() && !knowsAxis(spec.id());
    }
}
