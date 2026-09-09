package com.kael.title.paper;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class SurvivalSplitBridge {

    public static String suffixFor(UUID uuid) {
        if (uuid == null) {
            return "";
        }
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("SurvivalSplit");
            if (plugin == null || !plugin.isEnabled()) {
                return "";
            }
            Object store = plugin.getClass().getMethod("getProfileStore").invoke(plugin);
            if (store == null) {
                return "";
            }
            Object profile = store.getClass().getMethod("get", UUID.class).invoke(store, uuid);
            if (profile == null) {
                return "";
            }
            Object mode = profile.getClass().getMethod("mode").invoke(profile);
            boolean legacy = false;
            try {
                legacy = Boolean.TRUE.equals(profile.getClass().getMethod("legacy").invoke(profile));
            } catch (NoSuchMethodException ignored) {
            }
            return SplitSuffix.forProfile(mode == null ? null : mode.toString(), legacy);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[KaelorvynTitle] SurvivalSplitBridge 读取失败: " + t);
            return "";
        }
    }

    private SurvivalSplitBridge() {
    }
}
