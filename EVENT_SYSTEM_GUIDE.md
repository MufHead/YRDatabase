# YRDatabase 事件系统使用指南

## 概述

YRDatabase 提供了智能的玩家数据事件系统，能够自动适配是否使用WaterdogPE，让其他插件能够正确地处理玩家数据的初始化和持久化。

---

## 核心事件

### 1. PlayerDataInitializeEvent - 玩家数据初始化事件

当玩家需要初始化数据时触发。

**触发时机**：
- **没有WaterdogPE**：玩家加入子服时（无法区分转服）
- **有WaterdogPE**：
  - 收到真实加入消息时（真实加入）
  - 玩家加入但未收到消息时（可能是转服，仍需加载缓存）

**事件类**：
```java
com.yirankuma.yrdatabase.event.PlayerDataInitializeEvent
```

**方法**：
- `Player getPlayer()` - 获取玩家对象
- `String getUid()` - 获取玩家唯一ID
- `InitializeReason getReason()` - 获取初始化原因
- `boolean isRealJoin()` - 是否是真实加入（非转服）

**InitializeReason枚举**：
- `REAL_JOIN` - 真实加入（WaterdogPE确认）
- `LOCAL_JOIN` - 本地加入（没有WaterdogPE）
- `SERVER_TRANSFER` - 转服（但仍需加载数据到缓存）

### 2. PlayerDataPersistEvent - 玩家数据持久化事件

当玩家数据需要持久化时触发。

**触发时机**：
- **没有WaterdogPE**：玩家退出子服时（无法区分转服）
- **有WaterdogPE**：
  - 收到真实退出消息时（真实退出）
  - 玩家退出但仍在线（转服，不应持久化）
- **服务器关闭**：所有在线玩家

**事件类**：
```java
com.yirankuma.yrdatabase.event.PlayerDataPersistEvent
```

**方法**：
- `Player getPlayer()` - 获取玩家对象
- `String getUid()` - 获取玩家唯一ID
- `PersistReason getReason()` - 获取持久化原因
- `boolean isRealQuit()` - 是否是真实退出（非转服）
- `boolean shouldPersist()` - 是否应该持久化（非转服）
- `boolean isCancelled()` - 是否已取消
- `void setCancelled(boolean)` - 设置是否取消

**PersistReason枚举**：
- `REAL_QUIT` - 真实退出（WaterdogPE确认）
- `LOCAL_QUIT` - 本地退出（没有WaterdogPE）
- `SERVER_TRANSFER` - 转服（不应持久化）
- `SERVER_SHUTDOWN` - 服务器关闭

---

## 智能适配逻辑

### 场景1: 没有配置WaterdogPE

```
玩家加入子服
  ↓
触发 PlayerDataInitializeEvent (reason=LOCAL_JOIN)
  ↓
你的插件监听：初始化数据（从MySQL加载）
  ↓
玩家游戏中...
  ↓
玩家退出子服
  ↓
触发 PlayerDataPersistEvent (reason=LOCAL_QUIT)
  ↓
你的插件监听：持久化数据（保存到MySQL）
```

**特点**：无法区分转服，每次都会持久化

### 场景2: 配置了WaterdogPE + Redis Pub/Sub

```
玩家加入代理
  ↓
WaterdogPE发送REAL_JOIN消息
  ↓
触发 PlayerDataInitializeEvent (reason=REAL_JOIN)
  ↓
你的插件监听：初始化数据（从MySQL加载）
  ↓
玩家转服（lobby -> survival）
  ↓
触发 PlayerDataInitializeEvent (reason=SERVER_TRANSFER)
  ↓
你的插件监听：只加载到缓存，不重新初始化
  ↓
（不触发PlayerDataPersistEvent，因为没有真实退出）
  ↓
玩家退出代理
  ↓
WaterdogPE发送REAL_QUIT消息
  ↓
触发 PlayerDataPersistEvent (reason=REAL_QUIT)
  ↓
你的插件监听：持久化数据（保存到MySQL）
```

**特点**：完美区分转服和真实退出，转服不持久化

---

## 使用示例

### 基础示例：监听事件

