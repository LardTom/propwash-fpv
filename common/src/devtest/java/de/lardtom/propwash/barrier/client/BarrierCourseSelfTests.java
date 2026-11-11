package de.lardtom.propwash.barrier.client;

import de.lardtom.propwash.barrier.BarrierKind;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.race.CourseElementItem;
import de.lardtom.propwash.race.FreeformBlocks;
import de.lardtom.propwash.race.FreeformFill;
import de.lardtom.propwash.race.FreeformRegion;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateItem;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.RaceModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

final class BarrierCourseSelfTests {
    private static final int SITE_X = 18;
    private static final int SITE_Z = -5;
    private static final double EPSILON = 1.0E-6;
    private static final String RESULT = "barrier_course";

    private record Material(String name, BlockState ring, BlockState middle) {
    }

    private BarrierCourseSelfTests() {
    }

    static void register() {
        SelfTest.clientStep(BarrierSelfTests.GROUP + "Gates und Kurs-Elemente sehen beide Barrieren als volle Blöcke: Freeform-Rahmen, stehen, "
                + "andocken, Platz prüfen", 400, BarrierCourseSelfTests::step);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        StepResult waiting = BarrierSite.await(ctx, BarrierSelfTests.FUTURE, "Server");
        if (waiting != null) {
            return waiting;
        }
        Object stored = ctx.state().get(RESULT);
        if (!(stored instanceof AtomicReference<?> result)) {
            AtomicReference<String> out = new AtomicReference<>();
            ctx.state().put(RESULT, out);
            ctx.state().put(BarrierSelfTests.FUTURE, ctx.onServer(server -> out.set(check(server))));
            return StepResult.pending();
        }
        String text = String.valueOf(result.get());
        if (text.startsWith("FEHLER ")) {
            return StepResult.fail(text.substring(7));
        }
        ctx.log(text);
        return StepResult.pass();
    }

    private static String check(MinecraftServer server) {
        BlockPos origin = BarrierSite.prepare(server);
        ServerPlayer player = BarrierSite.player(server);
        ServerLevel level = player.level();
        BlockPos base = origin.offset(SITE_X, 0, SITE_Z);
        BlockState pass = BarrierSite.state(BarrierKind.DRONE_PASS);
        BlockState block = BarrierSite.state(BarrierKind.DRONE_BLOCK);
        List<Material> materials = List.of(new Material("Stein", Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState()),
                new Material("durchlässige Barriere", pass, pass), new Material("Drohnen-Sperre", block, block),
                new Material("durchlässige Barriere mit Sperre unten in der Mitte", pass, block));
        GateItem gate = RaceModule.GATE_SMALL.get();
        CourseElementItem pole = RaceModule.SLALOM_POLE.get();
        List<String> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (Material material : materials) {
            String framed = frame(level, base, material);
            if (framed != null) {
                problems.add(framed);
            }
            if (material.ring() != material.middle()) {
                continue;
            }
            String stood = stand(player, level, base.offset(6, 0, 0), material, gate, pole);
            if (stood != null) {
                problems.add(stood);
            }
            String docked = dock(player, level, base.offset(6, 0, 3), material, gate);
            if (docked != null) {
                problems.add(docked);
            }
            String placed = room(player, level, base.offset(2, 0, 4), material, gate);
            if (placed != null) {
                problems.add(placed);
            }
            notes.add(material.name());
        }
        if (!problems.isEmpty()) {
            return "FEHLER " + String.join("; ", problems);
        }
        return "Freeform-Rahmen 3×1 geschlossen, Gate und Slalomstange stehen oben auf dem Block, Wand-Gate dockt an der Seite an, "
                + "Gate zwischen zwei Säulen frei, Block in der Öffnung sperrt – gleich für " + String.join(", ", notes);
    }

