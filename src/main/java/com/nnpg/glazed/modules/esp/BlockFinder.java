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
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
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

    private final Setting<Color> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the highlight.")
        .defaultValue(new Color(0, 255, 120, 150))
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

    private final Map<BlockPos, Block> foundBlocks = new ConcurrentHashMap<>();

    private int tickDelay = 0;

    public BlockFinder() {
        super(GlazedAddon.esp, "BlockFinder", "Highlights specific blocks you choose in the world or on servers.");
    }

    @Override
    public void onActivate() {
        foundBlocks.clear();
        tickDelay = 0;
    }

    @Override
    public void onDeactivate() {
        foundBlocks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        // Simple delay so it doesn’t lag
        if (tickDelay++ < 20) return;
        tickDelay = 0;

        foundBlocks.clear();

        BlockPos playerPos = mc.player.getBlockPos();
        int search = range.get();
        List<Block> targets = blocksToFind.get();

        if (targets.isEmpty()) return;

        for (int x = -search; x <= search; x++) {
            for (int y = -search; y <= search; y++) {
                for (int z = -search; z <= search; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    if (state == null) continue;

                    Block block = state.getBlock();
                    if (targets.contains(block)) {
                        foundBlocks.put(pos.toImmutable(), block);
                    }
                }
            }
        }

        if (!foundBlocks.isEmpty()) {
            mc.execute(() -> {
                mc.player.sendMessage(Text.literal("§a[BlockFinder] Found " + foundBlocks.size() + " target block(s)."), false);
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.2f));
            });
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Vec3d eyePos = mc.player.getCameraPosVec(event.tickDelta);

        for (BlockPos pos : foundBlocks.keySet()) {
            if (pos == null) continue;

            Box box = new Box(pos);
            event.renderer.box(box, color.get(), color.get(), shapeMode.get(), 2);

            if (tracers.get()) {
                Vec3d center = Vec3d.ofCenter(pos);
                event.renderer.line(
                    eyePos.x, eyePos.y, eyePos.z,
                    center.x, center.y, center.z,
                    color.get()
                );
            }
        }
    }
}
