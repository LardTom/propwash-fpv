package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.server.client.ClientPersonalRules;
import de.lardtom.propwash.zone.ZoneField;
import de.lardtom.propwash.zone.ZoneResolution;
import de.lardtom.propwash.zone.ZoneResolver;
import net.minecraft.world.level.Level;

public final class ZonePhysics {
    private ZonePhysics() {
    }

    public static ZoneResolution at(Level level, double x, double y, double z) {
        return ZoneResolver.resolve(ClientZones.INSTANCE.zones(), ClientZones.dimension(level), ClientPersonalRules.INSTANCE.base(), x, y, z);
    }

    public static boolean update(ZoneField field, Level level, double x, double y, double z, double seconds) {
        return field.update(ClientZones.INSTANCE.zones(), ClientZones.dimension(level), ClientPersonalRules.INSTANCE.base(), x, y, z, seconds);
    }
}
