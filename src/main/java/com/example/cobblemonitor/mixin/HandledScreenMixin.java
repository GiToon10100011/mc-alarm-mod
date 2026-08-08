package com.example.cobblemonitor.mixin;

import com.example.cobblemonitor.CobbleMonitorClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the egg outlines once the container screen itself has finished rendering. */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow
    protected int x;
    @Shadow
    protected int y;
    @Shadow
    protected int backgroundWidth;
    @Shadow
    protected int backgroundHeight;

    @Inject(method = "render", at = @At("TAIL"))
    private void cobbleMonitor$outlineNotableEggs(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo callbackInfo
    ) {
        CobbleMonitorClient.renderEggHighlights(
                (HandledScreen<?>) (Object) this,
                context,
                x,
                y,
                backgroundWidth,
                backgroundHeight
        );
    }
}
