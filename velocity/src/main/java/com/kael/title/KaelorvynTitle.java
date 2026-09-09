package com.kael.title;

import com.google.inject.Inject;
import com.kael.title.command.TitleCommand;
import com.kael.title.config.PluginConfig;
import com.kael.title.storage.TitleDatabase;
import com.kael.title.storage.NeteaseRegistry;
import com.kael.title.sync.SyncService;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

@Plugin(
        id = "kaelorvyntitle",
        name = "KaelorvynTitle",
        version = "1.0.0",
        description = "Kaelorvyn 跨服称号插件",
        authors = {"Kael"}
)
public final class KaelorvynTitle {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private TitleDatabase database;
    private SyncService sync;
    private NeteaseRegistry neteaseRegistry;

    @Inject
    public KaelorvynTitle(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        PluginConfig config = new PluginConfig(dataDirectory, logger).load();
        database = new TitleDatabase(config, logger);
        database.ensureTables();
        neteaseRegistry = new NeteaseRegistry(config.getNeteaseMappingFile());
        sync = new SyncService(server, database, config, logger, neteaseRegistry);
        server.getChannelRegistrar().register(SyncService.CHANNEL);

        CommandMeta ktitle = server.getCommandManager().metaBuilder("ktitle")
                .aliases("称号").plugin(this).build();
        server.getCommandManager().register(ktitle, new TitleCommand(server, database, sync, config));
        CommandMeta knick = server.getCommandManager().metaBuilder("knick")
                .aliases("昵称").plugin(this).build();
        server.getCommandManager().register(knick, new TitleCommand(server, database, sync, config));

        logger.info("KaelorvynTitle 已启动，数据库可用：{}", database.available());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (sync != null) {
            sync.handlePluginMessage(event);
        }
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        com.velocitypowered.api.proxy.Player player = event.getPlayer();
        server.getScheduler().buildTask(this, () -> {
            if (sync != null && player.isActive()) {
                sync.pushToCurrent(player);
            }
        }).delay(10, TimeUnit.SECONDS).schedule();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (database != null) {
            database.close();
        }
        logger.info("KaelorvynTitle 已关闭。");
    }
}
