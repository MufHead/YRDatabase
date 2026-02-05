package com.yirankuma.yrdatabase.allay.event;

import com.yirankuma.yrdatabase.api.event.SessionReason;
import com.yirankuma.yrdatabase.api.session.SessionEventData;
import lombok.Getter;
import org.allaymc.api.eventbus.event.Event;
import org.allaymc.api.player.Player;

/**
 * Allay-specific player transfer event.
 * Fired when a player transfers between servers in a proxy network.
 *
 * <p><b>IMPORTANT:</b> Plugins should NOT persist data when receiving this event.
 * The player is just moving between servers, and their data should remain in Redis cache
 * for the destination server to access.</p>
 *
 * @author YiranKuma
 */
@Getter
public class AllayPlayerTransferEvent extends Event {

    private final SessionEventData data;
    private final Player player; // May be null if player is not on this server

    public AllayPlayerTransferEvent(SessionEventData data, Player player) {
        this.data = data;
        this.player = player;
    }

    public String getPlayerId() {
        return data.getPlayerId();
    }

    public String getPlayerName() {
        return data.getPlayerName();
    }

    public SessionReason getReason() {
        return data.getReason();
    }

    public long getTimestamp() {
        return data.getTimestamp();
    }

    public String getFromServer() {
        return data.getFromServer();
    }

    public String getToServer() {
        return data.getToServer();
    }

    public boolean isFromThisServer(String thisServerName) {
        return thisServerName != null && thisServerName.equals(data.getFromServer());
    }

    public boolean isToThisServer(String thisServerName) {
        return thisServerName != null && thisServerName.equals(data.getToServer());
    }
}
