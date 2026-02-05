package com.yirankuma.yrdatabase.nukkit;

import cn.nukkit.Player;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import com.yirankuma.yrdatabase.api.DatabaseManager;
import com.yirankuma.yrdatabase.api.config.DatabaseConfig;
import com.yirankuma.yrdatabase.api.session.SessionManager;
import com.yirankuma.yrdatabase.core.DatabaseManagerImpl;
import com.yirankuma.yrdatabase.nukkit.command.YRDBCommand;
import com.yirankuma.yrdatabase.nukkit.listener.PlayerEventListener;
import com.yirankuma.yrdatabase.nukkit.session.NukkitSessionBridge;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * YRDatabase plugin for NukkitMOT.
 * 
 * <p>Provides database management with Redis caching and MySQL/SQLite persistence.</p>
 *
 * @author YiranKuma
 */
public class YRDatabaseNukkit extends PluginBase {

    private static YRDatabaseNukkit instance;
    private static DatabaseManager databaseManager;
    private static NukkitSessionBridge sessionBridge;
    
    private Config pluginConfig;
    private boolean proxyMode = false;

    @Override
    public void onLoad() {
        instance = this;
        getLogger().info("YRDatabase is loading...");
    }

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        pluginConfig = getConfig();
        
        // Check mode
        proxyMode = "proxy".equalsIgnoreCase(pluginConfig.getString("mode", "standalone"));
        
        // Initialize database
        initDatabase();
        
        // Initialize session bridge (after database is initialized)
        initSessionBridge();
        
        // Register events
        getServer().getPluginManager().registerEvents(new PlayerEventListener(this), this);
        
        // Register commands
        getServer().getCommandMap().register("yrdatabase", new YRDBCommand(this));
        
