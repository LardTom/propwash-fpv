package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.drone.CollisionFlags;
import de.lardtom.propwash.api.drone.CollisionQuery;
import de.lardtom.propwash.api.drone.CollisionRule;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.apiimpl.Api17Registries.Registered;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.collision.CollisionEnvironment;
import de.lardtom.propwash.drone.collision.CollisionRules;
import de.lardtom.propwash.drone.collision.ContactResponse;
import de.lardtom.propwash.drone.collision.GhostLatch;
import de.lardtom.propwash.rescue.RescueWorld;
import de.lardtom.propwash.rescue.VoxelFlight;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonCollisionTest {
    private static final double DT = 1.0 / 144.0;
    private static final Identifier A = Identifier.fromNamespaceAndPath("collisiontest", "a");
    private static final Identifier B = Identifier.fromNamespaceAndPath("collisiontest", "b");
    private static final Identifier C = Identifier.fromNamespaceAndPath("collisiontest", "c");
    private static final Identifier D = Identifier.fromNamespaceAndPath("collisiontest", "d");

    private static final CollisionQuery QUERY = new CollisionQuery() {
        @Override
        public Entity drone() {
            return null;
        }

        @Override
        public DroneView view() {
            return null;
        }

        @Override
        public Optional<Player> pilot() {
            return Optional.empty();
        }

        @Override
        public Side side() {
            return Side.SERVER;
        }

        @Override
        public boolean autonomous() {
            return false;
        }

        @Override
        public boolean switchActive(Identifier id) {
            return false;
        }

        @Override
        public double axis(Identifier id) {
            return 0.0;
        }
    };

    private static final RescueWorld FLOOR = new RescueWorld() {
        @Override
        public boolean solid(int x, int y, int z) {
            return y < 64;
        }

        @Override
        public int surfaceY(int x, int z) {
            return 64;
        }

        @Override
        public int minY() {
            return 0;
        }

        @Override
        public int maxY() {
            return 128;
        }
    };

    private static Registered<CollisionRule> rule(Identifier id, CollisionRule rule) {
        return new Registered<>(id, rule);
    }

    @Test
    void flagsAreTheAndOfAllRules() {
        assertSame(CollisionRules.SOLID, CollisionRules.evaluate(List.of(), QUERY));
        CollisionRules.Evaluation solid = CollisionRules.evaluate(List.of(rule(A, q -> CollisionFlags.SOLID), rule(B, q -> null)), QUERY);
        assertSame(CollisionRules.SOLID, solid);
        assertNull(solid.rule());
        CollisionRules.Evaluation mixed = CollisionRules.evaluate(List.of(
                rule(A, q -> CollisionFlags.SOLID),
                rule(B, q -> CollisionFlags.SOLID.withFluids(false)),
                rule(C, q -> {
                    throw new IllegalStateException("kaputte Regel");
                }),
                rule(D, q -> CollisionFlags.SOLID.withSnags(false))), QUERY);
        assertEquals(new CollisionFlags(true, false, true, false), mixed.flags());
        assertEquals(B, mixed.rule());
        assertTrue(mixed.blocks());
        assertFalse(mixed.solid());
        CollisionRules.Evaluation ghost = CollisionRules.evaluate(List.of(
                rule(A, q -> CollisionFlags.GHOST),
                rule(B, q -> CollisionFlags.SOLID)), QUERY);
        assertEquals(CollisionFlags.GHOST, ghost.flags());
        assertEquals(A, ghost.rule());
        assertFalse(ghost.blocks());
        CollisionRules.Evaluation split = CollisionRules.evaluate(List.of(
                rule(A, q -> CollisionFlags.SOLID.withBlocks(false)),
                rule(B, q -> CollisionFlags.GHOST.withBlocks(true))), QUERY);
        assertEquals(CollisionFlags.GHOST, split.flags(), "jede Regel darf eine Kollision abschalten, keine sie wieder einschalten");
        assertEquals(A, split.rule());
    }

    @Test
    void rulesTheServerDoesNotKnowAreLeftOut() {
        List<Registered<CollisionRule>> rules = List.of(
                rule(A, q -> CollisionFlags.GHOST),
                rule(B, q -> CollisionFlags.SOLID.withFluids(false)),
                rule(C, q -> CollisionFlags.SOLID));
        assertSame(rules, CollisionRules.known(rules, id -> true), "kennt der Server alle Regeln, bleibt die Liste dieselbe");
        List<Registered<CollisionRule>> known = CollisionRules.known(rules, id -> !id.equals(A));
        assertEquals(List.of(B, C), known.stream().map(Registered::id).toList());
        CollisionRules.Evaluation partial = CollisionRules.evaluate(known, QUERY);
        assertTrue(partial.blocks(), "die dem Server unbekannte Geist-Regel schaltet die Block-Kollision nicht ab");
        assertEquals(CollisionFlags.SOLID.withFluids(false), partial.flags());
        assertEquals(B, partial.rule());
        List<Registered<CollisionRule>> none = CollisionRules.known(rules, id -> false);
        assertTrue(none.isEmpty());
        assertSame(CollisionRules.SOLID, CollisionRules.evaluate(none, QUERY));
    }

    @Test
    void ghostLatchHoldsUntilTheHullIsFree() {
        GhostLatch latch = new GhostLatch();
        AtomicInteger probes = new AtomicInteger();
        assertFalse(latch.update(false, () -> {
            probes.incrementAndGet();
            return true;
        }));
        assertEquals(0, probes.get(), "fest bleibt fest, ohne die Hülle zu prüfen");
        assertTrue(latch.update(true, () -> true));
        assertFalse(latch.inside());
        assertTrue(latch.update(false, () -> true));
        assertTrue(latch.inside());
        assertTrue(latch.update(false, () -> true));
        assertFalse(latch.update(false, () -> false));
        assertFalse(latch.inside());
        assertTrue(latch.update(true, () -> true));
        latch.reset();
        assertFalse(latch.ghost());
        assertFalse(latch.inside());
    }

    @Test
    void environmentDropsGroundAndFluidsOnlyForTheirFlags() {
        SimEnvironment env = sampleEnvironment();
        SimEnvironment copy = new SimEnvironment();
        env.copyTo(copy);
        CollisionEnvironment.apply(CollisionFlags.SOLID, env);
        CollisionEnvironment.beforeStep(CollisionFlags.SOLID, env);
        assertSameEnvironment(copy, env);
        CollisionEnvironment.apply(CollisionFlags.SOLID.withFluids(false), env);
        assertEquals(Fluid.NONE, env.fluid);
        assertEquals(Double.NEGATIVE_INFINITY, env.waterSurface);
        assertEquals(copy.groundDistance, env.groundDistance);
        CollisionEnvironment.apply(CollisionFlags.SOLID.withBlocks(false), env);
        assertEquals(Double.POSITIVE_INFINITY, env.groundDistance);
        assertEquals(Double.POSITIVE_INFINITY, env.solidDistance);
        assertTrue(env.snag.active());
        CollisionEnvironment.beforeStep(CollisionFlags.SOLID.withSnags(false), env);
        assertFalse(env.snag.active());
    }

    @Test
    void contactChainStartsFromTheSurfaceAndIsCapped() {
        List<SurfaceResponse> tags = List.of(SurfaceResponse.NORMAL, SurfaceResponse.BOUNCY, SurfaceResponse.CUSHION, SurfaceResponse.STICKY,
                SurfaceResponse.normal(0.6), SurfaceResponse.normal(0.98));
        List<ContactResponse.Step> idle = List.of(v -> {
        }, v -> v.setRestitution(v.restitution()));
        for (SurfaceResponse tag : tags) {
            assertSame(tag, ContactResponse.chain(tag, 0.35, List.of()), tag.toString());
            assertSame(tag, ContactResponse.chain(tag, 0.35, idle), tag.toString());
        }
        List<Double> seen = new ArrayList<>();
        SurfaceResponse chained = ContactResponse.chain(SurfaceResponse.NORMAL, 0.35, List.of(
                v -> {
                    seen.add(v.restitution());
                    v.setRestitution(0.5);
                    v.setGrip(10.0);
                },
                v -> {
                    seen.add(v.restitution());
                    v.setRestitution(v.restitution() * 3.0);
                    v.setImpactScale(-1.0);
                    v.setGrip(Double.NaN);
                }));
        assertEquals(List.of(0.35, 0.5), seen, "jede Regel sieht das Ergebnis der vorigen");
        assertEquals(0.95, chained.restitution());
        assertEquals(1.5, chained.grip());
        assertEquals(0.0, chained.impactScale());
        SurfaceResponse slime = ContactResponse.chain(SurfaceResponse.BOUNCY, 0.35, List.of(v -> {
            seen.add(v.restitution());
            v.setGrip(0.01);
        }));
        assertEquals(0.8, seen.getLast());
        assertEquals(SurfaceResponse.BOUNCY.restitution(), slime.restitution());
        assertEquals(0.05, slime.grip());
        assertEquals(SurfaceResponse.BOUNCY.impactScale(), slime.impactScale());
        SurfaceResponse gripOnly = ContactResponse.chain(SurfaceResponse.NORMAL, 0.35, List.of(v -> v.setGrip(0.5)));
        assertEquals(SurfaceResponse.DRONE_RESTITUTION, gripOnly.restitution(), "ohne Änderung bleibt der Rückprall der Drohne");
        assertEquals(0.5, gripOnly.grip());
    }

    @Test
    void contactRuleMakesStoneBounceInTheServerFlight() {
        VoxelFlight plain = voxelDrop(List.of());
        VoxelFlight idle = voxelDrop(List.of(v -> {
        }));
        for (int i = 0; i < 288; i++) {
            plain.frame(Math.round(DT * 1e9));
            idle.frame(Math.round(DT * 1e9));
            assertEquals(Double.doubleToLongBits(plain.sim.body.py), Double.doubleToLongBits(idle.sim.body.py));
            assertEquals(Double.doubleToLongBits(plain.sim.body.vy), Double.doubleToLongBits(idle.sim.body.vy));
        }
        VoxelFlight stone = voxelDrop(List.of(v -> {
            if (v.base().impactScale() >= 1.0 && v.base().restitution() < 0.0) {
                v.setRestitution(0.8);
                v.setImpactScale(0.0);
            }
        }));
        VoxelFlight bare = voxelDrop(List.of());
        double rebound = 0.0;
        double bareRebound = 0.0;
        for (int i = 0; i < 72; i++) {
            stone.frame(Math.round(DT * 1e9));
            bare.frame(Math.round(DT * 1e9));
            rebound = Math.max(rebound, stone.sim.body.vy);
            bareRebound = Math.max(bareRebound, bare.sim.body.vy);
            assertFalse(stone.sim.events().contains(SimEventType.CRASH));
        }
        assertTrue(rebound >= 5.5, "Rückprall " + rebound);
        assertTrue(rebound > bareRebound + 3.0, "Stein ohne Regel " + bareRebound + ", mit Regel " + rebound);
    }

    @Test
    void ghostMoveHasNoContactAndFallsThroughTheGround() {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE);
        w.placeOnGround(0.0, 0.0, 0.0);
        w.run(1.0, 144.0);
        assertTrue(w.sim.telemetry().grounded || w.sim.telemetry().sleeping);
        assertFalse(w.report.ghost);
        double start = w.sim.body.py;
        long nanos = Math.round(DT * 1e9);
        int takeoffs = 0;
        for (int i = 0; i < 144; i++) {
            w.env.groundDistance = Math.max(0.0, w.sim.body.py - w.groundY);
            w.env.solidDistance = w.env.groundDistance;
            CollisionEnvironment.apply(CollisionFlags.GHOST, w.env);
            CollisionEnvironment.beforeStep(CollisionFlags.GHOST, w.env);
            w.sim.step(nanos, w.input, w.env, w.result);
            MoveReport r = w.report;
            r.clear();
            r.requestedX = w.result.dx;
            r.requestedY = w.result.dy;
            r.requestedZ = w.result.dz;
            r.actualX = w.result.dx;
            r.actualY = w.result.dy;
            r.actualZ = w.result.dz;
            r.ghost = true;
            w.sim.afterMove(r);
            assertFalse(r.hitX || r.hitY || r.hitZ || r.obliqueContact());
            assertEquals(-1.0, r.surfaceRestitution);
            assertEquals(1.0, r.surfaceImpactScale);
            assertTrue(Double.isNaN(r.supportGap));
            assertTrue(w.sim.ghost());
            assertFalse(w.sim.telemetry().grounded, "Geist bleibt nicht am Boden");
            takeoffs += w.sim.events().count(SimEventType.TAKEOFF);
            assertFalse(w.sim.events().contains(SimEventType.CRASH));
        }
        assertTrue(start - w.sim.body.py > 2.0, "Geist fällt nicht durch den Boden: " + (start - w.sim.body.py));
        assertTrue(takeoffs <= 1, "Abheben " + takeoffs);
        w.report.clear();
        assertFalse(w.report.ghost);
    }

    private static VoxelFlight voxelDrop(List<ContactResponse.Step> steps) {
        VoxelFlight flight = new VoxelFlight(new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), Tests.quietTuning(), 42L), FLOOR);
        flight.surfaces((x, y, z) -> null);
        flight.contacts(steps);
        double height = 0.6;
        double speed = 8.0;
        flight.place(0.5, 64.0 + height, 0.5, 0.0);
        flight.sim.body.vy = -Math.sqrt(speed * speed - 2.0 * ModelConstants.G * height);
        return flight;
    }

    private static SimEnvironment sampleEnvironment() {
        SimEnvironment env = new SimEnvironment();
        env.groundDistance = 1.5;
        env.solidDistance = 0.8;
        env.windX = 2.0;
        env.fluid = Fluid.WATER;
        env.waterSurface = 0.3;
        env.snag.hold = 2.0;
        env.snag.linear[0] = 1.0;
        return env;
    }

    private static void assertSameEnvironment(SimEnvironment a, SimEnvironment b) {
        assertEquals(Double.doubleToLongBits(a.groundDistance), Double.doubleToLongBits(b.groundDistance));
        assertEquals(Double.doubleToLongBits(a.solidDistance), Double.doubleToLongBits(b.solidDistance));
        assertEquals(Double.doubleToLongBits(a.windX), Double.doubleToLongBits(b.windX));
        assertEquals(a.fluid, b.fluid);
        assertEquals(Double.doubleToLongBits(a.waterSurface), Double.doubleToLongBits(b.waterSurface));
        assertEquals(Double.doubleToLongBits(a.snag.hold), Double.doubleToLongBits(b.snag.hold));
        assertEquals(Double.doubleToLongBits(a.snag.linear[0]), Double.doubleToLongBits(b.snag.linear[0]));
    }
}
