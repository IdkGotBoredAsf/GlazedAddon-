package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.passive.TraderLlamaEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SusESP - finished version with:
 *  - tracers locked to crosshair (clean)
 *  - dripstone detection only for >= 10 blocks
 *  - optional detection for oddly rotated deepslate (sideways/upside-down)
 *  - full highlighting of vine groups (normal + cave vines)
 *  - one tracer per connected block group
 *  - chat notifications on first detection of each type
 *
 * Compatible with Meteor 1.21.4 / Minecraft 1.21.4 (typical usage).
 */
public class SusESP extends Module {
    private final SettingGroup sgDetect = settings.getDefaultGroup();

    private final Setting<Boolean> detectDripstone = sgDetect.add(new BoolSetting.Builder()
            .name("dripstone")
            .description("Detect dripstone formations 10+ blocks long.")
            .defaultValue(true).build());

    private final Setting<Boolean> detectVines = sgDetect.add(new BoolSetting.Builder()
            .name("vines")
            .description("Detect vines 9–11 blocks long.")
            .defaultValue(true).build());

    private final Setting<Boolean> detectGlowBerries = sgDetect.add(new BoolSetting.Builder()
            .name("glow-berries")
            .description("Detect cave vines 8 blocks long.")
            .defaultValue(true).build());

    private final Setting<Boolean> detectTrader = sgDetect.add(new BoolSetting.Builder()
            .name("wandering-trader")
            .description("Detect Wandering Trader with 2 trader llamas.")
            .defaultValue(true).build());

    private final Setting<Boolean> detectPillagers = sgDetect.add(new BoolSetting.Builder()
            .name("pillagers")
            .description("Detect pillagers in the area.")
            .defaultValue(true).build());

    private final Setting<Boolean> detectCobbledDeepslate = sgDetect.add(new BoolSetting.Builder()
            .name("cobbled-deepslate")
            .description("Detect cobbled & related deepslate blocks between Y -64 and 45.")
            .defaultValue(true).build());

    // NEW: toggle for rotated/oddly-placed deepslate detection
    private final Setting<Boolean> detectRotatedDeepslate = sgDetect.add(new BoolSetting.Builder()
            .name("rotated-deepslate")
            .description("Also detect oddly rotated deepslate (sideways / upside-down).")
            .defaultValue(true).build());

    private final Map<BlockPos, Color> highlights = new ConcurrentHashMap<>();
    private final Set<Entity> suspiciousEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final int CHUNK_RADIUS = 3;
    private static final int Y_MIN = -64, Y_MAX = 45;

    // slightly more transparent colours
    private static final Color COLOR_VINES = new Color(46, 204, 113, 110);
    private static final Color COLOR_DRIPSTONE = new Color(245, 245, 245, 110);
    private static final Color COLOR_GLOWBERRY = new Color(255, 223, 70, 110);
    private static final Color COLOR_TRADER = new Color(93, 173, 226, 140);
    private static final Color COLOR_PILLAGER = new Color(150, 75, 0, 140);
    private static final Color COLOR_DEEPSLATE = new Color(120, 120, 140, 110);

    // ensure we only send one chat message per detected type until module toggled or restarted
    private final Set<String> notified = Collections.synchronizedSet(new HashSet<>());

    public SusESP() {
        super(GlazedAddon.esp, "sus-esp", "Highlights suspicious formations and entities underground.");
    }

    @Override
    public void onActivate() {
        highlights.clear();
        suspiciousEntities.clear();
        notified.clear();
    }

    @Override
    public void onDeactivate() {
        highlights.clear();
        suspiciousEntities.clear();
        notified.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        highlights.clear();

        ClientPlayerEntity player = mc.player;
        int px = player.getBlockX(), pz = player.getBlockZ();
        int centerChunkX = px >> 4, centerChunkZ = pz >> 4;

        for (int cx = -CHUNK_RADIUS; cx <= CHUNK_RADIUS; cx++) {
            for (int cz = -CHUNK_RADIUS; cz <= CHUNK_RADIUS; cz++) {
                WorldChunk chunk = mc.world.getChunk(centerChunkX + cx, centerChunkZ + cz);
                if (chunk != null) scanChunk(chunk);
            }
        }
    }

