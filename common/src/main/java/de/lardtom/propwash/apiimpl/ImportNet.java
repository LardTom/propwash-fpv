package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.ImportTarget;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import net.minecraft.server.level.ServerPlayer;

public final class ImportNet {
    public static final ImportRateLimit RATE = new ImportRateLimit();

    private ImportNet() {
    }

    static void onImport(ImportPayloads.ImportC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        ImportResult result = handle(player, payload, context.server().getTickCount());
        PropwashNet.sendToPlayer(player, ImportPayloads.ImportResultS2C.of(payload.requestId(), result));
    }

    public static ImportResult handle(ServerPlayer player, ImportPayloads.ImportC2S payload, long tick) {
        boolean allowed = RATE.tryAcquire(player.getUUID(), tick);
        ImportRequest request;
        try {
            request = payload.request().toRequest();
        } catch (IllegalArgumentException e) {
            return ImportResult.of(allowed ? ImportStatus.INVALID : ImportStatus.BUSY);
        }
        if (!allowed) {
            return BuildAccessImpl.imported(player, request, ImportResult.of(ImportStatus.BUSY));
        }
        if (request.target() == ImportTarget.WORKBENCH && !workbenchOpen(player, payload.containerId())) {
            return BuildAccessImpl.imported(player, request, ImportResult.of(ImportStatus.NO_TARGET));
        }
        return BuildAccessImpl.INSTANCE.applyImport(player, request);
    }

    public static boolean workbenchOpen(ServerPlayer player, int containerId) {
        return player.containerMenu instanceof DroneWorkbenchMenu menu && menu.containerId == containerId && menu.stillValid(player);
    }
}
