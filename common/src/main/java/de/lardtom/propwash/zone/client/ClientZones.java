package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZonesS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class ClientZones {
    public static final ClientZones INSTANCE = new ClientZones();

    private List<Zone> zones = List.of();
    private String dimension = "";
    private boolean editable;
    private boolean known;
    private int version;

    private ClientZones() {
    }

    public static String dimension(Level level) {
        return level.dimension().identifier().toString();
    }

    public void accept(ZonesS2C payload) {
        boolean changed = !payload.zones().equals(this.zones) || !payload.dimension().equals(this.dimension) || payload.editable() != this.editable
                || !this.known;
        this.zones = payload.zones();
        this.dimension = payload.dimension();
        this.editable = payload.editable();
        this.known = true;
        if (changed) {
            this.version++;
        }
    }

    public void clear() {
        this.zones = List.of();
        this.dimension = "";
        this.editable = false;
        this.known = false;
        this.version++;
    }

    public List<Zone> zones() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !dimension(minecraft.level).equals(this.dimension)) {
            return List.of();
        }
        return this.zones;
    }

    public List<Zone> all() {
        return this.zones;
    }

    public String syncedDimension() {
        return this.dimension;
    }

    public boolean editable() {
        return this.known && this.editable;
    }

    public boolean known() {
        return this.known;
    }

    public int version() {
        return this.version;
    }

    public @Nullable Zone get(int id) {
        for (Zone zone : this.zones) {
            if (zone.id() == id) {
                return zone;
            }
        }
        return null;
    }

    public @Nullable String name(int id) {
        Zone zone = this.get(id);
        return zone == null ? null : zone.name();
    }
}
