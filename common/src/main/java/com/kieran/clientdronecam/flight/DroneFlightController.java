package com.kieran.clientdronecam.flight;

import com.kieran.clientdronecam.config.DroneConfig;
import com.kieran.clientdronecam.input.DroneInputMapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class DroneFlightController {
    private static final double HALF_BOX_SIZE = 0.175D;
    private static final double TICK_SECONDS = 1.0D / 20.0D;
    private static final double MAX_FRAME_SECONDS = 1.0D / 20.0D;
    private static final double GRAVITY = 18.0D;
    private static final double THRUST_FORCE = 75.0D;
    private static final double LINEAR_DAMPING = 0.94D;
    private static final double HORIZONTAL_DAMPING = 0.955D;
    private static final float INPUT_SMOOTHING = 0.22F;
    private static final float RATE_CENTER_SENSITIVITY = 200.0F;
    private static final float RATE_MAX = 670.0F;
    private static final float RATE_EXPO = 0.57F;
    private static final double THROTTLE_IDLE = 0.10D;

    private final DroneConfig config;
    private final DroneInputMapper inputMapper;
    private final DroneCameraState state = new DroneCameraState();
    private long lastFrameTimeNanos = -1L;

    public DroneFlightController(final DroneConfig config, final DroneInputMapper inputMapper) {
        this.config = config;
        this.inputMapper = inputMapper;
    }

    public void tick(final Minecraft minecraft) {
        this.handleSetupAndActivation(minecraft);
    }

    public void updateFrame(final Minecraft minecraft) {
        final var player = minecraft.player;
        final var level = minecraft.level;
        if (player == null || level == null) {
            if (this.state.isActive()) {
                this.forceDeactivate("missing_client_level");
            }
            return;
        }

        this.handleSetupAndActivation(minecraft);

        final double frameSeconds = this.consumeFrameSeconds();
        final DroneInputMapper.PollResult pollResult = this.inputMapper.poll(this.config, this.state);
        if (this.state.isActive() && !pollResult.connected()) {
            this.forceDeactivate("controller_disconnect");
            return;
        }

        if (this.state.isActive() && this.shouldForceStop(player.level(), player.isDeadOrDying())) {
            this.forceDeactivate("player_state_changed");
            return;
        }

        final long window = windowHandle(minecraft);
        final boolean escapeDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
        if (this.state.isActive() && escapeDown && !this.state.escapeDown()) {
            this.forceDeactivate("escape");
            this.state.setEscapeDown(true);
            return;
        }
        this.state.setEscapeDown(escapeDown);

        if (!this.state.isActive()) {
            if (pollResult.connected() && pollResult.togglePressed() && minecraft.screen == null) {
                final String controllerName = pollResult.controller() == null ? "" : pollResult.controller().displayName();
                this.state.activate(player.getEyePosition(), player.getYRot(), 0.0F, player.level().dimension(), controllerName);
                this.resetFrameTimer();
            }
            return;
        }

        if (pollResult.togglePressed() || pollResult.exitPressed()) {
            this.forceDeactivate("button_toggle");
            return;
        }

        if (frameSeconds <= 0.0D) {
            return;
        }

        this.applyRotation(pollResult, frameSeconds);
        this.applyMovement(player, level, frameSeconds);
    }

    public boolean isActive() {
        return this.state.isActive();
    }

    public float getCameraYaw() {
        return this.state.cameraYaw();
    }

    public float getCameraPitch() {
        return this.state.cameraPitch();
    }

    public float getCameraRoll() {
        return this.state.cameraRoll();
    }

    public DroneCameraAngles getCameraAngles() {
        return new DroneCameraAngles(this.getCameraYaw(), this.getCameraPitch(), this.getCameraRoll());
    }

    public String getActiveControllerName() {
        return this.state.controllerName();
    }

    public @Nullable Vec3 getRenderCameraPosition(final float partialTick) {
        if (!this.state.isActive()) {
            return null;
        }

        return this.state.renderPosition(partialTick);
    }

    public void forceDeactivate(final String reason) {
        this.state.deactivate();
        this.resetFrameTimer();
    }

    private void handleSetupAndActivation(final Minecraft minecraft) {
        if (!this.state.isActive()) {
            this.resetFrameTimer();
        }
    }

    private boolean shouldForceStop(final Level level, final boolean deadOrDying) {
        if (deadOrDying || this.state.dimension() == null) {
            return true;
        }

        return !this.state.dimension().equals(level.dimension());
    }

    private void applyRotation(final DroneInputMapper.PollResult pollResult, final double frameSeconds) {
        final float filteredThrottle = smooth(this.state.filteredThrottle(), pollResult.throttle(), INPUT_SMOOTHING);
        final float filteredYaw = smooth(this.state.filteredYaw(), pollResult.yaw(), INPUT_SMOOTHING);
        final float filteredPitch = smooth(this.state.filteredPitch(), pollResult.pitch(), INPUT_SMOOTHING);
        final float filteredRoll = smooth(this.state.filteredRoll(), pollResult.roll(), INPUT_SMOOTHING);

        this.state.setFilteredThrottle(filteredThrottle);
        this.state.setFilteredYaw(filteredYaw);
        this.state.setFilteredPitch(filteredPitch);
        this.state.setFilteredRoll(filteredRoll);

        final float pitchRate = -actualRate(filteredPitch, RATE_CENTER_SENSITIVITY, RATE_MAX, RATE_EXPO);
        final float rollRate = actualRate(filteredRoll, RATE_CENTER_SENSITIVITY, RATE_MAX, RATE_EXPO);
        final float yawRate = actualRate(filteredYaw, RATE_CENTER_SENSITIVITY, RATE_MAX, RATE_EXPO);

        this.state.setPitchVelocity(pitchRate);
        this.state.setRollVelocity(rollRate);
        this.state.setYawVelocity(yawRate);

        final Quaternionf orientation = this.state.orientation();
        final Vec3 startUp = this.state.upVector();
        final Vec3 startRight = this.state.rightVector();
        final Vec3 startForward = this.state.forwardVector();

        final float yawRadians = (float) Math.toRadians(yawRate * frameSeconds);
        final float pitchRadians = (float) Math.toRadians(pitchRate * frameSeconds);
        final float rollRadians = (float) Math.toRadians(rollRate * frameSeconds);

        // Apply local-stick rotations against the craft basis sampled at frame start:
        // 1. yaw around local up
        // 2. pitch around local right
        // 3. roll around local forward
        final Quaternionf yawDelta = axisRotation(startUp, yawRadians);
        final Quaternionf pitchDelta = axisRotation(startRight, pitchRadians);
        final Quaternionf rollDelta = axisRotation(startForward, rollRadians);

        orientation.premul(yawDelta);
        orientation.premul(pitchDelta);
        orientation.premul(rollDelta);
        this.state.setOrientation(orientation);
    }

    private void applyMovement(final Entity player, final Level level, final double frameSeconds) {
        final Vec3 craftUp = this.state.upVector();
        final double throttle = THROTTLE_IDLE + ((this.state.filteredThrottle() + 1.0D) * 0.5D) * (1.0D - THROTTLE_IDLE);
        final Vec3 acceleration = craftUp.scale(throttle * THRUST_FORCE).add(0.0D, -GRAVITY, 0.0D);

        Vec3 velocity = this.state.velocity().add(acceleration.scale(frameSeconds));
        final double horizontalDamping = Math.pow(HORIZONTAL_DAMPING, frameSeconds / TICK_SECONDS);
        final double verticalDamping = Math.pow(LINEAR_DAMPING, frameSeconds / TICK_SECONDS);
        velocity = new Vec3(velocity.x * horizontalDamping, velocity.y * verticalDamping, velocity.z * horizontalDamping);
        final Vec3 attemptedMove = velocity.scale(frameSeconds);
        final AABB currentBox = cameraBox(this.state.position());
        final AABB movedBox = currentBox.move(attemptedMove);
        if (!hasLoadedTerrain(level, movedBox)) {
            this.state.setVelocity(Vec3.ZERO);
            this.state.setPosition(this.state.position());
            return;
        }

        final Vec3 allowedMove = Entity.collideBoundingBox(player, attemptedMove, currentBox, level, List.of());
        final Vec3 nextPosition = this.state.position().add(allowedMove);
        this.state.setPosition(nextPosition);

        velocity = new Vec3(
                Math.abs(attemptedMove.x - allowedMove.x) > 1.0E-7D ? 0.0D : velocity.x,
                Math.abs(attemptedMove.y - allowedMove.y) > 1.0E-7D ? 0.0D : velocity.y,
                Math.abs(attemptedMove.z - allowedMove.z) > 1.0E-7D ? 0.0D : velocity.z
        );
        this.state.setVelocity(velocity);
    }

    private double consumeFrameSeconds() {
        final long now = System.nanoTime();
        if (this.lastFrameTimeNanos < 0L) {
            this.lastFrameTimeNanos = now;
            return 0.0D;
        }

        final double rawSeconds = (now - this.lastFrameTimeNanos) / 1_000_000_000.0D;
        this.lastFrameTimeNanos = now;
        return Mth.clamp(rawSeconds, 0.0D, MAX_FRAME_SECONDS);
    }

    private void resetFrameTimer() {
        this.lastFrameTimeNanos = -1L;
    }

    private static long windowHandle(final Minecraft minecraft) {
        try {
            return (long) minecraft.getWindow().getClass().getMethod("getWindow").invoke(minecraft.getWindow());
        } catch (final ReflectiveOperationException ignored) {
        }

        try {
            return (long) minecraft.getWindow().getClass().getMethod("handle").invoke(minecraft.getWindow());
        } catch (final ReflectiveOperationException ignored) {
            return 0L;
        }
    }

    private static float smooth(final float current, final float target, final float factor) {
        return Mth.lerp(factor, current, target);
    }

    private static float actualRate(final float input, final float centerSensitivity, final float maxRate, final float expo) {
        final float magnitude = Math.abs(input);
        final float shaped = centerSensitivity * magnitude
                + (maxRate - centerSensitivity) * blendCurve(magnitude, expo);
        return Math.copySign(shaped, input);
    }

    private static float blendCurve(final float x, final float expo) {
        final float squared = x * x;
        final float cubed = squared * x;
        return (1.0F - expo) * squared + expo * cubed;
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
        if (Math.abs(angleRadians) < 1.0E-8F) {
            return new Quaternionf();
        }
        return new Quaternionf().set(new AxisAngle4f(angleRadians, (float) axis.x, (float) axis.y, (float) axis.z));
    }
}
