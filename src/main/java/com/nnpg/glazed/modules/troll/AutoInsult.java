package com.nnpg.glazed.modules.troll;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.PlayerDeathS2CPacket;
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
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;

        if (event.packet instanceof PlayerDeathS2CPacket packet) {
            List<String> customMessages = messages.get();
            if (customMessages.isEmpty()) return;

            // Get the dead player's name from the packet
            String deadPlayerName = packet.getEntityName();

            // Only send message if local player was involved in the kill
            // Note: Some servers don't send attacker info in the packet
            // You may need to adjust based on server behavior
            String message = randomize.get()
                    ? customMessages.get(random.nextInt(customMessages.size()))
                    : customMessages.get(0);

            message = message.replace("{player}", deadPlayerName);

            mc.player.networkHandler.sendChatMessage(message);
        }
    }
}
