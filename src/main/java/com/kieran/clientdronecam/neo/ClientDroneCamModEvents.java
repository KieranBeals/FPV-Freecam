package com.kieran.clientdronecam.neo;

import com.kieran.clientdronecam.ClientDroneCam;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public final class ClientDroneCamModEvents {
    private ClientDroneCamModEvents() {
    }

    public static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(ClientDroneCam.OPEN_SETUP_KEY);
    }
}

