package com.kieran.fpvfreecam.flight;

import com.kieran.fpvfreecam.config.DroneConfig;

public final class DroneProfileDefaults {
    public static final float CAMERA_ANGLE_DEG = 30.0F;
    public static final float DRONE_MASS_KG = 1.0F;

    public static final float ROLL_RC_RATE = 1.15F;
    public static final float ROLL_SUPER_RATE = 0.72F;
    public static final float ROLL_EXPO = 0.15F;

    public static final float PITCH_RC_RATE = 1.10F;
    public static final float PITCH_SUPER_RATE = 0.70F;
    public static final float PITCH_EXPO = 0.15F;

    public static final float YAW_RC_RATE = 0.95F;
    public static final float YAW_SUPER_RATE = 0.62F;
    public static final float YAW_EXPO = 0.10F;

    public static final float THROTTLE_MID = 0.34F;
    public static final float THROTTLE_EXPO = 0.22F;

    public static final float THRUST_TO_WEIGHT = 6.2F;
    public static final float MOTOR_SPOOL_UP_SECONDS = 0.055F;
    public static final float MOTOR_SPOOL_DOWN_SECONDS = 0.080F;

    public static final float ROLL_RESPONSE_SECONDS = 0.044F;
    public static final float PITCH_RESPONSE_SECONDS = 0.047F;
    public static final float YAW_RESPONSE_SECONDS = 0.070F;

    public static final float FORWARD_DRAG = 0.034F;
    public static final float SIDE_DRAG = 0.150F;
    public static final float VERTICAL_DRAG = 0.072F;

    public static final float BATTERY_SAG_STRENGTH = 0.55F;
    public static final float BATTERY_SAG_MAX_LOSS = 0.12F;
    public static final float SAG_RECOVERY_SECONDS = 1.80F;
    public static final float DESCENT_WASH_STRENGTH = 0.35F;
    public static final float LOAD_IMPERFECTION_STRENGTH = 0.30F;

    public static final float GLANCING_IMPACT_SPEED = 3.0F;
    public static final float HARD_IMPACT_SPEED = 8.5F;
    public static final float HARD_IMPACT_ENERGY = 40.0F;

    private DroneProfileDefaults() {
    }

    public static float defaultAxisResponseSeconds(final Axis axis) {
        return switch (axis) {
            case ROLL -> ROLL_RESPONSE_SECONDS;
            case PITCH -> PITCH_RESPONSE_SECONDS;
            case YAW -> YAW_RESPONSE_SECONDS;
        };
    }

    public static float defaultMaxAxisAcceleration(final Axis axis) {
        return switch (axis) {
            case ROLL -> 8500.0F;
            case PITCH -> 7800.0F;
            case YAW -> 5000.0F;
        };
    }

    public static void applyFiveInchFreestyle(final DroneConfig config) {
        config.rateProfile.rollRcRate = ROLL_RC_RATE;
        config.rateProfile.rollSuperRate = ROLL_SUPER_RATE;
        config.rateProfile.rollExpo = ROLL_EXPO;
        config.rateProfile.pitchRcRate = PITCH_RC_RATE;
        config.rateProfile.pitchSuperRate = PITCH_SUPER_RATE;
        config.rateProfile.pitchExpo = PITCH_EXPO;
        config.rateProfile.yawRcRate = YAW_RC_RATE;
        config.rateProfile.yawSuperRate = YAW_SUPER_RATE;
        config.rateProfile.yawExpo = YAW_EXPO;

        config.throttleProfile.throttleMid = THROTTLE_MID;
        config.throttleProfile.throttleExpo = THROTTLE_EXPO;

        config.craftProfile.cameraAngleDeg = CAMERA_ANGLE_DEG;
        config.craftProfile.thrustToWeight = THRUST_TO_WEIGHT;
        config.craftProfile.massKg = DRONE_MASS_KG;
        config.craftProfile.motorSpoolUpSeconds = MOTOR_SPOOL_UP_SECONDS;
        config.craftProfile.motorSpoolDownSeconds = MOTOR_SPOOL_DOWN_SECONDS;
        config.craftProfile.rollResponseSeconds = ROLL_RESPONSE_SECONDS;
        config.craftProfile.pitchResponseSeconds = PITCH_RESPONSE_SECONDS;
        config.craftProfile.yawResponseSeconds = YAW_RESPONSE_SECONDS;
        config.craftProfile.forwardDrag = FORWARD_DRAG;
        config.craftProfile.sideDrag = SIDE_DRAG;
        config.craftProfile.verticalDrag = VERTICAL_DRAG;

        config.realismProfile.batterySagStrength = BATTERY_SAG_STRENGTH;
        config.realismProfile.batterySagMaxLoss = BATTERY_SAG_MAX_LOSS;
        config.realismProfile.sagRecoverySeconds = SAG_RECOVERY_SECONDS;
        config.realismProfile.descentWashStrength = DESCENT_WASH_STRENGTH;
        config.realismProfile.loadImperfectionStrength = LOAD_IMPERFECTION_STRENGTH;

        config.crashSettings.lowSpeedGlanceThreshold = GLANCING_IMPACT_SPEED;
        config.crashSettings.hardImpactSpeedThreshold = HARD_IMPACT_SPEED;
        config.crashSettings.hardImpactEnergyThreshold = HARD_IMPACT_ENERGY;
        config.crashSettings.crashResetMode = DroneConfig.CrashResetMode.EXIT_TO_PLAYER;
    }

    public enum Axis {
        ROLL,
        PITCH,
        YAW
    }
}
