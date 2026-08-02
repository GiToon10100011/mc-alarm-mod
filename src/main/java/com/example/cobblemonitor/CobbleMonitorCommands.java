package com.example.cobblemonitor;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Registers client-only commands for selecting monitored pastures. */
public final class CobbleMonitorCommands {
    private CobbleMonitorCommands() {
    }

    public static void register(ConfigManager configManager, Runnable reloadAction) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("cobble-monitor")
                        .then(ClientCommandManager.literal("reload")
                                .executes(context -> {
                                    reloadAction.run();
                                    feedback("Cobble Monitor configuration reloaded.");
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("pasture")
                                .then(ClientCommandManager.literal("add")
                                        .then(ClientCommandManager.literal("looking")
                                                .executes(context -> addLooking(configManager)))
                                        .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                                .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                        .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                                                .executes(context -> addCoordinates(
                                                                        configManager,
                                                                        IntegerArgumentType.getInteger(context, "x"),
                                                                        IntegerArgumentType.getInteger(context, "y"),
                                                                        IntegerArgumentType.getInteger(context, "z")
                                                                ))))))
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                                .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                        .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                                                .executes(context -> removeCoordinates(
                                                                        configManager,
                                                                        IntegerArgumentType.getInteger(context, "x"),
                                                                        IntegerArgumentType.getInteger(context, "y"),
                                                                        IntegerArgumentType.getInteger(context, "z")
                                                                ))))))
                                .then(ClientCommandManager.literal("list")
                                        .executes(context -> list(configManager)))
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> clear(configManager)))
                        )
        ));
    }

    private static int addLooking(ConfigManager configManager) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || !(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit)) {
            return failure("Look at a pasture block first.");
        }
        return add(configManager, hit.getBlockPos());
    }

    private static int addCoordinates(ConfigManager configManager, int x, int y, int z) {
        return add(configManager, new BlockPos(x, y, z));
    }

    private static int add(ConfigManager configManager, BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return failure("A world must be loaded to register a pasture.");
        }
        if (!PastureEggNotifier.isPastureBlock(client.world, pos)) {
            return failure("The selected block is not a Cobblemon pasture.");
        }

        String dimension = client.world.getRegistryKey().getValue().toString();
        String uuid = client.player.getUuidAsString();
        String name = client.player.getName().getString();
        ConfigManager.Config config = configManager.getConfig();

        for (ConfigManager.MonitoredPasture pasture : config.monitoredPastures) {
            if (pasture.sameLocation(dimension, pos.getX(), pos.getY(), pos.getZ())) {
                pasture.registeredByUuid = uuid;
                pasture.registeredByName = name;
                configManager.save();
                return success("Pasture monitor updated at " + pos.toShortString() + ".");
            }
        }

        config.monitoredPastures.add(new ConfigManager.MonitoredPasture(
                dimension, pos.getX(), pos.getY(), pos.getZ(), uuid, name
        ));
        configManager.save();
        return success("Pasture monitor added at " + pos.toShortString() + ".");
    }

    private static int removeCoordinates(ConfigManager configManager, int x, int y, int z) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return failure("A world must be loaded to remove a pasture.");
        }
        String dimension = client.world.getRegistryKey().getValue().toString();
        boolean removed = configManager.getConfig().monitoredPastures.removeIf(
                pasture -> pasture.sameLocation(dimension, x, y, z)
        );
        if (!removed) {
            return failure("No monitored pasture exists at that location.");
        }
        configManager.save();
        return success("Pasture monitor removed.");
    }

    private static int list(ConfigManager configManager) {
        var pastures = configManager.getConfig().monitoredPastures;
        if (pastures.isEmpty()) {
            return success("No monitored pastures are registered.");
        }
        feedback("Monitored pastures:");
        for (ConfigManager.MonitoredPasture pasture : pastures) {
            feedback("- " + pasture.dimension + " " + pasture.x + " " + pasture.y + " " + pasture.z
                    + " (registered by " + pasture.registeredByName + ")");
        }
        return 1;
    }

    private static int clear(ConfigManager configManager) {
        int count = configManager.getConfig().monitoredPastures.size();
        configManager.getConfig().monitoredPastures.clear();
        configManager.save();
        return success("Cleared " + count + " monitored pasture(s).");
    }

    private static int success(String message) {
        feedback(message);
        return 1;
    }

    private static int failure(String message) {
        feedback(message);
        return 0;
    }

    private static void feedback(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }
}
