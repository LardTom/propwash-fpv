package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.Constants;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public class RemoteGimbalModel extends Model<RemoteGimbalRenderer.Pose> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Constants.id("remote_gimbals"), "main");
    public static final float LEFT_X = 4.0F;
    public static final float RIGHT_X = 12.0F;
    public static final float PIVOT_Y = 6.5F;
    public static final float PIVOT_Z = 10.25F;
    public static final float STICK_LENGTH = 3.0F;
    public static final float MAX_TILT_RADIANS = (float) Math.toRadians(25.0);
    public static final float[] SWITCH_X = {2.5F, 4.0F, 12.0F, 13.5F};
    public static final float SWITCH_Y = 10.0F;
    public static final float SWITCH_Z = 9.25F;
    public static final float SWITCH_LENGTH = 1.0F;
    public static final float SWITCH_TILT_RADIANS = (float) Math.toRadians(40.0);
    public static final float[] BUTTON_X = {7.0F, 8.5F, 10.0F};
    public static final float BUTTON_Y = 4.5F;
    public static final float BUTTON_Z = 10.0F;
    public static final float BUTTON_TRAVEL = 0.22F;

    private final ModelPart left;
    private final ModelPart right;
    private final ModelPart[] switches = new ModelPart[SWITCH_X.length];
    private final ModelPart[] buttons = new ModelPart[BUTTON_X.length];

    public RemoteGimbalModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        this.left = root.getChild("gimbal_left");
        this.right = root.getChild("gimbal_right");
        for (int i = 0; i < this.switches.length; i++) {
            this.switches[i] = root.getChild("switch_" + i);
        }
        for (int i = 0; i < this.buttons.length; i++) {
            this.buttons[i] = root.getChild("button_" + i);
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("gimbal_left", stick(), PartPose.offset(LEFT_X, PIVOT_Y, PIVOT_Z));
        root.addOrReplaceChild("gimbal_right", stick(), PartPose.offset(RIGHT_X, PIVOT_Y, PIVOT_Z));
        for (int i = 0; i < SWITCH_X.length; i++) {
            root.addOrReplaceChild("switch_" + i, toggle(), PartPose.offset(SWITCH_X[i], SWITCH_Y, SWITCH_Z));
        }
        for (int i = 0; i < BUTTON_X.length; i++) {
            root.addOrReplaceChild("button_" + i, button(i == BUTTON_X.length - 1), PartPose.offset(BUTTON_X[i], BUTTON_Y, BUTTON_Z));
        }
        return LayerDefinition.create(mesh, 16, 16);
    }

    private static CubeListBuilder stick() {
        return CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.25F, -0.25F, 0.0F, 0.5F, 0.5F, 2.25F)
                .texOffs(0, 8).addBox(-0.5F, -0.5F, 2.25F, 1.0F, 1.0F, 0.75F);
    }

    private static CubeListBuilder toggle() {
        return CubeListBuilder.create()
                .texOffs(6, 0).addBox(-0.25F, 0.0F, -0.25F, 0.5F, 0.5F, 0.5F)
                .texOffs(6, 2).addBox(-0.25F, 0.5F, -0.25F, 0.5F, 0.5F, 0.5F);
    }

    private static CubeListBuilder button(boolean amber) {
        return CubeListBuilder.create()
                .texOffs(amber ? 12 : 9, 0).addBox(-0.5F, -0.5F, -0.05F, 1.0F, 1.0F, 0.3F);
    }

    @Override
    public void setupAnim(RemoteGimbalRenderer.Pose pose) {
        super.setupAnim(pose);
        tilt(this.left, pose.leftX(), pose.leftY());
        tilt(this.right, pose.rightX(), pose.rightY());
        RemoteGimbalRenderer.Controls controls = pose.controls();
        for (int i = 0; i < this.switches.length; i++) {
            this.switches[i].xRot = clamp(controls.switchPosition(i)) * SWITCH_TILT_RADIANS;
        }
        for (int i = 0; i < this.buttons.length; i++) {
            this.buttons[i].z = BUTTON_Z - Math.max(0.0F, clamp(controls.buttonPress(i))) * BUTTON_TRAVEL;
        }
    }

    private static void tilt(ModelPart part, float stickX, float stickY) {
        part.xRot = -clamp(stickY) * MAX_TILT_RADIANS;
        part.yRot = clamp(stickX) * MAX_TILT_RADIANS;
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) {
            return 0.0F;
        }
        return Math.max(-1.0F, Math.min(1.0F, value));
    }
}
