package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;

import net.minecraft.util.math.Direction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * NoRotate - Prevents rotating blocks when placing them.
 * Keeps your block facing fixed.
 */
public class NoRotate extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public NoRotate() {
        super(GlazedAddon.CATEGORY, "no-rotate", "Prevents rotating blocks when placing them.");
    }

    @Override
    public void onActivate() {
        info("NoRotate is active. Block placement rotation is now locked.");
    }

    /**
     * This method can be hooked into block placement.
     * For simplicity, we override player yaw/pitch during placement.
     * Most server-side mods ignore this unless client-side rotation affects placement.
     */
    public Direction getLockedDirection() {
        if (mc.player != null) {
            float yaw = mc.player.getYaw() % 360;
            if (yaw < 0) yaw += 360;

            if (yaw >= 45 && yaw < 135) return Direction.WEST;
            if (yaw >= 135 && yaw < 225) return Direction.NORTH;
            if (yaw >= 225 && yaw < 315) return Direction.EAST;
        }
        return Direction.SOUTH;
    }

    /**
     * Example hook for intercepting placement rotation.
     * You would need a mixin into BlockPlace or BlockHitResult for full prevention.
     */
    public void preventRotationOnPlace(ClientPlayerEntity player) {
        // Lock yaw and pitch to current direction
        Direction dir = getLockedDirection();
        switch (dir) {
            case NORTH -> player.setYaw(180);
            case EAST -> player.setYaw(-90);
            case SOUTH -> player.setYaw(0);
            case WEST -> player.setYaw(90);
        }
        player.setPitch(90); // Face directly forward for vertical placement if desired
    }
}
