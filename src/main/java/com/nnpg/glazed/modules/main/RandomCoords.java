package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.Random;

/**
 * RandomCoords - Hides your real coordinates client-side.
 * Replaces them with fake/random coordinates for HUDs or debug screen.
 */
public class RandomCoords extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> enableRandom = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-random-coords")
        .description("Show random/fake coordinates instead of your real ones.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxOffset = sgGeneral.add(new IntSetting.Builder()
        .name("max-offset")
        .description("Maximum random offset from your real coordinates.")
        .defaultValue(500)
        .min(1)
        .max(10000)
        .sliderMax(1000)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    public RandomCoords() {
        super(GlazedAddon.CATEGORY, "random-coords", "Hides your real coordinates client-side.");
    }

    /** Returns fake X coordinate */
    public double getX() {
        if (!isActive() || !enableRandom.get() || mc.player == null) return mc.player.getX();
        return mc.player.getX() + randomOffset();
    }

    /** Returns fake Y coordinate */
    public double getY() {
        if (!isActive() || !enableRandom.get() || mc.player == null) return mc.player.getY();
        return mc.player.getY() + randomOffset();
    }

    /** Returns fake Z coordinate */
    public double getZ() {
        if (!isActive() || !enableRandom.get() || mc.player == null) return mc.player.getZ();
        return mc.player.getZ() + randomOffset();
    }

    /** Generates a random offset for coordinates */
    private double randomOffset() {
        return random.nextDouble() * maxOffset.get() * (random.nextBoolean() ? 1 : -1);
    }

    /** Returns fake coordinates as a formatted string */
    public String getFakeCoordinates() {
        return String.format("X: %.1f Y: %.1f Z: %.1f", getX(), getY(), getZ());
    }
}