```java
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.plugin.PluginBase;
import com.yirankuma.yrdatabase.event.PlayerDataInitializeEvent;
import com.yirankuma.yrdatabase.event.PlayerDataPersistEvent;

public class MyPlugin extends PluginBase implements Listener {

    @Override
    public void onEnable() {
        // 注册事件监听
        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * 监听玩家数据初始化事件
     */
    @EventHandler
    public void onPlayerDataInitialize(PlayerDataInitializeEvent event) {
        String uid = event.getUid();

        if (event.isRealJoin()) {
            // 真实加入，需要完整初始化
            getLogger().info("玩家真实加入: " + event.getPlayer().getName());
            loadPlayerData(uid);
        } else if (event.getReason() == PlayerDataInitializeEvent.InitializeReason.SERVER_TRANSFER) {
            // 转服，只加载到缓存
            getLogger().info("玩家转服: " + event.getPlayer().getName());
            loadPlayerDataToCache(uid);
        } else {
            // 本地加入（没有WaterdogPE）
            getLogger().info("玩家加入: " + event.getPlayer().getName());
            loadPlayerData(uid);
        }
    }

    /**
     * 监听玩家数据持久化事件
     */
    @EventHandler
    public void onPlayerDataPersist(PlayerDataPersistEvent event) {
        String uid = event.getUid();

        if (event.shouldPersist()) {
            // 应该持久化（非转服）
            getLogger().info("持久化玩家数据: " + event.getPlayer().getName());
            savePlayerData(uid);
        } else {
            // 转服，不持久化
            getLogger().info("玩家转服，跳过持久化: " + event.getPlayer().getName());
        }
    }

    private void loadPlayerData(String uid) {
        // 从MySQL加载数据
    }

    private void loadPlayerDataToCache(String uid) {
        // 只加载到缓存，不重新初始化
    }

    private void savePlayerData(String uid) {
        // 保存到MySQL
    }
}
```

### 完整示例：玩家数据管理插件

