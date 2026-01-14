package com.yirankuma.yrdatabase;

import cn.nukkit.Player;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.neteasemc.nukkitmaster.NukkitMaster;
import com.yirankuma.yrdatabase.api.DatabaseManager;
import com.yirankuma.yrdatabase.config.DatabaseConfig;
import com.yirankuma.yrdatabase.impl.DatabaseManagerImpl;
import com.yirankuma.yrdatabase.redis.RedisPubSubListener;
import com.yirankuma.yrdatabase.manager.PlayerDataEventManager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;

public class YRDatabase extends PluginBase {
    
    private static YRDatabase instance;
    private DatabaseManager databaseManager;
    private DatabaseConfig config;
    private Gson gson;
    private RedisPubSubListener pubSubListener;
    private PlayerDataEventManager eventManager;

    public NukkitMaster nukkitMaster = null;

    public boolean isNukkitMasterLoaded() {
        return nukkitMaster != null;
    }
    
    @Override
    public void onEnable() {
        instance = this;
        gson = new GsonBuilder().setPrettyPrinting().create();
        
        this.getLogger().info(TextFormat.GREEN + "YRDatabase 插件正在启用...");

        //获取前置
        loadPrePlugins();
        
        // 加载配置
        loadConfig();
        
        // 初始化数据库管理器
        databaseManager = new DatabaseManagerImpl(config);
        databaseManager.initialize();

        // 检查连接状态
        checkConnections();

        // 初始化Redis Pub/Sub监听器（用于接收WaterdogPE的消息）
        if (config.getRedis().isEnabled()) {
            initPubSubListener();
        } else {
            this.getLogger().warning(TextFormat.YELLOW + "Redis已禁用，将无法接收WaterdogPE消息");
            this.getLogger().warning(TextFormat.YELLOW + "转服时仍会触发持久化操作");
        }

        // 初始化事件管理器
        eventManager = new PlayerDataEventManager(this);
        getServer().getPluginManager().registerEvents(eventManager, this);
        this.getLogger().info(TextFormat.GREEN + "玩家数据事件管理器已启动");

        this.getLogger().info(TextFormat.GREEN + "YRDatabase 插件已成功启用！");
    }

    public void loadPrePlugins() {
        nukkitMaster = (NukkitMaster) getServer().getPluginManager().getPlugin("NukkitMaster");
        if (nukkitMaster == null) {
            this.getLogger().warning(TextFormat.RED + "前置插件NukkitMaster 插件未找到！");
        }else{
            this.getLogger().info(TextFormat.GREEN + "前置插件NukkitMaster 插件已找到！");
        }
    }


    
    @Override
    public void onDisable() {
        this.getLogger().info(TextFormat.YELLOW + "YRDatabase 插件正在关闭...");

        // 触发所有在线玩家的持久化事件
        if (eventManager != null) {
            eventManager.onServerShutdown();
        }

        // 关闭Pub/Sub监听器
        if (pubSubListener != null) {
            pubSubListener.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        this.getLogger().info(TextFormat.RED + "YRDatabase 插件已关闭！");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("yrdb")) {
            if (args.length == 0) {
                sender.sendMessage(TextFormat.YELLOW + "=== YRDatabase 命令帮助 ===");
                sender.sendMessage(TextFormat.AQUA + "/yrdb status - 查看连接状态");
                sender.sendMessage(TextFormat.AQUA + "/yrdb reload - 重载配置");
                sender.sendMessage(TextFormat.AQUA + "/yrdb test - 测试数据库操作");
                return true;
            }
            
            switch (args[0].toLowerCase()) {
                case "status":
                    showStatus(sender);
                    return true;
                    
                case "reload":
                    reloadPlugin(sender);
                    return true;
                    
                case "test":
                    testDatabase(sender);
                    return true;
                    
                default:
                    sender.sendMessage(TextFormat.RED + "未知命令参数！使用 /yrdb 查看帮助");
                    return false;
            }
        }
        return false;
    }
    
    private void loadConfig() {
        File configFile = new File(getDataFolder(), "config.json");
        
        if (!configFile.exists()) {
            // 创建默认配置
            config = new DatabaseConfig();
            saveConfig(configFile);
            this.getLogger().info("已创建默认配置文件: " + configFile.getPath());
        } else {
            // 加载现有配置
            try (FileReader reader = new FileReader(configFile)) {
                config = gson.fromJson(reader, DatabaseConfig.class);
                this.getLogger().info("已加载配置文件: " + configFile.getPath());
            } catch (IOException e) {
                this.getLogger().error("加载配置文件失败: " + e.getMessage());
                config = new DatabaseConfig();
            }
        }
    }
    
    private void saveConfig(File configFile) {
        try {
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            
            try (FileWriter writer = new FileWriter(configFile)) {
                gson.toJson(config, writer);
            }
        } catch (IOException e) {
            this.getLogger().error("保存配置文件失败: " + e.getMessage());
        }
    }
    
    private void checkConnections() {
        boolean redisConnected = databaseManager.isRedisConnected();
        boolean mysqlConnected = databaseManager.isMySQLConnected();
        
        if (redisConnected) {
            this.getLogger().info(TextFormat.GREEN + "Redis 连接成功！");
        } else {
            this.getLogger().warning(TextFormat.YELLOW + "Redis 连接失败或已禁用");
        }
        
        if (mysqlConnected) {
            this.getLogger().info(TextFormat.GREEN + "MySQL 连接成功！");
        } else {
            this.getLogger().warning(TextFormat.YELLOW + "MySQL 连接失败或已禁用");
        }
        
        if (!redisConnected && !mysqlConnected) {
            this.getLogger().error(TextFormat.RED + "警告：所有数据库连接都失败了！");
        }
    }
    
