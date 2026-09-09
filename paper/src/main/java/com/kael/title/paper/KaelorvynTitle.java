package com.kael.title.paper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;

public final class KaelorvynTitle extends JavaPlugin implements PluginMessageListener {

    public static final String CHANNEL = "kaeltitle:main";

    private TitleStore store;
    private Object packetFeature;

    @Override
    public void onEnable() {
        store = new TitleStore(this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(new JoinListener(store), this);
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onChat(AsyncChatEvent event) {
                TitleData titleData = store.get(event.getPlayer().getUniqueId());
                String visibleName = titleData != null && titleData.netease()
                        ? titleData.originalName() : store.originalNameFor(event.getPlayer().getName());
                if (visibleName == null || visibleName.isEmpty()) {
                    return;
                }
                Component name = Component.text(visibleName);
                event.renderer((source, displayName, message, viewer) ->
                        Component.empty().append(Component.text("<"))
                                .append(name)
                                .append(Component.text("> "))
                                .append(message));
            }
        }, this);

        if (Bukkit.getPluginManager().isPluginEnabled("packetevents")) {
            try {
                Class<?> clazz = Class.forName("com.kael.title.paper.PacketHandler");
                packetFeature = clazz.getConstructor(TitleStore.class).newInstance(store);
                clazz.getMethod("register").invoke(packetFeature);
                getLogger().info("PacketEvents 已启用，头顶称号渲染已开启。");
            } catch (Throwable t) {
                getLogger().warning("PacketEvents 初始化失败：" + t.getMessage());
            }
        } else {
            getLogger().warning("未检测到 PacketEvents，称号不会显示。");
        }

        store.startReconcile();
        requestMissingForOnlinePlayers();
        getLogger().info("KaelorvynTitle Paper 已启动。");
    }

    private void requestMissingForOnlinePlayers() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (store.get(player.getUniqueId()) != null
                        || store.getByName(player.getName()) != null) {
                    continue;
                }
                String payload = "REQ\t" + player.getUniqueId() + "\t" + player.getName();
                player.sendPluginMessage(this, CHANNEL, payload.getBytes(StandardCharsets.UTF_8));
            }
        }, 300L);
    }

    @Override
    public void onDisable() {
        if (packetFeature != null) {
            try {
                packetFeature.getClass().getMethod("unregister").invoke(packetFeature);
            } catch (Throwable ignored) {
            }
        }
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        String text = new String(message, StandardCharsets.UTF_8);
        String[] parts = text.split("\\t", -1);
        if (parts.length < 7) {
            return;
        }
        String type = parts[0];
        if (!"SYNC".equals(type) && !"UPD".equals(type)) {
            return;
        }
        try {
            UUID uuid = UUID.fromString(parts[1]);
            String name = parts[2];
            String title = parts[3];
            String nickname = parts[4];
            String color = parts[5];
            String defaultTitle = parts[6];
            boolean netease = parts.length >= 8 && Boolean.parseBoolean(parts[7]);
            String originalName = parts.length >= 9 ? parts[8] : "";
            getLogger().info("收到代理端 " + type + ": " + name + " (" + uuid + ")");
            store.apply(new TitleData(uuid, name, title, nickname, color, defaultTitle, netease, originalName));
        } catch (IllegalArgumentException ignored) {
        }
    }

    public void refreshPlayer(UUID uuid) {
        if (store != null) {
            store.refresh(uuid);
        }
    }
}
