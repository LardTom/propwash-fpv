package de.lardtom.propwash.bando.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.bando.BandoBlocks;
import de.lardtom.propwash.bando.BandoPiece;
import de.lardtom.propwash.bando.plan.BandoPalettes;
import de.lardtom.propwash.bando.plan.BandoPlan;
import de.lardtom.propwash.bando.plan.BandoSite;
import de.lardtom.propwash.bando.plan.BandoTransform;
import de.lardtom.propwash.bando.plan.Mat;
import de.lardtom.propwash.bando.plan.Meta;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class BandoGroundCheck {
    private static final double EPS = 0.01;
    private static final int EXAMPLES = 6;
    private static final int SCAN_UP = 6;
    private static final int SCAN_DOWN = 10;
    private static final int CROWN_REACH = 7;
    private static final int CROWN_SEARCH = 4000;
    private static final int TRUNK_REACH = 12;
    private static final int TRUNK_SEARCH = 512;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    record Site(int terrain, List<String> terrainAt, int raised, List<String> raisedAt, int rooted, List<String> rootedAt, int canopy) {
    }

    record Edge(List<Integer> deltas, int foreign) {
        static final Edge NONE = new Edge(List.of(), 0);
    }

    record Report(int blocks, int floating, List<String> floatingAt, int plants, List<String> plantsAt, int crowns, int decaying, int strays, int resting,
                  int underwater, Edge border, Site site, int crownTrees, int crownTreesInside, List<String> crownTreesAt) {
        List<Integer> edge() {
            return this.border.deltas();
        }

        int median() {
            if (this.edge().isEmpty()) {
                return 0;
            }
            List<Integer> sorted = new ArrayList<>(this.edge());
            sorted.sort(null);
            return sorted.get(sorted.size() / 2);
        }

        int below() {
            int n = 0;
            for (int d : this.edge()) {
                if (d < 0) {
                    n++;
                }
            }
            return n;
        }

        int terrain() {
            return this.site.terrain();
        }

        int raised() {
            return this.site.raised();
        }

        int rooted() {
            return this.site.rooted();
        }

        String summary() {
            String text = String.format(Locale.ROOT, "%d Blöcke, %d schweben, %d Pflanzen ohne Halt oder auf dem Dach, dazu %d Blätter von Baumkronen,"
                            + " %d zerfallende Blätter, %d Blätter fremder Büsche ohne Stamm, %d Pflanzen auf vollen Blöcken, %d unter Wasser", this.blocks,
                    this.floating, this.plants, this.crowns, this.decaying, this.strays, this.resting, this.underwater);
            text += String.format(Locale.ROOT, ", über der Bodenzeile in Grundriss und Hof %d Geländeblöcke, %d Pflanzen auf Gelände, %d Bäume,"
                            + " %d Blöcke Laub und Äste fremder Kronen; Kronen von %d Bäumen liegen auf dem Bau (davon %d aus dem Hof)%s",
                    this.site.terrain(), this.site.raised(), this.site.rooted(), this.site.canopy(), this.crownTrees, this.crownTreesInside,
                    this.crownTreesAt.isEmpty() ? "" : " " + this.crownTreesAt);
            if (!this.edge().isEmpty()) {
                List<Integer> sorted = new ArrayList<>(this.edge());
                sorted.sort(null);
                text += String.format(Locale.ROOT, ", Gelände am Rand %+d (Median, %d–%d), %d von %d Randspalten tiefer", this.median(), sorted.getFirst(),
                        sorted.getLast(), this.below(), this.edge().size());
            }
            if (this.border.foreign() > 0) {
                text += String.format(Locale.ROOT, ", %d Randspalten in fremden Bauwerken nicht gezählt", this.border.foreign());
            }
            return text;
        }
    }

    private BandoGroundCheck() {
    }

    static boolean plant(BlockState state) {
        Block block = state.getBlock();
        return block instanceof VegetationBlock || block instanceof CactusBlock || block instanceof SugarCaneBlock || block instanceof BambooStalkBlock
                || block instanceof MossyCarpetBlock || state.is(Blocks.MOSS_CARPET)
                || block instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT);
    }

    private static String name(BlockState state, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    static Report check(ServerLevel level, BandoPiece piece, boolean edges) {
        BandoPlan plan = piece.plan();
        BandoTransform t = piece.transform();
        BandoBlocks resolver = piece.blocks();
        int sx = plan.sizeX();
        int sy = plan.sizeY();
        int sz = plan.sizeZ();
        int n = sx * sy * sz;
        boolean[] ours = new boolean[n];
        double[] lo = new double[n];
        double[] hi = new double[n];
        int blocks = 0;
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    Mat mat = plan.mat(x, y, z);
                    if (!mat.holdsUp() && mat.kind() != Mat.Kind.DECOR) {
                        continue;
                    }
                    BlockPos pos = world(t, x, y, z);
                    BlockState state = level.getBlockState(pos);
                    BlockState expected = mat == Mat.CHEST ? Blocks.CHEST.defaultBlockState()
                            : resolver.state(mat, plan.meta(x, y, z), BandoPalettes.hash(piece.seed(), pos.getX(), pos.getY(), pos.getZ()));
                    if (state.isAir() || expected == null || !state.is(expected.getBlock())
                            || state.getBlock() instanceof LeavesBlock && state.getValue(LeavesBlock.PERSISTENT) != expected.getValue(LeavesBlock.PERSISTENT)) {
                        continue;
                    }
                    VoxelShape shape = state.getShape(level, pos);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    int i = index(plan, x, y, z);
                    ours[i] = true;
                    lo[i] = shape.min(Direction.Axis.Y);
                    hi[i] = shape.max(Direction.Axis.Y);
                    blocks++;
                }
            }
        }
        boolean[] seen = new boolean[n];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int i = index(plan, x, y, z);
                    if (!ours[i]) {
                        continue;
                    }
                    BlockPos pos = world(t, x, y, z);
                    Mat mat = plan.mat(x, y, z);
                    boolean hangs = mat == Mat.VINE && level.getBlockState(pos).canSurvive(level, pos);
                    if (hangs || grounded(level, plan, t, ours, pos, lo[i]) || mat == Mat.FLOAT && level.getFluidState(pos.below()).is(FluidTags.WATER)) {
                        seen[i] = true;
                        queue.add(new int[]{x, y, z});
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            Mat from = plan.mat(c[0], c[1], c[2]);
            if (from.kind() == Mat.Kind.DECOR) {
                continue;
            }
            int ci = index(plan, c[0], c[1], c[2]);
            boolean step = from == Mat.ROOF_STEP;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int axes = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
                        if (axes == 0 || axes == 3 || axes == 2 && (!step || dy == 0)) {
                            continue;
                        }
                        int x = c[0] + dx;
                        int y = c[1] + dy;
                        int z = c[2] + dz;
                        if (!plan.inside(x, y, z)) {
                            continue;
                        }
                        int i = index(plan, x, y, z);
                        Mat mat = plan.mat(x, y, z);
                        if (!ours[i] || seen[i] || from.kind() == Mat.Kind.ATTACHED && mat.kind() == Mat.Kind.STRUCTURE
                                || axes == 2 && mat != Mat.ROOF_STEP && mat != Mat.ROOF) {
                            continue;
                        }
                        boolean touch;
                        if (axes == 2) {
                            touch = true;
                        } else if (dy > 0) {
                            touch = hi[ci] >= 1.0 - EPS && lo[i] <= EPS;
                        } else if (dy < 0) {
                            touch = hi[i] >= 1.0 - EPS && lo[ci] <= EPS;
                        } else {
                            touch = Math.min(hi[ci], hi[i]) - Math.max(lo[ci], lo[i]) > EPS;
                        }
                        if (touch) {
                            seen[i] = true;
                            queue.add(new int[]{x, y, z});
                        }
                    }
                }
            }
        }
        int floating = 0;
        List<String> floatingAt = new ArrayList<>();
        int plants = 0;
        int crowns = 0;
        boolean[] columns = BandoSite.siteColumns(plan);
        Map<BlockPos, Boolean> crownTrees = new HashMap<>();
        int decaying = 0;
        int strays = 0;
        int resting = 0;
        int underwater = 0;
        List<String> plantsAt = new ArrayList<>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int i = index(plan, x, y, z);
                    if (!ours[i]) {
                        continue;
                    }
                    BlockPos pos = world(t, x, y, z);
                    if (!seen[i]) {
                        floating++;
                        if (floatingAt.size() < EXAMPLES) {
                            BlockPos under = pos.below();
                            floatingAt.add(plan.mat(x, y, z) + "/" + name(level.getBlockState(pos), pos)
                                    + (ours(plan, t, ours, under) ? "" : " auf " + name(level.getBlockState(under), under) + " (Plan " + column(plan, x, y, z) + ")"));
                        }
                    }
                    Mat mat = plan.mat(x, y, z);
                    if (mat.kind() != Mat.Kind.STRUCTURE && mat.kind() != Mat.Kind.LOOSE || ours(plan, t, ours, pos.above())) {
                        continue;
                    }
                    BlockPos up = pos.above();
                    BlockState above = level.getBlockState(up);
                    if (!plant(above)) {
                        continue;
                    }
                    String problem;
                    if (above.getBlock() instanceof LeavesBlock) {
                        BlockPos log = crown(level, plan, t, ours, up);
                        if (log != null) {
                            crowns++;
                            BlockPos base = trunk(level, plan, t, ours, log, new HashSet<>());
                            if (base != null && standing(level, base)) {
                                crownTrees.putIfAbsent(base, inside(plan, t, columns, base));
                            }
                            continue;
                        }
                        if (above.getValue(LeavesBlock.DISTANCE) >= LeavesBlock.DECAY_DISTANCE) {
                            decaying++;
                            continue;
                        }
                        if (reachesOut(level, plan, t, ours, columns, up)) {
                            strays++;
                            continue;
                        }
                        problem = " ohne Stamm (Abstand " + above.getValue(LeavesBlock.DISTANCE) + ")";
                    } else if (!level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) {
                        problem = " ohne Halt";
                    } else if (mat == Mat.ROOF || mat == Mat.ROOF_STEP) {
                        problem = " auf dem Dach";
                    } else {
                        if (above.getFluidState().is(FluidTags.WATER)) {
                            underwater++;
                        } else {
                            resting++;
                        }
                        continue;
                    }
                    plants++;
                    if (plantsAt.size() < EXAMPLES) {
                        plantsAt.add(name(above, up) + " auf " + mat + problem);
                    }
                }
            }
        }
        Edge edge = edges ? edge(level, plan, t, ours) : Edge.NONE;
        Site site = site(level, piece, ours, columns);
        int inside = 0;
        List<String> crownTreesAt = new ArrayList<>();
        for (Map.Entry<BlockPos, Boolean> tree : crownTrees.entrySet()) {
            if (tree.getValue()) {
                inside++;
            }
            if (crownTreesAt.size() < EXAMPLES) {
                crownTreesAt.add(name(level.getBlockState(tree.getKey()), tree.getKey()) + (tree.getValue() ? " im Hof"
                        : " " + distance(plan, t, columns, tree.getKey()) + " Blöcke hinter dem Hof"));
            }
        }
        return new Report(blocks, floating, floatingAt, plants, plantsAt, crowns, decaying, strays, resting, underwater, edge, site, crownTrees.size(), inside,
                crownTreesAt);
    }

    private static Site site(ServerLevel level, BandoPiece piece, boolean[] ours, boolean[] columns) {
        BandoPlan plan = piece.plan();
        BandoTransform t = piece.transform();
        int g = plan.ground();
        int floor = t.worldY(g);
        int from = BandoSite.clearFrom(plan);
        int bank = t.worldY(from);
        int terrain = 0;
        int raised = 0;
        int canopy = 0;
        List<String> terrainAt = new ArrayList<>();
        List<String> raisedAt = new ArrayList<>();
        List<String> rootedAt = new ArrayList<>();
        Set<BlockPos> bases = new HashSet<>();
        Set<BlockPos> clustered = new HashSet<>();
        for (int z = 0; z < plan.sizeZ(); z++) {
            for (int x = 0; x < plan.sizeX(); x++) {
                if (!columns[z * plan.sizeX() + x]) {
                    continue;
                }
                Block pillar = pillar(piece, x, z);
                int bottom = bottom(plan, x, z);
                int column = 0;
                BlockPos lowest = null;
                for (int y = from; y < plan.sizeY(); y++) {
                    if (ours[index(plan, x, y, z)]) {
                        continue;
                    }
                    BlockPos pos = world(t, x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.is(BlockTags.LOGS)) {
                        canopy++;
                        if (clustered.contains(pos)) {
                            continue;
                        }
                        BlockPos base = trunk(level, plan, t, ours, pos, clustered);
                        if (base != null && standing(level, base) && inside(plan, t, columns, base) && bases.add(base) && rootedAt.size() < EXAMPLES) {
                            int bx = t.localX(base.getX(), base.getZ());
                            int bz = t.localZ(base.getX(), base.getZ());
                            rootedAt.add(name(level.getBlockState(base), base) + " (Stamm bis " + pos.getY() + ", Plan " + bx + "," + bz + ": "
                                    + plan.mat(bx, g, bz) + "/" + plan.mat(bx, g + 1, bz) + "/" + plan.mat(bx, g + 2, bz) + ", darunter "
                                    + name(level.getBlockState(base.below()), base.below()) + ")");
                        }
                        continue;
                    }
                    if (state.getBlock() instanceof LeavesBlock) {
                        if (!state.getValue(LeavesBlock.PERSISTENT)) {
                            canopy++;
                        }
                        continue;
                    }
                    Mat mat = plan.mat(x, y, z);
                    if (mat != Mat.EMPTY && mat != Mat.AIR || pillar != null && y < bottom && state.is(pillar)) {
                        continue;
                    }
                    if (plant(state)) {
                        BlockPos base = pos;
                        while (base.getY() > bank && plant(level.getBlockState(base.below())) && !(level.getBlockState(base.below()).getBlock() instanceof LeavesBlock)) {
                            base = base.below();
                        }
                        BlockPos under = base.below();
                        BlockState support = level.getBlockState(under);
                        if (base.getY() > bank && !ours(plan, t, ours, under) && solid(level, under, support)) {
                            raised++;
                            if (raisedAt.size() < EXAMPLES) {
                                raisedAt.add(name(state, pos) + " auf " + name(support, under));
                            }
                        }
                        continue;
                    }
                    if (solid(level, pos, state)) {
                        terrain++;
                        column++;
                        if (lowest == null) {
                            lowest = pos;
                        }
                    }
                }
                if (column > 0 && terrainAt.size() < EXAMPLES) {
                    terrainAt.add(column + "× " + name(level.getBlockState(lowest), lowest) + " (Boden " + floor + ")");
                }
            }
        }
        return new Site(terrain, terrainAt, raised, raisedAt, bases.size(), rootedAt, canopy);
    }

    private static String column(BandoPlan plan, int x, int y, int z) {
        StringBuilder text = new StringBuilder();
        for (int k = Math.max(0, plan.ground() - 1); k < y; k++) {
            text.append(k == Math.max(0, plan.ground() - 1) ? "" : "/").append(plan.mat(x, k, z));
        }
        return text.append(", Fundament ").append(plan.fill(x, z)).toString();
    }

    private static boolean solid(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
                && Block.isShapeFullBlock(state.getCollisionShape(level, pos));
    }

    private static @Nullable Block pillar(BandoPiece piece, int x, int z) {
        BandoPlan plan = piece.plan();
        Mat fill = plan.fill(x, z);
        if (fill == Mat.EMPTY) {
            return null;
        }
        BandoTransform t = piece.transform();
        int wx = t.worldX(x, z);
        int wz = t.worldZ(x, z);
        BlockState state = piece.blocks().state(fill, Meta.AXIS_Y, BandoPalettes.hash(piece.seed(), wx, 0, wz));
        return state == null ? null : state.getBlock();
    }

    private static int bottom(BandoPlan plan, int x, int z) {
        for (int y = 0; y < plan.sizeY(); y++) {
            Mat mat = plan.mat(x, y, z);
            if (mat != Mat.EMPTY && mat != Mat.CHEST && mat.holdsUp() && mat != Mat.YARD) {
                return y;
            }
        }
        return 0;
    }

    private static @Nullable BlockPos trunk(ServerLevel level, BandoPlan plan, BandoTransform t, boolean[] ours, BlockPos log, Set<BlockPos> cluster) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(log);
        seen.add(log);
        BlockPos lowest = null;
        while (!queue.isEmpty() && seen.size() < TRUNK_SEARCH) {
            BlockPos at = queue.poll();
            cluster.add(at);
            if (lowest == null || at.getY() < lowest.getY() || at.getY() == lowest.getY()
                    && (at.getX() < lowest.getX() || at.getX() == lowest.getX() && at.getZ() < lowest.getZ())) {
                lowest = at;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = at.offset(dx, dy, dz);
                        if (Math.abs(next.getX() - log.getX()) > TRUNK_REACH || Math.abs(next.getY() - log.getY()) > TRUNK_REACH * 2
                                || Math.abs(next.getZ() - log.getZ()) > TRUNK_REACH || !seen.add(next)) {
                            continue;
                        }
                        if (!ours(plan, t, ours, next) && level.getBlockState(next).is(BlockTags.LOGS)) {
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return lowest;
    }

    private static boolean standing(ServerLevel level, BlockPos base) {
        BlockState state = level.getBlockState(base);
        return (!state.hasProperty(RotatedPillarBlock.AXIS) || state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y)
                && level.getBlockState(base.above()).is(BlockTags.LOGS);
    }

    private static boolean inside(BandoPlan plan, BandoTransform t, boolean[] columns, BlockPos pos) {
        int x = t.localX(pos.getX(), pos.getZ());
        int z = t.localZ(pos.getX(), pos.getZ());
        return x >= 0 && z >= 0 && x < plan.sizeX() && z < plan.sizeZ() && columns[z * plan.sizeX() + x];
    }

    private static int distance(BandoPlan plan, BandoTransform t, boolean[] columns, BlockPos pos) {
        int px = t.localX(pos.getX(), pos.getZ());
        int pz = t.localZ(pos.getX(), pos.getZ());
        int best = Integer.MAX_VALUE;
        for (int z = 0; z < plan.sizeZ(); z++) {
            for (int x = 0; x < plan.sizeX(); x++) {
                if (columns[z * plan.sizeX() + x]) {
                    best = Math.min(best, Math.max(Math.abs(x - px), Math.abs(z - pz)));
                }
            }
        }
        return best;
    }

    private static @Nullable BlockPos crown(ServerLevel level, BandoPlan plan, BandoTransform t, boolean[] ours, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && seen.size() < CROWN_SEARCH) {
            BlockPos at = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (Math.abs(next.getX() - start.getX()) > CROWN_REACH || Math.abs(next.getY() - start.getY()) > CROWN_REACH
                        || Math.abs(next.getZ() - start.getZ()) > CROWN_REACH || !seen.add(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.is(BlockTags.LOGS) && !ours(plan, t, ours, next)) {
                    return next;
                }
                if (state.getBlock() instanceof LeavesBlock) {
                    queue.add(next);
                }
            }
        }
        return null;
    }

    private static boolean reachesOut(ServerLevel level, BandoPlan plan, BandoTransform t, boolean[] ours, boolean[] columns, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && seen.size() < CROWN_SEARCH) {
            BlockPos at = queue.poll();
            if (!inside(plan, t, columns, at)) {
                return true;
            }
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (Math.abs(next.getX() - start.getX()) > CROWN_REACH || Math.abs(next.getY() - start.getY()) > CROWN_REACH
                        || Math.abs(next.getZ() - start.getZ()) > CROWN_REACH || ours(plan, t, ours, next) || !seen.add(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    private static boolean grounded(ServerLevel level, BandoPlan plan, BandoTransform t, boolean[] ours, BlockPos pos, double lo) {
        if (lo <= EPS) {
            BlockPos below = pos.below();
            if (!ours(plan, t, ours, below)) {
                BlockState state = level.getBlockState(below);
                VoxelShape shape = state.getCollisionShape(level, below);
                if (!shape.isEmpty() && shape.max(Direction.Axis.Y) >= 1.0 - EPS) {
                    return true;
                }
            }
        }
        for (Direction side : SIDES) {
            BlockPos next = pos.relative(side);
            if (ours(plan, t, ours, next)) {
                continue;
            }
            BlockState state = level.getBlockState(next);
            if (Block.isShapeFullBlock(state.getCollisionShape(level, next)) && !state.is(BlockTags.LEAVES)) {
                return true;
            }
        }
        return false;
    }

    private static Edge edge(ServerLevel level, BandoPlan plan, BandoTransform t, boolean[] ours) {
        int g = plan.ground();
        int floor = t.worldY(g);
        Set<Long> done = new HashSet<>();
        List<Integer> deltas = new ArrayList<>();
        int foreign = 0;
        for (int z = 0; z < plan.sizeZ(); z++) {
            for (int x = 0; x < plan.sizeX(); x++) {
                if (!groundRow(plan, ours, x, z)) {
                    continue;
                }
                BlockPos pos = world(t, x, g, z);
                for (Direction side : SIDES) {
                    BlockPos next = pos.relative(side);
                    int lx = t.localX(next.getX(), next.getZ());
                    int lz = t.localZ(next.getX(), next.getZ());
                    if (plan.inside(lx, g, lz) && groundRow(plan, ours, lx, lz) || !done.add(BlockPos.asLong(next.getX(), 0, next.getZ()))) {
                        continue;
                    }
                    Integer top = terrainTop(level, plan, t, ours, next.getX(), next.getZ(), floor);
                    if (top == null) {
                        continue;
                    }
                    if (top != floor && foreignStructure(level, next.getX(), next.getZ(), Math.min(top, floor), Math.max(top, floor))) {
                        foreign++;
                    } else {
                        deltas.add(top - floor);
                    }
                }
            }
        }
        return new Edge(deltas, foreign);
    }

    private static boolean foreignStructure(ServerLevel level, int x, int z, int fromY, int toY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = fromY; y <= toY; y++) {
            if (level.structureManager().getStructureWithPieceAt(pos.set(x, y, z), BandoGroundCheck::foreign).isValid()) {
                return true;
            }
        }
        return false;
    }

    private static boolean foreign(Holder<Structure> structure) {
        return structure.unwrapKey().map(key -> !key.identifier().getNamespace().equals(Constants.MOD_ID)).orElse(true);
    }

    private static boolean groundRow(BandoPlan plan, boolean[] ours, int x, int z) {
        int g = plan.ground();
        Mat mat = plan.mat(x, g, z);
        return (mat.kind() == Mat.Kind.STRUCTURE || mat == Mat.YARD) && ours[index(plan, x, g, z)];
    }

    private static @Nullable Integer terrainTop(ServerLevel level, BandoPlan plan, BandoTransform t, boolean[] ours, int x, int z, int floor) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = floor + SCAN_UP; y >= floor - SCAN_DOWN; y--) {
            pos.set(x, y, z);
            if (ours(plan, t, ours, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.canBeReplaced() || plant(state) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.getCollisionShape(level, pos).isEmpty()) {
                continue;
            }
            return y;
        }
        return null;
    }

    private static boolean ours(BandoPlan plan, BandoTransform t, boolean[] ours, BlockPos pos) {
        int x = t.localX(pos.getX(), pos.getZ());
        int z = t.localZ(pos.getX(), pos.getZ());
        int y = pos.getY() - t.originY();
        if (!plan.inside(x, y, z)) {
            return false;
        }
        return ours[index(plan, x, y, z)];
    }

    private static int index(BandoPlan plan, int x, int y, int z) {
        return (y * plan.sizeZ() + z) * plan.sizeX() + x;
    }

    private static BlockPos world(BandoTransform t, int x, int y, int z) {
        return new BlockPos(t.worldX(x, z), t.worldY(y), t.worldZ(x, z));
    }
}
