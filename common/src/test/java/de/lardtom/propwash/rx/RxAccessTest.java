package de.lardtom.propwash.rx;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RxAccessTest {
    private static final UUID OWNER = new UUID(1L, 2L);
    private static final UUID OTHER = new UUID(3L, 4L);

    @Test
    void anyoneBindsAnUnboundBlock() {
        assertEquals(RxAccess.UNBOUND, RxAccess.of(null, OTHER, false));
        assertTrue(RxAccess.UNBOUND.mayBind());
        assertFalse(RxAccess.UNBOUND.mayChange());
        assertTrue(RxAccess.UNBOUND.opensWindow());
    }

    @Test
    void onlyOwnerAndOperatorsChangeABoundBlock() {
        assertEquals(RxAccess.OWNER, RxAccess.of(OWNER, OWNER, false));
        assertEquals(RxAccess.OWNER, RxAccess.of(OWNER, OWNER, true));
        assertEquals(RxAccess.OPERATOR, RxAccess.of(OWNER, OTHER, true));
        assertEquals(RxAccess.FOREIGN, RxAccess.of(OWNER, OTHER, false));
        assertTrue(RxAccess.OWNER.mayChange());
        assertTrue(RxAccess.OPERATOR.mayChange());
        assertFalse(RxAccess.FOREIGN.mayChange());
        assertFalse(RxAccess.FOREIGN.mayBind());
        assertFalse(RxAccess.FOREIGN.opensWindow());
    }
}
