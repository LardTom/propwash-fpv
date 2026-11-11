package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateItem;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.RaceModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class GateStackPreviewSelfTests {
    private static final String GROUP = "race-topo: Stapel-Vorschau – ";
    private static final String SHOTS = "bildschirmfotos: Stapel-Vorschau – ";
    private static final AtomicReference<CovRaceSite.Site> SITE = new AtomicReference<>();
    private static final AtomicReference<GatePlacement.Preference> SAVED = new AtomicReference<>();
    private static final AtomicReference<GateGeometry> GHOST = new AtomicReference<>();
    private static final int[] BOUNDS = {-6, 12, -6, 10, 16};
    private static final Vec3 STAND = new Vec3(3.0, 0.0, 6.0);
    private static final double GATE_X = 3.0;
    private static final double GATE_Z = 0.5;
    private static final double STAND_OFFSET = 2.5;
    private static final float FACE = 180.0F;
    private static final float START_TURN = 45.0F;
    private static final double EPS = 1.0E-4;
    private static final int WAIT_TICKS = 300;
    private static final int SETTLE = 10;
    private static final String LOW = "preview_low";
    private static final String TOP = "preview_top";
    private static final String BEFORE = "vorher";
    private static final String HEADINGS = "kurse";
    private static final double RING_X = 9.0;
    private static final double RING_Z = 1.0;
    private static final String RING_S = "ring_south";
    private static final String RING_E = "ring_east";
    private static final String RING_W = "ring_west";
    private static final String RING_N = "ring_north";

    private GateStackPreviewSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Aufbau: ein Gate am Boden, Spieler mit Gate-Item davor", 900, GateStackPreviewSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "Blick auf das Gate mit Gate-Item: Geist in der Drehung, mit der gestapelt wird", 400,
                GateStackPreviewSelfTests::ghostStep);
        SelfTest.clientStep(GROUP + "Dreh-Taste mit Gate-Item dreht die Vorschau, nicht das Gate im Blick", 400, GateStackPreviewSelfTests::turnStep);
        if (Screenshots.enabled()) {
            Screenshots.step(SHOTS + "Geist des oberen Gates um 45° gedreht, Hinweis mit Drehung", "race_stack_preview", 30, new PreviewShot());
        }
        SelfTest.clientStep(GROUP + "Rechtsklick stapelt genau wie der Geist", 400, GateStackPreviewSelfTests::clickStep);
        SelfTest.clientStep(GROUP + "Schleichen + Dreh-Taste wechselt die Lage der Vorschau, die Gates bleiben", 400,
                GateStackPreviewSelfTests::sneakStep);
        SelfTest.clientStep(GROUP + "Kurs-Werkzeug: Dreh-Taste dreht das Gate im Blick wie bisher", 400, GateStackPreviewSelfTests::toolStep);
        SelfTest.clientStep(GROUP + "Deckel: Aufbau – Ring aus vier Gates, Spieler mit Gate-Item davor, Vorliebe liegend", 900,
                GateStackPreviewSelfTests::lidSetupStep);
        SelfTest.clientStep(GROUP + "Deckel: Hinweis zeigt die Richtung des Deckel-Geists, Tasten-Zeile nur den Lagewechsel", 400,
                GateStackPreviewSelfTests::lidGhostStep);
        SelfTest.clientStep(GROUP + "Deckel: Dreh-Taste ändert weder Vorliebe noch Hinweis noch das Gate im Blick", 400,
                GateStackPreviewSelfTests::lidTurnStep);
        SelfTest.clientStep(GROUP + "Deckel: Vorliebe liegend aufwärts – Hinweis und Rechtsklick bleiben beim Deckel abwärts", 400,
                GateStackPreviewSelfTests::lidClickStep);
        SelfTest.clientStep(GROUP + "Aufräumen", 300, GateStackPreviewSelfTests::cleanupStep);
    }

    private static float relative(GateGeometry upper, GateGeometry lower) {
        return GateTilt.wrap(upper.heading() - lower.heading());
    }

    private static boolean same(float a, float b) {
        return Math.abs(GateTilt.wrap(a - b)) < 0.05F;
    }

    private static boolean close(GateGeometry a, GateGeometry b) {
        return a.mount() == b.mount() && Math.abs(a.x() - b.x()) < EPS && Math.abs(a.y() - b.y()) < EPS && Math.abs(a.z() - b.z()) < EPS
                && Math.abs(a.width() - b.width()) < EPS && same(a.heading(), b.heading());
    }

    private static String text(GateGeometry gate) {
        return String.format(java.util.Locale.ROOT, "%s %.3f/%.3f/%.3f %.1f° Breite %.1f", gate.mount(), gate.x(), gate.y(), gate.z(), gate.heading(),
                gate.width());
    }

    private static @Nullable GateEntity client(String key) {
        return CovRaceSite.clientEntity(SITE.get(), key);
    }

    private static KeyMapping rotate() {
        KeyMapping mapping = GateMountKeys.mapping();
        if (mapping == null) {
            throw new IllegalStateException("Taste „Gate drehen“ nicht registriert");
        }
        return mapping;
    }

    private static void press(KeyMapping mapping) {
        KeyMapping.click(InputConstants.getKey(mapping.saveString()));
    }

    private static GateGeometry expected(GateEntity low, LocalPlayer player) {
        return GateStacking.stackOn(low.geometry(), GateSize.SMALL, GateStacking.stackYaw(low.facingYaw(), player.getYRot()),
                GateStacking.placementTurn(GateMountKeys.preference()));
    }

    private static void lookAtBar(LocalPlayer player, GateEntity gate) {
        GateGeometry g = gate.geometry();
        CovRaceSite.lookAt(player, new Vec3(g.x(), g.y() + g.height() - g.frame() * 0.5, g.z()));
    }

    private static @Nullable StepResult aim(Minecraft mc, LocalPlayer player, GateEntity gate, SelfTest.StepContext ctx) {
        lookAtBar(player, gate);
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof GateEntity seen) || seen.stack().getFirst() != gate.stack().getFirst()) {
            return CovRaceSite.since(ctx) > 60 ? StepResult.fail("Fadenkreuz trifft den Stapel nicht: " + mc.hitResult) : StepResult.pending();
        }
        return null;
    }

    private static GatePlacement.Preference turned(float degrees) {
        return GatePlacement.Preference.DEFAULT.turnedBy(GateMount.Surface.FLOOR, degrees);
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult wait = CovRaceSite.prepare(ctx, SITE, -260, 200, BOUNDS, STAND);
        if (wait.status() != StepResult.Status.PASS) {
            return wait;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return StepResult.fail("kein Spieler");
        }
        if (SAVED.get() == null) {
            SAVED.set(GateMountKeys.preference());
        }
        wait = CovRaceSite.once(ctx, "Aufbau", server -> {
            ServerPlayer p = CovRaceSite.player(server);
            ServerLevel level = p.level();
            CovRaceSite.Site site = SITE.get();
            Vec3 at = site.raw(GATE_X, 0.0, GATE_Z);
            GateEntity low = CovRaceSite.spawn(level, GateGeometry.of(at.x, at.y, at.z, 0.0F, GateSize.MEDIUM), GateSize.MEDIUM);
            site.ids().put(LOW, low.getId());
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.GATE_SMALL.get(), 16));
            p.connection.send(new ClientboundSetHeldSlotPacket(p.getInventory().getSelectedSlot()));
            Vec3 feet = site.raw(GATE_X, 0.0, GATE_Z + STAND_OFFSET);
            p.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), FACE, 20.0F, true);
            p.resetFallDistance();
            return null;
        });
        if (wait != null) {
            return wait;
        }
        GateEntity low = client(LOW);
        if (low == null || !player.getMainHandItem().is(RaceModule.GATE_SMALL.get()) || mc.gui.screen() != null) {
            return ctx.tick() > 800 ? StepResult.fail("Gate oder Gate-Item kommt beim Client nicht an") : StepResult.pending();
        }
        GateMountKeys.setPreference(turned(START_TURN));
        ctx.log("mittleres Gate am Boden, Spieler davor mit kleinem Gate-Item, Vorliebe am Boden " + START_TURN + "°");
        return StepResult.pass();
    }

    private static StepResult ghostStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GateEntity low = client(LOW);
        if (player == null || low == null) {
            return StepResult.fail("Aufbau fehlt");
        }
        StepResult aiming = aim(mc, player, low, ctx);
        if (aiming != null) {
            return aiming;
        }
        GatePreview.Target target = GatePreview.target(mc);
        GateGeometry want = expected(low, player);
        if (target == null || !close(target.geometry(), want) || !target.free() || !GatePreview.visible()) {
            return CovRaceSite.since(ctx) > WAIT_TICKS ? StepResult.fail("Geist beim Blick auf das Gate: " + (target == null ? "fehlt"
                    : text(target.geometry()) + " frei " + target.free()) + ", erwartet " + text(want)) : StepResult.pending();
        }
        if (!same(relative(target.geometry(), low.geometry()), GateTilt.wrap(GateStacking.stackYaw(low.facingYaw(), player.getYRot()) - low.facingYaw()
                + START_TURN))) {
            return StepResult.fail("Geist nicht um " + START_TURN + "° zum Gate darunter gedreht: " + relative(target.geometry(), low.geometry()));
        }
        String problem = hint(target, low);
        if (problem != null) {
            return StepResult.fail(problem);
        }
        ctx.log("Geist " + text(target.geometry()) + ", " + relative(target.geometry(), low.geometry()) + "° zum unteren Gate, frei, Fuß "
                + target.base() + ", Hinweis „" + GatePreview.status(target, GateMountKeys.preference()).getString() + "“ / „"
                + GatePreview.keys(target).getString() + "“");
        return StepResult.pass();
    }

    private static @Nullable String hint(GatePreview.Target target, GateEntity low) {
        GatePlacement.Preference preference = GateMountKeys.preference();
        GateMount.Surface floor = GateMount.Surface.FLOOR;
        String status = GatePreview.status(target, preference).getString();
        String pose = GateMounting.describe(preference.mount(floor), preference.turn(floor), preference.fine(floor)).getString();
        if (target.surface() != floor || !status.equals(pose)) {
            return "Hinweis zeigt „" + status + "“ statt der Lage mit Drehung „" + pose + "“";
        }
        if (!(GatePreview.keys(target).getContents() instanceof TranslatableContents keys) || !keys.getKey().equals("propwash.race.mount.hint")) {
            return "Tasten-Hinweis nicht wie beim Bodenplatzieren: " + GatePreview.keys(target).getString();
        }
        GateStacking.Base base = GateStacking.halfTurn(relative(target.geometry(), low.geometry())) ? GateStacking.Base.STACKED : GateStacking.Base.MOUNTED;
        if (target.base() != base) {
            return "Geist mit Fuß " + target.base() + " statt " + base;
        }
        return null;
    }

    private static StepResult turnStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GateEntity low = client(LOW);
        if (player == null || low == null) {
            return StepResult.fail("Aufbau fehlt");
        }
        StepResult aiming = aim(mc, player, low, ctx);
        if (aiming != null) {
            return aiming;
        }
        int phase = CovRaceSite.phase(ctx);
        if (phase == 0) {
            StepResult wait = CovRaceSite.once(ctx, "Server vorher", server -> {
                GateEntity gate = CovRaceSite.require(CovRaceSite.player(server).level(), SITE.get(), LOW);
                ctx.state().put(HEADINGS, gate.geometry().heading());
                return null;
            });
            if (wait != null) {
                return wait;
            }
            ctx.state().put(BEFORE, GateMountKeys.preference());
            press(rotate());
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        if (CovRaceSite.since(ctx) < SETTLE) {
            return StepResult.pending();
        }
        GatePlacement.Preference before = (GatePlacement.Preference) ctx.state().get(BEFORE);
        GatePlacement.Preference now = GateMountKeys.preference();
        float want = GateTilt.wrap(GateStacking.placementTurn(before) + GateMountKeys.step());
        if (!same(GateStacking.placementTurn(now), want) || now.floor() != GateMount.STANDING) {
            return StepResult.fail("Dreh-Taste mit Gate-Item dreht die Vorschau nicht: " + GateStacking.placementTurn(before) + "° -> "
                    + GateStacking.placementTurn(now) + "°, erwartet " + want + "°");
        }
        StepResult wait = CovRaceSite.once(ctx, "Server nachher", server -> {
            ServerPlayer p = CovRaceSite.player(server);
            GateEntity gate = CovRaceSite.require(p.level(), SITE.get(), LOW);
            float old = (float) ctx.state().get(HEADINGS);
            if (!same(gate.geometry().heading(), old)) {
                return "Dreh-Taste dreht das Gate im Blick mit: " + old + "° -> " + gate.geometry().heading() + "°";
            }
            if (!same(GateStacking.placementTurn(GateMounting.preference(p, false)), want)) {
                return "Server kennt die gedrehte Vorliebe nicht: " + GateStacking.placementTurn(GateMounting.preference(p, false));
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        GatePreview.Target target = GatePreview.target(mc);
        GateGeometry expect = expected(low, player);
        if (target == null || !close(target.geometry(), expect)) {
            return StepResult.fail("Geist folgt der Dreh-Taste nicht: " + (target == null ? "fehlt" : text(target.geometry())) + ", erwartet " + text(expect));
        }
        GateGeometry lower = low.geometry();
        boolean half = GateStacking.halfTurn(relative(target.geometry(), lower));
        double seat = half ? GateStacking.seat(lower) : lower.y() + lower.height();
        if (Math.abs(target.geometry().y() - seat) > EPS) {
            return StepResult.fail("Geist sitzt bei " + target.geometry().y() + " statt " + seat);
        }
        String problem = hint(target, low);
        if (problem != null) {
            return StepResult.fail("nach der Dreh-Taste: " + problem);
        }
        GHOST.set(target.geometry());
        ctx.log("Dreh-Taste: Vorliebe " + GateStacking.placementTurn(before) + "° -> " + GateStacking.placementTurn(now) + "°, Geist "
                + relative(target.geometry(), lower) + "° zum unteren Gate, Fuß " + target.base() + ", Hinweis „"
                + GatePreview.status(target, now).getString() + "“, Gate im Blick bleibt am Server bei " + ctx.state().get(HEADINGS) + "°");
        return StepResult.pass();
    }

    private static StepResult clickStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GateEntity low = client(LOW);
        CovRaceSite.Site site = SITE.get();
        if (player == null || low == null || site == null) {
            return StepResult.fail("Aufbau fehlt");
        }
        int phase = CovRaceSite.phase(ctx);
        if (phase == 0) {
            StepResult aiming = aim(mc, player, low, ctx);
            if (aiming != null) {
                return aiming;
            }
            GatePreview.Target target = GatePreview.target(mc);
            if (target == null || !target.free()) {
                return StepResult.fail("kein freier Geist vor dem Klick");
            }
            GHOST.set(target.geometry());
            KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        List<GateEntity> column = low.stack();
        GateEntity top = column.size() == 2 ? column.getLast() : null;
        if (top == null) {
            return CovRaceSite.since(ctx) > WAIT_TICKS ? StepResult.fail("Klick stapelt kein Gate (Stapel " + column.size() + ")") : StepResult.pending();
        }
        GateGeometry ghost = GHOST.get();
        boolean crossed = !GateStacking.halfTurn(relative(top.geometry(), low.geometry()));
        if (ghost == null || !close(top.geometry(), ghost) || top.base() != (crossed ? GateStacking.Base.MOUNTED : GateStacking.Base.STACKED)) {
            return CovRaceSite.since(ctx) > WAIT_TICKS ? StepResult.fail("gestapeltes Gate " + text(top.geometry()) + " " + top.base() + " statt Geist "
                    + (ghost == null ? "fehlt" : text(ghost))) : StepResult.pending();
        }
        site.ids().put(TOP, top.getId());
        ctx.log("Rechtsklick: Gate liegt wie der Geist " + text(top.geometry()) + ", Fuß " + top.base());
        return StepResult.pass();
    }

    private static StepResult sneakStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GateEntity low = client(LOW);
        GateEntity top = client(TOP);
        if (player == null || low == null || top == null) {
            return StepResult.fail("Stapel fehlt");
        }
        int phase = CovRaceSite.phase(ctx);
        if (phase == 0 || phase == 2) {
            StepResult aiming = aim(mc, player, low, ctx);
            if (aiming != null) {
                return aiming;
            }
            if (phase == 0) {
                ctx.state().put(BEFORE, GateMountKeys.preference());
                ctx.state().put(HEADINGS, new float[]{low.geometry().heading(), top.geometry().heading()});
            }
            mc.options.keyShift.setDown(true);
            press(rotate());
            CovRaceSite.next(ctx, phase + 1);
            return StepResult.pending();
        }
        if (CovRaceSite.since(ctx) < SETTLE) {
            return StepResult.pending();
        }
        mc.options.keyShift.setDown(false);
        GatePlacement.Preference before = (GatePlacement.Preference) ctx.state().get(BEFORE);
        GatePlacement.Preference now = GateMountKeys.preference();
        GateMount want = phase == 1 ? before.floor().next() : before.floor();
        if (now.floor() != want) {
            return StepResult.fail("Schleichen + Dreh-Taste mit Gate-Item: Lage der Vorschau " + now.floor() + " statt " + want);
        }
        float[] headings = (float[]) ctx.state().get(HEADINGS);
        if (low.mount() != GateMount.STANDING || top.mount() != GateMount.STANDING || !same(low.geometry().heading(), headings[0])
                || !same(top.geometry().heading(), headings[1])) {
            return StepResult.fail("Schleichen + Dreh-Taste ändert die Gates im Blick: " + text(low.geometry()) + ", " + text(top.geometry()));
        }
        if (phase == 1) {
            ctx.log("Schleichen + Dreh-Taste: Lage der Vorschau " + before.floor() + " -> " + now.floor() + ", Gates unverändert");
            CovRaceSite.next(ctx, 2);
            return StepResult.pending();
        }
        GatePlacement.Preference floor = before.nextMount(GateMount.Surface.FLOOR).nextMount(GateMount.Surface.FLOOR);
        if (!now.equals(floor)) {
            return StepResult.fail("zweimal Lage gewechselt: " + now + " statt wie am Boden " + floor);
        }
        ctx.log("noch einmal: wieder " + now.floor() + " mit " + GateStacking.placementTurn(now) + "° wie beim Lagewechsel am Boden");
        return StepResult.pass();
    }

    private static StepResult toolStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GateEntity low = client(LOW);
        GateEntity top = client(TOP);
        if (player == null || low == null || top == null) {
            return StepResult.fail("Stapel fehlt");
        }
        int phase = CovRaceSite.phase(ctx);
        if (phase == 0) {
            StepResult wait = CovRaceSite.once(ctx, "Werkzeug", server -> {
                ServerPlayer p = CovRaceSite.player(server);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
                p.connection.send(new ClientboundSetHeldSlotPacket(p.getInventory().getSelectedSlot()));
                return null;
            });
            if (wait != null) {
                return wait;
            }
            if (!player.getMainHandItem().is(RaceModule.COURSE_TOOL.get())) {
                return CovRaceSite.since(ctx) > WAIT_TICKS ? StepResult.fail("Kurs-Werkzeug kommt nicht an") : StepResult.pending();
            }
            lookAtBar(player, low);
            if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof GateEntity seen) || (seen != low && seen != top)) {
                return CovRaceSite.since(ctx) > 120 ? StepResult.fail("Fadenkreuz trifft den Stapel nicht: " + mc.hitResult) : StepResult.pending();
            }
            if (GatePreview.target(mc) != null) {
                return StepResult.fail("Kurs-Werkzeug zeigt einen Geist");
            }
            ctx.state().put(BEFORE, GateMountKeys.preference());
            ctx.state().put(HEADINGS, new float[]{low.geometry().heading(), relative(top.geometry(), low.geometry()), seen == low ? 0.0F : 1.0F});
            press(rotate());
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        float[] before = (float[]) ctx.state().get(HEADINGS);
        boolean bottom = before[2] == 0.0F;
        float step = GateMountKeys.step();
        float lowWant = bottom ? GateTilt.wrap(before[0] + step) : before[0];
        float relWant = bottom ? before[1] : GateTilt.wrap(before[1] + step);
        float rel = relative(top.geometry(), low.geometry());
        if (!same(low.geometry().heading(), lowWant) || !same(rel, relWant)) {
            return CovRaceSite.since(ctx) > WAIT_TICKS ? StepResult.fail("Kurs-Werkzeug: Dreh-Taste dreht das " + (bottom ? "untere" : "obere")
                    + " Gate im Blick nicht (unten " + before[0] + "° -> " + low.geometry().heading() + "°, oben zum unteren " + before[1] + "° -> " + rel
                    + "°)") : StepResult.pending();
        }
        if (!GateMountKeys.preference().equals(ctx.state().get(BEFORE))) {
            return StepResult.fail("Kurs-Werkzeug: Dreh-Taste ändert zusätzlich die Vorliebe");
        }
        ctx.log("Kurs-Werkzeug: Dreh-Taste dreht das " + (bottom ? "untere Gate samt Stapel " + before[0] + "° -> " + low.geometry().heading() + "°"
                : "obere Gate " + before[1] + "° -> " + rel + "°") + ", Vorliebe bleibt");
        return StepResult.pass();
    }

    private static GatePlacement.Preference lying() {
        return GatePlacement.Preference.DEFAULT.nextMount(GateMount.Surface.FLOOR);
    }

    private static GateGeometry ringGate(CovRaceSite.Site site, double x, double z, float yaw) {
        Vec3 at = site.raw(x, 0.0, z);
        return GateGeometry.of(at.x, at.y, at.z, yaw, GateSize.MEDIUM);
    }

    private static @Nullable GateGeometry lidGhost(GateEntity south) {
        GateStacking.Lid lid = RaceModule.GATE_MEDIUM.get().lidFor(south);
        return lid.refusal() == GateStacking.LidRefusal.NONE ? lid.geometry() : null;
    }

    private static String lidText(GateGeometry lid) {
        return GateMounting.describe(lid.mount(), "up".equals(GateMounting.directionKey(lid)) ? 1 : 0).getString();
    }

    private static @Nullable String lidHint(GatePreview.Target target, GatePlacement.Preference preference) {
        GateGeometry lid = target.geometry();
        if (lid.mount() != GateMount.FLOOR_FLAT || !"down".equals(GateMounting.directionKey(lid)) || lid.flipped()) {
            return "Deckel-Geist nicht liegend abwärts: " + text(lid) + " Richtung " + GateMounting.directionKey(lid) + " flipped " + lid.flipped();
        }
        String status = GatePreview.status(target, preference).getString();
        if (!status.equals(lidText(lid))) {
            return "Hinweis zeigt „" + status + "“ statt der Richtung des Deckels „" + lidText(lid) + "“ (Vorliebe liegend, Stellung "
                    + preference.floorTurn() + ")";
        }
        if (!(GatePreview.keys(target).getContents() instanceof TranslatableContents keys) || !keys.getKey().equals("propwash.race.lid.hint")) {
            return "Tasten-Zeile am Deckel nennt das Drehen: „" + GatePreview.keys(target).getString() + "“";
        }
        return null;
    }

    private static @Nullable StepResult lidTarget(Minecraft mc, LocalPlayer player, GateEntity south, SelfTest.StepContext ctx,
                                                  AtomicReference<GatePreview.Target> out) {
        StepResult aiming = aim(mc, player, south, ctx);
        if (aiming != null) {
            return aiming;
        }
        GatePreview.Target target = GatePreview.target(mc);
        GateGeometry want = lidGhost(south);
        if (want == null || target == null || !close(target.geometry(), want) || !target.free()) {
            return CovRaceSite.since(ctx) > WAIT_TICKS ? StepResult.fail("Deckel-Geist: " + (target == null ? "fehlt" : text(target.geometry())
                    + " frei " + target.free()) + ", erwartet " + (want == null ? "kein Deckel" : text(want))) : StepResult.pending();
        }
        out.set(target);
        return null;
    }

    private static StepResult lidSetupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        CovRaceSite.Site site = SITE.get();
        if (player == null || site == null) {
            return StepResult.fail("Aufbau fehlt");
        }
        StepResult wait = CovRaceSite.once(ctx, "Ring", server -> {
            ServerPlayer p = CovRaceSite.player(server);
            ServerLevel level = p.level();
            GateGeometry south = ringGate(site, RING_X, RING_Z, 0.0F);
            GateGeometry east = GateStacking.snap(ringGate(site, RING_X + 2.0, RING_Z + 2.0, 90.0F), List.of(south));
            GateGeometry west = GateStacking.snap(ringGate(site, RING_X - 2.0, RING_Z + 2.0, 90.0F), List.of(south));
            GateGeometry north = GateStacking.snap(ringGate(site, RING_X, RING_Z + 4.0, 0.0F), List.of(east, west));
            List<GateEntity> ring = new ArrayList<>();
            String[] keys = {RING_S, RING_E, RING_W, RING_N};
            GateGeometry[] gates = {south, east, west, north};
            for (int i = 0; i < gates.length; i++) {
                GateEntity gate = CovRaceSite.spawn(level, gates[i], GateSize.MEDIUM);
                site.ids().put(keys[i], gate.getId());
                ring.add(gate);
            }
            for (GateEntity gate : ring) {
                GateItem.refreshAround(gate);
            }
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.GATE_MEDIUM.get(), 16));
            p.connection.send(new ClientboundSetHeldSlotPacket(p.getInventory().getSelectedSlot()));
            Vec3 feet = site.raw(RING_X, 0.0, RING_Z - STAND_OFFSET);
            p.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), 0.0F, 20.0F, true);
            p.resetFallDistance();
            return null;
        });
        if (wait != null) {
            return wait;
        }
        GateEntity south = client(RING_S);
        if (south == null || client(RING_E) == null || client(RING_W) == null || client(RING_N) == null
                || !player.getMainHandItem().is(RaceModule.GATE_MEDIUM.get())) {
            return CovRaceSite.since(ctx) > 800 ? StepResult.fail("Ring oder Gate-Item kommt beim Client nicht an") : StepResult.pending();
        }
        if (lidGhost(south) == null) {
            return CovRaceSite.since(ctx) > 800 ? StepResult.fail("Ring trägt keinen Deckel: " + RaceModule.GATE_MEDIUM.get().lidFor(south).refusal())
                    : StepResult.pending();
        }
        GateMountKeys.setPreference(lying());
        ctx.log("Ring aus vier mittleren Gates, Spieler mit mittlerem Gate-Item vor der Südseite, Vorliebe liegend (Stellung 0)");
        return StepResult.pass();
    }

    private static StepResult lidGhostStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GateEntity south = client(RING_S);
        if (player == null || south == null) {
            return StepResult.fail("Ring fehlt");
        }
        AtomicReference<GatePreview.Target> found = new AtomicReference<>();
        StepResult wait = lidTarget(mc, player, south, ctx, found);
        if (wait != null) {
            return wait;
        }
        GatePreview.Target target = found.get();
        String problem = lidHint(target, GateMountKeys.preference());
        if (problem != null) {
            return StepResult.fail(problem);
        }
        ctx.log("Deckel-Geist " + text(target.geometry()) + " Richtung " + GateMounting.directionKey(target.geometry()) + ", Hinweis „"
                + GatePreview.status(target, GateMountKeys.preference()).getString() + "“ / „" + GatePreview.keys(target).getString() + "“");
        return StepResult.pass();
    }

    private static StepResult lidTurnStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GateEntity south = client(RING_S);
        if (player == null || south == null) {
            return StepResult.fail("Ring fehlt");
        }
        AtomicReference<GatePreview.Target> found = new AtomicReference<>();
        StepResult aiming = lidTarget(mc, player, south, ctx, found);
        if (aiming != null) {
            return aiming;
        }
        int phase = CovRaceSite.phase(ctx);
        if (phase == 0) {
            StepResult wait = CovRaceSite.once(ctx, "Server vorher", server -> {
                GateEntity gate = CovRaceSite.require(CovRaceSite.player(server).level(), SITE.get(), RING_S);
                ctx.state().put(HEADINGS, gate.geometry().heading());
                return null;
            });
            if (wait != null) {
                return wait;
            }
            ctx.state().put(BEFORE, GateMountKeys.preference());
            GHOST.set(found.get().geometry());
            press(rotate());
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        if (CovRaceSite.since(ctx) < SETTLE) {
            return StepResult.pending();
        }
        GatePlacement.Preference before = (GatePlacement.Preference) ctx.state().get(BEFORE);
        GatePlacement.Preference now = GateMountKeys.preference();
        if (!now.equals(before)) {
            return StepResult.fail("Dreh-Taste ändert am Deckel die Vorliebe: liegend Stellung " + before.floorTurn() + " -> " + now.floor() + " Stellung "
                    + now.floorTurn() + ", Hinweis „" + GatePreview.status(found.get(), now).getString() + "“, Deckel-Geist "
                    + GateMounting.directionKey(found.get().geometry()));
        }
        GateGeometry ghost = GHOST.get();
        if (ghost == null || !close(found.get().geometry(), ghost)) {
            return StepResult.fail("Deckel-Geist ändert sich durch die Dreh-Taste: " + text(found.get().geometry()));
        }
        String problem = lidHint(found.get(), now);
        if (problem != null) {
            return StepResult.fail("nach der Dreh-Taste: " + problem);
        }
        StepResult wait = CovRaceSite.once(ctx, "Server nachher", server -> {
            ServerPlayer p = CovRaceSite.player(server);
            GateEntity gate = CovRaceSite.require(p.level(), SITE.get(), RING_S);
            float old = (float) ctx.state().get(HEADINGS);
            if (!same(gate.geometry().heading(), old)) {
                return "Dreh-Taste dreht am Deckel das Gate im Blick: " + old + "° -> " + gate.geometry().heading() + "°";
            }
            if (!GateMounting.preference(p, false).equals(before)) {
                return "Server-Vorliebe nach der Dreh-Taste am Deckel " + GateMounting.preference(p, false) + " statt " + before;
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("Dreh-Taste am Deckel: Vorliebe bleibt liegend Stellung " + now.floorTurn() + ", Hinweis „" + GatePreview.status(found.get(), now).getString()
                + "“, Gate im Blick bleibt bei " + ctx.state().get(HEADINGS) + "°");
        return StepResult.pass();
    }

    private static StepResult lidClickStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GateEntity south = client(RING_S);
        if (player == null || south == null) {
            return StepResult.fail("Ring fehlt");
        }
        int phase = CovRaceSite.phase(ctx);
        if (phase == 0) {
            GateMountKeys.setPreference(lying().with(GateMount.FLOOR_FLAT, 1));
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        if (phase == 1) {
            if (CovRaceSite.since(ctx) < SETTLE) {
                return StepResult.pending();
            }
            AtomicReference<GatePreview.Target> found = new AtomicReference<>();
            StepResult aiming = lidTarget(mc, player, south, ctx, found);
            if (aiming != null) {
                return aiming;
            }
            GatePlacement.Preference preference = GateMountKeys.preference();
            String problem = lidHint(found.get(), preference);
            if (problem != null) {
                return StepResult.fail(problem);
            }
            ctx.log("Vorliebe liegend aufwärts (wie am Boden eingestellt): Hinweis am Deckel „" + GatePreview.status(found.get(), preference).getString() + "“");
            GHOST.set(found.get().geometry());
            KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
            CovRaceSite.next(ctx, 2);
            return StepResult.pending();
        }
        GateGeometry ghost = GHOST.get();
        GateEntity lid = null;
        if (mc.level != null && ghost != null) {
            for (GateEntity gate : mc.level.getEntitiesOfClass(GateEntity.class, south.getBoundingBox().inflate(4.0),
                    gate -> !gate.isRemoved() && gate.mount() == GateMount.FLOOR_FLAT)) {
                lid = gate;
            }
        }
        if (lid == null || ghost == null) {
            return CovRaceSite.since(ctx) > WAIT_TICKS ? StepResult.fail("Rechtsklick setzt keinen Deckel") : StepResult.pending();
        }
        if (!close(lid.geometry(), ghost) || lid.geometry().flipped() || !"down".equals(GateMounting.directionKey(lid.geometry()))) {
            return CovRaceSite.since(ctx) > WAIT_TICKS ? StepResult.fail("gesetzter Deckel " + text(lid.geometry()) + " Richtung "
                    + GateMounting.directionKey(lid.geometry()) + " statt Geist " + text(ghost) + " abwärts") : StepResult.pending();
        }
        ctx.log("Rechtsklick: Deckel liegt wie der Geist " + text(lid.geometry()) + ", Richtung " + GateMounting.directionKey(lid.geometry()));
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyShift.setDown(false);
        GatePlacement.Preference saved = SAVED.getAndSet(null);
        if (!ctx.state().containsKey(BEFORE)) {
            GateMountKeys.setPreference(saved == null ? GatePlacement.Preference.DEFAULT : saved);
            ctx.state().put(BEFORE, true);
        }
        GHOST.set(null);
        CovRaceSite.Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        StepResult wait = CovRaceSite.once(ctx, "Abbau", server -> CovRaceSite.teardown(server, site));
        if (wait != null) {
            return wait;
        }
        SITE.set(null);
        return StepResult.pass();
    }

    private static final class PreviewShot implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            GateEntity low = client(LOW);
            if (player == null || low == null) {
                return StepResult.fail("Gate für das Bildschirmfoto fehlt");
            }
            mc.getTutorial().setStep(TutorialSteps.NONE);
            StepResult wait = CovRaceSite.once(ctx, "Stand", server -> {
                ServerPlayer p = CovRaceSite.player(server);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.GATE_SMALL.get(), 16));
                p.connection.send(new ClientboundSetHeldSlotPacket(p.getInventory().getSelectedSlot()));
                Vec3 feet = SITE.get().raw(GATE_X + 1.5, 0.0, GATE_Z + 4.0);
                p.teleportTo(p.level(), feet.x, feet.y, feet.z, Set.of(), FACE, 0.0F, true);
                p.resetFallDistance();
                return null;
            });
            if (wait != null) {
                return wait;
            }
            if (!ctx.state().containsKey(SHOTS)) {
                GateMountKeys.setPreference(turned(START_TURN));
                ctx.state().put(SHOTS, true);
            }
            if (!player.getMainHandItem().is(RaceModule.GATE_SMALL.get())) {
                return ctx.tick() > WAIT_TICKS ? StepResult.fail("Gate-Item kommt für das Bildschirmfoto nicht an") : StepResult.pending();
            }
            this.hold(ctx);
            GatePreview.Target target = GatePreview.target(mc);
            if (target == null || !target.free() || target.base() != GateStacking.Base.MOUNTED) {
                return ctx.tick() > WAIT_TICKS ? StepResult.fail("kein freier gedrehter Geist für das Bildschirmfoto: "
                        + (target == null ? "fehlt" : text(target.geometry()) + " " + target.base())) : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            GateEntity low = client(LOW);
            mc.gui.toastManager().clear();
            if (mc.player != null && low != null) {
                lookAtBar(mc.player, low);
            }
        }
    }
}
