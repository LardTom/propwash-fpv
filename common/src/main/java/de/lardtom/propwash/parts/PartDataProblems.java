package de.lardtom.propwash.parts;

import java.util.ArrayList;
import java.util.List;

public final class PartDataProblems {
    private final String source;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public PartDataProblems(String source) {
        this.source = source;
    }

    public String source() {
        return this.source;
    }

    public void error(String path, String message) {
        this.errors.add(path.isEmpty() ? message : path + ": " + message);
    }

    public void warning(String path, String message) {
        this.warnings.add(path.isEmpty() ? message : path + ": " + message);
    }

    public boolean hasErrors() {
        return !this.errors.isEmpty();
    }

    public List<String> errors() {
        return List.copyOf(this.errors);
    }

    public List<String> warnings() {
        return List.copyOf(this.warnings);
    }

    public List<String> errorLines() {
        List<String> out = new ArrayList<>();
        for (String error : this.errors) {
            out.add(this.source + " – " + error);
        }
        return out;
    }

    public List<String> warningLines() {
        List<String> out = new ArrayList<>();
        for (String warning : this.warnings) {
            out.add(this.source + " – " + warning);
        }
        return out;
    }
}
