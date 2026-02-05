package com.yirankuma.yrdatabase.allay.command;

import com.yirankuma.yrdatabase.allay.YRDatabaseAllay;
import com.yirankuma.yrdatabase.api.DatabaseManager;
import com.yirankuma.yrdatabase.api.DatabaseStatus;
import com.yirankuma.yrdatabase.core.DatabaseManagerImpl;
import org.allaymc.api.command.Command;
import org.allaymc.api.command.CommandSender;
import org.allaymc.api.command.tree.CommandTree;
import org.allaymc.api.server.Server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * /yrdb command implementation for Allay.
 * 
 * <p>Commands:</p>
 * <ul>
 *   <li>/yrdb status - Show database connection status</li>
 *   <li>/yrdb reload - Reload configuration</li>
 *   <li>/yrdb info - Show plugin information</li>
 *   <li>/yrdb test - Test database operations</li>
 *   <li>/yrdb stats - Show performance statistics</li>
 * </ul>
 *
 * @author YiranKuma
 */
public class YRDBCommand extends Command {

    private final YRDatabaseAllay plugin;

    public YRDBCommand(YRDatabaseAllay plugin) {
        super("yrdb", "YRDatabase management command", "yrdatabase.admin");
        this.plugin = plugin;
        getAliases().add("yrdatabase");
    }

    @Override
    public void prepareCommandTree(CommandTree tree) {
        tree.getRoot()
                // /yrdb help
                .key("help")
                .exec(context -> {
                    showHelp(context.getSender());
                    return context.success();
                })
                .root()
                // /yrdb status
                .key("status")
                .permission("yrdatabase.admin.status")
                .exec(context -> {
                    showStatus(context.getSender());
                    return context.success();
                })
                .root()
                // /yrdb reload
                .key("reload")
                .permission("yrdatabase.admin.reload")
                .exec(context -> {
                    reloadConfig(context.getSender());
                    return context.success();
                })
                .root()
                // /yrdb info
                .key("info")
                .permission("yrdatabase.admin.info")
                .exec(context -> {
                    showInfo(context.getSender());
                    return context.success();
                })
                .root()
                // /yrdb test
                .key("test")
                .permission("yrdatabase.admin.test")
                .exec(context -> {
                    testDatabase(context.getSender());
                    return context.success();
                })
                .root()
                // /yrdb stats
                .key("stats")
                .permission("yrdatabase.admin.stats")
                .exec(context -> {
                    showStats(context.getSender());
                    return context.success();
                })
                .root()
                // /yrdb (no args - show help)
                .exec(context -> {
                    showHelp(context.getSender());
                    return context.success();
                });
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
        sender.sendMessage("§e[YRDatabase] §fDatabase Status:");

        DatabaseManager db = YRDatabaseAllay.getDatabaseManager();
        if (db == null) {
            sender.sendMessage("§c  Database not initialized!");
            return;
        }

        DatabaseStatus status = db.getStatus();

        // Overall status
        String overallStatus = status.isConnected() ? "§aConnected" : "§cDisconnected";
        sender.sendMessage("§7  Overall: " + overallStatus);

        // Cache layer (Redis)
        DatabaseStatus.ProviderStatus cacheStatus = status.getCacheStatus();
        if (cacheStatus != null) {
            sender.sendMessage("");
            sender.sendMessage("§e  Cache Layer (Redis):");
            String cacheState = cacheStatus.isConnected() ? "§aConnected" : "§cDisconnected";
            sender.sendMessage("§7    Status: " + cacheState);
            sender.sendMessage("§7    Host: §f" + cacheStatus.getHost() + ":" + cacheStatus.getPort());
            sender.sendMessage("§7    Latency: §f" + cacheStatus.getLatencyMs() + "ms");
            if (cacheStatus.getErrorMessage() != null) {
                sender.sendMessage("§c    Error: " + cacheStatus.getErrorMessage());
            }
        } else {
            sender.sendMessage("");
            sender.sendMessage("§7  Cache Layer: §8Disabled");
        }

        // Persistence layer (MySQL/SQLite)
        DatabaseStatus.ProviderStatus persistStatus = status.getPersistStatus();
        if (persistStatus != null) {
            sender.sendMessage("");
            sender.sendMessage("§e  Persistence Layer (" + persistStatus.getType().toUpperCase() + "):");
            String persistState = persistStatus.isConnected() ? "§aConnected" : "§cDisconnected";
            sender.sendMessage("§7    Status: " + persistState);
            if (persistStatus.getPort() > 0) {
                sender.sendMessage("§7    Host: §f" + persistStatus.getHost() + ":" + persistStatus.getPort());
            } else {
                sender.sendMessage("§7    File: §f" + persistStatus.getHost());
            }
            sender.sendMessage("§7    Latency: §f" + persistStatus.getLatencyMs() + "ms");
            if (persistStatus.getErrorMessage() != null) {
                sender.sendMessage("§c    Error: " + persistStatus.getErrorMessage());
            }
        } else {
            sender.sendMessage("");
            sender.sendMessage("§7  Persistence Layer: §8Disabled");
        }

        // Statistics
        sender.sendMessage("");
        sender.sendMessage("§7  Cached Entries: §f" + status.getCachedEntries());
        sender.sendMessage("§7  Pending Persist: §f" + status.getPendingPersist());
    }

    private void reloadConfig(CommandSender sender) {
        sender.sendMessage("§e[YRDatabase] §fReloading configuration...");

        try {
            plugin.reloadConfig();
            sender.sendMessage("§a  Configuration reloaded successfully.");
            sender.sendMessage("§7  Note: Database connections are not reloaded.");
            sender.sendMessage("§7  Restart server to apply connection changes.");
        } catch (Exception e) {
            sender.sendMessage("§c  Failed to reload configuration: " + e.getMessage());
            plugin.getPluginLogger().error("Failed to reload config", e);
        }
    }

