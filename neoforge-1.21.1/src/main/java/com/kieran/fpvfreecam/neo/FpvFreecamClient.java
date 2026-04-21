package com.kieran.fpvfreecam.neo;

import com.kieran.fpvfreecam.FpvFreecam;
import com.kieran.fpvfreecam.ui.DroneSetupScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = FpvFreecam.MOD_ID, dist = Dist.CLIENT)
public final class FpvFreecamClient {
    public FpvFreecamClient(final IEventBus modBus, final ModContainer container) {
        FpvFreecam.bootstrap(() -> FMLPaths.CONFIGDIR.get());

        NeoForge.EVENT_BUS.addListener(FpvFreecamClientEvents::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(FpvFreecamClientEvents::onComputeCameraAngles);
        NeoForge.EVENT_BUS.addListener(FpvFreecamClientEvents::onRenderGuiPost);
        NeoForge.EVENT_BUS.addListener(FpvFreecamClientEvents::onClientLogout);
        NeoForge.EVENT_BUS.addListener(FpvFreecamClientEvents::onGameShuttingDown);

        container.registerExtensionPoint(IConfigScreenFactory.class, (minecraft, parent) -> new DroneSetupScreen(parent));
    }
}
