package com.kieran.fpvfreecam.mixin.client;

import com.kieran.fpvfreecam.FpvFreecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin12111 {
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
        final int x = graphics.guiWidth() - this.minecraft.font.width(overlayText) - 6;
        final int y = 6;
        graphics.drawString(this.minecraft.font, overlayText, x, y, 0xFFFFFFFF, true);
    }
}
