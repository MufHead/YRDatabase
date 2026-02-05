package com.yirankuma.yrdatabase.allay.event;

import com.yirankuma.yrdatabase.api.event.SessionReason;
import com.yirankuma.yrdatabase.api.session.SessionEventData;
import lombok.Getter;
import org.allaymc.api.eventbus.event.CancellableEvent;
import org.allaymc.api.eventbus.event.Event;
import org.allaymc.api.player.Player;

/**
 * Allay-specific player real quit event.
 * Fired when a player truly quits the proxy network (or locally in standalone mode).
 *
 * <p><b>IMPORTANT:</b> Plugins should persist player data when receiving this event.
 * This event indicates the player has truly left and data should be saved from cache to database.</p>
 *
 * @author YiranKuma
 */
@Getter
public class AllayPlayerRealQuitEvent extends Event implements CancellableEvent {

    private final SessionEventData data;
    private final Player player; // May be null if player has already disconnected

    public AllayPlayerRealQuitEvent(SessionEventData data, Player player) {
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

    public String getLastServer() {
        return data.getFromServer();
    }

    /**
     * Check if data should be persisted.
     * This considers both the session reason and cancellation state.
     */
    public boolean shouldPersist() {
        return !isCancelled() && data.shouldPersist();
    }
}
