package com.kieran.clientdronecam.mixin.client;

import com.kieran.clientdronecam.ClientDroneCam;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
    private void clientdronecam$setDetachedPosition(final Camera camera, final double x, final double y, final double z) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (ClientDroneCam.FLIGHT_CONTROLLER == null || !ClientDroneCam.FLIGHT_CONTROLLER.isActive() || this.entity != minecraft.player) {
            this.setPosition(x, y, z);
            return;
        }

        final Vec3 dronePosition = ClientDroneCam.FLIGHT_CONTROLLER.getRenderCameraPosition();
        if (dronePosition == null) {
            this.setPosition(x, y, z);
            return;
        }

        this.setPosition(dronePosition.x, dronePosition.y, dronePosition.z);
    }
}
