package de.lardtom.propwash.rx;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public enum RxAccess {
    UNBOUND,
    OWNER,
    OPERATOR,
    FOREIGN;

    public static RxAccess of(@Nullable UUID owner, UUID player, boolean operator) {
        if (owner == null) {
            return UNBOUND;
        }
        if (owner.equals(player)) {
            return OWNER;
        }
        return operator ? OPERATOR : FOREIGN;
    }

    public boolean mayChange() {
        return this == OWNER || this == OPERATOR;
    }

    public boolean mayBind() {
        return this != FOREIGN;
    }

    public boolean opensWindow() {
        return this != FOREIGN;
    }
}
