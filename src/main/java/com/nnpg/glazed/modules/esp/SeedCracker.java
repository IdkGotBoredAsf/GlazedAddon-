package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SeedCracker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Block>> blocksToFind = sgGeneral.add(new BlockListSetting.Builder()
            .name("blocks")
            .description("Blocks to locate using the seed.")
            .defaultValue(List.of(Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.DIAMOND_ORE))
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the highlight boxes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
            .name("highlight-color")
            .description("Color of the predicted blocks.")
            .defaultValue(new SettingColor(255, 0, 0, 150))
            .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
            .name("scan-range")
            .description("How far from the player to generate expected blocks.")
            .defaultValue(32)
            .min(8)
            .max(128)
            .build()
    );

    private final Map<BlockPos, Block> predictedBlocks = new ConcurrentHashMap<>();

    private static final long SEED = 6608149111735331168L;

    public SeedCracker() {
        super(GlazedAddon.esp, "seed-cracker", "Predicts blocks based on the world seed.");
    }

    @Override
    public void onActivate() {
        predictedBlocks.clear();
    }

    @Override
    public void onDeactivate() {
        predictedBlocks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        predictedBlocks.clear();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.get();

        List<Block> targets = blocksToFind.get();
        if (targets.isEmpty()) return;

        for (int x = playerPos.getX() - r; x <= playerPos.getX() + r; x++) {
            for (int y = 0; y <= 255; y++) {
                for (int z = playerPos.getZ() - r; z <= playerPos.getZ() + r; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block expected = getExpectedBlock(pos);
                    if (targets.contains(expected)) {
                        predictedBlocks.put(pos, expected);
                    }
                }
            }
        }
    }

    private Block getExpectedBlock(BlockPos pos) {
        // simple deterministic pseudo world-gen based on seed + coordinates
        Random r = new Random(SEED
                + (long) pos.getX() * 341873128712L
                + (long) pos.getY() * 132897987541L
                + (long) pos.getZ() * 42317861L);
        int val = r.nextInt(100);
        if (val < 5) return Blocks.DIAMOND_ORE;
        if (val < 15) return Blocks.IRON_ORE;
        if (val < 30) return Blocks.COAL_ORE;
        return Blocks.STONE;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d eye = mc.player.getCameraPosVec(event.tickDelta);
        Color c = color.get();

        for (BlockPos pos : predictedBlocks.keySet()) {
            Box box = new Box(pos);
            event.renderer.box(box, c, c, shapeMode.get(), 2);
            event.renderer.line(eye.x, eye.y, eye.z, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, c);
        }
    }
}
