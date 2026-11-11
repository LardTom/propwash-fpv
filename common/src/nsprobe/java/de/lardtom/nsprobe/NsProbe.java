package de.lardtom.nsprobe;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NsProbe {
    public static final String MOD_ID = "nsprobe";
    public static final Logger LOG = LoggerFactory.getLogger("Namespace Probe");
    public static final Identifier ITEM_ID = Identifier.fromNamespaceAndPath("propwash", "nsprobe_item");
    public static final String KEY = "key.propwash.nsprobe";
    public static final String COMMAND = "nsprobe";

    private NsProbe() {
    }

    public static Item item() {
        return new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ITEM_ID)));
    }

    public static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("propwash").then(Commands.literal(COMMAND).executes(ctx -> {
            ctx.getSource().sendSystemMessage(Component.literal("nsprobe"));
            return 1;
        })));
    }

    public static void ping(NsProbePing payload) {
        LOG.info("nsprobe ping {}", payload.value());
    }
}
