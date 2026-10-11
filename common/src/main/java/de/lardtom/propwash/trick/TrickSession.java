package de.lardtom.propwash.trick;

import java.util.ArrayList;
import java.util.List;

public final class TrickSession {
    private final TrickDetector detector = new TrickDetector();
    private final TrickScore score = new TrickScore();
    private final List<Trick> found = new ArrayList<>();
    private final List<Trick> history = new ArrayList<>();
    private int historyLimit;

    public TrickSession() {
    }

    public void update(TrickSample sample) {
        this.found.clear();
        this.detector.update(sample, this.found);
        if (this.historyLimit > 0) {
            for (Trick trick : this.found) {
                if (this.history.size() >= this.historyLimit) {
                    this.history.removeFirst();
                }
                this.history.add(trick);
            }
        }
        this.score.update(sample, this.detector.upY(), this.detector.tumbleRate(), this.detector.contact(), this.found);
    }

    public void reset() {
        this.detector.reset();
        this.score.reset();
        this.found.clear();
        this.history.clear();
    }

    public void keepHistory(int limit) {
        this.historyLimit = Math.max(0, limit);
        while (this.history.size() > this.historyLimit) {
            this.history.removeFirst();
        }
    }

    public List<Trick> history() {
        return this.history;
    }

    public TrickDetector detector() {
        return this.detector;
    }

    public TrickScore score() {
        return this.score;
    }
}
