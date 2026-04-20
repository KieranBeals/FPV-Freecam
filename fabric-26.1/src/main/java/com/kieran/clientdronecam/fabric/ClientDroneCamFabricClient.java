package com.kieran.clientdronecam.fabric;

import com.kieran.clientdronecam.ClientDroneCam;
import com.kieran.clientdronecam.ui.DroneSetupScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ClientDroneCamFabricClient implements ClientModInitializer {
    private static final Category OPEN_SETUP_KEY_CATEGORY = Category.register(Identifier.fromNamespaceAndPath(ClientDroneCam.MOD_ID, "controls"));
    private static final Identifier OVERLAY_ID = Identifier.fromNamespaceAndPath(ClientDroneCam.MOD_ID, "drone_overlay");

    public static final KeyMapping OPEN_SETUP_KEY = new KeyMapping(
            ClientDroneCam.OPEN_SETUP_KEY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            OPEN_SETUP_KEY_CATEGORY
    );

    @Override
    public void onInitializeClient() {
        ClientDroneCam.bootstrap(() -> FabricLoader.getInstance().getConfigDir());

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            ClientDroneCam.LIFECYCLE.onClientTick(minecraft);

            while (OPEN_SETUP_KEY.consumeClick()) {
                minecraft.setScreen(new DroneSetupScreen(minecraft.screen));
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> ClientDroneCam.LIFECYCLE.onLogout());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientDroneCam.LIFECYCLE.onLogout());
        HudElementRegistry.addLast(OVERLAY_ID, ClientDroneCamFabricClient::renderHud);
    }

    private static void renderHud(final GuiGraphicsExtractor graphics, final net.minecraft.client.DeltaTracker deltaTracker) {
        final Minecraft minecraft = Minecraft.getInstance();
        final String overlayText = ClientDroneCam.LIFECYCLE.getOverlayText();
        if (!overlayText.isEmpty()) {
            graphics.text(minecraft.font, overlayText, 6, 6, 0xFFFFFF, true);
        }
    }
}
