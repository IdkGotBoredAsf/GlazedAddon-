package com.nnpg.glazed.modules.troll;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.entity.player.KillEvent;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * AutoInsult - Sends custom funny messages after killing a player.
 */
public class AutoInsult extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> messages = sgGeneral.add(new StringListSetting.Builder()
        .name("messages")
        .description("Custom messages to send after kills.")
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
    private void onKill(KillEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.target instanceof net.minecraft.entity.player.PlayerEntity)) return;

        List<String> customMessages = messages.get();
        if (customMessages.isEmpty()) return;

        String messageToSend;
        if (randomize.get()) {
            messageToSend = customMessages.get(random.nextInt(customMessages.size()));
        } else {
            messageToSend = customMessages.get(0); // send the first message if randomize is false
        }

        mc.player.sendChatMessage(messageToSend);
    }
}
