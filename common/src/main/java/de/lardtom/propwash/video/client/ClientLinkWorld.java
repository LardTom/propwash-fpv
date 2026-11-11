package de.lardtom.propwash.video.client;

import de.lardtom.propwash.video.link.ObstructionTracer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class ClientLinkWorld {
    private final ClientBlockLoss blocks = new ClientBlockLoss();

    public ObstructionTracer.World bind(@Nullable Level level) {
        this.blocks.bind(level);
        return this.blocks;
    }
}
