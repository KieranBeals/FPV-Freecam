package com.kieran.clientdronecam.neo;

import com.kieran.clientdronecam.ClientDroneCam;
import com.kieran.clientdronecam.flight.DroneCameraAngles;
import com.kieran.clientdronecam.ui.DroneSetupScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class ClientDroneCamClientEvents {
    private ClientDroneCamClientEvents() {
    }

    public static void onClientTickPost(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        ClientDroneCam.LIFECYCLE.onClientTick(minecraft);

        while (ClientDroneCamModEvents.OPEN_SETUP_KEY.consumeClick()) {
            minecraft.setScreen(new DroneSetupScreen(minecraft.screen));
        }
    }

    public static void onComputeCameraAngles(final ViewportEvent.ComputeCameraAngles event) {
        final Minecraft minecraft = Minecraft.getInstance();
        ClientDroneCam.LIFECYCLE.onFrameUpdate(minecraft);

        if (ClientDroneCam.FLIGHT_CONTROLLER.isActive()) {
            final DroneCameraAngles angles = ClientDroneCam.FLIGHT_CONTROLLER.getCameraAngles();
            event.setYaw(angles.yaw());
            event.setPitch(angles.pitch());
            event.setRoll(angles.roll());
        }
    }

    public static void onRenderGuiPost(final RenderGuiEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final String overlayText = ClientDroneCam.LIFECYCLE.getOverlayText();
        if (!overlayText.isEmpty()) {
            final int x = minecraft.getWindow().getGuiScaledWidth() - minecraft.font.width(overlayText) - 6;
            final int y = 6;
            event.getGuiGraphics().drawString(minecraft.font, Component.literal(overlayText), x, y, 0xFFFFFF, true);
        }
    }

    public static void onClientLogout(final ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDroneCam.LIFECYCLE.onLogout();
    }
}