    private void scanChunk(WorldChunk chunk) {
        int startX = chunk.getPos().getStartX();
        int endX = chunk.getPos().getEndX();
        int startZ = chunk.getPos().getStartZ();
        int endZ = chunk.getPos().getEndZ();

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int y = Y_MIN; y <= Y_MAX; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    Block block = state.getBlock();

                    // cobbled / general deepslate detection
                    if (detectCobbledDeepslate.get() && isDeepslateTarget(block)) {
                        mark(pos, COLOR_DEEPSLATE, "Cobbled/Deepslate");
                    }

                    // optionally detect rotated / oddly-placed deepslate
                    if (detectRotatedDeepslate.get() && block != null) {
                        String key = block.getTranslationKey().toLowerCase();
                        if (key.contains("deepslate")) {
                            if (isOddlyRotated(state)) mark(pos, COLOR_DEEPSLATE, "Rotated Deepslate");
                        }
                    }

                    // dripstone -> now only detect 10+ long
                    if (detectDripstone.get() && block == Blocks.POINTED_DRIPSTONE) {
                        int len = measureVerticalLength(pos, Blocks.POINTED_DRIPSTONE);
                        if (len >= 10) mark(pos, COLOR_DRIPSTONE, "Long Dripstone (" + len + ")");
                    }

                    // vines
                    if (detectVines.get() && block == Blocks.VINE) {
                        int len = measureVerticalLength(pos, Blocks.VINE);
                        if (len >= 9 && len <= 11) mark(pos, COLOR_VINES, "Vines");
                        else if (len >= 9) mark(pos, COLOR_VINES, "Long Vines (" + len + ")");
                    }

                    // cave vines / glow berries
                    if (detectGlowBerries.get() && (block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT)) {
                        int len = measureVerticalLength(pos, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT);
                        if (len == 8) mark(pos, COLOR_GLOWBERRY, "Glow Berry Vines");
                        else if (len > 8) mark(pos, COLOR_GLOWBERRY, "Glow Berry Vines (" + len + ")");
                    }
                }
            }
        }
    }

    private void mark(BlockPos pos, Color color, String name) {
        highlights.put(pos, color);
        if (notified.add(name)) ChatUtils.info("§b[SusESP] §fDetected " + name + " nearby!");
    }

    private boolean isDeepslateTarget(Block block) {
        if (block == null) return false;
        // common deepslate-esque blocks
        return block == Blocks.COBBLED_DEEPSLATE
                || block == Blocks.POLISHED_DEEPSLATE
                || block == Blocks.DEEPSLATE_BRICKS
                || block == Blocks.DEEPSLATE_TILES
                || block == Blocks.DEEPSLATE;
    }

    /**
     * Rough heuristic for "odd rotation":
     * - axis property exists and is not Y => sideways
     * - stairs/slabs with TOP/UPPER half => upside-down
     * - other properties may be checked using Properties constants
     *
     * This is intentionally conservative and checks common state props only.
     */
    private boolean isOddlyRotated(BlockState state) {
        try {
            if (state.contains(Properties.AXIS)) {
                Direction.Axis axis = state.get(Properties.AXIS);
                if (axis != Direction.Axis.Y) return true;
            }

            if (state.contains(Properties.BLOCK_HALF)) {
                DoubleBlockHalf half = state.get(Properties.BLOCK_HALF);
                // UPPER means upside-down for stairs-like blocks
                if (half == DoubleBlockHalf.UPPER) return true;
            }

            if (state.contains(Properties.SLAB_TYPE)) {
                SlabType st = state.get(Properties.SLAB_TYPE);
                if (st == SlabType.TOP) return true;
            }

            // If the block has a facing and it's up/down it's unusual for a deepslate block
            if (state.contains(Properties.FACING)) {
                Direction facing = state.get(Properties.FACING);
                if (facing == Direction.UP || facing == Direction.DOWN) return true;
            }
        } catch (Exception ignored) {
            // be extremely defensive — if any of the property queries fail, don't crash;
            // ignore and return false (no odd rotation detected)
        }

        return false;
    }

    private int measureVerticalLength(BlockPos start, Block... matchBlocks) {
        int len = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable(start.getX(), start.getY(), start.getZ());
        for (int i = 0; i < 128; i++) {
            int ny = start.getY() - i;
            if (ny < Y_MIN) break;
            mutable.set(start.getX(), ny, start.getZ());
            Block block = mc.world.getBlockState(mutable).getBlock();
            boolean match = false;
            for (Block m : matchBlocks) {
                if (block == m) {
                    len++;
                    match = true;
                    break;
                }
            }
            if (!match) break;
        }
        return len;
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        Entity e = event.entity;
        if (detectPillagers.get() && e instanceof PillagerEntity) {
            suspiciousEntities.add(e);
            ChatUtils.info("§b[SusESP] §fDetected Pillager!");
        }

        if (detectTrader.get() && e instanceof WanderingTraderEntity trader) {
            List<TraderLlamaEntity> llamas = trader.getWorld().getEntitiesByClass(
                    TraderLlamaEntity.class, trader.getBoundingBox().expand(32), l -> true);
            if (llamas.size() == 2) {
                suspiciousEntities.add(trader);
                ChatUtils.info("§b[SusESP] §fDetected Wandering Trader with 2 Llamas!");
            }
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        suspiciousEntities.remove(event.entity);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        // Anchor tracer to camera crosshair by using eye pos + small forward offset
        Vec3d eye = mc.player.getCameraPosVec(event.tickDelta);
        Vec3d look = mc.player.getRotationVec(event.tickDelta);
        Vec3d tracerStart = eye.add(look.normalize().multiply(0.09)); // tiny forward offset keeps it visually locked

        // Single tracer per connected block group:
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos pos : highlights.keySet()) {
            if (visited.contains(pos)) continue;
            Color color = highlights.get(pos);
            List<BlockPos> group = new ArrayList<>();
            floodFill(pos, color, group, visited);

            // draw full connected group (each block)
            for (BlockPos p : group) {
                Box b = new Box(p.getX(), p.getY(), p.getZ(), p.getX() + 1.0, p.getY() + 1.0, p.getZ() + 1.0);
                event.renderer.box(b, color, color, ShapeMode.Both, 1);
            }

            // single tracer to group center
            double xSum = 0, ySum = 0, zSum = 0;
            for (BlockPos p : group) { xSum += p.getX() + 0.5; ySum += p.getY() + 0.5; zSum += p.getZ() + 0.5; }
            Vec3d center = new Vec3d(xSum / group.size(), ySum / group.size(), zSum / group.size());
            event.renderer.line(tracerStart.x, tracerStart.y, tracerStart.z, center.x, center.y, center.z, color);
        }

        // Entities (pillagers / traders + llamas)
        for (Entity e : suspiciousEntities) {
            if (e == null || e.isRemoved()) continue;
            Color color = (e instanceof PillagerEntity) ? COLOR_PILLAGER : COLOR_TRADER;
            Box box = e.getBoundingBox();
            event.renderer.box(box, color, color, ShapeMode.Both, 2);

            Vec3d center = box.getCenter();
            event.renderer.line(tracerStart.x, tracerStart.y, tracerStart.z, center.x, center.y, center.z, color);

            if (e instanceof WanderingTraderEntity trader) {
                List<TraderLlamaEntity> llamas = trader.getWorld().getEntitiesByClass(
                        TraderLlamaEntity.class, trader.getBoundingBox().expand(32), l -> true);
                for (TraderLlamaEntity llama : llamas) {
                    Box lBox = llama.getBoundingBox();
                    event.renderer.box(lBox, color, color, ShapeMode.Both, 2);
                }
            }
        }
    }

    private void floodFill(BlockPos start, Color color, List<BlockPos> group, Set<BlockPos> visited) {
        Queue<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        while (!q.isEmpty()) {
            BlockPos pos = q.poll();
            Color c = highlights.get(pos);
            if (pos == null || visited.contains(pos) || c == null || !c.equals(color)) continue;
            visited.add(pos);
            group.add(pos);

            for (BlockPos off : Arrays.asList(pos.add(1,0,0), pos.add(-1,0,0), pos.add(0,1,0), pos.add(0,-1,0), pos.add(0,0,1), pos.add(0,0,-1))) {
                q.add(off);
            }
        }
    }
}
