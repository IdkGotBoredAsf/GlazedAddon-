package com.nnpg.glazed.modules.esp;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SusESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Color> color = sgGeneral.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("Color of the ESP box and tracer.")
        .defaultValue(new Color(180, 90, 255, 180)) // soft purple
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

        MatrixStack matrices = event.matrices();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();

        Color c = color.get();
        float r = c.getRed() / 255f;
        float g = c.getGreen() / 255f;
        float b = c.getBlue() / 255f;
        float a = c.getAlpha() / 255f;

        for (BlockPos bp : espTargets.values()) {
            if (bp == null) continue;
            Box box = new Box(bp);
            RenderUtils.drawBoxOutline(box, r, g, b, a, (float) tracerThickness.get());
            if (showTracers.get()) drawTracerToBlock(bp, r, g, b, a);
        }

        RenderSystem.enableDepthTest();
    }

    private BlockPos findRotatedDeepslate(WorldChunk chunk) {
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        for (int x = 0; x < 16; x++) {
            for (int y = -64; y <= 45; y++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos pos = new BlockPos(startX + x, y, startZ + z);
                    Block block = mc.world.getBlockState(pos).getBlock();
                    if (block == Blocks.DEEPSLATE) {
                        // Heuristic rotation check: deepslate with directional properties
                        if (mc.world.getBlockState(pos).getEntries().toString().contains("axis")) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private BlockPos pickRandomBlockInChunk(ChunkPos chunkPos) {
        int x = ThreadLocalRandom.current().nextInt(16) + chunkPos.getStartX();
        int z = ThreadLocalRandom.current().nextInt(16) + chunkPos.getStartZ();
        int y = Math.min(mc.world.getTopY(), 255);
        return new BlockPos(x, y, z);
    }

    private void drawTracerToBlock(BlockPos pos, float r, float g, float b, float a) {
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d crosshair = camPos.add(mc.player.getRotationVector().multiply(1.0));

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth((float) tracerThickness.get());

        buffer.begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);
        buffer.vertex(crosshair.x, crosshair.y, crosshair.z).color(r, g, b, a).next();
        buffer.vertex(blockCenter.x, blockCenter.y, blockCenter.z).color(r, g, b, a).next();
        tess.draw();
    }

    // Internal helper for crisp outlines
    private static class RenderUtils {
        static void drawBoxOutline(Box box, float r, float g, float b, float a, float thickness) {
            RenderSystem.lineWidth(thickness);
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buffer = tess.getBuffer();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            buffer.begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);

            double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
            double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;

            // Draw box edges
            add(buffer, x1, y1, z1, x2, y1, z1, r, g, b, a);
            add(buffer, x1, y1, z1, x1, y2, z1, r, g, b, a);
            add(buffer, x1, y1, z1, x1, y1, z2, r, g, b, a);
            add(buffer, x2, y1, z1, x2, y2, z1, r, g, b, a);
            add(buffer, x2, y1, z1, x2, y1, z2, r, g, b, a);
            add(buffer, x1, y2, z1, x2, y2, z1, r, g, b, a);
            add(buffer, x1, y1, z2, x2, y1, z2, r, g, b, a);
            add(buffer, x1, y1, z2, x1, y2, z2, r, g, b, a);
            add(buffer, x1, y2, z2, x2, y2, z2, r, g, b, a);
            add(buffer, x2, y1, z2, x2, y2, z2, r, g, b, a);
            add(buffer, x1, y2, z1, x1, y2, z2, r, g, b, a);
            add(buffer, x2, y2, z1, x2, y2, z2, r, g, b, a);

            tess.draw();
        }

        private static void add(BufferBuilder buffer, double x1, double y1, double z1,
                                double x2, double y2, double z2, float r, float g, float b, float a) {
            buffer.vertex(x1, y1, z1).color(r, g, b, a).next();
            buffer.vertex(x2, y2, z2).color(r, g, b, a).next();
        }
    }
}
