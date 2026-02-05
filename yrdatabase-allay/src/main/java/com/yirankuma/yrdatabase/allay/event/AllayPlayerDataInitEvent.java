package com.yirankuma.yrdatabase.allay.event;

import com.yirankuma.yrdatabase.api.event.PlayerDataInitEvent;
import com.yirankuma.yrdatabase.api.event.SessionReason;
import lombok.Getter;
import org.allaymc.api.eventbus.event.Event;
import org.allaymc.api.player.Player;

/**
 * Allay-specific player data initialization event.
 * Fired when a player joins and their data should be loaded.
 *
 * @author YiranKuma
 */
@Getter
public class AllayPlayerDataInitEvent extends Event implements PlayerDataInitEvent {

    private final Player player;
    private final String playerId;
    private final String playerName;
    private final SessionReason reason;
    private final long timestamp;

    public AllayPlayerDataInitEvent(Player player, String playerId, String playerName, SessionReason reason) {
        this.player = player;
        this.playerId = playerId;
        this.playerName = playerName;
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String getPlayerId() {
        return playerId;
    }

    @Override
    public String getPlayerName() {
        return playerName;
    }

    @Override
    public SessionReason getReason() {
        return reason;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }
}
