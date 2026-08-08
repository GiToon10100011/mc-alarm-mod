package com.example.cobblemonitor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;

/**
 * Outlines notable Cobbreeding eggs in an open container screen and reports how many the
 * container holds in total.
 *
 * <p>Drawing at the slot's own coordinates sidesteps the question of a container's grid
 * shape. Sophisticated Storage lays its slots out through a scroll panel whose
 * {@code updateSlotsPosition} rewrites {@link Slot#x} and {@link Slot#y}, so reading them
 * per frame stays correct while scrolled, sorted, or after the player moves an egg by
 * hand - none of which a "row 4, slot 2" message could keep up with.
 *
 * <p>An outline can only be drawn on a slot the screen is currently showing, which alone
 * would hide an egg sitting in a scrolled-out row. The counts avoid that: a container's
 * menu registers a slot for every slot the inventory has - Sophisticated Storage's
 * {@code addStorageInventorySlots} loops to {@code getSlotCount()} - so every stack is
 * already on the client and the totals cover the whole container, visible or not.
 */
public final class EggHighlightRenderer {
    private static final int SHINY_COLOR = 0xFFFFC83C;
    private static final int HIGH_IV_COLOR = 0xFF4CE04C;
    private static final int SUMMARY_COLOR = 0xFFE0E0E0;
    private static final int SLOT_SIZE = 16;
    private static final int SUMMARY_OFFSET_Y = 11;

    private EggHighlightRenderer() {
    }

    public static void render(
            HandledScreen<?> screen,
            DrawContext context,
            int originX,
            int originY,
            int backgroundWidth,
            int backgroundHeight,
            ConfigManager.EggHighlight settings
    ) {
        if (screen == null || context == null || settings == null || !settings.enabled) {
            return;
        }
        int shiny = 0;
        int highIv = 0;
        int outlined = 0;
        for (Slot slot : screen.getScreenHandler().slots) {
            // The player's own inventory is shown in every container screen; counting it
            // would report eggs the container does not actually hold.
            if (slot.inventory instanceof PlayerInventory) {
                continue;
            }
            EggInspector.EggData egg = EggInspector.read(slot.getStack());
            if (egg == null || !egg.isNotable(settings)) {
                continue;
            }
            boolean asShiny = egg.shiny() && settings.highlightShiny;
            if (asShiny) {
                shiny++;
            } else {
                highIv++;
            }
            if (drawOutline(context, slot, originX, originY, backgroundWidth, backgroundHeight, asShiny)) {
                outlined++;
            }
        }
        drawSummary(context, originX, originY, shiny, highIv, shiny + highIv - outlined);
    }

    /** Returns false for a slot the screen is not currently drawing. */
    private static boolean drawOutline(
            DrawContext context,
            Slot slot,
            int originX,
            int originY,
            int backgroundWidth,
            int backgroundHeight,
            boolean asShiny
    ) {
        // A scrolled-out slot keeps stale coordinates, so skip anything outside the
        // screen's own background rather than painting an outline over the frame.
        if (!slot.isEnabled() || slot.x < 0 || slot.y < 0
                || slot.x + SLOT_SIZE > backgroundWidth || slot.y + SLOT_SIZE > backgroundHeight) {
            return false;
        }
        int color = asShiny ? SHINY_COLOR : HIGH_IV_COLOR;
        int x = originX + slot.x;
        int y = originY + slot.y;
        // Two rings: the inner one stays readable against a pale item, the outer one
        // against the slot's own border.
        context.drawBorder(x, y, SLOT_SIZE, SLOT_SIZE, color);
        context.drawBorder(x - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, color);
        return true;
    }

    /**
     * Symbols rather than words, so the line needs no translation: a star for shiny, a
     * diamond for a high average IV, and a double arrow for matches that are in the
     * container but scrolled out of view.
     */
    private static void drawSummary(
            DrawContext context,
            int originX,
            int originY,
            int shiny,
            int highIv,
            int hidden
    ) {
        if (shiny == 0 && highIv == 0) {
            return;
        }
        StringBuilder summary = new StringBuilder();
        if (shiny > 0) {
            summary.append("★ ").append(shiny).append("   ");
        }
        if (highIv > 0) {
            summary.append("◆ ").append(highIv).append("   ");
        }
        if (hidden > 0) {
            summary.append("↕ ").append(hidden);
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }
        context.drawTextWithShadow(
                client.textRenderer,
                summary.toString().strip(),
                originX,
                Math.max(2, originY - SUMMARY_OFFSET_Y),
                SUMMARY_COLOR
        );
    }
}
