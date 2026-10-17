package de.lardtom.propwash.video;

import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.api.part.VideoLink;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.sim.EnergyModel;
import de.lardtom.propwash.video.vtx.VtxPowerSettings;
import de.lardtom.propwash.video.vtx.VtxTable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VtxTables {
    private static final Map<VideoType, VtxTable> CACHE = new ConcurrentHashMap<>();

    private VtxTables() {
    }

    public static VtxTable of(VideoType video) {
        return CACHE.computeIfAbsent(video, VtxTables::create);
    }

    private static VtxTable create(VideoType video) {
        VideoDefinition d = video.definition();
        return VtxTable.of(d.link() == VideoLink.DIGITAL, d.vtxPowerMw(), d.vtxLevelsMw(), EnergyModel.videoSupplyW(video), video.massGrams());
    }

    public static int configuredMw(Map<String, Float> tune, VideoType video) {
        VtxTable table = of(video);
        VtxPowerSettings.Selection selection = VtxPowerSettings.selection(tune, table);
        return table.levelMw(selection.index());
    }

    public static double configuredGainDb(Map<String, Float> tune, VideoType video) {
        VtxTable table = of(video);
        VtxPowerSettings.Selection selection = VtxPowerSettings.selection(tune, table);
        return selection.auto() ? 0.0 : table.gainDb(selection.index());
    }
}
