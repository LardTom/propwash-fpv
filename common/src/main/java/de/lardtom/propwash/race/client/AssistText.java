package de.lardtom.propwash.race.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.rescue.client.RescueState;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.video.client.VideoConfig;
import de.lardtom.propwash.video.client.VideoSettings;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public final class AssistText {
    private AssistText() {
    }

    public static int evaluate(PilotSession session, QuadcopterEntity drone) {
        VideoState video = VideoState.INSTANCE;
        VideoConfig config = VideoSettings.get();
        LapAssist.Flight flight = new LapAssist.Flight(session.holdMode() != HoldMode.OFF, RescueState.INSTANCE.active(),
                ClientWorldRules.INSTANCE.physics().modified(), drone.build().isCreative(), !drone.build().buildable(), video.creative(),
                !config.creativeLatencyFromModule, config.creativeLatencyMs, drone.build().video().latencyMs,
                RadioLink.freeRange(Minecraft.getInstance().player), video.creative(), video.relayHops());
        return LapAssist.of(flight);
    }

    public static Component reasons(int mask) {
        MutableComponent out = Component.empty();
        List<LapAssist> list = LapAssist.list(mask);
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                out.append(Component.translatable("propwash.race.assist.separator"));
            }
            out.append(reason(list.get(i)));
        }
        return out;
    }

    public static Component storedReasons(int mask) {
        MutableComponent out = Component.empty();
        List<LapAssist> list = LapAssist.list(mask);
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                out.append(Component.translatable("propwash.race.assist.separator"));
            }
            out.append(Component.translatable(list.get(i).translationKey()));
        }
        return out;
    }

    public static Component reason(LapAssist assist) {
        return switch (assist) {
            case WORLD_PHYSICS -> {
                List<RuleSpec> changed = PhysicsRules.changed(ClientWorldRules.INSTANCE.values());
                if (changed.isEmpty()) {
                    yield Component.translatable(assist.translationKey());
                }
                MutableComponent names = Component.empty();
                for (int i = 0; i < changed.size(); i++) {
                    if (i > 0) {
                        names.append(", ");
                    }
                    names.append(Component.translatable(changed.get(i).translationKey()));
                }
                yield Component.translatable(assist.translationKey() + ".rules", names);
            }
            case GOGGLES_LATENCY -> {
                VideoConfig config = VideoSettings.get();
                VideoState video = VideoState.INSTANCE;
                int transmitter = video.camera() == null ? 0 : video.camera().latencyMs;
                yield Component.translatable(assist.translationKey() + ".ms", config.creativeLatencyMs, transmitter);
            }
            default -> Component.translatable(assist.translationKey());
        };
    }
}
