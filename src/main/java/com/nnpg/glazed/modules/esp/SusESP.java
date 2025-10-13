package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.*;
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
        .defaultValue(new SettingColor(180, 90, 255, 180)) // soft purple
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

        // Update detected chunks
        for (WorldChunk chunk : mc.world.getChunkManager().getChunks().values()) { // Fixed
            ChunkPos pos = chunk.getPos();
            if (!espTargets.containsKey(pos)) {
                BlockPos found = findRotatedDeepslate(chunk);
                if (found != null) {
                    BlockPos random = pickRandomBlockInChunk(pos);
                    espTargets.put(pos, random);
                }
            }
        }

        MatrixStack matrices = event.matrices; // Fixed
        SettingColor c = color.get();
        float r = c.r / 255f;
        float g = c.g / 255f;
        float b = c.b / 255f;
        float a = c.a / 255f;

        RenderSystem.disableDepthTest();

        for (BlockPos bp : espTargets.values()) {
            if (bp == null) continue;
            Box box = new Box(bp);
            RenderUtils.boxESP(matrices, box, c, 1.0f, true, true, true, true); // Fixed
            if (showTracers.get()) drawTracerToBlock(matrices, bp, r, g, b, a);
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

    private void drawTracerToBlock(MatrixStack matrices, BlockPos pos, float r, float g, float b, float a) {
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d crosshair = camPos.add(mc.player.getRotationVector());

        RenderSystem.setShader(GameRenderer.getPositionColorShader()); // Fixed
        RenderSystem.lineWidth(tracerThickness.get().floatValue());

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();
        buffer.begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);

        // Cast doubles to floats
        buffer.vertex((float) crosshair.x, (float) crosshair.y, (float) crosshair.z).color(r, g, b, a).next();
        buffer.vertex((float) blockCenter.x, (float) blockCenter.y, (float) blockCenter.z).color(r, g, b, a).next();

        tess.draw();
    }
}
