package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateItem;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GateOpenings;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class GateStackRowSelfTests {
    private static final String GROUP = "race-topo: Stapel nebeneinander – ";
    private static final String SHOTS = "bildschirmfotos: Stapel nebeneinander – ";
    private static final AtomicReference<CovRaceSite.Site> SITE = new AtomicReference<>();
    private static final AtomicReference<GatePlacement.Preference> SAVED = new AtomicReference<>();
    private static final AtomicInteger RULE = new AtomicInteger(Integer.MIN_VALUE);
    private static final int[] BOUNDS = {-8, 44, -9, 10, 18};
    private static final Vec3 STAND = new Vec3(18.0, 0.0, -7.0);
    private static final double Z = 0.5;
    private static final double EPS = 1.0E-4;
    private static final double LEAD = 2.5;
    private static final int WAIT = 300;
    private static final String A = "a";
    private static final String B = "b";
    private static final String A2 = "a2";
    private static final String B2 = "b2";
    private static final String ROW_W = "row_w";
    private static final String ROW_M = "row_m";
    private static final String ROW_E = "row_e";
    private static final String STEP = "step_";
    private static final String CORNER_S = "corner_s";
    private static final String CORNER_E = "corner_e";
    private static final String COURSE = "course_grid";
    private static final int[] HEIGHTS = {4, 3, 2, 1};
    private static final double[] STEP_X = {23.0, 25.5, 28.0, 30.5};
    private static final int[] STEP_BLOCK = {23, 25, 28, 30};
    private static final String TOO_HIGH = "propwash.race.stack.preview_too_high";

    private GateStackRowSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Aufbau: freie Kurs-Platzierung aus, Spieler mit Gate-Items", 900, GateStackRowSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "2×2: zweiter Stapel direkt neben dem ersten – Vorschau frei, Rechtsklick setzt, Pfosten geteilt", 600,
                GateStackRowSelfTests::gridStep);
        SelfTest.clientStep(GROUP + "3×1: Mitte zuerst, dann Osten und Westen – beide Pfosten der Mitte geteilt, Block im Weg bleibt kein Platz", 300,
                GateStackRowSelfTests::rowStep);
        SelfTest.clientStep(GROUP + "Stapelhöhen 4, 3, 2 und 1 nebeneinander, von Osten nach Westen gebaut", 300, GateStackRowSelfTests::stepsStep);
        SelfTest.clientStep(GROUP + "Ecke: zwei quer stehende Stapel teilen sich den Eckpfosten", 300, GateStackRowSelfTests::cornerStep);
        SelfTest.clientStep(GROUP + "fünftes Gate: Vorschau nennt „höchstens 4 Gates übereinander“, Rechtsklick setzt nichts", 600,
                GateStackRowSelfTests::limitStep);
        SelfTest.clientStep(GROUP + "Kurs durch das 2×2: vier Klicks mit dem Kurs-Werkzeug treffen je ihr Gate, Runde beim Client und am Server", 900,
                GateStackRowSelfTests::courseStep);
        if (Screenshots.enabled()) {
            Supplier<ItemStack> tool = () -> new ItemStack(RaceModule.COURSE_TOOL.get());
            Screenshots.step(SHOTS + "2×2 ohne freie Platzierung", "race_topo_stack_2x2", 40,
                    new View(new Vec3(2.25, 2.4, 9.0), new Vec3(2.25, 3.6, Z), tool));
            Screenshots.step(SHOTS + "3×1 ohne freie Platzierung", "race_topo_row_3x1", 40,
                    new View(new Vec3(12.5, 1.6, 9.0), new Vec3(12.5, 2.0, Z), tool));
            Screenshots.step(SHOTS + "Stapelhöhen 4 bis 1 nebeneinander", "race_topo_stack_heights", 40,
                    new View(new Vec3(26.75, 4.0, 10.0), new Vec3(26.75, 4.6, Z), tool));
            Screenshots.step(SHOTS + "Ecke aus zwei Stapeln", "race_topo_stack_corner", 40,
                    new View(new Vec3(42.0, 3.5, -5.0), new Vec3(38.25, 3.5, 0.5), tool));
            Screenshots.step(SHOTS + "fünftes Gate abgelehnt, Hinweis höchstens 4", "race_topo_stack_limit", 40, new LimitShot());
            SelfTest.switchStep(SHOTS + "Sprache Deutsch", SHOTS + "Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
            Screenshots.step(SHOTS + "fünftes Gate abgelehnt, Hinweis höchstens 4 deutsch", "race_topo_stack_limit_de", 40, new LimitShot());
            SelfTest.clientStep(SHOTS + "Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        }
        SelfTest.clientStep(GROUP + "Aufräumen", 300, GateStackRowSelfTests::cleanupStep);
    }

    private static @Nullable GateEntity client(String key) {
        return CovRaceSite.clientEntity(SITE.get(), key);
    }

    private static String text(GateGeometry gate) {
        return String.format(Locale.ROOT, "%.2f/%.2f/%.2f %.0f° Pfosten-Bits %d", gate.x(), gate.y(), gate.z(), gate.yawDegrees(), gate.joints());
    }

    private static int sideBits(int joints) {
        return Integer.bitCount(joints & (GateGeometry.JOINT_LEFT | GateGeometry.JOINT_RIGHT));
    }

    private static void hold(ServerPlayer player, Item item) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 64));
        player.connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
    }

    private static InteractionResult floor(ServerPlayer player, Item item, BlockPos block, float yaw) {
        CovRaceSite.face(player, yaw);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 64));
        BlockHitResult hit = new BlockHitResult(new Vec3(block.getX() + 0.5, block.getY() + 1.0, block.getZ() + 0.5), Direction.UP, block, false);
        return item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static InteractionResult stack(ServerPlayer player, Item item, GateEntity target) {
        CovRaceSite.face(player, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 64));
        GateGeometry g = target.geometry();
        Vec3 bar = new Vec3(g.x(), g.y() + g.height() - g.frame() * 0.5, g.z());
        return target.interact(player, InteractionHand.MAIN_HAND, bar.subtract(target.position()));
    }

    private static @Nullable GateEntity at(ServerLevel level, Vec3 at) {
        List<GateEntity> gates = level.getEntitiesOfClass(GateEntity.class, new AABB(at, at).inflate(0.3),
                gate -> !gate.isRemoved() && gate.kind() == CourseElement.GATE && gate.position().distanceTo(at) < 1.0E-3);
        return gates.size() == 1 ? gates.getFirst() : null;
    }

    private static @Nullable String put(ServerLevel level, CovRaceSite.Site site, String key, Vec3 expected) {
        GateEntity gate = at(level, expected);
        if (gate == null) {
            return key + ": kein Gate bei " + expected;
        }
        site.ids().put(key, gate.getId());
        return null;
    }

    private static @Nullable String onFloor(ServerPlayer player, CovRaceSite.Site site, String key, Item item, int block, int blockZ, float yaw,
                                            double x, double z) {
        InteractionResult result = floor(player, item, site.block(block, -1, blockZ), yaw);
        if (!result.consumesAction()) {
            return key + ": am Boden nicht gesetzt (" + result + ")";
        }
        return put(player.level(), site, key, site.raw(x, 0.0, z));
    }

    private static @Nullable String onTop(ServerPlayer player, CovRaceSite.Site site, String below, String key, Item item) {
        ServerLevel level = player.level();
        GateEntity lower = CovRaceSite.require(level, site, below);
        GateGeometry top = lower.stack().getLast().geometry();
        InteractionResult result = stack(player, item, lower);
        if (!result.consumesAction()) {
            return key + ": auf " + below + " nicht gestapelt (" + result + ")";
        }
        return put(level, site, key, new Vec3(top.x(), GateStacking.seat(top), top.z()));
    }

    private static @Nullable String clean(List<GateEntity> gates, int shared) {
        for (GateEntity gate : gates) {
            gate.refreshStack();
        }
        int bits = 0;
        for (GateEntity gate : gates) {
            bits += sideBits(gate.joints());
        }
        if (bits != shared) {
            StringBuilder out = new StringBuilder(bits + " geteilte Pfosten statt " + shared + ":");
            for (GateEntity gate : gates) {
                out.append(' ').append(text(gate.geometry()));
            }
            return out.toString();
        }
        for (int i = 0; i < gates.size(); i++) {
            for (int j = i + 1; j < gates.size(); j++) {
                for (AABB p : gates.get(i).colliders()) {
                    for (AABB q : gates.get(j).colliders()) {
                        if (p.deflate(0.01).intersects(q)) {
                            return "Teile überlappen: " + text(gates.get(i).geometry()) + " / " + text(gates.get(j).geometry());
                        }
                    }
                }
            }
        }
        return null;
    }

    private static List<GateEntity> all(ServerLevel level, CovRaceSite.Site site, String... keys) {
        List<GateEntity> out = new ArrayList<>();
        for (String key : keys) {
            out.add(CovRaceSite.require(level, site, key));
        }
        return out;
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult wait = CovRaceSite.prepare(ctx, SITE, -200, 290, BOUNDS, STAND);
        if (wait.status() != StepResult.Status.PASS) {
            return wait;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        if (SAVED.get() == null) {
            SAVED.set(GateMountKeys.preference());
            GateMountKeys.setPreference(GatePlacement.Preference.DEFAULT);
        }
        wait = CovRaceSite.once(ctx, "Regel", server -> {
            ServerPlayer player = CovRaceSite.player(server);
            RULE.set(WorldRules.values(player.level()).get(WorldRuleSpecs.FREE_COURSE_PLACEMENT));
            WorldRules.set(server, WorldRuleSpecs.FREE_COURSE_PLACEMENT, 0);
            GateMounting.setPreference(player, GatePlacement.Preference.DEFAULT);
            return null;
        });
        if (wait != null) {
            return wait;
        }
        if (ClientWorldRules.INSTANCE.on(WorldRuleSpecs.FREE_COURSE_PLACEMENT)) {
            return ctx.tick() > 800 ? StepResult.fail("freie Kurs-Platzierung bleibt beim Client an") : StepResult.pending();
        }
        ctx.log("freie Kurs-Platzierung aus (vorher " + RULE.get() + "), Vorliebe stehend ohne Drehung");
        return StepResult.pass();
    }

    private static StepResult gridStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        CovRaceSite.Site site = SITE.get();
        if (player == null || site == null) {
            return StepResult.fail("Aufbau fehlt");
        }
        int phase = CovRaceSite.phase(ctx);
        if (phase == 0) {
            StepResult wait = CovRaceSite.once(ctx, "2×2 unten", server -> {
                ServerPlayer p = CovRaceSite.player(server);
                Item medium = RaceModule.GATE_MEDIUM.get();
                String problem = onFloor(p, site, A, medium, 0, 0, 0.0F, 0.5, Z);
                problem = problem != null ? problem : onFloor(p, site, B, medium, 4, 0, 0.0F, 4.0, Z);
                problem = problem != null ? problem : onTop(p, site, A, A2, medium);
                if (problem != null) {
                    return problem;
                }
                hold(p, medium);
                Vec3 feet = site.raw(4.0, 0.0, Z - 2.5);
                p.getAbilities().flying = false;
                p.teleportTo(p.level(), feet.x, feet.y, feet.z, Set.of(), 0.0F, 20.0F, true);
                p.resetFallDistance();
                return null;
            });
            if (wait != null) {
                return wait;
            }
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        GateEntity a = client(A);
        GateEntity b = client(B);
        GateEntity a2 = client(A2);
        if (a == null || b == null || a2 == null || !player.getMainHandItem().is(RaceModule.GATE_MEDIUM.get())) {
            return CovRaceSite.since(ctx) > WAIT ? StepResult.fail("untere Gates, oberes westliches oder Gate-Item fehlen beim Client") : StepResult.pending();
        }
        GateGeometry lower = b.geometry();
        if (phase == 1) {
            CovRaceSite.lookAt(player, new Vec3(lower.x(), lower.y() + lower.height() - lower.frame() * 0.5, lower.z()));
            if (!(mc.hitResult instanceof EntityHitResult hit) || hit.getEntity() != b) {
                return CovRaceSite.since(ctx) > 80 ? StepResult.fail("Fadenkreuz trifft das östliche Gate nicht: " + mc.hitResult) : StepResult.pending();
            }
            GatePreview.Target target = GatePreview.target(mc);
            if (target == null) {
                return CovRaceSite.since(ctx) > 120 ? StepResult.fail("keine Vorschau beim Blick auf das östliche Gate") : StepResult.pending();
            }
            GateGeometry ghost = target.geometry();
            if (!target.free()) {
                return StepResult.fail("Vorschau des zweiten Stapels direkt neben dem ersten: „" + GatePreview.status(target, GateMountKeys.preference())
                        .getString() + "“ (" + text(ghost) + ")");
            }
            if (Math.abs(ghost.x() - lower.x()) > EPS || Math.abs(ghost.y() - GateStacking.seat(lower)) > EPS || sideBits(ghost.joints()) != 1) {
                return StepResult.fail("Vorschau sitzt nicht auf dem geteilten Pfosten: " + text(ghost));
            }
            ctx.log("Vorschau frei: " + text(ghost) + ", Hinweis „" + GatePreview.status(target, GateMountKeys.preference()).getString() + "“");
            KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
            CovRaceSite.next(ctx, 2);
            return StepResult.pending();
        }
        List<GateEntity> column = b.stack();
        if (column.size() != 2) {
            return CovRaceSite.since(ctx) > WAIT ? StepResult.fail("Rechtsklick setzt kein Gate auf das östliche (Stapel " + column.size() + ")")
                    : StepResult.pending();
        }
        GateEntity b2 = column.getLast();
        if (sideBits(b2.joints()) != 1 || sideBits(a2.joints()) != 0 || b2.base() != GateStacking.Base.STACKED || b.base() != GateStacking.Base.LEGS) {
            return CovRaceSite.since(ctx) > WAIT ? StepResult.fail("Pfosten beim Client: oben Ost " + text(b2.geometry()) + " " + b2.base() + ", oben West "
                    + text(a2.geometry())) : StepResult.pending();
        }
        site.ids().put(B2, b2.getId());
        StepResult wait = CovRaceSite.once(ctx, "2×2 am Server", server -> {
            ServerLevel level = CovRaceSite.player(server).level();
            return clean(all(level, site, A, B, A2, B2), 2);
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("2×2: unten " + text(a.geometry()) + " | " + text(b.geometry()) + ", oben " + text(a2.geometry()) + " | " + text(b2.geometry())
                + ", zwei geteilte Pfosten, keine Teile doppelt (Client und Server)");
        return StepResult.pass();
    }

    private static StepResult rowStep(SelfTest.StepContext ctx) {
        CovRaceSite.Site site = SITE.get();
        StepResult wait = CovRaceSite.once(ctx, "3×1", server -> {
            ServerPlayer p = CovRaceSite.player(server);
            ServerLevel level = p.level();
            Item medium = RaceModule.GATE_MEDIUM.get();
            String problem = onFloor(p, site, ROW_M, medium, 12, 0, 0.0F, 12.5, Z);
            problem = problem != null ? problem : onFloor(p, site, ROW_E, medium, 16, 0, 0.0F, 16.0, Z);
            problem = problem != null ? problem : onFloor(p, site, ROW_W, medium, 8, 0, 0.0F, 9.0, Z);
            problem = problem != null ? problem : clean(all(level, site, ROW_W, ROW_M, ROW_E), 2);
            if (problem != null) {
                return problem;
            }
            GateEntity middle = CovRaceSite.require(level, site, ROW_M);
            if (sideBits(middle.joints()) != 1 || sideBits(CovRaceSite.require(level, site, ROW_W).joints()) != 0) {
                return "Pfosten der Reihe: West " + text(CovRaceSite.require(level, site, ROW_W).geometry()) + ", Mitte " + text(middle.geometry());
            }
            BlockPos stone = site.block(12, 5, 0);
            CovRaceSite.set(level, site, stone, Blocks.STONE.defaultBlockState());
            try {
                CovRaceSite.face(p, 0.0F);
                GateStacking.StackRefusal blocked = ((GateItem) medium).stackFor(middle, p).refusal();
                InteractionResult result = stack(p, medium, middle);
                if (blocked != GateStacking.StackRefusal.BLOCKED || result.consumesAction() || middle.stack().size() != 1) {
                    return "Block im Weg: " + blocked + ", Klick " + result + ", Stapel " + middle.stack().size();
                }
            } finally {
                level.setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());
            }
            GateStacking.StackRefusal free = ((GateItem) medium).stackFor(middle, p).refusal();
            return free == GateStacking.StackRefusal.NONE ? null : "ohne Block: " + free;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("3×1: Mitte bei 12,5, Osten rastet bei 16,0 ein, Westen bei 9,0; zwei geteilte Pfosten; Block im Weg des Stapels: BLOCKED");
        return StepResult.pass();
    }

    private static StepResult stepsStep(SelfTest.StepContext ctx) {
        CovRaceSite.Site site = SITE.get();
        StepResult wait = CovRaceSite.once(ctx, "Stapelhöhen", server -> {
            ServerPlayer p = CovRaceSite.player(server);
            ServerLevel level = p.level();
            Item small = RaceModule.GATE_SMALL.get();
            List<String> keys = new ArrayList<>();
            for (int column = HEIGHTS.length - 1; column >= 0; column--) {
                String key = STEP + column + "_0";
                String problem = onFloor(p, site, key, small, STEP_BLOCK[column], 0, 0.0F, STEP_X[column], Z);
                keys.add(key);
                for (int tier = 1; problem == null && tier < HEIGHTS[column]; tier++) {
                    String upper = STEP + column + "_" + tier;
                    problem = onTop(p, site, STEP + column + "_0", upper, small);
                    keys.add(upper);
                }
                if (problem != null) {
                    return problem;
                }
            }
            return clean(all(level, site, keys.toArray(new String[0])), 3 + 2 + 1);
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("kleine Gates in Stapeln der Höhe 4, 3, 2 und 1 von Osten nach Westen gebaut: 6 geteilte Pfosten, keine Teile doppelt");
        return StepResult.pass();
    }

    private static StepResult cornerStep(SelfTest.StepContext ctx) {
        CovRaceSite.Site site = SITE.get();
        StepResult wait = CovRaceSite.once(ctx, "Ecke", server -> {
            ServerPlayer p = CovRaceSite.player(server);
            Item medium = RaceModule.GATE_MEDIUM.get();
            String problem = onFloor(p, site, CORNER_S, medium, 36, 0, 0.0F, 36.5, Z);
            problem = problem != null ? problem : onFloor(p, site, CORNER_E, medium, 38, 2, 90.0F, 38.25, 2.25);
            problem = problem != null ? problem : onTop(p, site, CORNER_S, CORNER_S + "2", medium);
            problem = problem != null ? problem : onTop(p, site, CORNER_E, CORNER_E + "2", medium);
            return problem != null ? problem : clean(all(p.level(), site, CORNER_S, CORNER_E, CORNER_S + "2", CORNER_E + "2"), 2);
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("Ecke: Süd und Ost je zwei hoch, unten und oben je ein geteilter Eckpfosten");
        return StepResult.pass();
    }

    private static @Nullable String limitAim(Minecraft mc, LocalPlayer player) {
        GateEntity third = client(STEP + "0_2");
        if (third == null) {
            return "Stapel aus vier Gates fehlt beim Client";
        }
        GateGeometry g = third.geometry();
        CovRaceSite.lookAt(player, new Vec3(g.x(), g.y() + g.height() - g.frame() * 0.5, g.z()));
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof GateEntity seen) || seen.stack().size() != GateStacking.MAX_STACK
                || seen.stack().getFirst() != third.stack().getFirst()) {
            return "Fadenkreuz trifft den Stapel aus vier Gates nicht: " + mc.hitResult;
        }
        return null;
    }

    private static @Nullable StepResult limitStand(SelfTest.StepContext ctx) {
        CovRaceSite.Site site = SITE.get();
        StepResult wait = CovRaceSite.once(ctx, "Stand vor dem Stapel", server -> {
            ServerPlayer p = CovRaceSite.player(server);
            hold(p, RaceModule.GATE_SMALL.get());
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            Vec3 feet = site.raw(STEP_X[0] + 0.6, 4.2, Z - 3.0);
            p.teleportTo(p.level(), feet.x, feet.y, feet.z, Set.of(), 0.0F, 0.0F, true);
            return null;
        });
        if (wait != null) {
            return wait;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.getMainHandItem().is(RaceModule.GATE_SMALL.get()) || player.position().distanceTo(site.raw(STEP_X[0] + 0.6, 4.2,
                Z - 3.0)) > 0.6) {
            return ctx.tick() > WAIT ? StepResult.fail("Spieler mit kleinem Gate kommt vor dem Stapel nicht an") : StepResult.pending();
        }
        return null;
    }

    private static @Nullable String limitHint(GatePreview.Target target) {
        Component status = GatePreview.status(target, GateMountKeys.preference());
        if (target.free() || target.refusal() != GateStacking.StackRefusal.TOO_HIGH) {
            return "Vorschau des fünften Gates: frei " + target.free() + ", " + target.refusal();
        }
        if (!(status.getContents() instanceof TranslatableContents contents) || !contents.getKey().equals(TOO_HIGH)
                || !Arrays.asList(contents.getArgs()).contains(GateStacking.MAX_STACK)) {
            return "Hinweis beim fünften Gate „" + status.getString() + "“ statt „höchstens " + GateStacking.MAX_STACK + " Gates übereinander“";
        }
        return null;
    }

    private static StepResult limitStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return StepResult.fail("kein Spieler");
        }
        StepResult stand = limitStand(ctx);
        if (stand != null) {
            return stand;
        }
        int phase = CovRaceSite.phase(ctx);
        if (phase == 0) {
            String aim = limitAim(mc, player);
            GatePreview.Target target = aim == null ? GatePreview.target(mc) : null;
            if (aim != null || target == null) {
                return ctx.tick() > WAIT ? StepResult.fail(aim != null ? aim : "keine Vorschau beim Blick auf den Stapel") : StepResult.pending();
            }
            String problem = limitHint(target);
            if (problem != null) {
                return StepResult.fail(problem);
            }
            ctx.log("Vorschau: „" + GatePreview.status(target, GateMountKeys.preference()).getString() + "“");
            KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        if (CovRaceSite.since(ctx) < 20) {
            return StepResult.pending();
        }
        CovRaceSite.Site site = SITE.get();
        StepResult wait = CovRaceSite.once(ctx, "Server nach dem Klick", server -> {
            ServerPlayer p = CovRaceSite.player(server);
            GateEntity bottom = CovRaceSite.require(p.level(), site, STEP + "0_0");
            int size = bottom.stack().size();
            GateStacking.StackRefusal refusal = ((GateItem) RaceModule.GATE_SMALL.get()).stackFor(bottom, p).refusal();
            return size == GateStacking.MAX_STACK && refusal == GateStacking.StackRefusal.TOO_HIGH ? null
                    : "Stapel nach dem Klick " + size + " hoch, Server meldet " + refusal;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("Rechtsklick: Stapel bleibt " + GateStacking.MAX_STACK + " hoch, Server meldet TOO_HIGH (Text "
                + Component.translatable("propwash.race.gate.stack_too_high", GateStacking.MAX_STACK).getString() + ")");
        return StepResult.pass();
    }

    private static StepResult courseStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        CovRaceSite.Site site = SITE.get();
        if (player == null || site == null) {
            return StepResult.fail("Aufbau fehlt");
        }
        List<String> order = List.of(A, B, B2, A2);
        Vec3 stand = site.raw(2.25, 3.0, Z - 2.5);
        int phase = CovRaceSite.phase(ctx);
        if (phase == 0) {
            StepResult wait = CovRaceSite.once(ctx, "Blickpunkt", server -> {
                ServerPlayer p = CovRaceSite.player(server);
                hold(p, RaceModule.COURSE_TOOL.get());
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(p.level(), stand.x, stand.y, stand.z, Set.of(), 0.0F, 0.0F, true);
                return null;
            });
            if (wait != null) {
                return wait;
            }
            if (player.position().distanceTo(stand) > 0.6 || !player.getMainHandItem().is(RaceModule.COURSE_TOOL.get())) {
                return ctx.tick() > WAIT ? StepResult.fail("Spieler mit Kurs-Werkzeug kommt am Blickpunkt nicht an") : StepResult.pending();
            }
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        if (phase <= order.size()) {
            if (CovRaceSite.since(ctx) < 5) {
                return StepResult.pending();
            }
            GateEntity gate = client(order.get(phase - 1));
            if (gate == null) {
                return StepResult.fail(order.get(phase - 1) + " fehlt beim Client");
            }
            GateGeometry g = gate.geometry();
            CovRaceSite.lookAt(player, new Vec3(g.x(), g.y() + g.height() - g.frame() * 0.5, g.z()));
            if (!(mc.hitResult instanceof EntityHitResult hit) || hit.getEntity() != gate) {
                return CovRaceSite.since(ctx) > 60 ? StepResult.fail("Blick auf den oberen Balken von " + order.get(phase - 1) + " trifft "
                        + (mc.hitResult instanceof EntityHitResult other ? other.getEntity() : mc.hitResult == null ? "nichts" : mc.hitResult.getType())
                        + " (Auge " + player.getEyePosition() + ", Gate " + text(g) + ")") : StepResult.pending();
            }
            KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
            CovRaceSite.next(ctx, phase + 1);
            return StepResult.pending();
        }
        if (CovRaceSite.since(ctx) < 10) {
            return StepResult.pending();
        }
        StepResult wait = CovRaceSite.once(ctx, "Kurs", server -> {
            ServerPlayer p = CovRaceSite.player(server);
            int course = CourseToolItem.courseOf(p.getMainHandItem());
            site.ids().put(COURSE, course);
            CourseRegistry registry = CourseRegistry.get();
            CourseRegistry.Course data = registry == null ? null : registry.course(course);
            if (data == null || data.gates.size() != order.size()) {
                return "Kurs aus vier Klicks nicht angelegt: " + (data == null ? "kein Kurs " + course : data.gates.size() + " Durchgänge");
            }
            for (int i = 0; i < order.size(); i++) {
                String uuid = CovRaceSite.require(p.level(), site, order.get(i)).getUUID().toString();
                if (!data.gates.get(i).uuid.equals(uuid) || data.gates.get(i).reversed()) {
                    return "Durchgang " + (i + 1) + " ist nicht " + order.get(i) + " vorwärts";
                }
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        int course = site.id(COURSE);
        CourseData data = ClientCourses.get(course);
        if (data == null || data.gates().size() != order.size()) {
            return CovRaceSite.since(ctx) > WAIT ? StepResult.fail("Kurs kommt beim Client nicht an") : StepResult.pending();
        }
        List<GateGeometry> gates = data.geometry();
        List<Vec3> path = CovRaceSite.waypointPath(gates, LEAD);
        String problem = CovRaceSite.clientLap(course, gates, path, GateOpenings.NONE);
        if (problem != null) {
            return StepResult.fail("Runde beim Client: " + problem);
        }
        wait = CovRaceSite.once(ctx, "Server-Runde", server -> CovRaceSite.serverLap(server, CovRaceSite.player(server), course, path, true).problem());
        if (wait != null) {
            return wait;
        }
        ctx.log("Kurs-Werkzeug: vier Klicks auf die oberen Balken treffen je das gemeinte Gate, Kurs " + course + " unten West, unten Ost, oben Ost, "
                + "oben West; Runde beim Client (Gates 1 bis 3, eine Runde, nichts ausgelassen) und am Server angenommen");
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        GatePlacement.Preference saved = SAVED.getAndSet(null);
        if (saved != null) {
            GateMountKeys.setPreference(saved);
        }
        CovRaceSite.Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        StepResult wait = CovRaceSite.once(ctx, "Abbau", server -> {
            int previous = RULE.getAndSet(Integer.MIN_VALUE);
            if (previous != Integer.MIN_VALUE) {
                WorldRules.set(server, WorldRuleSpecs.FREE_COURSE_PLACEMENT, previous);
            }
            ServerPlayer p = CovRaceSite.player(server);
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            return CovRaceSite.teardown(server, site);
        });
        if (wait != null) {
            return wait;
        }
        SITE.set(null);
        return StepResult.pass();
    }

    private static final class View implements Screenshots.Scene {
        private final Vec3 feet;
        private final Vec3 target;
        private final Supplier<ItemStack> held;

        private View(Vec3 feet, Vec3 target, Supplier<ItemStack> held) {
            this.feet = feet;
            this.target = target;
            this.held = held;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            CovRaceSite.Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null || mc.player == null) {
                return StepResult.fail("kein Aufbau für das Bildschirmfoto");
            }
            mc.getTutorial().setStep(TutorialSteps.NONE);
            Vec3 at = site.raw(this.feet.x, this.feet.y, this.feet.z);
            Supplier<ItemStack> item = this.held;
            StepResult wait = CovRaceSite.once(ctx, "Blickpunkt", server -> {
                ServerPlayer p = CovRaceSite.player(server);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(p.level(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
                p.setItemInHand(InteractionHand.MAIN_HAND, item.get());
                p.connection.send(new ClientboundSetHeldSlotPacket(p.getInventory().getSelectedSlot()));
                return null;
            });
            if (wait != null) {
                return wait;
            }
            if (mc.player.position().distanceTo(at) > 0.6 || mc.player.getMainHandItem().getItem() != item.get().getItem()) {
                return ctx.tick() > WAIT ? StepResult.fail("Spieler nicht am Blickpunkt") : StepResult.pending();
            }
            this.hold(ctx);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            CovRaceSite.Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            mc.gui.toastManager().clear();
            if (site != null && mc.player != null) {
                CovRaceSite.lookAt(mc.player, site.raw(this.target.x, this.target.y, this.target.z));
            }
        }
    }

    private static final class LimitShot implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return StepResult.fail("kein Spieler");
            }
            mc.getTutorial().setStep(TutorialSteps.NONE);
            StepResult stand = limitStand(ctx);
            if (stand != null) {
                return stand;
            }
            String aim = limitAim(mc, mc.player);
            GatePreview.Target target = aim == null ? GatePreview.target(mc) : null;
            String problem = aim != null ? aim : target == null ? "keine Vorschau" : limitHint(target);
            if (problem != null) {
                return ctx.tick() > WAIT ? StepResult.fail(problem) : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.toastManager().clear();
            if (mc.player != null) {
                limitAim(mc, mc.player);
            }
        }
    }
}
