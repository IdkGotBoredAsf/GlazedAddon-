package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

public class TridentESP extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to mobs holding a trident.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color of the tracers.")
        .defaultValue(new SettingColor(0, 255, 255, 200))
        .visible(tracers::get)
        .build()
    );

    private final Setting<ShapeMode> boxShapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("box-mode")
        .description("Render mode for the box around mobs.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> boxColor = sgGeneral.add(new ColorSetting.Builder()
        .name("box-color")
        .description("Color of the box around mobs.")
        .defaultValue(new SettingColor(255, 0, 0, 80))
        .build()
    );

    private final Set<LivingEntity> tridentMobs = new HashSet<>();

    public TridentESP() {
        super(GlazedAddon.esp, "trident-esp", "Highlights mobs holding a trident with tracers and boxes.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        // 🧠 Prevent crashes: skip if the game or player isn’t ready
        if (mc.player == null || mc.world == null) return;
        if (mc.player.age < 5) return; // Wait a few ticks after world load

        tridentMobs.clear();

        // ✅ Safely iterate through entities
        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;

            // Check if holding a trident in main or off-hand
            if (living.getMainHandStack().isOf(Items.TRIDENT) || living.getOffHandStack().isOf(Items.TRIDENT)) {
                tridentMobs.add(living);
            }
        }

        // ✅ Draw ESP visuals
        for (LivingEntity mob : tridentMobs) {
            // Box around entity
            event.renderer.box(mob.getBoundingBox(),
                new Color(boxColor.get()),
                new Color(boxColor.get()),
                boxShapeMode.get(),
                0
            );

            // Tracer from player to mob center
            if (tracers.get()) {
                Vec3d mobPos = mob.getPos().add(0, mob.getHeight() / 2.0, 0);
                Vec3d startPos = mc.player.getCameraPosVec(event.tickDelta);
                event.renderer.line(
                    startPos.x, startPos.y, startPos.z,
                    mobPos.x, mobPos.y, mobPos.z,
                    new Color(tracerColor.get())
                );
            }
        }
    }
}
