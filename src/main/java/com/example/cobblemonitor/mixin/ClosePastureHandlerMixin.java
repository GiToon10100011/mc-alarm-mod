package com.example.cobblemonitor.mixin;

import com.cobblemon.mod.common.client.net.pasture.ClosePastureHandler;
import com.cobblemon.mod.common.net.messages.client.pasture.ClosePasturePacket;
import com.example.cobblemonitor.CobbleMonitorClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ends the active pasture association as soon as Cobblemon closes its GUI. */
@Mixin(ClosePastureHandler.class)
public abstract class ClosePastureHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"))
    private void cobbleMonitor$closePastureGui(
            ClosePasturePacket packet,
            MinecraftClient client,
            CallbackInfo callbackInfo
    ) {
        CobbleMonitorClient.handleClosePasturePacket();
    }
}
