package com.kieran.clientdronecam.ui;

import com.kieran.clientdronecam.ClientDroneCam;
import com.kieran.clientdronecam.config.DroneConfig;
import com.kieran.clientdronecam.input.DroneInputMapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class DroneSetupScreen extends Screen {
    private static final int LABEL_COLOR = 0xE0E0E0;
    private static final long CAPTURE_GRACE_MS = 200L;

    private final Screen parent;
    private final DroneConfig workingConfig;

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

    public DroneSetupScreen(@Nullable final Screen parent) {
        super(Component.translatable("screen.clientdronecam.setup"));
        this.parent = parent;
        this.workingConfig = ClientDroneCam.CONFIG.copy();
    }

    @Override
    protected void init() {
        final int centerX = this.width / 2;
        final int left = centerX - 155;
        final int right = centerX + 5;
        final int wideButtonWidth = 150;
        final int axisButtonWidth = 110;
        final int invertButtonWidth = 70;
        int y = 40;

        this.controllerButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.cycleController();
            this.saveAndRefresh();
        }).bounds(left, y, 310, 20).build());
        y += 28;

        this.toggleButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.startButtonCapture(CaptureTarget.TOGGLE_BUTTON))
                .bounds(left, y, wideButtonWidth, 20).build());
        this.exitButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.startButtonCapture(CaptureTarget.EXIT_BUTTON))
                .bounds(right, y, wideButtonWidth, 20).build());
        y += 36;

        this.throttleAxisButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.startAxisCapture(CaptureTarget.THROTTLE_AXIS))
                .bounds(left, y, axisButtonWidth, 20).build());
        this.throttleInvertButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.workingConfig.invertThrottle = !this.workingConfig.invertThrottle;
            this.saveAndRefresh();
        }).bounds(left + axisButtonWidth + 10, y, invertButtonWidth, 20).build());
        this.yawAxisButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.startAxisCapture(CaptureTarget.YAW_AXIS))
                .bounds(right, y, axisButtonWidth, 20).build());
        this.yawInvertButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.workingConfig.invertYaw = !this.workingConfig.invertYaw;
            this.saveAndRefresh();
        }).bounds(right + axisButtonWidth + 10, y, invertButtonWidth, 20).build());
        y += 38;

        this.pitchAxisButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.startAxisCapture(CaptureTarget.PITCH_AXIS))
                .bounds(left, y, axisButtonWidth, 20).build());
        this.pitchInvertButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.workingConfig.invertPitch = !this.workingConfig.invertPitch;
            this.saveAndRefresh();
        }).bounds(left + axisButtonWidth + 10, y, invertButtonWidth, 20).build());
        this.rollAxisButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.startAxisCapture(CaptureTarget.ROLL_AXIS))
                .bounds(right, y, axisButtonWidth, 20).build());
        this.rollInvertButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.workingConfig.invertRoll = !this.workingConfig.invertRoll;
            this.saveAndRefresh();
        }).bounds(right + axisButtonWidth + 10, y, invertButtonWidth, 20).build());
        y += 36;

        this.deadzoneBox = this.addRenderableWidget(new EditBox(this.font, left, y, 90, 20, Component.translatable("screen.clientdronecam.deadzone")));
        this.deadzoneBox.setValue(String.format("%.2f", this.workingConfig.deadzone));
        this.deadzoneBox.setResponder(value -> this.handleDeadzoneEdited(value));
        this.addRenderableWidget(Button.builder(Component.translatable("screen.clientdronecam.done"), button -> this.onClose())
                .bounds(right + 160, this.height - 28, 70, 20).build());

        this.refreshLabels();
    }

    @Override
    public void tick() {
        super.tick();

        if (this.captureTarget == CaptureTarget.NONE || System.currentTimeMillis() - this.captureStartTime < CAPTURE_GRACE_MS) {
            return;
        }

        final Integer capture = this.captureTarget.isAxis()
                ? ClientDroneCam.INPUT_MAPPER.detectMovedAxis(this.workingConfig)
                : ClientDroneCam.INPUT_MAPPER.detectPressedButton(this.workingConfig, this.buttonCaptureSnapshot);

        if (capture == null) {
            return;
        }

        switch (this.captureTarget) {
            case TOGGLE_BUTTON -> this.workingConfig.toggleButton = capture;
            case EXIT_BUTTON -> this.workingConfig.exitButton = capture;
            case THROTTLE_AXIS -> this.workingConfig.axisThrottle = capture;
            case YAW_AXIS -> this.workingConfig.axisYaw = capture;
            case PITCH_AXIS -> this.workingConfig.axisPitch = capture;
            case ROLL_AXIS -> this.workingConfig.axisRoll = capture;
            case NONE -> {
            }
        }

        this.captureTarget = CaptureTarget.NONE;
        this.buttonCaptureSnapshot = null;
        this.saveAndRefresh();
    }

    @Override
    public void onClose() {
        this.saveConfig();
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (this.captureTarget != CaptureTarget.NONE && keyCode == 256) {
            this.captureTarget = CaptureTarget.NONE;
            this.buttonCaptureSnapshot = null;
            this.refreshLabels();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(final GuiGraphics guiGraphics, final int mouseX, final int mouseY, final float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        final int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 15, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("screen.clientdronecam.controller"), centerX - 155, 31, LABEL_COLOR);
        guiGraphics.drawString(this.font, Component.translatable("screen.clientdronecam.bindings"), centerX - 155, 59, LABEL_COLOR);
        guiGraphics.drawString(this.font, Component.translatable("screen.clientdronecam.axes"), centerX - 155, 87, LABEL_COLOR);
        guiGraphics.drawString(this.font, Component.translatable("screen.clientdronecam.left_stick"), centerX - 155, 101, LABEL_COLOR);
        guiGraphics.drawString(this.font, Component.translatable("screen.clientdronecam.right_stick"), centerX - 155, 139, LABEL_COLOR);
        guiGraphics.drawString(this.font, Component.translatable("screen.clientdronecam.deadzone"), centerX - 155, 175, LABEL_COLOR);

        if (this.captureTarget != CaptureTarget.NONE) {
            guiGraphics.drawCenteredString(this.font, Component.translatable(this.captureTarget.promptKey()), centerX, this.height - 50, 0xFFD37F);
        } else {
            final List<DroneInputMapper.ControllerInfo> controllers = ClientDroneCam.INPUT_MAPPER.getAvailableControllers();
            final String status = controllers.isEmpty()
                    ? "No controllers detected"
                    : controllers.size() + " controller(s) detected";
            guiGraphics.drawCenteredString(this.font, Component.literal(status), centerX, this.height - 50, 0xA0A0A0);
        }

        this.renderDiagnostics(guiGraphics, centerX - 155, 205);
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
        this.buttonCaptureSnapshot = null;
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
            return Component.literal(label + ": ...");
        }
        return Component.literal(label + ": " + value);
    }

    private void renderDiagnostics(final GuiGraphics guiGraphics, final int left, final int top) {
        final DroneInputMapper.RawInputSnapshot snapshot = ClientDroneCam.INPUT_MAPPER.snapshotRawInputs(this.workingConfig);
        guiGraphics.drawString(this.font, Component.literal("Diagnostics"), left, top, LABEL_COLOR);

        int y = top + this.font.lineHeight + 2;
        final List<String> lines = this.buildDiagnosticsLines(snapshot);
        final int maxY = this.height - 38;
        for (final String line : lines) {
            if (y > maxY) {
                guiGraphics.drawString(this.font, Component.literal("..."), left, y, 0xA0A0A0);
                break;
            }

            guiGraphics.drawString(this.font, Component.literal(line), left, y, 0xA0A0A0);
            y += this.font.lineHeight + 1;
        }
    }

    private List<String> buildDiagnosticsLines(final @Nullable DroneInputMapper.RawInputSnapshot snapshot) {
        final List<String> lines = new ArrayList<>();
        if (snapshot == null) {
            lines.add("No live controller snapshot");
            return lines;
        }

        lines.add("Selected: " + snapshot.controller().displayName() + " [J" + (snapshot.controller().joystickId() + 1) + "]");
        lines.add("Mapped: " + (snapshot.controller().gamepadMapped() ? "GLFW gamepad" : "raw joystick only"));
        lines.add("Raw axes (" + snapshot.rawAxes().length + ")");
        appendAxes(lines, snapshot.rawAxes(), 4);
        lines.add("Raw buttons (" + snapshot.rawButtons().length + ")");
        appendButtons(lines, snapshot.rawButtons(), 12, "B");
        lines.add("Raw hats (" + snapshot.rawHats().length + ")");
        appendHats(lines, snapshot.rawHats(), 8);

        if (snapshot.controller().gamepadMapped()) {
            lines.add("Mapped axes (" + snapshot.mappedAxes().length + ")");
            appendAxes(lines, snapshot.mappedAxes(), 4);
            lines.add("Mapped buttons (" + snapshot.mappedButtons().length + ")");
            appendButtons(lines, snapshot.mappedButtons(), 12, "G");
        }

        return lines;
    }

    private static void appendAxes(final List<String> lines, final float[] axes, final int perLine) {
        if (axes.length == 0) {
            lines.add("none");
            return;
        }

        for (int index = 0; index < axes.length; index += perLine) {
            final StringBuilder builder = new StringBuilder();
            for (int offset = 0; offset < perLine && index + offset < axes.length; offset++) {
                final int axis = index + offset;
                if (builder.length() > 0) {
                    builder.append("  ");
                }
                builder.append(String.format("A%02d:%+.2f", axis, axes[axis]));
            }
            lines.add(builder.toString());
        }
    }

    private static void appendButtons(final List<String> lines, final byte[] buttons, final int perLine, final String prefix) {
        if (buttons.length == 0) {
            lines.add("none");
            return;
        }

        for (int index = 0; index < buttons.length; index += perLine) {
            final StringBuilder builder = new StringBuilder();
            for (int offset = 0; offset < perLine && index + offset < buttons.length; offset++) {
                final int button = index + offset;
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(String.format("%s%02d:%d", prefix, button, buttons[button] == 0 ? 0 : 1));
            }
            lines.add(builder.toString());
        }
    }

    private static void appendHats(final List<String> lines, final byte[] hats, final int perLine) {
        if (hats.length == 0) {
            lines.add("none");
            return;
        }

        for (int index = 0; index < hats.length; index += perLine) {
            final StringBuilder builder = new StringBuilder();
            for (int offset = 0; offset < perLine && index + offset < hats.length; offset++) {
                final int hat = index + offset;
                if (builder.length() > 0) {
                    builder.append("  ");
                }
                builder.append("H").append(hat).append(':').append(formatHat(hats[hat]));
            }
            lines.add(builder.toString());
        }
    }

    private static String formatHat(final byte hat) {
        final int value = hat & 0xFF;
        if (value == GLFW.GLFW_HAT_CENTERED) {
            return "C";
        }

        final StringBuilder builder = new StringBuilder();
        if ((value & GLFW.GLFW_HAT_UP) != 0) {
            builder.append('U');
        }
        if ((value & GLFW.GLFW_HAT_RIGHT) != 0) {
            builder.append('R');
        }
        if ((value & GLFW.GLFW_HAT_DOWN) != 0) {
            builder.append('D');
        }
        if ((value & GLFW.GLFW_HAT_LEFT) != 0) {
            builder.append('L');
        }
        return builder.isEmpty() ? Integer.toHexString(value) : builder.toString();
    }

    private static String invertLabel(final boolean inverted) {
        return inverted ? "Invert: On" : "Invert: Off";
    }

    private enum CaptureTarget {
        NONE(false, ""),
        TOGGLE_BUTTON(false, "screen.clientdronecam.capture_button"),
        EXIT_BUTTON(false, "screen.clientdronecam.capture_button"),
        THROTTLE_AXIS(true, "screen.clientdronecam.capture_axis"),
        YAW_AXIS(true, "screen.clientdronecam.capture_axis"),
        PITCH_AXIS(true, "screen.clientdronecam.capture_axis"),
        ROLL_AXIS(true, "screen.clientdronecam.capture_axis");

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
}
