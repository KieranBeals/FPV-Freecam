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

        while (ClientDroneCam.OPEN_SETUP_KEY.consumeClick()) {
            minecraft.setScreen(new DroneSetupScreen(minecraft.screen));
        }

        ClientDroneCam.FLIGHT_CONTROLLER.tick(minecraft);
    }

    public static void onComputeCameraAngles(final ViewportEvent.ComputeCameraAngles event) {
        if (!ClientDroneCam.FLIGHT_CONTROLLER.isActive()) {
            return;
        }

        event.setYaw(ClientDroneCam.FLIGHT_CONTROLLER.getCameraYaw());
        event.setPitch(ClientDroneCam.FLIGHT_CONTROLLER.getCameraPitch());
        event.setRoll(ClientDroneCam.FLIGHT_CONTROLLER.getCameraRoll());
    }

    public static void onRenderGuiPost(final RenderGuiEvent.Post event) {
        if (!ClientDroneCam.FLIGHT_CONTROLLER.isActive()) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final String controllerName = ClientDroneCam.FLIGHT_CONTROLLER.getActiveControllerName();
        final String text = controllerName == null || controllerName.isBlank()
                ? "Drone Active"
                : "Drone Active - " + controllerName;
        event.getGuiGraphics().drawString(minecraft.font, Component.literal(text), 6, 6, 0xFFFFFF, true);
    }

    public static void onClientLogout(final ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDroneCam.FLIGHT_CONTROLLER.forceDeactivate("logout");
    }
}
