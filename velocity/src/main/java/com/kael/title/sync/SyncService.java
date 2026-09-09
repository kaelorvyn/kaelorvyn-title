package com.kael.title.sync;

import com.kael.title.config.PluginConfig;
import com.kael.title.storage.TitleDatabase;
import com.kael.title.storage.TitleDatabase.TitleRecord;
import com.kael.title.storage.NeteaseRegistry;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;

public final class SyncService {

    public static final ChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("kaeltitle", "main");

    private final ProxyServer server;
    private final TitleDatabase database;
    private final PluginConfig config;
    private final Logger logger;
    private final NeteaseRegistry neteaseRegistry;

    public SyncService(ProxyServer server, TitleDatabase database, PluginConfig config, Logger logger,
                       NeteaseRegistry neteaseRegistry) {
        this.server = server;
        this.database = database;
        this.config = config;
        this.logger = logger;
        this.neteaseRegistry = neteaseRegistry;
    }

    public ChannelIdentifier channel() {
        return CHANNEL;
    }

    public void handlePluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().getId().equals(CHANNEL.getId())) {
            return;
        }
        if (event.getSource() instanceof ServerConnection connection) {
            String text = new String(event.getData(), StandardCharsets.UTF_8);
            String[] parts = text.split("\\t", -1);
            if (parts.length >= 3 && "REQ".equals(parts[0])) {
                try {
                    UUID uuid = UUID.fromString(parts[1]);
                    String name = parts[2];
                    logger.info("收到子服补问 REQ: {} ({})", name, uuid);
                    byte[] payload = buildPayload("SYNC", uuid, name).getBytes(StandardCharsets.UTF_8);
                    connection.getServer().sendPluginMessage(CHANNEL, payload);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }

    public void pushToCurrent(Player player) {
        Optional<ServerConnection> connection = player.getCurrentServer();
        if (connection.isPresent()) {
            logger.info("延迟推送 SYNC -> {} 给 {}", connection.get().getServerInfo().getName(),
                    player.getUsername());
            byte[] payload = buildPayload("SYNC", player.getUniqueId(), player.getUsername())
                    .getBytes(StandardCharsets.UTF_8);
            connection.get().getServer().sendPluginMessage(CHANNEL, payload);
        }
    }

   public void broadcastUpdate(UUID uuid, String name) {
        logger.info("广播 UPD 给全部子服: {} ({})", name, uuid);
        byte[] payload = buildPayload("UPD", uuid, name).getBytes(StandardCharsets.UTF_8);
        for (RegisteredServer registeredServer : server.getAllServers()) {
            if (registeredServer.getPlatform().equals("paper")) {
                registeredServer.sendPluginMessage(CHANNEL, payload);
                continue;
            }
            try {
                registeredServer.getProxyServer().getServerConnector().sendPluginMessage(CHANNEL, payload);
            } catch (Exception e) {
                logger.warn("向子服 {} 发送 UPD 失败：{}", registeredServer.getDisplayName(), e.getMessage());
            }
       }
   }

    public String buildPayload(String type, UUID uuid, String name) {
        Optional<TitleRecord> record = database.get(uuid.toString(), name);
        String rank = rankFor(uuid);
        String title = record.map(TitleRecord::getTitle).orElse("");
        String nickname = record.map(TitleRecord::getNickname).orElse("");
        String originalName = neteaseRegistry.originalName(name);
        return type + "\t" + uuid + "\t" + name + "\t" + title + "\t"
                + (nickname == null ? "" : nickname) + "\t"
                + config.colorFor(rank) + "\t" + config.titleFor(rank) + "\t"
                + (originalName != null) + "\t" + (originalName == null ? "" : originalName);
    }

    public boolean isNetease(String name) {
        return neteaseRegistry.originalName(name) != null;
    }

    private String rankFor(UUID uuid) {
        Optional<Player> player = server.getPlayer(uuid);
        if (player.isPresent()) {
            if (config.getOwnerUuids().contains(uuid)
                    || player.get().hasPermission("ktitle.owner")) {
                return "owner";
            }
            if (config.getAdminUuids().contains(uuid)
                    || player.get().hasPermission("ktitle.admin")) {
                return "admin";
            }
        }
        return config.rankForUuid(uuid.toString());
    }
}
