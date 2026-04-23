package com.kieran.fpvfreecam.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kieran.fpvfreecam.FpvFreecam;
import com.kieran.fpvfreecam.flight.DroneProfileDefaults;
import com.kieran.fpvfreecam.platform.ClientConfigPaths;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DroneConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "fpv-freecam.json";
    private static final int LEGACY_SCHEMA_VERSION = 1;
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private transient Path path;
    public int schemaVersion = CURRENT_SCHEMA_VERSION;

    public SimulationMode simulationMode = SimulationMode.CLIENT_ONLY;
    public String clientOnlyNotice = "Client-side FPV freecam simulation only. No FPV packets are sent. Intended for servers that already allow freecam.";

    public ControllerConfig controller = ControllerConfig.defaults();
    public RateProfile rateProfile = RateProfile.defaults();
    public ThrottleProfile throttleProfile = ThrottleProfile.defaults();
    public CraftProfile craftProfile = CraftProfile.defaults();
    public RealismProfile realismProfile = RealismProfile.defaults();
    public CrashSettings crashSettings = CrashSettings.defaults();

    private DroneConfig(final Path path) {
        this.path = path;
    }

    public static DroneConfig load(final ClientConfigPaths configPaths) {
        final Path path = configPaths.configDirectory().resolve(FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                final DroneConfig config = defaults(path);
                config.save();
                return config;
            }

            try (Reader reader = Files.newBufferedReader(path)) {
                final JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed == null || !parsed.isJsonObject()) {
                    return rewriteDefaults(path);
                }

                final JsonObject json = parsed.getAsJsonObject();
                final JsonObject migrated = migrateToCurrentSchema(json);
                final DroneConfig config = parseGrouped(path, migrated);
                if (config == null) {
                    return rewriteDefaults(path);
                }

                config.ensureDefaults();
                config.clamp();
                if (!json.equals(migrated)) {
                    config.save();
                }
                return config;
            }
        } catch (final Exception exception) {
            FpvFreecam.LOGGER.error("Failed to load drone config", exception);
            return rewriteDefaults(path);
        }
    }

    public void save() {
        try {
            this.ensureDefaults();
            this.clamp();
            Files.createDirectories(this.path.getParent());
            try (Writer writer = Files.newBufferedWriter(this.path)) {
                GSON.toJson(this, writer);
            }
        } catch (final IOException exception) {
            FpvFreecam.LOGGER.error("Failed to save drone config", exception);
        }
    }

    public void clearControllerSelection() {
        this.controller.controllerGuid = "";
        this.controller.controllerName = "";
    }

    public void setController(final String guid, final String name) {
        this.controller.controllerGuid = guid == null ? "" : guid;
        this.controller.controllerName = name == null ? "" : name;
    }

    public DroneConfig copy() {
        final DroneConfig copy = new DroneConfig(this.path);
        copy.schemaVersion = this.schemaVersion;
        copy.simulationMode = this.simulationMode;
        copy.clientOnlyNotice = this.clientOnlyNotice;
        copy.controller = this.controller.copy();
        copy.rateProfile = this.rateProfile.copy();
        copy.throttleProfile = this.throttleProfile.copy();
        copy.craftProfile = this.craftProfile.copy();
        copy.realismProfile = this.realismProfile.copy();
        copy.crashSettings = this.crashSettings.copy();
        return copy;
    }

    public void copyFrom(final DroneConfig source) {
        this.schemaVersion = source.schemaVersion;
        this.simulationMode = source.simulationMode;
        this.clientOnlyNotice = source.clientOnlyNotice;
        this.controller = source.controller.copy();
        this.rateProfile = source.rateProfile.copy();
        this.throttleProfile = source.throttleProfile.copy();
        this.craftProfile = source.craftProfile.copy();
        this.realismProfile = source.realismProfile.copy();
        this.crashSettings = source.crashSettings.copy();
    }

    private void ensureDefaults() {
        this.schemaVersion = CURRENT_SCHEMA_VERSION;
        if (this.simulationMode == null) {
            this.simulationMode = SimulationMode.CLIENT_ONLY;
        }
        if (this.clientOnlyNotice == null || this.clientOnlyNotice.isBlank()) {
            this.clientOnlyNotice = "Client-side FPV freecam simulation only. No FPV packets are sent. Intended for servers that already allow freecam.";
        }
        if (this.controller == null) {
            this.controller = ControllerConfig.defaults();
        }
        if (this.rateProfile == null) {
            this.rateProfile = RateProfile.defaults();
        }
        if (this.throttleProfile == null) {
            this.throttleProfile = ThrottleProfile.defaults();
        }
        if (this.craftProfile == null) {
            this.craftProfile = CraftProfile.defaults();
        }
        if (this.realismProfile == null) {
            this.realismProfile = RealismProfile.defaults();
        }
        if (this.crashSettings == null) {
            this.crashSettings = CrashSettings.defaults();
        }
    }

    private void clamp() {
        this.schemaVersion = CURRENT_SCHEMA_VERSION;
        this.simulationMode = SimulationMode.CLIENT_ONLY;
        this.controller.clamp();
        this.rateProfile.clamp();
        this.throttleProfile.clamp();
        this.craftProfile.clamp();
        this.realismProfile.clamp();
        this.crashSettings.clamp();
    }

    private static DroneConfig defaults(final Path path) {
        return new DroneConfig(path);
    }

    private static DroneConfig rewriteDefaults(final Path path) {
        final DroneConfig config = defaults(path);
        config.save();
        return config;
    }

    private static boolean looksLegacyFlat(final JsonObject json) {
        return json.has("toggleButton")
                || json.has("controllerGuid")
                || json.has("axisThrottle")
                || json.has("cameraPitch")
                || json.has("deadzone");
    }

    private static DroneConfig parseGrouped(final Path path, final JsonObject json) {
        final DroneConfig parsed = GSON.fromJson(json, DroneConfig.class);
        if (parsed == null) {
            return null;
        }

        parsed.path = path;
        return parsed;
    }

    private static DroneConfig migrateLegacyFlat(final Path path, final JsonObject json) {
        final LegacyFlatConfig legacy = GSON.fromJson(json, LegacyFlatConfig.class);
        final DroneConfig config = defaults(path);
        if (legacy == null) {
            return config;
        }

        config.controller.controllerGuid = safe(legacy.controllerGuid);
        config.controller.controllerName = safe(legacy.controllerName);
        config.controller.armButton = legacy.toggleButton;
        config.controller.disarmButton = legacy.exitButton;
        config.controller.resetButton = -1;
        config.controller.axisThrottle = legacy.axisThrottle;
        config.controller.axisYaw = legacy.axisYaw;
        config.controller.axisPitch = legacy.axisPitch;
        config.controller.axisRoll = legacy.axisRoll;
        config.controller.axisThrottleMin = legacy.axisThrottleMin;
        config.controller.axisThrottleMax = legacy.axisThrottleMax;
        config.controller.axisYawMin = legacy.axisYawMin;
        config.controller.axisYawMax = legacy.axisYawMax;
        config.controller.axisPitchMin = legacy.axisPitchMin;
        config.controller.axisPitchMax = legacy.axisPitchMax;
        config.controller.axisRollMin = legacy.axisRollMin;
        config.controller.axisRollMax = legacy.axisRollMax;
        config.controller.invertThrottle = legacy.invertThrottle;
        config.controller.invertYaw = legacy.invertYaw;
        config.controller.invertPitch = legacy.invertPitch;
        config.controller.invertRoll = legacy.invertRoll;
        config.controller.deadzone = legacy.deadzone;
        config.craftProfile.cameraAngleDeg = legacy.cameraPitch;
        config.schemaVersion = LEGACY_SCHEMA_VERSION;

        return config;
    }

    private static JsonObject migrateToCurrentSchema(final JsonObject source) {
        JsonObject working = source.deepCopy();
        if (looksLegacyFlat(working)) {
            final DroneConfig grouped = migrateLegacyFlat(Path.of(FILE_NAME), working);
            working = GSON.toJsonTree(grouped).getAsJsonObject();
            working.addProperty("schemaVersion", LEGACY_SCHEMA_VERSION);
        }

        int schemaVersion = resolveSchemaVersion(working);
        while (schemaVersion < CURRENT_SCHEMA_VERSION) {
            switch (schemaVersion) {
                case 1 -> working = migrateV1ToV2(working);
                default -> {
                    schemaVersion = CURRENT_SCHEMA_VERSION;
                    continue;
                }
            }
            schemaVersion = resolveSchemaVersion(working);
        }

        working.addProperty("schemaVersion", CURRENT_SCHEMA_VERSION);
        return working;
    }

    private static int resolveSchemaVersion(final JsonObject json) {
        if (!json.has("schemaVersion") || !json.get("schemaVersion").isJsonPrimitive()) {
            return LEGACY_SCHEMA_VERSION;
        }

        try {
            return Math.max(LEGACY_SCHEMA_VERSION, json.get("schemaVersion").getAsInt());
        } catch (final RuntimeException ignored) {
            return LEGACY_SCHEMA_VERSION;
        }
    }

    private static JsonObject migrateV1ToV2(final JsonObject source) {
        final JsonObject migrated = source.deepCopy();
        final JsonObject controller = migrated.has("controller") && migrated.get("controller").isJsonObject()
                ? migrated.getAsJsonObject("controller")
                : new JsonObject();
        if (!migrated.has("controller") || !migrated.get("controller").isJsonObject()) {
            migrated.add("controller", controller);
        }

        final int armButton = readInt(controller, "armButton", readInt(controller, "toggleButton", GLFW.GLFW_GAMEPAD_BUTTON_START));
        final int disarmButton = readInt(controller, "disarmButton", readInt(controller, "exitButton", GLFW.GLFW_GAMEPAD_BUTTON_BACK));
        controller.remove("toggleButton");
        controller.remove("exitButton");
        controller.addProperty("armButton", armButton);
        controller.addProperty("disarmButton", disarmButton);
        if (!controller.has("resetButton")) {
            controller.addProperty("resetButton", -1);
        }

        final JsonObject crashSettings = migrated.has("crashSettings") && migrated.get("crashSettings").isJsonObject()
                ? migrated.getAsJsonObject("crashSettings")
                : new JsonObject();
        if (!migrated.has("crashSettings") || !migrated.get("crashSettings").isJsonObject()) {
            migrated.add("crashSettings", crashSettings);
        }
        if (!crashSettings.has("exitToPlayerOnDamage")) {
            crashSettings.addProperty("exitToPlayerOnDamage", true);
        }

        migrated.addProperty("schemaVersion", 2);
        return migrated;
    }

    private static int readInt(final JsonObject object, final String field, final int fallback) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(field).getAsInt();
        } catch (final RuntimeException ignored) {
            return fallback;
        }
    }

    private static String safe(final String value) {
        return value == null ? "" : value;
    }

    public enum SimulationMode {
        CLIENT_ONLY
    }

    public enum CrashResetMode {
        EXIT_TO_PLAYER,
        QUICK_REARM,
        CHECKPOINT_RESPAWN
    }

    public static final class ControllerConfig {
        public String controllerGuid = "";
        public String controllerName = "";
        public int armButton = GLFW.GLFW_GAMEPAD_BUTTON_START;
        public int disarmButton = GLFW.GLFW_GAMEPAD_BUTTON_BACK;
        public int resetButton = -1;
        public int axisThrottle = GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y;
        public int axisYaw = GLFW.GLFW_GAMEPAD_AXIS_LEFT_X;
        public int axisPitch = GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y;
        public int axisRoll = GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X;
        public float axisThrottleMin = -1.0F;
        public float axisThrottleMax = 1.0F;
        public float axisYawMin = -1.0F;
        public float axisYawMax = 1.0F;
        public float axisPitchMin = -1.0F;
        public float axisPitchMax = 1.0F;
        public float axisRollMin = -1.0F;
        public float axisRollMax = 1.0F;
        public boolean invertThrottle = true;
        public boolean invertYaw = false;
        public boolean invertPitch = true;
        public boolean invertRoll = false;
        public float deadzone = 0.08F;
        public boolean allowInFlightCameraAngleAdjust = true;

        public ControllerConfig() {
        }

        private static ControllerConfig defaults() {
            return new ControllerConfig();
        }

        private ControllerConfig copy() {
            final ControllerConfig copy = new ControllerConfig();
            copy.controllerGuid = this.controllerGuid;
            copy.controllerName = this.controllerName;
            copy.armButton = this.armButton;
            copy.disarmButton = this.disarmButton;
            copy.resetButton = this.resetButton;
            copy.axisThrottle = this.axisThrottle;
            copy.axisYaw = this.axisYaw;
            copy.axisPitch = this.axisPitch;
            copy.axisRoll = this.axisRoll;
            copy.axisThrottleMin = this.axisThrottleMin;
            copy.axisThrottleMax = this.axisThrottleMax;
            copy.axisYawMin = this.axisYawMin;
            copy.axisYawMax = this.axisYawMax;
            copy.axisPitchMin = this.axisPitchMin;
            copy.axisPitchMax = this.axisPitchMax;
            copy.axisRollMin = this.axisRollMin;
            copy.axisRollMax = this.axisRollMax;
            copy.invertThrottle = this.invertThrottle;
            copy.invertYaw = this.invertYaw;
            copy.invertPitch = this.invertPitch;
            copy.invertRoll = this.invertRoll;
            copy.deadzone = this.deadzone;
            copy.allowInFlightCameraAngleAdjust = this.allowInFlightCameraAngleAdjust;
            return copy;
        }

        private void clamp() {
            this.armButton = Math.max(-1, this.armButton);
            this.disarmButton = Math.max(-1, this.disarmButton);
            this.resetButton = Math.max(-1, this.resetButton);
            this.deadzone = Mth.clamp(this.deadzone, 0.0F, 0.95F);
            if (this.axisThrottleMax <= this.axisThrottleMin) {
                this.axisThrottleMin = -1.0F;
                this.axisThrottleMax = 1.0F;
            }
            if (this.axisYawMax <= this.axisYawMin) {
                this.axisYawMin = -1.0F;
                this.axisYawMax = 1.0F;
            }
            if (this.axisPitchMax <= this.axisPitchMin) {
                this.axisPitchMin = -1.0F;
                this.axisPitchMax = 1.0F;
            }
            if (this.axisRollMax <= this.axisRollMin) {
                this.axisRollMin = -1.0F;
                this.axisRollMax = 1.0F;
            }
        }
    }

    public static final class RateProfile {
        public float rollRcRate = DroneProfileDefaults.ROLL_RC_RATE;
        public float rollSuperRate = DroneProfileDefaults.ROLL_SUPER_RATE;
        public float rollExpo = DroneProfileDefaults.ROLL_EXPO;
        public float pitchRcRate = DroneProfileDefaults.PITCH_RC_RATE;
        public float pitchSuperRate = DroneProfileDefaults.PITCH_SUPER_RATE;
        public float pitchExpo = DroneProfileDefaults.PITCH_EXPO;
        public float yawRcRate = DroneProfileDefaults.YAW_RC_RATE;
        public float yawSuperRate = DroneProfileDefaults.YAW_SUPER_RATE;
        public float yawExpo = DroneProfileDefaults.YAW_EXPO;

        public RateProfile() {
        }

        private static RateProfile defaults() {
            return new RateProfile();
        }

        private RateProfile copy() {
            final RateProfile copy = new RateProfile();
            copy.rollRcRate = this.rollRcRate;
            copy.rollSuperRate = this.rollSuperRate;
            copy.rollExpo = this.rollExpo;
            copy.pitchRcRate = this.pitchRcRate;
            copy.pitchSuperRate = this.pitchSuperRate;
            copy.pitchExpo = this.pitchExpo;
            copy.yawRcRate = this.yawRcRate;
            copy.yawSuperRate = this.yawSuperRate;
            copy.yawExpo = this.yawExpo;
            return copy;
        }

        private void clamp() {
            this.rollRcRate = Mth.clamp(this.rollRcRate, 0.10F, 3.0F);
            this.pitchRcRate = Mth.clamp(this.pitchRcRate, 0.10F, 3.0F);
            this.yawRcRate = Mth.clamp(this.yawRcRate, 0.10F, 3.0F);
            this.rollSuperRate = Mth.clamp(this.rollSuperRate, 0.0F, 0.99F);
            this.pitchSuperRate = Mth.clamp(this.pitchSuperRate, 0.0F, 0.99F);
            this.yawSuperRate = Mth.clamp(this.yawSuperRate, 0.0F, 0.99F);
            this.rollExpo = Mth.clamp(this.rollExpo, 0.0F, 1.0F);
            this.pitchExpo = Mth.clamp(this.pitchExpo, 0.0F, 1.0F);
            this.yawExpo = Mth.clamp(this.yawExpo, 0.0F, 1.0F);
        }
    }

    public static final class ThrottleProfile {
        public float throttleMid = DroneProfileDefaults.THROTTLE_MID;
        public float throttleExpo = DroneProfileDefaults.THROTTLE_EXPO;

        public ThrottleProfile() {
        }

        private static ThrottleProfile defaults() {
            return new ThrottleProfile();
        }

        private ThrottleProfile copy() {
            final ThrottleProfile copy = new ThrottleProfile();
            copy.throttleMid = this.throttleMid;
            copy.throttleExpo = this.throttleExpo;
            return copy;
        }

        private void clamp() {
            this.throttleMid = Mth.clamp(this.throttleMid, 0.05F, 0.95F);
            this.throttleExpo = Mth.clamp(this.throttleExpo, 0.0F, 1.0F);
        }
    }

    public static final class CraftProfile {
        public float cameraAngleDeg = DroneProfileDefaults.CAMERA_ANGLE_DEG;
        public float thrustToWeight = DroneProfileDefaults.THRUST_TO_WEIGHT;
        public float massKg = DroneProfileDefaults.DRONE_MASS_KG;
        public float motorSpoolUpSeconds = DroneProfileDefaults.MOTOR_SPOOL_UP_SECONDS;
        public float motorSpoolDownSeconds = DroneProfileDefaults.MOTOR_SPOOL_DOWN_SECONDS;
        public float rollResponseSeconds = DroneProfileDefaults.ROLL_RESPONSE_SECONDS;
        public float pitchResponseSeconds = DroneProfileDefaults.PITCH_RESPONSE_SECONDS;
        public float yawResponseSeconds = DroneProfileDefaults.YAW_RESPONSE_SECONDS;
        public float forwardDrag = DroneProfileDefaults.FORWARD_DRAG;
        public float sideDrag = DroneProfileDefaults.SIDE_DRAG;
        public float verticalDrag = DroneProfileDefaults.VERTICAL_DRAG;

        public CraftProfile() {
        }

        private static CraftProfile defaults() {
            return new CraftProfile();
        }

        private CraftProfile copy() {
            final CraftProfile copy = new CraftProfile();
            copy.cameraAngleDeg = this.cameraAngleDeg;
            copy.thrustToWeight = this.thrustToWeight;
            copy.massKg = this.massKg;
            copy.motorSpoolUpSeconds = this.motorSpoolUpSeconds;
            copy.motorSpoolDownSeconds = this.motorSpoolDownSeconds;
            copy.rollResponseSeconds = this.rollResponseSeconds;
            copy.pitchResponseSeconds = this.pitchResponseSeconds;
            copy.yawResponseSeconds = this.yawResponseSeconds;
            copy.forwardDrag = this.forwardDrag;
            copy.sideDrag = this.sideDrag;
            copy.verticalDrag = this.verticalDrag;
            return copy;
        }

        private void clamp() {
            this.cameraAngleDeg = Mth.clamp(this.cameraAngleDeg, -90.0F, 90.0F);
            this.thrustToWeight = Mth.clamp(this.thrustToWeight, 1.2F, 12.0F);
            this.massKg = Mth.clamp(this.massKg, 0.10F, 3.0F);
            this.motorSpoolUpSeconds = Mth.clamp(this.motorSpoolUpSeconds, 0.005F, 0.6F);
            this.motorSpoolDownSeconds = Mth.clamp(this.motorSpoolDownSeconds, 0.005F, 0.8F);
            this.rollResponseSeconds = Mth.clamp(this.rollResponseSeconds, 0.010F, 0.250F);
            this.pitchResponseSeconds = Mth.clamp(this.pitchResponseSeconds, 0.010F, 0.250F);
            this.yawResponseSeconds = Mth.clamp(this.yawResponseSeconds, 0.010F, 0.350F);
            this.forwardDrag = Mth.clamp(this.forwardDrag, 0.005F, 0.35F);
            this.sideDrag = Mth.clamp(this.sideDrag, 0.020F, 0.50F);
            this.verticalDrag = Mth.clamp(this.verticalDrag, 0.010F, 0.45F);
        }
    }

    public static final class RealismProfile {
        public float batterySagStrength = DroneProfileDefaults.BATTERY_SAG_STRENGTH;
        public float batterySagMaxLoss = DroneProfileDefaults.BATTERY_SAG_MAX_LOSS;
        public float sagRecoverySeconds = DroneProfileDefaults.SAG_RECOVERY_SECONDS;
        public float descentWashStrength = DroneProfileDefaults.DESCENT_WASH_STRENGTH;
        public float loadImperfectionStrength = DroneProfileDefaults.LOAD_IMPERFECTION_STRENGTH;
        public boolean showNetworkSafetyDebugLine = false;

        public RealismProfile() {
        }

        private static RealismProfile defaults() {
            return new RealismProfile();
        }

        private RealismProfile copy() {
            final RealismProfile copy = new RealismProfile();
            copy.batterySagStrength = this.batterySagStrength;
            copy.batterySagMaxLoss = this.batterySagMaxLoss;
            copy.sagRecoverySeconds = this.sagRecoverySeconds;
            copy.descentWashStrength = this.descentWashStrength;
            copy.loadImperfectionStrength = this.loadImperfectionStrength;
            copy.showNetworkSafetyDebugLine = this.showNetworkSafetyDebugLine;
            return copy;
        }

        private void clamp() {
            this.batterySagStrength = Mth.clamp(this.batterySagStrength, 0.0F, 1.0F);
            this.batterySagMaxLoss = Mth.clamp(this.batterySagMaxLoss, 0.0F, 0.35F);
            this.sagRecoverySeconds = Mth.clamp(this.sagRecoverySeconds, 0.20F, 10.0F);
            this.descentWashStrength = Mth.clamp(this.descentWashStrength, 0.0F, 1.0F);
            this.loadImperfectionStrength = Mth.clamp(this.loadImperfectionStrength, 0.0F, 1.0F);
        }
    }

    public static final class CrashSettings {
        public float lowSpeedGlanceThreshold = DroneProfileDefaults.GLANCING_IMPACT_SPEED;
        public float hardImpactSpeedThreshold = DroneProfileDefaults.HARD_IMPACT_SPEED;
        public float hardImpactEnergyThreshold = DroneProfileDefaults.HARD_IMPACT_ENERGY;
        public CrashResetMode crashResetMode = CrashResetMode.EXIT_TO_PLAYER;
        public boolean exitToPlayerOnDamage = true;

        public CrashSettings() {
        }

        private static CrashSettings defaults() {
            return new CrashSettings();
        }

        private CrashSettings copy() {
            final CrashSettings copy = new CrashSettings();
            copy.lowSpeedGlanceThreshold = this.lowSpeedGlanceThreshold;
            copy.hardImpactSpeedThreshold = this.hardImpactSpeedThreshold;
            copy.hardImpactEnergyThreshold = this.hardImpactEnergyThreshold;
            copy.crashResetMode = this.crashResetMode;
            copy.exitToPlayerOnDamage = this.exitToPlayerOnDamage;
            return copy;
        }

        private void clamp() {
            this.lowSpeedGlanceThreshold = Mth.clamp(this.lowSpeedGlanceThreshold, 0.2F, 20.0F);
            this.hardImpactSpeedThreshold = Mth.clamp(this.hardImpactSpeedThreshold, 1.0F, 40.0F);
            this.hardImpactEnergyThreshold = Mth.clamp(this.hardImpactEnergyThreshold, 1.0F, 300.0F);
            if (this.crashResetMode == null) {
                this.crashResetMode = CrashResetMode.EXIT_TO_PLAYER;
            }
        }
    }

    private static final class LegacyFlatConfig {
        private String controllerGuid = "";
        private String controllerName = "";
        private int toggleButton = GLFW.GLFW_GAMEPAD_BUTTON_START;
        private int exitButton = GLFW.GLFW_GAMEPAD_BUTTON_BACK;
        private int axisThrottle = GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y;
        private int axisYaw = GLFW.GLFW_GAMEPAD_AXIS_LEFT_X;
        private int axisPitch = GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y;
        private int axisRoll = GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X;
        private float axisThrottleMin = -1.0F;
        private float axisThrottleMax = 1.0F;
        private float axisYawMin = -1.0F;
        private float axisYawMax = 1.0F;
        private float axisPitchMin = -1.0F;
        private float axisPitchMax = 1.0F;
        private float axisRollMin = -1.0F;
        private float axisRollMax = 1.0F;
        private float cameraPitch = 28.0F;
        private boolean invertThrottle = true;
        private boolean invertYaw = false;
        private boolean invertPitch = true;
        private boolean invertRoll = false;
        private float deadzone = 0.08F;
    }
}
