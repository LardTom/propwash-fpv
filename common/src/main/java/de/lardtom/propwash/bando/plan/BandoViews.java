package de.lardtom.propwash.bando.plan;

public final class BandoViews {
    public static final int MIN_RUN = 8;
    private static final int MAX_RUN = 28;
    private static final int LOOK = 5;

    public record View(int x, int y, int z, int facing, int run, int score) {
    }

    private BandoViews() {
    }

    public static View flyThrough(BandoPlan plan) {
        View best = null;
        int g = plan.ground();
        int top = Math.min(plan.sizeY() - 2, g + 10);
        int from = plan.variant().family().terrain() == BandoFamily.Terrain.WETLAND ? g + 3 : g + 2;
        for (int y = from; y <= top; y++) {
            for (int z = 0; z < plan.sizeZ(); z++) {
                for (int x = 0; x < plan.sizeX(); x++) {
                    if (!free(plan, x, y, z, 0, 1)) {
                        continue;
                    }
                    for (int facing = 0; facing < 4; facing++) {
                        View view = score(plan, x, y, z, facing);
                        if (view != null && (best == null || view.score() > best.score())) {
                            best = view;
                        }
                    }
                }
            }
        }
        if (best == null) {
            return new View(plan.sizeX() / 2, g + 3, 0, Meta.SOUTH, 0, 0);
        }
        return best;
    }

    private static boolean free(BandoPlan plan, int x, int y, int z, int facing, int width) {
        for (int dy = 0; dy <= 1; dy++) {
            for (int d = -width; d <= width; d++) {
                int cx = x + (Meta.dz(facing) != 0 ? d : 0);
                int cz = z + (Meta.dx(facing) != 0 ? d : 0);
                if (plan.blocksFlight(cx, y + dy, cz) || plan.mat(cx, y + dy, cz) == Mat.WATER) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean structure(BandoPlan plan, int x, int y, int z, int sx, int sy, int sz) {
        for (int i = sy < 0 ? 1 : 2; i <= LOOK; i++) {
            if (plan.mat(x + sx * i, y + sy * i, z + sz * i).kind() == Mat.Kind.STRUCTURE) {
                return true;
            }
        }
        return false;
    }

    private static View score(BandoPlan plan, int x, int y, int z, int facing) {
        int dx = Meta.dx(facing);
        int dz = Meta.dz(facing);
        int lx = dz;
        int lz = -dx;
        int run = 0;
        int score = 0;
        int framed = 0;
        for (int i = 0; i < MAX_RUN; i++) {
            int px = x + dx * i;
            int pz = z + dz * i;
            if (!free(plan, px, y, pz, facing, 1)) {
                break;
            }
            run++;
            int sides = 0;
            sides += structure(plan, px, y, pz, lx, 0, lz) ? 1 : 0;
            sides += structure(plan, px, y, pz, -lx, 0, -lz) ? 1 : 0;
            sides += structure(plan, px, y + 1, pz, 0, 1, 0) ? 1 : 0;
            sides += y - 1 > plan.ground() && structure(plan, px, y, pz, 0, -1, 0) ? 1 : 0;
            if (sides >= 2) {
                framed++;
            }
            score += Math.max(0, sides - 1) * 2 + (sides >= 3 ? 1 : 0);
        }
        if (run < MIN_RUN || framed < MIN_RUN / 2) {
            return null;
        }
        boolean outside = plan.mat(x, y, z) == Mat.EMPTY && !structure(plan, x, y, z, 0, 1, 0);
        return new View(x, y, z, facing, run, score + (outside ? 12 : 0));
    }
}
