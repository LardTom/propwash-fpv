package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.audio.DroneSoundStage;
import de.lardtom.propwash.audio.DroneSoundStage.Phase;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

public final class DroneSoundCheck {
    public static final double SPLIT = DroneSoundStage.FAR_BLOCKS / 2.0;

    private DroneSoundCheck() {
    }

    public record Counts(Phase phase, int sounds, int entitySounds, int events, int explosions, int excluded) {
        public boolean exact() {
            return this.sounds == 1 && this.entitySounds == 1 && this.events == 1 && this.explosions == 1 && this.excluded == 0;
        }

        public int got() {
            return Math.min(1, this.sounds) + Math.min(1, this.entitySounds) + Math.min(1, this.events) + Math.min(1, this.explosions);
        }

        public String line() {
            return this.phase.name().toLowerCase(Locale.ROOT) + "=" + this.got() + "/4 (Geräusch " + this.sounds + ", an der Drohne " + this.entitySounds
                    + ", Werfer-Event " + this.events + ", Explosion " + this.explosions + ", ausgenommen " + this.excluded + ")";
        }
    }

    public static Counts count(List<SoundPacketLog.Entry> entries, Vec3 body, Phase phase) {
        String mark = DroneSoundStage.MARK.location().toString();
        String event = Integer.toString(DroneSoundStage.DISPENSER_EVENT);
        int sounds = 0;
        int entitySounds = 0;
        int events = 0;
        int explosions = 0;
        int excluded = 0;
        for (SoundPacketLog.Entry entry : entries) {
            switch (entry.kind()) {
                case SOUND -> {
                    if (entry.key().equals(mark) && entry.pitch() == phase.soundPitch) {
                        sounds++;
                    } else if (entry.key().equals(mark) && entry.pitch() == phase.excludedPitch) {
                        excluded++;
                    }
                }
                case ENTITY_SOUND -> {
                    if (entry.key().equals(mark) && entry.pitch() == phase.entityPitch) {
                        entitySounds++;
                    }
                }
                case LEVEL_EVENT -> {
                    if (entry.key().equals(event) && inPhase(entry.at(), body, phase)) {
                        events++;
                    }
                }
                case EXPLOSION -> {
                    if (inPhase(entry.at(), body, phase)) {
                        explosions++;
                    }
                }
            }
        }
        return new Counts(phase, sounds, entitySounds, events, explosions, excluded);
    }

    private static boolean inPhase(Vec3 at, Vec3 body, Phase phase) {
        double distance = Math.hypot(at.x - body.x, at.z - body.z);
        return phase == Phase.FAR ? distance >= SPLIT : distance < SPLIT;
    }
}
