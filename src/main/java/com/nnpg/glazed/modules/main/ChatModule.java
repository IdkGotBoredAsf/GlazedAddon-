package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;

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
            .description("Display chat overlay in-game.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> showTimestamps = sgGeneral.add(new BoolSetting.Builder()
            .name("show-timestamps")
            .description("Show timestamps in chat messages.")
            .defaultValue(true)
            .build()
    );

    private final List<String> messageQueue = new ArrayList<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private int anonymousCounter = 1;

    public ChatModule() {
        super(GlazedAddon.CATEGORY, "chat-module", "Anonymous chat module for mod users with overlay or standard chat.");
    }

    // ------------------ Message Handling ------------------

    public void sendMessage(String message) {
        if (mc.player == null || message.isEmpty()) return;

        String formatted = formatMessage(message);
        addMessageToQueue(mc.player.getUuid(), formatted);

        if (!overlayChat.get()) {
            ChatUtils.info(playerPrefix(mc.player.getUuid()) + ": " + formatted);
        }

        sendNetworkMessage(mc.player.getUuid(), formatted);
    }

    private String formatMessage(String message) {
        if (showTimestamps.get()) {
            long ts = System.currentTimeMillis();
            return String.format("[%tH:%tM:%tS] %s", ts, ts, ts, message);
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

        List<String> latest = messageQueue.size() > maxMessages
                ? messageQueue.subList(messageQueue.size() - maxMessages, messageQueue.size())
                : messageQueue;

        for (String msg : latest) {
            mc.textRenderer.drawWithShadow(Text.literal(msg), 10f, (float) y, 0xFFFFFF);
            y += 12;
        }
    }

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
        // Placeholder: Replace with proper Fabric networking if desired
        receiveNetworkMessage(senderUUID, message);
    }

    public void receiveNetworkMessage(UUID senderUUID, String message) {
        addMessageToQueue(senderUUID, message);
    }
}
