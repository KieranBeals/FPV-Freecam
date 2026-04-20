package com.kieran.clientdronecam.flight;

import com.kieran.clientdronecam.input.DroneInputMapper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public final class DroneCameraState {
    private static final int MAX_TRACKED_BUTTONS = 256;

    private boolean active;
    private Vec3 position = Vec3.ZERO;
    private Vec3 previousPosition = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private float yaw;
    private float pitch;
    private float roll;
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
    private final boolean[] previousButtons = new boolean[MAX_TRACKED_BUTTONS];

    public boolean isActive() {
        return this.active;
    }

    public void activate(final Vec3 eyePosition, final float yaw, final float pitch, final ResourceKey<Level> dimension, final String controllerName) {
        this.active = true;
        this.position = eyePosition;
        this.previousPosition = eyePosition;
        this.velocity = Vec3.ZERO;
        this.yaw = yaw;
        this.pitch = 0.0F;
        this.roll = 0.0F;
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
    }

    public void deactivate() {
        this.active = false;
        this.position = Vec3.ZERO;
        this.previousPosition = Vec3.ZERO;
        this.velocity = Vec3.ZERO;
        this.dimension = null;
        this.controllerName = "";
        this.roll = 0.0F;
        this.yawVelocity = 0.0F;
        this.pitchVelocity = 0.0F;
        this.rollVelocity = 0.0F;
        this.filteredThrottle = 0.0F;
        this.filteredYaw = 0.0F;
        this.filteredPitch = 0.0F;
        this.filteredRoll = 0.0F;
        this.escapeDown = false;
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

    public float yaw() {
        return this.yaw;
    }

    public void setYaw(final float yaw) {
        this.yaw = yaw;
    }

    public float pitch() {
        return this.pitch;
    }

    public void setPitch(final float pitch) {
        this.pitch = pitch;
    }

    public @Nullable ResourceKey<Level> dimension() {
        return this.dimension;
    }

    public float roll() {
        return this.roll;
    }

    public void setRoll(final float roll) {
        this.roll = roll;
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
}
