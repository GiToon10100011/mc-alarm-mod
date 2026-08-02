package com.example.cobblemonitor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sends event notifications asynchronously to Discord and ntfy. */
public final class NotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.LOGGER_NAME);
    private static final int DISCORD_COLOR_NIGHT = 0x26356B;
    private static final int DISCORD_COLOR_EGG = 0x58B368;
    private static final int DISCORD_COLOR_SNACK = 0xF39C12;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ConfigManager.Config config;

    public NotificationService(ConfigManager.Config config) {
        this.config = config;
    }

    /** Sends one event to every enabled destination without blocking the game thread. */
    public void notify(EventType eventType, Map<String, Object> metadata) {
        String message = messageFor(eventType);
        if (message == null) {
            return;
        }

        Map<String, Object> safeMetadata = metadata == null
                ? Map.of()
                : new LinkedHashMap<>(metadata);

        if (config.enableDiscord && !config.discordWebhook.isBlank()) {
            sendDiscord(eventType, message, safeMetadata);
        }
        if (config.enableNtfy && !config.ntfyTopic.isBlank()) {
            sendNtfy(message, safeMetadata);
        }
    }

    public void notify(EventType eventType) {
        notify(eventType, Map.of());
    }

    private String messageFor(EventType eventType) {
        return switch (eventType) {
            case NIGHT -> config.messages.night;
            case PASTURE_EGG -> config.messages.pastureEgg;
            case SNACK_CONSUMED -> config.messages.snackConsumed;
        };
    }

    private void sendDiscord(EventType eventType, String message, Map<String, Object> metadata) {
        try {
            URI webhookUri = validHttpUri(config.discordWebhook.trim());
            JsonObject body = new JsonObject();
            body.addProperty("username", "Cobble Monitor");

            JsonArray embeds = new JsonArray();
            JsonObject embed = new JsonObject();
            embed.addProperty("title", message);
            embed.addProperty("description", descriptionFor(eventType));
            embed.addProperty("color", colorFor(eventType));

            JsonArray fields = new JsonArray();
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                String value = truncate(String.valueOf(entry.getValue()), 1024);
                if (value.isBlank()) {
                    continue;
                }
                JsonObject field = new JsonObject();
                field.addProperty("name", truncate(entry.getKey(), 256));
                field.addProperty("value", value);
                field.addProperty("inline", isInlineField(entry.getKey()));
                fields.add(field);
            }
            if (!fields.isEmpty()) {
                embed.add("fields", fields);
            }

            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Cobble Monitor");
            embed.add("footer", footer);
            embed.addProperty("timestamp", Instant.now().toString());
            embeds.add(embed);
            body.add("embeds", embeds);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(webhookUri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            send(request, "Discord notification sent");
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Failed to send notification", exception);
        }
    }

    private void sendNtfy(String title, Map<String, Object> metadata) {
        try {
            URI topicUri = validHttpUri("https://ntfy.sh/" + config.ntfyTopic.trim());
            StringBuilder message = new StringBuilder(title);
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank()) {
                    message.append('\n').append(entry.getKey()).append(": ").append(entry.getValue());
                }
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(topicUri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(message.toString(), StandardCharsets.UTF_8))
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

    private static URI validHttpUri(String value) {
        URI uri = URI.create(value);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Notification URL must use HTTP or HTTPS");
        }
        return uri;
    }

    private static String descriptionFor(EventType eventType) {
        return switch (eventType) {
            case NIGHT -> "Minecraft night has started.";
            case PASTURE_EGG -> "A new egg was detected in a monitored pasture.";
            case SNACK_CONSUMED -> "A wild Pokemon consumed a Poke Snack.";
        };
    }

    private static int colorFor(EventType eventType) {
        return switch (eventType) {
            case NIGHT -> DISCORD_COLOR_NIGHT;
            case PASTURE_EGG -> DISCORD_COLOR_EGG;
            case SNACK_CONSUMED -> DISCORD_COLOR_SNACK;
        };
    }

    private static boolean isInlineField(String name) {
        return switch (name) {
            case "Species", "Level", "Shiny", "Gender", "Owner", "Registered By" -> true;
            default -> false;
        };
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    public enum EventType {
        NIGHT,
        PASTURE_EGG,
        SNACK_CONSUMED
    }
}
