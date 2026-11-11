package de.lardtom.propwash.barrier;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.GateEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrierCollisionTest {
    private static final List<Class<?>> OTHERS = List.of(Player.class, ServerPlayer.class, LocalPlayer.class, ArmorStand.class, Arrow.class,
            Snowball.class, Zombie.class, Pig.class, ItemEntity.class, FallingBlockEntity.class, Minecart.class, Boat.class, GateEntity.class);
    private static final List<Class<?>> DRONES = List.of(QuadcopterEntity.class, AddonDrone.class);

    static final class AddonDrone extends QuadcopterEntity {
        AddonDrone(EntityType<? extends QuadcopterEntity> type, Level level) {
            super(type, level);
        }
    }

    @Test
    void everyDroneTypeCountsAsDrone() {
        for (Class<?> type : DRONES) {
            assertEquals(Collider.DRONE, Collider.ofType(type), type.getSimpleName());
        }
    }

    @Test
    void playersMobsItemsAndProjectilesAreOtherEntities() {
        for (Class<?> type : OTHERS) {
            assertEquals(Collider.ENTITY, Collider.ofType(type), type.getSimpleName());
        }
    }

    @Test
    void noEntityIsNoCollider() {
        assertEquals(Collider.NONE, Collider.ofType(null));
    }

    @Test
    void droneOnlyBarrierLetsOnlyDronesThrough() {
        for (Class<?> type : DRONES) {
            assertFalse(BarrierKind.DRONE_PASS.solidFor(Collider.ofType(type)), type.getSimpleName());
        }
        for (Class<?> type : OTHERS) {
            assertTrue(BarrierKind.DRONE_PASS.solidFor(Collider.ofType(type)), type.getSimpleName());
        }
        assertTrue(BarrierKind.DRONE_PASS.solidFor(Collider.NONE));
    }

    @Test
    void droneBarrierStopsOnlyDrones() {
        for (Class<?> type : DRONES) {
            assertTrue(BarrierKind.DRONE_BLOCK.solidFor(Collider.ofType(type)), type.getSimpleName());
        }
        for (Class<?> type : OTHERS) {
            assertFalse(BarrierKind.DRONE_BLOCK.solidFor(Collider.ofType(type)), type.getSimpleName());
        }
        assertFalse(BarrierKind.DRONE_BLOCK.solidFor(Collider.NONE));
    }

    @Test
    void theTwoBarriersAreExactOppositesForEveryMover() {
        for (Collider collider : List.of(Collider.DRONE, Collider.ENTITY, Collider.NONE)) {
            assertEquals(!BarrierKind.DRONE_PASS.solidFor(collider), BarrierKind.DRONE_BLOCK.solidFor(collider), collider.name());
        }
    }

    @Test
    void gatesAndCourseElementsSeeBothBarriersAsFullBlocks() {
        for (BarrierKind kind : BarrierKind.values()) {
            assertTrue(kind.solidFor(Collider.COURSE), kind.name());
        }
        assertEquals(List.of(Collider.DRONE, Collider.ENTITY, Collider.NONE, Collider.COURSE), List.of(Collider.values()));
    }

    @Test
    void blockIdsMatchTheKinds() {
        assertEquals("drone_pass_barrier", BarrierKind.DRONE_PASS.path());
        assertEquals("drone_block_barrier", BarrierKind.DRONE_BLOCK.path());
    }
}
