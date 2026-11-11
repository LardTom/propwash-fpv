package de.lardtom.propwash.dev.example;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.BlockContact;
import de.lardtom.propwash.api.drone.CollisionFlags;
import de.lardtom.propwash.api.drone.CollisionQuery;
import de.lardtom.propwash.api.drone.CollisionRule;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

public final class ExampleGhost {
    public static final Identifier GHOST = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "ghost");
    public static final Identifier GHOST_SWITCH = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "ghost");
    public static final Identifier BOUNCY_STONE = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "bouncy_stone");
    public static final String PERMISSION_TAG = "propwash_example_ghost";
    public static final double STONE_RESTITUTION = 0.8;

    private static volatile boolean forced;
    private static volatile boolean bouncyStone;
    private static boolean initialized;

    private ExampleGhost() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashApi.drones().registerCollisionRule(GHOST, ExampleGhost::ghost);
        PropwashApi.drones().registerCollisionRule(BOUNCY_STONE, new CollisionRule() {
            @Override
            public CollisionFlags flags(CollisionQuery query) {
                return CollisionFlags.SOLID;
            }

            @Override
            public void onBlockContact(BlockContact contact) {
                if (bouncyStone && contact.state().is(Blocks.STONE)) {
                    contact.setRestitution(STONE_RESTITUTION);
                    contact.setImpactScale(0.0);
                }
            }
        });
    }

    public static void force(boolean on) {
        forced = on;
    }

    public static boolean forced() {
        return forced;
    }

    public static void bouncyStone(boolean on) {
        bouncyStone = on;
    }

    public static boolean allowed(Player player) {
        return player.entityTags().contains(PERMISSION_TAG);
    }

    static CollisionFlags ghost(CollisionQuery query) {
        if (query.autonomous() || !(forced || query.switchActive(GHOST_SWITCH))) {
            return CollisionFlags.SOLID;
        }
        if (query.side() == CollisionQuery.Side.SERVER && !query.pilot().map(ExampleGhost::allowed).orElse(false)) {
            return CollisionFlags.SOLID;
        }
        return CollisionFlags.GHOST;
    }
}
