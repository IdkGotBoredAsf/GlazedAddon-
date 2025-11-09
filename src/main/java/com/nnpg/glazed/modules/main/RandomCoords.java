package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;

import java.util.Random;

/**
 * RandomCoords - Displays random fake coordinates in the HUD.
 * Useful for streaming or hiding your real position.
 */
public class RandomCoords extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> updateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("update-interval-ticks")
        .description("How often to generate new random coordinates.")
        .defaultValue(20)
        .min(1)
        .max(200)
        .sliderMin(1)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> showOnHud = sgGeneral.add(new BoolSetting.Builder()
        .name("show-on-hud")
        .description("Displays the fake coordinates on screen.")
        .defaultValue(true)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();
    private int tickCounter = 0;

    private double fakeX, fakeY, fakeZ;
    private static final int WORLD_LIMIT = 30000000;
    private static final int Y_LIMIT = 320;

    public RandomCoords() {
        super(GlazedAddon.MAIN, "random-coords", "Displays random fake coordinates instead of real ones.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        tickCounter++;
        if (tickCounter >= updateInterval.get()) {
            tickCounter = 0;
            generateRandomCoords();
        }
    }

    private void generateRandomCoords() {
        fakeX = random.nextDouble() * 2 * WORLD_LIMIT - WORLD_LIMIT;
        fakeY = random.nextDouble() * Y_LIMIT;
        fakeZ = random.nextDouble() * 2 * WORLD_LIMIT - WORLD_LIMIT;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!showOnHud.get() || mc.player == null) return;

        String coords = String.format("Fake Coords: X: %.0f Y: %.0f Z: %.0f", fakeX, fakeY, fakeZ);
        event.getRenderer().text(coords, 5, 5, 0xFF55FFFF, true);
    }

    public String getFakeCoordsString() {
        return String.format("X: %.0f Y: %.0f Z: %.0f", fakeX, fakeY, fakeZ);
    }
}
