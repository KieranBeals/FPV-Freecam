package com.kieran.fpvfreecam.mixin.client;

import com.kieran.fpvfreecam.FpvFreecam;
import com.kieran.fpvfreecam.flight.DroneNetworkSafetyGuard;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick(ZF)V", at = @At("RETURN"))
    private void fpvfreecam$zeroMovement(final CallbackInfo ci) {
        final boolean active = FpvFreecam.FLIGHT_CONTROLLER != null && FpvFreecam.FLIGHT_CONTROLLER.isActive();
        if (!DroneNetworkSafetyGuard.shouldSuppressPlayerInput(active)) {
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
