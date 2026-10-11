package de.lardtom.propwash.bando.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class BandoGenerator {
    public static final double CHEST_CHANCE = 0.55;
    public static final int MAX_SIZE = 64;
    public static final int YARD = 3;
    private static final long CHEST_SALT = 0x5DEECE66DL;

    private BandoGenerator() {
    }

    public static BandoPlan generate(BandoVariant variant, long seed) {
        List<int[]> spots = new ArrayList<>();
        BandoPlan plan = switch (variant) {
            case BARN -> FieldBandos.barn(seed, spots);
            case WAREHOUSE -> FieldBandos.warehouse(seed, spots);
            case FACTORY -> DesertBandos.factory(seed, spots);
            case WATER_TOWER -> DesertBandos.waterTower(seed, spots);
            case REFINERY -> DesertBandos.refinery(seed, spots);
            case COLLAPSED_HALL -> RuinBandos.collapsedHall(seed, spots);
            case CONCRETE_FRAME -> RuinBandos.concreteFrame(seed, spots);
            case PIER -> CoastBandos.pier(seed, spots);
            case PLATFORM -> CoastBandos.platform(seed, spots);
            case MINE_HEAD -> RuinBandos.mineHead(seed, spots);
            case SUNKEN_WAREHOUSE -> RuinBandos.sunkenWarehouse(seed, spots);
        };
        if (plan.sizeX() > MAX_SIZE || plan.sizeY() > MAX_SIZE || plan.sizeZ() > MAX_SIZE) {
            throw new IllegalStateException(variant + " zu groß: " + plan.sizeX() + "×" + plan.sizeY() + "×" + plan.sizeZ());
        }
        Random random = new Random(seed ^ CHEST_SALT);
        Kit kit = new Kit(plan, random);
        kit.settle(variant.family() != BandoFamily.COAST);
        if (variant.family().terrain() != BandoFamily.Terrain.SHORE) {
            yard(plan, random);
        }
        if (random.nextDouble() < CHEST_CHANCE) {
            placeChest(kit, spots, random);
        }
        if (variant.family() != BandoFamily.COAST) {
            kit.anchor();
        }
        kit.solidify();
        return plan;
    }

    static void yard(BandoPlan plan, Random random) {
        int g = plan.ground();
        int sx = plan.sizeX();
        int sz = plan.sizeZ();
        boolean[] near = new boolean[sx * sz];
        for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {
                Mat at = plan.mat(x, g, z);
                Mat above = plan.mat(x, g + 1, z);
                if (at != Mat.EMPTY && at != Mat.AIR || above.kind() == Mat.Kind.STRUCTURE) {
                    for (int dz = -YARD; dz <= YARD; dz++) {
                        for (int dx = -YARD; dx <= YARD; dx++) {
                            int nx = x + dx;
                            int nz = z + dz;
                            if (nx >= 0 && nz >= 0 && nx < sx && nz < sz && dx * dx + dz * dz <= YARD * YARD + 1) {
                                near[nz * sx + nx] = true;
                            }
                        }
                    }
                }
            }
        }
        for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {
                if (near[z * sx + x] && plan.mat(x, g, z) == Mat.EMPTY) {
                    random.nextDouble();
                    if (plan.mat(x, g + 1, z).kind() != Mat.Kind.STRUCTURE) {
                        plan.set(x, g, z, Mat.YARD);
                    }
                }
            }
        }
    }

    private static void placeChest(Kit kit, List<int[]> spots, Random random) {
        List<int[]> order = new ArrayList<>(spots);
        Collections.shuffle(order, random);
        for (int[] spot : order) {
            for (int[] shift : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}}) {
                for (int facing = 0; facing < 4; facing++) {
                    int f = (spot[3] + facing) & 3;
                    if (kit.chest(spot[0] + shift[0], spot[1], spot[2] + shift[1], f)) {
                        return;
                    }
                }
            }
        }
    }
}
