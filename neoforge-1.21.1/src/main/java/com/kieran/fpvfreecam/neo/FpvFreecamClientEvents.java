package com.kieran.fpvfreecam.neo;

import com.kieran.fpvfreecam.FpvFreecam;
import com.kieran.fpvfreecam.flight.DroneCameraAngles;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class FpvFreecamClientEvents {
    private FpvFreecamClientEvents() {
    }

    public static void onClientTickPost(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (FpvFreecam.LIFECYCLE != null && FpvFreecam.FLIGHT_CONTROLLER != null) {
            FpvFreecam.LIFECYCLE.onClientTick(minecraft);
        }
    }

    public static void onComputeCameraAngles(final ViewportEvent.ComputeCameraAngles event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (FpvFreecam.LIFECYCLE == null || FpvFreecam.FLIGHT_CONTROLLER == null) {
            return;
        }

        FpvFreecam.LIFECYCLE.onFrameUpdate(minecraft);

        if (FpvFreecam.FLIGHT_CONTROLLER.isActive()) {
            final DroneCameraAngles angles = FpvFreecam.FLIGHT_CONTROLLER.getCameraAngles();
            event.setYaw(angles.yaw());
            event.setPitch(angles.pitch());
            event.setRoll(angles.roll());
        }
    }

    public static void onRenderGuiPost(final RenderGuiEvent.Post event) {
        if (FpvFreecam.LIFECYCLE == null) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final String overlayText = FpvFreecam.LIFECYCLE.getOverlayText();
        if (!overlayText.isEmpty()) {
            final int x = minecraft.getWindow().getGuiScaledWidth() - minecraft.font.width(overlayText) - 6;
            final int y = 6;
            event.getGuiGraphics().drawString(minecraft.font, Component.literal(overlayText), x, y, 0xFFFFFF, true);
        }
    }

    public static void onClientLogout(final ClientPlayerNetworkEvent.LoggingOut event) {
        if (FpvFreecam.LIFECYCLE != null) {
            FpvFreecam.LIFECYCLE.onLogout();
        }
    }
}
