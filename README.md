# YRDatabase - 多平台数据库前置插件

[![JitPack](https://jitpack.io/v/MufHead/YRDatabase.svg)](https://jitpack.io/#MufHead/YRDatabase)

## 📖 简介

YRDatabase 是一款强大的多平台数据库前置插件，支持 **Allay**、**NukkitMOT** 和 **WaterdogPE**。提供 Redis 缓存 + MySQL/SQLite 持久化的双层架构，专为 Minecraft Bedrock Edition 服务器优化。

### ✨ 主要特性

- ✅ **双层缓存架构** - Redis 缓存 + MySQL/SQLite 持久化
- ✅ **类型安全 API** - 支持泛型 Repository 和实体映射
- ✅ **全异步设计** - 所有操作返回 CompletableFuture
- ✅ **多平台支持** - Allay / NukkitMOT / WaterdogPE
- ✅ **跨服会话管理** - Redis Pub/Sub 实现真实加入/退出检测
- ✅ **性能监控** - 内置指标收集和统计
- ✅ **灵活配置** - YAML 配置文件，支持热重载

---

## 🎯 支持平台

| 平台 | 模块 | 状态 |
|------|------|------|
| **Allay** | yrdatabase-allay | ✅ 完成 |
| **NukkitMOT** | yrdatabase-nukkit | ✅ 完成 |
| **WaterdogPE** | yrdatabase-waterdog | ✅ 完成 |

---

## 🚀 快速开始

### 1. 下载安装

从 [Releases](https://github.com/MufHead/YRDatabase/releases) 下载对应平台的 JAR 文件：

| 平台 | 文件名 |
|------|--------|
| Allay 服务器 | `yrdatabase-allay-x.x.x.jar` |
| NukkitMOT 服务器 | `yrdatabase-nukkit-x.x.x.jar` |
| WaterdogPE 代理 | `yrdatabase-waterdog-x.x.x.jar` |

将 JAR 放入对应服务器的 `plugins` 目录。

### 2. 配置

首次启动会生成默认配置 `plugins/YRDatabase/config.yml`：

```yaml
mode: standalone  # standalone（单服）/ proxy（跨服）

# Redis 缓存层配置（跨服必需，单服可选）
cache:
  enabled: false
  host: localhost
  port: 6379
  password: ""
  database: 0

# 持久化层配置
persist:
  enabled: true
  type: sqlite  # sqlite 或 mysql
  
  sqlite:
    file: data.db
  
  mysql:
    host: localhost
    port: 3306
    database: yrdatabase
    username: root
    password: ""
```

### 3. 管理命令

```
/yrdb help   - 显示帮助信息
/yrdb status - 查看数据库连接状态
/yrdb reload - 重新加载配置文件
/yrdb info   - 显示插件信息
/yrdb test   - 测试数据库读写操作
/yrdb stats  - 查看性能统计数据
```

---

## 📦 作为依赖使用

其他插件可以通过 JitPack 依赖 YRDatabase API：

| Artifact | 说明 |
|----------|------|
| `yrdatabase-api` | 平台无关的 API 与注解 |
| `yrdatabase-core` | Redis/MySQL/SQLite 实现，可在独立环境中使用 |
| `yrdatabase-allay` | Allay 平台插件（包含 `YRDatabaseAllay` 静态入口） |
| `yrdatabase-nukkit` | NukkitMOT 平台插件 |
| `yrdatabase-waterdog` | WaterdogPE 代理插件 |

引用平台模块时同样使用 JitPack 坐标，例如 Allay 插件：

```groovy
dependencies {
    compileOnly 'com.github.MufHead.YRDatabase:yrdatabase-allay:2.0.0'
}
```

### Gradle (Groovy)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.MufHead.YRDatabase:yrdatabase-api:2.0.0'
}
```

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.MufHead.YRDatabase:yrdatabase-api:2.0.0")
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.MufHead.YRDatabase</groupId>
        <artifactId>yrdatabase-api</artifactId>
        <version>2.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

---

## 📝 API 使用示例

### 获取 DatabaseManager

```java
// Allay
DatabaseManager db = YRDatabaseAllay.getDatabaseManager();

// NukkitMOT
DatabaseManager db = YRDatabaseNukkit.getDatabaseManager();
```

### 方式一：简单 Map API

```java
// 保存数据
Map<String, Object> playerData = new HashMap<>();
playerData.put("name", "Steve");
playerData.put("level", 10);
playerData.put("coins", 1000L);

db.set("players", playerId, playerData).thenAccept(success -> {
    if (success) {
        logger.info("数据保存成功");
    }
});

// 获取数据
db.get("players", playerId).thenAccept(optional -> {
    optional.ifPresent(data -> {
        int level = ((Number) data.get("level")).intValue();
        logger.info("玩家等级: " + level);
    });
});

// 删除数据
db.delete("players", playerId);

// 检查存在
db.exists("players", playerId).thenAccept(exists -> {
    logger.info("玩家存在: " + exists);
});
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
repo.save(player);

// 4. 查询数据
repo.findById(uuid).thenAccept(optional -> {
    optional.ifPresent(p -> {
        int level = p.getLevel();  // 类型安全！
        logger.info("等级: " + level);
    });
});

// 5. 条件查询
repo.findBy("level", 10).thenAccept(players -> {
    logger.info("找到 " + players.size() + " 个 10 级玩家");
});

// 6. 删除
repo.deleteById(uuid);
```

### 缓存策略

```java
// 四种缓存策略
db.set(table, key, data, CacheStrategy.CACHE_ONLY);      // 仅写缓存
db.set(table, key, data, CacheStrategy.PERSIST_ONLY);    // 仅写数据库
db.set(table, key, data, CacheStrategy.CACHE_FIRST);     // 先缓存，延迟持久化（默认）
db.set(table, key, data, CacheStrategy.WRITE_THROUGH);   // 同时写入缓存和数据库
```

---

## 🏗️ 模块架构

```
YRDatabase/
├── yrdatabase-api/        # API 层 - 接口、注解、DTO（无平台依赖）
├── yrdatabase-core/       # 核心层 - 数据库实现（Redis/MySQL/SQLite）
├── yrdatabase-allay/      # Allay 平台插件
├── yrdatabase-nukkit/     # NukkitMOT 平台插件
└── yrdatabase-waterdog/   # WaterdogPE 代理插件
```

### 依赖关系

```
其他插件 ──compileOnly──▶ yrdatabase-api
                              │
                              ▼
                        yrdatabase-core
                              │
            ┌─────────────────┼─────────────────┐
            ▼                 ▼                 ▼
    yrdatabase-allay  yrdatabase-nukkit  yrdatabase-waterdog
```

### 设计原则

| 特性 | 说明 |
|------|------|
| **高内聚** | 每个模块职责单一明确 |
| **低耦合** | 通过接口通信，平台模块互不依赖 |
| **可扩展** | 新增平台只需实现平台适配层 |

---

## 📊 相比原版改进

| 特性 | 原 Nukkit 版本 | 新版本 |
|------|----------------|--------|
| **数据库抽象** | 直接耦合 | StorageProvider 接口 |
| **类型安全** | `Map<String, Object>` | 泛型 `Repository<T>` + 注解 |
| **数据库支持** | MySQL + Redis | MySQL + Redis + **SQLite** |
| **平台解耦** | Nukkit 专用 | **API/Core/Platform 分离** |
| **跨服支持** | 基础 | **Redis Pub/Sub + WaterdogPE** |
| **性能监控** | 无 | **内置指标收集** |
| **配置格式** | JSON | **YAML** |

---

## ⚙️ 构建项目

### 前置要求

- Java 21
- Gradle 8.x

### 构建命令

```bash
# Windows (需要使用 Java 21)
set JAVA_HOME=C:\Program Files\Java\jdk-21
gradlew.bat build -x test

# Linux / macOS
export JAVA_HOME=/path/to/jdk-21
./gradlew build -x test
```

### 输出文件

```
yrdatabase-allay/build/libs/yrdatabase-allay-2.0.0.jar
yrdatabase-nukkit/build/libs/yrdatabase-nukkit-2.0.0.jar
yrdatabase-waterdog/build/libs/yrdatabase-waterdog-2.0.0.jar
```

---

## 📄 许可证

MIT License

---

## 👤 作者

**YiranKuma**

- GitHub: [@MufHead](https://github.com/MufHead)

---

## 💬 支持

如有问题或建议，请在 [GitHub Issues](https://github.com/MufHead/YRDatabase/issues) 提交。
