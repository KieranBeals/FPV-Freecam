package com.kieran.fpvfreecam.ui;

import com.kieran.fpvfreecam.FpvFreecam;
import com.kieran.fpvfreecam.config.DroneConfig;
import com.kieran.fpvfreecam.flight.DroneProfileDefaults;
import com.kieran.fpvfreecam.flight.DroneRateModel;
import com.kieran.fpvfreecam.input.DroneInputMapper;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class DroneSetupScreen extends Screen {
    private static final int LABEL_COLOR = 0xE0E0E0;
    private static final int MUTED_COLOR = 0xA0A0A0;
    private static final long CAPTURE_GRACE_MS = 200L;
    private static final long AXIS_CALIBRATION_MS = 2500L;
    private static final float AXIS_CALIBRATION_RANGE_MIN = 0.5F;

    private final Screen parent;
    private final DroneConfig workingConfig;
    private final DroneRateModel rateModel = new DroneRateModel();
    private final List<AbstractWidget> pageWidgets = new ArrayList<>();

    private Page page = Page.CONTROLLER;

    private Button previousPageButton;
    private Button nextPageButton;
    private Button doneButton;

    private Button controllerButton;
    private Button armButton;
    private Button disarmButton;
    private Button resetButton;
    private Button throttleAxisButton;
    private Button yawAxisButton;
    private Button pitchAxisButton;
    private Button rollAxisButton;
    private Button throttleInvertButton;
    private Button yawInvertButton;
    private Button pitchInvertButton;
    private Button rollInvertButton;
    private Button inFlightTiltAdjustButton;

    private Button crashModeButton;
    private Button safetyDebugButton;
    private Button damageExitButton;

    private CaptureTarget captureTarget = CaptureTarget.NONE;
    private long captureStartTime;
    private @Nullable DroneInputMapper.ButtonCaptureSnapshot buttonCaptureSnapshot;
    private @Nullable AxisCalibrationSession axisCalibrationSession;

    public DroneSetupScreen(@Nullable final Screen parent) {
        super(Component.translatable("screen.fpvfreecam.setup"));
        this.parent = parent;
        this.workingConfig = FpvFreecam.CONFIG.copy();
    }

    @Override
    protected void init() {
        this.rebuildPageWidgets();
    }

    private void rebuildPageWidgets() {
        this.clearWidgets();
        this.pageWidgets.clear();

        final int centerX = this.width / 2;
        final int bottomY = this.height - 28;
        final Button pageLabel = this.addPageWidget(Button.builder(
                        Component.literal("Page " + (this.page.ordinal() + 1) + "/4 - " + this.page.title),
                        button -> {
                        })
                .bounds(centerX - 170, 14, 340, 20)
                .build());
        pageLabel.active = false;

        this.previousPageButton = this.addRenderableWidget(Button.builder(Component.literal("< Prev"), button -> this.switchPage(-1))
                .bounds(centerX - 170, bottomY, 80, 20)
                .build());
        this.nextPageButton = this.addRenderableWidget(Button.builder(Component.literal("Next >"), button -> this.switchPage(1))
                .bounds(centerX - 84, bottomY, 80, 20)
                .build());
        this.doneButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.fpvfreecam.done"), button -> this.onClose())
                .bounds(centerX + 90, bottomY, 80, 20)
                .build());

        switch (this.page) {
            case CONTROLLER -> this.buildControllerPage();
            case RATES -> this.buildRatesPage();
            case CRAFT -> this.buildCraftPage();
            case REALISM_CRASH -> this.buildRealismPage();
        }

        this.previousPageButton.active = this.page != Page.CONTROLLER;
        this.nextPageButton.active = this.page != Page.REALISM_CRASH;
        this.refreshPageLabels();
    }

    private void buildControllerPage() {
        final int centerX = this.width / 2;
        final int left = centerX - 160;
        final int middle = centerX - 52;
        final int right = centerX + 56;
        int y = 48;

        this.controllerButton = this.addPageWidget(Button.builder(Component.empty(), button -> {
            this.cycleController();
            this.saveAndRefresh();
        }).bounds(left, y, 325, 20).build());
        y += 28;

        this.armButton = this.addPageWidget(Button.builder(Component.empty(), button -> this.startButtonCapture(CaptureTarget.ARM_BUTTON))
                .bounds(left, y, 96, 20)
                .build());
        this.disarmButton = this.addPageWidget(Button.builder(Component.empty(), button -> this.startButtonCapture(CaptureTarget.DISARM_BUTTON))
                .bounds(middle, y, 96, 20)
                .build());
        this.resetButton = this.addPageWidget(Button.builder(Component.empty(), button -> this.startButtonCapture(CaptureTarget.RESET_BUTTON))
                .bounds(right, y, 96, 20)
                .build());
        this.addFieldLabel(left, y - 11, "Arm Binding");
        this.addFieldLabel(middle, y - 11, "Disarm Binding");
        this.addFieldLabel(right, y - 11, "Reset Binding");
        y += 34;

        this.throttleAxisButton = this.addPageWidget(Button.builder(Component.empty(), button -> this.startAxisCapture(CaptureTarget.THROTTLE_AXIS))
                .bounds(left, y, 180, 20)
                .build());
        this.throttleInvertButton = this.addPageWidget(Button.builder(Component.empty(), button -> {
            this.workingConfig.controller.invertThrottle = !this.workingConfig.controller.invertThrottle;
            this.saveAndRefresh();
        }).bounds(left + 188, y, 132, 20).build());
        this.addFieldLabel(left, y - 11, "Throttle Axis");
        this.addFieldLabel(left + 188, y - 11, "Throttle Invert");
        y += 30;

        this.yawAxisButton = this.addPageWidget(Button.builder(Component.empty(), button -> this.startAxisCapture(CaptureTarget.YAW_AXIS))
                .bounds(left, y, 180, 20)
                .build());
        this.yawInvertButton = this.addPageWidget(Button.builder(Component.empty(), button -> {
            this.workingConfig.controller.invertYaw = !this.workingConfig.controller.invertYaw;
            this.saveAndRefresh();
        }).bounds(left + 188, y, 132, 20).build());
        this.addFieldLabel(left, y - 11, "Yaw Axis");
        this.addFieldLabel(left + 188, y - 11, "Yaw Invert");
        y += 30;

        this.pitchAxisButton = this.addPageWidget(Button.builder(Component.empty(), button -> this.startAxisCapture(CaptureTarget.PITCH_AXIS))
                .bounds(left, y, 180, 20)
                .build());
        this.pitchInvertButton = this.addPageWidget(Button.builder(Component.empty(), button -> {
            this.workingConfig.controller.invertPitch = !this.workingConfig.controller.invertPitch;
            this.saveAndRefresh();
        }).bounds(left + 188, y, 132, 20).build());
        this.addFieldLabel(left, y - 11, "Pitch Axis");
        this.addFieldLabel(left + 188, y - 11, "Pitch Invert");
        y += 30;

        this.rollAxisButton = this.addPageWidget(Button.builder(Component.empty(), button -> this.startAxisCapture(CaptureTarget.ROLL_AXIS))
                .bounds(left, y, 180, 20)
                .build());
        this.rollInvertButton = this.addPageWidget(Button.builder(Component.empty(), button -> {
            this.workingConfig.controller.invertRoll = !this.workingConfig.controller.invertRoll;
            this.saveAndRefresh();
        }).bounds(left + 188, y, 132, 20).build());
        this.addFieldLabel(left, y - 11, "Roll Axis");
        this.addFieldLabel(left + 188, y - 11, "Roll Invert");
        y += 34;

        this.addFieldLabel(left, y - 11, "Deadzone");
        this.addFieldLabel(right, y - 11, "In-flight Cam Adjust");
        this.addFloatField(left, y, 90, this.workingConfig.controller.deadzone, value -> this.workingConfig.controller.deadzone = value);
        this.inFlightTiltAdjustButton = this.addPageWidget(Button.builder(Component.empty(), button -> {
            this.workingConfig.controller.allowInFlightCameraAngleAdjust = !this.workingConfig.controller.allowInFlightCameraAngleAdjust;
            this.saveAndRefresh();
        }).bounds(right, y, 190, 20).build());
    }

    private void buildRatesPage() {
        final int centerX = this.width / 2;
        final int left = centerX - 160;
        final int columnGap = 105;
        int y = 62;

        this.addFieldLabel(left, y - 11, "Roll RC Rate");
        this.addFieldLabel(left + columnGap, y - 11, "Roll Super");
        this.addFieldLabel(left + columnGap * 2, y - 11, "Roll Expo");
        this.addFloatField(left, y, 90, this.workingConfig.rateProfile.rollRcRate, value -> this.workingConfig.rateProfile.rollRcRate = value);
        this.addFloatField(left + columnGap, y, 90, this.workingConfig.rateProfile.rollSuperRate, value -> this.workingConfig.rateProfile.rollSuperRate = value);
        this.addFloatField(left + columnGap * 2, y, 90, this.workingConfig.rateProfile.rollExpo, value -> this.workingConfig.rateProfile.rollExpo = value);
        y += 34;

        this.addFieldLabel(left, y - 11, "Pitch RC Rate");
        this.addFieldLabel(left + columnGap, y - 11, "Pitch Super");
        this.addFieldLabel(left + columnGap * 2, y - 11, "Pitch Expo");
        this.addFloatField(left, y, 90, this.workingConfig.rateProfile.pitchRcRate, value -> this.workingConfig.rateProfile.pitchRcRate = value);
        this.addFloatField(left + columnGap, y, 90, this.workingConfig.rateProfile.pitchSuperRate, value -> this.workingConfig.rateProfile.pitchSuperRate = value);
        this.addFloatField(left + columnGap * 2, y, 90, this.workingConfig.rateProfile.pitchExpo, value -> this.workingConfig.rateProfile.pitchExpo = value);
        y += 34;

        this.addFieldLabel(left, y - 11, "Yaw RC Rate");
        this.addFieldLabel(left + columnGap, y - 11, "Yaw Super");
        this.addFieldLabel(left + columnGap * 2, y - 11, "Yaw Expo");
        this.addFloatField(left, y, 90, this.workingConfig.rateProfile.yawRcRate, value -> this.workingConfig.rateProfile.yawRcRate = value);
        this.addFloatField(left + columnGap, y, 90, this.workingConfig.rateProfile.yawSuperRate, value -> this.workingConfig.rateProfile.yawSuperRate = value);
        this.addFloatField(left + columnGap * 2, y, 90, this.workingConfig.rateProfile.yawExpo, value -> this.workingConfig.rateProfile.yawExpo = value);
    }

    private void buildCraftPage() {
        final int centerX = this.width / 2;
        final int left = centerX - 160;
        final int right = centerX + 8;
        int y = 58;

        this.addFieldLabel(left, y - 11, "Camera Angle (deg)");
        this.addFieldLabel(left + 98, y - 11, "Throttle Mid");
        this.addFieldLabel(left + 196, y - 11, "Throttle Expo");
        this.addFloatField(left, y, 90, this.workingConfig.craftProfile.cameraAngleDeg, value -> this.workingConfig.craftProfile.cameraAngleDeg = value);
        this.addFloatField(left + 98, y, 90, this.workingConfig.throttleProfile.throttleMid, value -> this.workingConfig.throttleProfile.throttleMid = value);
        this.addFloatField(left + 196, y, 90, this.workingConfig.throttleProfile.throttleExpo, value -> this.workingConfig.throttleProfile.throttleExpo = value);
        y += 34;

        this.addFieldLabel(left, y - 11, "Thrust/Weight");
        this.addFieldLabel(left + 98, y - 11, "Spool Up (s)");
        this.addFieldLabel(left + 196, y - 11, "Spool Down (s)");
        this.addFloatField(left, y, 90, this.workingConfig.craftProfile.thrustToWeight, value -> this.workingConfig.craftProfile.thrustToWeight = value);
        this.addFloatField(left + 98, y, 90, this.workingConfig.craftProfile.motorSpoolUpSeconds, value -> this.workingConfig.craftProfile.motorSpoolUpSeconds = value);
        this.addFloatField(left + 196, y, 90, this.workingConfig.craftProfile.motorSpoolDownSeconds, value -> this.workingConfig.craftProfile.motorSpoolDownSeconds = value);
        y += 34;

        this.addFieldLabel(left, y - 11, "Forward Drag");
        this.addFieldLabel(left + 98, y - 11, "Side Drag");
        this.addFieldLabel(left + 196, y - 11, "Vertical Drag");
        this.addFloatField(left, y, 90, this.workingConfig.craftProfile.forwardDrag, value -> this.workingConfig.craftProfile.forwardDrag = value);
        this.addFloatField(left + 98, y, 90, this.workingConfig.craftProfile.sideDrag, value -> this.workingConfig.craftProfile.sideDrag = value);
        this.addFloatField(left + 196, y, 90, this.workingConfig.craftProfile.verticalDrag, value -> this.workingConfig.craftProfile.verticalDrag = value);
        y += 34;

        this.addFieldLabel(left, y - 11, "Mass (kg)");
        this.addFieldLabel(left + 98, y - 11, "Roll Response (s)");
        this.addFieldLabel(left + 196, y - 11, "Pitch Response (s)");
        this.addFloatField(left, y, 90, this.workingConfig.craftProfile.massKg, value -> this.workingConfig.craftProfile.massKg = value);
        this.addFloatField(left + 98, y, 90, this.workingConfig.craftProfile.rollResponseSeconds, value -> this.workingConfig.craftProfile.rollResponseSeconds = value);
        this.addFloatField(left + 196, y, 90, this.workingConfig.craftProfile.pitchResponseSeconds, value -> this.workingConfig.craftProfile.pitchResponseSeconds = value);
        y += 34;

        this.addFieldLabel(left, y - 11, "Yaw Response (s)");
        this.addFloatField(left, y, 90, this.workingConfig.craftProfile.yawResponseSeconds, value -> this.workingConfig.craftProfile.yawResponseSeconds = value);

        this.addPageWidget(Button.builder(Component.literal("Reset 5-inch Defaults"), button -> {
            this.workingConfig.craftProfile.cameraAngleDeg = 28.0F;
            this.workingConfig.throttleProfile.throttleMid = 0.34F;
            this.workingConfig.throttleProfile.throttleExpo = 0.22F;
            this.workingConfig.craftProfile.thrustToWeight = 5.5F;
            this.workingConfig.craftProfile.massKg = DroneProfileDefaults.DRONE_MASS_KG;
            this.workingConfig.craftProfile.motorSpoolUpSeconds = 0.060F;
            this.workingConfig.craftProfile.motorSpoolDownSeconds = 0.085F;
            this.workingConfig.craftProfile.forwardDrag = 0.035F;
            this.workingConfig.craftProfile.sideDrag = 0.160F;
            this.workingConfig.craftProfile.verticalDrag = 0.080F;
            this.workingConfig.craftProfile.rollResponseSeconds = 0.045F;
            this.workingConfig.craftProfile.pitchResponseSeconds = 0.048F;
            this.workingConfig.craftProfile.yawResponseSeconds = 0.075F;
            this.saveAndRebuild();
        }).bounds(right, this.height - 60, 165, 20).build());
    }

    private void buildRealismPage() {
        final int centerX = this.width / 2;
        final int left = centerX - 160;
        final int mid = centerX - 52;
        final int right = centerX + 56;
        int y = 58;

        this.addFieldLabel(left, y - 11, "Battery Sag");
        this.addFieldLabel(mid, y - 11, "Max Sag Loss");
        this.addFieldLabel(right, y - 11, "Sag Recovery (s)");
        this.addFloatField(left, y, 90, this.workingConfig.realismProfile.batterySagStrength, value -> this.workingConfig.realismProfile.batterySagStrength = value);
        this.addFloatField(mid, y, 90, this.workingConfig.realismProfile.batterySagMaxLoss, value -> this.workingConfig.realismProfile.batterySagMaxLoss = value);
        this.addFloatField(right, y, 90, this.workingConfig.realismProfile.sagRecoverySeconds, value -> this.workingConfig.realismProfile.sagRecoverySeconds = value);
        y += 34;

        this.addFieldLabel(left, y - 11, "Descent Wash");
        this.addFieldLabel(mid, y - 11, "Load Imperfection");
        this.addFieldLabel(right, y - 11, "Safety Debug");
        this.addFloatField(left, y, 90, this.workingConfig.realismProfile.descentWashStrength, value -> this.workingConfig.realismProfile.descentWashStrength = value);
        this.addFloatField(mid, y, 90, this.workingConfig.realismProfile.loadImperfectionStrength, value -> this.workingConfig.realismProfile.loadImperfectionStrength = value);
        this.safetyDebugButton = this.addPageWidget(Button.builder(Component.empty(), button -> {
            this.workingConfig.realismProfile.showNetworkSafetyDebugLine = !this.workingConfig.realismProfile.showNetworkSafetyDebugLine;
            this.saveAndRefresh();
        }).bounds(right, y, 110, 20).build());
        y += 34;

        this.addFieldLabel(left, y - 11, "Glance Speed (m/s)");
        this.addFieldLabel(mid, y - 11, "Crash Speed (m/s)");
        this.addFieldLabel(right, y - 11, "Crash Energy");
        this.addFloatField(left, y, 90, this.workingConfig.crashSettings.lowSpeedGlanceThreshold, value -> this.workingConfig.crashSettings.lowSpeedGlanceThreshold = value);
        this.addFloatField(mid, y, 90, this.workingConfig.crashSettings.hardImpactSpeedThreshold, value -> this.workingConfig.crashSettings.hardImpactSpeedThreshold = value);
        this.addFloatField(right, y, 90, this.workingConfig.crashSettings.hardImpactEnergyThreshold, value -> this.workingConfig.crashSettings.hardImpactEnergyThreshold = value);
        y += 34;

        this.addFieldLabel(left, y - 11, "Crash Reset Mode");
        this.crashModeButton = this.addPageWidget(Button.builder(Component.empty(), button -> {
            this.workingConfig.crashSettings.crashResetMode = nextCrashMode(this.workingConfig.crashSettings.crashResetMode);
            this.saveAndRefresh();
        }).bounds(left, y, 256, 20).build());
        y += 34;

        this.addFieldLabel(left, y - 11, "Exit To Player On Damage");
        this.damageExitButton = this.addPageWidget(Button.builder(Component.empty(), button -> {
            this.workingConfig.crashSettings.exitToPlayerOnDamage = !this.workingConfig.crashSettings.exitToPlayerOnDamage;
            this.saveAndRefresh();
        }).bounds(left, y, 256, 20).build());
    }

    private void switchPage(final int delta) {
        final int nextOrdinal = Mth.clamp(this.page.ordinal() + delta, 0, Page.values().length - 1);
        this.page = Page.values()[nextOrdinal];
        this.captureTarget = CaptureTarget.NONE;
        this.buttonCaptureSnapshot = null;
        this.axisCalibrationSession = null;
        this.rebuildPageWidgets();
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

        final Integer capture = FpvFreecam.INPUT_MAPPER.detectPressedButton(this.workingConfig, this.buttonCaptureSnapshot);
        if (capture == null) {
            return;
        }

        switch (this.captureTarget) {
            case ARM_BUTTON -> this.workingConfig.controller.armButton = capture;
            case DISARM_BUTTON -> this.workingConfig.controller.disarmButton = capture;
            case RESET_BUTTON -> this.workingConfig.controller.resetButton = capture;
            case THROTTLE_AXIS, YAW_AXIS, PITCH_AXIS, ROLL_AXIS, NONE -> {
            }
        }

        this.captureTarget = CaptureTarget.NONE;
        this.buttonCaptureSnapshot = null;
        this.saveAndRefresh();
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (this.captureTarget != CaptureTarget.NONE && keyCode == 256) {
            this.captureTarget = CaptureTarget.NONE;
            this.buttonCaptureSnapshot = null;
            this.axisCalibrationSession = null;
            this.refreshPageLabels();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.saveConfig();
        this.minecraft.setScreen(this.parent);
    }

    private void saveAndRefresh() {
        this.saveConfig();
        this.refreshPageLabels();
    }

    private void saveAndRebuild() {
        this.saveConfig();
        this.rebuildPageWidgets();
    }

    private void saveConfig() {
        FpvFreecam.CONFIG.copyFrom(this.workingConfig);
        FpvFreecam.CONFIG.save();
    }

    private void refreshPageLabels() {
        if (this.page == Page.CONTROLLER) {
            this.refreshControllerLabels();
            return;
        }

        if (this.page == Page.REALISM_CRASH) {
            if (this.crashModeButton != null) {
                this.crashModeButton.setMessage(Component.literal("Crash Mode: " + this.workingConfig.crashSettings.crashResetMode.name()));
            }
            if (this.safetyDebugButton != null) {
                this.safetyDebugButton.setMessage(Component.literal(this.workingConfig.realismProfile.showNetworkSafetyDebugLine ? "Debug Line: On" : "Debug Line: Off"));
            }
            if (this.damageExitButton != null) {
                this.damageExitButton.setMessage(Component.literal(this.workingConfig.crashSettings.exitToPlayerOnDamage ? "Exit On Damage: On" : "Exit On Damage: Off"));
            }
        }
    }

    private void refreshControllerLabels() {
        if (this.controllerButton == null) {
            return;
        }

        final DroneInputMapper.ControllerInfo controller = FpvFreecam.INPUT_MAPPER.resolveConfiguredController(this.workingConfig);
        final String controllerLabel = controller == null
                ? "Controller: None"
                : "Controller: " + controller.displayName();
        this.controllerButton.setMessage(Component.literal(controllerLabel));

        this.armButton.setMessage(bindingMessage("Arm", DroneInputMapper.buttonName(this.workingConfig.controller.armButton), CaptureTarget.ARM_BUTTON));
        this.disarmButton.setMessage(bindingMessage("Disarm", DroneInputMapper.buttonName(this.workingConfig.controller.disarmButton), CaptureTarget.DISARM_BUTTON));
        this.resetButton.setMessage(bindingMessage("Reset", buttonLabel(this.workingConfig.controller.resetButton), CaptureTarget.RESET_BUTTON));
        this.throttleAxisButton.setMessage(bindingMessage("Throttle", DroneInputMapper.axisName(this.workingConfig.controller.axisThrottle), CaptureTarget.THROTTLE_AXIS));
        this.yawAxisButton.setMessage(bindingMessage("Yaw", DroneInputMapper.axisName(this.workingConfig.controller.axisYaw), CaptureTarget.YAW_AXIS));
        this.pitchAxisButton.setMessage(bindingMessage("Pitch", DroneInputMapper.axisName(this.workingConfig.controller.axisPitch), CaptureTarget.PITCH_AXIS));
        this.rollAxisButton.setMessage(bindingMessage("Roll", DroneInputMapper.axisName(this.workingConfig.controller.axisRoll), CaptureTarget.ROLL_AXIS));
        this.throttleInvertButton.setMessage(Component.literal(invertLabel(this.workingConfig.controller.invertThrottle)));
        this.yawInvertButton.setMessage(Component.literal(invertLabel(this.workingConfig.controller.invertYaw)));
        this.pitchInvertButton.setMessage(Component.literal(invertLabel(this.workingConfig.controller.invertPitch)));
        this.rollInvertButton.setMessage(Component.literal(invertLabel(this.workingConfig.controller.invertRoll)));
        this.inFlightTiltAdjustButton.setMessage(Component.literal(
                this.workingConfig.controller.allowInFlightCameraAngleAdjust ? "In-flight Cam Adjust: On" : "In-flight Cam Adjust: Off"
        ));
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
            final Integer axis = FpvFreecam.INPUT_MAPPER.detectMovedAxis(this.workingConfig, this.buttonCaptureSnapshot);
            if (axis == null) {
                return;
            }

            final float value = FpvFreecam.INPUT_MAPPER.readRawAxis(this.workingConfig, axis);
            this.axisCalibrationSession = new AxisCalibrationSession(axis, now, value);
            this.refreshPageLabels();
            return;
        }

        final float value = FpvFreecam.INPUT_MAPPER.readRawAxis(this.workingConfig, this.axisCalibrationSession.axis());
        this.axisCalibrationSession.include(value);
        if (now - this.axisCalibrationSession.startTime() < AXIS_CALIBRATION_MS
                || this.axisCalibrationSession.range() < AXIS_CALIBRATION_RANGE_MIN) {
            this.refreshPageLabels();
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

    private void cycleController() {
        final List<DroneInputMapper.ControllerInfo> controllers = FpvFreecam.INPUT_MAPPER.getAvailableControllers();
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
        final DroneInputMapper.ControllerInfo selected = FpvFreecam.INPUT_MAPPER.resolveConfiguredController(this.workingConfig);
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
        this.buttonCaptureSnapshot = FpvFreecam.INPUT_MAPPER.createButtonCaptureSnapshot(this.workingConfig);
        this.captureStartTime = System.currentTimeMillis();
        this.refreshPageLabels();
    }

    private void startAxisCapture(final CaptureTarget captureTarget) {
        if (!this.ensureControllerSelected()) {
            return;
        }

        this.captureTarget = captureTarget;
        this.buttonCaptureSnapshot = FpvFreecam.INPUT_MAPPER.createButtonCaptureSnapshot(this.workingConfig);
        this.axisCalibrationSession = null;
        this.captureStartTime = System.currentTimeMillis();
        this.refreshPageLabels();
    }

    private boolean ensureControllerSelected() {
        if (FpvFreecam.INPUT_MAPPER.resolveConfiguredController(this.workingConfig) != null) {
            return true;
        }

        final List<DroneInputMapper.ControllerInfo> controllers = FpvFreecam.INPUT_MAPPER.getAvailableControllers();
        if (controllers.isEmpty()) {
            return false;
        }

        final DroneInputMapper.ControllerInfo controller = controllers.getFirst();
        this.workingConfig.setController(controller.guid(), controller.displayName());
        this.saveAndRefresh();
        return true;
    }

    private EditBox addFloatField(final int x, final int y, final int width, final float initialValue, final FloatSetter setter) {
        final EditBox box = this.addPageWidget(new EditBox(this.font, x, y, width, 20, Component.empty()));
        box.setValue(String.format("%.3f", initialValue));
        box.setResponder(value -> {
            try {
                setter.set(Float.parseFloat(value));
                this.saveConfig();
            } catch (final NumberFormatException ignored) {
            }
        });
        return box;
    }

    private void addFieldLabel(final int x, final int y, final String text) {
        final StringWidget label = this.addPageWidget(new StringWidget(x, y, 120, 9, Component.literal(text), this.font));
        label.setAlpha(0.85F);
    }

    private <T extends AbstractWidget> T addPageWidget(final T widget) {
        this.pageWidgets.add(widget);
        return this.addRenderableWidget(widget);
    }

    private static String invertLabel(final boolean inverted) {
        return inverted ? "Invert: On" : "Invert: Off";
    }

    private static String buttonLabel(final int button) {
        if (button < 0) {
            return "Unbound";
        }
        return DroneInputMapper.buttonName(button);
    }

    private static DroneConfig.CrashResetMode nextCrashMode(final DroneConfig.CrashResetMode current) {
        final DroneConfig.CrashResetMode[] modes = DroneConfig.CrashResetMode.values();
        return modes[(current.ordinal() + 1) % modes.length];
    }

    private void applyRealisticFiveInchPreset() {
        DroneProfileDefaults.applyFiveInchFreestyle(this.workingConfig);
        this.workingConfig.controller.axisThrottle = org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y;
        this.workingConfig.controller.axisYaw = org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_LEFT_X;
        this.workingConfig.controller.axisPitch = org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y;
        this.workingConfig.controller.axisRoll = org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X;
        this.workingConfig.controller.invertThrottle = true;
        this.workingConfig.controller.invertYaw = false;
        this.workingConfig.controller.invertPitch = true;
        this.workingConfig.controller.invertRoll = false;
        this.workingConfig.controller.deadzone = 0.08F;
    }

    private interface FloatSetter {
        void set(float value);
    }

    private enum Page {
        CONTROLLER("Controller"),
        RATES("Rates"),
        CRAFT("Craft"),
        REALISM_CRASH("Realism & Crash");

        private final String title;

        Page(final String title) {
            this.title = title;
        }
    }

    private enum CaptureTarget {
        NONE(false, ""),
        ARM_BUTTON(false, "screen.fpvfreecam.capture_button"),
        DISARM_BUTTON(false, "screen.fpvfreecam.capture_button"),
        RESET_BUTTON(false, "screen.fpvfreecam.capture_button"),
        THROTTLE_AXIS(true, "screen.fpvfreecam.capture_axis"),
        YAW_AXIS(true, "screen.fpvfreecam.capture_axis"),
        PITCH_AXIS(true, "screen.fpvfreecam.capture_axis"),
        ROLL_AXIS(true, "screen.fpvfreecam.capture_axis");

        private final boolean axis;
        private final String promptKey;

        CaptureTarget(final boolean axis, final String promptKey) {
            this.axis = axis;
            this.promptKey = promptKey;
        }

        public boolean isAxis() {
            return this.axis;
        }

        public String promptKey() {
            return this.promptKey;
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
