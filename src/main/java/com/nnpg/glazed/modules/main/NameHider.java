package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.Random;

/**
 * NameHider - Hides your real name client-side.
 * Two modes: Custom Name or Glitched Name.
 */
public class NameHider extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> customName = sgGeneral.add(new StringSetting.Builder()
        .name("custom-name")
        .description("Your name will be replaced with this text client-side.")
        .defaultValue("Player")
        .build()
    );

    private final Setting<Boolean> glitchedName = sgGeneral.add(new BoolSetting.Builder()
        .name("glitched-name")
        .description("Scrambles your name to be unreadable instead of using the custom name.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> hideChatName = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-chat-name")
        .description("Hides your name in chat and death messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hideNametag = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-nametag")
        .description("Hides your nametag and third-person name.")
        .defaultValue(true)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();
    private String username;

    public NameHider() {
        super(GlazedAddon.CATEGORY, "name-hider", "Hides your real name with a custom or glitched name.");
    }

    @Override
    public void onActivate() {
        // Store your real username on activation
        username = mc.getSession().getUsername();
    }

    /** Replace your real name with the custom/glitched name */
    public String replaceName(String message) {
        if (!isActive() || message == null) return message;
        String replacement = glitchedName.get() ? generateGlitchedName(username.length()) : customName.get();
        return message.replace(username, replacement);
    }

    /** Get the name that should be displayed client-side */
    public String getDisplayName() {
        if (!isActive()) return username;
        return glitchedName.get() ? generateGlitchedName(username.length()) : customName.get();
    }

    /** Whether nametag should be hidden */
    public boolean hideNametag() {
        return isActive() && hideNametag.get();
    }

    /** Event: replace name in chat and death messages */
    @EventHandler
    private void onChat(ReceiveMessageEvent event) {
        if (mc.player == null || !hideChatName.get()) return;
        event.setMessage(Text.literal(replaceName(event.getMessage().getString())));
    }

    /** Event: replace nametag and third-person name */
    @EventHandler
    private void onTick(meteordevelopment.meteorclient.events.world.TickEvent.Post event) {
        if (mc.player == null || !hideNametag()) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) {
                player.setCustomName(Text.literal(getDisplayName()));
                player.setCustomNameVisible(true);
            }
        }
    }

    /** Generate a random glitched name of a given length */
    private String generateGlitchedName(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:,.<>?";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
