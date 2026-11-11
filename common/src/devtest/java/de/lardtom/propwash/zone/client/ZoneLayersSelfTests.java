package de.lardtom.propwash.zone.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.server.WorldRuleText;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneCheck;
import de.lardtom.propwash.zone.ZoneData;
import de.lardtom.propwash.zone.ZoneDraft;
import de.lardtom.propwash.zone.ZoneModule;
import de.lardtom.propwash.zone.ZoneResolution;
import de.lardtom.propwash.zone.ZoneResolver;
import de.lardtom.propwash.zone.ZoneRuleMode;
import de.lardtom.propwash.zone.ZoneServer;
import de.lardtom.propwash.zone.ZoneShape;
import de.lardtom.propwash.zone.ZoneText;
import de.lardtom.propwash.zone.ZoneView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class ZoneLayersSelfTests {
    static final String PREFIX = ZonesOpenSelfTests.PREFIX;
    static final String SCROLL_STEP = "Zonen-Editor: Mausrad über dem Menü scrollt die Liste mit Scrollbalken, die untersten Regeln sind erreichbar (Befund PATTY-1)";
    static final String OUTLINE_STEP = "Zonen-Umrisse nur mit Zonen-Markierer oder Kurs-Werkzeug in der Hand, überlappende Zonen nur mit Kanten (Befund PATTY-1)";
    static final String FLIGHT_STEP = "Zonen-Umrisse im Flug: Standard „Mit Werkzeug oder im Flug“ zeigt sie in FPV und Sichtflug, zu Fuß ohne Werkzeug keine, F1 blendet aus (Befund FB1-8)";
    static final String NESTED_STEP = "verschachtelte Zonen: „Weltwert erzwingen“ hebt die äußere Schwerelosigkeit auf, Editor mit drei Zuständen, OSD nennt die Quelle (Befund PATTY-1)";
    static final String OUTER = "zones-open Kurs";
    static final String INNER = "zones-open Passage";
    static final int GUI_HEIGHT = 240;
    static final int SPOT_OFFSET = 220;
    static final int SPOT_LIFT = 30;
    static final int PLATFORM = 9;
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String MARK = "mark";
    private static final String SCALE = "scale";
    private static final String RESULT_KEY = "result";
    private static final String START = "start";
    private static final int AHEAD = 22;
    private static final AtomicReference<@Nullable String> RESULT = new AtomicReference<>();
    private static final AtomicReference<int @Nullable []> SPOT = new AtomicReference<>();
    private static final AtomicReference<double @Nullable []> HOME = new AtomicReference<>();

    private ZoneLayersSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + SCROLL_STEP, 600, ZoneLayersSelfTests::scrollStep);
        SelfTest.clientStep(PREFIX + OUTLINE_STEP, 900, ZoneLayersSelfTests::outlineStep);
        SelfTest.clientStep(PREFIX + FLIGHT_STEP, 1200, ZoneLayersSelfTests::flightStep);
        SelfTest.clientStep(PREFIX + NESTED_STEP, 900, ZoneLayersSelfTests::nestedStep);
    }

    public static void registerScreenshots(boolean german) {
        String suffix = german ? "_de" : "";
        String label = german ? " deutsch" : "";
        Screenshots.step(PREFIX + "Bildschirmfoto Zonen-Editor gescrollt mit Scrollbalken und drei Zuständen" + label, "zones_open_editor_scroll" + suffix, 10,
                new ScrollScene());
        Screenshots.step(PREFIX + "Bildschirmfoto verschachtelte Zonen mit Markierer: aktive Zone hervorgehoben, äußere nur Kanten" + label,
                "zones_open_nested" + suffix, 20, new NestedScene());
        Screenshots.step(PREFIX + "Bildschirmfoto FPV-Flugbild mit Zonen-Umriss ohne Werkzeug" + label, "zones_open_flight_fpv" + suffix, 20, new FlightScene());
    }

    private static @Nullable StepResult waitFuture(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Schritt fehlgeschlagen: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        String result = RESULT.getAndSet(null);
        return result == null ? null : StepResult.fail(result);
    }

    private static void server(SelfTest.StepContext ctx, Function<MinecraftServer, @Nullable String> action) {
        RESULT.set(null);
        ctx.state().put(FUTURE, ctx.onServer(server -> RESULT.set(action.apply(server))));
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx) {
        ctx.state().put(PHASE, phase(ctx) + 1);
        ctx.state().put(MARK, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    static @Nullable Zone client(String name) {
        for (Zone zone : ClientZones.INSTANCE.zones()) {
            if (ZoneCheck.sameName(zone.name(), name)) {
                return zone;
            }
        }
        return null;
    }

    static void removeTestZones(MinecraftServer server) {
        ServerPlayer player = ZonesOpenSelfTests.player(server);
        for (String name : List.of(OUTER, INNER)) {
            ZoneData.get(server).byName(ZoneServer.dimension(player.level()), name)
                    .ifPresent(zone -> ZoneServer.delete(server, zone.id(), Component.literal("Selbsttest"), "Selbsttest"));
        }
    }

    static void spot(MinecraftServer server) {
        ServerPlayer player = ZonesOpenSelfTests.player(server);
        ServerLevel level = player.level();
        if (HOME.get() == null) {
            HOME.set(new double[]{player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()});
        }
        int[] spot = SPOT.get();
        if (spot == null) {
            double[] home = HOME.get();
            int x = (int) Math.floor(home[0]);
            int z = (int) Math.floor(home[2]) + SPOT_OFFSET;
            for (int cx = (x - 16) >> 4; cx <= (x + 16) >> 4; cx++) {
                for (int cz = (z - 16) >> 4; cz <= (z + 16) >> 4; cz++) {
                    level.getChunk(cx, cz);
                }
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + SPOT_LIFT;
            for (int dx = -PLATFORM / 2; dx <= PLATFORM / 2; dx++) {
                for (int dz = -PLATFORM / 2; dz <= PLATFORM / 2; dz++) {
                    level.setBlockAndUpdate(new BlockPos(x + dx, y, z + dz), Blocks.SMOOTH_STONE.defaultBlockState());
                    for (int dy = 1; dy <= 6; dy++) {
                        level.setBlockAndUpdate(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            spot = new int[]{x, y, z};
            SPOT.set(spot);
        }
        player.teleportTo(level, spot[0] + 0.5, spot[1] + 1.0, spot[2] + 0.5, Set.of(), 0.0F, 20.0F, true);
    }

    static void leaveSpot(MinecraftServer server) {
        ServerPlayer player = ZonesOpenSelfTests.player(server);
        ServerLevel level = player.level();
        int[] spot = SPOT.getAndSet(null);
        if (spot != null) {
            for (int dx = -PLATFORM / 2; dx <= PLATFORM / 2; dx++) {
                for (int dz = -PLATFORM / 2; dz <= PLATFORM / 2; dz++) {
                    level.setBlockAndUpdate(new BlockPos(spot[0] + dx, spot[1], spot[2] + dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        double[] home = HOME.getAndSet(null);
        if (home != null) {
            player.teleportTo(level, home[0], home[1], home[2], Set.of(), (float) home[3], (float) home[4], true);
        }
    }

    static boolean atSpot(Minecraft mc) {
        int[] spot = SPOT.get();
        LocalPlayer player = mc.player;
        return spot != null && player != null && Math.abs(player.getX() - (spot[0] + 0.5)) < 0.6 && Math.abs(player.getZ() - (spot[2] + 0.5)) < 0.6
                && Math.abs(player.getY() - (spot[1] + 1.0)) < 0.6;
    }

    static @Nullable String nestedZones(MinecraftServer server, boolean forceWorld) {
        removeTestZones(server);
        ZoneServer.release(server, Map.of(WorldRuleSpecs.GRAVITY_SCALE.id(), true, WorldRuleSpecs.BATTERY_SCALE.id(), true, WorldRuleSpecs.WIND.id(), true),
                Component.literal("Selbsttest"), "Selbsttest");
        spot(server);
        int[] spot = SPOT.get();
        if (spot == null) {
            return "kein Testplatz";
        }
        return nestedZonesAt(server, spot[0] + 0.5, spot[1] + 1.0, spot[2] + 0.5, forceWorld);
    }

    static @Nullable String zonesAhead(MinecraftServer server, boolean single) {
        removeTestZones(server);
        ServerPlayer player = ZonesOpenSelfTests.player(server);
        ZoneServer.release(server, Map.of(WorldRuleSpecs.GRAVITY_SCALE.id(), true, WorldRuleSpecs.BATTERY_SCALE.id(), true, WorldRuleSpecs.WIND.id(), true),
                Component.literal("Selbsttest"), "Selbsttest");
        QuadcopterEntity drone = ZoneSelfTests.testDrone(server);
        if (drone == null) {
            return "keine Testdrohne";
        }
        double x = Math.floor(drone.getX()) + 0.5;
        double y = Math.floor(drone.getY()) + 1.0;
        double z = Math.floor(drone.getZ()) + 0.5 - AHEAD;
        if (!single) {
            return nestedZonesAt(server, x, y, z, false);
        }
        Zone zone = Zone.create(ZoneServer.dimension(player.level()), OUTER, ZoneShape.BOX, new ZoneBox(x - 9.5, y - 2, z - 7.5, x + 9.5, y + 9, z + 7.5))
                .withOverride(WorldRuleSpecs.GRAVITY_SCALE, 0);
        ZoneServer.Outcome outcome = ZoneServer.save(server, zone, Component.literal("Selbsttest"), "Selbsttest");
        return outcome.ok() ? null : "Testzone nicht angelegt: " + outcome.problem();
    }

    private static @Nullable String nestedZonesAt(MinecraftServer server, double x, double y, double z, boolean forceWorld) {
        ServerPlayer player = ZonesOpenSelfTests.player(server);
        String dimension = ZoneServer.dimension(player.level());
        Zone outer = Zone.create(dimension, OUTER, ZoneShape.BOX, new ZoneBox(x - 14.5, y - 2, z - 14.5, x + 13.5, y + 10, z + 13.5))
                .withOverride(WorldRuleSpecs.GRAVITY_SCALE, 0).withOverride(WorldRuleSpecs.BATTERY_SCALE, 0);
        Zone inner = Zone.create(dimension, INNER, ZoneShape.BOX, new ZoneBox(x - 4.5, y - 1, z - 4.5, x + 4.5, y + 5, z + 4.5)).withPriority(1)
                .withRaceLegal(true).withOverride(WorldRuleSpecs.WIND, 0);
        if (forceWorld) {
            inner = inner.withWorld(WorldRuleSpecs.GRAVITY_SCALE);
        }
        ZoneServer.Outcome first = ZoneServer.save(server, outer, Component.literal("Selbsttest"), "Selbsttest");
        ZoneServer.Outcome second = ZoneServer.save(server, inner, Component.literal("Selbsttest"), "Selbsttest");
        if (!first.ok() || !second.ok()) {
            return "Testzonen nicht angelegt: " + first.problem() + " / " + second.problem();
        }
        return null;
    }

    static void hand(MinecraftServer server, ItemStack stack) {
        ZonesOpenSelfTests.player(server).setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

    static boolean holds(Minecraft mc, @Nullable Item item) {
        LocalPlayer player = mc.player;
        return player != null && (item == null ? player.getMainHandItem().isEmpty() : player.getMainHandItem().is(item));
    }

    static boolean visible(ZoneEditPanel panel, ZoneDraft.Row row) {
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        for (int y = 0; y < height; y++) {
            if (row.equals(panel.rowAt(panel.right() - 30, y))) {
                return true;
            }
        }
        return false;
    }

    static int firstRowY(ZoneEditPanel panel) {
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        for (int y = 0; y < height; y++) {
            if (panel.rowAt(panel.right() - 30, y) != null) {
                return y + 2;
            }
        }
        return -1;
    }

    static int lowestScrollbarY(ZoneEditPanel panel, double x) {
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        for (int y = height - 1; y >= 0; y--) {
            if (panel.overScrollbar(x, y)) {
                return y;
            }
        }
        return -1;
    }

    private static void setScale(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        ctx.state().put(SCALE, mc.options.guiScale().get());
        mc.options.guiScale().set(Math.max(1, mc.getWindow().getHeight() / GUI_HEIGHT));
        mc.resizeGui();
    }

    private static void restoreScale(SelfTest.StepContext ctx) {
        Object saved = ctx.state().remove(SCALE);
        if (saved instanceof Integer scale) {
            Minecraft mc = Minecraft.getInstance();
            mc.options.guiScale().set(scale);
            mc.resizeGui();
        }
    }

    private static StepResult endScroll(SelfTest.StepContext ctx, StepResult result) {
        ZoneEditor.stop();
        restoreScale(ctx);
        return result;
    }

    private static StepResult scrollStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return StepResult.pending();
        }
        switch (phase(ctx)) {
            case 0 -> {
                ZoneEditor.stop();
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                setScale(ctx);
                if (!ZoneEditor.startNew()) {
                    return endScroll(ctx, StepResult.fail("Zonen-Editor startet nicht"));
                }
                ZoneEditScreen.openSticky();
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (!(mc.gui.screen() instanceof ZoneEditScreen screen) || screen.panel() == null || since(ctx) < 5) {
                    return since(ctx) > 100 ? endScroll(ctx, StepResult.fail("Editor-Fenster fehlt")) : StepResult.pending();
                }
                ZoneEditor editor = screen.editor();
                ZoneEditPanel panel = screen.panel();
                List<ZoneDraft.Row> rows = editor.model().rows();
                ZoneDraft.Row first = rows.getFirst();
                ZoneDraft.Row last = rows.getLast();
                ctx.log("GUI " + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight() + ", " + rows.size() + " Zeilen, unterste "
                        + last.key() + ", Scrollweg " + panel.maxOffset());
                if (visible(panel, last)) {
                    return endScroll(ctx, StepResult.fail("unterste Zeile schon ohne Scrollen sichtbar – GUI zu hoch für den Test"));
                }
                if (!panel.scrollable()) {
                    return endScroll(ctx, StepResult.fail("Zeilen passen nicht ins Menü, aber es gibt keinen Scrollbalken"));
                }
                Zone before = editor.draft();
                double x = panel.right() - 40;
                for (int i = 0; i < 40; i++) {
                    screen.mouseScrolled(x, firstRowY(panel), 0.0, -1.0);
                    if (!editor.draft().equals(before)) {
                        return endScroll(ctx, StepResult.fail("Mausrad über dem Menü ändert nach " + (i + 1) + " Rasten einen Wert statt zu scrollen: "
                                + before.overrides() + " " + before.shape() + " → " + editor.draft().overrides() + " " + editor.draft().shape()));
                    }
                }
                if (!visible(panel, last) || panel.offset() != panel.maxOffset()) {
                    return endScroll(ctx, StepResult.fail("unterste Regel " + last.key() + " nach 40 Rasten Mausrad nicht sichtbar (Versatz " + panel.offset()
                            + " von " + panel.maxOffset() + ")"));
                }
                screen.tick();
                if (panel.offset() != panel.maxOffset()) {
                    return endScroll(ctx, StepResult.fail("das Menü springt nach dem Scrollen zur gewählten Zeile zurück"));
                }
                for (int i = 0; i < 40; i++) {
                    screen.mouseScrolled(x, firstRowY(panel), 0.0, 1.0);
                }
                if (!visible(panel, first) || panel.offset() != 0) {
                    return endScroll(ctx, StepResult.fail("oberste Zeile nach dem Zurückscrollen nicht sichtbar"));
                }
                double barX = panel.right() - 3;
                int barY = lowestScrollbarY(panel, barX);
                if (barY < 0) {
                    return endScroll(ctx, StepResult.fail("Scrollbalken nicht greifbar"));
                }
                MouseButtonInfo left = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
                screen.mouseClicked(new MouseButtonEvent(barX, barY, left), false);
                screen.mouseDragged(new MouseButtonEvent(barX, barY, left), 0.0, 0.0);
                screen.mouseReleased(new MouseButtonEvent(barX, barY, left));
                if (panel.offset() != panel.maxOffset() || !editor.draft().equals(before)) {
                    return endScroll(ctx, StepResult.fail("Klick ans Ende des Scrollbalkens scrollt nicht ans Ende: " + panel.offset() + " von "
                            + panel.maxOffset()));
                }
                ctx.log("Mausrad scrollt 0 → " + panel.maxOffset() + " → 0, Scrollbalken ans Ende gezogen, kein Wert geändert");
                return endScroll(ctx, StepResult.pass());
            }
            default -> {
                return endScroll(ctx, StepResult.fail("unbekannte Phase"));
            }
        }
    }

    private static StepResult finishWith(SelfTest.StepContext ctx, StepResult result) {
        ZoneViewConfig.set(ZoneView.DEFAULT_SHOW);
        ZoneEditor.stop();
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        } else if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        server(ctx, server -> {
            removeTestZones(server);
            hand(server, ItemStack.EMPTY);
            leaveSpot(server);
            return null;
        });
        ctx.state().put(RESULT_KEY, result);
        return StepResult.pending();
    }

    private static @Nullable StepResult finished(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            Object done = ctx.state().get(RESULT_KEY);
            if (done instanceof StepResult && waiting.status() == StepResult.Status.FAIL) {
                return waiting;
            }
            if (!(done instanceof StepResult) && waiting.status() == StepResult.Status.FAIL) {
                return finishWith(ctx, waiting);
            }
            return waiting;
        }
        Object done = ctx.state().get(RESULT_KEY);
        return done instanceof StepResult result ? result : null;
    }

    private static boolean framesPassed(SelfTest.StepContext ctx) {
        Object start = ctx.state().get("frames");
        if (start == null) {
            ctx.state().put("frames", ZoneRenderer.frames());
            return false;
        }
        return ZoneRenderer.frames() >= (long) start + 5;
    }

    private static void resetFrames(SelfTest.StepContext ctx) {
        ctx.state().remove("frames");
    }

    private static StepResult outlineStep(SelfTest.StepContext ctx) {
        StepResult finished = finished(ctx);
        if (finished != null) {
            return finished;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return StepResult.pending();
        }
        switch (phase(ctx)) {
            case 0 -> {
                ZoneEditor.stop();
                ZoneMarkerClient.clear();
                ZoneFocus.clear();
                ZoneViewConfig.set(ZoneView.DEFAULT_SHOW);
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                server(ctx, server -> {
                    hand(server, ItemStack.EMPTY);
                    return nestedZones(server, false);
                });
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (client(OUTER) == null || client(INNER) == null || !holds(mc, null) || !atSpot(mc)) {
                    return since(ctx) > 200 ? finishWith(ctx, StepResult.fail("Testzonen kommen nicht an")) : StepResult.pending();
                }
                if (!framesPassed(ctx)) {
                    return since(ctx) > 300 ? finishWith(ctx, StepResult.fail("keine Bilder gezeichnet")) : StepResult.pending();
                }
                if (ZoneRenderer.drawn() != 0) {
                    return finishWith(ctx, StepResult.fail("ohne Werkzeug in der Hand zeichnet der Client " + ZoneRenderer.drawn() + " Zonen-Umrisse"));
                }
                ctx.log("ohne Werkzeug: 0 Umrisse");
                resetFrames(ctx);
                server(ctx, server -> {
                    hand(server, new ItemStack(ZoneModule.ZONE_MARKER.get()));
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (!holds(mc, ZoneModule.ZONE_MARKER.get()) || !framesPassed(ctx)) {
                    return since(ctx) > 200 ? finishWith(ctx, StepResult.fail("Markierer kommt nicht in die Hand")) : StepResult.pending();
                }
                Zone outer = client(OUTER);
                Zone inner = client(INNER);
                Map<Integer, ZoneView.Style> styles = ZoneRenderer.styles();
                if (ZoneRenderer.drawn() != 2 || outer == null || inner == null) {
                    return finishWith(ctx, StepResult.fail("mit dem Markierer " + ZoneRenderer.drawn() + " statt 2 Zonen-Umrissen"));
                }
                if (styles.get(inner.id()) != ZoneView.Style.HIGHLIGHT || styles.get(outer.id()) != ZoneView.Style.EDGES) {
                    return finishWith(ctx, StepResult.fail("überlappende Zonen: innere " + styles.get(inner.id()) + " statt HIGHLIGHT, äußere "
                            + styles.get(outer.id()) + " statt EDGES"));
                }
                ctx.log("mit Markierer: 2 Umrisse, innere hervorgehoben, äußere nur Kanten");
                resetFrames(ctx);
                server(ctx, server -> {
                    hand(server, new ItemStack(RaceModule.COURSE_TOOL.get()));
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                if (!holds(mc, RaceModule.COURSE_TOOL.get()) || !framesPassed(ctx)) {
                    return since(ctx) > 200 ? finishWith(ctx, StepResult.fail("Kurs-Werkzeug kommt nicht in die Hand")) : StepResult.pending();
                }
                if (ZoneRenderer.drawn() != 2) {
                    return finishWith(ctx, StepResult.fail("mit dem Kurs-Werkzeug " + ZoneRenderer.drawn() + " statt 2 Zonen-Umrissen"));
                }
                ctx.log("mit Kurs-Werkzeug: 2 Umrisse");
                resetFrames(ctx);
                server(ctx, server -> {
                    hand(server, ItemStack.EMPTY);
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                if (!holds(mc, null)) {
                    return since(ctx) > 200 ? finishWith(ctx, StepResult.fail("Hand wird nicht leer")) : StepResult.pending();
                }
                if (!ctx.state().containsKey("tab")) {
                    ctx.state().put("tab", true);
                    ConfigTabs.open(null, ZonesTab.ID);
                    return StepResult.pending();
                }
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof ZonesTab tab) || tab.showButton() == null) {
                    return since(ctx) > 200 ? finishWith(ctx, StepResult.fail("Tab „Zonen“ ohne Einstellung „Zonen anzeigen“")) : StepResult.pending();
                }
                if (tab.showDraft() != ZoneView.DEFAULT_SHOW) {
                    return finishWith(ctx, StepResult.fail("Standard von „Zonen anzeigen“ ist " + tab.showDraft() + " statt „mit Werkzeug oder im Flug“"));
                }
                tab.showButton().onClick(new MouseButtonEvent(tab.showButton().getX() + tab.showButton().getWidth() - 4, tab.showButton().getY() + 4,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                if (tab.showDraft() != ZoneView.Show.ALWAYS) {
                    return finishWith(ctx, StepResult.fail("Klick auf „Zonen anzeigen“ schaltet nicht auf „immer“: " + tab.showDraft()));
                }
                screen.saveAndClose();
                if (ZoneViewConfig.show() != ZoneView.Show.ALWAYS) {
                    return finishWith(ctx, StepResult.fail("„Speichern“ übernimmt „Zonen anzeigen: immer“ nicht"));
                }
                resetFrames(ctx);
                next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                if (mc.gui.screen() != null || !framesPassed(ctx)) {
                    return since(ctx) > 200 ? finishWith(ctx, StepResult.fail("Menü schließt nicht")) : StepResult.pending();
                }
                if (ZoneRenderer.drawn() != 2) {
                    return finishWith(ctx, StepResult.fail("„Zonen anzeigen: immer“ ohne Werkzeug zeigt " + ZoneRenderer.drawn() + " statt 2 Umrissen"));
                }
                ctx.log("„immer“ ohne Werkzeug: 2 Umrisse");
                return finishWith(ctx, StepResult.pass());
            }
            default -> {
                return finishWith(ctx, StepResult.fail("unbekannte Phase"));
            }
        }
    }

    private static @Nullable String configFileProblem() {
        try {
            Files.writeString(ZoneViewConfig.CONFIG.path(), "{\n  \"show\": \"TOOL\"\n}\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "zones.json nicht geschrieben: " + e;
        }
        ZoneViewConfig.CONFIG.load();
        if (ZoneViewConfig.show() != ZoneView.DEFAULT_SHOW) {
            return "zones.json aus rc.3: TOOL bleibt " + ZoneViewConfig.show() + " statt des neuen Standards";
        }
        ZoneViewConfig.set(ZoneView.Show.TOOL);
        ZoneViewConfig.CONFIG.load();
        if (ZoneViewConfig.show() != ZoneView.Show.TOOL) {
            return "danach gewähltes TOOL wird beim Laden wieder zu " + ZoneViewConfig.show();
        }
        return null;
    }

    private static StepResult finishFlight(SelfTest.StepContext ctx, StepResult result) {
        ZoneViewConfig.set(ZoneView.DEFAULT_SHOW);
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        endFlight();
        server(ctx, server -> {
            removeTestZones(server);
            ZoneSelfTests.discard(server);
            ZoneSelfTests.returnPlayer(server);
            return null;
        });
        ctx.state().put(RESULT_KEY, result);
        return StepResult.pending();
    }

    private static void endFlight() {
        PilotSession session = PilotSession.INSTANCE;
        session.clearTestInput();
        if (session.isActive()) {
            session.requestStop();
        }
    }

    private static @Nullable StepResult flightStarted(SelfTest.StepContext ctx, boolean fpv) {
        CompletableFuture<?> start = (CompletableFuture<?>) ctx.state().get(START);
        if (start == null) {
            return StepResult.fail("Flugstart fehlt");
        }
        if (start.isCompletedExceptionally()) {
            return StepResult.fail("Flugstart: " + start.exceptionNow());
        }
        PilotSession session = PilotSession.INSTANCE;
        if (!start.isDone() || (fpv ? !session.isFpv() : !session.isLineOfSight()) || session.sim() == null) {
            return since(ctx) > 300 ? StepResult.fail((fpv ? "FPV" : "Sichtflug") + "-Sitzung startet nicht") : StepResult.pending();
        }
        session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        return null;
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        StepResult finished = finished(ctx);
        if (finished != null) {
            return finished.status() != StepResult.Status.PENDING && PilotSession.INSTANCE.isActive() ? StepResult.pending() : finished;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return StepResult.pending();
        }
        switch (phase(ctx)) {
            case 0 -> {
                ZoneEditor.stop();
                ZoneMarkerClient.clear();
                ZoneFocus.clear();
                String problem = configFileProblem();
                ZoneViewConfig.set(ZoneView.DEFAULT_SHOW);
                if (problem != null) {
                    return StepResult.fail(problem);
                }
                ctx.log("zones.json aus rc.3: TOOL einmal auf den Standard gehoben, danach gewähltes TOOL bleibt");
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                ctx.state().put(START, ZoneSelfTests.startFlight(ctx, true));
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult started = flightStarted(ctx, true);
                if (started != null) {
                    return started.status() == StepResult.Status.FAIL ? finishFlight(ctx, started) : started;
                }
                server(ctx, server -> zonesAhead(server, false));
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                Zone outer = client(OUTER);
                Zone inner = client(INNER);
                if (outer == null || inner == null || !framesPassed(ctx)) {
                    return since(ctx) > 300 ? finishFlight(ctx, StepResult.fail("Testzonen kommen im Flug nicht an")) : StepResult.pending();
                }
                if (ZoneRenderer.holdingTool(mc.player)) {
                    return finishFlight(ctx, StepResult.fail("Testaufbau: Werkzeug statt Funke in der Hand"));
                }
                Map<Integer, ZoneView.Style> styles = ZoneRenderer.styles();
                if (!styles.containsKey(outer.id()) || !styles.containsKey(inner.id())) {
                    return finishFlight(ctx, StepResult.fail("FPV-Flug ohne Werkzeug zeichnet " + ZoneRenderer.drawn() + " Zonen-Umrisse, die beiden Testzonen fehlen"));
                }
                if (styles.get(inner.id()) != ZoneView.Style.EDGES || styles.get(outer.id()) != ZoneView.Style.EDGES) {
                    return finishFlight(ctx, StepResult.fail("überlappende Zonen im Flug: " + styles.get(inner.id()) + " und " + styles.get(outer.id()) + " statt nur Kanten"));
                }
                ctx.log("FPV: beide Testzonen mit Umriss, überlappend nur mit Kanten");
                mc.gui.hud.toggle();
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                if (since(ctx) < 10) {
                    return StepResult.pending();
                }
                if (ZoneRenderer.drawn() != 0) {
                    return finishFlight(ctx, StepResult.fail("F1 blendet die Umrisse im Flug nicht aus: " + ZoneRenderer.drawn()));
                }
                ctx.log("FPV mit F1: 0 Umrisse");
                mc.gui.hud.toggle();
                ZoneViewConfig.set(ZoneView.Show.TOOL);
                resetFrames(ctx);
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                if (!framesPassed(ctx)) {
                    return since(ctx) > 300 ? finishFlight(ctx, StepResult.fail("keine Bilder gezeichnet")) : StepResult.pending();
                }
                if (ZoneRenderer.drawn() != 0) {
                    return finishFlight(ctx, StepResult.fail("„Nur mit Werkzeug“ zeichnet im Flug " + ZoneRenderer.drawn() + " Umrisse"));
                }
                ctx.log("„Nur mit Werkzeug“ im Flug: 0 Umrisse");
                ZoneViewConfig.set(ZoneView.DEFAULT_SHOW);
                endFlight();
                resetFrames(ctx);
                next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                if (PilotSession.INSTANCE.isActive()) {
                    return since(ctx) > 200 ? finishFlight(ctx, StepResult.fail("FPV-Sitzung endet nicht")) : StepResult.pending();
                }
                ctx.state().put(START, ZoneSelfTests.startFlight(ctx, false));
                next(ctx);
                return StepResult.pending();
            }
            case 6 -> {
                StepResult started = flightStarted(ctx, false);
                if (started != null) {
                    return started.status() == StepResult.Status.FAIL ? finishFlight(ctx, started) : started;
                }
                if (!framesPassed(ctx)) {
                    return since(ctx) > 300 ? finishFlight(ctx, StepResult.fail("keine Bilder gezeichnet")) : StepResult.pending();
                }
                Zone outer = client(OUTER);
                Zone inner = client(INNER);
                Map<Integer, ZoneView.Style> styles = ZoneRenderer.styles();
                if (outer == null || inner == null || !styles.containsKey(outer.id()) || !styles.containsKey(inner.id())) {
                    return finishFlight(ctx, StepResult.fail("Sichtflug ohne Werkzeug zeichnet " + ZoneRenderer.drawn() + " Zonen-Umrisse, die beiden Testzonen fehlen"));
                }
                ctx.log("Sichtflug: beide Testzonen mit Umriss");
                endFlight();
                resetFrames(ctx);
                next(ctx);
                return StepResult.pending();
            }
            case 7 -> {
                if (PilotSession.INSTANCE.isActive()) {
                    return since(ctx) > 200 ? finishFlight(ctx, StepResult.fail("Sichtflug-Sitzung endet nicht")) : StepResult.pending();
                }
                if (!framesPassed(ctx)) {
                    return since(ctx) > 300 ? finishFlight(ctx, StepResult.fail("keine Bilder gezeichnet")) : StepResult.pending();
                }
                if (ZoneRenderer.drawn() != 0) {
                    return finishFlight(ctx, StepResult.fail("zu Fuß ohne Werkzeug nach dem Flug " + ZoneRenderer.drawn() + " Umrisse"));
                }
                ctx.log("zu Fuß ohne Werkzeug: 0 Umrisse");
                return finishFlight(ctx, StepResult.pass());
            }
            default -> {
                return finishFlight(ctx, StepResult.fail("unbekannte Phase"));
            }
        }
    }

    private static StepResult nestedStep(SelfTest.StepContext ctx) {
        StepResult finished = finished(ctx);
        if (finished != null) {
            return finished;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return StepResult.pending();
        }
        ZoneDraft.Row gravity = new ZoneDraft.Row(ZoneDraft.Kind.RULE, WorldRuleSpecs.GRAVITY_SCALE);
        ZoneDraft.Row battery = new ZoneDraft.Row(ZoneDraft.Kind.RULE, WorldRuleSpecs.BATTERY_SCALE);
        switch (phase(ctx)) {
            case 0 -> {
                ZoneEditor.stop();
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                server(ctx, server -> {
                    String problem = nestedZones(server, true);
                    if (problem != null) {
                        return problem;
                    }
                    int[] spot = SPOT.get();
                    ServerLevel level = ZonesOpenSelfTests.player(server).level();
                    ZoneResolution inside = ZoneServer.resolve(level, spot[0] + 0.5, spot[1] + 1.5, spot[2] + 0.5);
                    ZoneResolution outside = ZoneServer.resolve(level, spot[0] + 10.5, spot[1] + 1.5, spot[2] + 0.5);
                    PhysicsRules world = inside.world();
                    if (inside.rules().gravityPercent() != world.gravityPercent() || !inside.rules().infiniteBattery()) {
                        return "Server: in der inneren Zone Schwerkraft " + inside.rules().gravityPercent() + " statt Weltwert " + world.gravityPercent()
                                + ", Akku unendlich " + inside.rules().infiniteBattery();
                    }
                    if (outside.rules().gravityPercent() != 0) {
                        return "Server: in der äußeren Zone Schwerkraft " + outside.rules().gravityPercent() + " statt 0";
                    }
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                Zone outer = client(OUTER);
                Zone inner = client(INNER);
                if (outer == null || inner == null || !atSpot(mc)) {
                    return since(ctx) > 200 ? finishWith(ctx, StepResult.fail("Testzonen kommen nicht an")) : StepResult.pending();
                }
                if (inner.mode(WorldRuleSpecs.GRAVITY_SCALE) != ZoneRuleMode.WORLD) {
                    return finishWith(ctx, StepResult.fail("Client: innere Zone ohne „Weltwert erzwingen“ für die Schwerkraft: "
                            + inner.mode(WorldRuleSpecs.GRAVITY_SCALE)));
                }
                PhysicsRules world = ClientWorldRules.INSTANCE.physics();
                ZoneResolution here = ZoneResolver.resolve(ClientZones.INSTANCE.zones(), world, player.getX(), player.getY() + 0.5, player.getZ());
                if (here.rules().gravityPercent() != world.gravityPercent() || !here.rules().infiniteBattery() || here.forcedBy(WorldRuleSpecs.GRAVITY_SCALE) == null) {
                    return finishWith(ctx, StepResult.fail("Client: Schwerkraft " + here.rules().gravityPercent() + " statt Weltwert, Akku unendlich "
                            + here.rules().infiniteBattery()));
                }
                String notice = ZoneHud.entered(here).detail().getString();
                String forcedText = ZoneText.worldValue(WorldRuleSpecs.GRAVITY_SCALE, world.gravityPercent()).getString();
                if (!notice.contains(forcedText) || !notice.contains(OUTER)) {
                    return finishWith(ctx, StepResult.fail("OSD-Hinweis nennt die Quellen nicht: „" + notice + "“"));
                }
                ctx.log("OSD-Hinweis: " + notice);
                if (!ZoneEditor.start(inner) || !ZoneEditScreen.openSticky()) {
                    return finishWith(ctx, StepResult.fail("Editor der inneren Zone startet nicht"));
                }
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (!(mc.gui.screen() instanceof ZoneEditScreen screen) || screen.panel() == null || since(ctx) < 3) {
                    return since(ctx) > 100 ? finishWith(ctx, StepResult.fail("Editor-Fenster fehlt")) : StepResult.pending();
                }
                ZoneEditor editor = screen.editor();
                ZoneEditPanel panel = screen.panel();
                Zone draft = editor.draft();
                PhysicsRules world = ClientWorldRules.INSTANCE.physics();
                String gravityText = panel.value(gravity, draft).getString();
                String batteryText = panel.value(battery, draft).getString();
                String expectedGravity = Component.translatable(ZoneText.OPEN_KEY + "edit.world",
                        WorldRuleText.value(WorldRuleSpecs.GRAVITY_SCALE, world.gravityPercent())).getString();
                String expectedBattery = Component.translatable(ZoneEditPanel.KEY + "inherit", WorldRuleText.value(WorldRuleSpecs.BATTERY_SCALE, 0)).getString();
                if (!gravityText.equals(expectedGravity) || !batteryText.equals(expectedBattery)) {
                    return finishWith(ctx, StepResult.fail("Editor zeigt Schwerkraft „" + gravityText + "“ statt „" + expectedGravity + "“ und Akku „"
                            + batteryText + "“ statt „" + expectedBattery + "“"));
                }
                ctx.log("Editor: Schwerkraft „" + gravityText + "“, Akku „" + batteryText + "“");
                editor.reset(battery);
                if (editor.draft().mode(WorldRuleSpecs.BATTERY_SCALE) != ZoneRuleMode.WORLD) {
                    return finishWith(ctx, StepResult.fail("Entf auf „wie darunter“ erzwingt den Weltwert nicht"));
                }
                if (!editor.apply()) {
                    return finishWith(ctx, StepResult.fail("Übernehmen schickt nichts"));
                }
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                Zone inner = client(INNER);
                if (inner == null || inner.mode(WorldRuleSpecs.BATTERY_SCALE) != ZoneRuleMode.WORLD) {
                    return since(ctx) > 200 ? finishWith(ctx, StepResult.fail("Server übernimmt „Weltwert erzwingen“ für den Akku nicht")) : StepResult.pending();
                }
                PhysicsRules world = ClientWorldRules.INSTANCE.physics();
                ZoneResolution here = ZoneResolver.resolve(ClientZones.INSTANCE.zones(), world, player.getX(), player.getY() + 0.5, player.getZ());
                if (here.rules().batteryPercent() != world.batteryPercent()) {
                    return finishWith(ctx, StepResult.fail("Akku in der inneren Zone " + here.rules().batteryPercent() + " statt Weltwert"));
                }
                server(ctx, server -> {
                    int ok = ZoneSelfTests.command(server, "propwash zone set \"" + INNER + "\" wind world");
                    Zone stored = ZoneData.get(server).byName(ZoneServer.dimension(ZonesOpenSelfTests.player(server).level()), INNER).orElse(null);
                    if (ok == 0 || stored == null || stored.mode(WorldRuleSpecs.WIND) != ZoneRuleMode.WORLD) {
                        return "/propwash zone set … wind world setzt keinen Weltwert: " + (stored == null ? "keine Zone" : stored.mode(WorldRuleSpecs.WIND));
                    }
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                ctx.log("Akku per Entf auf Weltwert, Befehl set … wind world");
                return finishWith(ctx, StepResult.pass());
            }
            default -> {
                return finishWith(ctx, StepResult.fail("unbekannte Phase"));
            }
        }
    }

    private static final class ScrollScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE) && !ctx.state().containsKey("ready")) {
                ctx.state().put("ready", true);
                server(ctx, server -> nestedZones(server, true));
                return StepResult.pending();
            }
            StepResult waiting = waitFuture(ctx);
            if (waiting != null) {
                return waiting;
            }
            Zone inner = client(INNER);
            if (inner == null || !atSpot(mc)) {
                return ctx.tick() > 300 ? StepResult.fail("Szene kommt nicht an") : StepResult.pending();
            }
            if (!ZoneEditor.active()) {
                if (mc.player != null) {
                    ZonesOpenSelfTests.look(mc.player, mc.player.getEyePosition().add(6.0, -1.0, 8.0));
                }
                if (!ZoneEditor.start(inner) || !ZoneEditScreen.openSticky()) {
                    return StepResult.fail("Editor startet nicht");
                }
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ZoneEditScreen screen) || screen.panel() == null) {
                return StepResult.pending();
            }
            ZoneEditPanel panel = screen.panel();
            ZoneDraft.Row gravity = new ZoneDraft.Row(ZoneDraft.Kind.RULE, WorldRuleSpecs.GRAVITY_SCALE);
            screen.editor().model().select(gravity);
            panel.layout(screen.width, screen.height);
            for (int i = 0; i < 40; i++) {
                panel.scroll(-1.0);
            }
            for (int i = 0; i < 40 && !panel.rowVisible(gravity); i++) {
                panel.scroll(1.0);
            }
            return panel.scrollable() && panel.offset() > 0 ? StepResult.pass() : StepResult.fail("Editor ohne Scrollbalken");
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ZoneEditor.stop();
            ctx.onServer(server -> {
                removeTestZones(server);
                leaveSpot(server);
            });
        }
    }

    private static final class NestedScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE) && !ctx.state().containsKey("ready")) {
                ctx.state().put("ready", true);
                ZoneViewConfig.set(ZoneView.DEFAULT_SHOW);
                server(ctx, server -> {
                    String problem = nestedZones(server, true);
                    hand(server, new ItemStack(ZoneModule.ZONE_MARKER.get()));
                    return problem;
                });
                return StepResult.pending();
            }
            StepResult waiting = waitFuture(ctx);
            if (waiting != null) {
                return waiting;
            }
            if (client(INNER) == null || client(OUTER) == null || !atSpot(mc) || !holds(mc, ZoneModule.ZONE_MARKER.get())) {
                return ctx.tick() > 300 ? StepResult.fail("Szene kommt nicht an") : StepResult.pending();
            }
            Screenshots.parkMouse(mc);
            if (mc.player != null) {
                ZonesOpenSelfTests.look(mc.player, mc.player.getEyePosition().add(12.0, 1.0, 9.0));
            }
            return ZoneRenderer.drawn() == 2 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ZonesOpenSelfTests.look(mc.player, mc.player.getEyePosition().add(12.0, 1.0, 9.0));
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ctx.onServer(server -> {
                removeTestZones(server);
                hand(server, ItemStack.EMPTY);
                leaveSpot(server);
            });
        }
    }

    private static final class FlightScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(START)) {
                ZoneViewConfig.set(ZoneView.DEFAULT_SHOW);
                ctx.state().put(START, ZoneSelfTests.startFlight(ctx, true));
                return StepResult.pending();
            }
            StepResult started = flightStarted(ctx, true);
            if (started != null) {
                return started.status() == StepResult.Status.FAIL || ctx.tick() <= 300 ? started : StepResult.fail("FPV-Sitzung startet nicht");
            }
            if (!ctx.state().containsKey("ready")) {
                ctx.state().put("ready", true);
                server(ctx, server -> zonesAhead(server, true));
                return StepResult.pending();
            }
            StepResult waiting = waitFuture(ctx);
            if (waiting != null) {
                return waiting;
            }
            Zone zone = client(OUTER);
            if (zone == null || !ZoneRenderer.styles().containsKey(zone.id())) {
                return ctx.tick() > 300 ? StepResult.fail("Zonen-Umriss fehlt im FPV-Flugbild") : StepResult.pending();
            }
            Screenshots.parkMouse(mc);
            int flying = (int) ctx.state().computeIfAbsent("flying", key -> ctx.tick());
            return ctx.tick() - flying >= 40 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            endFlight();
            ctx.onServer(server -> {
                removeTestZones(server);
                ZoneSelfTests.discard(server);
                ZoneSelfTests.returnPlayer(server);
            });
        }
    }
}
