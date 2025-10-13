package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
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

import java.util.HashSet;
import java.util.Set;

public class SusESP extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far to search for suspicious blocks.")
        .defaultValue(50)
        .min(5)
        .max(200)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to suspicious blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the boxes.")
        .defaultValue(new SettingColor(255, 100, 0, 150))
        .build()
    );

    private final Setting<Boolean> clusterDetection = sgGeneral.add(new BoolSetting.Builder()
        .name("cluster-detection")
        .description("Only render clusters of suspicious blocks close together (helps remove false positives).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> clusterRadius = sgGeneral.add(new IntSetting.Builder()
        .name("cluster-radius")
        .description("How close blocks must be to be considered a cluster.")
        .defaultValue(3)
        .min(1)
        .max(10)
        .visible(clusterDetection::get)
        .build()
    );

    private final Set<Block> suspiciousBlocks = Set.of(
        Blocks.CHEST, Blocks.TRAPPED_CHEST,
        Blocks.ENDER_CHEST,
        Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
        Blocks.CRAFTING_TABLE,
        Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
        Blocks.BEDROCK, // sometimes used in custom builds or markers
        Blocks.BED,
        Blocks.ENCHANTING_TABLE,
        Blocks.BREWING_STAND
    );

    private final Set<BlockPos> renderPositions = new HashSet<>();

    public SusESP() {
        super(GlazedAddon.esp, "sus-esp", "Highlights suspicious blocks that could indicate a base.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        renderPositions.clear();

        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.get().intValue();

        // Scan nearby blocks
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    Block block = mc.world.getBlockState(pos).getBlock();

                    if (suspiciousBlocks.contains(block)) {
                        if (clusterDetection.get()) {
                            if (!isIsolated(pos)) renderPositions.add(pos);
                        } else {
                            renderPositions.add(pos);
                        }
                    }
                }
            }
        }

        Vec3d playerCam = mc.player.getCameraPosVec(event.tickDelta);

        for (BlockPos pos : renderPositions) {
            Box box = new Box(pos);
            Color c = new Color(color.get());
            event.renderer.box(box, c, c, shapeMode.get(), 0);

            if (tracers.get()) {
                Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                event.renderer.line(playerCam.x, playerCam.y, playerCam.z, center.x, center.y, center.z, c);
            }
        }
    }

    private boolean isIsolated(BlockPos pos) {
        int nearby = 0;
        for (int dx = -clusterRadius.get(); dx <= clusterRadius.get(); dx++) {
            for (int dy = -clusterRadius.get(); dy <= clusterRadius.get(); dy++) {
                for (int dz = -clusterRadius.get(); dz <= clusterRadius.get(); dz++) {
                    BlockPos neighbor = pos.add(dx, dy, dz);
                    if (neighbor.equals(pos)) continue;
                    if (suspiciousBlocks.contains(mc.world.getBlockState(neighbor).getBlock())) {
                        nearby++;
                        if (nearby >= 2) return false; // Found cluster
                    }
                }
            }
        }
        return true;
    }
}
