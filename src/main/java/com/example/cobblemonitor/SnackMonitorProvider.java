package com.example.cobblemonitor;

import com.cobblemon.mod.common.block.entity.PokeSnackBlockEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Handles Cobblemon's snack packet and resolves nearby Pokemon metadata briefly. */
public final class SnackMonitorProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.LOGGER_NAME);
    private static final double RESOLUTION_RADIUS = 5.0D;
    private static final int RESOLUTION_TICKS = 6;

    private final ConfigManager configManager;
    private final Deque<PendingSnack> pendingSnacks = new ArrayDeque<>();
    private final Map<String, Long> recentPackets = new HashMap<>();
    private NotificationService notificationService;
    private boolean initialized;
    private long receivedPacketCount;
    private long deduplicatedPacketCount;
    private String lastPacket = "none";
    private String lastResolution = "none";

    public SnackMonitorProvider(ConfigManager configManager, NotificationService notificationService) {
        this.configManager = configManager;
        this.notificationService = notificationService;
    }

    /** The actual packet interception is supplied by the optional Cobblemon mixin. */
    public void initialize() {
        initialized = true;
        LOGGER.info("Snack provider is listening for Cobblemon's Poke Snack packet");
    }

    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void handlePacket(Object packet) {
        if (!(packet instanceof com.cobblemon.mod.common.net.messages.client.effect.PokeSnackBlockParticlesPacket snackPacket)) {
            return;
        }
        receivedPacketCount++;
        lastPacket = snackPacket.getBlockPos().toShortString() + " -> " + snackPacket.getEntityPos().toShortString();
        if (!configManager.getConfig().events.snackConsumed) {
            lastResolution = "ignored: snack event disabled";
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        String key = client.world.getRegistryKey().getValue() + ":"
                + snackPacket.getBlockPos().toShortString() + ":"
                + snackPacket.getEntityPos().toShortString();
        long tick = client.world.getTime();
        Long previousTick = recentPackets.put(key, tick);
        if (previousTick != null && tick - previousTick < 5) {
            deduplicatedPacketCount++;
            lastResolution = "ignored: duplicate packet";
            return;
        }

        LOGGER.info("Snack detected using Packet");
        lastResolution = "pending Pokemon resolution";
        pendingSnacks.addLast(new PendingSnack(
                client.world,
                snackPacket.getBlockPos(),
                snackPacket.getEntityPos()
        ));
    }

    public void tick(MinecraftClient client) {
        if (client.world != null) {
            long currentTick = client.world.getTime();
            recentPackets.entrySet().removeIf(entry -> currentTick - entry.getValue() > 40);
        }
        if (pendingSnacks.isEmpty()) {
            return;
        }

        var iterator = pendingSnacks.iterator();
        while (iterator.hasNext()) {
            PendingSnack pending = iterator.next();
            if (pending.world != client.world) {
                iterator.remove();
                continue;
            }

            PokemonEntity pokemon = findNearestPokemon(pending);
            if (pokemon != null || pending.age >= RESOLUTION_TICKS) {
                sendNotification(client, pending, pokemon);
                iterator.remove();
            } else {
                pending.age++;
            }
        }
    }

    private PokemonEntity findNearestPokemon(PendingSnack pending) {
        Vec3d target = Vec3d.ofCenter(pending.entityPos);
        Box searchBox = new Box(target, target).expand(RESOLUTION_RADIUS);
        return pending.world.getEntitiesByClass(
                        PokemonEntity.class,
                        searchBox,
                        Entity::isAlive
                ).stream()
                .min(Comparator.comparingDouble(entity -> entity.getPos().squaredDistanceTo(target)))
                .orElse(null);
    }

    private void sendNotification(MinecraftClient client, PendingSnack pending, PokemonEntity pokemonEntity) {
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        PokeSnackBlockEntity snack = null;
        if (pending.world.getBlockEntity(pending.blockPos) instanceof PokeSnackBlockEntity blockEntity) {
            snack = blockEntity;
            addSnackMetadata(client, fields, snack);
        }

        if (pokemonEntity != null) {
            addPokemonMetadata(fields, pokemonEntity);
            fields.put("Pokemon Detection", "Estimated Pokemon (near packet position)");
        } else {
            fields.put("Pokemon", "Unavailable");
            fields.put("Pokemon Detection", "Estimated Pokemon could not be resolved");
        }
        fields.put("Snack Position", pending.world.getRegistryKey().getValue() + " " + pending.blockPos.toShortString());
        notificationService.notify(NotificationService.EventType.SNACK_CONSUMED, fields);
        lastResolution = pokemonEntity == null
                ? "notification sent with unresolved Pokemon"
                : "notification sent for " + pokemonEntity.getPokemon().getSpecies().getName();
    }

    /** Returns packet-observation diagnostics without scanning entities or exposing credentials. */
    public List<String> debugStatusLines() {
        return List.of(
                "Snack debug:",
                "Provider initialized=" + initialized + ", event enabled=" + configManager.getConfig().events.snackConsumed,
                "Packets received=" + receivedPacketCount + ", deduplicated=" + deduplicatedPacketCount
                        + ", pending=" + pendingSnacks.size(),
                "Last packet: " + lastPacket,
                "Last resolution: " + lastResolution
        );
    }

    private void addSnackMetadata(MinecraftClient client, Map<String, Object> fields, PokeSnackBlockEntity snack) {
        UUID placedBy = snack.getPlacedBy();
        if (placedBy != null) {
            String name = resolvePlayerName(client, placedBy);
            fields.put("Placed By", name == null ? "Unknown or offline player" : name);
        }
    }

    private String resolvePlayerName(MinecraftClient client, UUID uuid) {
        PlayerEntity player = client.world == null ? null : client.world.getPlayerByUuid(uuid);
        if (player != null) {
            String name = player.getName().getString();
            configManager.cachePlayerName(uuid, name);
            return name;
        }
        if (client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry != null) {
                String name = entry.getProfile().getName();
                configManager.cachePlayerName(uuid, name);
                return name;
            }
        }
        return configManager.getCachedPlayerName(uuid);
    }

    private static void addPokemonMetadata(Map<String, Object> fields, PokemonEntity entity) {
        Pokemon pokemon = entity.getPokemon();
        fields.put("Species", pokemon.getSpecies().getName());
        fields.put("Level", pokemon.getLevel());
        fields.put("Shiny", pokemon.getShiny());
        fields.put("Gender", String.valueOf(pokemon.getGender()));
        fields.put("UUID", entity.getUuidAsString());
        fields.put("Pokemon Position", entity.getBlockPos().toShortString());
        fields.put(
                NotificationService.DISCORD_THUMBNAIL_URL,
                pokemonSpriteUrl(pokemon.getSpecies().getNationalPokedexNumber(), pokemon.getShiny())
        );
    }

    /**
     * Returns PokeAPI's standard pixel sprite URL for Discord to fetch directly.
     * Cobblemon ships model textures, not standalone sprite assets suitable for an embed.
     */
    private static String pokemonSpriteUrl(int nationalPokedexNumber, boolean shiny) {
        if (nationalPokedexNumber <= 0) {
            return "";
        }
        String variantPath = shiny ? "shiny/" : "";
        return "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/"
                + variantPath + nationalPokedexNumber + ".png";
    }

    private static final class PendingSnack {
        private final ClientWorld world;
        private final BlockPos blockPos;
        private final BlockPos entityPos;
        private int age;

        private PendingSnack(ClientWorld world, BlockPos blockPos, BlockPos entityPos) {
            this.world = world;
            this.blockPos = blockPos;
            this.entityPos = entityPos;
        }
    }
}
