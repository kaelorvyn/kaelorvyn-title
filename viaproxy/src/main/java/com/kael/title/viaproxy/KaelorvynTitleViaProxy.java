package com.kael.title.viaproxy;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.lenni0451.lambdaevents.EventHandler;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.plugins.ViaProxyPlugin;
import net.raphimc.viaproxy.plugins.events.ClientLoggedInEvent;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import net.raphimc.netminecraft.packet.impl.login.C2SLoginHelloPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class KaelorvynTitleViaProxy extends ViaProxyPlugin {

    private static final Logger LOGGER = LogManager.getLogger("KaelorvynTitleViaProxy");
    private static final String PREFIX = "Netease";
    private final Map<String, Entry> byOriginal = new HashMap<>();
    private final Map<String, Entry> byInternal = new HashMap<>();
    private final Set<String> internationalIps = new HashSet<>();
    private Path mappingFile;
    private Path internationalIpsFile;

    @Override
    public void onEnable() {
        String configured = System.getProperty("kaeltitle.netease-mapping",
                "D:/MC/server/[25565] 代理端/netease-players.tsv");
        mappingFile = Path.of(configured).toAbsolutePath().normalize();
        internationalIpsFile = mappingFile.resolveSibling(mappingFile.getFileName() + ".international-ips");
        load();
        loadInternationalIps();
        ViaProxy.EVENT_MANAGER.register(this);
        LOGGER.info("网易名称映射已启用，文件：{}，国际版基准 IP：{}", mappingFile, internationalIpsFile);
    }

    @Override
    public void onDisable() {
        ViaProxy.EVENT_MANAGER.unregister(this);
    }

    @EventHandler(priority = Integer.MIN_VALUE)
    public void onClientLoggedIn(ClientLoggedInEvent event) {
        ProxyConnection connection = event.getProxyConnection();
        C2SLoginHelloPacket hello = connection.getLoginHelloPacket();
        if (hello == null) {
            return;
        }
        String clientIp = clientIp(connection);
        if (isSteveXiong(hello.name)) {
            if (clientIp != null && internationalIps.add(clientIp)) {
                saveInternationalIps();
                LOGGER.info("记录 SteveXiong 国际版基准 IP：{}", clientIp);
            }
            return;
        }
        boolean sameAsSteve = clientIp != null && internationalIps.contains(clientIp);
        if (!sameAsSteve && isInternationalName(hello.name)) {
            return;
        }
        synchronized (this) {
            Entry entry = byOriginal.get(hello.name);
            if (entry == null) {
                entry = allocate(hello.name);
                byOriginal.put(entry.originalName(), entry);
                byInternal.put(entry.internalName().toLowerCase(), entry);
                save();
            }
            hello.name = entry.internalName();
            hello.uuid = offlineUuid(entry.internalName());
            connection.setLoginHelloPacket(hello);
            connection.setGameProfile(new GameProfile(hello.uuid, entry.internalName()));
            LOGGER.info("网易玩家 {} 映射为 {}（来源 IP：{}，同 SteveXiong：{}）",
                    entry.originalName(), entry.internalName(), clientIp == null ? "unknown" : clientIp, sameAsSteve);
        }
    }

    private static boolean isSteveXiong(String name) {
        return name != null && name.equalsIgnoreCase("SteveXiong");
    }

    private static String clientIp(ProxyConnection connection) {
        if (connection == null || connection.getC2P() == null) {
            return null;
        }
        SocketAddress address = connection.getC2P().remoteAddress();
        if (address instanceof InetSocketAddress inet) {
            return inet.getAddress() == null ? inet.getHostString() : inet.getAddress().getHostAddress();
        }
        return null;
    }

    private Entry allocate(String originalName) {
        Set<Long> used = new HashSet<>();
        for (Entry entry : byInternal.values()) {
            try {
                used.add(Long.parseLong(entry.internalName().substring(PREFIX.length())));
            } catch (RuntimeException ignored) {
            }
        }
        long number = 1;
        while (used.contains(number)) {
            number++;
        }
        return new Entry(originalName, PREFIX + number);
    }

    private void load() {
        if (!Files.exists(mappingFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(mappingFile, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\t", -1);
                if (parts.length != 2) {
                    continue;
                }
                String original = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                Entry entry = new Entry(original, parts[0]);
                byOriginal.put(original, entry);
                byInternal.put(entry.internalName().toLowerCase(), entry);
            }
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.error("读取网易名称映射失败：{}", mappingFile, e);
        }
    }

    private void loadInternationalIps() {
        if (!Files.exists(internationalIpsFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(internationalIpsFile, StandardCharsets.UTF_8)) {
                String ip = line.trim();
                if (!ip.isEmpty() && !ip.startsWith("#")) {
                    internationalIps.add(ip);
                }
            }
        } catch (IOException e) {
            LOGGER.error("读取国际版基准 IP 失败：{}", internationalIpsFile, e);
        }
    }

    private void saveInternationalIps() {
        try {
            Path parent = internationalIpsFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            StringBuilder text = new StringBuilder();
            for (String ip : internationalIps) {
                text.append(ip).append('\n');
            }
            Files.writeString(internationalIpsFile, text.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            LOGGER.error("保存国际版基准 IP 失败：{}", internationalIpsFile, e);
        }
    }

    private void save() {
        try {
            Path parent = mappingFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            StringBuilder text = new StringBuilder();
            for (Entry entry : byInternal.values()) {
                text.append(entry.internalName()).append('\t')
                        .append(Base64.getEncoder().encodeToString(
                                entry.originalName().getBytes(StandardCharsets.UTF_8)))
                        .append('\n');
            }
            Files.writeString(mappingFile, text.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            LOGGER.error("保存网易名称映射失败：{}", mappingFile, e);
        }
    }

    private static boolean isInternationalName(String name) {
        return name != null && name.length() >= 3 && name.length() <= 16
                && name.matches("[A-Za-z0-9_]+");
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name)
                .getBytes(StandardCharsets.UTF_8));
    }

    private record Entry(String originalName, String internalName) {
    }
}
