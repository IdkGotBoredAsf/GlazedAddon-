package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.Random;

public class RandomCoords extends Module {
    private final Random random = new Random();

    private double fakeX, fakeY, fakeZ;

    public RandomCoords() {
        super(GlazedAddon.CATEGORY, "random-coords", "Hides your real coordinates client-side.");
    }

    public void generateRandomCoords() {
        int worldLimit = 30000000;
        int yLimit = 320;

        fakeX = random.nextDouble() * 2 * worldLimit - worldLimit;
        fakeY = random.nextDouble() * yLimit;
        fakeZ = random.nextDouble() * 2 * worldLimit - worldLimit;
    }

    public double getFakeX() { return fakeX; }
    public double getFakeY() { return fakeY; }
    public double getFakeZ() { return fakeZ; }
}
