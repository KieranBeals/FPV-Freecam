package com.kieran.fpvfreecam.platform;

import com.kieran.fpvfreecam.config.DroneConfig;
import com.kieran.fpvfreecam.flight.DroneFlightController;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

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
        final DroneFlightController.HudSnapshot snapshot = this.getHudSnapshot();
        if (snapshot == null) {
            return "";
        }

        final String controllerName = snapshot.controllerName() == null || snapshot.controllerName().isBlank()
                ? "Controller: unknown"
                : "Controller: " + snapshot.controllerName();
        final String base = String.format(
                "%s | Cam %.0f deg | Crash %s | Speed %.1f m/s | Thr %.0f%% | Sag %.0f%% | %s | R %.0f P %.0f Y %.0f deg/s",
                controllerName,
                snapshot.cameraAngleDeg(),
                formatCrashResetMode(snapshot.crashResetMode()),
                snapshot.speedMps(),
                snapshot.throttlePercent(),
                snapshot.sagPercent(),
                snapshot.crashed() ? "Crashed" : (snapshot.armed() ? "Armed" : "Disarmed"),
                snapshot.rollRateDegPerSecond(),
                snapshot.pitchRateDegPerSecond(),
                snapshot.yawRateDegPerSecond()
        );

        return base;
    }

    public @Nullable DroneFlightController.HudSnapshot getHudSnapshot() {
        return this.flightController.getHudSnapshot();
    }

    private static String formatCrashResetMode(final DroneConfig.CrashResetMode mode) {
        final String value = (mode == null ? DroneConfig.CrashResetMode.EXIT_TO_PLAYER : mode).name();
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
}
