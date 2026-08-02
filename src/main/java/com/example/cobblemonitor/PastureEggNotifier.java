package com.example.cobblemonitor;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import ludichat.cobbreeding.EggUtilities;
import ludichat.cobbreeding.PastureInventory;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Property;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Detects egg presence transitions on explicitly registered pasture blocks. */
public final class PastureEggNotifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.LOGGER_NAME);
    private static final String PASTURE_BLOCK_ID = "cobblemon:pasture";
    private static final String HAS_EGG_PROPERTY = "has_egg";

    private final ConfigManager configManager;
    private NotificationService notificationService;
    private final Map<String, Boolean> observedStates = new java.util.HashMap<>();

    public PastureEggNotifier(ConfigManager configManager, NotificationService notificationService) {
        this.configManager = configManager;
        this.notificationService = notificationService;
    }

    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void resetWorldState() {
        observedStates.clear();
    }

    public void tick(MinecraftClient client) {
        if (client.world == null || !configManager.getConfig().events.pastureEgg) {
            return;
        }

        String dimension = client.world.getRegistryKey().getValue().toString();
        for (ConfigManager.MonitoredPasture target : configManager.getConfig().monitoredPastures) {
            if (!target.dimension.equals(dimension)) {
                continue;
            }

            BlockPos pos = new BlockPos(target.x, target.y, target.z);
            if (!client.world.isChunkLoaded(pos)) {
                continue;
            }

            Boolean hasEgg = readHasEgg(client.world, pos);
            if (hasEgg == null) {
                continue;
            }

            String key = target.dimension + ":" + pos.toShortString();
            Boolean previous = observedStates.putIfAbsent(key, hasEgg);
            if (previous != null && !previous && hasEgg) {
                onEggCreated(client.world, pos, target);
            }
            observedStates.put(key, hasEgg);
        }
    }

    private void onEggCreated(ClientWorld world, BlockPos pos, ConfigManager.MonitoredPasture target) {
        EggMetadata metadata = readEggMetadata(world, pos);
        if (metadata.species.isEmpty()) {
            LOGGER.warn("Pasture egg detected at {}, but egg species was not synchronized to the client", pos);
            return;
        }

        LOGGER.info("Pasture detected using BlockState");
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("Species", String.join(", ", metadata.species));
        fields.put("Egg Count", metadata.eggCount);
        fields.put("Pasture", world.getRegistryKey().getValue() + " " + pos.toShortString());
        if (target.registeredByName != null && !target.registeredByName.isBlank()) {
            fields.put("Registered By", target.registeredByName);
        }
        fields.put("Source", "BlockState + BlockEntity NBT");
        notificationService.notify(NotificationService.EventType.PASTURE_EGG, fields);
    }

    private EggMetadata readEggMetadata(ClientWorld world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof PastureInventory inventory)) {
            return new EggMetadata(Set.of(), 0);
        }

        Set<String> species = new LinkedHashSet<>();
        int eggCount = 0;
        for (ItemStack stack : inventory.getItems()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            eggCount += stack.getCount();
            try {
                PokemonProperties properties = EggUtilities.extractProperties(stack);
                if (properties != null && properties.getSpecies() != null && !properties.getSpecies().isBlank()) {
                    species.add(properties.getSpecies());
                }
            } catch (RuntimeException exception) {
                LOGGER.debug("Could not read pasture egg metadata", exception);
            }
        }
        return new EggMetadata(species, eggCount);
    }

    private static Boolean readHasEgg(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!isPastureBlock(state)) {
            return null;
        }
        for (Property<?> property : state.getProperties()) {
            if (HAS_EGG_PROPERTY.equals(property.getName())) {
                return Boolean.valueOf(String.valueOf(state.get(property)));
            }
        }
        return null;
    }

    public static boolean isPastureBlock(ClientWorld world, BlockPos pos) {
        return isPastureBlock(world.getBlockState(pos));
    }

    private static boolean isPastureBlock(BlockState state) {
        return PASTURE_BLOCK_ID.equals(Registries.BLOCK.getId(state.getBlock()).toString())
                && state.getBlock() != Blocks.AIR;
    }

    private record EggMetadata(Set<String> species, int eggCount) {
    }
}
