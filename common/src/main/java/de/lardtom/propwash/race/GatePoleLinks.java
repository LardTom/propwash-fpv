package de.lardtom.propwash.race;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class GatePoleLinks {
    private static final double SEARCH = 0.1;

    private GatePoleLinks() {
    }

    public record Target(GatePoles.Seat seat, int chain, GatePoles.Refusal refusal) {
    }

    private static AABB around(double x, double y, double z) {
        return new AABB(x - SEARCH, y - SEARCH, z - SEARCH, x + SEARCH, y + SEARCH, z + SEARCH);
    }

    public static @Nullable GateEntity riderOn(Level level, GatePoles.Seat seat) {
        for (GateEntity other : level.getEntitiesOfClass(GateEntity.class, around(seat.x(), seat.y(), seat.z()),
                gate -> !gate.isRemoved() && gate.kind() == CourseElement.GATE_POLE)) {
            GateGeometry geometry = other.geometry();
            if (GatePoles.standing(geometry) && GatePoles.on(geometry, seat)) {
                return other;
            }
        }
        return null;
    }

    public static List<GateEntity> riders(GateEntity support) {
        List<GatePoles.Seat> seats = GatePoles.seats(support.geometry());
        if (seats.isEmpty()) {
            return List.of();
        }
        List<GateEntity> riders = new ArrayList<>(seats.size());
        for (GatePoles.Seat seat : seats) {
            GateEntity rider = riderOn(support.level(), seat);
            if (rider != null && rider != support && !riders.contains(rider)) {
                riders.add(rider);
            }
        }
        return riders;
    }

    public static @Nullable GateEntity support(GateEntity pole) {
        GateGeometry own = pole.geometry();
        if (!GatePoles.standing(own)) {
            return null;
        }
        for (GateEntity other : pole.level().getEntitiesOfClass(GateEntity.class, around(own.x(), own.y(), own.z()),
                gate -> gate != pole && !gate.isRemoved())) {
            if (GatePoles.restsOn(own, other.geometry())) {
                return other;
            }
        }
        return null;
    }

    public static int chainBelow(GateEntity pole) {
        int count = 1;
        GateEntity current = pole;
        for (int guard = 0; guard < GatePoles.MAX_CHAIN * 4; guard++) {
            GateEntity below = support(current);
            if (below == null || below.kind() != CourseElement.GATE_POLE) {
                break;
            }
            count++;
            current = below;
        }
        return count;
    }

    public static GateEntity top(GateEntity pole) {
        GateEntity current = pole;
        for (int guard = 0; guard < GatePoles.MAX_CHAIN * 4; guard++) {
            List<GateEntity> above = riders(current);
            if (above.isEmpty()) {
                break;
            }
            current = above.getFirst();
        }
        return current;
    }

    public static @Nullable Target target(GateEntity clicked, double hitX, double hitZ) {
        if (clicked.isRemoved()) {
            return null;
        }
        if (clicked.kind() == CourseElement.GATE_POLE) {
            if (!GatePoles.standing(clicked.geometry())) {
                return null;
            }
            GateEntity top = top(clicked);
            List<GatePoles.Seat> seats = GatePoles.seats(top.geometry());
            if (seats.isEmpty()) {
                return null;
            }
            int chain = chainBelow(top);
            return new Target(seats.getFirst(), chain, GatePoles.check(chain));
        }
        if (!clicked.kind().gate()) {
            return null;
        }
        GateEntity gate = clicked.stackable() ? clicked.stack().getLast() : clicked;
        List<GatePoles.Seat> seats = GatePoles.seats(gate.geometry());
        int index = GatePoles.nearest(seats, hitX, hitZ);
        if (index < 0) {
            return null;
        }
        GatePoles.Seat seat = seats.get(index);
        GateEntity rider = riderOn(gate.level(), seat);
        if (rider == null) {
            return new Target(seat, 0, GatePoles.Refusal.NONE);
        }
        GateEntity top = top(rider);
        List<GatePoles.Seat> above = GatePoles.seats(top.geometry());
        if (above.isEmpty()) {
            return null;
        }
        int chain = chainBelow(top);
        return new Target(above.getFirst(), chain, GatePoles.check(chain));
    }

    public static boolean keepsRiders(GateGeometry current, GateGeometry next) {
        if (!GatePoles.pole(current) || !GatePoles.pole(next) || current.mount() != next.mount()) {
            return false;
        }
        return Math.abs(current.x() - next.x()) < GatePoles.EPSILON && Math.abs(current.y() - next.y()) < GatePoles.EPSILON
                && Math.abs(current.z() - next.z()) < GatePoles.EPSILON && Math.abs(current.height() - next.height()) < GatePoles.EPSILON;
    }

    public static List<GateEntity> above(GateEntity support) {
        List<GateEntity> out = new ArrayList<>();
        List<GateEntity> open = new ArrayList<>(riders(support));
        while (!open.isEmpty() && out.size() < GatePoles.MAX_CHAIN * 8) {
            GateEntity next = open.removeFirst();
            if (next == support || out.contains(next)) {
                continue;
            }
            out.add(next);
            open.addAll(riders(next));
        }
        return out;
    }

    public static void detach(GateEntity support, @Nullable ServerPlayer breaker) {
        if (!(support.level() instanceof ServerLevel level)) {
            return;
        }
        List<GateEntity> loose = above(support);
        for (GateEntity pole : loose.reversed()) {
            if (breaker != null && !breaker.hasInfiniteMaterials()) {
                RaceModule.item(pole.kind(), pole.size()).ifPresent(item -> pole.spawnAtLocation(level, new ItemStack(item)));
            }
            pole.discard();
        }
        if (support.isRemoved() && support.kind() == CourseElement.GATE_POLE) {
            GateEntity below = support(support);
            if (below != null) {
                below.refreshStack();
            }
        }
    }

    public static boolean dye(GateEntity pole, Player player, ItemStack stack) {
        DyeColor color = stack.get(DataComponents.DYE);
        if (color == null || pole.kind() != CourseElement.GATE_POLE) {
            return false;
        }
        if (!player.mayBuild() || pole.variant() == color.getId()) {
            return true;
        }
        if (!pole.level().isClientSide()) {
            pole.setVariant(color.getId());
            pole.level().playSound(null, pole.getX(), pole.getY() + 0.5, pole.getZ(), SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            pole.gameEvent(GameEvent.ENTITY_INTERACT, player);
            stack.consume(1, player);
        }
        return true;
    }
}
