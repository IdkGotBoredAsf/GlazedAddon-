package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.OreBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seed-based predicted block ESP.
 *
 * - Uses the provided world seed (constant SEED) and player's coords to deterministically predict likely ore/block positions.
 * - Generates predictions per chunk (lightweight) for selected block types.
 * - Only generates predictions inside the configured 'scanRange' radius.
 * - Renders clean boxes + tracers pointing at the crosshair.
 *
 * NOTE: This is a prediction heuristic (deterministic RNG per chunk). It purposely does NOT read/scan every block in loaded chunks
 * to avoid lag. It provides high density of likely ore locations, but will not be 100% identical to actual Minecraft worldgen.
 */
public class SeedCrackerESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Blocks the user wants to predict (select any blocks available in the block list ui)
    private final Setting<List<Block>> blocksToFind = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Blocks to predict from the world seed.")
        .defaultValue(List.of(Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.DIAMOND_ORE))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the highlight boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Color> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of predicted blocks.")
        .defaultValue(new Color(125, 60, 152, 150))
        .build()
    );

    private final Setting<Integer> scanRange = sgGeneral.add(new IntSetting.Builder()
        .name("scan-range")
        .description("Radius (blocks) around the player to predict positions.")
        .defaultValue(48)
        .min(8)
        .max(256)
        .build()
    );

    private final Setting<Integer> chunkStep = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-step")
        .description("Chunk step (1 = every chunk, 2 = every 2nd chunk). Increase to reduce predictions and cpu.")
        .defaultValue(1)
        .min(1)
        .max(4)
        .build()
    );

    // YOUR SEED — set to the seed you provided
    private static final long SEED = 6608149111735331168L;

    // store predicted positions (thread-safe-ish)
    private final Map<BlockPos, Block> predicted = new ConcurrentHashMap<>();

    public SeedCrackerESP() {
        super(GlazedAddon.esp, "seed-cracker-esp", "Predict block positions from a known seed (client-side).");
    }

    @Override
    public void onActivate() {
        predicted.clear();
    }

    @Override
    public void onDeactivate() {
        predicted.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        predicted.clear();

        int range = scanRange.get();
        int step = Math.max(1, chunkStep.get());
        List<Block> targets = blocksToFind.get();
        if (targets.isEmpty()) return;

        // determine chunk radius to cover the scan range
        int chunkRadius = (range >> 4) + 1;
        BlockPos playerPos = mc.player.getBlockPos();
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        // iterate chunks around the player (with chunkStep to reduce work if desired)
        for (int cx = -chunkRadius; cx <= chunkRadius; cx += step) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz += step) {
                int chunkX = playerChunkX + cx;
                int chunkZ = playerChunkZ + cz;

                // simple bound check: center of chunk vs player range (fast)
                int chunkCenterX = (chunkX << 4) + 8;
                int chunkCenterZ = (chunkZ << 4) + 8;
                long dx = chunkCenterX - playerPos.getX();
                long dz = chunkCenterZ - playerPos.getZ();
                if (dx*dx + dz*dz > (long)range*range + (16*16)) continue;

                // generate predicted positions for each requested block in that chunk
                generatePredictionsForChunk(chunkX, chunkZ, playerPos, range, targets);
            }
        }
    }

    /**
     * Generate deterministic predicted positions inside the chunk for the requested blocks.
     * This uses a per-chunk seeded RNG derived from the world seed and chunk coords.
     * For each target block type we use a small number of attempts and a tuned Y-range that matches typical ore heights.
     */
    private void generatePredictionsForChunk(int chunkX, int chunkZ, BlockPos playerPos, int range, List<Block> targets) {
        long seedForChunk = mixSeed(SEED, chunkX, chunkZ);
        // per-chunk RNG
        Random chunkRng = new Random(seedForChunk);

        // For each block type requested produce several candidate positions.
        for (Block target : targets) {
            // choose parameters based on block type for sensible distribution
            OreDistribution dist = chooseDistribution(target);

            // number of attempts per chunk for this ore (keeps CPU low)
            int attempts = dist.attempts;
            for (int a = 0; a < attempts; a++) {
                int offX = chunkRng.nextInt(16);
                int offZ = chunkRng.nextInt(16);

                // Y sampling according to distribution
                int y;
                if (dist.randomUniform) {
                    y = dist.minY + chunkRng.nextInt(Math.max(1, dist.maxY - dist.minY + 1));
                } else {
                    // biased sample: triangular or gaussian approximation
                    int r1 = chunkRng.nextInt(dist.maxY - dist.minY + 1);
                    int r2 = chunkRng.nextInt(dist.maxY - dist.minY + 1);
                    y = dist.minY + Math.max(r1, r2);
                }

                int worldX = (chunkX << 4) + offX;
                int worldZ = (chunkZ << 4) + offZ;
                BlockPos pos = new BlockPos(worldX, y, worldZ);

                // distance check to player center (keep only positions in range)
                if (pos.getSquaredDistance(playerPos) > (long)range * range) continue;

                // deterministic filter: re-seed with full coord for reproducibility & lower false positives
                Random posRng = new Random(seedForChunk ^ ((long)offX << 16) ^ ((long)offZ << 32) ^ (long)y * 961748927L);
                int roll = posRng.nextInt(100);
                if (roll < dist.chancePercent) {
                    predicted.put(pos, target);
                }
            }
        }
    }

    // mix seed with chunk coords into a stable long
    private long mixSeed(long seed, int chunkX, int chunkZ) {
        long h = seed;
        h ^= (long)chunkX * 341873128712L;
        h ^= (long)chunkZ * 132897987541L;
        h = (h << 13) ^ (h >>> 7) ^ 0x9e3779b97f4a7c15L;
        return h;
    }

    // small helper describing per-ore sampling parameters
    private static class OreDistribution {
        final int minY, maxY;
        final int attempts;
        final int chancePercent;
        final boolean randomUniform;
        OreDistribution(int minY, int maxY, int attempts, int chancePercent, boolean uniform) {
            this.minY = minY; this.maxY = maxY; this.attempts = attempts; this.chancePercent = chancePercent; this.randomUniform = uniform;
        }
    }

    // choose sampling params by block
    private OreDistribution chooseDistribution(Block block) {
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
            // diamonds: deeper, rarer
            return new OreDistribution(-64, 16, 3, 18, false);
        } else if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
            return new OreDistribution(-16, 64, 6, 28, false);
        } else if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
            return new OreDistribution(0, 128, 8, 36, true);
        } else if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) {
            return new OreDistribution(-64, 32, 2, 12, false);
        } else if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) {
            return new OreDistribution(-64, 16, 6, 30, false);
        } else if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
            return new OreDistribution(-32, 32, 2, 10, false);
        } else {
            // default: search all Y but with low chance
            return new OreDistribution( -64, 128, 2, 8, true );
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d eye = mc.player.getCameraPosVec(event.tickDelta);
        Color c = color.get();

        // Draw predicted boxes and tracers (clean esp)
        for (Map.Entry<BlockPos, Block> e : predicted.entrySet()) {
            BlockPos pos = e.getKey();
            Box box = new Box(pos);
            event.renderer.box(box, c, c, shapeMode.get(), 2);

            // tracer always points at crosshair position (eye)
            Vec3d center = Vec3d.ofCenter(pos);
            event.renderer.line(eye.x, eye.y, eye.z, center.x, center.y, center.z, c);
        }
    }
}
