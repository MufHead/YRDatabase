# YRDatabase 架构设计文档

本文档详细说明了YRDatabase的技术架构、设计决策和实现细节。

---

## 🏗️ 总体架构

### 系统架构图

```
                                互联网
                                  │
                                  │ TCP连接
                                  v
                   ┌──────────────────────────────┐
                   │     WaterdogPE 代理服务器     │
                   │  (yrdatabase-waterdog.jar)   │
                   │                              │
                   │  ┌────────────────────────┐  │
                   │  │ YRDatabaseWaterdog     │  │
                   │  │  - 监听玩家登录        │  │
                   │  │  - 监听玩家断开        │  │
                   │  │  - 发送Plugin Messaging│  │
                   │  └────────────────────────┘  │
                   └───────────┬──────────────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │ Plugin Messaging   │   Plugin Messaging │
          │ (yrdatabase:main)  │   (yrdatabase:main)│
          v                    v                    v
    ┌─────────────┐      ┌─────────────┐      ┌─────────────┐
    │ Nukkit子服1  │      │ Nukkit子服2  │      │ Nukkit子服3  │
    │   (Lobby)   │      │ (Survival)  │      │ (Creative)  │
    │             │      │             │      │             │
    │ YRDatabase  │      │ YRDatabase  │      │ YRDatabase  │
    │   ├─ API    │      │   ├─ API    │      │   ├─ API    │
    │   ├─ Cache  │      │   ├─ Cache  │      │   ├─ Cache  │
    │   └─ Persist│      │   └─ Persist│      │   └─ Persist│
    └──────┬──────┘      └──────┬──────┘      └──────┬──────┘
           │                    │                    │
           │                    │                    │
           └────────────────────┼────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    │                       │
                    v                       v
            ┌──────────────┐        ┌──────────────┐
            │    Redis     │        │    MySQL     │
            │   (缓存层)    │        │  (持久层)    │
            │              │        │              │
            │ - KV存储     │        │ - 关系数据   │
            │ - 分布式锁   │        │ - 事务支持   │
            │ - Pub/Sub    │        │ - 索引查询   │
            └──────────────┘        └──────────────┘
```

---

## 📦 模块划分

### 1. yrdatabase-common (公共模块)

**职责**：定义跨模块的通信协议和数据结构

**包结构**：
```
com.yirankuma.yrdatabase.common/
├── protocol/
│   ├── MessageType.java          # 消息类型枚举（6种）
│   ├── PluginMessage.java        # 消息数据结构
│   ├── MessageCodec.java         # 二进制编解码器
│   └── ProtocolConstants.java    # 协议常量
└── session/
    └── PlayerSession.java        # 玩家会话对象
```

**依赖**：
- Gson 2.10.1 (JSON序列化)
- SLF4J 1.7.36 (日志接口)

**关键设计**：

#### 消息协议格式
```
[4字节] 魔数 (0x59524442 = "YRDB")
[1字节] 协议版本 (0x01)
[1字节] 消息类型 (0x01~0x06)
[8字节] 时间戳 (long)
[4字节] 数据长度 (int)
[N字节] JSON数据 (UTF-8)
[4字节] CRC32校验和
```

#### 安全机制
- **魔数验证**：防止非法消息
- **版本控制**：向后兼容
- **校验和**：防止数据损坏
- **过期检测**：丢弃30秒以上的消息

---

### 2. yrdatabase-waterdog (代理端插件)

**职责**：检测玩家真实加入/退出，通知所有子服

**核心类**：
```
com.yirankuma.yrdatabase.waterdog/
└── YRDatabaseWaterdog.java       # 主插件类
    ├── onPlayerLogin()           # 真实加入 → 发送REAL_JOIN
    ├── onPlayerDisconnect()      # 真实退出 → 发送REAL_QUIT
    ├── onServerTransfer()        # 转服记录（不触发持久化）
    └── startHeartbeat()          # 心跳任务（10秒间隔）
```

**依赖**：
- yrdatabase-common (公共模块)
- WaterdogPE 2.0.4-SNAPSHOT (代理API)

**事件监听**：
| 事件 | 处理 | 发送消息 |
|------|------|----------|
| PlayerLoginEvent | 标记真实加入 | REAL_JOIN (广播) |
| PlayerDisconnectedEvent | 标记真实退出 | REAL_QUIT (单播) |
| ServerTransferRequestEvent | 记录转服 | SERVER_TRANSFER (可选) |

