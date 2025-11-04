package com.nnpg.glazed.modules.troll;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
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

    public TwerkAura() {
        super(GlazedAddon.troll, "TwerkAura", "Makes your player sneak repeatedly near others.");
    }

    // Removed @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        if (tickCounter < sneakDelay.get()) return;
        tickCounter = 0;

        Box detectionBox = mc.player.getBoundingBox().expand(range.get());
        for (PlayerEntity target : mc.world.getPlayers()) {
            if (target == mc.player) continue;
            if (target.getBoundingBox().intersects(detectionBox)) {
                mc.player.setSneaking(!mc.player.isSneaking());
                break;
            }
        }
    }

    // Removed @Override
    public void onDeactivate() {
        if (mc.player != null) mc.player.setSneaking(false);
    }
}
