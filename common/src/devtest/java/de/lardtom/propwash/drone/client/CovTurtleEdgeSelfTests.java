package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public final class CovTurtleEdgeSelfTests {
    private static final String RUN = "run";
    private static final double BEYOND = 0.06;
    private static final double INSIDE = 0.05;
    private static final double FALLEN = 1.0;
    private static final double ON_TOP = 0.5;
    private static final int PILLAR_HEIGHT = 3;
    private static final int CLIFF_HEIGHT = 7;
    private static final int FALL_TICKS = 60;
    private static final double VIEW_OUT = 2.5;
    private static final double VIEW_SIDE = 3.5;
    private static final String WINDOW_BACK = WaterproofTurtleSelfTests.TURTLE + "Kanten – Fenster zurückstellen";

    private CovTurtleEdgeSelfTests() {
    }

    enum Site {
        GATE_EDGE("der Kante eines Gate-Balkens", "gate", true),
        BLOCK_EDGE("einer Blockkante in 3 m Höhe", "block", false),
        GATE_MIDDLE("einem Gate-Balken, mittig", "gate_middle", true),
        SINGLE_BLOCK("einem einzelnen Block", "single_block", false),
        PILLAR("einer Säule 1×1, 3 m hoch", "pillar", false),
        PILLAR_CORNER("der Ecke einer Säule 1×1", "pillar_corner", false),
        CLIFF("einer Klippe, 7 m hoch", "cliff", false),
        SLAB("einer Stufe aus Halbblöcken", "slab", false),
        STAIRS("einer Treppenstufe", "stairs", false),
        FENCE("einem Zaunpfosten", "fence", false),
        WALL("einer Mauer", "wall", false),
        CUBE_EDGE("dem oberen Balken eines Dive-Cubes", "cube", true),
        POLE_TOP("der Spitze einer Slalomstange", "pole", true);

        final String where;
        final String file;
        final boolean entity;

        Site(String where, String file, boolean entity) {
            this.where = where;
            this.file = file;
            this.entity = entity;
        }
    }

    enum Sticks {
        CENTRED,
        THROTTLE,
        TOWARD_EDGE
    }

    public static void register() {
        String turtle = WaterproofTurtleSelfTests.TURTLE;
        if (Screenshots.enabled()) {
            SelfTest.switchStep(turtle + "Kanten – Fenster 1920×1080 für die Bildschirmfotos", WINDOW_BACK, 2400, ctx -> {
                StepResult sized = Screenshots.resizeWindow(ctx);
                if (sized.status() == StepResult.Status.PASS) {
                    Screenshots.hideMarker(true);
                }
                return sized;
            });
        }
        SelfTest.clientStep(turtle + "kopfüber auf der Kante eines Gate-Balkens, Knüppel mittig – kippt herunter, rollt nicht endlos", 2400,
                ctx -> run(ctx, Site.GATE_EDGE, Sticks.CENTRED));
        SelfTest.clientStep(turtle + "kopfüber auf einer Blockkante, Knüppel mittig – kippt herunter, liegt am Boden still", 2400,
                ctx -> run(ctx, Site.BLOCK_EDGE, Sticks.CENTRED));
        SelfTest.clientStep(turtle + "kopfüber mitten auf einem Gate-Balken – Gate trägt wie Boden, Roll dreht sie um, "
                + "bleibt oben liegen", 2400,
                ctx -> run(ctx, Site.GATE_MIDDLE, Sticks.CENTRED));
        Site[] edges = {Site.SINGLE_BLOCK, Site.PILLAR, Site.PILLAR_CORNER, Site.CLIFF, Site.SLAB, Site.STAIRS, Site.FENCE, Site.WALL,
                Site.GATE_EDGE, Site.BLOCK_EDGE, Site.CUBE_EDGE, Site.POLE_TOP};
        for (Site site : edges) {
            String start = turtle + "Kanten: kopfüber auf " + site.where;
            if (site != Site.GATE_EDGE && site != Site.BLOCK_EDGE) {
                SelfTest.clientStep(start + ", Schwerpunkt über der Kante, Knüppel mittig – kippt herunter, liegt unten still", 2400,
                        ctx -> run(ctx, site, Sticks.CENTRED));
            }
            if (site == Site.PILLAR_CORNER) {
                continue;
            }
            SelfTest.clientStep(start + ", Schwerpunkt über der Kante, Knüppel mittig mit Vollgas – kippt herunter, liegt unten still", 2400,
                    ctx -> run(ctx, site, Sticks.THROTTLE));
            SelfTest.clientStep(start + ", Schwerpunkt auf der Auflage, Knüppel zur Kante mit Vollgas – rollt herunter oder dreht sich auf",
                    2400, ctx -> run(ctx, site, Sticks.TOWARD_EDGE));
        }
        if (Screenshots.enabled()) {
            SelfTest.clientStep(WINDOW_BACK, 2400, ctx -> {
                Screenshots.hideMarker(false);
                return Screenshots.restoreWindow(ctx);
            });
        }
    }

    private static StepResult run(SelfTest.StepContext ctx, Site site, Sticks sticks) {
        Run run = (Run) ctx.state().computeIfAbsent(RUN, key -> new Run(site, sticks));
        try {
            StepResult result = run.tick(ctx);
            if (result.status() != StepResult.Status.PENDING) {
                run.cleanup(ctx);
            }
            return result;
        } catch (RuntimeException exception) {
            run.cleanup(ctx);
            throw exception;
        }
    }

    record Phase(String name, Consumer<Run> enter, Function<Run, @Nullable String> check, int stableTicks, int maxTicks) {
        static Phase of(String name, Consumer<Run> enter, Function<Run, @Nullable String> check) {
            return new Phase(name, enter, check, 8, 200);
        }

        Phase stable(int ticks) {
            return new Phase(this.name, this.enter, this.check, ticks, this.maxTicks);
        }

        Phase limit(int ticks) {
            return new Phase(this.name, this.enter, this.check, this.stableTicks, ticks);
        }
    }

    record Saved(Vec3 pos, float yRot, float xRot) {
    }

    static final class Run {
        final Site site;
        final Sticks sticks;
        final List<Phase> phases;
        final Mode3dSelfTests.Script script = new Mode3dSelfTests.Script(Mode3dSelfTests.Kind.INVERTED, List.of());
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final List<CompletableFuture<String>> shots = new ArrayList<>();
        final Set<String> taken = new HashSet<>();
        volatile int gateId = -1;
        volatile double top = Double.NaN;
        volatile double drop = Double.NaN;
        volatile double outX;
        volatile double outZ = 1.0;
        volatile boolean alongX;
        volatile @Nullable Saved saved;
        int index = -2;
        int since;
        int stableSince = -1;
        int now;
        int armedAt = -1;
        double highest = Double.NEGATIVE_INFINITY;
        double lastTilt = Double.NaN;
        double lastY = Double.NaN;
        double startX;
        double startZ;
        boolean rolled;
        boolean finalShot;
        StickPositions flip = StickPositions.IDLE;
        @Nullable CompletableFuture<?> pending;
        SelfTest.@Nullable StepContext context;
        boolean cleaned;

        Run(Site site, Sticks sticks) {
            this.site = site;
            this.sticks = sticks;
            this.phases = site == Site.GATE_MIDDLE ? middlePhases() : sticks == Sticks.TOWARD_EDGE ? rollPhases() : edgePhases(sticks);
        }

        int ticks() {
            return this.now - this.since;
        }

        double fallen() {
            return Double.isNaN(this.drop) ? FALLEN : Math.min(FALLEN, 0.5 * this.drop);
        }

        boolean down() {
            return this.top - comY() > this.fallen();
        }

        StepResult tick(SelfTest.StepContext ctx) {
            this.context = ctx;
            this.now = ctx.tick();
            CompletableFuture<?> waiting = this.pending;
            if (waiting != null) {
                if (waiting.isCompletedExceptionally()) {
                    return StepResult.fail(this.label() + ": Server-Aktion fehlgeschlagen: " + waiting.exceptionNow().getMessage());
                }
                if (!waiting.isDone()) {
                    return StepResult.pending();
                }
                this.pending = null;
            }
            PilotSession session = PilotSession.INSTANCE;
            if (this.index == -2) {
                this.index = -1;
                this.since = this.now;
                session.clearTestInput();
                session.resetFlightModes();
                session.setTestTurtle(true);
                this.pending = ctx.onServer(this::setUp);
                return StepResult.pending();
            }
            this.aim();
            if (this.index == -1) {
                QuadcopterEntity drone = session.drone();
                if (drone == null || drone.getId() != this.script.droneId || !session.isActive()) {
                    return this.ticks() > 300 ? StepResult.fail("keine Sitzung mit der Testdrohne") : StepResult.pending();
                }
                this.flip = this.site == Site.GATE_MIDDLE ? this.flipStick(session) : this.towardStick(session);
                this.startX = drone.getX();
                this.startZ = drone.getZ();
                ctx.log(String.format(Locale.ROOT, "%s %s: Oberkante %.3f, Fallhöhe %.2f, Kante nach x %.2f z %.2f, Drohne bei %s, Knüppel %s",
                        this.site, this.sticks, this.top, this.drop, this.outX, this.outZ, drone.position(), this.flip));
                this.enter(0);
                return StepResult.pending();
            }
            if (this.index >= this.phases.size()) {
                return this.finish(ctx);
            }
            if (!session.isActive()) {
                return StepResult.fail(this.label() + ": Sitzung verloren");
            }
            Telemetry seen = Mode3dSelfTests.telemetry();
            if (this.armedAt < 0 && seen != null && seen.armed && seen.turtle) {
                this.armedAt = this.now;
                ctx.log(this.label() + ": Turtle scharf, Schwerpunkt " + fmt(comY() - this.top) + " über der Oberkante, " + state());
            }
            this.highest = Math.max(this.highest, comY());
            Phase phase = this.phases.get(this.index);
            String problem = phase.check().apply(this);
            this.lastTilt = tilt();
            this.lastY = comY();
            if (this.pending != null) {
                return StepResult.pending();
            }
            if (problem != null) {
                this.stableSince = -1;
                return this.ticks() > phase.maxTicks() ? StepResult.fail(this.label() + ": " + problem) : StepResult.pending();
            }
            if (this.stableSince < 0) {
                this.stableSince = this.now;
            }
            if (this.now - this.stableSince < phase.stableTicks()) {
                return StepResult.pending();
            }
            QuadcopterEntity flown = session.drone();
            String moved = flown == null ? "" : ", versetzt um x " + fmt(flown.getX() - this.startX) + ", z " + fmt(flown.getZ() - this.startZ);
            ctx.log(this.label() + " nach " + (this.stableSince - this.since) + " Ticks, Schwerpunkt " + fmt(comY() - this.top)
                    + " über der Oberkante" + moved + ", " + state());
            this.enter(this.index + 1);
            return this.index >= this.phases.size() ? this.finish(ctx) : StepResult.pending();
        }

        private StepResult finish(SelfTest.StepContext ctx) {
            if (!this.finalShot && this.sticks != Sticks.TOWARD_EDGE) {
                this.finalShot = true;
                this.photo(this.down() ? "_down" : "_up");
            }
            for (CompletableFuture<String> shot : this.shots) {
                if (!shot.isDone()) {
                    return StepResult.pending();
                }
            }
            for (CompletableFuture<String> shot : this.shots) {
                if (shot.isCompletedExceptionally()) {
                    return StepResult.fail("Bildschirmfoto: " + shot.exceptionNow());
                }
            }
            this.shots.clear();
            return StepResult.pass();
        }

        void photo(String suffix) {
            if (!Screenshots.enabled() || this.sticks == Sticks.THROTTLE) {
                return;
            }
            String name = "turtle_edge_" + this.site.file + (this.sticks == Sticks.TOWARD_EDGE ? "_toward_edge" : "") + suffix;
            if (!this.taken.add(name)) {
                return;
            }
            Screenshots.clearOverlays(Minecraft.getInstance());
            this.shots.add(Screenshots.capture(name));
            SelfTest.StepContext ctx = this.context;
            if (ctx != null) {
                ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(Screenshots.fileName(name) + ".png"));
            }
        }

        private void aim() {
            LocalPlayer player = Minecraft.getInstance().player;
            QuadcopterEntity drone = PilotSession.INSTANCE.drone();
            if (player == null || drone == null || this.saved == null) {
                return;
            }
            Vec3 eye = player.getEyePosition();
            Vec3 target = drone.getBoundingBox().getCenter();
            double dx = target.x - eye.x;
            double dy = target.y - eye.y;
            double dz = target.z - eye.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yRotO = yaw;
            player.xRotO = pitch;
            player.setYHeadRot(yaw);
        }

        private void enter(int next) {
            this.index = next;
            this.since = this.now;
            this.stableSince = -1;
            if (next < this.phases.size()) {
                this.phases.get(next).enter().accept(this);
            }
        }

        private String label() {
            return this.site + " " + this.sticks + " "
                    + (this.index >= 0 && this.index < this.phases.size() ? this.phases.get(this.index).name() : "Start");
        }

        private StickPositions flipStick(PilotSession session) {
            Quaternionf body = session.bodyOrientation(new Quaternionf());
            Vector3f right = body.transform(new Vector3f(1.0F, 0.0F, 0.0F));
            boolean rollAlongX = Math.abs(right.x) >= Math.abs(right.z);
            return rollAlongX == this.alongX ? new StickPositions(1.0F, 0.0F, 0.0F, 0.0F) : new StickPositions(0.0F, 1.0F, 0.0F, 0.0F);
        }

        private StickPositions towardStick(PilotSession session) {
            Quaternionf body = session.bodyOrientation(new Quaternionf());
            Vector3f right = body.transform(new Vector3f(1.0F, 0.0F, 0.0F));
            Vector3f back = body.transform(new Vector3f(0.0F, 0.0F, 1.0F));
            double across = right.x * this.outX + right.z * this.outZ;
            double along = back.x * this.outX + back.z * this.outZ;
            if (Math.abs(across) >= Math.abs(along)) {
                return new StickPositions((float) -Math.signum(across), 0.0F, 0.0F, 1.0F);
            }
            return new StickPositions(0.0F, (float) Math.signum(along), 0.0F, 1.0F);
        }

        private void setUp(MinecraftServer server) {
            ServerPlayer player = Mode3dSelfTests.firstPlayer(server);
            ServerLevel level = player.level();
            BlockPos feet = player.blockPosition();
            double inset = this.sticks == Sticks.TOWARD_EDGE ? -INSIDE : BEYOND;
            Vec3 at;
            if (this.site.entity) {
                at = this.gateSite(level, Vec3.atBottomCenterOf(feet.offset(-4, 0, 9)), inset);
            } else {
                at = this.blockSite(level, feet.offset(6, 0, 9), inset);
            }
            this.saved = new Saved(player.position(), player.getYRot(), player.getXRot());
            double sideX = this.outZ;
            double sideZ = -this.outX;
            double ground = feet.getY();
            player.teleportTo(level, at.x + this.outX * VIEW_OUT + sideX * VIEW_SIDE, ground, at.z + this.outZ * VIEW_OUT + sideZ * VIEW_SIDE,
                    Set.of(), player.getYRot(), player.getXRot(), true);
            Mode3dSelfTests.spawnAndFly(server, this.script, true, PilotMode.LINE_OF_SIGHT, at, 0.0F);
        }

        private void set(ServerLevel level, BlockPos pos, BlockState state) {
            this.blocks.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }

        private Vec3 blockSite(ServerLevel level, BlockPos base, double inset) {
            int height = this.site == Site.CLIFF ? CLIFF_HEIGHT : PILLAR_HEIGHT;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = 0; dy < height + 2; dy++) {
                        this.set(level, base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            BlockState stone = Blocks.STONE.defaultBlockState();
            switch (this.site) {
                case BLOCK_EDGE -> {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = 0; dy < PILLAR_HEIGHT; dy++) {
                            this.set(level, base.offset(dx, dy, 0), stone);
                        }
                    }
                }
                case SINGLE_BLOCK -> this.set(level, base, stone);
                case PILLAR, PILLAR_CORNER -> {
                    for (int dy = 0; dy < PILLAR_HEIGHT; dy++) {
                        this.set(level, base.offset(0, dy, 0), stone);
                    }
                }
                case CLIFF -> {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -3; dz <= 0; dz++) {
                            for (int dy = 0; dy < CLIFF_HEIGHT; dy++) {
                                this.set(level, base.offset(dx, dy, dz), stone);
                            }
                        }
                    }
                }
                case SLAB -> {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 0; dz++) {
                            this.set(level, base.offset(dx, 0, dz), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
                        }
                    }
                }
                case STAIRS -> {
                    for (int dx = -1; dx <= 1; dx++) {
                        BlockState stairs = Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
                        this.set(level, base.offset(dx, 0, 0), stairs);
                        this.set(level, base.offset(dx, 0, -1), stone);
                    }
                }
                case FENCE -> this.set(level, base, Blocks.OAK_FENCE.defaultBlockState());
                case WALL -> this.set(level, base, Blocks.COBBLESTONE_WALL.defaultBlockState());
                default -> throw new IllegalStateException("kein Block-Ort: " + this.site);
            }
            AABB shape = level.getBlockState(base).getCollisionShape(level, base, CollisionContext.empty()).bounds();
            this.alongX = true;
            this.outX = 0.0;
            this.outZ = 1.0;
            double cx = base.getX() + 0.5;
            if (this.site == Site.STAIRS) {
                AABB upper = null;
                for (AABB box : level.getBlockState(base).getCollisionShape(level, base, CollisionContext.empty()).toAabbs()) {
                    if (box.maxY > 0.999 && (upper == null || box.getZsize() < upper.getZsize())) {
                        upper = box;
                    }
                }
                if (upper == null || upper.maxZ > 0.999) {
                    throw new IllegalStateException("Treppe ohne obere Stufe im Norden: " + upper);
                }
                this.top = base.getY() + 1.0;
                this.drop = 0.5;
                return new Vec3(cx, this.top, base.getZ() + upper.maxZ + inset);
            }
            this.top = base.getY() + (this.site == Site.CLIFF ? CLIFF_HEIGHT : this.site == Site.BLOCK_EDGE || this.site == Site.PILLAR
                    || this.site == Site.PILLAR_CORNER ? PILLAR_HEIGHT - 1 + shape.maxY : shape.maxY);
            this.drop = this.top - base.getY();
            if (this.site == Site.PILLAR_CORNER) {
                double diagonal = inset / Math.sqrt(2.0);
                this.outX = Math.sqrt(0.5);
                this.outZ = Math.sqrt(0.5);
                return new Vec3(base.getX() + shape.maxX + diagonal, this.top, base.getZ() + shape.maxZ + diagonal);
            }
            return new Vec3(cx, this.top, base.getZ() + shape.maxZ + inset);
        }

        private Vec3 gateSite(ServerLevel level, Vec3 base, double inset) {
            GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
            if (gate == null) {
                throw new IllegalStateException("Gate nicht erzeugt");
            }
            gate.setSize(GateSize.MEDIUM);
            if (this.site == Site.CUBE_EDGE) {
                gate.applyGeometry(GateGeometry.element(base.x, base.y, base.z, 0.0F, CourseElement.CUBE, CourseElement.EXIT_FRONT, 3.0, 3.0,
                        GateTilt.NONE));
            } else if (this.site == Site.POLE_TOP) {
                gate.applyGeometry(GateGeometry.element(base.x, base.y, base.z, 0.0F, CourseElement.POLE, CourseElement.LEFT, 4.0, 3.0,
                        GateTilt.NONE));
            } else {
                gate.snapTo(base.x, base.y, base.z, 0.0F, 0.0F);
            }
            if (!level.addFreshEntity(gate)) {
                throw new IllegalStateException("Gate nicht gespawnt");
            }
            this.gateId = gate.getId();
            AABB bar = null;
            for (AABB part : gate.colliders()) {
                if (bar == null || part.maxY > bar.maxY + 1.0E-6 || part.maxY > bar.maxY - 1.0E-6 && length(part) > length(bar)) {
                    bar = part;
                }
            }
            if (bar == null) {
                throw new IllegalStateException("Gate ohne Rahmen");
            }
            this.top = bar.maxY;
            this.drop = bar.maxY - base.y;
            this.alongX = bar.getXsize() >= bar.getZsize();
            this.outX = this.alongX ? 0.0 : 1.0;
            this.outZ = this.alongX ? 1.0 : 0.0;
            double cx = (bar.minX + bar.maxX) * 0.5;
            double cz = (bar.minZ + bar.maxZ) * 0.5;
            if (this.site == Site.GATE_MIDDLE) {
                return new Vec3(cx, this.top, cz);
            }
            return this.alongX ? new Vec3(cx, this.top, bar.maxZ + inset) : new Vec3(bar.maxX + inset, this.top, cz);
        }

        void cleanup(SelfTest.StepContext ctx) {
            if (this.cleaned) {
                return;
            }
            this.cleaned = true;
            PilotSession.INSTANCE.setTestTurtle(null);
            Mode3dSelfTests.cleanup(ctx, this.script);
            Map<BlockPos, BlockState> restore = new LinkedHashMap<>(this.blocks);
            int gate = this.gateId;
            Saved back = this.saved;
            ctx.onServer(server -> {
                ServerPlayer player = Mode3dSelfTests.firstPlayer(server);
                ServerLevel level = player.level();
                restore.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
                Entity entity = gate >= 0 ? level.getEntity(gate) : null;
                if (entity != null) {
                    entity.discard();
                }
                if (back != null) {
                    player.teleportTo(level, back.pos().x, back.pos().y, back.pos().z, Set.of(), back.yRot(), back.xRot(), true);
                }
            });
        }
    }

    private static List<Phase> edgePhases(Sticks sticks) {
        boolean throttle = sticks == Sticks.THROTTLE;
        List<Phase> out = new ArrayList<>();
        out.add(Phase.of("lag oben auf der Kante", r -> {
        }, r -> {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ACRO);
            if (r.highest < r.top - 0.05) {
                return "nie auf der Oberkante, höchster Schwerpunkt " + fmt(r.highest - r.top);
            }
            r.photo("");
            return null;
        }).stable(1));
        out.add(Phase.of(throttle ? "Turtle an, Knüppel mittig mit Vollgas: kippt über die Kante und fällt"
                : "Turtle an, Knüppel mittig: kippt über die Kante und fällt", r -> {
        }, r -> {
            PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle && r.ticks() >= 2 ? 1.0F : 0.0F), true,
                    StabMode.ACRO);
            return r.down() ? null : "liegt noch oben, Schwerpunkt " + fmt(comY() - r.top) + " über der Oberkante, " + state();
        }).stable(1).limit(FALL_TICKS));
        out.add(stillPhase(throttle, "unten: liegt still, rollt nicht weiter", true));
        return out;
    }

    private static List<Phase> rollPhases() {
        List<Phase> out = new ArrayList<>();
        out.add(Phase.of("Turtle an: scharf, liegt kopfüber oben auf der Auflage", r -> {
        }, r -> {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, r.ticks() >= 10, StabMode.ACRO);
            Telemetry t = Mode3dSelfTests.telemetry();
            if (t == null || !t.armed || !t.turtle) {
                return "nicht im Turtle-Modus scharf, Blocker " + (t == null ? -1 : t.armBlockers);
            }
            if (t.tiltDeg < 150.0) {
                return "liegt nicht kopfüber: " + fmt(t.tiltDeg);
            }
            return onTop(r);
        }).stable(10));
        out.add(Phase.of("Knüppel zur Kante mit Vollgas: rollt herunter oder dreht sich auf", r -> {
        }, r -> {
            PilotSession.INSTANCE.setTestInput(r.flip, true, StabMode.ACRO);
            Telemetry t = Mode3dSelfTests.telemetry();
            if (t == null) {
                return "keine Telemetrie";
            }
            if (!r.rolled && t.tiltDeg < 130.0 && t.tiltDeg > 60.0) {
                r.rolled = true;
                r.photo("_rolling");
            }
            if (r.down()) {
                return null;
            }
            if (t.tiltDeg < 25.0 && onTop(r) == null) {
                return null;
            }
            return "weder herunter noch aufgedreht: Schwerpunkt " + fmt(comY() - r.top) + " über der Oberkante, " + state();
        }).stable(4).limit(300));
        out.add(stillPhase(false, "liegt still (unten oder aufrecht oben)", false));
        return out;
    }

    private static Phase stillPhase(boolean throttle, String name, boolean mustBeDown) {
        return Phase.of(name, r -> {
        }, r -> {
            Telemetry t = Mode3dSelfTests.telemetry();
            PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle ? 1.0F : 0.0F), true, StabMode.ACRO);
            if (t == null) {
                return "keine Telemetrie";
            }
            if (mustBeDown && !r.down()) {
                return "wieder oben: " + fmt(comY() - r.top);
            }
            double turn = Double.isNaN(r.lastTilt) ? 0.0 : Math.abs(t.tiltDeg - r.lastTilt);
            double drop = Double.isNaN(r.lastY) ? 0.0 : Math.abs(comY() - r.lastY);
            if (t.speed < 0.05 && turn < 1.0 && drop < 0.005) {
                return null;
            }
            return "bewegt sich noch: " + state() + ", Drehung " + fmt(turn) + "°/Tick";
        }).stable(40).limit(400);
    }

    private static List<Phase> middlePhases() {
        List<Phase> out = new ArrayList<>();
        out.add(Phase.of("Turtle an: scharf, liegt oben auf dem Balken", r -> {
        }, r -> {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, r.ticks() >= 10, StabMode.ACRO);
            Telemetry t = Mode3dSelfTests.telemetry();
            if (t == null || !t.armed || !t.turtle) {
                return "nicht im Turtle-Modus scharf, Blocker " + (t == null ? -1 : t.armBlockers);
            }
            if (t.tiltDeg < 150.0) {
                return "liegt nicht kopfüber: " + fmt(t.tiltDeg);
            }
            return onTop(r);
        }).stable(10));
        out.add(Phase.of("Roll: Props einer Seite rückwärts, dreht sich auf dem Balken um", r -> {
        }, r -> {
            PilotSession.INSTANCE.setTestInput(r.flip, true, StabMode.ACRO);
            Telemetry t = Mode3dSelfTests.telemetry();
            if (t == null || !t.armed) {
                return "nicht mehr scharf";
            }
            if (!r.rolled && t.tiltDeg < 130.0 && t.tiltDeg > 60.0) {
                r.rolled = true;
                r.photo("_rolling");
            }
            String top = onTop(r);
            if (top != null) {
                return top;
            }
            return t.tiltDeg < 25.0 ? null : "Neigung " + fmt(t.tiltDeg);
        }).stable(4).limit(300));
        out.add(Phase.of("aufrecht: Motoren stehen, liegt still oben auf dem Balken", r -> {
        }, r -> {
            PilotSession.INSTANCE.setTestInput(r.flip, true, StabMode.ACRO);
            Telemetry t = Mode3dSelfTests.telemetry();
            if (t == null || !t.armed || !t.turtle) {
                return "Turtle-Modus nicht mehr scharf";
            }
            String top = onTop(r);
            if (top != null) {
                return top;
            }
            if (t.tiltDeg > 25.0) {
                return "nicht mehr aufrecht: " + fmt(t.tiltDeg);
            }
            for (double rpm : t.motorRpm) {
                if (Math.abs(rpm) > 300.0) {
                    return "Motor dreht noch: " + fmt(rpm);
                }
            }
            double turn = Double.isNaN(r.lastTilt) ? 0.0 : Math.abs(t.tiltDeg - r.lastTilt);
            return t.speed < 0.05 && turn < 1.0 ? null : "bewegt sich noch: " + state() + ", Drehung " + fmt(turn) + "°/Tick";
        }).stable(40).limit(300));
        return out;
    }

    private static @Nullable String onTop(Run r) {
        double above = comY() - r.top;
        if (above > 0.0 && above < ON_TOP) {
            return null;
        }
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        String moved = drone == null ? "" : ", versetzt um x " + fmt(drone.getX() - r.startX) + ", z " + fmt(drone.getZ() - r.startZ);
        return "nicht oben auf der Auflage, Schwerpunkt " + fmt(above) + " über der Oberkante" + moved;
    }

    private static double length(AABB box) {
        return Math.max(box.getXsize(), box.getZsize());
    }

    private static String state() {
        Telemetry t = Mode3dSelfTests.telemetry();
        return t == null ? "-" : "Neigung " + fmt(t.tiltDeg) + ", Tempo " + fmt(t.speed) + ", scharf " + t.armed + ", Turtle " + t.turtle;
    }

    private static double comY() {
        return PilotSession.INSTANCE.comPosition(new Vector3d()).y;
    }

    private static double tilt() {
        Telemetry t = Mode3dSelfTests.telemetry();
        return t == null ? Double.NaN : t.tiltDeg;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