```java
import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.plugin.PluginBase;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.yirankuma.yrdatabase.YRDatabase;
import com.yirankuma.yrdatabase.api.DatabaseManager;
import com.yirankuma.yrdatabase.event.PlayerDataInitializeEvent;
import com.yirankuma.yrdatabase.event.PlayerDataPersistEvent;

import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataPlugin extends PluginBase implements Listener {

    private DatabaseManager database;
    private Gson gson;
    private ConcurrentHashMap<String, JsonObject> playerCache = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        // 检查YRDatabase
        if (getServer().getPluginManager().getPlugin("YRDatabase") == null) {
            getLogger().error("YRDatabase插件未找到！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 获取DatabaseManager
        database = YRDatabase.getDatabaseManager();
        gson = new Gson();

        // 注册事件
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("PlayerDataPlugin 已启用");
    }

    /**
     * 玩家数据初始化
     * 优先级设为LOWEST，确保在其他插件之前处理
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDataInitialize(PlayerDataInitializeEvent event) {
        Player player = event.getPlayer();
        String uid = event.getUid();

        switch (event.getReason()) {
            case REAL_JOIN:
                // 真实加入：完整初始化
                getLogger().info("真实加入: " + player.getName());
                initializeNewPlayer(uid);
                break;

            case SERVER_TRANSFER:
                // 转服：检查缓存，如果没有则加载
                getLogger().info("转服: " + player.getName());
                if (!playerCache.containsKey(uid)) {
                    loadPlayerDataFromDatabase(uid);
                }
                break;

            case LOCAL_JOIN:
                // 本地加入：无法区分转服，统一初始化
                getLogger().info("本地加入: " + player.getName());
                initializeNewPlayer(uid);
                break;
        }
    }

    /**
     * 玩家数据持久化
     * 优先级设为HIGHEST，确保在其他插件之后处理
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDataPersist(PlayerDataPersistEvent event) {
        Player player = event.getPlayer();
        String uid = event.getUid();

        // 检查是否应该持久化
        if (!event.shouldPersist()) {
            getLogger().info("转服，跳过持久化: " + player.getName());
            return;
        }

        // 检查事件是否被取消
        if (event.isCancelled()) {
            getLogger().info("持久化已被取消: " + player.getName());
            return;
        }

        // 持久化数据
        switch (event.getReason()) {
            case REAL_QUIT:
                getLogger().info("真实退出，持久化: " + player.getName());
                persistPlayerData(uid);
                break;

            case LOCAL_QUIT:
                getLogger().info("本地退出，持久化: " + player.getName());
                persistPlayerData(uid);
                break;

            case SERVER_SHUTDOWN:
                getLogger().info("服务器关闭，持久化: " + player.getName());
                persistPlayerData(uid);
                break;

            case SERVER_TRANSFER:
                // 不应该到这里，但以防万一
                getLogger().warning("转服触发了持久化事件？跳过: " + player.getName());
                break;
        }

        // 清理缓存
        playerCache.remove(uid);
    }

    /**
     * 初始化新玩家
     */
    private void initializeNewPlayer(String uid) {
        // 从数据库加载
        database.smartGet("playerdata:" + uid).thenAccept(json -> {
            if (json != null) {
                // 已有数据
                JsonObject data = gson.fromJson(json, JsonObject.class);
                playerCache.put(uid, data);
                getLogger().info("加载玩家数据: " + uid);
            } else {
                // 新玩家，创建默认数据
                JsonObject data = createDefaultData(uid);
                playerCache.put(uid, data);
                getLogger().info("创建新玩家数据: " + uid);
            }
        });
    }

    /**
     * 从数据库加载到缓存
     */
    private void loadPlayerDataFromDatabase(String uid) {
        database.smartGet("playerdata:" + uid).thenAccept(json -> {
            if (json != null) {
                JsonObject data = gson.fromJson(json, JsonObject.class);
                playerCache.put(uid, data);
            }
        });
    }

    /**
     * 持久化玩家数据
     */
    private void persistPlayerData(String uid) {
        JsonObject data = playerCache.get(uid);
        if (data == null) {
            getLogger().warning("玩家数据不存在: " + uid);
            return;
        }

        // 更新最后登录时间
        data.addProperty("lastQuit", System.currentTimeMillis());

        // 保存到数据库
        String json = gson.toJson(data);
        database.smartSet("playerdata:" + uid, json, 0).thenAccept(success -> {
            if (success) {
                getLogger().info("玩家数据已保存: " + uid);
            } else {
                getLogger().error("玩家数据保存失败: " + uid);
            }
        });
    }

    /**
     * 创建默认数据
     */
    private JsonObject createDefaultData(String uid) {
        JsonObject data = new JsonObject();
        data.addProperty("uid", uid);
        data.addProperty("firstJoin", System.currentTimeMillis());
        data.addProperty("level", 1);
        data.addProperty("exp", 0);
        data.addProperty("coins", 100);
        return data;
    }

    /**
     * 获取玩家数据（供其他方法使用）
     */
    public JsonObject getPlayerData(String uid) {
        return playerCache.get(uid);
    }

    /**
     * 更新玩家数据
     */
    public void updatePlayerData(String uid, String key, Object value) {
        JsonObject data = playerCache.get(uid);
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

---

## 最佳实践

### 1. 事件优先级

```java
// 数据加载：使用LOWEST优先级，确保最先执行
@EventHandler(priority = EventPriority.LOWEST)
public void onPlayerDataInitialize(PlayerDataInitializeEvent event) {
    // 加载玩家数据
}

// 数据持久化：使用HIGHEST优先级，确保最后执行
@EventHandler(priority = EventPriority.HIGHEST)
public void onPlayerDataPersist(PlayerDataPersistEvent event) {
    // 保存玩家数据
}
```

### 2. 区分初始化原因

```java
@EventHandler
public void onPlayerDataInitialize(PlayerDataInitializeEvent event) {
    switch (event.getReason()) {
        case REAL_JOIN:
            // 真实加入：完整初始化，可能需要触发首次加入逻辑
            fullInitialize(event.getUid());
            break;

        case SERVER_TRANSFER:
            // 转服：只加载缓存，不触发首次加入逻辑
            loadToCache(event.getUid());
            break;

        case LOCAL_JOIN:
            // 本地加入：当作真实加入处理（无法区分）
            fullInitialize(event.getUid());
            break;
    }
}
```

### 3. 检查是否应该持久化

```java
@EventHandler
public void onPlayerDataPersist(PlayerDataPersistEvent event) {
    // 方法1: 使用shouldPersist()
    if (!event.shouldPersist()) {
        return;  // 转服，不持久化
    }

    // 方法2: 检查原因
    if (event.getReason() == PlayerDataPersistEvent.PersistReason.SERVER_TRANSFER) {
        return;  // 转服，不持久化
    }

    // 持久化数据
    saveData(event.getUid());
}
```

### 4. 取消持久化（在特定情况下）

```java
@EventHandler
public void onPlayerDataPersist(PlayerDataPersistEvent event) {
    // 如果玩家数据有问题，可以取消持久化
    if (hasDataCorruption(event.getUid())) {
        event.setCancelled(true);
        getLogger().warning("数据损坏，取消持久化: " + event.getPlayer().getName());
        return;
    }

    // 正常持久化
    saveData(event.getUid());
}
```

---

## plugin.yml 配置

```yaml
name: MyPlayerDataPlugin
version: 1.0.0
main: com.example.MyPlayerDataPlugin
api: ["1.0.13"]
depend: [YRDatabase]  # 声明依赖
description: Player data management using YRDatabase events
author: YourName
```

---

## build.gradle.kts 配置

```kotlin
repositories {
    mavenLocal()  // 或者你的Maven仓库
}

