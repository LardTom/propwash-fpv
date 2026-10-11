package de.lardtom.propwash.race.world;

import com.mojang.serialization.MapCodec;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import de.lardtom.propwash.server.ServerRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class WorldCourses {
    public static final TagKey<Structure> TAG = TagKey.create(Registries.STRUCTURE, Constants.id("world_courses"));

    public static final RegHolder<StructureType<WorldCourseStructure>> STRUCTURE_TYPE = PropwashRegistries.list(Registries.STRUCTURE_TYPE)
            .<StructureType<WorldCourseStructure>>add("world_course", key -> () -> WorldCourseStructure.CODEC);
    public static final RegHolder<MapCodec<WorldCoursePlacement>> PLACEMENT = PropwashRegistries.list(Registries.STRUCTURE_PLACEMENT)
            .<MapCodec<WorldCoursePlacement>>add("world_course", key -> WorldCoursePlacement.CODEC);
    public static final RegHolder<StructurePieceType> PIECE = PropwashRegistries.list(Registries.STRUCTURE_PIECE)
            .<StructurePieceType>add("world_course", key -> (StructurePieceType.StructureTemplateType) WorldCoursePiece::new);

    private WorldCourses() {
    }

    public static void init() {
    }

    public static boolean generationEnabled() {
        return ServerRules.worldCourses();
    }

    public static boolean adopt(CourseRegistry registry, GateEntity gate, WorldCourseMarker marker) {
        String dimension = CourseRegistry.dimensionOf(gate.level());
        WorldCourseSite site = marker.site(gate.position(), gate.facingYaw());
        CourseRegistry.Course course = registry.worldCourse(dimension, site.key());
        if (course == null) {
            int[] board = site.blockToWorld(marker.board().getX(), marker.board().getY(), marker.board().getZ());
            String variant = WorldCourseVariant.byKey(marker.variant()).map(WorldCourseVariant::key).orElse(marker.variant());
            int id = registry.createWorldCourse(dimension, variant, site.key(), board);
            course = registry.course(id);
            if (course == null) {
                return false;
            }
        }
        if (registry.insertGate(course.id, gate, marker.slot()) == GateEntity.NO_INDEX) {
            return false;
        }
        gate.clearWorldCourse();
        registry.refreshBoard(course);
        return true;
    }
}
