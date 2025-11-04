package com.nnpg.glazed.modules.troll;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;

import java.util.*;

/**
 * AutoInsult - Sends custom funny messages after killing a player.
 * Supports {player} placeholder in messages.
 * Fully compatible with MC 1.21.4 + Meteor Client 1.21.4-42.
 */
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
    private final Set<UUID> deadPlayers = new HashSet<>();

    public AutoInsult() {
        super(GlazedAddon.troll, "AutoInsult", "Sends custom funny messages after kills.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            // Player is dead and not already tracked
            if (player.getHealth() <= 0 && !deadPlayers.contains(player.getUuid())) {

                // Only send message if you dealt last damage
                if (player.getRecentDamageSource() != null && player.getRecentDamageSource().getAttacker() == mc.player) {

                    List<String> customMessages = messages.get();
                    if (!customMessages.isEmpty()) {
                        String message = randomize.get()
                                ? customMessages.get(random.nextInt(customMessages.size()))
                                : customMessages.get(0);

                        // Replace {player} placeholder with actual player name
                        message = message.replace("{player}", player.getName().getString());

                        // Send message safely using Meteor's ChatUtils
                        ChatUtils.sendPlayerMsg(message);
                    }

                    deadPlayers.add(player.getUuid());
                }
            } else if (player.getHealth() > 0) {
                deadPlayers.remove(player.getUuid());
            }
        }
    }
}
