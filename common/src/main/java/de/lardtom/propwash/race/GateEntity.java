package de.lardtom.propwash.race;

import de.lardtom.propwash.core.ExtraCollider;
import de.lardtom.propwash.core.OrientedBox;
import de.lardtom.propwash.race.world.WorldCourseMarker;
import de.lardtom.propwash.race.world.WorldCourses;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class GateEntity extends Entity implements ExtraCollider {
    public static final int NO_INDEX = -1;
    public static final int NO_COURSE = 0;
    private static final int RECONCILE_INTERVAL = 20;
    private static final double UNREACHABLE_DEPTH = 1.0E6;
    private static final int STACK_INTERVAL = 5;
    private static final double NEIGHBOUR_REACH = 0.1;
    private static final double PICK_POINT = 0.01;
    private static volatile Consumer<GateEntity> editor = gate -> {
    };

    private static final EntityDataAccessor<Byte> DATA_SIZE =
            SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_GATE_INDEX =
            SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COURSE =
            SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_MOUNT =
            SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3fc> DATA_TILT =
            SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_OPENING =
            SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_KIND =
            SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<String> DATA_REGION =
            SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_SIGNAL =
            SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.INT);
    private static final int STANDARD_OPENING = 0;
    private static final int VARIANT_SHIFT = 4;
    private static final int KIND_MASK = 0x0F;

    private AABB @Nullable [] colliderCache;
    private OrientedBox @Nullable [] orientedCache;
    private @Nullable GateTilt cacheTilt;
    private int cacheOpening = -1;
    private double cacheX;
    private double cacheY;
    private double cacheZ;
    private float cacheYaw;
    private int cacheMount = -1;
    private int cacheKind = -1;
    private @Nullable GateSize cacheSize;
    private GateStacking.@Nullable Base cacheBase;
    private @Nullable WorldCourseMarker worldCourse;
    private boolean covered;
    private boolean supported;
    private boolean carrying;
    private String regionText = "";
    private @Nullable FreeformRegion regionCache;
    private int pulseLeft;
    private @Nullable GateSignalTarget emitting;

    public GateEntity(EntityType<? extends GateEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE, (byte) GateSize.MEDIUM.ordinal());
        builder.define(DATA_GATE_INDEX, NO_INDEX);
        builder.define(DATA_COURSE, NO_COURSE);
        builder.define(DATA_MOUNT, (byte) GateMount.pack(GateMount.STANDING, false));
        builder.define(DATA_TILT, new Vector3f());
        builder.define(DATA_OPENING, STANDARD_OPENING);
        builder.define(DATA_KIND, (byte) 0);
        builder.define(DATA_REGION, "");
        builder.define(DATA_SIGNAL, GateSignal.DEFAULT.pack());
    }

    public GateSignal signal() {
        return GateSignal.unpack(this.entityData.get(DATA_SIGNAL));
    }

    public void setSignal(GateSignal signal) {
        this.entityData.set(DATA_SIGNAL, signal.pack());
        if (this.level() instanceof ServerLevel level) {
            if (!signal.enabled()) {
                this.pulseLeft = 0;
            } else if (this.pulseLeft > signal.pulseTicks()) {
                this.pulseLeft = signal.pulseTicks();
            }
            this.updateEmission(level);
        }
    }

    public boolean signals() {
        return this.kind().signals() && this.signal().enabled();
    }

    public int pulseLeft() {
        return this.pulseLeft;
    }

    public @Nullable GateSignalTarget emitting() {
        return this.emitting;
    }

    public GateSignalTarget signalTarget() {
        GateEntity base = this;
        if (this.inStack()) {
            List<GateEntity> column = this.stack();
            if (!column.isEmpty()) {
                base = column.getFirst();
            }
        }
        return GateSignalTarget.of(base.geometry());
    }

    public void pulse() {
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved() || !this.signals()) {
            return;
        }
        this.pulseLeft = this.signal().pulseTicks();
        this.updateEmission(level);
    }

    private void updateEmission(ServerLevel level) {
        GateSignalTarget target = this.pulseLeft > 0 && !this.isRemoved() && this.signals() ? this.signalTarget() : null;
        GateSignalTarget current = this.emitting;
        if (target == null ? current == null : target.equals(current)) {
            return;
        }
        this.emitting = target;
        if (current != null) {
            GateSignals.release(level, current);
        }
        if (target != null) {
            GateSignals.hold(level, target);
            GateSignals.track(this);
        }
    }

    boolean signalTick(ServerLevel level) {
        if (this.isRemoved()) {
            return false;
        }
        if (this.emitting != null || !this.signals()) {
            this.pulseLeft = Math.max(0, this.pulseLeft - 1);
        }
        this.updateEmission(level);
        return this.pulseLeft > 0 || this.emitting != null;
    }

    private void endEmission() {
        GateSignalTarget current = this.emitting;
        this.emitting = null;
        if (current != null && this.level() instanceof ServerLevel level) {
            GateSignals.release(level, current);
        }
    }

    public @Nullable FreeformRegion region() {
        if (this.entityData == null || this.kind() != CourseElement.FREEFORM) {
            return null;
        }
        String text = this.entityData.get(DATA_REGION);
        if (!text.equals(this.regionText)) {
            this.regionText = text;
            this.regionCache = FreeformRegion.decode(text);
        }
        return this.regionCache;
    }

    public boolean freeform() {
        return this.region() != null;
    }

    public String regionText() {
        return this.kind() == CourseElement.FREEFORM ? this.entityData.get(DATA_REGION) : "";
    }

    public void setFreeform(FreeformRegion region, int variant) {
        this.entityData.set(DATA_REGION, region.encode());
        this.setKind(CourseElement.FREEFORM, variant);
        FreeformReach.extend(FreeformReach.of(region));
        this.setBoundingBox(this.makeBoundingBox());
    }

    public CourseElement kind() {
        return CourseElement.byOrdinal(this.entityData.get(DATA_KIND) & KIND_MASK);
    }

    public int variant() {
        return this.kind().variant((this.entityData.get(DATA_KIND) & 0xFF) >> VARIANT_SHIFT);
    }

    public boolean element() {
        return !this.kind().gate();
    }

    public void setKind(CourseElement kind, int variant) {
        CourseElement value = kind == null ? CourseElement.GATE : kind;
        int packed = value.ordinal() | (value.variant(variant) << VARIANT_SHIFT);
        this.entityData.set(DATA_KIND, (byte) packed);
        if (!value.mountable()) {
            this.entityData.set(DATA_MOUNT, (byte) GateMount.pack(GateMount.STANDING, false));
            GateTilt tilt = this.tilt();
            if (tilt.tilted()) {
                this.entityData.set(DATA_TILT, new Vector3f(tilt.yaw(), 0.0F, 0.0F));
            }
        }
        this.setBoundingBox(this.makeBoundingBox());
        ExtraCollider.extendReach(this.reach());
    }

    public void setVariant(int variant) {
        this.setKind(this.kind(), variant);
    }

    public GateTilt tilt() {
        Vector3fc packed = this.entityData.get(DATA_TILT);
        return new GateTilt(packed.x(), packed.y(), packed.z());
    }

    public void setTilt(GateTilt tilt) {
        GateTilt value = tilt == null ? GateTilt.NONE : tilt;
        this.entityData.set(DATA_TILT, new Vector3f(value.yaw(), value.pitch(), value.roll()));
        this.setBoundingBox(this.makeBoundingBox());
        ExtraCollider.extendReach(this.reach());
    }

    public boolean customOpening() {
        return this.entityData.get(DATA_OPENING) != STANDARD_OPENING;
    }

    public double standardOpeningWidth() {
        CourseElement kind = this.kind();
        FreeformRegion region = this.region();
        if (region != null) {
            return region.width();
        }
        return kind.gate() ? this.size().openingWidth() : kind.defaultWidth();
    }

    public double standardOpeningHeight() {
        CourseElement kind = this.kind();
        FreeformRegion region = this.region();
        if (region != null) {
            return region.height();
        }
        return kind.gate() ? this.size().openingHeight() : kind.defaultHeight();
    }

    public double openingWidth() {
        int packed = this.entityData.get(DATA_OPENING);
        return packed == STANDARD_OPENING ? this.standardOpeningWidth() : GateSize.unpackOpeningWidth(packed);
    }

    public double openingHeight() {
        int packed = this.entityData.get(DATA_OPENING);
        return packed == STANDARD_OPENING ? this.standardOpeningHeight() : GateSize.unpackOpeningHeight(packed);
    }

    public double width() {
        CourseElement kind = this.kind();
        FreeformRegion region = this.region();
        if (region != null) {
            return region.width();
        }
        if (!kind.gate()) {
            return kind.outerWidth(this.openingWidth());
        }
        return this.customOpening() ? this.size().outer(this.openingWidth()) : this.size().width();
    }

    public double height() {
        CourseElement kind = this.kind();
        FreeformRegion region = this.region();
        if (region != null) {
            return region.height();
        }
        if (!kind.gate()) {
            return kind.outerHeight(this.openingHeight());
        }
        return this.customOpening() ? this.size().outer(this.openingHeight()) : this.size().height();
    }

    public void setOpening(double width, double height) {
        if (this.kind() == CourseElement.FREEFORM) {
            this.entityData.set(DATA_OPENING, STANDARD_OPENING);
            return;
        }
        double w = GateSize.snapOpening(width);
        double h = GateSize.snapOpening(height);
        boolean standard = Math.abs(w - this.standardOpeningWidth()) < 1.0E-6 && Math.abs(h - this.standardOpeningHeight()) < 1.0E-6;
        this.entityData.set(DATA_OPENING, standard ? STANDARD_OPENING : GateSize.packOpening(w, h));
        this.setBoundingBox(this.makeBoundingBox());
        ExtraCollider.extendReach(this.reach());
    }

    private void setOuter(double width, double height) {
        CourseElement kind = this.kind();
        if (!kind.gate()) {
            this.setOpening(kind.innerWidth(width), kind.innerHeight(height));
            return;
        }
        GateSize size = this.size();
        this.setOpening(width - 2.0 * size.frame(), height - 2.0 * size.frame());
    }

    public double reach() {
        CourseElement kind = this.kind();
        if (kind == CourseElement.FREEFORM) {
            return ExtraCollider.MAX_HALF_EXTENT;
        }
        if (!kind.gate()) {
            double h = this.height();
            double w = kind == CourseElement.CUBE ? this.width() : CourseElement.BANNER_WIDTH;
            return Math.max(ExtraCollider.MAX_HALF_EXTENT, Math.sqrt(w * w + h * h) + 1.0);
        }
        if (this.tilt().none() && !this.customOpening()) {
            return ExtraCollider.MAX_HALF_EXTENT;
        }
        double w = this.width();
        double h = this.height();
        return Math.sqrt(w * w + h * h) + h + GateMount.HANG_DROP;
    }

    public GateMount mount() {
        return GateMount.unpackMount(this.entityData.get(DATA_MOUNT));
    }

    public boolean flipped() {
        return GateMount.unpackFlipped(this.entityData.get(DATA_MOUNT));
    }

    public void setMount(GateMount mount, boolean flipped) {
        this.entityData.set(DATA_MOUNT, (byte) GateMount.pack(mount, flipped));
        this.setBoundingBox(this.makeBoundingBox());
    }

    public void applyGeometry(GateGeometry geometry) {
        FreeformRegion region = geometry.region();
        if (region != null) {
            this.setFreeform(region, geometry.variant());
            this.setPos(geometry.x(), geometry.y(), geometry.z());
            this.setBoundingBox(this.makeBoundingBox());
            this.needsSync = true;
            return;
        }
        this.entityData.set(DATA_KIND, (byte) (geometry.kind().ordinal() | (geometry.variant() << VARIANT_SHIFT)));
        this.entityData.set(DATA_MOUNT, (byte) GateMount.pack(geometry.mount(), geometry.flipped()));
        GateTilt tilt = geometry.tilt();
        this.entityData.set(DATA_TILT, new Vector3f(tilt.yaw(), tilt.pitch(), tilt.roll()));
        this.setOuter(geometry.width(), geometry.height());
        ExtraCollider.extendReach(this.reach());
        super.setYRot(GateGeometry.snapYaw(geometry.yawDegrees()));
        this.yRotO = this.getYRot();
        this.setPos(geometry.x(), geometry.y(), geometry.z());
        this.setBoundingBox(this.makeBoundingBox());
        this.needsSync = true;
    }

    public GateSize size() {
        return GateSize.byOrdinal(this.entityData.get(DATA_SIZE));
    }

    public void setSize(GateSize size) {
        this.entityData.set(DATA_SIZE, (byte) size.ordinal());
        this.refreshDimensions();
    }

    public int gateIndex() {
        return this.entityData.get(DATA_GATE_INDEX);
    }

    public void setGateIndex(int index) {
        this.entityData.set(DATA_GATE_INDEX, Math.max(NO_INDEX, index));
    }

    public int courseId() {
        return this.entityData.get(DATA_COURSE);
    }

    public void setCourseId(int courseId) {
        this.entityData.set(DATA_COURSE, Math.max(NO_COURSE, courseId));
    }

    public void setCourse(int courseId, int index) {
        this.setCourseId(courseId);
        this.setGateIndex(courseId == NO_COURSE ? NO_INDEX : index);
    }

    public @Nullable WorldCourseMarker worldCourse() {
        return this.worldCourse;
    }

    public void setWorldCourse(@Nullable WorldCourseMarker marker) {
        this.worldCourse = marker;
    }

    public void clearWorldCourse() {
        this.worldCourse = null;
    }

    public boolean inCourse() {
        return this.courseId() != NO_COURSE && this.gateIndex() >= 0;
    }

    public float facingYaw() {
        return GateGeometry.snapYaw(this.getYRot());
    }

    public GateGeometry geometry() {
        return this.geometryAt(this.getX(), this.getY(), this.getZ());
    }

    @Override
    public Vec3 getLightProbePosition(float partialTickTime) {
        GateGeometry geometry = this.geometry();
        return new Vec3(geometry.centerX(), geometry.centerY(), geometry.centerZ());
    }

    private GateGeometry geometryAt(double x, double y, double z) {
        GateTilt tilt = this.tilt();
        CourseElement kind = this.kind();
        if (kind == CourseElement.FREEFORM) {
            FreeformRegion region = this.region();
            return GateGeometry.freeform(x, y, z, region == null ? FreeformRegion.single(FreeformRegion.AXIS_Z) : region, this.variant());
        }
        if (!kind.gate()) {
            return GateGeometry.element(x, y, z, this.facingYaw(), kind, this.variant(), this.width(), this.height(), tilt, this.mount());
        }
        if (tilt.none() && !this.customOpening()) {
            return GateGeometry.of(x, y, z, this.facingYaw(), this.size(), this.mount(), this.flipped());
        }
        return GateGeometry.of(x, y, z, this.facingYaw(), this.size(), this.mount(), this.flipped(), this.width(), this.height(), tilt);
    }

    @Override
    public void setYRot(float yRot) {
        float before = this.getYRot();
        super.setYRot(Float.isFinite(yRot) ? GateGeometry.snapYaw(yRot) : yRot);
        if (this.getYRot() != before && this.entityData != null) {
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_SIZE.equals(accessor)) {
            this.refreshDimensions();
        } else if (DATA_MOUNT.equals(accessor)) {
            this.setBoundingBox(this.makeBoundingBox());
        } else if (DATA_TILT.equals(accessor) || DATA_OPENING.equals(accessor) || DATA_KIND.equals(accessor)) {
            this.setBoundingBox(this.makeBoundingBox());
            ExtraCollider.extendReach(this.reach());
        } else if (DATA_REGION.equals(accessor)) {
            FreeformRegion region = this.region();
            if (region != null) {
                FreeformReach.extend(FreeformReach.of(region));
            }
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.size().dimensions();
    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        GateMount mount = this.entityData == null ? GateMount.STANDING : this.mount();
        boolean element = this.entityData != null && this.element();
        if (!element && mount == GateMount.STANDING && (this.entityData == null || (this.tilt().none() && !this.customOpening()))) {
            GateGeometry geometry = GateGeometry.of(position.x, position.y, position.z, this.facingYaw(), this.size());
            double ex = geometry.halfExtentX();
            double ez = geometry.halfExtentZ();
            return new AABB(position.x - ex, position.y, position.z - ez, position.x + ex, position.y + geometry.height(), position.z + ez);
        }
        AABB[] out = new AABB[1];
        this.geometryAt(position.x, position.y, position.z)
                .bounds((minX, minY, minZ, maxX, maxY, maxZ) -> out[0] = new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        return out[0];
    }

    public boolean oriented() {
        this.colliders();
        return this.orientedCache != null && this.orientedCache.length > 0;
    }

    public AABB[] colliders() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        float yaw = this.facingYaw();
        GateSize size = this.size();
        GateStacking.Base base = this.base();
        int mount = this.entityData.get(DATA_MOUNT);
        GateTilt tilt = this.tilt();
        int opening = this.entityData.get(DATA_OPENING);
        int kind = this.entityData.get(DATA_KIND);
        AABB[] cached = this.colliderCache;
        if (cached == null || x != this.cacheX || y != this.cacheY || z != this.cacheZ || yaw != this.cacheYaw || size != this.cacheSize
                || base != this.cacheBase || mount != this.cacheMount || !tilt.equals(this.cacheTilt) || opening != this.cacheOpening
                || kind != this.cacheKind) {
            List<AABB> boxes = new ArrayList<>(6);
            GateGeometry geometry = this.geometryAt(x, y, z);
            geometry.boxes((minX, minY, minZ, maxX, maxY, maxZ) -> boxes.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ)), base);
            List<OrientedBox> oriented = new ArrayList<>(6);
            if (!geometry.alignedFrame()) {
                geometry.parts(oriented::add, base);
            }
            cached = boxes.toArray(new AABB[0]);
            this.colliderCache = cached;
            this.orientedCache = oriented.toArray(new OrientedBox[0]);
            this.cacheX = x;
            this.cacheY = y;
            this.cacheZ = z;
            this.cacheYaw = yaw;
            this.cacheSize = size;
            this.cacheBase = base;
            this.cacheMount = mount;
            this.cacheTilt = tilt;
            this.cacheOpening = opening;
            this.cacheKind = kind;
        }
        return cached;
    }

    public OrientedBox[] orientedColliders() {
        this.colliders();
        OrientedBox[] cached = this.orientedCache;
        return cached == null ? new OrientedBox[0] : cached;
    }

    @Override
    public void collectColliders(AABB query, List<AABB> out) {
        for (AABB box : this.colliders()) {
            if (box.intersects(query)) {
                out.add(box);
            }
        }
    }

    @Override
    public void collectOrientedColliders(AABB query, List<AABB> aligned, List<OrientedBox> oriented) {
        OrientedBox[] parts = this.orientedColliders();
        if (parts.length == 0) {
            this.collectColliders(query, aligned);
            return;
        }
        for (OrientedBox part : parts) {
            if (part.aligned()) {
                AABB box = new AABB(part.minX(), part.minY(), part.minZ(), part.maxX(), part.maxY(), part.maxZ());
                if (box.intersects(query)) {
                    aligned.add(box);
                }
            } else if (part.maxX() > query.minX && part.minX() < query.maxX && part.maxY() > query.minY && part.minY() < query.maxY
                    && part.maxZ() > query.minZ && part.minZ() < query.maxZ) {
                oriented.add(part);
            }
        }
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    public AABB pickBox(Vec3 from, Vec3 to, @Nullable Entity picker) {
        if (this.kind() == CourseElement.FREEFORM) {
            return this.pickFreeform(from, to, picker);
        }
        return this.pickBox(from, to);
    }

    public static boolean picksFreeform(@Nullable Entity picker) {
        if (!(picker instanceof Player player)) {
            return false;
        }
        if (CourseToolItem.isCourseTool(player.getMainHandItem()) || CourseToolItem.isCourseTool(player.getOffhandItem())) {
            return true;
        }
        return player.isShiftKeyDown()
                && (player.getMainHandItem().getItem() instanceof FreeformGateItem || player.getOffhandItem().getItem() instanceof FreeformGateItem);
    }

    private AABB pickFreeform(Vec3 from, Vec3 to, @Nullable Entity picker) {
        if (picksFreeform(picker)) {
            GateGeometry geometry = this.geometry();
            Passage passage = geometry.stages().getFirst();
            double t = passage.crossing(from.x, from.y, from.z, to.x, to.y, to.z, true);
            if (t < 0.0) {
                t = passage.crossing(from.x, from.y, from.z, to.x, to.y, to.z, false);
            }
            if (t >= 0.0) {
                Vec3 hit = from.lerp(to, t);
                return new AABB(hit.x - PICK_POINT, hit.y - PICK_POINT, hit.z - PICK_POINT, hit.x + PICK_POINT, hit.y + PICK_POINT, hit.z + PICK_POINT);
            }
        }
        double below = this.level().getMinY() - UNREACHABLE_DEPTH;
        return new AABB(this.getX(), below, this.getZ(), this.getX(), below, this.getZ());
    }

    public AABB pickBox(Vec3 from, Vec3 to) {
        OrientedBox[] parts = this.orientedColliders();
        if (parts.length > 0) {
            return this.pickOriented(parts, from, to);
        }
        AABB best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AABB box : this.colliders()) {
            if (box.contains(from)) {
                return box;
            }
            Optional<Vec3> hit = box.clip(from, to);
            if (hit.isPresent()) {
                double distance = hit.get().distanceToSqr(from);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = box;
                }
            }
        }
        if (best != null) {
            return best;
        }
        double below = this.level().getMinY() - UNREACHABLE_DEPTH;
        return new AABB(this.getX(), below, this.getZ(), this.getX(), below, this.getZ());
    }

    private AABB pickOriented(OrientedBox[] parts, Vec3 from, Vec3 to) {
        double best = Double.MAX_VALUE;
        for (OrientedBox part : parts) {
            if (part.contains(from.x, from.y, from.z)) {
                return new AABB(from.x - PICK_POINT, from.y - PICK_POINT, from.z - PICK_POINT, from.x + PICK_POINT, from.y + PICK_POINT,
                        from.z + PICK_POINT);
            }
            double t = part.clip(from.x, from.y, from.z, to.x, to.y, to.z);
            if (t >= 0.0 && t < best) {
                best = t;
            }
        }
        if (best <= 1.0) {
            Vec3 hit = from.lerp(to, best);
            Vec3 back = to.subtract(from).normalize().scale(PICK_POINT);
            Vec3 center = hit.add(back);
            return new AABB(center.x - PICK_POINT, center.y - PICK_POINT, center.z - PICK_POINT, center.x + PICK_POINT, center.y + PICK_POINT,
                    center.z + PICK_POINT);
        }
        double below = this.level().getMinY() - UNREACHABLE_DEPTH;
        return new AABB(this.getX(), below, this.getZ(), this.getX(), below, this.getZ());
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        return RaceModule.item(this.kind(), this.size()).map(ItemStack::new).orElse(null);
    }

    public boolean covered() {
        return this.covered;
    }

    public boolean supported() {
        return this.supported;
    }

    public GateStacking.Base base() {
        if (this.kind() == CourseElement.GATE_POLE) {
            return GatePoles.base(this.mount(), this.supported);
        }
        if (this.element()) {
            return GateStacking.Base.MOUNTED;
        }
        GateMount mount = this.mount();
        if (this.tilt().tilted()) {
            return GateStacking.Base.MOUNTED;
        }
        if (!mount.stackable()) {
            return mount.base();
        }
        return GateStacking.Base.of(this.supported, this.covered);
    }

    public boolean stackable() {
        return !this.element() && this.mount().stackable() && !this.tilt().tilted();
    }

    public boolean inStack() {
        return (this.stackable() && (this.covered || this.supported)) || this.carrying;
    }

    public boolean carrying() {
        return this.carrying;
    }

    public void refreshStack() {
        if (this.kind() == CourseElement.GATE_POLE) {
            this.covered = false;
            this.supported = GatePoleLinks.support(this) != null;
            this.carrying = !GatePoleLinks.riders(this).isEmpty();
            return;
        }
        if (!this.stackable()) {
            this.covered = false;
            this.supported = false;
            this.carrying = this.kind().gate() && !GatePoleLinks.riders(this).isEmpty();
            return;
        }
        this.covered = this.gateAbove() != null;
        this.supported = this.gateBelow() != null;
        this.carrying = !this.covered && !GatePoleLinks.riders(this).isEmpty();
    }

    public void refreshColumn() {
        for (GateEntity gate : this.stack()) {
            gate.refreshStack();
        }
    }

    private List<GateEntity> neighbours(double y) {
        double x = this.getX();
        double z = this.getZ();
        AABB area = new AABB(x - NEIGHBOUR_REACH, y - NEIGHBOUR_REACH, z - NEIGHBOUR_REACH, x + NEIGHBOUR_REACH, y + NEIGHBOUR_REACH,
                z + NEIGHBOUR_REACH);
        return this.level().getEntitiesOfClass(GateEntity.class, area, gate -> gate != this && !gate.isRemoved());
    }

    public @Nullable GateEntity gateAbove() {
        GateGeometry own = this.geometry();
        for (GateEntity other : this.neighbours(GateStacking.seat(own))) {
            if (GateStacking.restsOn(other.geometry(), own)) {
                return other;
            }
        }
        return null;
    }

    public @Nullable GateEntity gateBelow() {
        GateGeometry own = this.geometry();
        for (GateEntity other : this.neighbours(own.y())) {
            if (GateStacking.restsOn(own, other.geometry())) {
                return other;
            }
        }
        return null;
    }

    public List<GateEntity> stack() {
        GateEntity bottom = this;
        for (int guard = 0; guard < GateStacking.MAX_STACK * 2; guard++) {
            GateEntity below = bottom.gateBelow();
            if (below == null || below == this) {
                break;
            }
            bottom = below;
        }
        List<GateEntity> column = new ArrayList<>();
        GateEntity current = bottom;
        while (current != null && !column.contains(current) && column.size() < GateStacking.MAX_STACK * 2) {
            column.add(current);
            current = current.gateAbove();
        }
        if (!column.contains(this)) {
            column.clear();
            column.add(this);
        }
        return column;
    }

    private void settleAbove(ServerLevel level) {
        GateEntity above = this.gateAbove();
        double support = this.getY();
        List<GateEntity> moved = new ArrayList<>();
        while (above != null && !moved.contains(above) && moved.size() < GateStacking.MAX_STACK * 2) {
            GateEntity next = above.gateAbove();
            above.dropTo(support);
            moved.add(above);
            support += above.size().height() - above.size().frame();
            above = next;
        }
        for (GateEntity gate : moved) {
            gate.refreshStack();
        }
        GateEntity below = this.gateBelow();
        if (below != null) {
            below.refreshStack();
        }
        if (!moved.isEmpty()) {
            level.playSound(null, this.getX(), support, this.getZ(), SoundEvents.ARMOR_STAND_FALL, SoundSource.NEUTRAL, 1.0F, 0.9F);
        }
    }

    private void dropTo(double y) {
        List<GateEntity> riders = GatePoleLinks.riders(this);
        double delta = y - this.getY();
        this.setPos(this.getX(), y, this.getZ());
        this.needsSync = true;
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null && registry.courseOf(this.getUUID()) != NO_COURSE) {
            registry.reconcile(this);
        }
        for (GateEntity rider : riders) {
            rider.dropTo(rider.getY() + delta);
        }
    }

    public static void setEditor(Consumer<GateEntity> opener) {
        editor = opener;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack stack = player.getItemInHand(hand);
        boolean tool = stack.getItem() instanceof GateItem || stack.getItem() instanceof CourseToolItem;
        if (tool && player.isSecondaryUseActive()) {
            if (this.level().isClientSide()) {
                editor.accept(this);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof GateItem gateItem) {
            return gateItem.stackOn(this, player, stack, location);
        }
        if (GatePoleLinks.dye(this, player, stack)) {
            return InteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof CourseToolItem) {
            if (player instanceof ServerPlayer serverPlayer) {
                CourseToolItem.useOnGate(stack, serverPlayer, this);
            }
            return InteractionResult.SUCCESS;
        }
        return super.interact(player, hand, location);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isRemoved() || this.isInvulnerableToBase(source) || !(source.getEntity() instanceof ServerPlayer player)
                || source.getDirectEntity() != player || !player.mayBuild()) {
            return false;
        }
        if (!player.isShiftKeyDown() && !CourseToolItem.isCourseTool(player.getMainHandItem())) {
            player.sendOverlayMessage(Component.translatable("propwash.race.gate.break_hint"));
            return false;
        }
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(registry.courseOf(this.getUUID()));
        if (course != null && !CourseRegistry.canEdit(player, course)) {
            player.sendOverlayMessage(Component.translatable("propwash.race.tool.not_owner", course.id, CourseRegistry.ownerName(course))
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        if (!player.hasInfiniteMaterials()) {
            RaceModule.item(this.kind(), this.size()).ifPresent(item -> this.spawnAtLocation(level, new ItemStack(item)));
        }
        GatePoleLinks.detach(this, player);
        level.playSound(null, this.getX(), this.getY() + 0.5, this.getZ(), SoundEvents.ARMOR_STAND_BREAK, SoundSource.NEUTRAL, 1.0F, 1.0F);
        this.gameEvent(GameEvent.ENTITY_DIE, player);
        this.kill(level);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount <= 1 || (this.tickCount + this.getId()) % STACK_INTERVAL == 0) {
            this.refreshStack();
        }
        if (this.pulseLeft > 0 && this.emitting == null && !this.level().isClientSide()) {
            GateSignals.track(this);
        }
        if (!this.level().isClientSide() && (this.tickCount + this.getId()) % RECONCILE_INTERVAL == 0) {
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null) {
                WorldCourseMarker marker = this.worldCourse;
                if (marker != null && registry.courseOf(this.getUUID()) == NO_COURSE) {
                    WorldCourses.adopt(registry, this, marker);
                } else {
                    registry.reconcile(this);
                }
            }
        }
    }

    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        super.onRemoval(reason);
        this.endEmission();
        if (this.level() instanceof ServerLevel level && reason.shouldDestroy()) {
            GatePoleLinks.detach(this, null);
            this.settleAbove(level);
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null) {
                registry.gateDestroyed(this);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.read("Size", GateSize.CODEC).ifPresent(this::setSize);
        CourseElement kind = CourseElement.byKey(input.getStringOr("Kind", CourseElement.GATE.key()));
        this.entityData.set(DATA_KIND, (byte) (kind.ordinal() | (kind.variant(input.getIntOr("Variant", 0)) << VARIANT_SHIFT)));
        if (kind == CourseElement.FREEFORM) {
            FreeformRegion region = FreeformRegion.decode(input.getStringOr("Region", ""));
            this.entityData.set(DATA_REGION, (region == null ? FreeformRegion.single(FreeformRegion.AXIS_Z) : region).encode());
            FreeformReach.extend(FreeformReach.of(region == null ? FreeformRegion.single(FreeformRegion.AXIS_Z) : region));
        }
        this.setGateIndex(input.getIntOr("GateIndex", NO_INDEX));
        this.setCourseId(input.getIntOr("Course", NO_COURSE));
        this.worldCourse = input.read("WorldCourse", WorldCourseMarker.CODEC).orElse(null);
        GateMount mount = input.read("Mount", GateMount.CODEC).orElse(GateMount.STANDING);
        this.entityData.set(DATA_MOUNT, (byte) GateMount.pack(mount, input.getBooleanOr("Flipped", false)));
        GateTilt tilt = input.read("Tilt", GateTilt.CODEC).orElse(GateTilt.NONE);
        this.entityData.set(DATA_TILT, new Vector3f(tilt.yaw(), tilt.pitch(), tilt.roll()));
        ExtraCollider.extendReach(this.reach());
        double width = input.getDoubleOr("OpeningWidth", this.standardOpeningWidth());
        double height = input.getDoubleOr("OpeningHeight", this.standardOpeningHeight());
        this.setOpening(width, height);
        this.setBoundingBox(this.makeBoundingBox());
        GateSignal signal = new GateSignal(input.getBooleanOr("RedstoneSignal", GateSignal.DEFAULT.enabled()),
                input.getIntOr("PulseTicks", GateSignal.DEFAULT_PULSE), input.getBooleanOr("SignalRaceOnly", false));
        this.entityData.set(DATA_SIGNAL, signal.pack());
        this.pulseLeft = signal.enabled() ? Math.clamp(input.getIntOr("SignalPulse", 0), 0, signal.pulseTicks()) : 0;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("Size", GateSize.CODEC, this.size());
        CourseElement kind = this.kind();
        if (!kind.gate()) {
            output.putString("Kind", kind.key());
            output.putInt("Variant", this.variant());
        }
        if (kind == CourseElement.FREEFORM) {
            output.putString("Region", this.entityData.get(DATA_REGION));
        }
        output.putInt("GateIndex", this.gateIndex());
        output.putInt("Course", this.courseId());
        output.storeNullable("WorldCourse", WorldCourseMarker.CODEC, this.worldCourse);
        output.store("Mount", GateMount.CODEC, this.mount());
        output.putBoolean("Flipped", this.flipped());
        GateTilt tilt = this.tilt();
        if (!tilt.none()) {
            output.store("Tilt", GateTilt.CODEC, tilt);
        }
        if (this.customOpening()) {
            output.putDouble("OpeningWidth", this.openingWidth());
            output.putDouble("OpeningHeight", this.openingHeight());
        }
        GateSignal signal = this.signal();
        if (!signal.equals(GateSignal.DEFAULT)) {
            output.putBoolean("RedstoneSignal", signal.enabled());
            output.putInt("PulseTicks", signal.pulseTicks());
            output.putBoolean("SignalRaceOnly", signal.raceOnly());
        }
        if (this.pulseLeft > 0) {
            output.putInt("SignalPulse", this.pulseLeft);
        }
    }
}