    private void showInfo(CommandSender sender) {
        sender.sendMessage("§e[YRDatabase] §fPlugin Information:");
        sender.sendMessage("§7  Version: §f1.0.0-SNAPSHOT");
        sender.sendMessage("§7  Author: §fYiranKuma");
        sender.sendMessage("§7  Platform: §fAllay");
        sender.sendMessage("");
        sender.sendMessage("§e  Features:");
        sender.sendMessage("§7    • Dual-layer caching (Redis + MySQL/SQLite)");
        sender.sendMessage("§7    • Type-safe Repository API with annotations");
        sender.sendMessage("§7    • Full async operations (CompletableFuture)");
        sender.sendMessage("§7    • Cross-server support (with WaterdogPE)");
        sender.sendMessage("§7    • Smart session management");
        sender.sendMessage("");
        sender.sendMessage("§7  Online Players: §f" + Server.getInstance().getPlayerManager().getPlayerCount());

        // Runtime info
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        sender.sendMessage("");
        sender.sendMessage("§e  Runtime:");
        sender.sendMessage("§7    Memory: §f" + usedMemory + "MB / " + maxMemory + "MB");
        sender.sendMessage("§7    JVM: §f" + System.getProperty("java.version"));
        sender.sendMessage("§7    Cores: §f" + runtime.availableProcessors());
    }

    private void testDatabase(CommandSender sender) {
        sender.sendMessage("§e[YRDatabase] §fTesting database operations...");

        DatabaseManager db = YRDatabaseAllay.getDatabaseManager();
        if (db == null) {
            sender.sendMessage("§c  Database not initialized!");
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
                    sender.sendMessage("§a  ✓ Write test passed §7(" + writeTime + "ms)");
                } else {
                    sender.sendMessage("§c  ✗ Write test failed");
                }
                
                // Test read
                long readStart = System.currentTimeMillis();
                return db.get(testTable, testKey).thenApply(result -> {
                    long readTime = System.currentTimeMillis() - readStart;
                    if (result.isPresent()) {
                        Map<String, Object> data = result.get();
                        boolean valid = "TestPlayer".equals(data.get("name"));
                        if (valid) {
                            sender.sendMessage("§a  ✓ Read test passed §7(" + readTime + "ms)");
                        } else {
                            sender.sendMessage("§c  ✗ Read test failed (data mismatch)");
                        }
                    } else {
                        sender.sendMessage("§c  ✗ Read test failed (no data)");
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
                        sender.sendMessage("§a  ✓ Exists test passed §7(" + existsTime + "ms)");
                    } else {
                        sender.sendMessage("§c  ✗ Exists test failed");
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
                        sender.sendMessage("§a  ✓ Delete test passed §7(" + deleteTime + "ms)");
                    } else {
                        sender.sendMessage("§c  ✗ Delete test failed");
                    }
                    return deleted;
                });
            })
            .thenAccept(v -> {
                long totalTime = System.currentTimeMillis() - startTime;
                sender.sendMessage("");
                sender.sendMessage("§e  Total time: §f" + totalTime + "ms");
                sender.sendMessage("§a  Database test completed!");
            })
            .exceptionally(e -> {
                sender.sendMessage("§c  Test failed with error: " + e.getMessage());
                plugin.getPluginLogger().error("Database test failed", e);
                return null;
            });
    }

    private void showStats(CommandSender sender) {
        sender.sendMessage("§e[YRDatabase] §fPerformance Statistics:");

        DatabaseManager db = YRDatabaseAllay.getDatabaseManager();
        if (db == null) {
            sender.sendMessage("§c  Database not initialized!");
            return;
        }

        // Check if metrics are available
        if (db instanceof DatabaseManagerImpl) {
            DatabaseManagerImpl impl = (DatabaseManagerImpl) db;
            
            // Get metrics from the implementation
            DatabaseStatus status = db.getStatus();
            
            sender.sendMessage("");
            sender.sendMessage("§e  Connection Status:");
            
            // Cache metrics
            if (status.getCacheStatus() != null) {
                DatabaseStatus.ProviderStatus cache = status.getCacheStatus();
                sender.sendMessage("§7    Redis Latency: §f" + cache.getLatencyMs() + "ms");
            }
            
            // Persist metrics
            if (status.getPersistStatus() != null) {
                DatabaseStatus.ProviderStatus persist = status.getPersistStatus();
                sender.sendMessage("§7    " + persist.getType().toUpperCase() + " Latency: §f" + persist.getLatencyMs() + "ms");
            }
            
            // General metrics
            sender.sendMessage("");
            sender.sendMessage("§e  Cache Statistics:");
            sender.sendMessage("§7    Cached Entries: §f" + status.getCachedEntries());
            sender.sendMessage("§7    Pending Writes: §f" + status.getPendingPersist());
            
            // JVM Metrics
            sender.sendMessage("");
            sender.sendMessage("§e  JVM Metrics:");
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
            long maxMemory = runtime.maxMemory() / 1024 / 1024;
            double memoryUsage = (double) usedMemory / maxMemory * 100;
            sender.sendMessage("§7    Memory Usage: §f" + String.format("%.1f", memoryUsage) + "% (" + usedMemory + "MB / " + maxMemory + "MB)");
            sender.sendMessage("§7    Available Processors: §f" + runtime.availableProcessors());
            sender.sendMessage("§7    Active Threads: §f" + Thread.activeCount());
        } else {
            sender.sendMessage("§7  Detailed metrics not available for this implementation.");
        }
    }
}
