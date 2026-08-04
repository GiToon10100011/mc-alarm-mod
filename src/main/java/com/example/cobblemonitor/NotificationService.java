package com.example.cobblemonitor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Sends event notifications asynchronously to Discord and ntfy. */
public final class NotificationService {
    /** Internal metadata key for a Discord-only thumbnail. It is never rendered as a field. */
    public static final String DISCORD_THUMBNAIL_URL = "_discordThumbnailUrl";
    /** Internal metadata key for a Discord-only dynamic Embed title. */
    public static final String DISCORD_TITLE = "_discordTitle";
    private static final String POKEAPI_SPRITE_BASE_URL = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/";
    private static final String COBBLEMON_TEXTURE_BASE_URL = "https://gitlab.com/cable-mc/cobblemon/-/raw/1.7.3/common/src/main/resources/assets/cobblemon/textures/pokemon/";
    private static final String SHOWDOWN_SPRITE_BASE_URL = "https://play.pokemonshowdown.com/sprites/gen5";
    /**
     * Cobblemon form aspects that Pokemon Showdown names with a known suffix.
     * The order is significant: Showdown appends a regional or alternate-form
     * name before a breed name, so "paldean" + "blaze-breed" is "paldeablaze".
     */
    private static final Map<String, String> SHOWDOWN_FORM_SUFFIXES = new LinkedHashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.LOGGER_NAME);
    private static final int DISCORD_COLOR_NIGHT = 0x26356B;
    private static final int DISCORD_COLOR_DAY = 0xF1C40F;
    private static final int DISCORD_COLOR_EGG = 0x58B368;
    private static final int DISCORD_COLOR_SNACK = 0xF39C12;

    static {
        SHOWDOWN_FORM_SUFFIXES.put("alolan", "alola");
        SHOWDOWN_FORM_SUFFIXES.put("galarian", "galar");
        SHOWDOWN_FORM_SUFFIXES.put("hisuian", "hisui");
        SHOWDOWN_FORM_SUFFIXES.put("paldean", "paldea");
        SHOWDOWN_FORM_SUFFIXES.put("mega", "mega");
        SHOWDOWN_FORM_SUFFIXES.put("mega_x", "megax");
        SHOWDOWN_FORM_SUFFIXES.put("mega_y", "megay");
        SHOWDOWN_FORM_SUFFIXES.put("gmax", "gmax");
        SHOWDOWN_FORM_SUFFIXES.put("blaze_breed", "blaze");
        SHOWDOWN_FORM_SUFFIXES.put("aqua_breed", "aqua");
    }

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
        if (config.useGameLanguageMessages) {
            return localizedMessageFor(eventType);
        }
        return switch (eventType) {
            case NIGHT -> config.messages.night;
            case DAY -> config.messages.day;
            case PASTURE_EGG -> config.messages.pastureEgg;
            case SNACK_CONSUMED -> config.messages.snackConsumed;
        };
    }

    private static String localizedMessageFor(EventType eventType) {
        if (usesKoreanLanguage()) {
            return switch (eventType) {
                case NIGHT -> "🌙 Minecraft에서 밤이 시작되었습니다.";
                case DAY -> "☀️ Minecraft에서 낮이 시작되었습니다.";
                case PASTURE_EGG -> "🥚 목장 알 생성";
                case SNACK_CONSUMED -> "🍪 포케스낵 소비";
            };
        }
        return switch (eventType) {
            case NIGHT -> "🌙 Minecraft night has started.";
            case DAY -> "☀️ Minecraft day has started.";
            case PASTURE_EGG -> "🥚 Pasture Egg Created";
            case SNACK_CONSUMED -> "🍪 Snack Consumed";
        };
    }

    /** Creates a localized snack title while retaining the detected Pokemon name. */
    public static String snackDiscordTitle(String pokemonName) {
        if (pokemonName == null || pokemonName.isBlank()) {
            return localizedMessageFor(EventType.SNACK_CONSUMED);
        }
        return usesKoreanLanguage()
                ? "🍪 " + pokemonName + " - 포케스낵 소비"
                : "🍪 " + pokemonName + " - Snack Consumed";
    }

    /** Creates a localized pasture egg title while retaining the inferred Pokemon name. */
    public static String pastureEggDiscordTitle(String pokemonName) {
        if (pokemonName == null || pokemonName.isBlank()) {
            return localizedMessageFor(EventType.PASTURE_EGG);
        }
        return usesKoreanLanguage()
                ? "🥚 " + pokemonName + " 알 생성"
                : "🥚 " + pokemonName + " Egg Created";
    }

    private void sendDiscord(EventType eventType, String message, Map<String, Object> metadata) {
        try {
            URI webhookUri = validHttpUri(config.discordWebhook.trim());
            JsonObject body = new JsonObject();
            body.addProperty("username", "Cobble Monitor");

            JsonArray embeds = new JsonArray();
            JsonObject embed = new JsonObject();
            Object titleOverride = metadata.remove(DISCORD_TITLE);
            String title = titleOverride == null || String.valueOf(titleOverride).isBlank()
                    ? message
                    : truncate(String.valueOf(titleOverride), 256);
            embed.addProperty("title", title);
            embed.addProperty("description", descriptionFor(eventType));
            embed.addProperty("color", colorFor(eventType));

            Object thumbnailUrl = metadata.remove(DISCORD_THUMBNAIL_URL);
            if (thumbnailUrl != null
                    && !String.valueOf(thumbnailUrl).isBlank()
                    && isValidThumbnailUrl(String.valueOf(thumbnailUrl))) {
                JsonObject thumbnail = new JsonObject();
                thumbnail.addProperty("url", thumbnailUrl.toString());
                embed.add("thumbnail", thumbnail);
            }

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
                if (DISCORD_THUMBNAIL_URL.equals(entry.getKey()) || DISCORD_TITLE.equals(entry.getKey())) {
                    continue;
                }
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
        if (usesKoreanLanguage()) {
            return switch (eventType) {
                case NIGHT -> "Minecraft 세계에서 밤이 시작되었습니다.";
                case DAY -> "Minecraft 세계에서 낮이 시작되었습니다.";
                case PASTURE_EGG -> "감시 중인 목장에서 새 알을 감지했습니다.";
                case SNACK_CONSUMED -> "야생 포켓몬이 포케스낵을 소비했습니다.";
            };
        }
        return switch (eventType) {
            case NIGHT -> "Minecraft night has started.";
            case DAY -> "Minecraft day has started.";
            case PASTURE_EGG -> "A new egg was detected in a monitored pasture.";
            case SNACK_CONSUMED -> "A wild Pokemon consumed a Poke Snack.";
        };
    }

    private static boolean usesKoreanLanguage() {
        String languageCode = MinecraftClient.getInstance()
                .getLanguageManager()
                .getLanguage();
        return languageCode.toLowerCase(Locale.ROOT).startsWith("ko");
    }

    private static int colorFor(EventType eventType) {
        return switch (eventType) {
            case NIGHT -> DISCORD_COLOR_NIGHT;
            case DAY -> DISCORD_COLOR_DAY;
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

    private static boolean isValidThumbnailUrl(String value) {
        try {
            return validHttpUri(value).getHost() != null;
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Ignoring invalid Discord thumbnail URL");
            return false;
        }
    }

    /** Returns PokeAPI's standard pixel sprite URL for Discord to fetch directly. */
    public static String pokemonSpriteUrl(int nationalPokedexNumber, boolean shiny) {
        if (nationalPokedexNumber <= 0) {
            return "";
        }
        String variantPath = shiny ? "shiny/" : "";
        return POKEAPI_SPRITE_BASE_URL + variantPath + nationalPokedexNumber + ".png";
    }

    /**
     * Returns the installed Cobblemon 1.7.3 texture for a non-base form.
     * Base forms continue using PokeAPI's compact sprite endpoint.
     */
    public static String cobblemonSpriteUrl(
            int nationalPokedexNumber,
            String speciesPath,
            List<String> formAspects,
            boolean shiny
    ) {
        if (formAspects == null || formAspects.isEmpty()) {
            return pokemonSpriteUrl(nationalPokedexNumber, shiny);
        }
        if (nationalPokedexNumber <= 0 || speciesPath == null || speciesPath.isBlank()) {
            return "";
        }

        // PokeAPI's endpoint is keyed by Pokedex number alone and cannot address a
        // regional or alternate form, so try Showdown's form-aware sprite before
        // falling back to the Cobblemon texture below.
        String showdownSprite = showdownFormSpriteUrl(speciesPath, formAspects, shiny);
        if (!showdownSprite.isBlank()) {
            return showdownSprite;
        }

        String normalizedSpecies = normalizeTexturePart(speciesPath);
        String aspectSuffix = formAspects.stream()
                .filter(aspect -> aspect != null && !aspect.isBlank())
                .map(NotificationService::normalizeTexturePart)
                .sorted()
                .collect(Collectors.joining("_"));
        if (normalizedSpecies.isBlank() || aspectSuffix.isBlank()) {
            return pokemonSpriteUrl(nationalPokedexNumber, shiny);
        }

        String folder = String.format(Locale.ROOT, "%04d_%s", nationalPokedexNumber, normalizedSpecies);
        String filename = normalizedSpecies + "_" + aspectSuffix + (shiny ? "_shiny" : "") + ".png";
        return COBBLEMON_TEXTURE_BASE_URL + folder + "/" + filename;
    }

    /**
     * Returns a Pokemon Showdown sprite only when every aspect of the form maps to
     * a known Showdown name. An unrecognized aspect returns an empty string so the
     * caller keeps using its existing fallback.
     */
    private static String showdownFormSpriteUrl(String speciesPath, List<String> formAspects, boolean shiny) {
        Set<String> remainingAspects = new LinkedHashSet<>();
        for (String aspect : formAspects) {
            if (aspect != null && !aspect.isBlank()) {
                remainingAspects.add(normalizeTexturePart(aspect));
            }
        }
        if (remainingAspects.isEmpty()) {
            return "";
        }

        StringBuilder formSuffix = new StringBuilder();
        for (Map.Entry<String, String> entry : SHOWDOWN_FORM_SUFFIXES.entrySet()) {
            if (remainingAspects.remove(entry.getKey())) {
                formSuffix.append(entry.getValue());
            }
        }
        if (formSuffix.isEmpty() || !remainingAspects.isEmpty()) {
            return "";
        }

        String speciesId = normalizeTexturePart(speciesPath).replaceAll("[^a-z0-9]", "");
        if (speciesId.isBlank()) {
            return "";
        }
        // Showdown has no plain "tauros-paldea"; its Combat Breed carries the name.
        if ("tauros".equals(speciesId) && "paldea".contentEquals(formSuffix)) {
            formSuffix.append("combat");
        }

        return SHOWDOWN_SPRITE_BASE_URL + (shiny ? "-shiny/" : "/") + speciesId + "-" + formSuffix + ".png";
    }

    private static String normalizeTexturePart(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    public enum EventType {
        NIGHT,
        DAY,
        PASTURE_EGG,
        SNACK_CONSUMED
    }
}
