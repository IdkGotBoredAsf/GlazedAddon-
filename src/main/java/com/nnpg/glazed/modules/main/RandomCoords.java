package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;

import java.util.Random;

/**
 * RandomCoords - Hides your real coordinates client-side.
 * Continuously updates your coordinates to random values every tick.
 */
public class RandomCoords extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    private double fakeX;
    private double fakeY;
    private double fakeZ;

    public RandomCoords() {
        super(GlazedAddon.CATEGORY, "random-coords", "Hides your real coordinates client-side.");
    }

    @Override
    public void onActivate() {
        generateRandomCoords();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player != null) generateRandomCoords();
    }

    private void generateRandomCoords() {
        // Minecraft world limits
        int worldLimit = 30000000;
        int yLimit = 320;

        fakeX = random.nextDouble() * 2 * worldLimit - worldLimit;
        fakeY = random.nextDouble() * yLimit;
        fakeZ = random.nextDouble() * 2 * worldLimit - worldLimit;
    }

    public double getX() {
        return fakeX;
    }

    public double getY() {
        return fakeY;
    }

    public double getZ() {
        return fakeZ;
    }

    public String getFakeCoordinates() {
        return String.format("X: %.0f Y: %.0f Z: %.0f", fakeX, fakeY, fakeZ);
    }
}
