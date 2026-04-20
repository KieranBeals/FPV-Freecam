package com.kieran.clientdronecam.mixin.client;

import com.kieran.clientdronecam.ClientDroneCam;
import com.kieran.clientdronecam.flight.DroneCameraAngles;
import com.kieran.clientdronecam.flight.DroneCameraRotationApplier;
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
    private void clientdronecam$applyDroneCameraBeforeFrustum(final DeltaTracker deltaTracker, final CallbackInfo ci) {
        // Must run before frustum setup: culling uses the matrix/frustum prepared during Camera.update.
        final Minecraft minecraft = Minecraft.getInstance();
        this.clientdronecam$applyDroneCamera(minecraft);
    }

    private void clientdronecam$applyDroneCamera(final Minecraft minecraft) {
        if (ClientDroneCam.LIFECYCLE != null) {
            ClientDroneCam.LIFECYCLE.onFrameUpdate(minecraft);
        }
        if (ClientDroneCam.FLIGHT_CONTROLLER == null || !ClientDroneCam.FLIGHT_CONTROLLER.isActive() || this.entity != minecraft.player) {
            return;
        }

        final Vec3 dronePosition = ClientDroneCam.FLIGHT_CONTROLLER.getRenderCameraPosition(1.0F);
        if (dronePosition == null) {
            return;
        }

        this.setPosition(dronePosition.x, dronePosition.y, dronePosition.z);
        final DroneCameraAngles cameraAngles = ClientDroneCam.FLIGHT_CONTROLLER.getCameraAngles();
        DroneCameraRotationApplier.apply((Camera) (Object) this, cameraAngles);
    }

    @Inject(method = "isDetached()Z", at = @At("HEAD"), cancellable = true)
    private void clientdronecam$detachDroneCamera(final CallbackInfoReturnable<Boolean> cir) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (ClientDroneCam.FLIGHT_CONTROLLER != null && ClientDroneCam.FLIGHT_CONTROLLER.isActive() && this.entity == minecraft.player) {
            cir.setReturnValue(true);
        }
    }

}
