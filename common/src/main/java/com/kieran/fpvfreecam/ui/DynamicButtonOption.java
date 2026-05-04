package com.kieran.fpvfreecam.ui;

import com.google.common.collect.ImmutableSet;
import dev.isxander.yacl3.api.Binding;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionEventListener;
import dev.isxander.yacl3.api.OptionFlag;
import dev.isxander.yacl3.api.StateManager;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ActionController;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class DynamicButtonOption implements ButtonOption {
    private final Component name;
    private final OptionDescription description;
    private final StateManager<BiConsumer<YACLScreen, ButtonOption>> stateManager;
    private final Controller<BiConsumer<YACLScreen, ButtonOption>> controller;
    private boolean available = true;

    private DynamicButtonOption(
            final String name,
            final String description,
            final Supplier<String> text,
            final BiConsumer<YACLScreen, ButtonOption> action
    ) {
        this.name = Component.literal(name);
        this.description = OptionDescription.of(Component.literal(description));
        this.stateManager = StateManager.createImmutable(action);
        this.controller = new DynamicActionController(this, text);
    }

    static ButtonOption create(
            final String name,
            final String description,
            final Supplier<String> text,
            final BiConsumer<YACLScreen, ButtonOption> action
    ) {
        return new DynamicButtonOption(name, description, text, action);
    }

    @Override
    public BiConsumer<YACLScreen, ButtonOption> action() {
        return this.stateManager.get();
    }

    @Override
    public Component name() {
        return this.name;
    }

    @Override
    public OptionDescription description() {
        return this.description;
    }

    @Override
    public Component tooltip() {
        return this.description.text();
    }

    @Override
    public Controller<BiConsumer<YACLScreen, ButtonOption>> controller() {
        return this.controller;
    }

    @Override
    public StateManager<BiConsumer<YACLScreen, ButtonOption>> stateManager() {
        return this.stateManager;
    }

    @Override
    public Binding<BiConsumer<YACLScreen, ButtonOption>> binding() {
        return Binding.immutable(this.action());
    }

    @Override
    public boolean available() {
        return this.available;
    }

    @Override
    public void setAvailable(final boolean available) {
        this.available = available;
    }

    @Override
    public ImmutableSet<OptionFlag> flags() {
        return ImmutableSet.of();
    }

    @Override
    public boolean changed() {
        return false;
    }

    @Override
    public BiConsumer<YACLScreen, ButtonOption> pendingValue() {
        return this.action();
    }

    @Override
    public void requestSet(final BiConsumer<YACLScreen, ButtonOption> value) {
    }

    @Override
    public boolean applyValue() {
        return false;
    }

    @Override
    public void forgetPendingValue() {
    }

    @Override
    public void requestSetDefault() {
    }

    @Override
    public boolean isPendingValueDefault() {
        return true;
    }

    @Override
    public void addEventListener(final OptionEventListener<BiConsumer<YACLScreen, ButtonOption>> listener) {
    }

    @Override
    public void addListener(final BiConsumer<Option<BiConsumer<YACLScreen, ButtonOption>>, BiConsumer<YACLScreen, ButtonOption>> listener) {
    }

    private static final class DynamicActionController extends ActionController {
        private final Supplier<String> text;

        private DynamicActionController(final ButtonOption option, final Supplier<String> text) {
            super(option);
            this.text = text;
        }

        @Override
        public Component formatValue() {
            return Component.literal(this.text.get());
        }

        @Override
        public AbstractWidget provideWidget(final YACLScreen screen, final Dimension<Integer> dimension) {
            return super.provideWidget(screen, dimension);
        }
    }
}
