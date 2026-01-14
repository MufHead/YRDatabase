# YRDatabase - Nukkit 通用数据库前置插件

> 🔥 高性能 Redis + MySQL 数据库管理前置插件，支持智能事件系统

---

## 📖 插件简介

YRDatabase 是一个为 Nukkit 服务器设计的通用数据库管理前置插件，提供了简洁易用的 API 来操作 Redis 和 MySQL 数据库。

**核心特性**：
- ⚡ **高性能连接池** - 使用 HikariCP 和 Lettuce 连接池，性能优异
- 🎯 **智能事件系统** - 自动适配单服/多服环境，避免转服触发持久化
- 🔌 **简单易用 API** - 封装常用操作，开发者无需关心底层细节
- 🌐 **跨服支持** - 配合 WaterdogPE 实现真正的跨服数据同步
- 📦 **完全开源** - MIT 协议，可自由修改和分发

---

## 🎮 适用场景

### 单服环境
- 使用 Redis 缓存玩家数据，减少数据库查询
- 使用 MySQL 持久化玩家数据
- 监听 `PlayerDataInitializeEvent` 和 `PlayerDataPersistEvent` 进行数据管理

### 多服环境（配合 WaterdogPE）
- 区分玩家真实加入/退出和服务器转服
- 仅在真实退出时持久化数据，避免转服卡顿
- 使用 Redis Pub/Sub 同步跨服数据

---

## 📥 下载安装

### 下载地址

