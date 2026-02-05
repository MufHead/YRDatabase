package com.yirankuma.yrdatabase.nukkit.event;

import cn.nukkit.Player;
import cn.nukkit.event.HandlerList;
import com.yirankuma.yrdatabase.api.session.SessionEventData;

/**
 * Event fired when a player truly joins the proxy network.
 * 
 * <p>In standalone mode, this is fired when a player joins the server.</p>
 * <p>In proxy mode, this is fired when Waterdog confirms the player joined the network.</p>
 *
 * <p>Plugins can use this event to initialize player data or load cached data.</p>
 *
 * @author YiranKuma
 */
public class NukkitPlayerRealJoinEvent extends NukkitSessionEvent {

    private static final HandlerList handlers = new HandlerList();

    public NukkitPlayerRealJoinEvent(SessionEventData data, Player player) {
        super(data, player);
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
