package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.UiScaleSelfTests;
import de.lardtom.propwash.guide.client.GuideSelfTests;
import de.lardtom.propwash.guide.client.screen.GuideTab;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.guide.scenes.GateMenuScene;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.LayoutAudit.Finding;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.FirstUseHints;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class GateHintSelfTests {
    public static final String GROUP = "race: Gate-Hinweis";
    public static final String SHOTS = "bildschirmfotos: Gate-Hinweis";
    public static final String SECOND_WORLD = "sp_selbsttest_zweite_welt";
    public static final String THIRD_WORLD = "sp_selbsttest_dritte_welt";
    private static final int OFFSET_X = 52;
    private static final int OFFSET_Z = 36;
    private static final int AREA_X = 10;
    private static final int AREA_Z0 = -11;
    private static final int AREA_Z1 = 6;
    private static final int AREA_TOP = 8;
    private static final int WALL_X = 6;
    private static final int WALL_Z = -8;
    private static final int STEP_TICKS = 1200;
    private static final int WAIT_TICKS = 160;
    private static final int QUIET_TICKS = 40;
    private static final Spot FIRST = Spot.floor(-6, 0);
    private static final Spot SECOND = Spot.floor(0, 0);
    private static final Spot OTHER_WORLD = Spot.floor(6, 0);
    private static final Spot THIRD = Spot.floor(-6, -6);
    private static final Spot CHAPTER = Spot.floor(0, -6);
    private static final Spot FREEFORM = new Spot(WALL_X, WALL_Z, true);
    private static final AtomicReference<@Nullable Site> SITE = new AtomicReference<>();
    private static final AtomicReference<@Nullable Memory> SAVED = new AtomicReference<>();
    private static final int[] SMALL = {960, 720, 3};
    private static final int[] LARGE = {1920, 1080, 3};

    private record Site(BlockPos origin, Map<BlockPos, BlockState> saved, Vec3 back, float yaw, float pitch, ItemStack hand) {
    }

    private record Memory(@Nullable Map<String, Integer> hints, @Nullable List<String> worlds) {
    }

    private record Spot(int x, int z, boolean freeform) {
        static Spot floor(int x, int z) {
            return new Spot(x, z, false);
        }

        BlockPos aim(BlockPos o) {
            return this.freeform ? o.offset(this.x, 0, this.z) : o.offset(this.x, -1, this.z);
        }

        Vec3 look(BlockPos o) {
            BlockPos aim = this.aim(o);
            return this.freeform ? new Vec3(aim.getX() + 0.5, aim.getY() + 1.0, aim.getZ() + 0.75) : Vec3.atCenterOf(aim).add(0.0, 0.5, 0.0);
        }

        Vec3 stand(BlockPos o) {
            BlockPos aim = this.aim(o);
            return new Vec3(aim.getX() + (this.freeform ? 1.0 : 0.5), o.getY(), aim.getZ() + 3.5);
        }

        Item item() {
            return this.freeform ? RaceModule.FREEFORM_GATE.get() : RaceModule.GATE_MEDIUM.get();
        }

        String label() {
            return (this.freeform ? "Freiform-Gate" : "Gate") + " bei " + this.x + "/" + this.z;
        }
    }

    private GateHintSelfTests() {
    }

    public static void register() {
        GatePlaceHint.mute(true);
        SelfTest.clientStep(GROUP + " – Hinweis-Speicher leeren, erstes Gate in der Welt öffnet das Fenster", STEP_TICKS, GateHintSelfTests::firstStep);
        SelfTest.clientStep(GROUP + " – zweites Gate in derselben Welt zeigt kein Fenster", STEP_TICKS, GateHintSelfTests::secondStep);
        SelfTest.clientStep(GROUP + " – zweite Welt zeigt das Fenster, „Nicht wieder anzeigen“ gilt dann in keiner Welt", STEP_TICKS * 2,
                GateHintSelfTests::worldsStep);
        SelfTest.clientStep(GROUP + " – „Hinweise zurücksetzen“ im Tab Flugschule, „Kapitel öffnen“ zeigt gate_menu", STEP_TICKS * 2,
                GateHintSelfTests::chapterStep);
        SelfTest.clientStep(GROUP + " – Freiform-Gate löst ebenfalls aus", STEP_TICKS, GateHintSelfTests::freeformStep);
        SelfTest.clientStep(GROUP + " – aufräumen", 600, GateHintSelfTests::cleanupStep);
        if (Screenshots.enabled()) {
            Screenshots.step(SHOTS + " – Fenster", "gate_hint_window", 10, new WindowScene(LARGE));
            Screenshots.step(SHOTS + " – Fenster bei 320 × 240", "gate_hint_window_small", 10, new WindowScene(SMALL));
            Screenshots.step(SHOTS + " – Kapitel gate_menu, Seite Redstone", "flight_school_gate_menu", 30, chapterShot());
            SelfTest.switchStep(SHOTS + " – Sprache Deutsch", SHOTS + " – Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
            Screenshots.step(SHOTS + " – Fenster deutsch", "gate_hint_window_de", 10, new WindowScene(LARGE));
            Screenshots.step(SHOTS + " – Fenster bei 320 × 240 deutsch", "gate_hint_window_small_de", 10, new WindowScene(SMALL));
            Screenshots.step(SHOTS + " – Kapitel gate_menu, Seite Redstone deutsch", "flight_school_gate_menu_de", 30, chapterShot());
            SelfTest.clientStep(SHOTS + " – Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        }
    }

    public static List<UiScaleSelfTests.Case> uiScaleCases() {
        return List.of(new HintCase());
    }

    private static StepResult firstStep(SelfTest.StepContext ctx) {
        StepResult site = ensureSite(ctx);
        if (site != null) {
            return site;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!ctx.state().containsKey("cleared")) {
            remember();
            GatePlaceHint.mute(false);
            GatePlaceHint.worldOverride(null);
            forgetGateHint();
            ctx.state().put("cleared", true);
            if (GatePlaceHint.never() || GateHintMemory.seen(GatePlaceHint.worlds(), FirstUseHints.playerKey(), GatePlaceHint.world(mc))) {
                return StepResult.fail("Hinweis-Speicher nicht geleert");
            }
        }
        StepResult placed = place(ctx, "first", FIRST, true);
        if (placed.status() != StepResult.Status.PASS) {
            return placed;
        }
        if (!(mc.gui.screen() instanceof GateHintScreen hint)) {
            return StepResult.fail("kein Gate-Hinweis offen: " + mc.gui.screen());
        }
        String world = GatePlaceHint.world(mc);
        String problem = null;
        if (GatePlaceHint.lastOutcome() != GateHintMemory.Outcome.SHOW) {
            problem = "Ergebnis " + GatePlaceHint.lastOutcome() + " statt SHOW";
        } else if (!world.startsWith("sp_")) {
            problem = "Welt-Schlüssel im Einzelspieler nicht „sp_…“: " + world;
        } else if (!GateHintMemory.seen(GatePlaceHint.worlds(), FirstUseHints.playerKey(), world)) {
            problem = "Welt " + world + " nach dem Zeigen nicht gemerkt";
        } else if (hint.neverBox() == null || hint.checked() || hint.chapterButton() == null || hint.okButton() == null) {
            problem = "Fenster unvollständig: Haken " + (hint.neverBox() == null ? "fehlt" : hint.checked() ? "gesetzt" : "frei") + ", Knöpfe "
                    + hint.chapterButton() + " / " + hint.okButton();
        } else if (newGate(mc, FIRST, CourseElement.GATE) == null) {
            problem = "neues Gate am Client nicht gefunden";
        }
        hint.confirm();
        if (problem == null && GatePlaceHint.never()) {
            problem = "„Verstanden“ ohne Haken setzt „Nicht wieder anzeigen“";
        }
        if (problem == null) {
            ctx.log("Fenster " + ctx.state().get("first.window") + " Ticks nach dem Klick, Gate nach " + ctx.state().get("first.gate") + " Ticks, Welt " + world);
        }
        return StepResult.check(problem == null, problem == null ? "" : problem);
    }

    private static StepResult secondStep(SelfTest.StepContext ctx) {
        StepResult site = ensureSite(ctx);
        if (site != null) {
            return site;
        }
        if (!ctx.state().containsKey("ready")) {
            remember();
            GatePlaceHint.mute(false);
            GatePlaceHint.worldOverride(null);
            FirstUseHints.forget(GateHintMemory.HINT);
            GateHintMemory.shown(GatePlaceHint.worlds(), FirstUseHints.playerKey(), GatePlaceHint.world(Minecraft.getInstance()));
            ctx.state().put("ready", true);
        }
        StepResult placed = place(ctx, "second", SECOND, false);
        if (placed.status() != StepResult.Status.PASS) {
            return placed;
        }
        return StepResult.check(GatePlaceHint.lastOutcome() == GateHintMemory.Outcome.SEEN_HERE,
                "Ergebnis " + GatePlaceHint.lastOutcome() + " statt SEEN_HERE");
    }

    private static StepResult worldsStep(SelfTest.StepContext ctx) {
        StepResult site = ensureSite(ctx);
        if (site != null) {
            return site;
        }
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey("ready")) {
            remember();
            GatePlaceHint.mute(false);
            FirstUseHints.forget(GateHintMemory.HINT);
            GateHintMemory.shown(GatePlaceHint.worlds(), FirstUseHints.playerKey(), GatePlaceHint.world(mc));
            GatePlaceHint.worldOverride(SECOND_WORLD);
            state.put("original", GateHintMemory.worldKey(GhostStore.worldKey(mc)));
            state.put("ready", true);
        }
        if (!state.containsKey("never")) {
            StepResult placed = place(ctx, "other", OTHER_WORLD, true);
            if (placed.status() != StepResult.Status.PASS) {
                return placed;
            }
            if (!(mc.gui.screen() instanceof GateHintScreen hint) || hint.neverBox() == null || hint.okButton() == null) {
                return StepResult.fail("Fenster in der zweiten Welt unvollständig: " + mc.gui.screen());
            }
            if (GatePlaceHint.lastOutcome() != GateHintMemory.Outcome.SHOW) {
                return StepResult.fail("zweite Welt: Ergebnis " + GatePlaceHint.lastOutcome() + " statt SHOW");
            }
            if (!click(hint, hint.neverBox()) || !hint.checked()) {
                hint.confirm();
                return StepResult.fail("Klick auf „Nicht wieder anzeigen“ setzt den Haken nicht");
            }
            hint.okButton().press();
            if (mc.gui.screen() != null) {
                return StepResult.fail("„Verstanden“ schließt das Fenster nicht: " + mc.gui.screen());
            }
            if (!GatePlaceHint.never()) {
                return StepResult.fail("Haken „Nicht wieder anzeigen“ nicht gespeichert");
            }
            GatePlaceHint.worldOverride(THIRD_WORLD);
            state.put("never", true);
            return StepResult.pending();
        }
        StepResult placed = place(ctx, "third", THIRD, false);
        if (placed.status() != StepResult.Status.PASS) {
            return placed;
        }
        GatePlaceHint.worldOverride(null);
        String original = (String) state.get("original");
        GateHintMemory.Outcome back = GateHintMemory.check(GatePlaceHint.hints(), GatePlaceHint.worlds(), FirstUseHints.playerKey(), "sp_noch_nie_besucht");
        if (GatePlaceHint.lastOutcome() != GateHintMemory.Outcome.NEVER) {
            return StepResult.fail("dritte Welt nach „Nicht wieder anzeigen“: Ergebnis " + GatePlaceHint.lastOutcome() + " statt NEVER");
        }
        if (back != GateHintMemory.Outcome.NEVER || GateHintMemory.check(GatePlaceHint.hints(), GatePlaceHint.worlds(), FirstUseHints.playerKey(), original)
                != GateHintMemory.Outcome.NEVER) {
            return StepResult.fail("„Nicht wieder anzeigen“ gilt nicht in jeder Welt: " + back);
        }
        ctx.log("Welten: " + original + " gesehen, " + SECOND_WORLD + " mit Fenster, danach " + THIRD_WORLD + " ohne Fenster");
        return StepResult.pass();
    }

    private static StepResult chapterStep(SelfTest.StepContext ctx) {
        StepResult site = ensureSite(ctx);
        if (site != null) {
            return site;
        }
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey("ready")) {
            remember();
            GatePlaceHint.mute(false);
            GatePlaceHint.worldOverride(null);
            FirstUseHints.learned(GateHintMemory.HINT, GateHintMemory.NEVER);
            GateHintMemory.shown(GatePlaceHint.worlds(), FirstUseHints.playerKey(), GatePlaceHint.world(mc));
            GatePlaceHint.WORLDS.save();
            state.put("ready", ctx.tick());
            ConfigTabs.open(null, GuideTab.ID);
            return StepResult.pending();
        }
        if (!state.containsKey("reset")) {
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof GuideTab tab)) {
                return ctx.tick() - (int) state.get("ready") > WAIT_TICKS ? StepResult.fail("Tab Flugschule öffnet nicht: " + mc.gui.screen())
                        : StepResult.pending();
            }
            FlatButton reset = tab.resetHintsButton();
            if (reset == null || !reset.visible || !reset.active) {
                return StepResult.fail("Knopf „Hinweise zurücksetzen“ fehlt im Tab Flugschule");
            }
            ScreenRectangleCheck where = ScreenRectangleCheck.of(screen, reset);
            if (where != null) {
                return StepResult.fail("Knopf „Hinweise zurücksetzen“ " + where.problem());
            }
            reset.press();
            boolean cleared = !GatePlaceHint.never() && !GateHintMemory.seen(GatePlaceHint.worlds(), FirstUseHints.playerKey(), GatePlaceHint.world(mc));
            screen.discardAndClose();
            if (!cleared) {
                return StepResult.fail("„Hinweise zurücksetzen“ leert den Hinweis-Speicher nicht");
            }
            state.put("reset", true);
            return StepResult.pending();
        }
        if (!state.containsKey("chapter")) {
            StepResult placed = place(ctx, "chapter", CHAPTER, true);
            if (placed.status() != StepResult.Status.PASS) {
                return placed;
            }
            if (!(mc.gui.screen() instanceof GateHintScreen hint) || hint.chapterButton() == null) {
                return StepResult.fail("kein Fenster nach dem Zurücksetzen: " + mc.gui.screen());
            }
            hint.chapterButton().press();
            state.put("chapter", ctx.tick());
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof SceneScreen scene)) {
            return ctx.tick() - (int) state.get("chapter") > WAIT_TICKS ? StepResult.fail("Flugschule öffnet nicht: " + mc.gui.screen()) : StepResult.pending();
        }
        String id = scene.scene().id();
        boolean never = GatePlaceHint.never();
        mc.gui.setScreen(null);
        if (!GateMenuScene.ID.equals(id)) {
            return StepResult.fail("Flugschule zeigt " + id + " statt " + GateMenuScene.ID);
        }
        return StepResult.check(never, "„Kapitel öffnen“ setzt „Nicht wieder anzeigen“ nicht");
    }

    private static StepResult freeformStep(SelfTest.StepContext ctx) {
        StepResult site = ensureSite(ctx);
        if (site != null) {
            return site;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!ctx.state().containsKey("ready")) {
            remember();
            GatePlaceHint.mute(false);
            GatePlaceHint.worldOverride(null);
            forgetGateHint();
            ctx.state().put("ready", true);
        }
        StepResult placed = place(ctx, "freeform", FREEFORM, true);
        if (placed.status() != StepResult.Status.PASS) {
            return placed;
        }
        if (!(mc.gui.screen() instanceof GateHintScreen hint)) {
            return StepResult.fail("kein Fenster nach dem Freiform-Gate: " + mc.gui.screen());
        }
        hint.confirm();
        GateEntity gate = newGate(mc, FREEFORM, CourseElement.FREEFORM);
        return StepResult.check(gate != null && GatePlaceHint.lastOutcome() == GateHintMemory.Outcome.SHOW,
                "Freiform-Gate " + (gate == null ? "nicht gefunden" : "gefunden") + ", Ergebnis " + GatePlaceHint.lastOutcome());
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (mc.gui.screen() instanceof GateHintScreen || mc.gui.screen() instanceof SceneScreen) {
                mc.gui.setScreen(null);
            }
            GatePlaceHint.mute(true);
            GatePlaceHint.worldOverride(null);
            restoreMemory();
            Site site = SITE.getAndSet(null);
            if (site == null) {
                return StepResult.pass();
            }
            ctx.state().put("future", ctx.onServer(server -> teardown(server, site)));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get("future");
        if (future == null) {
            return StepResult.pass();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen: " + future.exceptionNow());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static void remember() {
        if (SAVED.get() != null) {
            return;
        }
        String player = FirstUseHints.playerKey();
        Map<String, Integer> hints = GatePlaceHint.hints().players.get(player);
        List<String> worlds = GatePlaceHint.worlds().players.get(player);
        SAVED.set(new Memory(hints == null ? null : new LinkedHashMap<>(hints), worlds == null ? null : new ArrayList<>(worlds)));
    }

    private static void restoreMemory() {
        Memory memory = SAVED.getAndSet(null);
        if (memory == null) {
            return;
        }
        String player = FirstUseHints.playerKey();
        GatePlaceHint.hints().players.remove(player);
        if (memory.hints() != null) {
            GatePlaceHint.hints().players.put(player, new LinkedHashMap<>(memory.hints()));
        }
        FirstUseHints.CONFIG.save();
        GatePlaceHint.worlds().players.remove(player);
        if (memory.worlds() != null) {
            GatePlaceHint.worlds().players.put(player, new ArrayList<>(memory.worlds()));
        }
        GatePlaceHint.WORLDS.save();
    }

    private static void forgetGateHint() {
        FirstUseHints.forget(GateHintMemory.HINT);
        if (GateHintMemory.forgetWorlds(GatePlaceHint.worlds(), FirstUseHints.playerKey())) {
            GatePlaceHint.WORLDS.save();
        }
    }

    private static @Nullable StepResult ensureSite(SelfTest.StepContext ctx) {
        if (SITE.get() != null && !ctx.state().containsKey("site")) {
            return null;
        }
        if (!ctx.state().containsKey("site")) {
            ctx.state().put("site", ctx.onServer(GateHintSelfTests::build));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get("site");
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Testplatz: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove("site");
        return SITE.get() == null ? StepResult.fail("Testplatz nicht angelegt") : null;
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static AABB area(BlockPos o) {
        return new AABB(Vec3.atLowerCornerOf(o.offset(-AREA_X - 2, -2, AREA_Z0 - 2)), Vec3.atLowerCornerOf(o.offset(AREA_X + 3, AREA_TOP + 3, AREA_Z1 + 3)));
    }

    private static void build(MinecraftServer server) {
        if (SITE.get() != null) {
            return;
        }
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        int x = player.getBlockX() + OFFSET_X;
        int z = player.getBlockZ() + OFFSET_Z;
        level.getChunk(x >> 4, z >> 4);
        BlockPos o = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(-AREA_X, -1, AREA_Z0), o.offset(AREA_X, AREA_TOP, AREA_Z1))) {
            saved.put(pos.immutable(), level.getBlockState(pos));
        }
        level.getEntitiesOfClass(GateEntity.class, area(o), gate -> true).forEach(Entity::discard);
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : saved.keySet()) {
            level.setBlock(pos, pos.getY() == o.getY() - 1 ? floor : air, Block.UPDATE_CLIENTS);
        }
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        for (int dx = WALL_X - 2; dx <= WALL_X + 3; dx++) {
            for (int dy = 0; dy <= 3; dy++) {
                boolean opening = dx >= WALL_X && dx <= WALL_X + 1 && dy >= 1 && dy <= 2;
                level.setBlock(o.offset(dx, dy, WALL_Z), opening ? air : wall, Block.UPDATE_CLIENTS);
            }
        }
        SITE.set(new Site(o, saved, player.position(), player.getYRot(), player.getXRot(), player.getMainHandItem().copy()));
    }

    private static void teardown(MinecraftServer server, Site site) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        level.getEntitiesOfClass(GateEntity.class, area(site.origin()), gate -> true).forEach(Entity::discard);
        for (Map.Entry<BlockPos, BlockState> entry : site.saved().entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, site.hand());
        player.teleportTo(level, site.back().x, site.back().y, site.back().z, Set.of(), site.yaw(), site.pitch(), true);
    }

    private static void stand(MinecraftServer server, Site site, Spot spot) {
        ServerPlayer player = player(server);
        Vec3 stand = spot.stand(site.origin());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(spot.item(), 16));
        player.connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
        player.teleportTo(player.level(), stand.x, stand.y, stand.z, Set.of(), 180.0F, 30.0F, true);
        player.resetFallDistance();
    }

    private static StepResult place(SelfTest.StepContext ctx, String key, Spot spot, boolean window) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        Site site = SITE.get();
        LocalPlayer player = mc.player;
        if (site == null || player == null) {
            return StepResult.fail("kein Testplatz oder Spieler");
        }
        int phase = (int) state.getOrDefault(key + ".phase", 0);
        int since = (int) state.getOrDefault(key + ".since", ctx.tick());
        int waited = ctx.tick() - since;
        switch (phase) {
            case 0 -> {
                state.put(key + ".future", ctx.onServer(server -> stand(server, site, spot)));
                next(ctx, key, 1);
                return StepResult.pending();
            }
            case 1 -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(key + ".future");
                if (future.isCompletedExceptionally()) {
                    return StepResult.fail(spot.label() + ": " + future.exceptionNow());
                }
                boolean there = future.isDone() && player.position().distanceTo(spot.stand(site.origin())) < 0.3
                        && player.getMainHandItem().is(spot.item()) && mc.gui.screen() == null;
                if (!there) {
                    return waited > WAIT_TICKS ? StepResult.fail(spot.label() + ": Spieler kommt nicht an (" + player.position() + ", Hand "
                            + player.getMainHandItem() + ", Bildschirm " + mc.gui.screen() + ")") : StepResult.pending();
                }
                lookAt(player, spot.look(site.origin()));
                next(ctx, key, 2);
                return StepResult.pending();
            }
            case 2 -> {
                lookAt(player, spot.look(site.origin()));
                BlockPos aim = spot.aim(site.origin());
                boolean aimed = mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(aim)
                        && hit.getDirection() == Direction.UP;
                if (!aimed) {
                    return waited > 40 ? StepResult.fail(spot.label() + ": Fadenkreuz trifft " + describe(mc.hitResult) + " statt " + aim.toShortString())
                            : StepResult.pending();
                }
                state.put(key + ".detected", GatePlaceHint.detected());
                state.put(key + ".click", ctx.tick());
                KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
                next(ctx, key, 3);
                return StepResult.pending();
            }
            case 3 -> {
                if (GatePlaceHint.detected() <= (int) state.get(key + ".detected")) {
                    return waited > WAIT_TICKS ? StepResult.fail(spot.label() + ": kein neues Gate erkannt (Gates in der Nähe: " + gatesNear(mc, spot)
                            + ", Bildschirm " + mc.gui.screen() + ")") : StepResult.pending();
                }
                state.put(key + ".gate", ctx.tick() - (int) state.get(key + ".click"));
                next(ctx, key, 4);
                return StepResult.pending();
            }
            default -> {
                boolean open = mc.gui.screen() instanceof GateHintScreen;
                if (window) {
                    if (open) {
                        state.put(key + ".window", ctx.tick() - (int) state.get(key + ".click"));
                        return StepResult.pass();
                    }
                    return waited > QUIET_TICKS ? StepResult.fail(spot.label() + ": kein Fenster " + waited + " Ticks nach dem Gate (Ergebnis "
                            + GatePlaceHint.lastOutcome() + ", Bildschirm " + mc.gui.screen() + ")") : StepResult.pending();
                }
                if (open) {
                    mc.gui.setScreen(null);
                    return StepResult.fail(spot.label() + ": Fenster erscheint, obwohl es nicht soll (Ergebnis " + GatePlaceHint.lastOutcome() + ")");
                }
                return waited > QUIET_TICKS ? StepResult.pass() : StepResult.pending();
            }
        }
    }

    private static void next(SelfTest.StepContext ctx, String key, int phase) {
        ctx.state().put(key + ".phase", phase);
        ctx.state().put(key + ".since", ctx.tick());
    }

    private static String describe(@Nullable HitResult hit) {
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            return block.getBlockPos().toShortString() + " " + block.getDirection();
        }
        return hit == null ? "nichts" : hit.getType().name();
    }

    private static int gatesNear(Minecraft mc, Spot spot) {
        Site site = SITE.get();
        if (mc.level == null || site == null) {
            return -1;
        }
        return mc.level.getEntitiesOfClass(GateEntity.class, new AABB(spot.aim(site.origin())).inflate(8.0), gate -> true).size();
    }

    private static @Nullable GateEntity newGate(Minecraft mc, Spot spot, CourseElement kind) {
        Site site = SITE.get();
        if (mc.level == null || site == null) {
            return null;
        }
        List<GateEntity> gates = mc.level.getEntitiesOfClass(GateEntity.class, new AABB(spot.aim(site.origin())).inflate(4.0), gate -> gate.kind() == kind);
        return gates.isEmpty() ? null : gates.getFirst();
    }

    private static void lookAt(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
    }

    private static boolean click(Screen screen, @Nullable AbstractWidget widget) {
        if (widget == null) {
            return false;
        }
        double x = widget.getX() + Math.min(widget.getWidth() / 2.0, 6.0);
        double y = widget.getY() + widget.getHeight() / 2.0;
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        boolean handled = screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        return handled;
    }

    private record ScreenRectangleCheck(String problem) {
        static @Nullable ScreenRectangleCheck of(Screen screen, AbstractWidget widget) {
            if (widget.getX() < 0 || widget.getY() < 0 || widget.getX() + widget.getWidth() > screen.width
                    || widget.getY() + widget.getHeight() > screen.height) {
                return new ScreenRectangleCheck("liegt außerhalb des Fensters: " + widget.getX() + "/" + widget.getY());
            }
            return null;
        }
    }

    private static Screenshots.Scene chapterShot() {
        return new SizedScene(LARGE, GuideSelfTests.sceneShot(GateMenuScene.ID, 2, GateMenuScene.PASS_AT + 6.0F));
    }

    private static final class SizedScene implements Screenshots.Scene {
        private final int[] size;
        private final Screenshots.Scene inner;
        private int @Nullable [] saved;
        private int asked = -1;

        private SizedScene(int[] size, Screenshots.Scene inner) {
            this.size = size;
            this.inner = inner;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Window window = mc.getWindow();
            if (this.saved == null) {
                this.saved = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
                float density = Math.max(1.0F, window.getPixelDensity());
                window.setWindowed(Math.round(this.size[0] / density), Math.round(this.size[1] / density));
                this.asked = ctx.tick();
                return StepResult.pending();
            }
            mc.options.guiScale().set(this.size[2]);
            mc.resizeGui();
            int wantWidth = this.size[0] / this.size[2];
            int wantHeight = this.size[1] / this.size[2];
            if (window.getGuiScaledWidth() != wantWidth || window.getGuiScaledHeight() != wantHeight) {
                return ctx.tick() - this.asked > 200 ? StepResult.fail("GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + " statt "
                        + wantWidth + "x" + wantHeight) : StepResult.pending();
            }
            return this.inner.prepare(ctx);
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            this.inner.hold(ctx);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            this.inner.finish(ctx);
            Minecraft mc = Minecraft.getInstance();
            int[] previous = this.saved;
            if (previous != null) {
                mc.options.guiScale().set(previous[2]);
                mc.getWindow().setWindowed(previous[0], previous[1]);
                mc.resizeGui();
                this.saved = null;
            }
            this.asked = -1;
        }
    }

    private static final class WindowScene implements Screenshots.Scene {
        private final SizedScene sized;

        private WindowScene(int[] size) {
            this.sized = new SizedScene(size, new Screenshots.Scene() {
                private int opened = -1;
                private boolean marker;

                @Override
                public StepResult prepare(SelfTest.StepContext ctx) {
                    Minecraft mc = Minecraft.getInstance();
                    if (this.opened < 0) {
                        this.marker = Screenshots.markerHidden();
                        Screenshots.hideMarker(true);
                        mc.gui.setScreen(new GateHintScreen());
                        this.opened = ctx.tick();
                        return StepResult.pending();
                    }
                    return mc.gui.screen() instanceof GateHintScreen && ctx.tick() - this.opened > 3 ? StepResult.pass() : StepResult.pending();
                }

                @Override
                public void hold(SelfTest.StepContext ctx) {
                    Screenshots.parkMouse(Minecraft.getInstance());
                }

                @Override
                public void finish(SelfTest.StepContext ctx) {
                    Minecraft.getInstance().gui.setScreen(null);
                    if (this.opened >= 0) {
                        Screenshots.hideMarker(this.marker);
                    }
                    this.opened = -1;
                }
            });
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            return this.sized.prepare(ctx);
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            this.sized.hold(ctx);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            this.sized.finish(ctx);
        }
    }

    private static final class HintCase implements UiScaleSelfTests.Case {
        @Override
        public String name() {
            return "Gate-Hinweis";
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                mc.gui.setScreen(new GateHintScreen());
                return StepResult.pending();
            }
            return mc.gui.screen() instanceof GateHintScreen && ctx.tick() >= 3 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public List<Finding> audit(Minecraft mc, Screen screen) {
            List<Finding> findings = new ArrayList<>(LayoutProbe.audit(mc, screen));
            if (!(screen instanceof GateHintScreen hint)) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, name(), "Fenster nicht offen"));
                return findings;
            }
            List<@Nullable AbstractWidget> widgets = new ArrayList<>();
            widgets.add(hint.chapterButton());
            widgets.add(hint.okButton());
            widgets.add(hint.neverBox());
            for (AbstractWidget widget : widgets) {
                if (widget == null) {
                    findings.add(new Finding(LayoutAudit.Kind.HIDDEN, name(), "Knopf oder Haken fehlt"));
                    continue;
                }
                ScreenRectangleCheck outside = ScreenRectangleCheck.of(screen, widget);
                if (outside != null) {
                    findings.add(new Finding(LayoutAudit.Kind.OFFSCREEN, name(), "„" + widget.getMessage().getString() + "“ " + outside.problem()));
                }
                if (widget instanceof FlatButton button && mc.font.width(button.getMessage()) > button.getWidth() - 4) {
                    findings.add(new Finding(LayoutAudit.Kind.CUT, name(), "„" + button.getMessage().getString() + "“ passt nicht in " + button.getWidth()
                            + " GUI-Pixel"));
                }
            }
            return findings;
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }
}
