package com.kieran.clientdronecam.neo;

import com.kieran.clientdronecam.ClientDroneCam;
import com.kieran.clientdronecam.ui.DroneSetupScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ClientDroneCam.MOD_ID, dist = Dist.CLIENT)
public final class ClientDroneCamClient {
    public ClientDroneCamClient(final IEventBus modBus, final ModContainer container) {
        ClientDroneCam.bootstrap();

        NeoForge.EVENT_BUS.addListener(ClientDroneCamClientEvents::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(ClientDroneCamClientEvents::onComputeCameraAngles);
        NeoForge.EVENT_BUS.addListener(ClientDroneCamClientEvents::onRenderGuiPost);
        NeoForge.EVENT_BUS.addListener(ClientDroneCamClientEvents::onClientLogout);

        modBus.addListener(ClientDroneCamModEvents::onRegisterKeyMappings);

        container.registerExtensionPoint(IConfigScreenFactory.class, (minecraft, parent) -> new DroneSetupScreen(parent));
    }
}

