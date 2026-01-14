package com.yirankuma.yrdatabase.event;

import cn.nukkit.Player;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.player.PlayerEvent;

/**
 * 玩家数据初始化事件
 *
 * 当玩家真正需要初始化数据时触发（区分转服和真实加入）
 *
 * 触发时机：
 * - 没有WaterdogPE：玩家加入子服时
 * - 有WaterdogPE：收到真实加入消息时
 */
public class PlayerDataInitializeEvent extends PlayerEvent {

    private static final HandlerList handlers = new HandlerList();

    private final String uid;
    private final InitializeReason reason;

    public PlayerDataInitializeEvent(Player player, String uid, InitializeReason reason) {
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
     * 获取初始化原因
     */
    public InitializeReason getReason() {
        return reason;
    }

    /**
     * 是否是真实加入（非转服）
     */
    public boolean isRealJoin() {
        return reason == InitializeReason.REAL_JOIN;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    /**
     * 初始化原因
     */
    public enum InitializeReason {
        /**
         * 真实加入（通过WaterdogPE确认）
         */
        REAL_JOIN,

        /**
         * 本地加入（没有WaterdogPE，无法区分转服）
         */
        LOCAL_JOIN,

        /**
         * 转服（但仍需要加载数据）
         */
        SERVER_TRANSFER
    }
}
