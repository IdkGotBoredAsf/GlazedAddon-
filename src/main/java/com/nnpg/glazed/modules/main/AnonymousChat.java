package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AnonymousChat extends Module implements HudRenderCallback {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> privateMode = sgGeneral.add(new BoolSetting.Builder()
        .name("private-mode")
        .description("Toggle private mode for your messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> overlayEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("overlay-enabled")
        .description("Display messages in overlay instead of chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> timestampsEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("timestamps")
        .description("Show timestamps for messages.")
        .defaultValue(true)
        .build()
    );

    private final Map<UUID, String> anonymousNames = new HashMap<>();
    private int anonCounter = 1;

    private final LinkedList<Message> messageQueue = new LinkedList<>();
    private final int MAX_MESSAGES = 10;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public AnonymousChat() {
        super(GlazedAddon.CATEGORY, "anonymous-chat", "Send messages anonymously with overlay support.");
    }

    @Override
    public void onActivate() {
        ChatUtils.info("Anonymous Chat Module Enabled! Your session usernames are temporary.");
        anonymousNames.clear();
        messageQueue.clear();
        anonCounter = 1;
        HudRenderCallback.EVENT.register(this); // Register HUD overlay
    }

    @Override
    public void onDeactivate() {
        ChatUtils.info("Anonymous Chat Module Disabled. Clearing messages and usernames.");
        anonymousNames.clear();
        messageQueue.clear();
        anonCounter = 1;
        HudRenderCallback.EVENT.unregister(this); // Unregister HUD overlay
    }

    public void sendMessage(UUID playerId, String rawMessage) {
        String anonName = anonymousNames.computeIfAbsent(playerId, id -> "Player" + (anonCounter++));
        boolean isPrivate = privateMode.get();
        String displayMessage = (timestampsEnabled.get() ? "[" + LocalTime.now().format(TIME_FORMATTER) + "] " : "")
                + (isPrivate ? "[Private] " : "[Public] ")
                + anonName + ": " + rawMessage;

        enqueueMessage(displayMessage);
        if (!overlayEnabled.get()) {
            ChatUtils.info(displayMessage);
        }
    }

    private void enqueueMessage(String msg) {
        messageQueue.addLast(new Message(msg));
        if (messageQueue.size() > MAX_MESSAGES) {
            messageQueue.removeFirst();
        }
    }

    @Override
    public void onHudRender(MatrixStack matrices, float tickDelta) {
        if (!overlayEnabled.get() || mc.player == null) return;

        int yOffset = 10;
        for (Message msg : messageQueue) {
            mc.textRenderer.drawWithShadow(matrices, msg.text, 10, yOffset, 0xFFFFFF);
            yOffset += 12;
        }
    }

    private static class Message {
        String text;
        long timestamp;

        Message(String text) {
            this.text = text;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public void receiveNetworkMessage(UUID playerId, String rawMessage, boolean isPrivate) {
        String anonName = anonymousNames.computeIfAbsent(playerId, id -> "Player" + (anonCounter++));
        String displayMessage = (timestampsEnabled.get() ? "[" + LocalTime.now().format(TIME_FORMATTER) + "] " : "")
                + (isPrivate ? "[Private] " : "[Public] ")
                + anonName + ": " + rawMessage;

        enqueueMessage(displayMessage);
        if (!overlayEnabled.get()) {
            ChatUtils.info(displayMessage);
        }
    }
}
