package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.core.OrientedBox;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateCollisions;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GateOpenings;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.StackTurnRoute;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class GateStackTurnSelfTests {
    private static final String GROUP = "race-topo: Stapel drehen – ";
    private static final String SHOTS = "bildschirmfotos: Stapel drehen – ";
    private static final AtomicReference<CovRaceSite.Site> SITE = new AtomicReference<>();
    private static final AtomicReference<GatePlacement.Preference> PREFERENCE = new AtomicReference<>();
    private static final AtomicBoolean MUTED = new AtomicBoolean();
    private static final int[] BOUNDS = {-6, 24, -8, 10, 18};
    private static final Vec3 STAND = new Vec3(9.0, 0.0, 9.0);
    private static final double A_X = 2.5;
    private static final double B_X = 15.5;
    private static final double GATE_Z = 0.5;
    private static final float FACE = 180.0F;
    private static final double EPS = 1.0E-4;
    private static final int WAIT_TICKS = 300;
    private static final String A_LOW = "turn_a_low";
    private static final String A_TOP = "turn_a_top";
    private static final String B_LOW = "turn_b_low";
    private static final String B_MID = "turn_b_mid";
    private static final String B_TOP = "turn_b_top";
    private static final String COURSE_A = "course_turn_a";
    private static final String COURSE_B = "course_turn_b";
    private static final String OPENED = "Menü offen seit";
    private static final int TIP_TICKS = 320;

    private GateStackTurnSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Aufbau: zwei Gates und drei Gates mit Gate-Item gesetzt und gestapelt, oberes mit 90° Vorliebe", 900,
                GateStackTurnSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "echter Klick mit 45° Vorliebe: oberes Gate liegt gedreht auf dem Querbalken, Gate-Hinweis erkennt es", 600,
                GateStackTurnSelfTests::clickStep);
        SelfTest.clientStep(GROUP + "Auflage: kein Schweben, keine Überschneidung, Füße unten, eigener Balken oben (Client und Server)", 300,
                GateStackTurnSelfTests::seatStep);
        SelfTest.clientStep(GROUP + "Dreh-Taste in Schritten wie am Boden, 180° wieder auf dem gemeinsamen Balken, Lage gesperrt", 300,
                GateStackTurnSelfTests::rotateStep);
        SelfTest.clientStep(GROUP + "Gate-Menü: Drehung frei, Breite gesperrt; unteres Gate gedreht, die Gates darüber drehen mit", 600,
                GateStackTurnSelfTests::editorStep);
        SelfTest.clientStep(GROUP + "Kurs: Runde durch beide Stapel beim Client und am Server, Kursdaten mit Drehung", 900,
                GateStackTurnSelfTests::courseStep);
        SelfTest.clientStep(GROUP + "Kollision und Hitbox des gedrehten Gates", 300, GateStackTurnSelfTests::collisionStep);
        if (Screenshots.enabled()) {
            Screenshots.step(SHOTS + "zwei Gates, oberes um 45° gedreht", "race_stack_turn_45", 30, new StackShot(A_LOW, A_X, -6.0));
            Screenshots.step(SHOTS + "drei Gates, oberes um 90° gedreht", "race_stack_turn_90", 30, new StackShot(B_LOW, B_X, 6.5));
            Screenshots.step(SHOTS + "Gate-Menü dreht das obere Gate weiter", "race_stack_turn_menu", 30, new MenuShot());
        }
        SelfTest.clientStep(GROUP + "Abbauen: das mittlere Gate fehlt, das obere fällt und bleibt gedreht obenauf", 300,
                GateStackTurnSelfTests::removeStep);
        SelfTest.clientStep(GROUP + "Aufräumen", 300, GateStackTurnSelfTests::cleanupStep);
    }

    private static float relative(GateGeometry upper, GateGeometry lower) {
        return GateTilt.wrap(upper.heading() - lower.heading());
    }

    private static boolean same(float a, float b) {
        return Math.abs(GateTilt.wrap(a - b)) < 0.05F;
    }

    private static GatePlacement.Preference turned(float degrees) {
        return GatePlacement.Preference.DEFAULT.turnedBy(GateMount.Surface.FLOOR, degrees);
    }

    private static @Nullable GateEntity client(String key) {
        return CovRaceSite.clientEntity(SITE.get(), key);
    }

    private static GateEntity place(ServerPlayer player, GateSize size, BlockPos floor) {
        ItemStack previous = player.getMainHandItem().copy();
        try {
            CovRaceSite.face(player, FACE);
            ItemStack stack = new ItemStack(RaceModule.gateItem(size).orElseThrow());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockHitResult hit = new BlockHitResult(new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5), Direction.UP, floor, false);
            InteractionResult result = stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            if (!result.consumesAction()) {
                throw new IllegalStateException(size + " nicht gesetzt: " + result);
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        AABB area = new AABB(floor.above()).inflate(0.4);
        List<GateEntity> found = player.level().getEntitiesOfClass(GateEntity.class, area,
                gate -> !gate.isRemoved() && Math.abs(gate.getY() - (floor.getY() + 1.0)) < 0.01);
        if (found.size() != 1) {
            throw new IllegalStateException(size + " auf dem Boden " + found.size() + "-mal gefunden");
        }
        return found.getFirst();
    }

    private static GateEntity stack(ServerPlayer player, GateEntity target, GateSize size, GatePlacement.Preference preference) {
        ItemStack previous = player.getMainHandItem().copy();
        GatePlacement.Preference before = GateMounting.preference(player, false);
        InteractionResult result;
        try {
            CovRaceSite.face(player, FACE);
            GateMounting.setPreference(player, preference);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.gateItem(size).orElseThrow()));
            result = target.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            GateMounting.setPreference(player, before);
        }
        if (!result.consumesAction()) {
            throw new IllegalStateException(size + " nicht gestapelt: " + result);
        }
        GateEntity top = target.stack().getLast();
        if (top == target || top.size() != size) {
            throw new IllegalStateException(size + " nicht oben im Stapel");
        }
        return top;
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult wait = CovRaceSite.prepare(ctx, SITE, -230, 170, BOUNDS, STAND);
        if (wait.status() != StepResult.Status.PASS) {
            return wait;
        }
        wait = CovRaceSite.once(ctx, "Aufbau", server -> {
            ServerPlayer player = CovRaceSite.player(server);
            ServerLevel level = player.level();
            CovRaceSite.Site site = SITE.get();
            PREFERENCE.set(GateMounting.preference(player, false));
            GateMounting.setPreference(player, GatePlacement.Preference.DEFAULT);
            GateEntity aLow = place(player, GateSize.MEDIUM, site.block((int) Math.floor(A_X), -1, 0));
            site.ids().put(A_LOW, aLow.getId());
            GateEntity bLow = place(player, GateSize.LARGE, site.block((int) Math.floor(B_X), -1, 0));
            site.ids().put(B_LOW, bLow.getId());
            GateEntity bMid = stack(player, bLow, GateSize.MEDIUM, GatePlacement.Preference.DEFAULT);
            site.ids().put(B_MID, bMid.getId());
            GateEntity bTop = stack(player, bLow, GateSize.SMALL, turned(90.0F));
            site.ids().put(B_TOP, bTop.getId());
            for (GateEntity gate : List.of(aLow, bLow, bMid, bTop)) {
                gate.refreshColumn();
            }
            if (bLow.stack().size() != 3 || !same(relative(bMid.geometry(), bLow.geometry()), 0.0F)
                    || !same(relative(bTop.geometry(), bMid.geometry()), 90.0F)) {
                return "Stapel aus drei Gates falsch: " + bLow.stack().size() + " Gates, Drehung oben " + relative(bTop.geometry(), bMid.geometry());
            }
            Vec3 feet = site.raw(STAND.x, 0.0, STAND.z);
            player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), FACE, 15.0F, true);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
            return null;
        });
        if (wait != null) {
            return wait;
        }
        GateEntity bTop = client(B_TOP);
        boolean seen = client(A_LOW) != null && client(B_LOW) != null && client(B_MID) != null && bTop != null && bTop.crossed()
                && bTop.base() == GateStacking.Base.MOUNTED;
        if (!seen) {
            return ctx.tick() > 800 ? StepResult.fail("Aufbau kommt beim Client nicht an") : StepResult.pending();
        }
        ctx.log("Stapel B: großes Gate, mittleres gleich ausgerichtet auf dem gemeinsamen Balken, kleines mit Vorliebe 90° quer obenauf");
        return StepResult.pass();
    }

    private static StepResult clickStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        CovRaceSite.Site site = SITE.get();
        GateEntity low = client(A_LOW);
        if (player == null || site == null || low == null) {
            return StepResult.fail("Aufbau fehlt");
        }
        int phase = CovRaceSite.phase(ctx);
        Vec3 stand = new Vec3(low.getX(), low.getY(), low.getZ() + 2.5);
        switch (phase) {
            case 0 -> {
                StepResult wait = CovRaceSite.once(ctx, "Stand", server -> {
                    ServerPlayer p = CovRaceSite.player(server);
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.GATE_MEDIUM.get(), 16));
                    p.connection.send(new ClientboundSetHeldSlotPacket(p.getInventory().getSelectedSlot()));
                    p.teleportTo(p.level(), stand.x, stand.y, stand.z, Set.of(), FACE, 30.0F, true);
                    p.resetFallDistance();
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                if (player.position().distanceTo(stand) > 0.3 || !player.getMainHandItem().is(RaceModule.GATE_MEDIUM.get()) || mc.gui.screen() != null) {
                    return CovRaceSite.since(ctx) > WAIT_TICKS ? StepResult.fail("Spieler kommt nicht mit Gate-Item an") : StepResult.pending();
                }
                GateMountKeys.setPreference(turned(45.0F));
                CovRaceSite.next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult wait = CovRaceSite.once(ctx, "Vorliebe " + (ctx.tick() / 10), server -> {
                    GatePlacement.Preference seen = GateMounting.preference(CovRaceSite.player(server), false);
                    ctx.state().put("vorliebe", GateStacking.placementTurn(seen));
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                Object seen = ctx.state().get("vorliebe");
                if (!(seen instanceof Float turn) || !same(turn, 45.0F)) {
                    return CovRaceSite.since(ctx) > WAIT_TICKS ? StepResult.fail("Server kennt die Vorliebe 45° nicht: " + seen) : StepResult.pending();
                }
                GateGeometry g = low.geometry();
                CovRaceSite.lookAt(player, new Vec3(g.x(), g.y() + g.height() - g.frame() * 0.5, g.z()));
                if (!(mc.hitResult instanceof EntityHitResult hit) || hit.getEntity() != low) {
                    return CovRaceSite.since(ctx) > 60 ? StepResult.fail("Fadenkreuz trifft das untere Gate nicht: " + mc.hitResult) : StepResult.pending();
                }
                MUTED.set(GatePlaceHint.muted());
                GatePlaceHint.mute(true);
                ctx.state().put("erkannt", GatePlaceHint.detected());
                KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
                CovRaceSite.next(ctx, 2);
                return StepResult.pending();
            }
            default -> {
                List<GateEntity> column = low.stack();
                GateEntity top = column.size() == 2 ? column.getLast() : null;
                if (top == null || GatePlaceHint.detected() <= (int) ctx.state().get("erkannt") || top.base() != GateStacking.Base.MOUNTED) {
                    return CovRaceSite.since(ctx) > WAIT_TICKS ? StepResult.fail("Klick stapelt kein gedrehtes Gate (Stapel " + column.size()
                            + ", erkannt " + GatePlaceHint.detected() + ")") : StepResult.pending();
                }
                GateGeometry upper = top.geometry();
                GateGeometry lower = low.geometry();
                site.ids().put(A_TOP, top.getId());
                GateMountKeys.setPreference(GatePlacement.Preference.DEFAULT);
                GatePlaceHint.mute(MUTED.get());
                if (!same(relative(upper, lower), 45.0F) || Math.abs(upper.y() - (lower.y() + lower.height())) > EPS
                        || Math.abs(upper.x() - lower.x()) > EPS || Math.abs(upper.z() - lower.z()) > EPS) {
                    return StepResult.fail("Klick: oberes Gate " + relative(upper, lower) + "° bei " + upper.y() + " statt 45° auf " + (lower.y() + lower.height()));
                }
                ctx.log("echter Klick mit Vorliebe 45°: oberes Gate um " + relative(upper, lower) + "° gedreht obenauf (Höhe " + upper.y()
                        + "), Gate-Hinweis hat das neue Gate erkannt");
                return StepResult.pass();
            }
        }
    }

    private static @Nullable String seat(GateEntity lower, GateEntity upper, Level level) {
        GateGeometry low = lower.geometry();
        GateGeometry up = upper.geometry();
        if (lower.base() != GateStacking.Base.LEGS || upper.base() != GateStacking.Base.MOUNTED || !upper.crossed() || !lower.covered()) {
            return "Rollen: unten " + lower.base() + ", oben " + upper.base();
        }
        if (Math.abs(up.y() - (low.y() + low.height())) > EPS) {
            return "oberes Gate nicht auf dem Querbalken: " + up.y() + " statt " + (low.y() + low.height());
        }
        double lowest = Double.POSITIVE_INFINITY;
        for (OrientedBox box : upper.orientedColliders()) {
            lowest = Math.min(lowest, box.minY());
        }
        for (AABB box : upper.colliders()) {
            lowest = Math.min(lowest, box.minY);
        }
        if (Math.abs(lowest - (low.y() + low.height())) > 0.02) {
            return "Unterkante des oberen Gates bei " + lowest + " statt auf dem Balken " + (low.y() + low.height());
        }
        if (Math.abs(upper.getBoundingBox().minY - (low.y() + low.height())) > EPS) {
            return "Hitbox des oberen Gates beginnt bei " + upper.getBoundingBox().minY;
        }
        if (!clearOf(level, up, lower)) {
            return "oberes Gate überschneidet das untere";
        }
        return null;
    }

    private static boolean clearOf(Level level, GateGeometry upper, GateEntity lower) {
        List<OrientedBox> others = new ArrayList<>();
        GateCollisions.collectOrientedSkipping(level, lower.getBoundingBox().inflate(0.5), others, gate -> gate != lower);
        List<OrientedBox> parts = new ArrayList<>();
        upper.parts(part -> parts.add(part.deflated(0.01)), GateStacking.Base.MOUNTED);
        for (OrientedBox part : parts) {
            for (OrientedBox other : others) {
                if (part.intersects(other)) {
                    return false;
                }
            }
        }
        return !others.isEmpty();
    }

    private static StepResult seatStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        GateEntity aLow = client(A_LOW);
        GateEntity aTop = client(A_TOP);
        GateEntity bMid = client(B_MID);
        GateEntity bTop = client(B_TOP);
        if (aLow == null || aTop == null || bMid == null || bTop == null || mc.level == null) {
            return StepResult.fail("Gates fehlen beim Client");
        }
        String problem = seat(aLow, aTop, mc.level);
        if (problem == null && (bMid.base() != GateStacking.Base.STACKED || bMid.crossed() || bTop.base() != GateStacking.Base.MOUNTED)) {
            problem = "Stapel B: Mitte " + bMid.base() + ", oben " + bTop.base();
        }
        if (problem != null) {
            return ctx.tick() > 200 ? StepResult.fail("Client: " + problem) : StepResult.pending();
        }
        StepResult wait = CovRaceSite.once(ctx, "Server", server -> {
            ServerLevel level = CovRaceSite.player(server).level();
            CovRaceSite.Site site = SITE.get();
            GateEntity low = CovRaceSite.require(level, site, A_LOW);
            GateEntity top = CovRaceSite.require(level, site, A_TOP);
            low.refreshColumn();
            String seat = seat(low, top, level);
            if (seat != null) {
                return seat;
            }
            GateEntity mid = CovRaceSite.require(level, site, B_MID);
            GateEntity high = CovRaceSite.require(level, site, B_TOP);
            if (Math.abs(high.getY() - (mid.getY() + mid.height())) > EPS || mid.base() != GateStacking.Base.STACKED) {
                return "Stapel B am Server: oben bei " + high.getY() + ", Mitte " + mid.base();
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("gedrehte Gates liegen mit eigenem Balken auf dem Querbalken, Hitbox und Kollision beginnen dort, unten Füße (LEGS)");
        return StepResult.pass();
    }

    private static StepResult rotateStep(SelfTest.StepContext ctx) {
        StepResult wait = CovRaceSite.once(ctx, "Drehen", server -> {
            ServerPlayer player = CovRaceSite.player(server);
            ServerLevel level = player.level();
            CovRaceSite.Site site = SITE.get();
            GateEntity low = CovRaceSite.require(level, site, A_LOW);
            GateEntity top = CovRaceSite.require(level, site, A_TOP);
            Vec3 near = site.raw(A_X, 0.0, 4.0);
            player.teleportTo(level, near.x, near.y, near.z, Set.of(), FACE, 0.0F, true);
            float[][] steps = {{45.0F, 90.0F}, {90.0F, 180.0F}, {15.0F, -165.0F}, {5.0F, -160.0F}, {90.0F, -70.0F}};
            StringBuilder log = new StringBuilder();
            try {
                for (float[] step : steps) {
                    GateMounting.Result result = GateMounting.rotate(player, top, false, step[0]);
                    low.refreshColumn();
                    float rel = relative(top.geometry(), low.geometry());
                    boolean half = GateStacking.halfTurn(rel);
                    double want = half ? GateStacking.seat(low.geometry()) : low.getY() + low.height();
                    if (result != GateMounting.Result.ROTATED || !same(rel, step[1]) || Math.abs(top.getY() - want) > EPS
                            || top.base() != (half ? GateStacking.Base.STACKED : GateStacking.Base.MOUNTED) || low.stack().size() != 2) {
                        return "Taste " + step[0] + "°: " + result + ", Drehung " + rel + " statt " + step[1] + ", Höhe " + top.getY() + " statt " + want
                                + ", Fuß " + top.base();
                    }
                    log.append(step[0]).append("° → ").append(rel).append("° ").append(top.base()).append("; ");
                }
                if (GateMounting.rotate(player, top, true) != GateMounting.Result.STACK_LOCKED) {
                    return "Lagewechsel im Stapel nicht gesperrt";
                }
                GateGeometry before = top.geometry();
                GateMounting.Result wider = GateMounting.edit(player, top, before.heading(), 0.0F, 0.0F, top.openingWidth() + 1.0, top.openingHeight());
                GateMounting.Result moved = GateMounting.edit(player, top, before.heading(), 0.0F, 0.0F, top.openingWidth(), top.openingHeight(),
                        top.variant(), top.score(), 0.0, 0.5, 0.0);
                if (wider != GateMounting.Result.STACK_LOCKED || moved != GateMounting.Result.STACK_LOCKED || !top.geometry().sameAs(before)) {
                    return "Breite oder Position im Stapel nicht gesperrt: " + wider + ", " + moved;
                }
                GateMounting.Result back = GateMounting.edit(player, top, low.geometry().heading() + 45.0F, 0.0F, 0.0F, top.openingWidth(),
                        top.openingHeight());
                low.refreshColumn();
                if (back != GateMounting.Result.EDITED || !same(relative(top.geometry(), low.geometry()), 45.0F)) {
                    return "Gate-Menü am Server stellt 45° nicht ein: " + back + ", " + relative(top.geometry(), low.geometry());
                }
            } finally {
                Vec3 feet = site.raw(STAND.x, 0.0, STAND.z);
                player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), FACE, 15.0F, true);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
            }
            ctx.state().put("log", log.toString());
            return null;
        });
        if (wait != null) {
            return wait;
        }
        GateEntity low = client(A_LOW);
        GateEntity top = client(A_TOP);
        if (low == null || top == null || !same(relative(top.geometry(), low.geometry()), 45.0F) || top.base() != GateStacking.Base.MOUNTED) {
            return ctx.tick() > 200 ? StepResult.fail("Client sieht die Drehung nicht") : StepResult.pending();
        }
        ctx.log("Dreh-Taste: " + ctx.state().get("log") + "Lagewechsel, Breite und Position gesperrt, Gate-Menü stellt 45° ein");
        return StepResult.pass();
    }

    private static @Nullable GateEditor open(String key) {
        Minecraft mc = Minecraft.getInstance();
        GateEntity entity = client(key);
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

    private static StepResult editorStep(SelfTest.StepContext ctx) {
        GateEntity low = client(B_LOW);
        GateEntity mid = client(B_MID);
        GateEntity top = client(B_TOP);
        if (low == null || mid == null || top == null) {
            return StepResult.fail("Stapel B fehlt beim Client");
        }
        if (!ctx.state().containsKey("vorher")) {
            for (String key : List.of(A_TOP, B_MID)) {
                GateEditor editor = open(key);
                if (editor == null) {
                    return StepResult.fail(key + ": Gate-Menü startet nicht");
                }
                try {
                    float heading = editor.gate().geometry().heading();
                    editor.set(GateEditModel.Row.HEADING, heading + 15.0F);
                    if (editor.status() != GateEditor.Status.FREE || editor.turn() == null) {
                        return StepResult.fail(key + ": Drehung um 15° im Menü " + editor.status());
                    }
                    editor.set(GateEditModel.Row.WIDTH, editor.model().width() + 1.0);
                    if (editor.status() != GateEditor.Status.STACKED) {
                        return StepResult.fail(key + ": Breite im Stapel " + editor.status() + " statt STACKED");
                    }
                } finally {
                    GateEditor.stop();
                }
            }
            GateEditor editor = open(B_LOW);
            if (editor == null) {
                return StepResult.fail("Gate-Menü des unteren Gates startet nicht");
            }
            ctx.state().put("vorher", new float[]{low.geometry().heading(), mid.geometry().heading(), top.geometry().heading()});
            ctx.state().put("hoehe", new double[]{low.getY(), mid.getY(), top.getY()});
            editor.set(GateEditModel.Row.HEADING, low.geometry().heading() + 30.0F);
            GateMounting.ColumnTurn turn = editor.turn();
            if (editor.status() != GateEditor.Status.FREE || turn == null || turn.gates().size() != 3) {
                GateEditor.stop();
                return StepResult.fail("unteres Gate: " + editor.status() + ", Vorschau mit " + (turn == null ? 0 : turn.gates().size()) + " Gates");
            }
            if (!editor.apply()) {
                return StepResult.fail("Gate-Menü übernimmt die Drehung nicht: " + editor.status());
            }
            return StepResult.pending();
        }
        float[] before = (float[]) ctx.state().get("vorher");
        double[] heights = (double[]) ctx.state().get("hoehe");
        GateEntity[] gates = {low, mid, top};
        for (int i = 0; i < 3; i++) {
            if (!same(gates[i].geometry().heading(), before[i] + 30.0F) || Math.abs(gates[i].getY() - heights[i]) > EPS) {
                return ctx.tick() > 400 ? StepResult.fail("Gate " + (i + 1) + " nach dem Drehen: " + gates[i].geometry().heading() + "° statt "
                        + (before[i] + 30.0F) + "°, Höhe " + gates[i].getY()) : StepResult.pending();
            }
        }
        if (!same(relative(top.geometry(), mid.geometry()), 90.0F) || top.base() != GateStacking.Base.MOUNTED || mid.base() != GateStacking.Base.STACKED) {
            return StepResult.fail("relative Drehung nicht erhalten: " + relative(top.geometry(), mid.geometry()));
        }
        ctx.log("Gate-Menü: oberes und mittleres Gate drehen frei (FREE), Breite STACKED; unteres Gate um 30° gedreht, beide Gates darüber mit, "
                + "oben weiter 90° quer");
        return StepResult.pass();
    }

    private static List<Vec3> route(List<GateGeometry> gates, GateOpenings openings) {
        List<Vec3> path = new ArrayList<>();
        for (double[] point : StackTurnRoute.lap(gates, openings)) {
            path.add(new Vec3(point[0], point[1], point[2]));
        }
        return path;
    }

    private static StepResult courseStep(SelfTest.StepContext ctx) {
        StepResult wait = CovRaceSite.once(ctx, "Kurse", server -> {
            ServerPlayer player = CovRaceSite.player(server);
            ServerLevel level = player.level();
            CovRaceSite.Site site = SITE.get();
            ItemStack toolA = new ItemStack(RaceModule.COURSE_TOOL.get());
            for (String key : List.of(A_LOW, A_TOP)) {
                CourseToolItem.useOnGate(toolA, player, CovRaceSite.require(level, site, key));
            }
            ItemStack toolB = new ItemStack(RaceModule.COURSE_TOOL.get());
            for (String key : List.of(B_LOW, B_MID, B_TOP)) {
                CourseToolItem.useOnGate(toolB, player, CovRaceSite.require(level, site, key));
            }
            site.ids().put(COURSE_A, CourseToolItem.courseOf(toolA));
            site.ids().put(COURSE_B, CourseToolItem.courseOf(toolB));
            return CourseToolItem.courseOf(toolA) == GateEntity.NO_COURSE || CourseToolItem.courseOf(toolB) == GateEntity.NO_COURSE
                    ? "Kurse nicht angelegt" : null;
        });
        if (wait != null) {
            return wait;
        }
        CovRaceSite.Site site = SITE.get();
        List<String> done = new ArrayList<>();
        for (String key : List.of(COURSE_A, COURSE_B)) {
            int id = site.id(key);
            CourseData data = ClientCourses.get(id);
            int size = key.equals(COURSE_A) ? 2 : 3;
            if (data == null || data.gates().size() != size) {
                return ctx.tick() > 400 ? StepResult.fail(key + ": Kurs kommt beim Client nicht an") : StepResult.pending();
            }
            List<GateGeometry> gates = data.geometry();
            float rel = relative(gates.get(size - 1), gates.get(size - 2));
            if (!same(rel, key.equals(COURSE_A) ? 45.0F : 90.0F)) {
                return StepResult.fail(key + ": Kursdaten beim Client ohne Drehung: " + rel);
            }
            ClientGateOpenings openings = ClientGateOpenings.of(id, gates);
            if (!openings.legs(0)) {
                return ctx.tick() > 400 ? StepResult.fail(key + ": unteres Gate beim Client nicht auf Beinen") : StepResult.pending();
            }
            List<Vec3> path = route(gates, openings);
            String lap = CovRaceSite.clientLap(id, gates, path, openings);
            if (lap != null) {
                return StepResult.fail(key + ": " + lap);
            }
            String serverKey = "Runde " + key;
            StepResult server = CovRaceSite.once(ctx, serverKey, s -> CovRaceSite.serverLap(s, CovRaceSite.player(s), id, path, true).problem());
            if (server != null) {
                return server;
            }
            done.add(key + " " + size + " Gates, oben " + rel + "°");
        }
        ctx.log("Runde unten durch das Gate auf Beinen und durch jedes gedrehte Gate: Client zählt, Server nimmt an (" + String.join("; ", done) + ")");
        return StepResult.pass();
    }

    private static boolean solidAt(GateEntity gate, Vec3 point) {
        double h = 0.02;
        for (OrientedBox box : gate.orientedColliders()) {
            if (box.intersects(point.x - h, point.y - h, point.z - h, point.x + h, point.y + h, point.z + h)) {
                return true;
            }
        }
        for (AABB box : gate.colliders()) {
            if (box.inflate(h).contains(point)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable String solid(GateEntity top) {
        GateGeometry g = top.geometry();
        Vec3 bar = new Vec3(g.x(), g.y() + g.frame() * 0.5, g.z());
        Vec3 opening = new Vec3(g.x(), g.centerY(), g.z());
        if (!solidAt(top, bar)) {
            return "eigener Balken des gedrehten Gates kollidiert nicht";
        }
        if (solidAt(top, opening)) {
            return "Öffnung des gedrehten Gates ist zu";
        }
        Vec3 post = new Vec3(g.worldX(g.postOffset(), g.height() * 0.5, 0.0), g.worldY(g.postOffset(), g.height() * 0.5, 0.0),
                g.worldZ(g.postOffset(), g.height() * 0.5, 0.0));
        if (!solidAt(top, post)) {
            return "gedrehter Pfosten kollidiert nicht";
        }
        if (!top.getBoundingBox().contains(post) || !top.getBoundingBox().contains(bar)) {
            return "Hitbox umfasst das gedrehte Gate nicht";
        }
        return null;
    }

    private static StepResult collisionStep(SelfTest.StepContext ctx) {
        GateEntity top = client(A_TOP);
        if (top == null) {
            return StepResult.fail("oberes Gate fehlt");
        }
        String problem = solid(top);
        if (problem != null) {
            return StepResult.fail("Client: " + problem);
        }
        StepResult wait = CovRaceSite.once(ctx, "Server", server -> solid(CovRaceSite.require(CovRaceSite.player(server).level(), SITE.get(), A_TOP)));
        if (wait != null) {
            return wait;
        }
        ctx.log("gedrehtes Gate: eigener Balken und Pfosten kollidieren, Öffnung frei, Hitbox umfasst es (Client und Server)");
        return StepResult.pass();
    }

    private static StepResult removeStep(SelfTest.StepContext ctx) {
        StepResult wait = CovRaceSite.once(ctx, "Abbau", server -> {
            ServerLevel level = CovRaceSite.player(server).level();
            CovRaceSite.Site site = SITE.get();
            GateEntity low = CovRaceSite.require(level, site, B_LOW);
            GateEntity mid = CovRaceSite.require(level, site, B_MID);
            GateEntity top = CovRaceSite.require(level, site, B_TOP);
            float heading = top.geometry().heading();
            mid.discard();
            low.refreshColumn();
            top.refreshColumn();
            if (Math.abs(top.getY() - (low.getY() + low.height())) > EPS || !same(top.geometry().heading(), heading) || !top.crossed()
                    || top.base() != GateStacking.Base.MOUNTED || low.stack().size() != 2) {
                return "oberes Gate nach dem Abbau bei " + top.getY() + " statt " + (low.getY() + low.height()) + ", Fuß " + top.base();
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        GateEntity low = client(B_LOW);
        GateEntity top = client(B_TOP);
        if (low == null || top == null || Math.abs(top.getY() - (low.getY() + low.height())) > EPS || top.base() != GateStacking.Base.MOUNTED) {
            return ctx.tick() > 200 ? StepResult.fail("Client: oberes Gate nach dem Abbau nicht obenauf") : StepResult.pending();
        }
        ctx.log("mittleres Gate abgebaut: kleines Gate fällt auf das große und liegt gedreht mit eigenem Balken obenauf");
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        GateEditor.stop();
        Minecraft.getInstance().gui.setScreen(null);
        GateMountKeys.setPreference(GatePlacement.Preference.DEFAULT);
        CovRaceSite.Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        StepResult wait = CovRaceSite.once(ctx, "Abbau", server -> {
            GatePlacement.Preference before = PREFERENCE.getAndSet(null);
            ServerPlayer player = CovRaceSite.player(server);
            GateMounting.setPreference(player, before == null ? GatePlacement.Preference.DEFAULT : before);
            return CovRaceSite.teardown(server, site);
        });
        if (wait != null) {
            return wait;
        }
        SITE.set(null);
        return StepResult.pass();
    }

    private static void look(Minecraft mc, double x, double y, double z) {
        CovRaceSite.Site site = SITE.get();
        if (mc.player != null && site != null) {
            CovRaceSite.lookAt(mc.player, site.raw(x, y, z));
        }
    }

    private static final class StackShot implements Screenshots.Scene {
        private final String low;
        private final double x;
        private final double side;

        StackShot(String low, double x, double side) {
            this.low = low;
            this.x = x;
            this.side = side;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            GateEntity low = client(this.low);
            if (mc.player == null || low == null) {
                return StepResult.fail("Aufbau für das Bildschirmfoto fehlt");
            }
            GateEditor.stop();
            mc.getTutorial().setStep(TutorialSteps.NONE);
            StepResult wait = CovRaceSite.once(ctx, "Stand", server -> {
                ServerPlayer player = CovRaceSite.player(server);
                Vec3 feet = SITE.get().raw(this.x + this.side, 0.0, GATE_Z + 8.0);
                player.teleportTo(player.level(), feet.x, feet.y, feet.z, Set.of(), FACE, 15.0F, true);
                return null;
            });
            if (wait != null) {
                return wait;
            }
            this.hold(ctx);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.toastManager().clear();
            look(mc, this.x, 5.0, GATE_Z);
        }
    }

    private static final class MenuShot implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            GateEntity top = client(A_TOP);
            if (mc.player == null || top == null) {
                return StepResult.fail("Aufbau für das Bildschirmfoto fehlt");
            }
            mc.getTutorial().setStep(TutorialSteps.NONE);
            StepResult wait = CovRaceSite.once(ctx, "Stand", server -> {
                ServerPlayer player = CovRaceSite.player(server);
                Vec3 feet = SITE.get().raw(A_X + 1.5, 0.0, GATE_Z + 11.0);
                player.teleportTo(player.level(), feet.x, feet.y, feet.z, Set.of(), FACE, 15.0F, true);
                return null;
            });
            if (wait != null) {
                return wait;
            }
            look(mc, A_X + 4.5, 5.0, GATE_Z);
            GateEditor editor = GateEditor.current();
            if (editor == null || editor.gate() != top) {
                editor = open(A_TOP);
                if (editor == null) {
                    return StepResult.fail("Menü für das Bildschirmfoto startet nicht");
                }
                editor.set(GateEditModel.Row.HEADING, top.geometry().heading() + 45.0F);
                ctx.state().put(OPENED, ctx.tick());
            }
            if (editor.status() != GateEditor.Status.FREE) {
                return ctx.tick() > 300 ? StepResult.fail("Vorschau nicht frei: " + editor.status()) : StepResult.pending();
            }
            return ctx.tick() - (int) ctx.state().get(OPENED) < TIP_TICKS ? StepResult.pending() : StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.toastManager().clear();
            look(mc, A_X + 4.5, 5.0, GATE_Z);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            GateEditor.stop();
            Minecraft.getInstance().gui.setScreen(null);
        }
    }
}
