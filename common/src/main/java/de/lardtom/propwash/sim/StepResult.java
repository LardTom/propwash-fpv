package de.lardtom.propwash.sim;

import org.joml.Quaterniond;

public final class StepResult {
    public double dx;
    public double dy;
    public double dz;
    public final Quaterniond orientation = new Quaterniond();
    public int substeps;
    public boolean armed;
    public boolean sleeping;

    public StepResult() {
    }
}
