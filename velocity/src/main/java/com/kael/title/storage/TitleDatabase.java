package com.kael.title.storage;

import com.kael.title.config.PluginConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Optional;
import org.slf4j.Logger;

public final class TitleDatabase {

    private final PluginConfig config;
    private final Logger logger;
    private volatile boolean available;

    public TitleDatabase(PluginConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            logger.warn("无法加载 MariaDB 驱动：{}", e.getMessage());
        }
    }

    public boolean available() {
        if (available) {
            return true;
        }
        try (Connection connection = open()) {
            available = true;
            return true;
        } catch (SQLException e) {
            available = false;
            return false;
        }
    }

    public void ensureTables() {
        String sql = "CREATE TABLE IF NOT EXISTS player_titles ("
                + "uuid CHAR(36) PRIMARY KEY,"
                + "player_name VARCHAR(16) NOT NULL,"
                + "title VARCHAR(16) NOT NULL DEFAULT '',"
                + "nickname VARCHAR(16) NULL,"
                + "last_nick_change BIGINT NOT NULL DEFAULT 0,"
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
            available = true;
            logger.info("kaeltitle 数据表检查完成。");
        } catch (SQLException e) {
            available = false;
            logger.warn("数据库初始化失败：{}", e.getMessage());
        }
    }

    public Optional<TitleRecord> get(String uuid, String playerName) {
        String sql = "SELECT uuid, player_name, title, nickname, last_nick_change "
                + "FROM player_titles WHERE uuid = ? LIMIT 1";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(new TitleRecord(
                            result.getString("uuid"),
                            result.getString("player_name"),
                            result.getString("title"),
                            result.getString("nickname"),
                            result.getLong("last_nick_change")));
                }
            }
        } catch (SQLException e) {
            logQueryError("查询称号", e);
        }
        return Optional.empty();
    }

    public void setTitle(String uuid, String playerName, String title) {
        String sql = "INSERT INTO player_titles (uuid, player_name, title) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), title = VALUES(title)";
        update(sql, uuid, playerName, title);
    }

    public void clearTitle(String uuid, String playerName) {
        String sql = "INSERT INTO player_titles (uuid, player_name, title) VALUES (?, ?, '') "
                + "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), title = ''";
        update(sql, uuid, playerName, null);
    }

    public void setNickname(String uuid, String playerName, String nickname, long now) {
        String sql = "INSERT INTO player_titles (uuid, player_name, nickname, last_nick_change) "
                + "VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), "
                + "nickname = VALUES(nickname), last_nick_change = VALUES(last_nick_change)";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setString(2, playerName);
            statement.setString(3, nickname);
            statement.setLong(4, now);
            statement.executeUpdate();
        } catch (SQLException e) {
            logQueryError("写入昵称", e);
        }
    }

    public void clearNickname(String uuid, String playerName) {
        String sql = "INSERT INTO player_titles (uuid, player_name, nickname) "
                + "VALUES (?, ?, NULL) "
                + "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), nickname = NULL";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setString(2, playerName);
            statement.executeUpdate();
        } catch (SQLException e) {
            logQueryError("清除昵称", e);
        }
    }

    public void close() {
        logger.info("kaeltitle 数据库连接已释放。");
    }

    private void update(String sql, String uuid, String playerName, String value) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setString(2, playerName);
            if (value != null) {
                statement.setString(3, value);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            logQueryError("更新称号", e);
        }
    }

    private Connection open() throws SQLException {
        String url = "jdbc:mariadb://" + config.getDatabaseHost() + ":" + config.getDatabasePort() + "/"
                + config.getDatabaseName()
                + "?useSSL=false&characterEncoding=utf8&connectTimeout=3000&socketTimeout=3000"
                + "&allowPublicKeyRetrieval=true";
        return DriverManager.getConnection(url, config.getDatabaseUser(), config.getDatabasePassword());
    }

    private void logQueryError(String action, SQLException e) {
        available = false;
        logger.warn("{}失败：{}", action, e.getMessage());
    }

    public static final class TitleRecord {

        private final String uuid;
        private final String playerName;
        private final String title;
        private final String nickname;
        private final long lastNickChange;

        public TitleRecord(String uuid, String playerName, String title, String nickname, long lastNickChange) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.title = title == null ? "" : title;
            this.nickname = nickname == null ? "" : nickname;
            this.lastNickChange = lastNickChange;
        }

        public String getUuid() {
            return uuid;
        }

        public String getPlayerName() {
            return playerName;
        }

        public String getTitle() {
            return title;
        }

        public String getNickname() {
            return nickname;
        }

        public long getLastNickChange() {
            return lastNickChange;
        }
    }
}
