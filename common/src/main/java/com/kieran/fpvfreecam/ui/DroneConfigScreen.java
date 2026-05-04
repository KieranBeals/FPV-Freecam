package com.kieran.fpvfreecam.ui;

import com.kieran.fpvfreecam.config.DroneConfig;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class DroneConfigScreen extends YACLScreen {
    private final Screen parent;
    private final DroneConfig workingConfig;
    private final ControllerBindingSession controllerSession;

    public DroneConfigScreen(
            final YetAnotherConfigLib config,
            final Screen parent,
            final DroneConfig workingConfig,
            final ControllerBindingSession controllerSession
    ) {
        super(config, parent);
        this.parent = parent;
        this.workingConfig = workingConfig;
        this.controllerSession = controllerSession;
        this.controllerSession.captureDisplayedAxisLabels();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.controllerSession.tick()) {
            this.rebuild();
            return;
        }

        this.applyPendingValues();
        if (this.controllerSession.shouldRefreshAxisLabels(System.currentTimeMillis())) {
            this.rebuild();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (this.controllerSession.isCaptureActive()) {
            this.controllerSession.cancelCapture();
            this.rebuild();
            return false;
        }
        return super.shouldCloseOnEsc();
    }

    public void rebuild() {
        this.applyPendingValues();
        Minecraft.getInstance().setScreen(DroneConfigScreens.create(this.parent, this.workingConfig, this.controllerSession));
    }

    private void applyPendingValues() {
        for (final ConfigCategory category : this.config.categories()) {
            for (final OptionGroup group : category.groups()) {
                for (final Option<?> option : group.options()) {
                    if (!(option instanceof ButtonOption)) {
                        option.applyValue();
                    }
                }
            }
        }
    }
}
