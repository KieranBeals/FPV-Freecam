package com.kieran.clientdronecam.input;

import com.kieran.clientdronecam.config.DroneConfig;
import com.kieran.clientdronecam.flight.DroneCameraState;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DroneInputMapper {
    private static final int HAT_BUTTON_BASE = 128;
    private static final int HAT_DIRECTIONS = 4;
    private static final int AXIS_BUTTON_BASE = 160;
    private static final int AXIS_BUTTONS_PER_AXIS = 2;
    private static final float BUTTON_AXIS_THRESHOLD = 0.6F;
    private static final float BUTTON_CAPTURE_DELTA = 0.45F;
    private static final float AXIS_CAPTURE_DELTA = 0.35F;

    public List<ControllerInfo> getAvailableControllers() {
        final List<ControllerInfo> controllers = new ArrayList<>();

        for (int joystickId = GLFW.GLFW_JOYSTICK_1; joystickId <= GLFW.GLFW_JOYSTICK_LAST; joystickId++) {
            if (!GLFW.glfwJoystickPresent(joystickId)) {
                continue;
            }

            final boolean gamepadMapped = GLFW.glfwJoystickIsGamepad(joystickId);
            controllers.add(new ControllerInfo(
                    joystickId,
                    safe(GLFW.glfwGetJoystickGUID(joystickId)),
                    gamepadMapped ? safe(GLFW.glfwGetGamepadName(joystickId)) : safe(GLFW.glfwGetJoystickName(joystickId)),
                    gamepadMapped ? safe(GLFW.glfwGetJoystickName(joystickId)) : "",
                    gamepadMapped
            ));
        }

        controllers.sort(Comparator.comparing(ControllerInfo::displayName, String.CASE_INSENSITIVE_ORDER));
        return controllers;
    }

    public @Nullable ControllerInfo resolveConfiguredController(final DroneConfig config) {
        final List<ControllerInfo> controllers = this.getAvailableControllers();
        if (controllers.isEmpty()) {
            return null;
        }

        for (final ControllerInfo controller : controllers) {
            if (!config.controllerGuid.isBlank()
                    && config.controllerGuid.equals(controller.guid())
                    && this.matchesConfiguredName(controller, config.controllerName)) {
                return controller;
            }
        }

        for (final ControllerInfo controller : controllers) {
            if (!config.controllerGuid.isBlank() && config.controllerGuid.equals(controller.guid())) {
                return controller;
            }
        }

        for (final ControllerInfo controller : controllers) {
            if (!config.controllerName.isBlank() && config.controllerName.equals(controller.name())) {
                return controller;
            }
        }

        for (final ControllerInfo controller : controllers) {
            if (!config.controllerName.isBlank() && config.controllerName.equals(controller.displayName())) {
                return controller;
            }
        }

        for (final ControllerInfo controller : controllers) {
            if (!config.controllerName.isBlank() && config.controllerName.equals(controller.alternateName())) {
                return controller;
            }
        }

        return null;
    }

    private boolean matchesConfiguredName(final ControllerInfo controller, final String configuredName) {
        if (configuredName.isBlank()) {
            return false;
        }

        return configuredName.equals(controller.name())
                || configuredName.equals(controller.displayName())
                || configuredName.equals(controller.alternateName());
    }

    public PollResult poll(final DroneConfig config, final DroneCameraState state) {
        final ControllerInfo controller = this.resolveConfiguredController(config);
        if (controller == null) {
            state.clearButtons();
            return PollResult.disconnected();
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (controller.gamepadMapped()) {
                final GLFWGamepadState gamepadState = GLFWGamepadState.malloc(stack);
                if (!GLFW.glfwGetGamepadState(controller.joystickId(), gamepadState)) {
                    state.clearButtons();
                    return PollResult.disconnected();
                }

                final boolean togglePressed = this.edgePressed(gamepadState, state.previousButtons(), config.toggleButton);
                final boolean exitPressed = this.edgePressed(gamepadState, state.previousButtons(), config.exitButton);
                state.captureButtons(gamepadState);

                return new PollResult(
                        true,
                        controller,
                        togglePressed,
                        exitPressed,
                        this.readAxis(gamepadState, config.axisThrottle, config.axisThrottleMin, config.axisThrottleMax, config.deadzone, config.invertThrottle),
                        this.readAxis(gamepadState, config.axisYaw, config.axisYawMin, config.axisYawMax, config.deadzone, config.invertYaw),
                        this.readAxis(gamepadState, config.axisPitch, config.axisPitchMin, config.axisPitchMax, config.deadzone, config.invertPitch),
                        this.readAxis(gamepadState, config.axisRoll, config.axisRollMin, config.axisRollMax, config.deadzone, config.invertRoll)
                );
            }

            final FloatBuffer axes = GLFW.glfwGetJoystickAxes(controller.joystickId());
            final ByteBuffer buttons = GLFW.glfwGetJoystickButtons(controller.joystickId());
            final ByteBuffer hats = GLFW.glfwGetJoystickHats(controller.joystickId());
            if (axes == null) {
                state.clearButtons();
                return PollResult.disconnected();
            }

            final boolean togglePressed = this.edgePressed(buttons, hats, axes, state.previousButtons(), config.toggleButton);
            final boolean exitPressed = this.edgePressed(buttons, hats, axes, state.previousButtons(), config.exitButton);
            state.captureButtons(buttons, hats, axes);

            return new PollResult(
                    true,
                    controller,
                    togglePressed,
                    exitPressed,
                    this.readAxis(axes, config.axisThrottle, config.axisThrottleMin, config.axisThrottleMax, config.deadzone, config.invertThrottle),
                    this.readAxis(axes, config.axisYaw, config.axisYawMin, config.axisYawMax, config.deadzone, config.invertYaw),
                    this.readAxis(axes, config.axisPitch, config.axisPitchMin, config.axisPitchMax, config.deadzone, config.invertPitch),
                    this.readAxis(axes, config.axisRoll, config.axisRollMin, config.axisRollMax, config.deadzone, config.invertRoll)
            );
        }
    }

    public @Nullable ButtonCaptureSnapshot createButtonCaptureSnapshot(final DroneConfig config) {
        final ControllerInfo controller = this.resolveConfiguredController(config);
        if (controller == null) {
            return null;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (controller.gamepadMapped()) {
                final GLFWGamepadState gamepadState = GLFWGamepadState.malloc(stack);
                if (!GLFW.glfwGetGamepadState(controller.joystickId(), gamepadState)) {
                    return null;
                }

                final float[] axes = new float[GLFW.GLFW_GAMEPAD_AXIS_LAST + 1];
                for (int axis = 0; axis < axes.length; axis++) {
                    axes[axis] = gamepadState.axes(axis);
                }
                return new ButtonCaptureSnapshot(axes);
            }

            final FloatBuffer axes = GLFW.glfwGetJoystickAxes(controller.joystickId());
            if (axes == null) {
                return null;
            }

            final float[] snapshotAxes = new float[axes.limit()];
            for (int axis = 0; axis < axes.limit(); axis++) {
                snapshotAxes[axis] = axes.get(axis);
            }
            return new ButtonCaptureSnapshot(snapshotAxes);
        }
    }

    public @Nullable Integer detectPressedButton(final DroneConfig config) {
        return this.detectPressedButton(config, null);
    }

    public @Nullable Integer detectPressedButton(final DroneConfig config, final @Nullable ButtonCaptureSnapshot snapshot) {
        final ControllerInfo controller = this.resolveConfiguredController(config);
        if (controller == null) {
            return null;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (controller.gamepadMapped()) {
                final GLFWGamepadState gamepadState = GLFWGamepadState.malloc(stack);
                if (!GLFW.glfwGetGamepadState(controller.joystickId(), gamepadState)) {
                    return null;
                }

                for (int button = 0; button <= GLFW.GLFW_GAMEPAD_BUTTON_LAST; button++) {
                    if (gamepadState.buttons(button) == GLFW.GLFW_PRESS) {
                        return button;
                    }
                }

                return this.detectChangedAxis(gamepadState, snapshot);
            }

            final ByteBuffer buttons = GLFW.glfwGetJoystickButtons(controller.joystickId());
            final ByteBuffer hats = GLFW.glfwGetJoystickHats(controller.joystickId());
            final FloatBuffer axes = GLFW.glfwGetJoystickAxes(controller.joystickId());
            if (buttons == null && hats == null && axes == null) {
                return null;
            }

            if (buttons != null) {
                for (int button = 0; button < buttons.limit(); button++) {
                    if (buttons.get(button) == GLFW.GLFW_PRESS) {
                        return button;
                    }
                }
            }

            if (hats != null) {
                for (int hat = 0; hat < hats.limit(); hat++) {
                    for (int direction = 0; direction < HAT_DIRECTIONS; direction++) {
                        final int syntheticButton = hatButtonId(hat, direction);
                        if (isHatButtonPressed(hats, syntheticButton)) {
                            return syntheticButton;
                        }
                    }
                }
            }

            return this.detectChangedAxis(axes, snapshot);
        }
    }

    public @Nullable Integer detectMovedAxis(final DroneConfig config) {
        return this.detectMovedAxis(config, null);
    }

    public @Nullable Integer detectMovedAxis(final DroneConfig config, final @Nullable ButtonCaptureSnapshot snapshot) {
        final ControllerInfo controller = this.resolveConfiguredController(config);
        if (controller == null) {
            return null;
        }

        float strongest = AXIS_CAPTURE_DELTA;
        Integer result = null;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (controller.gamepadMapped()) {
                final GLFWGamepadState gamepadState = GLFWGamepadState.malloc(stack);
                if (!GLFW.glfwGetGamepadState(controller.joystickId(), gamepadState)) {
                    return null;
                }

                for (int axis = 0; axis <= GLFW.GLFW_GAMEPAD_AXIS_LAST; axis++) {
                    final float current = gamepadState.axes(axis);
                    final float baseline = snapshot == null ? 0.0F : snapshot.axisValue(axis);
                    final float delta = Math.abs(current - baseline);
                    if (delta > strongest) {
                        strongest = delta;
                        result = axis;
                    }
                }

                return result;
            }

            final FloatBuffer axes = GLFW.glfwGetJoystickAxes(controller.joystickId());
            if (axes == null) {
                return null;
            }

            for (int axis = 0; axis < axes.limit(); axis++) {
                final float current = axes.get(axis);
                final float baseline = snapshot == null ? 0.0F : snapshot.axisValue(axis);
                final float delta = Math.abs(current - baseline);
                if (delta > strongest) {
                    strongest = delta;
                    result = axis;
                }
            }
        }

        return result;
    }

    public float readRawAxis(final DroneConfig config, final int axis) {
        final ControllerInfo controller = this.resolveConfiguredController(config);
        if (controller == null) {
            return 0.0F;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (controller.gamepadMapped()) {
                final GLFWGamepadState gamepadState = GLFWGamepadState.malloc(stack);
                if (!GLFW.glfwGetGamepadState(controller.joystickId(), gamepadState) || axis < 0 || axis > GLFW.GLFW_GAMEPAD_AXIS_LAST) {
                    return 0.0F;
                }

                return gamepadState.axes(axis);
            }

            final FloatBuffer axes = GLFW.glfwGetJoystickAxes(controller.joystickId());
            if (axes == null || axis < 0 || axis >= axes.limit()) {
                return 0.0F;
            }

            return axes.get(axis);
        }
    }

    public @Nullable RawInputSnapshot snapshotRawInputs(final DroneConfig config) {
        final ControllerInfo controller = this.resolveConfiguredController(config);
        if (controller == null) {
            return null;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final float[] rawAxes = copyAxes(GLFW.glfwGetJoystickAxes(controller.joystickId()));
            final byte[] rawButtons = copyBytes(GLFW.glfwGetJoystickButtons(controller.joystickId()));
            final byte[] rawHats = copyBytes(GLFW.glfwGetJoystickHats(controller.joystickId()));

            float[] mappedAxes = new float[0];
            byte[] mappedButtons = new byte[0];
            if (controller.gamepadMapped()) {
                final GLFWGamepadState gamepadState = GLFWGamepadState.malloc(stack);
                if (GLFW.glfwGetGamepadState(controller.joystickId(), gamepadState)) {
                    mappedAxes = new float[GLFW.GLFW_GAMEPAD_AXIS_LAST + 1];
                    for (int axis = 0; axis < mappedAxes.length; axis++) {
                        mappedAxes[axis] = gamepadState.axes(axis);
                    }

                    mappedButtons = new byte[GLFW.GLFW_GAMEPAD_BUTTON_LAST + 1];
                    for (int button = 0; button < mappedButtons.length; button++) {
                        mappedButtons[button] = gamepadState.buttons(button);
                    }
                }
            }

            return new RawInputSnapshot(controller, rawAxes, rawButtons, rawHats, mappedAxes, mappedButtons);
        }
    }

    private boolean edgePressed(final GLFWGamepadState gamepadState, final boolean[] previousButtons, final int button) {
        if (button < 0 || button >= previousButtons.length) {
            return false;
        }

        final boolean pressed = button <= GLFW.GLFW_GAMEPAD_BUTTON_LAST
                ? gamepadState.buttons(button) == GLFW.GLFW_PRESS
                : isAxisButtonPressed(gamepadState, button);
        return pressed && !previousButtons[button];
    }

    private boolean edgePressed(final @Nullable ByteBuffer buttons, final @Nullable ByteBuffer hats, final @Nullable FloatBuffer axes, final boolean[] previousButtons, final int button) {
        if (button < 0 || button >= previousButtons.length) {
            return false;
        }

        final boolean pressed;
        if (button < HAT_BUTTON_BASE) {
            pressed = buttons != null && button < buttons.limit() && buttons.get(button) == GLFW.GLFW_PRESS;
        } else if (button < AXIS_BUTTON_BASE) {
            pressed = isHatButtonPressed(hats, button);
        } else {
            pressed = isAxisButtonPressed(axes, button);
        }
        return pressed && !previousButtons[button];
    }

    private float readAxis(final GLFWGamepadState gamepadState, final int axis, final float min, final float max, final float deadzone, final boolean invert) {
        if (axis < 0 || axis > GLFW.GLFW_GAMEPAD_AXIS_LAST) {
            return 0.0F;
        }

        float value = normalizeAxis(gamepadState.axes(axis), min, max);
        final float magnitude = Math.abs(value);
        if (magnitude <= deadzone) {
            return 0.0F;
        }

        value = Math.copySign((magnitude - deadzone) / (1.0F - deadzone), value);
        return invert ? -value : value;
    }

    private float readAxis(final FloatBuffer axes, final int axis, final float min, final float max, final float deadzone, final boolean invert) {
        if (axis < 0 || axis >= axes.limit()) {
            return 0.0F;
        }

        float value = normalizeAxis(axes.get(axis), min, max);
        final float magnitude = Math.abs(value);
        if (magnitude <= deadzone) {
            return 0.0F;
        }

        value = Math.copySign((magnitude - deadzone) / (1.0F - deadzone), value);
        return invert ? -value : value;
    }

    private static String safe(@Nullable final String value) {
        return value == null ? "" : value;
    }

    private static float[] copyAxes(final @Nullable FloatBuffer axes) {
        if (axes == null) {
            return new float[0];
        }

        final float[] copy = new float[axes.limit()];
        for (int index = 0; index < axes.limit(); index++) {
            copy[index] = axes.get(index);
        }
        return copy;
    }

    private static byte[] copyBytes(final @Nullable ByteBuffer buffer) {
        if (buffer == null) {
            return new byte[0];
        }

        final byte[] copy = new byte[buffer.limit()];
        for (int index = 0; index < buffer.limit(); index++) {
            copy[index] = buffer.get(index);
        }
        return copy;
    }

    private static float normalizeAxis(final float value, final float min, final float max) {
        if (max <= min) {
            return value;
        }

        final float clamped = Math.max(min, Math.min(max, value));
        return ((clamped - min) / (max - min)) * 2.0F - 1.0F;
    }

    public static String axisName(final int axis) {
        return switch (axis) {
            case GLFW.GLFW_GAMEPAD_AXIS_LEFT_X -> "Left Stick X";
            case GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y -> "Left Stick Y";
            case GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X -> "Right Stick X";
            case GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y -> "Right Stick Y";
            case GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER -> "Left Trigger";
            case GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER -> "Right Trigger";
            default -> "Axis " + axis;
        };
    }

    public static String buttonName(final int button) {
        if (button >= AXIS_BUTTON_BASE) {
            final int axis = (button - AXIS_BUTTON_BASE) / AXIS_BUTTONS_PER_AXIS;
            final boolean positive = (button - AXIS_BUTTON_BASE) % AXIS_BUTTONS_PER_AXIS == 1;
            return axisName(axis) + (positive ? " +" : " -");
        }

        if (button >= HAT_BUTTON_BASE) {
            final int hatIndex = (button - HAT_BUTTON_BASE) / HAT_DIRECTIONS;
            final int direction = (button - HAT_BUTTON_BASE) % HAT_DIRECTIONS;
            final String directionName = switch (direction) {
                case 0 -> "Up";
                case 1 -> "Right";
                case 2 -> "Down";
                case 3 -> "Left";
                default -> "Unknown";
            };
            return "Hat " + (hatIndex + 1) + " " + directionName;
        }

        return switch (button) {
            case GLFW.GLFW_GAMEPAD_BUTTON_A -> "A";
            case GLFW.GLFW_GAMEPAD_BUTTON_B -> "B";
            case GLFW.GLFW_GAMEPAD_BUTTON_X -> "X";
            case GLFW.GLFW_GAMEPAD_BUTTON_Y -> "Y";
            case GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER -> "LB";
            case GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER -> "RB";
            case GLFW.GLFW_GAMEPAD_BUTTON_BACK -> "Back";
            case GLFW.GLFW_GAMEPAD_BUTTON_START -> "Start";
            case GLFW.GLFW_GAMEPAD_BUTTON_GUIDE -> "Guide";
            case GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB -> "L3";
            case GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB -> "R3";
            case GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP -> "D-Pad Up";
            case GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT -> "D-Pad Right";
            case GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN -> "D-Pad Down";
            case GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT -> "D-Pad Left";
            default -> "Button " + button;
        };
    }

    public static boolean isHatButtonPressed(final @Nullable ByteBuffer hats, final int button) {
        if (hats == null || button < HAT_BUTTON_BASE) {
            return false;
        }

        final int hatButton = button - HAT_BUTTON_BASE;
        final int hatIndex = hatButton / HAT_DIRECTIONS;
        if (hatIndex < 0 || hatIndex >= hats.limit()) {
            return false;
        }

        final int direction = hatButton % HAT_DIRECTIONS;
        final int mask = hats.get(hatIndex) & 0xFF;
        return (mask & hatDirectionMask(direction)) != 0;
    }

    public static boolean isAxisButtonPressed(final GLFWGamepadState gamepadState, final int button) {
        if (button < AXIS_BUTTON_BASE) {
            return false;
        }

        final int axis = (button - AXIS_BUTTON_BASE) / AXIS_BUTTONS_PER_AXIS;
        if (axis < 0 || axis > GLFW.GLFW_GAMEPAD_AXIS_LAST) {
            return false;
        }

        final float value = gamepadState.axes(axis);
        return isAxisButtonPressed(value, button);
    }

    public static boolean isAxisButtonPressed(final @Nullable FloatBuffer axes, final int button) {
        if (axes == null || button < AXIS_BUTTON_BASE) {
            return false;
        }

        final int axis = (button - AXIS_BUTTON_BASE) / AXIS_BUTTONS_PER_AXIS;
        if (axis < 0 || axis >= axes.limit()) {
            return false;
        }

        final float value = axes.get(axis);
        return isAxisButtonPressed(value, button);
    }

    private @Nullable Integer detectChangedAxis(final GLFWGamepadState gamepadState, final @Nullable ButtonCaptureSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        for (int axis = 0; axis <= GLFW.GLFW_GAMEPAD_AXIS_LAST; axis++) {
            final float baseline = snapshot.axisValue(axis);
            final float current = gamepadState.axes(axis);
            final float delta = current - baseline;
            if (Math.abs(delta) >= BUTTON_CAPTURE_DELTA) {
                return axisButtonId(axis, delta > 0.0F);
            }
        }

        return null;
    }

    private @Nullable Integer detectChangedAxis(final @Nullable FloatBuffer axes, final @Nullable ButtonCaptureSnapshot snapshot) {
        if (axes == null || snapshot == null) {
            return null;
        }

        for (int axis = 0; axis < axes.limit(); axis++) {
            final float baseline = snapshot.axisValue(axis);
            final float current = axes.get(axis);
            final float delta = current - baseline;
            if (Math.abs(delta) >= BUTTON_CAPTURE_DELTA) {
                return axisButtonId(axis, delta > 0.0F);
            }
        }

        return null;
    }

    private static boolean isAxisButtonPressed(final float value, final int button) {
        final boolean positive = (button - AXIS_BUTTON_BASE) % AXIS_BUTTONS_PER_AXIS == 1;
        return positive ? value >= BUTTON_AXIS_THRESHOLD : value <= -BUTTON_AXIS_THRESHOLD;
    }

    private static int hatButtonId(final int hatIndex, final int direction) {
        return HAT_BUTTON_BASE + hatIndex * HAT_DIRECTIONS + direction;
    }

    private static int axisButtonId(final int axis, final boolean positive) {
        return AXIS_BUTTON_BASE + axis * AXIS_BUTTONS_PER_AXIS + (positive ? 1 : 0);
    }

    private static int hatDirectionMask(final int direction) {
        return switch (direction) {
            case 0 -> GLFW.GLFW_HAT_UP;
            case 1 -> GLFW.GLFW_HAT_RIGHT;
            case 2 -> GLFW.GLFW_HAT_DOWN;
            case 3 -> GLFW.GLFW_HAT_LEFT;
            default -> 0;
        };
    }

    public record ControllerInfo(int joystickId, String guid, String name, String alternateName, boolean gamepadMapped) {
        public String displayName() {
            if (!this.name.isBlank()) {
                return this.name;
            }

            if (!this.alternateName.isBlank()) {
                return this.alternateName;
            }

            return "Joystick " + (this.joystickId + 1);
        }
    }

    public record ButtonCaptureSnapshot(float[] axes) {
        public float axisValue(final int axis) {
            if (axis < 0 || axis >= this.axes.length) {
                return 0.0F;
            }

            return this.axes[axis];
        }
    }

    public record RawInputSnapshot(
            ControllerInfo controller,
            float[] rawAxes,
            byte[] rawButtons,
            byte[] rawHats,
            float[] mappedAxes,
            byte[] mappedButtons
    ) {
    }

    public record PollResult(
            boolean connected,
            @Nullable ControllerInfo controller,
            boolean togglePressed,
            boolean exitPressed,
            float throttle,
            float yaw,
            float pitch,
            float roll
    ) {
        public static PollResult disconnected() {
            return new PollResult(false, null, false, false, 0.0F, 0.0F, 0.0F, 0.0F);
        }
    }
}
