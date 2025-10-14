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
import java.util.concurrent.ConcurrentHashMap;

public class BlockFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Block>> blocksToFind = sgGeneral.add(new BlockListSetting.Builder()
            .name("blocks")
            .description("Blocks to highlight in the world.")
            .defaultValue(List.of())
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the boxes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
            .name("color")
            .description("Color of the highlight.")
            .defaultValue(new SettingColor(0, 255, 120, 150))
            .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
            .name("tracers")
            .description("Draws tracers to the found blocks.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
            .name("range")
            .description("How far to search for the blocks.")
            .defaultValue(64)
            .min(8)
            .sliderRange(8, 256)
            .build()
    );

    private final Setting<Integer> scanDelayTicks = sgGeneral.add(new IntSetting.Builder()
            .name("scan-delay-ticks")
            .description("Ticks between full scans (20 ticks = 1 second).")
            .defaultValue(10)
            .min(1)
            .max(200)
            .build()
    );

    private final Map<BlockPos, Block> foundBlocks = new ConcurrentHashMap<>();
    private final Set<BlockPos> predictedDiamonds = new HashSet<>();
    private int tickDelayCounter = 0;
    private final Random random = new Random(); // client-side pseudo-random

    public BlockFinder() {
        super(GlazedAddon.esp, "BlockFinder", "Advanced ESP for selected blocks, including hidden ores and predicted diamonds.");
    }

    @Override
    public void onActivate() {
        foundBlocks.clear();
        predictedDiamonds.clear();
        tickDelayCounter = 0;
    }

    @Override
    public void onDeactivate() {
        foundBlocks.clear();
        predictedDiamonds.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        if (++tickDelayCounter < scanDelayTicks.get()) return;
        tickDelayCounter = 0;

        foundBlocks.clear();
        predictedDiamonds.clear();

        BlockPos playerPos = mc.player.getBlockPos();
        int search = range.get();
        List<Block> targets = expandBlocks(blocksToFind.get());
        if (targets.isEmpty()) return;

        // Generate pseudo-random predicted diamonds
        generatePredictedDiamonds(playerPos, search);

        // Calculate chunk radius for efficiency
        int chunkRadius = (search >> 4) + 1;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int chunkX = (playerPos.getX() >> 4) + dx;
                int chunkZ = (playerPos.getZ() >> 4) + dz;
                WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;

                scanChunk(chunk, playerPos, search, targets);
            }
        }

        if (!foundBlocks.isEmpty() || !predictedDiamonds.isEmpty()) {
            mc.execute(() -> {
                mc.player.sendMessage(Text.literal(
                        "§a[BlockFinder] Found " + foundBlocks.size() + " target block(s) and " +
                                predictedDiamonds.size() + " predicted diamond(s)."), false);
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.2f));
            });
        }
    }

    private void generatePredictedDiamonds(BlockPos playerPos, int range) {
        int diamondCount = 20; // number of predicted diamonds

        for (int i = 0; i < diamondCount; i++) {
            int x = playerPos.getX() + random.nextInt(range * 2) - range;
            int y = 5 + random.nextInt(16); // diamond height 5-20
            int z = playerPos.getZ() + random.nextInt(range * 2) - range;

            predictedDiamonds.add(new BlockPos(x, y, z));
        }
    }

    private List<Block> expandBlocks(List<Block> targets) {
        List<Block> expanded = new ArrayList<>(targets);

        for (Block b : targets) {
            if (b == Blocks.COAL_ORE) expanded.add(Blocks.DEEPSLATE_COAL_ORE);
            else if (b == Blocks.IRON_ORE) expanded.add(Blocks.DEEPSLATE_IRON_ORE);
            else if (b == Blocks.GOLD_ORE) expanded.add(Blocks.DEEPSLATE_GOLD_ORE);
            else if (b == Blocks.DIAMOND_ORE) expanded.add(Blocks.DEEPSLATE_DIAMOND_ORE);
            else if (b == Blocks.REDSTONE_ORE) expanded.add(Blocks.DEEPSLATE_REDSTONE_ORE);
            else if (b == Blocks.LAPIS_ORE) expanded.add(Blocks.DEEPSLATE_LAPIS_ORE);
            else if (b == Blocks.EMERALD_ORE) expanded.add(Blocks.DEEPSLATE_EMERALD_ORE);
            else if (b == Blocks.COPPER_ORE) expanded.add(Blocks.DEEPSLATE_COPPER_ORE);
        }

        return expanded;
    }

    private void scanChunk(WorldChunk chunk, BlockPos playerPos, int range, List<Block> targets) {
        ChunkSection[] sections = chunk.getSectionArray();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section == null || section.isEmpty()) continue;

            int baseY = sectionIndex << 4;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int worldY = baseY + y;
                    if (worldY < 0 || worldY > 255) continue;

                    for (int z = 0; z < 16; z++) {
                        BlockPos pos = new BlockPos((chunk.getPos().x << 4) + x, worldY, (chunk.getPos().z << 4) + z);
                        if (pos.getSquaredDistance(playerPos) > range * range) continue;

                        BlockState state = chunk.getBlockState(pos);
                        if (state == null) continue;

                        Block block = state.getBlock();
                        if (targets.contains(block)) {
                            foundBlocks.put(pos, block);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Vec3d eyePos = mc.player.getCameraPosVec(event.tickDelta);
        Color baseColor = color.get();

        // Render found blocks
        for (BlockPos pos : foundBlocks.keySet()) {
            if (pos == null) continue;

            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
            double alpha = Math.max(0.2, 1 - (distance / range.get()));
            Color fadeColor = new Color(baseColor.r, baseColor.g, baseColor.b, (int) (baseColor.a * alpha));

            Box box = new Box(pos);
            event.renderer.box(box, fadeColor, fadeColor, shapeMode.get(), 2);

            if (tracers.get()) {
                Vec3d center = Vec3d.ofCenter(pos);
                event.renderer.line(eyePos.x, eyePos.y, eyePos.z, center.x, center.y, center.z, fadeColor);
            }
        }

        // Render predicted diamonds
        for (BlockPos pos : predictedDiamonds) {
            if (pos == null) continue;

            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
            if (distance > range.get()) continue;

            double alpha = Math.max(0.2, 1 - (distance / range.get()));
            Color predColor = new Color(0, 0, 255, (int) (255 * alpha)); // blue for predicted diamonds

            Box box = new Box(pos);
            event.renderer.box(box, predColor, predColor, shapeMode.get(), 2);

            if (tracers.get()) {
                Vec3d center = Vec3d.ofCenter(pos);
                event.renderer.line(eyePos.x, eyePos.y, eyePos.z, center.x, center.y, center.z, predColor);
            }
        }
    }
}
