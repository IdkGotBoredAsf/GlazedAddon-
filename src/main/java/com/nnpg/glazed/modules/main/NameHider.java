package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.network.MessageType;

import java.util.Random;

/**
 * NameHider - Hides your real name in chat.
 * Optional fake name or scrambled name.
 */
public class NameHider extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> fakeName = sgGeneral.add(new StringSetting.Builder()
        .name("fake-name")
        .description("Your name will be replaced with this text client-side.")
        .defaultValue("Player")
        .build()
    );

    private final Setting<Boolean> hideOwnName = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-own-name")
        .description("Replaces your real name in all chat messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> scrambleName = sgGeneral.add(new BoolSetting.Builder()
        .name("scramble-name")
        .description("Scrambles your name to be unreadable instead of using the fake name.")
        .defaultValue(false)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    public NameHider() {
        super(GlazedAddon.CATEGORY, "name-hider", "Hides your real name in chat.");
    }

    @EventHandler
    private void onChat(ReceiveMessageEvent event) {
        if (mc.player == null || !hideOwnName.get()) return;

        String realName = mc.player.getName().getString();
        String replacement;

        if (scrambleName.get()) {
            replacement = generateScrambledName(realName.length());
        } else {
            replacement = fakeName.get();
        }

        String message = event.getMessage().getString();
        if (message.contains(realName)) {
            // Replace all occurrences of real name with replacement
            String newMessage = message.replace(realName, replacement);

            // Prevent "Meteor" from appearing in death messages
            if (newMessage.contains("Meteor")) {
                newMessage = newMessage.replace("Meteor", replacement);
            }

            event.setMessage(Text.literal(newMessage), MessageType.SYSTEM);
        }
    }

    private String generateScrambledName(int length) {
        StringBuilder sb = new StringBuilder();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
