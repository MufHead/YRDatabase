# YRDatabase 使用示例

## 作为依赖使用

YRDatabase现在可以作为Maven/Gradle依赖在其他项目中使用！

---

## 快速开始

### 方法1: 使用本地Maven仓库（最简单）

首先发布到本地：
```bash
cd YRDatabase
./gradlew publishToMavenLocal
```

然后在你的项目中使用：

**Gradle (Kotlin DSL)**:
```kotlin
// build.gradle.kts
repositories {
    mavenLocal()  // 添加本地Maven仓库
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-releases/")
}

dependencies {
    // 只使用API
    compileOnly("com.yirankuma:yrdatabase-common:1.0-SNAPSHOT")

    // 或依赖完整的Nukkit插件
    compileOnly("com.yirankuma:yrdatabase-nukkit:1.0-SNAPSHOT")
}
```

**Gradle (Groovy DSL)**:
```groovy
// build.gradle
repositories {
    mavenLocal()
    mavenCentral()
    maven { url 'https://repo.opencollab.dev/maven-releases/' }
}

dependencies {
    compileOnly 'com.yirankuma:yrdatabase-common:1.0-SNAPSHOT'
}
```

**Maven**:
```xml
<dependencies>
    <dependency>
        <groupId>com.yirankuma</groupId>
        <artifactId>yrdatabase-common</artifactId>
        <version>1.0-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

---

## 使用场景

### 场景1: 只使用API（推荐）

如果你只想调用YRDatabase的API，只需依赖common模块：

```kotlin
dependencies {
    compileOnly("com.yirankuma:yrdatabase-common:1.0-SNAPSHOT")
}
```

**代码示例**:
```java
import com.yirankuma.yrdatabase.YRDatabase;
import com.yirankuma.yrdatabase.api.DatabaseManager;

public class MyPlugin extends PluginBase {

    @Override
    public void onEnable() {
        // 获取DatabaseManager
        DatabaseManager db = YRDatabase.getDatabaseManager();

        // 使用API
        db.set("player:data:" + playerUid, playerData, 3600)
            .thenAccept(success -> {
                if (success) {
                    getLogger().info("数据保存成功");
                }
            });
    }
}
```

### 场景2: 开发Nukkit插件依赖

```kotlin
dependencies {
    compileOnly("cn.nukkit:nukkit:1.0-SNAPSHOT")
    compileOnly("com.yirankuma:yrdatabase-nukkit:1.0-SNAPSHOT")
}
```

**plugin.yml**:
```yaml
name: MyPlugin
version: 1.0.0
main: com.example.MyPlugin
depend: [YRDatabase]  # 声明依赖
```

**代码示例**:
```java
import com.yirankuma.yrdatabase.YRDatabase;
import com.yirankuma.yrdatabase.api.DatabaseManager;

public class MyPlugin extends PluginBase {

    private DatabaseManager database;

