package com.example.nightnotifier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads and persists the client-only night notification configuration. */
public final class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("nightnotifier");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_MESSAGE = "🌙 Minecraft에서 밤이 시작되었습니다.";

    private final Path configPath;
    private Config config;

    public ConfigManager() {
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve("nightnotifier.json");
    }

    public void load() {
        try {
            Files.createDirectories(configPath.getParent());

            if (Files.notExists(configPath)) {
                config = new Config();
                save();
                return;
            }

            config = GSON.fromJson(Files.readString(configPath, StandardCharsets.UTF_8), Config.class);
            if (config == null) {
                throw new JsonParseException("Configuration is empty");
            }
            config.normalize();
        } catch (IOException | JsonParseException exception) {
            LOGGER.error("Failed to load config; using defaults", exception);
            config = new Config();
        }
    }

    public Config getConfig() {
        if (config == null) {
            config = new Config();
        }
        return config;
    }

    private void save() throws IOException {
        Files.writeString(
                configPath,
                GSON.toJson(config),
                StandardCharsets.UTF_8
        );
    }

    public static final class Config {
        public boolean enableDiscord = true;
        public String discordWebhook = "";

        public boolean enableNtfy = false;
        public String ntfyTopic = "";

        public int nightTime = 13000;
        public int resetTime = 1000;
        public String message = DEFAULT_MESSAGE;

        private void normalize() {
            if (discordWebhook == null) {
                discordWebhook = "";
            }
            if (ntfyTopic == null) {
                ntfyTopic = "";
            }
            if (message == null || message.isBlank()) {
                message = DEFAULT_MESSAGE;
            }
            nightTime = Math.max(0, Math.min(23999, nightTime));
            resetTime = Math.max(0, Math.min(23999, resetTime));
        }
    }
}
