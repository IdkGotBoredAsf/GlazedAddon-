package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

/**
 * NameProtect - Replaces your real name with a custom alias client-side.
 * Works in chat, nametags, and player list (Tab).
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
        .description("Hides your real name in chat and GUI messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hideNametag = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-nametag")
        .description("Replaces your in-world nametag with your fake name.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hideTabList = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-tab-list")
        .description("Replaces your name in the player list (Tab).")
        .defaultValue(true)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public NameProtect() {
        super(GlazedAddon.main, "name-protect", "Replaces your name with a custom alias client-side.");
    }

    @EventHandler
    private void onChat(ReceiveMessageEvent event) {
        if (mc.player == null || !hideOwnName.get()) return;

        String playerName = mc.player.getName().getString();
        String alias = fakeName.get();
        String message = event.getMessage().getString();

        if (message.contains(playerName)) {
            String replaced = message.replace(playerName, alias);
            ChatUtils.sendMsg(replaced);
            event.cancel();
        }
    }

    // Works in 1.21.4 - replaces nametag through the Entity itself
    @EventHandler
    private void onTick(meteordevelopment.meteorclient.events.world.TickEvent.Post event) {
        if (mc.player == null || !hideNametag.get()) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) {
                player.setCustomName(Text.literal(fakeName.get()));
                player.setCustomNameVisible(true);
            }
        }

        if (hideTabList.get() && mc.player.networkHandler != null) {
            for (PlayerListEntry entry : mc.player.networkHandler.getPlayerList()) {
                if (entry.getProfile().getName().equals(mc.player.getName().getString())) {
                    MutableText alias = Text.literal(fakeName.get());
                    entry.displayName(alias);
                }
            }
        }
    }
}
