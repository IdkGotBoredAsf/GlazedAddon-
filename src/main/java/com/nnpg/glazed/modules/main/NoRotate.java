package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * NoRotate - Forces deepslate-type blocks to always face upright like stone.
 */
public class NoRotate extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public NoRotate() {
        super(GlazedAddon.CATEGORY, "NoRotate", "Forces deepslate blocks to always face upright when placed.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        // Scan nearby blocks for rotated deepslate and correct them
        BlockPos playerPos = mc.player.getBlockPos();

        int radius = 4;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    Block block = state.getBlock();

                    if (isDeepslateVariant(block)) {
                        BlockState upright = block.getDefaultState();
                        if (state != upright) mc.world.setBlockState(pos, upright, 3);
                    }
                }
            }
        }
    }

    private boolean isDeepslateVariant(Block block) {
        return block == Blocks.DEEPSLATE
            || block == Blocks.COBBLED_DEEPSLATE
            || block == Blocks.POLISHED_DEEPSLATE
            || block == Blocks.DEEPSLATE_BRICKS
            || block == Blocks.DEEPSLATE_TILES
            || block == Blocks.INFESTED_DEEPSLATE;
    }
}
