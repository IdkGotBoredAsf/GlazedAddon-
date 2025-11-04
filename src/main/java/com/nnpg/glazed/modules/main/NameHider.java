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
 * Can replace your name with a fake name or scramble it.
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

    private final Setting<Boolean> hideNametag = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-nametag")
        .description("Replaces your in-world nametag with your fake name.")
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
        super(GlazedAddon.CATEGORY, "name-hider", "Hides your real name in chat and in-world.");
    }

    // Replace player name in chat and death messages
    @EventHandler
    private void onChat(ReceiveMessageEvent event) {
        if (mc.player == null || !hideOwnName.get()) return;

        String realName = mc.player.getName().getString();
        String replacement = scrambleName.get() ? generateScrambledName(realName.length()) : fakeName.get();
        String message = event.getMessage().getString();

        if (message.contains(realName)) {
            message = message.replace(realName, replacement);
        }

        // Replace any mention of "Meteor" with the fake name or scrambled name
        if (message.contains("Meteor")) {
            message = message.replace("Meteor", replacement);
        }

        event.setMessage(Text.literal(message));
    }

    // Replace nametag every tick (also affects third-person view)
    @EventHandler
    private void onTick(meteordevelopment.meteorclient.events.world.TickEvent.Post event) {
        if (mc.player == null || !hideNametag.get()) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) {
                player.setCustomName(Text.literal(scrambleName.get() ? generateScrambledName(player.getName().getString().length()) : fakeName.get()));
                player.setCustomNameVisible(true);
            }
        }
    }

    // Helper method to generate a random scrambled name
    private String generateScrambledName(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
