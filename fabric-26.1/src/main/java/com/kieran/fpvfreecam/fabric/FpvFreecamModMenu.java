package com.kieran.fpvfreecam.fabric;

import com.kieran.fpvfreecam.ui.DroneSetupScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class FpvFreecamModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DroneSetupScreen::new;
    }
}
