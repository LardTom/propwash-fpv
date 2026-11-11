package de.lardtom.propwash.race.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseOrder;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.FreeformBlocks;
import de.lardtom.propwash.race.GateCollisions;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GatePoleLinks;
import de.lardtom.propwash.race.GatePoles;
import de.lardtom.propwash.race.GateOpenings;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Passage;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.ScoreArea;
import de.lardtom.propwash.race.client.CovRaceSite.Site;
import de.lardtom.propwash.rescue.HomePoint;
import de.lardtom.propwash.rescue.HomePointCodecs;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.rescue.client.RescueState;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.LayoutProbe;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class CovRaceSystemSelfTests {
    public static final String GROUP = "race: Abdeckung – Rennsystem ";
    public static final String SHOTS = "bildschirmfotos: Abdeckung Rennsystem – ";
    private static final int OFFSET_X = -1700;
    private static final int OFFSET_Z = 1600;
    private static final int[] BOUNDS = {-40, 42, -12, 66, 16};
    private static final double EPS = 1.0E-4;
    private static final double MARGIN = 0.1;
    private static final double LEAD = 3.0;
    private static final double PILLAR = 3.0;
    private static final double FLAG_HEIGHT = 7.0;
    private static final double BELOW = 2.5;
    private static final double ABOVE = 1.5;
    private static final String GUEST = "PwCovGast";
    private static final AtomicReference<Site> SITE = new AtomicReference<>();
    private static final AtomicReference<String> DEBUG = new AtomicReference<>("");
    private static final AtomicReference<@Nullable ItemStack> LOOP_TOOL = new AtomicReference<>();

    private CovRaceSystemSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "– Aufbau: Kurse für Wertungshöhe, Gate-Stange, Freiform-Gate, Schleife, Stapeln und Verschieben", 1200,
                CovRaceSystemSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "R2 – Wertungshöhe einer hohen Flagge im Menü nach unten und oben, Durchflug knapp darunter und darüber", 900,
                CovRaceSystemSelfTests::heightStep);
        SelfTest.clientStep(GROUP + "R3 – Gate-Stange im Kurs: Runde durch den Bereich zählt, daneben nicht (Client und Server)", 300,
                CovRaceSystemSelfTests::gatePoleStep);
        SelfTest.clientStep(GROUP + "R4 – Freiform-Gate mit ignorierten Lücken: innen zählt, außen nicht, ohne Schleichen abgelehnt", 300,
                CovRaceSystemSelfTests::freeformStep);
        SelfTest.clientStep(GROUP + "R7 – Schleife durch zwei Gates in beide Richtungen: Nummern, Kurs-Fenster, HUD, Bestenliste, falsche Richtung", 600,
                CovRaceSystemSelfTests::loopStep);
        SelfTest.clientStep(GROUP + "R8 – Flagge und Stange auf dem Tauchwürfel, Gate-Stange auf dem Gate: Wertung, Kollision, Abbauen", 400,
                CovRaceSystemSelfTests::stackStep);
        SelfTest.clientStep(GROUP + "R6 – Verschieben in 1/16, 1/4 und 1 Block mit Tasten und Knöpfen, Kollision und Fläche wandern mit", 900,
                CovRaceSystemSelfTests::nudgeStep);
        SelfTest.clientStep(GROUP + "R6 – Gast ohne Recht wird beim Verschieben abgelehnt", 200, CovRaceSystemSelfTests::guestStep);
        SelfTest.clientStep(GROUP + "R3/R6 – Kursdaten und Gates neu geladen: Gate-Stange im Kurs und verschobene Lage bleiben", 300,
                CovRaceSystemSelfTests::reloadStep);
        SelfTest.clientStep(GROUP + "R1 – GPS-Rescue zum Homepoint am Start-Gate startet keinen Countdown, Scharfschalten ohne Einstellung auch nicht",
                2400, CovRaceSystemSelfTests::rescueStep);
        SelfTest.clientStep(GROUP + "R5 – Durchflugfläche im FPV und in der dritten Person gezeichnet, ohne aktiven Kurs nicht", 900,
                CovRaceSystemSelfTests::areaStep);
        if (Screenshots.enabled()) {
            Supplier<ItemStack> tool = () -> new ItemStack(RaceModule.COURSE_TOOL.get());
            Screenshots.step(SHOTS + "Wertungsflächen im FPV am Start-Gate", "race_cov_areas_fpv", 40, new Fpv());
            Screenshots.step(SHOTS + "Wertungsflächen in der dritten Person beim Bauen", "race_cov_areas_third_person", 40,
                    new ThirdPerson(new Vec3(-15.0, 0.0, 2.0), new Vec3(-9.5, 2.0, 20.0), tool, -22.0F));
            Screenshots.step(SHOTS + "Wertungsflächen in der dritten Person ohne Werkzeug", "race_cov_areas_third_person_plain", 40,
                    new ThirdPerson(new Vec3(-13.0, 0.0, 8.0), new Vec3(-9.5, 1.5, 14.0), () -> ItemStack.EMPTY, -24.0F));
            Screenshots.step(SHOTS + "hohe Flagge mit Wertung nach unten und oben", "race_cov_tall_flag", 40,
                    new ThirdPerson(new Vec3(-38.0, 0.0, 4.0), new Vec3(-29.5, 5.0, 14.5), tool, -22.0F));
            CovRaceIrisShowcase.register(new Fpv(), new ThirdPerson(new Vec3(-15.0, 0.0, 2.0), new Vec3(-9.5, 2.0, 20.0), tool, -22.0F));
        }
        SelfTest.clientStep(GROUP + "– aufräumen", 600, CovRaceSystemSelfTests::cleanupStep);
    }

    private static Site site() {
        return SITE.get();
    }

    private static void put(Site site, String key, GateEntity gate) {
        site.ids().put(key, gate.getId());
    }

    private static GateGeometry gateAt(Site site, double x, double z, float yaw) {
        Vec3 at = site.raw(x, 0.0, z);
        return GateGeometry.of(at.x, at.y, at.z, yaw, GateSize.MEDIUM);
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult prepared = CovRaceSite.prepare(ctx, SITE, OFFSET_X, OFFSET_Z, BOUNDS, new Vec3(0.5, 0.0, 25.0));
        if (prepared.status() != StepResult.Status.PASS) {
            return prepared;
        }
        StepResult wait = CovRaceSite.once(ctx, "Aufbau", CovRaceSystemSelfTests::build);
        if (wait != null) {
            return wait;
        }
        if (!ctx.state().containsKey("zweiter Klick")) {
            ctx.state().put("zweiter Klick", ctx.tick());
        }
        if (ctx.tick() - (int) ctx.state().get("zweiter Klick") < 14) {
            return StepResult.pending();
        }
        wait = CovRaceSite.once(ctx, "Schleife zurück", CovRaceSystemSelfTests::loopBack);
        if (wait != null) {
            return wait;
        }
        Site site = site();
        for (String key : List.of("s2", "f2", "s3", "p3", "l3", "f3", "s4", "ff4", "a7", "b7", "s8", "d8", "pole8", "flag8", "g8", "gp8", "n6", "m6")) {
            if (CovRaceSite.clientEntity(site, key) == null) {
                return ctx.tick() > 1100 ? StepResult.fail(key + " fehlt beim Client") : StepResult.pending();
            }
        }
        for (String key : List.of("course2", "course3", "course4", "course7", "course8", "course6")) {
            CourseData data = ClientCourses.get(site.id(key));
            if (data == null) {
                return ctx.tick() > 1100 ? StepResult.fail(key + " fehlt beim Client") : StepResult.pending();
            }
        }
        CourseData loop = ClientCourses.get(site.id("course7"));
        if (loop.gates().size() != 4) {
            return ctx.tick() > 1100 ? StepResult.fail("Schleife beim Client mit " + loop.gates().size() + " Durchgängen") : StepResult.pending();
        }
        ctx.log(DEBUG.get());
        return StepResult.pass();
    }

    private static int course(ServerPlayer player, float yaw, GateEntity... gates) {
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        CovRaceSite.face(player, yaw);
        for (GateEntity gate : gates) {
            CourseToolItem.useOnGate(tool, player, gate);
        }
        int id = CourseToolItem.courseOf(tool);
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(id);
        if (course == null || course.gates.size() != gates.length) {
            throw new IllegalStateException("Kurs nicht angelegt: " + (course == null ? "keiner" : course.gates.size() + " statt " + gates.length));
        }
        registry.setLaps(course, 1);
        return id;
    }

    private static InteractionResult interact(ServerPlayer player, GateEntity target, ItemStack stack, Vec3 point) {
        ItemStack previous = player.getMainHandItem().copy();
        try {
            CovRaceSite.face(player, 0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            return target.interact(player, InteractionHand.MAIN_HAND, point.subtract(target.position()));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
    }

    private static @Nullable GateEntity found(ServerLevel level, Vec3 at, CourseElement kind) {
        List<GateEntity> gates = level.getEntitiesOfClass(GateEntity.class, new AABB(at, at).inflate(0.4),
                gate -> !gate.isRemoved() && gate.kind() == kind && gate.position().distanceTo(at) < 0.4);
        return gates.size() == 1 ? gates.getFirst() : null;
    }

    private static @Nullable String build(MinecraftServer server) {
        ServerPlayer player = CovRaceSite.player(server);
        ServerLevel level = player.level();
        Site site = site();
        StringBuilder log = new StringBuilder();
        for (int y = 0; y < (int) PILLAR; y++) {
            CovRaceSite.set(level, site, site.block(-30, y, 14), Blocks.STONE_BRICKS.defaultBlockState());
        }
        GateEntity s2 = CovRaceSite.spawn(level, gateAt(site, -29.5, 0.5, 0.0F), GateSize.MEDIUM);
        Vec3 f = site.raw(-29.5, PILLAR, 14.5);
        GateEntity f2 = CovRaceSite.spawn(level, GateGeometry.element(f.x, f.y, f.z, 0.0F, CourseElement.FLAG, CourseElement.LEFT, 3.0, FLAG_HEIGHT,
                GateTilt.NONE), GateSize.MEDIUM);
        put(site, "s2", s2);
        put(site, "f2", f2);
        site.ids().put("course2", course(player, 0.0F, s2, f2));
        GateEntity s3 = CovRaceSite.spawn(level, gateAt(site, -9.5, 0.5, 0.0F), GateSize.MEDIUM);
        Vec3 p = site.raw(-9.5, 0.0, 12.5);
        GateEntity p3 = CovRaceSite.spawn(level, GateGeometry.element(p.x, p.y, p.z, 0.0F, CourseElement.GATE_POLE, 3, GatePoles.THICKNESS, 4.0,
                GateTilt.NONE, GateMount.STANDING), GateSize.MEDIUM);
        Vec3 l = site.raw(-9.5, 0.0, 22.5);
        GateEntity l3 = CovRaceSite.spawn(level, GateGeometry.element(l.x, l.y, l.z, 0.0F, CourseElement.POLE, CourseElement.LEFT, 4.0, 3.0,
                GateTilt.NONE), GateSize.MEDIUM);
        Vec3 fl = site.raw(-9.5, 0.0, 32.5);
        GateEntity f3 = CovRaceSite.spawn(level, GateGeometry.element(fl.x, fl.y, fl.z, 0.0F, CourseElement.FLAG, CourseElement.RIGHT, 4.0, 4.0,
                GateTilt.NONE), GateSize.MEDIUM);
        put(site, "s3", s3);
        put(site, "p3", p3);
        put(site, "l3", l3);
        put(site, "f3", f3);
        if (p3.inCourse() || p3.score().scoring()) {
            return "Gate-Stange wertet schon vor dem Kurs: " + p3.score();
        }
        site.ids().put("course3", course(player, 0.0F, s3, p3, l3, f3));
        if (p3.score().role() != ScoreArea.Role.PASS || !p3.inCourse()) {
            return "Gate-Stange nach dem Kurs-Werkzeug ohne Wertung: " + p3.score();
        }
        log.append("Gate-Stange mit dem Kurs-Werkzeug zugewiesen (").append(GateMounting.scoreName(p3.score()).getString()).append(", Reichweite ")
                .append(p3.score().reach()).append("); ");
        for (int y = 0; y <= 4; y++) {
            CovRaceSite.set(level, site, site.block(8, y, 14), Blocks.STONE_BRICKS.defaultBlockState());
            CovRaceSite.set(level, site, site.block(12, y, 14), Blocks.STONE_BRICKS.defaultBlockState());
        }
        GateEntity s4 = CovRaceSite.spawn(level, gateAt(site, 10.5, 0.5, 0.0F), GateSize.MEDIUM);
        put(site, "s4", s4);
        String freeform = placeFreeform(player, site);
        if (freeform != null) {
            return freeform;
        }
        site.ids().put("course4", course(player, 0.0F, s4, CovRaceSite.require(level, site, "ff4")));
        GateEntity a7 = CovRaceSite.spawn(level, gateAt(site, 30.5, 0.5, 0.0F), GateSize.MEDIUM);
        GateEntity b7 = CovRaceSite.spawn(level, gateAt(site, 30.5, 12.5, 0.0F), GateSize.MEDIUM);
        put(site, "a7", a7);
        put(site, "b7", b7);
        ItemStack loopTool = new ItemStack(RaceModule.COURSE_TOOL.get());
        CovRaceSite.face(player, 0.0F);
        CourseToolItem.useOnGate(loopTool, player, a7);
        CourseToolItem.useOnGate(loopTool, player, b7);
        site.ids().put("course7", CourseToolItem.courseOf(loopTool));
        LOOP_TOOL.set(loopTool);
        GateEntity s8 = CovRaceSite.spawn(level, gateAt(site, -29.5, 38.5, 0.0F), GateSize.MEDIUM);
        Vec3 d = site.raw(-29.5, 0.0, 50.5);
        GateEntity d8 = CovRaceSite.spawn(level, GateGeometry.element(d.x, d.y, d.z, 0.0F, CourseElement.CUBE, CourseElement.EXIT_FRONT, 4.5, 4.5,
                GateTilt.NONE), GateSize.MEDIUM);
        put(site, "s8", s8);
        put(site, "d8", d8);
        List<GatePoles.Seat> seats = GatePoles.seats(d8.geometry());
        GatePoles.Seat corner = seats.getFirst();
        GatePoles.Seat edge = null;
        for (GatePoles.Seat seat : seats) {
            if (seat.corner() < 0 && edge == null) {
                edge = seat;
            }
        }
        if (edge == null) {
            edge = seats.get(seats.size() - 1);
        }
        InteractionResult poleResult = interact(player, d8, new ItemStack(RaceModule.SLALOM_POLE.get()), new Vec3(corner.x(), corner.y(), corner.z()));
        GateEntity pole8 = found(level, new Vec3(corner.x(), corner.y(), corner.z()), CourseElement.POLE);
        InteractionResult flagResult = interact(player, d8, new ItemStack(RaceModule.RACE_FLAG.get()), new Vec3(edge.x(), edge.y(), edge.z()));
        GateEntity flag8 = found(level, new Vec3(edge.x(), edge.y(), edge.z()), CourseElement.FLAG);
        if (!poleResult.consumesAction() || pole8 == null || !flagResult.consumesAction() || flag8 == null) {
            return "Stange oder Flagge nicht auf den Tauchwürfel gesetzt: " + poleResult + " / " + flagResult + " (Plätze " + seats.size() + ")";
        }
        put(site, "pole8", pole8);
        put(site, "flag8", flag8);
        site.ids().put("course8", course(player, 0.0F, s8, pole8));
        GateEntity g8 = CovRaceSite.spawn(level, gateAt(site, -12.5, 50.5, 0.0F), GateSize.MEDIUM);
        put(site, "g8", g8);
        GatePoles.Seat top = GatePoles.seats(g8.geometry()).getFirst();
        InteractionResult gatePoleResult = interact(player, g8, new ItemStack(RaceModule.GATE_POLE.get()), new Vec3(top.x(), top.y(), top.z()));
        GateEntity gp8 = found(level, new Vec3(top.x(), top.y(), top.z()), CourseElement.GATE_POLE);
        if (!gatePoleResult.consumesAction() || gp8 == null) {
            return "Gate-Stange nicht auf das Gate gesetzt: " + gatePoleResult;
        }
        put(site, "gp8", gp8);
        log.append("Slalomstange auf einer Ecke und Flagge auf einer Kante des Tauchwürfels, Gate-Stange auf der Ecke eines Gates; ");
        GateEntity n6 = CovRaceSite.spawn(level, gateAt(site, 10.5, 46.5, 0.0F), GateSize.MEDIUM);
        GateEntity m6 = CovRaceSite.spawn(level, gateAt(site, 10.5, 58.5, 0.0F), GateSize.MEDIUM);
        put(site, "n6", n6);
        put(site, "m6", m6);
        site.ids().put("course6", course(player, 0.0F, n6, m6));
        DEBUG.set(log.toString());
        return null;
    }

    private static @Nullable String loopBack(MinecraftServer server) {
        ServerPlayer player = CovRaceSite.player(server);
        ServerLevel level = player.level();
        Site site = site();
        GateEntity a7 = CovRaceSite.require(level, site, "a7");
        GateEntity b7 = CovRaceSite.require(level, site, "b7");
        ItemStack tool = LOOP_TOOL.get();
        if (tool == null || CourseToolItem.courseOf(tool) != site.id("course7")) {
            return "Kurs-Werkzeug der Schleife verloren";
        }
        CovRaceSite.face(player, 180.0F);
        CourseToolItem.useOnGate(tool, player, b7);
        CourseToolItem.useOnGate(tool, player, a7);
        CovRaceSite.face(player, 0.0F);
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(site.id("course7"));
        if (course == null || course.gates.size() != 4 || course.gates.get(0).reversed() || course.gates.get(1).reversed() || !course.gates.get(2).reversed()
                || !course.gates.get(3).reversed()) {
            return "Schleife A, B, B rückwärts, A rückwärts mit Klicks von hinten nicht angelegt: " + (course == null ? "kein Kurs"
                    : course.gates.stream().map(g -> g.reversed() ? "r" : "v").toList());
        }
        registry.setLaps(course, 1);
        if (!a7.passes().equals(List.of(new CourseOrder.Pass(0, false), new CourseOrder.Pass(3, true)))
                || !b7.passes().equals(List.of(new CourseOrder.Pass(1, false), new CourseOrder.Pass(2, true)))) {
            return "Durchgänge an den Gates: A " + a7.passes() + ", B " + b7.passes();
        }
        LOOP_TOOL.set(null);
        return null;
    }

    private static @Nullable String placeFreeform(ServerPlayer player, Site site) {
        ServerLevel level = player.level();
        BlockPos post = site.block(8, 1, 14);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(post).add(0.5, 0.0, 0.0), Direction.EAST, post, false);
        ItemStack previous = player.getMainHandItem().copy();
        try {
            CovRaceSite.face(player, 0.0F);
            ItemStack stack = new ItemStack(RaceModule.FREEFORM_GATE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.setShiftKeyDown(false);
            if (stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()) {
                return "Freiform-Gate ohne Schleichen trotz offenem Rand gesetzt";
            }
            player.setShiftKeyDown(true);
            InteractionResult lenient = stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            if (!lenient.consumesAction()) {
                return "Freiform-Gate mit Schleichen nicht gesetzt: " + lenient;
            }
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        List<GateEntity> found = level.getEntitiesOfClass(GateEntity.class, new AABB(site.block(7, -1, 12)).expandTowards(7, 8, 4),
                gate -> gate.kind() == CourseElement.FREEFORM && !gate.isRemoved());
        if (found.size() != 1) {
            return "Freiform-Gate nicht gefunden: " + found.size();
        }
        GateEntity gate = found.getFirst();
        if (!gate.score().open() || gate.region() == null || gate.region().cells() != 15) {
            return "Freiform-Gate mit ignorierten Lücken: offen " + gate.score().open() + ", Fläche " + gate.region();
        }
        put(site, "ff4", gate);
        return null;
    }

    private static List<Vec3> around(GateGeometry start, GateGeometry element, double y) {
        List<Vec3> path = new ArrayList<>();
        double gy = start.centerY();
        path.add(new Vec3(start.x() - start.normalX() * LEAD, gy, start.z() - start.normalZ() * LEAD));
        path.add(new Vec3(start.x() + start.normalX() * LEAD, gy, start.z() + start.normalZ() * LEAD));
        for (GateGeometry.Waypoint point : element.waypoints()) {
            path.add(new Vec3(point.x() - point.dx() * LEAD, y, point.z() - point.dz() * LEAD));
            path.add(new Vec3(point.x() + point.dx() * LEAD, y, point.z() + point.dz() * LEAD));
        }
        Vec3 last = path.getLast();
        double side = last.x - start.x();
        double far = start.x() + (side >= 0.0 ? 8.0 : -8.0);
        path.add(new Vec3(far, gy, last.z));
        path.add(new Vec3(far, gy, start.z() - 4.0));
        path.add(new Vec3(start.x(), gy, start.z() - LEAD));
        path.add(new Vec3(start.x(), gy, start.z() + LEAD));
        return path;
    }

    private static List<Vec3> through(GateGeometry start, Vec3 point, Vec3 direction) {
        List<Vec3> path = new ArrayList<>();
        double gy = start.centerY();
        path.add(new Vec3(start.x() - start.normalX() * LEAD, gy, start.z() - start.normalZ() * LEAD));
        path.add(new Vec3(start.x() + start.normalX() * LEAD, gy, start.z() + start.normalZ() * LEAD));
        path.add(point.subtract(direction.scale(LEAD)));
        path.add(point.add(direction.scale(LEAD)));
        Vec3 last = path.getLast();
        double far = start.x() + (last.x - start.x() >= 0.0 ? 9.0 : -9.0);
        path.add(new Vec3(far, gy, last.z));
        path.add(new Vec3(far, gy, start.z() - 4.0));
        path.add(new Vec3(start.x(), gy, start.z() - LEAD));
        path.add(new Vec3(start.x(), gy, start.z() + LEAD));
        return path;
    }

    private static @Nullable String expect(int course, List<GateGeometry> gates, List<Vec3> path, boolean counts, String what) {
        List<LapTracker.Event> events = CovRaceSite.fly(course, gates, path, GateOpenings.NONE);
        List<Integer> passed = CovRaceSite.of(events, LapTracker.EventType.GATE);
        int laps = CovRaceSite.of(events, LapTracker.EventType.LAP).size();
        if (counts && (!passed.equals(CovRaceSite.upTo(gates.size())) || laps != 1)) {
            return what + ": Client zählt nicht " + events;
        }
        if (!counts && (laps != 0 || passed.contains(gates.size() - 1))) {
            return what + ": Client zählt, obwohl es nicht soll " + events;
        }
        return null;
    }

    private static StepResult heightStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0 && Minecraft.getInstance().gui.screen() != null) {
            Minecraft.getInstance().gui.setScreen(null);
        }
        Site site = site();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GateEntity flag = CovRaceSite.clientEntity(site, "f2");
        GateEntity start = CovRaceSite.clientEntity(site, "s2");
        if (site == null || player == null || flag == null || start == null) {
            return StepResult.fail("kein Aufbau");
        }
        int course = site.id("course2");
        switch (CovRaceSite.phase(ctx)) {
            case 0 -> {
                Vec3 stand = site.raw(-34.0, 0.0, 10.0);
                StepResult wait = CovRaceSite.once(ctx, "Werkzeug", server -> {
                    ServerPlayer p = CovRaceSite.player(server);
                    p.teleportTo(p.level(), stand.x, stand.y, stand.z, Set.of(), -45.0F, 0.0F, true);
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                if (!GateMounting.holdsTool(player) || player.position().distanceTo(stand) > 0.6) {
                    return CovRaceSite.since(ctx) > 200 ? StepResult.fail("Kurs-Werkzeug oder Platz kommt nicht an") : StepResult.pending();
                }
                GateEditor.start(flag);
                GateEditor editor = GateEditor.current();
                if (editor == null || editor.model().pageKind(1) != GateEditModel.Page.SCORE) {
                    return StepResult.fail("Menü der Flagge ohne Seite Wertung");
                }
                editor.showPage(1);
                if (!GateEditDriver.select(mc, editor, GateEditModel.Row.SCORE_BELOW)) {
                    return StepResult.fail("Zeile „nach unten“ nicht wählbar");
                }
                GateEditDriver.scroll(mc, 3);
                if (!GateEditDriver.select(mc, editor, GateEditModel.Row.SCORE_ABOVE)) {
                    return StepResult.fail("Zeile „nach oben“ nicht wählbar");
                }
                GateEditDriver.scroll(mc, 3);
                ScoreArea set = editor.model().score();
                if (Math.abs(set.below() - BELOW) > EPS || Math.abs(set.above() - ABOVE) > EPS) {
                    return StepResult.fail("Mausrad stellt die Wertung auf " + set.below() + " nach unten und " + set.above() + " nach oben");
                }
                GateEditDriver.press(mc, InputConstants.KEY_RETURN);
                CovRaceSite.next(ctx, 1);
                return StepResult.pending();
            }
            default -> {
                CourseData data = ClientCourses.get(course);
                if (Math.abs(flag.score().below() - BELOW) > EPS || Math.abs(flag.score().above() - ABOVE) > EPS || data == null
                        || Math.abs(data.gates().get(1).score().below() - BELOW) > EPS) {
                    return CovRaceSite.since(ctx) > 200 ? StepResult.fail("Wertung kommt nicht an: " + flag.score()) : StepResult.pending();
                }
                List<GateGeometry> gates = data.geometry();
                GateGeometry f = gates.get(1);
                double low = f.y() - BELOW;
                double high = f.y() + FLAG_HEIGHT + ABOVE;
                List<String> problems = new ArrayList<>();
                for (double y : new double[]{low + MARGIN, f.y() + 1.0, high - MARGIN}) {
                    String problem = expect(course, gates, around(gates.getFirst(), f, y), true, String.format(Locale.ROOT, "%.1f über dem Fuß", y - f.y()));
                    if (problem != null) {
                        problems.add(problem);
                    }
                }
                for (double y : new double[]{low - MARGIN, high + MARGIN}) {
                    String problem = expect(course, gates, around(gates.getFirst(), f, y), false, String.format(Locale.ROOT, "%.1f über dem Fuß", y - f.y()));
                    if (problem != null) {
                        problems.add(problem);
                    }
                }
                if (!problems.isEmpty()) {
                    return StepResult.fail(String.join("; ", problems));
                }
                StepResult wait = CovRaceSite.once(ctx, "Server", server -> {
                    ServerPlayer p = CovRaceSite.player(server);
                    List<String> out = new ArrayList<>();
                    for (double y : new double[]{low + MARGIN, high - MARGIN}) {
                        String problem = CovRaceSite.serverLap(server, p, course, around(gates.getFirst(), f, y), true).problem();
                        if (problem != null) {
                            out.add(String.format(Locale.ROOT, "%.1f: %s", y - f.y(), problem));
                        }
                    }
                    for (double y : new double[]{low - MARGIN, high + MARGIN}) {
                        String problem = CovRaceSite.serverLap(server, p, course, around(gates.getFirst(), f, y), false).problem();
                        if (problem != null) {
                            out.add(String.format(Locale.ROOT, "%.1f: %s", y - f.y(), problem));
                        }
                    }
                    return out.isEmpty() ? null : String.join("; ", out);
                });
                if (wait != null) {
                    return wait;
                }
                ctx.log(String.format(Locale.ROOT, "Flagge (7 Blöcke) auf %.0f Blöcken Säule, Wertung %.1f nach unten und %.1f nach oben per Mausrad: "
                        + "Durchflug %.1f über dem Boden und %.1f über der Spitze zählt, %.1f Block darunter bzw. darüber nicht (Client und Server)",
                        PILLAR, BELOW, ABOVE, PILLAR - BELOW + MARGIN, ABOVE - MARGIN, MARGIN));
                return StepResult.pass();
            }
        }
    }

    private static List<Vec3> withOverride(List<GateGeometry> gates, int index, Vec3 point, Vec3 direction) {
        return CovRaceSite.waypointPath(gates, LEAD, index, point, direction);
    }

    private static StepResult gatePoleStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0 && Minecraft.getInstance().gui.screen() != null) {
            Minecraft.getInstance().gui.setScreen(null);
        }
        Site site = site();
        int course = site.id("course3");
        CourseData data = ClientCourses.get(course);
        GateEntity pole = CovRaceSite.clientEntity(site, "p3");
        if (data == null || pole == null) {
            return StepResult.fail("Kurs mit Gate-Stange fehlt beim Client");
        }
        if (data.gates().get(1).kind() != CourseElement.GATE_POLE || data.gates().get(1).scoringKind() != CourseElement.POLE
                || pole.score().role() != ScoreArea.Role.PASS) {
            return StepResult.fail("Gate-Stange im Kurs beim Client: " + data.gates().get(1) + ", " + pole.score());
        }
        List<GateGeometry> gates = data.geometry();
        Passage stage = gates.get(1).stages().getFirst();
        double reach = stage.side1();
        Vec3 direction = new Vec3(stage.nx(), 0.0, stage.nz());
        Vec3 inside = new Vec3(stage.ox() + stage.sx() * (reach - 0.4), gates.get(1).y() + 1.5, stage.oz() + stage.sz() * (reach - 0.4));
        Vec3 beside = new Vec3(stage.ox() + stage.sx() * (reach + 0.6), gates.get(1).y() + 1.5, stage.oz() + stage.sz() * (reach + 0.6));
        List<Vec3> full = CovRaceSite.waypointPath(gates, LEAD);
        List<Vec3> edge = withOverride(gates, 1, inside, direction);
        List<Vec3> wide = withOverride(gates, 1, beside, direction);
        String problem = CovRaceSite.clientLap(course, gates, full, GateOpenings.NONE);
        if (problem == null) {
            problem = CovRaceSite.clientLap(course, gates, edge, GateOpenings.NONE);
        }
        if (problem != null) {
            return StepResult.fail("Runde durch den Bereich der Gate-Stange: " + problem);
        }
        List<LapTracker.Event> missed = CovRaceSite.fly(course, gates, wide, GateOpenings.NONE);
        if (CovRaceSite.of(missed, LapTracker.EventType.GATE).contains(1) || !CovRaceSite.of(missed, LapTracker.EventType.LAP).isEmpty()) {
            return StepResult.fail("Vorbeiflug " + (reach + 0.6) + " Blöcke neben der Gate-Stange zählt: " + missed);
        }
        StepResult wait = CovRaceSite.once(ctx, "Server", server -> {
            ServerPlayer p = CovRaceSite.player(server);
            String a = CovRaceSite.serverLap(server, p, course, edge, true).problem();
            String b = CovRaceSite.serverLap(server, p, course, wide, false).problem();
            return a == null && b == null ? null : "durch den Bereich: " + a + ", daneben: " + b;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log(String.format(Locale.ROOT, "Gate-Stange (Reichweite %.1f): Runde %.1f Blöcke neben der Stange zählt beim Client und am Server, "
                + "%.1f Blöcke daneben nicht (Server lehnt die Runde ab)", reach, reach - 0.4, reach + 0.6));
        return StepResult.pass();
    }

    private static StepResult freeformStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0 && Minecraft.getInstance().gui.screen() != null) {
            Minecraft.getInstance().gui.setScreen(null);
        }
        Site site = site();
        Minecraft mc = Minecraft.getInstance();
        int course = site.id("course4");
        CourseData data = ClientCourses.get(course);
        GateEntity freeform = CovRaceSite.clientEntity(site, "ff4");
        if (data == null || freeform == null || mc.level == null) {
            return StepResult.fail("Kurs mit Freiform-Gate fehlt beim Client");
        }
        GateGeometry open = freeform.geometry();
        if (!FreeformBlocks.integrity(mc.level, open).intact()) {
            return StepResult.fail("Freiform-Gate mit ignorierten Lücken meldet offene Kanten");
        }
        if (FreeformBlocks.integrity(mc.level, open.withScore(open.score().withOpen(false))).openEdges() == 0) {
            return StepResult.fail("ohne „Lücken ignorieren“ meldet der Rand keine offenen Kanten");
        }
        List<GateGeometry> gates = data.geometry();
        GateGeometry.Waypoint center = gates.get(1).waypoints().getFirst();
        Vec3 direction = new Vec3(center.dx(), center.dy(), center.dz());
        double floor = site.origin().getY();
        List<Vec3> inside = new ArrayList<>();
        for (double[] at : new double[][]{{9.2, 0.2}, {10.5, 2.5}, {11.8, 4.8}}) {
            inside.add(new Vec3(site.origin().getX() + at[0], floor + at[1], center.z()));
        }
        List<Vec3> outside = List.of(new Vec3(site.origin().getX() + 10.5, floor + 5.4, center.z()), new Vec3(site.origin().getX() + 7.5, floor + 2.0,
                center.z()), new Vec3(site.origin().getX() + 13.5, floor + 2.0, center.z()));
        List<String> problems = new ArrayList<>();
        for (Vec3 point : inside) {
            String problem = expect(course, gates, through(gates.getFirst(), point, direction), true, "innen bei " + fmt(point, site));
            if (problem != null) {
                problems.add(problem);
            }
        }
        for (Vec3 point : outside) {
            String problem = expect(course, gates, through(gates.getFirst(), point, direction), false, "außen bei " + fmt(point, site));
            if (problem != null) {
                problems.add(problem);
            }
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        StepResult wait = CovRaceSite.once(ctx, "Server", server -> {
            ServerPlayer p = CovRaceSite.player(server);
            String a = CovRaceSite.serverLap(server, p, course, through(gates.getFirst(), inside.get(1), direction), true).problem();
            String b = CovRaceSite.serverLap(server, p, course, through(gates.getFirst(), outside.getFirst(), direction), false).problem();
            return a == null && b == null ? null : "innen: " + a + ", außen: " + b;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("Torpfosten ohne Querbalken mit Schleichen gesetzt (ohne abgelehnt), Fläche 3 × 5: Durchflug unten links, Mitte und oben rechts zählt, "
                + "über der Fläche und neben den Pfosten nicht; Server nimmt innen an und lehnt außen ab; ohne „Lücken ignorieren“ offene Kanten");
        return StepResult.pass();
    }

    private static String fmt(Vec3 point, Site site) {
        return String.format(Locale.ROOT, "%.1f/%.1f", point.x - site.origin().getX(), point.y - site.origin().getY());
    }

    private static List<Vec3> loopPath(Site site) {
        double y = 2.0;
        double[][] points = {{30.5, -2.5}, {30.5, 3.5}, {30.5, 16.0}, {30.5, -4.0}, {24.5, -4.0}, {24.5, -6.0}, {30.5, -6.0}, {30.5, -2.5},
                {30.5, 3.5}};
        List<Vec3> path = new ArrayList<>();
        for (double[] p : points) {
            path.add(site.raw(p[0], y, p[1]));
        }
        return path;
    }

    private static StepResult loopStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0 && Minecraft.getInstance().gui.screen() != null) {
            Minecraft.getInstance().gui.setScreen(null);
        }
        Site site = site();
        Minecraft mc = Minecraft.getInstance();
        int course = site.id("course7");
        CourseData data = ClientCourses.get(course);
        GateEntity a = CovRaceSite.clientEntity(site, "a7");
        GateEntity b = CovRaceSite.clientEntity(site, "b7");
        if (data == null || a == null || b == null) {
            return StepResult.fail("Schleife fehlt beim Client");
        }
        switch (CovRaceSite.phase(ctx)) {
            case 0 -> {
                if (!"1 · 4←".equals(a.passText()) || !"2 · 3←".equals(b.passText())) {
                    return CovRaceSite.since(ctx) > 200 ? StepResult.fail("Nummern beim Client: A „" + a.passText() + "“, B „" + b.passText() + "“")
                            : StepResult.pending();
                }
                if (data.gates().size() != 4 || data.gates().get(0).reverse() || data.gates().get(1).reverse() || !data.gates().get(2).reverse()
                        || !data.gates().get(3).reverse() || data.firstPass(2) != 1 || data.firstPass(3) != 0) {
                    return StepResult.fail("Kursdaten der Schleife beim Client: " + data.gates());
                }
                CourseEditScreen.open(null, course);
                CovRaceSite.next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (!(mc.gui.screen() instanceof CourseEditScreen screen) || screen.course() == null) {
                    return CovRaceSite.since(ctx) > 100 ? StepResult.fail("Kurs-Fenster öffnet nicht") : StepResult.pending();
                }
                String again2 = Component.translatable("propwash.race.course_edit.row.repeat", "#", 2).getString();
                String again1 = Component.translatable("propwash.race.course_edit.row.repeat", "#", 1).getString();
                String back = Component.translatable("propwash.race.course_edit.row.reverse", "#").getString();
                String suffix2 = again2.substring(again2.indexOf('#') + 1) + back.substring(back.indexOf('#') + 1);
                String suffix1 = again1.substring(again1.indexOf('#') + 1) + back.substring(back.indexOf('#') + 1);
                List<String> rows = new ArrayList<>();
                for (int first = 0; first < 4; first++) {
                    screen.scrollTo(first);
                    for (LayoutAudit.Text text : LayoutProbe.capture(mc, screen).texts()) {
                        String value = text.value();
                        if ((value.endsWith(suffix2) || value.endsWith(suffix1)) && !rows.contains(value)) {
                            rows.add(value);
                        }
                    }
                }
                mc.gui.setScreen(null);
                if (rows.size() != 2 || !rows.get(0).endsWith(suffix2) || !rows.get(1).endsWith(suffix1)) {
                    return StepResult.fail("Kurs-Fenster zeigt die Durchgänge 3 und 4 nicht eindeutig in Reihenfolge: " + rows);
                }
                ctx.state().put("Fenster", "Zeile 3 „" + rows.get(0) + "“, Zeile 4 „" + rows.get(1) + "“");
                CovRaceSite.next(ctx, 2);
                return StepResult.pending();
            }
            default -> {
                List<GateGeometry> gates = ClientCourses.geometry(data);
                List<Vec3> path = loopPath(site);
                ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
                tracker.selectCourseAt(mc, site.raw(30.5, 2.0, -3.0));
                if (tracker.courseId() != course) {
                    return StepResult.fail("Renn-Tracker wählt die Schleife nicht: " + tracker.courseId());
                }
                List<String> hud = new ArrayList<>();
                long now = tracker.now() + 1_000L * CovRaceSite.MS;
                Vec3 at = path.getFirst();
                tracker.feed(now, at);
                String last = "";
                for (int i = 1; i < path.size(); i++) {
                    Vec3 to = path.get(i);
                    int steps = Math.max(1, (int) Math.ceil(at.distanceTo(to) / 0.25));
                    for (int s = 1; s <= steps; s++) {
                        now += 15 * CovRaceSite.MS;
                        tracker.feed(now, at.lerp(to, s / (double) steps));
                        RaceState race = RaceState.INSTANCE;
                        String label = race.nextGate() < 0 || race.gateCount() <= 0 ? "-/-" : (race.nextGate() + 1) + "/" + race.gateCount();
                        if (tracker.tracker().phase() != LapTracker.Phase.IDLE && !label.equals(last)) {
                            hud.add(label);
                            last = label;
                        }
                    }
                    at = to;
                }
                List<Long> laps = tracker.tracker().lapTimes();
                tracker.reset();
                if (hud.size() < 4 || !hud.subList(0, 4).equals(List.of("2/4", "3/4", "4/4", "1/4")) || laps.size() != 1) {
                    return StepResult.fail("HUD zählt die Schleife " + hud + ", Runden " + laps);
                }
                List<Vec3> wrong = List.of(site.raw(30.5, 2.0, -3.0), site.raw(30.5, 2.0, 16.0), site.raw(36.5, 2.0, 16.0), site.raw(36.5, 2.0, 4.0),
                        site.raw(30.5, 2.0, 4.0), site.raw(30.5, 2.0, 16.0));
                List<LapTracker.Event> events = CovRaceSite.fly(course, gates, wrong, GateOpenings.NONE);
                if (!CovRaceSite.of(events, LapTracker.EventType.GATE).equals(List.of(1)) || CovRaceSite.of(events, LapTracker.EventType.WRONG_WAY).size() != 1
                        || !CovRaceSite.of(events, LapTracker.EventType.MISSED).isEmpty()) {
                    return StepResult.fail("B vorwärts statt rückwärts: " + events);
                }
                StepResult wait = CovRaceSite.once(ctx, "Server", server -> CovRaceSite.serverLap(server, CovRaceSite.player(server), course, path, true)
                        .problem());
                if (wait != null) {
                    return wait;
                }
                ctx.log("Schleife A, B, B rückwärts, A rückwärts: Tafeln „1 · 4←“ und „2 · 3←“, " + ctx.state().get("Fenster") + ", HUD " + hud
                        + ", Runde " + laps.getFirst() / CovRaceSite.MS + " ms; B vorwärts statt rückwärts ist eine Meldung falsche Richtung; Server trägt "
                        + "die geflogene Zeit in die Bestenliste ein");
                return StepResult.pass();
            }
        }
    }

    private static int boxesAt(ServerLevel level, Vec3 point) {
        List<AABB> out = new ArrayList<>();
        GateCollisions.collect(level, new AABB(point, point).inflate(0.03), out);
        return out.size();
    }

    private static StepResult stackStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0 && Minecraft.getInstance().gui.screen() != null) {
            Minecraft.getInstance().gui.setScreen(null);
        }
        Site site = site();
        int course = site.id("course8");
        CourseData data = ClientCourses.get(course);
        if (data == null) {
            return StepResult.fail("Kurs mit der Stange auf dem Tauchwürfel fehlt beim Client");
        }
        List<GateGeometry> gates = data.geometry();
        List<Vec3> path = CovRaceSite.waypointPath(gates, LEAD);
        if (CovRaceSite.phase(ctx) == 0) {
            String problem = CovRaceSite.clientLap(course, gates, path, GateOpenings.NONE);
            if (problem != null) {
                return StepResult.fail("Runde um die Stange auf dem Tauchwürfel: " + problem);
            }
            StepResult wait = CovRaceSite.once(ctx, "Server", server -> {
                ServerPlayer player = CovRaceSite.player(server);
                ServerLevel level = player.level();
                GateEntity cube = CovRaceSite.require(level, site, "d8");
                GateEntity pole = CovRaceSite.require(level, site, "pole8");
                GateEntity flag = CovRaceSite.require(level, site, "flag8");
                GateEntity gate = CovRaceSite.require(level, site, "g8");
                GateEntity gatePole = CovRaceSite.require(level, site, "gp8");
                for (GateEntity carrier : List.of(cube, gate)) {
                    carrier.refreshStack();
                }
                if (GatePoleLinks.support(pole) != cube || GatePoleLinks.support(flag) != cube || GatePoleLinks.support(gatePole) != gate) {
                    return "Aufsatz ohne Träger";
                }
                if (!cube.carrying() || !gate.carrying()) {
                    return "Träger wissen nichts von ihren Aufsätzen";
                }
                for (GateEntity rider : List.of(pole, flag, gatePole)) {
                    GateGeometry g = rider.geometry();
                    Vec3 mid = new Vec3(g.x(), g.y() + g.height() * 0.5, g.z());
                    if (rider.colliders().length == 0 || boxesAt(level, mid) != 1) {
                        return rider.kind() + ": " + boxesAt(level, mid) + " Kollisionsboxen in der Mitte der Stange";
                    }
                }
                if (gate.colliders().length != 4) {
                    return "Gate mit Gate-Stange hat " + gate.colliders().length + " Kollisionsboxen";
                }
                String lap = CovRaceSite.serverLap(server, player, course, path, true).problem();
                if (lap != null) {
                    return lap;
                }
                flag.kill(level);
                if (cube.isRemoved() || pole.isRemoved()) {
                    return "Abbau der Flagge nimmt Würfel oder Stange mit";
                }
                cube.refreshStack();
                if (!cube.carrying()) {
                    return "Würfel trägt nach dem Abbau der Flagge die Stange nicht mehr";
                }
                pole.kill(level);
                gatePole.kill(level);
                cube.refreshStack();
                gate.refreshStack();
                if (cube.isRemoved() || gate.isRemoved() || cube.carrying() || gate.carrying() || gate.colliders().length != 4) {
                    return "nach dem Abbau aller Aufsätze: Würfel " + cube.isRemoved() + "/" + cube.carrying() + ", Gate " + gate.isRemoved() + "/"
                            + gate.carrying();
                }
                return null;
            });
            if (wait != null) {
                return wait;
            }
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        if (CovRaceSite.clientEntity(site, "flag8") != null || CovRaceSite.clientEntity(site, "pole8") != null
                || CovRaceSite.clientEntity(site, "gp8") != null) {
            return CovRaceSite.since(ctx) > 200 ? StepResult.fail("Aufsätze beim Client nicht abgebaut") : StepResult.pending();
        }
        if (CovRaceSite.clientEntity(site, "d8") == null || CovRaceSite.clientEntity(site, "g8") == null) {
            return StepResult.fail("Träger beim Client verschwunden");
        }
        ctx.log("Runde um die Slalomstange auf der Ecke des Tauchwürfels zählt beim Client und am Server; Slalomstange, Flagge und Gate-Stange je "
                + "eine Kollisionsbox in der Mitte, das Gate behält 4; Abbau der Flagge lässt Würfel und Stange stehen, Abbau aller Aufsätze lässt "
                + "Würfel und Gate stehen");
        return StepResult.pass();
    }

    private static final double[] NUDGE = {1.1875, 1.3125, -0.6875};

    private static String offset(GateEditModel model) {
        return String.format(Locale.ROOT, "%.4f/%.4f/%.4f", model.dx(), model.dy(), model.dz());
    }

    private static boolean at(GateEditModel model, double dx, double dy, double dz) {
        return Math.abs(model.dx() - dx) < EPS && Math.abs(model.dy() - dy) < EPS && Math.abs(model.dz() - dz) < EPS;
    }

    private static @Nullable String step(Minecraft mc, GateEditor editor, int notches, double expected) {
        if (!GateEditDriver.select(mc, editor, GateEditModel.Row.NUDGE_STEP)) {
            return "Zeile Schrittweite nicht wählbar";
        }
        GateEditDriver.scroll(mc, notches);
        return Math.abs(editor.model().nudgeStep() - expected) < EPS ? null : "Schrittweite " + editor.model().nudgeStep() + " statt " + expected;
    }

    private static StepResult nudgeStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0 && Minecraft.getInstance().gui.screen() != null) {
            Minecraft.getInstance().gui.setScreen(null);
        }
        Site site = site();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GateEntity gate = CovRaceSite.clientEntity(site, "n6");
        if (site == null || player == null || gate == null) {
            return StepResult.fail("kein Aufbau");
        }
        switch (CovRaceSite.phase(ctx)) {
            case 0 -> {
                Vec3 stand = site.raw(10.5, 0.0, 42.0);
                StepResult wait = CovRaceSite.once(ctx, "Werkzeug", server -> {
                    ServerPlayer p = CovRaceSite.player(server);
                    p.teleportTo(p.level(), stand.x, stand.y, stand.z, Set.of(), 0.0F, 0.0F, true);
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
                    GateEntity n6 = CovRaceSite.require(p.level(), site, "n6");
                    ctx.state().put("vorher", n6.position());
                    ctx.state().put("Boxen", List.of(n6.colliders()));
                    ctx.state().put("Geometrie", n6.geometry());
                    CourseRegistry registry = CourseRegistry.get();
                    ctx.state().put("Layout", registry.course(site.id("course6")).layout);
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                if (!GateMounting.holdsTool(player) || player.position().distanceTo(stand) > 0.6) {
                    return CovRaceSite.since(ctx) > 200 ? StepResult.fail("Kurs-Werkzeug oder Platz kommt nicht an") : StepResult.pending();
                }
                CovRaceSite.lookAt(player, gate.position().add(0.0, 2.0, 0.0));
                player.setYRot(0.0F);
                player.yRotO = 0.0F;
                RaceConfig.CONFIG.get().nudgeStep = GateEditModel.DEFAULT_NUDGE_STEP;
                GateEditor.start(gate);
                GateEditor editor = GateEditor.current();
                if (editor == null || editor.model().pages() != 3 || editor.model().pageKind(2) != GateEditModel.Page.POSITION) {
                    return StepResult.fail("Menü des Gates ohne Seite Position");
                }
                editor.showPage(2);
                String problem = step(mc, editor, 1, 1.0);
                if (problem != null) {
                    return StepResult.fail(problem);
                }
                GateEditDriver.press(mc, InputConstants.KEY_LEFT);
                GateEditDriver.press(mc, InputConstants.KEY_PAGEUP);
                GateEditDriver.press(mc, InputConstants.KEY_DOWN);
                if (!at(editor.model(), 1.0, 1.0, -1.0)) {
                    return StepResult.fail("Tasten mit 1 Block: " + offset(editor.model()));
                }
                problem = step(mc, editor, -1, 0.25);
                if (problem != null) {
                    return StepResult.fail(problem);
                }
                GateEditDriver.cursorHeld(mc, true);
                CovRaceSite.next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                GateEditor editor = GateEditor.current();
                GateEditPanel panel = GateEditDriver.panel(mc);
                if (editor == null || panel == null) {
                    return CovRaceSite.since(ctx) > 40 ? StepResult.fail("freie Maus öffnet das Panel nicht") : StepResult.pending();
                }
                for (GateEditModel.Row row : List.of(GateEditModel.Row.MOVE_X, GateEditModel.Row.MOVE_Y, GateEditModel.Row.MOVE_Z)) {
                    var plus = panel.plus(row);
                    if (plus == null || !GateEditDriver.click(mc, plus)) {
                        GateEditDriver.cursorHeld(mc, false);
                        return StepResult.fail("Knopf + bei " + row + " fehlt oder reagiert nicht");
                    }
                }
                GateEditDriver.cursorHeld(mc, false);
                if (!at(editor.model(), 1.25, 1.25, -0.75)) {
                    return StepResult.fail("Knöpfe mit 1/4: " + offset(editor.model()));
                }
                CovRaceSite.next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                GateEditor editor = GateEditor.current();
                if (editor == null) {
                    return StepResult.fail("Menü nach der freien Maus zu");
                }
                if (mc.gui.screen() != null) {
                    return CovRaceSite.since(ctx) > 40 ? StepResult.fail("freie Maus schließt nicht") : StepResult.pending();
                }
                String problem = step(mc, editor, -1, 1.0 / 16.0);
                if (problem != null) {
                    return StepResult.fail(problem);
                }
                GateEditDriver.press(mc, InputConstants.KEY_UP);
                GateEditDriver.press(mc, InputConstants.KEY_RIGHT);
                GateEditDriver.press(mc, InputConstants.KEY_PAGEUP);
                if (!at(editor.model(), NUDGE[0], NUDGE[1], NUDGE[2])) {
                    return StepResult.fail("Tasten mit 1/16: " + offset(editor.model()));
                }
                ctx.state().put("Menü", offset(editor.model()));
                GateEditDriver.press(mc, InputConstants.KEY_RETURN);
                CovRaceSite.next(ctx, 3);
                return StepResult.pending();
            }
            default -> {
                Vec3 before = (Vec3) ctx.state().get("vorher");
                Vec3 moved = gate.position().subtract(before);
                if (Math.abs(moved.x - NUDGE[0]) > EPS || Math.abs(moved.y - NUDGE[1]) > EPS || Math.abs(moved.z - NUDGE[2]) > EPS) {
                    return CovRaceSite.since(ctx) > 200 ? StepResult.fail("Gate beim Client nicht verschoben: " + moved) : StepResult.pending();
                }
                StepResult wait = CovRaceSite.once(ctx, "Server", server -> {
                    ServerLevel level = CovRaceSite.player(server).level();
                    GateEntity n6 = CovRaceSite.require(level, site, "n6");
                    Vec3 shift = n6.position().subtract(before);
                    if (Math.abs(shift.x - NUDGE[0]) > EPS || Math.abs(shift.y - NUDGE[1]) > EPS || Math.abs(shift.z - NUDGE[2]) > EPS) {
                        return "Gate am Server um " + shift + " verschoben";
                    }
                    @SuppressWarnings("unchecked")
                    List<AABB> old = (List<AABB>) ctx.state().get("Boxen");
                    AABB[] now = n6.colliders();
                    if (now.length != old.size()) {
                        return "Zahl der Kollisionsboxen ändert sich: " + old.size() + " → " + now.length;
                    }
                    for (int i = 0; i < now.length; i++) {
                        AABB want = old.get(i).move(shift);
                        if (Math.abs(now[i].minX - want.minX) > EPS || Math.abs(now[i].minY - want.minY) > EPS || Math.abs(now[i].minZ - want.minZ) > EPS
                                || Math.abs(now[i].maxX - want.maxX) > EPS || Math.abs(now[i].maxY - want.maxY) > EPS) {
                            return "Kollisionsbox " + i + " wandert nicht mit: " + now[i] + " statt " + want;
                        }
                    }
                    GateGeometry oldGeometry = (GateGeometry) ctx.state().get("Geometrie");
                    GateGeometry newGeometry = n6.geometry();
                    double y = newGeometry.centerY();
                    double leftOld = oldGeometry.x() - oldGeometry.innerHalfWidth() + 0.25;
                    double rightNew = newGeometry.x() + newGeometry.innerHalfWidth() - 0.25;
                    double z0 = newGeometry.z() - 1.0;
                    double z1 = newGeometry.z() + 1.0;
                    double oz0 = oldGeometry.z() - 1.0;
                    double oz1 = oldGeometry.z() + 1.0;
                    if (oldGeometry.crossing(leftOld, oldGeometry.centerY(), oz0, leftOld, oldGeometry.centerY(), oz1, true) < 0.0
                            || newGeometry.crossing(leftOld, y, z0, leftOld, y, z1, true) >= 0.0) {
                        return "Wertungsfläche bleibt an der alten Stelle";
                    }
                    if (newGeometry.crossing(rightNew, y, z0, rightNew, y, z1, true) < 0.0) {
                        return "Wertungsfläche an der neuen Stelle fehlt";
                    }
                    CourseRegistry registry = CourseRegistry.get();
                    CourseRegistry.Course course = registry.course(site.id("course6"));
                    CourseRegistry.GateRecord record = course.gates.getFirst();
                    if (Math.abs(record.x - n6.getX()) > EPS || Math.abs(record.y - n6.getY()) > EPS || Math.abs(record.z - n6.getZ()) > EPS) {
                        return "Kursdaten behalten die alte Lage: " + record.x + "/" + record.y + "/" + record.z;
                    }
                    ctx.state().put("Layout neu", course.layout);
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                ctx.log("Menü Seite Position: Schrittweite 1 Block mit ← Bild↑ ↓, 1/4 mit den Knöpfen + bei X, Y und Z (freie Maus, auf das Raster), "
                        + "1/16 mit ↑ → Bild↑: "
                        + ctx.state().get("Menü") + "; Gate am Server und beim Client genau so verschoben, Kollisionsboxen und Durchflugfläche wandern "
                        + "mit, Kursdaten mit neuer Lage (Layout " + ctx.state().get("Layout") + " → " + ctx.state().get("Layout neu") + ")");
                return StepResult.pass();
            }
        }
    }

    static ServerPlayer guest(MinecraftServer server, ServerLevel level, String name) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("propwash-cov-race-" + name).getBytes(StandardCharsets.UTF_8)), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    private static StepResult guestStep(SelfTest.StepContext ctx) {
        Site site = site();
        StepResult wait = CovRaceSite.once(ctx, "Gast", server -> {
            ServerLevel level = CovRaceSite.player(server).level();
            GateEntity gate = CovRaceSite.require(level, site, "n6");
            ServerPlayer guest = guest(server, level, GUEST);
            Vec3 near = gate.position().add(0.0, 0.0, -3.0);
            guest.snapTo(near.x, near.y, near.z, 0.0F, 0.0F);
            guest.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
            Vec3 before = gate.position();
            GateMounting.Result owner = GateMounting.edit(guest, gate, gate.geometry().heading(), 0.0F, 0.0F, gate.openingWidth(), gate.openingHeight(),
                    gate.variant(), gate.score(), 0.25, 0.0, 0.0);
            guest.setGameMode(GameType.ADVENTURE);
            GateMounting.Result build = GateMounting.edit(guest, gate, gate.geometry().heading(), 0.0F, 0.0F, gate.openingWidth(), gate.openingHeight(),
                    gate.variant(), gate.score(), 0.0, 1.0, 0.0);
            if (owner != GateMounting.Result.NOT_OWNER || build != GateMounting.Result.NOT_ALLOWED || gate.position().distanceTo(before) > EPS) {
                return "Gast ohne Recht: " + owner + " / im Abenteuermodus " + build + ", Gate um " + gate.position().subtract(before) + " verschoben";
            }
            DEBUG.set("Gast " + GUEST + " (kein Operator, nicht Besitzer des Kurses) beim Verschieben: " + owner + ", im Abenteuermodus: " + build);
            return null;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log(DEBUG.get() + "; Gate bleibt stehen");
        return StepResult.pass();
    }

    private static @Nullable String roundTrip(MinecraftServer server, ServerLevel level, GateEntity source) {
        ProblemReporter.Collector reporter = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(reporter, server.registryAccess());
        source.saveWithoutId(output);
        GateEntity copy = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
        if (copy == null) {
            return "Kopie nicht erzeugt";
        }
        copy.load(TagValueInput.create(reporter, server.registryAccess(), output.buildResult()));
        if (copy.position().distanceTo(source.position()) > EPS || !copy.geometry().equals(source.geometry()) || !copy.score().equals(source.score())
                || copy.courseId() != source.courseId() || copy.gateIndex() != source.gateIndex()) {
            return source.kind() + " nach dem Laden anders: " + copy.position() + " " + copy.score() + " Kurs " + copy.courseId();
        }
        AABB[] a = source.colliders();
        AABB[] b = copy.colliders();
        if (a.length != b.length) {
            return source.kind() + ": Kollisionsboxen nach dem Laden " + b.length + " statt " + a.length;
        }
        return reporter.isEmpty() ? null : "Meldungen: " + reporter.getReport();
    }

    private static StepResult reloadStep(SelfTest.StepContext ctx) {
        Site site = site();
        StepResult wait = CovRaceSite.once(ctx, "Neu laden", server -> {
            ServerLevel level = CovRaceSite.player(server).level();
            GateEntity n6 = CovRaceSite.require(level, site, "n6");
            GateEntity p3 = CovRaceSite.require(level, site, "p3");
            for (GateEntity gate : List.of(n6, p3)) {
                String problem = roundTrip(server, level, gate);
                if (problem != null) {
                    return problem;
                }
            }
            CourseRegistry.stop();
            CourseRegistry.start(server);
            CourseRegistry registry = CourseRegistry.get();
            if (registry == null) {
                return "Kursdaten nach dem Neuladen fehlen";
            }
            CourseRegistry.Course c3 = registry.course(site.id("course3"));
            CourseRegistry.Course c6 = registry.course(site.id("course6"));
            if (c3 == null || c6 == null) {
                return "Kurse nach dem Neuladen fehlen";
            }
            CourseRegistry.GateRecord pole = c3.gates.get(1);
            if (pole.kind() != CourseElement.GATE_POLE || pole.scoreArea().role() != ScoreArea.Role.PASS || !pole.uuid.equals(p3.getUUID().toString())) {
                return "Gate-Stange nach dem Neuladen nicht mehr im Kurs: " + pole.kind() + " " + pole.scoreArea();
            }
            CourseRegistry.GateRecord moved = c6.gates.getFirst();
            if (Math.abs(moved.x - n6.getX()) > EPS || Math.abs(moved.y - n6.getY()) > EPS || Math.abs(moved.z - n6.getZ()) > EPS) {
                return "verschobenes Gate nach dem Neuladen an alter Stelle: " + moved.x + "/" + moved.y + "/" + moved.z;
            }
            registry.reconcile(n6);
            registry.reconcile(p3);
            if (registry.courseOf(p3.getUUID()) != c3.id || registry.courseOf(n6.getUUID()) != c6.id) {
                return "Abgleich nach dem Neuladen löst die Gates aus den Kursen";
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("Entity-Daten von verschobenem Gate und Gate-Stange gespeichert und neu geladen (Lage, Geometrie, Wertung, Kurs gleich), Kursdatei "
                + "über CourseRegistry stop/start neu gelesen: Gate-Stange im Kurs mit Wertung „vorbei“, verschobenes Gate an der neuen Lage");
        return StepResult.pass();
    }

    private static void startFpv(MinecraftServer server, Vec3 at) {
        ServerPlayer player = CovRaceSite.player(server);
        ServerLevel level = player.level();
        Site site = site();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        Integer previous = site.ids().remove("drone");
        if (previous != null && level.getEntity(previous) instanceof QuadcopterEntity old) {
            old.discard();
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        drone.resetTo(at, new Quaternionf().rotationY((float) Math.PI));
        drone.setHomePoint(new HomePoint(at.x, at.y, at.z, HomePointCodecs.dimension(level), level.getGameTime(), HomePoint.Source.MANUAL));
        site.ids().put("drone", drone.getId());
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static @Nullable QuadcopterEntity sessionDrone() {
        PilotSession session = PilotSession.INSTANCE;
        return session.isActive() ? session.drone() : null;
    }

    private static void pressArm() {
        KeyMapping.click(InputConstants.getKey(InputKeys.ARM.saveString()));
    }

    private static boolean started() {
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        return tracker.lobbyMember() || tracker.countdownActive() || tracker.tracker().phase() == LapTracker.Phase.COUNTDOWN;
    }

    private static StepResult rescueStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0 && Minecraft.getInstance().gui.screen() != null) {
            Minecraft.getInstance().gui.setScreen(null);
        }
        Site site = site();
        PilotSession session = PilotSession.INSTANCE;
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        int course = site.id("course3");
        Vec3 home = site.raw(-9.5, 0.05, -4.5);
        int phase = CovRaceSite.phase(ctx);
        if (phase >= 1 && started()) {
            RaceConfig.CONFIG.get().countdown = (boolean) ctx.state().getOrDefault("Einstellung", false);
            return StepResult.fail("Countdown oder Aufstellung in Phase " + phase + " (GPS-Rescue)");
        }
        switch (phase) {
            case 0 -> {
                StepResult wait = CovRaceSite.once(ctx, "FPV", server -> {
                    startFpv(server, home);
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                QuadcopterEntity drone = sessionDrone();
                if (drone == null || !session.isFpv() || tracker.courseId() != course) {
                    return CovRaceSite.since(ctx) > 300 ? StepResult.fail("FPV am Start-Gate kommt nicht zustande, Kurs " + tracker.courseId())
                            : StepResult.pending();
                }
                ctx.state().put("Einstellung", RaceConfig.CONFIG.get().countdown);
                RaceConfig.CONFIG.get().countdown = false;
                session.clearTestInput();
                pressArm();
                CovRaceSite.next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                QuadcopterEntity drone = sessionDrone();
                if (drone == null || !drone.isArmed()) {
                    return CovRaceSite.since(ctx) > 100 ? StepResult.fail("Drohne lässt sich mit der Arm-Taste nicht scharf schalten") : StepResult.pending();
                }
                if (CovRaceSite.since(ctx) < 20) {
                    return StepResult.pending();
                }
                StepResult wait = CovRaceSite.once(ctx, "weg", server -> {
                    QuadcopterEntity d = DronePiloting.pilotedBy(CovRaceSite.player(server).getUUID()).orElse(null);
                    if (d == null) {
                        return "keine Drohne am Server";
                    }
                    d.resetTo(site.raw(-1.5, 7.0, -14.0), d.orientation());
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                CovRaceSite.next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                QuadcopterEntity drone = sessionDrone();
                if (drone == null || drone.position().distanceTo(home) < 8.0) {
                    return CovRaceSite.since(ctx) > 100 ? StepResult.fail("Drohne kommt nicht vom Homepoint weg") : StepResult.pending();
                }
                RescueController.INSTANCE.requestToggle();
                CovRaceSite.next(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                if (!RescueState.INSTANCE.active()) {
                    if (CovRaceSite.since(ctx) > 60) {
                        return StepResult.fail("GPS-Rescue startet nicht: Home " + RescueController.INSTANCE.home());
                    }
                    if (CovRaceSite.since(ctx) % 10 == 5) {
                        RescueController.INSTANCE.requestToggle();
                    }
                    return StepResult.pending();
                }
                ctx.state().put("Rescue ab", ctx.tick());
                CovRaceSite.next(ctx, 4);
                return StepResult.pending();
            }
            case 4 -> {
                QuadcopterEntity drone = sessionDrone();
                if (RescueState.INSTANCE.active()) {
                    return CovRaceSite.since(ctx) > 1500 ? StepResult.fail("GPS-Rescue endet nicht, Phase " + RescueController.INSTANCE.phase()
                            + ", Abstand " + (drone == null ? "-" : String.format(Locale.ROOT, "%.1f", drone.position().distanceTo(home))))
                            : StepResult.pending();
                }
                if (drone == null || drone.position().distanceTo(home) > 3.0) {
                    return StepResult.fail("GPS-Rescue endet nicht am Homepoint: " + (drone == null ? "-" : drone.position().subtract(home)));
                }
                ctx.state().put("Rescue", ctx.tick() - (int) ctx.state().get("Rescue ab"));
                ctx.state().put("scharf nach Landung", drone.isArmed());
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                CovRaceSite.next(ctx, 5);
                return StepResult.pending();
            }
            case 5 -> {
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                QuadcopterEntity drone = sessionDrone();
                if (drone != null && drone.isArmed()) {
                    return CovRaceSite.since(ctx) > 60 ? StepResult.fail("Drohne nach der Landung nicht zu entschärfen") : StepResult.pending();
                }
                if (CovRaceSite.since(ctx) < 40) {
                    return StepResult.pending();
                }
                CovRaceSite.next(ctx, 6);
                return StepResult.pending();
            }
            case 6 -> {
                session.setTestInput(StickPositions.IDLE, true, StabMode.ANGLE);
                QuadcopterEntity drone = sessionDrone();
                if (drone == null || !drone.isArmed()) {
                    return CovRaceSite.since(ctx) > 100 ? StepResult.fail("Drohne nach der Rescue-Landung nicht wieder scharf: Sperren "
                            + PilotSession.INSTANCE.armBlockers()) : StepResult.pending();
                }
                if (!ctx.state().containsKey("scharf ab")) {
                    ctx.state().put("scharf ab", ctx.tick());
                }
                if (ctx.tick() - (int) ctx.state().get("scharf ab") < 100) {
                    return StepResult.pending();
                }
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                CovRaceSite.next(ctx, 7);
                return StepResult.pending();
            }
            default -> {
                QuadcopterEntity drone = sessionDrone();
                if (drone != null && drone.isArmed()) {
                    return CovRaceSite.since(ctx) > 60 ? StepResult.fail("Drohne bleibt scharf") : StepResult.pending();
                }
                RaceConfig.CONFIG.get().countdown = (boolean) ctx.state().getOrDefault("Einstellung", false);
                StepResult wait = CovRaceSite.once(ctx, "FPV aus", server -> {
                    DronePiloting.pilotedBy(CovRaceSite.player(server).getUUID()).ifPresent(DronePiloting::release);
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                ctx.log("GPS-Rescue aus 12 Blöcken: " + ctx.state().get("Rescue") + " Ticks Heimflug bis zum Homepoint 5 Blöcke vor dem Start-Gate, "
                        + "scharf nach der Landung: " + ctx.state().get("scharf nach Landung") + "; während des Heimflugs, danach und nach erneutem "
                        + "Scharfschalten ohne „Start beim Scharfschalten“ weder Aufstellung noch Countdown");
                return StepResult.pass();
            }
        }
    }

    private static StepResult areaStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0 && Minecraft.getInstance().gui.screen() != null) {
            Minecraft.getInstance().gui.setScreen(null);
        }
        Site site = site();
        Minecraft mc = Minecraft.getInstance();
        GateEntity start = CovRaceSite.clientEntity(site, "s3");
        int course = site.id("course3");
        if (start == null || mc.player == null) {
            return StepResult.fail("kein Aufbau");
        }
        Vec3 home = site.raw(-9.5, 0.05, -4.5);
        switch (CovRaceSite.phase(ctx)) {
            case 0 -> {
                StepResult wait = CovRaceSite.once(ctx, "FPV", server -> {
                    startFpv(server, home);
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                if (sessionDrone() == null || !PilotSession.INSTANCE.isFpv() || ClientRaceTracker.INSTANCE.courseId() != course) {
                    return CovRaceSite.since(ctx) > 300 ? StepResult.fail("FPV am Start-Gate kommt nicht zustande") : StepResult.pending();
                }
                ctx.state().put("Bilder", ScoreAreaRenderer.framesDrawn());
                CovRaceSite.next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                ScoreAreaRenderer.Drawn opening = ScoreAreaRenderer.drawnFor(start.getId());
                if (ScoreAreaRenderer.framesDrawn() <= (int) ctx.state().get("Bilder") + 2 || opening == null
                        || opening.kind() != ScoreAreaRenderer.Kind.OPENING || opening.strength() < ScoreAreaRenderer.ACTIVE - EPS) {
                    return CovRaceSite.since(ctx) > 100 ? StepResult.fail("FPV: Durchflugfläche des Start-Gates nicht gezeichnet: " + opening + ", "
                            + ScoreAreaRenderer.lastDrawn()) : StepResult.pending();
                }
                ctx.state().put("FPV", ScoreAreaRenderer.framesDrawn() - (int) ctx.state().get("Bilder"));
                StepResult wait = CovRaceSite.once(ctx, "FPV aus", server -> {
                    ServerPlayer p = CovRaceSite.player(server);
                    DronePiloting.pilotedBy(p.getUUID()).ifPresent(DronePiloting::release);
                    p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    Vec3 stand = site.raw(-13.0, 0.0, -6.0);
                    p.teleportTo(p.level(), stand.x, stand.y, stand.z, Set.of(), -30.0F, 10.0F, true);
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                CovRaceSite.next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (PilotSession.INSTANCE.isActive() || mc.player.position().distanceTo(site.raw(-13.0, 0.0, -6.0)) > 0.6) {
                    return CovRaceSite.since(ctx) > 200 ? StepResult.fail("FPV endet nicht") : StepResult.pending();
                }
                ctx.state().put("Kamera", mc.options.getCameraType());
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                ClientRaceTracker.INSTANCE.selectCourseAt(mc, site.raw(-9.5, 1.0, -3.0));
                ctx.state().put("Bilder", ScoreAreaRenderer.framesDrawn());
                CovRaceSite.next(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                ScoreAreaRenderer.Drawn opening = ScoreAreaRenderer.drawnFor(start.getId());
                if (ScoreAreaRenderer.framesDrawn() <= (int) ctx.state().get("Bilder") + 2 || opening == null
                        || opening.kind() != ScoreAreaRenderer.Kind.OPENING) {
                    return CovRaceSite.since(ctx) > 100 ? StepResult.fail("dritte Person: Durchflugfläche nicht gezeichnet: " + opening + ", Kurs "
                            + ClientRaceTracker.INSTANCE.courseId() + ", Kamera " + mc.options.getCameraType()) : StepResult.pending();
                }
                ctx.state().put("dritte", ScoreAreaRenderer.framesDrawn() - (int) ctx.state().get("Bilder"));
                ClientRaceTracker.INSTANCE.selectCourseAt(mc, site.raw(-9.5, 1.0, -3.0).add(0.0, 0.0, -3000.0));
                CovRaceSite.next(ctx, 4);
                return StepResult.pending();
            }
            default -> {
                boolean openingShown = ScoreAreaRenderer.lastDrawn().stream().anyMatch(d -> d.kind() == ScoreAreaRenderer.Kind.OPENING);
                if (ClientRaceTracker.INSTANCE.courseId() != LapTracker.NO_COURSE || openingShown) {
                    if (CovRaceSite.since(ctx) > 60) {
                        mc.options.setCameraType((CameraType) ctx.state().get("Kamera"));
                        return StepResult.fail("ohne aktiven Kurs noch eine Durchflugfläche: Kurs " + ClientRaceTracker.INSTANCE.courseId() + ", "
                                + ScoreAreaRenderer.lastDrawn());
                    }
                    return StepResult.pending();
                }
                if (CovRaceSite.since(ctx) < 10) {
                    return StepResult.pending();
                }
                mc.options.setCameraType((CameraType) ctx.state().get("Kamera"));
                ctx.log("Durchflugfläche des Start-Gates im FPV (" + ctx.state().get("FPV") + " Bilder gezählt) und in der dritten Person ("
                        + ctx.state().get("dritte") + " Bilder) gezeichnet; ohne aktiven Kurs keine Durchflugfläche mehr");
                return StepResult.pass();
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Site site = site();
        GateEditor.stop();
        PilotSession.INSTANCE.clearTestInput();
        if (site == null) {
            return StepResult.pass();
        }
        StepResult wait = CovRaceSite.once(ctx, "Aufräumen", server -> {
            ServerPlayer player = CovRaceSite.player(server);
            ServerLevel level = player.level();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            Integer drone = site.ids().get("drone");
            if (drone != null && level.getEntity(drone) instanceof QuadcopterEntity d) {
                d.discard();
            }
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            return CovRaceSite.teardown(server, site);
        });
        if (wait != null) {
            return wait;
        }
        if (PilotSession.INSTANCE.isActive()) {
            return ctx.tick() > 400 ? StepResult.fail("FPV-Sitzung endet nicht") : StepResult.pending();
        }
        SITE.set(null);
        return StepResult.pass();
    }

    static final class ThirdPerson implements Screenshots.Scene {
        private final Vec3 feet;
        private final Vec3 target;
        private final Supplier<ItemStack> held;
        private final float turn;
        private @Nullable CameraType saved;

        ThirdPerson(Vec3 feet, Vec3 target, Supplier<ItemStack> held, float turn) {
            this.feet = feet;
            this.target = target;
            this.held = held;
            this.turn = turn;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Site site = site();
            Minecraft mc = Minecraft.getInstance();
            if (site == null || mc.player == null) {
                return StepResult.fail("kein Aufbau für das Bildschirmfoto");
            }
            Vec3 at = site.raw(this.feet.x, this.feet.y, this.feet.z);
            Supplier<ItemStack> held = this.held;
            StepResult wait = CovRaceSite.once(ctx, "Blickpunkt", server -> {
                ServerPlayer player = CovRaceSite.player(server);
                DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
                player.setItemInHand(InteractionHand.MAIN_HAND, held.get());
                return null;
            });
            if (wait != null) {
                return wait;
            }
            if (PilotSession.INSTANCE.isActive() || mc.player.position().distanceTo(at) > 0.6) {
                return ctx.tick() > 300 ? StepResult.fail("Spieler nicht am Blickpunkt") : StepResult.pending();
            }
            if (this.saved == null) {
                this.saved = mc.options.getCameraType();
            }
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            ClientRaceTracker.INSTANCE.selectCourseAt(mc, site.raw(-9.5, 1.0, -3.0));
            this.hold(ctx);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Site site = site();
            LocalPlayer player = Minecraft.getInstance().player;
            if (site != null && player != null) {
                CovRaceSite.lookAt(player, site.raw(this.target.x, this.target.y, this.target.z));
                float yaw = player.getYRot() + this.turn;
                player.setYRot(yaw);
                player.yRotO = yaw;
                player.setYHeadRot(yaw);
                player.yHeadRotO = yaw;
                player.setYBodyRot(yaw);
                player.yBodyRotO = yaw;
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.saved != null) {
                mc.options.setCameraType(this.saved);
                this.saved = null;
            }
            ctx.onServer(server -> CovRaceSite.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        }
    }

    static final class Fpv implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Site site = site();
            if (site == null) {
                return StepResult.fail("kein Aufbau für das Bildschirmfoto");
            }
            Vec3 home = site.raw(-9.5, 0.05, -4.5);
            StepResult wait = CovRaceSite.once(ctx, "FPV", server -> {
                startFpv(server, home);
                return null;
            });
            if (wait != null) {
                return wait;
            }
            if (sessionDrone() == null || !PilotSession.INSTANCE.isFpv() || ClientRaceTracker.INSTANCE.courseId() != site.id("course3")) {
                return ctx.tick() > 400 ? StepResult.fail("keine FPV-Sitzung am Start-Gate") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.clearTestInput();
            ctx.onServer(server -> DronePiloting.pilotedBy(CovRaceSite.player(server).getUUID()).ifPresent(DronePiloting::release));
        }
    }

    static Map<String, Integer> ids() {
        Site site = site();
        return site == null ? Map.of() : site.ids();
    }
}
