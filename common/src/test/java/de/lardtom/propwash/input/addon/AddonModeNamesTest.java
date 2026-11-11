package de.lardtom.propwash.input.addon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.StabilizationMode;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class AddonModeNamesTest {
    private static final String N = "modenamestest.mode.n";
    private static final String C = "modenamestest.mode.c";
    private static final String S = "modenamestest.mode.s";
    private static final String ATTI = "modenamestest.mode.atti";

    private Language previous;

    @BeforeEach
    void remember() {
        this.previous = Language.getInstance();
    }

    @AfterEach
    void restore() {
        Language.inject(this.previous);
    }

    private static final class CameraDroneAssist implements FlightAssist {
        boolean gps = true;

        @Override
        public void update(FlightState state, AssistOutput output) {
        }

        @Override
        public Optional<String> modeNameKey(StabilizationMode mode) {
            return Optional.of(switch (mode) {
                case ANGLE -> this.gps ? N : ATTI;
                case HORIZON -> C;
                case ACRO -> S;
            });
        }
    }

    private static Map<String, String> texts(String language) throws IOException {
        Map<String, String> out = new HashMap<>();
        Path root = Stream.of(Path.of("common/src/lang"), Path.of("src/lang")).filter(Files::isDirectory).findFirst().orElse(null);
        if (root == null) {
            fail("Sprachquellen nicht gefunden");
        }
        try (Stream<Path> folders = Files.list(root)) {
            for (Path folder : folders.sorted().toList()) {
                Path file = folder.resolve(language + ".json");
                if (!Files.exists(file)) {
                    continue;
                }
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                        out.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        }
        out.put(N, "N");
        out.put(C, "C");
        out.put(S, "S");
        out.put(ATTI, "ATTI");
        return out;
    }

    private void use(String language) throws IOException {
        Map<String, String> texts = texts(language);
        Language base = this.previous;
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                return texts.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(String key) {
                return texts.containsKey(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return base.getVisualOrder(text);
            }
        });
    }

    @Test
    void popupOfACameraDroneNamesItsOwnModes() throws IOException {
        CameraDroneAssist assist = new CameraDroneAssist();
        this.use("en_us");
        assertEquals("Mode: C", AddonModeNames.changed(assist, StabMode.HORIZON, null).getString());
        assertEquals("Mode: C", AddonModeNames.changed(assist, StabMode.HORIZON, StabMode.HORIZON).getString());
        assertEquals("Mode: C (key – radio switch: S)", AddonModeNames.changed(assist, StabMode.HORIZON, StabMode.ACRO).getString());
        assertEquals("Mode: S (key – radio switch: N)", AddonModeNames.changed(assist, StabMode.ACRO, StabMode.ANGLE).getString());
        assist.gps = false;
        assertEquals("Mode: ATTI (key – radio switch: C)", AddonModeNames.changed(assist, StabMode.ANGLE, StabMode.HORIZON).getString());
        this.use("de_de");
        assist.gps = true;
        assertEquals("Modus: N (Taste – Funkenschalter: S)", AddonModeNames.changed(assist, StabMode.ANGLE, StabMode.ACRO).getString());
    }

    @Test
    void modesTabHintNamesTheModeOfTheFlightAssist() throws IOException {
        CameraDroneAssist assist = new CameraDroneAssist();
        this.use("en_us");
        assertEquals("C chosen by key – flipping a mode switch on the radio takes over again",
                Component.translatable("propwash.input.modes.key_override", AddonModeNames.name(assist, StabMode.HORIZON)).getString());
        this.use("de_de");
        assertEquals("S per Taste gewählt – legst du einen Modusschalter um, übernimmt die Funke wieder",
                Component.translatable("propwash.input.modes.key_override", AddonModeNames.name(assist, StabMode.ACRO)).getString());
    }

    @Test
    void withoutNamesFromTheFlightAssistPropwashNamesStay() throws IOException {
        this.use("en_us");
        FlightAssist plain = (state, output) -> {
        };
        for (StabilizationMode mode : StabilizationMode.values()) {
            assertEquals(Optional.empty(), plain.modeNameKey(mode), mode.name());
        }
        FlightAssist nothing = new FlightAssist() {
            @Override
            public void update(FlightState state, AssistOutput output) {
            }

            @Override
            public Optional<String> modeNameKey(StabilizationMode mode) {
                return null;
            }
        };
        FlightAssist blank = new FlightAssist() {
            @Override
            public void update(FlightState state, AssistOutput output) {
            }

            @Override
            public Optional<String> modeNameKey(StabilizationMode mode) {
                return Optional.of("  ");
            }
        };
        FlightAssist broken = new FlightAssist() {
            @Override
            public void update(FlightState state, AssistOutput output) {
            }

            @Override
            public Optional<String> modeNameKey(StabilizationMode mode) {
                throw new IllegalStateException("kaputt");
            }
        };
        for (FlightAssist assist : Arrays.asList(null, plain, nothing, blank, broken)) {
            assertEquals("Mode: Horizon (key – radio switch: Acro)", AddonModeNames.changed(assist, StabMode.HORIZON, StabMode.ACRO).getString());
            assertEquals("Mode: Angle", AddonModeNames.changed(assist, StabMode.ANGLE, null).getString());
        }
    }

    @Test
    void modesWithoutANameKeepThePropwashName() throws IOException {
        this.use("en_us");
        FlightAssist cineOnly = new FlightAssist() {
            @Override
            public void update(FlightState state, AssistOutput output) {
            }

            @Override
            public Optional<String> modeNameKey(StabilizationMode mode) {
                return mode == StabilizationMode.HORIZON ? Optional.of(" " + C + " ") : Optional.empty();
            }
        };
        assertEquals("Mode: C (key – radio switch: Acro)", AddonModeNames.changed(cineOnly, StabMode.HORIZON, StabMode.ACRO).getString());
        assertEquals(List.of(C, "propwash.drone.mode.angle"), List.of(key(AddonModeNames.name(cineOnly, StabMode.HORIZON)),
                key(AddonModeNames.name(cineOnly, StabMode.ANGLE))));
    }

    @Test
    void namesMatchTheModesOfTheApi() {
        assertEquals(StabMode.values().length, StabilizationMode.values().length);
        for (StabMode mode : StabMode.values()) {
            assertEquals(mode.name(), AddonModeNames.apiMode(mode).name());
            assertTrue(AddonModeNames.propwashKey(mode).startsWith("propwash.drone.mode."), mode.name());
        }
    }

    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }
}
