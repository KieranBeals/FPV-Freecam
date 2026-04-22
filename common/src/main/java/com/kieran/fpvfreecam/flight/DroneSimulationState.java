package com.kieran.fpvfreecam.flight;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.AxisAngle4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.jetbrains.annotations.Nullable;

public final class DroneSimulationState {
    private static final int MAX_TRACKED_BUTTONS = 256;
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 LOCAL_FORWARD = new Vec3(0.0D, 0.0D, 1.0D);
    private static final Vec3 LOCAL_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 LOCAL_RIGHT = new Vec3(-1.0D, 0.0D, 0.0D);
    private static final double MIN_HORIZONTAL_LENGTH_SQUARED = 1.0E-8D;
    private static final double MIN_REFERENCE_LENGTH_SQUARED = 1.0E-8D;

    private boolean active;
    private boolean armed;
    private boolean crashed;

    private Vec3 position = Vec3.ZERO;
    private Vec3 previousPosition = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private final Quaternionf orientation = new Quaternionf();

    private float filteredThrottle;
    private float filteredYaw;
    private float filteredPitch;
    private float filteredRoll;
    private float inputThrottle;
    private float inputYaw;
    private float inputPitch;
    private float inputRoll;

    private float rollRateDegPerSecond;
    private float pitchRateDegPerSecond;
    private float yawRateDegPerSecond;

    private float desiredRollRateDegPerSecond;
    private float desiredPitchRateDegPerSecond;
    private float desiredYawRateDegPerSecond;

    private float motorThrottle;
    private float batterySagLoss;
    private float cameraAngleDeg;
    private float renderAlpha;

    private @Nullable ResourceKey<Level> dimension;
    private String controllerName = "";
    private boolean escapeDown;

    private float lastImpactSpeed;
    private float lastImpactEnergy;

    private double simTimeSeconds;
    private double checkpointAgeSeconds;

    private Snapshot launchSnapshot;
    private @Nullable Snapshot checkpointSnapshot;
    private @Nullable Snapshot pendingQuickRearmSnapshot;

    private final boolean[] previousButtons = new boolean[MAX_TRACKED_BUTTONS];

    private float lastCameraYaw;
    private float lastCameraRoll;

    public boolean isActive() {
        return this.active;
    }

    public boolean isArmed() {
        return this.armed;
    }

    public boolean isCrashed() {
        return this.crashed;
    }

    public void activate(
            final Vec3 eyePosition,
            final float yaw,
            final float pitch,
            final ResourceKey<Level> dimension,
            final String controllerName,
            final float cameraAngleDeg
    ) {
        this.active = true;
        this.armed = true;
        this.crashed = false;
        this.dimension = dimension;
        this.controllerName = controllerName == null ? "" : controllerName;
        this.escapeDown = false;
        this.lastImpactSpeed = 0.0F;
        this.lastImpactEnergy = 0.0F;
        this.simTimeSeconds = 0.0D;
        this.checkpointAgeSeconds = 0.0D;
        this.renderAlpha = 1.0F;

        final Snapshot quickRearm = this.pendingQuickRearmSnapshot;
        this.pendingQuickRearmSnapshot = null;
        if (quickRearm != null) {
            this.applySnapshot(quickRearm);
            this.launchSnapshot = quickRearm.copy();
            this.checkpointSnapshot = quickRearm.copy();
            this.cameraAngleDeg = Mth.clamp(cameraAngleDeg, -90.0F, 90.0F);
        } else {
            this.position = eyePosition;
            this.previousPosition = eyePosition;
            this.velocity = Vec3.ZERO;
            this.setOrientationFromView(yaw, pitch);
            this.motorThrottle = 0.0F;
            this.batterySagLoss = 0.0F;
            this.cameraAngleDeg = Mth.clamp(cameraAngleDeg, -90.0F, 90.0F);
            this.launchSnapshot = this.createSnapshot();
            this.checkpointSnapshot = this.launchSnapshot.copy();
        }

        this.filteredThrottle = 0.0F;
        this.filteredYaw = 0.0F;
        this.filteredPitch = 0.0F;
        this.filteredRoll = 0.0F;
        this.inputThrottle = 0.0F;
        this.inputYaw = 0.0F;
        this.inputPitch = 0.0F;
        this.inputRoll = 0.0F;
        this.rollRateDegPerSecond = 0.0F;
        this.pitchRateDegPerSecond = 0.0F;
        this.yawRateDegPerSecond = 0.0F;
        this.desiredRollRateDegPerSecond = 0.0F;
        this.desiredPitchRateDegPerSecond = 0.0F;
        this.desiredYawRateDegPerSecond = 0.0F;
    }

