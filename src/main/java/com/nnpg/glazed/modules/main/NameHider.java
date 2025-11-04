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
 * NameHider - Hides your real name in chat and in-world.
 * Two modes: Custom Name or Glitched Name.
 */
public class NameHider extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> customName = sgGeneral.add(new StringSetting.Builder()
        .name("custom-name")
        .description("Set a custom name to replace your real name.")
        .defaultValue("Player")
        .build()
    );

    private final Setting<Boolean> glitchedName = sgGeneral.add(new BoolSetting.Builder()
        .name("glitched-name")
        .description("Scrambles your name to be unreadable instead of using the custom name.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> hideNametag = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-nametag")
        .description("Replaces your in-world nametag and third-person name with the chosen name.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hideChatName = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-chat-name")
        .description("Replaces your name in all chat messages and death messages.")
        .defaultValue(true)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    public NameHider() {
        super(GlazedAddon.CATEGORY, "name-hider", "Hides your real name with a custom or glitched name.");
    }

    // Replace player name in chat & death messages
    @EventHandler
    private void onChat(ReceiveMessageEvent event) {
        if (mc.player == null || !hideChatName.get()) return;

        String realName = mc.player.getName().getString();
        String replacement = glitchedName.get() ? generateGlitchedName(realName.length()) : customName.get();
        String message = event.getMessage().getString();

        if (message.contains(realName)) message = message.replace(realName, replacement);

        event.setMessage(Text.literal(message));
    }

    // Replace nametag every tick (also affects third-person view)
    @EventHandler
    private void onTick(meteordevelopment.meteorclient.events.world.TickEvent.Post event) {
        if (mc.player == null || !hideNametag.get()) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) {
                String replacement = glitchedName.get() ? generateGlitchedName(player.getName().getString().length()) : customName.get();
                player.setCustomName(Text.literal(replacement));
                player.setCustomNameVisible(true);
            }
        }
    }

    // Helper method to generate a random glitched name
    private String generateGlitchedName(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:,.<>?";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
