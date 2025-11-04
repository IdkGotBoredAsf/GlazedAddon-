package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.*;

public class ChatModule extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> privateChat = sgGeneral.add(new BoolSetting.Builder()
        .name("private-chat")
        .description("Toggle between private and public chat.")
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

    // Stores messages to display
    private final List<String> messageQueue = new ArrayList<>();

    // Maps player UUIDs to anonymous names
    private final Map<UUID, String> playerNames = new HashMap<>();
    private int anonymousCounter = 1;

    public ChatModule() {
        super(GlazedAddon.CATEGORY, "chat-module", "Anonymous chat module with overlay or normal Minecraft chat.");
    }

    /**
     * Sends a chat message to other mod users
     */
    public void sendMessage(String message) {
        if (mc.player == null || message.isEmpty()) return;

        String formattedMessage = formatMessage(message);

        if (overlayChat.get()) {
            addMessageToQueue(mc.player.getUuid(), formattedMessage);
        } else {
            ChatUtils.info("[Private] " + formattedMessage);
        }

        // TODO: Implement networking to send this message to other clients
    }

    /**
     * Formats message with timestamp if enabled
     */
    private String formatMessage(String message) {
        if (showTimestamps.get()) {
            long timestamp = System.currentTimeMillis();
            return String.format("[%tH:%tM:%tS] %s", timestamp, timestamp, timestamp, message);
        }
        return message;
    }

    /**
     * Adds a message to the overlay queue
     */
    private void addMessageToQueue(UUID senderUUID, String message) {
        String anonName = getAnonymousName(senderUUID);
        String prefix = privateChat.get() ? "[Private]" : "[Public]";
        messageQueue.add(prefix + " " + anonName + ": " + message);
    }

    /**
     * Returns anonymous name for a player
     */
    private String getAnonymousName(UUID uuid) {
        return playerNames.computeIfAbsent(uuid, k -> "Player" + anonymousCounter++);
    }

    /**
     * Display messages in overlay
     */
    private void renderOverlay(MatrixStack matrices) {
        if (!overlayChat.get() || messageQueue.isEmpty()) return;

        int y = 20;
        int maxMessages = 10;
        List<String> latest = messageQueue.size() > maxMessages ?
            messageQueue.subList(messageQueue.size() - maxMessages, messageQueue.size()) :
            messageQueue;

        for (String msg : latest) {
            mc.textRenderer.drawWithShadow(Text.literal(msg), 10f, (float)y, 0xFFFFFF);
            y += 12;
        }
    }

    /**
     * Display queued messages in Minecraft chat
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
        playerNames.clear();
        anonymousCounter = 1;
    }

    // Render overlay during 2D render events
    @EventHandler
    private void onRender(Render2DEvent event) {
        renderOverlay(event.matrixStack);
    }

    // Placeholder for receiving messages from other mod users
    public void receiveMessage(UUID senderUUID, String message) {
        addMessageToQueue(senderUUID, message);
    }
}