    public void deactivate() {
        this.active = false;
        this.armed = false;
        this.crashed = false;
        this.position = Vec3.ZERO;
        this.previousPosition = Vec3.ZERO;
        this.velocity = Vec3.ZERO;
        this.orientation.identity();
        this.filteredThrottle = 0.0F;
        this.filteredYaw = 0.0F;
        this.filteredPitch = 0.0F;
        this.filteredRoll = 0.0F;
        this.inputThrottle = 0.0F;
        this.inputYaw = 0.0F;
        this.inputPitch = 0.0F;
        this.inputRoll = 0.0F;
        this.rollRateDegPerSecond = 0.0F;
        this.pitchRateDegPerSecond = 0.0F;
        this.yawRateDegPerSecond = 0.0F;
        this.desiredRollRateDegPerSecond = 0.0F;
        this.desiredPitchRateDegPerSecond = 0.0F;
        this.desiredYawRateDegPerSecond = 0.0F;
        this.motorThrottle = 0.0F;
        this.batterySagLoss = 0.0F;
        this.dimension = null;
        this.controllerName = "";
        this.escapeDown = false;
        this.lastImpactSpeed = 0.0F;
        this.lastImpactEnergy = 0.0F;
        this.simTimeSeconds = 0.0D;
        this.renderAlpha = 1.0F;
        this.launchSnapshot = null;
        this.checkpointSnapshot = null;
        this.lastCameraYaw = 0.0F;
        this.lastCameraRoll = 0.0F;
    }

    public void queueQuickRearmFromLaunch() {
        if (this.launchSnapshot != null) {
            this.pendingQuickRearmSnapshot = this.launchSnapshot.copy();
        }
    }

    public boolean restoreCheckpoint() {
        if (this.checkpointSnapshot == null) {
            return false;
        }

        this.applySnapshot(this.checkpointSnapshot);
        this.armed = true;
        this.crashed = false;
        this.filteredThrottle = 0.0F;
        this.filteredYaw = 0.0F;
        this.filteredPitch = 0.0F;
        this.filteredRoll = 0.0F;
        this.inputThrottle = 0.0F;
        this.inputYaw = 0.0F;
        this.inputPitch = 0.0F;
        this.inputRoll = 0.0F;
        this.lastImpactSpeed = 0.0F;
        this.lastImpactEnergy = 0.0F;
        this.checkpointAgeSeconds = 0.0D;
        return true;
    }

    public void considerCheckpoint(final double dt) {
        if (!this.active || this.crashed || !this.armed) {
            return;
        }

        this.checkpointAgeSeconds += dt;
        if (this.checkpointAgeSeconds < 0.35D) {
            return;
        }

        final double speed = this.velocity.length();
        if (speed > 26.0D || this.velocity.y < -8.0D) {
            return;
        }

        this.checkpointSnapshot = this.createSnapshot();
        this.checkpointAgeSeconds = 0.0D;
    }

    public void markCrash(final float impactSpeed, final float impactEnergy) {
        this.crashed = true;
        this.armed = false;
        this.lastImpactSpeed = impactSpeed;
        this.lastImpactEnergy = impactEnergy;
        this.velocity = Vec3.ZERO;
    }

    public void setCrashedState(final boolean crashed) {
        this.crashed = crashed;
        this.armed = !crashed;
    }

    public Vec3 position() {
        return this.position;
    }

