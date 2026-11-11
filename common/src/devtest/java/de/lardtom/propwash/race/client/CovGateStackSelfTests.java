package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.StackGateJoinCheck;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GateOpenings;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.client.CovRaceSite.Site;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class CovGateStackSelfTests {
    public static final String GROUP = "race: Abdeckung – Gate-Stapel ";
    public static final int MAX_TICKS = 5;
    private static final int OFFSET_X = -1600;
    private static final int OFFSET_Z = 1600;
    private static final int[] BOUNDS = {-6, 22, -8, 18, 14};
    private static final String BOTTOM = "legs_bottom";
    private static final String OTHER = "legs_other";
    private static final String TOP = "legs_top";
    private static final String COURSE = "course_legs";
    private static final String SCALED = "scaled_bottom";
    private static final String SCALED_TOP = "scaled_top";
    private static final String SCALED_OTHER = "scaled_other";
    private static final String SCALED_COURSE = "course_scaled";
    private static final double SCALED_WIDTH = 5.0;
    private static final double SCALED_HEIGHT = 4.0;
    private static final AtomicReference<Site> SITE = new AtomicReference<>();
    private static final AtomicLong CLICK_TIME = new AtomicLong(-1L);
    private static final AtomicLong SERVER_TICKS = new AtomicLong(-1L);

    private CovGateStackSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "– Platz mit Gate und Kurs", 900, CovGateStackSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "frisch gestapelt: Beine nach höchstens 5 Ticks ab dem Klick, Runde unten (Client und Server)", 600, CovGateStackSelfTests::strictFreshStep);
        SelfTest.clientStep(GROUP + "oberes Gate abbauen: unten nach höchstens 5 Ticks wieder Rahmen, Runde unten zählt nicht mehr", 400,
                CovGateStackSelfTests::removeStep);
        SelfTest.clientStep(GROUP + "frei skaliert: unten bis zum Boden, oben ab dem Balken, Runde unten (Client und Server)", 600,
                CovGateStackSelfTests::scaledStep);
        SelfTest.clientStep(GROUP + "– aufräumen", 400, CovGateStackSelfTests::cleanupStep);
    }

    private static double low(GateGeometry gate) {
        return gate.y() + gate.frame() * 0.5;
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult prepared = CovRaceSite.prepare(ctx, SITE, OFFSET_X, OFFSET_Z, BOUNDS, new Vec3(0.5, 0.0, -2.0));
        if (prepared.status() != StepResult.Status.PASS) {
            return prepared;
        }
        StepResult wait = CovRaceSite.once(ctx, "Aufbau", server -> {
            ServerPlayer player = CovRaceSite.player(server);
            Site site = SITE.get();
            int x = site.origin().getX();
            int y = site.origin().getY();
            int z = site.origin().getZ();
            GateEntity bottom = StackGateJoinCheck.place(player, GateSize.MEDIUM, x, y, z, 0.0F);
            GateEntity other = StackGateJoinCheck.place(player, GateSize.MEDIUM, x, y, z + 10, 0.0F);
            ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
            CourseToolItem.useOnGate(tool, player, bottom);
            CourseToolItem.useOnGate(tool, player, other);
            int course = CourseToolItem.courseOf(tool);
            CourseRegistry registry = CourseRegistry.get();
            CourseRegistry.Course data = registry == null ? null : registry.course(course);
            if (data == null || data.gates.size() != 2) {
                return "Kurs nicht angelegt";
            }
            registry.setLaps(data, 1);
            site.ids().put(BOTTOM, bottom.getId());
            site.ids().put(OTHER, other.getId());
            site.ids().put(COURSE, course);
            return null;
        });
        if (wait != null) {
            return wait;
        }
        Site site = SITE.get();
        GateEntity bottom = CovRaceSite.clientEntity(site, BOTTOM);
        CourseData data = ClientCourses.get(site.id(COURSE));
        if (bottom == null || CovRaceSite.clientEntity(site, OTHER) == null || data == null || data.gates().size() != 2) {
            return ctx.tick() > 800 ? StepResult.fail("Gates oder Kurs nicht beim Client") : StepResult.pending();
        }
        if (bottom.base() != GateStacking.Base.BAR) {
            return StepResult.fail("einzelnes Gate ohne unteren Balken: " + bottom.base());
        }
        ctx.log("Kurs " + site.id(COURSE) + " mit einzelnem Gate (unterer Balken) und zweitem Gate 10 Blöcke dahinter");
        return StepResult.pass();
    }

    private static List<Vec3> legsPath(Site site, double x, double side, GateGeometry bottom) {
        double l = low(bottom);
        double mid = bottom.y() + 2.0;
        double out = x + 6.5;
        return List.of(site.raw(x + side, l - site.origin().getY(), -3.0), site.raw(x + side, l - site.origin().getY(), 3.0),
                site.raw(x, mid - site.origin().getY(), 7.0), site.raw(x, mid - site.origin().getY(), 13.0),
                site.raw(out, mid - site.origin().getY(), 13.0), site.raw(out, l - site.origin().getY(), -3.0),
                site.raw(x + side, l - site.origin().getY(), -3.0), site.raw(x + side, l - site.origin().getY(), 3.0));
    }

    private static @Nullable GateEntity serverAbove(ServerLevel level, Site site) {
        GateEntity bottom = CovRaceSite.entity(level, site, BOTTOM);
        return bottom == null ? null : bottom.gateAbove();
    }

    static StepResult strictFreshStep(SelfTest.StepContext ctx) {
        return freshStep(ctx, true);
    }

    private static StepResult freshStep(SelfTest.StepContext ctx, boolean strict) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GateEntity bottom = CovRaceSite.clientEntity(site, BOTTOM);
        if (site == null || player == null || bottom == null) {
            return StepResult.fail("kein Aufbau");
        }
        int course = site.id(COURSE);
        Vec3 stand = site.raw(0.5, 0.0, -1.5);
        switch (CovRaceSite.phase(ctx)) {
            case 0 -> {
                StepResult wait = CovRaceSite.once(ctx, "Stellung", server -> {
                    ServerPlayer p = CovRaceSite.player(server);
                    p.teleportTo(p.level(), stand.x, stand.y, stand.z, Set.of(), 0.0F, -30.0F, true);
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.GATE_MEDIUM.get(), 16));
                    p.connection.send(new ClientboundSetHeldSlotPacket(p.getInventory().getSelectedSlot()));
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                if (player.position().distanceTo(stand) > 0.3 || !player.getMainHandItem().is(RaceModule.GATE_MEDIUM.get()) || mc.gui.screen() != null) {
                    return CovRaceSite.since(ctx) > 200 ? StepResult.fail("Spieler mit Gate in der Hand kommt nicht an") : StepResult.pending();
                }
                CovRaceSite.next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                GateGeometry g = bottom.geometry();
                CovRaceSite.lookAt(player, new Vec3(g.x(), g.y() + g.height() - g.frame() * 0.5, g.z()));
                if (!(mc.hitResult instanceof EntityHitResult hit) || hit.getEntity() != bottom) {
                    return CovRaceSite.since(ctx) > 40 ? StepResult.fail("Fadenkreuz trifft das Gate nicht: " + mc.hitResult) : StepResult.pending();
                }
                CLICK_TIME.set(-1L);
                SERVER_TICKS.set(-1L);
                ctx.onServer(server -> CLICK_TIME.set(CovRaceSite.player(server).level().getGameTime()));
                ctx.state().put("click", ctx.tick());
                KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
                CovRaceSite.next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (SERVER_TICKS.get() < 0L) {
                    ctx.onServer(server -> {
                        ServerLevel level = CovRaceSite.player(server).level();
                        GateEntity serverBottom = CovRaceSite.entity(level, site, BOTTOM);
                        GateEntity above = serverAbove(level, site);
                        if (serverBottom != null && above != null && serverBottom.base() == GateStacking.Base.LEGS && serverBottom.geometry().legs()
                                && CLICK_TIME.get() >= 0L && SERVER_TICKS.get() < 0L) {
                            SERVER_TICKS.set(level.getGameTime() - CLICK_TIME.get());
                            site.ids().put(TOP, above.getId());
                        }
                    });
                }
                CourseData data = ClientCourses.get(course);
                int click = (int) ctx.state().get("click");
                if (!ctx.state().containsKey("client") && data != null && ClientGateOpenings.of(course, data.geometry()).legs(0)) {
                    ctx.state().put("client", ctx.tick() - click);
                }
                if (!ctx.state().containsKey("client") || SERVER_TICKS.get() < 0L) {
                    return CovRaceSite.since(ctx) > 200 ? StepResult.fail("Beine nach dem Stapeln nicht gemeldet: Server " + SERVER_TICKS.get()
                            + ", Client " + ctx.state().get("client") + ", Basis " + bottom.base()) : StepResult.pending();
                }
                int client = (int) ctx.state().get("client");
                ctx.log("Beine nach dem Klick: Server nach " + SERVER_TICKS.get() + " Ticks (base=LEGS), Client nach " + client
                        + " Ticks (ClientGateOpenings)");
                if (strict && (client > MAX_TICKS || SERVER_TICKS.get() > MAX_TICKS)) {
                    return StepResult.fail("frisch gestapelt: Beine erst nach " + SERVER_TICKS.get() + " Server- und " + client
                            + " Client-Ticks ab dem Klick (höchstens " + MAX_TICKS + ")");
                }
                ctx.state().put("Ticks", "Server " + SERVER_TICKS.get() + ", Client " + client);
                CovRaceSite.next(ctx, 3);
                return StepResult.pending();
            }
            default -> {
                CourseData data = ClientCourses.get(course);
                GateEntity top = CovRaceSite.clientEntity(site, TOP);
                if (data == null || top == null) {
                    return CovRaceSite.since(ctx) > 100 ? StepResult.fail("Kurs oder oberes Gate fehlt beim Client") : StepResult.pending();
                }
                if (top.base() != GateStacking.Base.STACKED || top.size() != GateSize.MEDIUM) {
                    return StepResult.fail("gestapeltes Gate beim Client: " + top.base() + " " + top.size());
                }
                List<GateGeometry> gates = data.geometry();
                List<Vec3> path = legsPath(site, 0.5, 0.0, bottom.geometry());
                String problem = CovRaceSite.clientLap(course, gates, path, ClientGateOpenings.of(course, gates));
                if (problem != null) {
                    return StepResult.fail("Runde unten durch das frisch gestapelte Gate: " + problem);
                }
                List<LapTracker.Event> plain = CovRaceSite.fly(course, gates, path, GateOpenings.NONE);
                if (!CovRaceSite.of(plain, LapTracker.EventType.START).isEmpty()) {
                    return StepResult.fail("Durchflug unten zählt auch ohne Beine: " + plain);
                }
                StepResult wait = CovRaceSite.once(ctx, "Server-Runde", server -> CovRaceSite.serverLap(server, CovRaceSite.player(server), course, path,
                        true).problem());
                if (wait != null) {
                    return wait;
                }
                ctx.log("Runde in halber Rahmenhöhe unten durch das frisch gestapelte Gate: Client zählt, Server nimmt sie an (Beine nach "
                        + ctx.state().get("Ticks") + " Ticks ab dem Klick)");
                return StepResult.pass();
            }
        }
    }

    private static StepResult removeStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        GateEntity bottom = CovRaceSite.clientEntity(site, BOTTOM);
        if (site == null || bottom == null || mc.level == null) {
            return StepResult.fail("kein Aufbau");
        }
        int course = site.id(COURSE);
        if (CovRaceSite.phase(ctx) == 0) {
            if (!ctx.state().containsKey("request")) {
                CLICK_TIME.set(-1L);
                SERVER_TICKS.set(-1L);
                ctx.state().put("request", ctx.tick());
            }
            StepResult wait = CovRaceSite.once(ctx, "Abbau", server -> {
                ServerPlayer player = CovRaceSite.player(server);
                ServerLevel level = player.level();
                GateEntity top = CovRaceSite.entity(level, site, TOP);
                if (top == null) {
                    return "oberes Gate fehlt am Server";
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
                CLICK_TIME.set(level.getGameTime());
                if (!top.hurtServer(level, level.damageSources().playerAttack(player), 1.0F) || !top.isRemoved()) {
                    return "Abbauen mit dem Kurs-Werkzeug entfernt das obere Gate nicht";
                }
                return null;
            });
            if (wait != null) {
                return wait;
            }
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        if (SERVER_TICKS.get() < 0L) {
            ctx.onServer(server -> {
                ServerLevel level = CovRaceSite.player(server).level();
                GateEntity serverBottom = CovRaceSite.entity(level, site, BOTTOM);
                if (serverBottom != null && serverBottom.base() == GateStacking.Base.BAR && !serverBottom.geometry().legs() && SERVER_TICKS.get() < 0L) {
                    SERVER_TICKS.set(level.getGameTime() - CLICK_TIME.get());
                }
            });
        }
        if (!ctx.state().containsKey("gone") && mc.level.getEntity(site.ids().getOrDefault(TOP, -1)) == null) {
            ctx.state().put("gone", ctx.tick());
        }
        CourseData data = ClientCourses.get(course);
        boolean frame = bottom.base() == GateStacking.Base.BAR && !bottom.geometry().legs() && bottom.geometry().openingFloor() == bottom.geometry().frame()
                && data != null && !ClientGateOpenings.of(course, data.geometry()).legs(0);
        if (ctx.state().containsKey("gone") && frame && !ctx.state().containsKey("client")) {
            ctx.state().put("client", ctx.tick() - (int) ctx.state().get("gone"));
            ctx.state().put("total", ctx.tick() - (int) ctx.state().get("request"));
        }
        if (!ctx.state().containsKey("client") || SERVER_TICKS.get() < 0L) {
            return CovRaceSite.since(ctx) > 200 ? StepResult.fail("nach dem Abbau unten kein Rahmen: Client " + bottom.base() + ", Server-Ticks "
                    + SERVER_TICKS.get()) : StepResult.pending();
        }
        int client = (int) ctx.state().get("client");
        ctx.log("unten wieder Rahmen: Server nach " + SERVER_TICKS.get() + " Ticks, Client " + client + " Ticks nach dem Verschwinden des oberen Gates ("
                + ctx.state().get("total") + " Ticks ab dem Abbau)");
        if (client > MAX_TICKS || SERVER_TICKS.get() > MAX_TICKS) {
            return StepResult.fail("oberes Gate abgebaut: unten erst nach " + SERVER_TICKS.get() + " Server- und " + client
                    + " Client-Ticks wieder Rahmen (höchstens " + MAX_TICKS + ")");
        }
        List<GateGeometry> gates = data.geometry();
        List<LapTracker.Event> events = CovRaceSite.fly(course, gates, legsPath(site, 0.5, 0.0, bottom.geometry()), ClientGateOpenings.of(course, gates));
        if (!CovRaceSite.of(events, LapTracker.EventType.START).isEmpty()) {
            return StepResult.fail("nach dem Abbau zählt der Durchflug im unteren Balken noch: " + events);
        }
        return StepResult.pass();
    }

    private static StepResult scaledStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Aufbau");
        }
        StepResult wait = CovRaceSite.once(ctx, "skaliert", server -> {
            ServerPlayer player = CovRaceSite.player(server);
            ServerLevel level = player.level();
            int x = site.origin().getX() + 12;
            int y = site.origin().getY();
            int z = site.origin().getZ();
            Vec3 near = site.raw(12.5, 0.0, -2.0);
            player.teleportTo(level, near.x, near.y, near.z, Set.of(), 0.0F, 0.0F, true);
            GateEntity bottom = StackGateJoinCheck.place(player, GateSize.MEDIUM, x, y, z, 0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
            GateMounting.Result edited = GateMounting.edit(player, bottom, bottom.geometry().heading(), 0.0F, 0.0F, SCALED_WIDTH, SCALED_HEIGHT);
            double outerWidth = GateSize.MEDIUM.outer(SCALED_WIDTH);
            if (edited != GateMounting.Result.EDITED || Math.abs(bottom.geometry().width() - outerWidth) > 1.0E-4
                    || Math.abs(bottom.geometry().height() - GateSize.MEDIUM.outer(SCALED_HEIGHT)) > 1.0E-4) {
                return "Breite und Höhe im Gate-Menü nicht übernommen: " + edited + " " + bottom.geometry();
            }
            GateEntity top = StackGateJoinCheck.stacked(player, bottom, GateSize.MEDIUM, 0.0F);
            GateEntity other = StackGateJoinCheck.place(player, GateSize.MEDIUM, x, y, z + 10, 0.0F);
            ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
            CourseToolItem.useOnGate(tool, player, bottom);
            CourseToolItem.useOnGate(tool, player, other);
            int course = CourseToolItem.courseOf(tool);
            CourseRegistry registry = CourseRegistry.get();
            CourseRegistry.Course data = registry == null ? null : registry.course(course);
            if (data == null || data.gates.size() != 2) {
                return "Kurs mit dem skalierten Gate nicht angelegt";
            }
            registry.setLaps(data, 1);
            site.ids().put(SCALED, bottom.getId());
            site.ids().put(SCALED_TOP, top.getId());
            site.ids().put(SCALED_OTHER, other.getId());
            site.ids().put(SCALED_COURSE, course);
            if (bottom.base() != GateStacking.Base.LEGS || !bottom.geometry().legs() || bottom.geometry().openingFloor() != 0.0) {
                return "Server: skaliertes Gate unten " + bottom.base() + ", Öffnung ab " + bottom.geometry().openingFloor();
            }
            if (top.base() != GateStacking.Base.STACKED || top.geometry().openingFloor() != top.geometry().frame()
                    || Math.abs(top.getY() - GateStacking.seat(bottom.geometry())) > 1.0E-4) {
                return "Server: oberes Gate " + top.base() + " bei " + top.getY() + ", Öffnung ab " + top.geometry().openingFloor();
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        int course = site.id(SCALED_COURSE);
        GateEntity bottom = CovRaceSite.clientEntity(site, SCALED);
        GateEntity top = CovRaceSite.clientEntity(site, SCALED_TOP);
        CourseData data = ClientCourses.get(course);
        if (bottom == null || top == null || data == null || data.gates().size() != 2 || bottom.base() != GateStacking.Base.LEGS
                || !ClientGateOpenings.of(course, data.geometry()).legs(0)) {
            return ctx.tick() > 300 ? StepResult.fail("skaliertes Gate beim Client ohne Beine: " + (bottom == null ? "-" : bottom.base()))
                    : StepResult.pending();
        }
        GateGeometry g = bottom.geometry();
        if (Math.abs(g.width() - GateSize.MEDIUM.outer(SCALED_WIDTH)) > 1.0E-4 || g.openingFloor() != 0.0
                || top.geometry().openingFloor() != top.geometry().frame()) {
            return StepResult.fail("Client: skaliertes Gate " + g + ", oben Öffnung ab " + top.geometry().openingFloor());
        }
        if (top.geometry().insideOpening(0.0, top.geometry().frame() * 0.5) || !g.insideOpening(g.innerHalfWidth() - 0.05, 0.05)) {
            return StepResult.fail("Öffnungen: unten nicht bis zum Boden oder oben schon im Balken");
        }
        List<GateGeometry> gates = data.geometry();
        double side = g.innerHalfWidth() - 0.3;
        List<Vec3> path = legsPath(site, 12.5, side, g);
        String problem = CovRaceSite.clientLap(course, gates, path, ClientGateOpenings.of(course, gates));
        if (problem != null) {
            return StepResult.fail("Runde unten am Rand des skalierten Gates: " + problem);
        }
        wait = CovRaceSite.once(ctx, "Server-Runde", server -> CovRaceSite.serverLap(server, CovRaceSite.player(server), course, path, true).problem());
        if (wait != null) {
            return wait;
        }
        ctx.log("Gate mit Öffnung " + SCALED_WIDTH + " × " + SCALED_HEIGHT + " (Gate-Menü) gestapelt: unten Öffnung ab dem Boden, oben ab dem Balken; "
                + "Runde " + String.format(java.util.Locale.ROOT, "%.2f", side) + " Blöcke neben der Mitte knapp über dem Boden zählt, Server nimmt sie an");
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        StepResult wait = CovRaceSite.once(ctx, "Aufräumen", server -> CovRaceSite.teardown(server, site));
        if (wait != null) {
            return wait;
        }
        SITE.set(null);
        return StepResult.pass();
    }
}
