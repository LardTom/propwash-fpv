package de.lardtom.propwash.race.client;

import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.RaceLine;
import net.minecraft.util.Mth;

public final class RaceConfig {
    public static final int MAX_LAPS = 99;
    public static final JsonConfig<RaceConfig> CONFIG = JsonConfig.create("race", RaceConfig.class, RaceConfig::new, c -> {
        c.laps = Mth.clamp(c.laps, 0, MAX_LAPS);
        c.countdownSeconds = Mth.clamp(c.countdownSeconds, 1, 10);
        c.raceLineGates = RaceLine.clampAhead(c.raceLineGates);
        c.gateRotateStep = GatePlacement.validStep(c.gateRotateStep);
    });

    public int laps = 3;
    public boolean countdown = false;
    public int countdownSeconds = 3;
    public boolean startHint = true;
    public boolean showMessages = true;
    public boolean reportResults = true;
    public boolean ghost = true;
    public boolean recordGhost = true;
    public boolean startLights = true;
    public boolean raceLine = true;
    public int raceLineGates = RaceLine.DEFAULT_AHEAD;
    public float gateRotateStep = GatePlacement.QUARTER;

    public RaceConfig copy() {
        RaceConfig copy = new RaceConfig();
        copy.laps = this.laps;
        copy.countdown = this.countdown;
        copy.countdownSeconds = this.countdownSeconds;
        copy.startHint = this.startHint;
        copy.showMessages = this.showMessages;
        copy.reportResults = this.reportResults;
        copy.ghost = this.ghost;
        copy.recordGhost = this.recordGhost;
        copy.startLights = this.startLights;
        copy.raceLine = this.raceLine;
        copy.raceLineGates = this.raceLineGates;
        copy.gateRotateStep = this.gateRotateStep;
        return copy;
    }
}
