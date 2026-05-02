package com.kieran.fpvfreecam.ui;

import com.kieran.fpvfreecam.FpvFreecam;
import com.kieran.fpvfreecam.config.DroneConfig;
import com.kieran.fpvfreecam.flight.DroneProfileDefaults;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import net.minecraft.client.Minecraft;
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
        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("FPV Freecam Configuration"))
                .category(controllerCategory(workingConfig))
                .category(ratesCategory(workingConfig))
                .category(craftCategory(workingConfig))
                .category(realismCrashCategory(workingConfig))
                .save(() -> save(workingConfig))
                .build()
                .generateScreen(parent);
    }

    private static ConfigCategory controllerCategory(final DroneConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Controller"))
                .tooltip(Component.literal("Gamepad selection, capture, calibration, and camera adjustment."))
                .option(ButtonOption.createBuilder()
                        .name(Component.literal("Controller Setup"))
                        .text(Component.literal("Open"))
                        .description(description("Open the custom gamepad capture and calibration screen."))
                        .action((yaclScreen, option) -> Minecraft.getInstance().setScreen(new DroneSetupScreen(yaclScreen)))
                        .build())
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
                .tooltip(Component.literal("Battery sag, prop wash, imperfections, safety debug, and crash behavior."))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Realism"))
                        .option(floatOption("Battery Sag", DroneProfileDefaults.BATTERY_SAG_STRENGTH, () -> config.realismProfile.batterySagStrength, value -> config.realismProfile.batterySagStrength = value, 0.0F, 1.0F, 0.01F))
                        .option(floatOption("Max Sag Loss", DroneProfileDefaults.BATTERY_SAG_MAX_LOSS, () -> config.realismProfile.batterySagMaxLoss, value -> config.realismProfile.batterySagMaxLoss = value, 0.0F, 0.35F, 0.01F))
                        .option(floatOption("Sag Recovery", DroneProfileDefaults.SAG_RECOVERY_SECONDS, () -> config.realismProfile.sagRecoverySeconds, value -> config.realismProfile.sagRecoverySeconds = value, 0.20F, 10.0F, 0.1F, "s"))
                        .option(floatOption("Descent Wash", DroneProfileDefaults.DESCENT_WASH_STRENGTH, () -> config.realismProfile.descentWashStrength, value -> config.realismProfile.descentWashStrength = value, 0.0F, 1.0F, 0.01F))
                        .option(floatOption("Load Imperfection", DroneProfileDefaults.LOAD_IMPERFECTION_STRENGTH, () -> config.realismProfile.loadImperfectionStrength, value -> config.realismProfile.loadImperfectionStrength = value, 0.0F, 1.0F, 0.01F))
                        .option(booleanOption("Safety Debug Line", "Render the client-only network safety debug line.", false, () -> config.realismProfile.showNetworkSafetyDebugLine, value -> config.realismProfile.showNetworkSafetyDebugLine = value))
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
                        .valueFormatter(value -> Component.literal(formatEnum(value.name()))))
                .build();
    }

    private static OptionDescription description(final String text) {
        return OptionDescription.of(Component.literal(text));
    }

    private static void save(final DroneConfig config) {
        preserveLiveControllerSettings(config, FpvFreecam.CONFIG);
        FpvFreecam.CONFIG.copyFrom(config);
        FpvFreecam.CONFIG.save();
    }

    private static void preserveLiveControllerSettings(final DroneConfig target, final DroneConfig source) {
        target.controller.controllerGuid = source.controller.controllerGuid;
        target.controller.controllerName = source.controller.controllerName;
        target.controller.armButton = source.controller.armButton;
        target.controller.disarmButton = source.controller.disarmButton;
        target.controller.resetButton = source.controller.resetButton;
        target.controller.axisThrottle = source.controller.axisThrottle;
        target.controller.axisYaw = source.controller.axisYaw;
        target.controller.axisPitch = source.controller.axisPitch;
        target.controller.axisRoll = source.controller.axisRoll;
        target.controller.axisThrottleMin = source.controller.axisThrottleMin;
        target.controller.axisThrottleMax = source.controller.axisThrottleMax;
        target.controller.axisYawMin = source.controller.axisYawMin;
        target.controller.axisYawMax = source.controller.axisYawMax;
        target.controller.axisPitchMin = source.controller.axisPitchMin;
        target.controller.axisPitchMax = source.controller.axisPitchMax;
        target.controller.axisRollMin = source.controller.axisRollMin;
        target.controller.axisRollMax = source.controller.axisRollMax;
        target.controller.invertThrottle = source.controller.invertThrottle;
        target.controller.invertYaw = source.controller.invertYaw;
        target.controller.invertPitch = source.controller.invertPitch;
        target.controller.invertRoll = source.controller.invertRoll;
        target.controller.deadzone = source.controller.deadzone;
        target.controller.allowInFlightCameraAngleAdjust = source.controller.allowInFlightCameraAngleAdjust;
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
}