        getLogger().info("YRDatabase enabled! (mode: " + (proxyMode ? "proxy" : "standalone") + ")");
    }

    @Override
    public void onDisable() {
        getLogger().info("YRDatabase is disabling...");
        
        // Stop session bridge first
        if (sessionBridge != null) {
            sessionBridge.stop();
            sessionBridge = null;
        }
        
        // Save all online players data
        for (Player player : getServer().getOnlinePlayers().values()) {
            if (databaseManager != null) {
                try {
                    // Sync save on shutdown
                    databaseManager.flush().get();
                } catch (Exception e) {
                    getLogger().error("Failed to flush database on shutdown", e);
                }
            }
        }
        
        // Close database connections
        if (databaseManager != null) {
            databaseManager.close();
            databaseManager = null;
        }
        
        getLogger().info("YRDatabase disabled!");
    }

    private void initDatabase() {
        try {
            DatabaseConfig dbConfig = loadDatabaseConfig();
            databaseManager = new DatabaseManagerImpl(dbConfig);
            
            CompletableFuture<Boolean> initFuture = databaseManager.initialize();
            initFuture.whenComplete((success, error) -> {
                if (error != null) {
                    getLogger().error("Failed to initialize database: " + error.getMessage());
                } else if (success) {
                    getLogger().info("Database initialized successfully!");
                } else {
                    getLogger().warning("Database initialization returned false");
                }
            });
        } catch (Exception e) {
            getLogger().error("Failed to initialize database", e);
        }
    }

    private void initSessionBridge() {
        if (databaseManager == null) {
            getLogger().warning("Cannot initialize session bridge: DatabaseManager is null");
            return;
        }
        
        try {
            sessionBridge = new NukkitSessionBridge(this, databaseManager, proxyMode);
            sessionBridge.start();
            getLogger().info("Session bridge initialized");
        } catch (Exception e) {
            getLogger().error("Failed to initialize session bridge", e);
        }
    }

    private DatabaseConfig loadDatabaseConfig() {
        DatabaseConfig config = new DatabaseConfig();
        
        // Mode
        config.setMode(pluginConfig.getString("mode", "standalone"));
        
        // Cache (Redis) config
        DatabaseConfig.CacheConfig cacheConfig = config.getCache();
        Map<String, Object> cacheSection = pluginConfig.getSection("cache").getAllMap();
        if (!cacheSection.isEmpty()) {
            cacheConfig.setEnabled(getBoolean(cacheSection, "enabled", false));
            cacheConfig.setHost(getString(cacheSection, "host", "localhost"));
            cacheConfig.setPort(getInt(cacheSection, "port", 6379));
            cacheConfig.setPassword(getString(cacheSection, "password", ""));
            cacheConfig.setDatabase(getInt(cacheSection, "database", 0));
            cacheConfig.setTimeout(getInt(cacheSection, "timeout", 5000));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> poolSection = (Map<String, Object>) cacheSection.get("pool");
            if (poolSection != null) {
                cacheConfig.getPool().setMaxTotal(getInt(poolSection, "maxTotal", 20));
                cacheConfig.getPool().setMaxIdle(getInt(poolSection, "maxIdle", 10));
                cacheConfig.getPool().setMinIdle(getInt(poolSection, "minIdle", 2));
            }
        }
        
        // Persist (MySQL/SQLite) config
        DatabaseConfig.PersistConfig persistConfig = config.getPersist();
        Map<String, Object> persistSection = pluginConfig.getSection("persist").getAllMap();
        if (!persistSection.isEmpty()) {
            persistConfig.setEnabled(getBoolean(persistSection, "enabled", true));
            persistConfig.setType(getString(persistSection, "type", "sqlite"));
            
            // MySQL config
            @SuppressWarnings("unchecked")
            Map<String, Object> mysqlSection = (Map<String, Object>) persistSection.get("mysql");
            if (mysqlSection != null) {
                DatabaseConfig.PersistConfig.MySQLConfig mysqlConfig = persistConfig.getMysql();
                mysqlConfig.setHost(getString(mysqlSection, "host", "localhost"));
                mysqlConfig.setPort(getInt(mysqlSection, "port", 3306));
                mysqlConfig.setDatabase(getString(mysqlSection, "database", "yrdatabase"));
                mysqlConfig.setUsername(getString(mysqlSection, "username", "root"));
                mysqlConfig.setPassword(getString(mysqlSection, "password", ""));
                mysqlConfig.setTimezone(getString(mysqlSection, "timezone", "UTC"));
                
                @SuppressWarnings("unchecked")
                Map<String, Object> poolSection = (Map<String, Object>) mysqlSection.get("pool");
                if (poolSection != null) {
                    mysqlConfig.getPool().setMaxSize(getInt(poolSection, "maxSize", 10));
                    mysqlConfig.getPool().setMinIdle(getInt(poolSection, "minIdle", 2));
                    mysqlConfig.getPool().setConnectionTimeout(getInt(poolSection, "connectionTimeout", 30000));
                }
            }
            
            // SQLite config
            @SuppressWarnings("unchecked")
            Map<String, Object> sqliteSection = (Map<String, Object>) persistSection.get("sqlite");
            if (sqliteSection != null) {
                String defaultPath = new File(getDataFolder(), "data.db").getAbsolutePath();
                persistConfig.getSqlite().setFile(getString(sqliteSection, "file", defaultPath));
            } else {
                // Default SQLite path
                persistConfig.getSqlite().setFile(new File(getDataFolder(), "data.db").getAbsolutePath());
            }
        }
        
        // Caching config
        Map<String, Object> cachingSection = pluginConfig.getSection("caching").getAllMap();
        if (!cachingSection.isEmpty()) {
            config.getCaching().setDefaultTTL(getInt(cachingSection, "defaultTTL", 3600));
            config.getCaching().setPlayerDataTTL(getInt(cachingSection, "playerDataTTL", 7200));
            config.getCaching().setAutoRefresh(getBoolean(cachingSection, "autoRefresh", true));
        }
        
        // Session config
        Map<String, Object> sessionSection = pluginConfig.getSection("session").getAllMap();
        if (!sessionSection.isEmpty()) {
            config.getSession().setTimeout(getInt(sessionSection, "timeout", 300000));
            config.getSession().setHeartbeatInterval(getInt(sessionSection, "heartbeatInterval", 10000));
        }
        
        // Advanced config
        Map<String, Object> advancedSection = pluginConfig.getSection("advanced").getAllMap();
        if (!advancedSection.isEmpty()) {
            config.getAdvanced().setAsyncExecutorSize(getInt(advancedSection, "asyncExecutorSize", 4));
            config.getAdvanced().setEnableMetrics(getBoolean(advancedSection, "enableMetrics", false));
            config.getAdvanced().setDebugMode(getBoolean(advancedSection, "debugMode", false));
        }
        
        return config;
    }

    // ==================== Config Helpers ====================

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    // ==================== Public API ====================

    /**
     * Get the plugin instance.
     */
    public static YRDatabaseNukkit getInstance() {
        return instance;
    }

    /**
     * Get the database manager.
     */
    public static DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Get the session bridge.
     * Use this to access session-related functionality.
     */
    public static NukkitSessionBridge getSessionBridge() {
        return sessionBridge;
    }

    /**
     * Get the session manager.
     * Use this to register session event listeners.
     */
    public static SessionManager getSessionManager() {
        return sessionBridge != null ? sessionBridge.getSessionManager() : null;
    }

    /**
     * Check if running in proxy mode.
     */
    public boolean isProxyMode() {
        return proxyMode;
    }

    /**
     * Reload the plugin configuration.
     */
    public void reloadPluginConfig() {
        reloadConfig();
        pluginConfig = getConfig();
        getLogger().info("Configuration reloaded");
    }
}
