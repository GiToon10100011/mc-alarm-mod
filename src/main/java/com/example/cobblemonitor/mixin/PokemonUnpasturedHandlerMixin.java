package com.example.cobblemonitor.mixin;

import com.cobblemon.mod.common.client.net.pasture.PokemonUnpasturedHandler;
import com.cobblemon.mod.common.net.messages.client.pasture.PokemonUnpasturedPacket;
import com.example.cobblemonitor.CobbleMonitorClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes Pokemon from the active monitored pasture cache as Cobblemon removes them. */
@Mixin(PokemonUnpasturedHandler.class)
public abstract class PokemonUnpasturedHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"))
    private void cobbleMonitor$cacheUnpasturedPokemon(
            PokemonUnpasturedPacket packet,
            MinecraftClient client,
            CallbackInfo callbackInfo
    ) {
        CobbleMonitorClient.handlePokemonUnpasturedPacket(packet);
    }
}
