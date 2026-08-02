package com.example.nightnotifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric client entrypoint for detecting the start of each Minecraft night. */
public final class NightNotifierClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("nightnotifier");

    private ConfigManager.Config config;
    private NotificationService notificationService;
    private ClientWorld lastWorld;
    private boolean nightNotified;

    @Override
    public void onInitializeClient() {
        ConfigManager configManager = new ConfigManager();
        configManager.load();
        config = configManager.getConfig();
        notificationService = new NotificationService(config);

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        LOGGER.info("NightNotifier initialized");
    }

    private void onClientTick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null) {
            lastWorld = null;
            nightNotified = false;
            return;
        }

        if (world != lastWorld) {
            lastWorld = world;
            nightNotified = false;
        }

        long timeOfDay = world.getTimeOfDay() % 24000L;
        if (timeOfDay < config.resetTime) {
            nightNotified = false;
        }

        if (timeOfDay >= config.nightTime && !nightNotified) {
            nightNotified = true;
            LOGGER.info("Night detected");
            notificationService.notify(NotificationService.EventType.NIGHT);
        }
    }
}
