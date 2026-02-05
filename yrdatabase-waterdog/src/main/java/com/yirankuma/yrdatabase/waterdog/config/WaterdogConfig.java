package com.yirankuma.yrdatabase.waterdog.config;

import lombok.Data;

/**
 * WaterdogPE plugin configuration.
 *
 * @author YiranKuma
 */
@Data
public class WaterdogConfig {

    /**
     * Redis configuration for cross-server messaging.
     */
    private Redis redis = new Redis();

    /**
     * Heartbeat configuration.
     */
    private Heartbeat heartbeat = new Heartbeat();

    /**
     * Debug mode.
     */
    private boolean debug = false;

    @Data
    public static class Redis {
        private boolean enabled = true;
        private String host = "localhost";
        private int port = 6379;
        private String password = "";
        private int database = 0;
        private int timeout = 5000;

        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getPassword() { return password; }
        public int getDatabase() { return database; }
        public int getTimeout() { return timeout; }
    }

    @Data
    public static class Heartbeat {
        private boolean enabled = true;
        private int intervalSeconds = 10;
    }
}
