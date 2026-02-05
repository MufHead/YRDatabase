package com.yirankuma.yrdatabase.api.event;

/**
 * Base interface for player data events.
 * Platform-specific implementations will extend this.
 *
 * @author YiranKuma
 */
public interface PlayerDataEvent {

    /**
     * Get the player's unique identifier.
     *
     * @return Player ID (UUID or platform-specific ID)
     */
    String getPlayerId();

    /**
     * Get the player's display name.
     *
     * @return Player name
     */
    String getPlayerName();

    /**
     * Get the reason for this event.
     *
     * @return Session reason
     */
    SessionReason getReason();

    /**
     * Get the timestamp when this event occurred.
     *
     * @return Unix timestamp in milliseconds
     */
    long getTimestamp();
}
