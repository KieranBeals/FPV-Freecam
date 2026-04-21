package com.kieran.fpvfreecam.platform;

import com.kieran.fpvfreecam.FpvFreecam;
import com.kieran.fpvfreecam.flight.DroneFlightController;
import com.kieran.fpvfreecam.flight.DroneNetworkSafetyGuard;
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
        final DroneFlightController.HudSnapshot snapshot = this.flightController.getHudSnapshot();
        if (snapshot == null) {
            return "";
        }

        final String controllerName = snapshot.controllerName() == null || snapshot.controllerName().isBlank()
                ? "Controller: unknown"
                : "Controller: " + snapshot.controllerName();
        final String base = String.format(
                "%s | Cam %.0f deg | Speed %.1f m/s | Thr %.0f%% | Sag %.0f%% | %s | R %.0f P %.0f Y %.0f deg/s",
                controllerName,
                snapshot.cameraAngleDeg(),
                snapshot.speedMps(),
                snapshot.throttlePercent(),
                snapshot.sagPercent(),
                snapshot.crashed() ? "Crashed" : (snapshot.armed() ? "Armed" : "Disarmed"),
                snapshot.rollRateDegPerSecond(),
                snapshot.pitchRateDegPerSecond(),
                snapshot.yawRateDegPerSecond()
        );

        if (FpvFreecam.CONFIG != null && FpvFreecam.CONFIG.realismProfile.showNetworkSafetyDebugLine) {
            return base + "\n" + DroneNetworkSafetyGuard.DEBUG_LINE_CLIENT_ONLY
                    + "\n" + DroneNetworkSafetyGuard.DEBUG_LINE_NO_PACKETS;
        }
        return base;
    }
}
