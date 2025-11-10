package com.nnpg.glazed.modules.main;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.utils.render.Renderer3D;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.color.ShapeMode;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

public class BlockPillarHighlighter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Scan range for pillars.")
        .defaultValue(20)
        .min(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Set<Block>> highlightBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("highlight-blocks")
        .description("Blocks to use as surrounding material.")
        .defaultValue(Set.of(Blocks.ANDESITE, Blocks.GRANITE, Blocks.DIORITE))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .description("How the block boxes are rendered.")
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(255, 255, 255, 40))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 255, 255, 200))
        .build()
    );

    public BlockPillarHighlighter() {
        super(Category.World, "block-pillar-highlighter", "Highlights stone pillars surrounded by chosen blocks.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;

        Set<BlockPos> rendered = new HashSet<>();
        BlockIterator.register(range.get(), range.get(), (blockPos, blockState) -> {
            if (blockState.getBlock() == Blocks.STONE) {
                boolean surrounded = true;
                for (BlockPos around : new BlockPos[]{
                    blockPos.north(), blockPos.south(), blockPos.east(), blockPos.west()
                }) {
                    if (!highlightBlocks.get().contains(mc.world.getBlockState(around).getBlock())) {
                        surrounded = false;
                        break;
                    }
                }

                if (surrounded && !rendered.contains(blockPos)) {
                    Renderer3D.renderBox(event, blockPos, sideColor.get(), lineColor.get(), shapeMode.get());
                    rendered.add(blockPos);
                }
            }
        });
    }
}
