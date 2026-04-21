package com.kieran.fpvfreecam.flight;

import com.kieran.fpvfreecam.config.DroneConfig;
import com.kieran.fpvfreecam.input.DroneInputMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;

import java.util.List;

public final class DronePhysicsModel {
    private static final double GRAVITY = 9.81D;
    private static final double HALF_BOX_SIZE = 0.175D;
    private static final float INPUT_SMOOTHING_SECONDS = 0.050F;
    private static final float AIR_MODE_AUTHORITY_FLOOR = 0.35F;
    private static final float ROLL_MAX_ACCEL_DPS2 = DroneProfileDefaults.defaultMaxAxisAcceleration(DroneProfileDefaults.Axis.ROLL);
    private static final float PITCH_MAX_ACCEL_DPS2 = DroneProfileDefaults.defaultMaxAxisAcceleration(DroneProfileDefaults.Axis.PITCH);
    private static final float YAW_MAX_ACCEL_DPS2 = DroneProfileDefaults.defaultMaxAxisAcceleration(DroneProfileDefaults.Axis.YAW);

    private final DroneRateModel rateModel;
    private final DroneCrashModel crashModel;

    public DronePhysicsModel(final DroneRateModel rateModel, final DroneCrashModel crashModel) {
        this.rateModel = rateModel;
        this.crashModel = crashModel;
    }

