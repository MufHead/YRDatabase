package com.yirankuma.yrdatabase.nukkit.command;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import com.yirankuma.yrdatabase.api.DatabaseManager;
import com.yirankuma.yrdatabase.api.DatabaseStatus;
import com.yirankuma.yrdatabase.core.DatabaseManagerImpl;
import com.yirankuma.yrdatabase.nukkit.YRDatabaseNukkit;

import java.util.HashMap;
import java.util.Map;

/**
 * /yrdb command implementation for NukkitMOT.
 *
 * @author YiranKuma
 */
public class YRDBCommand extends Command {

    private final YRDatabaseNukkit plugin;

    public YRDBCommand(YRDatabaseNukkit plugin) {
        super("yrdb", "YRDatabase management command", "/yrdb <help|status|reload|info|test|stats>");
        this.plugin = plugin;
        
        this.setAliases(new String[]{"yrdatabase"});
        this.setPermission("yrdatabase.admin");
        
        // Add command parameters for auto-completion
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{
            CommandParameter.newEnum("action", new String[]{"help", "status", "reload", "info", "test", "stats"})
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return false;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                showHelp(sender);
                break;
            case "status":
                showStatus(sender);
                break;
            case "reload":
                reloadConfig(sender);
                break;
            case "info":
                showInfo(sender);
                break;
            case "test":
                testDatabase(sender);
                break;
            case "stats":
                showStats(sender);
                break;
            default:
                sender.sendMessage("§c未知命令: " + args[0]);
                sender.sendMessage("§7使用 §f/yrdb help §7查看帮助");
                return false;
        }

        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6§l━━━━━━━━━━ §e§lYRDatabase §6§l━━━━━━━━━━");
        sender.sendMessage("");
        sender.sendMessage("§e用法: §f/yrdb <子命令>");
        sender.sendMessage("");
        sender.sendMessage("§6可用命令:");
        sender.sendMessage("  §b/yrdb help   §7- §f显示此帮助信息");
        sender.sendMessage("  §b/yrdb status §7- §f查看数据库连接状态");
        sender.sendMessage("  §b/yrdb reload §7- §f重新加载配置文件");
        sender.sendMessage("  §b/yrdb info   §7- §f显示插件信息");
        sender.sendMessage("  §b/yrdb test   §7- §f测试数据库读写操作");
        sender.sendMessage("  §b/yrdb stats  §7- §f查看性能统计数据");
        sender.sendMessage("");
        sender.sendMessage("§7别名: §f/yrdatabase");
        sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage("§e[YRDatabase] §f数据库状态:");

        DatabaseManager db = YRDatabaseNukkit.getDatabaseManager();
        if (db == null) {
            sender.sendMessage("§c  数据库未初始化!");
            return;
        }

        DatabaseStatus status = db.getStatus();

        // Overall status
        String overallStatus = status.isConnected() ? "§a已连接" : "§c未连接";
        sender.sendMessage("§7  总体状态: " + overallStatus);

        // Cache layer (Redis)
        DatabaseStatus.ProviderStatus cacheStatus = status.getCacheStatus();
        if (cacheStatus != null) {
            sender.sendMessage("");
            sender.sendMessage("§e  缓存层 (Redis):");
            String cacheState = cacheStatus.isConnected() ? "§a已连接" : "§c未连接";
            sender.sendMessage("§7    状态: " + cacheState);
            sender.sendMessage("§7    地址: §f" + cacheStatus.getHost() + ":" + cacheStatus.getPort());
            sender.sendMessage("§7    延迟: §f" + cacheStatus.getLatencyMs() + "ms");
            if (cacheStatus.getErrorMessage() != null) {
                sender.sendMessage("§c    错误: " + cacheStatus.getErrorMessage());
            }
        } else {
            sender.sendMessage("");
            sender.sendMessage("§7  缓存层: §8未启用");
        }

        // Persistence layer
        DatabaseStatus.ProviderStatus persistStatus = status.getPersistStatus();
        if (persistStatus != null) {
            sender.sendMessage("");
            sender.sendMessage("§e  持久层 (" + persistStatus.getType().toUpperCase() + "):");
            String persistState = persistStatus.isConnected() ? "§a已连接" : "§c未连接";
            sender.sendMessage("§7    状态: " + persistState);
            if (persistStatus.getPort() > 0) {
                sender.sendMessage("§7    地址: §f" + persistStatus.getHost() + ":" + persistStatus.getPort());
            } else {
                sender.sendMessage("§7    文件: §f" + persistStatus.getHost());
            }
            sender.sendMessage("§7    延迟: §f" + persistStatus.getLatencyMs() + "ms");
            if (persistStatus.getErrorMessage() != null) {
                sender.sendMessage("§c    错误: " + persistStatus.getErrorMessage());
            }
        } else {
            sender.sendMessage("");
            sender.sendMessage("§7  持久层: §8未启用");
        }

        // Statistics
        sender.sendMessage("");
        sender.sendMessage("§7  缓存条目: §f" + status.getCachedEntries());
        sender.sendMessage("§7  待持久化: §f" + status.getPendingPersist());
    }

