package com.kieran.fpvfreecam;

import com.kieran.fpvfreecam.config.DroneConfig;
import com.kieran.fpvfreecam.flight.DroneFlightController;
import com.kieran.fpvfreecam.input.DroneInputMapper;
import com.kieran.fpvfreecam.platform.ClientConfigPaths;
import com.kieran.fpvfreecam.platform.ClientLifecycleBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FpvFreecam {
    public static final String MOD_ID = "fpvfreecam";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String KEY_CATEGORY = "key.categories.fpvfreecam";
    public static final String OPEN_SETUP_KEY = "key.fpvfreecam.open_setup";

    public static DroneConfig CONFIG;
    public static DroneInputMapper INPUT_MAPPER;
    public static DroneFlightController FLIGHT_CONTROLLER;
    public static ClientLifecycleBridge LIFECYCLE;

    private FpvFreecam() {
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