    public StepOutcome step(
            final DroneSimulationState state,
            final DroneConfig config,
            final DroneInputMapper.PollResult input,
            final Entity collisionEntity,
            final Level level,
            final double dt
    ) {
        final float smoothedThrottle = smooth(state.filteredThrottle(), input.throttle(), dt, INPUT_SMOOTHING_SECONDS);
        final float smoothedYaw = smooth(state.filteredYaw(), input.yaw(), dt, INPUT_SMOOTHING_SECONDS);
        final float smoothedPitch = smooth(state.filteredPitch(), input.pitch(), dt, INPUT_SMOOTHING_SECONDS);
        final float smoothedRoll = smooth(state.filteredRoll(), input.roll(), dt, INPUT_SMOOTHING_SECONDS);

        state.setFilteredThrottle(smoothedThrottle);
        state.setFilteredYaw(smoothedYaw);
        state.setFilteredPitch(smoothedPitch);
        state.setFilteredRoll(smoothedRoll);

        final float throttleInput = (smoothedThrottle + 1.0F) * 0.5F;
        final float throttleCurve = throttleCurve(throttleInput, config.throttleProfile);
        final float motorThrottle = simulateMotorSpoolStep(
                state.motorThrottle(),
                throttleCurve,
                config.craftProfile.motorSpoolUpSeconds,
                config.craftProfile.motorSpoolDownSeconds,
                dt
        );
        state.setMotorThrottle(motorThrottle);

        final double angularLoad = Math.min(1.0D, (
                Math.abs(state.rollRateDegPerSecond())
                        + Math.abs(state.pitchRateDegPerSecond())
                        + Math.abs(state.yawRateDegPerSecond())
        ) / 2400.0D);
        final float sagLoss = simulateBatterySagStep(
                state.batterySagLoss(),
                motorThrottle,
                (float) angularLoad,
                config.realismProfile,
                dt
        );
        state.setBatterySagLoss(sagLoss);

        final DroneRateModel.AxisRates desiredRates = this.rateModel.desiredRatesDegPerSecond(
                config.rateProfile,
                smoothedRoll,
                -smoothedPitch,
                smoothedYaw
        );

        final float authority = Math.max(AIR_MODE_AUTHORITY_FLOOR, motorThrottle)
                * (1.0F - sagLoss * 0.60F);

        final float desiredRollRate = desiredRates.roll() * authority;
        final float desiredPitchRate = desiredRates.pitch() * authority;
        final float desiredYawRate = desiredRates.yaw() * authority;
        state.setDesiredRollRateDegPerSecond(desiredRollRate);
        state.setDesiredPitchRateDegPerSecond(desiredPitchRate);
        state.setDesiredYawRateDegPerSecond(desiredYawRate);

        float rollRate = updateAxisRate(
                state.rollRateDegPerSecond(),
                desiredRollRate,
                config.craftProfile.rollResponseSeconds,
                ROLL_MAX_ACCEL_DPS2,
                dt
        );
        float pitchRate = updateAxisRate(
                state.pitchRateDegPerSecond(),
                desiredPitchRate,
                config.craftProfile.pitchResponseSeconds,
                PITCH_MAX_ACCEL_DPS2,
                dt
        );
        float yawRate = updateAxisRate(
                state.yawRateDegPerSecond(),
                desiredYawRate,
                config.craftProfile.yawResponseSeconds,
                YAW_MAX_ACCEL_DPS2,
                dt
        );

        final Vec3 right = state.rightVector();
        final Vec3 up = state.upVector();
        final Vec3 forward = state.forwardVector();

        final float washFactor = computeDescentWashFactor(
                state.velocity(),
                right,
                up,
                forward,
                motorThrottle,
                config.realismProfile.descentWashStrength
        );

        final float trackingError = (float) Math.min(1.0D, (
                Math.abs(desiredRollRate - rollRate)
                        + Math.abs(desiredPitchRate - pitchRate)
                        + Math.abs(desiredYawRate - yawRate)
        ) / 1100.0D);
        final float dynamicImperfection = Mth.clamp(trackingError * 0.90F + (float) angularLoad * 0.20F - 0.16F, 0.0F, 1.0F);
        final float imperfectionScale = config.realismProfile.loadImperfectionStrength
                * dynamicImperfection
                * (0.35F + motorThrottle * 0.65F);

        final double simTime = state.simTimeSeconds();
        rollRate += (float) Math.sin(simTime * 22.0D + 0.8D) * imperfectionScale * 5.5F;
        pitchRate += (float) Math.sin(simTime * 19.0D + 1.7D) * imperfectionScale * 4.8F;
        yawRate += (float) Math.sin(simTime * 15.0D + 2.1D) * imperfectionScale * 3.0F;

        final float washWobble = washFactor * (0.35F + config.realismProfile.loadImperfectionStrength * 0.65F);
        rollRate += (float) Math.sin(simTime * 41.0D + 0.6D) * washWobble * 10.0F;
        pitchRate += (float) Math.cos(simTime * 37.0D + 1.4D) * washWobble * 9.5F;
        yawRate += (float) Math.sin(simTime * 29.0D + 2.0D) * washWobble * 5.5F;

        state.setRollRateDegPerSecond(rollRate);
        state.setPitchRateDegPerSecond(pitchRate);
        state.setYawRateDegPerSecond(yawRate);

        integrateOrientation(state, rollRate, pitchRate, yawRate, dt);

        final float thrustScale = 1.0F - sagLoss;
        final double thrustAcceleration = GRAVITY * config.craftProfile.thrustToWeight * motorThrottle * thrustScale;
        final Vec3 thrust = up.scale(thrustAcceleration);

        final Vec3 gravity = new Vec3(0.0D, -GRAVITY, 0.0D);
        final Vec3 drag = anisotropicDrag(state.velocity(), right, up, forward, config.craftProfile);
        final Vec3 wash = descentWash(state, right, forward, washFactor);

        final Vec3 totalAcceleration = thrust.add(gravity).add(drag).add(wash);
        Vec3 velocity = state.velocity().add(totalAcceleration.scale(dt));

        final Vec3 attemptedMove = velocity.scale(dt);
        final AABB currentBox = cameraBox(state.position());
        final AABB movedBox = currentBox.move(attemptedMove);
        if (!hasLoadedTerrain(level, movedBox)) {
            state.setVelocity(Vec3.ZERO);
            state.setPosition(state.position());
            state.advanceSimTime(dt);
            return StepOutcome.noEvent();
        }

        final Vec3 allowedMove = Entity.collideBoundingBox(collisionEntity, attemptedMove, currentBox, level, List.of());
        final Vec3 nextPosition = state.position().add(allowedMove);
        state.setPosition(nextPosition);

        if (touchesWater(level, cameraBox(nextPosition))) {
            final float impactSpeed = (float) velocity.length();
            final float impactEnergy = 0.5F * impactSpeed * impactSpeed;
            state.setVelocity(Vec3.ZERO);
            state.advanceSimTime(dt);
            return new StepOutcome(true, false, impactSpeed, impactEnergy);
        }

        final boolean collided = Math.abs(attemptedMove.x - allowedMove.x) > 1.0E-7D
                || Math.abs(attemptedMove.y - allowedMove.y) > 1.0E-7D
                || Math.abs(attemptedMove.z - allowedMove.z) > 1.0E-7D;

        if (collided) {
            final DroneCrashModel.CollisionOutcome collisionOutcome = this.crashModel.classifyCollision(
                    velocity,
                    attemptedMove,
                    allowedMove,
                    dt,
                    config.crashSettings
            );
            velocity = collisionOutcome.velocityAfterCollision();
            state.setVelocity(velocity);
            state.advanceSimTime(dt);

            if (collisionOutcome.crashed()) {
                return new StepOutcome(true, false, collisionOutcome.impactSpeed(), collisionOutcome.impactEnergy());
            }
            if (collisionOutcome.glanced()) {
                state.considerCheckpoint(dt);
                return new StepOutcome(false, true, collisionOutcome.impactSpeed(), collisionOutcome.impactEnergy());
            }
        } else {
            state.setVelocity(velocity);
        }

        state.considerCheckpoint(dt);
        state.advanceSimTime(dt);
        return StepOutcome.noEvent();
    }

