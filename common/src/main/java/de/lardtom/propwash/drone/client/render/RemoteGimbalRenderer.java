package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.content.client.ThemedPanel;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.rescue.client.RescueState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public class RemoteGimbalRenderer implements SpecialModelRenderer<RemoteGimbalRenderer.Pose> {
    public static final Identifier TYPE = Constants.id("remote_gimbals");
    public static final Identifier TEXTURE = Constants.id("textures/entity/remote/gimbal.png");
    public static final Identifier FALLBACK_TEXTURE = Identifier.withDefaultNamespace("textures/block/gray_concrete.png");
    public static final Pose NEUTRAL = new Pose(0.0F, -1.0F, 0.0F, 0.0F);
    private static final float STEPS = 32.0F;

    private static @Nullable ItemStack posedStack;
    private static @Nullable Pose posedPose;

    private final RemoteGimbalModel model;

    public RemoteGimbalRenderer(RemoteGimbalModel model) {
        this.model = model;
    }

    public record Pose(float leftX, float leftY, float rightX, float rightY, Controls controls) {
        public Pose(float leftX, float leftY, float rightX, float rightY) {
            this(leftX, leftY, rightX, rightY, Controls.NEUTRAL);
        }

        public Pose {
            controls = controls == null ? Controls.NEUTRAL : controls;
        }

        public static Pose of(StickPositions sticks) {
            StickPositions s = sticks.clamped();
            return new Pose(quantize(s.yaw()), quantize(s.throttle() * 2.0F - 1.0F), quantize(s.roll()), quantize(s.pitch()));
        }
    }

    public record Controls(float switch0, float switch1, float switch2, float switch3, float button0, float button1, float button2) {
        public static final Controls NEUTRAL = new Controls(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        public float switchPosition(int index) {
            return switch (index) {
                case 0 -> this.switch0;
                case 1 -> this.switch1;
                case 2 -> this.switch2;
                case 3 -> this.switch3;
                default -> 0.0F;
            };
        }

        public float buttonPress(int index) {
            return switch (index) {
                case 0 -> this.button0;
                case 1 -> this.button1;
                case 2 -> this.button2;
                default -> 0.0F;
            };
        }
    }

    public static void extractPosed(ItemStack stack, Pose pose, Runnable extract) {
        posedStack = stack;
        posedPose = pose;
        try {
            extract.run();
        } finally {
            posedStack = null;
            posedPose = null;
        }
    }

    static float quantize(float value) {
        if (Float.isNaN(value)) {
            return 0.0F;
        }
        return Math.round(Math.max(-1.0F, Math.min(1.0F, value)) * STEPS) / STEPS;
    }

    @Override
    public void submit(@Nullable Pose argument, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
                       int overlayCoords, boolean hasFoil, int outlineColor) {
        Pose pose = argument != null ? argument : NEUTRAL;
        Identifier texture = ThemedPanel.available(TEXTURE) ? TEXTURE : FALLBACK_TEXTURE;
        submitNodeCollector.submitModel(this.model, pose, poseStack, texture, lightCoords, overlayCoords, outlineColor);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        this.model.setupAnim(NEUTRAL);
        this.model.root().getExtentsForGui(new PoseStack(), output);
    }

    @Override
    public @Nullable Pose extractArgument(ItemStack stack) {
        if (posedStack != null && stack == posedStack) {
            return posedPose;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || stack != player.getMainHandItem()) {
            return null;
        }
        RescueState rescue = RescueState.INSTANCE;
        if (PilotSession.INSTANCE.isActive() && rescue.active()) {
            return Pose.of(rescue.commandedSticks());
        }
        RadioState radio = RadioState.INSTANCE;
        if (!radio.connected()) {
            return null;
        }
        return Pose.of(radio.sticks());
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Pose> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public @Nullable SpecialModelRenderer<Pose> bake(SpecialModelRenderer.BakingContext context) {
            return new RemoteGimbalRenderer(new RemoteGimbalModel(context.entityModelSet().bakeLayer(RemoteGimbalModel.LAYER)));
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
