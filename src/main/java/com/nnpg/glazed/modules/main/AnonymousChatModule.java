package com.glazedaddon.modules;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AnonymousChatModule implements ClientModInitializer {

    // Module settings
    private boolean overlayEnabled = true;
    private boolean privateModeEnabled = true;
    private boolean timestampsEnabled = true;
    private int overlayMessageLimit = 10;

    // Anonymous usernames
    private final Map<UUID, String> anonymousNames = new HashMap<>();
    private int anonCounter = 1;

    // Message queue
    private final Queue<ChatMessage> messageQueue = new ConcurrentLinkedQueue<>();

    // Minecraft instance
    private final MinecraftClient client = MinecraftClient.getInstance();

    @Override
    public void onInitializeClient() {
        sendChatInfo("Anonymous Chat Module enabled!");
    }

    // Activation
    public void activate() {
        sendChatInfo("Anonymous Chat Module activated!");
    }

    // Deactivation
    public void deactivate() {
        anonymousNames.clear();
        messageQueue.clear();
        anonCounter = 1;
        sendChatInfo("Anonymous Chat Module deactivated!");
    }

    // Send message
    public void sendMessage(UUID playerId, String message, boolean isPrivate) {
        String anonName = anonymousNames.computeIfAbsent(playerId, k -> "Player" + anonCounter++);
        messageQueue.add(new ChatMessage(anonName, message, isPrivate, System.currentTimeMillis()));

        if (!overlayEnabled) {
            ChatUtils.info(formatMessage(messageQueue.peek()));
        }
    }

    // Format message for display
    private String formatMessage(ChatMessage msg) {
        String timePrefix = timestampsEnabled ? "[" + new Date(msg.timestamp).toString() + "] " : "";
        String modePrefix = msg.isPrivate ? Formatting.GRAY + "[Private] " : Formatting.GREEN + "[Public] ";
        return timePrefix + modePrefix + msg.anonName + ": " + Formatting.RESET + msg.message;
    }

    // Render overlay
    public void renderOverlay(MatrixStack matrices) {
        if (!overlayEnabled) return;

        int y = 10;
        int count = 0;
        for (ChatMessage msg : messageQueue) {
            if (count >= overlayMessageLimit) break;
            client.textRenderer.drawWithShadow(matrices, formatMessage(msg), 10, y, 0xFFFFFF);
            y += 12;
            count++;
        }
    }

    // Placeholder networking
    public void receiveMessage(UUID senderId, String message, boolean isPrivate) {
        sendMessage(senderId, message, isPrivate);
    }

    // Utility
    private void sendChatInfo(String msg) {
        client.inGameHud.getChatHud().addMessage(Text.literal(msg));
    }

    // Inner class for queued messages
    private static class ChatMessage {
        final String anonName;
        final String message;
        final boolean isPrivate;
        final long timestamp;

        ChatMessage(String anonName, String message, boolean isPrivate, long timestamp) {
            this.anonName = anonName;
            this.message = message;
            this.isPrivate = isPrivate;
            this.timestamp = timestamp;
        }
    }

    // ChatUtils stub
    private static class ChatUtils {
        static void info(String msg) {
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal(msg));
        }
    }

    // Settings setters
    public void setOverlayEnabled(boolean overlayEnabled) { this.overlayEnabled = overlayEnabled; }
    public void setPrivateModeEnabled(boolean privateModeEnabled) { this.privateModeEnabled = privateModeEnabled; }
    public void setTimestampsEnabled(boolean timestampsEnabled) { this.timestampsEnabled = timestampsEnabled; }
}