    public static float throttleCurve(final float throttleInput, final DroneConfig.ThrottleProfile throttleProfile) {
        final float normalized = Mth.clamp(throttleInput, 0.0F, 1.0F);
        final float mid = Mth.clamp(throttleProfile.throttleMid, 0.05F, 0.95F);
        final float expo = Mth.clamp(throttleProfile.throttleExpo, 0.0F, 1.0F);

        final float span = normalized < mid ? mid : (1.0F - mid);
        final float centered = span <= 1.0E-6F ? 0.0F : (normalized - mid) / span;
        final float curved = centered + expo * (centered * centered * centered - centered);
        return Mth.clamp(mid + curved * span, 0.0F, 1.0F);
    }

    static float simulateBatterySagStep(
            final float currentSagLoss,
            final float motorThrottle,
            final float angularLoad,
            final DroneConfig.RealismProfile realismProfile,
            final double dt
    ) {
        final float load = Mth.clamp(motorThrottle * (0.65F + angularLoad * 0.35F), 0.0F, 1.0F);
        final float targetLoss = realismProfile.batterySagStrength * realismProfile.batterySagMaxLoss * load;

        final float responseSeconds = targetLoss > currentSagLoss ? 0.45F : realismProfile.sagRecoverySeconds;
        final float alpha = 1.0F - (float) Math.exp(-dt / Math.max(0.005D, responseSeconds));
        return Mth.clamp(currentSagLoss + (targetLoss - currentSagLoss) * alpha, 0.0F, realismProfile.batterySagMaxLoss);
    }

    static float simulateMotorSpoolStep(
            final float current,
            final float target,
            final float spoolUpSeconds,
            final float spoolDownSeconds,
            final double dt
    ) {
        final float responseSeconds = target >= current ? spoolUpSeconds : spoolDownSeconds;
        final float alpha = 1.0F - (float) Math.exp(-dt / Math.max(0.003D, responseSeconds));
        return Mth.clamp(current + (target - current) * alpha, 0.0F, 1.0F);
    }

    private static float updateAxisRate(
            final float currentRate,
            final float desiredRate,
            final float responseSeconds,
            final float maxAcceleration,
            final double dt
    ) {
        final float responseAlpha = 1.0F - (float) Math.exp(-dt / Math.max(0.003D, responseSeconds));
        final float filteredTarget = currentRate + (desiredRate - currentRate) * responseAlpha;
        final float maxDelta = maxAcceleration * (float) dt;
        final float delta = Mth.clamp(filteredTarget - currentRate, -maxDelta, maxDelta);
        return currentRate + delta;
    }

    private static Vec3 anisotropicDrag(
            final Vec3 velocity,
            final Vec3 right,
            final Vec3 up,
            final Vec3 forward,
            final DroneConfig.CraftProfile craftProfile
    ) {
        final double rightSpeed = velocity.dot(right);
        final double upSpeed = velocity.dot(up);
        final double forwardSpeed = velocity.dot(forward);

        final double dragRight = -Math.signum(rightSpeed) * craftProfile.sideDrag * rightSpeed * rightSpeed;
        final double dragUp = -Math.signum(upSpeed) * craftProfile.verticalDrag * upSpeed * upSpeed;
        final double dragForward = -Math.signum(forwardSpeed) * craftProfile.forwardDrag * forwardSpeed * forwardSpeed;

        return right.scale(dragRight).add(up.scale(dragUp)).add(forward.scale(dragForward));
    }

    static float computeDescentWashFactor(
            final Vec3 velocity,
            final Vec3 right,
            final Vec3 up,
            final Vec3 forward,
            final float motorThrottle,
            final float strength
    ) {
        if (strength <= 0.0F || motorThrottle <= 0.25F) {
            return 0.0F;
        }

        final double descentRate = Math.max(0.0D, -velocity.dot(up));
        if (descentRate < 6.0D) {
            return 0.0F;
        }

        final double lateralSpeed = velocity.dot(right);
        final double forwardSpeed = velocity.dot(forward);
        final double bodyHorizontalSpeed = Math.sqrt(lateralSpeed * lateralSpeed + forwardSpeed * forwardSpeed);
        final double trappedAirFactor = 1.0D - Mth.clamp(bodyHorizontalSpeed / 18.0D, 0.0D, 1.0D);
        if (trappedAirFactor <= 0.01D) {
            return 0.0F;
        }

        final double descentFactor = Mth.clamp((descentRate - 6.0D) / 16.0D, 0.0D, 1.0D);
        final double throttleRecoveryFactor = Mth.clamp((motorThrottle - 0.45D) / 0.55D, 0.0D, 1.0D);
        return (float) (strength * descentFactor * throttleRecoveryFactor * trappedAirFactor);
    }

