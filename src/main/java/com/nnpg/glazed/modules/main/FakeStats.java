package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.scoreboard.*;

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

    private ScoreboardObjective savedObjective = null; // Original sidebar
    private ScoreboardObjective fakeObjective = null;  // Our fake scoreboard

    public FakeStats() {
        super(GlazedAddon.misc, "fake-stats", "Display fake stats on the scoreboard (client-side only).");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null) return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective current = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);

        // Save original scoreboard once
        if (current != null && savedObjective == null) savedObjective = current;

        // Create fake scoreboard if it doesn't exist
        if (fakeObjective == null) {
            fakeObjective = scoreboard.addObjective("fakeStats", ScoreboardCriterion.DUMMY, new LiteralText("Stats"), ScoreboardCriterion.RenderType.INTEGER);
        }

        // Update fake values
        setScore(scoreboard, "Kills", kills.get());
        setScore(scoreboard, "Deaths", deaths.get());
        setScore(scoreboard, "Coins", coins.get());

        // Display our fake scoreboard
        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, fakeObjective);
    }

    private void setScore(Scoreboard scoreboard, String name, int value) {
        Score score = scoreboard.getOrCreateScore(name, fakeObjective);
        score.setScore(value);
    }

    @Override
    public void onDeactivate() {
        if (mc.world == null) return;

        Scoreboard scoreboard = mc.world.getScoreboard();

        // Restore original sidebar
        if (savedObjective != null) {
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, savedObjective);
            savedObjective = null;
        }

        // Remove fake objective so it doesn't persist
        if (fakeObjective != null) {
            scoreboard.removeObjective(fakeObjective);
            fakeObjective = null;
        }
    }
}
