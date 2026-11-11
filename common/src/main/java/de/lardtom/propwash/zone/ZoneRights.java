package de.lardtom.propwash.zone;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class ZoneRights {
    private ZoneRights() {
    }

    public static boolean mayCreate(boolean admin, boolean playersCreate) {
        return admin || playersCreate;
    }

    public static boolean mayChange(boolean admin, boolean playersCreate, Zone zone, @Nullable UUID player) {
        return admin || playersCreate && zone.ownedBy(player);
    }

    public static boolean mayDelete(boolean admin, boolean playersCreate, Zone zone, @Nullable UUID player) {
        return mayChange(admin, playersCreate, zone, player);
    }

    public static boolean maySend(boolean admin, boolean playersCreate, ZoneEditC2S.Action action, Zone sent, @Nullable Zone stored,
                                  @Nullable UUID player) {
        if (stored == null) {
            return sent.id() == Zone.NEW && action == ZoneEditC2S.Action.SAVE ? mayCreate(admin, playersCreate) : admin || playersCreate;
        }
        return action == ZoneEditC2S.Action.DELETE ? mayDelete(admin, playersCreate, stored, player) : mayChange(admin, playersCreate, stored, player);
    }

    public static @Nullable ZoneOwner ownerOfNew(boolean admin, Zone sent, ZoneOwner creator) {
        return admin && sent.owner() == null ? null : creator;
    }

    public static @Nullable ZoneOwner ownerOfChanged(Zone stored, ZoneOwner editor) {
        ZoneOwner owner = stored.owner();
        return owner != null && owner.id().equals(editor.id()) ? editor : owner;
    }
}
