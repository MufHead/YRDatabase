# YRDatabase for Allay - 数据库前置插件

## 📖 简介

YRDatabase 是一款强大的数据库前置插件，支持 Allay、Nukkit 和 WaterdogPE。提供 Redis 缓存 + MySQL/SQLite 持久化的双层架构，专为 Minecraft Bedrock Edition 服务器优化。

### ✨ 主要特性

- ✅ **双层缓存架构** - Redis 缓存 + MySQL/SQLite 持久化
- ✅ **类型安全 API** - 支持泛型 Repository 和实体映射
- ✅ **全异步设计** - 所有操作返回 CompletableFuture
- ✅ **多平台支持** - Allay / Nukkit / WaterdogPE
- ✅ **智能会话管理** - 自动区分真实加入/退出 vs 转服
- ✅ **灵活配置** - YAML 配置文件，支持热重载

---

## 🚀 快速开始

### 1. 安装

将 `yrdatabase-allay-1.0.0-SNAPSHOT.jar` 放入 Allay 服务器的 `plugins` 目录。

### 2. 配置

首次启动会在 `plugins/yrdatabase-allay/config.yml` 生成默认配置：

```yaml
mode: standalone  # standalone（单服）/ cluster（跨服）

# Redis 缓存层配置
cache:
  enabled: true
  type: redis
  host: localhost
  port: 6379
  password: ""
  database: 0

# 持久化层配置
persist:
  enabled: true
  type: sqlite  # sqlite 或 mysql
  
  # SQLite 配置（单服推荐）
  sqlite:
    file: data/yrdatabase.db
  
  # MySQL 配置（跨服推荐）
  mysql:
    host: localhost
    port: 3306
    database: yrdatabase
    username: root
    password: ""
```

### 3. 重启服务器

插件将自动初始化数据库连接。

---

## 📝 API 使用示例

### 方式一：简单 Map API（兼容原 Nukkit 版本）

```java
DatabaseManager db = YRDatabaseAllay.getDatabaseManager();

// 保存玩家数据
Map<String, Object> playerData = new HashMap<>();
playerData.put("name", "Steve");
playerData.put("level", 10);
playerData.put("coins", 1000L);

db.set("players", playerId, playerData).thenAccept(success -> {
    if (success) {
        plugin.getLogger().info("数据保存成功");
    }
});

// 获取玩家数据
db.get("players", playerId).thenAccept(optional -> {
    if (optional.isPresent()) {
        Map<String, Object> data = optional.get();
        int level = ((Number) data.get("level")).intValue();
        plugin.getLogger().info("玩家等级: " + level);
    }
});

// 持久化并清除缓存
db.persistAndClear("players", playerId).join();
```

### 方式二：类型安全的 Repository API（推荐）

```java
// 1. 定义实体类
@Table("player_data")
public class PlayerData {
    @PrimaryKey
    private String playerId;
    
    @Column("player_name")
    private String name;
    
    @Column
    private int level;
    
    @Column
    private long coins;
    
    @Column("last_login")
    private long lastLoginTime;
    
    // getters and setters...
}

// 2. 获取 Repository
Repository<PlayerData> repo = db.getRepository(PlayerData.class);

// 3. 保存数据
PlayerData player = new PlayerData();
player.setPlayerId(uuid);
player.setName("Alex");
player.setLevel(1);
player.setCoins(1000);
repo.save(player).thenAccept(success -> {
    plugin.getLogger().info("保存成功");
});

// 4. 查询数据
repo.findById(uuid).thenAccept(optional -> {
    optional.ifPresent(p -> {
        int level = p.getLevel();  // 类型安全！
        plugin.getLogger().info("等级: " + level);
    });
});

// 5. 批量查询
repo.findBy("level", 10).thenAccept(players -> {
    plugin.getLogger().info("找到 " + players.size() + " 个 10 级玩家");
});
```

### 方式三：监听数据事件

