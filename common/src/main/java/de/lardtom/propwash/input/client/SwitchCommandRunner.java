package de.lardtom.propwash.input.client;

import com.mojang.brigadier.ParseResults;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.CommandTrigger;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.KeyRepeatFilter;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchCommand;
import de.lardtom.propwash.input.SwitchCommands;
import de.lardtom.propwash.input.SwitchEvaluator;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class SwitchCommandRunner {
    public static final SwitchCommandRunner INSTANCE = new SwitchCommandRunner();
    public static final long TOAST_MILLIS = 2500L;
    private static final SwitchCommand EMPTY = new SwitchCommand();

    private final CommandTrigger trigger = new CommandTrigger();
    private final KeyRepeatFilter[] keys = new KeyRepeatFilter[CommandTrigger.SLOTS];
    private final Object[] toastTokens = new Object[CommandTrigger.SLOTS];
    private RadioSource source = RadioSource.NONE;
    private String sourceGuid = "";
    private int sent;
    private String lastSent = "";
    private CommandTrigger.@Nullable Outcome lastOutcome;
    private boolean failed;

    private SwitchCommandRunner() {
        for (int i = 0; i < CommandTrigger.SLOTS; i++) {
            this.keys[i] = new KeyRepeatFilter();
            this.toastTokens[i] = new Object();
        }
    }

    public int sent() {
        return this.sent;
    }

    public String lastSent() {
        return this.lastSent;
    }

    public CommandTrigger.@Nullable Outcome lastOutcome() {
        return this.lastOutcome;
    }

    public boolean primed() {
        return this.trigger.primed();
    }

    public void reset() {
        this.trigger.reset();
        this.source = RadioSource.NONE;
        this.sourceGuid = "";
    }

    public void onFrame(Minecraft minecraft, Map<String, SwitchCommand> commands) {
        try {
            this.update(minecraft, commands);
        } catch (RuntimeException e) {
            if (!this.failed) {
                this.failed = true;
                Constants.LOG.error("Propwash: eigene Befehle konnten nicht ausgewertet werden", e);
            }
            this.trigger.reset();
        }
    }

    public static CommandTrigger.Gate gate(Minecraft minecraft) {
        if (!PilotSession.INSTANCE.isActive() || minecraft.player == null || minecraft.getConnection() == null) {
            return CommandTrigger.Gate.OFF;
        }
        return minecraft.gui == null || minecraft.gui.screen() != null ? CommandTrigger.Gate.BLOCKED : CommandTrigger.Gate.OPEN;
    }

    private void update(Minecraft minecraft, Map<String, SwitchCommand> commands) {
        RadioState radio = RadioState.INSTANCE;
        if (radio.source() != this.source || !radio.deviceGuid().equals(this.sourceGuid)) {
            this.source = radio.source();
            this.sourceGuid = radio.deviceGuid();
            this.trigger.reset();
        }
        CommandTrigger.Gate gate = gate(minecraft);
        int active = radio.activeSwitches();
        int readable = radio.readableSwitches();
        int states = 0;
        int readableSlots = 0;
        int withOn = 0;
        int withOff = 0;
        for (int i = 0; i < CommandTrigger.SLOTS; i++) {
            SwitchFunction function = SwitchFunction.command(i);
            SwitchCommand command = commands.get(DeviceProfile.key(function));
            if (command == null) {
                command = EMPTY;
            }
            if (SwitchEvaluator.isSet(active, function)) {
                states |= CommandTrigger.bit(i);
            }
            if (SwitchEvaluator.isSet(readable, function)) {
                readableSlots |= CommandTrigger.bit(i);
            }
            if (command.hasOn()) {
                withOn |= CommandTrigger.bit(i);
            }
            if (command.hasOff()) {
                withOff |= CommandTrigger.bit(i);
            }
        }
        long now = Util.getMillis();
        for (CommandTrigger.Event event : this.trigger.update(states, readableSlots, withOn, withOff, gate, now)) {
            this.handle(minecraft, event, commands);
        }
        List<KeyMapping> mappings = InputKeys.COMMANDS;
        for (int i = 0; i < mappings.size() && i < CommandTrigger.SLOTS; i++) {
            KeyMapping key = mappings.get(i);
            int clicks = 0;
            while (key.consumeClick()) {
                clicks++;
            }
            if (!this.keys[i].pressed(clicks, key.isDown())) {
                continue;
            }
            SwitchCommand command = SwitchCommands.get(commands, SwitchFunction.command(i));
            CommandTrigger.Event event = this.trigger.keyPress(i, command.hasOn(), command.hasOff(), gate, now);
            if (event != null) {
                this.handle(minecraft, event, commands);
            }
        }
    }

    private void handle(Minecraft minecraft, CommandTrigger.Event event, Map<String, SwitchCommand> commands) {
        this.lastOutcome = event.outcome();
        int number = event.slot() + 1;
        String text = SwitchCommands.get(commands, SwitchFunction.command(event.slot())).text(event.on());
        switch (event.outcome()) {
            case RUN -> this.run(minecraft, event.slot(), text);
            case BLOCKED -> this.toast(minecraft, event.slot(), Component.translatable("propwash.input.command.toast.skipped", number),
                    Component.translatable("propwash.input.command.toast.screen_open"), Palette.WARNING);
            case RATE_LIMITED -> this.toast(minecraft, event.slot(), Component.translatable("propwash.input.command.toast.skipped", number),
                    Component.translatable("propwash.input.command.toast.rate_limited"), Palette.WARNING);
            case EMPTY -> this.toast(minecraft, event.slot(), Component.translatable("propwash.input.command.toast.empty", number),
                    Component.translatable("propwash.input.command.toast.empty.hint"), Palette.INFO);
        }
    }

    private void run(Minecraft minecraft, int slot, String text) {
        int number = slot + 1;
        String normalized = SwitchCommands.normalize(text);
        ClientPacketListener connection = minecraft.getConnection();
        if (normalized.isEmpty() || SwitchCommands.problem(normalized) != SwitchCommands.Problem.NONE || connection == null) {
            this.toast(minecraft, slot, Component.translatable("propwash.input.command.toast.invalid", number),
                    Component.translatable("propwash.input.command.toast.invalid.hint", SwitchCommands.MAX_LENGTH), Palette.ERROR);
            return;
        }
        String body = SwitchCommands.body(normalized);
        boolean known = known(connection, body);
        connection.sendCommand(body);
        this.sent++;
        this.lastSent = normalized;
        Constants.LOG.debug("Propwash: eigener Befehl {} gesendet", number);
        if (known) {
            this.toast(minecraft, slot, Component.translatable("propwash.input.command.toast.run", number), Component.literal(normalized), Palette.INFO);
        } else {
            this.toast(minecraft, slot, Component.translatable("propwash.input.command.toast.unknown", number),
                    Component.translatable("propwash.input.command.toast.unknown.hint", normalized), Palette.WARNING);
        }
    }

    public static boolean known(ClientPacketListener connection, String body) {
        try {
            ParseResults<ClientSuggestionProvider> parse = connection.getCommands().parse(body, connection.getSuggestionsProvider());
            return !parse.getReader().canRead() && parse.getExceptions().isEmpty() && parse.getContext().getLastChild().getCommand() != null;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private void toast(Minecraft minecraft, int slot, Component title, Component message, int color) {
        if (minecraft.gui == null) {
            return;
        }
        ThemedToast.show(minecraft.gui.toastManager(), this.toastTokens[slot], title, message, color, TOAST_MILLIS);
    }
}
