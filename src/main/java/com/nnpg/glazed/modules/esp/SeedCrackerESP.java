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
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.util.math.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SeedCrackerESP predicts ore/block positions from a fixed seed and player coordinates.
 */
public class SeedCrackerESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the highlight boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Color> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of predicted blocks.")
        .defaultValue(new Color(255, 0, 0, 150))
        .build()
    );

    private final Setting<Integer> horizontalRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-range")
        .description("Number of chunks to predict around player.")
        .defaultValue(5)
        .min(1)
        .max(10)
        .build()
    );

    // Fixed world seed
    private static final long WORLD_SEED = 6608149111735331168L;

    // Caches predictions per chunk
    private final Map<Long, Map<Block, Set<Vec3d>>> chunkPredictions = new ConcurrentHashMap<>();

    public SeedCrackerESP() {
        super(GlazedAddon.esp, "seed-cracker-esp", "Predicts ore positions from a fixed seed.");
    }

    @Override
    public void onActivate() {
        chunkPredictions.clear();
    }

    @Override
    public void onDeactivate() {
        chunkPredictions.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        int range = horizontalRadius.get();
        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                long chunkKey = ChunkPos.toLong(chunkX, chunkZ);
                if (!chunkPredictions.containsKey(chunkKey)) {
                    Chunk chunk = mc.world.getChunk(chunkX, chunkZ);
                    if (chunk != null) {
                        chunkPredictions.put(chunkKey, generateChunkPredictions(chunk));
                    }
                }
            }
        }
    }

    private Map<Block, Set<Vec3d>> generateChunkPredictions(Chunk chunk) {
        Map<Block, Set<Vec3d>> predictions = new HashMap<>();
        ClientWorld world = mc.world;

        if (world == null) return predictions;

        // Replacement for old ChunkRandom
        Random random = Random.create(CheckedRandom.create(WORLD_SEED ^ ((long) chunk.getPos().x * 341873128712L) ^ ((long) chunk.getPos().z * 132897987541L)));

        Set<RegistryKey<Biome>> biomes = new HashSet<>();
        for (ChunkSection section : chunk.getSectionArray()) {
            section.getBiomeContainer().forEachValue(entry -> biomes.add(entry.getKey().get()));
        }

        // Get ores for biomes
        Set<Block> ores = biomes.stream().flatMap(b -> getOresForBiome(b).stream()).collect(Collectors.toSet());

        for (Block ore : ores) {
            Set<Vec3d> positions = new HashSet<>();

            int attempts = 5; // simplified number of veins per chunk
            for (int i = 0; i < attempts; i++) {
                int x = chunk.getPos().x * 16 + random.nextInt(16);
                int z = chunk.getPos().z * 16 + random.nextInt(16);
                int y = getOreHeight(ore, random);

                BlockPos pos = new BlockPos(x, y, z);
                if (pos.getY() >= -64 && pos.getY() <= 320) {
                    positions.add(Vec3d.ofCenter(pos));
                }
            }
            predictions.put(ore, positions);
        }

        return predictions;
    }

    private List<Block> getOresForBiome(RegistryKey<Biome> biome) {
        // Simplified biome-ore mapping
        return List.of(Blocks.DIAMOND_ORE, Blocks.IRON_ORE, Blocks.COAL_ORE, Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE);
    }

    private int getOreHeight(Block ore, Random random) {
        if (ore == Blocks.DIAMOND_ORE) return random.nextInt(16) - 64;
        if (ore == Blocks.IRON_ORE) return random.nextInt(64);
        if (ore == Blocks.COAL_ORE) return random.nextInt(128);
        if (ore == Blocks.REDSTONE_ORE) return random.nextInt(16) - 64;
        if (ore == Blocks.LAPIS_ORE) return random.nextInt(32);
        return random.nextInt(64);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d eye = mc.player.getCameraPosVec(event.tickDelta);
        Color c = color.get();

        for (Map<Block, Set<Vec3d>> chunk : chunkPredictions.values()) {
            for (Set<Vec3d> positions : chunk.values()) {
                for (Vec3d pos : positions) {
                    Box box = new Box(pos.x - 0.5, pos.y - 0.5, pos.z - 0.5, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
                    event.renderer.box(box, c, c, shapeMode.get(), 1);
                    event.renderer.line(eye.x, eye.y, eye.z, pos.x, pos.y, pos.z, c);
                }
            }
        }
    }
}
