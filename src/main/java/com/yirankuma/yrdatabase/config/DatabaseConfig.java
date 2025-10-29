package com.yirankuma.yrdatabase.config;

import com.google.gson.annotations.SerializedName;

public class DatabaseConfig {
    
    public static class Redis {
        private boolean enabled = true;
        private String host = "localhost";
        private int port = 6379;
        private String password = "";
        private int database = 0;
        private int timeout = 5000;
        private int maxConnections = 20;
        
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
        
        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
    }
    
    public static class MySQL {
        private boolean enabled = true;
        private String host = "localhost";
        private int port = 3306;
        private String database = "yrdatabase";
        private String username = "root";
        private String password = "";
        private String timezone = "Asia/Shanghai";  // 新增时区配置
        private int maxPoolSize = 10;
        private int minIdle = 2;
        private long connectionTimeout = 30000;
        private long idleTimeout = 600000;
        private long maxLifetime = 1800000;
        
        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        
        public int getMinIdle() { return minIdle; }
        public void setMinIdle(int minIdle) { this.minIdle = minIdle; }
        
        public long getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(long connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        
        public long getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(long idleTimeout) { this.idleTimeout = idleTimeout; }
        
        public long getMaxLifetime() { return maxLifetime; }
        public void setMaxLifetime(long maxLifetime) { this.maxLifetime = maxLifetime; }
        
        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
    }
    
    private Redis redis = new Redis();
    private MySQL mysql = new MySQL();

    @SerializedName("UseNeteaseUid")
    private boolean useNeteaseUid = false;

    public boolean isUseNeteaseUid() { return useNeteaseUid; }
    public void setUseNeteaseUid(boolean useNeteaseUid) { this.useNeteaseUid = useNeteaseUid; }

    public Redis getRedis() { return redis; }
    public void setRedis(Redis redis) { this.redis = redis; }

    public MySQL getMysql() { return mysql; }
    public void setMysql(MySQL mysql) { this.mysql = mysql; }
}