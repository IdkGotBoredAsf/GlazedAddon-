package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;

import java.util.*;

public class ChatModule extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ------------------ Settings ------------------
    private final Setting<Boolean> privateChat = sgGeneral.add(new BoolSetting.Builder()
            .name("private-chat")
            .description("Toggle private vs public chat.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> overlayChat = sgGeneral.add(new BoolSetting.Builder()
            .name("overlay-chat")
            .description("Display a chat box overlay in-game.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> showTimestamps = sgGeneral.add(new BoolSetting.Builder()
            .name("show-timestamps")
            .description("Show timestamps in messages.")
            .defaultValue(true)
            .build()
    );

    // ------------------ Internal State ------------------
    private final List<String> messageQueue = new ArrayList<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private int anonymousCounter = 1;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public ChatModule() {
        super(GlazedAddon.CATEGORY, "chat-module", "Anonymous chat module with overlay or normal Minecraft chat.");
    }

    // ------------------ Message Handling ------------------
    public void sendMessage(String message) {
        if (mc.player == null || message.isEmpty()) return;

        String formattedMessage = formatMessage(message);
        addMessageToQueue(mc.player.getUuid(), formattedMessage);

        if (!overlayChat.get()) {
            ChatUtils.info(playerPrefix(mc.player.getUuid()) + ": " + formattedMessage);
        }

        sendNetworkMessage(mc.player.getUuid(), formattedMessage);
    }

    private String formatMessage(String message) {
        if (showTimestamps.get()) {
            long timestamp = System.currentTimeMillis();
            return String.format("[%tH:%tM:%tS] %s", timestamp, timestamp, timestamp, message);
        }
        return message;
    }

    private void addMessageToQueue(UUID senderUUID, String message) {
        String prefix = privateChat.get() ? "[Private]" : "[Public]";
        String anonName = getAnonymousName(senderUUID);
        messageQueue.add(prefix + " " + anonName + ": " + message);
    }

    private String getAnonymousName(UUID uuid) {
        return playerNames.computeIfAbsent(uuid, k -> "Player" + anonymousCounter++);
    }

    private String playerPrefix(UUID uuid) {
        return getAnonymousName(uuid);
    }

    // ------------------ Overlay Rendering ------------------
    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!overlayChat.get() || messageQueue.isEmpty()) return;

        int y = 20;
        int maxMessages = 10;

        // Display latest messages only
        List<String> latest = messageQueue.size() > maxMessages ?
                messageQueue.subList(messageQueue.size() - maxMessages, messageQueue.size()) :
                messageQueue;

        for (String msg : latest) {
            // Fully compatible rendering in 1.21.4 using drawWithShadow
            mc.textRenderer.drawWithShadow(msg, 10f, (float) y, 0xFFFFFF);
            y += 12;
        }
    }

    // ------------------ Display Messages in Chat ------------------
    public void displayMessages() {
        for (String msg : messageQueue) ChatUtils.info(msg);
        messageQueue.clear();
    }

    // ------------------ Module Lifecycle ------------------
    @Override
    public void onActivate() {
        ChatUtils.info("💬 Chat Module Activated! Mode: " + (privateChat.get() ? "Private" : "Public"));
    }

    @Override
    public void onDeactivate() {
        ChatUtils.info("💬 Chat Module Deactivated!");
        messageQueue.clear();
        playerNames.clear();
        anonymousCounter = 1;
    }

    // ------------------ Networking Stub ------------------
    private void sendNetworkMessage(UUID senderUUID, String message) {
        // TODO: Replace with proper Fabric SimpleChannel networking
        // Currently simulates network by direct receive
        receiveNetworkMessage(senderUUID, message);
    }

    public void receiveNetworkMessage(UUID senderUUID, String message) {
        addMessageToQueue(senderUUID, message);
    }

    // ------------------ Extra Utilities ------------------
    public void clearMessages() {
        messageQueue.clear();
    }

    public List<String> getMessages() {
        return new ArrayList<>(messageQueue);
    }
}