**心跳机制**：
```java
heartbeatScheduler.scheduleAtFixedRate(() -> {
    sendHeartbeat();
}, 10, 10, TimeUnit.SECONDS);
```

---

### 3. yrdatabase-nukkit (子服端插件)

**职责**：接收会话消息，管理数据初始化和持久化

**核心类**：
```
com.yirankuma.yrdatabase/
├── YRDatabase.java                    # 主插件类 (实现PluginMessageListener)
├── api/
│   └── DatabaseManager.java           # 数据库API接口 (311行)
├── config/
│   └── DatabaseConfig.java            # 配置类 (104行)
├── impl/
│   └── DatabaseManagerImpl.java       # 核心实现 (1036行)
├── mysql/
│   └── MySQLManager.java              # MySQL驱动 (358行)
└── redis/
    └── RedisManager.java              # Redis驱动 (187行)
```

**依赖**：
- yrdatabase-common (公共模块)
- Nukkit 1.0 (服务器API)
- Lettuce 6.1.10 (Redis客户端)
- HikariCP 4.0.3 (MySQL连接池)
- MySQL Connector 8.0.33
- Guava 31.1-jre (缓存)

**数据流**：

#### 真实加入流程
```
1. 收到REAL_JOIN消息
   ↓
2. 存入realOnlinePlayers缓存 (60秒过期)
   ↓
3. 等待PlayerJoinEvent
   ↓
4. 检查缓存是否存在
   ↓
5. 如果存在 → 初始化数据 (从MySQL加载)
   如果不存在 → 跳过 (转服场景)
```

#### 真实退出流程
```
1. 收到REAL_QUIT消息
   ↓
2. 从realOnlinePlayers缓存中移除
   ↓
3. 加入persistQueue持久化队列
   ↓
4. 后台线程异步处理
   ↓
5. 持久化到MySQL
   ↓
6. 清除Redis缓存 (可选)
```

#### 转服流程（优化点）
```
玩家从服务器A转到服务器B:

服务器A:
  PlayerQuitEvent → 不持久化 (等待REAL_QUIT)

服务器B:
  PlayerJoinEvent → 检查缓存 → 不存在 → 跳过初始化

结果: 0次数据库查询，0次持久化
```

---

## 🔐 安全设计

### 1. 消息安全

#### 防篡改
```java
// 计算校验和
private static int calculateChecksum(byte[] data) {
    int checksum = 0;
    for (byte b : data) {
        checksum = (checksum << 1) | (checksum >>> 31);
        checksum ^= b & 0xFF;
    }
    return checksum;
}
```

#### 防重放攻击
```java
// 检查消息是否过期
long messageAge = System.currentTimeMillis() - pluginMessage.getTimestamp();
if (messageAge > ProtocolConstants.MESSAGE_EXPIRY_MS) {
    getLogger().warning("收到过期消息 (延迟: " + messageAge + "ms)");
    return; // 丢弃
}
```

### 2. 并发安全

#### 缓存线程安全
```java
// 使用Guava的ConcurrentHashMap实现
private Cache<Long, Long> realOnlinePlayers = CacheBuilder.newBuilder()
    .expireAfterWrite(60, TimeUnit.SECONDS)
    .maximumSize(10000)
    .build(); // 线程安全
```

#### 持久化队列
```java
// 无锁并发队列
private final BlockingQueue<Long> persistQueue = new LinkedBlockingQueue<>();

// 多线程消费
for (int i = 0; i < 2; i++) {
    persistExecutor.submit(() -> {
        while (!Thread.currentThread().isInterrupted()) {
            Long uid = persistQueue.poll(1, TimeUnit.SECONDS);
            if (uid != null) {
                persistPlayerDataSync(uid);
            }
        }
    });
}
```

### 3. 数据一致性

#### 问题：玩家转服期间的并发写入
```
场景:
  T0: 玩家在服务器A修改数据
  T1: 玩家开始转服到服务器B
  T2: 服务器A尝试持久化
  T3: 服务器B加载数据

风险: T3可能读到T2之前的旧数据
```

