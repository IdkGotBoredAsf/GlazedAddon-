package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SusESP extends Module {
    // Soft purple highlight color
    private final Color blockColor = new Color(180, 100, 255, 140);

    private final Map<ChunkPos, BlockPos> highlightedChunks = new ConcurrentHashMap<>();
    private final Queue<Long> recentAlerts = new ConcurrentLinkedQueue<>();
    private final Random random = new Random();

    // Config
    private final boolean drawTracers = true;
    private final boolean sidesOnly = false;
    private final int scanInterval = 40; // every 2 seconds (20tps = 1s)
    private int tickCounter = 0;

    public SusESP() {
        super(GlazedAddon.esp, "SusESP", "Detects rotated deepslate and highlights a random block in that chunk (searches whole render distance).");
    }

    @Override
    public void onActivate() {
        highlightedChunks.clear();
        recentAlerts.clear();
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        highlightedChunks.clear();
        recentAlerts.clear();
    }

    // === Main tick loop ===
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        while (!recentAlerts.isEmpty() && now - recentAlerts.peek() > 60000) recentAlerts.poll();

        if (++tickCounter < scanInterval) return; // scan every few seconds
        tickCounter = 0;

        int renderDistance = mc.options.getViewDistance().getValue();
        BlockPos playerPos = mc.player.getBlockPos();

        // Iterate all loaded chunks in render distance
        for (int cx = -renderDistance; cx <= renderDistance; cx++) {
            for (int cz = -renderDistance; cz <= renderDistance; cz++) {
                int chunkX = (playerPos.getX() >> 4) + cx;
                int chunkZ = (playerPos.getZ() >> 4) + cz;
                ChunkPos cpos = new ChunkPos(chunkX, chunkZ);

                if (highlightedChunks.containsKey(cpos)) continue; // already analyzed

                WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
                if (chunk != null) {
                    if (containsRotatedDeepslate(chunk)) {
                        highlightRandomBlock(chunk);
                    }
                }
            }
        }
    }

    // === Detect sideways or upside-down deepslate ===
    private boolean containsRotatedDeepslate(WorldChunk chunk) {
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section.isEmpty()) continue;

            int baseY = chunk.getBottomY() + (section.getYOffset());
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Block block = state.getBlock();

                        if (block == Blocks.DEEPSLATE && state.contains(Properties.AXIS)) {
                            Direction.Axis axis = state.get(Properties.AXIS);
                            if (axis == Direction.Axis.X || axis == Direction.Axis.Z) return true;
                        }
                        if (block == Blocks.DEEPSLATE && state.contains(Properties.FACING)) {
                            Direction facing = state.get(Properties.FACING);
                            if (facing == Direction.DOWN || facing == Direction.NORTH || facing == Direction.SOUTH
                                    || facing == Direction.EAST || facing == Direction.WEST) {
                                if (facing != Direction.UP) return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    // === Highlight a random block in the chunk ===
    private void highlightRandomBlock(WorldChunk chunk) {
        ChunkPos pos = chunk.getPos();
        ChunkSection[] sections = chunk.getSectionArray();
        List<BlockPos> blocks = new ArrayList<>();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section.isEmpty()) continue;
            int baseY = chunk.getBottomY() + sectionIndex * 16;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        blocks.add(new BlockPos(pos.getStartX() + x, baseY + y, pos.getStartZ() + z));
                    }
                }
            }
        }

        if (!blocks.isEmpty()) {
            BlockPos selected = blocks.get(random.nextInt(blocks.size()));
            highlightedChunks.put(pos, selected);
            notifyDetection();
        }
    }

    private void notifyDetection() {
        long now = System.currentTimeMillis();
        if (recentAlerts.size() >= 5) return;

        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("§6[SusESP] §eRotated Deepslate Found! Random block highlighted."), false);
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f));
            }
            recentAlerts.offer(now);
        });
    }

    // === Render highlights and tracers ===
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d playerEyes = mc.player.getCameraPosVec(1);

        for (Map.Entry<ChunkPos, BlockPos> entry : highlightedChunks.entrySet()) {
            BlockPos pos = entry.getValue();
            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
            if (distance > mc.options.getViewDistance().getValue() * 16) continue;

            ShapeMode mode = sidesOnly ? ShapeMode.Lines : ShapeMode.Both;
            event.renderer.box(new Box(pos), blockColor, blockColor, mode, 2);

            if (drawTracers) {
                Vec3d blockCenter = Vec3d.ofCenter(pos);
                event.renderer.line(playerEyes, blockCenter, blockColor, 1.5);
            }
        }
    }
}
