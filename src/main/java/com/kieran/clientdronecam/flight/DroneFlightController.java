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
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class DroneFlightController {
    private static final double HALF_BOX_SIZE = 0.175D;
    private static final double TICK_SECONDS = 1.0D / 20.0D;
    private static final double GRAVITY = 18.0D;
    private static final double THRUST_FORCE = 34.0D;
    private static final double LINEAR_DAMPING = 0.985D;
    private static final double HORIZONTAL_DAMPING = 0.99D;
    private static final double MAX_SPEED = 20.0D;
    private static final float INPUT_SMOOTHING = 0.22F;
    private static final float PITCH_RATE = 165.0F;
    private static final float ROLL_RATE = 185.0F;
    private static final float YAW_RATE = 125.0F;

    private final DroneConfig config;
    private final DroneInputMapper inputMapper;
    private final DroneCameraState state = new DroneCameraState();

    public DroneFlightController(final DroneConfig config, final DroneInputMapper inputMapper) {
        this.config = config;
        this.inputMapper = inputMapper;
    }

    public void tick(final Minecraft minecraft) {
        final var player = minecraft.player;
        final var level = minecraft.level;
        if (player == null || level == null) {
            if (this.state.isActive()) {
                this.forceDeactivate("missing_client_level");
            }
            return;
        }

        final DroneInputMapper.PollResult pollResult = this.inputMapper.poll(this.config, this.state);
        if (this.state.isActive() && !pollResult.connected()) {
            this.forceDeactivate("controller_disconnect");
            return;
        }

        if (this.state.isActive() && this.shouldForceStop(player.level(), player.isDeadOrDying())) {
            this.forceDeactivate("player_state_changed");
            return;
        }

        final long window = minecraft.getWindow().getWindow();
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
                this.state.activate(player.getEyePosition(), player.getYRot(), player.getXRot(), player.level().dimension(), controllerName);
            }
            return;
        }

        if (pollResult.togglePressed() || pollResult.exitPressed()) {
            this.forceDeactivate("button_toggle");
            return;
        }

        this.applyRotation(pollResult);
        this.applyMovement(player, level, pollResult);
    }

    public boolean isActive() {
        return this.state.isActive();
    }

    public float getCameraYaw() {
        return this.state.yaw();
    }

    public float getCameraPitch() {
        return this.state.pitch();
    }

    public float getCameraRoll() {
        return this.state.roll();
    }

    public String getActiveControllerName() {
        return this.state.controllerName();
    }

    public @Nullable Vec3 getRenderCameraPosition() {
        if (!this.state.isActive()) {
            return null;
        }

        return this.state.renderPosition(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
    }

    public void forceDeactivate(final String reason) {
        this.state.deactivate();
    }

    private boolean shouldForceStop(final Level level, final boolean deadOrDying) {
        if (deadOrDying || this.state.dimension() == null) {
            return true;
        }

        return !this.state.dimension().equals(level.dimension());
    }

    private void applyRotation(final DroneInputMapper.PollResult pollResult) {
        final float filteredThrottle = smooth(this.state.filteredThrottle(), pollResult.throttle(), INPUT_SMOOTHING);
        final float filteredYaw = smooth(this.state.filteredYaw(), pollResult.yaw(), INPUT_SMOOTHING);
        final float filteredPitch = smooth(this.state.filteredPitch(), pollResult.pitch(), INPUT_SMOOTHING);
        final float filteredRoll = smooth(this.state.filteredRoll(), pollResult.roll(), INPUT_SMOOTHING);

        this.state.setFilteredThrottle(filteredThrottle);
        this.state.setFilteredYaw(filteredYaw);
        this.state.setFilteredPitch(filteredPitch);
        this.state.setFilteredRoll(filteredRoll);

        final float pitchRate = -filteredPitch * PITCH_RATE;
        final float rollRate = filteredRoll * ROLL_RATE;
        final float yawRate = filteredYaw * YAW_RATE;

        this.state.setPitchVelocity(pitchRate);
        this.state.setRollVelocity(rollRate);
        this.state.setYawVelocity(yawRate);

        this.state.setPitch(wrapAngle(this.state.pitch() + pitchRate * (float) TICK_SECONDS));
        this.state.setRoll(wrapAngle(this.state.roll() + rollRate * (float) TICK_SECONDS));
        this.state.setYaw(wrapAngle(this.state.yaw() + yawRate * (float) TICK_SECONDS));
    }

    private void applyMovement(final Entity player, final Level level, final DroneInputMapper.PollResult pollResult) {
        final Vec3 craftUp = droneUpVector(this.state.yaw(), this.state.pitch(), this.state.roll());
        final double throttle = (this.state.filteredThrottle() + 1.0D) * 0.5D;
        final Vec3 acceleration = craftUp.scale(throttle * THRUST_FORCE).add(0.0D, -GRAVITY, 0.0D);

        Vec3 velocity = this.state.velocity().add(acceleration.scale(TICK_SECONDS));
        velocity = new Vec3(
                velocity.x * HORIZONTAL_DAMPING,
                velocity.y * LINEAR_DAMPING,
                velocity.z * HORIZONTAL_DAMPING
        );
        if (velocity.lengthSqr() > MAX_SPEED * MAX_SPEED) {
            velocity = velocity.normalize().scale(MAX_SPEED);
        }

        final Vec3 attemptedMove = velocity;
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
                attemptedMove.x != allowedMove.x ? 0.0D : velocity.x,
                attemptedMove.y != allowedMove.y ? 0.0D : velocity.y,
                attemptedMove.z != allowedMove.z ? 0.0D : velocity.z
        );
        this.state.setVelocity(velocity);
    }

    private static float smooth(final float current, final float target, final float factor) {
        return Mth.lerp(factor, current, target);
    }

    private static float wrapAngle(final float angle) {
        return Mth.wrapDegrees(angle);
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

    private static Vec3 droneUpVector(final float yaw, final float pitch, final float roll) {
        final Vec3 forward = horizontalForward(yaw);
        final Vec3 right = horizontalRight(yaw);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);

        up = rotateAroundAxis(up, right, Math.toRadians(-pitch));
        up = rotateAroundAxis(up, forward, Math.toRadians(roll));
        return up.normalize();
    }

    private static Vec3 horizontalForward(final float yaw) {
        final double yawRadians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians)).normalize();
    }

    private static Vec3 horizontalRight(final float yaw) {
        final Vec3 forward = horizontalForward(yaw);
        return forward.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
    }

    private static Vec3 rotateAroundAxis(final Vec3 vector, final Vec3 axis, final double angleRadians) {
        final double cos = Math.cos(angleRadians);
        final double sin = Math.sin(angleRadians);
        return vector.scale(cos)
                .add(axis.cross(vector).scale(sin))
                .add(axis.scale(axis.dot(vector) * (1.0D - cos)));
    }
}
