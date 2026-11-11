package de.lardtom.propwash.share;

import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.osd.OsdLayoutCodec;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

public final class ShareService {
    private ShareService() {
    }

    public static ShareDecoded decode(String text) {
        return ShareFormat.decode(text, ShareTables.current());
    }

    public static String encode(DroneData data, Collection<ShareLayer> layers, boolean addonInfo, Optional<String> osdJson) {
        return encode(data, Optional.empty(), layers, addonInfo, osdJson);
    }

    public static String encode(DroneData data, Optional<CreativeSpec> creative, Collection<ShareLayer> layers, boolean addonInfo,
                                Optional<String> osdJson) {
        Objects.requireNonNull(data, "data");
        EnumSet<ShareLayer> chosen = EnumSet.of(ShareLayer.PARTS);
        chosen.addAll(Objects.requireNonNull(layers, "layers"));
        Optional<ShareOsd> osd = Objects.requireNonNullElse(osdJson, Optional.<String>empty()).filter(json -> !json.isBlank())
                .map(json -> new ShareOsd(OsdLayoutCodec.VERSION, json));
        if (chosen.contains(ShareLayer.OSD) && osd.isEmpty()) {
            chosen.remove(ShareLayer.OSD);
        }
        ShareContent content = ShareContent.of(data, chosen, osd)
                .withCreative(chosen.contains(ShareLayer.CREATIVE) ? Objects.requireNonNullElse(creative, Optional.<CreativeSpec>empty()) : Optional.empty());
        Pw3Codec.Options options = addonInfo ? Pw3Codec.Options.DEFAULT : Pw3Codec.Options.NO_INFO;
        return Pw3Codec.encode(content, ShareTables.current(), options, Pw3Carry.EMPTY);
    }

    public static ShareConvert.Conversion convert(String legacyCode) {
        return ShareConvert.toPw3(legacyCode);
    }
}
