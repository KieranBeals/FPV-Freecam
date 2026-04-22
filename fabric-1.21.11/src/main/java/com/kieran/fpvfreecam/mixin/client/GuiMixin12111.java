package com.kieran.fpvfreecam.mixin.client;

import com.kieran.fpvfreecam.FpvFreecam;
import com.kieran.fpvfreecam.flight.DroneFlightController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin12111 {
    private static final int HUD_MARGIN_BOTTOM = 64;
    private static final int STICK_SIZE = 56;
    private static final int STICK_GAP = 8;
    private static final int STICK_BACKGROUND = 0x700A0A0A;
    private static final int STICK_BORDER = 0x80D0D0D0;
    private static final int STICK_CROSSHAIR = 0x70FFFFFF;
    private static final int STICK_DOT_LEFT = 0xE6F6F6F6;
    private static final int STICK_DOT_LEFT_CORE = 0xFFFFFFFF;
    private static final int STICK_DOT_RIGHT = 0xE060D9F4;
    private static final int STICK_DOT_RIGHT_CORE = 0xFFBDEFFF;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "renderOverlayMessage(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void fpvfreecam$renderDroneOverlay(final GuiGraphics graphics, final DeltaTracker deltaTracker, final CallbackInfo ci) {
        if (FpvFreecam.LIFECYCLE == null) {
            return;
        }

        final String overlayText = FpvFreecam.LIFECYCLE.getOverlayText();
        if (overlayText.isEmpty()) {
            return;
        }

        ci.cancel();
        final String[] lines = overlayText.split("\\n");
        int y = 6;
        for (final String line : lines) {
            final int x = graphics.guiWidth() - this.minecraft.font.width(line) - 6;
            graphics.drawString(this.minecraft.font, line, x, y, 0xFFFFFFFF, true);
            y += this.minecraft.font.lineHeight + 2;
        }

        final DroneFlightController.HudSnapshot snapshot = FpvFreecam.LIFECYCLE.getHudSnapshot();
        if (snapshot != null) {
            this.fpvfreecam$renderStickHud(graphics, snapshot);
        }
    }

    private void fpvfreecam$renderStickHud(final GuiGraphics graphics, final DroneFlightController.HudSnapshot snapshot) {
        final int totalWidth = STICK_SIZE * 2 + STICK_GAP;
        final int leftStickX = (graphics.guiWidth() - totalWidth) / 2;
        final int rightStickX = leftStickX + STICK_SIZE + STICK_GAP;
        final int stickY = graphics.guiHeight() - STICK_SIZE - HUD_MARGIN_BOTTOM;
        this.fpvfreecam$drawStickBox(
                graphics,
                leftStickX,
                stickY,
                snapshot.yawInput(),
                snapshot.throttleInput(),
                STICK_DOT_LEFT,
                STICK_DOT_LEFT_CORE
        );
        this.fpvfreecam$drawStickBox(
                graphics,
                rightStickX,
                stickY,
                snapshot.rollInput(),
                snapshot.pitchInput(),
                STICK_DOT_RIGHT,
                STICK_DOT_RIGHT_CORE
        );
    }

    private void fpvfreecam$drawStickBox(
            final GuiGraphics graphics,
            final int x,
            final int y,
            final float xInput,
            final float yInput,
            final int dotOuter,
            final int dotInner
    ) {
        final int right = x + STICK_SIZE;
        final int bottom = y + STICK_SIZE;
        final int centerX = x + STICK_SIZE / 2;
        final int centerY = y + STICK_SIZE / 2;

        graphics.fill(x, y, right, bottom, STICK_BACKGROUND);
        this.fpvfreecam$drawBorder(graphics, x, y, STICK_SIZE, STICK_SIZE, STICK_BORDER);

        graphics.fill(centerX, y + 2, centerX + 1, bottom - 2, STICK_CROSSHAIR);
        graphics.fill(x + 2, centerY, right - 2, centerY + 1, STICK_CROSSHAIR);

        final int range = STICK_SIZE / 2 - 4;
        final int dotX = centerX + Math.round(Mth.clamp(xInput, -1.0F, 1.0F) * range);
        final int dotY = centerY - Math.round(Mth.clamp(yInput, -1.0F, 1.0F) * range);

        this.fpvfreecam$drawDot(graphics, dotX, dotY, dotOuter, dotInner);
    }

    private void fpvfreecam$drawDot(final GuiGraphics graphics, final int x, final int y, final int outerColor, final int innerColor) {
        graphics.fill(x - 2, y - 2, x + 3, y + 3, outerColor);
        graphics.fill(x - 1, y - 1, x + 2, y + 2, innerColor);
    }

    private void fpvfreecam$drawBorder(final GuiGraphics graphics, final int x, final int y, final int width, final int height, final int color) {
        final int right = x + width;
        final int bottom = y + height;
        graphics.fill(x, y, right, y + 1, color);
        graphics.fill(x, bottom - 1, right, bottom, color);
        graphics.fill(x, y, x + 1, bottom, color);
        graphics.fill(right - 1, y, right, bottom, color);
    }
}
