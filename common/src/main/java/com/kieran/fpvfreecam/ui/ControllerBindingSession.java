package com.kieran.fpvfreecam.ui;

import com.kieran.fpvfreecam.FpvFreecam;
import com.kieran.fpvfreecam.config.DroneConfig;
import com.kieran.fpvfreecam.input.DroneInputMapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ControllerBindingSession {
    private static final long CAPTURE_GRACE_MS = 200L;
    private static final long AXIS_CALIBRATION_MS = 2500L;
    private static final float AXIS_CALIBRATION_RANGE_MIN = 0.5F;
    private static final long LABEL_REFRESH_MS = 100L;
    private static final CaptureTarget[] AXIS_TARGETS = {
            CaptureTarget.THROTTLE_AXIS,
            CaptureTarget.YAW_AXIS,
            CaptureTarget.PITCH_AXIS,
            CaptureTarget.ROLL_AXIS
    };

    private final DroneConfig workingConfig;
    private final Map<CaptureTarget, String> displayedAxisLabels = new EnumMap<>(CaptureTarget.class);

    private CaptureTarget captureTarget = CaptureTarget.NONE;
    private long captureStartTime;
    private long lastLabelRefreshTime;
    private @Nullable DroneInputMapper.ButtonCaptureSnapshot buttonCaptureSnapshot;
    private @Nullable AxisCalibrationSession axisCalibrationSession;
    private CaptureTarget displayedCaptureTarget = CaptureTarget.NONE;
    private @Nullable Integer selectedControllerIndex;

    public ControllerBindingSession(final DroneConfig workingConfig) {
        this.workingConfig = workingConfig;
    }

    public boolean tick() {
        if (this.captureTarget == CaptureTarget.NONE) {
            return false;
        }

        final long now = System.currentTimeMillis();
        if (now - this.captureStartTime < CAPTURE_GRACE_MS) {
            return false;
        }

        if (this.captureTarget.isAxis()) {
            return this.tickAxisCapture(now);
        }

        final Integer capture = FpvFreecam.INPUT_MAPPER.detectPressedButton(this.workingConfig, this.buttonCaptureSnapshot);
        if (capture == null) {
            return false;
        }

        switch (this.captureTarget) {
            case ARM_BUTTON -> this.workingConfig.controller.armButton = capture;
            case DISARM_BUTTON -> this.workingConfig.controller.disarmButton = capture;
            case RESET_BUTTON -> this.workingConfig.controller.resetButton = capture;
            case NONE, THROTTLE_AXIS, YAW_AXIS, PITCH_AXIS, ROLL_AXIS -> {
            }
        }

        this.cancelCapture();
        return true;
    }

    public void startButtonCapture(final CaptureTarget target) {
        if (!target.isButton() || !this.ensureControllerSelected()) {
            return;
        }

        this.captureTarget = target;
        this.buttonCaptureSnapshot = FpvFreecam.INPUT_MAPPER.createButtonCaptureSnapshot(this.workingConfig);
        this.axisCalibrationSession = null;
        this.captureStartTime = System.currentTimeMillis();
    }

    public void startAxisCapture(final CaptureTarget target) {
        if (!target.isAxis() || !this.ensureControllerSelected()) {
            return;
        }

        this.captureTarget = target;
        this.buttonCaptureSnapshot = FpvFreecam.INPUT_MAPPER.createButtonCaptureSnapshot(this.workingConfig);
        this.axisCalibrationSession = null;
        this.captureStartTime = System.currentTimeMillis();
    }

    public void cancelCapture() {
        this.captureTarget = CaptureTarget.NONE;
        this.buttonCaptureSnapshot = null;
        this.axisCalibrationSession = null;
    }

    public boolean isCaptureActive() {
        return this.captureTarget != CaptureTarget.NONE;
    }

    public CaptureTarget captureTarget() {
        return this.captureTarget;
    }

    public void cycleController() {
        this.cancelCapture();

        final List<DroneInputMapper.ControllerInfo> controllers = this.getSelectableControllers();
        if (controllers.isEmpty()) {
            this.workingConfig.clearControllerSelection();
            this.selectedControllerIndex = null;
            return;
        }

        final int currentIndex = this.findCurrentControllerIndex(controllers);
        if (currentIndex == controllers.size() - 1) {
            this.workingConfig.clearControllerSelection();
            this.selectedControllerIndex = null;
            return;
        }

        final int nextIndex = currentIndex < 0 ? 0 : currentIndex + 1;
        final DroneInputMapper.ControllerInfo controller = controllers.get(nextIndex);
        this.workingConfig.setController(controller.guid(), controller.displayName());
        this.selectedControllerIndex = nextIndex;
    }

    public boolean ensureControllerSelected() {
        if (FpvFreecam.INPUT_MAPPER.resolveConfiguredController(this.workingConfig) != null) {
            return true;
        }

        final List<DroneInputMapper.ControllerInfo> controllers = this.getSelectableControllers();
        if (controllers.isEmpty()) {
            this.workingConfig.clearControllerSelection();
            this.selectedControllerIndex = null;
            this.cancelCapture();
            return false;
        }

        final DroneInputMapper.ControllerInfo controller = controllers.get(0);
        this.workingConfig.setController(controller.guid(), controller.displayName());
        this.selectedControllerIndex = 0;
        return true;
    }

    public String selectedControllerName() {
        final List<DroneInputMapper.ControllerInfo> controllers = this.getSelectableControllers();
        final int currentIndex = this.findCurrentControllerIndex(controllers);
        if (currentIndex < 0) {
            return "None";
        }

        final DroneInputMapper.ControllerInfo controller = controllers.get(currentIndex);
        return controller.displayName() + " (" + (currentIndex + 1) + "/" + controllers.size() + ")";
    }

    public String buttonBindingLabel(final CaptureTarget target) {
        if (this.captureTarget == target) {
            return "Waiting...";
        }

        final int button = switch (target) {
            case ARM_BUTTON -> this.workingConfig.controller.armButton;
            case DISARM_BUTTON -> this.workingConfig.controller.disarmButton;
            case RESET_BUTTON -> this.workingConfig.controller.resetButton;
            case NONE, THROTTLE_AXIS, YAW_AXIS, PITCH_AXIS, ROLL_AXIS -> -1;
        };
        return buttonName(button);
    }

    public String axisBindingLabel(final CaptureTarget target) {
        if (this.captureTarget == target) {
            if (this.axisCalibrationSession != null) {
                final float value = FpvFreecam.INPUT_MAPPER.readConfiguredAxisValue(
                        this.workingConfig,
                        this.axisCalibrationSession.axis(),
                        this.axisCalibrationSession.min(),
                        this.axisCalibrationSession.max(),
                        this.workingConfig.controller.deadzone,
                        this.invertForTarget(target)
                );
                return "Calibrating " + formatAxisValue(value);
            }
            return "Waiting...";
        }

        final int axis = axisForTarget(target);
        if (axis < 0) {
            return "Unbound";
        }

        final float value = FpvFreecam.INPUT_MAPPER.readConfiguredAxisValue(
                this.workingConfig,
                axis,
                this.axisMinForTarget(target),
                this.axisMaxForTarget(target),
                this.workingConfig.controller.deadzone,
                this.invertForTarget(target)
        );
        return formatAxisValue(value);
    }

    public void captureDisplayedAxisLabels() {
        for (final CaptureTarget target : AXIS_TARGETS) {
            this.displayedAxisLabels.put(target, this.axisBindingLabel(target));
        }
        this.displayedCaptureTarget = this.captureTarget;
        this.lastLabelRefreshTime = System.currentTimeMillis();
    }

    public boolean shouldRefreshAxisLabels(final long now) {
        final boolean captureChanged = this.displayedCaptureTarget != this.captureTarget;
        if (!captureChanged && now - this.lastLabelRefreshTime < LABEL_REFRESH_MS) {
            return false;
        }

        boolean changed = captureChanged;
        for (final CaptureTarget target : AXIS_TARGETS) {
            final String label = this.axisBindingLabel(target);
            if (!label.equals(this.displayedAxisLabels.get(target))) {
                this.displayedAxisLabels.put(target, label);
                changed = true;
            }
        }

        this.displayedCaptureTarget = this.captureTarget;
        this.lastLabelRefreshTime = now;
        return changed;
    }

    private int axisForTarget(final CaptureTarget target) {
        return switch (target) {
            case THROTTLE_AXIS -> this.workingConfig.controller.axisThrottle;
            case YAW_AXIS -> this.workingConfig.controller.axisYaw;
            case PITCH_AXIS -> this.workingConfig.controller.axisPitch;
            case ROLL_AXIS -> this.workingConfig.controller.axisRoll;
            case NONE, ARM_BUTTON, DISARM_BUTTON, RESET_BUTTON -> -1;
        };
    }

    private float axisMinForTarget(final CaptureTarget target) {
        return switch (target) {
            case THROTTLE_AXIS -> this.workingConfig.controller.axisThrottleMin;
            case YAW_AXIS -> this.workingConfig.controller.axisYawMin;
            case PITCH_AXIS -> this.workingConfig.controller.axisPitchMin;
            case ROLL_AXIS -> this.workingConfig.controller.axisRollMin;
            case NONE, ARM_BUTTON, DISARM_BUTTON, RESET_BUTTON -> -1.0F;
        };
    }

    private float axisMaxForTarget(final CaptureTarget target) {
        return switch (target) {
            case THROTTLE_AXIS -> this.workingConfig.controller.axisThrottleMax;
            case YAW_AXIS -> this.workingConfig.controller.axisYawMax;
            case PITCH_AXIS -> this.workingConfig.controller.axisPitchMax;
            case ROLL_AXIS -> this.workingConfig.controller.axisRollMax;
            case NONE, ARM_BUTTON, DISARM_BUTTON, RESET_BUTTON -> 1.0F;
        };
    }

    private boolean invertForTarget(final CaptureTarget target) {
        return switch (target) {
            case THROTTLE_AXIS -> this.workingConfig.controller.invertThrottle;
            case YAW_AXIS -> this.workingConfig.controller.invertYaw;
            case PITCH_AXIS -> this.workingConfig.controller.invertPitch;
            case ROLL_AXIS -> this.workingConfig.controller.invertRoll;
            case NONE, ARM_BUTTON, DISARM_BUTTON, RESET_BUTTON -> false;
        };
    }

    private boolean tickAxisCapture(final long now) {
        if (FpvFreecam.INPUT_MAPPER.resolveConfiguredController(this.workingConfig) == null) {
            this.cancelCapture();
            return true;
        }

        if (this.axisCalibrationSession == null) {
            final Integer axis = FpvFreecam.INPUT_MAPPER.detectMovedAxis(this.workingConfig, this.buttonCaptureSnapshot);
            if (axis == null) {
                return false;
            }

            final float value = FpvFreecam.INPUT_MAPPER.readRawAxis(this.workingConfig, axis);
            this.axisCalibrationSession = new AxisCalibrationSession(axis, now, value);
            return true;
        }

        final float value = FpvFreecam.INPUT_MAPPER.readRawAxis(this.workingConfig, this.axisCalibrationSession.axis());
        this.axisCalibrationSession.include(value);
        if (now - this.axisCalibrationSession.startTime() < AXIS_CALIBRATION_MS
                || this.axisCalibrationSession.range() < AXIS_CALIBRATION_RANGE_MIN) {
            return false;
        }

        this.applyAxisCapture(this.captureTarget, this.axisCalibrationSession.axis(), this.axisCalibrationSession.min(), this.axisCalibrationSession.max());
        this.cancelCapture();
        return true;
    }

    private void applyAxisCapture(final CaptureTarget target, final int axis, final float min, final float max) {
        switch (target) {
            case THROTTLE_AXIS -> {
                this.workingConfig.controller.axisThrottle = axis;
                this.workingConfig.controller.axisThrottleMin = min;
                this.workingConfig.controller.axisThrottleMax = max;
            }
            case YAW_AXIS -> {
                this.workingConfig.controller.axisYaw = axis;
                this.workingConfig.controller.axisYawMin = min;
                this.workingConfig.controller.axisYawMax = max;
            }
            case PITCH_AXIS -> {
                this.workingConfig.controller.axisPitch = axis;
                this.workingConfig.controller.axisPitchMin = min;
                this.workingConfig.controller.axisPitchMax = max;
            }
            case ROLL_AXIS -> {
                this.workingConfig.controller.axisRoll = axis;
                this.workingConfig.controller.axisRollMin = min;
                this.workingConfig.controller.axisRollMax = max;
            }
            case NONE, ARM_BUTTON, DISARM_BUTTON, RESET_BUTTON -> {
            }
        }
    }

    private int findCurrentControllerIndex(final List<DroneInputMapper.ControllerInfo> controllers) {
        if (this.selectedControllerIndex != null
                && this.selectedControllerIndex >= 0
                && this.selectedControllerIndex < controllers.size()) {
            return this.selectedControllerIndex;
        }

        if (this.workingConfig.controller.controllerGuid.isBlank() && this.workingConfig.controller.controllerName.isBlank()) {
            return -1;
        }

        final DroneInputMapper.ControllerInfo selected = FpvFreecam.INPUT_MAPPER.resolveConfiguredController(this.workingConfig);
        if (selected != null) {
            for (int index = 0; index < controllers.size(); index++) {
                if (samePersistedController(controllers.get(index), selected)) {
                    this.selectedControllerIndex = index;
                    return index;
                }
            }
        }
        return -1;
    }

    private List<DroneInputMapper.ControllerInfo> getSelectableControllers() {
        final List<DroneInputMapper.ControllerInfo> selectable = new ArrayList<>();
        final Set<String> seen = new HashSet<>();

        for (final DroneInputMapper.ControllerInfo controller : FpvFreecam.INPUT_MAPPER.getAvailableControllers()) {
            final String key = controller.guid() + "||" + controller.displayName();
            if (seen.add(key)) {
                selectable.add(controller);
            }
        }

        return selectable;
    }

    private static boolean samePersistedController(
            final DroneInputMapper.ControllerInfo first,
            final DroneInputMapper.ControllerInfo second
    ) {
        return first.guid().equals(second.guid()) && first.displayName().equals(second.displayName());
    }

    private static String buttonName(final int button) {
        if (button < 0) {
            return "Unbound";
        }
        return DroneInputMapper.buttonName(button);
    }

    public enum CaptureTarget {
        NONE(false),
        ARM_BUTTON(false),
        DISARM_BUTTON(false),
        RESET_BUTTON(false),
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

        public boolean isButton() {
            return this != NONE && !this.axis;
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

        private float min() {
            return this.min;
        }

        private float max() {
            return this.max;
        }

        private void include(final float value) {
            this.min = Math.min(this.min, value);
            this.max = Math.max(this.max, value);
        }

        private float range() {
            return this.max - this.min;
        }
    }

    private static String formatAxisValue(final float value) {
        final float displayValue = Math.abs(value) < 0.005F ? 0.0F : value;
        return String.format(Locale.ROOT, "%+.2f", displayValue);
    }
}
