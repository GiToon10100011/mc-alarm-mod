package com.example.cobblemonitor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** Main client entrypoint for Cobble Monitor. */
public final class CobbleMonitorClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.LOGGER_NAME);

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
            synchronizeTimeNotificationState(world);
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

    /** Initializes phase state after a world or dimension change without sending an alert. */
    private void synchronizeTimeNotificationState(ClientWorld world) {
        if (!World.OVERWORLD.equals(world.getRegistryKey())) {
            nightNotified = true;
            dayNotified = true;
            return;
        }

        long timeOfDay = world.getTimeOfDay() % 24000L;
        nightNotified = timeOfDay >= config.nightTime;
        dayNotified = timeOfDay < config.resetTime;
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
}
