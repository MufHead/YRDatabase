package com.yirankuma.yrdatabase.allay;

import com.yirankuma.yrdatabase.allay.command.YRDBCommand;
import com.yirankuma.yrdatabase.allay.listener.PlayerEventListener;
import com.yirankuma.yrdatabase.allay.session.AllaySessionBridge;
import com.yirankuma.yrdatabase.api.DatabaseManager;
import com.yirankuma.yrdatabase.api.config.DatabaseConfig;
import com.yirankuma.yrdatabase.api.session.SessionManager;
import com.yirankuma.yrdatabase.core.DatabaseManagerImpl;
import lombok.Getter;
import org.allaymc.api.plugin.Plugin;
import org.allaymc.api.registry.Registries;
import org.allaymc.api.server.Server;
import org.allaymc.api.utils.config.Config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * YRDatabase plugin for Allay server.
 *
 * @author YiranKuma
 */
public class YRDatabaseAllay extends Plugin {

    @Getter
    private static YRDatabaseAllay instance;

    @Getter
    private static DatabaseManager databaseManager;

    @Getter
    private static AllaySessionBridge sessionBridge;

    private DatabaseManagerImpl databaseManagerImpl;
    private Config config;
    private PlayerEventListener playerEventListener;
    private boolean proxyMode = false;

    @Override
    public void onLoad() {
        instance = this;
        pluginLogger.info("YRDatabase is loading...");
    }

    @Override
    public void onEnable() {
        pluginLogger.info("YRDatabase is enabling...");

        // Load configuration
        loadConfiguration();

        // Check mode
        proxyMode = "proxy".equalsIgnoreCase(config.getString("mode", "standalone"));

        // Initialize database manager
        initializeDatabase();

        // Initialize session bridge
        initSessionBridge();

        // Register event listeners
        registerListeners();

        // Register commands
        registerCommands();

        pluginLogger.info("YRDatabase enabled successfully! (mode: {})", proxyMode ? "proxy" : "standalone");
    }

    @Override
    public void onDisable() {
        pluginLogger.info("YRDatabase is disabling...");

        // Stop session bridge first
        if (sessionBridge != null) {
            sessionBridge.stop();
            sessionBridge = null;
        }

        // Persist all cached data
        if (playerEventListener != null) {
            playerEventListener.persistAllPlayers();
        }

        // Close database connections
        if (databaseManagerImpl != null) {
            databaseManagerImpl.close();
        }

        pluginLogger.info("YRDatabase disabled!");
    }

    private void loadConfiguration() {
        File dataFolder = getPluginContainer().dataFolder().toFile();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File configFile = new File(dataFolder, "config.yml");
        
        // Save default config from resources if not exists
        if (!configFile.exists()) {
            saveDefaultConfig(configFile);
        }

        config = new Config(configFile, Config.YAML);
        pluginLogger.info("Configuration loaded from {}", configFile.getAbsolutePath());
    }

