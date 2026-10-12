package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.ActionCamSpec;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.content.paint.PaintCodecs;
import de.lardtom.propwash.content.paint.PaintComponents;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.DroneBuildCodecs;
import de.lardtom.propwash.core.ExtraCollider;
import de.lardtom.propwash.core.OrientedBox;
import de.lardtom.propwash.core.OrientedSweep;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.core.WearCodecs;
import de.lardtom.propwash.core.event.Event;
import de.lardtom.propwash.drone.bind.BindCodecs;
import de.lardtom.propwash.drone.bind.BindComponents;
import de.lardtom.propwash.drone.bind.ReceiverBind;
import de.lardtom.propwash.drone.net.DroneStateC2S;
import de.lardtom.propwash.drone.net.MoveBudget;
import de.lardtom.propwash.drone.net.StateValidation;
import de.lardtom.propwash.drone.tag.DroneTagRules;
import de.lardtom.propwash.drone.tag.DroneTags;
import de.lardtom.propwash.interact.DroneCargo;
import de.lardtom.propwash.rescue.ReturnPlan;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class QuadcopterEntity extends Entity {
    public static final Event<Consumer<QuadcopterEntity>> CLIENT_TICK = new Event<>("drone_client_tick");
    public static final Event<PilotListener> PILOT_CHANGED = new Event<>("drone_pilot_changed");
    public static final Event<ServerMoveListener> SERVER_MOVED = new Event<>("drone_server_moved");

    public static final int MOTORS = 4;
    public static final int INTERPOLATION_STEPS = 2;
    public static final float BREAK_DAMAGE = 6.0F;
    public static final double GRAVITY = 0.08;
    public static final double GROUND_PROBE_RAISE = 0.02;
    public static final String RETURN_PLAN_KEY = "ReturnPlan";
    public static final String RECEIVER_BIND_KEY = "ReceiverBind";
    private static final float[] PROP_SPIN = {-1.0F, 1.0F, 1.0F, -1.0F};
    private static final float PROP_IDLE_SPEED = 0.9F;
    private static final float PROP_MAX_SPEED = 3.2F;
    private static final float PROP_RESPONSE = 0.35F;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private static final EntityDataAccessor<DroneBuild> DATA_BUILD =
            SynchedEntityData.defineId(QuadcopterEntity.class, CoreComponents.DRONE_BUILD_SERIALIZER);
    private static final EntityDataAccessor<Quaternionfc> DATA_ORIENTATION =
            SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Boolean> DATA_ARMED =
            SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_PILOT =
            SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    private static final EntityDataAccessor<Float> DATA_BATTERY =
            SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MOTOR_OUTPUT =
            SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<FlightTune> DATA_TUNE =
            SynchedEntityData.defineId(QuadcopterEntity.class, CoreComponents.FLIGHT_TUNE_SERIALIZER);
    private static final EntityDataAccessor<Integer> DATA_MOTORS =
            SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EPOCH =
            SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_PILOT_MODE =
            SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_HAS_BATTERY =
            SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_CAMERA_CHARGE =
            SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<PartWear> DATA_WEAR =
            SynchedEntityData.defineId(QuadcopterEntity.class, CoreComponents.PART_WEAR_SERIALIZER);
    private static final EntityDataAccessor<Boolean> DATA_LEDS_OFF =
            SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<DronePaint> DATA_PAINT =
            SynchedEntityData.defineId(QuadcopterEntity.class, PaintComponents.DRONE_PAINT_SERIALIZER);
    private static final EntityDataAccessor<Float> DATA_CARGO_GRAMS =
            SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<ItemStack> DATA_CARGO_ITEM =
            SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.ITEM_STACK);

    private static volatile Predicate<UUID> localPlayerCheck = uuid -> false;
    private static volatile Predicate<QuadcopterEntity> serverAutopilot = drone -> false;

    private long stateSentTick = Long.MIN_VALUE;
    private long lastRejectTick = Long.MIN_VALUE;
    private final MoveBudget moveBudget = new MoveBudget();
    private boolean physicsModified;
    private int pilotUpdates;
    private float damageTaken;
    private final DroneCargo.Hold cargo = new DroneCargo.Hold();
    private @Nullable ReturnPlan returnPlan;
    private @Nullable ReceiverBind receiverBind;

    private final Quaternionf renderAttitude = new Quaternionf();
    private final Quaternionf prevRenderOrientation = new Quaternionf();
    private final Quaternionf orientationTarget = new Quaternionf();
    private int orientationSteps;
    private boolean renderOrientationReady;
    private final float[] rotorAngle = new float[MOTORS];
    private final float[] prevPropAngle = new float[MOTORS];
    private final float[] propSpeed = new float[MOTORS];
    private boolean localState;
    private boolean localArmed;
    private float localBattery;
    private int localMotors;
    private boolean localLedsOff;
    private final Quaternionf settleScratch = new Quaternionf();
    private final OrientedSweep.Result sweepResult = new OrientedSweep.Result();
    private boolean obliqueContact;
    private Vec3 obliqueNormal = Vec3.ZERO;
    private Vec3 obliqueDelta = Vec3.ZERO;

    public QuadcopterEntity(EntityType<? extends QuadcopterEntity> type, Level level) {
        super(type, level);
        this.refreshDimensions();
    }

    public static void setLocalPlayerCheck(Predicate<UUID> check) {
        localPlayerCheck = Objects.requireNonNull(check, "check");
    }

    public static void setServerAutopilot(Predicate<QuadcopterEntity> autopilot) {
        serverAutopilot = Objects.requireNonNull(autopilot, "autopilot");
    }

    public @Nullable ReturnPlan returnPlan() {
        return this.returnPlan;
    }

    public void setReturnPlan(@Nullable ReturnPlan plan) {
        this.returnPlan = plan;
    }

    public boolean returningHome() {
        return this.returnPlan != null;
    }

    public @Nullable ReceiverBind receiverBind() {
        return this.receiverBind;
    }

    public void setReceiverBind(@Nullable ReceiverBind bind) {
        this.receiverBind = bind;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BUILD, DroneBuild.FREESTYLE);
        builder.define(DATA_ORIENTATION, new Quaternionf());
        builder.define(DATA_ARMED, false);
        builder.define(DATA_PILOT, Optional.empty());
        builder.define(DATA_BATTERY, 1.0F);
        builder.define(DATA_MOTOR_OUTPUT, 0.0F);
        builder.define(DATA_TUNE, FlightTune.EMPTY);
        builder.define(DATA_MOTORS, 0);
        builder.define(DATA_EPOCH, 0);
        builder.define(DATA_PILOT_MODE, (byte) PilotMode.NONE.ordinal());
        builder.define(DATA_HAS_BATTERY, true);
        builder.define(DATA_CAMERA_CHARGE, 1.0F);
        builder.define(DATA_WEAR, PartWear.PRISTINE);
        builder.define(DATA_LEDS_OFF, false);
        builder.define(DATA_PAINT, DronePaint.DEFAULT);
        builder.define(DATA_CARGO_GRAMS, 0.0F);
        builder.define(DATA_CARGO_ITEM, ItemStack.EMPTY);
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, INTERPOLATION_STEPS);
    }

    @Override
    protected boolean isLocalClientAuthoritative() {
        if (!this.level().isClientSide()) {
            return false;
        }
        Optional<UUID> pilot = this.pilot();
        return pilot.isPresent() && localPlayerCheck.test(pilot.get());
    }

    @Override
    public boolean isClientAuthoritative() {
        return this.pilot().isPresent();
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.tickClientVisuals();
            CLIENT_TICK.fire(listener -> listener.accept(this));
        } else {
            this.tickServer();
        }
    }

    private void tickServer() {
        boolean autonomous = this.returnPlan != null && this.pilot().isEmpty() && serverAutopilot.test(this);
        if (!autonomous && this.canSimulateMovement()) {
            this.tickLoosePhysics();
        }
        if (this.tickCount % CameraCharging.INTERVAL_TICKS == 0 && this.pilot().isEmpty()) {
            CameraCharging.tick(this);
        }
        DroneCargo.tick(this);
        this.syncFlightTags(autonomous);
        this.applyEffectsFromBlocks();
    }

    private void syncFlightTags(boolean serverFlies) {
        boolean piloted = this.pilot().isPresent();
        LivingEntity pilot = piloted ? this.pilotEntity() : null;
        DroneTags.flightState(this, DroneTagRules.flying(piloted, pilot != null && pilot.isAlive()),
                DroneTagRules.autopilot(piloted, serverFlies));
    }

    private void tickLoosePhysics() {
        if (this.isArmed()) {
            this.setArmed(false);
        }
        if (this.motorOutputsPacked() != 0 || this.motorOutput() != 0.0F) {
            this.setMotorOutputsPacked(0);
        }
        if (this.onGround() && GroundSettle.step(this.orientation(), this.settleScratch)) {
            this.setAttitude(this.settleScratch);
        }
        Vec3 velocity = this.getDeltaMovement();
        boolean resting = this.onGround() && velocity.lengthSqr() < 1.0E-7;
        if (resting && (this.tickCount % 10) != 0) {
            return;
        }
        if (!this.isNoGravity()) {
            velocity = velocity.add(0.0, -GRAVITY, 0.0);
        }
        if (this.isInWater()) {
            velocity = velocity.multiply(0.8, 0.8, 0.8);
            velocity = new Vec3(velocity.x, Math.max(velocity.y, -0.06), velocity.z);
        }
        this.move(MoverType.SELF, velocity);
        if (this.onGround()) {
            velocity = velocity.multiply(0.5, 0.0, 0.5);
        }
        velocity = velocity.scale(0.98);
        if (velocity.lengthSqr() < 1.0E-8) {
            velocity = Vec3.ZERO;
        }
        this.setDeltaMovement(velocity);
    }

    private void tickClientVisuals() {
        if (!this.renderOrientationReady) {
            this.renderAttitude.set(this.orientation());
            this.prevRenderOrientation.set(this.renderAttitude);
            this.orientationTarget.set(this.renderAttitude);
            this.renderOrientationReady = true;
        }
        if (!this.localState) {
            this.prevRenderOrientation.set(this.renderAttitude);
            if (this.orientationSteps > 0) {
                this.renderAttitude.slerp(this.orientationTarget, 1.0F / this.orientationSteps).normalize();
                this.orientationSteps--;
            } else {
                this.renderAttitude.set(this.orientationTarget);
            }
        }
        boolean armed = this.isArmed();
        for (int i = 0; i < MOTORS; i++) {
            float target = armed ? PROP_IDLE_SPEED + (PROP_MAX_SPEED - PROP_IDLE_SPEED) * this.motorOutput(i) : 0.0F;
            this.propSpeed[i] += (target - this.propSpeed[i]) * PROP_RESPONSE;
            if (Math.abs(this.propSpeed[i]) < 1.0E-3F) {
                this.propSpeed[i] = 0.0F;
            }
            this.prevPropAngle[i] = this.rotorAngle[i];
            this.rotorAngle[i] += PROP_SPIN[i] * this.propSpeed[i];
            if (Math.abs(this.rotorAngle[i]) > 1000.0F) {
                float shift = TWO_PI * Math.round(this.rotorAngle[i] / TWO_PI);
                this.rotorAngle[i] -= shift;
                this.prevPropAngle[i] -= shift;
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_BUILD.equals(accessor)) {
            this.refreshDimensions();
        } else if (DATA_PILOT.equals(accessor) && this.level().isClientSide()) {
            this.pilotUpdates++;
        } else if (DATA_ORIENTATION.equals(accessor) && this.level().isClientSide()) {
            this.orientationTarget.set(this.orientation());
            if (this.renderOrientationReady) {
                this.orientationSteps = INTERPOLATION_STEPS;
            } else {
                this.renderAttitude.set(this.orientationTarget);
                this.prevRenderOrientation.set(this.orientationTarget);
                this.renderOrientationReady = true;
            }
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        AirframeSpec params = AirframeSpec.of(this.build());
        return EntityDimensions.fixed((float) params.hitboxWidth, (float) params.hitboxHeight);
    }

    @Override
    public void move(MoverType type, Vec3 delta) {
        Vec3 clipped = delta;
        this.obliqueContact = false;
        this.obliqueDelta = delta;
        boolean extra = !this.noPhysics && type == MoverType.SELF && delta.lengthSqr() > 0.0;
        if (extra) {
            clipped = this.clipAgainstExtraColliders(delta);
        }
        super.move(type, clipped);
        if (extra && delta.y < 0.0 && clipped.y - delta.y > 1.0E-7 && !this.obliqueContact) {
            this.setOnGround(true);
            Vec3 velocity = this.getDeltaMovement();
            if (velocity.y < 0.0) {
                this.setDeltaMovement(velocity.x, 0.0, velocity.z);
            }
        }
    }

    public double supportGap(double range) {
        double free = Math.max(0.0, this.getAvailableSpaceBelow(range));
        if (this.noPhysics || free <= 0.0) {
            return free;
        }
        boolean contact = this.obliqueContact;
        Vec3 normal = this.obliqueNormal;
        Vec3 wanted = this.obliqueDelta;
        double gap = Math.max(0.0, -this.clipAgainstExtraColliders(new Vec3(0.0, -free, 0.0)).y);
        this.obliqueContact = contact;
        this.obliqueNormal = normal;
        this.obliqueDelta = wanted;
        return gap;
    }

    public double groundGap(double range) {
        if (this.noPhysics || !(range > 0.0)) {
            return Math.max(0.0, range);
        }
        AABB box = this.getBoundingBox().move(0.0, GROUND_PROBE_RAISE, 0.0);
        double reach = range + GROUND_PROBE_RAISE;
        double free = Math.max(0.0, -collideBoundingBox(this, new Vec3(0.0, -reach, 0.0), box, this.level(), List.of()).y);
        if (free > 0.0) {
            boolean contact = this.obliqueContact;
            Vec3 normal = this.obliqueNormal;
            Vec3 wanted = this.obliqueDelta;
            free = Math.max(0.0, -this.clipAgainstExtraColliders(box, new Vec3(0.0, -free, 0.0)).y);
            this.obliqueContact = contact;
            this.obliqueNormal = normal;
            this.obliqueDelta = wanted;
        }
        return Math.max(0.0, free - GROUND_PROBE_RAISE);
    }

    private Vec3 clipAgainstExtraColliders(Vec3 delta) {
        return this.clipAgainstExtraColliders(this.getBoundingBox(), delta);
    }

    private Vec3 clipAgainstExtraColliders(AABB box, Vec3 delta) {
        this.obliqueContact = false;
        AABB swept = box.expandTowards(delta);
        List<Entity> candidates = this.level().getEntities(this, swept.inflate(ExtraCollider.reach()), e -> e instanceof ExtraCollider);
        List<AABB> boxes = new ArrayList<>();
        SoftGround.collect(this.level(), swept, boxes);
        if (candidates.isEmpty() && boxes.isEmpty()) {
            return delta;
        }
        List<OrientedBox> oriented = new ArrayList<>();
        for (Entity candidate : candidates) {
            ((ExtraCollider) candidate).collectOrientedColliders(swept, boxes, oriented);
        }
        Vec3 wanted = delta;
        if (!oriented.isEmpty()) {
            OrientedSweep.Result result = this.sweepResult;
            OrientedSweep.clip(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, delta.x, delta.y, delta.z, oriented, result);
            if (result.hit) {
                this.obliqueContact = true;
                this.obliqueNormal = new Vec3(result.nx, result.ny, result.nz);
                wanted = new Vec3(result.dx, result.dy, result.dz);
            }
        }
        this.obliqueDelta = wanted;
        List<VoxelShape> shapes = new ArrayList<>(boxes.size());
        AABB reach = box.expandTowards(wanted);
        for (AABB collider : boxes) {
            if (collider != null && collider.intersects(reach)) {
                shapes.add(Shapes.create(collider));
            }
        }
        if (shapes.isEmpty()) {
            return wanted;
        }
        Vec3 out = Vec3.ZERO;
        AABB moving = box;
        for (Direction.Axis axis : Direction.axisStepOrder(wanted)) {
            double step = wanted.get(axis);
            if (step != 0.0) {
                double allowed = Shapes.collide(axis, moving, shapes, step);
                out = out.with(axis, allowed);
                moving = moving.move(axis == Direction.Axis.X ? allowed : 0.0, axis == Direction.Axis.Y ? allowed : 0.0,
                        axis == Direction.Axis.Z ? allowed : 0.0);
            }
        }
        return out;
    }

    public boolean obliqueContact() {
        return this.obliqueContact;
    }

    public Vec3 obliqueNormal() {
        return this.obliqueNormal;
    }

    public Vec3 obliqueDelta() {
        return this.obliqueDelta;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    public DroneBuild build() {
        return this.entityData.get(DATA_BUILD);
    }

    public void setBuild(DroneBuild build) {
        this.entityData.set(DATA_BUILD, Objects.requireNonNull(build, "build"));
    }

    public Quaternionfc orientation() {
        return this.entityData.get(DATA_ORIENTATION);
    }

    public void setAttitude(Quaternionfc orientation) {
        this.entityData.set(DATA_ORIENTATION, new Quaternionf(orientation));
    }

    public boolean isArmed() {
        if (this.localState) {
            return this.localArmed;
        }
        return this.entityData.get(DATA_ARMED);
    }

    public void setArmed(boolean armed) {
        this.entityData.set(DATA_ARMED, armed);
    }

    public Optional<UUID> pilot() {
        return this.entityData.get(DATA_PILOT).map(EntityReference::getUUID);
    }

    public @Nullable LivingEntity pilotEntity() {
        return this.entityData.get(DATA_PILOT).map(ref -> ref.getEntity(this.level(), LivingEntity.class)).orElse(null);
    }

    public boolean isFlownBy(UUID player) {
        return this.pilot().filter(player::equals).isPresent();
    }

    public void setPilot(@Nullable LivingEntity pilot) {
        Optional<UUID> previous = this.pilot();
        this.entityData.set(DATA_PILOT, Optional.ofNullable(EntityReference.<LivingEntity>of(pilot)));
        Optional<UUID> current = this.pilot();
        if (current.isEmpty()) {
            this.entityData.set(DATA_PILOT_MODE, (byte) PilotMode.NONE.ordinal());
        }
        if (!this.level().isClientSide() && !previous.equals(current)) {
            if (current.isPresent()) {
                this.returnPlan = null;
            }
            this.entityData.set(DATA_LEDS_OFF, false);
            this.stateSentTick = this.level().getGameTime();
            this.moveBudget.restart();
            this.physicsModified = false;
            if (current.isEmpty()) {
                this.setOnGround(false);
            }
            if (current.isPresent() && pilot instanceof Player player) {
                DroneTags.flownBy(this, player);
            }
            this.syncFlightTags(false);
            PILOT_CHANGED.fire(listener -> listener.onPilotChanged(this, previous, current));
        }
    }

    public PilotMode pilotMode() {
        return PilotMode.byOrdinal(this.entityData.get(DATA_PILOT_MODE));
    }

    public void setPilotMode(PilotMode mode) {
        this.entityData.set(DATA_PILOT_MODE, (byte) Objects.requireNonNull(mode, "mode").ordinal());
    }

    public boolean hasBattery() {
        return this.entityData.get(DATA_HAS_BATTERY);
    }

    public boolean ledsOff() {
        if (this.localState) {
            return this.localLedsOff;
        }
        return this.entityData.get(DATA_LEDS_OFF);
    }

    public void setLedsOff(boolean off) {
        this.entityData.set(DATA_LEDS_OFF, off);
    }

    public boolean batteryLocked() {
        return this.isArmed() || this.pilot().isPresent();
    }

    public ItemStack batteryStack() {
        if (!this.hasBattery()) {
            return ItemStack.EMPTY;
        }
        return PartCondition.with(Batteries.stackOf(this.build().battery(), StateValidation.unit(this.entityData.get(DATA_BATTERY))),
                this.wear().battery());
    }

    public ItemStack takeBattery() {
        ItemStack stack = this.batteryStack();
        this.entityData.set(DATA_HAS_BATTERY, false);
        this.setBatteryCharge(0.0F);
        this.setWear(this.wear().withBattery(PartWear.NEW));
        return stack;
    }

    public void insertBattery(BatteryType type, float charge) {
        this.insertBattery(type, charge, PartWear.NEW);
    }

    public void insertBattery(BatteryType type, float charge, float condition) {
        DroneBuild build = this.build();
        if (build.battery() != type) {
            this.setBuild(build.withBattery(type));
        }
        this.setBatteryCharge(charge);
        this.entityData.set(DATA_HAS_BATTERY, true);
        this.setWear(this.wear().withBattery(condition));
    }

    public PartWear wear() {
        return this.entityData.get(DATA_WEAR);
    }

    public DronePaint paint() {
        return this.entityData.get(DATA_PAINT);
    }

    public void setPaint(DronePaint paint) {
        this.entityData.set(DATA_PAINT, paint == null ? DronePaint.DEFAULT : paint);
    }

    public void setWear(PartWear wear) {
        this.entityData.set(DATA_WEAR, wear == null ? PartWear.PRISTINE : wear);
    }

    public float batteryCharge() {
        if (this.localState) {
            return this.localBattery;
        }
        return this.entityData.get(DATA_BATTERY);
    }

    public void setBatteryCharge(float charge) {
        this.entityData.set(DATA_BATTERY, StateValidation.unit(charge));
    }

    public float cameraCharge() {
        return StateValidation.unit(this.entityData.get(DATA_CAMERA_CHARGE));
    }

    public void setCameraCharge(float charge) {
        this.entityData.set(DATA_CAMERA_CHARGE, StateValidation.unit(charge));
    }

    public DroneCargo.Hold cargoHold() {
        return this.cargo;
    }

    public float cargoGrams() {
        return Math.max(0.0F, this.entityData.get(DATA_CARGO_GRAMS));
    }

    public ItemStack cargoDisplay() {
        return this.entityData.get(DATA_CARGO_ITEM);
    }

    public void setCargoSync(float grams, ItemStack display) {
        this.entityData.set(DATA_CARGO_GRAMS, Float.isFinite(grams) ? Math.max(0.0F, grams) : 0.0F);
        if (!ItemStack.matches(display, this.entityData.get(DATA_CARGO_ITEM))) {
            this.entityData.set(DATA_CARGO_ITEM, display.copy());
        }
    }

    public boolean hasOwnCameraBattery() {
        return this.build().accessories().camera().flatMap(AccessoryType::camera).map(ActionCamSpec::ownBattery).orElse(false);
    }

    public Quaternionf renderAttitude(float partialTick, Quaternionf dest) {
        if (!this.renderOrientationReady) {
            return dest.set(this.orientation());
        }
        if (this.localState) {
            return dest.set(this.renderAttitude);
        }
        return this.prevRenderOrientation.slerp(this.renderAttitude, Mth.clamp(partialTick, 0.0F, 1.0F), dest);
    }

    public float rotorAngle(int motor, float partialTick) {
        if (motor < 0 || motor >= MOTORS) {
            return 0.0F;
        }
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.prevPropAngle[motor], this.rotorAngle[motor]);
    }

    public float motorOutput(int motor) {
        return StateValidation.unpackMotor(this.motorOutputsPacked(), motor);
    }

    public float motorOutput() {
        if (this.localState) {
            return StateValidation.averageMotor(this.localMotors);
        }
        return this.entityData.get(DATA_MOTOR_OUTPUT);
    }

    public void setMotorOutput(float output) {
        this.entityData.set(DATA_MOTOR_OUTPUT, StateValidation.unit(output));
    }

    public int motorOutputsPacked() {
        if (this.localState) {
            return this.localMotors;
        }
        return this.entityData.get(DATA_MOTORS);
    }

    public void setMotorOutputs(float m0, float m1, float m2, float m3) {
        this.setMotorOutputsPacked(StateValidation.packMotors(m0, m1, m2, m3));
    }

    public void setMotorOutputsPacked(int packed) {
        this.entityData.set(DATA_MOTORS, packed);
        this.entityData.set(DATA_MOTOR_OUTPUT, StateValidation.averageMotor(packed));
    }

    public FlightTune tune() {
        return this.entityData.get(DATA_TUNE);
    }

    public void setTune(FlightTune tune) {
        this.entityData.set(DATA_TUNE, Objects.requireNonNull(tune, "tune"));
    }

    public void applyLocalState(Quaternionfc orientation, boolean armed, float battery, int motorsPacked) {
        this.applyLocalState(orientation, armed, battery, motorsPacked, this.localState && this.localLedsOff);
    }

    public void applyLocalState(Quaternionfc orientation, boolean armed, float battery, int motorsPacked, boolean ledsOff) {
        this.localLedsOff = ledsOff;
        this.localState = true;
        this.localArmed = armed;
        this.localBattery = StateValidation.unit(battery);
        this.localMotors = motorsPacked;
        this.renderAttitude.set(orientation);
        this.prevRenderOrientation.set(orientation);
        this.orientationTarget.set(orientation);
        this.orientationSteps = 0;
        this.renderOrientationReady = true;
    }

    public void clearLocalState() {
        this.localState = false;
        this.localLedsOff = false;
        this.orientationTarget.set(this.orientation());
        this.orientationSteps = INTERPOLATION_STEPS;
    }

    public void snapClientPose(Vec3 pos, Quaternionfc orientation) {
        this.getInterpolation().cancel();
        this.setPos(pos);
        this.setOldPosAndRot();
        this.renderAttitude.set(orientation);
        this.prevRenderOrientation.set(orientation);
        this.orientationTarget.set(orientation);
        this.orientationSteps = 0;
        this.renderOrientationReady = true;
    }

    public int resetEpoch() {
        return this.entityData.get(DATA_EPOCH);
    }

    public long stateSentTick() {
        return this.stateSentTick;
    }

    long lastRejectTick() {
        return this.lastRejectTick;
    }

    MoveBudget moveBudget() {
        return this.moveBudget;
    }

    public boolean physicsModified() {
        return this.physicsModified;
    }

    public int pilotUpdates() {
        return this.pilotUpdates;
    }

    void markRejected(long gameTime) {
        this.lastRejectTick = gameTime;
    }

    void applyPilotState(DroneStateC2S state, Quaternionf orientation, float battery, long gameTime) {
        this.setPos(state.x(), state.y(), state.z());
        this.setOnGround(false);
        this.setAttitude(orientation);
        this.setYRot(yawDegrees(orientation));
        this.setDeltaMovement(new Vec3(state.vx(), state.vy(), state.vz()).scale(0.05));
        this.setArmed(state.armed());
        this.setBatteryCharge(battery);
        this.setMotorOutputsPacked(state.armed() ? state.motors() : 0);
        this.setLedsOff(state.ledsOff());
        if (state.physicsModified()) {
            this.physicsModified = true;
        } else if (!state.armed()) {
            this.physicsModified = false;
        }
        this.stateSentTick = gameTime;
    }

    public static float yawDegrees(Quaternionfc orientation) {
        Vector3f forward = orientation.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        if (forward.x * forward.x + forward.z * forward.z < 1.0E-8F) {
            Vector3f up = orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
            forward.set(-up.x * Math.signum(forward.y), 0.0F, -up.z * Math.signum(forward.y));
        }
        return (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
    }

    public void loadFromStack(ItemStack stack) {
        this.setBuild(stack.getOrDefault(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE));
        boolean installed = stack.getOrDefault(CoreComponents.BATTERY_INSTALLED.get(), Boolean.TRUE);
        this.entityData.set(DATA_HAS_BATTERY, installed);
        this.setBatteryCharge(installed ? stack.getOrDefault(CoreComponents.BATTERY_CHARGE.get(), 1.0F) : 0.0F);
        this.setTune(stack.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY));
        PartWear wear = stack.getOrDefault(CoreComponents.PART_WEAR.get(), PartWear.PRISTINE);
        this.setWear(installed ? wear : wear.withBattery(PartWear.NEW));
        this.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        this.setCameraCharge(stack.getOrDefault(CoreComponents.CAMERA_CHARGE.get(), 1.0F));
        this.setPaint(PaintItems.paint(stack));
        this.receiverBind = BindComponents.of(stack);
    }

    public ItemStack toStack() {
        ItemStack stack = ContentIds.item(ContentIds.DRONE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        stack.set(CoreComponents.DRONE_BUILD.get(), this.build());
        stack.set(CoreComponents.DRONE_ID.get(), this.getUUID());
        if (this.hasBattery()) {
            stack.set(CoreComponents.BATTERY_CHARGE.get(), Batteries.quantize(StateValidation.unit(this.entityData.get(DATA_BATTERY))));
        } else {
            stack.remove(CoreComponents.BATTERY_CHARGE.get());
            stack.set(CoreComponents.BATTERY_INSTALLED.get(), false);
        }
        if (!this.tune().values().isEmpty()) {
            stack.set(CoreComponents.FLIGHT_TUNE.get(), this.tune());
        }
        if (!this.wear().isNew()) {
            stack.set(CoreComponents.PART_WEAR.get(), this.wear());
        }
        if (this.hasCustomName()) {
            stack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
        }
        if (this.hasOwnCameraBattery()) {
            DroneAssembly.storeCameraCharge(stack, this.cameraCharge());
        }
        PaintItems.store(stack, this.paint());
        BindComponents.store(stack, this.receiverBind);
        return stack;
    }

    public void resetTo(Vec3 pos, Quaternionfc orientation) {
        if (this.level().isClientSide()) {
            throw new IllegalStateException("QuadcopterEntity#resetTo nur auf dem Server");
        }
        this.snapTo(pos.x, pos.y, pos.z, yawDegrees(orientation), 0.0F);
        this.setDeltaMovement(Vec3.ZERO);
        this.setOnGround(false);
        this.setAttitude(orientation);
        this.entityData.set(DATA_EPOCH, this.resetEpoch() + 1);
        this.stateSentTick = this.level().getGameTime();
        this.moveBudget.restart();
        DronePiloting.broadcastReset(this);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() && CraftNames.useNameTag(player, this, held)) {
            return InteractionResult.SUCCESS;
        }
        DroneInteraction.Action action = DroneInteractions.resolve(player, this, held);
        if (action == DroneInteraction.Action.PASS) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        DroneInteractions.apply(serverPlayer, this, action, held);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isRemoved()) {
            return true;
        }
        if (this.isInvulnerableToBase(source)) {
            return false;
        }
        if (source.getEntity() instanceof Player player) {
            if (!this.mayBeBrokenBy(level, player)) {
                return false;
            }
            this.breakApart(level, !player.getAbilities().instabuild);
            return true;
        }
        this.damageTaken += Math.max(0.0F, damage);
        if (this.damageTaken >= BREAK_DAMAGE) {
            this.breakApart(level, true);
        }
        return true;
    }

    public boolean mayBeBrokenBy(ServerLevel level, Player player) {
        if (!player.mayBuild()) {
            return false;
        }
        Optional<UUID> pilot = this.pilot();
        return pilot.isEmpty() || pilot.get().equals(player.getUUID()) || level.isPvpAllowed();
    }

    public void breakApart(ServerLevel level, boolean drop) {
        if (this.isRemoved()) {
            return;
        }
        if (drop && level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            ItemStack stack = this.toStack();
            if (!stack.isEmpty()) {
                this.spawnAtLocation(level, stack);
            }
        }
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        ItemStack stack = this.toStack();
        if (stack.isEmpty()) {
            return null;
        }
        stack.remove(CoreComponents.DRONE_ID.get());
        return stack;
    }

    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        super.onRemoval(reason);
        if (!this.level().isClientSide() && this.pilot().isPresent()) {
            this.setPilot(null);
        }
        if (!this.level().isClientSide() && reason.shouldDestroy()) {
            DroneCargo.onRemoved(this);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.read("Build", DroneBuildCodecs.CODEC).ifPresent(this::setBuild);
        input.read("Orientation", ExtraCodecs.QUATERNIONF).ifPresent(this::setAttitude);
        boolean hasBattery = input.getBooleanOr("HasBattery", true);
        this.entityData.set(DATA_HAS_BATTERY, hasBattery);
        this.setBatteryCharge(hasBattery ? input.getFloatOr("Battery", 1.0F) : 0.0F);
        input.read("Tune", FlightTune.CODEC).ifPresent(this::setTune);
        this.setCameraCharge(input.getFloatOr("CameraCharge", 1.0F));
        this.setWear(input.read("Wear", WearCodecs.CODEC).orElse(PartWear.PRISTINE));
        this.setPaint(input.read("Paint", PaintCodecs.CODEC).orElse(DronePaint.DEFAULT));
        DroneCargo.load(this, input.read(DroneCargo.SAVE_KEY, DroneCargo.SAVE_CODEC).orElse(List.of()));
        this.returnPlan = input.read(RETURN_PLAN_KEY, ReturnPlan.CODEC).orElse(null);
        this.receiverBind = input.read(RECEIVER_BIND_KEY, BindCodecs.CODEC).orElse(null);
        if (!this.level().isClientSide()) {
            this.syncFlightTags(false);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("Build", DroneBuildCodecs.CODEC, this.build());
        output.store("Orientation", ExtraCodecs.QUATERNIONF, this.orientation());
        output.putFloat("Battery", StateValidation.unit(this.entityData.get(DATA_BATTERY)));
        output.putBoolean("HasBattery", this.hasBattery());
        output.store("Tune", FlightTune.CODEC, this.tune());
        output.putFloat("CameraCharge", this.cameraCharge());
        if (!this.wear().isNew()) {
            output.store("Wear", WearCodecs.CODEC, this.wear());
        }
        if (!this.paint().isDefault()) {
            output.store("Paint", PaintCodecs.CODEC, this.paint());
        }
        if (!this.cargo.isEmpty()) {
            output.store(DroneCargo.SAVE_KEY, DroneCargo.SAVE_CODEC, this.cargo.stacks());
        }
        output.storeNullable(RETURN_PLAN_KEY, ReturnPlan.CODEC, this.returnPlan);
        output.storeNullable(RECEIVER_BIND_KEY, BindCodecs.CODEC, this.receiverBind);
    }

    @FunctionalInterface
    public interface PilotListener {
        void onPilotChanged(QuadcopterEntity drone, Optional<UUID> previous, Optional<UUID> current);
    }

    public interface ServerMoveListener {
        void onServerMoved(QuadcopterEntity drone, ServerPlayer pilot, Vec3 from, Vec3 to);
    }
}
