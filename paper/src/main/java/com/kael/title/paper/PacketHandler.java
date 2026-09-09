package com.kael.title.paper;

import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import java.util.UUID;

public final class PacketHandler extends com.github.retrooper.packetevents.event.SimplePacketListenerAbstract {

    private final TitleStore store;
    private PacketListenerCommon handle;

    public PacketHandler(TitleStore store) {
        this.store = store;
    }

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        try {
            if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO) {
                rewriteLegacyPlayerInfo(event);
            } else if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
                rewriteModernPlayerInfo(event);
            }
        } catch (Throwable t) {
            store.plugin().getLogger().log(java.util.logging.Level.WARNING,
                    "PlayerInfo 重写失败 " + event.getPacketType(), t);
        }
    }

    private void rewriteLegacyPlayerInfo(PacketPlaySendEvent event) {
        WrapperPlayServerPlayerInfo wrapper = new WrapperPlayServerPlayerInfo(event);
        if (wrapper.getAction() != WrapperPlayServerPlayerInfo.Action.ADD_PLAYER
                && wrapper.getAction() != WrapperPlayServerPlayerInfo.Action.UPDATE_DISPLAY_NAME) {
            return;
        }
        for (WrapperPlayServerPlayerInfo.PlayerData playerData : wrapper.getPlayerDataList()) {
            UUID uuid = playerData.getUserProfile().getUUID();
            TitleData titleData = store.get(uuid);
            if (titleData == null) {
                continue;
            }
            store.plugin().getLogger().info("重写 PlayerInfo " + event.getPacketType() + " uuid=" + uuid);
            String display = store.cachedTabDisplay(uuid);
            if (display != null) {
                playerData.setDisplayName(legacy(display));
            }
        }
    }

    private void rewriteModernPlayerInfo(PacketPlaySendEvent event) {
        WrapperPlayServerPlayerInfoUpdate wrapper = new WrapperPlayServerPlayerInfoUpdate(event);
        for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo : wrapper.getEntries()) {
            UUID uuid = playerInfo.getProfileId();
            TitleData titleData = store.get(uuid);
            if (titleData == null) {
                continue;
            }
            store.plugin().getLogger().info("重写 PlayerInfoUpdate " + event.getPacketType() + " uuid=" + uuid);
            if (wrapper.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER)) {
                wrapper.getActions().add(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME);
            }
            if (wrapper.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME)) {
            String display = store.cachedTabDisplay(uuid);
            if (display != null) {
                playerInfo.setDisplayName(legacy(display));
            }
            }
        }
    }

    private static net.kyori.adventure.text.Component legacy(String text) {
        return io.github.retrooper.packetevents.adventure.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(text);
    }

    public void register() {
        handle = com.github.retrooper.packetevents.PacketEvents.getAPI()
                .getEventManager().registerListener(this);
    }

    public void unregister() {
        if (handle != null) {
            com.github.retrooper.packetevents.PacketEvents.getAPI()
                    .getEventManager().unregisterListener(handle);
        }
    }
}