    private void saveDefaultConfig(File configFile) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            if (is != null) {
                Files.copy(is, configFile.toPath());
                pluginLogger.info("Default configuration saved");
            } else {
                pluginLogger.warn("Could not find default config.yml in resources");
            }
        } catch (IOException e) {
            pluginLogger.error("Failed to save default config: {}", e.getMessage());
        }
    }

    private DatabaseConfig buildDatabaseConfig() {
        DatabaseConfig dbConfig = new DatabaseConfig();

        dbConfig.setMode(config.getString("mode", "standalone"));

        // Cache config
        DatabaseConfig.CacheConfig cacheConfig = dbConfig.getCache();
        cacheConfig.setEnabled(config.getBoolean("cache.enabled", true));
        cacheConfig.setType(config.getString("cache.type", "redis"));
        cacheConfig.setHost(config.getString("cache.host", "localhost"));
        cacheConfig.setPort(config.getInt("cache.port", 6379));
        cacheConfig.setPassword(config.getString("cache.password", ""));
        cacheConfig.setDatabase(config.getInt("cache.database", 0));
        cacheConfig.setTimeout(config.getInt("cache.timeout", 5000));
        cacheConfig.getPool().setMaxTotal(config.getInt("cache.pool.maxTotal", 20));
        cacheConfig.getPool().setMaxIdle(config.getInt("cache.pool.maxIdle", 10));
        cacheConfig.getPool().setMinIdle(config.getInt("cache.pool.minIdle", 2));

        // Persist config
        DatabaseConfig.PersistConfig persistConfig = dbConfig.getPersist();
        persistConfig.setEnabled(config.getBoolean("persist.enabled", true));
        persistConfig.setType(config.getString("persist.type", "sqlite"));

        // MySQL
        DatabaseConfig.PersistConfig.MySQLConfig mysqlConfig = persistConfig.getMysql();
        mysqlConfig.setHost(config.getString("persist.mysql.host", "localhost"));
        mysqlConfig.setPort(config.getInt("persist.mysql.port", 3306));
        mysqlConfig.setDatabase(config.getString("persist.mysql.database", "yrdatabase"));
        mysqlConfig.setUsername(config.getString("persist.mysql.username", "root"));
        mysqlConfig.setPassword(config.getString("persist.mysql.password", ""));
        mysqlConfig.setTimezone(config.getString("persist.mysql.timezone", "Asia/Shanghai"));
        mysqlConfig.getPool().setMaxSize(config.getInt("persist.mysql.pool.maxSize", 10));
        mysqlConfig.getPool().setMinIdle(config.getInt("persist.mysql.pool.minIdle", 2));
        mysqlConfig.getPool().setConnectionTimeout(config.getLong("persist.mysql.pool.connectionTimeout", 30000));
        mysqlConfig.getPool().setIdleTimeout(config.getLong("persist.mysql.pool.idleTimeout", 600000));
        mysqlConfig.getPool().setMaxLifetime(config.getLong("persist.mysql.pool.maxLifetime", 1800000));

        // SQLite
        File dataFolder = getPluginContainer().dataFolder().toFile();
        String sqlitePath = config.getString("persist.sqlite.file", "data/yrdatabase.db");
        if (!sqlitePath.startsWith("/") && !sqlitePath.contains(":")) {
            sqlitePath = new File(dataFolder, sqlitePath).getAbsolutePath();
        }
        persistConfig.getSqlite().setFile(sqlitePath);

        // Caching
        DatabaseConfig.CachingConfig cachingConfig = dbConfig.getCaching();
        cachingConfig.setDefaultTTL(config.getLong("caching.defaultTTL", 3600));
        cachingConfig.setPlayerDataTTL(config.getLong("caching.playerDataTTL", 7200));
        cachingConfig.setAutoRefresh(config.getBoolean("caching.autoRefresh", true));
        cachingConfig.setRefreshThreshold(config.getLong("caching.refreshThreshold", 300));

        // Session
        DatabaseConfig.SessionConfig sessionConfig = dbConfig.getSession();
        sessionConfig.setTimeout(config.getLong("session.timeout", 300000));
        sessionConfig.setHeartbeatInterval(config.getLong("session.heartbeatInterval", 10000));
        sessionConfig.setMessageExpiry(config.getLong("session.messageExpiry", 30000));

        // Advanced
        DatabaseConfig.AdvancedConfig advancedConfig = dbConfig.getAdvanced();
        advancedConfig.setAsyncExecutorSize(config.getInt("advanced.asyncExecutorSize", 4));
        advancedConfig.setEnableMetrics(config.getBoolean("advanced.enableMetrics", false));
        advancedConfig.setDebugMode(config.getBoolean("advanced.debugMode", false));

        return dbConfig;
    }

    private void initializeDatabase() {
        DatabaseConfig dbConfig = buildDatabaseConfig();

        databaseManagerImpl = new DatabaseManagerImpl(dbConfig);
        databaseManager = databaseManagerImpl;

        try {
            databaseManagerImpl.initialize().join();
            pluginLogger.info("Database initialized: cache={}, persist={}",
                    databaseManagerImpl.getCacheProvider().map(p -> "connected").orElse("disabled"),
                    databaseManagerImpl.getPersistProvider().map(p -> "connected").orElse("disabled"));
        } catch (Exception e) {
            pluginLogger.error("Failed to initialize database: {}", e.getMessage());
        }
    }

    private void initSessionBridge() {
        if (databaseManager == null) {
            pluginLogger.warn("Cannot initialize session bridge: DatabaseManager is null");
            return;
        }

        try {
            sessionBridge = new AllaySessionBridge(this, databaseManager, proxyMode);
            sessionBridge.start();
            pluginLogger.info("Session bridge initialized");
        } catch (Exception e) {
            pluginLogger.error("Failed to initialize session bridge: {}", e.getMessage());
        }
    }

    private void registerListeners() {
        playerEventListener = new PlayerEventListener(this);
        Server.getInstance().getEventBus().registerListener(playerEventListener);
        pluginLogger.info("Event listeners registered");
    }

    private void registerCommands() {
        YRDBCommand yrdbCommand = new YRDBCommand(this);
        Registries.COMMANDS.register(yrdbCommand);
        pluginLogger.info("Commands registered: /yrdb");
    }

    /**
     * Reload the plugin configuration.
     */
    public void reloadConfig() {
        config.reload();
        pluginLogger.info("Configuration reloaded");
    }

    /**
     * Get the plugin configuration.
     *
     * @return Config instance
     */
    public Config getPluginConfig() {
        return config;
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
}