    public Vec3 renderPosition(final float partialTick) {
        return this.previousPosition.lerp(this.position, Mth.clamp(partialTick, 0.0F, 1.0F));
    }

    public void setPosition(final Vec3 position) {
        this.previousPosition = this.position;
        this.position = position;
    }

    public Vec3 velocity() {
        return this.velocity;
    }

    public void setVelocity(final Vec3 velocity) {
        this.velocity = velocity;
    }

    public Quaternionf orientation() {
        return new Quaternionf(this.orientation);
    }

    public void setOrientation(final Quaternionf orientation) {
        this.orientation.set(orientation).normalize();
    }

    public void setOrientationFromView(final float initialYaw, final float initialPitch) {
        final double yawRadians = Math.toRadians(initialYaw);
        final double pitchRadians = Math.toRadians(initialPitch);
        final double cosPitch = Math.cos(pitchRadians);

        final Vec3 forward = new Vec3(
                -Math.sin(yawRadians) * cosPitch,
                -Math.sin(pitchRadians),
                Math.cos(yawRadians) * cosPitch
        ).normalize();

        Vec3 right = forward.cross(WORLD_UP);
        if (right.lengthSqr() < MIN_HORIZONTAL_LENGTH_SQUARED) {
            right = new Vec3(-Math.cos(yawRadians), 0.0D, -Math.sin(yawRadians));
        }
        right = right.normalize();
        final Vec3 up = right.cross(forward).normalize();

        final Matrix3f basis = new Matrix3f().set(
                (float) right.x, (float) up.x, (float) forward.x,
                (float) right.y, (float) up.y, (float) forward.y,
                (float) right.z, (float) up.z, (float) forward.z
        );
        this.orientation.setFromNormalized(basis).normalize();
        this.lastCameraYaw = Mth.wrapDegrees(initialYaw);
        this.lastCameraRoll = 0.0F;
    }

    public Vec3 forwardVector() {
        return transformAxis(LOCAL_FORWARD, this.orientation);
    }

    public Vec3 upVector() {
        return transformAxis(LOCAL_UP, this.orientation);
    }

    public Vec3 rightVector() {
        return transformAxis(LOCAL_RIGHT, this.orientation);
    }

    public DroneCameraAngles cameraAngles() {
        return this.cameraAngles(this.cameraOrientation());
    }

    public Quaternionf cameraOrientation() {
        final Quaternionf cameraOrientation = new Quaternionf(this.orientation);
        if (Math.abs(this.cameraAngleDeg) <= 1.0E-7F) {
            return cameraOrientation;
        }

        final Vec3 cameraRight = this.rightVector();
        final Quaternionf pitchOffset = new Quaternionf().set(new AxisAngle4f(
                (float) Math.toRadians(this.cameraAngleDeg),
                (float) cameraRight.x,
                (float) cameraRight.y,
                (float) cameraRight.z
        ));
        cameraOrientation.premul(pitchOffset);
        return cameraOrientation.normalize();
    }

    public float cameraAngleDeg() {
        return this.cameraAngleDeg;
    }

    public void setCameraAngleDeg(final float cameraAngleDeg) {
        this.cameraAngleDeg = Mth.clamp(cameraAngleDeg, -90.0F, 90.0F);
    }

    public void adjustCameraAngle(final float deltaDeg) {
        this.cameraAngleDeg = Mth.clamp(this.cameraAngleDeg + deltaDeg, -90.0F, 90.0F);
    }

    public float filteredThrottle() {
        return this.filteredThrottle;
    }

    public void setFilteredThrottle(final float filteredThrottle) {
        this.filteredThrottle = filteredThrottle;
    }

    public float filteredYaw() {
        return this.filteredYaw;
    }

    public void setFilteredYaw(final float filteredYaw) {
        this.filteredYaw = filteredYaw;
    }

    public float filteredPitch() {
        return this.filteredPitch;
    }

    public void setFilteredPitch(final float filteredPitch) {
        this.filteredPitch = filteredPitch;
    }

    public float filteredRoll() {
        return this.filteredRoll;
    }

    public void setFilteredRoll(final float filteredRoll) {
        this.filteredRoll = filteredRoll;
    }

