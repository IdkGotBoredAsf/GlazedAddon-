package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor; // ✅ FIXED IMPORT
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highlights blocks that don't match what the world seed would normally generate.
 * (Simplified "seed consistency" checker.)
 */
public class SeedCheckerESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Block>> blocksToCheck = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks-to-check")
        .description("Blocks that should match expected world generation from your seed.")
        .defaultValue(List.of(Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.DIAMOND_ORE))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the highlight boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> highlightColor = sgGeneral.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("Color used for misplaced blocks.")
        .defaultValue(new SettingColor(255, 0, 0, 150))
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("scan-range")
        .description("Radius (in blocks) around the player to scan.")
        .defaultValue(32)
        .min(8)
        .max(128)
        .build()
    );

    private final Map<BlockPos, Block> misplacedBlocks = new ConcurrentHashMap<>();

    // Known survival world seed
    private static final long SEED = 6608149111735331168L;

    public SeedCheckerESP() {
        super(GlazedAddon.esp, "seed-checker-esp", "Highlights blocks that don't match the known world seed.");
    }

    @Override
    public void onActivate() {
        misplacedBlocks.clear();
    }

    @Override
    public void onDeactivate() {
        misplacedBlocks.clear();
    }

    // Lightweight scanning loop
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        misplacedBlocks.clear();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.get();
        int chunkRadius = (r >> 4) + 1;

        List<Block> targets = blocksToCheck.get();
        if (targets.isEmpty()) return;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int chunkX = (playerPos.getX() >> 4) + dx;
                int chunkZ = (playerPos.getZ() >> 4) + dz;
                WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;

                scanChunk(chunk, targets, playerPos, r);
            }
        }
    }

    private void scanChunk(WorldChunk chunk, List<Block> targets, BlockPos playerPos, int range) {
        for (BlockPos pos : BlockPos.iterate(
            chunk.getPos().getStartX(), 0,
            chunk.getPos().getStartZ(),
            chunk.getPos().getEndX(), 255,
            chunk.getPos().getEndZ()
        )) {
            if (pos.getSquaredDistance(playerPos) > (range * range)) continue;

            BlockState state = chunk.getBlockState(pos);
            if (state == null) continue;
            Block actual = state.getBlock();

            Block expected = generateExpectedBlock(pos);

            // highlight if mismatch between expected world-gen and actual block
            if (targets.contains(expected) && actual != expected) {
                misplacedBlocks.put(pos.toImmutable(), actual);
            }
        }
    }

    // Pseudo world-gen simulation based on seed
    private Block generateExpectedBlock(BlockPos pos) {
        Random r = new Random(SEED
            + (long) pos.getX() * 341873128712L
            + (long) pos.getY() * 132897987541L
            + (long) pos.getZ() * 42317861L
        );
        int val = r.nextInt(100);
        if (val < 5) return Blocks.DIAMOND_ORE;
        if (val < 15) return Blocks.IRON_ORE;
        if (val < 30) return Blocks.COAL_ORE;
        return Blocks.STONE;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Vec3d eye = mc.player.getCameraPosVec(event.tickDelta());
        Color c = highlightColor.get();

        for (BlockPos pos : misplacedBlocks.keySet()) {
            Box box = new Box(pos);
            event.renderer.box(box, c, c, shapeMode.get(), 2);
            // tracer to crosshair
            event.renderer.line(
                eye.x, eye.y, eye.z,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                c
            );
        }
    }
}
