package de.lardtom.propwash.trick;

import java.util.ArrayList;
import java.util.List;

final class ManeuverClassifier {
    static final double STROKE_RATE = 130.0;
    static final double YAW_RATE = 150.0;
    static final double SPLIT_COS = Math.cos(Math.toRadians(55.0));
    static final double PAUSE_SPLIT = 0.3;
    static final double MIN_STROKE = 40.0;
    static final double MERGE_GAP = 0.35;
    static final double GROUP_GAP = 0.6;
    static final int MAX_GROUP = 6;
    static final double MAX_GROUP_SECONDS = 6.0;
    static final double INVERTED_UP = -0.5;
    static final double END_UP = 0.15;
    static final double MIN_SPIN_RATE = 150.0;
    static final double HALF_MIN = 110.0;
    static final double HALF_MAX = 270.0;
    static final double MATTY_MIN_PITCH = 230.0;
    static final double MATTY_MAX_SECONDS = 4.0;
    static final double MATTY_MAX_PITCH = 450.0;
    static final double PUNCH_UP = -0.1;
    static final double PUNCH_THROTTLE = 0.45;
    static final double PUNCH_BACK = -0.3;
    static final double MIN_PUNCH = 0.15;
    static final double REVERSAL_START_SPEED = 3.0;
    static final double REVERSAL_END_SPEED = 2.0;
    static final double REVERSAL_DOT = -0.3;
    static final double LOOP_MIN_PITCH = 230.0;
    static final double LOOP_MAX_ROLL = 120.0;
    static final double LOOP_MIN_GAIN = 2.5;
    static final double LOOP_MIN_SECONDS = 0.6;
    static final double LOOP_MAX_SECONDS = 6.0;
    static final double LOOP_MIN_PATH_TURN = 200.0;
    static final double SPLIT_MIN_AXIS = 90.0;
    static final double SPLIT_MAX_AXIS = 300.0;
    static final double SPLIT_MIN_DROP = 2.0;
    static final double SPLIT_MAX_SECONDS = 4.0;
    static final double IMMELMANN_MIN_RISE = 1.5;
    static final double JUICY_MAX_SECONDS = 2.5;
    static final double JUICY_MIN_TOTAL = 300.0;
    static final double REWIND_MIN = 150.0;
    static final double REWIND_COS = -0.7;
    static final double REWIND_TOLERANCE = 60.0;
    static final double REWIND_FRACTION = 0.35;
    static final double RUBIK_MIN_STROKE = 70.0;
    static final double RUBIK_MIN_TOTAL = 540.0;
    static final double RUBIK_MAX_SECONDS = 4.0;
    static final int RUBIK_MIN_CHANGES = 2;
    static final int RUBIK_MIN_PARTIAL = 2;
    static final double SNAP_MIN = 300.0;
    static final double SNAP_RATE = 500.0;
    static final double CORK_MIN = 330.0;
    static final double CORK_MAX_RATE = 450.0;
    static final double CORK_THROTTLE = 0.3;
    static final double CORK_MIN_SPEED = 4.0;
    static final double CORK_MAX_TRAVEL_ANGLE = 75.0;
    static final double MCTWIST_PITCH = 300.0;
    static final double MCTWIST_YAW = 300.0;
    static final double YAW_MIN_TURN = 330.0;
    static final double YAW_CROSS_FRACTION = 0.5;
    static final double YAW_PATH_FRACTION = 0.5;
    static final double SPIN_INVERTED_FRACTION = 0.6;
    static final double SPIN_UPRIGHT_FRACTION = 0.6;
    static final double SPIN_UPRIGHT_UP = 0.3;
    static final double TRIPPY_DROP = 4.0;
    static final double TRIPPY_ORBIT = 270.0;
    static final double ENTRY_MIN = 90.0;
    static final double ENTRY_MAX = 270.0;
    static final double HANG_MIN_SECONDS = 0.9;
    static final double PATH_MIN_SPEED = 1.0;
    static final double HEADING_MIN_SPEED = 2.0;
    static final double SIDE_MAX_UP = 0.5;
    static final double SIDE_MIN_FRACTION = 0.5;
    static final double FLAT_NOSE_FRACTION = 0.6;
    static final double SIDE_MIN_UP = -0.7;
    static final double SIDE_MIN_TURN = 330.0;
    static final double SIDE_MIN_RATE = 200.0;
    static final double SIDE_PATH_FRACTION = 0.6;
    static final double SIDE_MAX_RADIUS = 6.0;
    static final double SIDE_MIN_SPEED = 2.5;
    static final double FUNKY_MIN_YAW = 120.0;
    static final double FUNKY_MAX_YAW = 250.0;
    static final double FUNKY_INVERTED_FRACTION = 0.5;
    static final double FUNKY_MIN_NOSE_TURN = 120.0;
    static final int TIC_TOC_MIN_HALVES = 4;
    static final double TIC_TOC_MIN = 120.0;
    static final double TIC_TOC_MAX = 240.0;
    static final double TIC_TOC_MAX_SECONDS = 6.0;
    static final double MODE_3D_FRACTION = 0.9;
    static final double REVERSE_THROTTLE = -0.05;
    static final double INVERTED_FLIGHT_MIN_SECONDS = 1.0;
    static final double INVERTED_FLIGHT_REVERSE_FRACTION = 0.6;
    static final double INVERTED_FLIGHT_MAX_DROP = 1.5;
    static final double HOVER_MAX_SPEED = 1.5;
    static final double POWER_MATTY_MAX_PITCH = 810.0;
    static final double POWER_FLIP_EXTRA = 270.0;
    static final double LOOP_PART_MIN = 60.0;
    static final double LOOP_TRICK_MIN = 250.0;
    static final double LOOP_TRICK_MAX = 450.0;
    static final double SIDE_LOOP_MIN_ROLL = 300.0;
    static final double FLIP_REWIND_MIN = 300.0;
    static final double SEGMENT_MIN_PAUSE = 0.4;
    static final double SEGMENT_MAX_PAUSE = HANG_MIN_SECONDS;
    static final double SEGMENT_INVERTED_FRACTION = 0.7;
    static final double HALF_MATTY_MAX_PITCH = 300.0;
    static final double BACKWARD_COS = -0.5;
    static final double SPLIT_540_MIN_ROLL = 450.0;
    static final double SPLIT_540_MAX_ROLL = 650.0;
    static final double SPLITBACK_MIN_PITCH = 450.0;
    static final double SPLITBACK_MAX_PITCH = 650.0;
    static final double SPLIT_VARIANT_MAX_SECONDS = 5.0;
    static final double VANNY_MIN_YAW = 120.0;
    static final double VANNY_MAX_YAW = 240.0;
    static final double VANNY_MIN_TOTAL = 300.0;
    static final double VANNY_MAX_TOTAL = 420.0;
    static final double VANNY_MIN_ROLL = 250.0;
    static final double VANNY_MAX_ROLL = 450.0;
    static final double VANNY_MIN_SPEED = 3.0;
    static final double VANNY_MAX_PATH_TURN = 60.0;
    static final double COIN_DROP_SINK = 2.0;
    static final double COIN_DROP_MIN = 2.0;
    static final double COIN_DROP_THROTTLE = 0.2;
    static final double TORNADO_DIVE_FRACTION = 0.7;
    static final double TORNADO_MIN_DROP = 3.0;
    static final double DIVE_SINK = -4.0;
    static final double BARANI_MIN_GAIN = 1.5;
    static final double REVERSE_INVERTED_FRACTION = 0.5;
    static final double LOOP_3D_REVERSE_FRACTION = 0.4;
    static final double LOOP_3D_MIN_PITCH = 300.0;
    static final double LOOP_3D_MIN_PATH_TURN = 270.0;

