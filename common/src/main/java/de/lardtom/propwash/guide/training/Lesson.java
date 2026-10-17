package de.lardtom.propwash.guide.training;

import de.lardtom.propwash.trick.TrickType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record Lesson(String id, String chapter, int number, String icon, List<Ring> rings, List<Zone> zones, List<Pad> pads,
                     List<Marker> markers, List<Pylon> pylons, List<Goal> goals, double timeLimit, StarRule stars,
                     List<double[]> path, List<Probe> airspace, double[] bounds) {
    public static final String PREFIX = "propwash.guide.training.lesson.";
    public static final double START_HEIGHT = 1.5;
    public static final double APPROACH = 2.5;
    public static final double EXIT = 1.5;
    public static final double RING_CLEARANCE = 0.5;
    public static final double CORRIDOR_HALF = 0.6;
    public static final double CORRIDOR_STEP = 0.75;
    public static final double PAD_CLEARANCE = 1.8;

    public record Probe(double x, double y, double z, double halfX, double halfY, double halfZ, double heading) {
        public boolean contains(double lx, double ly, double lz) {
            double dx = lx - this.x;
            double dz = lz - this.z;
            double c = Math.cos(Math.toRadians(this.heading));
            double s = Math.sin(Math.toRadians(this.heading));
            double side = dx * c - dz * s;
            double along = dx * s + dz * c;
            return Math.abs(side) <= this.halfX && Math.abs(ly - this.y) <= this.halfY && Math.abs(along) <= this.halfZ;
        }

        public double radius() {
            return Math.sqrt(this.halfX * this.halfX + this.halfY * this.halfY + this.halfZ * this.halfZ);
        }
    }

    public Lesson {
        rings = List.copyOf(rings);
        zones = List.copyOf(zones);
        pads = List.copyOf(pads);
        markers = List.copyOf(markers);
        pylons = List.copyOf(pylons);
        goals = List.copyOf(goals);
        path = List.copyOf(path);
        airspace = List.copyOf(airspace);
        bounds = bounds.clone();
    }

    public String titleKey() {
        return PREFIX + this.id + ".title";
    }

    public String descriptionKey() {
        return PREFIX + this.id + ".description";
    }

    public List<String> mouseTipKeys(boolean invertPitch) {
        String mouse = this.tipKey() + "_mouse";
        return invertPitch ? List.of(mouse + "_inverted", mouse) : List.of(mouse);
    }

    public String tipKey() {
        return PREFIX + this.id + ".tip";
    }

    public String code() {
        return Curriculum.chapterNumber(this.chapter) + "." + this.number;
    }

    public List<String> translationKeys() {
        return List.of(this.titleKey(), this.descriptionKey(), this.tipKey());
    }

    public boolean usesTricks() {
        for (Goal goal : this.goals) {
            if (goal instanceof Goal.Tricks || goal instanceof Goal.Combo) {
                return true;
            }
        }
        return false;
    }

    public double[] bounds() {
        return this.bounds.clone();
    }

    public static Builder builder(String id, String chapter, int number, String icon) {
        return new Builder(id, chapter, number, icon);
    }

    public static final class Builder {
        private final String id;
        private final String chapter;
        private final int number;
        private final String icon;
        private final List<Ring> rings = new ArrayList<>();
        private final List<Zone> zones = new ArrayList<>();
        private final List<Pad> pads = new ArrayList<>();
        private final List<Marker> markers = new ArrayList<>();
        private final List<Pylon> pylons = new ArrayList<>();
        private final List<Goal> goals = new ArrayList<>();
        private final List<double[]> path = new ArrayList<>();
        private double timeLimit = 120.0;
        private StarRule stars = StarRule.time(10.0, 20.0);

        private Builder(String id, String chapter, int number, String icon) {
            this.id = id;
            this.chapter = chapter;
            this.number = number;
            this.icon = icon;
            this.pads.add(new Pad(0.0, 0.0, Pad.START_RADIUS));
            this.path.add(new double[]{0.0, START_HEIGHT, 0.0});
        }

        public Builder ring(Ring ring) {
            this.rings.add(ring);
            return this;
        }

        public Builder rings(List<Ring> rings) {
            this.rings.addAll(rings);
            return this;
        }

        public Builder zone(Zone zone) {
            this.zones.add(zone);
            return this;
        }

        public Builder pad(double x, double z, double radius) {
            this.pads.add(new Pad(x, z, radius));
            return this;
        }

        public Builder marker(double x, double z, double height) {
            this.markers.add(new Marker(x, z, height));
            return this;
        }

        public Builder pylon(double x, double z, double height) {
            this.pylons.add(new Pylon(x, z, height));
            return this;
        }

        public Builder arm(double seconds) {
            this.goals.add(new Goal.Arm(seconds));
            return this;
        }

        public Builder hover(int zone, double seconds) {
            this.goals.add(new Goal.Hover(zone, seconds));
            Zone z = this.zones.get(zone);
            this.waypoint(z.centerX(), z.centerY(), z.centerZ());
            return this;
        }

        public Builder heading(int zone, List<Integer> markers, double hold, double tolerance) {
            this.goals.add(new Goal.Heading(zone, markers, hold, tolerance));
            Zone z = this.zones.get(zone);
            this.waypoint(z.centerX(), z.centerY(), z.centerZ());
            return this;
        }

        public Builder rings(int from, int to, boolean strict) {
            this.goals.add(new Goal.Rings(from, to, strict));
            for (int i = from; i < to; i++) {
                this.through(this.rings.get(i));
            }
            return this;
        }

        public Builder laps(int from, int to, int laps) {
            this.goals.add(new Goal.Laps(from, to, laps));
            for (int i = from; i < to; i++) {
                this.through(this.rings.get(i));
            }
            this.through(this.rings.get(from));
            return this;
        }

        public Builder land(int pad) {
            this.goals.add(new Goal.Land(pad));
            Pad p = this.pads.get(pad);
            this.waypoint(p.x(), START_HEIGHT, p.z());
            return this;
        }

        public Builder tricks(Set<TrickType> types, int count, int zone) {
            this.goals.add(new Goal.Tricks(types.isEmpty() ? Set.of() : EnumSet.copyOf(types), count, zone));
            Zone z = this.zones.get(zone);
            this.waypoint(z.centerX(), z.centerY(), z.centerZ());
            return this;
        }

        public Builder combo(int tricks, int zone) {
            this.goals.add(new Goal.Combo(tricks, zone));
            Zone z = this.zones.get(zone);
            this.waypoint(z.centerX(), z.centerY(), z.centerZ());
            return this;
        }

        public Builder waypoint(double x, double y, double z) {
            this.path.add(new double[]{x, y, z});
            return this;
        }

        public Builder limit(double seconds) {
            this.timeLimit = seconds;
            return this;
        }

        public Builder stars(StarRule rule) {
            this.stars = rule;
            return this;
        }

        private void through(Ring ring) {
            double dx = ring.directionX();
            double dy = ring.directionY();
            double dz = ring.directionZ();
            this.waypoint(ring.x() - dx * APPROACH, ring.y() - dy * APPROACH, ring.z() - dz * APPROACH);
            this.waypoint(ring.x(), ring.y(), ring.z());
            this.waypoint(ring.x() + dx * EXIT, ring.y() + dy * EXIT, ring.z() + dz * EXIT);
        }

        public Lesson build() {
            if (this.goals.isEmpty()) {
                throw new IllegalStateException("Lektion ohne Ziel: " + this.id);
            }
            if (!(this.timeLimit > 0.0)) {
                throw new IllegalStateException("Lektion ohne Zeitlimit: " + this.id);
            }
            List<Probe> airspace = new ArrayList<>();
            for (Ring ring : this.rings) {
                double halfW = ring.width() * 0.5 + RING_CLEARANCE;
                double halfH = ring.height() * 0.5 + RING_CLEARANCE;
                airspace.add(ring.flat()
                        ? new Probe(ring.x(), ring.y(), ring.z(), halfW, 1.0, halfH, ring.heading())
                        : new Probe(ring.x(), ring.y(), ring.z(), halfW, halfH, 1.0, ring.heading()));
            }
            for (Zone zone : this.zones) {
                airspace.add(new Probe(zone.centerX(), zone.centerY(), zone.centerZ(), (zone.maxX() - zone.minX()) * 0.5,
                        (zone.maxY() - zone.minY()) * 0.5, (zone.maxZ() - zone.minZ()) * 0.5, 0.0));
            }
            for (int i = 0; i + 1 < this.path.size(); i++) {
                double[] a = this.path.get(i);
                double[] b = this.path.get(i + 1);
                double dx = b[0] - a[0];
                double dy = b[1] - a[1];
                double dz = b[2] - a[2];
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                int steps = Math.max(1, (int) Math.ceil(length / CORRIDOR_STEP));
                for (int s = i == 0 ? 0 : 1; s <= steps; s++) {
                    double t = s / (double) steps;
                    double y = Math.max(a[1] + dy * t, CORRIDOR_HALF + 0.3);
                    airspace.add(new Probe(a[0] + dx * t, y, a[2] + dz * t, CORRIDOR_HALF, CORRIDOR_HALF, CORRIDOR_HALF, 0.0));
                }
            }
            double[] bounds = {0.0, 0.0, 0.0, 0.0, START_HEIGHT, 0.0};
            for (Ring ring : this.rings) {
                double r = ring.reach();
                grow(bounds, ring.x() - r, ring.y() - r, ring.z() - r);
                grow(bounds, ring.x() + r, ring.y() + r, ring.z() + r);
            }
            for (Zone zone : this.zones) {
                grow(bounds, zone.minX(), zone.minY(), zone.minZ());
                grow(bounds, zone.maxX(), zone.maxY(), zone.maxZ());
            }
            for (Pad pad : this.pads) {
                grow(bounds, pad.x() - pad.radius(), 0.0, pad.z() - pad.radius());
                grow(bounds, pad.x() + pad.radius(), 0.0, pad.z() + pad.radius());
            }
            for (Marker marker : this.markers) {
                grow(bounds, marker.x(), marker.height(), marker.z());
            }
            for (Pylon pylon : this.pylons) {
                grow(bounds, pylon.x(), pylon.height(), pylon.z());
            }
            for (double[] point : this.path) {
                grow(bounds, point[0], point[1], point[2]);
            }
            return new Lesson(this.id, this.chapter, this.number, this.icon, this.rings, this.zones, this.pads, this.markers, this.pylons,
                    this.goals, this.timeLimit, this.stars, this.path, airspace, bounds);
        }

        private static void grow(double[] bounds, double x, double y, double z) {
            bounds[0] = Math.min(bounds[0], x);
            bounds[1] = Math.min(bounds[1], y);
            bounds[2] = Math.min(bounds[2], z);
            bounds[3] = Math.max(bounds[3], x);
            bounds[4] = Math.max(bounds[4], y);
            bounds[5] = Math.max(bounds[5], z);
        }
    }
}
