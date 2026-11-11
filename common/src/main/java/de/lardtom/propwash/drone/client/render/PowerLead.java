package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.DroneBuild;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.joml.Vector3f;

public final class PowerLead {
    public static final PowerLead NONE = new PowerLead(DroneAssembly.Part.EMPTY, PlugMask.NONE, PlugMask.NONE);

    private final DroneAssembly.Part stack;
    private final PlugMask batteryPlug;
    private final PlugMask stackCables;

    private PowerLead(DroneAssembly.Part stack, PlugMask batteryPlug, PlugMask stackCables) {
        this.stack = stack;
        this.batteryPlug = batteryPlug;
        this.stackCables = stackCables;
    }

    static PowerLead of(DroneRenderResources res, DroneBuild build, DroneAssembly.Part stack, DroneAssembly.Part battery) {
        if (stack.item() == null || battery.item() == null || battery.model() == null) {
            return NONE;
        }
        PartModel mounted = PartModel.stackMounted(build.stack());
        PlugMask cables = res.mountedCables(mounted);
        PlugMask plug = res.batteryPlug(battery.model());
        if (cables.isEmpty() || plug.isEmpty()) {
            return NONE;
        }
        ItemStackRenderState item = res.item(mounted);
        if (item == null) {
            return NONE;
        }
        return new PowerLead(new DroneAssembly.Part(item, Shape.EMPTY, Shape.EMPTY, new Vector3f(stack.position()), mounted), plug, cables);
    }

    public boolean present() {
        return !this.batteryPlug.isEmpty();
    }

    public DroneAssembly.Part stack() {
        return this.stack;
    }

    public PlugMask batteryPlug() {
        return this.batteryPlug;
    }

    public PlugMask stackCables() {
        return this.stackCables;
    }

    public static final class Slot {
        public final DroneRenderState.PartSlot stack = new DroneRenderState.PartSlot();
        public PlugMask batteryPlug = PlugMask.NONE;
        public PlugMask stackCables = PlugMask.NONE;
        public boolean present;

        public void set(PowerLead lead) {
            this.present = lead.present();
            this.batteryPlug = lead.batteryPlug();
            this.stackCables = lead.stackCables();
            this.stack.set(lead.stack());
        }

        public boolean shown(DroneRenderState state) {
            return this.present && !state.batteryMissing;
        }
    }
}
