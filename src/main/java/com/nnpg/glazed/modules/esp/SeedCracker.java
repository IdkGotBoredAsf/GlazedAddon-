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

public class SeedCracker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Block>> blocksToFind = sgGeneral.add(new BlockListSetting.Builder()
            .name("blocks")
            .description("Blocks to predict from the seed.")
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
            .description("Color of predicted blocks.")
            .defaultValue(new SettingColor(255, 0, 0, 150))
            .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
            .name("scan-range")
            .description("Radius around the player to predict blocks.")
            .defaultValue(32)
            .min(8)
            .max(128)
            .build()
    );

    private final List<BlockPos> predictedBlocks = new ArrayList<>();
    private static final long SEED = 6608149111735331168L;

    public SeedCracker() {
        super(GlazedAddon.esp, "seed-cracker", "Predicts block positions from the world seed.");
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
        if (mc.player == null) return;

        predictedBlocks.clear();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.get();

        // Only generate positions in chunks around player
        int minX = playerPos.getX() - r;
        int maxX = playerPos.getX() + r;
        int minZ = playerPos.getZ() - r;
        int maxZ = playerPos.getZ() + r;

        for (int x = minX; x <= maxX; x += 4) { // step 4 for less blocks
            for (int z = minZ; z <= maxZ; z += 4) {
                for (int y = 0; y <= 64; y++) { // only below y=64 for ores
                    Block predicted = getPredictedBlock(x, y, z);
                    if (blocksToFind.get().contains(predicted)) {
                        predictedBlocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    private Block getPredictedBlock(int x, int y, int z) {
        // Deterministic pseudo-random generation based on seed + coords
        Random rand = new Random(SEED + x * 341873128712L + y * 132897987541L + z * 42317861L);
        int val = rand.nextInt(100);
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

        for (BlockPos pos : predictedBlocks) {
            Box box = new Box(pos);
            event.renderer.box(box, c, c, shapeMode.get(), 2);
            event.renderer.line(eye.x, eye.y, eye.z,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, c);
        }
    }
}
