package com.kieran.clientdronecam.platform;

import net.minecraft.client.gui.screens.Screen;

public interface ClientPlatformHooks {
    void registerOpenSetupKey();

    void openSetupScreen(Screen parent);

    boolean isConfigScreenIntegrationAvailable();
}
