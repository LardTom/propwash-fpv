package de.lardtom.propwash.trick;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class LineRecognizer {
    static final double SBANG_GAP = 0.8;
    static final double SBANG_MAX_SECONDS = 8.0;
    static final int SBANG_MIN_EJECTS = 1;
    static final int SBANG_MIN_MOVES = 2;
    static final double SHINOBI_GAP = 0.8;
    static final double IMMELMATT_GAP = 0.8;

    private final List<Trick> fresh = new ArrayList<>();
    private @Nullable Trick previous;
    private @Nullable Trick beforePrevious;
    private double lastEnd = Double.NaN;
    private double lineStart;
    private int lineTricks;
    private int lineEjects;
    private int lineMoves;
    private boolean sbangAwarded;

    LineRecognizer() {
    }

    void reset() {
        this.previous = null;
        this.beforePrevious = null;
        this.lastEnd = Double.NaN;
        this.lineTricks = 0;
        this.lineEjects = 0;
        this.lineMoves = 0;
        this.sbangAwarded = false;
    }

    void accept(List<Trick> out, int from) {
        int to = out.size();
        if (to <= from) {
            return;
        }
        this.fresh.clear();
        for (int i = from; i < to; i++) {
            Trick trick = out.get(i);
            if (!trick.type().line()) {
                this.fresh.add(trick);
            }
        }
        this.fresh.sort(Comparator.comparingDouble(Trick::start));
        for (Trick trick : this.fresh) {
            this.feed(trick, out);
        }
    }

    private void feed(Trick trick, List<Trick> out) {
        boolean linked = Double.isFinite(this.lastEnd) && trick.start() - this.lastEnd <= SBANG_GAP;
        if (!linked) {
            this.lineStart = trick.start();
            this.lineTricks = 0;
            this.lineEjects = 0;
            this.lineMoves = 0;
            this.sbangAwarded = false;
        }
        this.lineTricks++;
        if (trick.type() == TrickType.EJECT) {
            this.lineEjects++;
        } else if (sbangMove(trick.type())) {
            this.lineMoves++;
        }
        this.lastEnd = Double.isFinite(this.lastEnd) && linked ? Math.max(this.lastEnd, trick.time()) : trick.time();
        if (!this.sbangAwarded && this.lineEjects >= SBANG_MIN_EJECTS && this.lineMoves >= SBANG_MIN_MOVES
                && trick.time() - this.lineStart <= SBANG_MAX_SECONDS) {
            this.sbangAwarded = true;
            out.add(new Trick(TrickType.SBANG, trick.time(), this.lineTricks, Double.POSITIVE_INFINITY, this.lineStart));
        }
        Trick rubik = this.previous;
        Trick split = this.beforePrevious;
        if ((matty(trick.type()) || trick.type() == TrickType.FLIP) && rubik != null && split != null && rubik.type() == TrickType.RUBIKS_CUBE
                && (split.type() == TrickType.SPLIT_S || split.type() == TrickType.FUNKY_SPLIT)
                && rubik.start() - split.time() <= SHINOBI_GAP && trick.start() - rubik.time() <= SHINOBI_GAP) {
            out.add(new Trick(TrickType.SHINOBI, trick.time(), 1.0, Double.POSITIVE_INFINITY, split.start()));
        }
        Trick immelmann = this.previous;
        if (matty(trick.type()) && immelmann != null && immelmann.type() == TrickType.IMMELMANN && trick.start() - immelmann.time() <= IMMELMATT_GAP) {
            out.add(new Trick(TrickType.IMMELMATT, trick.time(), 1.0, Double.POSITIVE_INFINITY, immelmann.start()));
        }
        this.beforePrevious = this.previous;
        this.previous = trick;
    }

    static boolean matty(TrickType type) {
        return type == TrickType.MATTY_FLIP || type == TrickType.ANTI_MATTY || type == TrickType.POWER_MATTY;
    }

    static boolean sbangMove(TrickType type) {
        return switch (type) {
            case SPLIT_S, JUICY_FLICK, IMMELMANN, REWIND, FUNKY_SPLIT, INVERTED_YAW_SPIN, TRIPPY_SPIN, MATTY_FLIP, RUBIKS_CUBE, MCTWIST,
                 SNAP_ROLL, TIC_TOC, ANTI_MATTY, HALF_MATTY, POWER_MATTY, SPLIT_S_540, SPLITBACK, FLIP_REWIND, BARANI -> true;
            default -> false;
        };
    }
}
