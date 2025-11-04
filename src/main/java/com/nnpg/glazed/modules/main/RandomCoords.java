package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.MinecraftClient;

import java.util.Random;

public class RandomCoords extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    private double fakeX, fakeY, fakeZ;

    public RandomCoords() {
        super(GlazedAddon.CATEGORY, "random-coords", "Hides your real coordinates client-side.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player != null) generateRandomCoords();
    }

    private void generateRandomCoords() {
        int worldLimit = 30000000; // Minecraft world limits
        int yLimit = 320; // Max build height

        fakeX = random.nextDouble() * 2 * worldLimit - worldLimit;
        fakeY = random.nextDouble() * yLimit;
        fakeZ = random.nextDouble() * 2 * worldLimit - worldLimit;
    }

    public double getFakeX() { return fakeX; }
    public double getFakeY() { return fakeY; }
    public double getFakeZ() { return fakeZ; }
}
