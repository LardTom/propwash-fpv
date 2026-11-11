package de.lardtom.propwash.race.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.scenes.GateMenuScene;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseElementItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateItem;
import de.lardtom.propwash.ui.HintMemory;
import de.lardtom.propwash.ui.client.FirstUseHints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class GatePlaceHint {
    public static final int WINDOW_TICKS = 30;
    public static final double RADIUS = 8.0;
    public static final double KNOWN_RADIUS = 48.0;
    public static final int PENDING_TICKS = 200;
    public static final JsonConfig<GateHintMemory.Worlds> WORLDS = JsonConfig.create("gate_hint", GateHintMemory.Worlds.class,
            GateHintMemory.Worlds::new, GateHintMemory::sanitize);

    private static final Set<Integer> KNOWN = new HashSet<>();
    private static boolean initialized;
    private static boolean muted;
    private static @Nullable String worldOverride;
    private static LivingEntity.@Nullable SwingDescription lastSwing;
    private static int ticks;
    private static int armedUntil = -1;
    private static int pendingUntil = -1;
    private static int detected;
    private static GateHintMemory.@Nullable Outcome lastOutcome;

    private GatePlaceHint() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        GuideScenes.register(GateMenuScene::create);
        ClientBus.TICK_END.register(GatePlaceHint::tick);
        ClientBus.DISCONNECT.register(minecraft -> reset());
    }

    static void tick(Minecraft minecraft) {
        ticks++;
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            reset();
            return;
        }
        if (pendingUntil >= 0) {
            if (ticks > pendingUntil) {
                pendingUntil = -1;
            } else if (minecraft.gui.screen() == null) {
                pendingUntil = -1;
                open(minecraft);
            }
        }
        if (armedUntil >= 0) {
            GateEntity placed = findNew(level, player);
            if (placed != null) {
                disarm();
                onPlaced(minecraft, placed);
            } else if (ticks > armedUntil) {
                disarm();
            }
        }
        LivingEntity.SwingDescription swing = player.getCurrentSwing();
        boolean swung = swing != null && swing != lastSwing && player.isSwinging();
        lastSwing = swing;
        boolean used = minecraft.gui.screen() == null && (swung || minecraft.options.keyUse.isDown());
        if (used && holdsGate(player)) {
            if (armedUntil < 0) {
                remember(level, player);
            }
            armedUntil = ticks + WINDOW_TICKS;
        }
    }

    public static boolean placesGate(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof GateItem item && !(item instanceof CourseElementItem);
    }

    static boolean holdsGate(LocalPlayer player) {
        return placesGate(player.getMainHandItem()) || placesGate(player.getOffhandItem());
    }

    private static boolean counts(GateEntity gate) {
        return !gate.isRemoved() && (gate.kind() == CourseElement.GATE || gate.kind() == CourseElement.FREEFORM);
    }

    private static void remember(ClientLevel level, LocalPlayer player) {
        KNOWN.clear();
        for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, player.getBoundingBox().inflate(KNOWN_RADIUS), gate -> true)) {
            KNOWN.add(gate.getId());
        }
    }

    private static @Nullable GateEntity findNew(ClientLevel level, LocalPlayer player) {
        AABB search = player.getBoundingBox().inflate(RADIUS + 24.0);
        double limit = RADIUS * RADIUS;
        for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, search, GatePlaceHint::counts)) {
            if (!KNOWN.contains(gate.getId()) && gate.getBoundingBox().distanceToSqr(player.getEyePosition()) <= limit) {
                return gate;
            }
        }
        return null;
    }

    private static void disarm() {
        armedUntil = -1;
        KNOWN.clear();
    }

    private static void onPlaced(Minecraft minecraft, GateEntity gate) {
        detected++;
        if (muted) {
            lastOutcome = null;
            return;
        }
        String player = FirstUseHints.playerKey();
        String world = world(minecraft);
        GateHintMemory.Outcome outcome = GateHintMemory.check(hints(), worlds(), player, world);
        lastOutcome = outcome;
        if (outcome != GateHintMemory.Outcome.SHOW) {
            return;
        }
        if (GateHintMemory.shown(worlds(), player, world)) {
            WORLDS.save();
        }
        Constants.LOG.info("Gate-Hinweis: erstes Gate ({}) in der Welt {}", gate.kind().name().toLowerCase(Locale.ROOT), world);
        if (minecraft.gui.screen() == null) {
            open(minecraft);
        } else {
            pendingUntil = ticks + PENDING_TICKS;
        }
    }

    private static void open(Minecraft minecraft) {
        minecraft.gui.setScreen(new GateHintScreen());
    }

    static HintMemory.Seen hints() {
        if (!FirstUseHints.CONFIG.isLoaded()) {
            FirstUseHints.CONFIG.load();
        }
        return FirstUseHints.CONFIG.get();
    }

    static GateHintMemory.Worlds worlds() {
        if (!WORLDS.isLoaded()) {
            WORLDS.load();
        }
        return WORLDS.get();
    }

    public static String world(Minecraft minecraft) {
        String override = worldOverride;
        return GateHintMemory.worldKey(override != null ? override : GhostStore.worldKey(minecraft));
    }

    public static void closed(GateHintMemory.Choice choice, boolean checked) {
        if (GateHintMemory.close(hints(), FirstUseHints.playerKey(), choice, checked)) {
            FirstUseHints.CONFIG.save();
        }
    }

    public static boolean never() {
        return GateHintMemory.never(hints(), FirstUseHints.playerKey());
    }

    public static boolean resetHints() {
        String player = FirstUseHints.playerKey();
        boolean hints = GateHintMemory.forgetHints(hints(), player);
        boolean worlds = GateHintMemory.forgetWorlds(worlds(), player);
        if (hints) {
            FirstUseHints.CONFIG.save();
        }
        if (worlds) {
            WORLDS.save();
        }
        return hints || worlds;
    }

    public static void mute(boolean value) {
        muted = value;
    }

    public static boolean muted() {
        return muted;
    }

    public static void worldOverride(@Nullable String world) {
        worldOverride = world;
    }

    public static int detected() {
        return detected;
    }

    public static GateHintMemory.@Nullable Outcome lastOutcome() {
        return lastOutcome;
    }

    public static boolean armed() {
        return armedUntil >= 0;
    }

    static void reset() {
        disarm();
        pendingUntil = -1;
        lastSwing = null;
    }
}
