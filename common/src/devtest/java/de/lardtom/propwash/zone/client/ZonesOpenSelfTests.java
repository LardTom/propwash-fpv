package de.lardtom.propwash.zone.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.ZonesOpenJoinCheck;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.WorldRulesS2C;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.client.RuleTierSwitch;
import de.lardtom.propwash.server.client.WorldRulesTab;
import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneCheck;
import de.lardtom.propwash.zone.ZoneData;
import de.lardtom.propwash.zone.ZoneDraft;
import de.lardtom.propwash.zone.ZoneEditC2S;
import de.lardtom.propwash.zone.ZoneJumpC2S;
import de.lardtom.propwash.zone.ZoneMarker;
import de.lardtom.propwash.zone.ZoneModule;
import de.lardtom.propwash.zone.ZoneOwner;
import de.lardtom.propwash.zone.ZoneRelease;
import de.lardtom.propwash.zone.ZoneReleaseC2S;
import de.lardtom.propwash.zone.ZoneResolution;
import de.lardtom.propwash.zone.ZoneResolver;
import de.lardtom.propwash.zone.ZoneServer;
import de.lardtom.propwash.zone.ZoneShape;
import de.lardtom.propwash.zone.ZoneText;
import de.lardtom.propwash.zone.ZonesS2C;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class ZonesOpenSelfTests {
    public static final String PREFIX = "zones-open: ";
    static final String MENU_STEP = "eigener Tab „Zonen“ im K-Menü, Tab Welt-Regeln ohne Zonen-Liste (nur Häkchen je Regel)";
    static final String ZONES_TAB = "zones";
    static final String MENU_OWN = "zones-open Menü eigen";
    static final String MENU_OTHER = "zones-open Menü Alice";
    static final String MENU_ADMIN = "zones-open Menü Admin";
    static final String JUMP_STEP = "„Hinspringen“ landet unter einer Zone hoch in der Luft auf dem Boden, ohne Boden lehnt der Server ab";
    static final String JUMP_SKY = "zones-open Sprung hoch";
    static final String JUMP_NONE = "zones-open Sprung ohne Boden";
    static final int SKY_LIFT = 90;
    static final String MARKED = "zones-open Markiert";
    static final String ALICE_ZONE = "zones-open Alice";
    static final String ADMIN_ZONE = "zones-open Admin";
    static final UUID ALICE = UUID.fromString("00000000-0000-4000-8000-0000000002a1");
    static final UUID BOB = UUID.fromString("00000000-0000-4000-8000-0000000002b1");
    static final int SITE_OFFSET = 160;
    static final int SITE_LIFT = 24;
    static final int PLATFORM = 13;
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String MARK = "mark";
    private static final String DEFAULT_LANGUAGE = "en_us";
    private static final AtomicReference<@Nullable String> RESULT = new AtomicReference<>();
    private static final AtomicReference<int @Nullable []> SITE = new AtomicReference<>();
    private static final AtomicReference<double @Nullable []> HOME = new AtomicReference<>();

    private ZonesOpenSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + "Markierer setzt zwei Ecken mit Vorschau-Rahmen und öffnet den Zonen-Editor", 1200, ZonesOpenSelfTests::markerStep);
        SelfTest.clientStep(PREFIX + "Häkchen „Zonen“ im Tab Welt-Regeln sperrt die Schwerkraft für Zonen", 900, ZonesOpenSelfTests::releaseStep);
        SelfTest.clientStep(PREFIX + "gesperrte Regel bleibt gespeichert, wirkt nicht und ist im Editor ausgegraut", 900, ZonesOpenSelfTests::lockedStep);
        SelfTest.clientStep(PREFIX + "Rechte: Besitzer, fremder Spieler, Operator und Welt-Regel „Spieler dürfen Zonen anlegen“", 900,
                ZonesOpenSelfTests::rightsStep);
        SelfTest.clientStep(PREFIX + "Ansicht ohne Operator-Recht: Tab Zonen nur mit der eigenen Zone, Häkchen gesperrt, Markierer bei Regel aus abgelehnt", 900,
                ZonesOpenSelfTests::viewStep);
        SelfTest.clientStep(PREFIX + MENU_STEP, 900, ZonesOpenSelfTests::menuStep);
        SelfTest.clientStep(PREFIX + JUMP_STEP, 600, ZonesOpenSelfTests::jumpGroundStep);
        ZoneLayersSelfTests.register();
        if (Screenshots.enabled()) {
            registerScreenshots(false);
            SelfTest.switchStep(PREFIX + "Sprache Deutsch", PREFIX + "Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
            registerScreenshots(true);
            SelfTest.clientStep(PREFIX + "Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
            SelfTest.clientStep(PREFIX + "options.txt behält nach den deutschen Bildschirmfotos die Sprache des Laufs", 100, ZonesOpenSelfTests::languageKeptStep);
        }
        SelfTest.clientStep(PREFIX + "Aufräumen", 600, ZonesOpenSelfTests::cleanupStep);
    }

    private static void registerScreenshots(boolean german) {
        String suffix = german ? "_de" : "";
        String label = german ? " deutsch" : "";
        Screenshots.step(PREFIX + "Bildschirmfoto Vorschau-Rahmen des Markierers" + label, "zones_open_marker" + suffix, 20, new MarkerScene());
        Screenshots.step(PREFIX + "Bildschirmfoto Editor mit ausgegrauter Regel" + label, "zones_open_editor_locked" + suffix, 10, new EditorScene());
        Screenshots.step(PREFIX + "Bildschirmfoto Tab Welt-Regeln mit Häkchen „Zonen“" + label, "zones_open_world_rules" + suffix, 20, new TabScene());
        ZoneLayersSelfTests.registerScreenshots(german);
    }

    static ServerPlayer player(MinecraftServer server) {
        return ZoneSelfTests.player(server);
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

    static @Nullable Zone stored(String name) {
        for (Zone zone : ClientZones.INSTANCE.stored()) {
            if (ZoneCheck.sameName(zone.name(), name)) {
                return zone;
            }
        }
        return null;
    }

    static @Nullable Zone effective(int id) {
        for (Zone zone : ClientZones.INSTANCE.zones()) {
            if (zone.id() == id) {
                return zone;
            }
        }
        return null;
    }

    static Optional<Zone> onServer(MinecraftServer server, String name) {
        return ZoneData.get(server).byName(ZoneServer.dimension(player(server).level()), name);
    }

    static @Nullable String site(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        if (HOME.get() == null) {
            HOME.set(new double[]{player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()});
        }
        int[] site = SITE.get();
        if (site == null) {
            double[] home = HOME.get();
            int x = (int) Math.floor(home[0]) + SITE_OFFSET;
            int z = (int) Math.floor(home[2]);
            for (int cx = (x - 2) >> 4; cx <= (x + PLATFORM + 2) >> 4; cx++) {
                for (int cz = (z - 2) >> 4; cz <= (z + PLATFORM + 2) >> 4; cz++) {
                    level.getChunk(cx, cz);
                }
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + SITE_LIFT;
            for (int dx = 0; dx < PLATFORM; dx++) {
                for (int dz = 0; dz < PLATFORM; dz++) {
                    level.setBlockAndUpdate(new BlockPos(x + dx, y, z + dz), Blocks.SMOOTH_STONE.defaultBlockState());
                    for (int dy = 1; dy <= 6; dy++) {
                        level.setBlockAndUpdate(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            site = new int[]{x, y, z};
            SITE.set(site);
        }
        int centre = PLATFORM / 2;
        player.teleportTo(level, site[0] + centre + 0.5, site[1] + 1.0, site[2] + centre + 0.5, Set.of(), 180.0F, 90.0F, true);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ZoneModule.ZONE_MARKER.get()));
        return null;
    }

    static void leaveSite(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        int[] site = SITE.getAndSet(null);
        if (site != null) {
            for (int dx = 0; dx < PLATFORM; dx++) {
                for (int dz = 0; dz < PLATFORM; dz++) {
                    level.setBlockAndUpdate(new BlockPos(site[0] + dx, site[1], site[2] + dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        double[] home = HOME.getAndSet(null);
        if (home != null) {
            player.teleportTo(level, home[0], home[1], home[2], Set.of(), (float) home[3], (float) home[4], true);
        }
    }

    static boolean atSite(Minecraft mc) {
        int[] site = SITE.get();
        LocalPlayer player = mc.player;
        return site != null && player != null && ZoneMarkerClient.holding(player) && Math.abs(player.getY() - (site[1] + 1.0)) < 0.6
                && Math.abs(player.getX() - (site[0] + PLATFORM / 2 + 0.5)) < 0.6;
    }

    static void look(LocalPlayer player, Vec3 at) {
        Vec3 eye = player.getEyePosition();
        Vec3 d = at.subtract(eye);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, flat));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
    }

    static Vec3 aim(BlockPos corner) {
        return Vec3.atBottomCenterOf(corner).subtract(0.0, 0.001, 0.0);
    }

    static BlockPos corner(int dx, int dz) {
        int[] site = SITE.get();
        if (site == null) {
            throw new IllegalStateException("kein Testplatz");
        }
        return new BlockPos(site[0] + dx, site[1] + 1, site[2] + dz);
    }

    static void press(Minecraft mc) {
        KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
    }

    private static StepResult markerStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return StepResult.pending();
        }
        switch (phase(ctx)) {
            case 0 -> {
                ZoneEditor.stop();
                ZoneMarkerClient.clear();
                server(ctx, server -> {
                    ZoneServer.release(server, Map.of(WorldRuleSpecs.GRAVITY_SCALE.id(), true), Component.literal("Selbsttest"), "Selbsttest");
                    onServer(server, MARKED).ifPresent(zone -> ZoneServer.delete(server, zone.id(), Component.literal("Selbsttest"), "Selbsttest"));
                    return site(server);
                });
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (!atSite(mc) || !ClientZones.INSTANCE.creatable()) {
                    return since(ctx) > 200 ? StepResult.fail("Spieler steht nicht mit dem Markierer auf dem Testplatz") : StepResult.pending();
                }
                look(player, aim(corner(2, 2)));
                BlockPos target = ZoneMarkerClient.target(mc);
                if (!corner(2, 2).equals(target)) {
                    return since(ctx) > 60 ? StepResult.fail("Markierer zielt auf " + target + " statt " + corner(2, 2)) : StepResult.pending();
                }
                press(mc);
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (!corner(2, 2).equals(ZoneMarkerClient.first())) {
                    return since(ctx) > 40 ? StepResult.fail("Rechtsklick setzt die erste Ecke nicht: " + ZoneMarkerClient.first()) : StepResult.pending();
                }
                look(player, aim(corner(10, 9)));
                Zone preview = ZoneMarkerClient.preview(mc);
                ZoneBox expected = ZoneMarker.box(2 + SITE.get()[0], corner(2, 2).getY(), 2 + SITE.get()[2], 10 + SITE.get()[0], corner(2, 2).getY(),
                        9 + SITE.get()[2]);
                if (preview == null || !preview.box().equals(expected)) {
                    return since(ctx) > 60 ? StepResult.fail("Vorschau-Rahmen " + (preview == null ? "fehlt" : preview.box()) + " statt " + expected)
                            : StepResult.pending();
                }
                ctx.log("Vorschau-Rahmen " + preview.box());
                press(mc);
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                ZoneEditor editor = ZoneEditor.current();
                if (editor == null || !(mc.gui.screen() instanceof ZoneEditScreen)) {
                    return since(ctx) > 40 ? StepResult.fail("zweite Ecke öffnet den Zonen-Editor nicht") : StepResult.pending();
                }
                Zone draft = editor.draft();
                ZoneBox expected = ZoneMarker.box(corner(2, 2).getX(), corner(2, 2).getY(), corner(2, 2).getZ(), corner(10, 9).getX(),
                        corner(10, 9).getY(), corner(10, 9).getZ());
                if (!editor.model().isNew() || !draft.box().equals(expected) || !draft.ownedBy(player.getUUID()) || ZoneMarkerClient.first() != null) {
                    return StepResult.fail("Entwurf vom Markierer falsch: " + draft + " erwartet " + expected);
                }
                editor.model().setName(MARKED);
                editor.change(new ZoneDraft.Row(ZoneDraft.Kind.RULE, WorldRuleSpecs.GRAVITY_SCALE), -10);
                editor.change(new ZoneDraft.Row(ZoneDraft.Kind.RULE, WorldRuleSpecs.BATTERY_SCALE), -10);
                if (editor.draft().value(WorldRuleSpecs.GRAVITY_SCALE, -1) != 0 || editor.draft().value(WorldRuleSpecs.BATTERY_SCALE, -1) != 0) {
                    return StepResult.fail("Editor ändert Schwerkraft und Akku nicht: " + editor.draft());
                }
                if (editor.problem() != null || !editor.apply()) {
                    return StepResult.fail("Übernehmen fehlgeschlagen: " + editor.problem());
                }
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                Zone zone = stored(MARKED);
                if (zone == null) {
                    return since(ctx) > 200 ? StepResult.fail("markierte Zone kommt nicht an") : StepResult.pending();
                }
                if (!zone.ownedBy(player.getUUID()) || zone.value(WorldRuleSpecs.GRAVITY_SCALE, -1) != 0) {
                    return StepResult.fail("markierte Zone ohne Besitzer oder Wert: " + zone);
                }
                UUID me = player.getUUID();
                server(ctx, server -> {
                    Optional<Zone> saved = onServer(server, MARKED);
                    if (saved.isEmpty() || !saved.get().ownedBy(me)) {
                        return "Server speichert die Zone nicht mit Besitzer: " + saved;
                    }
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    static @Nullable WorldRulesTab tab() {
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

    static Optional<WorldRulesTab.Row> row(WorldRulesTab tab, String id) {
        return tab.row(Identifier.parse(id));
    }

    private static StepResult releaseStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        switch (phase(ctx)) {
            case 0 -> {
                WorldRulesTab tab = tab();
                if (tab == null || tab.rows().isEmpty()) {
                    return since(ctx) > 200 ? StepResult.fail("Tab Welt-Regeln öffnet nicht") : StepResult.pending();
                }
                List<String> problems = new ArrayList<>();
                for (WorldRulesTab.Row row : tab.rows()) {
                    RuleTierSwitch box = row.tier();
                    boolean capable = ZoneRelease.capable(row.id().toString());
                    if (box == null) {
                        if (capable) {
                            problems.add(row.id() + " ohne Schalter „Freigabe“");
                        }
                        continue;
                    }
                    if (!capable || !box.active || box.tier() != RuleTier.ZONES) {
                        problems.add(row.id() + " aktiv " + box.active + " Stufe " + box.tier());
                    }
                    if (row.widget() != null && row.widget().getX() + row.widget().getWidth() > box.getX()) {
                        problems.add(row.id() + ": Regler überdeckt den Schalter");
                    }
                }
                if (row(tab, WorldRuleSpecs.PLAYERS_CREATE_ZONES.id()).isEmpty()) {
                    problems.add("Regel „Spieler dürfen Zonen anlegen“ fehlt im Tab");
                }
                long addon = tab.rows().stream().filter(row -> !row.id().getNamespace().equals("propwash")).count();
                if (!problems.isEmpty()) {
                    return StepResult.fail(String.join("; ", problems));
                }
                ctx.log(tab.rows().size() + " Regeln, davon " + addon + " von Add-ons, " + PhysicsCount.count(tab) + " für Zonen freigegeben");
                WorldRulesTab.Row gravity = row(tab, WorldRuleSpecs.GRAVITY_SCALE.id()).orElseThrow();
                if (gravity.tier() == null) {
                    return StepResult.fail("Schwerkraft ohne Schalter „Freigabe“");
                }
                gravity.tier().set(RuleTier.FIXED);
                if (tab.tierDraft().get(Identifier.parse(WorldRuleSpecs.GRAVITY_SCALE.id())) != RuleTier.FIXED) {
                    return StepResult.fail("Stufe Fest landet nicht im Entwurf: " + tab.tierDraft());
                }
                WorldRulesTab.Row crash = row(tab, WorldRuleSpecs.CRASH_DAMAGE.id()).orElseThrow();
                if (crash.tier() != null) {
                    return StepResult.fail("„Schaden bei Absturz“ hat einen Schalter „Freigabe“, obwohl Zonen sie nie ändern");
                }
                if (tab.tierDraft().size() != 1) {
                    return StepResult.fail("Entwurf der Freigaben: " + tab.tierDraft());
                }
                if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                    screen.saveAndClose();
                }
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (ClientZones.INSTANCE.release().released(WorldRuleSpecs.GRAVITY_SCALE)) {
                    return since(ctx) > 200 ? StepResult.fail("Sperre kommt nicht beim Client an") : StepResult.pending();
                }
                server(ctx, server -> {
                    ZoneRelease release = ZoneData.get(server).release();
                    if (release.released(WorldRuleSpecs.GRAVITY_SCALE) || !release.released(WorldRuleSpecs.BATTERY_SCALE)) {
                        return "Weltdaten haben die Sperre nicht: " + release;
                    }
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static final class PhysicsCount {
        static int count(WorldRulesTab tab) {
            int count = 0;
            for (WorldRulesTab.Row row : tab.rows()) {
                if (row.spec() != null && tab.tier(row.spec()).zones()) {
                    count++;
                }
            }
            return count;
        }
    }

    private static StepResult lockedStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        switch (phase(ctx)) {
            case 0 -> {
                Zone zone = stored(MARKED);
                if (zone == null || ClientZones.INSTANCE.release().released(WorldRuleSpecs.GRAVITY_SCALE)) {
                    return StepResult.fail("Vorbedingung fehlt: markierte Zone und gesperrte Schwerkraft");
                }
                Zone applied = effective(zone.id());
                if (zone.value(WorldRuleSpecs.GRAVITY_SCALE, -1) != 0 || applied == null || applied.overrides(WorldRuleSpecs.GRAVITY_SCALE)
                        || applied.value(WorldRuleSpecs.BATTERY_SCALE, -1) != 0) {
                    return StepResult.fail("gesperrter Wert falsch: gespeichert " + zone + ", wirksam " + applied);
                }
                ZoneBox box = zone.box();
                ZoneResolution client = ZonePhysics.at(mc.level, box.centerX(), box.minY() + 1.0, box.centerZ());
                if (client.rules().gravityPercent() != ClientWorldRules.INSTANCE.physics().gravityPercent() || client.rules().batteryPercent() != 0) {
                    return StepResult.fail("Client wendet die gesperrte Regel an: " + client.rules());
                }
                String blocked = ZoneText.overrides(zone, ClientZones.INSTANCE.release()).getString();
                String marker = Component.translatable(ZoneText.OPEN_KEY + "blocked_value", "").getString().trim();
                if (!blocked.contains(marker)) {
                    return StepResult.fail("Tab zeigt den gesperrten Wert nicht als gesperrt: " + blocked);
                }
                if (!ZoneEditor.start(zone)) {
                    return StepResult.fail("Editor öffnet die eigene Zone nicht");
                }
                ZoneEditor editor = ZoneEditor.current();
                if (editor == null) {
                    return StepResult.fail("kein Editor");
                }
                List<ZoneDraft.Row> rows = editor.model().rows();
                ZoneDraft.Row locked = new ZoneDraft.Row(ZoneDraft.Kind.LOCKED, WorldRuleSpecs.GRAVITY_SCALE);
                if (!rows.contains(locked) || rows.contains(new ZoneDraft.Row(ZoneDraft.Kind.RULE, WorldRuleSpecs.GRAVITY_SCALE))) {
                    return StepResult.fail("Editor zeigt die gesperrte Regel nicht ausgegraut: " + rows);
                }
                ZoneEditPanel panel = new ZoneEditPanel(editor, true);
                String value = panel.value(locked, editor.draft()).getString();
                Component tip = panel.tooltip(locked);
                if (!value.startsWith("0") || tip == null || !tip.getString().contains(Component.translatable(WorldRuleSpecs.GRAVITY_SCALE.translationKey())
                        .getString())) {
                    return StepResult.fail("ausgegraute Zeile zeigt " + value + " / " + tip);
                }
                editor.change(locked, 5);
                editor.reset(locked);
                editor.change(ZoneDraft.Row.of(ZoneDraft.Kind.PRIORITY), 2);
                if (editor.draft().value(WorldRuleSpecs.GRAVITY_SCALE, -1) != 0 || editor.draft().priority() != 2 || !editor.apply()) {
                    return StepResult.fail("Editor ändert die gesperrte Regel oder speichert nicht: " + editor.draft());
                }
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                Zone zone = stored(MARKED);
                if (zone == null || zone.priority() != 2) {
                    return since(ctx) > 200 ? StepResult.fail("Änderung der Priorität kommt nicht an: " + zone) : StepResult.pending();
                }
                server(ctx, server -> {
                    Zone saved = onServer(server, MARKED).orElse(null);
                    if (saved == null || saved.value(WorldRuleSpecs.GRAVITY_SCALE, -1) != 0) {
                        return "Server verliert den gesperrten Wert: " + saved;
                    }
                    ServerPlayer player = player(server);
                    ZoneBox box = saved.box();
                    ZoneResolution resolution = ZoneServer.resolve(player.level(), box.centerX(), box.minY() + 1.0, box.centerZ());
                    if (resolution.rules().gravityPercent() != WorldRules.physics(server).gravityPercent() || resolution.rules().batteryPercent() != 0) {
                        return "Server wendet die gesperrte Regel an: " + resolution.rules();
                    }
                    Zone attempt = saved.withOverride(WorldRuleSpecs.GRAVITY_SCALE, 500);
                    if (!ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, attempt), player).ok()
                            || onServer(server, MARKED).orElseThrow().value(WorldRuleSpecs.GRAVITY_SCALE, -1) != 0) {
                        return "Paket ändert die gesperrte Regel: " + onServer(server, MARKED);
                    }
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    static ServerPlayer fake(MinecraftServer server, UUID id, String name) {
        return ZonesOpenJoinCheck.fake(server, id, name);
    }

    static @Nullable String rights(MinecraftServer server) {
        ServerPlayer alice = fake(server, ALICE, "zones_open_alice");
        ServerPlayer bob = fake(server, BOB, "zones_open_bob");
        String dimension = ZoneServer.dimension(player(server).level());
        boolean rule = WorldRules.on(server, WorldRuleSpecs.PLAYERS_CREATE_ZONES);
        try {
            WorldRules.set(server, WorldRuleSpecs.PLAYERS_CREATE_ZONES, 1);
            if (ZoneServer.mayEdit(alice) || ZoneServer.mayEdit(bob)) {
                return "Testspieler haben schon Operator-Rechte";
            }
            ZonesS2C view = ZoneServer.snapshotFor(alice);
            if (view.editable() || !view.creatable()) {
                return "Sync für Spieler ohne Recht falsch: editable " + view.editable() + ", creatable " + view.creatable();
            }
            Zone fresh = Zone.create(dimension, ALICE_ZONE, ZoneShape.BOX, ZoneBox.blocks(0, 200, 0, 1023, 400, 1023))
                    .withOverride(WorldRuleSpecs.BATTERY_SCALE, 0);
            if (ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, fresh), alice).ok()) {
                Zone saved = onServer(server, ALICE_ZONE).orElse(null);
                if (saved == null || !saved.ownedBy(ALICE)) {
                    return "Zone eines Spielers ohne Besitzer gespeichert: " + saved;
                }
            } else {
                return "Spieler darf mit Regel an keine Zone anlegen";
            }
            Zone alices = onServer(server, ALICE_ZONE).orElseThrow();
            if (ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, alices.withPriority(9)), bob).ok()
                    || ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.DELETE, alices), bob).ok()
                    || onServer(server, ALICE_ZONE).orElseThrow().priority() != 0) {
                return "fremder Spieler ändert oder löscht die Zone";
            }
            if (!ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, alices.withRaceLegal(true).withTransition(20)), alice).ok()
                    || !onServer(server, ALICE_ZONE).orElseThrow().raceLegal()) {
                return "Besitzer darf seine Zone nicht rennfrei machen";
            }
            ZoneServer.Outcome admin = ZoneServer.save(server, Zone.create(dimension, ADMIN_ZONE, ZoneShape.BOX, ZoneBox.blocks(0, 210, 0, 4, 214, 4)),
                    Component.literal("Selbsttest"), "Selbsttest");
            Zone adminZone = admin.zone();
            if (adminZone == null || adminZone.owned()) {
                return "Admin-Zone per Befehlsweg mit Besitzer: " + adminZone;
            }
            if (ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, adminZone.withPriority(4)), alice).ok()) {
                return "Spieler ändert eine Admin-Zone";
            }
            ServerPlayer host = player(server);
            if (!ZoneServer.mayEdit(host)) {
                return "Gastgeber ohne Recht auf die Welt-Regeln";
            }
            ZoneServer.Outcome operator = ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE,
                    onServer(server, ALICE_ZONE).orElseThrow().withPriority(5)), host);
            if (!operator.ok()) {
                return "Operator darf die Zone eines Spielers nicht ändern: " + operator.problem();
            }
            Zone changed = onServer(server, ALICE_ZONE).orElseThrow();
            if (changed.priority() != 5 || !changed.ownedBy(ALICE)) {
                return "Operator ändert den Besitzer oder die Priorität nicht: " + changed;
            }
            if (ZoneServer.release(new ZoneReleaseC2S(Map.of(WorldRuleSpecs.WIND.id(), false)), alice)
                    || !ZoneData.get(server).release().released(WorldRuleSpecs.WIND)) {
                return "Spieler ohne Recht sperrt eine Regel für Zonen";
            }
            WorldRules.set(server, WorldRuleSpecs.PLAYERS_CREATE_ZONES, 0);
            Zone current = onServer(server, ALICE_ZONE).orElseThrow();
            if (ZoneServer.snapshotFor(alice).creatable()
                    || ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, current.withPriority(1)), alice).ok()
                    || ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, fresh.withName("zones-open Alice 2")), alice).ok()
                    || ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.DELETE, current), alice).ok()) {
                return "Regel aus: Spieler darf trotzdem anlegen, ändern oder löschen";
            }
            WorldRules.set(server, WorldRuleSpecs.PLAYERS_CREATE_ZONES, 1);
            if (!ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.DELETE, current), alice).ok() || onServer(server, ALICE_ZONE).isPresent()) {
                return "Besitzer darf seine Zone nicht löschen";
            }
            return null;
        } finally {
            WorldRules.set(server, WorldRuleSpecs.PLAYERS_CREATE_ZONES, rule ? 1 : 0);
            onServer(server, ALICE_ZONE).ifPresent(zone -> ZoneData.get(server).remove(zone.id()));
        }
    }

    private static StepResult rightsStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (phase(ctx) == 0) {
            server(ctx, ZonesOpenSelfTests::rights);
            next(ctx);
            return StepResult.pending();
        }
        return StepResult.pass();
    }

    private static StepResult viewStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return StepResult.pending();
        }
        switch (phase(ctx)) {
            case 0 -> {
                if (stored(MARKED) == null || stored(ADMIN_ZONE) == null) {
                    return since(ctx) > 200 ? StepResult.fail("eigene und Admin-Zone fehlen beim Client") : StepResult.pending();
                }
                List<WorldRulesS2C.Entry> entries = new ArrayList<>();
                ClientWorldRules.INSTANCE.raw().forEach((id, value) -> entries.add(new WorldRulesS2C.Entry(id, value)));
                ClientWorldRules.INSTANCE.accept(new WorldRulesS2C(false, entries));
                ClientZones.INSTANCE.accept(new ZonesS2C(false, true, ClientZones.INSTANCE.syncedDimension(), ClientZones.INSTANCE.stored(),
                        ClientZones.INSTANCE.release()));
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                ZonesTab zones = zonesTab();
                if (zones == null || zones.zones().entries().isEmpty() || zones.zones().admin()) {
                    return since(ctx) > 200 ? StepResult.fail("Tab Zonen zeigt die eigene Zone nicht") : StepResult.pending();
                }
                List<String> problems = new ArrayList<>();
                for (ZoneSection.Entry entry : zones.zones().entries()) {
                    boolean own = entry.zone().ownedBy(player.getUUID());
                    if (!own) {
                        problems.add("fremde Zone „" + entry.zone().name() + "“ ohne Operator-Recht gelistet");
                    } else if (entry.edit() == null || entry.delete() == null) {
                        problems.add(entry.zone().name() + " ohne Knopf Bearbeiten oder Löschen");
                    }
                }
                if (entry(zones, MARKED) == null) {
                    problems.add("eigene Zone fehlt im Tab Zonen");
                }
                if (zones.zones().newButton() == null) {
                    problems.add("„Neue Zone hier“ fehlt, obwohl Spieler Zonen anlegen dürfen");
                }
                ((ConfiguratorScreen) mc.gui.screen()).selectTab(WorldRulesTab.ID);
                if (!(((ConfiguratorScreen) mc.gui.screen()).activeTab() instanceof WorldRulesTab tab)) {
                    return StepResult.fail("Tab Welt-Regeln öffnet nicht");
                }
                for (WorldRulesTab.Row row : tab.rows()) {
                    if (row.tier() != null && row.tier().active) {
                        problems.add(row.id() + ": Schalter „Freigabe“ ohne Recht bedienbar");
                    }
                }
                if (!zoneButtons(mc.gui.screen()).isEmpty()) {
                    problems.add("Tab Welt-Regeln zeigt Zonen-Knöpfe");
                }
                if (!problems.isEmpty()) {
                    return StepResult.fail(String.join("; ", problems));
                }
                Zone admin = stored(ADMIN_ZONE);
                Zone own = stored(MARKED);
                if (admin == null || own == null || ZoneEditor.start(admin) || !ZoneEditor.start(own)) {
                    return StepResult.fail("Editor-Rechte ohne Operator-Recht falsch");
                }
                ZoneEditor.stop();
                if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                    screen.discardAndClose();
                }
                ClientZones.INSTANCE.accept(new ZonesS2C(false, false, ClientZones.INSTANCE.syncedDimension(), ClientZones.INSTANCE.stored(),
                        ClientZones.INSTANCE.release()));
                if (ZoneMarkerClient.click(mc, false) || ZoneMarkerClient.first() != null || ZoneEditor.start(own)) {
                    return StepResult.fail("Regel aus: Markierer oder Editor trotzdem offen");
                }
                ClientZones.INSTANCE.accept(new ZonesS2C(false, true, ClientZones.INSTANCE.syncedDimension(), ClientZones.INSTANCE.stored(),
                        ClientZones.INSTANCE.release()));
                server(ctx, server -> {
                    ServerPlayer host = player(server);
                    PropwashNet.sendToPlayer(host, WorldRules.snapshotFor(host));
                    PropwashNet.sendToPlayer(host, ZoneServer.snapshotFor(host));
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (!ClientZones.INSTANCE.editable() || !ClientWorldRules.INSTANCE.editable()) {
                    return since(ctx) > 200 ? StepResult.fail("Rechte kommen nicht zurück") : StepResult.pending();
                }
                return StepResult.pass();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    static List<String> zoneButtons(Screen screen) {
        List<String> labels = List.of(Component.translatable(ZoneSection.KEY + "new").getString(), Component.translatable(ZoneSection.KEY + "edit").getString());
        List<String> found = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof FlatButton button && button.visible
                    && labels.contains(button.getMessage().getString())) {
                found.add(button.getMessage().getString());
            }
        }
        return found;
    }

    static @Nullable ZonesTab zonesTab() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            ConfigTabs.open(null, ZonesTab.ID);
            return null;
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !ZonesTab.ID.equals(active.id())) {
            screen.selectTab(ZonesTab.ID);
            return null;
        }
        return active instanceof ZonesTab tab ? tab : null;
    }

    static ZoneSection.@Nullable Entry entry(ZonesTab tab, String name) {
        for (ZoneSection.Entry entry : tab.zones().entries()) {
            if (ZoneCheck.sameName(entry.zone().name(), name)) {
                return entry;
            }
        }
        return null;
    }

    private static void closeScreen(Minecraft mc) {
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        } else if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
    }

    static @Nullable String menuZones(MinecraftServer server) {
        String problem = site(server);
        if (problem != null) {
            return problem;
        }
        ServerPlayer host = player(server);
        String dimension = ZoneServer.dimension(host.level());
        for (String name : List.of(MENU_OWN, MENU_OTHER, MENU_ADMIN)) {
            onServer(server, name).ifPresent(zone -> ZoneServer.delete(server, zone.id(), Component.literal("Selbsttest"), "Selbsttest"));
        }
        int[] site = SITE.get();
        double[] home = HOME.get();
        int hx = (int) Math.floor(home[0]);
        int hy = (int) Math.floor(home[1]);
        int hz = (int) Math.floor(home[2]);
        ZoneSelfTests.save(server, Zone.create(dimension, MENU_OWN, ZoneShape.BOX, ZoneBox.blocks(site[0], site[1] + 1, site[2], site[0] + PLATFORM - 1,
                site[1] + 6, site[2] + PLATFORM - 1)).withOverride(WorldRuleSpecs.BATTERY_SCALE, 0).withOwner(ZoneServer.owner(host)));
        ZoneSelfTests.save(server, Zone.create(dimension, MENU_OTHER, ZoneShape.BOX, ZoneBox.blocks(hx + 20, hy - 2, hz + 20, hx + 29, hy + 6, hz + 29))
                .withOwner(new ZoneOwner(ALICE, "zones_open_alice")));
        ZoneSelfTests.save(server, Zone.create(dimension, MENU_ADMIN, ZoneShape.CYLINDER, ZoneBox.cylinder(hx - 25.5, hy - 2, hz + 25.5, 5.0, 8.0)));
        host.teleportTo(host.level(), home[0], home[1], home[2], Set.of(), (float) home[3], (float) home[4], true);
        return null;
    }

    private static StepResult menuStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return StepResult.pending();
        }
        switch (phase(ctx)) {
            case 0 -> {
                WorldRulesTab tab = tab();
                if (tab == null || tab.rows().isEmpty() || since(ctx) < 3) {
                    return since(ctx) > 200 ? StepResult.fail("Tab Welt-Regeln öffnet nicht") : StepResult.pending();
                }
                List<String> problems = new ArrayList<>();
                List<String> buttons = zoneButtons(mc.gui.screen());
                if (!buttons.isEmpty()) {
                    problems.add("Tab Welt-Regeln zeigt noch Zonen-Knöpfe " + buttons);
                }
                for (WorldRulesTab.Row row : tab.rows()) {
                    if (row.tier() == null && ZoneRelease.capable(row.id().toString())) {
                        problems.add(row.id() + " ohne Schalter „Freigabe“");
                    }
                }
                ConfiguratorScreen screen = (ConfiguratorScreen) mc.gui.screen();
                boolean own = screen.tabs().stream().anyMatch(t -> ZONES_TAB.equals(t.id()));
                if (!own) {
                    problems.add("K-Menü ohne eigenen Tab „" + ZONES_TAB + "“");
                }
                screen.discardAndClose();
                if (!problems.isEmpty()) {
                    return StepResult.fail(String.join("; ", problems));
                }
                ZoneEditor.stop();
                ZoneMarkerClient.clear();
                ZoneFocus.clear();
                server(ctx, ZonesOpenSelfTests::menuZones);
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                Zone other = stored(MENU_OTHER);
                if (stored(MENU_OWN) == null || other == null || stored(MENU_ADMIN) == null || !ClientZones.INSTANCE.editable()) {
                    return since(ctx) > 200 ? StepResult.fail("Testzonen des Menüs kommen nicht an") : StepResult.pending();
                }
                if (!ZonesTab.open(other.id())) {
                    return StepResult.fail("ZonesTab.open öffnet den Tab nicht");
                }
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                ZonesTab tab = zonesTab();
                if (tab == null || tab.zones().entries().size() < 3 || since(ctx) < 3) {
                    return since(ctx) > 200 ? StepResult.fail("Tab Zonen zeigt die Testzonen nicht") : StepResult.pending();
                }
                List<String> problems = new ArrayList<>();
                boolean jump = ClientZones.mayJump();
                if (!jump) {
                    problems.add("Gastgeber mit Cheats ohne /tp im Befehlsbaum");
                }
                for (String name : List.of(MENU_OWN, MENU_OTHER, MENU_ADMIN)) {
                    ZoneSection.Entry entry = entry(tab, name);
                    if (entry == null) {
                        problems.add(name + " fehlt in der Liste des Operators");
                        continue;
                    }
                    if (!entry.owner() || entry.show() == null || entry.edit() == null || entry.delete() == null || (entry.jump() != null) != jump) {
                        problems.add(name + ": Besitzerzeile " + entry.owner() + ", Knöpfe " + entry.buttons().size());
                    }
                }
                Zone other = stored(MENU_OTHER);
                if (other == null || tab.focused() != other.id()) {
                    problems.add("ZonesTab.open hebt die Zone nicht hervor: " + tab.focused());
                }
                String shown = texts(mc);
                String alice = ZoneText.owner(other == null ? Zone.create("", "", ZoneShape.BOX, ZoneBox.blocks(0, 0, 0, 1, 1, 1)) : other).getString();
                String admin = Component.translatable(ZoneText.OPEN_KEY + "owner.admin").getString();
                if (!shown.contains(alice) || !shown.contains(admin)) {
                    problems.add("Besitzer nicht gezeichnet („" + alice + "“, „" + admin + "“)");
                }
                if (tab.zones().newButton() == null || !tab.zones().admin()) {
                    problems.add("Operator ohne „Neue Zone hier“");
                }
                if (!problems.isEmpty()) {
                    closeScreen(mc);
                    return StepResult.fail(String.join("; ", problems));
                }
                ctx.log("Operator sieht " + tab.zones().entries().size() + " Zonen mit Besitzer: " + alice + ", " + admin);
                ZoneSection.Entry target = entry(tab, MENU_ADMIN);
                target.show().press();
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                Zone admin = stored(MENU_ADMIN);
                Component last = ZoneFocus.last();
                if (admin == null || mc.gui.screen() != null || ZoneFocus.focusedId() != admin.id() || last == null) {
                    return since(ctx) > 100 ? StepResult.fail("„Zeigen“ schließt das Menü nicht oder hebt die Zone nicht hervor: Fokus "
                            + ZoneFocus.focusedId() + ", Hinweis " + last) : StepResult.pending();
                }
                if (!last.getString().contains(MENU_ADMIN)) {
                    return StepResult.fail("Hinweis nennt die Zone nicht: " + last.getString());
                }
                ctx.log("Zeigen: " + last.getString());
                ZoneFocus.clear();
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                ZonesTab tab = zonesTab();
                ZoneSection.Entry own = tab == null ? null : entry(tab, MENU_OWN);
                if (own == null || since(ctx) < 3) {
                    return since(ctx) > 200 ? StepResult.fail("Tab Zonen öffnet nicht erneut") : StepResult.pending();
                }
                if (own.jump() == null) {
                    closeScreen(mc);
                    return StepResult.fail("kein Knopf „Hinspringen“");
                }
                own.jump().press();
                next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                int[] site = SITE.get();
                double cx = site[0] + PLATFORM / 2 + 0.5;
                double cz = site[2] + PLATFORM / 2 + 0.5;
                boolean there = Math.abs(player.getX() - cx) < 1.1 && Math.abs(player.getZ() - cz) < 1.1 && Math.abs(player.getY() - (site[1] + 1.0)) < 0.6;
                if (!there || mc.gui.screen() != null) {
                    return since(ctx) > 200 ? StepResult.fail("„Hinspringen“ bringt den Spieler nicht auf den Boden der Zone: " + player.position()
                            + " statt " + cx + " " + (site[1] + 1) + " " + cz) : StepResult.pending();
                }
                ctx.log("Hinspringen: Spieler steht bei " + player.position());
                ZoneMarkerClient.clear();
                if (!ZoneMarkerClient.click(mc, true)) {
                    return StepResult.fail("Schleichen + Rechtsklick mit dem Markierer öffnet das Zonen-Menü nicht");
                }
                next(ctx);
                return StepResult.pending();
            }
            case 6 -> {
                ZonesTab tab = zonesTab();
                Zone own = stored(MENU_OWN);
                if (tab == null || own == null || since(ctx) < 3) {
                    return since(ctx) > 200 ? StepResult.fail("Markierer öffnet den Tab Zonen nicht") : StepResult.pending();
                }
                List<Integer> here = ZoneResolver.containing(ClientZones.INSTANCE.stored(), player.getX(), player.getY() + 0.1, player.getZ()).stream()
                        .map(Zone::id).toList();
                if (tab.focused() == 0 || !here.contains(tab.focused()) || !here.contains(own.id())) {
                    closeScreen(mc);
                    return StepResult.fail("Markierer hebt keine Zone am Ort hervor: " + tab.focused() + ", am Ort " + here);
                }
                ctx.log("Markierer: Tab Zonen mit Zone " + tab.focused() + " hervorgehoben (am Ort " + here + ")");
                ZoneSection.Entry other = entry(tab, MENU_OTHER);
                if (other == null || other.delete() == null) {
                    closeScreen(mc);
                    return StepResult.fail("fremde Zone ohne „Löschen“ beim Operator");
                }
                other.delete().press();
                if (!tab.zones().deleteArmed(other.zone().id()) || stored(MENU_OTHER) == null) {
                    closeScreen(mc);
                    return StepResult.fail("erster Klick auf „Löschen“ fragt nicht nach");
                }
                other.delete().press();
                next(ctx);
                return StepResult.pending();
            }
            case 7 -> {
                if (stored(MENU_OTHER) != null) {
                    return since(ctx) > 200 ? StepResult.fail("zweiter Klick auf „Löschen“ löscht die Zone nicht") : StepResult.pending();
                }
                ZonesTab tab = zonesTab();
                if (tab == null || entry(tab, MENU_OTHER) != null || since(ctx) < 3) {
                    return since(ctx) > 200 ? StepResult.fail("Liste zeigt die gelöschte Zone weiter") : StepResult.pending();
                }
                closeScreen(mc);
                List<WorldRulesS2C.Entry> entries = new ArrayList<>();
                ClientWorldRules.INSTANCE.raw().forEach((id, value) -> entries.add(new WorldRulesS2C.Entry(id, value)));
                ClientWorldRules.INSTANCE.accept(new WorldRulesS2C(false, entries));
                ClientZones.INSTANCE.accept(new ZonesS2C(false, true, ClientZones.INSTANCE.syncedDimension(), ClientZones.INSTANCE.stored(),
                        ClientZones.INSTANCE.release()));
                next(ctx);
                return StepResult.pending();
            }
            case 8 -> {
                ZonesTab tab = zonesTab();
                if (tab == null || tab.zones().admin() || since(ctx) < 3) {
                    return since(ctx) > 200 ? StepResult.fail("Tab Zonen ohne Operator-Recht öffnet nicht") : StepResult.pending();
                }
                List<String> problems = new ArrayList<>();
                for (ZoneSection.Entry entry : tab.zones().entries()) {
                    if (!entry.zone().ownedBy(player.getUUID())) {
                        problems.add("fremde Zone „" + entry.zone().name() + "“ ohne Operator-Recht gelistet");
                    }
                    if (entry.owner()) {
                        problems.add("Besitzerzeile ohne Operator-Recht bei " + entry.zone().name());
                    }
                }
                ZoneSection.Entry own = entry(tab, MENU_OWN);
                if (own == null || own.edit() == null || own.delete() == null || own.show() == null) {
                    problems.add("eigene Zone fehlt oder ohne Knöpfe");
                }
                if (tab.zones().newButton() == null) {
                    problems.add("„Neue Zone hier“ fehlt, obwohl Spieler Zonen anlegen dürfen");
                }
                String shown = texts(mc);
                String heading = ZoneSection.heading(false).getString();
                if (!shown.contains(heading)) {
                    problems.add("Überschrift „" + heading + "“ fehlt");
                }
                closeScreen(mc);
                if (!problems.isEmpty()) {
                    return StepResult.fail(String.join("; ", problems));
                }
                ctx.log("ohne Operator-Recht: nur eigene Zonen (" + tab.zones().entries().size() + "), Überschrift „" + heading + "“");
                server(ctx, server -> {
                    ServerPlayer host = player(server);
                    ServerPlayer alice = fake(server, ALICE, "zones_open_alice");
                    Zone own2 = onServer(server, MENU_OWN).orElse(null);
                    if (own2 == null) {
                        return "eigene Testzone fehlt am Server";
                    }
                    double x = alice.getX();
                    if (ZoneServer.mayJump(alice) || ZoneServer.jump(new ZoneJumpC2S(own2.id()), alice).ok() || alice.getX() != x) {
                        return "Spieler ohne /tp springt zu einer Zone";
                    }
                    if (!ZoneServer.mayJump(host)) {
                        return "Gastgeber mit Cheats darf nicht springen";
                    }
                    if (server instanceof IntegratedServer integrated) {
                        integrated.setWorldAllowCommands(false);
                    }
                    PropwashNet.sendToPlayer(host, WorldRules.snapshotFor(host));
                    PropwashNet.sendToPlayer(host, ZoneServer.snapshotFor(host));
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 9 -> {
                if (ClientZones.mayJump() || !ClientZones.INSTANCE.editable()) {
                    return since(ctx) > 200 ? StepResult.fail("ohne Cheats: /tp bleibt im Befehlsbaum oder Rechte kommen nicht zurück") : StepResult.pending();
                }
                ZonesTab tab = zonesTab();
                if (tab == null || entry(tab, MENU_OWN) == null || since(ctx) < 3) {
                    return since(ctx) > 200 ? StepResult.fail("Tab Zonen ohne Cheats öffnet nicht") : StepResult.pending();
                }
                for (ZoneSection.Entry entry : tab.zones().entries()) {
                    if (entry.jump() != null) {
                        closeScreen(mc);
                        return StepResult.fail("„Hinspringen“ ohne /tp bei " + entry.zone().name());
                    }
                }
                closeScreen(mc);
                server(ctx, server -> {
                    ServerPlayer host = player(server);
                    Zone own = onServer(server, MENU_OWN).orElse(null);
                    double y = host.getY();
                    boolean jumped = own != null && ZoneServer.jump(new ZoneJumpC2S(own.id()), host).ok();
                    if (server instanceof IntegratedServer integrated) {
                        integrated.setWorldAllowCommands(true);
                    }
                    return jumped || host.getY() != y ? "Gastgeber ohne Cheats springt trotzdem" : null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 10 -> {
                if (!ClientZones.mayJump()) {
                    return since(ctx) > 200 ? StepResult.fail("Cheats kommen nicht zurück") : StepResult.pending();
                }
                server(ctx, server -> {
                    for (String name : List.of(MENU_OWN, MENU_OTHER, MENU_ADMIN)) {
                        onServer(server, name).ifPresent(zone -> ZoneServer.delete(server, zone.id(), Component.literal("Selbsttest"), "Selbsttest"));
                    }
                    leaveSite(server);
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                if (stored(MENU_OWN) != null || stored(MENU_ADMIN) != null) {
                    return since(ctx) > 200 ? StepResult.fail("Testzonen des Menüs bleiben") : StepResult.pending();
                }
                return StepResult.pass();
            }
        }
    }

    private static StepResult jumpGroundStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (phase(ctx) == 0) {
            server(ctx, ZonesOpenSelfTests::jumpGround);
            next(ctx);
            return StepResult.pending();
        }
        return StepResult.pass();
    }

    static @Nullable String jumpGround(MinecraftServer server) {
        String problem = site(server);
        if (problem != null) {
            return problem;
        }
        ServerPlayer host = player(server);
        ServerLevel level = host.level();
        String dimension = ZoneServer.dimension(level);
        int[] site = SITE.get();
        double[] home = HOME.get();
        int cx = site[0] + PLATFORM / 2;
        int cz = site[2] + PLATFORM / 2;
        int floor = level.getMinY();
        List<BlockState> kept = new ArrayList<>();
        for (int dy = 0; dy <= 3; dy++) {
            kept.add(level.getBlockState(new BlockPos(cx, floor + dy, cz)));
        }
        try {
            if (!ZoneServer.mayJump(host)) {
                return "Gastgeber mit Cheats darf nicht springen";
            }
            if (site[1] + SKY_LIFT + 7 > level.getMaxY()) {
                return "Testplatz zu hoch für eine Zone " + SKY_LIFT + " Blöcke darüber: y " + site[1];
            }
            for (String name : List.of(JUMP_SKY, JUMP_NONE)) {
                onServer(server, name).ifPresent(zone -> ZoneServer.delete(server, zone.id(), Component.literal("Selbsttest"), "Selbsttest"));
            }
            for (int dy = 0; dy <= 3; dy++) {
                level.setBlockAndUpdate(new BlockPos(cx, floor + dy, cz), Blocks.BEDROCK.defaultBlockState());
            }
            Zone sky = ZoneSelfTests.save(server, Zone.create(dimension, JUMP_SKY, ZoneShape.BOX, ZoneBox.blocks(site[0], site[1] + SKY_LIFT, site[2],
                    site[0] + PLATFORM - 1, site[1] + SKY_LIFT + 6, site[2] + PLATFORM - 1)).withOwner(ZoneServer.owner(host)));
            Zone none = ZoneSelfTests.save(server, Zone.create(dimension, JUMP_NONE, ZoneShape.BOX, ZoneBox.blocks(cx - 1, floor, cz - 1, cx + 1, floor + 2,
                    cz + 1)).withOwner(ZoneServer.owner(host)));
            host.teleportTo(level, home[0], home[1], home[2], Set.of(), (float) home[3], (float) home[4], true);
            List<String> problems = new ArrayList<>();
            boolean landed = ZoneServer.jump(new ZoneJumpC2S(sky.id()), host).ok();
            Vec3 ground = new Vec3(cx + 0.5, site[1] + 1.0, cz + 0.5);
            if (!landed || host.position().distanceTo(ground) > 0.01) {
                problems.add("Zone " + SKY_LIFT + " Blöcke über dem Testplatz: Sprung " + (landed ? "angenommen" : "abgelehnt") + ", Spieler bei "
                        + host.position() + " statt auf dem Boden " + ground);
            }
            Vec3 before = host.position();
            boolean jumped = ZoneServer.jump(new ZoneJumpC2S(none.id()), host).ok();
            if (jumped || host.position().distanceTo(before) > 0.01) {
                problems.add("Zone ohne Boden am Weltboden: Sprung " + (jumped ? "angenommen" : "abgelehnt") + ", Spieler bei " + host.position()
                        + " statt bei " + before);
            }
            return problems.isEmpty() ? null : String.join("; ", problems);
        } finally {
            for (String name : List.of(JUMP_SKY, JUMP_NONE)) {
                onServer(server, name).ifPresent(zone -> ZoneServer.delete(server, zone.id(), Component.literal("Selbsttest"), "Selbsttest"));
            }
            for (int dy = 0; dy <= 3; dy++) {
                level.setBlockAndUpdate(new BlockPos(cx, floor + dy, cz), kept.get(dy));
            }
            leaveSite(server);
        }
    }

    static String texts(Minecraft mc) {
        StringBuilder joined = new StringBuilder();
        if (mc.gui.screen() != null) {
            for (LayoutAudit.Text text : LayoutProbe.capture(mc, mc.gui.screen()).texts()) {
                joined.append(text.value()).append(' ');
            }
        }
        return joined.toString();
    }

    private static StepResult languageKeptStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Path file = mc.gameDirectory.toPath().resolve("options.txt");
        String selected = mc.getLanguageManager().getSelected();
        String stored;
        try {
            stored = storedLanguage(file);
        } catch (IOException e) {
            return StepResult.fail("options.txt nicht lesbar: " + e);
        }
        if (!selected.equals(stored)) {
            return StepResult.fail("options.txt speichert lang:" + stored + ", der Lauf spricht " + selected
                    + " – der nächste Lauf in diesem Verzeichnis startete in der Sprache der Bildschirmfotos");
        }
        ctx.log("options.txt: lang:" + stored + " wie die Sprache des Laufs");
        return StepResult.pass();
    }

    static String storedLanguage(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return DEFAULT_LANGUAGE;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.startsWith("lang:")) {
                return line.substring("lang:".length()).trim();
            }
        }
        return DEFAULT_LANGUAGE;
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        if (phase(ctx) == 0) {
            ZoneEditor.stop();
            ZoneMarkerClient.clear();
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            server(ctx, server -> {
                for (String name : List.of(MARKED, ALICE_ZONE, ADMIN_ZONE)) {
                    onServer(server, name).ifPresent(zone -> ZoneServer.delete(server, zone.id(), Component.literal("Selbsttest"), "Selbsttest"));
                }
                ZoneServer.release(server, Map.of(WorldRuleSpecs.GRAVITY_SCALE.id(), true), Component.literal("Selbsttest"), "Selbsttest");
                WorldRules.set(server, WorldRuleSpecs.PLAYERS_CREATE_ZONES, 1);
                leaveSite(server);
                return null;
            });
            next(ctx);
            return StepResult.pending();
        }
        if (stored(MARKED) != null || stored(ADMIN_ZONE) != null || !ClientZones.INSTANCE.release().isDefault()) {
            return since(ctx) > 200 ? StepResult.fail("Testzonen oder Sperre bleiben") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static final class MarkerScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) {
                return StepResult.pending();
            }
            if (!ctx.state().containsKey(FUTURE) && !ctx.state().containsKey("ready")) {
                mc.options.tutorialStep = TutorialSteps.NONE;
                mc.getTutorial().start();
                ZoneEditor.stop();
                ZoneMarkerClient.clear();
                ctx.state().put(FUTURE, ctx.onServer(ZonesOpenSelfTests::site));
                return StepResult.pending();
            }
            CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
            if (future != null && !future.isDone()) {
                return StepResult.pending();
            }
            ctx.state().put("ready", true);
            ctx.state().remove(FUTURE);
            if (!atSite(mc)) {
                return ctx.tick() > 300 ? StepResult.fail("Spieler steht nicht auf dem Testplatz") : StepResult.pending();
            }
            if (ZoneMarkerClient.first() == null) {
                look(player, aim(corner(1, 1)));
                if (ZoneMarkerClient.target(mc) == null) {
                    return ctx.tick() > 300 ? StepResult.fail("Testplatz kommt am Client nicht an") : StepResult.pending();
                }
                if (!ZoneMarkerClient.click(mc, false)) {
                    return StepResult.fail("erste Ecke nicht gesetzt");
                }
            }
            look(player, aim(corner(11, 11)));
            return ZoneMarkerClient.preview(mc) != null ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ZoneMarkerClient.clear();
        }
    }

    private static final class EditorScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey("zone")) {
                ctx.state().put("zone", true);
                UUID me = mc.player == null ? null : mc.player.getUUID();
                String name = Component.translatable("propwash.zone.new_name", 7).getString();
                ctx.state().put("name", name);
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    site(server);
                    ServerPlayer player = player(server);
                    int[] site = SITE.get();
                    Zone zone = Zone.create(ZoneServer.dimension(player.level()), name, ZoneShape.BOX, ZoneMarker.box(site[0] + 1, site[1] + 1, site[2] + 1,
                                    site[0] + 11, site[1] + 1, site[2] + 11))
                            .withOverride(WorldRuleSpecs.GRAVITY_SCALE, 0).withOverride(WorldRuleSpecs.BATTERY_SCALE, 0)
                            .withOwner(new ZoneOwner(me == null ? player.getUUID() : me, player.getGameProfile().name()));
                    onServer(server, name).ifPresent(old -> ZoneServer.delete(server, old.id(), Component.literal("Bildschirmfoto"), "Bildschirmfoto"));
                    ZoneSelfTests.save(server, zone);
                    ZoneServer.release(server, Map.of(WorldRuleSpecs.GRAVITY_SCALE.id(), false), Component.literal("Bildschirmfoto"), "Bildschirmfoto");
                }));
                return StepResult.pending();
            }
            CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Szene: " + future.exceptionNow());
            }
            Zone zone = stored((String) ctx.state().get("name"));
            if (!future.isDone() || zone == null || ClientZones.INSTANCE.release().released(WorldRuleSpecs.GRAVITY_SCALE) || !atSite(mc)) {
                return ctx.tick() > 300 ? StepResult.fail("Szene kommt nicht an") : StepResult.pending();
            }
            if (!ZoneEditor.active()) {
                if (mc.player != null) {
                    look(mc.player, Vec3.atCenterOf(corner(11, 11)).add(0.0, 2.0, 0.0));
                }
                if (!ZoneEditor.start(zone) || !ZoneEditScreen.openSticky()) {
                    return StepResult.fail("Editor startet nicht");
                }
                ZoneEditor editor = ZoneEditor.current();
                if (editor != null) {
                    editor.model().select(new ZoneDraft.Row(ZoneDraft.Kind.LOCKED, WorldRuleSpecs.GRAVITY_SCALE));
                }
                return StepResult.pending();
            }
            return mc.gui.screen() instanceof ZoneEditScreen screen && screen.panel() != null ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof ZoneEditScreen screen) || screen.panel() == null) {
                Screenshots.parkMouse(mc);
                return;
            }
            ZoneEditPanel panel = screen.panel();
            ZoneDraft.Row target = new ZoneDraft.Row(ZoneDraft.Kind.LOCKED, WorldRuleSpecs.GRAVITY_SCALE);
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
            String name = (String) ctx.state().get("name");
            ctx.onServer(server -> {
                if (name != null) {
                    onServer(server, name).ifPresent(zone -> ZoneServer.delete(server, zone.id(), Component.literal("Bildschirmfoto"), "Bildschirmfoto"));
                }
                ZoneServer.release(server, Map.of(WorldRuleSpecs.GRAVITY_SCALE.id(), true), Component.literal("Bildschirmfoto"), "Bildschirmfoto");
            });
        }
    }

    private static final class TabScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            WorldRulesTab tab = tab();
            if (tab == null || tab.rows().isEmpty()) {
                return ctx.tick() > 300 ? StepResult.fail("Tab öffnet nicht") : StepResult.pending();
            }
            WorldRulesTab.Row gravity = row(tab, WorldRuleSpecs.GRAVITY_SCALE.id()).orElse(null);
            if (gravity == null || gravity.tier() == null) {
                return StepResult.fail("Schwerkraft ohne Schalter „Freigabe“");
            }
            tab.reveal(gravity);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            WorldRulesTab tab = mc.gui.screen() instanceof ConfiguratorScreen screen && screen.activeTab() instanceof WorldRulesTab rules ? rules : null;
            WorldRulesTab.Row gravity = tab == null ? null : row(tab, WorldRuleSpecs.GRAVITY_SCALE.id()).orElse(null);
            if (gravity == null || gravity.tier() == null) {
                Screenshots.parkMouse(mc);
                return;
            }
            double gx = gravity.tier().getX() + gravity.tier().getWidth() / 2.0;
            double gy = gravity.tier().getY() + gravity.tier().getHeight() / 2.0;
            double sx = gx * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            double sy = gy * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
            mc.mouseHandler.onMove(mc.getWindow().handle(), sx, sy, 0.0, 0.0);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
        }
    }
}
