package de.lardtom.propwash.share.client;

import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.apiimpl.ImportWire;
import de.lardtom.propwash.apiimpl.client.ClientApiImpl;
import de.lardtom.propwash.apiimpl.client.PendingImports;
import de.lardtom.propwash.build.PartAvailability;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.share.ShareContent;
import de.lardtom.propwash.share.ShareCreativeC2S;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ShareCreativeClient {
    private ShareCreativeClient() {
    }

    public static boolean available() {
        return ClientNet.canSend(ShareCreativeC2S.TYPE);
    }

    public static CompletableFuture<ImportResult> createDrone(ShareContent content) {
        DroneData data = content.toDroneData();
        if (content.creative().isEmpty() || !available()) {
            return PropwashClientApi.sendCreateDrone(data);
        }
        List<Identifier> inactive = data.build().allParts().stream().filter(id -> !PartAvailability.active(id)).distinct().toList();
        if (!inactive.isEmpty()) {
            return CompletableFuture.completedFuture(new ImportResult(ImportStatus.UNKNOWN_PARTS, Set.of(), inactive, Map.of()));
        }
        ImportWire wire = ImportWire.of(ImportRequest.createDrone(data));
        if (wire.encodedBytes() > ImportWire.MAX_ENCODED_BYTES) {
            return CompletableFuture.completedFuture(ImportResult.of(ImportStatus.INVALID));
        }
        PendingImports.Ticket ticket = ClientApiImpl.PENDING.open();
        if (!ClientNet.sendToServer(new ShareCreativeC2S(ticket.id(), wire, content.creative().get()))) {
            ClientApiImpl.PENDING.complete(ticket.id(), ImportResult.of(ImportStatus.NO_TARGET));
        }
        return ticket.future();
    }
}
