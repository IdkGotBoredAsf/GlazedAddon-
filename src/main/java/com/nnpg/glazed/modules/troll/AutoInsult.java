package com.nnpg.glazed.modules.troll;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;

/**
 * AutoInsult - Sends custom funny messages after killing a player.
 * Fully compatible with Meteor Client 1.21.4-42 and MC 1.21.4
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

    // Track dead players to prevent multiple messages
    private final Set<UUID> deadPlayers = new HashSet<>();

    public AutoInsult() {
        super(GlazedAddon.troll, "AutoInsult", "Sends custom funny messages after kills.");
    }

    // Remove @Override — onTick is called by Module automatically
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue; // Skip yourself

            // If player is dead and not already tracked
            if (player.getHealth() <= 0 && !deadPlayers.contains(player.getUuid())) {
                // Only send message if you dealt last damage
                if (player.getRecentDamageSource() != null &&
                    player.getRecentDamageSource().getAttacker() == mc.player) {

                    List<String> customMessages = messages.get();
                    if (!customMessages.isEmpty()) {
                        String message = randomize.get()
                                ? customMessages.get(random.nextInt(customMessages.size()))
                                : customMessages.get(0);

                        // Correct way to send chat in MC 1.21.4
                        mc.player.networkHandler.sendChatMessage(message);
                    }

                    deadPlayers.add(player.getUuid());
                }
            } else if (player.getHealth() > 0) {
                // Remove from deadPlayers if they respawned
                deadPlayers.remove(player.getUuid());
            }
        }
    }
}
