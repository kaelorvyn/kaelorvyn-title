package com.kael.title.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;

public final class PluginConfig {

    private final Path dataDirectory;
    private final Logger logger;
    private String databaseHost = "localhost";
    private int databasePort = 3306;
    private String databaseName = "kaeltitle";
    private String databaseUser = "kaeltitle";
    private String databasePassword = "";
    private int cooldownHours = 24;
    private int titleMax = 8;
    private int nicknameMax = 8;
    private final Set<UUID> ownerUuids = new HashSet<>();
    private final Set<UUID> adminUuids = new HashSet<>();
    private String ownerColor = "6";
    private String adminColor = "d";
    private String defaultColor = "b";
    private String ownerTitle = "服主";
    private String adminTitle = "管理员";
    private String defaultTitle = "玩家";
    private String neteaseMappingFile = "D:/MC/server/[25565] 代理端/netease-players.tsv";

    public PluginConfig(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public PluginConfig load() {
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve("config.properties");
            if (!Files.exists(file)) {
                Files.writeString(file, defaultConfig(), StandardCharsets.UTF_8);
                logger.info("已创建默认配置：{}", file);
            }
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(file);
                 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            databaseHost = value(properties, "database.host", databaseHost);
            databasePort = Integer.parseInt(value(properties, "database.port", String.valueOf(databasePort)));
            databaseName = value(properties, "database.name", databaseName);
            databaseUser = value(properties, "database.user", databaseUser);
            databasePassword = value(properties, "database.password", databasePassword);
            cooldownHours = Integer.parseInt(value(properties, "cooldown-hours", String.valueOf(cooldownHours)));
            titleMax = Integer.parseInt(value(properties, "title-max", String.valueOf(titleMax)));
            nicknameMax = Integer.parseInt(value(properties, "nickname-max", String.valueOf(nicknameMax)));
            ownerUuids.addAll(parseUuids(value(properties, "owner-uuids", "")));
            adminUuids.addAll(parseUuids(value(properties, "admin-uuids", "")));
            ownerColor = value(properties, "color.owner", ownerColor);
            adminColor = value(properties, "color.admin", adminColor);
            defaultColor = value(properties, "color.default", defaultColor);
            ownerTitle = value(properties, "title.owner", ownerTitle);
            adminTitle = value(properties, "title.admin", adminTitle);
            defaultTitle = value(properties, "title.default", defaultTitle);
            neteaseMappingFile = value(properties, "netease.mapping-file", neteaseMappingFile);
        } catch (IOException | NumberFormatException e) {
            logger.warn("读取配置失败，使用默认配置：{}", e.getMessage());
        }
        return this;
    }

    private String value(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Set<UUID> parseUuids(String text) {
        Set<UUID> uuids = new HashSet<>();
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                uuids.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return uuids;
    }

    private String defaultConfig() {
        return "database.host=localhost\n"
                + "database.port=3306\n"
                + "database.name=kaeltitle\n"
                + "database.user=kaeltitle\n"
                + "database.password=\n"
                + "cooldown-hours=24\n"
                + "title-max=8\n"
                + "nickname-max=8\n"
                + "owner-uuids=f9a88b70-142a-3dcb-9f91-a151ec729f38\n"
                + "admin-uuids=713fa79b-c7c9-391e-9596-9292cec87f02,003602f4-453e-3f98-aff4-fa5e5c857d5e\n"
                + "color.owner=6\n"
                + "color.admin=d\n"
                + "color.default=b\n"
                + "title.owner=服主\n"
                + "title.admin=管理员\n"
                + "title.default=玩家\n"
                + "netease.mapping-file=D:/MC/server/[25565] 代理端/netease-players.tsv\n";
    }

    public String getDatabaseHost() {
        return databaseHost;
    }

    public int getDatabasePort() {
        return databasePort;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDatabaseUser() {
        return databaseUser;
    }

    public String getDatabasePassword() {
        return databasePassword;
    }

    public int getCooldownHours() {
        return cooldownHours;
    }

    public int getTitleMax() {
        return titleMax;
    }

    public int getNicknameMax() {
        return nicknameMax;
    }

    public Set<UUID> getOwnerUuids() {
        return ownerUuids;
    }

    public Set<UUID> getAdminUuids() {
        return adminUuids;
    }

    public String getOwnerColor() {
        return ownerColor;
    }

    public String getAdminColor() {
        return adminColor;
    }

    public String getDefaultColor() {
        return defaultColor;
    }

    public String getOwnerTitle() {
        return ownerTitle;
    }

    public String getAdminTitle() {
        return adminTitle;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }

    public String getNeteaseMappingFile() {
        return neteaseMappingFile;
    }

    public String colorFor(String rank) {
        if ("owner".equals(rank)) {
            return ownerColor;
        }
        if ("admin".equals(rank)) {
            return adminColor;
        }
        return defaultColor;
    }

    public String titleFor(String rank) {
        if ("owner".equals(rank)) {
            return ownerTitle;
        }
        if ("admin".equals(rank)) {
            return adminTitle;
        }
        return defaultTitle;
    }

    public String rankForUuid(String uuidText) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidText);
        } catch (IllegalArgumentException e) {
            return "default";
        }
        if (ownerUuids.contains(uuid)) {
            return "owner";
        }
        if (adminUuids.contains(uuid)) {
            return "admin";
        }
        return "default";
    }

    public static String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    public static boolean isInternationalName(String name) {
        return name != null && name.length() >= 3 && name.length() <= 16
                && name.matches("[A-Za-z0-9_]+");
    }
}
