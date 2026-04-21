package com.kieran.fpvfreecam.fabric;

import com.kieran.fpvfreecam.FpvFreecam;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class FpvFreecamFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FpvFreecam.bootstrap(() -> FabricLoader.getInstance().getConfigDir());

        ClientTickEvents.END_CLIENT_TICK.register(FpvFreecam.LIFECYCLE::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> FpvFreecam.LIFECYCLE.onLogout());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FpvFreecam.LIFECYCLE.onLogout());
    }
}
