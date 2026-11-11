package de.lardtom.propwash.share;

import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.creative.CreativeSpec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class ShareParsed {
    public final List<ShareLayer> layers = new ArrayList<>();
    public final List<String> skipped = new ArrayList<>();
    public final EnumMap<AccessorySlot, Identifier> accessories = new EnumMap<>(AccessorySlot.class);
    public Identifier[] parts;
    public PaintSpec paint;
    public TuneSpec tune;
    public ShareOsd osd;
    public String name;
    public CreativeSpec creative;

    public void skipOnce(String marker) {
        if (!this.skipped.contains(marker)) {
            this.skipped.add(marker);
        }
    }
}
