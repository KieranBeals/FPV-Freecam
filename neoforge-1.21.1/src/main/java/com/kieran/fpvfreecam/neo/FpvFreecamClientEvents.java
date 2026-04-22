package com.kieran.fpvfreecam.neo;

import com.kieran.fpvfreecam.FpvFreecam;
import com.kieran.fpvfreecam.flight.DroneCameraAngles;
import com.kieran.fpvfreecam.flight.DroneFlightController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

public final class FpvFreecamClientEvents {
    private static final int HUD_MARGIN_BOTTOM = 64;
    private static final int STICK_SIZE = 56;
    private static final int STICK_GAP = 8;
    private static final int STICK_BACKGROUND = 0x700A0A0A;
    private static final int STICK_BORDER = 0x80D0D0D0;
    private static final int STICK_CROSSHAIR = 0x70FFFFFF;
    private static final int STICK_DOT_LEFT = 0xE6F6F6F6;
    private static final int STICK_DOT_LEFT_CORE = 0xFFFFFFFF;
    private static final int STICK_DOT_RIGHT = 0xE060D9F4;
    private static final int STICK_DOT_RIGHT_CORE = 0xFFBDEFFF;

    private FpvFreecamClientEvents() {
    }

    public static void onClientTickPost(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (FpvFreecam.LIFECYCLE != null && FpvFreecam.FLIGHT_CONTROLLER != null) {
            FpvFreecam.LIFECYCLE.onClientTick(minecraft);
        }
    }

    public static void onComputeCameraAngles(final ViewportEvent.ComputeCameraAngles event) {
        if (FpvFreecam.LIFECYCLE == null || FpvFreecam.FLIGHT_CONTROLLER == null) {
            return;
        }

        if (FpvFreecam.FLIGHT_CONTROLLER.isActive()) {
            final DroneCameraAngles angles = FpvFreecam.FLIGHT_CONTROLLER.getCameraAngles();
            event.setYaw(angles.yaw());
            event.setPitch(angles.pitch());
            event.setRoll(angles.roll());
        }
    }

    public static void onRenderGuiPost(final RenderGuiEvent.Post event) {
        if (FpvFreecam.LIFECYCLE == null) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final GuiGraphics graphics = event.getGuiGraphics();
        final String overlayText = FpvFreecam.LIFECYCLE.getOverlayText();
        if (!overlayText.isEmpty()) {
            final String[] lines = overlayText.split("\\n");
            int y = 6;
            for (final String line : lines) {
                final int x = minecraft.getWindow().getGuiScaledWidth() - minecraft.font.width(line) - 6;
                graphics.drawString(minecraft.font, Component.literal(line), x, y, 0xFFFFFF, true);
                y += minecraft.font.lineHeight + 2;
            }
        }

        final DroneFlightController.HudSnapshot snapshot = FpvFreecam.LIFECYCLE.getHudSnapshot();
        if (snapshot != null) {
            renderStickHud(graphics, snapshot);
        }
    }

    public static void onClientLogout(final ClientPlayerNetworkEvent.LoggingOut event) {
        if (FpvFreecam.LIFECYCLE != null) {
            FpvFreecam.LIFECYCLE.onLogout();
        }
    }

    public static void onGameShuttingDown(final GameShuttingDownEvent event) {
        if (FpvFreecam.LIFECYCLE != null) {
            FpvFreecam.LIFECYCLE.onLogout();
        }
    }

    private static void renderStickHud(final GuiGraphics graphics, final DroneFlightController.HudSnapshot snapshot) {
        final int totalWidth = STICK_SIZE * 2 + STICK_GAP;
        final int leftStickX = (graphics.guiWidth() - totalWidth) / 2;
        final int rightStickX = leftStickX + STICK_SIZE + STICK_GAP;
        final int stickY = graphics.guiHeight() - STICK_SIZE - HUD_MARGIN_BOTTOM;
        drawStickBox(
                graphics,
                leftStickX,
                stickY,
                snapshot.yawInput(),
                snapshot.throttleInput(),
                STICK_DOT_LEFT,
                STICK_DOT_LEFT_CORE
        );
        drawStickBox(
                graphics,
                rightStickX,
                stickY,
                snapshot.rollInput(),
                snapshot.pitchInput(),
                STICK_DOT_RIGHT,
                STICK_DOT_RIGHT_CORE
        );
    }

    private static void drawStickBox(
            final GuiGraphics graphics,
            final int x,
            final int y,
            final float xInput,
            final float yInput,
            final int dotOuter,
            final int dotInner
    ) {
        final int right = x + STICK_SIZE;
        final int bottom = y + STICK_SIZE;
        final int centerX = x + STICK_SIZE / 2;
        final int centerY = y + STICK_SIZE / 2;

        graphics.fill(x, y, right, bottom, STICK_BACKGROUND);
        drawBorder(graphics, x, y, STICK_SIZE, STICK_SIZE, STICK_BORDER);

        graphics.fill(centerX, y + 2, centerX + 1, bottom - 2, STICK_CROSSHAIR);
        graphics.fill(x + 2, centerY, right - 2, centerY + 1, STICK_CROSSHAIR);

        final int range = STICK_SIZE / 2 - 4;
        final int dotX = centerX + Math.round(Mth.clamp(xInput, -1.0F, 1.0F) * range);
        final int dotY = centerY - Math.round(Mth.clamp(yInput, -1.0F, 1.0F) * range);

        drawDot(graphics, dotX, dotY, dotOuter, dotInner);
    }

    private static void drawDot(final GuiGraphics graphics, final int x, final int y, final int outerColor, final int innerColor) {
        graphics.fill(x - 2, y - 2, x + 3, y + 3, outerColor);
        graphics.fill(x - 1, y - 1, x + 2, y + 2, innerColor);
    }

    private static void drawBorder(final GuiGraphics graphics, final int x, final int y, final int width, final int height, final int color) {
        final int right = x + width;
        final int bottom = y + height;
        graphics.fill(x, y, right, y + 1, color);
        graphics.fill(x, bottom - 1, right, bottom, color);
        graphics.fill(x, y, x + 1, bottom, color);
        graphics.fill(right - 1, y, right, bottom, color);
    }
}
