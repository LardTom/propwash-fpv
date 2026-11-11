package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class GateStackRulesSelfTests {
    private static final String GROUP = "race-topo: Stapelregeln – ";
    private static final String SHOTS = "bildschirmfotos: Stapelregeln – ";
    private static final AtomicReference<CovRaceSite.Site> SITE = new AtomicReference<>();
    private static final AtomicInteger RULE = new AtomicInteger(Integer.MIN_VALUE);
    private static final int[] BOUNDS = {-2, 28, -4, 10, 12};
    private static final Vec3 STAND = new Vec3(13.0, 0.0, 9.0);
    private static final String LOWER = "stack_lower";
    private static final String UPPER = "stack_upper";
    private static final String NARROW = "narrow";
    private static final String WIDE = "wide";
    private static final String OLD_LOW = "old_low";
    private static final String OLD_WIDE = "old_wide";
    private static final String BASE = "place_base";
    private static final double STACK_X = 2.5;
    private static final double PAIR_X = 9.5;
    private static final double OLD_X = 16.5;
    private static final double PLACE_X = 23.5;
    private static final double GATE_Z = 0.5;
    private static final double WIDE_OPENING = 5.5;
    private static final double LIFT = 0.5;
    private static final int TIP_TICKS = 320;
    private static final String OPENED = "Menü offen seit";

    private GateStackRulesSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Aufbau: Stapel, schmales Gate mit breitem knapp darunter, alter Stapel aus rc.1, freie Platzierung an", 900,
                GateStackRulesSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "jedes Gate des Stapels meldet beim Verstellen dasselbe, beim Client und am Server", 300,
                GateStackRulesSelfTests::sameStep);
        SelfTest.clientStep(GROUP + "breites Gate mit Position Y +0,5 auf das schmale wird abgelehnt, schmal gemacht passt es", 300,
                GateStackRulesSelfTests::moveStep);
        SelfTest.clientStep(GROUP + "alter Stapel: breites Gate auf schmalem steht auf eigenen Beinen, der Editor prüft den neuen Zustand", 300,
                GateStackRulesSelfTests::oldStackStep);
        SelfTest.clientStep(GROUP + "Gate-Item in Sitzhöhe eines schmaleren Gates wird abgelehnt, ein schmaleres stapelt sich", 300,
                GateStackRulesSelfTests::placeStep);
        if (Screenshots.enabled()) {
            registerShots("");
            SelfTest.switchStep(SHOTS + "Sprache Deutsch", SHOTS + "Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
            Screenshots.step(SHOTS + "breites Gate auf schmalem im Gate-Menü abgelehnt deutsch", "race_stack_rules_refused_de", 30,
                    new RefusedShot());
            SelfTest.clientStep(SHOTS + "Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        }
        SelfTest.clientStep(GROUP + "Aufräumen", 300, GateStackRulesSelfTests::cleanupStep);
    }

    private static void registerShots(String suffix) {
        Screenshots.step(SHOTS + "breites Gate auf schmalem im Gate-Menü abgelehnt", "race_stack_rules_refused" + suffix, 30, new RefusedShot());
        Screenshots.step(SHOTS + "alter Stapel: breites Gate auf eigenen Beinen", "race_stack_rules_own_legs" + suffix, 30, new LegsShot());
    }

    private static GateGeometry gate(CovRaceSite.Site site, double x, double y, double opening) {
        Vec3 at = site.raw(x, y, GATE_Z);
        GateSize size = GateSize.MEDIUM;
        return GateGeometry.of(at.x, at.y, at.z, 0.0F, size, GateMount.STANDING, false, size.outer(opening), size.height(), GateTilt.NONE);
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult wait = CovRaceSite.prepare(ctx, SITE, -170, 230, BOUNDS, STAND);
        if (wait.status() != StepResult.Status.PASS) {
            return wait;
        }
        wait = CovRaceSite.once(ctx, "Aufbau", server -> {
            ServerPlayer player = CovRaceSite.player(server);
            ServerLevel level = player.level();
            CovRaceSite.Site site = SITE.get();
            RULE.set(WorldRules.values(level).get(WorldRuleSpecs.FREE_COURSE_PLACEMENT));
            WorldRules.set(server, WorldRuleSpecs.FREE_COURSE_PLACEMENT, 1);
            GateGeometry lower = gate(site, STACK_X, 0.0, 3.0);
            put(level, site, LOWER, lower);
            put(level, site, UPPER, GateStacking.stackOn(lower, GateSize.MEDIUM, 0.0F));
            GateGeometry narrow = gate(site, PAIR_X, 0.0, 3.0);
            put(level, site, NARROW, narrow);
            put(level, site, WIDE, gate(site, PAIR_X, GateStacking.seat(narrow) - site.origin().getY() - LIFT, WIDE_OPENING));
            GateGeometry old = gate(site, OLD_X, 0.0, 3.0);
            put(level, site, OLD_LOW, old);
            put(level, site, OLD_WIDE, gate(site, OLD_X, GateStacking.seat(old) - site.origin().getY(), WIDE_OPENING));
            put(level, site, BASE, gate(site, PLACE_X, LIFT, 3.0));
            CovRaceSite.set(level, site, site.block((int) Math.floor(PLACE_X), 3, 0), Blocks.SMOOTH_STONE.defaultBlockState());
            Vec3 feet = site.raw(STAND.x, 0.0, STAND.z);
            player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), 180.0F, 15.0F, true);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
            return null;
        });
        if (wait != null) {
            return wait;
        }
        Minecraft mc = Minecraft.getInstance();
        GateEntity upper = element(UPPER);
        GateEntity lower = element(LOWER);
        boolean seen = mc.player != null && GateMounting.holdsTool(mc.player) && upper != null && lower != null && element(NARROW) != null
                && element(WIDE) != null && element(OLD_LOW) != null && element(OLD_WIDE) != null && element(BASE) != null
                && ClientWorldRules.INSTANCE.on(WorldRuleSpecs.FREE_COURSE_PLACEMENT);
        if (!seen || !upper.supported() || !lower.covered()) {
            return ctx.tick() > 800 ? StepResult.fail("Aufbau kommt beim Client nicht an (Gates, Kurs-Werkzeug, Regel oder Stapel)")
                    : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static void put(ServerLevel level, CovRaceSite.Site site, String key, GateGeometry geometry) {
        site.ids().put(key, CovRaceSite.spawn(level, geometry, GateSize.MEDIUM).getId());
    }

    private static @Nullable GateEntity element(String key) {
        return CovRaceSite.clientEntity(SITE.get(), key);
    }

    private static @Nullable GateEditor open(String key) {
        Minecraft mc = Minecraft.getInstance();
        GateEntity entity = element(key);
        if (entity == null) {
            return null;
        }
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        GateEditor.muteStartNotice(true);
        GateEditor.start(entity);
        GateEditor editor = GateEditor.current();
        return editor != null && editor.gate() == entity ? editor : null;
    }

    private static String key(Component text) {
        return text.getContents() instanceof TranslatableContents contents ? contents.getKey() : text.getString();
    }

    private static @Nullable String expect(String key, String what, GateEditModel.Row row, double value, GateEditor.Status want) {
        GateEditor editor = open(key);
        if (editor == null) {
            return key + ": Gate-Menü startet nicht";
        }
        try {
            editor.set(row, value);
            if (editor.status() != want) {
                return key + " " + what + ": " + editor.status() + " statt " + want;
            }
            if (want == GateEditor.Status.TOO_WIDE && !key(GateEditor.statusText(editor.status())).equals("propwash.race.gate.stack_too_wide")) {
                return key + " " + what + ": Meldung " + key(GateEditor.statusText(editor.status()));
            }
            return null;
        } finally {
            GateEditor.stop();
        }
    }

    private static GateMounting.Result edit(ServerPlayer player, GateEntity gate, double openingWidth, double dy) {
        GateGeometry geometry = gate.geometry();
        return GateMounting.edit(player, gate, geometry.heading(), 0.0F, 0.0F, openingWidth, gate.openingHeight(), gate.variant(), gate.score(),
                0.0, dy, 0.0);
    }

    private static StepResult sameStep(SelfTest.StepContext ctx) {
        for (String key : List.of(LOWER, UPPER)) {
            String problem = expect(key, "Breite 4", GateEditModel.Row.WIDTH, 4.0, GateEditor.Status.STACKED);
            if (problem == null) {
                problem = expect(key, "Position Y −0,5", GateEditModel.Row.MOVE_Y, -LIFT, GateEditor.Status.STACKED);
            }
            if (problem != null) {
                return StepResult.fail(problem);
            }
        }
        StepResult wait = CovRaceSite.once(ctx, "Server", server -> {
            ServerPlayer player = CovRaceSite.player(server);
            ServerLevel level = player.level();
            for (String key : List.of(LOWER, UPPER)) {
                GateEntity gate = CovRaceSite.require(level, SITE.get(), key);
                GateGeometry before = gate.geometry();
                GateMounting.Result wider = edit(player, gate, 4.0, 0.0);
                GateMounting.Result lower = edit(player, gate, gate.openingWidth(), -LIFT);
                if (wider != GateMounting.Result.STACK_LOCKED || lower != GateMounting.Result.STACK_LOCKED || !gate.geometry().sameAs(before)) {
                    return key + ": Server meldet " + wider + " und " + lower + " statt STACK_LOCKED";
                }
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("unteres und oberes Gate des Stapels: Breite und Position gesperrt, Client und Server gleich");
        return StepResult.pass();
    }

    private static StepResult moveStep(SelfTest.StepContext ctx) {
        String problem = expect(WIDE, "Position Y +0,5", GateEditModel.Row.MOVE_Y, LIFT, GateEditor.Status.TOO_WIDE);
        if (problem != null) {
            return StepResult.fail(problem);
        }
        GateEditor editor = open(WIDE);
        if (editor == null) {
            return StepResult.fail("Gate-Menü des breiten Gates startet nicht");
        }
        try {
            editor.set(GateEditModel.Row.MOVE_Y, LIFT);
            editor.set(GateEditModel.Row.WIDTH, 3.0);
            if (editor.status() != GateEditor.Status.FREE) {
                return StepResult.fail("schmal gemacht und auf das schmale Gate geschoben: " + editor.status() + " statt FREE");
            }
        } finally {
            GateEditor.stop();
        }
        StepResult wait = CovRaceSite.once(ctx, "Server", server -> {
            ServerPlayer player = CovRaceSite.player(server);
            ServerLevel level = player.level();
            GateEntity wide = CovRaceSite.require(level, SITE.get(), WIDE);
            GateEntity narrow = CovRaceSite.require(level, SITE.get(), NARROW);
            GateGeometry before = wide.geometry();
            GateMounting.Result result = edit(player, wide, WIDE_OPENING, LIFT);
            narrow.refreshColumn();
            wide.refreshColumn();
            if (result != GateMounting.Result.STACK_TOO_WIDE) {
                return "Server meldet " + result + " statt STACK_TOO_WIDE";
            }
            if (!wide.geometry().sameAs(before) || narrow.covered() || wide.supported()) {
                return "breites Gate trotzdem auf das schmale gesetzt: " + wide.geometry() + " unten bedeckt=" + narrow.covered();
            }
            Component message = GateMounting.message(result, wide);
            if (message == null || !key(message).equals("propwash.race.gate.stack_too_wide")) {
                return "Meldung am Server: " + message;
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("Position Y +0,5 mit Öffnung 5,5 auf Öffnung 3: Client TOO_WIDE, Server STACK_TOO_WIDE; mit Öffnung 3 FREE");
        return StepResult.pass();
    }

    private static StepResult oldStackStep(SelfTest.StepContext ctx) {
        GateEntity low = element(OLD_LOW);
        GateEntity wide = element(OLD_WIDE);
        if (low == null || wide == null) {
            return StepResult.fail("alter Stapel fehlt beim Client");
        }
        if (low.covered() || wide.supported() || wide.inStack() || low.inStack() || wide.base() != GateStacking.Base.BAR) {
            return StepResult.fail("Client: breites Gate gehört zum Stapel (bedeckt=" + low.covered() + ", getragen=" + wide.supported() + ", Fuß="
                    + wide.base() + ")");
        }
        String problem = expect(OLD_WIDE, "Breite 6", GateEditModel.Row.WIDTH, 6.0, GateEditor.Status.TOO_WIDE);
        if (problem == null) {
            problem = expect(OLD_WIDE, "Breite 3", GateEditModel.Row.WIDTH, 3.0, GateEditor.Status.FREE);
        }
        if (problem == null) {
            problem = expect(OLD_LOW, "Breite 6", GateEditModel.Row.WIDTH, 6.0, GateEditor.Status.FREE);
        }
        if (problem == null) {
            problem = expect(OLD_LOW, "Breite 2", GateEditModel.Row.WIDTH, 2.0, GateEditor.Status.TOO_WIDE);
        }
        if (problem != null) {
            return StepResult.fail(problem);
        }
        StepResult wait = CovRaceSite.once(ctx, "Server", server -> {
            ServerLevel level = CovRaceSite.player(server).level();
            GateEntity serverLow = CovRaceSite.require(level, SITE.get(), OLD_LOW);
            GateEntity serverWide = CovRaceSite.require(level, SITE.get(), OLD_WIDE);
            serverLow.refreshColumn();
            serverWide.refreshColumn();
            if (serverLow.covered() || serverWide.supported() || serverWide.base() != GateStacking.Base.BAR || serverLow.stack().size() != 1
                    || serverWide.stack().size() != 1) {
                return "Server: breites Gate gehört zum Stapel (Fuß " + serverWide.base() + ", Stapel " + serverWide.stack().size() + ")";
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("alter Stapel: breites Gate mit Fuß BAR, beide Gates einzeln; Menü verbreitern TOO_WIDE, schmal FREE, unten 6 FREE, unten 2 TOO_WIDE");
        return StepResult.pass();
    }

    private static InteractionResult useOnFloor(ServerPlayer player, GateSize size, BlockPos floor) {
        ItemStack previous = player.getMainHandItem().copy();
        try {
            CovRaceSite.face(player, 180.0F);
            ItemStack stack = new ItemStack(RaceModule.gateItem(size).orElseThrow());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockHitResult hit = new BlockHitResult(new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5), Direction.UP, floor, false);
            return stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
    }

    private static List<GateEntity> onSeat(ServerLevel level, GateEntity base) {
        double seat = GateStacking.seat(base.geometry());
        AABB area = new AABB(base.getX() - 0.1, seat - 0.1, base.getZ() - 0.1, base.getX() + 0.1, seat + 0.1, base.getZ() + 0.1);
        return level.getEntitiesOfClass(GateEntity.class, area, gate -> gate != base && !gate.isRemoved() && Math.abs(gate.getY() - seat) < 1.0E-6);
    }

    private static StepResult placeStep(SelfTest.StepContext ctx) {
        StepResult wait = CovRaceSite.once(ctx, "Setzen", server -> {
            ServerPlayer player = CovRaceSite.player(server);
            ServerLevel level = player.level();
            CovRaceSite.Site site = SITE.get();
            GateEntity base = CovRaceSite.require(level, site, BASE);
            BlockPos floor = site.block((int) Math.floor(PLACE_X), 3, 0);
            if (Math.abs(floor.getY() + 1.0 - GateStacking.seat(base.geometry())) > 1.0E-6) {
                return "Block liegt nicht in Sitzhöhe: " + (floor.getY() + 1) + " statt " + GateStacking.seat(base.geometry());
            }
            InteractionResult large = useOnFloor(player, GateSize.LARGE, floor);
            if (large.consumesAction() || !onSeat(level, base).isEmpty()) {
                return "großes Gate in Sitzhöhe des mittleren gesetzt: " + large + ", " + onSeat(level, base).size() + " Gates auf dem Sitz";
            }
            InteractionResult small = useOnFloor(player, GateSize.SMALL, floor);
            List<GateEntity> seated = onSeat(level, base);
            if (!small.consumesAction() || seated.size() != 1) {
                return "kleines Gate in Sitzhöhe nicht gesetzt: " + small + ", " + seated.size() + " Gates auf dem Sitz";
            }
            seated.getFirst().refreshColumn();
            if (!base.covered() || !seated.getFirst().supported()) {
                return "kleines Gate auf dem mittleren bildet keinen Stapel";
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("großes Gate in Sitzhöhe abgelehnt, kleines Gate gesetzt und gestapelt");
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        GateEditor.stop();
        Minecraft.getInstance().gui.setScreen(null);
        CovRaceSite.Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        StepResult wait = CovRaceSite.once(ctx, "Abbau", server -> {
            restoreRule(server);
            return CovRaceSite.teardown(server, site);
        });
        if (wait != null) {
            return wait;
        }
        SITE.set(null);
        return StepResult.pass();
    }

    private static void restoreRule(MinecraftServer server) {
        int previous = RULE.getAndSet(Integer.MIN_VALUE);
        if (previous != Integer.MIN_VALUE) {
            WorldRules.set(server, WorldRuleSpecs.FREE_COURSE_PLACEMENT, previous);
        }
    }

    private static void look(Minecraft mc, double x, double y) {
        CovRaceSite.Site site = SITE.get();
        if (mc.player != null && site != null) {
            CovRaceSite.lookAt(mc.player, site.raw(x, y, GATE_Z));
        }
    }

    private static final class RefusedShot implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || element(WIDE) == null) {
                return StepResult.fail("Aufbau für das Bildschirmfoto fehlt");
            }
            mc.getTutorial().setStep(TutorialSteps.NONE);
            look(mc, PAIR_X + 4.5, 2.8);
            GateEditor editor = GateEditor.current();
            if (editor == null || editor.gate() != element(WIDE)) {
                editor = open(WIDE);
                if (editor == null) {
                    return StepResult.fail("Menü für das Bildschirmfoto startet nicht");
                }
                editor.showPage(2);
                editor.set(GateEditModel.Row.MOVE_Y, LIFT);
                ctx.state().put(OPENED, ctx.tick());
            }
            if (editor.status() != GateEditor.Status.TOO_WIDE) {
                return ctx.tick() > 300 ? StepResult.fail("Vorschau nicht abgelehnt: " + editor.status()) : StepResult.pending();
            }
            return ctx.tick() - (int) ctx.state().get(OPENED) < TIP_TICKS ? StepResult.pending() : StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.toastManager().clear();
            look(mc, PAIR_X + 4.5, 2.8);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            GateEditor.stop();
            Minecraft.getInstance().gui.setScreen(null);
        }
    }

    private static final class LegsShot implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            GateEntity wide = element(OLD_WIDE);
            if (mc.player == null || wide == null) {
                return StepResult.fail("Aufbau für das Bildschirmfoto fehlt");
            }
            GateEditor.stop();
            mc.getTutorial().setStep(TutorialSteps.NONE);
            look(mc, OLD_X + 0.5, 3.4);
            return wide.base() == GateStacking.Base.BAR ? StepResult.pass()
                    : ctx.tick() > 300 ? StepResult.fail("breites Gate ohne eigene Beine: " + wide.base()) : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.toastManager().clear();
            look(mc, OLD_X + 0.5, 3.4);
        }
    }
}
