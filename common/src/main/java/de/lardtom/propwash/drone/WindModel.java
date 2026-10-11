package de.lardtom.propwash.drone;

import de.lardtom.propwash.sim.SimEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class WindModel {
    private static final double CALM_SPEED = 1.2;
    private static final double RAIN_SPEED = 3.5;
    private static final double THUNDER_SPEED = 5.0;
    private static final double REFERENCE_HEIGHT = 10.0;
    private static final double PROFILE_EXPONENT = 0.14;
    private static final double SHELTER_FACTOR = 0.25;

    private WindModel() {
    }

    public static void fill(Level level, double x, double y, double z, double groundDistance, SimEnvironment env) {
        if (level.dimension() != Level.OVERWORLD) {
            env.windX = 0.0;
            env.windY = 0.0;
            env.windZ = 0.0;
            env.gustIntensity = 0.08;
            return;
        }
        float rain = level.getRainLevel(1.0F);
        float thunder = level.getThunderLevel(1.0F);
        double reference = CALM_SPEED + RAIN_SPEED * rain + THUNDER_SPEED * thunder;
        double height = Double.isFinite(groundDistance) ? groundDistance : Math.max(REFERENCE_HEIGHT, y - level.getSeaLevel());
        double profile = Math.pow(Math.max(0.5, Math.min(60.0, height)) / REFERENCE_HEIGHT, PROFILE_EXPONENT);
        double speed = reference * profile;
        BlockPos pos = BlockPos.containing(x, y, z);
        if (level.isLoaded(pos) && !level.canSeeSky(pos)) {
            speed *= SHELTER_FACTOR;
        }
        double time = level.getGameTime();
        double angle = time / 24000.0 * Math.PI * 1.4 + 0.35 * Math.sin(time / 3100.0);
        env.windX = Math.cos(angle) * speed;
        env.windY = 0.0;
        env.windZ = Math.sin(angle) * speed;
        env.gustIntensity = Math.max(0.0, Math.min(1.0, 0.12 + 0.3 * rain + 0.45 * thunder));
    }
}
