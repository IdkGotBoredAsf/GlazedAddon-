package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * NameProtect - Replaces your real name with a custom alias client-side.
 * Works in chat and nametags (client side only).
 */
public class NameProtect extends Module {
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

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public NameProtect() {
        // ✅ use correct category — replace `.main` with your actual existing one (misc, troll, etc.)
        super(GlazedAddon.misc, "name-protect", "Replaces your name with a custom alias client-side.");
    }

    // Replace player name in chat
    @EventHandler
    private void onChat(ReceiveMessageEvent event) {
        if (mc.player == null || !hideOwnName.get()) return;

        String realName = mc.player.getName().getString();
        String alias = fakeName.get();
        String message = event.getMessage().getString();

        if (message.contains(realName)) {
            // ChatUtils now expects Text
            ChatUtils.sendMsg(Text.literal(message.replace(realName, alias)));
            event.cancel();
        }
    }

    // Replace nametag every tick
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !hideNametag.get()) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) {
                player.setCustomName(Text.literal(fakeName.get()));
                player.setCustomNameVisible(true);
            }
        }
    }
}
