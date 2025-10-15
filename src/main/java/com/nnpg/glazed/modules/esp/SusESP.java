package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TraderLlamaEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SusESP - rewritten to be clean, performant, and focused on the requested detections.
 *
 * Notes:
 * - Only exposed settings are the detection toggles requested.
 * - Scans nearby chunks within a small radius each tick to avoid heavy CPU use.
 * - Draws a simple clean box ESP and a tracer that always appears to come from the crosshair.
 */
public class SusESP extends Module {
    private final SettingGroup sgDetect = settings.getDefaultGroup();

    // Detection toggles (the only settings exposed)
    private final Setting<Boolean> detectDripstone = sgDetect.add(new BoolSetting.Builder()
        .name("dripstone")
        .description("Detect dripstone formations 6–7 blocks long.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectVines = sgDetect.add(new BoolSetting.Builder()
        .name("vines")
        .description("Detect vines 9–11 blocks long.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectGlowBerries = sgDetect.add(new BoolSetting.Builder()
        .name("glow-berries")
        .description("Detect cave vines (glow berries) 8 blocks long.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectTrader = sgDetect.add(new BoolSetting.Builder()
        .name("wandering-trader")
        .description("Detect Wandering Trader with exactly 2 trader llamas.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectPillagers = sgDetect.add(new BoolSetting.Builder()
        .name("pillagers")
        .description("Detect pillagers in the area.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectCobbledDeepslate = sgDetect.add(new BoolSetting.Builder()
        .name("cobbled-deepslate")
        .description("Detect cobbled and related deepslate blocks between Y -64 and 45.")
        .defaultValue(true)
        .build()
    );

    // Internal caches (thread-safe)
    private final Map<BlockPos, Color> highlights = new ConcurrentHashMap<>();
    private final Set<Entity> suspiciousEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // constants tuned for safety / performance
    private static final int CHUNK_RADIUS = 4; // small radius to avoid large scans
    private static final int Y_MIN = -64;
    private static final int Y_MAX = 45;

    // colors (nice tones requested)
    private static final Color COLOR_VINES      = new Color(46, 204, 113, 180);  // nice green
    private static final Color COLOR_DRIPSTONE  = new Color(245, 245, 245, 200); // nice white
    private static final Color COLOR_GLOWBERRY  = new Color(255, 223, 70, 200);  // nice yellow
    private static final Color COLOR_TRADER     = new Color(93, 173, 226, 200);  // nice blue
    private static final Color COLOR_PILLAGER   = new Color(150, 75, 0, 200);    // nice brown
    private static final Color COLOR_DEEPSLATE  = new Color(120, 120, 140, 180); // subtle grey-blue

    public SusESP() {
        super(GlazedAddon.esp, "sus-esp", "Highlights suspicious formations and entities underground.");
    }

    @Override
    public void onActivate() {
        highlights.clear();
        suspiciousEntities.clear();
    }

    @Override
    public void onDeactivate() {
        highlights.clear();
        suspiciousEntities.clear();
    }

    /**
     * Tick-based scanning. Kept lightweight:
     * - only scans a small chunk radius around the player,
     * - only within Y_MIN..Y_MAX,
     * - uses a concurrent map for quick writes.
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        highlights.clear(); // refresh each tick to reflect world changes

        ClientPlayerEntity player = mc.player;
        int px = player.getBlockX();
        int pz = player.getBlockZ();

        // iterate nearby chunks (small radius)
        int centerChunkX = px >> 4;
        int centerChunkZ = pz >> 4;

        for (int cx = -CHUNK_RADIUS; cx <= CHUNK_RADIUS; cx++) {
            for (int cz = -CHUNK_RADIUS; cz <= CHUNK_RADIUS; cz++) {
                WorldChunk chunk = mc.world.getChunk(centerChunkX + cx, centerChunkZ + cz);
                if (chunk == null) continue;
                scanChunk(chunk);
            }
        }
    }

    /**
     * Scans the given chunk for target blocks in the allowed Y range and populates highlights map.
     * The scanning is intentionally conservative (no expensive operations per-block).
     */
    private void scanChunk(WorldChunk chunk) {
        // iterate blocks within chunk corners but clipped to Y_MIN..Y_MAX
        int startX = chunk.getPos().getStartX();
        int endX   = chunk.getPos().getEndX();
        int startZ = chunk.getPos().getStartZ();
        int endZ   = chunk.getPos().getEndZ();

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int y = Y_MIN; y <= Y_MAX; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (state == null) continue;
                    Block block = state.getBlock();

                    // Cobbled / related deepslate detection
                    if (detectCobbledDeepslate.get()) {
                        if (isDeepslateTarget(block)) {
                            highlights.put(pos.toImmutable(), COLOR_DEEPSLATE);
                            continue;
                        }
                    }

                    // Pointed dripstone detection (6-7 vertical pointed dripstone)
                    if (detectDripstone.get() && block == Blocks.POINTED_DRIPSTONE) {
                        int length = measureVerticalLength(pos, Blocks.POINTED_DRIPSTONE);
                        if (length >= 6 && length <= 7) {
                            // use white-ish color for dripstone but slightly warm so it's visible underground
                            highlights.put(pos.toImmutable(), COLOR_DRIPSTONE);
                            continue;
                        }
                    }

                    // Classic vines (hanging green vines)
                    if (detectVines.get() && block == Blocks.VINE) {
                        int length = measureVerticalLength(pos, Blocks.VINE);
                        if (length >= 9 && length <= 11) {
                            highlights.put(pos.toImmutable(), COLOR_VINES);
                            continue;
                        }
                    }

                    // Cave vines / glow berries -> two-block types can appear: CAVE_VINES_PLANT & CAVE_VINES
                    if (detectGlowBerries.get() &&
                        (block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT)) {
                        int length = measureVerticalLength(pos, Blocks.CAVE_VINES_PLANT, Blocks.CAVE_VINES);
                        if (length == 8) {
                            highlights.put(pos.toImmutable(), COLOR_GLOWBERRY);
                            continue;
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns true for cobbled deepslate and a small selection of rotated/polished deepslate variants
     * (covers typical "rotated/decorative" deepslate-like blocks users mean).
     */
    private boolean isDeepslateTarget(Block block) {
        return block == Blocks.COBBLED_DEEPSLATE
            || block == Blocks.POLISHED_DEEPSLATE
            || block == Blocks.DEEPSLATE_BRICKS
            || block == Blocks.DEEPSLATE_TILES
            || block == Blocks.DEEPSLATE; // keep a catch-all in case user expects raw deepslate variants
    }

    /**
     * Measures how many matching blocks extend downward from start (inclusive).
     * Stops after a safety cap to avoid infinite loops.
     */
    private int measureVerticalLength(BlockPos start, Block... matchBlocks) {
        int length = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable(start.getX(), start.getY(), start.getZ());
        for (int i = 0; i < 64; i++) {
            // stay within world bounds
            int ny = start.getY() - i;
            if (ny < Y_MIN) break;
            mutable.set(start.getX(), ny, start.getZ());
            Block block = mc.world.getBlockState(mutable).getBlock();
            boolean matched = false;
            for (Block m : matchBlocks) {
                if (block == m) {
                    length++;
                    matched = true;
                    break;
                }
            }
            if (!matched) break;
        }
        return length;
    }

    // Entity detection handlers
    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        Entity e = event.entity;
        if (detectPillagers.get() && e instanceof PillagerEntity) suspiciousEntities.add(e);

        if (detectTrader.get() && e instanceof WanderingTraderEntity trader) {
            // count trader llamas nearby (within 10 blocks)
            List<TraderLlamaEntity> llamas = trader.getWorld().getEntitiesByClass(
                TraderLlamaEntity.class, trader.getBoundingBox().expand(10), l -> true
            );
            if (llamas.size() == 2) suspiciousEntities.add(trader);
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        suspiciousEntities.remove(event.entity);
    }

    /**
     * Rendering: clean boxes + tracer lines that appear to originate from the crosshair.
     * Tracer start = eye + lookVec * smallOffset so it visually stays locked to center.
     */
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        // compute a tracer start point just in front of the camera (so it visually "sticks" to crosshair)
        Vec3d eye = mc.player.getCameraPosVec(event.tickDelta);
        Vec3d look = mc.player.getRotationVec(event.tickDelta); // direction camera is pointing
        Vec3d tracerStart = eye.add(look.normalize().multiply(0.5)); // 0.5 blocks in front of camera

        // Render block highlights
        for (Map.Entry<BlockPos, Color> e : highlights.entrySet()) {
            BlockPos pos = e.getKey();
            Color color = e.getValue();

            // draw a clean box (unit block) around the block position
            Box box = new Box(pos.getX(), pos.getY(), pos.getZ(),
                              pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);

            event.renderer.box(box, color, color, ShapeMode.Both, 1);

            // tracer from crosshair to block center
            Vec3d blockCenter = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            event.renderer.line(tracerStart.x, tracerStart.y, tracerStart.z,
                                blockCenter.x, blockCenter.y, blockCenter.z, color);
        }

        // Render entity ESP (pillagers / traders)
        for (Entity entity : suspiciousEntities) {
            if (entity == null || entity.isRemoved()) continue;

            Color color = (entity instanceof PillagerEntity) ? COLOR_PILLAGER : COLOR_TRADER;
            Box box = entity.getBoundingBox();

            event.renderer.box(box, color, color, ShapeMode.Both, 1);

            // tracer from crosshair to entity center
            Vec3d entCenter = new Vec3d(entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ());
            event.renderer.line(tracerStart.x, tracerStart.y, tracerStart.z,
                                entCenter.x, entCenter.y, entCenter.z, color);
        }
    }
}
