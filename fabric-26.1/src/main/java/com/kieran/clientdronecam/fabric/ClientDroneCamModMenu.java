package com.kieran.clientdronecam.fabric;

import com.kieran.clientdronecam.ui.DroneSetupScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class ClientDroneCamModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DroneSetupScreen::new;
    }
}
