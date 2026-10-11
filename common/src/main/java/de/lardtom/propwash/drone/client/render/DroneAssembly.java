package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.sim.FpvRig;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class DroneAssembly {
    public static final Vector3fc ORIGIN = new Vector3f();
    public static final int MOUNTS = AccessoryMount.values().length;

    private final DroneBuild build;
    private final float cameraTiltDegrees;
    private final Part frame;
    private final Part stack;
    private final Part battery;
    private final Part video;
    private final Part transmitter;
    private final Part[] motors = new Part[DroneLayout.SLOTS];
    private final Part[] props = new Part[DroneLayout.SLOTS];
    private final Part[][] damagedProps = new Part[PropDamage.values().length][DroneLayout.SLOTS];
    private final Part[] blurs = new Part[DroneLayout.SLOTS];
    private final Shape[] ledGlow = new Shape[DroneLayout.SLOTS];
    private final Shape[] ledHalo = new Shape[DroneLayout.SLOTS];
    private final Shape leds;
    private final float ledShiftY;
    private final Shape motorGlow;
    private final Accessory[] accessories = new Accessory[MOUNTS];
    private final VisualHull hull;

    private DroneAssembly(DroneBuild build, DroneRenderResources res) {
        FallbackShapes.invalidateIfStale(res.generation());
        FrameType frameType = build.frame();
        DroneLayout layout = res.layout(frameType);
        this.build = build;
        this.cameraTiltDegrees = layout.cameraTiltDegrees();

        this.frame = part(res, PartModel.of(frameType), ORIGIN, () -> FallbackShapes.frame(frameType, layout));
        this.stack = part(res, PartModel.of(build.stack()), layout.stack(), () -> FallbackShapes.stack(build.stack()));
        this.battery = batteryPart(res, layout, build.battery());
        this.video = part(res, PartModel.of(build.video()), layout.video(), () -> FallbackShapes.camera(build.video()));
        if (this.video.item() != null) {
            this.transmitter = Part.EMPTY;
        } else {
            float stackHeight = FallbackShapes.stackHeight(build.stack());
            float stackTop = this.stack.item() != null ? res.top(PartModel.of(build.stack()), stackHeight) : stackHeight;
            Vector3f position = new Vector3f(layout.stack()).add(0.0F, stackTop + 0.5F, 0.0F);
            this.transmitter = new Part(null, FallbackShapes.transmitter(build.video()), Shape.EMPTY, position);
        }

        PropType prop = build.prop();
        PartModel blurModel = PartModel.blur(prop);
        ItemStackRenderState blurItem = res.item(blurModel);
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            Vector3fc motorPosition = layout.motor(slot);
            this.motors[slot] = part(res, PartModel.of(build.motor()), motorPosition, () -> FallbackShapes.motor(build.motor()));
            float propHeight = propHeight(res, layout, build, this.motors[slot].item() != null);
            Vector3f hub = new Vector3f(motorPosition).add(0.0F, propHeight, 0.0F);
            this.props[slot] = propPart(res, prop, layout.counterClockwise(slot), hub);
            this.damagedProps[PropDamage.NONE.ordinal()][slot] = this.props[slot];
            for (PropDamage damage : PropDamage.values()) {
                if (damage != PropDamage.NONE) {
                    this.damagedProps[damage.ordinal()][slot] = damagedProp(res, prop, layout.counterClockwise(slot), damage, hub, this.props[slot]);
                }
            }
            this.blurs[slot] = blurItem != null ? new Part(blurItem, Shape.EMPTY, Shape.EMPTY, hub, blurModel)
                    : new Part(null, Shape.EMPTY, FallbackShapes.blur(prop), hub);
            this.ledGlow[slot] = FallbackShapes.ledGlow(frameType, layout, slot);
            this.ledHalo[slot] = FallbackShapes.ledHalo(frameType, layout, slot);
        }

        boolean fallbackFrame = this.frame.item() == null;
        this.leds = fallbackFrame ? FallbackShapes.leds(frameType, layout) : Shape.EMPTY;
        this.ledShiftY = fallbackFrame ? 0.0F : ledShift(res, frameType, layout);
        this.motorGlow = FallbackShapes.motorGlow(build.motor());
        for (AccessoryMount mount : AccessoryMount.values()) {
            AccessoryType type = build.accessories().at(mount).orElse(null);
            this.accessories[mount.ordinal()] = type == null ? Accessory.NONE : accessory(res, layout, frameType, mount, type);
        }
        this.hull = VisualHull.compute(this);
    }

    static DroneAssembly create(DroneBuild build, DroneRenderResources res) {
        return new DroneAssembly(build, res);
    }

    private static Accessory accessory(DroneRenderResources res, DroneLayout layout, FrameType frame, AccessoryMount mount, AccessoryType type) {
        AccessoryAnchor anchor = layout.accessory(mount).orElseGet(() -> AccessoryAnchor.of(frame, mount));
        Vector3f position = new Vector3f((float) anchor.x(), (float) anchor.y(), (float) anchor.z());
        Part body = part(res, PartModel.of(type), position, () -> FallbackShapes.accessory(type));
        Part base = PartModel.base(mount).map(model -> part(res, model, position, () -> FallbackShapes.accessoryBase(mount))).orElse(Part.EMPTY);
        return new Accessory(type, anchor, body, base, (float) anchor.tiltFor(type), (float) anchor.stretch(type));
    }

    private static Part part(DroneRenderResources res, PartModel model, Vector3fc position, Supplier<Shape> fallback) {
        ItemStackRenderState item = res.item(model);
        return item != null ? new Part(item, Shape.EMPTY, Shape.EMPTY, new Vector3f(position), model)
                : new Part(null, fallback.get(), Shape.EMPTY, new Vector3f(position));
    }

    private static Part propPart(DroneRenderResources res, PropType prop, boolean counterClockwise, Vector3fc hub) {
        PartModel model = PartModel.prop(prop, counterClockwise);
        ItemStackRenderState item = res.item(model);
        if (item == null && counterClockwise) {
            model = PartModel.of(prop);
            item = res.item(model);
        }
        return item != null ? new Part(item, Shape.EMPTY, Shape.EMPTY, hub, model)
                : new Part(null, FallbackShapes.prop(prop, counterClockwise), Shape.EMPTY, hub);
    }

    private static Part damagedProp(DroneRenderResources res, PropType prop, boolean counterClockwise, PropDamage damage, Vector3fc hub,
                                    Part intact) {
        PartModel model = PartModel.prop(prop, counterClockwise, damage);
        ItemStackRenderState item = res.item(model);
        return item != null ? new Part(item, Shape.EMPTY, Shape.EMPTY, hub, model) : intact;
    }

    static Part batteryPart(DroneRenderResources res, DroneLayout layout, BatteryType battery) {
        Supplier<Shape> fallback = () -> FallbackShapes.battery(battery);
        if (layout.batteryBelow()) {
            PartModel under = PartModel.batteryUnder(battery);
            ItemStackRenderState item = res.hasModel(under) ? res.item(under) : null;
            if (item != null) {
                return new Part(item, Shape.EMPTY, Shape.EMPTY, new Vector3f(layout.battery()), under);
            }
        }
        return part(res, PartModel.of(battery), batteryPosition(res, layout, battery), fallback);
    }

    static Vector3fc batteryPosition(DroneRenderResources res, DroneLayout layout, BatteryType battery) {
        Vector3fc anchor = layout.battery();
        if (!layout.batteryBelow()) {
            return anchor;
        }
        PartModel model = PartModel.of(battery);
        float procedural = FallbackShapes.batteryDimensions(battery)[1];
        float height = res.hasModel(model) ? res.top(model, procedural) : procedural;
        return hangBelow(anchor, height);
    }

    static Vector3f hangBelow(Vector3fc anchor, float packTop) {
        return new Vector3f(anchor.x(), anchor.y() - Math.max(1.0F, packTop), anchor.z());
    }

    static float propHeight(DroneRenderResources res, DroneLayout layout, DroneBuild build, boolean motorModel) {
        boolean fromLayout = layout.hasPropHeight();
        float seat = res.motorSeat(build.motor());
        float presetSeat = fromLayout ? res.motorSeat(DroneLayout.typicalMotor(build.frame())) : Float.NaN;
        float top = !fromLayout && !Float.isFinite(seat) && motorModel ? res.top(PartModel.of(build.motor()), Float.NaN) : Float.NaN;
        return (float) FpvRig.propHeightMm(fromLayout ? layout.propHeight() : Double.NaN, seat, presetSeat, top, build.motor());
    }

    private static float ledShift(DroneRenderResources res, FrameType frame, DroneLayout layout) {
        float[] led = FallbackShapes.ledBox(frame, layout, DroneLayout.FRONT_LEFT);
        float bottom = res.bottom(PartModel.of(frame), Float.NaN);
        if (led == null || !Float.isFinite(bottom)) {
            return 0.0F;
        }
        return bottom - (led[1] + led[4] * 0.5F);
    }

    public DroneBuild build() {
        return this.build;
    }

    public float cameraTiltDegrees() {
        return this.cameraTiltDegrees;
    }

    public Part frame() {
        return this.frame;
    }

    public Part stack() {
        return this.stack;
    }

    public Part battery() {
        return this.battery;
    }

    public Part video() {
        return this.video;
    }

    public Part transmitter() {
        return this.transmitter;
    }

    public Part motor(int slot) {
        return this.motors[slot];
    }

    public Part prop(int slot) {
        return this.props[slot];
    }

    public Part prop(int slot, PropDamage damage) {
        return this.damagedProps[damage.ordinal()][slot];
    }

    public Part blur(int slot) {
        return this.blurs[slot];
    }

    public Shape ledGlow(int slot) {
        return this.ledGlow[slot];
    }

    public Shape ledHalo(int slot) {
        return this.ledHalo[slot];
    }

    public Shape leds() {
        return this.leds;
    }

    public float ledShiftY() {
        return this.ledShiftY;
    }

    public Shape motorGlow() {
        return this.motorGlow;
    }

    public VisualHull hull() {
        return this.hull;
    }

    public Accessory accessory(AccessoryMount mount) {
        return this.accessories[mount.ordinal()];
    }

    public record Accessory(@Nullable AccessoryType type, @Nullable AccessoryAnchor anchor, Part body, Part base, float tiltDegrees,
                            float stretchX) {
        public static final Accessory NONE = new Accessory(null, null, Part.EMPTY, Part.EMPTY, 0.0F, 1.0F);

        public boolean present() {
            return this.type != null;
        }
    }

    public record Part(@Nullable ItemStackRenderState item, Shape solid, Shape translucent, Vector3fc position, @Nullable PartModel model) {
        public static final Part EMPTY = new Part(null, Shape.EMPTY, Shape.EMPTY, ORIGIN, null);

        public Part(@Nullable ItemStackRenderState item, Shape solid, Shape translucent, Vector3fc position) {
            this(item, solid, translucent, position, null);
        }
    }
}
