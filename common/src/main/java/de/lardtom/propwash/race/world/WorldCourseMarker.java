package de.lardtom.propwash.race.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.race.CourseOrder;
import de.lardtom.propwash.race.PointToPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public record WorldCourseMarker(String variant, int slot, Vec3 local, float localYaw, BlockPos board, String passText, String modeKey) {
    public static final Codec<WorldCourseMarker> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("Variant").forGetter(WorldCourseMarker::variant),
            Codec.INT.fieldOf("Slot").forGetter(WorldCourseMarker::slot),
            Vec3.CODEC.fieldOf("Local").forGetter(WorldCourseMarker::local),
            Codec.FLOAT.fieldOf("LocalYaw").forGetter(WorldCourseMarker::localYaw),
            BlockPos.CODEC.fieldOf("Board").forGetter(WorldCourseMarker::board),
            Codec.STRING.optionalFieldOf("Passes", "").forGetter(WorldCourseMarker::passText),
            Codec.STRING.optionalFieldOf("Mode", "").forGetter(WorldCourseMarker::modeKey)
    ).apply(i, WorldCourseMarker::new));

    public WorldCourseMarker(String variant, int slot, Vec3 local, float localYaw, BlockPos board) {
        this(variant, slot, local, localYaw, board, "", "");
    }

    public WorldCourseSite site(Vec3 worldPosition, float worldYaw) {
        return WorldCourseSite.locate(this.local.x, this.local.y, this.local.z, this.localYaw,
                worldPosition.x, worldPosition.y, worldPosition.z, worldYaw);
    }

    public List<CourseOrder.Pass> passes() {
        List<CourseOrder.Pass> passes = CourseOrder.decode(this.passText, Math.max(0, this.slot));
        return passes.isEmpty() ? List.of(new CourseOrder.Pass(Math.max(0, this.slot), false)) : passes;
    }

    public CourseMode mode() {
        return PointToPoint.read(this.modeKey.isEmpty() ? null : this.modeKey);
    }
}