#### 解决方案1：分布式锁（推荐）
```java
// 使用Redis SET NX EX命令实现分布式锁
public CompletableFuture<Boolean> acquireLock(String key, int expireSeconds) {
    String lockKey = ProtocolConstants.REDIS_KEY_LOCK_PREFIX + key;
    return redisManager.set(lockKey, "locked", expireSeconds, true); // NX
}

// 持久化前先获取锁
if (acquireLock(uid, 30).get()) {
    try {
        persistData(uid);
    } finally {
        releaseLock(uid);
    }
}
```

#### 解决方案2：版本号机制
```java
// 数据结构
{
    "player_data": {...},
    "version": 12345678901234,  // 时间戳
    "server": "lobby"
}

// 写入前检查版本
if (remoteVersion > localVersion) {
    // 丢弃本地修改，重新加载
    reloadData(uid);
}
```

---

## ⚡ 性能优化

### 1. 减少数据库压力

#### 优化前（每次转服都持久化）
```
玩家行为:
  登录 → 进入lobby → 转到survival → 转到creative → 退出

数据库操作:
  初始化: 1次读取
  持久化: 4次写入 (lobby退出、survival退出、creative退出、真实退出)

总计: 1读 + 4写 = 5次操作
```

#### 优化后（仅真实退出持久化）
```
玩家行为:
  登录 → 进入lobby → 转到survival → 转到creative → 退出

数据库操作:
  初始化: 1次读取
  持久化: 1次写入 (仅真实退出)

总计: 1读 + 1写 = 2次操作
减少: 60%的数据库操作！
```

### 2. 异步处理

#### 持久化异步化
```java
// 主线程
persistQueue.offer(uid); // O(1)，立即返回

// 后台线程
while (true) {
    Long uid = persistQueue.poll(1, TimeUnit.SECONDS);
    if (uid != null) {
        persistPlayerDataSync(uid); // 可能耗时100ms
    }
}
```

#### 数据库操作异步化
```java
// 所有数据库操作返回CompletableFuture
CompletableFuture<String> get(String key);
CompletableFuture<Boolean> set(String key, String value, int expireSeconds);

// 使用示例
db.get("player:123:coins").thenAccept(coins -> {
    player.sendMessage("你有 " + coins + " 金币");
});
```

### 3. 缓存策略

#### 多层缓存
```
1. Nukkit内存缓存 (Guava Cache)
   ├─ 过期时间: 60秒
   └─ 最大条目: 10000

2. Redis缓存 (集群共享)
   ├─ 过期时间: 1小时
   └─ 容量: 无限

3. MySQL持久化 (最终数据)
   ├─ 持久化
   └─ 事务支持
```

#### 智能API
```java
// smartGet: 自动多层查询
public CompletableFuture<Map<String, Object>> smartGet(String tableName, String key) {
    // 1. 检查Redis
    return redisManager.hgetAll(cacheKey).thenCompose(cacheData -> {
        if (!cacheData.isEmpty()) {
            return CompletableFuture.completedFuture(cacheData); // 缓存命中
        }

        // 2. 缓存未命中，查询MySQL
        return mysqlManager.selectFromTable(tableName, key).thenApply(dbData -> {
            // 3. 写入Redis缓存
            if (!dbData.isEmpty()) {
                redisManager.hmset(cacheKey, dbData, expireSeconds);
            }
            return dbData;
        });
    });
}
```

---

## 🛡️ 容错设计

### 1. 网络分区处理

#### 场景：WaterdogPE与Nukkit网络中断
```
问题: Nukkit收不到REAL_QUIT消息，导致数据无法持久化

解决: 超时检测机制
```

```java
// 守护线程每30秒检查一次
guardExecutor.scheduleAtFixedRate(() -> {
    long now = System.currentTimeMillis();
    for (Player player : getServer().getOnlinePlayers().values()) {
        long uid = getPlayerUid(player);
        Long joinTime = realOnlinePlayers.getIfPresent(uid);

        // 如果玩家在线超过5分钟但没有真实加入标记
        if (joinTime == null && (now - player.getLoginTime() > 5 * 60 * 1000)) {
            getLogger().warning("检测到异常会话: " + player.getName());
            persistQueue.offer(uid); // 强制持久化
        }
    }
}, 30, 30, TimeUnit.SECONDS);
```

### 2. WaterdogPE崩溃处理

