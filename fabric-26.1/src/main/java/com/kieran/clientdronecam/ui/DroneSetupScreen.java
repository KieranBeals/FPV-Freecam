package com.kieran.clientdronecam.ui;

import com.kieran.clientdronecam.ClientDroneCam;
import com.kieran.clientdronecam.config.DroneConfig;
import com.kieran.clientdronecam.input.DroneInputMapper;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DroneSetupScreen extends Screen {
    private static final long CAPTURE_GRACE_MS = 200L;
    private static final long AXIS_CALIBRATION_MS = 2500L;
    private static final float AXIS_CALIBRATION_RANGE_MIN = 0.5F;

    private final Screen parent;
    private final DroneConfig workingConfig;
    private final LinearLayout layout = LinearLayout.vertical().spacing(6);

    private Button controllerButton;
    private Button toggleButton;
    private Button exitButton;
    private Button throttleAxisButton;
    private Button yawAxisButton;
    private Button pitchAxisButton;
    private Button rollAxisButton;
    private Button throttleInvertButton;
    private Button yawInvertButton;
    private Button pitchInvertButton;
    private Button rollInvertButton;
    private EditBox deadzoneBox;
    private CaptureTarget captureTarget = CaptureTarget.NONE;
    private long captureStartTime;
    private @Nullable DroneInputMapper.ButtonCaptureSnapshot buttonCaptureSnapshot;
    private @Nullable AxisCalibrationSession axisCalibrationSession;

    public DroneSetupScreen(@Nullable final Screen parent) {
        super(Component.translatable("screen.clientdronecam.setup"));
        this.parent = parent;
        this.workingConfig = ClientDroneCam.CONFIG.copy();
    }

    @Override
    protected void init() {
        super.init();
        this.layout.defaultCellSetting().alignHorizontallyCenter();
        this.layout.addChild(new StringWidget(this.title, this.font));
        this.layout.addChild(new MultiLineTextWidget(Component.literal("Configure the selected controller and drone bindings."), this.font).setMaxWidth(320).setCentered(true));

        this.controllerButton = this.layout.addChild(Button.builder(Component.empty(), button -> {
            this.cycleController();
            this.saveAndRefresh();
        }).width(310).build());

        final LinearLayout buttonsRow = this.layout.addChild(LinearLayout.horizontal().spacing(4));
        this.toggleButton = buttonsRow.addChild(Button.builder(Component.empty(), button -> this.startButtonCapture(CaptureTarget.TOGGLE_BUTTON)).width(150).build());
        this.exitButton = buttonsRow.addChild(Button.builder(Component.empty(), button -> this.startButtonCapture(CaptureTarget.EXIT_BUTTON)).width(150).build());

        this.layout.addChild(this.axisRow("Throttle", true));
        this.layout.addChild(this.axisRow("Pitch", false));

        this.deadzoneBox = this.layout.addChild(new EditBox(this.font, 90, 20, Component.translatable("screen.clientdronecam.deadzone")));
        this.deadzoneBox.setValue(String.format("%.2f", this.workingConfig.deadzone));
        this.deadzoneBox.setResponder(this::handleDeadzoneEdited);

        final LinearLayout footer = this.layout.addChild(LinearLayout.horizontal().spacing(4));
        footer.addChild(Button.builder(Component.translatable("screen.clientdronecam.done"), button -> this.onClose()).width(90).build());

        this.layout.visitWidgets(this::addRenderableWidget);
        this.refreshLabels();
        this.repositionElements();
    }

    private LinearLayout axisRow(final String label, final boolean leftStick) {
        final LinearLayout row = LinearLayout.horizontal().spacing(4);
        if (leftStick) {
            this.throttleAxisButton = row.addChild(Button.builder(Component.empty(), button -> this.startAxisCapture(CaptureTarget.THROTTLE_AXIS)).width(110).build());
            this.throttleInvertButton = row.addChild(Button.builder(Component.empty(), button -> {
                this.workingConfig.invertThrottle = !this.workingConfig.invertThrottle;
                this.saveAndRefresh();
            }).width(90).build());
            this.yawAxisButton = row.addChild(Button.builder(Component.empty(), button -> this.startAxisCapture(CaptureTarget.YAW_AXIS)).width(110).build());
            this.yawInvertButton = row.addChild(Button.builder(Component.empty(), button -> {
                this.workingConfig.invertYaw = !this.workingConfig.invertYaw;
                this.saveAndRefresh();
            }).width(90).build());
        } else {
            this.pitchAxisButton = row.addChild(Button.builder(Component.empty(), button -> this.startAxisCapture(CaptureTarget.PITCH_AXIS)).width(110).build());
            this.pitchInvertButton = row.addChild(Button.builder(Component.empty(), button -> {
                this.workingConfig.invertPitch = !this.workingConfig.invertPitch;
                this.saveAndRefresh();
            }).width(90).build());
            this.rollAxisButton = row.addChild(Button.builder(Component.empty(), button -> this.startAxisCapture(CaptureTarget.ROLL_AXIS)).width(110).build());
            this.rollInvertButton = row.addChild(Button.builder(Component.empty(), button -> {
                this.workingConfig.invertRoll = !this.workingConfig.invertRoll;
                this.saveAndRefresh();
            }).width(90).build());
        }
        return row;
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        FrameLayout.centerInRectangle(this.layout, this.getRectangle());
    }

    @Override
    public void tick() {
        super.tick();

        if (this.captureTarget == CaptureTarget.NONE) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - this.captureStartTime < CAPTURE_GRACE_MS) {
            return;
        }

        if (this.captureTarget.isAxis()) {
            this.tickAxisCapture(now);
            return;
        }

        final Integer capture = ClientDroneCam.INPUT_MAPPER.detectPressedButton(this.workingConfig, this.buttonCaptureSnapshot);

        if (capture == null) {
            return;
        }

        switch (this.captureTarget) {
            case TOGGLE_BUTTON -> this.workingConfig.toggleButton = capture;
            case EXIT_BUTTON -> this.workingConfig.exitButton = capture;
            case THROTTLE_AXIS, YAW_AXIS, PITCH_AXIS, ROLL_AXIS -> {
            }
            case NONE -> {
            }
        }

        this.captureTarget = CaptureTarget.NONE;
        this.buttonCaptureSnapshot = null;
        this.saveAndRefresh();
    }

    @Override
    public boolean keyPressed(final KeyEvent event) {
        if (this.captureTarget != CaptureTarget.NONE && event.isEscape()) {
            this.captureTarget = CaptureTarget.NONE;
            this.buttonCaptureSnapshot = null;
            this.axisCalibrationSession = null;
            this.refreshLabels();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.saveConfig();
        this.minecraft.setScreen(this.parent);
    }

    private void handleDeadzoneEdited(final String value) {
        try {
            this.workingConfig.deadzone = Float.parseFloat(value);
            this.saveConfig();
        } catch (final NumberFormatException ignored) {
        }
    }

    private void cycleController() {
        final List<DroneInputMapper.ControllerInfo> controllers = ClientDroneCam.INPUT_MAPPER.getAvailableControllers();
        if (controllers.isEmpty()) {
            this.workingConfig.clearControllerSelection();
            return;
        }

        final int currentIndex = this.findCurrentControllerIndex(controllers);
        final int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % controllers.size();
        final DroneInputMapper.ControllerInfo controller = controllers.get(nextIndex);
        this.workingConfig.setController(controller.guid(), controller.displayName());
    }

    private int findCurrentControllerIndex(final List<DroneInputMapper.ControllerInfo> controllers) {
        final DroneInputMapper.ControllerInfo selected = ClientDroneCam.INPUT_MAPPER.resolveConfiguredController(this.workingConfig);
        if (selected != null) {
            for (int index = 0; index < controllers.size(); index++) {
                if (controllers.get(index).joystickId() == selected.joystickId()) {
                    return index;
                }
            }
        }

        return -1;
    }

    private void startButtonCapture(final CaptureTarget captureTarget) {
        if (!this.ensureControllerSelected()) {
            return;
        }

        this.captureTarget = captureTarget;
        this.buttonCaptureSnapshot = ClientDroneCam.INPUT_MAPPER.createButtonCaptureSnapshot(this.workingConfig);
        this.captureStartTime = System.currentTimeMillis();
        this.refreshLabels();
    }

    private void startAxisCapture(final CaptureTarget captureTarget) {
        if (!this.ensureControllerSelected()) {
            return;
        }

        this.captureTarget = captureTarget;
        this.buttonCaptureSnapshot = ClientDroneCam.INPUT_MAPPER.createButtonCaptureSnapshot(this.workingConfig);
        this.axisCalibrationSession = null;
        this.captureStartTime = System.currentTimeMillis();
        this.refreshLabels();
    }

    private boolean ensureControllerSelected() {
        if (ClientDroneCam.INPUT_MAPPER.resolveConfiguredController(this.workingConfig) != null) {
            return true;
        }

        final List<DroneInputMapper.ControllerInfo> controllers = ClientDroneCam.INPUT_MAPPER.getAvailableControllers();
        if (controllers.isEmpty()) {
            return false;
        }

        final DroneInputMapper.ControllerInfo controller = controllers.getFirst();
        this.workingConfig.setController(controller.guid(), controller.displayName());
        this.saveAndRefresh();
        return true;
    }

    private void saveAndRefresh() {
        this.saveConfig();
        this.refreshLabels();
    }

    private void saveConfig() {
        ClientDroneCam.CONFIG.controllerGuid = this.workingConfig.controllerGuid;
        ClientDroneCam.CONFIG.controllerName = this.workingConfig.controllerName;
        ClientDroneCam.CONFIG.toggleButton = this.workingConfig.toggleButton;
        ClientDroneCam.CONFIG.exitButton = this.workingConfig.exitButton;
        ClientDroneCam.CONFIG.axisThrottle = this.workingConfig.axisThrottle;
        ClientDroneCam.CONFIG.axisYaw = this.workingConfig.axisYaw;
        ClientDroneCam.CONFIG.axisPitch = this.workingConfig.axisPitch;
        ClientDroneCam.CONFIG.axisRoll = this.workingConfig.axisRoll;
        ClientDroneCam.CONFIG.axisThrottleMin = this.workingConfig.axisThrottleMin;
        ClientDroneCam.CONFIG.axisThrottleMax = this.workingConfig.axisThrottleMax;
        ClientDroneCam.CONFIG.axisYawMin = this.workingConfig.axisYawMin;
        ClientDroneCam.CONFIG.axisYawMax = this.workingConfig.axisYawMax;
        ClientDroneCam.CONFIG.axisPitchMin = this.workingConfig.axisPitchMin;
        ClientDroneCam.CONFIG.axisPitchMax = this.workingConfig.axisPitchMax;
        ClientDroneCam.CONFIG.axisRollMin = this.workingConfig.axisRollMin;
        ClientDroneCam.CONFIG.axisRollMax = this.workingConfig.axisRollMax;
        ClientDroneCam.CONFIG.invertThrottle = this.workingConfig.invertThrottle;
        ClientDroneCam.CONFIG.invertYaw = this.workingConfig.invertYaw;
        ClientDroneCam.CONFIG.invertPitch = this.workingConfig.invertPitch;
        ClientDroneCam.CONFIG.invertRoll = this.workingConfig.invertRoll;
        ClientDroneCam.CONFIG.deadzone = this.workingConfig.deadzone;
        ClientDroneCam.CONFIG.save();
    }

    private void refreshLabels() {
        if (this.controllerButton == null) {
            return;
        }

        final DroneInputMapper.ControllerInfo controller = ClientDroneCam.INPUT_MAPPER.resolveConfiguredController(this.workingConfig);
        final String controllerLabel = controller == null
                ? "Controller: None"
                : "Controller: " + controller.displayName();
        this.controllerButton.setMessage(Component.literal(controllerLabel));

        this.toggleButton.setMessage(bindingMessage("Toggle", DroneInputMapper.buttonName(this.workingConfig.toggleButton), CaptureTarget.TOGGLE_BUTTON));
        this.exitButton.setMessage(bindingMessage("Exit", DroneInputMapper.buttonName(this.workingConfig.exitButton), CaptureTarget.EXIT_BUTTON));
        this.throttleAxisButton.setMessage(bindingMessage("Throttle", DroneInputMapper.axisName(this.workingConfig.axisThrottle), CaptureTarget.THROTTLE_AXIS));
        this.yawAxisButton.setMessage(bindingMessage("Yaw", DroneInputMapper.axisName(this.workingConfig.axisYaw), CaptureTarget.YAW_AXIS));
        this.pitchAxisButton.setMessage(bindingMessage("Pitch", DroneInputMapper.axisName(this.workingConfig.axisPitch), CaptureTarget.PITCH_AXIS));
        this.rollAxisButton.setMessage(bindingMessage("Roll", DroneInputMapper.axisName(this.workingConfig.axisRoll), CaptureTarget.ROLL_AXIS));
        this.throttleInvertButton.setMessage(Component.literal(invertLabel(this.workingConfig.invertThrottle)));
        this.yawInvertButton.setMessage(Component.literal(invertLabel(this.workingConfig.invertYaw)));
        this.pitchInvertButton.setMessage(Component.literal(invertLabel(this.workingConfig.invertPitch)));
        this.rollInvertButton.setMessage(Component.literal(invertLabel(this.workingConfig.invertRoll)));
    }

    private Component bindingMessage(final String label, final String value, final CaptureTarget target) {
        if (this.captureTarget == target) {
            if (this.axisCalibrationSession != null && this.captureTarget.isAxis()) {
                return Component.literal(label + ": " + DroneInputMapper.axisName(this.axisCalibrationSession.axis()) + " [" + this.axisCalibrationSession.progressLabel() + "]");
            }
            return Component.literal(label + ": ...");
        }
        return Component.literal(label + ": " + value);
    }

    private void tickAxisCapture(final long now) {
        if (this.axisCalibrationSession == null) {
            final Integer axis = ClientDroneCam.INPUT_MAPPER.detectMovedAxis(this.workingConfig, this.buttonCaptureSnapshot);
            if (axis == null) {
                return;
            }

            final float value = ClientDroneCam.INPUT_MAPPER.readRawAxis(this.workingConfig, axis);
            this.axisCalibrationSession = new AxisCalibrationSession(axis, now, value);
            this.refreshLabels();
            return;
        }

        final float value = ClientDroneCam.INPUT_MAPPER.readRawAxis(this.workingConfig, this.axisCalibrationSession.axis());
        this.axisCalibrationSession.include(value);
        if (now - this.axisCalibrationSession.startTime() < AXIS_CALIBRATION_MS
                || this.axisCalibrationSession.range() < AXIS_CALIBRATION_RANGE_MIN) {
            this.refreshLabels();
            return;
        }

        this.applyAxisCapture(this.captureTarget, this.axisCalibrationSession.axis(), this.axisCalibrationSession.min(), this.axisCalibrationSession.max());
        this.captureTarget = CaptureTarget.NONE;
        this.buttonCaptureSnapshot = null;
        this.axisCalibrationSession = null;
        this.saveAndRefresh();
    }

    private void applyAxisCapture(final CaptureTarget target, final int axis, final float min, final float max) {
        switch (target) {
            case THROTTLE_AXIS -> {
                this.workingConfig.axisThrottle = axis;
                this.workingConfig.axisThrottleMin = min;
                this.workingConfig.axisThrottleMax = max;
            }
            case YAW_AXIS -> {
                this.workingConfig.axisYaw = axis;
                this.workingConfig.axisYawMin = min;
                this.workingConfig.axisYawMax = max;
            }
            case PITCH_AXIS -> {
                this.workingConfig.axisPitch = axis;
                this.workingConfig.axisPitchMin = min;
                this.workingConfig.axisPitchMax = max;
            }
            case ROLL_AXIS -> {
                this.workingConfig.axisRoll = axis;
                this.workingConfig.axisRollMin = min;
                this.workingConfig.axisRollMax = max;
            }
            case NONE, TOGGLE_BUTTON, EXIT_BUTTON -> {
            }
        }
    }

    private static String invertLabel(final boolean inverted) {
        return inverted ? "Invert: On" : "Invert: Off";
    }

    private enum CaptureTarget {
        NONE(false),
        TOGGLE_BUTTON(false),
        EXIT_BUTTON(false),
        THROTTLE_AXIS(true),
        YAW_AXIS(true),
        PITCH_AXIS(true),
        ROLL_AXIS(true);

        private final boolean axis;

        CaptureTarget(final boolean axis) {
            this.axis = axis;
        }

        public boolean isAxis() {
            return this.axis;
        }
    }

    private static final class AxisCalibrationSession {
        private final int axis;
        private final long startTime;
        private float min;
        private float max;

        private AxisCalibrationSession(final int axis, final long startTime, final float initialValue) {
            this.axis = axis;
            this.startTime = startTime;
            this.min = initialValue;
            this.max = initialValue;
        }

        private int axis() {
            return this.axis;
        }

        private long startTime() {
            return this.startTime;
        }

        private void include(final float value) {
            this.min = Math.min(this.min, value);
            this.max = Math.max(this.max, value);
        }

        private float min() {
            return this.min;
        }

        private float max() {
            return this.max;
        }

        private float range() {
            return this.max - this.min;
        }

        private String progressLabel() {
            return String.format("min %.2f max %.2f", this.min, this.max);
        }
    }
}