    record Candidate(TrickType type, double magnitude, double start, double end, double nearest, int strokes) {
        long value() {
            return Math.round(this.type.points(this.magnitude, Double.POSITIVE_INFINITY)) * 100_000L - 100L + this.strokes;
        }
    }

    private final List<Stroke> strokes = new ArrayList<>();
    private final Metrics group = new Metrics();
    private final Metrics part = new Metrics();
    private ManeuverLog log;

    ManeuverClassifier() {
    }

    List<Stroke> strokes() {
        return this.strokes;
    }

    void classify(ManeuverLog log, int end, List<Trick> out) {
        this.log = log;
        int last = Math.min(end, log.size - 1);
        if (last < 1) {
            return;
        }
        int from = out.size();
        this.segment(last);
        this.parse(out);
        this.hang(last, out, from);
    }

    void segment(int last) {
        ManeuverLog l = this.log;
        List<Stroke> list = this.strokes;
        list.clear();
        Stroke current = null;
        double leadPitch = 0.0;
        double leadRoll = 0.0;
        double leadYaw = 0.0;
        int leadFirst = -1;
        for (int i = 1; i <= last; i++) {
            double dt = l.dt[i];
            if (!(dt > 0.0)) {
                continue;
            }
            double dp = l.pitch[i];
            double dr = l.roll[i];
            double dy = l.yaw[i];
            double tumble = Math.hypot(dp, dr);
            boolean tumbling = tumble / dt >= STROKE_RATE;
            boolean yawing = !tumbling && Math.abs(dy) / dt >= YAW_RATE;
            if (tumbling) {
                if (current != null && !current.yawStroke && current.cosine(dp, dr) >= SPLIT_COS) {
                    extend(current, l, i, dp, dr, dy, tumble);
                } else {
                    close(current, list);
                    current = new Stroke(false, i, l.start(i));
                    if (leadFirst >= 0 && leadPitch * dp + leadRoll * dr > 0.0) {
                        current.first = leadFirst;
                        current.start = l.start(leadFirst);
                        current.pitch += leadPitch;
                        current.roll += leadRoll;
                        current.yaw += leadYaw;
                    }
                    extend(current, l, i, dp, dr, dy, tumble);
                }
                leadFirst = -1;
            } else if (yawing) {
                if (current != null && current.yawStroke && Math.signum(current.yaw) == Math.signum(dy)) {
                    extend(current, l, i, dp, dr, dy, Math.abs(dy));
                } else {
                    close(current, list);
                    current = new Stroke(true, i, l.start(i));
                    if (leadFirst >= 0 && leadYaw * dy > 0.0) {
                        current.first = leadFirst;
                        current.start = l.start(leadFirst);
                        current.pitch += leadPitch;
                        current.roll += leadRoll;
                        current.yaw += leadYaw;
                    }
                    extend(current, l, i, dp, dr, dy, Math.abs(dy));
                }
                leadFirst = -1;
            } else if (current != null) {
                boolean along = current.yawStroke ? current.yaw * dy > 0.0 : current.cosine(dp, dr) > 0.0;
                if (along) {
                    current.pitch += dp;
                    current.roll += dr;
                    current.yaw += dy;
                }
                current.idle += dt;
                if (current.idle >= PAUSE_SPLIT) {
                    close(current, list);
                    current = null;
                    leadFirst = -1;
                }
            } else {
                if (leadFirst < 0) {
                    leadFirst = i;
                    leadPitch = 0.0;
                    leadRoll = 0.0;
                    leadYaw = 0.0;
                }
                leadPitch += dp;
                leadRoll += dr;
                leadYaw += dy;
            }
        }
        close(current, list);
        list.removeIf(s -> s.angle() < MIN_STROKE);
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int i = 0; i + 1 < list.size(); i++) {
                Stroke a = list.get(i);
                Stroke b = list.get(i + 1);
                if (b.start - a.end <= MERGE_GAP && a.sameDirection(b, SPLIT_COS) && !this.paused(a, b)) {
                    a.absorb(b);
                    list.remove(i + 1);
                    merged = true;
                    break;
                }
            }
        }
    }

    private boolean paused(Stroke a, Stroke b) {
        if (b.activeStart - a.end < SEGMENT_MIN_PAUSE || a.yawStroke || !half(a.angle()) || !half(b.angle())) {
            return false;
        }
        int from = a.last + 1;
        int to = from;
        while (to + 1 < this.log.size && this.log.time[to + 1] <= b.activeStart) {
            to++;
        }
        if (to >= this.log.size || this.log.time[to] > b.activeStart) {
            return false;
        }
        Metrics pause = this.part.compute(this.log, from, to);
        return pause.invertedFraction >= SEGMENT_INVERTED_FRACTION && pause.punch < MIN_PUNCH;
    }

    private static void extend(Stroke s, ManeuverLog l, int i, double dp, double dr, double dy, double path) {
        s.pitch += dp;
        s.roll += dr;
        s.yaw += dy;
        s.path += path;
        s.active += l.dt[i];
        s.last = i;
        s.end = l.time[i];
        s.idle = 0.0;
    }

    private static void close(Stroke s, List<Stroke> list) {
        if (s != null) {
            list.add(s);
        }
    }

    private void parse(List<Trick> out) {
        List<Stroke> list = this.strokes;
        int n = list.size();
        if (n == 0) {
            return;
        }
        long[] best = new long[n + 1];
        Candidate[] choice = new Candidate[n];
        int[] next = new int[n];
        for (int i = n - 1; i >= 0; i--) {
            best[i] = best[i + 1];
            next[i] = i + 1;
            for (int j = i; j < n && j < i + MAX_GROUP; j++) {
                boolean wide = j > i && list.get(j).start - list.get(j - 1).end > GROUP_GAP;
                if (wide && !this.segmentPair(i, j)) {
                    break;
                }
                Candidate c = this.candidate(i, j);
                if (c != null) {
                    long value = c.value() + best[j + 1];
                    if (value > best[i]) {
                        best[i] = value;
                        choice[i] = c;
                        next[i] = j + 1;
                    }
                }
                if (wide) {
                    break;
                }
            }
        }
        for (int i = 0; i < n; ) {
            Candidate c = choice[i];
            if (c != null) {
                out.add(new Trick(c.type(), c.end(), c.magnitude(), c.nearest(), c.start()));
            }
            i = next[i];
        }
    }

    Candidate candidate(int i, int j) {
        List<Stroke> list = this.strokes;
        Stroke first = list.get(i);
        Stroke last = list.get(j);
        Metrics g = this.group.compute(this.log, first.first, last.last);
        if (g.contact || g.seconds > MAX_GROUP_SECONDS) {
            return null;
        }
        int count = j - i + 1;
        int yawStrokes = 0;
        boolean aligned = true;
        for (int m = i; m <= j; m++) {
            Stroke s = list.get(m);
            if (s.yawStroke) {
                yawStrokes++;
            }
            if (m > i && !first.sameDirection(s, SPLIT_COS)) {
                aligned = false;
            }
        }
        TrickType type = null;
        double magnitude = 0.0;
        if (aligned && yawStrokes == 0) {
            Stroke sum = combined(list, i, j);
            Result r = this.single(sum, g);
            if (count == 2 && (r == null || plain(r.type)) && this.segmented(first, last, g)) {
                r = new Result(TrickType.SEGMENTED, last.activeStart - first.end);
            }
            if (r != null) {
                type = r.type;
                magnitude = r.magnitude;
            }
        } else if (aligned && yawStrokes == count) {
            Stroke sum = combined(list, i, j);
            Result r = this.yawSpin(sum, g);
            if (r != null) {
                type = r.type;
                magnitude = r.magnitude;
            }
        } else if (yawStrokes == 0 && count == 2) {
            Result r = this.pair(first, last, g);
            if (r != null) {
                type = r.type;
                magnitude = r.magnitude;
            }
        } else if (yawStrokes == 2 && count == 3) {
            Result r = vanny(list.get(i), list.get(i + 1), list.get(j), g);
            if (r != null) {
                type = r.type;
                magnitude = r.magnitude;
            }
        } else if (yawStrokes == 0) {
            Result loop = count == 3 ? loopCombo(list.get(i), list.get(i + 1), list.get(j), g) : null;
            if (loop != null) {
                type = loop.type;
                magnitude = loop.magnitude;
            } else if (this.ticToc(list, i, j, g)) {
                type = TrickType.TIC_TOC;
                magnitude = count;
            } else if (this.rubiksCube(list, i, j, g)) {
                type = TrickType.RUBIKS_CUBE;
                magnitude = rubikStrokes(list, i, j);
            }
        } else if (yawStrokes == 1 && count <= 3) {
            Result r = this.spinGroup(list, i, j, g);
            if (r != null) {
                type = r.type;
                magnitude = r.magnitude;
            }
        }
        if (type == null) {
            return null;
        }
        return new Candidate(type, magnitude, g.startTime, g.endTime, g.nearest, count);
    }

    private record Result(TrickType type, double magnitude) {
    }

    private static Stroke combined(List<Stroke> list, int i, int j) {
        Stroke first = list.get(i);
        Stroke sum = new Stroke(first.yawStroke, first.first, first.start);
        for (int m = i; m <= j; m++) {
            sum.absorb(list.get(m));
        }
        return sum;
    }

    private Result single(Stroke s, Metrics g) {
        Result side = sideTrippy(s, g);
        if (side != null) {
            return side;
        }
        double pitch = Math.abs(s.pitch);
        double roll = Math.abs(s.roll);
        double yaw = Math.abs(s.yaw);
        double main = s.tumble();
        double rate = s.rate();
        Stroke.Kind kind = s.kind();
        if (kind == Stroke.Kind.ROLL && roll >= TrickDetector.TURN_FIRST && rate >= MIN_SPIN_RATE && g.diveFraction >= TORNADO_DIVE_FRACTION
                && g.drop >= TORNADO_MIN_DROP) {
            return new Result(TrickType.TORNADO, TrickDetector.turns(roll));
        }
        if (g.minUp >= INVERTED_UP) {
            return null;
        }
        boolean pitchy = s.pitchDominant();
        if (pitchy && pitch >= MCTWIST_PITCH && yaw >= MCTWIST_YAW && g.endUp >= END_UP) {
            return new Result(TrickType.MCTWIST, yaw);
        }
        if (kind == Stroke.Kind.PITCH && pitch >= LOOP_3D_MIN_PITCH && loop3d(g)) {
            return new Result(TrickType.POWER_LOOP_3D, pitch);
        }
        if (kind == Stroke.Kind.PITCH && g.punch >= MIN_PUNCH && g.reversal && g.seconds <= MATTY_MAX_SECONDS) {
            if (pitch >= MATTY_MIN_PITCH && pitch <= MATTY_MAX_PITCH) {
                return new Result(s.pitch > 0.0 ? TrickType.MATTY_FLIP : TrickType.ANTI_MATTY, pitch);
            }
            if (s.pitch > 0.0 && pitch > MATTY_MAX_PITCH && pitch <= POWER_MATTY_MAX_PITCH) {
                return new Result(TrickType.POWER_MATTY, pitch);
            }
        }
        if (kind == Stroke.Kind.PITCH && pitch >= LOOP_MIN_PITCH && roll < LOOP_MAX_ROLL && loop(g) && (!g.reversal || pitch > MATTY_MAX_PITCH)) {
            if (pitch - g.verticalTurn >= POWER_FLIP_EXTRA) {
                return new Result(TrickType.POWER_FLIP, pitch);
            }
            return new Result(TrickType.POWER_LOOP, g.gain);
        }
        if (kind == Stroke.Kind.ROLL && roll >= SIDE_LOOP_MIN_ROLL && pitch < LOOP_MAX_ROLL && loop(g) && !g.reversal) {
            return new Result(TrickType.SIDE_LOOP, g.gain);
        }
        if (splitS(pitch, roll, g)) {
            return new Result(TrickType.SPLIT_S, g.drop);
        }
        if (kind == Stroke.Kind.DIAGONAL && main >= SNAP_MIN && rate >= SNAP_RATE) {
            return new Result(TrickType.SNAP_ROLL, main);
        }
        if (main < TrickDetector.TURN_FIRST || rate < MIN_SPIN_RATE) {
            return null;
        }
        if (g.mode3dFraction >= MODE_3D_FRACTION && g.invertedSeconds > 0.0 && g.reverseInverted >= REVERSE_INVERTED_FRACTION) {
            int turns = TrickDetector.turns(main);
            return new Result(pitchy ? TrickType.FLIP_3D : TrickType.ROLL_3D, turns);
        }
        if (kind == Stroke.Kind.ROLL && roll >= CORK_MIN && rate <= CORK_MAX_RATE && g.avgThrottle >= CORK_THROTTLE
                && g.minSpeed >= CORK_MIN_SPEED && g.travelAngle <= CORK_MAX_TRAVEL_ANGLE) {
            return new Result(TrickType.CORKSCREW, Math.floor((roll + 360.0 - CORK_MIN) / 360.0));
        }
        int turns = TrickDetector.turns(main);
        return new Result(pitchy ? TrickType.flips(turns) : TrickType.rolls(turns), turns);
    }

    private static boolean plain(TrickType type) {
        return switch (type) {
            case FLIP, DOUBLE_FLIP, TRIPLE_FLIP, ROLL, DOUBLE_ROLL, TRIPLE_ROLL -> true;
            default -> false;
        };
    }

    private static boolean loop(Metrics g) {
        return g.gain >= LOOP_MIN_GAIN && g.seconds >= LOOP_MIN_SECONDS && g.seconds <= LOOP_MAX_SECONDS && g.verticalTurn >= LOOP_MIN_PATH_TURN;
    }

    private static boolean loop3d(Metrics g) {
        return g.mode3dFraction >= MODE_3D_FRACTION && g.reverseFraction >= LOOP_3D_REVERSE_FRACTION && g.verticalTurn >= LOOP_3D_MIN_PATH_TURN
                && g.seconds >= LOOP_MIN_SECONDS && g.seconds <= LOOP_MAX_SECONDS;
    }

    private static Result sideTrippy(Stroke s, Metrics g) {
        double nose = Math.abs(g.noseTurn);
        if (s.kind() != Stroke.Kind.PITCH || g.minUp < SIDE_MIN_UP || g.sideFraction < SIDE_MIN_FRACTION || nose < SIDE_MIN_TURN
                || nose / g.seconds < SIDE_MIN_RATE || g.minSpeed < SIDE_MIN_SPEED || g.radius > SIDE_MAX_RADIUS) {
            return null;
        }
        if (g.headingTurn * g.noseTurn <= 0.0 || Math.abs(g.headingTurn) < SIDE_PATH_FRACTION * nose) {
            return null;
        }
        return new Result(TrickType.SIDE_TRIPPY, 1 + (int) ((nose - SIDE_MIN_TURN) / TrickDetector.TURN_FULL));
    }

    private static Result loopCombo(Stroke a, Stroke b, Stroke c, Metrics g) {
        if (!a.pitchDominant() || !c.pitchDominant() || a.pitch >= 0.0 || c.pitch >= 0.0 || a.angle() < LOOP_PART_MIN || c.angle() < LOOP_PART_MIN
                || b.angle() < LOOP_TRICK_MIN || b.angle() > LOOP_TRICK_MAX || !loop(g) || g.endUp < END_UP || g.minUp >= INVERTED_UP) {
            return null;
        }
        Stroke.Kind kind = b.kind();
        if (kind == Stroke.Kind.ROLL) {
            return new Result(TrickType.POWER_ROLL, b.angle());
        }
        if (kind == Stroke.Kind.PITCH && b.pitch > 0.0) {
            return new Result(TrickType.POWER_FLIP, b.angle());
        }
        return null;
    }

    private static Result vanny(Stroke a, Stroke b, Stroke c, Metrics g) {
        if (!a.yawStroke || b.yawStroke || !c.yawStroke || Math.signum(a.yaw) != Math.signum(c.yaw) || b.kind() != Stroke.Kind.ROLL) {
            return null;
        }
        double ya = Math.abs(a.yaw);
        double yc = Math.abs(c.yaw);
        double roll = Math.abs(b.roll);
        if (ya < VANNY_MIN_YAW || ya > VANNY_MAX_YAW || yc < VANNY_MIN_YAW || yc > VANNY_MAX_YAW || ya + yc < VANNY_MIN_TOTAL
                || ya + yc > VANNY_MAX_TOTAL || roll < VANNY_MIN_ROLL || roll > VANNY_MAX_ROLL) {
            return null;
        }
        if (g.minUp >= INVERTED_UP || g.endUp < END_UP || g.startSpeed < VANNY_MIN_SPEED || Math.abs(g.headingTurn) > VANNY_MAX_PATH_TURN) {
            return null;
        }
        return new Result(TrickType.VANNY_ROLL, roll);
    }

    private static boolean flatTurn(Stroke s, Metrics g) {
        return g.sideFraction >= SIDE_MIN_FRACTION && Math.abs(g.noseTurn) >= FLAT_NOSE_FRACTION * s.angle();
    }

    private boolean ticToc(List<Stroke> list, int i, int j, Metrics g) {
        int count = j - i + 1;
        if (count < TIC_TOC_MIN_HALVES || g.mode3dFraction < MODE_3D_FRACTION || g.minUp >= INVERTED_UP || g.seconds > TIC_TOC_MAX_SECONDS) {
            return false;
        }
        boolean pitch = list.get(i).pitchDominant();
        for (int m = i; m <= j; m++) {
            Stroke s = list.get(m);
            double angle = s.angle();
            if (s.yawStroke || s.pitchDominant() != pitch || s.kind() == Stroke.Kind.DIAGONAL || angle < TIC_TOC_MIN || angle > TIC_TOC_MAX) {
                return false;
            }
            if (m > i && !list.get(m - 1).opposite(s, REWIND_COS)) {
                return false;
            }
        }
        return true;
    }

    private static boolean splitS(double pitch, double roll, Metrics g) {
        return roll >= SPLIT_MIN_AXIS && roll <= SPLIT_MAX_AXIS && pitch >= SPLIT_MIN_AXIS && pitch <= SPLIT_MAX_AXIS
                && g.reversal && g.drop >= SPLIT_MIN_DROP && g.seconds <= SPLIT_MAX_SECONDS && g.minUp < INVERTED_UP;
    }

    private Result pair(Stroke a, Stroke b, Metrics g) {
        if (g.minUp >= INVERTED_UP) {
            return null;
        }
        double angleA = a.angle();
        double angleB = b.angle();
        if (a.opposite(b, REWIND_COS) && angleA >= REWIND_MIN && angleB >= REWIND_MIN
                && Math.abs(angleA - angleB) <= Math.max(REWIND_TOLERANCE, REWIND_FRACTION * Math.max(angleA, angleB)) && g.endUp >= END_UP) {
            double angle = Math.min(angleA, angleB);
            return new Result(angle >= FLIP_REWIND_MIN ? TrickType.FLIP_REWIND : TrickType.REWIND, angle);
        }
        Stroke.Kind ka = a.kind();
        Stroke.Kind kb = b.kind();
        if (ka == Stroke.Kind.PITCH && kb == Stroke.Kind.ROLL && half(angleA) && half(angleB) && g.endUp >= END_UP) {
            if (a.pitch > 0.0 && g.seconds <= JUICY_MAX_SECONDS && angleA + angleB >= JUICY_MIN_TOTAL) {
                return new Result(TrickType.JUICY_FLICK, angleA + angleB);
            }
            if (a.pitch < 0.0 && g.rise >= IMMELMANN_MIN_RISE && g.reversal) {
                return new Result(TrickType.IMMELMANN, g.rise);
            }
        }
        if (ka == Stroke.Kind.ROLL && kb == Stroke.Kind.PITCH && b.pitch > 0.0 && half(angleA) && angleB >= HALF_MIN
                && angleB <= HALF_MATTY_MAX_PITCH && g.punch >= MIN_PUNCH && !g.reversal && g.backwardEnd && g.endUp >= END_UP
                && g.netNoseTurn >= FUNKY_MIN_NOSE_TURN && g.seconds <= MATTY_MAX_SECONDS) {
            return new Result(TrickType.HALF_MATTY, angleB);
        }
        if (ka == Stroke.Kind.ROLL && kb == Stroke.Kind.PITCH && b.pitch < 0.0 && g.reversal && g.drop >= SPLIT_MIN_DROP
                && g.seconds <= SPLIT_VARIANT_MAX_SECONDS) {
            if (angleA >= SPLIT_540_MIN_ROLL && angleA <= SPLIT_540_MAX_ROLL && angleB >= SPLIT_MIN_AXIS && angleB <= SPLIT_MAX_AXIS) {
                return new Result(TrickType.SPLIT_S_540, g.drop);
            }
            if (angleA >= SPLIT_MIN_AXIS && angleA <= SPLIT_MAX_AXIS && angleB >= SPLITBACK_MIN_PITCH && angleB <= SPLITBACK_MAX_PITCH) {
                return new Result(TrickType.SPLITBACK, g.drop);
            }
        }
        if (ka == Stroke.Kind.ROLL && kb == Stroke.Kind.PITCH && b.pitch < 0.0 && splitS(angleB, angleA, g)) {
            return new Result(TrickType.SPLIT_S, g.drop);
        }
        if (splitS(Math.abs(a.pitch) + Math.abs(b.pitch), Math.abs(a.roll) + Math.abs(b.roll), g)) {
            return new Result(TrickType.SPLIT_S, g.drop);
        }
        return null;
    }

    private boolean segmented(Stroke a, Stroke b, Metrics g) {
        double pause = b.activeStart - a.end;
        if (pause < SEGMENT_MIN_PAUSE || pause > SEGMENT_MAX_PAUSE || a.yawStroke || b.yawStroke || !a.sameDirection(b, SPLIT_COS)
                || a.kind() == Stroke.Kind.DIAGONAL || a.kind() != b.kind() || !half(a.angle()) || !half(b.angle()) || g.endUp < END_UP) {
            return false;
        }
        return this.paused(a, b);
    }

    boolean segmentPair(int i, int j) {
        Stroke a = this.strokes.get(i);
        Stroke b = this.strokes.get(j);
        double pause = b.activeStart - a.end;
        return j == i + 1 && pause <= SEGMENT_MAX_PAUSE && !a.yawStroke && !b.yawStroke && a.sameDirection(b, SPLIT_COS)
                && half(a.angle()) && half(b.angle());
    }

    private static boolean half(double angle) {
        return angle >= HALF_MIN && angle <= HALF_MAX;
    }

    private boolean rubiksCube(List<Stroke> list, int i, int j, Metrics g) {
        int main = rubikStrokes(list, i, j);
        if (main < 3 || g.minUp >= INVERTED_UP || g.endUp < END_UP || g.seconds > RUBIK_MAX_SECONDS) {
            return false;
        }
        double total = 0.0;
        int changes = 0;
        int partial = 0;
        for (int m = i; m < i + main; m++) {
            Stroke s = list.get(m);
            double angle = s.angle();
            if (s.yawStroke || angle < RUBIK_MIN_STROKE) {
                return false;
            }
            if (s.kind() == Stroke.Kind.PITCH && flatTurn(s, this.part.compute(this.log, s.first, s.last))) {
                return false;
            }
            if (s.kind() == Stroke.Kind.ROLL && this.part.compute(this.log, s.first, s.last).diveFraction >= TORNADO_DIVE_FRACTION) {
                return false;
            }
            total += angle;
            if (s.partial()) {
                partial++;
            }
            if (m > i && list.get(m - 1).pitchDominant() != s.pitchDominant()) {
                changes++;
            }
        }
        return total >= RUBIK_MIN_TOTAL && changes >= RUBIK_MIN_CHANGES && partial >= RUBIK_MIN_PARTIAL;
    }

    private static int rubikStrokes(List<Stroke> list, int i, int j) {
        int count = j - i + 1;
        Stroke last = list.get(j);
        return count > 3 && !last.yawStroke && last.angle() < RUBIK_MIN_STROKE ? count - 1 : count;
    }

    private Result yawSpin(Stroke s, Metrics g) {
        return this.yawSpin(Math.abs(s.yaw), s.tumble(), g);
    }

    private Result yawSpin(double yaw, double cross, Metrics g) {
        if (yaw < YAW_MIN_TURN || cross > YAW_CROSS_FRACTION * yaw) {
            return null;
        }
        int turns = 1 + (int) ((yaw - YAW_MIN_TURN) / TrickDetector.TURN_FULL);
        double path = Math.abs(g.headingTurn);
        if (g.invertedFraction >= SPIN_INVERTED_FRACTION) {
            boolean orbiting = path >= TRIPPY_ORBIT && path >= YAW_PATH_FRACTION * yaw && path <= 2.0 * yaw;
            if (g.drop >= TRIPPY_DROP || orbiting) {
                return new Result(TrickType.TRIPPY_SPIN, turns);
            }
            if (path <= YAW_PATH_FRACTION * yaw) {
                return new Result(TrickType.INVERTED_YAW_SPIN, turns);
            }
            return null;
        }
        if (g.uprightFraction >= SPIN_UPRIGHT_FRACTION && path <= YAW_PATH_FRACTION * yaw) {
            if (g.drop >= COIN_DROP_MIN && g.drop / g.seconds >= COIN_DROP_SINK && g.avgThrottle <= COIN_DROP_THROTTLE) {
                return new Result(TrickType.COIN_DROP, turns);
            }
            return new Result(turns >= 2 ? TrickType.DOUBLE_YAW_SPIN : TrickType.YAW_SPIN, turns);
        }
        return null;
    }

    private Result spinGroup(List<Stroke> list, int i, int j, Metrics g) {
        Stroke spin = null;
        double pitchSum = 0.0;
        boolean pitchOnly = true;
        double sign = 0.0;
        double yawSum = 0.0;
        int tumbles = 0;
        for (int m = i; m <= j; m++) {
            Stroke s = list.get(m);
            yawSum += s.yaw;
            if (s.yawStroke) {
                spin = s;
                continue;
            }
            double angle = s.angle();
            if (angle < ENTRY_MIN || angle > ENTRY_MAX) {
                return null;
            }
            tumbles++;
            if (!s.pitchDominant() || (sign != 0.0 && Math.signum(s.pitch) != sign)) {
                pitchOnly = false;
            }
            sign = Math.signum(s.pitch);
            pitchSum += Math.abs(s.pitch);
        }
        int count = j - i + 1;
        if (spin == null || (count == 3 && list.get(i + 1) != spin)) {
            return null;
        }
        Metrics p = this.part.compute(this.log, spin.first, spin.last);
        if (count == 3 && tumbles == 2 && barani(list.get(i), list.get(j), Math.abs(spin.yaw), spin.tumble(), p, g)) {
            return new Result(TrickType.BARANI, Math.abs(spin.yaw));
        }
        if (count == 3 && tumbles == 2 && funkySplit(Math.abs(spin.yaw), spin.tumble(), p, g)) {
            return new Result(TrickType.FUNKY_SPLIT, Math.abs(spin.yaw));
        }
        Result r = Math.signum(yawSum) == Math.signum(spin.yaw) ? this.yawSpin(Math.abs(yawSum), spin.tumble(), p) : null;
        if (r == null || (r.type != TrickType.INVERTED_YAW_SPIN && r.type != TrickType.TRIPPY_SPIN)) {
            return null;
        }
        if (count == 3 && tumbles == 2 && pitchOnly && pitchSum >= MCTWIST_PITCH && g.endUp >= END_UP) {
            if (sign < 0.0 && loop(g)) {
                return new Result(TrickType.INVERTED_360_POWER_LOOP, Math.abs(yawSum));
            }
            return new Result(TrickType.MCTWIST, Math.abs(yawSum));
        }
        return r;
    }

    private static boolean barani(Stroke in, Stroke out, double yaw, double cross, Metrics spin, Metrics g) {
        return in.pitchDominant() && out.pitchDominant() && in.pitch < 0.0 && out.pitch > 0.0 && yaw >= FUNKY_MIN_YAW && yaw <= FUNKY_MAX_YAW
                && cross <= YAW_CROSS_FRACTION * yaw && spin.invertedFraction >= FUNKY_INVERTED_FRACTION && g.gain >= BARANI_MIN_GAIN
                && g.verticalTurn >= LOOP_MIN_PATH_TURN && g.endUp >= END_UP && g.netNoseTurn >= FUNKY_MIN_NOSE_TURN;
    }

    private static boolean funkySplit(double yaw, double cross, Metrics spin, Metrics g) {
        return yaw >= FUNKY_MIN_YAW && yaw <= FUNKY_MAX_YAW && cross <= YAW_CROSS_FRACTION * yaw
                && spin.invertedFraction >= FUNKY_INVERTED_FRACTION && g.endUp >= END_UP && g.minUp < INVERTED_UP
                && g.netNoseTurn >= FUNKY_MIN_NOSE_TURN;
    }

    private void hang(int last, List<Trick> out, int from) {
        ManeuverLog l = this.log;
        double run = 0.0;
        double best = 0.0;
        double bestEnd = 0.0;
        double bestStart = 0.0;
        double runStart = 0.0;
        double near = Double.POSITIVE_INFINITY;
        double bestNear = Double.POSITIVE_INFINITY;
        double reverse = 0.0;
        double bestReverse = 0.0;
        double runY = 0.0;
        double runLow = 0.0;
        double bestDrop = 0.0;
        boolean all3d = true;
        boolean best3d = false;
        double runSpeed = 0.0;
        double bestSpeed = 0.0;
        for (int i = 1; i <= last; i++) {
            double dt = l.dt[i];
            if (!(dt > 0.0)) {
                continue;
            }
            boolean still = Math.hypot(l.pitch[i], l.roll[i]) / dt < STROKE_RATE && Math.abs(l.yaw[i]) / dt < YAW_RATE;
            if (l.up[i] < INVERTED_UP && still && !l.contact[i]) {
                if (run == 0.0) {
                    runStart = l.start(i);
                    near = Double.POSITIVE_INFINITY;
                    reverse = 0.0;
                    runY = l.y[i - 1];
                    runLow = runY;
                    all3d = true;
                    runSpeed = 0.0;
                }
                run += dt;
                runSpeed += l.horizontalSpeed(i) * dt;
                near = Math.min(near, l.nearest[i]);
                runLow = Math.min(runLow, l.y[i]);
                all3d &= l.mode3d[i];
                if (l.mode3d[i] && l.throttle[i] <= REVERSE_THROTTLE) {
                    reverse += dt;
                }
                if (run > best) {
                    best = run;
                    bestEnd = l.time[i];
                    bestStart = runStart;
                    bestNear = near;
                    bestReverse = reverse;
                    bestDrop = runY - runLow;
                    best3d = all3d;
                    bestSpeed = runSpeed / run;
                }
            } else {
                run = 0.0;
            }
        }
        if (best < Math.min(HANG_MIN_SECONDS, INVERTED_FLIGHT_MIN_SECONDS)) {
            return;
        }
        for (int k = from; k < out.size(); k++) {
            Trick t = out.get(k);
            if (t.type() == TrickType.SEGMENTED && t.start() <= bestStart + 1.0E-6 && t.time() >= bestEnd - 1.0E-6) {
                return;
            }
        }
        if (best3d && best >= INVERTED_FLIGHT_MIN_SECONDS && bestReverse >= INVERTED_FLIGHT_REVERSE_FRACTION * best
                && bestDrop <= INVERTED_FLIGHT_MAX_DROP) {
            out.add(new Trick(bestSpeed <= HOVER_MAX_SPEED ? TrickType.INVERTED_HOVER : TrickType.INVERTED_FLIGHT, bestEnd, best, bestNear, bestStart));
        } else if (best >= HANG_MIN_SECONDS) {
            out.add(new Trick(TrickType.INVERTED_HANG, bestEnd, best, bestNear, bestStart));
        }
    }

    static final class Metrics {
        double startTime;
        double endTime;
        double seconds;
        double minUp;
        double endUp;
        boolean contact;
        double nearest;
        double gain;
        double drop;
        double rise;
        boolean reversal;
        double verticalTurn;
        double headingTurn;
        double punch;
        double avgThrottle;
        double minSpeed;
        double travelAngle;
        double invertedFraction;
        double uprightFraction;
        double sideFraction;
        double noseTurn;
        double netNoseTurn;
        double radius;
        double mode3dFraction;
        double diveFraction;
        double reverseFraction;
        double reverseInverted;
        double invertedSeconds;
        double startSpeed;
        boolean backwardEnd;

        Metrics compute(ManeuverLog l, int a, int b) {
            int before = Math.max(0, a - 1);
            this.startTime = l.start(a);
            this.endTime = l.time[b];
            this.seconds = Math.max(1.0E-3, this.endTime - this.startTime);
            this.minUp = 1.0;
            this.endUp = l.up[b];
            this.contact = false;
            this.nearest = Double.POSITIVE_INFINITY;
            double maxY = l.y[before];
            double punch = 0.0;
            double throttle = 0.0;
            double inverted = 0.0;
            double upright = 0.0;
            double total = 0.0;
            double minSpeed = Double.POSITIVE_INFINITY;
            double hs0 = l.horizontalSpeed(before);
            double refX;
            double refZ;
            if (hs0 >= REVERSAL_END_SPEED) {
                refX = l.vx[before] / hs0;
                refZ = l.vz[before] / hs0;
            } else {
                double h = Math.hypot(l.fwdX[before], l.fwdZ[before]);
                refX = h > 1.0E-6 ? l.fwdX[before] / h : 0.0;
                refZ = h > 1.0E-6 ? l.fwdZ[before] / h : 1.0;
            }
            double vertical = 0.0;
            double prevVertical = Double.NaN;
            double heading = 0.0;
            double prevHeading = Double.NaN;
            double nose = 0.0;
            double prevNose = Double.NaN;
            double side = 0.0;
            double threeD = 0.0;
            double diving = 0.0;
            double reverse = 0.0;
            double reverseUpsideDown = 0.0;
            double sumX = 0.0;
            double sumZ = 0.0;
            int points = 0;
            for (int i = before; i <= b; i++) {
                maxY = Math.max(maxY, l.y[i]);
                double speed = l.speed(i);
                if (speed >= PATH_MIN_SPEED) {
                    double along = l.vx[i] * refX + l.vz[i] * refZ;
                    double angle = Math.atan2(l.vy[i], along);
                    if (Double.isFinite(prevVertical)) {
                        vertical += wrap(angle - prevVertical);
                    }
                    prevVertical = angle;
                }
                double hs = l.horizontalSpeed(i);
                if (hs >= HEADING_MIN_SPEED) {
                    double angle = Math.atan2(l.vx[i], l.vz[i]);
                    if (Double.isFinite(prevHeading)) {
                        heading += wrap(angle - prevHeading);
                    }
                    prevHeading = angle;
                } else {
                    prevHeading = Double.NaN;
                }
                double nh = Math.hypot(l.fwdX[i], l.fwdZ[i]);
                if (nh >= 0.3) {
                    double angle = Math.atan2(l.fwdX[i], l.fwdZ[i]);
                    if (Double.isFinite(prevNose)) {
                        nose += wrap(angle - prevNose);
                    }
                    prevNose = angle;
                } else {
                    prevNose = Double.NaN;
                }
                if (i < a) {
                    continue;
                }
                sumX += l.x[i];
                sumZ += l.z[i];
                points++;
                double dt = l.dt[i];
                if (Math.abs(l.up[i]) <= SIDE_MAX_UP) {
                    side += dt;
                }
                if (l.mode3d[i]) {
                    threeD += dt;
                    if (l.throttle[i] <= REVERSE_THROTTLE) {
                        reverse += dt;
                        if (l.up[i] < INVERTED_UP) {
                            reverseUpsideDown += dt;
                        }
                    }
                }
                if (l.fwdY[i] <= TrickDetector.DIVE_NOSE_DOWN && l.vy[i] <= DIVE_SINK) {
                    diving += dt;
                }
                this.minUp = Math.min(this.minUp, l.up[i]);
                this.contact |= l.contact[i];
                this.nearest = Math.min(this.nearest, l.nearest[i]);
                minSpeed = Math.min(minSpeed, speed);
                total += dt;
                throttle += l.throttle[i] * dt;
                boolean braking = l.upX[i] * refX + l.upZ[i] * refZ < PUNCH_BACK;
                if (l.throttle[i] >= PUNCH_THROTTLE && (l.up[i] < PUNCH_UP || braking)) {
                    punch += dt;
                }
                if (l.up[i] < INVERTED_UP) {
                    inverted += dt;
                }
                if (l.up[i] >= SPIN_UPRIGHT_UP) {
                    upright += dt;
                }
            }
            double y0 = l.y[before];
            double y1 = l.y[b];
            this.gain = maxY - Math.max(y0, y1);
            this.drop = y0 - y1;
            this.rise = y1 - y0;
            double hs1 = l.horizontalSpeed(b);
            this.reversal = hs0 >= REVERSAL_START_SPEED && hs1 >= REVERSAL_END_SPEED
                    && l.vx[before] * l.vx[b] + l.vz[before] * l.vz[b] < REVERSAL_DOT * hs0 * hs1;
            this.verticalTurn = Math.abs(Math.toDegrees(vertical));
            this.headingTurn = Math.toDegrees(heading);
            this.punch = punch;
            this.avgThrottle = total > 0.0 ? throttle / total : 0.0;
            this.minSpeed = Double.isFinite(minSpeed) ? minSpeed : 0.0;
            double s0 = l.speed(before);
            double dx = l.x[b] - l.x[before];
            double dy = l.y[b] - l.y[before];
            double dz = l.z[b] - l.z[before];
            double travel = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (s0 >= PATH_MIN_SPEED && travel > 1.0E-6) {
                double cos = (l.vx[before] * dx + l.vy[before] * dy + l.vz[before] * dz) / (s0 * travel);
                this.travelAngle = Math.toDegrees(Math.acos(Math.clamp(cos, -1.0, 1.0)));
            } else {
                this.travelAngle = 180.0;
            }
            this.invertedFraction = total > 0.0 ? inverted / total : 0.0;
            this.uprightFraction = total > 0.0 ? upright / total : 0.0;
            this.sideFraction = total > 0.0 ? side / total : 0.0;
            this.mode3dFraction = total > 0.0 ? threeD / total : 0.0;
            this.diveFraction = total > 0.0 ? diving / total : 0.0;
            this.reverseFraction = total > 0.0 ? reverse / total : 0.0;
            this.invertedSeconds = inverted;
            this.reverseInverted = inverted > 0.0 ? reverseUpsideDown / inverted : 0.0;
            this.startSpeed = hs0;
            double nh1 = Math.hypot(l.fwdX[b], l.fwdZ[b]);
            this.backwardEnd = hs1 >= REVERSAL_END_SPEED && nh1 > 0.3 && (l.fwdX[b] * l.vx[b] + l.fwdZ[b] * l.vz[b]) / (nh1 * hs1) <= BACKWARD_COS;
            this.noseTurn = Math.toDegrees(nose);
            double h0 = Math.hypot(l.fwdX[before], l.fwdZ[before]);
            double h1 = Math.hypot(l.fwdX[b], l.fwdZ[b]);
            if (h0 > 1.0E-6 && h1 > 1.0E-6) {
                double cos = (l.fwdX[before] * l.fwdX[b] + l.fwdZ[before] * l.fwdZ[b]) / (h0 * h1);
                this.netNoseTurn = Math.toDegrees(Math.acos(Math.clamp(cos, -1.0, 1.0)));
            } else {
                this.netNoseTurn = 0.0;
            }
            double cx = points > 0 ? sumX / points : l.x[b];
            double cz = points > 0 ? sumZ / points : l.z[b];
            double spread = 0.0;
            for (int i = a; i <= b; i++) {
                spread += Math.hypot(l.x[i] - cx, l.z[i] - cz);
            }
            this.radius = points > 0 ? spread / points : 0.0;
            return this;
        }
    }

    static double wrap(double radians) {
        double r = radians % (2.0 * Math.PI);
        if (r > Math.PI) {
            r -= 2.0 * Math.PI;
        } else if (r < -Math.PI) {
            r += 2.0 * Math.PI;
        }
        return r;
    }
}
