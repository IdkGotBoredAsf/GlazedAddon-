package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.RenderEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.ShapeMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class BlockPillarHighlighter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Block>> targetBlocks = sgGeneral.add(new ListSetting.Builder<Block>()
        .name("pillar-blocks")
        .description("Blocks to detect as pillars.")
        .defaultValue(List.of(Blocks.STONE))
        .build()
    );

    private final Setting<List<Block>> surroundingBlocks = sgGeneral.add(new ListSetting.Builder<Block>()
        .name("surrounding-blocks")
        .description("Blocks that should surround the pillar core (e.g. andesite, granite, gravel).")
        .defaultValue(List.of(Blocks.ANDESITE, Blocks.GRANITE, Blocks.GRAVEL))
        .build()
    );

    private final Setting<Integer> pillarHeight = sgGeneral.add(new IntSetting.Builder()
        .name("pillar-height")
        .description("Minimum height of a pillar to highlight.")
        .defaultValue(3)
        .min(1)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("scan-range")
        .description("How far to scan around the player.")
        .defaultValue(30.0)
        .min(5.0)
        .max(100.0)
        .sliderMax(100.0)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("Color of the highlight boxes.")
        .defaultValue(new SettingColor(255, 100, 50, 150))
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public BlockPillarHighlighter() {
        super(GlazedAddon.CATEGORY, "block-pillar-highlighter", "Highlights vertical pillars made of chosen blocks surrounded by chosen materials.");
    }

    @EventHandler
    private void onRender(RenderEvent event) {
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
                        event.renderer.box(box, color.get(), color.get(), ShapeMode.Lines, 0);
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

        // Check surroundings of each pillar block
        for (int i = 0; i < height; i++) {
            BlockPos p = startPos.up(i);
            if (!isSurroundedByAllowed(world, p)) return false;
        }

        return true;
    }

    private boolean isSurroundedByAllowed(World world, BlockPos pos) {
        List<BlockPos> neighbors = List.of(
            pos.north(), pos.south(), pos.east(), pos.west()
        );

        for (BlockPos neighbor : neighbors) {
            Block block = world.getBlockState(neighbor).getBlock();
            if (!surroundingBlocks.get().contains(block)) return false;
        }

        return true;
    }
}
