package com.nnpg.glazed.modules.troll;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

public class TwerkAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Range to nearby players.")
        .defaultValue(5.0)
        .min(1.0)
        .max(20.0)
        .build()
    );

    private final Setting<Integer> sneakDelay = sgGeneral.add(new IntSetting.Builder()
        .name("sneak-delay")
        .description("Delay between sneak toggles in ticks.")
        .defaultValue(5)
        .min(1)
        .max(20)
        .build()
    );

    private int tickCounter = 0;
    private boolean sneaking = false;

    public TwerkAura() {
        super(GlazedAddon.troll, "TwerkAura", "Makes your player sneak repeatedly near others.");
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        if (tickCounter < sneakDelay.get()) return;
        tickCounter = 0;

        // Detect if any player is nearby
        Box detectionBox = mc.player.getBoundingBox().expand(range.get());
        boolean nearPlayer = false;
        for (PlayerEntity target : mc.world.getPlayers()) {
            if (target == mc.player) continue;
            if (target.getBoundingBox().intersects(detectionBox)) {
                nearPlayer = true;
                break;
            }
        }

        // Toggle sneaking only if a player is nearby
        if (nearPlayer) {
            sneaking = !sneaking;
            ((ClientPlayerEntity) mc.player).input.sneaking = sneaking;
        } else {
            // Stop sneaking if no player nearby
            sneaking = false;
            ((ClientPlayerEntity) mc.player).input.sneaking = false;
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) ((ClientPlayerEntity) mc.player).input.sneaking = false;
        sneaking = false;
    }
}
