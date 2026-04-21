package com.kieran.fpvfreecam.mixin.client;

import com.kieran.fpvfreecam.FpvFreecam;
import com.kieran.fpvfreecam.flight.DroneCameraAngles;
import com.kieran.fpvfreecam.flight.DroneCameraRotationApplier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private Entity entity;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(
            method = "update(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;getViewRotationMatrix(Lorg/joml/Matrix4f;)Lorg/joml/Matrix4f;"
            )
    )
    private void fpvfreecam$applyDroneCameraBeforeFrustum(final DeltaTracker deltaTracker, final CallbackInfo ci) {
        // Must run before frustum setup: culling uses the matrix/frustum prepared during Camera.update.
        final Minecraft minecraft = Minecraft.getInstance();
        this.fpvfreecam$applyDroneCamera(minecraft);
    }

    private void fpvfreecam$applyDroneCamera(final Minecraft minecraft) {
        if (FpvFreecam.LIFECYCLE != null) {
            FpvFreecam.LIFECYCLE.onFrameUpdate(minecraft);
        }
        if (FpvFreecam.FLIGHT_CONTROLLER == null || !FpvFreecam.FLIGHT_CONTROLLER.isActive() || this.entity != minecraft.player) {
            return;
        }

        final Vec3 dronePosition = FpvFreecam.FLIGHT_CONTROLLER.getRenderCameraPosition(1.0F);
        if (dronePosition == null) {
            return;
        }

        this.setPosition(dronePosition.x, dronePosition.y, dronePosition.z);
        final DroneCameraAngles cameraAngles = FpvFreecam.FLIGHT_CONTROLLER.getCameraAngles();
        DroneCameraRotationApplier.apply((Camera) (Object) this, cameraAngles);
    }

    @Inject(method = "isDetached()Z", at = @At("HEAD"), cancellable = true)
    private void fpvfreecam$detachDroneCamera(final CallbackInfoReturnable<Boolean> cir) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (FpvFreecam.FLIGHT_CONTROLLER != null && FpvFreecam.FLIGHT_CONTROLLER.isActive() && this.entity == minecraft.player) {
            cir.setReturnValue(true);
        }
    }

}
