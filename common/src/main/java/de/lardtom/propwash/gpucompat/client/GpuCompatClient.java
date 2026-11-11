package de.lardtom.propwash.gpucompat.client;

import de.lardtom.propwash.gpucompat.GpuCompatDecision;
import de.lardtom.propwash.gpucompat.GpuCompatMode;
import de.lardtom.propwash.gpucompat.GpuPaths;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public final class GpuCompatClient {
    public static final String SECTION = "propwash.gpucompat.section";
    public static final String SETTING = "propwash.gpucompat.setting";
    public static final List<GpuCompatMode> CHOICES = List.of(GpuCompatMode.values());

    private GpuCompatClient() {
    }

    public static boolean restartNeeded(GpuCompatMode chosen) {
        GpuCompatDecision decision = GpuCompatHooks.status().decision();
        if (decision.reason() == GpuCompatDecision.Reason.NOT_OPENGL) {
            return false;
        }
        if (decision.source() == GpuCompatDecision.Source.OVERRIDE) {
            return false;
        }
        GpuCompatDecision wanted = GpuCompatDecision.decide(chosen, decision.source(), decision.windows(), decision.vendor(), decision.renderer(),
                decision.simulated());
        return wanted.active() != decision.active();
    }

    public static Component state(GpuCompatMode chosen) {
        GpuCompatDecision decision = GpuCompatHooks.status().decision();
        Component reason = Component.translatable(decision.reason().key());
        MutableComponent text = Component.translatable(decision.active() ? "propwash.gpucompat.state.on" : "propwash.gpucompat.state.off", reason);
        if (decision.source() == GpuCompatDecision.Source.OVERRIDE) {
            text.append(" ").append(Component.translatable("propwash.gpucompat.state.override", GpuCompatDecision.MODE_ENV));
        }
        if (restartNeeded(chosen)) {
            text.append(" ").append(Component.translatable("propwash.gpucompat.state.restart"));
        }
        GpuPaths.Health health = GpuPathCheck.health();
        if (health.tested() && health.anyBroken()) {
            text.append(" ").append(Component.translatable(health.readPath() == GpuPaths.ReadPath.NONE ? "propwash.gpucompat.state.paths_none"
                    : "propwash.gpucompat.state.paths_broken"));
        }
        return text;
    }
}
