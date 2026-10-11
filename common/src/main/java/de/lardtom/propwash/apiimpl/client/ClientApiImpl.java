package de.lardtom.propwash.apiimpl.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.ImportTarget;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.client.ClientTune;
import de.lardtom.propwash.api.client.OsdLayoutAccess;
import de.lardtom.propwash.api.client.OsdLayoutSpec;
import de.lardtom.propwash.apiimpl.BuildAccessImpl;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.apiimpl.ImportPayloads;
import de.lardtom.propwash.apiimpl.ImportWire;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartAvailability;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.client.render.DronePreview;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.drone.client.TuneAccess;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdLayoutCodec;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ClientApiImpl {
    public static final PendingImports PENDING = new PendingImports();

    public static final OsdLayoutAccess OSD = new OsdLayoutAccess() {
        @Override
        public OsdLayoutSpec current() {
            return new OsdLayoutSpec(OsdLayoutCodec.VERSION, OsdLayoutCodec.encode(OsdClient.CONFIG.get()));
        }

        @Override
        public OsdLayoutSpec defaults() {
            return new OsdLayoutSpec(OsdLayoutCodec.VERSION, OsdLayoutCodec.encode(OsdConfig.defaults()));
        }

        @Override
        public boolean apply(OsdLayoutSpec layout) {
            if (layout == null) {
                return false;
            }
            try {
                OsdClient.CONFIG.set(OsdLayoutCodec.decode(layout.json(), OsdClient.CONFIG.get()));
                return true;
            } catch (IllegalArgumentException e) {
                Constants.LOG.debug("OSD-Layout eines Addons abgelehnt: {}", e.getMessage());
                return false;
            }
        }

        @Override
        public boolean validate(OsdLayoutSpec layout) {
            return validOsd(layout);
        }
    };

    public static final ClientTune TUNE = new ClientTune() {
        @Override
        public boolean available() {
            return TuneAccess.INSTANCE.available();
        }

        @Override
        public TuneSpec current() {
            return Conversions.tune(TuneAccess.INSTANCE.current());
        }

        @Override
        public TuneSpec defaults() {
            return Conversions.tune(TuneAccess.INSTANCE.defaults());
        }

        @Override
        public boolean apply(TuneSpec tune) {
            return tune != null && TuneAccess.INSTANCE.apply(Conversions.tune(tune));
        }
    };

    private ClientApiImpl() {
    }

    public static Optional<DroneData> heldDrone() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return Optional.empty();
        }
        return BuildAccessImpl.INSTANCE.read(player.getMainHandItem());
    }

    public static boolean validOsd(OsdLayoutSpec layout) {
        if (layout == null) {
            return false;
        }
        try {
            OsdLayoutCodec.decode(layout.json(), OsdConfig.defaults());
            return true;
        } catch (IllegalArgumentException e) {
            Constants.LOG.debug("OSD-Layout eines Addons ungültig: {}", e.getMessage());
            return false;
        }
    }

    public static CompletableFuture<ImportResult> sendImport(ImportRequest request) {
        Objects.requireNonNull(request, "request");
        int containerId = request.target() == ImportTarget.WORKBENCH ? openWorkbench() : ImportPayloads.ImportC2S.NO_CONTAINER;
        return send(containerId, request);
    }

    public static CompletableFuture<ImportResult> sendWorkbenchImport(int containerId, ImportRequest request) {
        Objects.requireNonNull(request, "request");
        return send(containerId, request);
    }

    public static void renderPreview(GuiGraphicsExtractor graphics, DroneData drone, int x0, int y0, int x1, int y1, float yawDegrees) {
        if (graphics == null || drone == null) {
            return;
        }
        Optional<DroneBuild> build = Conversions.build(drone.build());
        if (build.isEmpty()) {
            return;
        }
        DronePreview.render(graphics, build.get(), PartWear.PRISTINE, Conversions.paint(drone.paint()), x0, y0, x1, y1, yawDegrees);
    }

    public static void renderPreview(GuiGraphicsExtractor graphics, DroneData drone, int x0, int y0, int x1, int y1, float yawDegrees,
                                     float pixelsPerMillimetre) {
        if (graphics == null || drone == null || !(pixelsPerMillimetre > 0.0F) || !Float.isFinite(pixelsPerMillimetre)) {
            return;
        }
        Optional<DroneBuild> build = Conversions.build(drone.build());
        if (build.isEmpty()) {
            return;
        }
        DronePreview.renderAtScale(graphics, build.get(), PartWear.PRISTINE, Conversions.paint(drone.paint()), x0, y0, x1, y1, yawDegrees,
                pixelsPerMillimetre);
    }

    public static boolean openConfigTab(String tabId, @Nullable Screen parent) {
        return ConfiguratorScreen.openTab(parent, tabId);
    }

    public static int openWorkbench() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof DroneWorkbenchMenu menu) {
            return menu.containerId;
        }
        return ImportPayloads.ImportC2S.NO_CONTAINER;
    }

    static void onResult(ImportPayloads.ImportResultS2C payload) {
        PENDING.complete(payload.requestId(), payload.result());
    }

    private static CompletableFuture<ImportResult> send(int containerId, ImportRequest request) {
        List<Identifier> inactive = request.data().build().allParts().stream().filter(id -> !PartAvailability.active(id)).distinct().toList();
        if (!inactive.isEmpty()) {
            Constants.LOG.info("Propwash: Import nicht an den Server geschickt, Teile auf diesem Server inaktiv: {}", inactive);
            return CompletableFuture.completedFuture(new ImportResult(ImportStatus.UNKNOWN_PARTS, Set.of(), inactive, Map.of()));
        }
        ImportWire wire = ImportWire.of(request);
        int bytes = wire.encodedBytes();
        if (bytes > ImportWire.MAX_ENCODED_BYTES) {
            Constants.LOG.debug("Import-Anfrage zu groß ({} Bytes), nicht gesendet", bytes);
            return CompletableFuture.completedFuture(ImportResult.of(ImportStatus.INVALID));
        }
        PendingImports.Ticket ticket = PENDING.open();
        if (!ClientNet.sendToServer(new ImportPayloads.ImportC2S(ticket.id(), containerId, wire))) {
            PENDING.complete(ticket.id(), ImportResult.of(ImportStatus.NO_TARGET));
        }
        return ticket.future();
    }
}
