package com.example.cobblemonitor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/** Main client entrypoint for Cobble Monitor. */
public final class CobbleMonitorClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.LOGGER_NAME);
    private static CobbleMonitorClient activeInstance;

    private static SnackMonitorProvider snackMonitorProvider;

    private ConfigManager configManager;
    private ConfigManager.Config config;
    private NotificationService notificationService;
    private PastureEggNotifier pastureEggNotifier;
    private ClientWorld lastWorld;
    private boolean nightNotified;
    private boolean dayNotified;

    @Override
    public void onInitializeClient() {
        activeInstance = this;
        configManager = new ConfigManager();
        configManager.load();
        config = configManager.getConfig();
        notificationService = new NotificationService(config);

        CobbleMonitorCommands.register(configManager, this::reloadRuntimeConfiguration);

        if (FabricLoader.getInstance().isModLoaded("cobblemon")
                && FabricLoader.getInstance().isModLoaded("cobbreeding")) {
            pastureEggNotifier = new PastureEggNotifier(configManager, notificationService);
            LOGGER.info("Pasture Egg provider initialized");
        } else {
            LOGGER.info("Pasture Egg provider disabled; Cobblemon and Cobbreeding are required");
        }

        if (FabricLoader.getInstance().isModLoaded("cobblemon")) {
            snackMonitorProvider = new SnackMonitorProvider(configManager, notificationService);
            snackMonitorProvider.initialize();
            LOGGER.info("Snack provider initialized");
        } else {
            LOGGER.info("Snack provider disabled; Cobblemon is not installed");
        }

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        LOGGER.info("Cobble Monitor initialized");
    }

    private void onClientTick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null) {
            lastWorld = null;
            nightNotified = false;
            dayNotified = false;
            if (snackMonitorProvider != null) {
                snackMonitorProvider.tick(client);
            }
            return;
        }

        if (world != lastWorld) {
            lastWorld = world;
            nightNotified = false;
            dayNotified = false;
            if (pastureEggNotifier != null) {
                pastureEggNotifier.resetWorldState();
            }
        }

        if (!World.OVERWORLD.equals(world.getRegistryKey())) {
            if (pastureEggNotifier != null) {
                pastureEggNotifier.tick(client);
            }
            if (snackMonitorProvider != null) {
                snackMonitorProvider.tick(client);
            }
            return;
        }

        long timeOfDay = world.getTimeOfDay() % 24000L;
        if (timeOfDay < config.resetTime) {
            nightNotified = false;
            if (!dayNotified && config.events != null) {
                dayNotified = true;
                LOGGER.info("Day detected");
                if (config.events.day) {
                    notificationService.notify(NotificationService.EventType.DAY, Map.of(
                            "Time", timeOfDay,
                            "Dimension", world.getRegistryKey().getValue().toString()
                    ));
                }
            }
        } else if (timeOfDay >= config.nightTime) {
            dayNotified = false;
        }

        if (timeOfDay >= config.nightTime && !nightNotified && config.events != null) {
            nightNotified = true;
            LOGGER.info("Night detected");
            if (config.events.night) {
                notificationService.notify(NotificationService.EventType.NIGHT, Map.of(
                        "Time", timeOfDay,
                        "Dimension", world.getRegistryKey().getValue().toString()
                ));
            }
        }

        if (pastureEggNotifier != null) {
            pastureEggNotifier.tick(client);
        }
        if (snackMonitorProvider != null) {
            snackMonitorProvider.tick(client);
        }
    }

    private void reloadRuntimeConfiguration() {
        configManager.reload();
        config = configManager.getConfig();
        notificationService = new NotificationService(config);
        if (pastureEggNotifier != null) {
            pastureEggNotifier.setNotificationService(notificationService);
        }
        if (snackMonitorProvider != null) {
            snackMonitorProvider.setNotificationService(notificationService);
        }
        LOGGER.info("Configuration reloaded");
    }

    /** Receives an optional Cobblemon packet from the integration mixin. */
    public static void handleSnackPacket(Object packet) {
        if (snackMonitorProvider != null) {
            snackMonitorProvider.handlePacket(packet);
        }
    }

    /** Returns runtime diagnostics without exposing notification credentials. */
    public static List<String> debugStatusLines() {
        if (activeInstance == null || activeInstance.config == null) {
            return List.of("Cobble Monitor is not initialized.");
        }

        ConfigManager.Config config = activeInstance.config;
        MinecraftClient client = MinecraftClient.getInstance();
        String dimension = client.world == null
                ? "none"
                : client.world.getRegistryKey().getValue().toString();
        String time = client.world == null
                ? "none"
                : String.valueOf(client.world.getTimeOfDay() % 24000L);
        boolean overworld = client.world != null
                && World.OVERWORLD.equals(client.world.getRegistryKey());
        String version = FabricLoader.getInstance()
                .getModContainer("cobble-monitor")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");

        return List.of(
                "Cobble Monitor debug status:",
                "Version: " + version,
                "World: " + (client.world == null ? "not connected" : "connected"),
                "Dimension: " + dimension + " (Overworld eligible: " + overworld + ")",
                "Time: " + time + " / nightTime=" + config.nightTime + ", resetTime=" + config.resetTime,
                "Events: night=" + config.events.night + ", day=" + config.events.day,
                "State: nightNotified=" + activeInstance.nightNotified
                        + ", dayNotified=" + activeInstance.dayNotified,
                "Discord: enabled=" + config.enableDiscord
                        + ", configured=" + !config.discordWebhook.isBlank(),
                "ntfy: enabled=" + config.enableNtfy
                        + ", configured=" + !config.ntfyTopic.isBlank()
        );
    }

    /** Sends a deliberate test notification through the configured destinations. */
    public static boolean sendDebugNotification(String eventName) {
        if (activeInstance == null || activeInstance.notificationService == null) {
            return false;
        }

        NotificationService.EventType eventType = switch (eventName) {
            case "night" -> NotificationService.EventType.NIGHT;
            case "day" -> NotificationService.EventType.DAY;
            default -> null;
        };
        if (eventType == null) {
            return false;
        }

        activeInstance.notificationService.notify(eventType, Map.of(
                "Source", "Manual debug command"
        ));
        LOGGER.info("Debug notification requested: {}", eventName);
        return true;
    }
}
