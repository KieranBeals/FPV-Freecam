package com.kieran.fpvfreecam.flight;

import com.kieran.fpvfreecam.config.DroneConfig;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneSimulationMathTest {
    @Test
    void rateCurveIsSymmetricAndNonlinear() {
        final float zero = DroneRateModel.axisRateDegPerSecond(0.0F, 1.15F, 0.72F, 0.15F);
        final float half = DroneRateModel.axisRateDegPerSecond(0.5F, 1.15F, 0.72F, 0.15F);
        final float negativeHalf = DroneRateModel.axisRateDegPerSecond(-0.5F, 1.15F, 0.72F, 0.15F);
        final float max = DroneRateModel.axisRateDegPerSecond(1.0F, 1.15F, 0.72F, 0.15F);

        assertEquals(0.0F, zero, 1.0E-6F);
        assertEquals(-half, negativeHalf, 1.0E-4F);
        assertTrue(Math.abs(max) > Math.abs(half));
    }

    @Test
    void throttleCurveIsMonotonic() {
        final DroneConfig.ThrottleProfile throttleProfile = new DroneConfig.ThrottleProfile();
        throttleProfile.throttleMid = 0.34F;
        throttleProfile.throttleExpo = 0.22F;

        float previous = Float.NEGATIVE_INFINITY;
        for (int index = 0; index <= 1000; index++) {
            final float input = index / 1000.0F;
            final float output = DronePhysicsModel.throttleCurve(input, throttleProfile);
            assertTrue(output + 1.0E-6F >= previous, "Throttle curve must be monotonic");
            previous = output;
        }
    }

    @Test
    void motorLagRiseAndFallBehavesAsymmetric() {
        final double dt = 1.0D / 240.0D;
        final float riseStep = DronePhysicsModel.simulateMotorSpoolStep(0.0F, 1.0F, 0.060F, 0.085F, dt);
        final float fallStep = 1.0F - DronePhysicsModel.simulateMotorSpoolStep(1.0F, 0.0F, 0.060F, 0.085F, dt);

        assertTrue(riseStep > fallStep);

        float throttle = 0.0F;
        for (int index = 0; index < 240; index++) {
            throttle = DronePhysicsModel.simulateMotorSpoolStep(throttle, 1.0F, 0.060F, 0.085F, dt);
        }
        assertTrue(throttle > 0.99F);

        for (int index = 0; index < 24; index++) {
            throttle = DronePhysicsModel.simulateMotorSpoolStep(throttle, 0.0F, 0.060F, 0.085F, dt);
        }
        assertTrue(throttle < 0.99F);
        assertTrue(throttle > 0.0F);
    }

    @Test
    void batterySagAccumulatesAndRecovers() {
        final DroneConfig.RealismProfile realismProfile = new DroneConfig.RealismProfile();
        realismProfile.batterySagStrength = 0.55F;
        realismProfile.batterySagMaxLoss = 0.12F;
        realismProfile.sagRecoverySeconds = 1.8F;

        final double dt = 1.0D / 240.0D;
        float sag = 0.0F;
        for (int index = 0; index < 480; index++) {
            sag = DronePhysicsModel.simulateBatterySagStep(sag, 1.0F, 0.75F, realismProfile, dt);
        }
        final float loadedSag = sag;

        for (int index = 0; index < 720; index++) {
            sag = DronePhysicsModel.simulateBatterySagStep(sag, 0.0F, 0.0F, realismProfile, dt);
        }

        assertTrue(loadedSag > 0.0F);
        assertTrue(sag < loadedSag);
    }

    @Test
    void crashModelDistinguishesGlanceAndHardImpact() {
        final DroneCrashModel crashModel = new DroneCrashModel();
        final DroneConfig.CrashSettings crashSettings = new DroneConfig.CrashSettings();
        crashSettings.lowSpeedGlanceThreshold = 3.0F;
        crashSettings.hardImpactSpeedThreshold = 8.5F;
        crashSettings.hardImpactEnergyThreshold = 40.0F;

        final DroneCrashModel.CollisionOutcome glance = crashModel.classifyCollision(
                new Vec3(2.0D, 0.0D, 0.0D),
                new Vec3(0.1D, 0.0D, 0.0D),
                Vec3.ZERO,
                0.05D,
                crashSettings
        );
        assertTrue(glance.glanced());
        assertFalse(glance.crashed());

        final DroneCrashModel.CollisionOutcome hardCrash = crashModel.classifyCollision(
                new Vec3(10.0D, 0.0D, 0.0D),
                new Vec3(0.5D, 0.0D, 0.0D),
                Vec3.ZERO,
                0.05D,
                crashSettings
        );
        assertTrue(hardCrash.crashed());
    }

    @Test
    void descentWashOnlyAppearsOnFastVerticalDrops() {
        final Vec3 right = new Vec3(1.0D, 0.0D, 0.0D);
        final Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        final Vec3 forward = new Vec3(0.0D, 0.0D, 1.0D);
        final float strength = 0.35F;

        final float climb = DronePhysicsModel.computeDescentWashFactor(
                new Vec3(0.0D, 3.0D, 0.0D),
                right,
                up,
                forward,
                0.8F,
                strength
        );
        final float slowDrop = DronePhysicsModel.computeDescentWashFactor(
                new Vec3(0.0D, -3.0D, 0.0D),
                right,
                up,
                forward,
                0.8F,
                strength
        );
        final float highForwardSpeed = DronePhysicsModel.computeDescentWashFactor(
                new Vec3(0.0D, -12.0D, 20.0D),
                right,
                up,
                forward,
                0.8F,
                strength
        );
        final float fastDrop = DronePhysicsModel.computeDescentWashFactor(
                new Vec3(0.0D, -12.0D, 2.0D),
                right,
                up,
                forward,
                0.8F,
                strength
        );

        assertEquals(0.0F, climb, 1.0E-6F);
        assertEquals(0.0F, slowDrop, 1.0E-6F);
        assertEquals(0.0F, highForwardSpeed, 1.0E-6F);
        assertTrue(fastDrop > 0.0F);
    }

    @Test
    void noCollisionModeIsSelectableInConfig() {
        final DroneConfig.CrashSettings crashSettings = new DroneConfig.CrashSettings();
        crashSettings.crashResetMode = DroneConfig.CrashResetMode.NO_COLLISION;

        assertEquals(DroneConfig.CrashResetMode.NO_COLLISION, crashSettings.crashResetMode);
    }
}
