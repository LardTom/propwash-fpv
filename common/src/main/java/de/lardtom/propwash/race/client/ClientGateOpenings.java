package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateOpenings;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.LapTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class ClientGateOpenings implements GateOpenings {
    public static final double REACH = 0.25;

    private final IntSupplier course;
    private final Supplier<List<GateGeometry>> gates;

    private ClientGateOpenings(IntSupplier course, Supplier<List<GateGeometry>> gates) {
        this.course = course;
        this.gates = gates;
    }

    public static ClientGateOpenings of(int courseId, List<GateGeometry> gates) {
        List<GateGeometry> copy = List.copyOf(gates);
        return new ClientGateOpenings(() -> courseId, () -> copy);
    }

    public static ClientGateOpenings following(LapTracker tracker) {
        return new ClientGateOpenings(tracker::courseId, tracker::gates);
    }

    @Override
    public boolean legs(int gateIndex) {
        List<GateGeometry> list = this.gates.get();
        if (gateIndex < 0 || gateIndex >= list.size()) {
            return false;
        }
        GateGeometry geometry = list.get(gateIndex);
        if (!GateStacking.stackable(geometry)) {
            return false;
        }
        GateEntity gate = find(this.course.getAsInt(), gateIndex, geometry);
        return gate != null && gate.base() == GateStacking.Base.LEGS;
    }

    public static @Nullable GateEntity find(int courseId, int gateIndex, GateGeometry geometry) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft == null ? null : minecraft.level;
        if (level == null || courseId == GateEntity.NO_COURSE) {
            return null;
        }
        double x = geometry.x();
        double y = geometry.y();
        double z = geometry.z();
        AABB area = new AABB(x - REACH, y - REACH, z - REACH, x + REACH, y + REACH, z + REACH);
        List<GateEntity> found = level.getEntitiesOfClass(GateEntity.class, area,
                gate -> !gate.isRemoved() && gate.courseId() == courseId && gate.hasPass(gateIndex) && Math.abs(gate.getX() - x) <= REACH
                        && Math.abs(gate.getY() - y) <= REACH && Math.abs(gate.getZ() - z) <= REACH);
        return found.isEmpty() ? null : found.getFirst();
    }
}