    private void showStatus(CommandSender sender) {
        sender.sendMessage(TextFormat.YELLOW + "=== YRDatabase 状态 ===");
        sender.sendMessage(TextFormat.AQUA + "Redis 状态: " +
                (databaseManager.isRedisConnected() ? TextFormat.GREEN + "已连接" : TextFormat.RED + "未连接"));
        sender.sendMessage(TextFormat.AQUA + "MySQL 状态: " +
                (databaseManager.isMySQLConnected() ? TextFormat.GREEN + "已连接" : TextFormat.RED + "未连接"));

        // 显示Pub/Sub状态
        if (pubSubListener != null) {
            sender.sendMessage(TextFormat.AQUA + "Redis Pub/Sub: " + TextFormat.GREEN + "已启用");
            sender.sendMessage(TextFormat.AQUA + "真实在线玩家数: " + TextFormat.GREEN + pubSubListener.getRealOnlineCount());
        } else {
            sender.sendMessage(TextFormat.AQUA + "Redis Pub/Sub: " + TextFormat.YELLOW + "未启用");
        }
    }
    
    private void reloadPlugin(CommandSender sender) {
        sender.sendMessage(TextFormat.YELLOW + "正在重载配置...");
        
        // 关闭现有连接
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        
        // 重新加载配置
        loadConfig();
        
        // 重新初始化
        databaseManager = new DatabaseManagerImpl(config);
        databaseManager.initialize();
        
        sender.sendMessage(TextFormat.GREEN + "配置重载完成！");
        checkConnections();
    }
    
    private void testDatabase(CommandSender sender) {
        sender.sendMessage(TextFormat.YELLOW + "正在测试数据库操作...");
        
        // 测试基本操作
        databaseManager.set("test_key", "test_value", 60)
                .thenCompose(success -> {
                    if (success) {
                        sender.sendMessage(TextFormat.GREEN + "✓ 数据写入测试成功");
                        return databaseManager.get("test_key");
                    } else {
                        sender.sendMessage(TextFormat.RED + "✗ 数据写入测试失败");
                        return null;
                    }
                })
                .thenCompose(value -> {
                    if ("test_value".equals(value)) {
                        sender.sendMessage(TextFormat.GREEN + "✓ 数据读取测试成功");
                        return databaseManager.delete("test_key");
                    } else {
                        sender.sendMessage(TextFormat.RED + "✗ 数据读取测试失败");
                        return null;
                    }
                })
                .thenAccept(deleted -> {
                    if (Boolean.TRUE.equals(deleted)) {
                        sender.sendMessage(TextFormat.GREEN + "✓ 数据删除测试成功");
                    } else {
                        sender.sendMessage(TextFormat.RED + "✗ 数据删除测试失败");
                    }
                    sender.sendMessage(TextFormat.YELLOW + "数据库测试完成！");
                })
                .exceptionally(throwable -> {
                    sender.sendMessage(TextFormat.RED + "数据库测试出错: " + throwable.getMessage());
                    return null;
                });
    }
    
    // ========== Redis Pub/Sub 初始化 ==========

    private void initPubSubListener() {
        try {
            pubSubListener = new RedisPubSubListener(this);
            DatabaseConfig.Redis redisConfig = config.getRedis();

            pubSubListener.initialize(
                    redisConfig.getHost(),
                    redisConfig.getPort(),
                    redisConfig.getPassword(),
                    redisConfig.getDatabase(),
                    redisConfig.getTimeout()
            );

            this.getLogger().info(TextFormat.GREEN + "Redis Pub/Sub 监听器已启动");
            this.getLogger().info(TextFormat.AQUA + "将接收WaterdogPE的玩家真实加入/退出消息");

        } catch (Exception e) {
            this.getLogger().error(TextFormat.RED + "Redis Pub/Sub 初始化失败: " + e.getMessage());
            pubSubListener = null;
        }
    }

    // ========== 静态方法 ==========

    public static YRDatabase getInstance() {
        return instance;
    }
    
    public static DatabaseManager getDatabaseManager() {
        return instance != null ? instance.databaseManager : null;
    }

    public RedisPubSubListener getPubSubListener() {
        return pubSubListener;
    }

    public PlayerDataEventManager getEventManager() {
        return eventManager;
    }

    public boolean isUseNeteaseUid() {
        return config != null && config.isUseNeteaseUid();
    }

    public String resolvePlayerId(Player player) {
        if (player == null) return null;
//        if (config != null && config.isUseNeteaseUid() && isNukkitMasterLoaded()) {
//            try {
//                long proxyUid = com.neteasemc.nukkitmaster.NukkitMaster.getInstance()
//                        .getGeyserMsgListener()
//                        .getPlayerInfo(player)
//                        .getProxyUid();
//
//                if (proxyUid != 0) {
//                    return Long.toString(proxyUid);
//                }else{
//                    getLogger().warning(String.format("§c玩家 §a%s §c客户端非正式客户端，使用UUID作为玩家ID", player.getName()));
//                }
//            } catch (Exception ignored) { }
//        }
        UUID uuid = player.getUniqueId();
        return uuid != null ? uuid.toString() : player.getName();
    }
}