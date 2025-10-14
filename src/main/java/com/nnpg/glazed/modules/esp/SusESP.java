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

public class SusESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Show tracers to esp.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> highlightMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("highlight-mode")
        .description("Choose how esp are highlighted.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("Delay between scans (in ticks).")
        .defaultValue(20)
        .min(5)
        .sliderRange(5, 200)
        .build()
    );

    private final Color blockColor = new Color(125, 60, 152, 150);

    private final Map<ChunkPos, BlockPos> highlightedChunks = new ConcurrentHashMap<>();
    private final Queue<Long> recentAlerts = new ConcurrentLinkedQueue<>();
    private final Random random = new Random();
    private int tickCounter = 0;

    private static final int COBBLED_THRESHOLD = 4;
    private static final int Y_MIN = -64;
    private static final int Y_MAX = 45;

    public SusESP() {
        super(GlazedAddon.esp, "SusESP", "Detects suspicious clusters.");
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

        if (++tickCounter < scanInterval.get()) return;
        tickCounter = 0;

        long now = System.currentTimeMillis();
        while (!recentAlerts.isEmpty() && now - recentAlerts.peek() > 60000) recentAlerts.poll();

        int renderDistance = mc.options.getViewDistance().getValue();
        BlockPos playerPos = mc.player.getBlockPos();

        for (int cx = -renderDistance; cx <= renderDistance; cx++) {
            for (int cz = -renderDistance; cz <= renderDistance; cz++) {
                int chunkX = (playerPos.getX() >> 4) + cx;
                int chunkZ = (playerPos.getZ() >> 4) + cz;
                ChunkPos cpos = new ChunkPos(chunkX, chunkZ);

                if (highlightedChunks.containsKey(cpos)) continue;

                WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;

                if (containsRotatedOrCobbledDeepslate(chunk)) {
                    highlightRandomSolidBlock(chunk);
                }
            }
        }
    }

    private boolean containsRotatedOrCobbledDeepslate(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int cobbledCount = 0;
        boolean foundRotated = false;

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;

            int baseY = chunk.getBottomY() + (i * 16);
            if (baseY > Y_MAX || baseY + 15 < Y_MIN) continue;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int worldY = baseY + y;
                    if (worldY > Y_MAX || worldY < Y_MIN) continue;

                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Block block = state.getBlock();

                        if (block == Blocks.COBBLED_DEEPSLATE) {
                            cobbledCount++;
                            if (cobbledCount >= COBBLED_THRESHOLD) return true;
                        }

                        if (block == Blocks.DEEPSLATE) {
                            if (state.contains(Properties.AXIS)) {
                                Direction.Axis axis = state.get(Properties.AXIS);
                                if (axis == Direction.Axis.X || axis == Direction.Axis.Z) foundRotated = true;
                            }
                            if (state.contains(Properties.FACING)) {
                                Direction facing = state.get(Properties.FACING);
                                if (facing != Direction.UP) foundRotated = true;
                            }
                        }

                        if (foundRotated) return true;
                    }
                }
            }
        }
        return false;
    }

    private void highlightRandomSolidBlock(WorldChunk chunk) {
        ChunkPos pos = chunk.getPos();
        List<BlockPos> solidBlocks = new ArrayList<>();

        for (int x = 0; x < 16; x++) {
            for (int y = Y_MIN; y <= Y_MAX; y++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos blockPos = new BlockPos(pos.getStartX() + x, y, pos.getStartZ() + z);
                    BlockState state = chunk.getBlockState(blockPos);
                    if (!state.isAir() && state.getBlock() != Blocks.VINE && state.getBlock() != Blocks.GLOW_LICHEN) {
                        solidBlocks.add(blockPos);
                    }
                }
            }
        }

        if (!solidBlocks.isEmpty()) {
            BlockPos selected = solidBlocks.get(random.nextInt(solidBlocks.size()));
            highlightedChunks.put(pos, selected);
            notifyDetection();
        }
    }

    private void notifyDetection() {
        long now = System.currentTimeMillis();
        if (recentAlerts.size() >= 5) return;

        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("§6[SusESP] §dSuspicious esp detected!"), false);
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
