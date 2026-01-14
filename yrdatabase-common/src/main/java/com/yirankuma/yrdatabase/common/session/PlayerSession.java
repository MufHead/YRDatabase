package com.yirankuma.yrdatabase.common.session;

/**
 * 玩家会话信息
 * 记录玩家的在线状态和服务器位置
 */
public class PlayerSession {
    private final long uid;
    private final String username;
    private boolean online;
    private String currentServer;
    private long loginTime;
    private long lastHeartbeat;

    public PlayerSession(long uid, String username) {
        this.uid = uid;
        this.username = username;
        this.online = false;
        this.currentServer = null;
        this.loginTime = 0;
        this.lastHeartbeat = 0;
    }

    // ============ Getters & Setters ============

    public long getUid() {
        return uid;
    }

    public String getUsername() {
        return username;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getCurrentServer() {
        return currentServer;
    }

    public void setCurrentServer(String currentServer) {
        this.currentServer = currentServer;
    }

    public long getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(long loginTime) {
        this.loginTime = loginTime;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    // ============ 便捷方法 ============

    /**
     * 标记玩家登录
     */
    public void markLogin(String serverName) {
        this.online = true;
        this.currentServer = serverName;
        this.loginTime = System.currentTimeMillis();
        this.lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * 标记玩家退出
     */
    public void markLogout() {
        this.online = false;
        this.currentServer = null;
    }

    /**
     * 更新心跳
     */
    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * 检查会话是否超时
     */
    public boolean isTimeout(long timeoutMs) {
        return System.currentTimeMillis() - lastHeartbeat > timeoutMs;
    }

    /**
     * 获取在线时长（毫秒）
     */
    public long getOnlineDuration() {
        if (!online || loginTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - loginTime;
    }

    @Override
    public String toString() {
        return "PlayerSession{" +
                "uid=" + uid +
                ", username='" + username + '\'' +
                ", online=" + online +
                ", currentServer='" + currentServer + '\'' +
                ", loginTime=" + loginTime +
                ", lastHeartbeat=" + lastHeartbeat +
                '}';
    }
}
