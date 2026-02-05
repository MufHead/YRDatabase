package com.yirankuma.yrdatabase.allay.event;

import com.yirankuma.yrdatabase.api.event.SessionReason;
import com.yirankuma.yrdatabase.api.session.SessionEventData;
import lombok.Getter;
import org.allaymc.api.eventbus.event.Event;
import org.allaymc.api.player.Player;

/**
 * Allay-specific player real join event.
 * Fired when a player truly joins the proxy network (or locally in standalone mode).
 *
 * @author YiranKuma
 */
@Getter
public class AllayPlayerRealJoinEvent extends Event {

    private final SessionEventData data;
    private final Player player; // May be null if player is not on this server

    public AllayPlayerRealJoinEvent(SessionEventData data, Player player) {
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

    public boolean isOnThisServer() {
        return player != null;
    }
}
