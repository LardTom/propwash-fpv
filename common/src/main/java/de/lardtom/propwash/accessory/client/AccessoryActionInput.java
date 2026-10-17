package de.lardtom.propwash.accessory.client;

import de.lardtom.propwash.accessory.net.AccessoryActionC2S;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class AccessoryActionInput {
    public static final AccessoryActionInput INSTANCE = new AccessoryActionInput();
    private static final KeyMapping[] KEYS = new KeyMapping[AccessoryActionC2S.ACTIONS];

    static {
        for (int i = 0; i < KEYS.length; i++) {
            KeyDefaults.Entry entry = KeyDefaults.ACCESSORY_ACTIONS.get(i);
            KEYS[i] = ClientRegistrar.keyMapping(entry.name(), entry.key());
        }
    }

    private int bits;
    private int sent;
    private int droneId = -1;
    private int sentCount;
    private @Nullable Integer testBits;

    private AccessoryActionInput() {
    }

    static void bootstrap() {
    }

    public int bits() {
        return this.bits;
    }

    public boolean held(int number) {
        return number >= 1 && number <= AccessoryActionC2S.ACTIONS && (this.bits & (1 << (number - 1))) != 0;
    }

    public int sentCount() {
        return this.sentCount;
    }

    public void setTestBits(@Nullable Integer bits) {
        this.testBits = bits;
    }

    void tick(Minecraft minecraft) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.isActive() ? session.drone() : null;
        if (drone == null) {
            this.bits = 0;
            this.sent = 0;
            this.droneId = -1;
            return;
        }
        if (drone.getId() != this.droneId) {
            this.droneId = drone.getId();
            this.sent = 0;
        }
        int now = 0;
        Integer test = this.testBits;
        if (test != null) {
            now = test & 0xF;
        } else if (minecraft.gui.screen() == null) {
            RadioState radio = RadioState.INSTANCE;
            for (int i = 0; i < KEYS.length; i++) {
                if (KEYS[i].isDown() || radio.isActive(SwitchFunction.accessory(i + 1))) {
                    now |= 1 << i;
                }
            }
        }
        int changed = now ^ this.sent;
        for (int i = 0; i < KEYS.length; i++) {
            if ((changed & (1 << i)) != 0) {
                boolean pressed = (now & (1 << i)) != 0;
                if (ClientNet.sendToServer(new AccessoryActionC2S(drone.getId(), drone.resetEpoch(), i + 1, pressed))) {
                    this.sentCount++;
                }
            }
        }
        this.sent = now;
        this.bits = now;
    }

    void reset() {
        this.bits = 0;
        this.sent = 0;
        this.droneId = -1;
        this.testBits = null;
    }
}
