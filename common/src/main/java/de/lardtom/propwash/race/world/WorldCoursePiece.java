package de.lardtom.propwash.race.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class WorldCoursePiece extends TemplateStructurePiece {
    private static final String ROTATION = "Rot";

    public WorldCoursePiece(StructureTemplateManager manager, Identifier template, BlockPos origin, Rotation rotation) {
        super(WorldCourses.PIECE.get(), 0, manager, template, template.toString(), settings(rotation), origin);
    }

    public WorldCoursePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(WorldCourses.PIECE.get(), tag, manager, id -> settings(tag.read(ROTATION, Rotation.LEGACY_CODEC).orElse(Rotation.NONE)));
    }

    public static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setRotationPivot(BlockPos.ZERO)
                .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.store(ROTATION, Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
    }

    @Override
    protected void handleDataMarker(String markerId, BlockPos position, ServerLevelAccessor level, RandomSource random, BoundingBox chunkBB) {
    }
}
