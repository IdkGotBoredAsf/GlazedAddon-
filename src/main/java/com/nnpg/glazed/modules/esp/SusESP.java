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
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.passive.TraderLlamaEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SusESP extends Module {
    private final SettingGroup sgDetect = settings.getDefaultGroup();

    private final Setting<Boolean> detectDripstone = sgDetect.add(new BoolSetting.Builder()
            .name("dripstone").description("Detect dripstone formations 6–7 blocks long.")
            .defaultValue(true).build());
    private final Setting<Boolean> detectVines = sgDetect.add(new BoolSetting.Builder()
            .name("vines").description("Detect vines 9–11 blocks long.")
            .defaultValue(true).build());
    private final Setting<Boolean> detectGlowBerries = sgDetect.add(new BoolSetting.Builder()
            .name("glow-berries").description("Detect cave vines 8 blocks long.")
            .defaultValue(true).build());
    private final Setting<Boolean> detectTrader = sgDetect.add(new BoolSetting.Builder()
            .name("wandering-trader").description("Detect Wandering Trader with 2 trader llamas.")
            .defaultValue(true).build());
    private final Setting<Boolean> detectPillagers = sgDetect.add(new BoolSetting.Builder()
            .name("pillagers").description("Detect pillagers in the area.")
            .defaultValue(true).build());
    private final Setting<Boolean> detectCobbledDeepslate = sgDetect.add(new BoolSetting.Builder()
            .name("cobbled-deepslate").description("Detect cobbled & rotated deepslate between Y -64 and 45.")
            .defaultValue(true).build());

    private final Map<BlockPos, Color> highlights = new ConcurrentHashMap<>();
    private final Set<Entity> suspiciousEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final int CHUNK_RADIUS = 3;
    private static final int Y_MIN = -64, Y_MAX = 45;

    private static final Color COLOR_VINES = new Color(46, 204, 113, 120);
    private static final Color COLOR_DRIPSTONE = new Color(245, 245, 245, 120);
    private static final Color COLOR_GLOWBERRY = new Color(255, 223, 70, 120);
    private static final Color COLOR_TRADER = new Color(93, 173, 226, 120);
    private static final Color COLOR_PILLAGER = new Color(150, 75, 0, 120);
    private static final Color COLOR_DEEPSLATE = new Color(120, 120, 140, 120);

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
                    Block block = chunk.getBlockState(pos).getBlock();

                    if (detectCobbledDeepslate.get() && isDeepslateTarget(block))
                        mark(pos, COLOR_DEEPSLATE, "Cobbled Deepslate");

                    if (detectDripstone.get() && block == Blocks.POINTED_DRIPSTONE) {
                        int len = measureVerticalLength(pos, Blocks.POINTED_DRIPSTONE);
                        if (len >= 6 && len <= 7) mark(pos, COLOR_DRIPSTONE, "Dripstone Formation");
                    }

                    if (detectVines.get() && block == Blocks.VINE) {
                        int len = measureVerticalLength(pos, Blocks.VINE);
                        if (len >= 9 && len <= 11) mark(pos, COLOR_VINES, "Vines");
                    }

                    if (detectGlowBerries.get() && (block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT)) {
                        int len = measureVerticalLength(pos, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT);
                        if (len == 8) mark(pos, COLOR_GLOWBERRY, "Glow Berry Vines");
                    }
                }
            }
        }
    }

    private void mark(BlockPos pos, Color color, String name) {
        highlights.put(pos, color);
        if (notified.add(name))
            ChatUtils.info("§b[SusESP] §fDetected " + name + " nearby!");
    }

    private boolean isDeepslateTarget(Block block) {
        return block == Blocks.COBBLED_DEEPSLATE || block == Blocks.POLISHED_DEEPSLATE ||
               block == Blocks.DEEPSLATE_BRICKS || block == Blocks.DEEPSLATE_TILES ||
               block == Blocks.DEEPSLATE;
    }

    private int measureVerticalLength(BlockPos start, Block... matchBlocks) {
        int len = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable(start.getX(), start.getY(), start.getZ());
        for (int i = 0; i < 64; i++) {
            int ny = start.getY() - i;
            if (ny < Y_MIN) break;
            mutable.set(start.getX(), ny, start.getZ());
            Block block = mc.world.getBlockState(mutable).getBlock();
            boolean match = false;
            for (Block m : matchBlocks)
                if (block == m) {
                    len++;
                    match = true;
                    break;
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

        Vec3d eye = mc.player.getCameraPosVec(event.tickDelta);
        Vec3d look = mc.player.getRotationVec(event.tickDelta);
        Vec3d tracerStart = eye.add(look.normalize().multiply(0.1)); // center of crosshair

        Set<BlockPos> visited = new HashSet<>();

        for (BlockPos pos : highlights.keySet()) {
            if (visited.contains(pos)) continue;
            Color color = highlights.get(pos);
            List<BlockPos> group = new ArrayList<>();
            floodFill(pos, color, group, visited);

            double minX = group.stream().mapToDouble(BlockPos::getX).min().orElse(pos.getX());
            double maxX = group.stream().mapToDouble(BlockPos::getX).max().orElse(pos.getX());
            double minY = group.stream().mapToDouble(BlockPos::getY).min().orElse(pos.getY());
            double maxY = group.stream().mapToDouble(BlockPos::getY).max().orElse(pos.getY());
            double minZ = group.stream().mapToDouble(BlockPos::getZ).min().orElse(pos.getZ());
            double maxZ = group.stream().mapToDouble(BlockPos::getZ).max().orElse(pos.getZ());

            Box box = new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
            event.renderer.box(box, color, color, ShapeMode.Both, 1);
            Vec3d center = new Vec3d((minX + maxX + 1) / 2, (minY + maxY + 1) / 2, (minZ + maxZ + 1) / 2);
            event.renderer.line(tracerStart.x, tracerStart.y, tracerStart.z, center.x, center.y, center.z, color);
        }

        for (Entity e : suspiciousEntities) {
            if (e == null || e.isRemoved()) continue;
            Color color = (e instanceof PillagerEntity) ? COLOR_PILLAGER : COLOR_TRADER;
            Box box = e.getBoundingBox();
            event.renderer.box(box, color, color, ShapeMode.Both, 2);
            Vec3d center = e.getBoundingBox().getCenter();
            event.renderer.line(tracerStart.x, tracerStart.y, tracerStart.z, center.x, center.y, center.z, color);
        }
    }

    private void floodFill(BlockPos start, Color color, List<BlockPos> group, Set<BlockPos> visited) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (visited.contains(pos) || !highlights.getOrDefault(pos, Color.BLACK).equals(color)) continue;
            visited.add(pos);
            group.add(pos);
            for (BlockPos offset : Arrays.asList(
                    pos.add(1, 0, 0), pos.add(-1, 0, 0),
                    pos.add(0, 1, 0), pos.add(0, -1, 0),
                    pos.add(0, 0, 1), pos.add(0, 0, -1))) {
                queue.add(offset);
            }
        }
    }
}
