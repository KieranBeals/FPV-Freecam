package com.kieran.fpvfreecam.flight;

import com.kieran.fpvfreecam.config.DroneConfig;
import net.minecraft.world.phys.Vec3;

public final class DroneCrashModel {
    private static final double MIN_BLOCKED_DISTANCE = 1.0E-7D;

    public CollisionOutcome classifyCollision(
            final Vec3 velocityBefore,
            final Vec3 attemptedMove,
            final Vec3 allowedMove,
            final double dt,
            final DroneConfig.CrashSettings crashSettings
    ) {
        final Vec3 blockedMove = attemptedMove.subtract(allowedMove);
        final double blockedDistance = blockedMove.length();
        if (blockedDistance < MIN_BLOCKED_DISTANCE || dt <= 0.0D) {
            return CollisionOutcome.noCollision(velocityBefore);
        }

        final Vec3 impactDirection = blockedMove.normalize();
        final double impactSpeed = Math.max(0.0D, velocityBefore.dot(impactDirection));
        final double impactEnergy = 0.5D * impactSpeed * impactSpeed;

        final boolean hardCrash = impactSpeed >= crashSettings.hardImpactSpeedThreshold
                || impactEnergy >= crashSettings.hardImpactEnergyThreshold;
        if (hardCrash) {
            return new CollisionOutcome(true, false, Vec3.ZERO, (float) impactSpeed, (float) impactEnergy);
        }

        final boolean glance = impactSpeed <= crashSettings.lowSpeedGlanceThreshold;
        if (!glance) {
            return new CollisionOutcome(true, false, Vec3.ZERO, (float) impactSpeed, (float) impactEnergy);
        }

        Vec3 correctedVelocity = velocityBefore;
        final double intoSurface = correctedVelocity.dot(impactDirection);
        if (intoSurface > 0.0D) {
            correctedVelocity = correctedVelocity.subtract(impactDirection.scale(intoSurface));
        }

        // Keep some tangential momentum so light grazes feel like scrapes, not hard stops.
        correctedVelocity = correctedVelocity.scale(0.92D);
        return new CollisionOutcome(false, true, correctedVelocity, (float) impactSpeed, (float) impactEnergy);
    }

    public record CollisionOutcome(
            boolean crashed,
            boolean glanced,
            Vec3 velocityAfterCollision,
            float impactSpeed,
            float impactEnergy
    ) {
        private static CollisionOutcome noCollision(final Vec3 velocity) {
            return new CollisionOutcome(false, false, velocity, 0.0F, 0.0F);
        }
    }
}
