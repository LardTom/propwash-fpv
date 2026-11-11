package de.lardtom.propwash.video;

import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdLayoutCodec;
import de.lardtom.propwash.ui.SpeedUnit;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

public record PilotLook(String layout, SpeedUnit unit, StickMode stickMode) {
    private static final SpeedUnit[] UNITS = SpeedUnit.values();
    private static final StickMode[] STICK_MODES = StickMode.values();
    public static final StreamCodec<ByteBuf, PilotLook> CODEC = StreamCodec.of(PilotLook::write, PilotLook::read);

    public PilotLook {
        Objects.requireNonNull(layout, "layout");
        if (layout.length() > OsdLayoutCodec.MAX_LENGTH) {
            throw new IllegalArgumentException("OSD-Layout des Piloten zu groß: " + layout.length() + " Zeichen");
        }
        unit = unit == null ? SpeedUnit.KMH : unit;
        stickMode = StickMode.orDefault(stickMode);
    }

    public static PilotLook of(OsdConfig config, SpeedUnit unit, StickMode stickMode) {
        return new PilotLook(OsdLayoutCodec.encode(config), unit, stickMode);
    }

    public OsdConfig config(OsdConfig own) {
        return OsdLayoutCodec.decode(this.layout, own);
    }

    private static void write(ByteBuf buf, PilotLook look) {
        ByteBufCodecs.stringUtf8(OsdLayoutCodec.MAX_LENGTH).encode(buf, look.layout);
        buf.writeByte(look.unit.ordinal());
        buf.writeByte(look.stickMode.ordinal());
    }

    private static PilotLook read(ByteBuf buf) {
        String layout = ByteBufCodecs.stringUtf8(OsdLayoutCodec.MAX_LENGTH).decode(buf);
        int unit = buf.readUnsignedByte();
        int mode = buf.readUnsignedByte();
        return new PilotLook(layout, unit < UNITS.length ? UNITS[unit] : SpeedUnit.KMH, mode < STICK_MODES.length ? STICK_MODES[mode] : StickMode.DEFAULT);
    }
}
