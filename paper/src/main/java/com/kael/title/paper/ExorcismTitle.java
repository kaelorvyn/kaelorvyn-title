package com.kael.title.paper;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class ExorcismTitle {

    private static final String[] TAGS = {
            "kit_1", "kit_2", "kit_3", "kit_4",
            "kit_5", "kit_6", "kit_7", "kit_8"
    };
    private static final String[] NAMES = {
            "轻甲战士", "重甲战士", "法师", "盾卫士",
            "浪人", "医师", "寻宝者", "武器匠"
    };

    private ExorcismTitle() {
    }

    static String forUuid(UUID uuid) {
        if (!enabled()) {
            return "";
        }
        Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
        if (player == null) {
            return "";
        }
        for (int i = 0; i < TAGS.length; i++) {
            if (player.getScoreboardTags().contains(TAGS[i])) {
                return NAMES[i];
            }
        }
        return "";
    }

    private static boolean enabled() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ExorcismResourcePack");
        return plugin != null && plugin.isEnabled()
                && "exorcism".equalsIgnoreCase(plugin.getConfig().getString("mode", ""));
    }
}
