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
import net.minecraft.block.BlockState;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

public class CoverPathDetector extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final long seed = 6608149111735331168L; // Your seed
    private final int searchRadius = 50; // How far to scan around player

    private final Set<BlockPos> suspiciousPaths = new HashSet<>();
    private int scanCounter = 0;

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the boxes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
            .name("color")
            .description("Color of suspicious blocks.")
            .defaultValue(new SettingColor(255, 0, 0, 150))
            .build()
    );

    public CoverPathDetector() {
        super(GlazedAddon.esp, "CoverPathDetector", "Detects paths covered with predictable blocks like stone.");
    }

    @Override
    public void onActivate() {
        suspiciousPaths.clear();
        scanCounter = 0;
    }

    @Override
    public void onDeactivate() {
        suspiciousPaths.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        if (++scanCounter < 20) return; // Run scan every 20 ticks (~1 second)
        scanCounter = 0;

        suspiciousPaths.clear();

        BlockPos playerPos = mc.player.getBlockPos();

        // Scan within radius
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                for (int dy = -5; dy <= 5; dy++) {
                    BlockPos currentPos = playerPos.add(dx, dy, dz);
                    if (currentPos.isWithinDistance(playerPos, searchRadius)) {
                        // Check if this position is a cover path
                        if (isSuspiciousBlock(currentPos)) {
                            suspiciousPaths.add(currentPos);
                        }
                    }
                }
            }
        }

        if (!suspiciousPaths.isEmpty()) {
            mc.execute(() -> {
                mc.player.sendMessage(Text.literal(
                        "[CoverPathDetector] Suspicious path blocks detected: " + suspiciousPaths.size()), false);
            });
        }
    }

    private boolean isSuspiciousBlock(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        // Generate expected block based on seed and position
        Block expectedBlock = getExpectedBlockAt(pos);

        // If the actual block differs from expected (e.g., stone covering coal ore)
        if (block != expectedBlock) {
            // Additional logic: you can refine this check, e.g., ignoring air, water, etc.
            if (block == Blocks.STONE || block == Blocks.DEEPSLATE) {
                return true; // Suspicious if expected was something else but found stone
            }
        }
        return false;
    }

    private Block getExpectedBlockAt(BlockPos pos) {
        // Use seed and position to deterministically select expected block
        long combined = seed ^ (pos.getX() * 341873128712L) ^ (pos.getZ() * 132897987541L);
        Random rand = new Random(combined);

        // Example: assume the "expected" block is coal ore at specific positions
        // You can expand this logic based on your world layout
        int choice = rand.nextInt(100);
        if (choice < 10) {
            return Blocks.COAL_ORE;
        } else if (choice < 20) {
            return Blocks.IRON_ORE;
        } else {
            return Blocks.DIRT;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Vec3d eyePos = mc.player.getCameraPosVec(event.tickDelta);
        Color highlightColor = color.get();

        for (BlockPos pos : suspiciousPaths) {
            if (pos == null) continue;

            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
            if (distance > searchRadius) continue;

            double alpha = Math.max(0.2, 1 - (distance / searchRadius));
            Color fadeColor = new Color(highlightColor.r, highlightColor.g, highlightColor.b, (int) (highlightColor.a * alpha));

            Box box = new Box(pos);
            event.renderer.box(box, fadeColor, fadeColor, shapeMode.get(), 2);
        }
    }
}
