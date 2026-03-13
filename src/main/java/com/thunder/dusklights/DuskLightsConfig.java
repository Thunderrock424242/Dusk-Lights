package com.thunder.dusklights;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class DuskLightsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Paths.get("config", DuskLights.MOD_ID + ".json");

    private static Values cached;

    private DuskLightsConfig() {
    }

    public static Values get() {
        if (cached == null) {
            cached = loadOrCreate();
        }
        return cached;
    }

    private static Values loadOrCreate() {
        Values defaults = new Values();

        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            if (!Files.exists(CONFIG_PATH)) {
                save(defaults);
                return defaults;
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                Values loaded = GSON.fromJson(reader, Values.class);
                if (loaded == null) {
                    save(defaults);
                    return defaults;
                }
                loaded.sanitize();
                save(loaded);
                return loaded;
            }
        } catch (IOException | JsonSyntaxException ex) {
            DuskLights.LOGGER.warn("Failed to read config {}, using defaults", CONFIG_PATH, ex);
            return defaults;
        }
    }

    private static void save(Values values) {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(values, writer);
        } catch (IOException ex) {
            DuskLights.LOGGER.warn("Failed to write config {}", CONFIG_PATH, ex);
        }
    }

    public static final class Values {
        public int sunsetStartTick = 12000;
        public double sunsetRampMinutes = 1.0;
        public int sunsetMinimumBrightness = 1;

        public int sunriseStartTick = 23000;
        public double sunriseRampMinutes = 1.0;

        public boolean autoCompatDiscovery = true;
        public List<String> manualCompatBlockIds = new ArrayList<>();

        private void sanitize() {
            sunsetStartTick = clamp(sunsetStartTick, 0, 23999);
            sunriseStartTick = clamp(sunriseStartTick, 0, 23999);

            sunsetRampMinutes = clamp(sunsetRampMinutes, 0.05, 30.0);
            sunriseRampMinutes = clamp(sunriseRampMinutes, 0.05, 30.0);

            sunsetMinimumBrightness = clamp(sunsetMinimumBrightness, 0, 14);

            if (manualCompatBlockIds == null) {
                manualCompatBlockIds = new ArrayList<>();
            } else {
                manualCompatBlockIds.removeIf(value -> value == null || value.isBlank());
            }
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
