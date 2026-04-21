package com.kieran.fpvfreecam.mixin.client;

import com.kieran.fpvfreecam.FpvFreecam;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private Entity entity;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Redirect(
            method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V")
    )
    private void fpvfreecam$setDetachedPosition(final Camera camera, final double x, final double y, final double z) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (FpvFreecam.LIFECYCLE != null) {
            FpvFreecam.LIFECYCLE.onFrameUpdate(minecraft);
        }
        if (FpvFreecam.FLIGHT_CONTROLLER == null || !FpvFreecam.FLIGHT_CONTROLLER.isActive() || this.entity != minecraft.player) {
            this.setPosition(x, y, z);
            return;
        }

        final Vec3 dronePosition = FpvFreecam.FLIGHT_CONTROLLER.getRenderCameraPosition(1.0F);
        if (dronePosition == null) {
            this.setPosition(x, y, z);
            return;
        }

        this.setPosition(dronePosition.x, dronePosition.y, dronePosition.z);
    }

    @Inject(method = "isDetached()Z", at = @At("HEAD"), cancellable = true)
    private void fpvfreecam$detachDroneCamera(final CallbackInfoReturnable<Boolean> cir) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (FpvFreecam.FLIGHT_CONTROLLER != null && FpvFreecam.FLIGHT_CONTROLLER.isActive() && this.entity == minecraft.player) {
            cir.setReturnValue(true);
        }
    }
}