    @Override
    public void onEnable() {
        // 检查YRDatabase是否加载
        if (getServer().getPluginManager().getPlugin("YRDatabase") == null) {
            getLogger().error("YRDatabase插件未找到！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 获取DatabaseManager
        database = YRDatabase.getDatabaseManager();

        getLogger().info("成功连接到YRDatabase");
    }

    public void savePlayerData(String uid, String data) {
        // 智能保存（自动Redis缓存 + MySQL持久化）
        database.smartSet("player:" + uid, data, 3600)
            .thenAccept(success -> {
                if (success) {
                    getLogger().info("玩家数据保存成功: " + uid);
                }
            });
    }

    public void loadPlayerData(String uid) {
        // 智能读取（先Redis，再MySQL）
        database.smartGet("player:" + uid)
            .thenAccept(data -> {
                if (data != null) {
                    getLogger().info("玩家数据加载成功: " + data);
                } else {
                    getLogger().info("玩家数据不存在");
                }
            });
    }
}
```

### 场景3: 开发WaterdogPE插件依赖

```kotlin
dependencies {
    compileOnly("dev.waterdog.waterdogpe:waterdog:2.0.4-SNAPSHOT")
    compileOnly("com.yirankuma:yrdatabase-waterdog:1.0-SNAPSHOT")
}
```

---

## 完整项目示例

### 示例：玩家数据管理插件

**项目结构**:
```
MyPlayerDataPlugin/
├── build.gradle.kts
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/
│       │       ├── MyPlugin.java
│       │       └── PlayerDataManager.java
│       └── resources/
│           └── plugin.yml
```

**build.gradle.kts**:
```kotlin
plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenLocal()  // 使用本地Maven仓库
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-releases/")
}

dependencies {
    // Nukkit API
    compileOnly("cn.nukkit:nukkit:1.0-SNAPSHOT")

    // YRDatabase依赖
    compileOnly("com.yirankuma:yrdatabase-nukkit:1.0-SNAPSHOT")

    // Gson (如果需要)
    implementation("com.google.gson:gson:2.10.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

tasks.shadowJar {
    archiveFileName.set("MyPlayerDataPlugin.jar")

    // 不要打包YRDatabase，它已经作为插件存在
    dependencies {
        exclude(dependency("com.yirankuma:.*"))
    }

    // 只打包Gson
    relocate("com.google.gson", "com.example.libs.gson")
}
```

**plugin.yml**:
```yaml
name: MyPlayerDataPlugin
version: 1.0.0
main: com.example.MyPlugin
api: ["1.0.13"]
depend: [YRDatabase]
description: Player data management plugin using YRDatabase
author: YourName
```

**MyPlugin.java**:
```java
package com.example;

import cn.nukkit.Player;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import com.yirankuma.yrdatabase.YRDatabase;
import com.yirankuma.yrdatabase.api.DatabaseManager;

public class MyPlugin extends PluginBase implements Listener {

    private DatabaseManager database;
    private PlayerDataManager dataManager;

    @Override
    public void onEnable() {
        // 检查YRDatabase是否存在
        if (getServer().getPluginManager().getPlugin("YRDatabase") == null) {
            getLogger().error("YRDatabase插件未找到！请先安装YRDatabase");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 获取DatabaseManager
        database = YRDatabase.getDatabaseManager();

        if (database == null) {
            getLogger().error("无法获取DatabaseManager");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 初始化数据管理器
        dataManager = new PlayerDataManager(database, this);

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("MyPlayerDataPlugin 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().info("MyPlayerDataPlugin 已禁用！");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String uid = YRDatabase.getInstance().resolvePlayerId(player);

        // 异步加载玩家数据
        dataManager.loadPlayerData(uid).thenAccept(data -> {
            if (data != null) {
                getLogger().info("玩家 " + player.getName() + " 数据加载成功");
            } else {
                getLogger().info("玩家 " + player.getName() + " 是新玩家，创建数据");
                dataManager.createDefaultData(uid);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String uid = YRDatabase.getInstance().resolvePlayerId(player);

        // 异步保存玩家数据
        dataManager.savePlayerData(uid).thenAccept(success -> {
            if (success) {
                getLogger().info("玩家 " + player.getName() + " 数据保存成功");
            }
        });
    }
}
```

**PlayerDataManager.java**:
```java
package com.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.yirankuma.yrdatabase.api.DatabaseManager;
import cn.nukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final DatabaseManager database;
    private final Plugin plugin;
    private final Gson gson;

    // 内存缓存
    private final ConcurrentHashMap<String, JsonObject> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(DatabaseManager database, Plugin plugin) {
        this.database = database;
        this.plugin = plugin;
        this.gson = new Gson();
    }

    /**
     * 加载玩家数据
     */
    public CompletableFuture<JsonObject> loadPlayerData(String uid) {
        // 先检查内存缓存
        if (cache.containsKey(uid)) {
            return CompletableFuture.completedFuture(cache.get(uid));
        }

        // 从数据库加载
        return database.smartGet("playerdata:" + uid)
            .thenApply(json -> {
                if (json != null) {
                    JsonObject data = gson.fromJson(json, JsonObject.class);
                    cache.put(uid, data);
                    return data;
                }
                return null;
            });
    }

    /**
     * 保存玩家数据
     */
    public CompletableFuture<Boolean> savePlayerData(String uid) {
        JsonObject data = cache.get(uid);
        if (data == null) {
            return CompletableFuture.completedFuture(false);
        }

        String json = gson.toJson(data);

        // 使用smartSet自动保存到Redis和MySQL
        return database.smartSet("playerdata:" + uid, json, 3600);
    }

    /**
     * 创建默认数据
     */
    public void createDefaultData(String uid) {
        JsonObject data = new JsonObject();
        data.addProperty("uid", uid);
        data.addProperty("joinTime", System.currentTimeMillis());
        data.addProperty("level", 1);
        data.addProperty("exp", 0);
        data.addProperty("coins", 100);

        cache.put(uid, data);

        // 立即保存
        savePlayerData(uid);
    }

    /**
     * 获取玩家数据（从缓存）
     */
    public JsonObject getPlayerData(String uid) {
        return cache.get(uid);
    }

    /**
     * 更新玩家数据
     */
    public void updatePlayerData(String uid, String key, Object value) {
        JsonObject data = cache.get(uid);
        if (data != null) {
            if (value instanceof String) {
                data.addProperty(key, (String) value);
            } else if (value instanceof Number) {
                data.addProperty(key, (Number) value);
            } else if (value instanceof Boolean) {
                data.addProperty(key, (Boolean) value);
            }
        }
    }
}
```

### 编译和部署

```bash
# 1. 发布YRDatabase到本地Maven
cd YRDatabase
./gradlew publishToMavenLocal

# 2. 构建你的插件
cd MyPlayerDataPlugin
./gradlew shadowJar

# 3. 部署
cp build/libs/MyPlayerDataPlugin.jar /path/to/server/plugins/
```

---

## API参考

### DatabaseManager主要方法

```java
// 基础操作
CompletableFuture<Boolean> set(String key, String value, long expireSeconds);
CompletableFuture<String> get(String key);
CompletableFuture<Boolean> delete(String key);
CompletableFuture<Boolean> exists(String key);

// 智能API（推荐）
CompletableFuture<Boolean> smartSet(String key, String value, long expireSeconds);
CompletableFuture<String> smartGet(String key);

// Hash操作
CompletableFuture<Boolean> hset(String key, String field, String value);
CompletableFuture<String> hget(String key, String field);
CompletableFuture<Map<String, String>> hgetAll(String key);
CompletableFuture<Boolean> hdel(String key, String field);

// 批量操作
CompletableFuture<Map<String, String>> smartBatchGet(List<String> keys);
CompletableFuture<Boolean> smartBatchSet(Map<String, String> data, long expireSeconds);

// 连接状态
boolean isRedisConnected();
boolean isMySQLConnected();
```

### 获取玩家ID

```java
// 获取玩家唯一ID（支持网易UID）
String uid = YRDatabase.getInstance().resolvePlayerId(player);
```

---

## 常见问题

### Q1: 编译时提示找不到YRDatabase

**A**: 确保已经执行了 `./gradlew publishToMavenLocal`

### Q2: 运行时提示ClassNotFoundException

**A**:
1. 确保服务器上已安装YRDatabase插件
2. 在plugin.yml中添加 `depend: [YRDatabase]`
3. 不要在shadowJar中打包YRDatabase

### Q3: 如何使用最新版本？

**A**:
```bash
# 每次YRDatabase更新后，重新发布
cd YRDatabase
./gradlew clean publishToMavenLocal

# 然后重新构建你的项目
cd YourPlugin
./gradlew clean build
```

### Q4: 可以同时依赖多个模块吗？

**A**: 可以，但通常不需要。推荐：
- 开发Nukkit插件 → 只依赖 `yrdatabase-nukkit`
- 开发WaterdogPE插件 → 只依赖 `yrdatabase-waterdog`
- 只使用API → 只依赖 `yrdatabase-common`

---

## 更多示例

查看项目中的其他文档：
- [MAVEN_PUBLISH_GUIDE.md](MAVEN_PUBLISH_GUIDE.md) - 发布指南
- [README.md](README.md) - 完整API文档
- [REDIS_PUBSUB_COMPLETE.md](REDIS_PUBSUB_COMPLETE.md) - Redis Pub/Sub说明

---

**提示**: 如果你要分享你的插件给其他开发者，记得在README中说明需要安装YRDatabase作为前置插件！
