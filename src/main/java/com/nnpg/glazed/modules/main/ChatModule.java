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

    private final Setting<Boolean> overlayChat = sgGeneral.add(new BoolSetting.Builder()
            .name("overlay-chat")
            .description("Display chat messages as overlay.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> showTimestamps = sgGeneral.add(new BoolSetting.Builder()
            .name("show-timestamps")
            .description("Show timestamps for messages.")
            .defaultValue(true)
            .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();

    // ---------------------- Message Storage ----------------------
    private final List<String> messageQueue = new ArrayList<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private int anonCounter = 1;

    // ---------------------- Constructor ----------------------
    public ChatModule() {
        super(GlazedAddon.CATEGORY, "chat-module", "Private anonymous chat for mod users.");
    }

    // ---------------------- Sending Messages ----------------------
    public void sendMessage(String message) {
        if (mc.player == null || message.isEmpty()) return;

        String formatted = formatMessage(message);
        UUID sender = mc.player.getUuid();

        addMessage(sender, formatted);

        // Show locally if overlay is off
        if (!overlayChat.get()) {
            ChatUtils.info(getAnonName(sender) + ": " + formatted);
        }

        // Send to other mod users (stub, replace with network)
        sendNetworkMessage(sender, formatted);
    }

    private String formatMessage(String message) {
        if (showTimestamps.get()) {
            long time = System.currentTimeMillis();
            return String.format("[%tH:%tM:%tS] %s", time, time, time, message);
        }
        return message;
    }

    private void addMessage(UUID sender, String message) {
        String anon = getAnonName(sender);
        messageQueue.add(anon + ": " + message);
    }

    private String getAnonName(UUID uuid) {
        return playerNames.computeIfAbsent(uuid, k -> "Player" + anonCounter++);
    }

    // ---------------------- Receiving Messages ----------------------
    public void receiveNetworkMessage(UUID sender, String message) {
        addMessage(sender, message);
    }

    private void sendNetworkMessage(UUID sender, String message) {
        // TODO: Replace with Fabric SimpleChannel networking
        // For now, simulate by calling receive locally
        receiveNetworkMessage(sender, message);
    }

    // ---------------------- Overlay Rendering ----------------------
    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!overlayChat.get() || messageQueue.isEmpty()) return;

        int maxLines = 10;
        int y = 20;

        List<String> latest = messageQueue.size() > maxLines ?
                messageQueue.subList(messageQueue.size() - maxLines, messageQueue.size()) :
                messageQueue;

        for (String msg : latest) {
            mc.textRenderer.drawWithShadow(msg, 10, y, 0xFFFFFF);
            y += 12;
        }
    }

    // ---------------------- Lifecycle ----------------------
    @Override
    public void onActivate() {
        ChatUtils.info("💬 Chat Module Activated! Private Mod Chat Enabled.");
    }

    @Override
    public void onDeactivate() {
        ChatUtils.info("💬 Chat Module Deactivated!");
        messageQueue.clear();
        playerNames.clear();
        anonCounter = 1;
    }

    // ---------------------- Utility ----------------------
    public void displayAllMessages() {
        for (String msg : messageQueue) ChatUtils.info(msg);
    }
}
