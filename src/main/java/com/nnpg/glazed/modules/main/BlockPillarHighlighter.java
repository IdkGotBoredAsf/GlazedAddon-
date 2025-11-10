package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.shape.ShapeMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

public class BlockPillarHighlighter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Block>> targetBlocks = sgGeneral.add(new ListSetting.Builder<Block>()
        .name("pillar-blocks")
        .description("Blocks to detect as vertical pillars.")
        .defaultValue(List.of(Blocks.STONE))
        .build()
    );

    private final Setting<List<Block>> surroundingBlocks = sgGeneral.add(new ListSetting.Builder<Block>()
        .name("surrounding-blocks")
        .description("Blocks that should surround each pillar block.")
        .defaultValue(List.of(Blocks.ANDESITE, Blocks.GRANITE, Blocks.GRAVEL))
        .build()
    );

    private final Setting<Integer> pillarHeight = sgGeneral.add(new IntSetting.Builder()
        .name("pillar-height")
        .description("Minimum pillar height to detect.")
        .defaultValue(3)
        .min(1)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("scan-range")
        .description("How far around the player to scan.")
        .defaultValue(30.0)
        .min(5.0)
        .max(100.0)
        .sliderMax(100.0)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("Color used for rendering pillar boxes.")
        .defaultValue(new SettingColor(255, 100, 50, 150))
        .build()
    );

    private final Setting<ShapeMode> shape = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Whether to show outlines or full boxes.")
        .defaultValue(ShapeMode.Lines)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public BlockPillarHighlighter() {
        super(GlazedAddon.CATEGORY, "block-pillar-highlighter",
            "Highlights vertical pillars made of selected blocks surrounded by specific materials.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        World world = mc.world;
        BlockPos playerPos = mc.player.getBlockPos();
        int r = (int) Math.ceil(range.get());

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -r; y <= r; y++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (isPillar(world, pos)) {
                        Box box = new Box(pos);
                        RenderUtils.renderBox(event, box, color.get(), shape.get(), 0);
                    }
                }
            }
        }
    }

    private boolean isPillar(World world, BlockPos startPos) {
        Block baseBlock = world.getBlockState(startPos).getBlock();
        if (!targetBlocks.get().contains(baseBlock)) return false;

        int height = 1;
        BlockPos checkPos = startPos.up();
        while (height < pillarHeight.get() && targetBlocks.get().contains(world.getBlockState(checkPos).getBlock())) {
            height++;
            checkPos = checkPos.up();
        }

        if (height < pillarHeight.get()) return false;

        for (int i = 0; i < height; i++) {
            BlockPos p = startPos.up(i);
            if (!isSurrounded(world, p)) return false;
        }

        return true;
    }

    private boolean isSurrounded(World world, BlockPos pos) {
        BlockPos[] neighbors = new BlockPos[]{
            pos.north(), pos.south(), pos.east(), pos.west()
        };

        for (BlockPos n : neighbors) {
            Block b = world.getBlockState(n).getBlock();
            if (!surroundingBlocks.get().contains(b)) return false;
        }

        return true;
    }
}
