package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.drone.DroneScale;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public class DroneRenderState extends EntityRenderState {
    public static final int MAX_STRIP_LEDS = AccessoryDefinition.MAX_LEDS;
    public static final Shape[] NO_SHAPES = new Shape[0];

    public final Quaternionf orientation = new Quaternionf();
    public final Vector3f pivot = new Vector3f();
    public float originY;
    public float lowestAboveFoot;
    public float groundOffset;
    public float poseScale = DroneVisualScale.MODEL_UNIT_SCALE * DroneRenderConfig.DEFAULT_VISUAL_SCALE;
    public float visualScale = DroneRenderConfig.DEFAULT_VISUAL_SCALE;
    public float visualRadius = 0.25F;
    public float entityScale = DroneScale.NORMAL;
    public DroneBuild build = DroneBuild.FREESTYLE;
    public boolean armed;
    public boolean preview;
    public boolean hidden;
    public boolean shadowPass;
    public float cameraTiltDegrees;
    public float batteryCharge = 1.0F;
    public boolean batteryMissing;
    public PartWear wear = PartWear.PRISTINE;
    public DronePaint paint = DronePaint.DEFAULT;
    public @Nullable FpvPropGeometry fpvGeometry;
    public FpvGuardMesh fpvGuards = FpvGuardMesh.EMPTY;
    public FpvGuardMesh fpvArms = FpvGuardMesh.EMPTY;
    public final FpvGuardMesh[] fpvMotors = new FpvGuardMesh[DroneLayout.SLOTS];

    public final PartSlot frame = new PartSlot();
    public final PartSlot stack = new PartSlot();
    public final PartSlot battery = new PartSlot();
    public final PartSlot video = new PartSlot();
    public final PartSlot transmitter = new PartSlot();
    public final PartSlot antenna = new PartSlot();
    public final PartSlot[] motors = new PartSlot[DroneLayout.SLOTS];
    public final PartSlot[] props = new PartSlot[DroneLayout.SLOTS];
    public final float[] rotorAngle = new float[DroneLayout.SLOTS];
    public final boolean[] propBlur = new boolean[DroneLayout.SLOTS];
    public final float[] motorOutput = new float[DroneLayout.SLOTS];
    public final int[] propVariants = new int[DroneLayout.SLOTS];
    public final int[] ledColor = new int[DroneLayout.SLOTS];
    public Shape leds = Shape.EMPTY;
    public final Shape[] ledGlow = new Shape[DroneLayout.SLOTS];
    public final Shape[] ledHalo = new Shape[DroneLayout.SLOTS];
    public float ledShiftY;
    public Shape motorGlow = Shape.EMPTY;
    public final int[] motorGlowColor = new int[DroneLayout.SLOTS];
    public final AccessoryType[] accessoryType = new AccessoryType[DroneAssembly.MOUNTS];
    public final PartSlot[] accessories = new PartSlot[DroneAssembly.MOUNTS];
    public final PartSlot[] accessoryBases = new PartSlot[DroneAssembly.MOUNTS];
    public final float[] accessoryTilt = new float[DroneAssembly.MOUNTS];
    public final float[] accessoryStretch = new float[DroneAssembly.MOUNTS];
    public int stripMount = -1;
    public final ItemStackRenderState cargo = new ItemStackRenderState();
    public boolean hasCargo;
    public int stripLeds;
    public Shape[] stripGlow = NO_SHAPES;
    public Shape[] stripHalo = NO_SHAPES;
    public final int[] stripColor = new int[MAX_STRIP_LEDS];
    public final PowerLead.Slot powerLead = new PowerLead.Slot();
    public final AccessoryDisplayState accessoryDisplay = new AccessoryDisplayState();

    public DroneRenderState() {
        for (int i = 0; i < DroneAssembly.MOUNTS; i++) {
            this.accessories[i] = new PartSlot();
            this.accessoryBases[i] = new PartSlot();
            this.accessoryStretch[i] = 1.0F;
        }
        for (int i = 0; i < DroneLayout.SLOTS; i++) {
            this.motors[i] = new PartSlot();
            this.props[i] = new PartSlot();
            this.fpvMotors[i] = FpvGuardMesh.EMPTY;
            this.ledGlow[i] = Shape.EMPTY;
            this.ledHalo[i] = Shape.EMPTY;
        }
    }

    public static final class PartSlot {
        public static final ItemStackRenderState NO_ITEM = new ItemStackRenderState();

        public ItemStackRenderState item = NO_ITEM;
        public Vector3fc position = DroneAssembly.ORIGIN;
        public Shape solid = Shape.EMPTY;
        public Shape translucent = Shape.EMPTY;
        public boolean useItem;

        public void set(DroneAssembly.Part part) {
            this.item = part.item() != null ? part.item() : NO_ITEM;
            this.useItem = part.item() != null;
            this.solid = part.solid();
            this.translucent = part.translucent();
            this.position = part.position();
        }
    }
}
