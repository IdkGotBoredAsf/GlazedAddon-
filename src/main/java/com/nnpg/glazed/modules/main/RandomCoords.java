package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public class BlockPillarHighlighter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Block> coreBlock = sgGeneral.add(new BlockSetting.Builder()
        .name("core-block")
        .description("The block to detect as the vertical pillar core.")
        .defaultValue(net.minecraft.block.Blocks.STONE)
        .build()
    );

    private final Setting<Set<Block>> surroundingBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("surrounding-blocks")
        .description("Blocks that should surround the pillar core.")
        .defaultValue(new HashSet<>())
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("How far to search for pillars around the player.")
        .defaultValue(20)
        .min(5)
        .max(100)
        .sliderMin(5)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> renderBox = sgGeneral.add(new BoolSetting.Builder()
        .name("render-box")
        .description("Renders a box around detected pillars.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("Color used to highlight pillars.")
        .defaultValue(new SettingColor(255, 100, 100, 120))
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public BlockPillarHighlighter() {
        super(GlazedAddon.CATEGORY, "block-pillar-highlighter", "Highlights pillars of one block surrounded by chosen blocks.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        World world = mc.world;
        BlockPos playerPos = mc.player.getBlockPos();
        int scan = radius.get();

        for (int x = -scan; x <= scan; x++) {
            for (int z = -scan; z <= scan; z++) {
                for (int y = -scan; y <= scan; y++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    Block block = world.getBlockState(pos).getBlock();

                    if (block == coreBlock.get()) {
                        // Check surrounding blocks (N, S, E, W)
                        if (isSurroundedByAllowed(world, pos)) {
                            Box box = new Box(pos);
                            if (renderBox.get()) {
                                RenderUtils.renderBox(event.matrices, box, color.get());
                            } else {
                                RenderUtils.renderOutline(event.matrices, box, color.get());
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isSurroundedByAllowed(World world, BlockPos pos) {
        for (BlockPos offset : new BlockPos[]{
            pos.north(), pos.south(), pos.east(), pos.west()
        }) {
            Block check = world.getBlockState(offset).getBlock();
            if (!surroundingBlocks.get().contains(check)) {
                return false;
            }
        }
        return true;
    }
}
