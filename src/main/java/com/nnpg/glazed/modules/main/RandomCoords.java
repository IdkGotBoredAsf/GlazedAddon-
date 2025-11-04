package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.Random;

/**
 * RandomCoords - Hides your real coordinates client-side.
 * Always returns random coordinates for HUDs, F3 screen, or chat.
 */
public class RandomCoords extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    public RandomCoords() {
        super(GlazedAddon.CATEGORY, "random-coords", "Hides your real coordinates client-side.");
    }

    /** Returns fake X coordinate */
    public double getX() {
        return getRandomCoord();
    }

    /** Returns fake Y coordinate */
    public double getY() {
        return getRandomCoord();
    }

    /** Returns fake Z coordinate */
    public double getZ() {
        return getRandomCoord();
    }

    /** Returns a completely random coordinate */
    private double getRandomCoord() {
        // Generate a random coordinate within the normal Minecraft world limits
        // Minecraft world height: Y: 0-320, X/Z approx: -30 million to +30 million
        int dimensionLimit = 30000000;
        int yLimit = 320;
        double coord;
        int axis = random.nextInt(3);
        if (axis == 1) {
            coord = random.nextInt(yLimit + 1); // Y coordinate
        } else {
            coord = random.nextInt(dimensionLimit * 2) - dimensionLimit; // X or Z
        }
        return coord;
    }

    /** Returns fake coordinates as a formatted string */
    public String getFakeCoordinates() {
        return String.format("X: %.0f Y: %.0f Z: %.0f", getX(), getY(), getZ());
    }
}