**GitHub Releases**：[YRDatabase Releases](https://github.com/MufHead/YRDatabase/releases)

**Jenkins CI 构建**：[最新构建](https://motci.cn/job/YRDatabase/)

### 安装步骤

1. 下载 `YRDatabase.jar`
2. 放入服务器 `plugins/` 目录
3. 启动服务器，插件会自动生成配置文件
4. 编辑 `plugins/YRDatabase/config.yml` 配置数据库
5. 重启服务器

---

## ⚙️ 配置文件

```yaml
# YRDatabase 配置文件

# MySQL 配置
mysql:
  enabled: true
  host: localhost
  port: 3306
  database: minecraft
  username: root
  password: your_password
  # 连接池配置
  pool:
    maximum-pool-size: 10
    minimum-idle: 5
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000

# Redis 配置
redis:
  enabled: true
  host: localhost
  port: 6379
  password: ""
  database: 0
  timeout: 3000
  # 连接池配置
  pool:
    max-total: 20
    max-idle: 10
    min-idle: 5

# 网易 UID 记录功能（需要 NukkitMaster）
netease-uid:
  enabled: true
  # 是否将网易UID存入数据库（需要MySQL启用）
  save-to-database: true

# Redis Pub/Sub 配置（仅在使用 WaterdogPE 时启用）
pubsub:
  enabled: false
```

---

## 👨‍💻 开发者 API

### 1. 添加依赖

#### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    // 只依赖 API（推荐）
    compileOnly("com.github.MufHead.YRDatabase:yrdatabase-common:v1.0.3")
}
```

在 `plugin.yml` 中添加依赖：
```yaml
depend: [YRDatabase]
```

### 2. 获取插件实例

```java
public class MyPlugin extends PluginBase {

    private YRDatabase yrDatabase;

    @Override
    public void onEnable() {
        // 获取 YRDatabase 实例
        yrDatabase = (YRDatabase) getServer().getPluginManager().getPlugin("YRDatabase");

        if (yrDatabase == null) {
            getLogger().error("YRDatabase 未安装！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }
}
```

### 3. Redis 操作

#### 基础操作

```java
// 获取 Redis 管理器
RedisManager redis = yrDatabase.getRedisManager();

// 设置值
redis.set("player:data:Steve", "{\"level\":10,\"money\":1000}");

// 设置值（带过期时间，单位：秒）
redis.setex("player:session:Steve", 3600, "online");

// 获取值
String data = redis.get("player:data:Steve");

// 删除值
redis.del("player:data:Steve");

// 检查键是否存在
boolean exists = redis.exists("player:data:Steve");
```

#### Hash 操作

```java
// 设置 Hash 字段
redis.hset("player:Steve", "level", "10");
redis.hset("player:Steve", "money", "1000");

// 获取 Hash 字段
String level = redis.hget("player:Steve", "level");

// 获取整个 Hash
Map<String, String> playerData = redis.hgetAll("player:Steve");

// 删除 Hash 字段
redis.hdel("player:Steve", "level");
```

#### 异步操作（推荐）

```java
// 异步获取数据
redis.getAsync("player:data:Steve").thenAccept(data -> {
    if (data != null) {
        getLogger().info("玩家数据: " + data);
    }
});

// 异步设置数据
redis.setAsync("player:data:Steve", "{\"level\":10}").thenAccept(success -> {
    if (success) {
        getLogger().info("数据保存成功");
    }
});
```

### 4. MySQL 操作

#### 执行查询

```java
// 获取 MySQL 管理器
MySQLManager mysql = yrDatabase.getMySQLManager();

// 查询数据
mysql.queryAsync("SELECT * FROM players WHERE name = ?", "Steve").thenAccept(result -> {
    try {
        if (result.next()) {
            int level = result.getInt("level");
            int money = result.getInt("money");
            getLogger().info("玩家等级: " + level + ", 金币: " + money);
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
});
```

#### 执行更新

```java
// 插入数据
mysql.updateAsync(
    "INSERT INTO players (name, level, money) VALUES (?, ?, ?)",
    "Steve", 10, 1000
).thenAccept(affectedRows -> {
    getLogger().info("插入了 " + affectedRows + " 行数据");
});

// 更新数据
mysql.updateAsync(
    "UPDATE players SET level = ? WHERE name = ?",
    20, "Steve"
).thenAccept(affectedRows -> {
    getLogger().info("更新了 " + affectedRows + " 行数据");
});
```

### 5. 智能事件系统（核心功能）

#### 监听玩家数据初始化事件

```java
@EventHandler(priority = EventPriority.LOW)
public void onPlayerDataInitialize(PlayerDataInitializeEvent event) {
    Player player = event.getPlayer();
    String uid = event.getUid();

    // 判断是否为真实加入（非转服）
    if (event.isRealJoin()) {
        // 真实加入：从数据库加载数据
        loadPlayerDataFromDatabase(player, uid);
    } else {
        // 转服：从 Redis 缓存加载数据（更快）
        loadPlayerDataFromCache(player, uid);
    }
}
```

#### 监听玩家数据持久化事件

```java
@EventHandler(priority = EventPriority.HIGH)
public void onPlayerDataPersist(PlayerDataPersistEvent event) {
    Player player = event.getPlayer();
    String uid = event.getUid();

    // 判断是否应该持久化
    if (event.shouldPersist()) {
        // 真实退出或服务器关闭：保存到数据库
        savePlayerDataToDatabase(player, uid);
    } else {
        // 转服：只保存到 Redis 缓存
        savePlayerDataToCache(player, uid);
    }
}
```

#### 完整示例：玩家等级系统

```java
public class LevelPlugin extends PluginBase implements Listener {

    private YRDatabase yrDatabase;
    private Map<String, Integer> playerLevels = new HashMap<>();

    @Override
    public void onEnable() {
        yrDatabase = (YRDatabase) getServer().getPluginManager().getPlugin("YRDatabase");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDataInitialize(PlayerDataInitializeEvent event) {
        Player player = event.getPlayer();
        String uid = event.getUid();

        if (event.isRealJoin()) {
            // 真实加入：从数据库加载
            yrDatabase.getMySQLManager().queryAsync(
                "SELECT level FROM player_levels WHERE uid = ?", uid
            ).thenAccept(rs -> {
                try {
                    int level = rs.next() ? rs.getInt("level") : 1;
                    playerLevels.put(uid, level);
                    player.sendMessage("§a欢迎回来！你的等级：" + level);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        } else {
            // 转服：从 Redis 加载
            yrDatabase.getRedisManager().getAsync("level:" + uid).thenAccept(levelStr -> {
                int level = levelStr != null ? Integer.parseInt(levelStr) : 1;
                playerLevels.put(uid, level);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDataPersist(PlayerDataPersistEvent event) {
        String uid = event.getUid();
        Integer level = playerLevels.get(uid);

        if (level == null) return;

        if (event.shouldPersist()) {
            // 真实退出：保存到数据库
            yrDatabase.getMySQLManager().updateAsync(
                "INSERT INTO player_levels (uid, level) VALUES (?, ?) ON DUPLICATE KEY UPDATE level = ?",
                uid, level, level
            );
        }

        // 无论如何都保存到 Redis（用于转服）
        yrDatabase.getRedisManager().setexAsync("level:" + uid, 3600, level.toString());
        playerLevels.remove(uid);
    }
}
```

---

## 🌐 多服环境配置

### 1. WaterdogPE 端配置

在 WaterdogPE 服务器上安装 `YRDatabase-Waterdog.jar`，并配置：

```yaml
redis:
  enabled: true
  host: localhost
  port: 6379
  password: ""
  database: 0

pubsub:
  enabled: true
```

### 2. Nukkit 子服配置

在所有 Nukkit 子服上安装 `YRDatabase.jar`，并启用 Pub/Sub：

```yaml
pubsub:
  enabled: true
```

### 3. 工作原理

1. 玩家在 WaterdogPE 登录 → WaterdogPE 发送 `REAL_JOIN` 消息
2. 所有子服收到消息 → 标记玩家为"真实在线"
3. 玩家加入子服 → 触发 `PlayerDataInitializeEvent` (REAL_JOIN)
4. 玩家转服 → 触发 `PlayerDataInitializeEvent` (SERVER_TRANSFER)
5. 玩家从 WaterdogPE 退出 → 发送 `REAL_QUIT` 消息
6. 玩家所在子服触发 `PlayerDataPersistEvent` (REAL_QUIT)

---

## 📊 事件原因说明

### PlayerDataInitializeEvent 初始化原因

| 原因 | 说明 | 应该做什么 |
|------|------|-----------|
| `REAL_JOIN` | 玩家真实加入（确认非转服） | 从数据库加载完整数据 |
| `LOCAL_JOIN` | 本地加入（无法判断是否转服） | 从数据库或缓存加载 |
| `SERVER_TRANSFER` | 服务器转服（已确认） | 从 Redis 缓存快速加载 |

### PlayerDataPersistEvent 持久化原因

| 原因 | 说明 | 应该做什么 |
|------|------|-----------|
| `REAL_QUIT` | 玩家真实退出（确认非转服） | 保存到数据库 + Redis |
| `LOCAL_QUIT` | 本地退出（无法判断是否转服） | 保存到数据库 + Redis |
| `SERVER_TRANSFER` | 服务器转服（已确认） | 只保存到 Redis 缓存 |
| `SERVER_SHUTDOWN` | 服务器关闭 | 保存到数据库 + Redis |

---

## 🔧 常见问题

### Q: 我的服务器是单服，需要配置 WaterdogPE 吗？

**A:** 不需要。单服环境下，YRDatabase 会自动使用 `LOCAL_JOIN` 和 `LOCAL_QUIT` 事件，你只需要监听事件即可。

### Q: 如何判断玩家是否使用网易账号登录？

**A:** 使用 `yrDatabase.resolvePlayerId(player)` 方法：
```java
String uid = yrDatabase.resolvePlayerId(player);
// 如果安装了 NukkitMaster 且玩家是网易登录，返回网易UID（数字）
// 否则返回 UUID 字符串
```

### Q: 如何在不使用事件的情况下直接操作数据库？

**A:** 直接使用 `RedisManager` 和 `MySQLManager`：
```java
RedisManager redis = yrDatabase.getRedisManager();
MySQLManager mysql = yrDatabase.getMySQLManager();
```

### Q: 数据库连接失败怎么办？

**A:** 检查以下几点：
1. MySQL/Redis 服务是否启动
2. 配置文件中的地址、端口、密码是否正确
3. 防火墙是否允许连接
4. 查看 `logs/` 目录中的错误日志

### Q: 如何优化性能？

**A:**
1. 使用异步操作（`xxxAsync` 方法）
2. 使用 Redis 缓存热数据
3. 批量操作使用事务
4. 合理配置连接池大小

---

## 📚 更多资源

- **GitHub 仓库**：https://github.com/MufHead/YRDatabase
- **问题反馈**：https://github.com/MufHead/YRDatabase/issues
- **JitPack Maven**：https://jitpack.io/#MufHead/YRDatabase
- **完整文档**：查看项目 README.md

---

## 📜 开源协议

本插件使用 **MIT License** 开源协议。

你可以自由：
- ✅ 商业使用
- ✅ 修改代码
- ✅ 分发
- ✅ 私人使用

唯一要求：保留原作者版权声明。

---

## 💖 支持作者

如果这个插件对你有帮助，请：
- ⭐ 在 GitHub 上给项目点个 Star
- 📢 分享给其他服主
- 🐛 提交 Bug 和建议

---

**插件版本**：v1.0.3
**支持的 Nukkit 版本**：1.0+
**最后更新**：2026-01-14
