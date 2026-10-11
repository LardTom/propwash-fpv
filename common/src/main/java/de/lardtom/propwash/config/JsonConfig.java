package de.lardtom.propwash.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.Services;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class JsonConfig<T> {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    private final String name;
    private final Class<T> type;
    private final Supplier<T> defaults;
    private final Consumer<T> validator;
    private T value;
    private boolean loaded;

    private JsonConfig(String name, Class<T> type, Supplier<T> defaults, Consumer<T> validator) {
        if (!name.matches("[a-z0-9_\\-]+")) {
            throw new IllegalArgumentException("Ungültiger Config-Name: " + name);
        }
        this.name = name;
        this.type = type;
        this.defaults = defaults;
        this.validator = validator;
        this.value = defaults.get();
    }

    public static <T> JsonConfig<T> create(String name, Class<T> type, Supplier<T> defaults) {
        return new JsonConfig<>(name, type, defaults, v -> { });
    }

    public static <T> JsonConfig<T> create(String name, Class<T> type, Supplier<T> defaults, Consumer<T> validator) {
        return new JsonConfig<>(name, type, defaults, validator);
    }

    public Path path() {
        return Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID).resolve(this.name + ".json");
    }

    public T get() {
        return this.value;
    }

    public boolean isLoaded() {
        return this.loaded;
    }

    public T load() {
        Path path = this.path();
        T result = null;
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                result = GSON.fromJson(reader, this.type);
            } catch (JsonParseException | IOException e) {
                Constants.LOG.error("Config {} ist beschädigt, verwende Standardwerte: {}", path, e.toString());
                this.moveBroken(path);
            }
        }
        if (result == null) {
            result = Objects.requireNonNull(this.defaults.get(), "defaults");
        }
        this.validator.accept(result);
        this.value = result;
        this.loaded = true;
        this.save();
        return result;
    }

    public void set(T newValue) {
        this.validator.accept(newValue);
        this.value = newValue;
        this.save();
    }

    public void save() {
        Path path = this.path();
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this.value, this.type, writer);
                writer.write('\n');
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Constants.LOG.error("Config {} konnte nicht gespeichert werden", path, e);
        }
    }

    private void moveBroken(Path path) {
        try {
            Files.move(path, path.resolveSibling(path.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Constants.LOG.error("Beschädigte Config {} konnte nicht verschoben werden", path, e);
        }
    }

    @Override
    public String toString() {
        return "JsonConfig[" + this.name + "]";
    }
}
