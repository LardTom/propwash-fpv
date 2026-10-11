package de.lardtom.propwash.core.event;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class ServerBus {
    public static final Event<ServerListener> TICK_END = new Event<>("server_tick_end");

    public static final Event<PlayerListener> PLAYER_JOIN = new Event<>("player_join");

    public static final Event<PlayerListener> PLAYER_LEAVE = new Event<>("player_leave");

    public static final Event<ServerListener> STARTED = new Event<>("server_started");

    public static final Event<ServerListener> STOPPED = new Event<>("server_stopped");

    public static final Event<CommandRegistration> REGISTER_COMMANDS = new Event<>("register_commands");

    public static final Event<LevelListener> LEVEL_UNLOAD = new Event<>("level_unload");

    private ServerBus() {
    }

    @FunctionalInterface
    public interface ServerListener {
        void accept(MinecraftServer server);
    }

    @FunctionalInterface
    public interface PlayerListener {
        void accept(ServerPlayer player);
    }

    @FunctionalInterface
    public interface LevelListener {
        void accept(ServerLevel level);
    }

    @FunctionalInterface
    public interface CommandRegistration {
        void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection);
    }
}
