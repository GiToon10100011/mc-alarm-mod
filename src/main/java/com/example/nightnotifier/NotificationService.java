package com.example.nightnotifier;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Sends notifications away from the Minecraft client tick thread. */
public final class NotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger("nightnotifier");
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ConfigManager.Config config;

    public NotificationService(ConfigManager.Config config) {
        this.config = config;
    }

    /**
     * Entry point for notification events. More event types can be added here later.
     */
    public void notify(EventType eventType) {
        if (eventType != EventType.NIGHT) {
            return;
        }

        String message = config.message;
        if (config.enableDiscord && !config.discordWebhook.isBlank()) {
            sendDiscord(message);
        }
        if (config.enableNtfy && !config.ntfyTopic.isBlank()) {
            sendNtfy(message);
        }
    }

    private void sendDiscord(String message) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("content", message);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.discordWebhook.trim()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            send(request, "Discord notification sent");
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Failed to send notification", exception);
        }
    }

    private void sendNtfy(String message) {
        try {
            URI topicUri = URI.create("https://ntfy.sh/" + config.ntfyTopic.trim());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(topicUri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(message, StandardCharsets.UTF_8))
                    .build();

            send(request, "ntfy notification sent");
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Failed to send notification", exception);
        }
    }

    private void send(HttpRequest request, String successMessage) {
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        LOGGER.info(successMessage);
                    } else {
                        LOGGER.error("Failed to send notification (HTTP {})", response.statusCode());
                    }
                })
                .exceptionally(exception -> {
                    LOGGER.error("Failed to send notification", exception);
                    return null;
                });
    }

    public enum EventType {
        NIGHT,
        DAY,
        RAIN,
        THUNDER,
        LEGENDARY_SPAWN
    }
}
