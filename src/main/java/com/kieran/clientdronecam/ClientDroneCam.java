package com.kieran.clientdronecam;

import com.kieran.clientdronecam.config.DroneConfig;
import com.kieran.clientdronecam.flight.DroneFlightController;
import com.kieran.clientdronecam.input.DroneInputMapper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

public final class ClientDroneCam {
    public static final String MOD_ID = "clientdronecam";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String KEY_CATEGORY = "key.categories.clientdronecam";

    public static final KeyMapping OPEN_SETUP_KEY = new KeyMapping(
            "key.clientdronecam.open_setup",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KEY_CATEGORY
    );

    public static DroneConfig CONFIG;
    public static DroneInputMapper INPUT_MAPPER;
    public static DroneFlightController FLIGHT_CONTROLLER;

    private ClientDroneCam() {
    }

    public static void bootstrap() {
        if (CONFIG != null) {
            return;
        }

        CONFIG = DroneConfig.load();
        INPUT_MAPPER = new DroneInputMapper();
        FLIGHT_CONTROLLER = new DroneFlightController(CONFIG, INPUT_MAPPER);
    }
}

