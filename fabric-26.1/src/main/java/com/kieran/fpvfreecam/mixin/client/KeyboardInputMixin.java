package com.kieran.fpvfreecam.mixin.client;

import com.kieran.fpvfreecam.FpvFreecam;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick()V", at = @At("RETURN"))
    private void fpvfreecam$zeroMovement(final CallbackInfo ci) {
        if (FpvFreecam.FLIGHT_CONTROLLER == null || !FpvFreecam.FLIGHT_CONTROLLER.isActive()) {
            return;
        }

        this.moveVector = Vec2.ZERO;
        this.keyPresses = Input.EMPTY;
    }
}
