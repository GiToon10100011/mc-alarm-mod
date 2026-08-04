package com.example.cobblemonitor.mixin;

import com.cobblemon.mod.common.client.net.pasture.PokemonPasturedHandler;
import com.cobblemon.mod.common.net.messages.client.pasture.PokemonPasturedPacket;
import com.example.cobblemonitor.CobbleMonitorClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the current monitored pasture cache in sync while its Cobblemon GUI is open. */
@Mixin(PokemonPasturedHandler.class)
public abstract class PokemonPasturedHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"))
    private void cobbleMonitor$cachePasturedPokemon(
            PokemonPasturedPacket packet,
            MinecraftClient client,
            CallbackInfo callbackInfo
    ) {
        CobbleMonitorClient.handlePokemonPasturedPacket(packet);
    }
}
