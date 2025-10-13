package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SusESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
            .name("highlight-color")
            .description("Color of the ESP box and tracer.")
            .defaultValue(new SettingColor(180, 90, 255, 180))
            .build()
    );

    private final Setting<Boolean> showTracers = sgGeneral.add(new BoolSetting.Builder()
            .name("show-tracers")
            .description("Draws a tracer from crosshair to detected blocks.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> tracerThickness = sgGeneral.add(new DoubleSetting.Builder()
            .name("tracer-thickness")
            .defaultValue(1.2)
            .min(0.1)
            .sliderMax(5)
            .build()
    );

    private final Map<ChunkPos, BlockPos> espTargets = new HashMap<>();

    public SusESP() {
        super(GlazedAddon.esp, "sus-esp", "Highlights random blocks in chunks where rotated deepslate is detected.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;

        // Iterate loaded chunks properly in 1.21
        for (WorldChunk chunk : mc.world.getChunkManager().getLoadedChunksIterable()) {
            ChunkPos pos = chunk.getPos();
            if (!espTargets.containsKey(pos)) {
                BlockPos found = findRotatedDeepslate(chunk);
                if (found != null) {
                    BlockPos random = pickRandomBlockInChunk(pos);
                    espTargets.put(pos, random);
                }
            }
        }

        MatrixStack matrices = event.matrices;
        SettingColor c = color.get();
        float r = c.r / 255f;
        float g = c.g / 255f;
        float b = c.b / 255f;
        float a = c.a / 255f;

        for (BlockPos bp : espTargets.values()) {
            if (bp == null) continue;

            // Draw ESP box using Meteor's RenderUtils
            RenderUtils.box(matrices, new Box(bp), r, g, b, a, (float) tracerThickness.get());

            // Draw tracers
            if (showTracers.get()) drawTracer(matrices, bp, r, g, b, a);
        }
    }

    private BlockPos findRotatedDeepslate(WorldChunk chunk) {
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        for (int x = 0; x < 16; x++) {
            for (int y = -64; y <= 45; y++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos pos = new BlockPos(startX + x, y, startZ + z);
                    if (mc.world.getBlockState(pos).getBlock() == Blocks.DEEPSLATE
                            && mc.world.getBlockState(pos).getEntries().toString().contains("axis")) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private BlockPos pickRandomBlockInChunk(ChunkPos chunkPos) {
        int x = ThreadLocalRandom.current().nextInt(16) + chunkPos.getStartX();
        int z = ThreadLocalRandom.current().nextInt(16) + chunkPos.getStartZ();
        int y = mc.world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
        return new BlockPos(x, y, z);
    }

    private void drawTracer(MatrixStack matrices, BlockPos pos, float r, float g, float b, float a) {
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        Vec3d blockCenter = Vec3d.ofCenter(pos);

        // Use Meteor RenderUtils line for tracers
        RenderUtils.line(matrices, camPos, blockCenter, r, g, b, a, (float) tracerThickness.get());
    }
}
