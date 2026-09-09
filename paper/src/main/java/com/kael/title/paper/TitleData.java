package com.kael.title.paper;

import java.util.UUID;

public final class TitleData {

    private final UUID uuid;
    private final String playerName;
    private final String title;
    private final String nickname;
    private final String color;
    private final String defaultTitle;
    private final boolean netease;
    private final String originalName;

    public TitleData(UUID uuid, String playerName, String title, String nickname,
                     String color, String defaultTitle) {
        this(uuid, playerName, title, nickname, color, defaultTitle, false, "");
    }

    public TitleData(UUID uuid, String playerName, String title, String nickname,
                     String color, String defaultTitle, boolean netease) {
        this(uuid, playerName, title, nickname, color, defaultTitle, netease, "");
    }

    public TitleData(UUID uuid, String playerName, String title, String nickname,
                     String color, String defaultTitle, boolean netease, String originalName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.title = title == null ? "" : title;
        this.nickname = nickname == null ? "" : nickname;
        this.color = color == null || color.isEmpty() ? "b" : color;
        this.defaultTitle = defaultTitle == null || defaultTitle.isEmpty() ? "玩家" : defaultTitle;
        this.netease = netease;
        this.originalName = originalName == null ? "" : originalName;
    }

    public UUID uuid() {
        return uuid;
    }

    public String playerName() {
        return playerName;
    }

    public String title() {
        return title;
    }

    public String nickname() {
        return nickname;
    }

    public String color() {
        return color;
    }

    public String defaultTitle() {
        return defaultTitle;
    }

    public boolean netease() {
        return netease;
    }

    public String originalName() {
        return originalName;
    }

    public String effectiveTitle() {
        return title.isEmpty() ? defaultTitle : title;
    }

    public String effectiveTitle(String titleOverride) {
        return titleOverride == null || titleOverride.isEmpty() ? effectiveTitle() : titleOverride;
    }

    public boolean hasNickname() {
        return !nickname.isEmpty();
    }

    public String profileDisplayName() {
        return playerName;
    }

    public String visibleName() {
        if (netease && !originalName.isEmpty()) {
            return originalName;
        }
        return hasNickname() ? nickname : playerName;
    }

    public String fullDisplay() {
        return fullDisplay("");
    }

    public String fullDisplay(String splitSuffix) {
        return fullDisplay(splitSuffix, "");
    }

    public String fullDisplay(String splitSuffix, String titleOverride) {
        StringBuilder builder = new StringBuilder();
        builder.append('\u00A7').append(color).append('[').append(effectiveTitle(titleOverride)).append(']');
        if (netease) {
            builder.append('\u00A7').append('r').append(visibleName());
        } else if (hasNickname()) {
            builder.append('\u00A7').append('r').append(nickname)
                    .append('(').append(playerName).append(')');
        } else {
            builder.append(' ').append('\u00A7').append('r').append(playerName);
        }
        if (splitSuffix != null && !splitSuffix.isEmpty()) {
            builder.append(splitSuffix);
        }
        return builder.toString();
    }

    public String tabDisplay(int ping) {
        return tabDisplay(ping, "");
    }

    public String tabDisplay(int ping, String splitSuffix) {
        return tabDisplay(ping, splitSuffix, "");
    }

    public String tabDisplay(int ping, String splitSuffix, String titleOverride) {
        StringBuilder display = new StringBuilder();
        display.append('\u00A7').append(color).append('[').append(effectiveTitle(titleOverride)).append(']');
        if (netease) {
            display.append('\u00A7').append('r').append(visibleName())
                    .append('（').append(playerName).append('）');
        } else if (hasNickname()) {
            display.append('\u00A7').append('r').append(nickname)
                    .append('(').append(playerName).append(')');
        } else {
            display.append(' ').append('\u00A7').append('r').append(playerName);
        }
        if (splitSuffix != null && !splitSuffix.isEmpty()) {
            display.append(splitSuffix);
        }
        return display.append(" §7[§a").append(ping).append("ms§7]").toString();
    }

    public String teamPrefix() {
        return teamPrefix("");
    }

    public String teamPrefix(String titleOverride) {
        StringBuilder builder = new StringBuilder();
        builder.append('\u00A7').append(color).append('[').append(effectiveTitle(titleOverride)).append(']');
        if (netease) {
            builder.append('\u00A7').append('r').append(visibleName()).append('（');
        } else if (hasNickname()) {
            builder.append('\u00A7').append('r').append(nickname).append('(');
        } else {
            builder.append(' ').append('\u00A7').append('r');
        }
        return builder.toString();
    }

    public String teamSuffix() {
        return teamSuffix("");
    }

    public String teamSuffix(String splitSuffix) {
        StringBuilder suffix = new StringBuilder();
        if (netease) {
            suffix.append('）');
        } else if (hasNickname()) {
            suffix.append(')');
        }
        if (splitSuffix != null && !splitSuffix.isEmpty()) {
            suffix.append(splitSuffix);
        }
        return suffix.toString();
    }

    public String[] teamChunks() {
        return teamChunks("");
    }

    public String[] teamChunks(String splitSuffix) {
        return teamChunks(splitSuffix, "");
    }

    public String[] teamChunks(String splitSuffix, String titleOverride) {
        return new String[]{teamPrefix(titleOverride), playerName, teamSuffix(splitSuffix)};
    }
}
