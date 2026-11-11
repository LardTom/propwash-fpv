package de.lardtom.propwash.race;

import de.lardtom.propwash.network.ServerPayloadContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public final class GateMounting {
    public static final double EXTRA_REACH = 2.0;
    public static final double EDIT_REACH = 16.0;
    public static final double MAX_NUDGE = 8.0;
    public static final double COLUMN_REACH = GateStacking.MAX_STACK * (GateSize.MAX_OPENING + 2.0);
    private static final Map<UUID, GatePlacement.Preference> PREFERENCES = new ConcurrentHashMap<>();
    private static volatile Function<Player, GatePlacement.Preference> client = player -> GatePlacement.Preference.DEFAULT;
    private static volatile int lastUnlinked = GateEntity.NO_COURSE;
    private static volatile Component lastRescan = Component.empty();

    public enum Result {
        ROTATED,
        EDITED,
        UNLINKED,
        MISSING,
        TOO_FAR,
        NOT_ALLOWED,
        NOT_OWNER,
        STACK_LOCKED,
        BLOCKED,
        NEED_TOOL,
        NOT_IN_COURSE,
        RESCANNED,
        RESCAN_FAILED,
        SIGNAL_SET,
        NUDGE_LIMIT,
        SCORE_LOCKED,
        STACK_TOO_WIDE,
        STACK_TOO_HIGH,
        STACK_TURN_LOCKED
    }

    public record ColumnTurn(List<GateEntity> gates, List<GateGeometry> next, List<GateStacking.Base> bases, float delta) {
        public boolean halfOnly() {
            for (GateEntity gate : this.gates) {
                if (gate.carrying() || gate.linked()) {
                    return true;
                }
            }
            return false;
        }
    }

    private GateMounting() {
    }

    public static void setClientPreference(Function<Player, GatePlacement.Preference> source) {
        client = source;
    }

    public static GatePlacement.Preference preference(@Nullable Player player, boolean clientSide) {
        if (player == null) {
            return GatePlacement.Preference.DEFAULT;
        }
        if (clientSide) {
            return client.apply(player);
        }
        return PREFERENCES.getOrDefault(player.getUUID(), GatePlacement.Preference.DEFAULT);
    }

    public static void setPreference(ServerPlayer player, GatePlacement.Preference preference) {
        if (preference.equals(GatePlacement.Preference.DEFAULT)) {
            PREFERENCES.remove(player.getUUID());
        } else {
            PREFERENCES.put(player.getUUID(), preference);
        }
    }

    static void forget(ServerPlayer player) {
        PREFERENCES.remove(player.getUUID());
    }

    static void clear() {
        PREFERENCES.clear();
    }

    static void onPreference(RacePayloads.GatePreferenceC2S payload, ServerPayloadContext context) {
        setPreference(context.player(), payload.preference());
    }

    static void onRotate(RacePayloads.GateRotateC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        Entity entity = player.level().getEntity(payload.entityId());
        GateEntity gate = entity instanceof GateEntity found ? found : null;
        Result result = gate == null ? Result.MISSING : rotate(player, gate, payload.changeMount(), payload.step());
        Component message = message(result, gate);
        if (message != null) {
            player.sendOverlayMessage(message);
        }
    }

    static void onEdit(RacePayloads.GateEditC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        Entity entity = player.level().getEntity(payload.entityId());
        GateEntity gate = entity instanceof GateEntity found ? found : null;
        Result result;
        if (gate == null) {
            result = Result.MISSING;
        } else if (payload.action() == RacePayloads.GateEditAction.UNLINK) {
            result = unlink(player, gate);
        } else if (payload.action() == RacePayloads.GateEditAction.RESCAN) {
            result = holdsTool(player) ? rescan(player, gate, ScoreArea.unpack(payload.score()).open()) : Result.NEED_TOOL;
        } else if (!holdsTool(player)) {
            result = Result.NEED_TOOL;
        } else {
            result = edit(player, gate, payload.heading(), payload.pitch(), payload.roll(), payload.openingWidth(), payload.openingHeight(),
                    payload.variant(), ScoreArea.unpack(payload.score()), payload.dx(), payload.dy(), payload.dz());
        }
        Component message = result == Result.BLOCKED ? Component.translatable("propwash.race.edit.status.blocked").withStyle(ChatFormatting.RED)
                : result == Result.RESCAN_FAILED ? lastRescan
                : message(result, gate);
        if (message != null) {
            player.sendOverlayMessage(message);
        }
    }

    static void onSignal(RacePayloads.GateSignalC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        Entity entity = player.level().getEntity(payload.entityId());
        GateEntity gate = entity instanceof GateEntity found ? found : null;
        Result result = gate == null ? Result.MISSING : signal(player, gate, GateSignal.unpack(payload.signal()));
        Component message = message(result, gate);
        if (message != null) {
            player.sendOverlayMessage(message);
        }
    }

    public static Result signal(ServerPlayer player, GateEntity gate, GateSignal signal) {
        if (!gate.kind().signals()) {
            return Result.MISSING;
        }
        if (!holdsTool(player)) {
            return Result.NEED_TOOL;
        }
        Result denied = access(player, gate, EDIT_REACH);
        if (denied != null) {
            return denied;
        }
        if (!signal.equals(gate.signal())) {
            gate.setSignal(signal);
            gate.gameEvent(GameEvent.ENTITY_INTERACT, player);
        }
        return Result.SIGNAL_SET;
    }

    public static MutableComponent describeSignal(GateSignal signal) {
        if (!signal.enabled()) {
            return Component.translatable("propwash.race.signal.off");
        }
        MutableComponent pulse = Component.translatable("propwash.race.signal.on", signal.pulseTicks(), GateSignal.secondsText(signal.pulseTicks()));
        return signal.raceOnly() ? Component.translatable("propwash.race.signal.race_only", pulse) : pulse;
    }

    public static boolean holdsTool(Player player) {
        return player.getMainHandItem().getItem() instanceof GateItem || player.getOffhandItem().getItem() instanceof GateItem
                || CourseToolItem.isCourseTool(player.getMainHandItem()) || CourseToolItem.isCourseTool(player.getOffhandItem());
    }

    private static @Nullable Result access(ServerPlayer player, GateEntity gate, double reach) {
        if (gate.isRemoved() || gate.level() != player.level()) {
            return Result.MISSING;
        }
        if (!player.isWithinEntityInteractionRange(gate, reach)) {
            return Result.TOO_FAR;
        }
        if (!player.mayBuild()) {
            return Result.NOT_ALLOWED;
        }
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(registry.courseOf(gate.getUUID()));
        if (course != null && !CourseRegistry.canEdit(player, course)) {
            return Result.NOT_OWNER;
        }
        return null;
    }

    public static Result rotate(ServerPlayer player, GateEntity gate, boolean changeMount) {
        return rotate(player, gate, changeMount, GatePlacement.QUARTER);
    }

    public static Result rotate(ServerPlayer player, GateEntity gate, boolean changeMount, float step) {
        Result denied = access(player, gate, EXTRA_REACH);
        if (denied != null) {
            return denied;
        }
        boolean stacked = gate.inStack();
        if (!changeMount && turnsWithColumn(gate)) {
            float valid = GatePlacement.validStep(step);
            float heading = gate.geometry().heading();
            ColumnTurn turn = columnTurn(gate, heading + valid);
            if (turn != null && turn.halfOnly()) {
                turn = valid >= GatePlacement.QUARTER || gate.linked() ? columnTurn(gate, heading + 2.0F * GatePlacement.QUARTER) : null;
                if (turn == null) {
                    return Result.STACK_TURN_LOCKED;
                }
            }
            if (turn != null) {
                return applyTurn(player, turn, Result.ROTATED);
            }
        }
        if (gate.linked() && !changeMount && gate.kind() == CourseElement.GATE) {
            return apply(player, gate, turnedInPlace(gate.geometry()), gate.base(), Result.ROTATED);
        }
        GateGeometry next = GatePlacement.rotated(gate.geometry(), changeMount, stacked, step);
        if (next == null) {
            return Result.STACK_LOCKED;
        }
        GateStacking.Base base = stacked ? gate.base() : GateStacking.placedBase(next);
        return apply(player, gate, next, base, Result.ROTATED);
    }

    public static GateGeometry turnedInPlace(GateGeometry gate) {
        boolean flip = gate.mount().reversible();
        float yaw = flip ? gate.yawDegrees() : GateGeometry.snapYaw(gate.yawDegrees() + 180.0F);
        return new GateGeometry(gate.x(), gate.y(), gate.z(), yaw, gate.width(), gate.height(), gate.frame(), gate.depth(), gate.mount(),
                flip != gate.flipped(), gate.tilt());
    }

    public static Result edit(ServerPlayer player, GateEntity gate, float heading, float pitch, float roll, double openingWidth,
                              double openingHeight) {
        return edit(player, gate, heading, pitch, roll, openingWidth, openingHeight, gate.variant());
    }

    public static Result edit(ServerPlayer player, GateEntity gate, float heading, float pitch, float roll, double openingWidth,
                              double openingHeight, int variant) {
        return edit(player, gate, heading, pitch, roll, openingWidth, openingHeight, variant, gate.score(), 0.0, 0.0, 0.0);
    }

    public static boolean nudgeAllowed(double dx, double dy, double dz) {
        return Double.isFinite(dx) && Double.isFinite(dy) && Double.isFinite(dz) && Math.abs(dx) <= MAX_NUDGE + 1.0E-6
                && Math.abs(dy) <= MAX_NUDGE + 1.0E-6 && Math.abs(dz) <= MAX_NUDGE + 1.0E-6;
    }

    public static boolean scoreAllowed(GateEntity gate, ScoreArea score) {
        return !(gate.kind() == CourseElement.GATE_POLE && gate.inCourse() && !score.normalized(gate.kind()).scoring());
    }

    public static Result edit(ServerPlayer player, GateEntity gate, float heading, float pitch, float roll, double openingWidth,
                              double openingHeight, int variant, ScoreArea score, double dx, double dy, double dz) {
        Result denied = access(player, gate, EDIT_REACH);
        if (denied != null) {
            return denied;
        }
        if (!nudgeAllowed(dx, dy, dz)) {
            return Result.NUDGE_LIMIT;
        }
        if (!scoreAllowed(gate, score)) {
            return Result.SCORE_LOCKED;
        }
        GateGeometry current = gate.geometry();
        GateGeometry next = preview(gate, heading, pitch, roll, openingWidth, openingHeight, variant, score, dx, dy, dz);
        if (next.sameAs(current)) {
            return Result.EDITED;
        }
        if (next.withScore(current.score()).sameAs(current) && !gate.freeform()) {
            gate.setScore(next.score());
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null && registry.courseOf(gate.getUUID()) != GateEntity.NO_COURSE) {
                registry.gateRotated(gate);
            }
            gate.gameEvent(GameEvent.ENTITY_INTERACT, player);
            return Result.EDITED;
        }
        if (gate.inStack() && !GatePoleLinks.keepsRiders(current, next)) {
            ColumnTurn turn = turnsWithColumn(gate) && headingOnly(current, next) ? columnTurn(gate, next.heading(), next.score()) : null;
            return turn == null ? Result.STACK_LOCKED : applyTurn(player, turn, Result.EDITED);
        }
        return apply(player, gate, next, GateStacking.placedBase(next), Result.EDITED);
    }

    public static Result shortenReach(ServerPlayer player, GateEntity gate, double value) {
        GateGeometry current = gate.geometry();
        CourseElement kind = gate.kind();
        boolean byScore = kind == CourseElement.GATE_POLE;
        ScoreArea score = byScore ? gate.score().withReach(value) : gate.score();
        double width = kind.innerWidth(byScore ? current.width() : value);
        return edit(player, gate, current.heading(), 0.0F, 0.0F, width, kind.innerHeight(current.height()), gate.variant(), score, 0.0, 0.0, 0.0);
    }

    public static boolean turnsWithColumn(GateEntity gate) {
        return gate.kind() == CourseElement.GATE && gate.stackable() && (gate.supported() || gate.covered()) && gate.mount().turns() == 4;
    }

    public static boolean headingOnly(GateGeometry current, GateGeometry next) {
        return next.sameAs(GatePlacement.turnedTo(current, next.heading()).withScore(next.score()));
    }

    public static @Nullable ColumnTurn columnTurn(GateEntity gate, float heading) {
        return columnTurn(gate, heading, gate.score());
    }

    public static @Nullable ColumnTurn columnTurn(GateEntity gate, float heading, ScoreArea score) {
        List<GateEntity> column = gate.stack();
        int index = column.indexOf(gate);
        if (index < 0) {
            return null;
        }
        List<GateGeometry> geometries = new ArrayList<>(column.size());
        for (GateEntity member : column) {
            geometries.add(member.geometry());
        }
        GateGeometry current = geometries.get(index);
        List<GateGeometry> next = new ArrayList<>(GateStacking.turned(geometries, index, heading));
        next.set(0, next.getFirst().withScore(score));
        List<GateGeometry> after = new ArrayList<>(geometries.subList(0, index));
        after.addAll(next);
        List<GateStacking.Base> bases = new ArrayList<>(next.size());
        for (int i = index; i < after.size(); i++) {
            bases.add(GateStacking.base(after, i));
        }
        return new ColumnTurn(List.copyOf(column.subList(index, column.size())), List.copyOf(next), List.copyOf(bases),
                GateTilt.wrap(next.getFirst().heading() - current.heading()));
    }

    public static @Nullable Result turnRefusal(Level level, ColumnTurn turn) {
        if (turn.halfOnly() && !GateStacking.halfTurn(turn.delta())) {
            return Result.STACK_TURN_LOCKED;
        }
        List<GateEntity> moving = turn.gates();
        for (int i = 0; i < moving.size(); i++) {
            GateGeometry next = turn.next().get(i);
            if (!GateItem.insideWorld(level, next)) {
                return Result.BLOCKED;
            }
            List<GateGeometry> others = new ArrayList<>(columnSkipping(level, next, moving::contains));
            for (int j = 0; j < moving.size(); j++) {
                if (j != i) {
                    others.add(turn.next().get(j));
                }
            }
            GateStacking.Refusal refusal = GateStacking.refusal(next, others);
            if (refusal != GateStacking.Refusal.NONE) {
                return refusal == GateStacking.Refusal.TOO_HIGH ? Result.STACK_TOO_HIGH : Result.STACK_TOO_WIDE;
            }
            GateEntity gate = moving.get(i);
            GateGeometry placed = gate.linked() ? next.withJoints(GateStacking.placementJoints(next, GateItem.neighbourGeometries(level, next, gate)))
                    : next;
            if (!GateItem.freeSkipping(level, placed, turn.bases().get(i), true, moving::contains)) {
                return Result.BLOCKED;
            }
        }
        return null;
    }

    private static Result applyTurn(ServerPlayer player, ColumnTurn turn, Result done) {
        for (GateEntity gate : turn.gates()) {
            Result denied = access(player, gate, Double.MAX_VALUE);
            if (denied != null) {
                return denied;
            }
        }
        ServerLevel level = player.level();
        Result refused = turnRefusal(level, turn);
        if (refused != null) {
            return refused;
        }
        List<GateEntity> moving = turn.gates();
        for (int i = 0; i < moving.size(); i++) {
            moving.get(i).applyGeometry(turn.next().get(i));
        }
        CourseRegistry registry = CourseRegistry.get();
        for (GateEntity gate : moving) {
            gate.refreshColumn();
            if (gate.linked()) {
                GateItem.refreshAround(gate);
            }
            if (registry != null && registry.courseOf(gate.getUUID()) != GateEntity.NO_COURSE) {
                registry.gateRotated(gate);
            }
        }
        GateGeometry first = turn.next().getFirst();
        level.playSound(null, first.centerX(), first.centerY(), first.centerZ(), SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0F, 0.8F);
        moving.getFirst().gameEvent(GameEvent.ENTITY_INTERACT, player);
        return done;
    }

    public static GateGeometry preview(GateEntity gate, float heading, float pitch, float roll, double openingWidth, double openingHeight) {
        return preview(gate, heading, pitch, roll, openingWidth, openingHeight, gate.variant());
    }

    public static GateGeometry preview(GateEntity gate, float heading, float pitch, float roll, double openingWidth, double openingHeight,
                                       int variant) {
        return preview(gate, heading, pitch, roll, openingWidth, openingHeight, variant, gate.score(), 0.0, 0.0, 0.0);
    }

    public static GateGeometry preview(GateEntity gate, float heading, float pitch, float roll, double openingWidth, double openingHeight,
                                       int variant, ScoreArea score, double dx, double dy, double dz) {
        CourseElement kind = gate.kind();
        if (kind == CourseElement.FREEFORM) {
            return gate.geometry().withFreeformYaw(heading).withVariant(variant).withScore(score);
        }
        GateGeometry edited;
        if (!kind.gate()) {
            edited = GatePlacement.edited(gate.geometry().withVariant(variant), heading, 0.0F, 0.0F, kind.outerWidth(openingWidth),
                    kind.outerHeight(openingHeight));
        } else {
            GateSize size = gate.size();
            edited = GatePlacement.edited(gate.geometry(), heading, pitch, roll, size.outer(openingWidth), size.outer(openingHeight));
        }
        return edited.withScore(score).moved(dx, dy, dz);
    }

    public static MutableComponent scoreName(ScoreArea score) {
        if (!score.scoring()) {
            return Component.translatable("propwash.race.score.role.none");
        }
        return Component.translatable("propwash.race.score." + score.role().key() + "." + ScoreArea.sideKey(score.side()));
    }

    static boolean fits(ServerLevel level, GateEntity gate, GateGeometry next, GateStacking.Base base) {
        if (!GateItem.insideWorld(level, next)) {
            return false;
        }
        if (!gate.linked() || next.freeform() || !next.gate()) {
            return GateItem.free(level, next, base, true, gate);
        }
        if (next.mount() == GateMount.FLOOR_FLAT) {
            return GateItem.lidFree(level, next, gate);
        }
        GateGeometry joined = next.withJoints(GateStacking.placementJoints(next, GateItem.neighbourGeometries(level, next, gate)));
        return GateItem.free(level, joined, base, true, gate);
    }

    public static List<GateGeometry> column(Level level, GateGeometry gate, @Nullable GateEntity ignore) {
        return columnSkipping(level, gate, other -> other == ignore);
    }

    public static List<GateGeometry> columnSkipping(Level level, GateGeometry gate, Predicate<GateEntity> skip) {
        AABB area = new AABB(gate.x() - GateStacking.EPSILON, gate.y() - COLUMN_REACH, gate.z() - GateStacking.EPSILON,
                gate.x() + GateStacking.EPSILON, gate.y() + gate.height() + COLUMN_REACH, gate.z() + GateStacking.EPSILON);
        List<GateGeometry> found = new ArrayList<>();
        for (GateEntity other : level.getEntitiesOfClass(GateEntity.class, area, other -> !skip.test(other) && !other.isRemoved())) {
            if (other.stackable()) {
                found.add(other.geometry());
            }
        }
        return found;
    }

    public static GateStacking.Refusal stackRefusal(Level level, GateGeometry gate, @Nullable GateEntity ignore) {
        if (!GateStacking.stackable(gate)) {
            return GateStacking.Refusal.NONE;
        }
        return GateStacking.refusal(gate, column(level, gate, ignore));
    }

    public static MutableComponent stackRefusalText(GateStacking.Refusal refusal) {
        return refusal == GateStacking.Refusal.TOO_HIGH ? Component.translatable("propwash.race.gate.stack_too_high", GateStacking.MAX_STACK)
                : Component.translatable("propwash.race.gate.stack_too_wide");
    }

    private static Result apply(ServerPlayer player, GateEntity gate, GateGeometry next, GateStacking.Base base, Result done) {
        ServerLevel level = player.level();
        GateStacking.Refusal refusal = stackRefusal(level, next, gate);
        if (refusal != GateStacking.Refusal.NONE) {
            return refusal == GateStacking.Refusal.TOO_HIGH ? Result.STACK_TOO_HIGH : Result.STACK_TOO_WIDE;
        }
        if (!fits(level, gate, next, base)) {
            return Result.BLOCKED;
        }
        gate.applyGeometry(next);
        gate.refreshColumn();
        if (gate.linked()) {
            GateItem.refreshAround(gate);
        }
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null && registry.courseOf(gate.getUUID()) != GateEntity.NO_COURSE) {
            registry.gateRotated(gate);
        }
        level.playSound(null, next.centerX(), next.centerY(), next.centerZ(), SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0F, 0.8F);
        gate.gameEvent(GameEvent.ENTITY_INTERACT, player);
        return done;
    }

    public static Result rescan(ServerPlayer player, GateEntity gate) {
        return rescan(player, gate, gate.score().open());
    }

    public static Result rescan(ServerPlayer player, GateEntity gate, boolean ignoreGaps) {
        Result denied = access(player, gate, EDIT_REACH);
        if (denied != null) {
            return denied;
        }
        GateGeometry current = gate.geometry();
        if (!current.freeform()) {
            return Result.MISSING;
        }
        ServerLevel level = player.level();
        FreeformFill.Result result = FreeformBlocks.rescan(level, current, ignoreGaps);
        GateEntity occupant = null;
        if (result.ok()) {
            int[] seed = FreeformBlocks.seed(current);
            occupant = FreeformBlocks.occupant(level, seed[0], seed[1], seed[2], gate);
        }
        if (!result.ok() || occupant != null) {
            lastRescan = (occupant != null ? Component.translatable("propwash.race.freeform.taken") : FreeformGateItem.problem(result))
                    .withStyle(ChatFormatting.RED);
            return Result.RESCAN_FAILED;
        }
        GateGeometry next = GateGeometry.freeform(result.anchorX(), result.anchorY(), result.anchorZ(), result.region(), current.variant(),
                current.score().withOpen(ignoreGaps), current.yawDegrees());
        if (!next.sameAs(current)) {
            gate.applyGeometry(next);
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null && registry.courseOf(gate.getUUID()) != GateEntity.NO_COURSE) {
                registry.gateRotated(gate);
            }
        }
        level.playSound(null, next.centerX(), next.centerY(), next.centerZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.4F);
        gate.gameEvent(GameEvent.ENTITY_INTERACT, player);
        return Result.RESCANNED;
    }

    public static Result unlink(ServerPlayer player, GateEntity gate) {
        Result denied = access(player, gate, EDIT_REACH);
        if (denied != null) {
            return denied;
        }
        if (!CourseToolItem.isCourseTool(player.getMainHandItem()) && !CourseToolItem.isCourseTool(player.getOffhandItem())) {
            return Result.NEED_TOOL;
        }
        CourseRegistry registry = CourseRegistry.get();
        int current = registry == null ? GateEntity.NO_COURSE : registry.courseOf(gate.getUUID());
        CourseRegistry.Course course = registry == null ? null : registry.course(current);
        if (course == null || !registry.removeGate(gate.getUUID())) {
            return Result.NOT_IN_COURSE;
        }
        registry.claim(course, player);
        gate.setCourse(GateEntity.NO_COURSE, GateEntity.NO_INDEX);
        gate.level().playSound(null, gate.getX(), gate.getY() + 1.0, gate.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 0.5F);
        lastUnlinked = current;
        return Result.UNLINKED;
    }

    public static @Nullable Component message(Result result, @Nullable GateEntity gate) {
        return switch (result) {
            case ROTATED -> gate == null ? null : Component.translatable("propwash.race.mount.rotated", describe(gate.geometry()));
            case EDITED -> gate == null ? null : Component.translatable("propwash.race.edit.done", describe(gate.geometry()));
            case UNLINKED -> Component.translatable("propwash.race.tool.removed", lastUnlinked).withStyle(ChatFormatting.YELLOW);
            case RESCANNED -> gate == null ? null : Component.translatable("propwash.race.freeform.rescanned", describe(gate.geometry()))
                    .withStyle(ChatFormatting.GREEN);
            case RESCAN_FAILED -> lastRescan;
            case SIGNAL_SET -> gate == null ? null : Component.translatable("propwash.race.signal.saved", describeSignal(gate.signal()))
                    .withStyle(ChatFormatting.GREEN);
            case NEED_TOOL -> Component.translatable("propwash.race.mount.need_tool").withStyle(ChatFormatting.RED);
            case NOT_IN_COURSE -> Component.translatable("propwash.race.tool.not_in_course");
            case MISSING -> null;
            case TOO_FAR -> Component.translatable("propwash.race.mount.too_far").withStyle(ChatFormatting.RED);
            case NOT_ALLOWED -> Component.translatable("propwash.race.mount.not_allowed").withStyle(ChatFormatting.RED);
            case NOT_OWNER -> {
                CourseRegistry registry = CourseRegistry.get();
                CourseRegistry.Course course = registry == null || gate == null ? null : registry.course(registry.courseOf(gate.getUUID()));
                yield course == null ? null : CourseRegistry.notOwner(course)
                        .withStyle(ChatFormatting.RED);
            }
            case STACK_LOCKED -> Component.translatable(gate != null && gate.carrying() ? "propwash.race.pole.locked"
                    : gate != null && gate.linked() ? "propwash.race.row.locked" : "propwash.race.mount.stack_locked").withStyle(ChatFormatting.RED);
            case NUDGE_LIMIT -> Component.translatable("propwash.race.nudge.limit", blocks(MAX_NUDGE)).withStyle(ChatFormatting.RED);
            case SCORE_LOCKED -> Component.translatable("propwash.race.score.pole.in_course").withStyle(ChatFormatting.RED);
            case BLOCKED -> Component.translatable("propwash.race.mount.blocked").withStyle(ChatFormatting.RED);
            case STACK_TOO_WIDE -> stackRefusalText(GateStacking.Refusal.TOO_WIDE).withStyle(ChatFormatting.RED);
            case STACK_TOO_HIGH -> stackRefusalText(GateStacking.Refusal.TOO_HIGH).withStyle(ChatFormatting.RED);
            case STACK_TURN_LOCKED -> Component.translatable("propwash.race.stack.turn_locked").withStyle(ChatFormatting.RED);
        };
    }

    public static MutableComponent mountName(GateMount mount) {
        return Component.translatable("propwash.race.mount." + mount.key());
    }

    public static MutableComponent surfaceName(GateMount.Surface surface) {
        return Component.translatable("propwash.race.mount.surface." + surface.key());
    }

    public static String directionKey(GateGeometry gate) {
        if (Math.abs(gate.normalY()) > 0.5) {
            return gate.normalY() < 0.0 ? "down" : "up";
        }
        if (gate.mount() == GateMount.WALL_WINDOW) {
            return gate.flipped() ? "out" : "in";
        }
        double nx = gate.normalX();
        double nz = gate.normalZ();
        if (Math.abs(nx) > Math.abs(nz)) {
            return nx > 0.0 ? "east" : "west";
        }
        return nz > 0.0 ? "south" : "north";
    }

    public static String turnKey(GateMount mount, int turn) {
        int step = Math.floorMod(turn, mount.turns());
        return switch (mount) {
            case STANDING, HANGING -> switch (step) {
                case 1 -> "right";
                case 2 -> "back";
                case 3 -> "left";
                default -> "ahead";
            };
            case WALL_SIDE -> step == 1 ? "left" : "right";
            case WALL_WINDOW -> step == 1 ? "out" : "in";
            case FLOOR_FLAT, CEILING_FLAT, WALL_DIVE -> step == 1 ? "up" : "down";
        };
    }

    public static MutableComponent elementName(CourseElement kind) {
        return Component.translatable("propwash.race.element." + kind.key());
    }

    public static MutableComponent variantName(CourseElement kind, int variant) {
        if (kind == CourseElement.GATE_POLE) {
            return Component.translatable("color.minecraft." + kind.variantKey(variant));
        }
        return Component.translatable("propwash.race.element." + kind.key() + "." + kind.variantKey(variant));
    }

    public static MutableComponent variantName(GateGeometry gate) {
        if (gate.freeform()) {
            return Component.translatable("propwash.race.mount.dir." + directionKey(gate));
        }
        return variantName(gate.kind(), gate.variant());
    }

    public static MutableComponent freeformSize(GateGeometry gate) {
        FreeformRegion region = gate.region();
        if (region == null) {
            return Component.empty();
        }
        return Component.translatable("propwash.race.freeform.size", region.width(), region.height(), region.cells());
    }

    public static MutableComponent describe(GateGeometry gate) {
        if (gate.freeform()) {
            return Component.translatable("propwash.race.element.pose", elementName(gate.kind()), freeformSize(gate), variantName(gate));
        }
        if (!gate.gate()) {
            return Component.translatable("propwash.race.element.pose", elementName(gate.kind()), variantName(gate.kind(), gate.variant()),
                    Component.translatable("propwash.race.element.heading", degrees(gate.heading())));
        }
        GateTilt tilt = gate.tilt();
        if (tilt.none()) {
            return describeDirection(gate);
        }
        MutableComponent pose = Component.translatable("propwash.race.mount.pose", surfaceName(gate.mount().surface()), mountName(gate.mount()),
                Component.translatable("propwash.race.mount.yaw", degrees(gate.heading())));
        if (tilt.pitch() != 0.0F) {
            pose = Component.translatable("propwash.race.mount.pose_more", pose, Component.translatable("propwash.race.mount.pitch", degrees(tilt.pitch())));
        }
        if (tilt.roll() != 0.0F) {
            pose = Component.translatable("propwash.race.mount.pose_more", pose, Component.translatable("propwash.race.mount.roll", degrees(tilt.roll())));
        }
        return pose;
    }

    public static MutableComponent describeDirection(GateGeometry gate) {
        return Component.translatable("propwash.race.mount.pose", surfaceName(gate.mount().surface()), mountName(gate.mount()),
                Component.translatable("propwash.race.mount.dir." + directionKey(gate)));
    }

    public static MutableComponent describe(GateMount mount, int turn) {
        return Component.translatable("propwash.race.mount.pose", surfaceName(mount.surface()), mountName(mount),
                Component.translatable("propwash.race.mount.dir." + turnKey(mount, turn)));
    }

    public static MutableComponent describe(GateMount mount, int turn, float fine) {
        if (fine == 0.0F) {
            return describe(mount, turn);
        }
        float relative = GateTilt.wrap(turn * GatePlacement.QUARTER + fine);
        return Component.translatable("propwash.race.mount.pose", surfaceName(mount.surface()), mountName(mount),
                Component.translatable("propwash.race.mount.dir.angle", signedDegrees(relative)));
    }

    public static String degrees(float value) {
        float rounded = Math.round(value * GateTilt.RESOLUTION) / GateTilt.RESOLUTION;
        if (rounded == Math.rint(rounded)) {
            return Integer.toString((int) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    public static String blocks(double value) {
        double rounded = Math.round(value * 32.0) / 32.0;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        String text = String.format(Locale.ROOT, "%.5f", rounded);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    public static String signedBlocks(double value) {
        String text = blocks(value);
        return value > 0.0 ? "+" + text : text;
    }

    public static String signedDegrees(float value) {
        String text = degrees(value);
        return value > 0.0F ? "+" + text : text;
    }
}
