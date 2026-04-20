package com.kieran.clientdronecam.flight;

import org.joml.Quaternionf;

public record DroneCameraAngles(float yaw, float pitch, float roll) {

    public LegacyRotation legacyRotation() {
        return legacyRotation(this);
    }

    public static LegacyRotation legacyRotation(final float yawDeg, final float pitchDeg, final float rollDeg) {
        final float yawRadians = (float) Math.toRadians(yawDeg);
        final float pitchRadians = (float) Math.toRadians(pitchDeg);
        final float rollRadians = (float) Math.toRadians(rollDeg);

        final float legacyYawRadians = (float) Math.PI - yawRadians;
        final float legacyPitchRadians = -pitchRadians;
        final float legacyRollRadians = -rollRadians;

        return new LegacyRotation(
                yawDeg,
                pitchDeg,
                rollDeg,
                legacyYawRadians,
                legacyPitchRadians,
                legacyRollRadians,
                new Quaternionf().rotationYXZ(legacyYawRadians, legacyPitchRadians, legacyRollRadians)
        );
    }

    public static LegacyRotation legacyRotation(final DroneCameraAngles angles) {
        return legacyRotation(angles.yaw(), angles.pitch(), angles.roll());
    }

    public record LegacyRotation(
            float yawDeg,
            float pitchDeg,
            float rollDeg,
            float yawRadians,
            float pitchRadians,
            float rollRadians,
            Quaternionf rotation
    ) {
    }
}
