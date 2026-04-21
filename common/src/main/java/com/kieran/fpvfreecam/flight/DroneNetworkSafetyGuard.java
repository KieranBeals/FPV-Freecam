package com.kieran.fpvfreecam.flight;

import com.kieran.fpvfreecam.config.DroneConfig;

public final class DroneNetworkSafetyGuard {
    public static final String DEBUG_LINE_CLIENT_ONLY = "Client-only sim active";
    public static final String DEBUG_LINE_NO_PACKETS = "No FPV packets";

    private DroneNetworkSafetyGuard() {
    }

    public static void enforceClientOnlyMode(final DroneConfig config) {
        config.simulationMode = DroneConfig.SimulationMode.CLIENT_ONLY;
    }

    public static boolean shouldSuppressPlayerInput(final boolean droneActive) {
        // Input suppression preserves freecam-like packet behavior by blocking normal movement inputs.
        return droneActive;
    }
}