```java
public class MyEventListener {
    
    @EventHandler
    private void onPlayerDataInit(AllayPlayerDataInitEvent event) {
        if (event.shouldLoadData()) {
            String playerId = event.getPlayerId();
            
            // 加载玩家数据
            Repository<PlayerData> repo = db.getRepository(PlayerData.class);
            repo.findById(playerId).thenAccept(optional -> {
                if (optional.isPresent()) {
                    PlayerData data = optional.get();
                    // 应用数据到玩家...
                } else {
                    // 创建新玩家数据...
                }
            });
        }
    }
    
    @EventHandler
    private void onPlayerDataSave(AllayPlayerDataSaveEvent event) {
        if (event.shouldPersist() && !event.isCancelled()) {
            String playerId = event.getPlayerId();
            
            // 持久化玩家数据
            PlayerData data = collectPlayerData(playerId);
            Repository<PlayerData> repo = db.getRepository(PlayerData.class);
            repo.save(data, CacheStrategy.WRITE_THROUGH);
        }
    }
}
```

---

## 🔧 高级配置

### 缓存策略

```java
// 四种缓存策略
repo.save(data, CacheStrategy.CACHE_ONLY);      // 仅写缓存
repo.save(data, CacheStrategy.PERSIST_ONLY);    // 仅写数据库
repo.save(data, CacheStrategy.CACHE_FIRST);     // 先缓存，延迟持久化（默认）
repo.save(data, CacheStrategy.WRITE_THROUGH);   // 同时写入
```

### 表结构定义

```java
@Table(value = "custom_table", cacheTTL = 7200)  // 自定义表名和缓存时间
public class CustomData {
    @PrimaryKey(autoGenerate = true)
    private String id;
    
    @Column(value = "user_name", nullable = false, length = 50)
    private String userName;
    
    @Column(type = "TEXT")
    private String description;
    
    @Index(unique = true)
    @Column
    private String email;
    
    @Transient  // 不持久化
    private transient boolean online;
}
```

---

## 📊 架构改进

相比原 Nukkit 版本的改进：

| 特性 | 原设计 | 新设计 |
|------|--------|--------|
| **数据库抽象** | MySQL/Redis 直接耦合 | StorageProvider 统一接口 |
| **类型安全** | `Map<String, Object>` | 泛型 `Repository<T>` + 注解 |
| **数据库支持** | MySQL + Redis | **MySQL + Redis + SQLite** |
| **平台解耦** | 与 Nukkit 耦合 | **API/Core/Platform 三层分离** |
| **事件系统** | 依赖 Nukkit 事件 | **平台无关事件接口** |
| **线程安全** | 基础异步 | **Allay 多线程优化** |

---

## 📦 模块结构

```
yrdatabase-api/       # 纯 API 接口（无平台依赖）
yrdatabase-core/      # 核心实现（Redis/MySQL/SQLite）
yrdatabase-allay/     # Allay 平台插件
yrdatabase-nukkit/    # Nukkit 平台插件（待实现）
yrdatabase-waterdog/  # WaterdogPE 代理端（待实现）
```

---

## ⚙️ 构建

### 前置要求
- Java 21
- Gradle 8.14+

### 构建命令

```bash
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-21
gradlew.bat build

# 生成插件 JAR
gradlew.bat :yrdatabase-allay:jar
```

生成的插件位于：`yrdatabase-allay/build/libs/yrdatabase-allay-1.0.0-SNAPSHOT.jar`

---

## 🔮 TODO

- [ ] 添加 Nukkit 兼容模块
- [ ] 添加 WaterdogPE 跨服支持
- [ ] 实现 `/yrdb` 管理命令
- [ ] 性能监控和统计
- [ ] 单元测试

---

## 📄 许可证

原作者：YiranKuma  
GitHub: https://github.com/MufHead/YRDatabase

---

## 💬 支持

如有问题或建议，请在 GitHub 仓库提交 Issue。
