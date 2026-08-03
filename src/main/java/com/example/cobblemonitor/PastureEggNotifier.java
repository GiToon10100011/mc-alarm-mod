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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Detects egg presence transitions on explicitly registered pasture blocks. */
public final class PastureEggNotifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.LOGGER_NAME);
    private static final String PASTURE_BLOCK_ID = "cobblemon:pasture";
    private static final String HAS_EGG_PROPERTY = "has_egg";
    private static final String PART_PROPERTY = "part";
    private static final String TOP_PART = "top";

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

            BlockPos registeredPos = new BlockPos(target.x, target.y, target.z);
            if (!client.world.isChunkLoaded(registeredPos)) {
                continue;
            }
            BlockPos pos = resolvePastureBase(client.world, registeredPos);
            if (pos == null) {
                continue;
            }
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

        LOGGER.info("Pasture detected using BlockState");
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        if (metadata.species.isEmpty()) {
            String reason = metadata.inventorySynced
                    ? "egg metadata unavailable"
                    : "pasture inventory not synchronized";
            LOGGER.warn("Pasture egg detected at {}, but species is unavailable: {}", pos, reason);
            fields.put("Species", "Unavailable (" + reason + ")");
        } else {
            fields.put("Species", String.join(", ", metadata.species));
        }
        fields.put("Egg Count", metadata.inventorySynced ? metadata.eggCount : "Unavailable");
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
            return new EggMetadata(Set.of(), 0, false);
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
        return new EggMetadata(species, eggCount, true);
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

    /** Resolves either half of a two-block pasture to its bottom BlockEntity position. */
    public static BlockPos resolvePastureBase(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!isPastureBlock(state)) {
            return null;
        }

        String part = readProperty(state, PART_PROPERTY);
        if (TOP_PART.equalsIgnoreCase(part)) {
            BlockPos bottom = pos.down();
            return isPastureBlock(world.getBlockState(bottom)) ? bottom : null;
        }
        return pos;
    }

    /** Produces safe, local diagnostics for a looked-at pasture block. */
    public List<String> debugAt(ClientWorld world, BlockPos lookedPos) {
        List<String> lines = new ArrayList<>();
        BlockState lookedState = world.getBlockState(lookedPos);
        lines.add("Pasture debug:");
        lines.add("Looked position: " + lookedPos.toShortString());
        lines.add("Looked block: " + Registries.BLOCK.getId(lookedState.getBlock()));
        lines.add("Looked part=" + readProperty(lookedState, PART_PROPERTY)
                + ", has_egg=" + readProperty(lookedState, HAS_EGG_PROPERTY));

        BlockPos base = resolvePastureBase(world, lookedPos);
        if (base == null) {
            lines.add("Resolved base: unavailable (look at a Cobblemon pasture block)");
            return lines;
        }

        BlockState baseState = world.getBlockState(base);
        Boolean hasEgg = readHasEgg(world, base);
        EggMetadata metadata = readEggMetadata(world, base);
        String key = world.getRegistryKey().getValue() + ":" + base.toShortString();
        boolean registered = configManager.getConfig().monitoredPastures.stream().anyMatch(target -> {
            if (!world.getRegistryKey().getValue().toString().equals(target.dimension)) {
                return false;
            }
            BlockPos targetBase = resolvePastureBase(world, new BlockPos(target.x, target.y, target.z));
            return base.equals(targetBase);
        });

        lines.add("Resolved base: " + base.toShortString());
        lines.add("Base part=" + readProperty(baseState, PART_PROPERTY)
                + ", has_egg=" + hasEgg);
        BlockEntity blockEntity = world.getBlockEntity(base);
        lines.add("BlockEntity: " + (blockEntity == null ? "none" : blockEntity.getClass().getSimpleName()));
        lines.add("Inventory synced=" + metadata.inventorySynced
                + ", eggCount=" + metadata.eggCount
                + ", species=" + (metadata.species.isEmpty() ? "unavailable" : String.join(", ", metadata.species)));
        lines.add("Monitoring: " + registered + ", observed has_egg=" + observedStates.get(key));
        return lines;
    }

    private static boolean isPastureBlock(BlockState state) {
        return PASTURE_BLOCK_ID.equals(Registries.BLOCK.getId(state.getBlock()).toString())
                && state.getBlock() != Blocks.AIR;
    }

    private static String readProperty(BlockState state, String propertyName) {
        for (Property<?> property : state.getProperties()) {
            if (propertyName.equals(property.getName())) {
                return String.valueOf(state.get(property));
            }
        }
        return "unavailable";
    }

    private record EggMetadata(Set<String> species, int eggCount, boolean inventorySynced) {
    }
}