    public void setInputAxes(final float throttle, final float yaw, final float pitch, final float roll) {
        this.inputThrottle = Mth.clamp(throttle, -1.0F, 1.0F);
        this.inputYaw = Mth.clamp(yaw, -1.0F, 1.0F);
        this.inputPitch = Mth.clamp(pitch, -1.0F, 1.0F);
        this.inputRoll = Mth.clamp(roll, -1.0F, 1.0F);
    }

    public float inputThrottle() {
        return this.inputThrottle;
    }

    public float inputYaw() {
        return this.inputYaw;
    }

    public float inputPitch() {
        return this.inputPitch;
    }

    public float inputRoll() {
        return this.inputRoll;
    }

    public float rollRateDegPerSecond() {
        return this.rollRateDegPerSecond;
    }

    public void setRollRateDegPerSecond(final float rollRateDegPerSecond) {
        this.rollRateDegPerSecond = rollRateDegPerSecond;
    }

    public float pitchRateDegPerSecond() {
        return this.pitchRateDegPerSecond;
    }

    public void setPitchRateDegPerSecond(final float pitchRateDegPerSecond) {
        this.pitchRateDegPerSecond = pitchRateDegPerSecond;
    }

    public float yawRateDegPerSecond() {
        return this.yawRateDegPerSecond;
    }

    public void setYawRateDegPerSecond(final float yawRateDegPerSecond) {
        this.yawRateDegPerSecond = yawRateDegPerSecond;
    }

    public float desiredRollRateDegPerSecond() {
        return this.desiredRollRateDegPerSecond;
    }

    public void setDesiredRollRateDegPerSecond(final float desiredRollRateDegPerSecond) {
        this.desiredRollRateDegPerSecond = desiredRollRateDegPerSecond;
    }

    public float desiredPitchRateDegPerSecond() {
        return this.desiredPitchRateDegPerSecond;
    }

    public void setDesiredPitchRateDegPerSecond(final float desiredPitchRateDegPerSecond) {
        this.desiredPitchRateDegPerSecond = desiredPitchRateDegPerSecond;
    }

    public float desiredYawRateDegPerSecond() {
        return this.desiredYawRateDegPerSecond;
    }

    public void setDesiredYawRateDegPerSecond(final float desiredYawRateDegPerSecond) {
        this.desiredYawRateDegPerSecond = desiredYawRateDegPerSecond;
    }

    public float motorThrottle() {
        return this.motorThrottle;
    }

    public void setMotorThrottle(final float motorThrottle) {
        this.motorThrottle = Mth.clamp(motorThrottle, 0.0F, 1.0F);
    }

    public float batterySagLoss() {
        return this.batterySagLoss;
    }

    public void setBatterySagLoss(final float batterySagLoss) {
        this.batterySagLoss = Mth.clamp(batterySagLoss, 0.0F, 1.0F);
    }

    public float lastImpactSpeed() {
        return this.lastImpactSpeed;
    }

    public float lastImpactEnergy() {
        return this.lastImpactEnergy;
    }

    public @Nullable ResourceKey<Level> dimension() {
        return this.dimension;
    }

    public String controllerName() {
        return this.controllerName;
    }

    public boolean escapeDown() {
        return this.escapeDown;
    }

    public void setEscapeDown(final boolean escapeDown) {
        this.escapeDown = escapeDown;
    }

    public boolean[] previousButtons() {
        return this.previousButtons;
    }

    public void clearButtons() {
        for (int index = 0; index < this.previousButtons.length; index++) {
            this.previousButtons[index] = false;
        }
    }

    public double simTimeSeconds() {
        return this.simTimeSeconds;
    }

    public void advanceSimTime(final double dt) {
        this.simTimeSeconds += dt;
    }

    public float renderAlpha() {
        return this.renderAlpha;
    }

    public void setRenderAlpha(final float renderAlpha) {
        this.renderAlpha = Mth.clamp(renderAlpha, 0.0F, 1.0F);
    }

