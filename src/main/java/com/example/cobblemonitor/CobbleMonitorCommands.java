package com.example.cobblemonitor;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                        .executes(context -> helpGeneral())
                        .then(ClientCommandManager.literal("help")
                                .executes(context -> helpGeneral())
                                .then(ClientCommandManager.literal("pasture")
                                        .executes(context -> helpPasture()))
                                .then(ClientCommandManager.literal("notifications")
                                        .executes(context -> helpNotifications()))
                                .then(ClientCommandManager.literal("config")
                                        .executes(context -> helpConfig())))
                        .then(ClientCommandManager.literal("config")
                                .executes(context -> helpConfig())
                                        .then(ClientCommandManager.literal("discord")
                                                .then(ClientCommandManager.literal("clear")
                                                        .executes(context -> clearDiscordWebhook(configManager)))
                                                .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                                        .executes(context -> setDiscordWebhook(
                                                                configManager,
                                                                StringArgumentType.getString(context, "url")
                                                        ))))
                                        .then(ClientCommandManager.literal("ntfy")
                                                .then(ClientCommandManager.literal("clear")
                                                        .executes(context -> clearNtfyTopic(configManager)))
                                                .then(ClientCommandManager.argument("topic", StringArgumentType.word())
                                                        .executes(context -> setNtfyTopic(
                                                                configManager,
                                                                StringArgumentType.getString(context, "topic")
                                                        ))))
                                .then(ClientCommandManager.literal("event")
                                        .then(ClientCommandManager.literal("night")
                                                .then(ClientCommandManager.literal("on")
                                                        .executes(context -> setEventEnabled(configManager, "night", true)))
                                                .then(ClientCommandManager.literal("off")
                                                        .executes(context -> setEventEnabled(configManager, "night", false))))
                                        .then(ClientCommandManager.literal("day")
                                                .then(ClientCommandManager.literal("on")
                                                        .executes(context -> setEventEnabled(configManager, "day", true)))
                                                .then(ClientCommandManager.literal("off")
                                                        .executes(context -> setEventEnabled(configManager, "day", false))))))
                        .then(ClientCommandManager.literal("debug")
                                .executes(context -> debugStatus())
                                .then(ClientCommandManager.literal("status")
                                        .executes(context -> debugStatus()))
                                .then(ClientCommandManager.literal("notify")
                                        .then(ClientCommandManager.literal("night")
                                                .executes(context -> debugNotification("night")))
                                        .then(ClientCommandManager.literal("day")
                                                .executes(context -> debugNotification("day")))))
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
                                .then(ClientCommandManager.literal("inspect")
                                        .executes(context -> inspectLooking(configManager)))
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> clear(configManager)))
                        )
        ));
    }

    private static int helpGeneral() {
        feedback("Cobble Monitor commands:");
        feedback("/cobble-monitor help [pasture|notifications|config]");
        feedback("/cobble-monitor pasture add looking");
        feedback("/cobble-monitor pasture add <x> <y> <z>");
        feedback("/cobble-monitor pasture remove <x> <y> <z>");
        feedback("/cobble-monitor pasture list");
        feedback("/cobble-monitor pasture inspect");
        feedback("/cobble-monitor pasture clear");
        feedback("/cobble-monitor config discord <url>");
        feedback("/cobble-monitor config ntfy <topic>");
        feedback("/cobble-monitor config event night <on|off>");
        feedback("/cobble-monitor config event day <on|off>");
        feedback("/cobble-monitor debug status");
        feedback("/cobble-monitor debug notify <night|day>");
        feedback("/cobble-monitor reload");
        return 1;
    }

    private static int helpPasture() {
        feedback("Pasture monitoring:");
        feedback("Look at a Cobblemon pasture, then run:");
        feedback("/cobble-monitor pasture add looking");
        feedback("Or use coordinates in the current dimension:");
        feedback("/cobble-monitor pasture add <x> <y> <z>");
        feedback("Remove one: /cobble-monitor pasture remove <x> <y> <z>");
        feedback("Show all: /cobble-monitor pasture list");
        feedback("Inspect the block you are looking at: /cobble-monitor pasture inspect");
        feedback("Remove all: /cobble-monitor pasture clear");
        feedback("Only registered pastures are monitored.");
        return 1;
    }

    private static int helpNotifications() {
        feedback("Notifications:");
        feedback("Night: detected when world time reaches nightTime.");
        feedback("Day: detected when world time reaches resetTime.");
        feedback("Day/night monitoring is limited to the Overworld.");
        feedback("Pasture Egg: detected when HAS_EGG changes false -> true.");
        feedback("Snack: detected from Cobblemon's snack S2C packet.");
        feedback("Discord uses Embed cards; ntfy uses plain text.");
        feedback("Use config events.* to enable or disable event types.");
        return 1;
    }

    private static int helpConfig() {
        feedback("Configuration:");
        feedback("File: config/cobble-monitor.json");
        feedback("Edit the file, then run /cobble-monitor reload.");
        feedback("Set Discord: /cobble-monitor config discord <url>");
        feedback("Clear Discord: /cobble-monitor config discord clear");
        feedback("Set ntfy: /cobble-monitor config ntfy <topic>");
        feedback("Clear ntfy: /cobble-monitor config ntfy clear");
        feedback("Toggle night: /cobble-monitor config event night <on|off>");
        feedback("Toggle day: /cobble-monitor config event day <on|off>");
        feedback("Webhook values are kept local and are never logged.");
        return 1;
    }

    private static int setDiscordWebhook(ConfigManager configManager, String webhook) {
        String value = webhook == null ? "" : webhook.trim();
        if (value.isBlank()) {
            return failure("Discord Webhook URL cannot be empty.");
        }
        configManager.getConfig().discordWebhook = value;
        configManager.getConfig().enableDiscord = true;
        configManager.save();
        return success("Discord Webhook saved and enabled.");
    }

    private static int clearDiscordWebhook(ConfigManager configManager) {
        configManager.getConfig().discordWebhook = "";
        configManager.getConfig().enableDiscord = false;
        configManager.save();
        return success("Discord Webhook cleared and disabled.");
    }

    private static int setNtfyTopic(ConfigManager configManager, String topic) {
        String value = topic == null ? "" : topic.trim();
        if (value.isBlank()) {
            return failure("ntfy topic cannot be empty.");
        }
        configManager.getConfig().ntfyTopic = value;
        configManager.getConfig().enableNtfy = true;
        configManager.save();
        return success("ntfy topic saved and enabled.");
    }

    private static int clearNtfyTopic(ConfigManager configManager) {
        configManager.getConfig().ntfyTopic = "";
        configManager.getConfig().enableNtfy = false;
        configManager.save();
        return success("ntfy topic cleared and disabled.");
    }

    private static int setEventEnabled(ConfigManager configManager, String event, boolean enabled) {
        if ("night".equals(event)) {
            configManager.getConfig().events.night = enabled;
        } else if ("day".equals(event)) {
            configManager.getConfig().events.day = enabled;
        } else {
            return failure("Unknown event: " + event);
        }
        configManager.save();
        return success(event + " monitoring " + (enabled ? "enabled" : "disabled") + ".");
    }

    private static int debugStatus() {
        for (String line : CobbleMonitorClient.debugStatusLines()) {
            feedback(line);
        }
        return 1;
    }

    private static int debugNotification(String event) {
        if (!CobbleMonitorClient.sendDebugNotification(event)) {
            return failure("Could not request debug notification.");
        }
        return success("Debug " + event + " notification requested. Check Discord/ntfy and latest.log.");
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

    private static int inspectLooking(ConfigManager configManager) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null
                || !(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit)) {
            return failure("Look at a pasture block first.");
        }

        BlockPos pos = hit.getBlockPos();
        if (!PastureEggNotifier.isPastureBlock(client.world, pos)) {
            return failure("The block you are looking at is not a Cobblemon pasture.");
        }

        String dimension = client.world.getRegistryKey().getValue().toString();
        feedback("Pasture position: " + dimension + " " + pos.toShortString());
        for (ConfigManager.MonitoredPasture pasture : configManager.getConfig().monitoredPastures) {
            if (pasture.sameLocation(dimension, pos.getX(), pos.getY(), pos.getZ())) {
                feedback("Monitoring: enabled (registered by " + pasture.registeredByName + ")");
                return 1;
            }
        }
        feedback("Monitoring: not registered");
        feedback("Register it with: /cobble-monitor pasture add looking");
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