    private static Vec3 descentWash(
            final DroneSimulationState state,
            final Vec3 right,
            final Vec3 forward,
            final float washFactor
    ) {
        if (washFactor <= 0.0F) {
            return Vec3.ZERO;
        }

        final double simTime = state.simTimeSeconds();
        final double lateral = Math.sin(simTime * 37.0D + 0.8D)
                + 0.50D * Math.sin(simTime * 73.0D + 2.3D);
        final double forwardNoise = Math.cos(simTime * 31.0D + 1.9D)
                + 0.45D * Math.cos(simTime * 61.0D + 0.2D);
        final double washAcceleration = washFactor * 1.8D;
        return right.scale(lateral * washAcceleration)
                .add(forward.scale(forwardNoise * washAcceleration * 0.70D));
    }

    private static void integrateOrientation(
            final DroneSimulationState state,
            final float rollRateDegPerSecond,
            final float pitchRateDegPerSecond,
            final float yawRateDegPerSecond,
            final double dt
    ) {
        final Quaternionf orientation = state.orientation();

        final Vec3 up = state.upVector();
        final Vec3 right = state.rightVector();
        final Vec3 forward = state.forwardVector();

        final float yawRadians = (float) Math.toRadians(yawRateDegPerSecond * dt);
        final float pitchRadians = (float) Math.toRadians(pitchRateDegPerSecond * dt);
        final float rollRadians = (float) Math.toRadians(rollRateDegPerSecond * dt);

        orientation.premul(axisRotation(up, yawRadians));
        orientation.premul(axisRotation(right, pitchRadians));
        orientation.premul(axisRotation(forward, rollRadians));
        state.setOrientation(orientation);
    }

    private static float smooth(final float current, final float target, final double dt, final float responseSeconds) {
        final float alpha = 1.0F - (float) Math.exp(-dt / Math.max(0.001D, responseSeconds));
        return current + (target - current) * alpha;
    }

    private static boolean hasLoadedTerrain(final Level level, final AABB box) {
        return hasLoadedChunk(level, box.minX, box.minY, box.minZ)
                && hasLoadedChunk(level, box.minX, box.minY, box.maxZ)
                && hasLoadedChunk(level, box.maxX, box.minY, box.minZ)
                && hasLoadedChunk(level, box.maxX, box.minY, box.maxZ)
                && hasLoadedChunk(level, box.minX, box.maxY, box.minZ)
                && hasLoadedChunk(level, box.maxX, box.maxY, box.maxZ);
    }

    private static boolean hasLoadedChunk(final Level level, final double x, final double y, final double z) {
        return level.hasChunkAt(BlockPos.containing(x, y, z));
    }

    private static boolean touchesWater(final Level level, final AABB box) {
        return isWater(level, box.minX, box.minY, box.minZ)
                || isWater(level, box.minX, box.minY, box.maxZ)
                || isWater(level, box.maxX, box.minY, box.minZ)
                || isWater(level, box.maxX, box.minY, box.maxZ)
                || isWater(level, box.minX, box.maxY, box.minZ)
                || isWater(level, box.minX, box.maxY, box.maxZ)
                || isWater(level, box.maxX, box.maxY, box.minZ)
                || isWater(level, box.maxX, box.maxY, box.maxZ)
                || isWater(level, box.getCenter().x, box.getCenter().y, box.getCenter().z);
    }

    private static boolean isWater(final Level level, final double x, final double y, final double z) {
        final BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    private static AABB cameraBox(final Vec3 position) {
        return new AABB(
                position.x - HALF_BOX_SIZE,
                position.y - HALF_BOX_SIZE,
                position.z - HALF_BOX_SIZE,
                position.x + HALF_BOX_SIZE,
                position.y + HALF_BOX_SIZE,
                position.z + HALF_BOX_SIZE
        );
    }

    private static Quaternionf axisRotation(final Vec3 axis, final float angleRadians) {
        if (Math.abs(angleRadians) <= 1.0E-8F) {
            return new Quaternionf();
        }
        return new Quaternionf().set(new AxisAngle4f(angleRadians, (float) axis.x, (float) axis.y, (float) axis.z));
    }

    public record StepOutcome(boolean crashed, boolean glanced, float impactSpeed, float impactEnergy) {
        private static StepOutcome noEvent() {
            return new StepOutcome(false, false, 0.0F, 0.0F);
        }
    }
}
