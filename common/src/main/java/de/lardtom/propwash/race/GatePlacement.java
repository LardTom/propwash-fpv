package de.lardtom.propwash.race;

public final class GatePlacement {
    private static final double EPSILON = 1.0E-6;
    public static final float QUARTER = 90.0F;
    public static final float[] STEPS = {90.0F, 45.0F, 15.0F, 5.0F};
    private static final float FOLD_EPSILON = 1.0E-3F;
    public static final int MAX_VARIANTS = 16;

    private GatePlacement() {
    }

    public record Preference(GateMount floor, int floorTurn, GateMount wall, int wallTurn, GateMount ceiling, int ceilingTurn, float floorFine,
                             float ceilingFine, int variant) {
        public static final Preference DEFAULT = new Preference(GateMount.STANDING, 0, GateMount.WALL_DIVE, 0, GateMount.HANGING, 0);

        public Preference {
            floorTurn = valid(floor, GateMount.Surface.FLOOR) ? Math.floorMod(floorTurn, floor.turns()) : 0;
            wallTurn = valid(wall, GateMount.Surface.WALL) ? Math.floorMod(wallTurn, wall.turns()) : 0;
            ceilingTurn = valid(ceiling, GateMount.Surface.CEILING) ? Math.floorMod(ceilingTurn, ceiling.turns()) : 0;
            floor = valid(floor, GateMount.Surface.FLOOR) ? floor : GateMount.Surface.FLOOR.defaultMount();
            wall = valid(wall, GateMount.Surface.WALL) ? wall : GateMount.Surface.WALL.defaultMount();
            ceiling = valid(ceiling, GateMount.Surface.CEILING) ? ceiling : GateMount.Surface.CEILING.defaultMount();
            floorFine = floor.turns() == 4 ? fine(floorFine) : 0.0F;
            ceilingFine = ceiling.turns() == 4 ? fine(ceilingFine) : 0.0F;
            variant = Math.floorMod(variant, MAX_VARIANTS);
        }

        public Preference(GateMount floor, int floorTurn, GateMount wall, int wallTurn, GateMount ceiling, int ceilingTurn, float floorFine,
                          float ceilingFine) {
            this(floor, floorTurn, wall, wallTurn, ceiling, ceilingTurn, floorFine, ceilingFine, 0);
        }

        public Preference(GateMount floor, int floorTurn, GateMount wall, int wallTurn, GateMount ceiling, int ceilingTurn) {
            this(floor, floorTurn, wall, wallTurn, ceiling, ceilingTurn, 0.0F, 0.0F);
        }

        public int variant(CourseElement kind) {
            return kind.variant(this.variant);
        }

        public Preference withVariant(int next) {
            return new Preference(this.floor, this.floorTurn, this.wall, this.wallTurn, this.ceiling, this.ceilingTurn, this.floorFine,
                    this.ceilingFine, next);
        }

        public Preference nextVariant(CourseElement kind) {
            return this.withVariant(kind.nextVariant(this.variant(kind)));
        }

        private static boolean valid(GateMount mount, GateMount.Surface surface) {
            return mount != null && mount.surface() == surface;
        }

        private static float fine(float value) {
            if (!Float.isFinite(value)) {
                return 0.0F;
            }
            float rounded = Math.round(value * GateTilt.RESOLUTION) / GateTilt.RESOLUTION;
            return rounded > 0.0F && rounded < QUARTER ? rounded : 0.0F;
        }

        public GateMount mount(GateMount.Surface surface) {
            return switch (surface) {
                case FLOOR -> this.floor;
                case WALL -> this.wall;
                case CEILING -> this.ceiling;
            };
        }

        public int turn(GateMount.Surface surface) {
            return switch (surface) {
                case FLOOR -> this.floorTurn;
                case WALL -> this.wallTurn;
                case CEILING -> this.ceilingTurn;
            };
        }

        public float fine(GateMount.Surface surface) {
            return switch (surface) {
                case FLOOR -> this.floorFine;
                case WALL -> 0.0F;
                case CEILING -> this.ceilingFine;
            };
        }

        public Preference with(GateMount mount, int turn) {
            return this.with(mount, turn, 0.0F);
        }

        public Preference with(GateMount mount, int turn, float fine) {
            return switch (mount.surface()) {
                case FLOOR -> new Preference(mount, turn, this.wall, this.wallTurn, this.ceiling, this.ceilingTurn, fine, this.ceilingFine, this.variant);
                case WALL -> new Preference(this.floor, this.floorTurn, mount, turn, this.ceiling, this.ceilingTurn, this.floorFine, this.ceilingFine,
                        this.variant);
                case CEILING -> new Preference(this.floor, this.floorTurn, this.wall, this.wallTurn, mount, turn, this.floorFine, fine, this.variant);
            };
        }

        public Preference turned(GateMount.Surface surface) {
            return this.with(this.mount(surface), this.turn(surface) + 1, this.fine(surface));
        }

        public Preference turnedBy(GateMount.Surface surface, float step) {
            GateMount mount = this.mount(surface);
            if (mount.turns() != 4 || step >= QUARTER || !(step > 0.0F)) {
                return this.turned(surface);
            }
            float total = this.turn(surface) * QUARTER + this.fine(surface) + step;
            int turn = (int) Math.floor((total + FOLD_EPSILON) / QUARTER);
            float fine = total - turn * QUARTER;
            if (Math.abs(fine) < FOLD_EPSILON) {
                fine = 0.0F;
            }
            return this.with(mount, turn, fine);
        }

        public Preference nextMount(GateMount.Surface surface) {
            return this.with(this.mount(surface).next(), 0);
        }
    }

