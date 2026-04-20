package com.kieran.clientdronecam.mixin.client;

import com.kieran.clientdronecam.ClientDroneCam;
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
import org.joml.Quaternionf;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Mixin(Camera.class)
public abstract class CameraMixin {
    private static final Method CAMERA_SET_ROTATION_3 = resolveMethod("setRotation", float.class, float.class, float.class);
    private static final Method CAMERA_SET_ROTATION_2 = resolveMethod("setRotation", float.class, float.class);
    private static final Field CAMERA_ROTATION = resolveField("rotation", Quaternionf.class);

    @Shadow
    private Entity entity;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    private static Method resolveMethod(final String name, final Class<?>... args) {
        try {
            final Method method = Camera.class.getDeclaredMethod(name, args);
            method.setAccessible(true);
            return method;
        } catch (final NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Field resolveField(final String name, final Class<?> expectedType) {
        try {
            final Field field = Camera.class.getDeclaredField(name);
            field.setAccessible(true);
            if (expectedType.isAssignableFrom(field.getType())) {
                return field;
            }
        } catch (final NoSuchFieldException ignored) {
        }
        return null;
    }

    @Inject(
            method = "update(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("RETURN")
    )
    private void clientdronecam$setDetachedPosition(final DeltaTracker deltaTracker, final CallbackInfo ci) {
        final Minecraft minecraft = Minecraft.getInstance();
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
        final float droneYaw = ClientDroneCam.FLIGHT_CONTROLLER.getCameraYaw();
        final float dronePitch = ClientDroneCam.FLIGHT_CONTROLLER.getCameraPitch();
        final float droneRoll = ClientDroneCam.FLIGHT_CONTROLLER.getCameraRoll();
        this.setDroneRotation(droneYaw, dronePitch, droneRoll);
    }

    @Inject(method = "isDetached()Z", at = @At("HEAD"), cancellable = true)
    private void clientdronecam$detachDroneCamera(final CallbackInfoReturnable<Boolean> cir) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (ClientDroneCam.FLIGHT_CONTROLLER != null && ClientDroneCam.FLIGHT_CONTROLLER.isActive() && this.entity == minecraft.player) {
            cir.setReturnValue(true);
        }
    }

    private void setDroneRotation(final float yaw, final float pitch, final float roll) {
        if (CAMERA_SET_ROTATION_3 != null) {
            try {
                CAMERA_SET_ROTATION_3.invoke(this, yaw, pitch, roll);
                return;
            } catch (final IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        if (CAMERA_SET_ROTATION_2 != null) {
            try {
                CAMERA_SET_ROTATION_2.invoke(this, yaw, pitch);
            } catch (final IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        if (CAMERA_ROTATION == null) {
            return;
        }
        try {
            final Object fieldValue = CAMERA_ROTATION.get(this);
            if (fieldValue instanceof final Quaternionf quaternion) {
                quaternion.rotationYXZ(
                        (float) Math.toRadians(yaw),
                        (float) Math.toRadians(pitch),
                        (float) Math.toRadians(roll)
                );
            }
        } catch (final IllegalAccessException ignored) {
        }
    }
}
