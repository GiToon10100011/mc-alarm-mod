package com.example.cobblemonitor.mixin;

import com.cobblemon.mod.common.client.net.pasture.OpenPastureHandler;
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket;
import com.example.cobblemonitor.CobbleMonitorClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes Cobblemon's GUI packet without replacing the original pasture screen handler. */
@Mixin(OpenPastureHandler.class)
public abstract class OpenPastureHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"))
    private void cobbleMonitor$cacheOpenedPasture(
            OpenPasturePacket packet,
            MinecraftClient client,
            CallbackInfo callbackInfo
    ) {
        CobbleMonitorClient.handleOpenPasturePacket(packet);
    }
}
