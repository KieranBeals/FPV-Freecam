package com.kieran.clientdronecam.neo;

import com.kieran.clientdronecam.ClientDroneCam;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientDroneCamModEvents {
    static final KeyMapping OPEN_SETUP_KEY = new KeyMapping(
            ClientDroneCam.OPEN_SETUP_KEY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            ClientDroneCam.KEY_CATEGORY
    );

    private ClientDroneCamModEvents() {
    }

    public static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETUP_KEY);
    }
}
