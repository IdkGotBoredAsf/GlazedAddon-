package com.nnpg.glazed.modules.troll;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.entity.living.LivingDeathEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;

public class AutoInsult extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> messages = sgGeneral.add(new StringListSetting.Builder()
        .name("messages")
        .description("Custom messages to send after kills. Use {player} to insert the killed player's name.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<Boolean> randomize = sgGeneral.add(new BoolSetting.Builder()
        .name("randomize")
        .description("Randomly pick a message from your list to send.")
        .defaultValue(true)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    public AutoInsult() {
        super(GlazedAddon.troll, "AutoInsult", "Sends custom funny messages after kills.");
    }

    @EventHandler
    private void onLivingDeath(LivingDeathEvent event) {
        if (mc.player == null || mc.world == null) return;

        // Only trigger for player deaths
        if (!(event.getEntity() instanceof PlayerEntity target)) return;

        // Only trigger if local player dealt the final hit
        if (event.getSource().getAttacker() != mc.player) return;

        List<String> customMessages = messages.get();
        if (customMessages.isEmpty()) return;

        String message = randomize.get()
                ? customMessages.get(random.nextInt(customMessages.size()))
                : customMessages.get(0);

        // Replace {player} placeholder
        message = message.replace("{player}", target.getEntityName());

        mc.player.networkHandler.sendChatMessage(message);
    }
}
