package com.kael.title.command;

import com.kael.title.config.PluginConfig;
import com.kael.title.storage.TitleDatabase;
import com.kael.title.storage.TitleDatabase.TitleRecord;
import com.kael.title.sync.SyncService;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class TitleCommand implements SimpleCommand {

    private static final String NETEASE_NAME_CHANGE_MESSAGE =
            "§c请退出游戏后在网易游戏内修改昵称。";

    private final ProxyServer server;
    private final TitleDatabase database;
    private final SyncService sync;
    private final PluginConfig config;

    public TitleCommand(ProxyServer server, TitleDatabase database, SyncService sync, PluginConfig config) {
        this.server = server;
        this.database = database;
        this.sync = sync;
        this.config = config;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String alias = invocation.alias().toLowerCase(Locale.ROOT);
        String[] args = invocation.arguments();
        if (alias.equals("ktitle") || alias.equals("称号")) {
            handleTitle(source, args);
        } else {
            handleNick(source, args);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        String alias = invocation.alias().toLowerCase(Locale.ROOT);
        if (alias.equals("ktitle") || alias.equals("称号")) {
            return isAdmin(invocation.source());
        }
        return true;
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String alias = invocation.alias().toLowerCase(Locale.ROOT);
        String[] args = invocation.arguments();
        List<String> suggestions = new ArrayList<>();
        if (args.length == 0) {
            suggestions.add("set");
            suggestions.add("clear");
            suggestions.add("get");
        } else if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 1) {
                if ("set".startsWith(sub)) {
                    suggestions.add("set");
                }
                if ("clear".startsWith(sub)) {
                    suggestions.add("clear");
                }
                if ("get".startsWith(sub)) {
                    suggestions.add("get");
                }
            } else if (args.length == 2) {
                boolean ktitle = alias.equals("ktitle") || alias.equals("称号");
                boolean knick = alias.equals("knick") || alias.equals("昵称");
                boolean playerArgWanted = ktitle
                        || (knick && !sub.equals("set"))
                        || (knick && sub.equals("set") && isAdmin(invocation.source()));
                if (playerArgWanted) {
                    String prefix = args[1].toLowerCase(Locale.ROOT);
                    for (Player player : server.getAllPlayers()) {
                        if (player.getUsername().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            suggestions.add(player.getUsername());
                        }
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(suggestions);
    }

    private void handleTitle(CommandSource source, String[] args) {
        if (!isAdmin(source)) {
            send(source, "§c你没有权限执行此命令。");
            return;
        }
        if (args.length == 0) {
            send(source, "§c用法：/ktitle set <玩家> <称号> | /ktitle clear <玩家> | /ktitle get [玩家]");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("set")) {
            if (args.length < 3) {
                send(source, "§c用法：/ktitle set <玩家> <称号>");
                return;
            }
            String playerName = args[1];
            String title = join(args, 2);
            if (!validateText(title, config.getTitleMax(), "称号")) {
                send(source, "§c称号只能包含中文、字母、数字和下划线，且最多 " + config.getTitleMax() + " 字。");
                return;
            }
            UUID uuid = resolveUuid(playerName);
            String currentNickname = database.get(uuid.toString(), playerName)
                    .map(TitleRecord::getNickname).orElse("");
            int allowedTitle = 9 - currentNickname.length();
            if (title.length() > allowedTitle) {
                send(source, "§c称号和昵称总共最多 9 字，当前昵称 " + currentNickname.length()
                        + " 字，称号最多 " + allowedTitle + " 字。");
                return;
            }
            String targetRank = syncRank(uuid);
            if (("服主".equals(title) && !"owner".equals(targetRank))
                    || ("管理员".equals(title) && !"admin".equals(targetRank))) {
                send(source, "§c不能把该玩家的称号设置为“" + title + "”。");
                return;
            }
            database.setTitle(uuid.toString(), playerName, title);
            sync.broadcastUpdate(uuid, playerName);
            send(source, "§a已将 §e" + playerName + " §a的称号设为 §f[" + title + "]");
        } else if (sub.equals("clear")) {
            if (args.length < 2) {
                send(source, "§c用法：/ktitle clear <玩家>");
                return;
            }
            String playerName = args[1];
            UUID uuid = resolveUuid(playerName);
            database.clearTitle(uuid.toString(), playerName);
            sync.broadcastUpdate(uuid, playerName);
            send(source, "§a已清除 §e" + playerName + " §a的自定义称号。");
        } else if (sub.equals("get")) {
            String playerName = args.length >= 2 ? args[1] : selfName(source);
            if (playerName == null) {
                send(source, "§c用法：/ktitle get [玩家]");
                return;
            }
            showInfo(source, playerName);
        } else {
            send(source, "§c用法：/ktitle set <玩家> <称号> | /ktitle clear <玩家> | /ktitle get [玩家]");
        }
    }

    private void handleNick(CommandSource source, String[] args) {
        if (args.length == 0) {
            send(source, "§c用法：/knick set [玩家] <昵称> | /knick clear [玩家] | /knick get [玩家]");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("set")) {
            String targetName;
            String nickname;
            if (args.length == 2) {
                targetName = selfName(source);
                if (targetName == null) {
                    send(source, "§c用法：/knick set <玩家> <昵称>");
                    return;
                }
                nickname = args[1];
            } else if (args.length == 3) {
                targetName = args[1];
                nickname = args[2];
            } else {
                send(source, "§c用法：/knick set [玩家] <昵称>");
                return;
            }
            if (!validateText(nickname, config.getNicknameMax(), "昵称")) {
                send(source, "§c昵称只能包含中文、字母、数字和下划线，且最多 " + config.getNicknameMax() + " 字。");
                return;
            }
            if (!isAdmin(source) && !isSelf(source, targetName)) {
                send(source, "§c你只能修改自己的昵称。");
                return;
            }
            if (isNeteaseName(targetName)) {
                send(source, NETEASE_NAME_CHANGE_MESSAGE);
                return;
            }
            UUID uuid = resolveUuid(targetName);
            String currentTitle = database.get(uuid.toString(), targetName)
                    .map(TitleRecord::getTitle).orElse("");
            String effectiveTitle = currentTitle.isEmpty() ? config.titleFor(syncRank(uuid)) : currentTitle;
            int allowedNickname = 9 - effectiveTitle.length();
            if (nickname.length() > allowedNickname) {
                send(source, "§c称号和昵称总共最多 9 字，当前称号 " + effectiveTitle
                        + "（" + effectiveTitle.length() + " 字），昵称最多 " + allowedNickname + " 字。");
                return;
            }
            if (!isAdmin(source)) {
                long last = database.get(uuid.toString(), targetName)
                        .map(TitleRecord::getLastNickChange).orElse(0L);
                long cooldownMillis = config.getCooldownHours() * 3600_000L;
                long remaining = cooldownMillis - (System.currentTimeMillis() - last);
                if (remaining > 0) {
                    send(source, "§c昵称修改冷却中，还需 §e" + remainingText(remaining) + "§c。");
                    return;
                }
            }
            database.setNickname(uuid.toString(), targetName, nickname, System.currentTimeMillis());
            sync.broadcastUpdate(uuid, targetName);
            if (isSelf(source, targetName) && !isAdmin(source)) {
                send(source, "§a昵称修改成功：§f" + nickname + "§a，24 小时内不能再次修改。");
            } else {
                send(source, "§a已把 §e" + targetName + " §a的昵称设为 §f" + nickname);
            }
        } else if (sub.equals("clear")) {
            String targetName;
            if (args.length == 1) {
                targetName = selfName(source);
                if (targetName == null) {
                    send(source, "§c用法：/knick clear <玩家>");
                    return;
                }
            } else if (args.length == 2) {
                targetName = args[1];
            } else {
                send(source, "§c用法：/knick clear [玩家]");
                return;
            }
            if (!isAdmin(source) && !isSelf(source, targetName)) {
                send(source, "§c你只能清除自己的昵称。");
                return;
            }
            if (isNeteaseName(targetName)) {
                send(source, NETEASE_NAME_CHANGE_MESSAGE);
                return;
            }
            UUID uuid = resolveUuid(targetName);
            database.clearNickname(uuid.toString(), targetName);
            sync.broadcastUpdate(uuid, targetName);
            send(source, "§a已清除 §e" + targetName + " §a的昵称。");
        } else if (sub.equals("get")) {
            String playerName = args.length >= 2 ? args[1] : selfName(source);
            if (playerName == null) {
                send(source, "§c用法：/knick get [玩家]");
                return;
            }
            showInfo(source, playerName);
        } else {
            send(source, "§c用法：/knick set [玩家] <昵称> | /knick clear [玩家] | /knick get [玩家]");
        }
    }

    private void showInfo(CommandSource source, String playerName) {
        UUID uuid = resolveUuid(playerName);
        Optional<TitleRecord> record = database.get(uuid.toString(), playerName);
        String title = record.map(TitleRecord::getTitle).orElse("");
        String nickname = record.map(TitleRecord::getNickname).orElse("");
        String rank = syncRank(uuid);
        String effectiveTitle = title.isEmpty() ? config.titleFor(rank) : title;
        String color = config.colorFor(rank);
        String display = "\u00A7" + color + "[" + effectiveTitle + "]\u00A7r"
                + (nickname.isEmpty() ? " " + playerName : nickname + "(" + playerName + ")");
        send(source, "§e" + playerName + " §7当前称号：§f[" + effectiveTitle + "]"
                + "§7，昵称：" + (nickname.isEmpty() ? "§7无" : "§f" + nickname)
                + "§7，显示：§f" + display);
    }

    private String syncRank(UUID uuid) {
        Optional<Player> player = server.getPlayer(uuid);
        if (player.isPresent()) {
            if (config.getOwnerUuids().contains(uuid) || player.get().hasPermission("ktitle.owner")) {
                return "owner";
            }
            if (config.getAdminUuids().contains(uuid) || player.get().hasPermission("ktitle.admin")) {
                return "admin";
            }
        }
        return config.rankForUuid(uuid.toString());
    }

    private UUID resolveUuid(String playerName) {
        for (Player player : server.getAllPlayers()) {
            if (player.getUsername().equalsIgnoreCase(playerName)) {
                return player.getUniqueId();
            }
        }
        Optional<Player> exact = server.getPlayer(playerName);
        if (exact.isPresent()) {
            return exact.get().getUniqueId();
        }
        return offlineUuid(playerName);
    }

    private static UUID offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }

    private boolean isAdmin(CommandSource source) {
        if (source instanceof Player player) {
            UUID uuid = player.getUniqueId();
            if (config.getOwnerUuids().contains(uuid) || config.getAdminUuids().contains(uuid)) {
                return true;
            }
        }
        return source instanceof ConsoleCommandSource
                || source.hasPermission("ktitle.admin")
                || source.hasPermission("ktitle.owner");
    }

    private boolean isSelf(CommandSource source, String playerName) {
        if (!(source instanceof Player player)) {
            return false;
        }
        return player.getUsername().equalsIgnoreCase(playerName);
    }

    private String selfName(CommandSource source) {
        return source instanceof Player player ? player.getUsername() : null;
    }

   private boolean isNeteaseName(String playerName) {
        return sync.isNetease(playerName) || !playerName.matches("[A-Za-z0-9_]+");
   }

    private boolean validateText(String text, int max, String label) {
        if (text == null || text.isEmpty() || text.length() > max) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' || c == '&' || c == ' ' || c == '\t' || c == '\n'
                    || c == '(' || c == ')' || c == '[' || c == ']') {
                return false;
            }
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    private String remainingText(long millis) {
        long totalMinutes = Math.max(1, (millis + 59999) / 60000);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours > 0 ? hours + " 小时 " + minutes + " 分" : minutes + " 分";
    }

    private String join(String[] args, int from) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private void send(CommandSource source, String legacy) {
        source.sendMessage(LegacyComponentSerializer.legacySection().deserialize(legacy));
    }
}