    public record Click(GateMount.Surface surface, int blockX, int blockY, int blockZ, int faceX, int faceZ, double hitX, double hitY,
                        double hitZ, double floorX, double floorY, double floorZ, float playerYaw, double plane) {
        public Click(GateMount.Surface surface, int blockX, int blockY, int blockZ, int faceX, int faceZ, double hitX, double hitY,
                     double hitZ, double floorX, double floorY, double floorZ, float playerYaw) {
            this(surface, blockX, blockY, blockZ, faceX, faceZ, hitX, hitY, hitZ, floorX, floorY, floorZ, playerYaw,
                    blockPlane(surface, blockX, blockY, blockZ, faceX, faceZ, floorY));
        }
    }

    public static double blockPlane(GateMount.Surface surface, int blockX, int blockY, int blockZ, int faceX, int faceZ, double floorY) {
        return switch (surface) {
            case FLOOR -> floorY;
            case CEILING -> blockY;
            case WALL -> faceX != 0 ? blockX + 0.5 + 0.5 * faceX : blockZ + 0.5 + 0.5 * faceZ;
        };
    }

    public static GateMount.Surface surface(int faceY) {
        if (faceY > 0) {
            return GateMount.Surface.FLOOR;
        }
        return faceY < 0 ? GateMount.Surface.CEILING : GateMount.Surface.WALL;
    }

    public static float wallYaw(int faceX, int faceZ) {
        if (faceX > 0) {
            return 270.0F;
        }
        if (faceX < 0) {
            return 90.0F;
        }
        return faceZ < 0 ? 180.0F : 0.0F;
    }

    public static double align(double hit, int block, double inner) {
        long whole = Math.round(inner);
        if (Math.abs(inner - whole) > EPSILON || whole % 2 != 0) {
            return block + 0.5;
        }
        return Math.max(block, Math.min(block + 1, Math.round(hit)));
    }

    public static GateGeometry resolve(Click click, Preference preference, GateSize size) {
        GateMount mount = preference.mount(click.surface());
        GateGeometry plain = resolve(click, mount, preference.turn(click.surface()), size);
        float fine = preference.fine(click.surface());
        return fine == 0.0F || plain.mount() != mount ? plain : plain.withTilt(new GateTilt(fine, 0.0F, 0.0F));
    }

    public static GateGeometry resolveElement(Click click, Preference preference, CourseElement kind) {
        int turn = preference.floor().turns() == 4 ? preference.floorTurn() : 0;
        float yaw = GateGeometry.snapYaw(click.playerYaw() + QUARTER * turn);
        float fine = preference.floor().turns() == 4 ? preference.floorFine() : 0.0F;
        return GateGeometry.element(click.floorX(), click.floorY(), click.floorZ(), yaw, kind, preference.variant(kind),
                kind.outerWidth(kind.defaultWidth()), kind.outerHeight(kind.defaultHeight()), new GateTilt(fine, 0.0F, 0.0F));
    }

    public static float validStep(float step) {
        for (float allowed : STEPS) {
            if (Math.abs(allowed - step) < 0.01F) {
                return allowed;
            }
        }
        return QUARTER;
    }

