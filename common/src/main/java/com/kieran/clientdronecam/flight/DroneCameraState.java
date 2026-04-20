package com.kieran.clientdronecam.flight;

import com.kieran.clientdronecam.input.DroneInputMapper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.AxisAngle4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public final class DroneCameraState {
    private static final int MAX_TRACKED_BUTTONS = 256;
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 LOCAL_FORWARD = new Vec3(0.0D, 0.0D, 1.0D);
    private static final Vec3 LOCAL_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 LOCAL_RIGHT = new Vec3(-1.0D, 0.0D, 0.0D);
    private static final double MIN_HORIZONTAL_LENGTH_SQUARED = 1.0E-8D;
    private static final double MIN_REFERENCE_LENGTH_SQUARED = 1.0E-8D;

    private boolean active;
    private Vec3 position = Vec3.ZERO;
    private Vec3 previousPosition = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private final Quaternionf orientation = new Quaternionf();
    private float yawVelocity;
    private float pitchVelocity;
    private float rollVelocity;
    private float filteredThrottle;
    private float filteredYaw;
    private float filteredPitch;
    private float filteredRoll;
    private @Nullable ResourceKey<Level> dimension;
    private String controllerName = "";
    private boolean escapeDown;
    private float lastCameraYaw;
    private float lastCameraRoll;
    private float cameraPitch;
    private final boolean[] previousButtons = new boolean[MAX_TRACKED_BUTTONS];

    public boolean isActive() {
        return this.active;
    }

    public void activate(final Vec3 eyePosition, final float yaw, final float pitch, final ResourceKey<Level> dimension, final String controllerName) {
        this.active = true;
        this.position = eyePosition;
        this.previousPosition = eyePosition;
        this.velocity = Vec3.ZERO;
        this.setOrientationFromView(yaw, pitch);
        this.yawVelocity = 0.0F;
        this.pitchVelocity = 0.0F;
        this.rollVelocity = 0.0F;
        this.filteredThrottle = 0.0F;
        this.filteredYaw = 0.0F;
        this.filteredPitch = 0.0F;
        this.filteredRoll = 0.0F;
        this.dimension = dimension;
        this.controllerName = controllerName == null ? "" : controllerName;
        this.escapeDown = false;
        this.cameraPitch = 0.0F;
    }

    public void deactivate() {
        this.active = false;
        this.position = Vec3.ZERO;
        this.previousPosition = Vec3.ZERO;
        this.velocity = Vec3.ZERO;
        this.orientation.identity();
        this.dimension = null;
        this.controllerName = "";
        this.yawVelocity = 0.0F;
        this.pitchVelocity = 0.0F;
        this.rollVelocity = 0.0F;
        this.filteredThrottle = 0.0F;
        this.filteredYaw = 0.0F;
        this.filteredPitch = 0.0F;
        this.filteredRoll = 0.0F;
        this.escapeDown = false;
        this.lastCameraYaw = 0.0F;
        this.lastCameraRoll = 0.0F;
        this.cameraPitch = 0.0F;
        this.clearButtons();
    }

    public Vec3 position() {
        return this.position;
    }

    public Vec3 renderPosition(final float partialTick) {
        return this.previousPosition.lerp(this.position, partialTick);
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

    public Quaternionf orientation() {
        return new Quaternionf(this.orientation);
    }

    public void setOrientation(final Quaternionf orientation) {
        this.orientation.set(orientation).normalize();
    }

    public void setCameraPitch(final float cameraPitch) {
        this.cameraPitch = Mth.clamp(cameraPitch, -90.0F, 90.0F);
    }

    public float cameraPitchOffset() {
        return this.cameraPitch;
    }

    public void adjustCameraPitch(final float pitchDeltaDegrees) {
        this.cameraPitch = Mth.clamp(this.cameraPitch + pitchDeltaDegrees, -90.0F, 90.0F);
    }

    public Quaternionf cameraOrientation() {
        final Quaternionf viewOrientation = new Quaternionf(this.orientation);
        final Vec3 cameraRight = this.rightVector();
        if (Math.abs(this.cameraPitch) > 1.0E-7F) {
            final Quaternionf cameraPitchRotation = new Quaternionf().set(new AxisAngle4f(
                    (float) Math.toRadians(this.cameraPitch),
                    (float) cameraRight.x,
                    (float) cameraRight.y,
                    (float) cameraRight.z
            ));
            viewOrientation.premul(cameraPitchRotation);
        }
        return viewOrientation.normalize();
    }

    public float cameraYaw() {
        return this.cameraAngles(this.cameraOrientation()).yaw();
    }

    public DroneCameraAngles cameraAngles() {
        return this.cameraAngles(this.cameraOrientation());
    }

    public float cameraPitch() {
        return this.cameraAngles(this.cameraOrientation()).pitch();
    }

    public float cameraRoll() {
        return this.cameraAngles(this.cameraOrientation()).roll();
    }

    public float bodyPitch() {
        return this.bodyPitchDegrees();
    }

    public float pitchOffset() {
        return this.cameraPitch;
    }

    public Vec3 forwardVector() {
        return transformAxis(LOCAL_FORWARD, this.orientation);
    }

    public @Nullable ResourceKey<Level> dimension() {
        return this.dimension;
    }

    public Vec3 rightVector() {
        return transformAxis(LOCAL_RIGHT, this.orientation);
    }

    public Vec3 upVector() {
        return transformAxis(LOCAL_UP, this.orientation);
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

    public float yawVelocity() {
        return this.yawVelocity;
    }

    public void setYawVelocity(final float yawVelocity) {
        this.yawVelocity = yawVelocity;
    }

    public float pitchVelocity() {
        return this.pitchVelocity;
    }

    public void setPitchVelocity(final float pitchVelocity) {
        this.pitchVelocity = pitchVelocity;
    }

    public float rollVelocity() {
        return this.rollVelocity;
    }

    public void setRollVelocity(final float rollVelocity) {
        this.rollVelocity = rollVelocity;
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
        for (int i = 0; i < this.previousButtons.length; i++) {
            this.previousButtons[i] = false;
        }
    }

    public float bodyYaw() {
        final Vec3 forward = this.forwardVector();
        final double horizontalLengthSquared = forward.x * forward.x + forward.z * forward.z;
        if (horizontalLengthSquared >= MIN_HORIZONTAL_LENGTH_SQUARED) {
            this.lastCameraYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-forward.x, forward.z)));
        }
        return this.lastCameraYaw;
    }

    private float bodyPitchDegrees() {
        final Vec3 forward = this.forwardVector();
        final double clampedY = Mth.clamp(-forward.y, -1.0D, 1.0D);
        return Mth.wrapDegrees((float) Math.toDegrees(Math.asin(clampedY)));
    }

    private float cameraPitch(final Quaternionf orientation) {
        final Vec3 forward = transformAxis(LOCAL_FORWARD, orientation);
        final double clampedY = Mth.clamp(-forward.y, -1.0D, 1.0D);
        return Mth.wrapDegrees((float) Math.toDegrees(Math.asin(clampedY)));
    }

    private float cameraYaw(final Quaternionf orientation) {
        final Vec3 forward = transformAxis(LOCAL_FORWARD, orientation);
        final double horizontalLengthSquared = forward.x * forward.x + forward.z * forward.z;
        if (horizontalLengthSquared >= MIN_HORIZONTAL_LENGTH_SQUARED) {
            this.lastCameraYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-forward.x, forward.z)));
        }
        return this.lastCameraYaw;
    }

    private float cameraRoll(final Quaternionf orientation) {
        final Vec3 forward = transformAxis(LOCAL_FORWARD, orientation);
        final Vec3 up = transformAxis(LOCAL_UP, orientation);
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

    private DroneCameraAngles cameraAngles(final Quaternionf orientation) {
        return new DroneCameraAngles(
                this.cameraYaw(orientation),
                this.cameraPitch(orientation),
                this.cameraRoll(orientation)
        );
    }

    public void captureButtons(final GLFWGamepadState gamepadState) {
        for (int button = 0; button < this.previousButtons.length; button++) {
            final boolean buttonPressed = button <= GLFW.GLFW_GAMEPAD_BUTTON_LAST
                    && gamepadState.buttons(button) == GLFW.GLFW_PRESS;
            final boolean axisPressed = DroneInputMapper.isAxisButtonPressed(gamepadState, button);
            this.previousButtons[button] = buttonPressed || axisPressed;
        }
    }

    public void captureButtons(final @Nullable ByteBuffer buttons, final @Nullable ByteBuffer hats, final @Nullable FloatBuffer axes) {
        for (int button = 0; button < this.previousButtons.length; button++) {
            final boolean buttonPressed = buttons != null
                    && button < buttons.limit()
                    && buttons.get(button) == GLFW.GLFW_PRESS;
            final boolean hatPressed = DroneInputMapper.isHatButtonPressed(hats, button);
            final boolean axisPressed = DroneInputMapper.isAxisButtonPressed(axes, button);
            this.previousButtons[button] = buttonPressed || hatPressed || axisPressed;
        }
    }

    private static Vec3 transformAxis(final Vec3 axis, final Quaternionf orientation) {
        final Vector3f transformed = new Vector3f((float) axis.x, (float) axis.y, (float) axis.z);
        orientation.transform(transformed);
        return new Vec3(transformed.x(), transformed.y(), transformed.z()).normalize();
    }
}
