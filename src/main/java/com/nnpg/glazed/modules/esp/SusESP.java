package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

public class SusESP extends Module {

    private final Color blockColor = new Color(180, 100, 255, 120); // soft purple

    private final Map<ChunkPos, BlockPos> suspiciousBlocks = new ConcurrentHashMap<>();
    private final Queue<Long> recentAlerts = new ConcurrentLinkedQueue<>();
    private final Random random = new Random();

    public SusESP() {
        super(GlazedAddon.esp, "SusESP", "ESP sideways rotated normal deepslate blocks.");
    }

    @Override
    public void onActivate() {
        suspiciousBlocks.clear();
        recentAlerts.clear();
    }

    @Override
    public void onDeactivate() {
        suspiciousBlocks.clear();
        recentAlerts.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        long now = System.currentTimeMillis();
        while (!recentAlerts.isEmpty() && now - recentAlerts.peek() > 60000) recentAlerts.poll();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (mc.world == null) return;
        WorldChunk chunk = (WorldChunk) mc.world.getChunk(event.chunk().getPos().x, event.chunk().getPos().z);
        if (!suspiciousBlocks.containsKey(chunk.getPos())) analyzeChunk(chunk);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null) return;
        BlockPos pos = event.pos;
        BlockState state = event.newState;
        if (isSuspiciousBlock(state)) {
            ChunkPos chunkPos = new ChunkPos(pos);
            if (!suspiciousBlocks.containsKey(chunkPos)) {
                WorldChunk chunk = (WorldChunk) mc.world.getChunk(chunkPos.x, chunkPos.z);
                analyzeChunk(chunk);
            }
        }
    }

    private void analyzeChunk(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        var candidates = new java.util.ArrayList<BlockPos>();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section.isEmpty()) continue;
            int sectionY = chunk.getBottomY() + sectionIndex * 16;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockPos bp = new BlockPos(chunk.getPos().getStartX() + x, sectionY + y, chunk.getPos().getStartZ() + z);
                        BlockState state = section.getBlockState(x, y, z);
                        if (isSuspiciousBlock(state)) candidates.add(bp);
                    }
                }
            }
        }

        if (!candidates.isEmpty()) {
            BlockPos selected = candidates.get(random.nextInt(candidates.size()));
            suspiciousBlocks.put(chunk.getPos(), selected);
            notifyDetection();
        }
    }

    // Only detect sideways normal deepslate (AXIS X or Z)
    private boolean isSuspiciousBlock(BlockState state) {
        Block block = state.getBlock();
        if (block != Blocks.DEEPSLATE) return false;

        // Check axis property
        if (state.contains(Properties.AXIS)) {
            return state.get(Properties.AXIS) != net.minecraft.util.math.Direction.Axis.Y;
        }
        return false;
    }

    private void notifyDetection() {
        long now = System.currentTimeMillis();
        if (recentAlerts.size() >= 5) return;

        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("§6[SusESP] §eSideways Deepslate Detected!"), false);
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f));
            }
            recentAlerts.offer(now);
        });
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;

        for (Map.Entry<ChunkPos, BlockPos> entry : suspiciousBlocks.entrySet()) {
            BlockPos pos = entry.getValue();
            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
            if (distance > mc.options.getViewDistance().getValue() * 16) continue;

            event.renderer.box(new net.minecraft.util.math.Box(pos), blockColor, blockColor, ShapeMode.Lines, 2);
        }
    }
}
