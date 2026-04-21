package com.kieran.fpvfreecam.fabric;

import com.kieran.fpvfreecam.flight.DroneCameraAngles;
import net.minecraft.client.Camera;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;

public final class CameraRotationLegacyApplier {
    private static final Field CAMERA_X_ROT = resolveField("xRot", float.class);
    private static final Field CAMERA_Y_ROT = resolveField("yRot", float.class);
    private static final Field CAMERA_ROTATION = resolveField("rotation", Quaternionf.class);
    private static final Field CAMERA_FORWARDS = resolveField("forwards", Vector3f.class);
    private static final Field CAMERA_UP = resolveField("up", Vector3f.class);
    private static final Field CAMERA_LEFT = resolveField("left", Vector3f.class);
    private static final Field CAMERA_MATRIX_PROPERTIES_DIRTY = resolveField("matrixPropertiesDirty", int.class);

    private static final Vector3f BASE_FORWARD = new Vector3f(0.0F, 0.0F, -1.0F);
    private static final Vector3f BASE_UP = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Vector3f BASE_LEFT = new Vector3f(-1.0F, 0.0F, 0.0F);

    private CameraRotationLegacyApplier() {
    }

    public static boolean apply(final Camera camera, final DroneCameraAngles.LegacyRotation transform) {
        if (camera == null || transform == null
            || CAMERA_X_ROT == null
            || CAMERA_Y_ROT == null
            || CAMERA_ROTATION == null
            || CAMERA_FORWARDS == null
            || CAMERA_UP == null
            || CAMERA_LEFT == null) {
            return false;
        }

        try {
            CAMERA_Y_ROT.setFloat(camera, transform.yawDeg());
            CAMERA_X_ROT.setFloat(camera, transform.pitchDeg());

            final Quaternionf rotation = (Quaternionf) CAMERA_ROTATION.get(camera);
            if (rotation == null) {
                return false;
            }
            rotation.set(transform.rotation());

            final Vector3f cameraForwards = (Vector3f) CAMERA_FORWARDS.get(camera);
            if (cameraForwards != null) {
                cameraForwards.set(
                    rotateCopy(BASE_FORWARD, rotation)
                );
            }
            final Vector3f cameraUp = (Vector3f) CAMERA_UP.get(camera);
            if (cameraUp != null) {
                cameraUp.set(
                    rotateCopy(BASE_UP, rotation)
                );
            }
            final Vector3f cameraLeft = (Vector3f) CAMERA_LEFT.get(camera);
            if (cameraLeft != null) {
                cameraLeft.set(
                    rotateCopy(BASE_LEFT, rotation)
                );
            }

            if (CAMERA_MATRIX_PROPERTIES_DIRTY != null) {
                final int matrixPropertiesDirty = CAMERA_MATRIX_PROPERTIES_DIRTY.getInt(camera);
                CAMERA_MATRIX_PROPERTIES_DIRTY.setInt(
                        camera,
                        matrixPropertiesDirty | 3
                );
            }
            return true;
        } catch (final IllegalAccessException ignored) {
            return false;
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

    private static Vector3f rotateCopy(final Vector3f vector, final Quaternionf rotation) {
        final Vector3f rotated = new Vector3f(vector);
        rotated.rotate(rotation);
        return rotated;
    }

}