dependencies {
    compileOnly("cn.nukkit:nukkit:1.0-SNAPSHOT")
    compileOnly("com.yirankuma:yrdatabase-nukkit:1.0-SNAPSHOT")
}
```

---

## 调试和日志

### 查看事件触发情况

YRDatabase会输出详细的日志：

**没有WaterdogPE时**：
```log
[INFO] 玩家真实加入: TestPlayer (UID: 123456)
[DEBUG] 触发初始化事件: TestPlayer (UID: 123456, 原因: LOCAL_JOIN)
[INFO] 持久化玩家数据: TestPlayer
[DEBUG] 触发持久化事件: TestPlayer (UID: 123456, 原因: LOCAL_QUIT)
```

**有WaterdogPE时**：
```log
[INFO] 收到REAL_JOIN: TestPlayer (UID: 123456)
[DEBUG] 触发初始化事件: TestPlayer (UID: 123456, 原因: REAL_JOIN)
[INFO] 玩家转服: TestPlayer
[DEBUG] 触发初始化事件: TestPlayer (UID: 123456, 原因: SERVER_TRANSFER)
[INFO] 收到REAL_QUIT: TestPlayer (UID: 123456)
[DEBUG] 触发持久化事件: TestPlayer (UID: 123456, 原因: REAL_QUIT)
```

---

## 常见问题

### Q1: 如何知道当前环境是否使用了WaterdogPE？

**A**: 通过事件的原因判断：
```java
if (event.getReason() == PlayerDataInitializeEvent.InitializeReason.REAL_JOIN) {
    // 使用了WaterdogPE
} else if (event.getReason() == PlayerDataInitializeEvent.InitializeReason.LOCAL_JOIN) {
    // 没有使用WaterdogPE
}
```

### Q2: 转服时还会触发初始化事件吗？

**A**: 会，但原因是`SERVER_TRANSFER`。你应该只加载数据到缓存，不重新初始化。

### Q3: 如何确保数据不会在转服时丢失？

**A**:
1. 使用内存缓存保持数据
2. 只在`shouldPersist()`返回true时才持久化
3. 在`SERVER_TRANSFER`时保持缓存，不清理

### Q4: 服务器关闭时数据会保存吗？

**A**: 会！YRDatabase会在服务器关闭时触发所有在线玩家的持久化事件（reason=SERVER_SHUTDOWN）。

---

## 性能对比

### 没有WaterdogPE

```
玩家流程：
1. 加入lobby → 初始化 + 持久化
2. 转服到survival → 初始化 + 持久化
3. 转服到creative → 初始化 + 持久化
4. 退出 → 持久化

持久化次数：4次
```

### 使用WaterdogPE + 事件系统

```
玩家流程：
1. 加入代理 → 初始化
2. 转服到survival → 只加载缓存（不持久化）
3. 转服到creative → 只加载缓存（不持久化）
4. 退出 → 持久化

持久化次数：1次
```

**性能提升：75%减少**

---

## 相关文档

- [USAGE_EXAMPLE.md](USAGE_EXAMPLE.md) - YRDatabase基础使用示例
- [REDIS_PUBSUB_COMPLETE.md](REDIS_PUBSUB_COMPLETE.md) - Redis Pub/Sub功能说明
- [README.md](README.md) - 完整API文档

---

**总结**：使用YRDatabase事件系统，你的插件可以自动适配是否使用WaterdogPE，并智能地处理玩家数据的初始化和持久化，大幅减少数据库操作！
