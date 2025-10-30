package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seed-based predicted block ESP.
 */
public class SeedCrackerESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

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

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of predicted blocks.")
        .defaultValue(new SettingColor(125, 60, 152, 150))
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

    private static final long SEED = 6608149111735331168L;
    private final Map<BlockPos, Block> predicted = new ConcurrentHashMap<>();

    public SeedCrackerESP() {
        super(GlazedAddon.esp, "seed-cracker-esp", "Predict block positions from a known seed (client-side).");
    }

    @Override
    public void onActivate() { predicted.clear(); }

    @Override
    public void onDeactivate() { predicted.clear(); }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        predicted.clear();

        int range = scanRange.get();
        int step = Math.max(1, chunkStep.get());
        List<Block> targets = blocksToFind.get();
        if (targets.isEmpty()) return;

        int chunkRadius = (range >> 4) + 1;
        BlockPos playerPos = mc.player.getBlockPos();
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        for (int cx = -chunkRadius; cx <= chunkRadius; cx += step) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz += step) {
                int chunkX = playerChunkX + cx;
                int chunkZ = playerChunkZ + cz;

                long chunkCenterX = (chunkX << 4) + 8;
                long chunkCenterZ = (chunkZ << 4) + 8;
                long dx = chunkCenterX - playerPos.getX();
                long dz = chunkCenterZ - playerPos.getZ();
                if (dx*dx + dz*dz > (long)range*range + 256) continue;

                generatePredictionsForChunk(chunkX, chunkZ, playerPos, range, targets);
            }
        }
    }

    private void generatePredictionsForChunk(int chunkX, int chunkZ, BlockPos playerPos, int range, List<Block> targets) {
        long seedForChunk = mixSeed(SEED, chunkX, chunkZ);
        Random chunkRng = new Random(seedForChunk);

        for (Block target : targets) {
            OreDistribution dist = chooseDistribution(target);
            int attempts = dist.attempts;

            for (int a = 0; a < attempts; a++) {
                int offX = chunkRng.nextInt(16);
                int offZ = chunkRng.nextInt(16);

                int y;
                if (dist.randomUniform) {
                    y = dist.minY + chunkRng.nextInt(Math.max(1, dist.maxY - dist.minY + 1));
                } else {
                    int r1 = chunkRng.nextInt(dist.maxY - dist.minY + 1);
                    int r2 = chunkRng.nextInt(dist.maxY - dist.minY + 1);
                    y = dist.minY + Math.max(r1, r2);
                }

                int worldX = (chunkX << 4) + offX;
                int worldZ = (chunkZ << 4) + offZ;
                BlockPos pos = new BlockPos(worldX, y, worldZ);
                if (pos.getSquaredDistance(playerPos) > (long)range * range) continue;

                Random posRng = new Random(seedForChunk ^ ((long)offX << 16) ^ ((long)offZ << 32) ^ (long)y * 961748927L);
                if (posRng.nextInt(100) < dist.chancePercent) predicted.put(pos, target);
            }
        }
    }

    private long mixSeed(long seed, int chunkX, int chunkZ) {
        long h = seed;
        h ^= (long)chunkX * 341873128712L;
        h ^= (long)chunkZ * 132897987541L;
        h = (h << 13) ^ (h >>> 7) ^ 0x9e3779b97f4a7c15L;
        return h;
    }

    private static class OreDistribution {
        final int minY, maxY, attempts, chancePercent;
        final boolean randomUniform;
        OreDistribution(int minY, int maxY, int attempts, int chancePercent, boolean uniform) {
            this.minY = minY; this.maxY = maxY; this.attempts = attempts;
            this.chancePercent = chancePercent; this.randomUniform = uniform;
        }
    }

    private OreDistribution chooseDistribution(Block block) {
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return new OreDistribution(-64,16,3,18,false);
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return new OreDistribution(-16,64,6,28,false);
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return new OreDistribution(0,128,8,36,true);
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) return new OreDistribution(-64,32,2,12,false);
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return new OreDistribution(-64,16,6,30,false);
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return new OreDistribution(-32,32,2,10,false);
        return new OreDistribution(-64,128,2,8,true);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d eye = mc.player.getCameraPosVec(event.tickDelta);
        SettingColor c = color.get();

        for (Map.Entry<BlockPos, Block> e : predicted.entrySet()) {
            BlockPos pos = e.getKey();
            Box box = new Box(pos);
            event.renderer.box(box, c, c, shapeMode.get(), 2);
            Vec3d center = Vec3d.ofCenter(pos);
            event.renderer.line(eye.x, eye.y, eye.z, center.x, center.y, center.z, c);
        }
    }
}
