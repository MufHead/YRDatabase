# Changelog

## [Unreleased]

### Bug Fixes
- **Write-back 缺失 pending 注册**：`get()` 在 Redis miss → MySQL hit 时写回 Redis 的同时，现在会自动将 key 注册到 `yrdatabase:pending` 排序集，使 sweep 能对这类 key 执行 TTL 续期。

---

## [2.0.0] - 2025

### New Features

#### 可配置扫描间隔（`sweepIntervalSeconds`）
- `caching.sweepIntervalSeconds` 支持配置化，不再硬编码为 30 秒。
- 启动时校验三条约束，违反时打印 WARN 日志：
  - `sweepIntervalSeconds ≤ refreshThreshold`
  - `sweepIntervalSeconds ≤ autoSyncIntervalSeconds`
  - `defaultTTL > sweepIntervalSeconds`
- 配置文件注释详细说明了各约束的含义。

#### 在线感知 TTL 续期（`setOnlineChecker`）
- `DatabaseManager` 新增 `setOnlineChecker(Predicate<String> checker)` 接口方法。
- 平台层（Allay / Nukkit）注入在线判断逻辑，sweep 只对**在线玩家**续期 TTL。
- Allay 实现：从 cacheKey 末段提取 playerId，查询 `PlayerEventListener.isOnline()`。

#### 双扫描 Sweep 机制（Refresh + Persist）
- 原固定缓冲扫描（`SWEEP_BUFFER_SECONDS=60`）替换为两条独立扫描：
  - **Refresh 扫描**：`TTL ≤ refreshThreshold` 且玩家在线 → `expire(defaultTTL)` + 更新 pending 分值。
  - **Persist 扫描**：`TTL ≤ autoSyncIntervalSeconds` 且玩家离线 → 持久化到 MySQL/SQLite + `zrem`。
- 新增 `sweepRefreshKey()`：续期失败（key 已过期）时自动回退到持久化流程。

#### 主动 TTL 续期（`get()` 命中时）
- `get()` 缓存命中后，若 `autoRefresh=true` 且剩余 TTL `< refreshThreshold`，自动调用 `expire()` 并更新 pending 分值，无需等待 sweep。

### Bug Fixes

#### HikariCP 连接验证警告
- MySQL Provider 新增 `hikariConfig.setKeepaliveTime(60000)`，每 60 秒 ping 一次空闲连接，防止 MySQL 在 `maxLifetime` 前关闭连接导致 HikariCP 报 "Failed to validate connection"。

#### `saveToPersist` 误删 pending 条目
- **根因**：autoSync 路径（`persistOnly → saveToPersist`）调用了 `zrem`，导致 key 从 pending 中移除，后续 sweep 的 refresh 扫描找不到该 key。
- **修复**：`saveToPersist` 不再调用 `zrem`。只有 `persistAndClear`、`processPendingKey`、`processPendingKeySync` 在明确完成数据生命周期时才执行 `zrem`。

#### `SetArgs.Builder` 内部类重定位错误
- `setNxEx` 改用 `new SetArgs().nx().ex(ttl)` 直接实例化，避免 Shadow JAR 重定位不更新 `InnerClasses` 属性导致的 `NoClassDefFoundError`。

#### `persistOnly` 空 key 处理
- Redis key 不存在时，`persistOnly` 现在会主动 `zrem` 移除 pending 中的残留条目，避免 sweep 反复处理已失效的 key。

### Removed
- `syncAllPending()` 方法及其定时任务已移除。相关功能完全由双扫描 sweep 接管，逻辑更清晰、无竞态。
- 移除 `SYNC_LOCK_KEY` 常量。

### Configuration Changes

`config.yml` 新增字段（`caching` 节）：

```yaml
caching:
  # pending 集合扫描间隔（秒）
  # 必须满足：
  #   sweepIntervalSeconds ≤ refreshThreshold
  #   sweepIntervalSeconds ≤ autoSyncIntervalSeconds
  #   defaultTTL > sweepIntervalSeconds
  # 推荐值：min(refreshThreshold, autoSyncIntervalSeconds) 的一半
  sweepIntervalSeconds: 30
```

---

## [1.x] - Earlier

初始版本，基础 Redis + MySQL/SQLite 双层存储，CACHE_FIRST 策略，pending 排序集持久化机制。
