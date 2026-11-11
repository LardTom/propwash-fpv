package de.lardtom.propwash.video.link;

import java.util.Arrays;

public final class TerrainColumns {
    public static final int SIZE = 16;
    public static final int COLUMNS = SIZE * SIZE;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_CANOPY = 255;
    public static final int GROUND_SEARCH = 4;
    public static final double THIN_VOLUME = 0.5;
    public static final double SOLID_DB = 7.0;
    private static final RfMaterial[] MATERIALS = RfMaterial.values();
    private static final Medium[] SOLID = new Medium[MATERIALS.length];
    private static final Medium CANOPY;

    static {
        for (RfMaterial material : MATERIALS) {
            SOLID[material.ordinal()] = material == RfMaterial.AIR ? Medium.AIR : new Medium(material, null, reflectivity(material));
        }
        CANOPY = SOLID[RfMaterial.FOLIAGE.ordinal()];
    }

    private final short[] top = new short[COLUMNS];
    private final byte[] canopy = new byte[COLUMNS];
    private final byte[] material = new byte[COLUMNS];

    public TerrainColumns() {
        Arrays.fill(this.top, Short.MIN_VALUE);
    }

    public static TerrainColumns scan(int originX, int originZ, int minY, Heights surface, Heights ground, ObstructionTracer.World world) {
        TerrainColumns columns = new TerrainColumns();
        for (int lz = 0; lz < SIZE; lz++) {
            for (int lx = 0; lx < SIZE; lx++) {
                columns.scanColumn(lx, lz, originX + lx, originZ + lz, surface.height(lx, lz), ground.height(lx, lz), minY, world);
            }
        }
        return columns;
    }

    void scanColumn(int lx, int lz, int x, int z, int surface, int ground, int minY, ObstructionTracer.World world) {
        int leaf = Integer.MIN_VALUE;
        int from = surface;
        int bottom = Math.max(minY, surface - MAX_DEPTH + 1);
        boolean resumed = false;
        while (true) {
            for (int y = from; y >= bottom; y--) {
                Medium medium = world.medium(x, y, z);
                if (passable(medium)) {
                    if (leaf == Integer.MIN_VALUE && medium.material() == RfMaterial.FOLIAGE) {
                        leaf = y;
                    }
                    continue;
                }
                RfMaterial found = medium.material();
                if (found == RfMaterial.SNOW || found == RfMaterial.ICE) {
                    found = beneath(world, x, y, z, minY, found);
                }
                this.set(lx, lz, y, leaf == Integer.MIN_VALUE ? 0 : leaf - y, found);
                return;
            }
            int next = Math.min(ground, bottom - 1);
            if (resumed || next < minY) {
                break;
            }
            resumed = true;
            from = next;
            bottom = Math.max(minY, next - MAX_DEPTH + 1);
        }
        int top = bottom - 1;
        this.set(lx, lz, top, leaf == Integer.MIN_VALUE ? 0 : leaf - top, RfMaterial.AIR);
    }

    private static RfMaterial beneath(ObstructionTracer.World world, int x, int y, int z, int minY, RfMaterial cover) {
        for (int depth = 1; depth <= GROUND_SEARCH && y - depth >= minY; depth++) {
            Medium below = world.medium(x, y - depth, z);
            if (passable(below)) {
                continue;
            }
            RfMaterial material = below.material();
            if (material != RfMaterial.SNOW && material != RfMaterial.ICE) {
                return material;
            }
        }
        return cover;
    }

    public static boolean passable(Medium medium) {
        return medium.empty() || medium.material().foliage() || thin(medium);
    }

    public static boolean thin(Medium medium) {
        double[] boxes = medium.boxes();
        if (boxes == null) {
            return false;
        }
        double volume = 0.0;
        for (int i = 0; i + 5 < boxes.length; i += 6) {
            volume += Math.max(0.0, boxes[i + 3] - boxes[i]) * Math.max(0.0, boxes[i + 4] - boxes[i + 1]) * Math.max(0.0, boxes[i + 5] - boxes[i + 2]);
        }
        return volume < THIN_VOLUME;
    }

    public static double reflectivity(RfMaterial material) {
        return switch (material) {
            case AIR -> 0.0;
            case FOLIAGE -> 1.2 / SOLID_DB;
            case WATER -> 3.5 / SOLID_DB;
            case GLASS, ICE, PLANT, METAL_MESH -> 1.5 / SOLID_DB;
            default -> 1.0;
        };
    }

    public void set(int lx, int lz, int top, int canopy, RfMaterial material) {
        int index = index(lx, lz);
        this.top[index] = (short) Math.clamp(top, Short.MIN_VALUE, Short.MAX_VALUE);
        this.canopy[index] = (byte) Math.clamp(canopy, 0, MAX_CANOPY);
        this.material[index] = (byte) material.ordinal();
    }

    public int top(int lx, int lz) {
        return this.top[index(lx, lz)];
    }

    public int canopy(int lx, int lz) {
        return this.canopy[index(lx, lz)] & 0xFF;
    }

    public RfMaterial material(int lx, int lz) {
        return MATERIALS[this.material[index(lx, lz)]];
    }

    public Medium medium(int lx, int y, int lz) {
        int index = index(lx, lz);
        int top = this.top[index];
        if (y <= top) {
            return SOLID[this.material[index]];
        }
        return y - top <= (this.canopy[index] & 0xFF) ? CANOPY : Medium.AIR;
    }

    private static int index(int lx, int lz) {
        return (lz & (SIZE - 1)) * SIZE + (lx & (SIZE - 1));
    }

    @FunctionalInterface
    public interface Heights {
        int height(int lx, int lz);
    }
}