    private static @Nullable String frame(ServerLevel level, BlockPos base, Material material) {
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = 1; dx <= 3; dx++) {
            cells.add(base.offset(dx, 0, 0));
            cells.add(base.offset(dx, 2, 0));
        }
        cells.add(base.offset(0, 1, 0));
        cells.add(base.offset(4, 1, 0));
        BlockPos middle = base.offset(2, 0, 0);
        for (BlockPos cell : cells) {
            BarrierSite.set(level, cell, cell.equals(middle) ? material.middle() : material.ring());
        }
        FreeformFill.Result result = FreeformFill.fill(FreeformBlocks.probe(level), FreeformRegion.AXIS_Z, base.getX() + 2, base.getY() + 1,
                base.getZ(), FreeformBlocks.limit(level));
        for (BlockPos cell : cells) {
            BarrierSite.set(level, cell, Blocks.AIR.defaultBlockState());
        }
        if (result.status() == FreeformFill.Status.OK && result.width() == 3 && result.height() == 1) {
            return null;
        }
        return material.name() + ": Freeform-Rahmen " + result.status() + " " + result.width() + "×" + result.height() + " statt OK 3×1";
    }

    private static GatePlacement.Click click(ServerPlayer player, GateItem item, BlockPos clicked, Direction face, Vec3 hit, Vec3 eye, float yaw) {
        BlockPlaceContext place = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(item),
                new BlockHitResult(hit, face, clicked, false));
        return GateItem.click(place, clicked, face, hit, eye, yaw);
    }

    private static @Nullable String stand(ServerPlayer player, ServerLevel level, BlockPos at, Material material, GateItem gate, CourseElementItem pole) {
        BarrierSite.set(level, at, material.ring());
        Vec3 hit = new Vec3(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5);
        Vec3 eye = hit.add(0.0, 1.6, -2.5);
        GatePlacement.Click click = click(player, gate, at, Direction.UP, hit, eye, 0.0F);
        GatePlacement.Click element = click(player, pole, at, Direction.UP, hit, eye, 0.0F);
        GateGeometry placed = pole.placement(element, GatePlacement.Preference.DEFAULT);
        BarrierSite.set(level, at, Blocks.AIR.defaultBlockState());
        List<String> wrong = new ArrayList<>();
        if (click.surface() != GateMount.Surface.FLOOR || click.blockY() != at.getY() || Math.abs(click.floorY() - (at.getY() + 1.0)) > EPSILON) {
            wrong.add(String.format(Locale.ROOT, "Gate oben auf dem Block steht auf %s y %d, Boden %.2f statt FLOOR y %d, Boden %d", click.surface(),
                    click.blockY(), click.floorY(), at.getY(), at.getY() + 1));
        }
        if (!pole.allowed(element) || Math.abs(placed.y() - (at.getY() + 1.0)) > EPSILON) {
            wrong.add(String.format(Locale.ROOT, "Slalomstange oben auf dem Block bei y %.2f statt %d", placed.y(), at.getY() + 1));
        }
        return wrong.isEmpty() ? null : material.name() + ": " + String.join(", ", wrong);
    }

    private static @Nullable String dock(ServerPlayer player, ServerLevel level, BlockPos at, Material material, GateItem gate) {
        BlockPos clicked = at.above();
        BarrierSite.set(level, at, material.ring());
        BarrierSite.set(level, clicked, material.ring());
        Vec3 hit = new Vec3(clicked.getX(), clicked.getY() + 0.5, clicked.getZ() + 0.5);
        GatePlacement.Click click = click(player, gate, clicked, Direction.WEST, hit, hit.add(-3.0, 0.2, 0.0), 90.0F);
        BarrierSite.set(level, clicked, Blocks.AIR.defaultBlockState());
        BarrierSite.set(level, at, Blocks.AIR.defaultBlockState());
        if (click.surface() == GateMount.Surface.WALL && click.blockX() == clicked.getX() && click.blockY() == clicked.getY() && click.faceX() == -1
                && Math.abs(click.plane() - clicked.getX()) < EPSILON) {
            return null;
        }
        return String.format(Locale.ROOT, "%s: Gate an der Westseite dockt als %s an Block x %d y %d, Ebene %.2f an statt WALL x %d y %d, Ebene %d",
                material.name(), click.surface(), click.blockX(), click.blockY(), click.plane(), clicked.getX(), clicked.getY(), clicked.getX());
    }

    private static @Nullable String room(ServerPlayer player, ServerLevel level, BlockPos at, Material material, GateItem gate) {
        BlockPos floor = at.below();
        Vec3 hit = new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5);
        GatePlacement.Click click = click(player, gate, floor, Direction.UP, hit, hit.add(0.0, 1.6, -2.5), 0.0F);
        GateGeometry geometry = GateItem.snapped(level, gate.placement(click, GatePlacement.Preference.DEFAULT));
        boolean empty = free(level, geometry);
        List<BlockPos> pillars = new ArrayList<>();
        for (int dy = 0; dy <= 3; dy++) {
            pillars.add(at.offset(-2, dy, 0));
            pillars.add(at.offset(2, dy, 0));
        }
        for (BlockPos pillar : pillars) {
            BarrierSite.set(level, pillar, material.ring());
        }
        boolean between = free(level, geometry);
        BarrierSite.set(level, at.above(), material.ring());
        boolean inside = free(level, geometry);
        BarrierSite.set(level, at.above(), Blocks.AIR.defaultBlockState());
        for (BlockPos pillar : pillars) {
            BarrierSite.set(level, pillar, Blocks.AIR.defaultBlockState());
        }
        if (empty && between && !inside) {
            return null;
        }
        return material.name() + ": Platz für das Gate leer " + (empty ? "frei" : "belegt") + ", zwischen zwei Säulen " + (between ? "frei" : "belegt")
                + ", mit Block in der Öffnung " + (inside ? "frei" : "belegt") + " statt frei, frei, belegt";
    }

    private static boolean free(ServerLevel level, GateGeometry geometry) {
        return GateItem.insideWorld(level, geometry) && GateItem.free(level, geometry, GateStacking.placedBase(geometry), false, null, true, true);
    }
}
