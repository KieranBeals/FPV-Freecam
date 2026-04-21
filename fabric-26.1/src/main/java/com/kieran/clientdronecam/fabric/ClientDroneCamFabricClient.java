package com.kieran.clientdronecam.fabric;

import com.kieran.clientdronecam.ClientDroneCam;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class ClientDroneCamFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientDroneCam.bootstrap(() -> FabricLoader.getInstance().getConfigDir());

        ClientTickEvents.END_CLIENT_TICK.register(ClientDroneCam.LIFECYCLE::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> ClientDroneCam.LIFECYCLE.onLogout());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientDroneCam.LIFECYCLE.onLogout());
    }
}
