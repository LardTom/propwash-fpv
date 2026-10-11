package de.lardtom.propwash.guide.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.FreeformRegion;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.client.GateRenderState;
import de.lardtom.propwash.race.client.GateRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SceneGateVisual {
    private final GateRenderState state = new GateRenderState();
    private final CameraRenderState camera = new CameraRenderState();
    private final Map<Integer, ItemStack> stacks = new HashMap<>();

    static GateGeometry geometry(ActorSpec.Gate gate, ActorState actor, double x, double y, double z) {
        if (gate.isFreeform()) {
            FreeformRegion region = FreeformRegion.decode(gate.region());
            return GateGeometry.freeform(x, y, z, region == null ? FreeformRegion.single(FreeformRegion.AXIS_Z) : region, gate.variant());
        }
        if (gate.isElement()) {
            CourseElement kind = CourseElement.byKey(gate.element());
            double width = kind.outerWidth(gate.openingWidth() > 0.0F ? gate.openingWidth() : kind.defaultWidth());
            double height = kind.outerHeight(gate.openingHeight() > 0.0F ? gate.openingHeight() : kind.defaultHeight());
            return GateGeometry.element(x, y, z, actor.yaw(), kind, gate.variant(), width, height, GateTilt.NONE);
        }
        GateSize size = GateSize.byKey(gate.size());
        GateMount mount = GateMount.byKey(gate.mount());
        GateTilt tilt = new GateTilt(0.0F, actor.pitch(), actor.roll());
        if (tilt.none() && !gate.customSize()) {
            return GateGeometry.of(x, y, z, actor.yaw(), size, mount, gate.flipped());
        }
        return GateGeometry.of(x, y, z, actor.yaw(), size, mount, gate.flipped(), gate.outerWidth(size.width()), gate.outerHeight(size.height()), tilt);
    }

    void prepare(ActorSpec.Gate gate, ActorState actor, GateStacking.Base base, boolean covered) {
        this.prepare(gate, actor, base, covered, 0.0F);
    }

    void prepare(ActorSpec.Gate gate, ActorState actor, GateStacking.Base base, boolean covered, float time) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemModelResolver items = minecraft.getItemModelResolver();
        GateRenderState s = this.state;
        GateSize size = GateSize.byKey(gate.size());
        GateMount mount = GateMount.byKey(gate.mount());
        int lightColor = actor.lit() ? GateRenderer.LIGHT_GREEN : GateRenderer.LIGHT_WHITE;
        s.entityType = RaceModule.GATE.get();
        s.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        s.outlineColor = 0;
        s.nameTag = null;
        s.shadowPieces.clear();
        GateRenderer renderer = GateRenderer.current();
        if (renderer != null && gate.isFreeform()) {
            s.distanceToCameraSq = 0.0;
            s.ageInTicks = time;
            renderer.fill(s, geometry(gate, actor, 0.0, 0.0, 0.0), null, GateRenderer.Marking.NONE, 0, lightColor, actor.lit() ? 0.7F : 0.0F);
            return;
        }
        if (renderer != null) {
            renderer.fill(s, geometry(gate, actor, 0.0, 0.0, 0.0), size, base, covered, GateRenderer.Marking.NONE, 0, lightColor, minecraft.level);
            return;
        }
        s.size = size;
        s.yaw = actor.yaw();
        s.mount = mount;
        s.flipped = GateMount.normalizeFlip(mount, gate.flipped());
        GateRenderer.rotation(s.rotation, GateGeometry.of(0.0, 0.0, 0.0, actor.yaw(), size, mount, s.flipped));
        s.lightColor = lightColor;
        s.marking = GateRenderer.Marking.NONE;
        s.label = null;
        s.labelWidth = 0;
        s.plate.clear();
        s.hanger.clear();
        s.startFrame.clear();
        s.banner.clear();
        s.startModelled = false;
        s.base = base;
        s.covered = covered;
        items.updateForTopItem(s.frame, this.stack(size, false, GateStacking.Base.BAR, 0), ItemDisplayContext.NONE, minecraft.level, null, 0);
        s.modelled = !s.frame.isEmpty() && this.fit(size);
        if (base != GateStacking.Base.BAR) {
            items.updateForTopItem(s.frame, this.stack(size, false, base, 0), ItemDisplayContext.NONE, minecraft.level, null, 0);
            s.modelled &= !s.frame.isEmpty();
        }
        items.updateForTopItem(s.lights, this.stack(size, true, base, s.lightColor), ItemDisplayContext.NONE, minecraft.level, null, 0);
        if (!s.modelled) {
            s.frame.clear();
            s.lights.clear();
        }
    }

    private boolean fit(GateSize size) {
        AABB box = this.state.frame.getModelBoundingBox();
        double sizeX = box.getXsize();
        double sizeY = box.getYsize();
        double sizeZ = box.getZsize();
        if (sizeY <= 1.0E-4 || Math.max(sizeX, sizeZ) <= 1.0E-4) {
            return false;
        }
        boolean rotate = sizeZ > sizeX * 1.5;
        double width = rotate ? sizeZ : sizeX;
        GateRenderState s = this.state;
        s.fitScale = (float) Math.min(size.width() / width, size.height() / sizeY);
        s.fitX = (float) ((box.minX + box.maxX) * 0.5);
        s.fitBottom = (float) box.minY;
        s.fitZ = (float) ((box.minZ + box.maxZ) * 0.5);
        s.fitRotate = rotate;
        return true;
    }

    private ItemStack stack(GateSize size, boolean lights, GateStacking.Base base, int color) {
        int rgb = color & 0xFFFFFF;
        int key = (size.ordinal() << 28) | (base.ordinal() << 26) | (lights ? 1 << 25 : 0) | (lights ? rgb : 0);
        return this.stacks.computeIfAbsent(key, k -> {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.ITEM_MODEL, lights ? GateRenderer.lightsModelId(size, base) : GateRenderer.frameModelId(size, base));
            if (lights) {
                stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(rgb)));
            }
            return stack;
        });
    }

    void submit(ActorState actor, PoseStack ps, SubmitNodeCollector collector) {
        ScenePos pos = actor.position();
        float presence = actor.presence();
        ps.translate(pos.x(), pos.y(), pos.z());
        ps.scale(presence, presence, presence);
        Minecraft.getInstance().getEntityRenderDispatcher().submit(this.state, this.camera, 0.0, 0.0, 0.0, ps, collector);
    }
}
