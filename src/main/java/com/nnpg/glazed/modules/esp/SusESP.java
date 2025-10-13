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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SusESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to detected blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> highlightMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("highlight-mode")
        .description("Choose how blocks are highlighted.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("Delay between scans (in ticks).")
        .defaultValue(40)
        .min(5)
        .sliderRange(5, 200)
        .build()
    );

    private final Color blockColor = new Color(125, 60, 152, 150);
    private final Map<ChunkPos, BlockPos> highlightedChunks = new ConcurrentHashMap<>();
    private final Queue<Long> recentAlerts = new ConcurrentLinkedQueue<>();
    private int tickCounter = 0;
    private final Random random = new Random();
    private ExecutorService threadPool;

    public SusESP() {
        super(GlazedAddon.esp, "SusESP", "Detects rotated/cobbled deepslate, long cave vines, and tube-like cover holes.");
    }

    @Override
    public void onActivate() {
        highlightedChunks.clear();
        recentAlerts.clear();
        tickCounter = 0;
        threadPool = Executors.newFixedThreadPool(2);
    }

    @Override
    public void onDeactivate() {
        highlightedChunks.clear();
        recentAlerts.clear();
        if (threadPool != null) {
            threadPool.shutdownNow();
            threadPool = null;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        while (!recentAlerts.isEmpty() && now - recentAlerts.peek() > 60000) recentAlerts.poll();

        if (++tickCounter < scanInterval.get()) return;
        tickCounter = 0;

        int renderDistance = mc.options.getViewDistance().getValue();
        BlockPos playerPos = mc.player.getBlockPos();

        for (int cx = -renderDistance; cx <= renderDistance; cx++) {
            for (int cz = -renderDistance; cz <= renderDistance; cz++) {
                int chunkX = (playerPos.getX() >> 4) + cx;
                int chunkZ = (playerPos.getZ() >> 4) + cz;
                ChunkPos cpos = new ChunkPos(chunkX, chunkZ);

                if (highlightedChunks.containsKey(cpos)) continue;

                WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
                if (chunk != null) threadPool.submit(() -> scanChunk(chunk));
            }
        }
    }

    private void scanChunk(WorldChunk chunk) {
        ChunkPos pos = chunk.getPos();
        List<BlockPos> candidates = new ArrayList<>();

        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;

            int sectionBaseY = chunk.getBottomY() + Arrays.asList(chunk.getSectionArray()).indexOf(section) * 16;
            int startY = Math.max(sectionBaseY, -64);
            int endY = Math.min(sectionBaseY + 15, 45);

            for (int x = 0; x < 16; x++) {
                for (int y = startY; y <= endY; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockPos bp = new BlockPos(pos.getStartX() + x, y, pos.getStartZ() + z);
                        BlockState state = chunk.getBlockState(bp);
                        Block block = state.getBlock();

                        // Rotated DEEPSLATE / COBBLED_DEEPSLATE / DEEPSLATE above Y8
                        if ((block == Blocks.DEEPSLATE || block == Blocks.COBBLED_DEEPSLATE) && y > 8) {
                            if ((state.contains(Properties.AXIS) && state.get(Properties.AXIS) != Direction.Axis.Y)
                             || (state.contains(Properties.FACING) && state.get(Properties.FACING) != Direction.UP)) {
                                candidates.add(bp);
                            }
                        }

                        // Cave vine detection (length >= 18)
                        if ((block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT) && detectVineLength(chunk, bp) >= 18) {
                            candidates.add(bp);
                        }

                        // Tube-like cover hole detection: air column 1x1 surrounded by solid blocks
                        if (isTubeCoverBlock(chunk, bp)) candidates.add(bp);
                    }
                }
            }
        }

        if (!candidates.isEmpty()) {
            BlockPos selected = candidates.get(random.nextInt(candidates.size()));
            highlightedChunks.put(chunk.getPos(), selected);
            notifyDetection("Sus Block Detected! Chunk highlighted.");
        }
    }

    private int detectVineLength(WorldChunk chunk, BlockPos start) {
        int length = 1;
        BlockPos check = start.up();
        while (check.getY() <= 319 && (chunk.getBlockState(check).isOf(Blocks.CAVE_VINES) || chunk.getBlockState(check).isOf(Blocks.CAVE_VINES_PLANT))) {
            length++;
            check = check.up();
        }
        return length;
    }

    private boolean isTubeCoverBlock(WorldChunk chunk, BlockPos bp) {
        // Only consider air blocks
        if (!chunk.getBlockState(bp).isAir()) return false;

        // Check vertical depth 10–100
        int depth = 0;
        for (int i = 1; i <= 100; i++) {
            BlockPos check = bp.down(i);
            if (chunk.getBlockState(check).isAir()) depth++;
            else break;
        }
        if (depth < 10) return false;

        // Check horizontal surrounding blocks (tube: 1x1 air, all 4 sides solid)
        BlockPos[] sides = { bp.north(), bp.south(), bp.east(), bp.west() };
        for (BlockPos side : sides) {
            BlockState sideState = chunk.getBlockState(side);
            if (sideState.isAir()) return false;
        }

        return true;
    }

    private void notifyDetection(String msg) {
        long now = System.currentTimeMillis();
        if (recentAlerts.size() >= 5) return;

        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("§6[SusESP] §d" + msg), false);
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f));
            }
            recentAlerts.offer(now);
        });
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Vec3d crosshairPos = mc.player.getCameraPosVec(1);
        HitResult hit = mc.crosshairTarget;
        if (hit instanceof BlockHitResult blockHit) crosshairPos = blockHit.getPos();

        for (Map.Entry<ChunkPos, BlockPos> entry : highlightedChunks.entrySet()) {
            BlockPos pos = entry.getValue();
            if (mc.world.getBlockState(pos).isAir()) continue;

            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
            if (distance > mc.options.getViewDistance().getValue() * 16) continue;

            event.renderer.box(new Box(pos), blockColor, blockColor, highlightMode.get(), 2);

            if (tracers.get()) {
                Vec3d blockCenter = Vec3d.ofCenter(pos);
                event.renderer.line(
                    crosshairPos.x, crosshairPos.y, crosshairPos.z,
                    blockCenter.x, blockCenter.y, blockCenter.z,
                    blockColor
                );
            }
        }
    }
}
