package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.font.TextRenderer;

public class FakeStats extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<Integer> kills = sgGeneral.add(new IntSetting.Builder()
        .name("kills")
        .description("Fake kills count")
        .defaultValue(100)
        .min(0)
        .max(9999)
        .build()
    );

    private final Setting<Integer> deaths = sgGeneral.add(new IntSetting.Builder()
        .name("deaths")
        .description("Fake deaths count")
        .defaultValue(5)
        .min(0)
        .max(9999)
        .build()
    );

    private final Setting<Integer> coins = sgGeneral.add(new IntSetting.Builder()
        .name("coins")
        .description("Fake coins count")
        .defaultValue(1000)
        .min(0)
        .max(999999)
        .build()
    );

    public FakeStats() {
        super(GlazedAddon.main, "FakeStats", "Display fake stats on scoreboard (client-side only)."); 
        // Replace GlazedAddon.main with whatever Category your addon has, or create one
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        TextRenderer textRenderer = mc.textRenderer; // Fixed: use client textRenderer
        int x = 5;
        int y = 5;
        int spacing = 12;

        textRenderer.draw(event.matrixStack, "Kills: " + kills.get(), x, y, 0xFFFFFF);
        textRenderer.draw(event.matrixStack, "Deaths: " + deaths.get(), x, y + spacing, 0xFFFFFF);
        textRenderer.draw(event.matrixStack, "Coins: " + coins.get(), x, y + spacing * 2, 0xFFFFFF);
    }
}
