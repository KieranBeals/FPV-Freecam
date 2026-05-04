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
        final YetAnotherConfigLib yacl = YetAnotherConfigLib.createBuilder()
                .title(Component.literal("FPV Freecam Configuration"))
                .category(controllerCategory(workingConfig, controllerSession))
                .category(craftCategory(workingConfig))
                .category(ratesCategory(workingConfig))
                .category(realismCrashCategory(workingConfig))
                .save(() -> save(workingConfig))
                .build();
        return new DroneConfigScreen(yacl, parent, workingConfig, controllerSession);
    }

    private static ConfigCategory controllerCategory(
            final DroneConfig config,
            final ControllerBindingSession controllerSession
    ) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Controller"))
                .tooltip(Component.literal("Choose a controller, bind sticks and buttons, and fix inverted inputs."))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Controller Selection"))
                        .option(DynamicButtonOption.create(
                                "Controller",
                                "Select the gamepad or radio controller to use for FPV flight. Choose None to fly without controller input.",
                                controllerSession::selectedControllerName,
                                (yaclScreen, option) -> {
                                    controllerSession.cycleController();
                                    if (controllerSession.consumeConfigChanged()) {
                                        save(config);
                                    }
                                }))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Flight Buttons"))
                        .option(buttonCaptureOption("Arm Drone", "Starts the motors and lets throttle control the drone.", ControllerBindingSession.CaptureTarget.ARM_BUTTON, config, controllerSession))
                        .option(buttonCaptureOption("Disarm Drone", "Stops the motors but keeps camera detached.", ControllerBindingSession.CaptureTarget.DISARM_BUTTON, config, controllerSession))
                        .option(buttonCaptureOption("Reset", "Exit to the player camera. Also used for post-crash reset when configured.", ControllerBindingSession.CaptureTarget.RESET_BUTTON, config, controllerSession))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Flight Sticks"))
                        .option(axisCaptureOption("Throttle", "Controls climb and motor power. Move the throttle stick through its full range to bind and calibrate it.", ControllerBindingSession.CaptureTarget.THROTTLE_AXIS, config, controllerSession))
                        .option(axisCaptureOption("Yaw", "Turns the drone left or right. Move the yaw stick through its full range to bind and calibrate it.", ControllerBindingSession.CaptureTarget.YAW_AXIS, config, controllerSession))
                        .option(axisCaptureOption("Pitch", "Tilts the camera nose down or up for forward and backward movement. Move the pitch stick through its full range to bind and calibrate it.", ControllerBindingSession.CaptureTarget.PITCH_AXIS, config, controllerSession))
                        .option(axisCaptureOption("Roll", "Tilts the drone left or right. Move the roll stick through its full range to bind and calibrate it.", ControllerBindingSession.CaptureTarget.ROLL_AXIS, config, controllerSession))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Stick Direction"))
                        .option(booleanOption("Invert Throttle", "Flip throttle if pushing the stick up lowers power instead of raising it.", false, () -> config.controller.invertThrottle, value -> config.controller.invertThrottle = value))
                        .option(booleanOption("Invert Yaw", "Flip yaw if left and right turns feel backward.", false, () -> config.controller.invertYaw, value -> config.controller.invertYaw = value))
                        .option(booleanOption("Invert Pitch", "Flip pitch if pushing forward tilts the drone the wrong way.", false, () -> config.controller.invertPitch, value -> config.controller.invertPitch = value))
                        .option(booleanOption("Invert Roll", "Flip roll if left and right tilt feel backward.", false, () -> config.controller.invertRoll, value -> config.controller.invertRoll = value))
                        .option(floatOption("Stick Deadzone", "Ignores small stick movement near center to stop drift. Raise it if the drone moves when sticks are centered.", 0.0F, () -> config.controller.deadzone, value -> config.controller.deadzone = value, 0.0F, 0.95F, 0.01F))
                        .option(booleanOption("In-flight Camera Angle Adjust", "Lets controller input change camera angle while flying.", true, () -> config.controller.allowInFlightCameraAngleAdjust, value -> config.controller.allowInFlightCameraAngleAdjust = value))
                        .build())
                .build();
    }

    private static ButtonOption buttonCaptureOption(
            final String name,
            final String description,
            final ControllerBindingSession.CaptureTarget target,
            final DroneConfig workingConfig,
            final ControllerBindingSession controllerSession
    ) {
        return DynamicButtonOption.create(
                name,
                description,
                () -> controllerSession.buttonBindingLabel(target),
                (yaclScreen, option) -> {
                    controllerSession.startButtonCapture(target);
                    if (controllerSession.consumeConfigChanged()) {
                        save(workingConfig);
                    }
                });
    }

    private static ButtonOption axisCaptureOption(
            final String name,
            final String description,
            final ControllerBindingSession.CaptureTarget target,
            final DroneConfig workingConfig,
            final ControllerBindingSession controllerSession
    ) {
        return DynamicButtonOption.create(
                name,
                description,
                () -> controllerSession.axisBindingLabel(target),
                (yaclScreen, option) -> {
                    controllerSession.startAxisCapture(target);
                    if (controllerSession.consumeConfigChanged()) {
                        save(workingConfig);
                    }
                });
    }

    private static ConfigCategory ratesCategory(final DroneConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Rates"))
                .tooltip(Component.literal("Advanced stick response tuning for roll, pitch, and yaw."))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Roll"))
                        .option(floatOption("Roll Base Rate", "Sets how quickly the drone rolls near the center of the stick. Higher values feel more sensitive.", DroneProfileDefaults.ROLL_RC_RATE, () -> config.rateProfile.rollRcRate, value -> config.rateProfile.rollRcRate = value, 0.10F, 3.0F, 0.01F))
                        .option(floatOption("Roll Max Rate Boost", "Adds extra roll speed near full stick. Raise it for faster flips and rolls.", DroneProfileDefaults.ROLL_SUPER_RATE, () -> config.rateProfile.rollSuperRate, value -> config.rateProfile.rollSuperRate = value, 0.0F, 0.99F, 0.01F))
                        .option(floatOption("Roll Expo", "Softens roll around stick center while keeping full-stick speed.", DroneProfileDefaults.ROLL_EXPO, () -> config.rateProfile.rollExpo, value -> config.rateProfile.rollExpo = value, 0.0F, 1.0F, 0.01F))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Pitch"))
                        .option(floatOption("Pitch Base Rate", "Sets how quickly the drone pitches near the center of the stick. Higher values feel more sensitive.", DroneProfileDefaults.PITCH_RC_RATE, () -> config.rateProfile.pitchRcRate, value -> config.rateProfile.pitchRcRate = value, 0.10F, 3.0F, 0.01F))
                        .option(floatOption("Pitch Max Rate Boost", "Adds extra pitch speed near full stick. Raise it for faster flips.", DroneProfileDefaults.PITCH_SUPER_RATE, () -> config.rateProfile.pitchSuperRate, value -> config.rateProfile.pitchSuperRate = value, 0.0F, 0.99F, 0.01F))
                        .option(floatOption("Pitch Expo", "Softens pitch around stick center while keeping full-stick speed.", DroneProfileDefaults.PITCH_EXPO, () -> config.rateProfile.pitchExpo, value -> config.rateProfile.pitchExpo = value, 0.0F, 1.0F, 0.01F))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Yaw"))
                        .option(floatOption("Yaw Base Rate", "Sets how quickly the drone turns left or right near the center of the stick.", DroneProfileDefaults.YAW_RC_RATE, () -> config.rateProfile.yawRcRate, value -> config.rateProfile.yawRcRate = value, 0.10F, 3.0F, 0.01F))
                        .option(floatOption("Yaw Max Rate Boost", "Adds extra turn speed near full stick. Raise it for quicker spins.", DroneProfileDefaults.YAW_SUPER_RATE, () -> config.rateProfile.yawSuperRate, value -> config.rateProfile.yawSuperRate = value, 0.0F, 0.99F, 0.01F))
                        .option(floatOption("Yaw Expo", "Softens yaw around stick center while keeping full-stick turn speed.", DroneProfileDefaults.YAW_EXPO, () -> config.rateProfile.yawExpo, value -> config.rateProfile.yawExpo = value, 0.0F, 1.0F, 0.01F))
                        .build())
                .build();
    }

    private static ConfigCategory craftCategory(final DroneConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Drone Feel"))
                .tooltip(Component.literal("Main flight feel: camera angle, throttle, power, weight, drag, and response."))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Camera and Throttle"))
                        .option(floatOption("Camera Angle", "Tilts the FPV camera upward. Higher angles are better for fast forward flight; lower angles are easier for slow flying.", DroneProfileDefaults.CAMERA_ANGLE_DEG, () -> config.craftProfile.cameraAngleDeg, value -> config.craftProfile.cameraAngleDeg = value, -90.0F, 90.0F, 1.0F, "deg"))
                        .option(floatOption("Hover Throttle Point", "Sets where the throttle stick feels centered around hover. Lower values put hover lower on the stick.", DroneProfileDefaults.THROTTLE_MID, () -> config.throttleProfile.throttleMid, value -> config.throttleProfile.throttleMid = value, 0.05F, 0.95F, 0.01F))
                        .option(floatOption("Throttle Expo", "Softens throttle around the hover point for smoother height control.", DroneProfileDefaults.THROTTLE_EXPO, () -> config.throttleProfile.throttleExpo, value -> config.throttleProfile.throttleExpo = value, 0.0F, 1.0F, 0.01F))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Power and Weight"))
                        .option(floatOption("Power", "Controls how much thrust the drone has compared with its weight. Higher values climb faster and recover from dives sooner.", DroneProfileDefaults.THRUST_TO_WEIGHT, () -> config.craftProfile.thrustToWeight, value -> config.craftProfile.thrustToWeight = value, 1.2F, 12.0F, 0.1F))
                        .option(floatOption("Weight", "Changes how heavy the drone feels. Heavier drones carry momentum longer and need more room to stop.", DroneProfileDefaults.DRONE_MASS_KG, () -> config.craftProfile.massKg, value -> config.craftProfile.massKg = value, 0.10F, 3.0F, 0.01F, "kg"))
                        .option(floatOption("Motor Spool Up Time", "How long motors take to reach higher power. Lower values make throttle punchier.", DroneProfileDefaults.MOTOR_SPOOL_UP_SECONDS, () -> config.craftProfile.motorSpoolUpSeconds, value -> config.craftProfile.motorSpoolUpSeconds = value, 0.005F, 0.6F, 0.005F, "s"))
                        .option(floatOption("Motor Spool Down Time", "How long motors take to reduce power. Higher values make drops and throttle cuts feel softer.", DroneProfileDefaults.MOTOR_SPOOL_DOWN_SECONDS, () -> config.craftProfile.motorSpoolDownSeconds, value -> config.craftProfile.motorSpoolDownSeconds = value, 0.005F, 0.8F, 0.005F, "s"))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Rotation Response"))
                        .option(floatOption("Roll Response Time", "How quickly the drone reaches the requested roll rate. Lower values feel sharper.", DroneProfileDefaults.ROLL_RESPONSE_SECONDS, () -> config.craftProfile.rollResponseSeconds, value -> config.craftProfile.rollResponseSeconds = value, 0.010F, 0.250F, 0.005F, "s"))
                        .option(floatOption("Pitch Response Time", "How quickly the drone reaches the requested pitch rate. Lower values feel sharper.", DroneProfileDefaults.PITCH_RESPONSE_SECONDS, () -> config.craftProfile.pitchResponseSeconds, value -> config.craftProfile.pitchResponseSeconds = value, 0.010F, 0.250F, 0.005F, "s"))
                        .option(floatOption("Yaw Response Time", "How quickly the drone reaches the requested yaw rate. Lower values feel sharper.", DroneProfileDefaults.YAW_RESPONSE_SECONDS, () -> config.craftProfile.yawResponseSeconds, value -> config.craftProfile.yawResponseSeconds = value, 0.010F, 0.350F, 0.005F, "s"))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Drag"))
                        .option(floatOption("Forward Drag", "Air resistance while moving forward or backward. Higher values slow forward flight more quickly.", DroneProfileDefaults.FORWARD_DRAG, () -> config.craftProfile.forwardDrag, value -> config.craftProfile.forwardDrag = value, 0.005F, 0.35F, 0.005F))
                        .option(floatOption("Side Drag", "Air resistance while sliding sideways. Higher values make side movement bleed off faster.", DroneProfileDefaults.SIDE_DRAG, () -> config.craftProfile.sideDrag, value -> config.craftProfile.sideDrag = value, 0.020F, 0.50F, 0.005F))
                        .option(floatOption("Vertical Drag", "Air resistance while climbing or falling. Higher values make vertical speed settle faster.", DroneProfileDefaults.VERTICAL_DRAG, () -> config.craftProfile.verticalDrag, value -> config.craftProfile.verticalDrag = value, 0.010F, 0.45F, 0.005F))
                        .build())
                .build();
    }

    private static ConfigCategory realismCrashCategory(final DroneConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Realism and Crashes"))
                .tooltip(Component.literal("Optional realism effects and what happens when the drone hits something."))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Realism"))
                        .option(floatOption("Battery Sag Strength", "Reduces available power during hard throttle, like a real battery under load.", DroneProfileDefaults.BATTERY_SAG_STRENGTH, () -> config.realismProfile.batterySagStrength, value -> config.realismProfile.batterySagStrength = value, 0.0F, 1.0F, 0.01F))
                        .option(floatOption("Maximum Sag Power Loss", "Caps how much power battery sag can remove. Lower values keep the drone more consistent.", DroneProfileDefaults.BATTERY_SAG_MAX_LOSS, () -> config.realismProfile.batterySagMaxLoss, value -> config.realismProfile.batterySagMaxLoss = value, 0.0F, 0.35F, 0.01F))
                        .option(floatOption("Sag Recovery Time", "How long power takes to recover after heavy throttle use.", DroneProfileDefaults.SAG_RECOVERY_SECONDS, () -> config.realismProfile.sagRecoverySeconds, value -> config.realismProfile.sagRecoverySeconds = value, 0.20F, 10.0F, 0.1F, "s"))
                        .option(floatOption("Prop Wash on Descent", "Adds wobble when dropping through disturbed air. Raise it for more real FPV turbulence.", DroneProfileDefaults.DESCENT_WASH_STRENGTH, () -> config.realismProfile.descentWashStrength, value -> config.realismProfile.descentWashStrength = value, 0.0F, 1.0F, 0.01F))
                        .option(floatOption("Motor Load Imperfection", "Adds small unevenness under throttle so the drone feels less perfectly digital.", DroneProfileDefaults.LOAD_IMPERFECTION_STRENGTH, () -> config.realismProfile.loadImperfectionStrength, value -> config.realismProfile.loadImperfectionStrength = value, 0.0F, 1.0F, 0.01F))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Crashes"))
                        .option(floatOption("Glancing Hit Speed", "Impacts below this speed are treated as light bumps instead of full crashes.", DroneProfileDefaults.GLANCING_IMPACT_SPEED, () -> config.crashSettings.lowSpeedGlanceThreshold, value -> config.crashSettings.lowSpeedGlanceThreshold = value, 0.2F, 20.0F, 0.1F, "m/s"))
                        .option(floatOption("Hard Crash Speed", "Impacts above this speed can trigger a hard crash even if the hit is brief.", DroneProfileDefaults.HARD_IMPACT_SPEED, () -> config.crashSettings.hardImpactSpeedThreshold, value -> config.crashSettings.hardImpactSpeedThreshold = value, 1.0F, 40.0F, 0.1F, "m/s"))
                        .option(floatOption("Hard Crash Energy", "Impact force needed for a hard crash. Raise it if crashes feel too sensitive.", DroneProfileDefaults.HARD_IMPACT_ENERGY, () -> config.crashSettings.hardImpactEnergyThreshold, value -> config.crashSettings.hardImpactEnergyThreshold = value, 1.0F, 300.0F, 1.0F))
                        .option(crashResetModeOption(config))
                        .option(booleanOption("Exit FPV When Player Takes Damage", "Returns to the player immediately if your Minecraft character takes damage while flying.", true, () -> config.crashSettings.exitToPlayerOnDamage, value -> config.crashSettings.exitToPlayerOnDamage = value))
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
            final String description,
            final float defaultValue,
            final Supplier<Float> getter,
            final Consumer<Float> setter,
            final float min,
            final float max,
            final float step
    ) {
        return floatOption(name, description, defaultValue, getter, setter, min, max, step, null);
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
        return floatOption(name, null, defaultValue, getter, setter, min, max, step, suffix);
    }

    private static Option<Float> floatOption(
            final String name,
            final String description,
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
                .description(description == null ? OptionDescription.EMPTY : description(description))
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
                .description(description("Choose what happens after a crash: leave FPV, respawn at the last checkpoint, or ignore collisions."))
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
