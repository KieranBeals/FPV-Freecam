package com.kieran.fpvfreecam.ui;

import com.kieran.fpvfreecam.FpvFreecam;
import com.kieran.fpvfreecam.config.DroneConfig;
import com.kieran.fpvfreecam.flight.DroneProfileDefaults;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class DroneConfigScreens {
    private DroneConfigScreens() {
    }

    public static Screen create(final Screen parent) {
        final DroneConfig workingConfig = FpvFreecam.CONFIG.copy();
        final ControllerBindingSession controllerSession = new ControllerBindingSession(workingConfig);
        return create(parent, workingConfig, controllerSession);
    }

    static Screen create(
            final Screen parent,
            final DroneConfig workingConfig,
            final ControllerBindingSession controllerSession
    ) {
        final DroneConfigScreen[] screenRef = new DroneConfigScreen[1];
        final Runnable refreshScreen = () -> {
            if (screenRef[0] != null) {
                screenRef[0].rebuild();
            }
        };
        final YetAnotherConfigLib yacl = YetAnotherConfigLib.createBuilder()
                .title(Component.literal("FPV Freecam Configuration"))
                .category(controllerCategory(workingConfig, controllerSession, refreshScreen))
                .category(ratesCategory(workingConfig))
                .category(craftCategory(workingConfig))
                .category(realismCrashCategory(workingConfig))
                .save(() -> save(workingConfig))
                .build();
        screenRef[0] = new DroneConfigScreen(yacl, parent, workingConfig, controllerSession);
        return screenRef[0];
    }

    private static ConfigCategory controllerCategory(
            final DroneConfig config,
            final ControllerBindingSession controllerSession,
            final Runnable refreshScreen
    ) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Controller"))
                .tooltip(Component.literal("Gamepad selection, capture, calibration, and camera adjustment."))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Selection"))
                        .option(ButtonOption.createBuilder()
                                .name(Component.literal("Controller"))
                                .text(Component.literal(controllerSession.selectedControllerName()))
                                .description(description("Cycle through connected controllers and None. Shows None when no controller is selected or available."))
                                .action((yaclScreen, option) -> {
                                    controllerSession.cycleController();
                                    refreshScreen.run();
                                })
                                .build())
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Buttons"))
                        .option(buttonCaptureOption("Arm", "Button used to arm the drone.", ControllerBindingSession.CaptureTarget.ARM_BUTTON, controllerSession, refreshScreen))
                        .option(buttonCaptureOption("Disarm", "Button used to disarm the drone.", ControllerBindingSession.CaptureTarget.DISARM_BUTTON, controllerSession, refreshScreen))
                        .option(buttonCaptureOption("Reset", "Button used to reset after a crash.", ControllerBindingSession.CaptureTarget.RESET_BUTTON, controllerSession, refreshScreen))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Axes"))
                        .option(axisCaptureOption("Throttle", "Move the throttle stick through its full range to bind and calibrate it.", ControllerBindingSession.CaptureTarget.THROTTLE_AXIS, controllerSession, refreshScreen))
                        .option(axisCaptureOption("Yaw", "Move the yaw stick through its full range to bind and calibrate it.", ControllerBindingSession.CaptureTarget.YAW_AXIS, controllerSession, refreshScreen))
                        .option(axisCaptureOption("Pitch", "Move the pitch stick through its full range to bind and calibrate it.", ControllerBindingSession.CaptureTarget.PITCH_AXIS, controllerSession, refreshScreen))
                        .option(axisCaptureOption("Roll", "Move the roll stick through its full range to bind and calibrate it.", ControllerBindingSession.CaptureTarget.ROLL_AXIS, controllerSession, refreshScreen))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Axis Behavior"))
                        .option(booleanOption("Invert Throttle", "Invert the throttle axis input.", false, () -> config.controller.invertThrottle, value -> config.controller.invertThrottle = value))
                        .option(booleanOption("Invert Yaw", "Invert the yaw axis input.", false, () -> config.controller.invertYaw, value -> config.controller.invertYaw = value))
                        .option(booleanOption("Invert Pitch", "Invert the pitch axis input.", false, () -> config.controller.invertPitch, value -> config.controller.invertPitch = value))
                        .option(booleanOption("Invert Roll", "Invert the roll axis input.", false, () -> config.controller.invertRoll, value -> config.controller.invertRoll = value))
                        .option(floatOption("Deadzone", 0.08F, () -> config.controller.deadzone, value -> config.controller.deadzone = value, 0.0F, 0.95F, 0.01F))
                        .option(booleanOption("In-flight Camera Adjust", "Allow controller input to adjust camera angle while flying.", true, () -> config.controller.allowInFlightCameraAngleAdjust, value -> config.controller.allowInFlightCameraAngleAdjust = value))
                        .build())
                .build();
    }

    private static ButtonOption buttonCaptureOption(
            final String name,
            final String description,
            final ControllerBindingSession.CaptureTarget target,
            final ControllerBindingSession controllerSession,
            final Runnable refreshScreen
    ) {
        return ButtonOption.createBuilder()
                .name(Component.literal(name))
                .text(Component.literal(controllerSession.buttonBindingLabel(target)))
                .description(description(description))
                .action((yaclScreen, option) -> {
                    controllerSession.startButtonCapture(target);
                    refreshScreen.run();
                })
                .build();
    }

    private static ButtonOption axisCaptureOption(
            final String name,
            final String description,
            final ControllerBindingSession.CaptureTarget target,
            final ControllerBindingSession controllerSession,
            final Runnable refreshScreen
    ) {
        return ButtonOption.createBuilder()
                .name(Component.literal(name))
                .text(Component.literal(controllerSession.axisBindingLabel(target)))
                .description(description(description))
                .action((yaclScreen, option) -> {
                    controllerSession.startAxisCapture(target);
                    refreshScreen.run();
                })
                .build();
    }

    private static ConfigCategory ratesCategory(final DroneConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Rates"))
                .tooltip(Component.literal("Roll, pitch, and yaw stick response tuning."))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Roll"))
                        .option(floatOption("RC Rate", DroneProfileDefaults.ROLL_RC_RATE, () -> config.rateProfile.rollRcRate, value -> config.rateProfile.rollRcRate = value, 0.10F, 3.0F, 0.01F))
                        .option(floatOption("Super Rate", DroneProfileDefaults.ROLL_SUPER_RATE, () -> config.rateProfile.rollSuperRate, value -> config.rateProfile.rollSuperRate = value, 0.0F, 0.99F, 0.01F))
                        .option(floatOption("Expo", DroneProfileDefaults.ROLL_EXPO, () -> config.rateProfile.rollExpo, value -> config.rateProfile.rollExpo = value, 0.0F, 1.0F, 0.01F))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Pitch"))
                        .option(floatOption("RC Rate", DroneProfileDefaults.PITCH_RC_RATE, () -> config.rateProfile.pitchRcRate, value -> config.rateProfile.pitchRcRate = value, 0.10F, 3.0F, 0.01F))
                        .option(floatOption("Super Rate", DroneProfileDefaults.PITCH_SUPER_RATE, () -> config.rateProfile.pitchSuperRate, value -> config.rateProfile.pitchSuperRate = value, 0.0F, 0.99F, 0.01F))
                        .option(floatOption("Expo", DroneProfileDefaults.PITCH_EXPO, () -> config.rateProfile.pitchExpo, value -> config.rateProfile.pitchExpo = value, 0.0F, 1.0F, 0.01F))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Yaw"))
                        .option(floatOption("RC Rate", DroneProfileDefaults.YAW_RC_RATE, () -> config.rateProfile.yawRcRate, value -> config.rateProfile.yawRcRate = value, 0.10F, 3.0F, 0.01F))
                        .option(floatOption("Super Rate", DroneProfileDefaults.YAW_SUPER_RATE, () -> config.rateProfile.yawSuperRate, value -> config.rateProfile.yawSuperRate = value, 0.0F, 0.99F, 0.01F))
                        .option(floatOption("Expo", DroneProfileDefaults.YAW_EXPO, () -> config.rateProfile.yawExpo, value -> config.rateProfile.yawExpo = value, 0.0F, 1.0F, 0.01F))
                        .build())
                .build();
    }

    private static ConfigCategory craftCategory(final DroneConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Craft"))
                .tooltip(Component.literal("Camera angle, throttle curve, craft mass, thrust, drag, and response."))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Camera and Throttle"))
                        .option(floatOption("Camera Angle", DroneProfileDefaults.CAMERA_ANGLE_DEG, () -> config.craftProfile.cameraAngleDeg, value -> config.craftProfile.cameraAngleDeg = value, -90.0F, 90.0F, 1.0F, "deg"))
                        .option(floatOption("Throttle Mid", DroneProfileDefaults.THROTTLE_MID, () -> config.throttleProfile.throttleMid, value -> config.throttleProfile.throttleMid = value, 0.05F, 0.95F, 0.01F))
                        .option(floatOption("Throttle Expo", DroneProfileDefaults.THROTTLE_EXPO, () -> config.throttleProfile.throttleExpo, value -> config.throttleProfile.throttleExpo = value, 0.0F, 1.0F, 0.01F))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Power and Mass"))
                        .option(floatOption("Thrust to Weight", DroneProfileDefaults.THRUST_TO_WEIGHT, () -> config.craftProfile.thrustToWeight, value -> config.craftProfile.thrustToWeight = value, 1.2F, 12.0F, 0.1F))
                        .option(floatOption("Mass", DroneProfileDefaults.DRONE_MASS_KG, () -> config.craftProfile.massKg, value -> config.craftProfile.massKg = value, 0.10F, 3.0F, 0.01F, "kg"))
                        .option(floatOption("Motor Spool Up", DroneProfileDefaults.MOTOR_SPOOL_UP_SECONDS, () -> config.craftProfile.motorSpoolUpSeconds, value -> config.craftProfile.motorSpoolUpSeconds = value, 0.005F, 0.6F, 0.005F, "s"))
                        .option(floatOption("Motor Spool Down", DroneProfileDefaults.MOTOR_SPOOL_DOWN_SECONDS, () -> config.craftProfile.motorSpoolDownSeconds, value -> config.craftProfile.motorSpoolDownSeconds = value, 0.005F, 0.8F, 0.005F, "s"))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Response"))
                        .option(floatOption("Roll Response", DroneProfileDefaults.ROLL_RESPONSE_SECONDS, () -> config.craftProfile.rollResponseSeconds, value -> config.craftProfile.rollResponseSeconds = value, 0.010F, 0.250F, 0.005F, "s"))
                        .option(floatOption("Pitch Response", DroneProfileDefaults.PITCH_RESPONSE_SECONDS, () -> config.craftProfile.pitchResponseSeconds, value -> config.craftProfile.pitchResponseSeconds = value, 0.010F, 0.250F, 0.005F, "s"))
                        .option(floatOption("Yaw Response", DroneProfileDefaults.YAW_RESPONSE_SECONDS, () -> config.craftProfile.yawResponseSeconds, value -> config.craftProfile.yawResponseSeconds = value, 0.010F, 0.350F, 0.005F, "s"))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Drag"))
                        .option(floatOption("Forward Drag", DroneProfileDefaults.FORWARD_DRAG, () -> config.craftProfile.forwardDrag, value -> config.craftProfile.forwardDrag = value, 0.005F, 0.35F, 0.005F))
                        .option(floatOption("Side Drag", DroneProfileDefaults.SIDE_DRAG, () -> config.craftProfile.sideDrag, value -> config.craftProfile.sideDrag = value, 0.020F, 0.50F, 0.005F))
                        .option(floatOption("Vertical Drag", DroneProfileDefaults.VERTICAL_DRAG, () -> config.craftProfile.verticalDrag, value -> config.craftProfile.verticalDrag = value, 0.010F, 0.45F, 0.005F))
                        .build())
                .build();
    }

    private static ConfigCategory realismCrashCategory(final DroneConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Realism and Crash"))
                .tooltip(Component.literal("Battery sag, prop wash, imperfections, and crash behavior."))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Current Status"))
                        .option(LabelOption.createBuilder()
                                .line(Component.literal("Crash Mode: " + formatCrashResetMode(config.crashSettings.crashResetMode)))
                                .line(Component.literal("Camera Angle: " + String.format(Locale.ROOT, "%.0f deg", config.craftProfile.cameraAngleDeg)))
                                .build())
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Realism"))
                        .option(floatOption("Battery Sag", DroneProfileDefaults.BATTERY_SAG_STRENGTH, () -> config.realismProfile.batterySagStrength, value -> config.realismProfile.batterySagStrength = value, 0.0F, 1.0F, 0.01F))
                        .option(floatOption("Max Sag Loss", DroneProfileDefaults.BATTERY_SAG_MAX_LOSS, () -> config.realismProfile.batterySagMaxLoss, value -> config.realismProfile.batterySagMaxLoss = value, 0.0F, 0.35F, 0.01F))
                        .option(floatOption("Sag Recovery", DroneProfileDefaults.SAG_RECOVERY_SECONDS, () -> config.realismProfile.sagRecoverySeconds, value -> config.realismProfile.sagRecoverySeconds = value, 0.20F, 10.0F, 0.1F, "s"))
                        .option(floatOption("Descent Wash", DroneProfileDefaults.DESCENT_WASH_STRENGTH, () -> config.realismProfile.descentWashStrength, value -> config.realismProfile.descentWashStrength = value, 0.0F, 1.0F, 0.01F))
                        .option(floatOption("Load Imperfection", DroneProfileDefaults.LOAD_IMPERFECTION_STRENGTH, () -> config.realismProfile.loadImperfectionStrength, value -> config.realismProfile.loadImperfectionStrength = value, 0.0F, 1.0F, 0.01F))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Crash"))
                        .option(floatOption("Glance Speed", DroneProfileDefaults.GLANCING_IMPACT_SPEED, () -> config.crashSettings.lowSpeedGlanceThreshold, value -> config.crashSettings.lowSpeedGlanceThreshold = value, 0.2F, 20.0F, 0.1F, "m/s"))
                        .option(floatOption("Hard Impact Speed", DroneProfileDefaults.HARD_IMPACT_SPEED, () -> config.crashSettings.hardImpactSpeedThreshold, value -> config.crashSettings.hardImpactSpeedThreshold = value, 1.0F, 40.0F, 0.1F, "m/s"))
                        .option(floatOption("Hard Impact Energy", DroneProfileDefaults.HARD_IMPACT_ENERGY, () -> config.crashSettings.hardImpactEnergyThreshold, value -> config.crashSettings.hardImpactEnergyThreshold = value, 1.0F, 300.0F, 1.0F))
                        .option(crashResetModeOption(config))
                        .option(booleanOption("Exit To Player On Damage", "Exit FPV mode when the player takes damage.", true, () -> config.crashSettings.exitToPlayerOnDamage, value -> config.crashSettings.exitToPlayerOnDamage = value))
                        .build())
                .build();
    }

    private static Option<Float> floatOption(
            final String name,
            final float defaultValue,
            final Supplier<Float> getter,
            final Consumer<Float> setter,
            final float min,
            final float max,
            final float step
    ) {
        return floatOption(name, defaultValue, getter, setter, min, max, step, null);
    }

    private static Option<Float> floatOption(
            final String name,
            final float defaultValue,
            final Supplier<Float> getter,
            final Consumer<Float> setter,
            final float min,
            final float max,
            final float step,
            final String suffix
    ) {
        return Option.<Float>createBuilder()
                .name(Component.literal(name))
                .binding(defaultValue, getter, setter)
                .controller(option -> FloatSliderControllerBuilder.create(option)
                        .range(min, max)
                        .step(step)
                        .valueFormatter(value -> Component.literal(formatFloat(value, suffix))))
                .build();
    }

    private static Option<Boolean> booleanOption(
            final String name,
            final String description,
            final boolean defaultValue,
            final Supplier<Boolean> getter,
            final Consumer<Boolean> setter
    ) {
        return Option.<Boolean>createBuilder()
                .name(Component.literal(name))
                .description(description(description))
                .binding(defaultValue, getter, setter)
                .controller(option -> BooleanControllerBuilder.create(option).yesNoFormatter().coloured(true))
                .build();
    }

    private static Option<DroneConfig.CrashResetMode> crashResetModeOption(final DroneConfig config) {
        return Option.<DroneConfig.CrashResetMode>createBuilder()
                .name(Component.literal("Crash Reset Mode"))
                .binding(DroneConfig.CrashResetMode.EXIT_TO_PLAYER, () -> config.crashSettings.crashResetMode, value -> config.crashSettings.crashResetMode = value)
                .controller(option -> EnumControllerBuilder.create(option)
                        .enumClass(DroneConfig.CrashResetMode.class)
                        .valueFormatter(value -> Component.literal(formatCrashResetMode(value))))
                .build();
    }

    private static OptionDescription description(final String text) {
        return OptionDescription.of(Component.literal(text));
    }

    private static void save(final DroneConfig config) {
        FpvFreecam.CONFIG.copyFrom(config);
        FpvFreecam.CONFIG.save();
    }

    private static String formatFloat(final float value, final String suffix) {
        final String formatted = String.format(Locale.ROOT, value < 1.0F ? "%.3f" : "%.2f", value);
        if (suffix == null) {
            return formatted;
        }
        return formatted + " " + suffix;
    }

    private static String formatEnum(final String value) {
        final String[] parts = value.toLowerCase(Locale.ROOT).split("_");
        final StringBuilder builder = new StringBuilder();
        for (final String part : parts) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private static String formatCrashResetMode(final DroneConfig.CrashResetMode mode) {
        return formatEnum(mode == null ? DroneConfig.CrashResetMode.EXIT_TO_PLAYER.name() : mode.name());
    }
}
