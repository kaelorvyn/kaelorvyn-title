package com.kael.title.paper;

import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TitleStore {

    private final KaelorvynTitle plugin;
    private final Map<UUID, TitleData> data = new ConcurrentHashMap<>();
    private final Map<String, TitleData> byName = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> createdTeams = new ConcurrentHashMap<>();
    private final Map<UUID, String> cachedTabDisplay = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRefresh = ConcurrentHashMap.newKeySet();

    public TitleStore(KaelorvynTitle plugin) {
        this.plugin = plugin;
    }

    public KaelorvynTitle plugin() {
        return plugin;
    }

    public TitleData get(UUID uuid) {
        return data.get(uuid);
    }

    public String cachedTabDisplay(UUID uuid) {
        return cachedTabDisplay.get(uuid);
    }

    public TitleData getByName(String playerName) {
        return playerName == null ? null : byName.get(playerName.toLowerCase(Locale.ROOT));
    }

    public String originalNameFor(String internalName) {
        TitleData titleData = getByName(internalName);
        if (titleData != null && titleData.netease() && !titleData.originalName().isEmpty()) {
            return titleData.originalName();
        }
        return originalNameFromFile(internalName);
    }

    private String originalNameFromFile(String internalName) {
        if (internalName == null || !internalName.matches("(?i)Netease[0-9]+")) {
            return null;
        }
        Path file = Path.of("D:/MC/server/[25565] 代理端/netease-players.tsv");
        try {
            if (!Files.exists(file)) {
                return null;
            }
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\t", -1);
                if (parts.length == 2 && parts[0].equalsIgnoreCase(internalName)) {
                    return new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public void apply(TitleData titleData) {
        UUID uuid = titleData.uuid();
        Player target = Bukkit.getPlayer(uuid);
        if (target == null) {
            target = findOnlineByName(titleData.playerName());
            if (target != null) {
                uuid = target.getUniqueId();
                titleData = new TitleData(uuid, titleData.playerName(), titleData.title(),
                        titleData.nickname(), titleData.color(), titleData.defaultTitle(), titleData.netease(),
                        titleData.originalName());
            }
        }
        data.put(uuid, titleData);
        byName.put(titleData.playerName().toLowerCase(Locale.ROOT), titleData);
        updateCachedTabDisplay(titleData, target);
        plugin.getLogger().info("应用称号 " + titleData.playerName() + " uuid=" + uuid
                + " target=" + (target == null ? "null" : target.getName())
                + " 分流尾缀=" + splitSuffix(uuid));
        if (target == null || !target.isOnline()) {
            return;
        }
        refreshAll(target, titleData);
        scheduleRefreshChain(titleData.uuid());
    }

    private Player findOnlineByName(String playerName) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(playerName)) {
                return player;
            }
        }
        return null;
    }

    private void refreshAll(Player target, TitleData titleData) {
        for (Player observer : Bukkit.getOnlinePlayers()) {
            sendTeam(observer, target, titleData, true);
            sendDisplayUpdate(observer, target, titleData);
        }
    }

    private void scheduleRefreshChain(UUID uuid) {
        if (!pendingRefresh.add(uuid)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            refreshStored(uuid);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                refreshStored(uuid);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    pendingRefresh.remove(uuid);
                    refreshStored(uuid);
                }, 120L);
            }, 60L);
        }, 20L);
    }

    private void refreshStored(UUID uuid) {
        TitleData titleData = data.get(uuid);
        Player target = Bukkit.getPlayer(uuid);
        if (titleData == null || target == null || !target.isOnline()) {
            return;
        }
        updateCachedTabDisplay(titleData, target);
        refreshAll(target, titleData);
    }

    private void updateCachedTabDisplay(TitleData titleData, Player target) {
        int ping = target == null ? 0 : com.github.retrooper.packetevents.PacketEvents.getAPI()
                .getPlayerManager().getPing(target);
        cachedTabDisplay.put(titleData.uuid(), titleData.tabDisplay(ping, splitSuffix(titleData.uuid()),
                exorcismTitle(titleData.uuid())));
    }

    private void sendProfileRefresh(Player observer, Player target, TitleData titleData) {
        if (!observer.isOnline() || !target.isOnline()) {
            return;
        }
        try {
            int ping = com.github.retrooper.packetevents.PacketEvents.getAPI()
                    .getPlayerManager().getPing(target);
            net.kyori.adventure.text.Component display =
                    legacy(titleData.tabDisplay(ping, splitSuffix(titleData.uuid()),
                            exorcismTitle(titleData.uuid())));
            com.github.retrooper.packetevents.protocol.player.UserProfile sourceProfile =
                    com.github.retrooper.packetevents.PacketEvents.getAPI()
                            .getPlayerManager().getUser(target).getProfile();
            com.github.retrooper.packetevents.protocol.player.UserProfile profile =
                    new com.github.retrooper.packetevents.protocol.player.UserProfile(
                            titleData.uuid(),
                            titleData.profileDisplayName(),
                            sourceProfile.getTextureProperties());
            com.github.retrooper.packetevents.protocol.player.GameMode gameMode =
                    toPacketGameMode(target.getGameMode());
            boolean modern = com.github.retrooper.packetevents.PacketEvents.getAPI()
                    .getServerManager().getVersion()
                    .isNewerThanOrEquals(com.github.retrooper.packetevents.manager.server.ServerVersion.V_1_19_3);
            if (modern) {
                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove remove =
                        new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove(
                                titleData.uuid());
                com.github.retrooper.packetevents.PacketEvents.getAPI()
                        .getPlayerManager().sendPacket(observer, remove);
                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate add =
                        new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate(
                                java.util.EnumSet.of(
                                        com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                                        com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME),
                                new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                                        titleData.uuid()));
                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry =
                        add.getEntries().get(0);
                entry.setGameProfile(profile);
                entry.setListed(true);
                entry.setLatency(ping);
                entry.setGameMode(gameMode);
                entry.setDisplayName(display);
                com.github.retrooper.packetevents.PacketEvents.getAPI()
                        .getPlayerManager().sendPacket(observer, add);
            } else {
                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.PlayerData removeData =
                        new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.PlayerData(
                                null, profile, gameMode, ping);
                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo remove =
                        new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo(
                                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER,
                                removeData);
                com.github.retrooper.packetevents.PacketEvents.getAPI()
                        .getPlayerManager().sendPacket(observer, remove);
                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.PlayerData addData =
                        new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.PlayerData(
                                display, profile, gameMode, ping);
                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo add =
                        new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo(
                                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.Action.ADD_PLAYER,
                                addData);
                com.github.retrooper.packetevents.PacketEvents.getAPI()
                        .getPlayerManager().sendPacket(observer, add);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "发送队伍包失败 " + titleData.playerName(), t);
        }
    }

    private static com.github.retrooper.packetevents.protocol.player.GameMode toPacketGameMode(
            org.bukkit.GameMode mode) {
        if (mode == org.bukkit.GameMode.CREATIVE) {
            return com.github.retrooper.packetevents.protocol.player.GameMode.CREATIVE;
        }
        if (mode == org.bukkit.GameMode.ADVENTURE) {
            return com.github.retrooper.packetevents.protocol.player.GameMode.ADVENTURE;
        }
        if (mode == org.bukkit.GameMode.SPECTATOR) {
            return com.github.retrooper.packetevents.protocol.player.GameMode.SPECTATOR;
        }
        return com.github.retrooper.packetevents.protocol.player.GameMode.SURVIVAL;
    }

    public void onObserverJoin(Player observer) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (observer.isOnline()) {
                sendAllTeams(observer);
            }
        }, 10L);
    }

    public void onObserverQuit(Player observer) {
        createdTeams.remove(observer.getUniqueId());
    }

    public void startReconcile() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player observer : Bukkit.getOnlinePlayers()) {
                sendAllTeams(observer);
                sendDisplayUpdates(observer);
            }
        }, 100L, 100L);
    }

    private void sendAllTeams(Player observer) {
        for (TitleData titleData : data.values()) {
            Player target = Bukkit.getPlayer(titleData.uuid());
            if (target != null && target.isOnline()) {
                sendTeam(observer, target, titleData, false);
            }
        }
    }

    private void sendTeam(Player observer, Player target, TitleData titleData, boolean force) {
        if (observer.equals(target) || !observer.isOnline()) {
            return;
        }
        try {
            String teamName = "kt" + titleData.uuid().toString().replace("-", "").substring(0, 14);
            Set<UUID> teams = createdTeams.computeIfAbsent(observer.getUniqueId(),
                    key -> ConcurrentHashMap.newKeySet());
            boolean created = teams.contains(titleData.uuid());
            String[] chunks = titleData.teamChunks(splitSuffix(titleData.uuid()),
                    exorcismTitle(titleData.uuid()));
            if (force || !created) {
                if (created) {
                    sendTeamsPacket(observer, teamName,
                            com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode.REMOVE,
                            null, null);
                }
                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo info =
                        new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo(
                                net.kyori.adventure.text.Component.text(teamName),
                                legacy(chunks[0]),
                                legacy(chunks[2]),
                                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.CollisionRule.NEVER,
                                net.kyori.adventure.text.format.NamedTextColor.WHITE,
                                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.OptionData.NONE);
                sendTeamsPacket(observer, teamName,
                        com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode.CREATE,
                        info, chunks[1]);
                teams.add(titleData.uuid());
            } else {
                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo info =
                        new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo(
                                net.kyori.adventure.text.Component.text(teamName),
                                legacy(chunks[0]),
                                legacy(chunks[2]),
                                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.CollisionRule.NEVER,
                                net.kyori.adventure.text.format.NamedTextColor.WHITE,
                                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.OptionData.NONE);
                sendTeamsPacket(observer, teamName,
                        com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode.UPDATE,
                        info, null);
                sendTeamsPacket(observer, teamName,
                        com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
                        null, chunks[1]);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "发送显示名失败 " + titleData.playerName(), t);
        }
    }

    private void sendTeamsPacket(Player observer, String teamName,
                                 com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode mode,
                                 com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo info,
                                 String player) {
        com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams wrapper;
        if (info == null) {
            wrapper = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams(
                    teamName, mode, java.util.Optional.empty(),
                    player == null ? new String[0] : new String[]{player});
        } else if (player == null) {
            wrapper = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams(
                    teamName, mode, info, new String[0]);
        } else {
            wrapper = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams(
                    teamName, mode, info, player);
        }
        com.github.retrooper.packetevents.PacketEvents.getAPI()
                .getPlayerManager().sendPacket(observer, wrapper);
    }

    private void sendDisplayUpdates(Player observer) {
        for (TitleData titleData : data.values()) {
            Player target = Bukkit.getPlayer(titleData.uuid());
            if (target != null && target.isOnline()) {
                sendDisplayUpdate(observer, target, titleData);
            }
        }
    }

    private void sendDisplayUpdate(Player observer, Player target, TitleData titleData) {
        if (!observer.isOnline()) {
            return;
        }
        try {
            updateCachedTabDisplay(titleData, target);
            net.kyori.adventure.text.Component display =
                    legacy(titleData.tabDisplay(com.github.retrooper.packetevents.PacketEvents.getAPI()
                            .getPlayerManager().getPing(target), splitSuffix(titleData.uuid()),
                            exorcismTitle(titleData.uuid())));
            boolean modern = com.github.retrooper.packetevents.PacketEvents.getAPI()
                    .getServerManager().getVersion()
                    .isNewerThanOrEquals(com.github.retrooper.packetevents.manager.server.ServerVersion.V_1_19_3);
            if (modern) {
                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate wrapper =
                        new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate(
                                java.util.EnumSet.of(
                                        com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME),
                                new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                                        titleData.uuid()));
                wrapper.getEntries().get(0).setDisplayName(display);
                com.github.retrooper.packetevents.PacketEvents.getAPI()
                        .getPlayerManager().sendPacket(observer, wrapper);
            } else {
                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.PlayerData playerData =
                        new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.PlayerData(
                                display,
                                new com.github.retrooper.packetevents.protocol.player.UserProfile(
                                        titleData.uuid(), titleData.profileDisplayName()),
                                com.github.retrooper.packetevents.protocol.player.GameMode.SURVIVAL,
                                com.github.retrooper.packetevents.PacketEvents.getAPI()
                                        .getPlayerManager().getPing(target));
                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo wrapper =
                        new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo(
                                com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.Action.UPDATE_DISPLAY_NAME,
                                playerData);
                com.github.retrooper.packetevents.PacketEvents.getAPI()
                        .getPlayerManager().sendPacket(observer, wrapper);
            }
        } catch (Throwable ignored) {
        }
    }

    private static net.kyori.adventure.text.Component legacy(String text) {
        return io.github.retrooper.packetevents.adventure.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(text);
    }

    public String splitSuffix(UUID uuid) {
        return SurvivalSplitBridge.suffixFor(uuid);
    }

    private String exorcismTitle(UUID uuid) {
        return ExorcismTitle.forUuid(uuid);
    }

    public void refresh(UUID uuid) {
        refreshStored(uuid);
    }
}
