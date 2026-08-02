package com.example.cobblemonitor.mixin;

import com.cobblemon.mod.common.client.net.effect.PokeSnackBlockParticlesHandler;
import com.cobblemon.mod.common.net.messages.client.effect.PokeSnackBlockParticlesPacket;
import com.example.cobblemonitor.CobbleMonitorClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes Cobblemon's existing snack effect handler without replacing it. */
@Mixin(PokeSnackBlockParticlesHandler.class)
public abstract class PokeSnackBlockParticlesHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"))
    private void cobbleMonitor$observeSnack(
            PokeSnackBlockParticlesPacket packet,
            MinecraftClient client,
            CallbackInfo callbackInfo
    ) {
        CobbleMonitorClient.handleSnackPacket(packet);
    }
}
