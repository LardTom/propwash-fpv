package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public final class GateRenderState extends EntityRenderState {
    public final ItemStackRenderState frame = new ItemStackRenderState();
    public final ItemStackRenderState lights = new ItemStackRenderState();
    public final ItemStackRenderState startFrame = new ItemStackRenderState();
    public final ItemStackRenderState banner = new ItemStackRenderState();
    public final ItemStackRenderState plate = new ItemStackRenderState();
    public final ItemStackRenderState hanger = new ItemStackRenderState();
    public final ItemStackRenderState[] pieces = new ItemStackRenderState[GateRenderer.Piece.values().length];
    public final ItemStackRenderState[] elementPieces = new ItemStackRenderState[CourseElementRenderer.Piece.values().length];
    public CourseElement kind = CourseElement.GATE;
    public int variant;
    public float elementFrame = 0.25F;
    public float bannerHeight;
    public final Quaternionf rotation = new Quaternionf();
    public final Quaternionf chevronRotation = new Quaternionf();
    public boolean levelChevrons;
    public float chevronX;
    public float chevronY;
    public float chevronZ;
    public final float[] strapX = new float[2];
    public final float[] strapY = new float[2];
    public final float[] strapZ = new float[2];
    public final float[] strapLength = new float[2];
    public int straps;
    public GateSize size = GateSize.MEDIUM;
    public float width = GateSize.MEDIUM.width();
    public float height = GateSize.MEDIUM.height();
    public float offsetX;
    public float offsetY;
    public float offsetZ;
    public boolean tiled;
    public GateTilt tilt = GateTilt.NONE;
    public float yaw;
    public GateMount mount = GateMount.STANDING;
    public boolean flipped;
    public int lightColor = GateRenderer.LIGHT_WHITE;
    public boolean modelled;
    public boolean startModelled;
    public boolean covered;
    public GateStacking.Base base = GateStacking.Base.BAR;
    public float fitScale = 1.0F;
    public float fitX;
    public float fitBottom;
    public float fitZ;
    public boolean fitRotate;
    public GateRenderer.Marking marking = GateRenderer.Marking.NONE;
    public int number;
    public @Nullable FormattedCharSequence label;
    public int labelWidth;
    public @Nullable FreeformView freeform;
    public float freeformSign = 1.0F;
    public float freeformFill;

    public GateRenderState() {
        for (int i = 0; i < this.pieces.length; i++) {
            this.pieces[i] = new ItemStackRenderState();
        }
        for (int i = 0; i < this.elementPieces.length; i++) {
            this.elementPieces[i] = new ItemStackRenderState();
        }
    }
}
