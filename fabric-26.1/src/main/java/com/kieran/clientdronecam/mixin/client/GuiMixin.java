package com.kieran.clientdronecam.mixin.client;

import com.kieran.clientdronecam.ClientDroneCam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void clientdronecam$renderDroneOverlay(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker, final CallbackInfo ci) {
        if (ClientDroneCam.LIFECYCLE == null) {
            return;
        }

        final String overlayText = ClientDroneCam.LIFECYCLE.getOverlayText();
        if (overlayText.isEmpty()) {
            return;
        }

        ci.cancel();
        graphics.nextStratum();
        final int x = graphics.guiWidth() - this.minecraft.font.width(overlayText) - 6;
        final int y = 6;
        graphics.text(this.minecraft.font, overlayText, x, y, 0xFFFFFFFF, true);
    }
}
