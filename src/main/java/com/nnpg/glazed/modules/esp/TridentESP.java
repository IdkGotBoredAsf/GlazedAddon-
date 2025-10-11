package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.renderer.ShapeMode;

import java.util.HashSet;
import java.util.Set;

public class TridentESP extends Module {

    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
            .name("tracers")
            .description("Draw tracers to mobs holding a trident")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> tracerColor = sgGeneral.add(new ColorSetting.Builder()
            .name("tracer-color")
            .description("Color of the tracers")
            .defaultValue(new SettingColor(0, 255, 255, 200))
            .visible(tracers::get)
            .build()
    );

    private final Setting<ShapeMode> boxShapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
            .name("box-mode")
            .description("Render mode for box around mobs")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> boxColor = sgGeneral.add(new ColorSetting.Builder()
            .name("box-color")
            .description("Color of the box around mobs")
            .defaultValue(new SettingColor(255, 0, 0, 80))
            .build()
    );

    private final Set<LivingEntity> tridentMobs = new HashSet<>();

    public TridentESP() {
        super(GlazedAddon.esp, "trident-esp", "Highlights mobs holding a trident with tracers and boxes.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d playerPos = mc.player.getCameraPosVec(event.tickDelta);
        tridentMobs.clear();

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;

            // Check if holding trident
            if (living.getMainHandStack().isOf(Items.TRIDENT) || living.getOffHandStack().isOf(Items.TRIDENT)) {
                tridentMobs.add(living);
            }
        }

        for (LivingEntity mob : tridentMobs) {
            // Draw box
            event.renderer.box(mob.getBoundingBox(), new Color(boxColor.get()), new Color(boxColor.get()), boxShapeMode.get(), 0);

            // Draw tracer
            if (tracers.get()) {
                Vec3d mobPos = mob.getPos().add(0, mob.getHeight() / 2.0, 0);
                Vec3d startPos = mc.player.getCameraPosVec(event.tickDelta);
                event.renderer.line(startPos.x, startPos.y, startPos.z, mobPos.x, mobPos.y, mobPos.z, new Color(tracerColor.get()));
            }
        }
    }
}
