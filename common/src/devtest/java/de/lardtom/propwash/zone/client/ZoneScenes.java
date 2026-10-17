package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.server.client.WorldRulesTab;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneDraft;
import de.lardtom.propwash.zone.ZoneServer;
import de.lardtom.propwash.zone.ZoneShape;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class ZoneScenes {
    private static final AtomicReference<double @Nullable []> HOME = new AtomicReference<>();
    private static final String FUTURE = "scene_future";
    private static final String STAGE = "scene_stage";
    private static final int SCENE_OFFSET = 96;

    private ZoneScenes() {
    }

    public record Names(String tunnel, String field, String gate) {
        public static Names english() {
            return new Names("Zero-G tunnel", "Practice field", "Zero-G gate");
        }

        public static Names german() {
            return new Names("Schwerelos-Tunnel", "Übungsplatz", "Schwerelos-Tor");
        }

        List<String> all() {
            return List.of(this.tunnel, this.field, this.gate);
        }
    }

    public static void registerScreenshots(boolean german) {
        Names names = german ? Names.german() : Names.english();
        String suffix = german ? "_de" : "";
        String label = german ? " deutsch" : "";
        Screenshots.step("bildschirmfotos: Zonen – Rand in der Welt" + label, "zones_border" + suffix, 20, border(names));
        Screenshots.step("bildschirmfotos: Zonen – Editor im HUD" + label, "zones_editor_hud" + suffix, 10, editorHud(names));
        Screenshots.step("bildschirmfotos: Zonen – Editor mit Mauszeiger" + label, "zones_editor_cursor" + suffix, 10, editorCursor(names));
        Screenshots.step("bildschirmfotos: Zonen – Welt-Regeln-Tab" + label, "configurator_world_rules_zones" + suffix, 20, tab(names));
        Screenshots.step("bildschirmfotos: Zonen – OSD-Hinweis im FPV" + label, "zones_fpv_notice" + suffix, 10, fpv(names));
        SelfTest.clientStep("bildschirmfotos: Zonen – Aufräumen" + label, 400, ctx -> cleanup(ctx, names));
    }

    public static Screenshots.Scene border(Names names) {
        return new BorderScene(names);
    }

    public static Screenshots.Scene editorHud(Names names) {
        return new EditorScene(names, false);
    }

    public static Screenshots.Scene editorCursor(Names names) {
        return new EditorScene(names, true);
    }

    public static Screenshots.Scene tab(Names names) {
        return new TabScene(names);
    }

    public static Screenshots.Scene fpv(Names names) {
        return new FpvScene(names);
    }

    public static StepResult cleanup(SelfTest.StepContext ctx, Names names) {
        if (ctx.tick() == 0) {
            ZoneEditor.stop();
            PilotSession.INSTANCE.clearTestInput();
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
            }
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                remove(server, names);
                ZoneSelfTests.discard(server);
                ZoneSelfTests.returnPlayer(server);
                double[] home = HOME.getAndSet(null);
                if (home != null) {
                    ServerPlayer player = ZoneSelfTests.player(server);
                    player.teleportTo(player.level(), home[0], home[1], home[2], Set.of(), (float) home[3], (float) home[4], true);
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null || !future.isDone()) {
            return StepResult.pending();
        }
        for (String name : names.all()) {
            if (ZoneSelfTests.client(name) != null) {
                return ctx.tick() > 200 ? StepResult.fail("Bildschirmfoto-Zone bleibt: " + name) : StepResult.pending();
            }
        }
        return StepResult.pass();
    }

    static void remove(MinecraftServer server, Names names) {
        for (String name : names.all()) {
            ZoneSelfTests.zone(server, name).ifPresent(zone -> ZoneServer.delete(server, zone.id(), Component.literal("Bildschirmfoto"), "Bildschirmfoto"));
        }
    }

    static void setup(MinecraftServer server, Names names) {
        ServerPlayer player = ZoneSelfTests.player(server);
        ServerLevel level = player.level();
        if (HOME.get() == null) {
            HOME.set(new double[]{player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()});
        }
        double[] home = HOME.get();
        int x = (int) Math.floor(home[0]) + SCENE_OFFSET;
        int z = (int) Math.floor(home[2]);
        level.getChunk(x >> 4, z >> 4);
        level.getChunk(x >> 4, (z - 32) >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), 168.0F, 8.0F, true);
        String dimension = ZoneServer.dimension(level);
        if (ZoneSelfTests.zone(server, names.field).isEmpty()) {
            ZoneSelfTests.save(server, Zone.create(dimension, names.field, ZoneShape.BOX, new ZoneBox(x - 22.0, y - 1.0, z - 30.0, x - 2.0, y + 9.0, z + 6.0))
                    .withOverride(WorldRuleSpecs.BATTERY_SCALE, 0).withOverride(WorldRuleSpecs.CRASH_TOLERANCE, 200));
        }
        if (ZoneSelfTests.zone(server, names.tunnel).isEmpty()) {
            ZoneSelfTests.save(server, Zone.create(dimension, names.tunnel, ZoneShape.CYLINDER, ZoneBox.cylinder(x + 7.5, y, z - 20.5, 5.0, 11.0))
                    .withRaceLegal(true).withPriority(2).withOverride(WorldRuleSpecs.GRAVITY_SCALE, 0).withOverride(WorldRuleSpecs.WIND, 0));
        }
    }

    static @Nullable StepResult waitSetup(SelfTest.StepContext ctx, Names names) {
        if (!ctx.state().containsKey(FUTURE)) {
            ctx.state().put(FUTURE, ctx.onServer(server -> setup(server, names)));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Szene: " + future.exceptionNow());
        }
        if (!future.isDone() || ZoneSelfTests.client(names.tunnel) == null || ZoneSelfTests.client(names.field) == null) {
            return ctx.tick() > 300 ? StepResult.fail("Bildschirmfoto-Zonen kommen nicht an") : StepResult.pending();
        }
        return null;
    }

    private static final class BorderScene implements Screenshots.Scene {
        private final Names names;

        private BorderScene(Names names) {
            this.names = names;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            StepResult waiting = waitSetup(ctx, this.names);
            if (waiting != null) {
                return waiting;
            }
            Screenshots.parkMouse(Minecraft.getInstance());
            return ZoneRenderer.drawn() >= 2 ? StepResult.pass() : StepResult.pending();
        }
    }

    private static final class EditorScene implements Screenshots.Scene {
        private final Names names;
        private final boolean cursor;

        private EditorScene(Names names, boolean cursor) {
            this.names = names;
            this.cursor = cursor;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            StepResult waiting = waitSetup(ctx, this.names);
            if (waiting != null) {
                return waiting;
            }
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(STAGE)) {
                ctx.state().put(STAGE, true);
                Zone tunnel = ZoneSelfTests.client(this.names.tunnel);
                if (tunnel == null || !ZoneEditor.start(tunnel)) {
                    return StepResult.fail("Editor startet nicht");
                }
                ZoneEditor editor = ZoneEditor.current();
                if (editor == null) {
                    return StepResult.fail("kein Editor");
                }
                editor.change(ZoneDraft.Row.of(ZoneDraft.Kind.HEIGHT), 2);
                editor.change(new ZoneDraft.Row(ZoneDraft.Kind.RULE, WorldRuleSpecs.BATTERY_SCALE), -10);
                editor.model().select(new ZoneDraft.Row(ZoneDraft.Kind.RULE, WorldRuleSpecs.GRAVITY_SCALE));
                if (this.cursor) {
                    ZoneEditScreen.openSticky();
                }
                return StepResult.pending();
            }
            if (this.cursor && !(mc.gui.screen() instanceof ZoneEditScreen)) {
                return ctx.tick() > 100 ? StepResult.fail("Editor-Fenster fehlt") : StepResult.pending();
            }
            return ZoneEditor.active() ? StepResult.pass() : StepResult.fail("Editor beendet");
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!this.cursor || !(mc.gui.screen() instanceof ZoneEditScreen screen) || screen.panel() == null) {
                Screenshots.parkMouse(mc);
                return;
            }
            ZoneEditPanel panel = screen.panel();
            ZoneDraft.Row target = new ZoneDraft.Row(ZoneDraft.Kind.RULE, WorldRuleSpecs.GRAVITY_SCALE);
            for (int y = 0; y < mc.getWindow().getGuiScaledHeight(); y++) {
                if (target.equals(panel.rowAt(panel.right() - 30, y))) {
                    double gx = panel.right() - 60;
                    double gy = y + 4;
                    double sx = gx * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
                    double sy = gy * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
                    mc.mouseHandler.onMove(mc.getWindow().handle(), sx, sy, 0.0, 0.0);
                    return;
                }
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ZoneEditor.stop();
        }
    }

    private static final class TabScene implements Screenshots.Scene {
        private final Names names;

        private TabScene(Names names) {
            this.names = names;
        }

        private static @Nullable WorldRulesTab tab() {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                ConfigTabs.open(null, WorldRulesTab.ID);
                return null;
            }
            ConfigTab active = screen.activeTab();
            if (active == null || !WorldRulesTab.ID.equals(active.id())) {
                screen.selectTab(WorldRulesTab.ID);
                return null;
            }
            return active instanceof WorldRulesTab tab ? tab : null;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            StepResult waiting = waitSetup(ctx, this.names);
            if (waiting != null) {
                return waiting;
            }
            WorldRulesTab tab = tab();
            if (tab == null || tab.zones().entries().size() < 2) {
                return ctx.tick() > 300 ? StepResult.fail("Tab zeigt die Zonen nicht") : StepResult.pending();
            }
            for (ZoneSection.Entry entry : tab.zones().entries()) {
                if (entry.edit() == null) {
                    return StepResult.fail("Zone ohne Knopf Bearbeiten trotz Rechten");
                }
            }
            FlatButton create = tab.zones().newButton();
            if (!tab.atTop() || tab.rows().isEmpty() || tab.zonesTop() < tab.viewTop() || tab.zonesTop() >= tab.screenY(tab.rows().getFirst())
                    || create == null || create.getY() + create.getHeight() > tab.clipBottom()) {
                return StepResult.fail("Zonen stehen nicht ohne Scrollen oben im Tab");
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
        }
    }

    private static final class FpvScene implements Screenshots.Scene {
        private final Names names;

        private FpvScene(Names names) {
            this.names = names;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            if (!ctx.state().containsKey(STAGE)) {
                ctx.state().put(STAGE, ZoneSelfTests.startFlight(ctx, true));
                return StepResult.pending();
            }
            CompletableFuture<?> start = (CompletableFuture<?>) ctx.state().get(STAGE);
            if (start.isCompletedExceptionally()) {
                return StepResult.fail("Flugstart: " + start.exceptionNow());
            }
            if (!start.isDone() || !session.isActive() || session.sim() == null) {
                return ctx.tick() > 300 ? StepResult.fail("FPV-Sitzung startet nicht") : StepResult.pending();
            }
            int flying = (int) ctx.state().computeIfAbsent("flying", key -> ctx.tick());
            if (ctx.tick() - flying < 60) {
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                return StepResult.pending();
            }
            if (!ctx.state().containsKey(FUTURE)) {
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                ctx.state().put(FUTURE, ctx.onServer(server -> ZoneSelfTests.save(server, ZoneSelfTests.floatDraft(server, this.names.gate, true)
                        .withOverride(WorldRuleSpecs.CRASH_TOLERANCE, 300).withOverride(WorldRuleSpecs.CRASH_DISARM, 0))));
                return StepResult.pending();
            }
            ZoneHud.Notice notice = ZoneHud.notice();
            if (notice == null || !notice.title().getString().contains(this.names.gate)) {
                return ctx.tick() > 300 ? StepResult.fail("OSD-Hinweis fehlt") : StepResult.pending();
            }
            Player player = Minecraft.getInstance().player;
            return player != null && session.isFpv() ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.clearTestInput();
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
            }
            ctx.onServer(server -> {
                ZoneSelfTests.zone(server, this.names.gate).ifPresent(zone -> ZoneServer.delete(server, zone.id(), Component.literal("Bildschirmfoto"),
                        "Bildschirmfoto"));
                ZoneSelfTests.discard(server);
                ZoneSelfTests.returnPlayer(server);
            });
        }
    }
}
