package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.network.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.render.RenderNametagEvent;
import meteordevelopment.meteorclient.events.render.RenderEvent;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;

/**
 * NameProtect - Replaces your real name with a custom alias client-side.
 * Works in chat, nametags, death messages, and player list (Tab).
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

    private final Setting<Boolean> hideDeathMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-death-messages")
        .description("Replaces your name in death messages client-side.")
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
        String originalMsg = event.getMessage().getString();

        // Covers normal + death messages
        if ((hideOwnName.get() || hideDeathMessages.get()) && originalMsg.contains(playerName)) {
            String replaced = originalMsg.replace(playerName, alias);
            ChatUtils.sendClientMessage(replaced);
            event.cancel();
        }
    }

    @EventHandler
    private void onNametag(RenderNametagEvent event) {
        if (!hideNametag.get() || mc.player == null) return;

        PlayerEntity entity = event.entity;
        if (entity == mc.player) {
            event.name = fakeName.get();
        }
    }

    @EventHandler
    private void onRender(RenderEvent event) {
        if (!hideTabList.get() || mc.player == null || mc.player.networkHandler == null) return;

        String playerName = mc.player.getName().getString();
        String alias = fakeName.get();

        for (PlayerListEntry entry : mc.player.networkHandler.getPlayerList()) {
            if (entry.getProfile().getName().equals(playerName)) {
                MutableText newName = Text.literal(alias);
                // Update display name client-side
                entry.displayName(newName);
            }
        }
    }
}