#### 场景：代理服务器突然崩溃
```
问题: 所有玩家未收到REAL_QUIT消息

解决: Nukkit插件关闭时处理所有在线玩家
```

```java
@Override
public void onDisable() {
    // 处理剩余的持久化任务
    getLogger().info("处理剩余的持久化任务... (队列大小: " + persistQueue.size() + ")");
    while (!persistQueue.isEmpty()) {
        Long uid = persistQueue.poll();
        if (uid != null) {
            persistPlayerDataSync(uid);
        }
    }
}
```

### 3. 数据库连接失败

#### Redis连接失败降级
```java
@Override
public CompletableFuture<String> get(String key) {
    if (!redisManager.isConnected()) {
        getLogger().warning("Redis未连接，降级到MySQL");
        return mysqlManager.selectValue(key); // 降级
    }
    return redisManager.get(key);
}
```

#### MySQL连接失败重试
```java
private void initializeDatabase() {
    int retryCount = 0;
    while (retryCount < 3) {
        try {
            mysqlManager.initialize();
            getLogger().info("MySQL连接成功");
            break;
        } catch (Exception e) {
            retryCount++;
            getLogger().warning("MySQL连接失败，重试 " + retryCount + "/3");
            Thread.sleep(5000);
        }
    }
}
```

---

## 📊 监控与调试

### 1. 日志级别

| 级别 | 使用场景 | 示例 |
|------|----------|------|
| DEBUG | 消息收发、缓存操作 | "收到心跳: WaterdogPE-Proxy" |
| INFO | 玩家加入/退出、持久化 | "玩家真实加入: Steve" |
| WARNING | 异常会话、超时 | "检测到异常会话: Steve" |
| ERROR | 数据库错误、编解码失败 | "消息编码失败" |

### 2. 监控指标

#### 关键指标
```java
// 1. 会话缓存大小
int cacheSize = realOnlinePlayers.size();

// 2. 持久化队列长度
int queueSize = persistQueue.size();

// 3. 数据库连接状态
boolean redisOk = databaseManager.isRedisConnected();
boolean mysqlOk = databaseManager.isMySQLConnected();
```

#### 命令查看
```bash
/yrdb status     # 连接状态
/yrdb sessions   # 会话缓存
/yrdb queue      # 持久化队列
```

### 3. 调试模式

启用调试日志：
```yaml
# WaterdogPE: waterdog.yml
logging:
  level: DEBUG

# Nukkit: 修改YRDatabase.java
private boolean isDebugMode() {
    return true; // 开启调试
}
```

---

## 🔮 未来扩展

### 1. Redis Pub/Sub方案（备选）

当前方案的缺点：
- 依赖Plugin Messaging（需要WaterdogPE和Nukkit支持）
- 消息仅在启动后的子服生效（动态扩容困难）

Redis Pub/Sub优势：
- 完全解耦
- 支持动态扩容
- 可跨多个WaterdogPE实例

实现示例：
```java
// WaterdogPE发布
redisManager.publish("yrdatabase:player:join", jsonData);

// Nukkit订阅
redisManager.subscribe("yrdatabase:player:join", (channel, message) -> {
    handleRealJoin(message);
});
```

### 2. 分布式锁（防并发写入）

集成Redisson：
```java
RLock lock = redisson.getLock("player:" + uid);
try {
    if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
        persistData(uid);
    }
} finally {
    lock.unlock();
}
```

### 3. 监控集成（Prometheus + Grafana）

暴露指标：
```java
// 自定义Metrics
Gauge.build()
    .name("yrdatabase_cache_size")
    .help("玩家会话缓存大小")
    .register()
    .set(realOnlinePlayers.size());

Counter.build()
    .name("yrdatabase_persist_total")
    .help("持久化操作总数")
    .register()
    .inc();
```

---

## 📚 参考资料

- [WaterdogPE文档](https://docs.waterdog.dev/)
- [Nukkit Wiki](https://wiki.cloudburst.mc/)
- [Redis命令参考](https://redis.io/commands/)
- [HikariCP配置](https://github.com/brettwooldridge/HikariCP)
- [Lettuce文档](https://lettuce.io/core/release/reference/)

---

**设计原则**：简单、可靠、高性能

**核心优化**：减少转服时的数据库操作

**安全保障**：多重校验、超时保护、容错降级
