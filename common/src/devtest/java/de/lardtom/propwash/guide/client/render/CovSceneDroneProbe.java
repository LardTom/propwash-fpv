package de.lardtom.propwash.guide.client.render;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.client.render.DroneRenderState;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.ScenePos;

import java.lang.reflect.Field;

public final class CovSceneDroneProbe {
    private CovSceneDroneProbe() {
    }

    public static DroneRenderState state(DroneBuild build) throws ReflectiveOperationException {
        SceneDroneVisual visual = new SceneDroneVisual();
        ActorState actor = new ActorState("cov_drone", new ActorSpec.Drone(build, true, 1.0F), new ScenePos(0.0F, 1.0F, 0.0F), 0.0F);
        visual.prepare(build, 1.0F, actor, 0.0F);
        Field field = SceneDroneVisual.class.getDeclaredField("state");
        field.setAccessible(true);
        return (DroneRenderState) field.get(visual);
    }
}
