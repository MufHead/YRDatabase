package com.yirankuma.yrdatabase.event;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.player.PlayerEvent;

/**
 * 玩家数据持久化事件
 *
 * 当玩家数据需要持久化到数据库时触发（区分转服和真实退出）
 *
 * 触发时机：
 * - 没有WaterdogPE：玩家退出子服时
 * - 有WaterdogPE：收到真实退出消息时（转服不触发）
 */
public class PlayerDataPersistEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final String uid;
    private final PersistReason reason;
    private boolean cancelled = false;

    public PlayerDataPersistEvent(Player player, String uid, PersistReason reason) {
        this.player = player;
        this.uid = uid;
        this.reason = reason;
    }

    /**
     * 获取玩家唯一ID
     */
    public String getUid() {
        return uid;
    }

    /**
     * 获取持久化原因
     */
    public PersistReason getReason() {
        return reason;
    }

    /**
     * 是否是真实退出（非转服）
     */
    public boolean isRealQuit() {
        return reason == PersistReason.REAL_QUIT;
    }

    /**
     * 是否应该持久化（非转服）
     */
    public boolean shouldPersist() {
        return reason != PersistReason.SERVER_TRANSFER;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    /**
     * 持久化原因
     */
    public enum PersistReason {
        /**
         * 真实退出（通过WaterdogPE确认）
         */
        REAL_QUIT,

        /**
         * 本地退出（没有WaterdogPE，无法区分转服）
         */
        LOCAL_QUIT,

        /**
         * 转服（不应该持久化）
         */
        SERVER_TRANSFER,

        /**
         * 服务器关闭
         */
        SERVER_SHUTDOWN
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
