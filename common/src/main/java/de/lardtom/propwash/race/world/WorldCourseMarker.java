package de.lardtom.propwash.race.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public record WorldCourseMarker(String variant, int slot, Vec3 local, float localYaw, BlockPos board) {
    public static final Codec<WorldCourseMarker> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("Variant").forGetter(WorldCourseMarker::variant),
            Codec.INT.fieldOf("Slot").forGetter(WorldCourseMarker::slot),
            Vec3.CODEC.fieldOf("Local").forGetter(WorldCourseMarker::local),
            Codec.FLOAT.fieldOf("LocalYaw").forGetter(WorldCourseMarker::localYaw),
            BlockPos.CODEC.fieldOf("Board").forGetter(WorldCourseMarker::board)
    ).apply(i, WorldCourseMarker::new));

    public WorldCourseSite site(Vec3 worldPosition, float worldYaw) {
        return WorldCourseSite.locate(this.local.x, this.local.y, this.local.z, this.localYaw,
                worldPosition.x, worldPosition.y, worldPosition.z, worldYaw);
    }
}