    public static GateGeometry resolve(Click click, GateMount mount, int turn, GateSize size) {
        if (mount.surface() != click.surface()) {
            mount = click.surface().defaultMount();
            turn = 0;
        }
        int step = Math.floorMod(turn, mount.turns());
        boolean flipped = mount.reversible() && step == 1;
        double innerWidth = size.width() - 2.0 * size.frame();
        double innerHeight = size.height() - 2.0 * size.frame();
        return switch (mount) {
            case STANDING -> GateGeometry.of(click.floorX(), click.floorY(), click.floorZ(),
                    GateGeometry.snapYaw(click.playerYaw() + 90.0F * step), size);
            case HANGING -> place(mount, click.blockX() + 0.5, click.plane(), click.blockZ() + 0.5,
                    GateGeometry.snapYaw(click.playerYaw() + 90.0F * step), false, size);
            case FLOOR_FLAT, CEILING_FLAT -> {
                float yaw = GateGeometry.snapYaw(click.playerYaw());
                GateGeometry probe = GateGeometry.of(0.0, 0.0, 0.0, yaw, size, mount, flipped);
                boolean widthAlongX = Math.abs(probe.sideX()) > 0.5;
                double px = align(click.hitX(), click.blockX(), widthAlongX ? innerWidth : innerHeight);
                double pz = align(click.hitZ(), click.blockZ(), widthAlongX ? innerHeight : innerWidth);
                double py = mount == GateMount.FLOOR_FLAT ? click.floorY() : click.plane();
                yield place(mount, px, py, pz, yaw, flipped, size);
            }
            case WALL_DIVE, WALL_SIDE, WALL_WINDOW -> {
                float yaw = wallYaw(click.faceX(), click.faceZ());
                boolean alongZ = click.faceX() != 0;
                double alongHit = alongZ ? click.hitZ() : click.hitX();
                int alongBlock = alongZ ? click.blockZ() : click.blockX();
                double along = mount == GateMount.WALL_SIDE ? alongBlock + 0.5 : align(alongHit, alongBlock, innerWidth);
                double py = mount == GateMount.WALL_DIVE ? click.blockY() + 0.5 : align(click.hitY(), click.blockY(), innerHeight);
                double px = alongZ ? click.plane() : along;
                double pz = alongZ ? along : click.plane();
                yield place(mount, px, py, pz, yaw, flipped, size);
            }
        };
    }

    public static GateGeometry place(GateMount mount, double px, double py, double pz, float yaw, boolean flipped, GateSize size) {
        return place(mount, px, py, pz, yaw, flipped, size.width(), size.height(), size.frame(), size.depth(), GateTilt.NONE);
    }

    public static GateGeometry place(GateMount mount, double px, double py, double pz, float yaw, boolean flipped, double width, double height,
                                     double frame, double depth, GateTilt tilt) {
        GateGeometry probe = new GateGeometry(0.0, 0.0, 0.0, yaw, width, height, frame, depth, mount, flipped);
        double fx = probe.facingX();
        double fz = probe.facingZ();
        double[] offset = offset(mount, fx, fz, width, height, depth);
        return new GateGeometry(px + offset[0], py + offset[1], pz + offset[2], yaw, width, height, frame, depth, mount, flipped, tilt);
    }

    private static double[] offset(GateMount mount, double fx, double fz, double width, double height, double depth) {
        return switch (mount) {
            case STANDING, WALL_DIVE -> new double[]{0.0, 0.0, 0.0};
            case FLOOR_FLAT -> new double[]{-fx * height * 0.5, depth * 0.5, -fz * height * 0.5};
            case CEILING_FLAT -> new double[]{-fx * height * 0.5, -depth * 0.5, -fz * height * 0.5};
            case HANGING -> new double[]{0.0, -(GateMount.HANG_DROP + height), 0.0};
            case WALL_SIDE -> new double[]{fx * width * 0.5, -height * 0.5, fz * width * 0.5};
            case WALL_WINDOW -> new double[]{fx * depth * 0.5, -height * 0.5, fz * depth * 0.5};
        };
    }

    public static double[] mountPoint(GateGeometry gate) {
        double[] offset = offset(gate.mount(), gate.facingX(), gate.facingZ(), gate.width(), gate.height(), gate.depth());
        return new double[]{gate.x() - offset[0], gate.y() - offset[1], gate.z() - offset[2]};
    }

    public static GateGeometry rotated(GateGeometry gate, boolean changeMount, boolean stacked) {
        if (gate.freeform()) {
            return gate.withVariant(gate.kind().opposite(gate.variant()));
        }
        if (!gate.gate()) {
            return changeMount ? gate.withVariant(gate.kind().nextVariant(gate.variant())) : turnedTo(gate, gate.heading() + QUARTER);
        }
        GateMount mount = gate.mount();
        float yaw = gate.yawDegrees();
        boolean flipped = gate.flipped();
        GateTilt tilt = gate.tilt();
        if (changeMount) {
            if (stacked) {
                return null;
            }
            mount = mount.next();
            flipped = false;
            tilt = GateTilt.NONE;
        } else if (mount.reversible()) {
            flipped = !flipped;
            tilt = new GateTilt(tilt.yaw(), -tilt.pitch(), -tilt.roll());
        } else {
            yaw = GateGeometry.snapYaw(yaw + (stacked ? 180.0F : 90.0F));
        }
        double[] point = snapped(mount, mountPoint(gate), yaw, flipped, gate.width(), gate.height(), gate.frame(), gate.depth());
        return place(mount, point[0], point[1], point[2], yaw, flipped, gate.width(), gate.height(), gate.frame(), gate.depth(), tilt);
    }

