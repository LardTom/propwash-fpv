package de.lardtom.propwash.input.addon.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.addon.AccessoryActionHints;
import de.lardtom.propwash.input.client.FunctionKeys;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.InputLabels;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CovAccessoryHintTextTest {
    private static final String GUID = "03000000c0ffee00c0a1a1a100000000";
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath("covhinttext", "test"));
    private static final Identifier ACCESSORY = Identifier.fromNamespaceAndPath("covhinttext", "signal_light");
    private static final String ACTION_KEY = "covhinttext.accessory.signal_light.action.1";
    private static final int BUTTON = 3;
    private static final int MOUSE_4 = InputConstants.MOUSE_BUTTON_4;

    private Language previous;
    private final List<String> created = new ArrayList<>();

    @BeforeEach
    void remember() {
        this.previous = Language.getInstance();
        assertNull(FunctionKeys.mapping(SwitchFunction.ACCESSORY_1), "im JUnit-Lauf gibt es keine Tastenbelegung der Zubehör-Aktion 1");
        assertNull(TransmitterInput.INSTANCE.raw(), "im JUnit-Lauf ist keine Funke verbunden");
    }

    @AfterEach
    void restore() throws ReflectiveOperationException {
        Language.inject(this.previous);
        TransmitterInput.INSTANCE.simulate(null);
        InputConfig.get().profiles.remove(GUID);
        Field all = KeyMapping.class.getDeclaredField("ALL");
        all.setAccessible(true);
        Map<?, ?> mappings = (Map<?, ?>) all.get(null);
        for (String name : this.created) {
            mappings.remove(name);
        }
        this.created.clear();
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
        boolean german = language.equals("de_de");
        out.put("key.mouse", german ? "Maustaste %s" : "Button %s");
        out.put("key.mouse.4", german ? "Maustaste 4" : "Button 4");
        out.put(ACTION_KEY, german ? "Blinken" : "Blink");
        return out;
    }

    private Language language(Map<String, String> texts) {
        Language base = this.previous;
        return new Language() {
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
        };
    }

    private static String keyName(KeyMapping mapping) {
        String shown = mapping.getTranslatedKeyMessage().getString();
        assertFalse(shown.isBlank() || shown.startsWith("key."), "Name der Taste übersetzt: " + shown);
        return shown;
    }

    private KeyMapping key(String name, InputConstants.Type type, int code) {
        KeyMapping mapping = new KeyMapping(name, type, code, CATEGORY);
        this.created.add(name);
        return mapping;
    }

    private static SimulatedRadio radio(SwitchAssignment binding) {
        DeviceProfile profile = ProfileDefaults.radio("Cov-Funke", 8);
        profile.setBinding(SwitchFunction.ACCESSORY_1, binding);
        profile.setupDone = true;
        InputConfig.get().profiles.put(GUID, profile);
        SimulatedRadio radio = new SimulatedRadio(GUID, "Cov-Funke", profile, 8, 12).useSavedProfile();
        TransmitterInput.INSTANCE.simulate(radio);
        return radio;
    }

    private static List<Component> hint() {
        AccessoryActionHints.Named named = new AccessoryActionHints.Named(ACCESSORY, Map.of(1, ACTION_KEY));
        return AccessoryHintClient.message(null, named);
    }

    @Test
    void unassignedActionPointsToTheModesTabInBothLanguages() throws IOException {
        for (String code : List.of("en_us", "de_de")) {
            Map<String, String> texts = texts(code);
            Language.inject(this.language(texts));
            Component binding = AccessoryHintClient.binding(1);
            String expected = texts.get(AccessoryHintClient.UNBOUND_KEY).replace("%s", texts.get("propwash.input.key.unbound"));
            assertEquals(expected, binding.getString(), code);
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.YELLOW), binding.getStyle().getColor(), "unbelegt in Gelb");
            List<Component> lines = hint();
            assertEquals(2, lines.size(), "Titel und eine Zeile je benannter Aktion");
            String row = String.format(Locale.ROOT, texts.get(AccessoryHintClient.ACTION_KEY), "1", texts.get(ACTION_KEY), expected);
            assertEquals(row, lines.get(1).getString(), code);
            assertEquals(texts.get(AccessoryHintClient.TITLE_KEY).replace("%s", ACCESSORY.toString()), lines.get(0).getString());
        }
        Language.inject(this.language(texts("de_de")));
        assertTrue(AccessoryHintClient.binding(1).getString().startsWith("nicht belegt – im Tab „Modi“"), AccessoryHintClient.binding(1).getString());
        Language.inject(this.language(texts("en_us")));
        assertTrue(AccessoryHintClient.binding(1).getString().startsWith("not assigned – set it in the Modes tab"));
    }

    @Test
    void unassignedActionNamesTheConfiguratorKeyWhenItIsBound() throws IOException {
        KeyMapping configurator = this.key(KeyDefaults.CONFIGURATOR.mappingName(), InputConstants.Type.MOUSE, MOUSE_4);
        for (String code : List.of("en_us", "de_de")) {
            Map<String, String> texts = texts(code);
            Language.inject(this.language(texts));
            assertEquals(texts.get(AccessoryHintClient.UNBOUND_KEY).replace("%s", keyName(configurator)), AccessoryHintClient.binding(1).getString(),
                    code);
        }
    }

    @Test
    void keyBindingIsShownAsKey() throws IOException {
        KeyMapping action = this.key(KeyDefaults.accessory(SwitchFunction.ACCESSORY_1).mappingName(), InputConstants.Type.MOUSE, MOUSE_4);
        for (String code : List.of("en_us", "de_de")) {
            Map<String, String> texts = texts(code);
            Language.inject(this.language(texts));
            String expected = texts.get(AccessoryHintClient.KEY_KEY).replace("%s", keyName(action));
            Component binding = AccessoryHintClient.binding(1);
            assertEquals(expected, binding.getString(), code);
            assertFalse(binding.getString().contains(texts.get(AccessoryHintClient.UNBOUND_KEY).substring(0, 6)), "kein Hinweis „nicht belegt“");
        }
        Language.inject(this.language(texts("de_de")));
        assertEquals("Taste Maustaste 4", AccessoryHintClient.binding(1).getString());
    }

    @Test
    void radioBindingNamesTheSwitchAndBothWhenTheKeyIsBoundToo() throws IOException {
        SwitchAssignment button = SwitchAssignment.button(BUTTON, false);
        SimulatedRadio radio = radio(button);
        for (String code : List.of("en_us", "de_de")) {
            Map<String, String> texts = texts(code);
            Language.inject(this.language(texts));
            DeviceProfile profile = InputConfig.get().profile(GUID);
            String device = InputLabels.bindingFor(profile.binding(SwitchFunction.ACCESSORY_1), profile, radio.raw()).getString();
            assertFalse(device.isBlank());
            assertEquals(device, AccessoryHintClient.binding(1).getString(), code + ": nur die Funke");
        }
        KeyMapping action = this.key(KeyDefaults.accessory(SwitchFunction.ACCESSORY_1).mappingName(), InputConstants.Type.MOUSE, MOUSE_4);
        for (String code : List.of("en_us", "de_de")) {
            Map<String, String> texts = texts(code);
            Language.inject(this.language(texts));
            DeviceProfile profile = InputConfig.get().profile(GUID);
            String device = InputLabels.bindingFor(profile.binding(SwitchFunction.ACCESSORY_1), profile, radio.raw()).getString();
            String key = texts.get(AccessoryHintClient.KEY_KEY).replace("%s", keyName(action));
            String or = texts.get(AccessoryHintClient.OR_KEY);
            String expected = String.format(Locale.ROOT, or, device, key);
            assertEquals(expected, AccessoryHintClient.binding(1).getString(), code + ": Funke oder Taste");
        }
        InputConfig.get().profiles.get(GUID).setBinding(SwitchFunction.ACCESSORY_1, SwitchAssignment.none());
        Language.inject(this.language(texts("de_de")));
        assertEquals("Taste " + keyName(action), AccessoryHintClient.binding(1).getString(), "ohne Belegung an der Funke nur die Taste");
    }
}
