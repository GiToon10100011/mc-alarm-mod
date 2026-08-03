package com.example.cobblemonitor;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Loads, migrates, and persists Cobble Monitor's client configuration. */
public final class ConfigManager {
    public static final String LOGGER_NAME = "cobble-monitor";
    private static final int MAX_CACHED_PLAYER_NAMES = 2048;
    private static final Logger LOGGER = LoggerFactory.getLogger(LOGGER_NAME);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_DAY_MESSAGE = "☀️ Minecraft에서 낮이 시작되었습니다.";
    private static final String DEFAULT_NIGHT_MESSAGE = "🌙 Minecraft에서 밤이 시작되었습니다.";

    private final Path configPath;
    private final Path legacyConfigPath;
    private Config config;

    public ConfigManager() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.configPath = configDir.resolve("cobble-monitor.json");
        this.legacyConfigPath = configDir.resolve("nightnotifier.json");
    }

    /** Loads the new configuration or migrates the original nightnotifier file. */
    public void load() {
        try {
            Files.createDirectories(configPath.getParent());

            if (Files.notExists(configPath)) {
                if (Files.exists(legacyConfigPath)) {
                    config = migrateLegacyConfig(
                            GSON.fromJson(Files.readString(legacyConfigPath, StandardCharsets.UTF_8), LegacyConfig.class)
                    );
                    save();
                    LOGGER.info("Migrated legacy configuration to {}", configPath.getFileName());
                } else {
                    config = new Config();
                    save();
                }
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

    /** Reloads the file and keeps the current process alive if parsing fails. */
    public void reload() {
        load();
    }

    public Config getConfig() {
        if (config == null) {
            config = new Config();
        }
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(getConfig()), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.error("Failed to save config", exception);
        }
    }

    /** Stores a name observed by this client so an offline snack placer remains readable later. */
    public boolean cachePlayerName(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) {
            return false;
        }
        Map<String, String> names = getConfig().playerNameCache;
        String uuidText = uuid.toString();
        if (name.equals(names.get(uuidText))) {
            return false;
        }
        names.put(uuidText, name);
        while (names.size() > MAX_CACHED_PLAYER_NAMES) {
            names.remove(names.keySet().iterator().next());
        }
        return true;
    }

    /** Returns a locally cached name only; this never queries an external service. */
    public String getCachedPlayerName(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return getConfig().playerNameCache.get(uuid.toString());
    }

    private Config migrateLegacyConfig(LegacyConfig legacy) {
        Config migrated = new Config();
        if (legacy == null) {
            return migrated;
        }
        migrated.enableDiscord = legacy.enableDiscord;
        migrated.discordWebhook = legacy.discordWebhook;
        migrated.enableNtfy = legacy.enableNtfy;
        migrated.ntfyTopic = legacy.ntfyTopic;
        migrated.nightTime = legacy.nightTime;
        migrated.resetTime = legacy.resetTime;
        migrated.messages.night = legacy.message;
        migrated.normalize();
        return migrated;
    }

    public static final class Config {
        public boolean enableDiscord = true;
        public String discordWebhook = "";
        public boolean enableNtfy = false;
        public String ntfyTopic = "";
        public int nightTime = 13000;
        public int resetTime = 1000;
        public Events events = new Events();
        public Messages messages = new Messages();
        public String pastureMonitorMode = "selected";
        public List<MonitoredPasture> monitoredPastures = new ArrayList<>();
        public Map<String, String> playerNameCache = new LinkedHashMap<>();

        private void normalize() {
            if (discordWebhook == null) discordWebhook = "";
            if (ntfyTopic == null) ntfyTopic = "";
            if (events == null) events = new Events();
            if (messages == null) messages = new Messages();
            if (messages.day == null || messages.day.isBlank()) messages.day = DEFAULT_DAY_MESSAGE;
            if (messages.night == null || messages.night.isBlank()) messages.night = DEFAULT_NIGHT_MESSAGE;
            if (messages.pastureEgg == null || messages.pastureEgg.isBlank()) messages.pastureEgg = "🥚 Pasture Egg Created";
            if (messages.snackConsumed == null || messages.snackConsumed.isBlank()) messages.snackConsumed = "🍪 Snack Consumed";
            if (pastureMonitorMode == null || pastureMonitorMode.isBlank()) pastureMonitorMode = "selected";
            if (monitoredPastures == null) monitoredPastures = new ArrayList<>();
            if (playerNameCache == null) playerNameCache = new LinkedHashMap<>();
            monitoredPastures.removeIf(pasture -> pasture == null || !pasture.isValid());
            playerNameCache.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue().isBlank());
            while (playerNameCache.size() > MAX_CACHED_PLAYER_NAMES) {
                playerNameCache.remove(playerNameCache.keySet().iterator().next());
            }
            nightTime = Math.max(0, Math.min(23999, nightTime));
            resetTime = Math.max(0, Math.min(23999, resetTime));
        }
    }

    public static final class Events {
        public boolean night = false;
        public boolean day = false;
        public boolean legendarySpawn = true;
        public boolean shinySpawn = true;
        public boolean pastureEgg = true;
        public boolean snackConsumed = true;
    }

    public static final class Messages {
        public String night = DEFAULT_NIGHT_MESSAGE;
        public String day = DEFAULT_DAY_MESSAGE;
        public String legendarySpawn = "⭐ Legendary Spawn";
        public String shinySpawn = "✨ Shiny Spawn";
        public String pastureEgg = "🥚 Pasture Egg Created";
        public String snackConsumed = "🍪 Snack Consumed";
    }

    public static final class MonitoredPasture {
        public String dimension = "minecraft:overworld";
        public int x;
        public int y;
        public int z;
        public String registeredByUuid = "";
        public String registeredByName = "";

        public MonitoredPasture() {
        }

        public MonitoredPasture(String dimension, int x, int y, int z, String registeredByUuid, String registeredByName) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.registeredByUuid = registeredByUuid;
            this.registeredByName = registeredByName;
        }

        public boolean isValid() {
            return dimension != null && !dimension.isBlank();
        }

        public boolean sameLocation(String otherDimension, int otherX, int otherY, int otherZ) {
            return dimension.equals(otherDimension) && x == otherX && y == otherY && z == otherZ;
        }
    }

    private static final class LegacyConfig {
        boolean enableDiscord = true;
        String discordWebhook = "";
        boolean enableNtfy = false;
        String ntfyTopic = "";
        int nightTime = 13000;
        int resetTime = 1000;
        String message = DEFAULT_NIGHT_MESSAGE;
    }
}
