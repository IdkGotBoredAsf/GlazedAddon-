package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
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

    private final Setting<Integer> minVineLength = sgGeneral.add(new IntSetting.Builder()
        .name("min-vine-length")
        .description("Minimum glow berry vine length to detect")
        .defaultValue(10)
        .min(10)
        .max(50)
        .build()
    );

    private final Color blockColor = new Color(125, 60, 152, 150);
    private final Map<ChunkPos, BlockPos> highlightedChunks = new ConcurrentHashMap<>();
    private final Queue<Long> recentAlerts = new ConcurrentLinkedQueue<>();
    private int tickCounter = 0;
    private final Random random = new Random();

    public SusESP() {
        super(GlazedAddon.esp, "SusESP", "Detects rotated deepslate and long glow berry vines.");
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
                if (chunk != null) {
                    if (containsRotatedDeepslate(chunk)) {
                        highlightRandomSolidBlock(chunk);
                    } else {
                        BlockPos vineBlock = detectGlowBerryVine(chunk);
                        if (vineBlock != null) {
                            highlightedChunks.put(cpos, vineBlock);
                            notifyDetection("Glow Berry Vine Detected! Chunk highlighted.");
                        }
                    }
                }
            }
        }
    }

    private boolean containsRotatedDeepslate(WorldChunk chunk) {
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isOf(Blocks.DEEPSLATE)) {
                            if (state.contains(Properties.AXIS)) {
                                Direction.Axis axis = state.get(Properties.AXIS);
                                if (axis != Direction.Axis.Y) return true;
                            }
                            if (state.contains(Properties.FACING)) {
                                Direction facing = state.get(Properties.FACING);
                                if (facing != Direction.UP) return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void highlightRandomSolidBlock(WorldChunk chunk) {
        ChunkPos pos = chunk.getPos();
        List<BlockPos> solidBlocks = new ArrayList<>();

        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;
            int baseY = chunk.getBottomY() + Arrays.asList(chunk.getSectionArray()).indexOf(section) * 16;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockPos blockPos = new BlockPos(pos.getStartX() + x, baseY + y, pos.getStartZ() + z);
                        if (!chunk.getBlockState(blockPos).isAir()) solidBlocks.add(blockPos);
                    }
                }
            }
        }

        if (!solidBlocks.isEmpty()) {
            highlightedChunks.put(pos, solidBlocks.get(random.nextInt(solidBlocks.size())));
            notifyDetection("Rotated Deepslate Found! Chunk highlighted.");
        }
    }

    private BlockPos detectGlowBerryVine(WorldChunk chunk) {
        ChunkPos pos = chunk.getPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = mc.world.getBottomY(); y < mc.world.getTopY(); y++) {
                    BlockPos start = new BlockPos(pos.getStartX() + x, y, pos.getStartZ() + z);
                    BlockState state = chunk.getBlockState(start);

                    if (state.isOf(Blocks.CAVE_VINES) || state.isOf(Blocks.CAVE_VINES_PLANT)) {
                        int length = 1;
                        BlockPos check = start.up();
                        while ((chunk.getBlockState(check).isOf(Blocks.CAVE_VINES) || chunk.getBlockState(check).isOf(Blocks.CAVE_VINES_PLANT)) && length <= 26) {
                            length++;
                            check = check.up();
                        }

                        if (length >= 10 && length <= 26) return start;
                    }
                }
            }
        }

        return null;
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
