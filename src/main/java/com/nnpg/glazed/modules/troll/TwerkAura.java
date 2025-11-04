package com.nnpg.glazed.modules.troll;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.lang.reflect.Field;

/**
 * TwerkAura - toggles sneak repeatedly while a player is nearby.
 */
public class TwerkAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Range to nearby players.")
        .defaultValue(5.0)
        .min(0.5)
        .max(50.0)
        .build()
    );

    private final Setting<Integer> sneakDelay = sgGeneral.add(new IntSetting.Builder()
        .name("sneak-delay")
        .description("Delay (in ticks) between sneak toggles.")
        .defaultValue(5)
        .min(1)
        .max(40)
        .build()
    );

    private int tickCounter = 0;
    private boolean sneakingState = false;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public TwerkAura() {
        super(GlazedAddon.troll, "TwerkAura", "Makes your player sneak repeatedly near others.");
    }

    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        if (tickCounter < sneakDelay.get()) return;
        tickCounter = 0;

        Box detectionBox = mc.player.getBoundingBox().expand(range.get(), range.get(), range.get());
        boolean near = false;

        for (PlayerEntity pe : mc.world.getPlayers()) {
            if (pe == mc.player) continue;
            if (pe.getBoundingBox().intersects(detectionBox)) {
                near = true;
                break;
            }
        }

        if (near) {
            sneakingState = !sneakingState;
            applySneak(sneakingState);
        } else {
            sneakingState = false;
            applySneak(false);
        }
    }

    public void onDeactivate() {
        sneakingState = false;
        applySneak(false);
    }

    private void applySneak(boolean sneak) {
        try {
            if (mc.player != null) {
                // 1) Base API
                try {
                    mc.player.setSneaking(sneak);
                } catch (Throwable ignored) {}

                // 2) KeyBinding press
                try {
                    if (mc.options != null) {
                        KeyBinding kb = mc.options.sneakKey;
                        if (kb != null) {
                            try {
                                kb.setPressed(sneak);
                            } catch (Throwable ex1) {
                                // maybe setDown(boolean)
                                try {
                                    Field down = kb.getClass().getDeclaredField("down");
                                    down.setAccessible(true);
                                    down.setBoolean(kb, sneak);
                                } catch (Throwable ignored2) {}
                            }
                        }
                    }
                } catch (Throwable ignored3) {}

                // 3) Reflection fallback into input.sneaking
                try {
                    Field inputField = mc.player.getClass().getDeclaredField("input");
                    inputField.setAccessible(true);
                    Object inputObj = inputField.get(mc.player);
                    if (inputObj != null) {
                        try {
                            Field sneakingField = inputObj.getClass().getDeclaredField("sneaking");
                            sneakingField.setAccessible(true);
                            sneakingField.setBoolean(inputObj, sneak);
                        } catch (NoSuchFieldException nsf) {
                            try {
                                Field f2 = inputObj.getClass().getDeclaredField("sneak");
                                f2.setAccessible(true);
                                f2.setBoolean(inputObj, sneak);
                            } catch (Throwable ignored4) {}
                        }
                    }
                } catch (Throwable ignored5) {}
            }
        } catch (Throwable ignoredFinal) {}
    }
}
