package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

/**
 * NoDeepslateRotation - Prevents rotation of directional blocks (like deepslate, logs, pillars)
 * when placing them. Client-side only.
 */
public class NoDeepslateRotation extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public NoDeepslateRotation() {
        super(GlazedAddon.CATEGORY, "no-deepslate-rotation", "Prevents rotating deepslate and directional blocks when placing them.");
    }

    @Override
    public void onActivate() {
        info("NoDeepslateRotation active. Placed directional blocks will not rotate.");
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        ClientPlayerEntity player = mc.player;
        BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;

        if (hit != null && player.getMainHandStack() != null) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);

            // Only target directional blocks (example: logs, deepslate variants)
            if (player.getMainHandStack().getBlock() == Blocks.DEEPSLATE_BRICKS
                || player.getMainHandStack().getBlock() == Blocks.DEEPSLATE_TILE_WALL
                || player.getMainHandStack().getBlock() == Blocks.DEEPSLATE_BRICK_SLAB
                || player.getMainHandStack().getBlock() == Blocks.DEEPSLATE_BRICK_STAIRS
                || player.getMainHandStack().getBlock().getDefaultState().getMaterial().isSolid()) {
                // Lock yaw/pitch so block is placed with default orientation
                preventRotation(player);
            }
        }
    }

    private void preventRotation(ClientPlayerEntity player) {
        // Lock yaw to nearest cardinal direction
        float yaw = player.getYaw() % 360;
        if (yaw < 0) yaw += 360;

        if (yaw >= 45 && yaw < 135) player.setYaw(90);   // East
        else if (yaw >= 135 && yaw < 225) player.setYaw(180); // South
        else if (yaw >= 225 && yaw < 315) player.setYaw(-90); // West
        else player.setYaw(0); // North

        // Lock pitch to 0 to prevent vertical rotation
        player.setPitch(0);
    }
}
