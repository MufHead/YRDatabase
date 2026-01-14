package com.yirankuma.yrdatabase.manager;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import com.yirankuma.yrdatabase.YRDatabase;
import com.yirankuma.yrdatabase.event.PlayerDataInitializeEvent;
import com.yirankuma.yrdatabase.event.PlayerDataPersistEvent;
import com.yirankuma.yrdatabase.redis.RedisPubSubListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家数据事件管理器
 *
 * 智能处理玩家数据初始化和持久化事件触发
 * 自动适配是否使用WaterdogPE
 */
public class PlayerDataEventManager implements Listener {

    private final YRDatabase plugin;
    private final Map<String, Long> playerJoinTimes = new ConcurrentHashMap<>();

    public PlayerDataEventManager(YRDatabase plugin) {
        this.plugin = plugin;
    }

    /**
     * 玩家加入子服
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String uid = plugin.resolvePlayerId(player);

        // 记录加入时间
        playerJoinTimes.put(uid, System.currentTimeMillis());

        // 检查是否有WaterdogPE + Redis Pub/Sub
        RedisPubSubListener pubSubListener = plugin.getPubSubListener();

        if (pubSubListener != null) {
            // 有WaterdogPE，检查是否是真实在线
            if (pubSubListener.isRealOnline(uid)) {
                // 真实加入（WaterdogPE已发送REAL_JOIN消息）
                callInitializeEvent(player, uid, PlayerDataInitializeEvent.InitializeReason.REAL_JOIN);
            } else {
                // 可能是转服，暂不触发（等待Redis消息）
                // 但仍然需要加载数据到缓存
                callInitializeEvent(player, uid, PlayerDataInitializeEvent.InitializeReason.SERVER_TRANSFER);
            }
        } else {
            // 没有WaterdogPE，无法区分转服
            callInitializeEvent(player, uid, PlayerDataInitializeEvent.InitializeReason.LOCAL_JOIN);
        }
    }

    /**
     * 玩家退出子服
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String uid = plugin.resolvePlayerId(player);

        // 检查是否有WaterdogPE + Redis Pub/Sub
        RedisPubSubListener pubSubListener = plugin.getPubSubListener();

        if (pubSubListener != null) {
            // 有WaterdogPE，检查是否仍在真实在线列表
            if (pubSubListener.isRealOnline(uid)) {
                // 仍在线，说明是转服，不持久化
                callPersistEvent(player, uid, PlayerDataPersistEvent.PersistReason.SERVER_TRANSFER);
            } else {
                // 已不在线，说明是真实退出，需要持久化
                callPersistEvent(player, uid, PlayerDataPersistEvent.PersistReason.REAL_QUIT);
            }
        } else {
            // 没有WaterdogPE，无法区分转服，全部持久化
            callPersistEvent(player, uid, PlayerDataPersistEvent.PersistReason.LOCAL_QUIT);
        }

        // 清理记录
        playerJoinTimes.remove(uid);
    }

    /**
     * 当收到WaterdogPE的真实加入消息时调用
     */
    public void onRealJoinFromWaterdog(String uid, String username) {
        // 查找玩家
        Player player = findPlayerByUid(uid);

        if (player != null) {
            // 玩家在线，触发真实加入事件
            callInitializeEvent(player, uid, PlayerDataInitializeEvent.InitializeReason.REAL_JOIN);
        }
    }

    /**
     * 当收到WaterdogPE的真实退出消息时调用
     */
    public void onRealQuitFromWaterdog(String uid, String username) {
        // 查找玩家
        Player player = findPlayerByUid(uid);

        if (player != null) {
            // 玩家仍在线（可能还在最后一个子服），触发真实退出事件
            callPersistEvent(player, uid, PlayerDataPersistEvent.PersistReason.REAL_QUIT);
        }
    }

    /**
     * 触发初始化事件
     */
    private void callInitializeEvent(Player player, String uid, PlayerDataInitializeEvent.InitializeReason reason) {
        PlayerDataInitializeEvent event = new PlayerDataInitializeEvent(player, uid, reason);
        plugin.getServer().getPluginManager().callEvent(event);

        plugin.getLogger().debug("触发初始化事件: " + player.getName() + " (UID: " + uid + ", 原因: " + reason + ")");
    }

    /**
     * 触发持久化事件
     */
    private void callPersistEvent(Player player, String uid, PlayerDataPersistEvent.PersistReason reason) {
        PlayerDataPersistEvent event = new PlayerDataPersistEvent(player, uid, reason);
        plugin.getServer().getPluginManager().callEvent(event);

        if (!event.isCancelled() && event.shouldPersist()) {
            plugin.getLogger().debug("触发持久化事件: " + player.getName() + " (UID: " + uid + ", 原因: " + reason + ")");
        }
    }

    /**
     * 根据UID查找玩家
     */
    private Player findPlayerByUid(String uid) {
        for (Player player : plugin.getServer().getOnlinePlayers().values()) {
            if (uid.equals(plugin.resolvePlayerId(player))) {
                return player;
            }
        }
        return null;
    }

    /**
     * 服务器关闭时触发所有在线玩家的持久化
     */
    public void onServerShutdown() {
        for (Player player : plugin.getServer().getOnlinePlayers().values()) {
            String uid = plugin.resolvePlayerId(player);
            callPersistEvent(player, uid, PlayerDataPersistEvent.PersistReason.SERVER_SHUTDOWN);
        }
    }
}
