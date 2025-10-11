package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;

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
        super(GlazedAddon.misc, "FakeStats", "Display fake stats on scoreboard (client-side only).");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        DrawContext drawContext = event.drawContext;

        // Example coordinates, adjust X/Y as needed to overlay the scoreboard
        int x = 5;
        int y = 5;
        int spacing = 12;

        drawContext.drawText(event.textRenderer, "Kills: " + kills.get(), x, y, 0xFFFFFF, false);
        drawContext.drawText(event.textRenderer, "Deaths: " + deaths.get(), x, y + spacing, 0xFFFFFF, false);
        drawContext.drawText(event.textRenderer, "Coins: " + coins.get(), x, y + spacing * 2, 0xFFFFFF, false);
    }
}
