package com.kieran.fpvfreecam.flight;

import com.kieran.fpvfreecam.FpvFreecam;
import com.kieran.fpvfreecam.config.DroneConfig;
import com.kieran.fpvfreecam.input.DroneInputMapper;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class DroneFlightController {
    private static final double FIXED_STEP_SECONDS = 1.0D / 240.0D;
    private static final double MAX_CATCH_UP_SECONDS = 0.050D;
    private static final int MAX_SUB_STEPS = 12;
    private static final double MAX_FRAME_SECONDS = 0.250D;
    private static final float KEY_CAMERA_ANGLE_RATE_DPS = 45.0F;
    private static final String[][] MOVEMENT_KEY_NAMES = new String[][]{
            {"keyUp", "upKey", "forwardKey"},
            {"keyDown", "downKey", "backKey"},
            {"keyLeft", "leftKey"},
            {"keyRight", "rightKey"},
            {"keyJump", "jumpKey"},
            {"keyShift", "shiftKey"},
            {"keySprint", "sprintKey"}
    };

    private final DroneConfig config;
    private final DroneInputMapper inputMapper;
    private final DroneSimulationState state = new DroneSimulationState();
    private final DroneRateModel rateModel = new DroneRateModel();
    private final DroneCrashModel crashModel = new DroneCrashModel();
    private final DronePhysicsModel physicsModel = new DronePhysicsModel(this.rateModel, this.crashModel);

    private long lastFrameTimeNanos = -1L;
    private double accumulatorSeconds;
    private boolean inactivityFpsOverrideActive;
    private @Nullable Object previousInactivityFpsLimit;
    private @Nullable Object inactivityFpsOption;
    private boolean inactivityFpsOverrideUnavailableLogged;

    public DroneFlightController(final DroneConfig config, final DroneInputMapper inputMapper) {
        this.config = config;
        this.inputMapper = inputMapper;
    }

    public void tick(final Minecraft minecraft) {
        if (!this.state.isActive()) {
            this.resetFrameTimer();
            return;
        }

        final var player = minecraft.player;
        final var level = minecraft.level;
        if (player == null || level == null) {
            this.forceDeactivate("missing_client_level");
            return;
        }

        if (this.shouldForceStop(player.level(), player.isDeadOrDying())) {
            this.forceDeactivate("player_state_changed");
        }
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

        DroneNetworkSafetyGuard.enforceClientOnlyMode(this.config);

        final DroneInputMapper.PollResult pollResult = this.inputMapper.poll(this.config, this.state.previousButtons());
        this.state.setInputAxes(
                pollResult.throttle(),
                pollResult.yaw(),
                pollResult.pitch(),
                pollResult.roll()
        );
        if (this.state.isActive() && !pollResult.connected()) {
            this.forceDeactivate("controller_disconnect");
            return;
        }

        if (this.state.isActive() && this.shouldForceStop(player.level(), player.isDeadOrDying())) {
            this.forceDeactivate("player_state_changed");
            return;
        }

        final long window = windowHandle(minecraft);
        final boolean escapeDown = window != 0L && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
        final boolean[] movementDown = this.readMovementKeybindState(minecraft);
        final boolean movementExitPressed = this.state.updateMovementExitEdge(movementDown);
        final boolean damageEdge = this.state.updateDamageExitEdge(player.getHealth());
        if (this.state.isActive() && escapeDown && !this.state.escapeDown()) {
            this.forceDeactivate("escape");
            this.state.setEscapeDown(true);
            return;
        }
        this.state.setEscapeDown(escapeDown);

        if (!this.state.isActive()) {
            if (pollResult.connected() && pollResult.armPressed() && minecraft.screen == null) {
                final String controllerName = pollResult.controller() == null ? "" : pollResult.controller().displayName();
                final float launchYaw = player.getYHeadRot();
                this.state.activate(
                        player.getEyePosition(),
                        launchYaw,
                        0.0F,
                        player.level().dimension(),
                        controllerName,
                        this.config.craftProfile.cameraAngleDeg,
                        player.getHealth(),
                        movementDown
                );
                this.resetFrameTimer();
                this.enableInactivityFpsOverride(minecraft);
            } else {
                this.resetFrameTimer();
            }
            return;
        }

        if (pollResult.resetPressed()) {
            this.forceDeactivate("button_reset");
            return;
        }
        if (movementExitPressed) {
            this.forceDeactivate("movement_keybind_exit");
            return;
        }
        if (this.config.crashSettings.exitToPlayerOnDamage && damageEdge) {
            this.forceDeactivate("damage_exit");
            return;
        }

        if (pollResult.disarmPressed() && this.state.isArmed()) {
            this.state.setArmed(false);
        } else if (pollResult.armPressed() && !this.state.isArmed() && !this.state.isCrashed()) {
            this.state.setArmed(true);
        }

        if (this.state.isCrashed()) {
            return;
        }

        final double frameSeconds = this.consumeFrameSeconds();
        this.applyKeyboardCameraAngle(window, frameSeconds);
        if (frameSeconds <= 0.0D) {
            return;
        }

        this.accumulatorSeconds = Math.min(this.accumulatorSeconds + frameSeconds, MAX_CATCH_UP_SECONDS);
        int subSteps = 0;
        while (this.accumulatorSeconds >= FIXED_STEP_SECONDS && subSteps < MAX_SUB_STEPS) {
            final DronePhysicsModel.StepOutcome stepOutcome = this.physicsModel.step(
                    this.state,
                    this.config,
                    pollResult,
                    player,
                    level,
                    FIXED_STEP_SECONDS
            );
            if (stepOutcome.crashed()) {
                this.handleCrash(stepOutcome);
                return;
            }

            this.accumulatorSeconds -= FIXED_STEP_SECONDS;
            subSteps++;
        }

        if (subSteps >= MAX_SUB_STEPS && this.accumulatorSeconds >= FIXED_STEP_SECONDS) {
            this.accumulatorSeconds = 0.0D;
        }

        this.state.setRenderAlpha((float) Mth.clamp(this.accumulatorSeconds / FIXED_STEP_SECONDS, 0.0D, 1.0D));
    }

    public boolean isActive() {
        return this.state.isActive();
    }

    public float getCameraYaw() {
        return this.state.cameraAngles().yaw();
    }

    public float getCameraPitch() {
        return this.state.cameraAngleDeg();
    }

    public float getCameraRoll() {
        return this.state.cameraAngles().roll();
    }

    public DroneCameraAngles getCameraAngles() {
        return this.state.cameraAngles();
    }

    public String getActiveControllerName() {
        return this.state.controllerName();
    }

    public @Nullable Vec3 getRenderCameraPosition(final float partialTick) {
        if (!this.state.isActive()) {
            return null;
        }
        return this.state.renderPosition(this.state.renderAlpha());
    }

    public @Nullable HudSnapshot getHudSnapshot() {
        if (!this.state.isActive()) {
            return null;
        }

        return new HudSnapshot(
                this.state.controllerName(),
                this.state.cameraAngleDeg(),
                (float) this.state.velocity().length(),
                this.state.motorThrottle() * 100.0F,
                this.state.batterySagLoss() * 100.0F,
                this.state.isArmed(),
                this.state.isCrashed(),
                this.state.rollRateDegPerSecond(),
                this.state.pitchRateDegPerSecond(),
                this.state.yawRateDegPerSecond(),
                this.state.lastImpactSpeed(),
                this.state.inputYaw(),
                this.state.inputThrottle(),
                this.state.inputRoll(),
                this.state.inputPitch()
        );
    }

    public void forceDeactivate(final String reason) {
        if (this.state.isActive()) {
            this.config.craftProfile.cameraAngleDeg = this.state.cameraAngleDeg();
            this.config.save();
        }
        this.state.deactivate();
        this.disableInactivityFpsOverride(Minecraft.getInstance());
        this.resetFrameTimer();
    }

    private void handleCrash(final DronePhysicsModel.StepOutcome stepOutcome) {
        this.state.markCrash(stepOutcome.impactSpeed(), stepOutcome.impactEnergy());
        switch (this.config.crashSettings.crashResetMode) {
            case EXIT_TO_PLAYER -> this.forceDeactivate("crash_exit_to_player");
            case QUICK_REARM -> {
                this.state.queueQuickRearmFromLaunch();
                this.forceDeactivate("crash_quick_rearm");
            }
            case CHECKPOINT_RESPAWN -> {
                final boolean restored = this.state.restoreCheckpoint();
                if (!restored) {
                    this.forceDeactivate("crash_no_checkpoint");
                }
            }
        }
    }

    private boolean shouldForceStop(final Level level, final boolean deadOrDying) {
        if (deadOrDying || this.state.dimension() == null) {
            return true;
        }
        return !this.state.dimension().equals(level.dimension());
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
        this.accumulatorSeconds = 0.0D;
        this.state.setRenderAlpha(1.0F);
    }

    private void applyKeyboardCameraAngle(final long window, final double frameSeconds) {
        if (window == 0L || frameSeconds <= 0.0D || !this.config.controller.allowInFlightCameraAngleAdjust) {
            return;
        }

        final boolean upPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS;
        final boolean downPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS;
        if (!(upPressed ^ downPressed)) {
            return;
        }

        final float direction = upPressed ? 1.0F : -1.0F;
        this.state.adjustCameraAngle(direction * KEY_CAMERA_ANGLE_RATE_DPS * (float) frameSeconds);
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

    private boolean[] readMovementKeybindState(final Minecraft minecraft) {
        final boolean[] down = new boolean[MOVEMENT_KEY_NAMES.length];
        final Object options = minecraft.options;
        for (int index = 0; index < MOVEMENT_KEY_NAMES.length; index++) {
            down[index] = this.resolveKeyDown(options, MOVEMENT_KEY_NAMES[index]);
        }
        return down;
    }

    private boolean resolveKeyDown(final Object options, final String[] candidates) {
        for (final String name : candidates) {
            try {
                final Field field = options.getClass().getDeclaredField(name);
                field.setAccessible(true);
                final Object keyMapping = field.get(options);
                if (keyMapping != null) {
                    return this.keyMappingIsDown(keyMapping);
                }
            } catch (final ReflectiveOperationException ignored) {
            }
        }
        for (final String name : candidates) {
            try {
                final Method method = options.getClass().getMethod(name);
                final Object keyMapping = method.invoke(options);
                if (keyMapping != null) {
                    return this.keyMappingIsDown(keyMapping);
                }
            } catch (final ReflectiveOperationException ignored) {
            }
        }
        return false;
    }

    private boolean keyMappingIsDown(final Object keyMapping) {
        for (final String methodName : new String[]{"isDown", "isPressed"}) {
            try {
                final Method method = keyMapping.getClass().getMethod(methodName);
                final Object result = method.invoke(keyMapping);
                if (result instanceof Boolean down) {
                    return down;
                }
            } catch (final ReflectiveOperationException ignored) {
            }
        }
        return false;
    }

    private void enableInactivityFpsOverride(final Minecraft minecraft) {
        if (this.inactivityFpsOverrideActive) {
            return;
        }

        try {
            final Object option = this.findInactivityFpsOption(minecraft);
            if (option == null) {
                this.logInactivityFpsOverrideUnavailable(new IllegalStateException("inactivity FPS option not found"));
                return;
            }

            final Object currentValue = this.optionGet(option);
            final Object overrideValue = this.selectOverrideValue(currentValue);
            if (currentValue == null || overrideValue == null) {
                this.logInactivityFpsOverrideUnavailable(new IllegalStateException("inactivity FPS option values unavailable"));
                return;
            }

            this.previousInactivityFpsLimit = currentValue;
            this.inactivityFpsOption = option;
            if (currentValue != overrideValue) {
                this.optionSet(option, overrideValue);
            }
            this.inactivityFpsOverrideActive = true;
        } catch (final Throwable throwable) {
            this.logInactivityFpsOverrideUnavailable(throwable);
            this.inactivityFpsOverrideActive = false;
            this.previousInactivityFpsLimit = null;
            this.inactivityFpsOption = null;
        }
    }

    private void disableInactivityFpsOverride(final Minecraft minecraft) {
        if (!this.inactivityFpsOverrideActive) {
            return;
        }

        try {
            if (this.previousInactivityFpsLimit != null && this.inactivityFpsOption != null) {
                this.optionSet(this.inactivityFpsOption, this.previousInactivityFpsLimit);
            }
        } catch (final Throwable throwable) {
            this.logInactivityFpsOverrideUnavailable(throwable);
        } finally {
            this.inactivityFpsOverrideActive = false;
            this.previousInactivityFpsLimit = null;
            this.inactivityFpsOption = null;
        }
    }

    private @Nullable Object findInactivityFpsOption(final Minecraft minecraft) {
        final Object options = minecraft.options;
        for (final String methodName : new String[]{"inactivityFpsLimit", "getInactivityFpsLimit"}) {
            try {
                final Method method = options.getClass().getMethod(methodName);
                final Object value = method.invoke(options);
                if (value != null) {
                    return value;
                }
            } catch (final ReflectiveOperationException ignored) {
            }
        }

        for (final String fieldName : new String[]{"inactivityFpsLimit"}) {
            try {
                final Field field = options.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                final Object value = field.get(options);
                if (value != null) {
                    return value;
                }
            } catch (final ReflectiveOperationException ignored) {
            }
        }

        for (final Field field : options.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                final Object candidate = field.get(options);
                if (this.isInactivityFpsOptionCandidate(candidate)) {
                    return candidate;
                }
            } catch (final ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    private boolean isInactivityFpsOptionCandidate(final @Nullable Object candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            final Object value = this.optionGet(candidate);
            if (!(value instanceof Enum<?> currentEnum)) {
                return false;
            }
            final Class<?> enumType = currentEnum.getDeclaringClass();
            return this.hasEnumConstant(enumType, "AFK") || this.hasEnumConstant(enumType, "MINIMIZED");
        } catch (final ReflectiveOperationException ignored) {
            return false;
        }
    }

    private Object optionGet(final Object optionInstance) throws ReflectiveOperationException {
        for (final String getterName : new String[]{"get", "getValue"}) {
            try {
                final Method getter = optionInstance.getClass().getMethod(getterName);
                return getter.invoke(optionInstance);
            } catch (final NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException("No option getter found");
    }

    private void optionSet(final Object optionInstance, final Object value) throws ReflectiveOperationException {
        for (final String setterName : new String[]{"set", "setValue"}) {
            for (final Method method : optionInstance.getClass().getMethods()) {
                if (!method.getName().equals(setterName) || method.getParameterCount() != 1) {
                    continue;
                }
                final Class<?> parameterType = method.getParameterTypes()[0];
                if (parameterType.isInstance(value) || (parameterType.isEnum() && value.getClass().isEnum())) {
                    method.invoke(optionInstance, value);
                    return;
                }
            }
        }
        throw new NoSuchMethodException("No compatible option setter found");
    }

    private @Nullable Object selectOverrideValue(final Object currentValue) {
        if (!(currentValue instanceof Enum<?> currentEnum)) {
            return null;
        }

        final Class<?> enumType = currentEnum.getDeclaringClass();
        Object firstNonAfk = null;
        for (final Object constant : enumType.getEnumConstants()) {
            if (!(constant instanceof Enum<?> enumConstant)) {
                continue;
            }
            if ("MINIMIZED".equals(enumConstant.name())) {
                return constant;
            }
            if (!"AFK".equals(enumConstant.name()) && firstNonAfk == null) {
                firstNonAfk = constant;
            }
        }
        return firstNonAfk != null ? firstNonAfk : currentValue;
    }

    private boolean hasEnumConstant(final Class<?> enumType, final String constantName) {
        for (final Object constant : enumType.getEnumConstants()) {
            if (constant instanceof Enum<?> enumConstant && constantName.equals(enumConstant.name())) {
                return true;
            }
        }
        return false;
    }

    private void logInactivityFpsOverrideUnavailable(final Throwable throwable) {
        if (this.inactivityFpsOverrideUnavailableLogged) {
            return;
        }
        this.inactivityFpsOverrideUnavailableLogged = true;
        FpvFreecam.LOGGER.warn("FPV inactivity FPS override unavailable on this runtime; continuing without override.", throwable);
    }

    public record HudSnapshot(
            String controllerName,
            float cameraAngleDeg,
            float speedMps,
            float throttlePercent,
            float sagPercent,
            boolean armed,
            boolean crashed,
            float rollRateDegPerSecond,
            float pitchRateDegPerSecond,
            float yawRateDegPerSecond,
            float impactSpeed,
            float yawInput,
            float throttleInput,
            float rollInput,
            float pitchInput
    ) {
    }
}
