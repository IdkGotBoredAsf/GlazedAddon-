package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayList;
import java.util.List;

public class ChatModule extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> privateChat = sgGeneral.add(new BoolSetting.Builder()
        .name("private-chat")
        .description("Toggle between private chat and public chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showTimestamps = sgGeneral.add(new BoolSetting.Builder()
        .name("show-timestamps")
        .description("Display timestamps before messages.")
        .defaultValue(true)
        .build()
    );

    private final List<String> messageQueue = new ArrayList<>();

    public ChatModule() {
        super(GlazedAddon.CATEGORY, "chat-module", "A module for game chat with private/public mode without revealing player names.");
    }

    /**
     * Send a message through the module
     * @param message The message content
     */
    public void sendMessage(String message) {
        if (mc.player == null) return;

        String formattedMessage = formatMessage(message);
        if (privateChat.get()) {
            sendPrivateMessage(formattedMessage);
        } else {
            sendPublicMessage(formattedMessage);
        }
    }

    /**
     * Formats the message, adds timestamp if enabled
     */
    private String formatMessage(String message) {
        if (showTimestamps.get()) {
            long timestamp = System.currentTimeMillis();
            return String.format("[%tH:%tM:%tS] %s", timestamp, timestamp, timestamp, message);
        }
        return message;
    }

    /**
     * Sends the message in private mode
     * Hides player names, only shows "Player" as sender
     */
    private void sendPrivateMessage(String message) {
        // Only show the message locally to self, or to trusted recipients via some system
        // Here we'll just display it in your chat
        ChatUtils.info("Player: " + message);
        messageQueue.add("[Private] " + message);
    }

    /**
     * Sends the message in public mode
     * Player names are anonymized
     */
    private void sendPublicMessage(String message) {
        // Display message in chat without revealing the sender's real name
        ChatUtils.info("Player: " + message);
        messageQueue.add("[Public] " + message);
    }

    /**
     * Display all queued messages
     */
    public void displayMessages() {
        for (String msg : messageQueue) {
            ChatUtils.info(msg);
        }
        messageQueue.clear();
    }

    @Override
    public void onActivate() {
        ChatUtils.info("💬 Chat Module Activated! Mode: " + (privateChat.get() ? "Private" : "Public"));
    }

    @Override
    public void onDeactivate() {
        ChatUtils.info("💬 Chat Module Deactivated!");
        messageQueue.clear();
    }
}
