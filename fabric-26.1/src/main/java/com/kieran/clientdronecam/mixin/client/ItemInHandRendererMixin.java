package com.kieran.clientdronecam.mixin.client;

import com.kieran.clientdronecam.ClientDroneCam;
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
    private void clientdronecam$hideHands(final CallbackInfo ci) {
        if (ClientDroneCam.FLIGHT_CONTROLLER != null && ClientDroneCam.FLIGHT_CONTROLLER.isActive()) {
            ci.cancel();
        }
    }
}
