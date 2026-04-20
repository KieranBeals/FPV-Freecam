package com.kieran.clientdronecam.mixin.client;

import com.kieran.clientdronecam.ClientDroneCam;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick(ZF)V", at = @At("RETURN"))
    private void clientdronecam$zeroMovement(final CallbackInfo ci) {
        if (ClientDroneCam.FLIGHT_CONTROLLER == null || !ClientDroneCam.FLIGHT_CONTROLLER.isActive()) {
            return;
        }

        this.leftImpulse = 0.0F;
        this.forwardImpulse = 0.0F;
        this.up = false;
        this.down = false;
        this.left = false;
        this.right = false;
        this.jumping = false;
        this.shiftKeyDown = false;
    }
}
