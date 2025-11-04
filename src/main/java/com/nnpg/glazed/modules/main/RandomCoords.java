package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.MinecraftClient;

import java.util.Random;

/**
 * RandomCoords - Hides your real coordinates client-side.
 * Generates fully random coordinates every tick for display purposes.
 */
public class RandomCoords extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> updateInterval = sgGeneral.add(new DoubleSetting.Builder()
            .name("update-interval-ticks")
            .description("How often to generate new random coordinates.")
            .defaultValue(1)
            .min(1)
            .max(20)
            .sliderMin(1)
            .sliderMax(20)
            .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();
    private int tickCounter = 0;

    private double fakeX = 0;
    private double fakeY = 0;
    private double fakeZ = 0;

    private final int WORLD_LIMIT = 30000000;
    private final int Y_LIMIT = 320;

    public RandomCoords() {
        super(GlazedAddon.CATEGORY, "random-coords", "Hides your real coordinates client-side.");
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

    /**
     * Getters for fake coordinates.
     * Use these in HUDs, chat, or other modules for client-side coordinate hiding.
     */
    public double getFakeX() { return fakeX; }
    public double getFakeY() { return fakeY; }
    public double getFakeZ() { return fakeZ; }
}

