package com.kael.title.paper;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class JoinListener implements Listener {

    private final TitleStore store;

    public JoinListener(TitleStore store) {
        this.store = store;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        autoLoginNetease(player);
        TitleData existing = store.get(player.getUniqueId());
        if (existing == null) {
            existing = store.getByName(player.getName());
        }
        if (existing != null) {
            store.apply(existing);
        }
        store.plugin().getLogger().info("玩家进服: " + player.getName() + " 本地缓存=" + (existing != null));
        store.onObserverJoin(player);
        Bukkit.getScheduler().runTaskLater(store.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            String payload = "REQ\t" + player.getUniqueId() + "\t" + player.getName();
            player.sendPluginMessage(store.plugin(), KaelorvynTitle.CHANNEL,
                    payload.getBytes(StandardCharsets.UTF_8));
            store.plugin().getLogger().info("进服后补问 REQ: " + player.getName());
        }, 300L);
    }

    private void autoLoginNetease(Player player) {
        if (!player.getName().matches("Netease[0-9]+")
                || Bukkit.getPluginManager().getPlugin("AuthMe") == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(store.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            try {
                Class<?> apiClass = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
                Object api = apiClass.getMethod("getInstance").invoke(null);
                Method isRegistered = apiClass.getMethod("isRegistered", String.class);
                if (!(Boolean) isRegistered.invoke(api, player.getName())) {
                    String password = "Netease-" + UUID.nameUUIDFromBytes(
                            ("Kaelorvyn:" + player.getUniqueId()).getBytes(StandardCharsets.UTF_8));
                    apiClass.getMethod("registerPlayer", String.class, String.class)
                            .invoke(api, player.getName(), password);
                }
                apiClass.getMethod("forceLogin", Player.class).invoke(api, player);
                store.plugin().getLogger().info("网易玩家自动完成登录: " + player.getName());
            } catch (Throwable error) {
                store.plugin().getLogger().warning("网易玩家自动登录失败 " + player.getName()
                        + ": " + error.getMessage());
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        store.onObserverQuit(event.getPlayer());
    }
}
