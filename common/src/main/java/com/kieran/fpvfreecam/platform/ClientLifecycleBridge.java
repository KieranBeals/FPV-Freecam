package com.kieran.fpvfreecam.platform;

import com.kieran.fpvfreecam.flight.DroneFlightController;
import net.minecraft.client.Minecraft;

public final class ClientLifecycleBridge {
    private final DroneFlightController flightController;

    public ClientLifecycleBridge(final DroneFlightController flightController) {
        this.flightController = flightController;
    }

    public void onClientTick(final Minecraft minecraft) {
        this.flightController.tick(minecraft);
    }

    public void onFrameUpdate(final Minecraft minecraft) {
        this.flightController.updateFrame(minecraft);
    }

    public void onLogout() {
        this.flightController.forceDeactivate("logout");
    }

    public String getOverlayText() {
        if (!this.flightController.isActive()) {
            return "";
        }

        final String controllerName = this.flightController.getActiveControllerName();
        final int roundedTilt = Math.round(this.flightController.getCameraPitch());
        final String tiltText = "Tilt: " + roundedTilt + " deg";
        return controllerName == null || controllerName.isBlank()
                ? "Drone Active | " + tiltText
                : "Drone Active - " + controllerName + " | " + tiltText;
    }
}
