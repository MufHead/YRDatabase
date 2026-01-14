package com.yirankuma.yrdatabase.waterdog.config;

/**
 * WaterdogPE插件配置类
 *
 * 配置Redis连接用于Pub/Sub通信
 */
public class WaterdogConfig {

    public static class Redis {
        private boolean enabled = true;
        private String host = "localhost";
        private int port = 6379;
        private String password = "";
        private int database = 0;
        private int timeout = 5000;

        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public int getDatabase() { return database; }
        public void setDatabase(int database) { this.database = database; }

        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
    }

    private Redis redis = new Redis();

    public Redis getRedis() { return redis; }
    public void setRedis(Redis redis) { this.redis = redis; }
}
