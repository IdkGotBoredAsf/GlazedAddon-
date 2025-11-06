package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.GlowBerryBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GlowBerryESP extends Module {
    private final SettingGroup sgRender = settings.getDefaultGroup();
    private final SettingGroup sgRange = settings.createGroup("Range");
    private final SettingGroup sgThreading = settings.createGroup("Threading");

    private final Setting<SettingColor> espColor = sgRender.add(new ColorSetting.Builder()
        .name("ESP Color")
        .description("Color for the ESP boxes")
        .defaultValue(new SettingColor(0, 255, 0, 100))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("Shape Mode")
        .description("Rendering mode for the ESP boxes")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> showTracers = sgRender.add(new BoolSetting.Builder()
        .name("Show Tracers")
        .description("Draw tracer lines to grounded glow berries")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("Tracer Color")
        .description("Color for the tracer lines")
        .defaultValue(new SettingColor(0, 255, 0, 200))
        .visible(showTracers::get)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgRender.add(new BoolSetting.Builder()
        .name("Chat Feedback")
        .description("Announce glow berry detections in chat")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minBerryLength = sgRender.add(new IntSetting.Builder()
        .name("Min Berry Length")
        .description("Minimum berry length to highlight")
        .defaultValue(20)
        .min(15)
        .max(35)
        .sliderMax(35)
        .build()
    );


    private final Setting<Integer> minY = sgRange.add(new IntSetting.Builder()
        .name("Min Y")
        .description("Minimum Y level to scan for glow berries")
        .defaultValue(-64)
        .min(-64)
        .max(128)
        .sliderRange(-64, 128)
        .build()
    );

    private final Setting<Integer> maxY = sgRange.add(new IntSetting.Builder()
        .name("Max Y")
        .description("Maximum Y level to scan for glow berries")
        .defaultValue(320)
        .min(-64)
        .max(320)
        .sliderRange(-64, 320)
        .build()
    );


    private final Setting<Boolean> useThreading = sgThreading.add(new BoolSetting.Builder()
        .name("Enable Threading")
        .description("Use multi-threading for chunk scanning (better performance)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> threadPoolSize = sgThreading.add(new IntSetting.Builder()
        .name("Thread Pool Size")
        .description("Number of threads to use for scanning")
        .defaultValue(2)
        .min(1)
        .max(8)
        .sliderRange(1, 8)
        .visible(useThreading::get)
        .build()
    );

    private final Setting<Boolean> limitChatSpam = sgThreading.add(new BoolSetting.Builder()
        .name("Limit Chat Spam")
        .description("Reduce chat spam when using threading")
        .defaultValue(true)
        .visible(useThreading::get)
        .build()
    );

    private final Set<BlockPos> groundedGlowBerries = ConcurrentHashMap.newKeySet();

    private ExecutorService threadPool;

    public GlowBerryESP() {
        super(GlazedAddon.esp, "Glow Berry ESP", "ESP for fully grown glow berries touching the ground.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;

        if (useThreading.get()) {
            threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
        }

        groundedGlowBerries.clear();

        if (useThreading.get()) {
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) {
                    threadPool.submit(() -> scanChunk(worldChunk));
                }
            }
        } else {
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) {
                    scanChunk(worldChunk);
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            threadPool = null;
        }

        groundedGlowBerries.clear();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(() -> scanChunk(event.chunk()));
        } else {
            scanChunk(event.chunk());
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;
        BlockState state = event.newState;

        Runnable updateTask = () -> {
            boolean isGrounded = isGroundedGlowBerry(state, pos);

            if (isGrounded) {
                boolean wasAdded = groundedGlowBerries.add(pos);
                if (wasAdded && chatFeedback.get() && (!useThreading.get() || !limitChatSpam.get())) {
                    info("§aGlowBerryESP§f: Found glow berry at §a%s", pos.toShortString());
                }
            } else {
                groundedGlowBerries.remove(pos);
            }
        };

        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(updateTask);
        } else {
            updateTask.run();
        }
    }

    private void scanChunk(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = Math.max(chunk.getBottomY(), minY.get());
        int yMax = Math.min(chunk.getBottomY() + chunk.getHeight(), maxY.get());

        Set<BlockPos> chunkGlowBerries = new HashSet<>();
        int foundCount = 0;

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (isGroundedGlowBerryChunk(chunk, pos)) {
                        chunkGlowBerries.add(pos);
                        foundCount++;
                    }
                }
            }
        }

        groundedGlowBerries.removeIf(pos -> {
            ChunkPos blockChunk = new ChunkPos(pos);
            return blockChunk.equals(cpos) && !chunkGlowBerries.contains(pos);
        });

        int newGlowBerries = 0;
        for (BlockPos pos : chunkGlowBerries) {
            if (groundedGlowBerries.add(pos)) {
                newGlowBerries++;
            }
        }

        if (chatFeedback.get() && foundCount > 0) {
            if (useThreading.get() && limitChatSpam.get()) {
                if (newGlowBerries > 0) {
                    info("§aGlowBerryESP§f: Chunk %s,%s: §a%d new glow berries found", cpos.x, cpos.z, newGlowBerries);
                }
            } else {
                for (BlockPos pos : chunkGlowBerries) {
                    if (!groundedGlowBerries.contains(pos)) {
                        info("§aGlowBerryESP§f: Found glow berry at §a%s", pos.toShortString());
                    }
                }
            }
        }
    }

    private boolean isGroundedGlowBerry(BlockState state, BlockPos pos) {
        if (state.isOf(Blocks.GLOW_BERRY_BUSH)) {
            GlowBerryBlock glowBerryBlock = (GlowBerryBlock) state.getBlock();
            int age = state.get(GlowBerryBlock.AGE);
            return age == 7 && isGroundedVine(state, pos); // Fully grown glow berries
        }
        return false;
    }

    private boolean isGroundedGlowBerryChunk(WorldChunk chunk, BlockPos pos) {
        BlockState state = chunk.getBlockState(pos);
        if (state.isOf(Blocks.GLOW_BERRY_BUSH)) {
            GlowBerryBlock glowBerryBlock = (GlowBerryBlock) state.getBlock();
            int age = state.get(GlowBerryBlock.AGE);
            return age == 7 && isGroundedVineChunk(chunk, pos);
        }
        return false;
    }

    private int getGlowBerryLength(BlockPos start) {
        int length = 1;

        for (BlockPos current = start.up(); mc.world != null && mc.world.getBlockState(current).isOf(Blocks.VINE); current = current.up()) {
            length++;
        }

        return length;
    }

    private int getGlowBerryLengthChunk(WorldChunk chunk, BlockPos start) {
        int length = 1;

        for (BlockPos current = start.up(); chunk.getBlockState(current).isOf(Blocks.VINE); current = current.up()) {
            length++;
        }

        return length;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d playerPos = mc.player.getLerpedPos(event.tickDelta);
        Color sideColor = new Color(espColor.get());
        Color lineColor = new Color(espColor.get());
        Color tracerColorValue = new Color(tracerColor.get());

        for (BlockPos pos : groundedGlowBerries) {
            event.renderer.box(pos, sideColor, lineColor, shapeMode.get(), 0);

            if (showTracers.get()) {
                Vec3d blockCenter = Vec3d.ofCenter(pos);

                Vec3d startPos;
                if (mc.options.getPerspective().isFirstPerson()) {
                    Vec3d lookDirection = mc.player.getRotationVector();
                    startPos = new Vec3d(
                        playerPos.x + lookDirection.x * 0.5,
                        playerPos.y + mc.player.getEyeHeight(mc.player.getPose()) + lookDirection.y * 0.5,
                        playerPos.z + lookDirection.z * 0.5
                    );
                } else {
                    startPos = new Vec3d(
                        playerPos.x,
                        playerPos.y + mc.player.getEyeHeight(mc.player.getPose()),
                        playerPos.z
                    );
                }

                event.renderer.line(startPos.x, startPos.y, startPos.z,
                    blockCenter.x, blockCenter.y, blockCenter.z, tracerColorValue);
            }
        }
    }
}
