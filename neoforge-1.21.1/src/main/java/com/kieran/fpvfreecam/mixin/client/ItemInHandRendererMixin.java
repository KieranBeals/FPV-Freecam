package com.kieran.fpvfreecam.mixin.client;

import com.kieran.fpvfreecam.FpvFreecam;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(
            method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fpvfreecam$hideHands(final CallbackInfo ci) {
        if (FpvFreecam.FLIGHT_CONTROLLER != null && FpvFreecam.FLIGHT_CONTROLLER.isActive()) {
            ci.cancel();
        }
    }
}
