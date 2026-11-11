package de.lardtom.propwash.input;

import de.lardtom.propwash.input.addon.AddonBindings;
import de.lardtom.propwash.input.addon.AxisAssignment;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DeviceProfile {
    public static final float MAX_DEADBAND = 0.3F;
    private static final String[] KEYS = keys();
    public static final Map<String, SwitchFunction> RENAMED = Map.of("race_restart", SwitchFunction.RESET);

    public String name = "";
    public boolean gamepadLayout = false;
    public ChannelMapping roll = new ChannelMapping(0, false);
    public ChannelMapping pitch = new ChannelMapping(1, false);
    public ChannelMapping yaw = new ChannelMapping(3, false);
    public ChannelMapping throttle = new ChannelMapping(2, false);
    public float deadband = 0.02F;
    public ThrottleMode throttleMode = ThrottleMode.FULL_RANGE;
    public float throttleRate = 0.8F;
    public Map<String, SwitchAssignment> switches = new LinkedHashMap<>();
    public Map<String, SwitchCommand> commands = new LinkedHashMap<>();
    public Map<String, AxisAssignment> addonAxes = new LinkedHashMap<>();
    public boolean setupDone = false;

    public DeviceProfile() {
    }

    public static String key(SwitchFunction function) {
        return KEYS[function.ordinal()];
    }

    private static String[] keys() {
        SwitchFunction[] functions = SwitchFunction.values();
        String[] keys = new String[functions.length];
        for (SwitchFunction function : functions) {
            keys[function.ordinal()] = function.name().toLowerCase(Locale.ROOT);
        }
        return keys;
    }

    private void migrateRenamed() {
        for (Map.Entry<String, SwitchFunction> renamed : RENAMED.entrySet()) {
            if (!this.switches.containsKey(renamed.getKey())) {
                continue;
            }
            Map<String, SwitchAssignment> copy = new LinkedHashMap<>(this.switches);
            SwitchAssignment old = copy.remove(renamed.getKey());
            if (old != null) {
                copy.putIfAbsent(key(renamed.getValue()), old);
            }
            this.switches = copy;
        }
    }

    public ChannelMapping channel(StickChannel channel) {
        return switch (channel) {
            case ROLL -> this.roll;
            case PITCH -> this.pitch;
            case YAW -> this.yaw;
            case THROTTLE -> this.throttle;
        };
    }

    public void setChannel(StickChannel channel, ChannelMapping mapping) {
        switch (channel) {
            case ROLL -> this.roll = mapping;
            case PITCH -> this.pitch = mapping;
            case YAW -> this.yaw = mapping;
            case THROTTLE -> this.throttle = mapping;
        }
    }

    public SwitchAssignment binding(SwitchFunction function) {
        SwitchAssignment binding = this.switches.get(key(function));
        return binding != null ? binding : SwitchAssignment.none();
    }

    public void setBinding(SwitchFunction function, SwitchAssignment binding) {
        if (binding == null || !binding.bound()) {
            this.switches.remove(key(function));
        } else {
            this.switches.put(key(function), binding);
        }
    }

    public SwitchAssignment addonBinding(Identifier id) {
        SwitchAssignment binding = id == null ? null : this.switches.get(id.toString());
        return binding != null ? binding : SwitchAssignment.none();
    }

    public void setAddonBinding(Identifier id, @Nullable SwitchAssignment binding) {
        if (binding == null || !binding.bound()) {
            this.switches.remove(id.toString());
        } else {
            this.switches.put(id.toString(), binding);
        }
    }

    public AxisAssignment addonAxis(Identifier id) {
        return AddonBindings.axisFor(id == null ? null : this.addonAxes.get(id.toString()));
    }

    public void setAddonAxis(Identifier id, @Nullable AxisAssignment assignment) {
        if (assignment == null) {
            this.addonAxes.remove(id.toString());
        } else {
            this.addonAxes.put(id.toString(), assignment);
        }
    }

    public void keepAddonsFrom(DeviceProfile other) {
        for (Map.Entry<String, SwitchAssignment> e : other.switches.entrySet()) {
            SwitchAssignment binding = e.getValue();
            if (AddonBindings.addonKey(e.getKey()) && binding != null && binding.bound() && this.stickConflict(binding) == null) {
                this.switches.put(e.getKey(), binding.copy());
            }
        }
        Map<String, AxisAssignment> axes = new LinkedHashMap<>();
        if (other.addonAxes != null) {
            for (Map.Entry<String, AxisAssignment> e : other.addonAxes.entrySet()) {
                AxisAssignment assignment = e.getValue() == null ? AxisAssignment.keys() : e.getValue().copy();
                if (assignment.onAxis() && this.stickOnAxis(assignment.index) != null) {
                    assignment = AxisAssignment.keys();
                }
                axes.put(e.getKey(), assignment);
            }
        }
        this.addonAxes = axes;
    }

    public SwitchCommand command(SwitchFunction function) {
        return SwitchCommands.get(this.commands, function);
    }

    public void setCommand(SwitchFunction function, SwitchCommand command) {
        SwitchCommands.put(this.commands, function, command);
    }

    public void keepCommandsFrom(DeviceProfile other) {
        for (SwitchFunction function : SwitchFunction.commands()) {
            SwitchAssignment binding = other.binding(function);
            this.setBinding(function, binding.bound() && this.stickConflict(binding) == null ? binding.copy() : null);
        }
        this.commands = SwitchCommands.copy(other.commands);
    }

    public boolean[] mappedAxes(int axisCount) {
        boolean[] mapped = new boolean[Math.max(0, axisCount)];
        for (StickChannel channel : StickChannel.values()) {
            ChannelMapping m = this.channel(channel);
            if (m.axis >= 0 && m.axis < mapped.length) {
                mapped[m.axis] = true;
            }
        }
        return mapped;
    }

    public int[] auxAxes(int axisCount) {
        boolean[] mapped = this.mappedAxes(axisCount);
        int count = 0;
        for (boolean m : mapped) {
            if (!m) {
                count++;
            }
        }
        int[] aux = new int[count];
        int n = 0;
        for (int i = 0; i < mapped.length; i++) {
            if (!mapped[i]) {
                aux[n++] = i;
            }
        }
        return aux;
    }

    public int auxNumber(int axis, int axisCount) {
        int[] aux = this.auxAxes(axisCount);
        for (int i = 0; i < aux.length; i++) {
            if (aux[i] == axis) {
                return i + 1;
            }
        }
        return 0;
    }

    public @Nullable StickChannel stickOnAxis(int axis) {
        if (axis < 0) {
            return null;
        }
        for (StickChannel channel : StickChannel.values()) {
            if (this.channel(channel).axis == axis) {
                return channel;
            }
        }
        return null;
    }

    public @Nullable StickChannel stickConflict(SwitchAssignment binding) {
        return binding.source == SwitchSource.AXIS ? this.stickOnAxis(binding.index) : null;
    }

    public int[] stickAxes() {
        StickChannel[] channels = StickChannel.values();
        int[] axes = new int[channels.length];
        for (int i = 0; i < channels.length; i++) {
            axes[i] = this.channel(channels[i]).axis;
        }
        return axes;
    }

    public List<SwitchFunction> assignAxis(StickChannel channel, int axis) {
        ChannelMapping target = this.channel(channel);
        int wanted = axis < 0 ? -1 : axis;
        if (wanted == target.axis) {
            return List.of();
        }
        int[] sticksBefore = this.stickAxes();
        StickChannel holder = this.stickOnAxis(wanted);
        ChannelMapping before = target.copy();
        if (holder != null) {
            ChannelMapping other = this.channel(holder);
            target.min = other.min;
            target.center = other.center;
            target.max = other.max;
            other.axis = before.axis;
            if (before.axis < 0) {
                other.resetCalibration();
            } else {
                other.min = before.min;
                other.center = before.center;
                other.max = before.max;
            }
        } else {
            target.resetCalibration();
        }
        target.axis = wanted;
        return this.moveSwitchesOffSticks(sticksBefore);
    }

    public List<SwitchFunction> moveSwitchesOffSticks(int[] sticksBefore) {
        int[] sticksAfter = this.stickAxes();
        List<Integer> taken = new ArrayList<>();
        for (int axis : sticksAfter) {
            if (axis >= 0 && !contains(sticksBefore, axis) && !taken.contains(axis)) {
                taken.add(axis);
            }
        }
        if (taken.isEmpty()) {
            return List.of();
        }
        List<Integer> freed = new ArrayList<>();
        for (int axis : sticksBefore) {
            if (axis >= 0 && !contains(sticksAfter, axis) && !freed.contains(axis)) {
                freed.add(axis);
            }
        }
        Map<Integer, Integer> targets = new LinkedHashMap<>();
        for (int i = 0; i < sticksAfter.length && i < sticksBefore.length; i++) {
            int now = sticksAfter[i];
            int was = sticksBefore[i];
            if (taken.contains(now) && freed.contains(was) && !targets.containsKey(now) && !targets.containsValue(was)) {
                targets.put(now, was);
            }
        }
        List<Integer> spare = new ArrayList<>(freed);
        spare.removeAll(targets.values());
        for (int axis : taken) {
            if (!targets.containsKey(axis)) {
                targets.put(axis, spare.isEmpty() ? -1 : spare.removeFirst());
            }
        }
        List<SwitchFunction> affected = new ArrayList<>();
        for (SwitchFunction function : SwitchFunction.values()) {
            SwitchAssignment binding = this.switches.get(key(function));
            if (binding == null || binding.source != SwitchSource.AXIS) {
                continue;
            }
            Integer to = targets.get(binding.index);
            if (to == null) {
                continue;
            }
            if (to >= 0) {
                binding.index = to;
            } else {
                this.switches.remove(key(function));
            }
            affected.add(function);
        }
        this.moveAddonsOffSticks(targets);
        return affected;
    }

    private void moveAddonsOffSticks(Map<Integer, Integer> targets) {
        for (String key : new ArrayList<>(this.switches.keySet())) {
            SwitchAssignment binding = this.switches.get(key);
            Integer to = AddonBindings.addonKey(key) && binding != null && binding.source == SwitchSource.AXIS ? targets.get(binding.index) : null;
            if (to == null) {
                continue;
            }
            if (to >= 0) {
                binding.index = to;
            } else {
                this.switches.remove(key);
            }
        }
        if (this.addonAxes == null) {
            return;
        }
        for (Map.Entry<String, AxisAssignment> e : this.addonAxes.entrySet()) {
            AxisAssignment assignment = e.getValue();
            Integer to = assignment != null && assignment.onAxis() ? targets.get(assignment.index) : null;
            if (to == null) {
                continue;
            }
            AxisAssignment moved = to >= 0 ? AxisAssignment.axis(to) : AxisAssignment.keys();
            moved.invert = assignment.invert;
            moved.deadband = assignment.deadband;
            e.setValue(moved);
        }
    }

    private static boolean contains(int[] values, int value) {
        for (int v : values) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    public DeviceProfile copy() {
        DeviceProfile copy = new DeviceProfile();
        copy.name = this.name;
        copy.setupDone = this.setupDone;
        copy.copyReceiverFrom(this);
        copy.copySwitchesFrom(this);
        return copy;
    }

    public boolean calibrated() {
        for (StickChannel channel : StickChannel.values()) {
            ChannelMapping mapping = this.channel(channel);
            if (mapping != null && mapping.assigned() && mapping.calibrated()) {
                return true;
            }
        }
        return false;
    }

    public void copyReceiverFrom(DeviceProfile other) {
        this.gamepadLayout = other.gamepadLayout;
        this.roll = other.roll.copy();
        this.pitch = other.pitch.copy();
        this.yaw = other.yaw.copy();
        this.throttle = other.throttle.copy();
        this.deadband = other.deadband;
        this.throttleMode = other.throttleMode;
        this.throttleRate = other.throttleRate;
    }

    public void copySwitchesFrom(DeviceProfile other) {
        Map<String, SwitchAssignment> copy = new LinkedHashMap<>();
        for (Map.Entry<String, SwitchAssignment> e : other.switches.entrySet()) {
            copy.put(e.getKey(), e.getValue().copy());
        }
        this.switches = copy;
        this.commands = SwitchCommands.copy(other.commands);
        Map<String, AxisAssignment> axes = new LinkedHashMap<>();
        if (other.addonAxes != null) {
            for (Map.Entry<String, AxisAssignment> e : other.addonAxes.entrySet()) {
                if (e.getValue() != null) {
                    axes.put(e.getKey(), e.getValue().copy());
                }
            }
        }
        this.addonAxes = axes;
    }

    public void sanitize() {
        if (this.name == null) {
            this.name = "";
        }
        for (StickChannel channel : StickChannel.values()) {
            if (this.channel(channel) == null) {
                this.setChannel(channel, ChannelMapping.unassigned());
            }
            this.channel(channel).sanitize();
        }
        if (!Float.isFinite(this.deadband)) {
            this.deadband = 0.02F;
        }
        this.deadband = ChannelMapping.clamp(this.deadband, 0.0F, MAX_DEADBAND);
        if (this.throttleMode == null) {
            this.throttleMode = ThrottleMode.FULL_RANGE;
        }
        if (!Float.isFinite(this.throttleRate)) {
            this.throttleRate = 0.8F;
        }
        this.throttleRate = ChannelMapping.clamp(this.throttleRate, 0.1F, 5.0F);
        Map<String, SwitchAssignment> clean = new LinkedHashMap<>();
        if (this.switches != null) {
            this.migrateRenamed();
            for (SwitchFunction function : SwitchFunction.values()) {
                SwitchAssignment b = this.switches.get(key(function));
                if (b != null) {
                    b.sanitize();
                    if (!function.supportsToggle()) {
                        b.toggle = false;
                    }
                    if (b.bound()) {
                        clean.put(key(function), b);
                    }
                }
            }
            AddonBindings.keepSwitches(this.switches, clean);
        }
        this.switches = clean;
        this.commands = SwitchCommands.sanitize(this.commands);
        this.addonAxes = AddonBindings.keepAxes(this.addonAxes);
    }
}