    private void reloadConfig(CommandSender sender) {
        sender.sendMessage("§e[YRDatabase] §f正在重新加载配置...");

        try {
            plugin.reloadPluginConfig();
            sender.sendMessage("§a  配置重新加载成功。");
            sender.sendMessage("§7  注意: 数据库连接不会重新加载。");
            sender.sendMessage("§7  需要重启服务器以应用连接更改。");
        } catch (Exception e) {
            sender.sendMessage("§c  重新加载配置失败: " + e.getMessage());
            plugin.getLogger().error("Failed to reload config", e);
        }
    }

    private void showInfo(CommandSender sender) {
        sender.sendMessage("§e[YRDatabase] §f插件信息:");
        sender.sendMessage("§7  版本: §f2.0.0");
        sender.sendMessage("§7  作者: §fYiranKuma");
        sender.sendMessage("§7  平台: §fNukkitMOT");
        sender.sendMessage("");
        sender.sendMessage("§e  特性:");
        sender.sendMessage("§7    • 双层缓存 (Redis + MySQL/SQLite)");
        sender.sendMessage("§7    • 类型安全的 Repository API");
        sender.sendMessage("§7    • 完全异步操作 (CompletableFuture)");
        sender.sendMessage("§7    • 跨服支持 (配合 WaterdogPE)");
        sender.sendMessage("§7    • 智能会话管理");
        sender.sendMessage("");
        sender.sendMessage("§7  在线玩家: §f" + Server.getInstance().getOnlinePlayers().size());

        // Runtime info
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        sender.sendMessage("");
        sender.sendMessage("§e  运行时:");
        sender.sendMessage("§7    内存: §f" + usedMemory + "MB / " + maxMemory + "MB");
        sender.sendMessage("§7    JVM: §f" + System.getProperty("java.version"));
        sender.sendMessage("§7    核心数: §f" + runtime.availableProcessors());
    }

