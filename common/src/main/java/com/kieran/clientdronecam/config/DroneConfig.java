package com.kieran.clientdronecam.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kieran.clientdronecam.ClientDroneCam;
import com.kieran.clientdronecam.platform.ClientConfigPaths;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class DroneConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "client-drone-cam.json";

    private transient final Path path;

    public String controllerGuid = "";
    public String controllerName = "";
    public int toggleButton = GLFW.GLFW_GAMEPAD_BUTTON_START;
    public int exitButton = GLFW.GLFW_GAMEPAD_BUTTON_BACK;
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
    public float deadzone = 0.12F;

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
                final DroneConfig config = GSON.fromJson(reader, DroneConfig.class);
                if (config == null) {
                    return rewriteDefaults(path);
                }

                config.clamp();
                return config.withPath(path);
            }
        } catch (final Exception exception) {
            ClientDroneCam.LOGGER.error("Failed to load drone config", exception);
            return rewriteDefaults(path);
        }
    }

    public void save() {
        try {
            this.clamp();
            Files.createDirectories(this.path.getParent());
            try (Writer writer = Files.newBufferedWriter(this.path)) {
                GSON.toJson(this, writer);
            }
        } catch (final IOException exception) {
            ClientDroneCam.LOGGER.error("Failed to save drone config", exception);
        }
    }

    public void clearControllerSelection() {
        this.controllerGuid = "";
        this.controllerName = "";
    }

    public void setController(final String guid, final String name) {
        this.controllerGuid = guid == null ? "" : guid;
        this.controllerName = name == null ? "" : name;
    }

    public DroneConfig copy() {
        final DroneConfig copy = new DroneConfig(this.path);
        copy.controllerGuid = this.controllerGuid;
        copy.controllerName = this.controllerName;
        copy.toggleButton = this.toggleButton;
        copy.exitButton = this.exitButton;
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
        return copy;
    }

    private DroneConfig withPath(final Path path) {
        final DroneConfig copy = this.copy();
        return Objects.equals(copy.path, path) ? copy : copy.rebind(path);
    }

    private DroneConfig rebind(final Path path) {
        final DroneConfig rebound = new DroneConfig(path);
        rebound.controllerGuid = this.controllerGuid;
        rebound.controllerName = this.controllerName;
        rebound.toggleButton = this.toggleButton;
        rebound.exitButton = this.exitButton;
        rebound.axisThrottle = this.axisThrottle;
        rebound.axisYaw = this.axisYaw;
        rebound.axisPitch = this.axisPitch;
        rebound.axisRoll = this.axisRoll;
        rebound.axisThrottleMin = this.axisThrottleMin;
        rebound.axisThrottleMax = this.axisThrottleMax;
        rebound.axisYawMin = this.axisYawMin;
        rebound.axisYawMax = this.axisYawMax;
        rebound.axisPitchMin = this.axisPitchMin;
        rebound.axisPitchMax = this.axisPitchMax;
        rebound.axisRollMin = this.axisRollMin;
        rebound.axisRollMax = this.axisRollMax;
        rebound.invertThrottle = this.invertThrottle;
        rebound.invertYaw = this.invertYaw;
        rebound.invertPitch = this.invertPitch;
        rebound.invertRoll = this.invertRoll;
        rebound.deadzone = this.deadzone;
        return rebound;
    }

    private void clamp() {
        this.deadzone = Math.max(0.0F, Math.min(0.95F, this.deadzone));
        clampAxisRange();
    }

    private void clampAxisRange() {
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

    private static DroneConfig defaults(final Path path) {
        return new DroneConfig(path);
    }

    private static DroneConfig rewriteDefaults(final Path path) {
        final DroneConfig config = defaults(path);
        config.save();
        return config;
    }
}
