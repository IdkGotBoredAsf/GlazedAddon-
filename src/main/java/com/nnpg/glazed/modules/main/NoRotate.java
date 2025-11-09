package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.BlockPlaceEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DeepslateBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * NoRotate - Forces deepslate-type blocks to always face upright when placed.
 */
public class NoRotate extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public NoRotate() {
        super(GlazedAddon.CATEGORY, "NoRotate", "Forces deepslate blocks to always face upright when placed.");
    }

    @EventHandler
    private void onBlockPlace(BlockPlaceEvent event) {
        if (mc.world == null || mc.player == null) return;

        BlockPos pos = event.blockPos;
        BlockState placed = mc.world.getBlockState(pos);

        // Only affect deepslate and its common variants
        if (placed.getBlock() instanceof DeepslateBlock
            || placed.isOf(Blocks.POLISHED_DEEPSLATE)
            || placed.isOf(Blocks.COBBLED_DEEPSLATE)
            || placed.isOf(Blocks.DEEPSLATE_BRICKS)
            || placed.isOf(Blocks.DEEPSLATE_TILES)) {

            BlockState upright = placed.getBlock().getDefaultState();
            mc.world.setBlockState(pos, upright, 3);
        }
    }
}
