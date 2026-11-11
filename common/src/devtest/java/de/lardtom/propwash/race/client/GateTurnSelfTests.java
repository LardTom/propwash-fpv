package de.lardtom.propwash.race.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.FreeformBlocks;
import de.lardtom.propwash.race.FreeformFill;
import de.lardtom.propwash.race.FreeformRegion;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GatePoles;
import de.lardtom.propwash.race.GateSignalTarget;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RecordGhosts;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.TextFit;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.LayoutProbe;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class GateTurnSelfTests {
    private static final String GROUP = "race: Feinschritt und Drehen – ";
    private static final String SHOTS = "bildschirmfotos: Feinschritt und Drehen – ";
    private static final AtomicReference<CovRaceSite.Site> SITE = new AtomicReference<>();
    private static final int[] BOUNDS = {-4, 16, -4, 9, 8};
    private static final Vec3 STAND = new Vec3(3.5, 0.0, 7.5);
    private static final String GATE = "gate";
    private static final String POLE = "pole";
    private static final String FREEFORM = "freeform";
    private static final int SLAB_Y = 2;
    private static final float[] TURNS = {0.0F, 90.0F, 180.0F, 270.0F};
    private static final int[][] OUTPUTS = {
            {11, -1, GateSignalTarget.SOUTH},
            {13, 0, GateSignalTarget.WEST},
            {11, 2, GateSignalTarget.NORTH},
            {9, 1, GateSignalTarget.EAST}};
    private static final int[][] PLACED = {
            {11, 4, 11, 4, GateSignalTarget.SOUTH},
            {13, 5, 13, 5, GateSignalTarget.WEST},
            {11, 7, 11, 7, GateSignalTarget.NORTH},
            {9, 6, 9, 6, GateSignalTarget.EAST}};
    private static final Direction[] PLACE_FACES = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
    private static final String[] LOOKS = {"Süden", "Westen", "Norden", "Osten"};
    private static final float PLACE_PITCH = 60.0F;
    private static final String COURSE = "course-turn";
    private static final String FRAME = "race_5";
    private static final long LAP_NANOS = 23_000_000_000L;
    private static final AtomicReference<int[]> COURSE_START = new AtomicReference<>(new int[2]);
    private static final AtomicReference<String> COURSE_REPORT = new AtomicReference<>("");
    private static final double HALF_PIXEL = 1.0 / 32.0;
    private static volatile double gateY = Double.NaN;

    private GateTurnSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Aufbau: Gate, Gate-Stange und liegendes Freiform-Gate über einer Öffnung", 900, GateTurnSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "Gate-Menü ohne Maus zeigt den Tooltip der gewählten Zeile, Strg-Schritte beschriftet", 300,
                GateTurnSelfTests::tooltipStep);
        SelfTest.clientStep(GROUP + "Strg+Umschalt schiebt um 1/32 Block, Strg um 1/16, der Server übernimmt 1/32", 400, GateTurnSelfTests::nudgeStep);
        SelfTest.clientStep(GROUP + "Fuß der Gate-Stange bei 0°, 90°, 180° und 270° gleich", 300, GateTurnSelfTests::footStep);
        SelfTest.clientStep(GROUP + "Redstone-Ausgang des liegenden Freiform-Gates dreht mit seiner Ausrichtung", 1200, GateTurnSelfTests::outputStep);
        SelfTest.clientStep(GROUP + "Fußzeile des Freiform-Menüs je Zeile und Seite: Umkehren, 90° drehen, Rand umschalten, Redstone mit Strg: 1 Tick", 300,
                GateTurnSelfTests::footerStep);
        SelfTest.clientStep(GROUP + "Liegendes Freiform-Gate mit Blick nach Süden, Westen, Norden und Osten gesetzt: Ausgang auf der Seite des Spielers",
                400, GateTurnSelfTests::placeStep);
        SelfTest.clientStep(GROUP + "Ausrichtung im Kurs: Bestenliste, Rekord-Geist und Layout bleiben, Umkehren archiviert weiter", 1200,
                GateTurnSelfTests::courseStep);
        if (Screenshots.enabled()) {
            for (int i = 0; i < TURNS.length; i++) {
                int turn = (int) TURNS[i];
                Screenshots.step(SHOTS + "Gate-Stange bei " + turn + "°", "race_turn_pole_" + turn, 30, new View(new Vec3(8.6, 0.0, 2.6),
                        new Vec3(6.5, 0.15, 0.5), POLE, turn, false, true));
            }
            for (int i = 0; i < TURNS.length; i++) {
                int turn = (int) TURNS[i];
                Screenshots.step(SHOTS + "Freiform-Gate liegend, Ausgang bei " + turn + "°", "race_turn_freeform_output_" + turn, 30,
                        new View(new Vec3(11.5, 4.0, 5.0), new Vec3(11.5, 2.0, 0.6), FREEFORM, turn, true, false));
            }
            Screenshots.step(SHOTS + "Menü des liegenden Freiform-Gates, Zeile Ausrichtung", "race_turn_freeform_menu", 30,
                    new View(new Vec3(14.0, 7.0, 9.5), new Vec3(18.5, 5.0, 0.0), FREEFORM, 90, true, true));
            Screenshots.step(SHOTS + "Gate-Menü ohne Maus, Tooltip der Zeile X", "race_turn_menu_tooltip", 30,
                    new View(STAND, new Vec3(0.5, 1.5, 0.5), GATE, -1, true, true));
        }
        SelfTest.clientStep(GROUP + "Aufräumen", CovRaceSite.HOME_TICKS + 100, GateTurnSelfTests::cleanupStep);
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult wait = CovRaceSite.prepare(ctx, SITE, 190, 210, BOUNDS, STAND);
        if (wait.status() != StepResult.Status.PASS) {
            return wait;
        }
        wait = CovRaceSite.once(ctx, "Aufbau", server -> {
            ServerPlayer player = CovRaceSite.player(server);
            ServerLevel level = player.level();
            CovRaceSite.Site site = SITE.get();
            Vec3 g = site.raw(0.5, 0.0, 0.5);
            GateEntity gate = CovRaceSite.spawn(level, GateGeometry.of(g.x, g.y, g.z, 0.0F, GateSize.MEDIUM), GateSize.MEDIUM);
            gateY = gate.getY();
            Vec3 p = site.raw(6.5, 0.0, 0.5);
            GateEntity pole = CovRaceSite.spawn(level, GateGeometry.element(p.x, p.y, p.z, 0.0F, CourseElement.GATE_POLE, 0, GatePoles.THICKNESS, 3.0,
                    GateTilt.NONE, GateMount.STANDING), GateSize.MEDIUM);
            for (int x = 9; x <= 13; x++) {
                for (int z = -1; z <= 2; z++) {
                    boolean hole = x >= 10 && x <= 12 && z >= 0 && z <= 1;
                    if (!hole) {
                        CovRaceSite.set(level, site, site.block(x, SLAB_Y, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
                    }
                }
            }
            BlockPos seed = site.block(11, SLAB_Y, 0);
            FreeformFill.Result fill = FreeformBlocks.fill(level, new FreeformFill.Aim(FreeformRegion.AXIS_Y, CourseElement.AWAY, seed.getX(), seed.getY(),
                    seed.getZ()));
            if (!fill.ok() || fill.region() == null || fill.region().cells() != 6) {
                return "Öffnung für das liegende Freiform-Gate nicht erkannt: " + fill.status();
            }
            GateEntity freeform = CovRaceSite.spawn(level, GateGeometry.freeform(fill.anchorX(), fill.anchorY(), fill.anchorZ(), fill.region(),
                    CourseElement.AWAY), GateSize.MEDIUM);
            site.ids().put(GATE, gate.getId());
            site.ids().put(POLE, pole.getId());
            site.ids().put(FREEFORM, freeform.getId());
            Vec3 feet = site.raw(STAND.x, 0.0, STAND.z);
            player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), 180.0F, 18.0F, true);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
            return null;
        });
        if (wait != null) {
            return wait;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !GateMounting.holdsTool(mc.player) || element(GATE) == null || element(POLE) == null || element(FREEFORM) == null) {
            return ctx.tick() > 800 ? StepResult.fail("Gate, Gate-Stange, Freiform-Gate oder Kurs-Werkzeug kommen beim Client nicht an")
                    : StepResult.pending();
        }
        return StepResult.pass();
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

    private static LayoutProbe.Frame hud(Minecraft mc) {
        return LayoutProbe.capture(mc, g -> GateEditor.extractHud(g, DeltaTracker.ZERO), List.of());
    }

    private static String squeeze(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static String joined(LayoutProbe.Frame frame) {
        StringBuilder all = new StringBuilder();
        for (LayoutAudit.Text drawn : frame.texts()) {
            all.append(drawn.value());
        }
        return squeeze(all.toString());
    }

    private static @Nullable String outside(Minecraft mc, LayoutProbe.Frame frame, String where) {
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        for (LayoutAudit.Text drawn : frame.texts()) {
            LayoutAudit.Box box = drawn.full();
            if (box.left() < 0 || box.top() < 0 || box.right() > width || box.bottom() > height) {
                return where + ": „" + drawn.value() + "“ liegt außerhalb des Fensters (" + box + ", " + width + " × " + height + ")";
            }
        }
        return null;
    }

    private static void key(Minecraft mc, int key, int modifiers) {
        KeyEvent event = new KeyEvent(key, 0, modifiers);
        mc.keyboardHandler.keyPress(mc.getWindow().handle(), InputConstants.PRESS, event);
        mc.keyboardHandler.keyPress(mc.getWindow().handle(), InputConstants.RELEASE, event);
    }

    private static StepResult tooltipStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        List<String> seen = new ArrayList<>();
        for (String key : List.of(GATE, POLE)) {
            GateEditor editor = open(key);
            if (editor == null) {
                return StepResult.fail("Menü startet nicht: " + key);
            }
            try {
                GateEditModel model = editor.model();
                CourseElement kind = model.kind();
                String headingTip = I18n.get(kind.gate() ? "propwash.race.edit.heading.tooltip" : "propwash.race.edit.element.heading.tooltip");
                LayoutProbe.Frame first = hud(mc);
                if (!joined(first).contains(squeeze(headingTip))) {
                    return StepResult.fail(kind + ": Anzeige ohne Maus zeigt den Tooltip der gewählten Zeile „Richtung“ nicht („" + headingTip + "“)");
                }
                if (!joined(first).contains("1°")) {
                    return StepResult.fail(kind + ": Anzeige nennt den Strg-Schritt der Winkel (1°) nicht");
                }
                String problem = outside(mc, first, kind + " Seite 1");
                if (problem != null) {
                    return StepResult.fail(problem);
                }
                int position = model.pageOf(GateEditModel.Page.POSITION);
                if (position < 0) {
                    return StepResult.fail(kind + " ohne Seite Position");
                }
                editor.showPage(position);
                model.select(GateEditModel.Row.MOVE_X);
                LayoutProbe.Frame moving = hud(mc);
                String moveTip = I18n.get("propwash.race.edit.nudge.x.tooltip");
                if (!joined(moving).contains(squeeze(moveTip))) {
                    return StepResult.fail(kind + ": Tooltip der Zeile X fehlt in der Anzeige („" + moveTip + "“)");
                }
                if (!joined(moving).contains("1/16") || !joined(moving).contains("1/32")) {
                    return StepResult.fail(kind + ": Seite Position nennt Strg (1/16) und Strg+Umschalt (1/32) nicht");
                }
                problem = outside(mc, moving, kind + " Seite Position");
                if (problem != null) {
                    return StepResult.fail(problem);
                }
                model.select(GateEditModel.Row.NUDGE_STEP);
                String stepTip = I18n.get("propwash.race.edit.nudge.step.tooltip");
                if (!joined(hud(mc)).contains(squeeze(stepTip))) {
                    return StepResult.fail(kind + ": Tooltip der Zeile Schritt fehlt in der Anzeige");
                }
                seen.add(kind + " " + first.texts().size() + "/" + moving.texts().size() + " Texte");
            } finally {
                GateEditor.stop();
            }
        }
        ctx.log("Tooltip der gewählten Zeile in der Anzeige: " + seen);
        return StepResult.pass();
    }

    private static StepResult nudgeStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int phase = CovRaceSite.phase(ctx);
        if (phase == 0) {
            GateEditor editor = open(GATE);
            if (editor == null) {
                return StepResult.fail("Gate-Menü startet nicht");
            }
            GateEditModel model = editor.model();
            model.setNudgeStep(0.25);
            editor.showPage(model.pageOf(GateEditModel.Page.POSITION));
            int both = InputConstants.MOD_CONTROL | InputConstants.MOD_SHIFT;
            double[] expected = {HALF_PIXEL, 1.0 / 16.0, 0.25, 0.25 - HALF_PIXEL};
            int[][] presses = {{InputConstants.KEY_PAGEUP, both}, {InputConstants.KEY_PAGEUP, InputConstants.MOD_CONTROL},
                    {InputConstants.KEY_PAGEUP, 0}, {InputConstants.KEY_PAGEDOWN, both}};
            String[] names = {"Strg+Umschalt+Bild↑", "Strg+Bild↑", "Bild↑", "Strg+Umschalt+Bild↓"};
            for (int i = 0; i < presses.length; i++) {
                key(mc, presses[i][0], presses[i][1]);
                if (Math.abs(model.dy() - expected[i]) > 1.0E-9) {
                    GateEditor.stop();
                    return StepResult.fail(names[i] + " schiebt auf " + model.dy() + " statt " + expected[i]);
                }
            }
            String shown = GateMounting.signedBlocks(model.dy());
            if (!shown.equals("+0.21875") || !joined(hud(mc)).contains(shown)) {
                GateEditor.stop();
                return StepResult.fail("Anzeige zeigt die Verschiebung nicht als +0.21875 (" + shown + ")");
            }
            if (editor.status() != GateEditor.Status.FREE) {
                GateEditor.stop();
                return StepResult.fail("Verschiebung um 0.21875 nicht frei: " + editor.status());
            }
            editor.apply();
            ctx.log("Tasten: " + String.join(", ", names) + " → " + shown);
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        GateEntity gate = element(GATE);
        double want = gateY + 0.25 - HALF_PIXEL;
        if (phase == 1) {
            if (gate == null || Math.abs(gate.getY() - want) > 1.0E-6) {
                return CovRaceSite.since(ctx) > 200 ? StepResult.fail("Gate beim Client nicht auf " + want + ", sondern " + (gate == null ? "-" : gate.getY()))
                        : StepResult.pending();
            }
            CovRaceSite.next(ctx, 2);
        }
        StepResult wait = CovRaceSite.once(ctx, "Server", server -> {
            GateEntity onServer = CovRaceSite.require(CovRaceSite.player(server).level(), SITE.get(), GATE);
            double y = onServer.getY();
            Vec3 g = SITE.get().raw(0.5, 0.0, 0.5);
            onServer.applyGeometry(GateGeometry.of(g.x, g.y, g.z, 0.0F, GateSize.MEDIUM));
            return Math.abs(y - want) > 1.0E-9 ? "Server hat " + y + " statt " + want : null;
        });
        if (wait != null) {
            return wait;
        }
        return StepResult.pass();
    }

    private static StepResult footStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Resource> resource = mc.getResourceManager().getResource(Constants.id("models/gate/piece/pole_foot.json"));
        if (resource.isEmpty()) {
            return StepResult.fail("Modell gate/piece/pole_foot fehlt");
        }
        JsonObject model;
        try (Reader reader = resource.get().openAsReader()) {
            model = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return StepResult.fail("Modell nicht lesbar: " + e);
        }
        List<String> problems = ModelSymmetry.quarterTurnProblems(model);
        if (!problems.isEmpty()) {
            return StepResult.fail("Fuß der Gate-Stange sieht nach einer Vierteldrehung anders aus (" + problems.size() + " Flächen): " + problems.getFirst());
        }
        int phase = CovRaceSite.phase(ctx);
        if (phase < TURNS.length) {
            float turn = TURNS[phase];
            if (!ctx.state().containsKey("drehen " + phase)) {
                ctx.state().put("drehen " + phase, ctx.onServer(server -> turnPole(server, turn)));
                return StepResult.pending();
            }
            GateEntity pole = element(POLE);
            if (pole == null || Math.abs(GateTilt.wrap(pole.geometry().heading() - turn)) > 0.01F) {
                return CovRaceSite.since(ctx) > 200 ? StepResult.fail("Gate-Stange beim Client nicht auf " + turn + "°") : StepResult.pending();
            }
            CovRaceSite.next(ctx, phase + 1);
            return StepResult.pending();
        }
        ctx.onServer(server -> turnPole(server, 0.0F));
        ctx.log("Fuß: " + ModelSymmetry.quads(model).size() + " Flächen, nach jeder Vierteldrehung gleich; Stange beim Client 0°, 90°, 180°, 270°");
        return StepResult.pass();
    }

    private static void turnPole(MinecraftServer server, float turn) {
        GateEntity pole = CovRaceSite.require(CovRaceSite.player(server).level(), SITE.get(), POLE);
        Vec3 p = SITE.get().raw(6.5, 0.0, 0.5);
        pole.applyGeometry(GateGeometry.element(p.x, p.y, p.z, turn, CourseElement.GATE_POLE, 0, GatePoles.THICKNESS, 3.0, GateTilt.NONE,
                GateMount.STANDING));
    }

    private static GateSignalTarget expected(int index) {
        int[] out = OUTPUTS[index];
        BlockPos pos = SITE.get().block(out[0], SLAB_Y, out[1]);
        return new GateSignalTarget(pos.getX(), pos.getY(), pos.getZ(), out[2]);
    }

    private static StepResult outputStep(SelfTest.StepContext ctx) {
        int phase = CovRaceSite.phase(ctx);
        int index = phase / 2;
        if (index >= TURNS.length) {
            StepResult wait = CovRaceSite.once(ctx, "Speichern", GateTurnSelfTests::saveCheck);
            if (wait != null) {
                return wait;
            }
            ctx.log("Ausgang bei 0°/90°/180°/270° am Nord-, Ost-, Süd- und Westrand, gespeichert und geladen");
            return StepResult.pass();
        }
        float turn = TURNS[index];
        GateSignalTarget want = expected(index);
        if (phase % 2 == 0) {
            GateEditor editor = open(FREEFORM);
            if (editor == null) {
                return StepResult.fail("Menü des Freiform-Gates startet nicht");
            }
            editor.set(GateEditModel.Row.HEADING, turn);
            GateSignalTarget shown = editor.signalTarget();
            if (!want.equals(shown)) {
                GateEditor.stop();
                return StepResult.fail("Rote Markierung bei " + turn + "° auf " + shown + " statt " + want);
            }
            editor.apply();
            GateEditor.stop();
            CovRaceSite.next(ctx, phase + 1);
            return StepResult.pending();
        }
        String key = "Server " + index;
        if (!ctx.state().containsKey(key)) {
            AtomicReference<GateSignalTarget> seen = new AtomicReference<>();
            ctx.state().put(key, seen);
            ctx.state().put(key + " frage", ctx.onServer(server -> seen.set(CovRaceSite.require(CovRaceSite.player(server).level(), SITE.get(), FREEFORM)
                    .signalTarget())));
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        AtomicReference<GateSignalTarget> seen = (AtomicReference<GateSignalTarget>) ctx.state().get(key);
        GateSignalTarget target = seen.get();
        if (!want.equals(target)) {
            ctx.state().remove(key);
            return CovRaceSite.since(ctx) > 200 ? StepResult.fail("Ausgang bei " + turn + "° am Server " + target + " statt " + want) : StepResult.pending();
        }
        GateEntity client = element(FREEFORM);
        if (client == null || !want.equals(client.signalTarget())) {
            ctx.state().remove(key);
            return CovRaceSite.since(ctx) > 200 ? StepResult.fail("Ausgang bei " + turn + "° beim Client " + (client == null ? "-" : client.signalTarget()))
                    : StepResult.pending();
        }
        CovRaceSite.next(ctx, phase + 1);
        return StepResult.pending();
    }

    private static String hint(String key) {
        return Component.translatable(key, Component.keybind(KeyDefaults.GATE_ROTATE.mappingName())).getString();
    }

    private static String shown(String text, boolean compact) {
        int cut = text.indexOf(" · ");
        return squeeze(compact && cut > 0 ? text.substring(0, cut) : text);
    }

    private static StepResult footerStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        GateEditor editor = open(FREEFORM);
        if (editor == null) {
            return StepResult.fail("Menü des Freiform-Gates startet nicht");
        }
        try {
            GateEditModel model = editor.model();
            if (!model.quarterTurns()) {
                return StepResult.fail("Freiform-Gate liegt nicht, keine Zeile Ausrichtung");
            }
            String flip = hint("propwash.race.edit.hint.flip");
            String turn = hint("propwash.race.edit.hint.freeform.turn");
            String toggle = hint("propwash.race.edit.hint.freeform.toggle");
            String pulse = hint("propwash.race.edit.hint.value.pulse");
            String row = Component.translatable("propwash.race.edit.hint.row", Component.keybind("key.sneak")).getString();
            List<String> all = List.of(flip, turn, toggle, pulse);
            GateEditModel.Row[] rows = {GateEditModel.Row.VARIANT, GateEditModel.Row.HEADING, GateEditModel.Row.OPEN_EDGES, GateEditModel.Row.SIGNAL,
                    GateEditModel.Row.PULSE, GateEditModel.Row.RACE_ONLY};
            String[] wanted = {flip, turn, toggle, pulse, pulse, pulse};
            List<String> seen = new ArrayList<>();
            for (int i = 0; i < rows.length; i++) {
                model.select(rows[i]);
                GateEditPanel probe = new GateEditPanel(editor, false);
                LayoutProbe.Frame frame = LayoutProbe.capture(mc, g -> probe.extractHud(g, 0.0F), List.of());
                String drawn = joined(frame);
                boolean compact = probe.compact();
                if (!drawn.contains(shown(wanted[i], compact))) {
                    return StepResult.fail(rows[i] + ": Fußzeile nennt „" + wanted[i] + "“ nicht");
                }
                for (String other : all) {
                    if (!other.equals(wanted[i]) && drawn.contains(shown(other, compact))) {
                        return StepResult.fail(rows[i] + ": Fußzeile nennt zusätzlich „" + other + "“");
                    }
                }
                if (!drawn.contains(shown(row, compact))) {
                    return StepResult.fail(rows[i] + ": Fußzeile nennt „" + row + "“ nicht");
                }
                for (LayoutAudit.Text text : frame.texts()) {
                    LayoutAudit.Box box = text.full();
                    if (box.left() >= probe.left() && box.bottom() > probe.bottom() + 1) {
                        return StepResult.fail(rows[i] + ": „" + text.value() + "“ ragt unten aus dem Menü (" + box + ", Menü bis " + probe.bottom() + ")");
                    }
                }
                int width = mc.getWindow().getGuiScaledWidth();
                int height = mc.getWindow().getGuiScaledHeight();
                for (LayoutAudit.Finding finding : LayoutProbe.auditDrawing(mc, LayoutAudit.Box.of(0, 0, width, height), g -> probe.extractHud(g, 0.0F))) {
                    String subject = squeeze(finding.subject());
                    for (String footer : List.of(wanted[i], row)) {
                        if (!subject.isEmpty() && squeeze(footer).contains(subject)) {
                            return StepResult.fail(rows[i] + ": Fußzeile " + finding.describe());
                        }
                    }
                }
                for (int[] size : new int[][]{{480, 270}, {320, 240}}) {
                    probe.layout(size[0], size[1]);
                    if (probe.top() < 0 || probe.bottom() > size[1]) {
                        return StepResult.fail(rows[i] + ": Menü passt nicht in " + size[0] + " × " + size[1] + ": " + probe.top() + "–" + probe.bottom());
                    }
                }
                seen.add(rows[i] + (compact ? " gedrängt" : ""));
            }
            String fit = fitsInBothLanguages(mc, editor);
            if (fit != null) {
                return StepResult.fail(fit);
            }
            List<String> heights = new ArrayList<>();
            for (GateEditModel.Row pose : model.rows(0)) {
                model.select(pose);
                GateEditPanel probe = new GateEditPanel(editor, false);
                probe.layout(854, 480);
                if (probe.compact()) {
                    return StepResult.fail(pose + ": Menü braucht bei 854 × 480 die gedrängte Darstellung");
                }
                heights.add(probe.top() + "–" + probe.bottom());
            }
            if (heights.stream().distinct().count() != 1) {
                return StepResult.fail("Menü springt beim Zeilenwechsel auf der ersten Seite (854 × 480): " + heights);
            }
            model.select(GateEditModel.Row.HEADING);
            float before = model.heading();
            editor.scroll(1);
            float after = model.heading();
            editor.scroll(-1);
            if (Math.abs(GateTilt.wrap(after - before - 90.0F)) > 0.01F || Math.abs(GateTilt.wrap(model.heading() - before)) > 0.01F) {
                return StepResult.fail("Mausrad auf der Zeile Ausrichtung dreht nicht um 90°: " + before + "° → " + after + "° → " + model.heading() + "°");
            }
            ctx.log("Fußzeile je Zeile: " + String.join(", ", seen) + "; erste Seite bei 854 × 480 ungedrängt " + heights.getFirst() + "; Mausrad auf Ausrichtung "
                    + before + "° → " + after + "°");
            return StepResult.pass();
        } finally {
            GateEditor.stop();
        }
    }

    private static @Nullable String fitsInBothLanguages(Minecraft mc, GateEditor editor) {
        int inner = GateEditPanel.panelWidth(mc.font, GateEditPanel.title(editor.gate().kind())) - 2 * Metrics.PADDING;
        String rotate = Component.keybind(KeyDefaults.GATE_ROTATE.mappingName()).getString();
        String sneak = Component.keybind("key.sneak").getString();
        for (String code : List.of("en_us", "de_de")) {
            Optional<Resource> resource = mc.getResourceManager().getResource(Constants.id("lang/" + code + ".json"));
            if (resource.isEmpty()) {
                return "Sprachdatei " + code + " fehlt";
            }
            JsonObject lang;
            try (Reader reader = resource.get().openAsReader()) {
                lang = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                return "Sprachdatei " + code + " nicht lesbar: " + e;
            }
            for (String key : List.of("propwash.race.edit.hint.flip", "propwash.race.edit.hint.freeform.turn", "propwash.race.edit.hint.freeform.toggle",
                    "propwash.race.edit.hint.value.pulse", "propwash.race.edit.hint.row", "propwash.race.nudge.hint",
                    "propwash.race.nudge.hint.move")) {
                if (!lang.has(key)) {
                    return code + ": " + key + " fehlt";
                }
                String text = lang.get(key).getAsString().replace("%s", key.endsWith(".row") ? sneak : rotate);
                int width = mc.font.width(text);
                if (TextFit.mode(width, inner) == TextFit.Mode.CUT || mc.font.split(Component.literal(text), inner).size() > 2) {
                    return code + ": Fußzeile „" + text + "“ (" + width + " px) passt nicht in das Menü (" + inner + " px)";
                }
            }
        }
        return null;
    }

    private static StepResult placeStep(SelfTest.StepContext ctx) {
        StepResult wait = CovRaceSite.once(ctx, "Setzen", GateTurnSelfTests::placeCheck);
        if (wait != null) {
            return wait;
        }
        ctx.log("liegend gesetzt mit Blick nach Süden, Westen, Norden, Osten: Ausrichtung 0°, 90°, 180°, 270°, Ausgang am Nord-, Ost-, Süd- und Westrand");
        return StepResult.pass();
    }

    private static @Nullable String placeCheck(MinecraftServer server) {
        ServerPlayer player = CovRaceSite.player(server);
        ServerLevel level = player.level();
        CovRaceSite.Site site = SITE.get();
        int keep = site.id(FREEFORM);
        for (int x = 9; x <= 13; x++) {
            for (int z = 4; z <= 7; z++) {
                boolean hole = x >= 10 && x <= 12 && z >= 5 && z <= 6;
                if (!hole) {
                    CovRaceSite.set(level, site, site.block(x, SLAB_Y, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
                }
            }
        }
        try {
            for (int i = 0; i < TURNS.length; i++) {
                float yaw = TURNS[i];
                int[] place = PLACED[i];
                BlockPos clicked = site.block(place[0], SLAB_Y, place[1]);
                String label = "Blick nach " + LOOKS[i] + " (" + (int) yaw + "°)";
                InteractionResult used = use(player, clicked, PLACE_FACES[i], yaw);
                List<GateEntity> placed = level.getEntitiesOfClass(GateEntity.class, CovRaceSite.area(site),
                        gate -> !gate.isRemoved() && gate.getId() != keep && gate.kind() == CourseElement.FREEFORM);
                try {
                    if (!used.consumesAction() || placed.size() != 1) {
                        return label + ": Freiform-Gate nicht gesetzt (" + used + ", " + placed.size() + " neue)";
                    }
                    GateEntity gate = placed.getFirst();
                    FreeformRegion region = gate.region();
                    if (region == null || !region.horizontal() || region.cells() != 6) {
                        return label + ": kein liegendes Gate über der Öffnung: " + (region == null ? "-" : region.axis() + "/" + region.cells());
                    }
                    if (Math.abs(GateTilt.wrap(gate.facingYaw() - yaw)) > 0.01F) {
                        return label + ": Ausrichtung " + gate.facingYaw() + "° statt " + yaw + "°";
                    }
                    BlockPos rim = site.block(place[2], SLAB_Y, place[3]);
                    GateSignalTarget want = new GateSignalTarget(rim.getX(), rim.getY(), rim.getZ(), place[4]);
                    if (!want.equals(gate.signalTarget())) {
                        return label + ": Ausgang " + gate.signalTarget() + " statt " + want + " auf der Seite des Spielers";
                    }
                } finally {
                    for (GateEntity gate : placed) {
                        gate.discard();
                    }
                }
            }
            return null;
        } finally {
            for (int x = 9; x <= 13; x++) {
                for (int z = 4; z <= 7; z++) {
                    CovRaceSite.set(level, site, site.block(x, SLAB_Y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static InteractionResult use(ServerPlayer player, BlockPos clicked, Direction face, float yaw) {
        float head = player.getYHeadRot();
        try {
            player.setYHeadRot(yaw);
            return FreeformGateSelfTests.use(player, clicked, face, yaw, PLACE_PITCH);
        } finally {
            player.setYHeadRot(head);
        }
    }

    private static StepResult courseStep(SelfTest.StepContext ctx) {
        int phase = CovRaceSite.phase(ctx);
        if (phase == 0) {
            StepResult wait = CovRaceSite.once(ctx, "Kurs", GateTurnSelfTests::courseSetup);
            if (wait != null) {
                return wait;
            }
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        if (phase == 1 || phase == 3) {
            GateEntity client = element(FREEFORM);
            if (client == null || !client.inCourse()) {
                return CovRaceSite.since(ctx) > 200 ? StepResult.fail("Freiform-Gate beim Client nicht im Kurs") : StepResult.pending();
            }
            GateEditor editor = open(FREEFORM);
            if (editor == null) {
                return StepResult.fail("Menü des Freiform-Gates startet nicht");
            }
            if (phase == 1) {
                editor.set(GateEditModel.Row.HEADING, 90.0F);
            } else {
                editor.change(GateEditModel.Row.VARIANT, 1);
            }
            if (editor.status() != GateEditor.Status.FREE) {
                GateEditor.stop();
                return StepResult.fail((phase == 1 ? "Ausrichtung 90°" : "Umkehren") + " nicht frei: " + editor.status());
            }
            editor.apply();
            GateEditor.stop();
            CovRaceSite.next(ctx, phase + 1);
            return StepResult.pending();
        }
        if (phase == 2 || phase == 4) {
            String key = "Prüfung " + phase;
            AtomicReference<String> problem = new AtomicReference<>();
            if (!ctx.state().containsKey(key)) {
                ctx.state().put(key, problem);
                ctx.state().put(key + " läuft", ctx.onServer(server -> problem.set(phase == 2 ? turnedCheck(server) : flippedCheck(server))));
                return StepResult.pending();
            }
            @SuppressWarnings("unchecked")
            AtomicReference<String> result = (AtomicReference<String>) ctx.state().get(key);
            CompletableFuture<?> running = (CompletableFuture<?>) ctx.state().get(key + " läuft");
            if (!running.isDone()) {
                return StepResult.pending();
            }
            String found = result.get();
            if (found != null) {
                ctx.state().remove(key);
                if (found.startsWith("warten:")) {
                    return CovRaceSite.since(ctx) > 300 ? StepResult.fail(found.substring(7)) : StepResult.pending();
                }
                return StepResult.fail(found);
            }
            CovRaceSite.next(ctx, phase + 1);
            return StepResult.pending();
        }
        StepResult wait = CovRaceSite.once(ctx, "Abbau", GateTurnSelfTests::courseTeardown);
        if (wait != null) {
            return wait;
        }
        ctx.log(COURSE_REPORT.get());
        return StepResult.pass();
    }

    private static @Nullable String courseSetup(MinecraftServer server) {
        ServerPlayer player = CovRaceSite.player(server);
        ServerLevel level = player.level();
        CovRaceSite.Site site = SITE.get();
        GateEntity freeform = CovRaceSite.require(level, site, FREEFORM);
        GateGeometry lying = freeform.geometry();
        freeform.applyGeometry(GateGeometry.freeform(lying.x(), lying.y(), lying.z(), lying.region(), CourseElement.AWAY));
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return "keine Kursdaten";
        }
        int id = registry.createCourse(CourseRegistry.dimensionOf(level), player);
        CourseRegistry.Course course = registry.course(id);
        if (course == null) {
            return "Kurs nicht angelegt";
        }
        site.ids().put(COURSE, id);
        if (registry.appendGate(id, freeform) < 0) {
            return "Freiform-Gate nicht im Kurs";
        }
        registry.submitLap(course, player.getUUID(), player.getGameProfile().name(), FRAME, LAP_NANOS, System.currentTimeMillis());
        registry.ghosts().put(course.id, FRAME, course.toData().layoutHash(), player.getGameProfile().name(), LAP_NANOS, new byte[]{1, 2, 3});
        if (!registry.records().hasEntries(course.id) || registry.ghosts().get(course.id, FRAME) == null) {
            return "Bestzeit oder Rekord-Geist nicht abgelegt";
        }
        COURSE_START.set(new int[]{course.layout, course.toData().layoutHash()});
        return null;
    }

    private static @Nullable String turnedCheck(MinecraftServer server) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(SITE.get().id(COURSE));
        GateEntity freeform = CovRaceSite.require(CovRaceSite.player(server).level(), SITE.get(), FREEFORM);
        if (course == null) {
            return "Kurs fehlt am Server";
        }
        if (Math.abs(GateTilt.wrap(freeform.facingYaw() - 90.0F)) > 0.01F) {
            return "warten:Ausrichtung kommt am Server nicht an: " + freeform.facingYaw() + "°";
        }
        int[] start = COURSE_START.get();
        CourseData data = course.toData();
        if (course.layout != start[0] || !registry.records().archive(course.id).isEmpty()) {
            return "Ausrichtung hat die Bestenliste archiviert: Layout " + start[0] + " → " + course.layout + ", Archiv "
                    + registry.records().archive(course.id).size();
        }
        if (!registry.records().hasEntries(course.id)) {
            return "Bestzeit nach dem Drehen weg";
        }
        RecordGhosts.Stored ghost = registry.ghosts().get(course.id, FRAME);
        if (ghost == null || ghost.layout() != data.layoutHash() || data.layoutHash() != start[1]) {
            return "Rekord-Geist nach dem Drehen weg oder Layout-Prüfsumme geändert: " + (ghost == null ? "-" : ghost.layout()) + " / " + start[1]
                    + " → " + data.layoutHash();
        }
        if (Math.abs(GateTilt.wrap(data.gates().getFirst().yaw() - 90.0F)) > 0.01F) {
            return "Kursdaten behalten die alte Ausrichtung: " + data.gates().getFirst().yaw() + "°";
        }
        if (!expected(1).equals(freeform.signalTarget())) {
            return "Ausgang im Kurs bei 90° " + freeform.signalTarget() + " statt " + expected(1);
        }
        return null;
    }

    private static @Nullable String flippedCheck(MinecraftServer server) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(SITE.get().id(COURSE));
        GateEntity freeform = CovRaceSite.require(CovRaceSite.player(server).level(), SITE.get(), FREEFORM);
        if (course == null) {
            return "Kurs fehlt am Server";
        }
        if (freeform.variant() != CourseElement.TOWARD) {
            return "warten:Umkehren kommt am Server nicht an: Variante " + freeform.variant();
        }
        int[] start = COURSE_START.get();
        if (course.layout != start[0] + 1 || registry.records().archive(course.id).size() != 1) {
            return "Umkehren archiviert die Bestenliste nicht: Layout " + start[0] + " → " + course.layout + ", Archiv "
                    + registry.records().archive(course.id).size();
        }
        if (registry.records().hasEntries(course.id) || registry.ghosts().get(course.id, FRAME) != null) {
            return "alte Zeit oder Rekord-Geist stehen nach dem Umkehren weiter im Kurs";
        }
        COURSE_REPORT.set("Ausrichtung 0° → 90° im Kurs: Layout " + start[0] + ", Prüfsumme " + start[1] + ", Bestzeit und Rekord-Geist bleiben; Umkehren: Layout "
                + course.layout + ", Archiv 1");
        return null;
    }

    private static @Nullable String courseTeardown(MinecraftServer server) {
        CovRaceSite.Site site = SITE.get();
        CourseRegistry registry = CourseRegistry.get();
        Integer id = site.ids().remove(COURSE);
        if (registry != null && id != null) {
            registry.deleteCourse(id);
        }
        GateEntity freeform = CovRaceSite.require(CovRaceSite.player(server).level(), site, FREEFORM);
        GateGeometry lying = freeform.geometry();
        freeform.applyGeometry(GateGeometry.freeform(lying.x(), lying.y(), lying.z(), lying.region(), CourseElement.AWAY));
        return null;
    }

    private static @Nullable String saveCheck(MinecraftServer server) {
        ServerLevel level = CovRaceSite.player(server).level();
        GateEntity freeform = CovRaceSite.require(level, SITE.get(), FREEFORM);
        ProblemReporter.Collector reporter = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(reporter, server.registryAccess());
        freeform.saveWithoutId(output);
        CompoundTag tag = output.buildResult();
        GateEntity copy = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
        if (copy == null) {
            return "keine Kopie";
        }
        copy.load(TagValueInput.create(reporter, server.registryAccess(), tag));
        if (!copy.signalTarget().equals(freeform.signalTarget()) || !copy.geometry().equals(freeform.geometry())) {
            return "nach dem Laden Ausgang " + copy.signalTarget() + " statt " + freeform.signalTarget();
        }
        if (!expected(TURNS.length - 1).equals(copy.signalTarget())) {
            return "geladen nicht bei 270°: " + copy.signalTarget();
        }
        GateGeometry lying = freeform.geometry();
        freeform.applyGeometry(GateGeometry.freeform(lying.x(), lying.y(), lying.z(), lying.region(), lying.variant()));
        return reporter.isEmpty() ? null : "Meldungen beim Speichern/Laden: " + reporter.getReport();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        GateEditor.stop();
        GateEditor.muteStartNotice(true);
        CovRaceSite.Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        StepResult wait = CovRaceSite.once(ctx, "Abbau", server -> CovRaceSite.teardown(server, site));
        if (wait == null) {
            wait = CovRaceSite.home(ctx, site);
        }
        if (wait != null) {
            return wait;
        }
        SITE.set(null);
        return StepResult.pass();
    }

    private static final class View implements Screenshots.Scene {
        private final Vec3 feet;
        private final Vec3 target;
        private final String key;
        private final int turn;
        private final boolean menu;
        private final boolean gui;

        private View(Vec3 feet, Vec3 target, String key, int turn, boolean menu, boolean gui) {
            this.feet = feet;
            this.target = target;
            this.key = key;
            this.turn = turn;
            this.menu = menu;
            this.gui = gui;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            CovRaceSite.Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null || mc.player == null) {
                return StepResult.fail("kein Aufbau für das Bildschirmfoto");
            }
            Vec3 at = site.raw(this.feet.x, this.feet.y, this.feet.z);
            int turn = this.turn;
            String key = this.key;
            StepResult wait = CovRaceSite.once(ctx, "Blickpunkt", server -> {
                ServerPlayer player = CovRaceSite.player(server);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
                if (turn >= 0 && key.equals(POLE)) {
                    turnPole(server, turn);
                } else if (turn >= 0 && key.equals(FREEFORM)) {
                    GateEntity freeform = CovRaceSite.require(player.level(), SITE.get(), FREEFORM);
                    GateGeometry lying = freeform.geometry();
                    freeform.applyGeometry(GateGeometry.freeform(lying.x(), lying.y(), lying.z(), lying.region(), lying.variant(), lying.score(), turn));
                }
                return null;
            });
            if (wait != null) {
                return wait;
            }
            GateEntity entity = element(this.key);
            if (mc.player.position().distanceTo(at) > 0.6 || entity == null || turn >= 0 && Math.abs(GateTilt.wrap(entity.geometry().heading() - turn)) > 0.01F) {
                return ctx.tick() > 300 ? StepResult.fail("Spieler oder " + this.key + " nicht bereit") : StepResult.pending();
            }
            this.hold(ctx);
            if (this.menu) {
                GateEditor editor = GateEditor.current();
                if (editor == null || editor.gate() != entity) {
                    editor = open(this.key);
                    if (editor == null) {
                        return StepResult.fail("Menü startet nicht");
                    }
                    GateEditModel model = editor.model();
                    if (this.key.equals(GATE)) {
                        editor.showPage(model.pageOf(GateEditModel.Page.POSITION));
                        model.select(GateEditModel.Row.MOVE_X);
                    } else {
                        model.select(GateEditModel.Row.HEADING);
                    }
                }
            } else {
                GateEditor.stop();
            }
            if (mc.gui.hud.isHidden() == this.gui) {
                mc.gui.hud.toggle();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            CovRaceSite.Site site = SITE.get();
            LocalPlayer player = Minecraft.getInstance().player;
            if (site != null && player != null) {
                CovRaceSite.lookAt(player, site.raw(this.target.x, this.target.y, this.target.z));
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            GateEditor.stop();
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            Consumer<MinecraftServer> land = server -> {
                ServerPlayer player = CovRaceSite.player(server);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            };
            ctx.onServer(land);
        }
    }
}
