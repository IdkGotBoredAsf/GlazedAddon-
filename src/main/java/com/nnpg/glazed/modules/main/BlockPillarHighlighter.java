package com.nnpg.glazed.modules.main;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class BlockPillarHighlighter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("How far to scan for pillars.")
        .defaultValue(20)
        .min(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<List<Block>> surroundBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("surround-blocks")
        .description("Blocks that should surround the pillar.")
        .defaultValue(Arrays.asList(Blocks.ANDESITE, Blocks.GRANITE, Blocks.DIORITE))
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Color of the box sides.")
        .defaultValue(new SettingColor(255, 255, 255, 40))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Color of the box outline.")
        .defaultValue(new SettingColor(255, 255, 255, 200))
        .build()
    );

    public BlockPillarHighlighter() {
        super(Categories.World, "block-pillar-highlighter", "Highlights stone pillars surrounded by selected blocks.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        Set<BlockPos> rendered = new HashSet<>();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.get();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    Block block = mc.world.getBlockState(pos).getBlock();

                    if (block == Blocks.STONE) {
                        boolean surrounded = true;

                        for (BlockPos offset : new BlockPos[]{
                            pos.north(), pos.south(), pos.east(), pos.west()
                        }) {
                            Block surround = mc.world.getBlockState(offset).getBlock();
                            if (!surroundBlocks.get().contains(surround)) {
                                surrounded = false;
                                break;
                            }
                        }

                        if (surrounded && !rendered.contains(pos)) {
                            Box box = new Box(pos);
                            RenderUtils.box(event, box, sideColor.get(), lineColor.get(), true);
                            rendered.add(pos);
                        }
                    }
                }
            }
        }
    }
}
