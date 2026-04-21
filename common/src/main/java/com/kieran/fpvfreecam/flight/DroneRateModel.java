package com.kieran.fpvfreecam.flight;

import com.kieran.fpvfreecam.config.DroneConfig;

public final class DroneRateModel {
    public AxisRates desiredRatesDegPerSecond(final DroneConfig.RateProfile profile, final float rollInput, final float pitchInput, final float yawInput) {
        return new AxisRates(
                axisRateDegPerSecond(rollInput, profile.rollRcRate, profile.rollSuperRate, profile.rollExpo),
                axisRateDegPerSecond(pitchInput, profile.pitchRcRate, profile.pitchSuperRate, profile.pitchExpo),
                axisRateDegPerSecond(yawInput, profile.yawRcRate, profile.yawSuperRate, profile.yawExpo)
        );
    }

    public float maxRateDegPerSecond(final float rcRate, final float superRate, final float expo) {
        return Math.abs(axisRateDegPerSecond(1.0F, rcRate, superRate, expo));
    }

    public static float axisRateDegPerSecond(final float input, final float rcRate, final float superRate, final float expo) {
        final float clampedInput = clamp(input, -1.0F, 1.0F);
        final float absolute = Math.abs(clampedInput);

        final float expoShaped = clampedInput * (1.0F - expo)
                + clampedInput * absolute * absolute * expo;

        final float baseRate = 200.0F * rcRate * expoShaped;
        final float denominator = Math.max(0.02F, 1.0F - absolute * superRate);
        return baseRate / denominator;
    }

    private static float clamp(final float value, final float min, final float max) {
        return Math.max(min, Math.min(max, value));
    }

    public record AxisRates(float roll, float pitch, float yaw) {
    }
}