    public static GateGeometry rotated(GateGeometry gate, boolean changeMount, boolean stacked, float step) {
        if (gate.freeform()) {
            return rotated(gate, changeMount, stacked);
        }
        float valid = validStep(step);
        if (!gate.gate()) {
            return changeMount ? rotated(gate, true, false) : turnedTo(gate, gate.heading() + valid);
        }
        if (changeMount || valid >= QUARTER || gate.mount().turns() != 4) {
            return rotated(gate, changeMount, stacked);
        }
        if (stacked) {
            return null;
        }
        return turnedTo(gate, gate.heading() + valid);
    }

    public static GateGeometry turnedTo(GateGeometry gate, float heading) {
        float wrapped = heading % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        GateTilt tilt = gate.tilt();
        if (gate.mount().turns() != 4) {
            return gate.withTilt(tilt.withYaw(wrapped - gate.yawDegrees()));
        }
        int quarter = (int) Math.floor((wrapped + FOLD_EPSILON) / QUARTER);
        float fine = wrapped - quarter * QUARTER;
        if (Math.abs(fine) < FOLD_EPSILON) {
            fine = 0.0F;
        }
        float base = GateGeometry.snapYaw(quarter * QUARTER);
        return new GateGeometry(gate.x(), gate.y(), gate.z(), base, gate.width(), gate.height(), gate.frame(), gate.depth(), gate.mount(),
                gate.flipped(), tilt.withYaw(fine), gate.kind(), gate.variant());
    }

    public static GateGeometry edited(GateGeometry gate, float heading, float pitch, float roll, double width, double height) {
        if (gate.kind() == CourseElement.GATE_POLE) {
            return GatePoles.edited(gate, heading, height);
        }
        if (!gate.gate()) {
            GateGeometry turned = turnedTo(gate, heading);
            return GateGeometry.element(gate.x(), gate.y(), gate.z(), turned.yawDegrees(), gate.kind(), gate.variant(), width, height, turned.tilt());
        }
        GateGeometry turned = turnedTo(gate, heading);
        GateTilt tilt = new GateTilt(turned.tilt().yaw(), pitch, roll);
        double[] point = mountPoint(gate);
        return place(gate.mount(), point[0], point[1], point[2], turned.yawDegrees(), gate.flipped(), width, height, gate.frame(), gate.depth(),
                tilt);
    }

    static double[] snapped(GateMount mount, double[] point, float yaw, boolean flipped, double width, double height, double frame, double depth) {
        double innerWidth = width - 2.0 * frame;
        double innerHeight = height - 2.0 * frame;
        GateGeometry probe = new GateGeometry(0.0, 0.0, 0.0, yaw, width, height, frame, depth, mount, flipped);
        double x = point[0];
        double y = point[1];
        double z = point[2];
        switch (mount) {
            case STANDING, HANGING -> {
                x = blockCenter(x);
                z = blockCenter(z);
            }
            case FLOOR_FLAT, CEILING_FLAT -> {
                boolean widthAlongX = Math.abs(probe.sideX()) > 0.5;
                x = grid(x, widthAlongX ? innerWidth : innerHeight);
                z = grid(z, widthAlongX ? innerHeight : innerWidth);
            }
            case WALL_DIVE, WALL_SIDE, WALL_WINDOW -> {
                boolean alongZ = Math.abs(probe.facingX()) > 0.5;
                double along = alongZ ? z : x;
                along = mount == GateMount.WALL_SIDE ? blockCenter(along) : grid(along, innerWidth);
                y = mount == GateMount.WALL_DIVE ? blockCenter(y) : grid(y, innerHeight);
                if (alongZ) {
                    z = along;
                } else {
                    x = along;
                }
            }
        }
        return new double[]{x, y, z};
    }

    static double blockCenter(double value) {
        return Math.floor(value + EPSILON) + 0.5;
    }

    static double grid(double value, double inner) {
        long whole = Math.round(inner);
        if (Math.abs(inner - whole) > EPSILON || whole % 2 != 0) {
            return blockCenter(value);
        }
        return Math.ceil(value - 0.5 - EPSILON);
    }
}
