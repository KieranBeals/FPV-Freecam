package com.kieran.fpvfreecam.flight;

import net.minecraft.client.Camera;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class DroneCameraRotationApplier {
    private static final Method CAMERA_SET_ROTATION_3 = resolveMethod("setRotation", float.class, float.class, float.class);
    private static final Method CAMERA_SET_ROTATION_2 = resolveMethod("setRotation", float.class, float.class);
    private static final Method CAMERA_LEGACY_ROTATION_APPLIER = resolveLegacyRotationApplier();

    private DroneCameraRotationApplier() {
    }

    public static void apply(final Camera camera, final DroneCameraAngles angles) {
        apply(camera, angles.yaw(), angles.pitch(), angles.roll());
    }

    public static void apply(final Camera camera, final float yaw, final float pitch, final float roll) {
        if (CAMERA_SET_ROTATION_3 != null) {
            try {
                CAMERA_SET_ROTATION_3.invoke(camera, yaw, pitch, roll);
                return;
            } catch (final IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        if (CAMERA_SET_ROTATION_2 != null && roll == 0.0F) {
            try {
                CAMERA_SET_ROTATION_2.invoke(camera, yaw, pitch);
                return;
            } catch (final IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        if (CAMERA_LEGACY_ROTATION_APPLIER == null) {
            return;
        }
        final DroneCameraAngles.LegacyRotation legacyRotation = DroneCameraAngles.legacyRotation(yaw, pitch, roll);
        try {
            final Object applied = CAMERA_LEGACY_ROTATION_APPLIER.invoke(null, camera, legacyRotation);
            if (applied instanceof final Boolean legacyApplied && legacyApplied) {
                return;
            }
        } catch (final IllegalAccessException | InvocationTargetException ignored) {
        }
        if (CAMERA_SET_ROTATION_2 != null) {
            try {
                CAMERA_SET_ROTATION_2.invoke(camera, yaw, pitch);
            } catch (final IllegalAccessException | InvocationTargetException ignored) {
            }
        }
    }

    private static Method resolveMethod(final String name, final Class<?>... args) {
        try {
            final Method method = Camera.class.getDeclaredMethod(name, args);
            method.setAccessible(true);
            return method;
        } catch (final NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveLegacyRotationApplier() {
        try {
            final Class<?> applierClass = Class.forName("com.kieran.fpvfreecam.fabric.CameraRotationLegacyApplier");
            final Method method = applierClass.getDeclaredMethod(
                    "apply",
                    Camera.class,
                    DroneCameraAngles.LegacyRotation.class
            );
            method.setAccessible(true);
            return method;
        } catch (final ClassNotFoundException | NoSuchMethodException | SecurityException ignored) {
            return null;
        }
    }

}