    private Snapshot createSnapshot() {
        return new Snapshot(
                this.position,
                this.velocity,
                new Quaternionf(this.orientation),
                this.motorThrottle,
                this.batterySagLoss,
                this.cameraAngleDeg
        );
    }

    private void applySnapshot(final Snapshot snapshot) {
        this.position = snapshot.position;
        this.previousPosition = snapshot.position;
        this.velocity = snapshot.velocity;
        this.orientation.set(snapshot.orientation).normalize();
        this.motorThrottle = snapshot.motorThrottle;
        this.batterySagLoss = snapshot.batterySagLoss;
        this.cameraAngleDeg = snapshot.cameraAngleDeg;
    }

    private float cameraYaw(final Quaternionf cameraOrientation) {
        final Vec3 forward = transformAxis(LOCAL_FORWARD, cameraOrientation);
        final double horizontalLengthSquared = forward.x * forward.x + forward.z * forward.z;
        if (horizontalLengthSquared >= MIN_HORIZONTAL_LENGTH_SQUARED) {
            this.lastCameraYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-forward.x, forward.z)));
        }
        return this.lastCameraYaw;
    }

    private float cameraPitch(final Quaternionf cameraOrientation) {
        final Vec3 forward = transformAxis(LOCAL_FORWARD, cameraOrientation);
        final double clampedY = Mth.clamp(-forward.y, -1.0D, 1.0D);
        return Mth.wrapDegrees((float) Math.toDegrees(Math.asin(clampedY)));
    }

    private float cameraRoll(final Quaternionf cameraOrientation) {
        final Vec3 forward = transformAxis(LOCAL_FORWARD, cameraOrientation);
        final Vec3 up = transformAxis(LOCAL_UP, cameraOrientation);
        final Vec3 projectedWorldUp = WORLD_UP.subtract(forward.scale(WORLD_UP.dot(forward)));
        final Vec3 projectedUp = up.subtract(forward.scale(up.dot(forward)));

        if (projectedWorldUp.lengthSqr() >= MIN_REFERENCE_LENGTH_SQUARED
                && projectedUp.lengthSqr() >= MIN_REFERENCE_LENGTH_SQUARED) {
            final Vec3 referenceUp = projectedWorldUp.normalize();
            final Vec3 currentUp = projectedUp.normalize();
            final double sin = forward.dot(referenceUp.cross(currentUp));
            final double cos = currentUp.dot(referenceUp);
            this.lastCameraRoll = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(sin, cos)));
        }
        return this.lastCameraRoll;
    }

    private DroneCameraAngles cameraAngles(final Quaternionf cameraOrientation) {
        return new DroneCameraAngles(
                this.cameraYaw(cameraOrientation),
                this.cameraPitch(cameraOrientation),
                this.cameraRoll(cameraOrientation)
        );
    }

    private static Vec3 transformAxis(final Vec3 localAxis, final Quaternionf orientation) {
        final org.joml.Vector3f transformed = new org.joml.Vector3f((float) localAxis.x, (float) localAxis.y, (float) localAxis.z)
                .rotate(orientation);
        return new Vec3(transformed.x(), transformed.y(), transformed.z());
    }

    private static final class Snapshot {
        private final Vec3 position;
        private final Vec3 velocity;
        private final Quaternionf orientation;
        private final float motorThrottle;
        private final float batterySagLoss;
        private final float cameraAngleDeg;

        private Snapshot(
                final Vec3 position,
                final Vec3 velocity,
                final Quaternionf orientation,
                final float motorThrottle,
                final float batterySagLoss,
                final float cameraAngleDeg
        ) {
            this.position = position;
            this.velocity = velocity;
            this.orientation = orientation;
            this.motorThrottle = motorThrottle;
            this.batterySagLoss = batterySagLoss;
            this.cameraAngleDeg = cameraAngleDeg;
        }

        private Snapshot copy() {
            return new Snapshot(
                    this.position,
                    this.velocity,
                    new Quaternionf(this.orientation),
                    this.motorThrottle,
                    this.batterySagLoss,
                    this.cameraAngleDeg
            );
        }
    }
}
