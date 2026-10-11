package de.lardtom.propwash.race;

import de.lardtom.propwash.core.ExtraCollider;
import de.lardtom.propwash.core.OrientedBox;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class GateCollisions {
    private GateCollisions() {
    }

    public static boolean blocks(Entity entity) {
        return (entity instanceof Player || entity instanceof QuadcopterEntity) && !entity.isSpectator() && !entity.noPhysics;
    }

    public static void collect(Level level, AABB area, List<AABB> out) {
        collect(level, area, out, null);
    }

    public static List<GateEntity> nearby(Level level, AABB area, @Nullable GateEntity ignore) {
        List<GateEntity> gates = level.getEntitiesOfClass(GateEntity.class, area.inflate(ExtraCollider.reach()));
        gates.removeIf(gate -> gate.isRemoved() || gate == ignore);
        return gates;
    }

    public static void collect(Level level, AABB area, List<AABB> out, @Nullable GateEntity ignore) {
        for (GateEntity gate : nearby(level, area, ignore)) {
            gate.collectColliders(area, out);
        }
    }

    public static void collectOriented(Level level, AABB area, List<OrientedBox> out, @Nullable GateEntity ignore) {
        List<AABB> aligned = new ArrayList<>();
        for (GateEntity gate : nearby(level, area, ignore)) {
            aligned.clear();
            gate.collectOrientedColliders(area, aligned, out);
            for (AABB box : aligned) {
                out.add(OrientedBox.ofBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
            }
        }
    }

    public static List<VoxelShape> append(Entity entity, Level level, AABB area, List<VoxelShape> shapes) {
        if (!blocks(entity)) {
            return shapes;
        }
        List<AABB> boxes = new ArrayList<>();
        if (entity instanceof QuadcopterEntity) {
            List<OrientedBox> ignored = new ArrayList<>();
            for (GateEntity gate : nearby(level, area, null)) {
                gate.collectOrientedColliders(area, boxes, ignored);
                ignored.clear();
            }
        } else {
            collect(level, area, boxes);
        }
        if (boxes.isEmpty()) {
            return shapes;
        }
        List<VoxelShape> combined = new ArrayList<>(shapes.size() + boxes.size());
        combined.addAll(shapes);
        for (AABB box : boxes) {
            combined.add(Shapes.create(box));
        }
        return combined;
    }
}
