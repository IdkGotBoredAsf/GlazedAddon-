package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

public class SusESP extends Module {

    private final SettingColor blockColor = new SettingColor(255, 0, 0, 180);

    private final Map<ChunkPos, BlockPos> suspiciousBlocks = new ConcurrentHashMap<>();
    private final Queue<Long> recentAlerts = new ConcurrentLinkedQueue<>();

    private final Random random = new Random();

    public SusESP() {
        super(GlazedAddon.esp, "SusESP", "ESP suspicious blocks with notifications (fixed detection).");
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
        while (!recentAlerts.isEmpty() && now - recentAlerts.peek() > 60000) {
            recentAlerts.poll();
        }
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (mc.world == null) return;
        Chunk chunk = event.chunk();
        ChunkPos pos = chunk.getPos();
        if (!suspiciousBlocks.containsKey(pos)) analyzeChunk(chunk);
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
        ChunkPos pos = chunk.getPos();
        ChunkSection[] sections = chunk.getSectionArray();

        // Collect all suspicious blocks in this chunk
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
                        if (isSuspiciousBlock(state)) {
                            candidates.add(bp);
                        }
                    }
                }
            }
        }

        if (!candidates.isEmpty()) {
            BlockPos selected = candidates.get(random.nextInt(candidates.size()));
            suspiciousBlocks.put(pos, selected);
            notifyDetection();
        }
    }

    private boolean isSuspiciousBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.DEEPSLATE ||
               block == Blocks.COBBLED_DEEPSLATE ||
               block == Blocks.POLISHED_DEEPSLATE ||
               block == Blocks.DEEPSLATE_BRICKS ||
               block == Blocks.DEEPSLATE_TILES ||
               block == Blocks.CHISELED_DEEPSLATE ||
               block == Blocks.DIAMOND_ORE ||
               block == Blocks.DEEPSLATE_DIAMOND_ORE ||
               block == Blocks.EMERALD_ORE ||
               block == Blocks.DEEPSLATE_EMERALD_ORE ||
               block == Blocks.GOLD_ORE ||
               block == Blocks.DEEPSLATE_GOLD_ORE ||
               block == Blocks.IRON_ORE ||
               block == Blocks.DEEPSLATE_IRON_ORE ||
               block == Blocks.ANCIENT_DEBRIS;
    }

    private void notifyDetection() {
        long now = System.currentTimeMillis();
        if (recentAlerts.size() >= 5) return;
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("§6[SusESP] §eSusESP Detected!"), false);
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

            Color color = new Color(blockColor);
            event.renderer.box(new net.minecraft.util.math.Box(pos), color, color, ShapeMode.Lines, 0);
        }
    }
}