    private void testDatabase(CommandSender sender) {
        sender.sendMessage("§e[YRDatabase] §f正在测试数据库操作...");

        DatabaseManager db = YRDatabaseNukkit.getDatabaseManager();
        if (db == null) {
            sender.sendMessage("§c  数据库未初始化!");
            return;
        }

        String testTable = "yrdb_test";
        String testKey = "test_" + System.currentTimeMillis();
        Map<String, Object> testData = new HashMap<>();
        testData.put("name", "TestPlayer");
        testData.put("score", 100);
        testData.put("timestamp", System.currentTimeMillis());

        long startTime = System.currentTimeMillis();

        // Test write
        db.set(testTable, testKey, testData)
            .thenCompose(writeSuccess -> {
                long writeTime = System.currentTimeMillis() - startTime;
                if (writeSuccess) {
                    sender.sendMessage("§a  ✓ 写入测试通过 §7(" + writeTime + "ms)");
                } else {
                    sender.sendMessage("§c  ✗ 写入测试失败");
                }

                // Test read
                long readStart = System.currentTimeMillis();
                return db.get(testTable, testKey).thenApply(result -> {
                    long readTime = System.currentTimeMillis() - readStart;
                    if (result.isPresent()) {
                        Map<String, Object> data = result.get();
                        boolean valid = "TestPlayer".equals(data.get("name"));
                        if (valid) {
                            sender.sendMessage("§a  ✓ 读取测试通过 §7(" + readTime + "ms)");
                        } else {
                            sender.sendMessage("§c  ✗ 读取测试失败 (数据不匹配)");
                        }
                    } else {
                        sender.sendMessage("§c  ✗ 读取测试失败 (无数据)");
                    }
                    return true;
                });
            })
            .thenCompose(v -> {
                // Test exists
                long existsStart = System.currentTimeMillis();
                return db.exists(testTable, testKey).thenApply(exists -> {
                    long existsTime = System.currentTimeMillis() - existsStart;
                    if (exists) {
                        sender.sendMessage("§a  ✓ 存在检查通过 §7(" + existsTime + "ms)");
                    } else {
                        sender.sendMessage("§c  ✗ 存在检查失败");
                    }
                    return exists;
                });
            })
            .thenCompose(v -> {
                // Test delete
                long deleteStart = System.currentTimeMillis();
                return db.delete(testTable, testKey).thenApply(deleted -> {
                    long deleteTime = System.currentTimeMillis() - deleteStart;
                    if (deleted) {
                        sender.sendMessage("§a  ✓ 删除测试通过 §7(" + deleteTime + "ms)");
                    } else {
                        sender.sendMessage("§c  ✗ 删除测试失败");
                    }
                    return deleted;
                });
            })
            .thenAccept(v -> {
                long totalTime = System.currentTimeMillis() - startTime;
                sender.sendMessage("");
                sender.sendMessage("§e  总耗时: §f" + totalTime + "ms");
                sender.sendMessage("§a  数据库测试完成!");
            })
            .exceptionally(e -> {
                sender.sendMessage("§c  测试失败: " + e.getMessage());
                plugin.getLogger().error("Database test failed", e);
                return null;
            });
    }

    private void showStats(CommandSender sender) {
        sender.sendMessage("§e[YRDatabase] §f性能统计:");

        DatabaseManager db = YRDatabaseNukkit.getDatabaseManager();
        if (db == null) {
            sender.sendMessage("§c  数据库未初始化!");
            return;
        }

        if (db instanceof DatabaseManagerImpl) {
            DatabaseStatus status = db.getStatus();

            sender.sendMessage("");
            sender.sendMessage("§e  连接状态:");

            // Cache metrics
            if (status.getCacheStatus() != null) {
                DatabaseStatus.ProviderStatus cache = status.getCacheStatus();
                sender.sendMessage("§7    Redis 延迟: §f" + cache.getLatencyMs() + "ms");
            }

            // Persist metrics
            if (status.getPersistStatus() != null) {
                DatabaseStatus.ProviderStatus persist = status.getPersistStatus();
                sender.sendMessage("§7    " + persist.getType().toUpperCase() + " 延迟: §f" + persist.getLatencyMs() + "ms");
            }

            // General metrics
            sender.sendMessage("");
            sender.sendMessage("§e  缓存统计:");
            sender.sendMessage("§7    缓存条目: §f" + status.getCachedEntries());
            sender.sendMessage("§7    待写入: §f" + status.getPendingPersist());

            // JVM Metrics
            sender.sendMessage("");
            sender.sendMessage("§e  JVM 指标:");
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
            long maxMemory = runtime.maxMemory() / 1024 / 1024;
            double memoryUsage = (double) usedMemory / maxMemory * 100;
            sender.sendMessage("§7    内存使用: §f" + String.format("%.1f", memoryUsage) + "% (" + usedMemory + "MB / " + maxMemory + "MB)");
            sender.sendMessage("§7    可用核心: §f" + runtime.availableProcessors());
            sender.sendMessage("§7    活动线程: §f" + Thread.activeCount());
        } else {
            sender.sendMessage("§7  详细指标对此实现不可用。");
        }
    }
}
