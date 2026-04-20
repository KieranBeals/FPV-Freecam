package com.kieran.clientdronecam;

import com.kieran.clientdronecam.config.DroneConfig;
import com.kieran.clientdronecam.flight.DroneFlightController;
import com.kieran.clientdronecam.input.DroneInputMapper;
import com.kieran.clientdronecam.platform.ClientConfigPaths;
import com.kieran.clientdronecam.platform.ClientLifecycleBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientDroneCam {
    public static final String MOD_ID = "clientdronecam";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String KEY_CATEGORY = "key.categories.clientdronecam";
    public static final String OPEN_SETUP_KEY = "key.clientdronecam.open_setup";

    public static DroneConfig CONFIG;
    public static DroneInputMapper INPUT_MAPPER;
    public static DroneFlightController FLIGHT_CONTROLLER;
    public static ClientLifecycleBridge LIFECYCLE;

    private ClientDroneCam() {
    }

    public static void bootstrap(final ClientConfigPaths configPaths) {
        if (CONFIG != null) {
            return;
        }

        CONFIG = DroneConfig.load(configPaths);
        INPUT_MAPPER = new DroneInputMapper();
        FLIGHT_CONTROLLER = new DroneFlightController(CONFIG, INPUT_MAPPER);
        LIFECYCLE = new ClientLifecycleBridge(FLIGHT_CONTROLLER);
    }
}
