package com.kieran.clientdronecam.neo;

import com.kieran.clientdronecam.ClientDroneCam;
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
            event.setYaw(ClientDroneCam.FLIGHT_CONTROLLER.getCameraYaw());
            event.setPitch(ClientDroneCam.FLIGHT_CONTROLLER.getCameraPitch());
            event.setRoll(ClientDroneCam.FLIGHT_CONTROLLER.getCameraRoll());
        }
    }

    public static void onRenderGuiPost(final RenderGuiEvent.Post event) {
        final String overlayText = ClientDroneCam.LIFECYCLE.getOverlayText();
        if (!overlayText.isEmpty()) {
            event.getGuiGraphics().drawString(Minecraft.getInstance().font, Component.literal(overlayText), 6, 6, 0xFFFFFF, true);
        }
    }

    public static void onClientLogout(final ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDroneCam.LIFECYCLE.onLogout();
    }
}
