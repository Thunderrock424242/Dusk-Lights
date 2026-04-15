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
        public double sunsetRampMinutes = 20.0D / 1200.0D;
        public int sunsetMinimumBrightness = 0;

        public int sunriseStartTick = 23000;
        public double sunriseRampMinutes = 1.0;

        public boolean autoCompatDiscovery = true;
        public boolean defaultSensorEnabled = true;
        public List<String> manualCompatBlockIds = new ArrayList<>();

        public boolean dynamicTorchesEnabled = true;
        public double torchRainBrightnessMultiplier = 0.65D;
        public double torchStormFlickerChance = 0.35D;
        public int torchWarmupSeconds = 5;
        public int torchUnderwaterSputterSeconds = 2;

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

            torchRainBrightnessMultiplier = clamp(torchRainBrightnessMultiplier, 0.1D, 1.0D);
            torchStormFlickerChance = clamp(torchStormFlickerChance, 0.0D, 1.0D);
            torchWarmupSeconds = clamp(torchWarmupSeconds, 0, 30);
            torchUnderwaterSputterSeconds = clamp(torchUnderwaterSputterSeconds, 0, 30);
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
