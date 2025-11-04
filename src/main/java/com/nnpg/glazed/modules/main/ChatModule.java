package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.*;

public class ChatModule extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

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

    private final List<String> messageQueue = new ArrayList<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private int anonymousCounter = 1;

    public ChatModule() {
        super(GlazedAddon.CATEGORY, "chat-module", "Anonymous chat module with overlay or normal Minecraft chat.");
    }

    public void sendMessage(String message) {
        if (mc.player == null || message.isEmpty()) return;

        String formattedMessage = formatMessage(message);

        if (overlayChat.get()) {
            addMessageToQueue(mc.player.getUuid(), formattedMessage);
        } else {
            ChatUtils.info("[Private] " + formattedMessage);
        }

        // TODO: send to networked players
    }

    private String formatMessage(String message) {
        if (showTimestamps.get()) {
            long timestamp = System.currentTimeMillis();
            return String.format("[%tH:%tM:%tS] %s", timestamp, timestamp, timestamp, message);
        }
        return message;
    }

    private void addMessageToQueue(UUID senderUUID, String message) {
        String anonName = getAnonymousName(senderUUID);
        String prefix = privateChat.get() ? "[Private]" : "[Public]";
        messageQueue.add(prefix + " " + anonName + ": " + message);
    }

    private String getAnonymousName(UUID uuid) {
        return playerNames.computeIfAbsent(uuid, k -> "Player" + anonymousCounter++);
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (!overlayChat.get() || messageQueue.isEmpty()) return;

        int y = 20;
        int maxMessages = 10;
        List<String> latest = messageQueue.size() > maxMessages ?
            messageQueue.subList(messageQueue.size() - maxMessages, messageQueue.size()) :
            messageQueue;

        for (String msg : latest) {
            mc.textRenderer.drawWithShadow(msg, 10, y, 0xFFFFFF); // FIX: no MatrixStack, just String
            y += 12;
        }
    }

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
        playerNames.clear();
        anonymousCounter = 1;
    }

    public void receiveMessage(UUID senderUUID, String message) {
        addMessageToQueue(senderUUID, message);
    }
}
