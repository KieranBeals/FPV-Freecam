package com.kieran.clientdronecam.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kieran.clientdronecam.ClientDroneCam;
import net.neoforged.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DroneConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("client-drone-cam.json");

    public String controllerGuid = "";
    public String controllerName = "";
    public int toggleButton = GLFW.GLFW_GAMEPAD_BUTTON_START;
    public int exitButton = GLFW.GLFW_GAMEPAD_BUTTON_BACK;
    public int axisThrottle = GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y;
    public int axisYaw = GLFW.GLFW_GAMEPAD_AXIS_LEFT_X;
    public int axisPitch = GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y;
    public int axisRoll = GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X;
    public boolean invertThrottle = true;
    public boolean invertYaw = false;
    public boolean invertPitch = true;
    public boolean invertRoll = false;
    public float deadzone = 0.12F;

    public static DroneConfig load() {
        try {
            Files.createDirectories(PATH.getParent());
            if (Files.notExists(PATH)) {
                final DroneConfig config = defaults();
                config.save();
                return config;
            }

            try (Reader reader = Files.newBufferedReader(PATH)) {
                final DroneConfig config = GSON.fromJson(reader, DroneConfig.class);
                if (config == null) {
                    return defaults();
                }

                config.clamp();
                return config;
            }
        } catch (final IOException exception) {
            ClientDroneCam.LOGGER.error("Failed to load drone config", exception);
            return defaults();
        }
    }

    public void save() {
        try {
            this.clamp();
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
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
        final DroneConfig copy = new DroneConfig();
        copy.controllerGuid = this.controllerGuid;
        copy.controllerName = this.controllerName;
        copy.toggleButton = this.toggleButton;
        copy.exitButton = this.exitButton;
        copy.axisThrottle = this.axisThrottle;
        copy.axisYaw = this.axisYaw;
        copy.axisPitch = this.axisPitch;
        copy.axisRoll = this.axisRoll;
        copy.invertThrottle = this.invertThrottle;
        copy.invertYaw = this.invertYaw;
        copy.invertPitch = this.invertPitch;
        copy.invertRoll = this.invertRoll;
        copy.deadzone = this.deadzone;
        return copy;
    }

    private void clamp() {
        this.deadzone = Math.max(0.0F, Math.min(0.95F, this.deadzone));
    }

    private static DroneConfig defaults() {
        return new DroneConfig();
    }
}

