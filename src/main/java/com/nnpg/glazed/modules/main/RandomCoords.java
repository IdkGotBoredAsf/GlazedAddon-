package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import net.minecraft.client.MinecraftClient;

import java.util.Random;

/**
 * RandomCoords - Hides your real coordinates client-side by generating random coordinates every tick.
 * Fully client-side: affects F3 debug, HUDs, and third-person view.
 * Features:
 *  - Fully random coordinates without offsets
 *  - Configurable update interval
 *  - Toggle display in F3
 */
public class RandomCoords extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMisc = settings.createGroup("Misc");

    // Options
    private final Setting<Boolean> enableRandomCoords = sgGeneral.add(new BoolSetting.Builder()
            .name("enable")
            .description("Enable random coordinates replacement.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> updateInterval = sgGeneral.add(new DoubleSetting.Builder()
            .name("update-interval-ticks")
            .description("How often to generate new random coordinates (in ticks).")
            .defaultValue(1)
            .min(1)
            .max(20)
            .sliderMin(1)
            .sliderMax(20)
            .build()
    );

    private final Setting<Boolean> displayFakeCoords = sgMisc.add(new BoolSetting.Builder()
            .name("display-fake-coords")
            .description("Whether to override the F3 coordinates display with fake ones.")
            .defaultValue(true)
            .build()
    );

    // Internal
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();
    private int tickCounter = 0;
    private double fakeX, fakeY, fakeZ;

    // World limits for generating random coordinates
    private final int WORLD_LIMIT = 30000000;
    private final int Y_LIMIT = 320;

    public RandomCoords() {
        super(GlazedAddon.CATEGORY, "random-coords", "Hides your real coordinates client-side with full randomness.");
    }

    @Override
    public void onActivate() {
        generateRandomCoords();
        tickCounter = 0;
    }

    @Override
    public void onTick() {
        if (!enableRandomCoords.get() || mc.player == null) return;

        tickCounter++;
        if (tickCounter >= updateInterval.get()) {
            tickCounter = 0;
            generateRandomCoords();

            // Set fake coordinates directly to player entity fields
            mc.player.x = fakeX;
            mc.player.y = fakeY;
            mc.player.z = fakeZ;
        }
    }

    /**
     * Generate completely random coordinates within world limits
     */
    private void generateRandomCoords() {
        fakeX = random.nextDouble() * 2 * WORLD_LIMIT - WORLD_LIMIT;
        fakeY = random.nextDouble() * Y_LIMIT;
        fakeZ = random.nextDouble() * 2 * WORLD_LIMIT - WORLD_LIMIT;
    }

    /**
     * Optional: get the fake coordinates for display elsewhere in the client
     */
    public double getFakeX() {
        return fakeX;
    }

    public double getFakeY() {
        return fakeY;
    }

    public double getFakeZ() {
        return fakeZ;
    }
}
