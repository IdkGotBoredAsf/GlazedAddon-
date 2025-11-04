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
 * Tries several approaches to activate crouch so it works across mappings/versions:
 *  - mc.player.setSneaking(...)
 *  - mc.options.sneakKey.setPressed(...)
 *  - reflection into mc.player.input.sneaking if present
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
    private boolean sneakingState = false; // current desired state for toggling

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public TwerkAura() {
        super(GlazedAddon.troll, "TwerkAura", "Makes your player sneak repeatedly near others.");
    }

    // Use onTick without @Override to avoid mapping mismatch problems.
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // increment ticks; only run detection every (sneakDelay) ticks
        tickCounter++;
        if (tickCounter < sneakDelay.get()) return;
        tickCounter = 0;

        // detect players within range
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
            // toggle sneak state
            sneakingState = !sneakingState;
            applySneak(sneakingState);
        } else {
            // ensure we are not sneaking when nobody's near
            sneakingState = false;
            applySneak(false);
        }
    }

    public void onDeactivate() {
        sneakingState = false;
        applySneak(false);
    }

    /**
     * Try multiple ways to apply sneak:
     * 1) mc.player.setSneaking(...) (safe at compile time)
     * 2) press/unpress mc.options.sneakKey (causes client to send input packets)
     * 3) reflection to set mc.player.input.sneaking boolean (if available)
     */
    private void applySneak(boolean sneak) {
        try {
            if (mc.player != null) {
                // 1) Set entity sneaking state
                try {
                    mc.player.setSneaking(sneak);
                } catch (Throwable ignored) {}

                // 2) Try to set the sneak key binding (many versions: mc.options.sneakKey)
                try {
                    // Some mappings expose mc.options and a KeyBinding field named sneakKey.
                    if (mc.options != null) {
                        KeyBinding kb = mc.options.sneakKey;
                        if (kb != null) {
                            // setPressed exists on many mappings; fall back silently otherwise
                            try {
                                kb.setPressed(sneak);
                            } catch (NoSuchMethodError | NoSuchMethodException | Throwable ex) {
                                // some versions use setDown(...) or different names - try setDown via reflection
                                try {
                                    Field down = kb.getClass().getDeclaredField("down");
                                    down.setAccessible(true);
                                    down.setBoolean(kb, sneak);
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                // 3) Reflection fallback to player.input.sneaking (no compile-time dependency)
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
                            // sometimes field name differs, try "sneak" as fallback
                            try {
                                Field f2 = inputObj.getClass().getDeclaredField("sneak");
                                f2.setAccessible(true);
                                f2.setBoolean(inputObj, sneak);
                            } catch (Throwable ignored) {}
                        }
                    }
                } catch (NoSuchFieldException nf) {
                    // input field doesn't exist; ignore
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            // never crash the client because of this
        }
    }
}
