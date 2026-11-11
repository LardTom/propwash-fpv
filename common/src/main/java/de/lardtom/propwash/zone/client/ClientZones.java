package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.server.rules.RuleAccess;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneMenu;
import de.lardtom.propwash.zone.ZoneRelease;
import de.lardtom.propwash.zone.ZoneRights;
import de.lardtom.propwash.zone.ZonesS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class ClientZones {
    public static final ClientZones INSTANCE = new ClientZones();

    private List<Zone> stored = List.of();
    private List<Zone> zones = List.of();
    private ZoneRelease release = ZoneRelease.DEFAULT;
    private String dimension = "";
    private boolean editable;
    private boolean creatable;
    private boolean known;
    private int version;

    private ClientZones() {
    }

    public static String dimension(Level level) {
        return level.dimension().identifier().toString();
    }

    public void accept(ZonesS2C payload) {
        boolean changed = !payload.zones().equals(this.stored) || !payload.dimension().equals(this.dimension) || payload.editable() != this.editable
                || payload.creatable() != this.creatable || !payload.release().equals(this.release) || !this.known;
        this.stored = payload.zones();
        this.release = payload.release();
        this.zones = this.release.effective(this.stored);
        this.dimension = payload.dimension();
        this.editable = payload.editable();
        this.creatable = payload.creatable();
        this.known = true;
        if (changed) {
            this.version++;
        }
    }

    public void clear() {
        this.stored = List.of();
        this.zones = List.of();
        this.release = ZoneRelease.DEFAULT;
        this.dimension = "";
        this.editable = false;
        this.creatable = false;
        this.known = false;
        this.version++;
    }

    private boolean here() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && dimension(minecraft.level).equals(this.dimension);
    }

    public List<Zone> zones() {
        return this.here() ? this.zones : List.of();
    }

    public List<Zone> stored() {
        return this.here() ? this.stored : List.of();
    }

    public List<Zone> all() {
        return this.zones;
    }

    public ZoneRelease release() {
        return this.release;
    }

    public String syncedDimension() {
        return this.dimension;
    }

    public boolean editable() {
        return this.known && this.editable;
    }

    public boolean creatable() {
        return this.known && this.creatable;
    }

    public boolean known() {
        return this.known;
    }

    public int version() {
        return this.version;
    }

    public static @Nullable UUID me() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null : minecraft.player.getUUID();
    }

    public boolean mayChange(Zone zone) {
        if (!this.known) {
            return false;
        }
        if (zone.id() == Zone.NEW) {
            return this.creatable;
        }
        return ZoneRights.mayChange(this.editable, this.creatable, zone, me());
    }

    public boolean mayDelete(Zone zone) {
        return this.known && zone.id() != Zone.NEW && ZoneRights.mayDelete(this.editable, this.creatable, zone, me());
    }

    public boolean listed(Zone zone) {
        return this.known && ZoneMenu.listed(this.editable, zone, me());
    }

    public List<Zone> listed() {
        return this.known ? ZoneMenu.listed(this.stored(), this.editable, me()) : List.of();
    }

    public List<ZoneMenu.Action> actions(Zone zone) {
        if (!this.known) {
            return List.of();
        }
        return ZoneMenu.actions(this.editable, this.creatable, mayJump(), zone, me());
    }

    public static boolean mayJump() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || minecraft.player == null || PilotSession.INSTANCE.isActive()) {
            return false;
        }
        try {
            return RuleAccess.runs(connection.getCommands().getRoot(), connection.getSuggestionsProvider(), ZoneMenu.TELEPORT);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public @Nullable Zone get(int id) {
        for (Zone zone : this.stored) {
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
