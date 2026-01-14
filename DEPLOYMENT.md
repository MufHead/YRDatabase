# YRDatabase 部署与测试指南

本文档提供详细的部署步骤和测试方法。

---

## 📋 部署前检查清单

### 硬件要求

| 服务器类型 | CPU | 内存 | 磁盘 | 网络 |
|-----------|-----|------|------|------|
| WaterdogPE | 2核+ | 2GB+ | 10GB+ | 低延迟 |
| Nukkit子服 | 4核+ | 4GB+ | 20GB+ | 低延迟 |
| Redis | 2核+ | 1GB+ | 5GB+ | - |
| MySQL | 4核+ | 8GB+ | 50GB+ | - |

### 软件要求

```bash
# 1. Java 17+
java -version
# 应输出: openjdk version "17.0.x" 或更高

# 2. Redis 5.0+ (可选)
redis-cli --version
# 应输出: redis-cli 5.x.x 或更高

# 3. MySQL 8.0+ (可选)
mysql --version
# 应输出: mysql Ver 8.0.x 或更高
```

---

## 🔨 第一步：编译项目

### 方式1：使用Gradle Wrapper (推荐)

```bash
# Windows
cd E:\ServerPLUGINS\YRDatabase
gradlew.bat clean shadowJar

# Linux/Mac
cd /path/to/YRDatabase
./gradlew clean shadowJar
```

### 方式2：使用本地Gradle

```bash
gradle clean shadowJar
```

### 编译产物位置

```
YRDatabase/
├── yrdatabase-waterdog/build/libs/
│   └── YRDatabase-Waterdog.jar  ← WaterdogPE插件
│
└── yrdatabase-nukkit/build/libs/
    └── YRDatabase.jar           ← Nukkit插件
```

---

## 🚀 第二步：部署WaterdogPE插件

### 2.1 上传插件

```bash
# 将插件复制到WaterdogPE的plugins目录
scp YRDatabase-Waterdog.jar root@waterdog-server:/path/to/WaterdogPE/plugins/

# 或者直接复制（本地）
cp yrdatabase-waterdog/build/libs/YRDatabase-Waterdog.jar \
   /path/to/WaterdogPE/plugins/
```

### 2.2 启动WaterdogPE

```bash
cd /path/to/WaterdogPE
java -Xms2G -Xmx2G -jar WaterdogPE.jar
```

### 2.3 验证加载成功

查看日志中是否有以下输出：

```log
[INFO] Loading YRDatabase-Waterdog v1.0.0
[INFO] YRDatabase-Waterdog 正在启动...
[INFO] 事件监听器已注册
[INFO] 心跳任务已启动 (间隔: 10秒)
[INFO] YRDatabase-Waterdog 已成功启动!
[INFO] 当前在线玩家数: 0
```

---

## 🎮 第三步：部署Nukkit插件

### 3.1 上传插件到所有子服

```bash
# 假设有3个子服: lobby, survival, creative
for server in lobby survival creative; do
    scp YRDatabase.jar root@nukkit-${server}:/path/to/Nukkit/plugins/
done
```

### 3.2 配置数据库

在每个子服的 `plugins/YRDatabase/config.json` 中配置：

```json
{
  "UseNeteaseUid": false,
  "redis": {
    "enabled": true,
    "host": "your-redis-host.com",
    "port": 6379,
    "password": "your_redis_password",
    "database": 0,
    "timeout": 5000,
    "maxConnections": 20
  },
  "mysql": {
    "enabled": true,
    "host": "your-mysql-host.com",
    "port": 3306,
    "database": "yrdatabase",
    "username": "yrdatabase_user",
    "password": "your_mysql_password",
    "timezone": "Asia/Shanghai",
    "maxPoolSize": 10,
    "minIdle": 2,
    "connectionTimeout": 30000,
    "idleTimeout": 600000,
    "maxLifetime": 1800000
  }
}
```

### 3.3 创建MySQL数据库

```sql
-- 1. 创建数据库
CREATE DATABASE yrdatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2. 创建用户并授权
CREATE USER 'yrdatabase_user'@'%' IDENTIFIED BY 'your_mysql_password';
GRANT ALL PRIVILEGES ON yrdatabase.* TO 'yrdatabase_user'@'%';
FLUSH PRIVILEGES;

-- 3. 验证连接
mysql -h your-mysql-host.com -u yrdatabase_user -p yrdatabase
```

### 3.4 启动Nukkit子服

```bash
# 每个子服分别启动
cd /path/to/Nukkit-Lobby
java -Xms4G -Xmx4G -jar nukkit.jar

cd /path/to/Nukkit-Survival
java -Xms4G -Xmx4G -jar nukkit.jar

cd /path/to/Nukkit-Creative
java -Xms4G -Xmx4G -jar nukkit.jar
```

### 3.5 验证加载成功

查看每个子服的日志：

```log
[INFO] Loading YRDatabase v1.0-SNAPSHOT
[INFO] YRDatabase 插件正在启用...
[INFO] 前置插件NukkitMaster 插件已找到!
[INFO] 配置文件加载成功
[INFO] 玩家会话缓存已初始化 (过期时间: 60秒)
[INFO] Plugin Messaging 已注册: yrdatabase:main
[INFO] 持久化工作线程已启动 (线程数: 2)
[INFO] 守护线程已启动 (检查间隔: 30秒)
[INFO] Redis 连接: 正常
[INFO] MySQL 连接: 正常
[INFO] YRDatabase 插件已成功启用！
[INFO] 等待来自WaterdogPE的玩家会话消息...
```

---

## 🧪 第四步：功能测试

### 测试1：真实加入检测

#### 测试步骤

1. 使用Minecraft客户端连接到WaterdogPE代理
2. 观察WaterdogPE日志：

```log
[INFO] 玩家真实加入: TestPlayer (UID: 123456789)
[INFO] 已广播REAL_JOIN消息到 3 个子服
```

3. 观察Nukkit子服日志：

```log
[INFO] 收到REAL_JOIN消息: TestPlayer (UID: 123456789)
```

4. 玩家进入子服（如lobby），观察日志：

```log
[INFO] 玩家真实加入: TestPlayer (UID: 123456789) - 初始化数据
[INFO] 玩家数据已初始化: UID=123456789
```

#### 预期结果
✅ WaterdogPE成功发送REAL_JOIN消息
✅ Nukkit成功接收并缓存UID
✅ 玩家加入时触发数据初始化

---

### 测试2：转服检测（不触发持久化）

#### 测试步骤

1. 玩家在子服之间转服（lobby → survival）
2. 观察Nukkit日志（**不应出现**持久化消息）：

```log
# Lobby服务器日志
[INFO] 玩家退出: TestPlayer (UID: 123456789) - 等待REAL_QUIT信号

# Survival服务器日志
[INFO] 玩家转服加入: TestPlayer (UID: 123456789) - 跳过初始化
```

#### 预期结果
✅ 转服时**不触发**数据持久化
✅ 转服时**不触发**数据初始化
✅ 数据库查询次数为0

---

### 测试3：真实退出持久化

#### 测试步骤

1. 玩家断开连接（关闭客户端或网络断开）
2. 观察WaterdogPE日志：

```log
[INFO] 玩家真实退出: TestPlayer (UID: 123456789)
[INFO] 已发送REAL_QUIT消息到服务器: survival
```

3. 观察Nukkit子服日志：

```log
[INFO] 收到REAL_QUIT消息: TestPlayer (UID: 123456789, 最后服务器: survival)
[INFO] 玩家数据已持久化: UID=123456789
```

4. 验证MySQL数据库：

```sql
-- 查询玩家数据是否存在
SELECT * FROM yr_key_value WHERE key_name LIKE '%123456789%';
```

#### 预期结果
✅ WaterdogPE成功发送REAL_QUIT消息
✅ Nukkit成功接收并触发持久化
✅ 数据成功写入MySQL

---

### 测试4：心跳机制

#### 测试步骤

等待10秒，观察Nukkit日志（debug模式）：

```log
[DEBUG] 收到心跳: WaterdogPE-Proxy (玩家数: 1)
```

使用命令检查状态：

```bash
/yrdb status
```

应显示：

```
=== YRDatabase 状态 ===
Redis: 已连接
MySQL: 已连接
在线会话: 1
持久化队列: 0
```

#### 预期结果
✅ 心跳包每10秒发送一次
✅ 状态命令正常显示

---

### 测试5：会话缓存查询

#### 测试步骤

使用命令：

```bash
/yrdb sessions
```

应显示：

```
=== 当前会话缓存 ===
缓存大小: 1
UID: 123456789 (加入 15秒前)
```

#### 预期结果
✅ 显示所有真实在线的玩家UID
✅ 显示加入时间

---

### 测试6：异常场景 - WaterdogPE崩溃

#### 测试步骤

1. 玩家连接并进入子服
2. 强制关闭WaterdogPE进程（模拟崩溃）
3. 等待5分钟
4. 观察Nukkit守护线程日志：

```log
[WARNING] 检测到异常会话: TestPlayer (UID: 123456789) - 可能未收到REAL_QUIT消息
[WARNING] 检测到 1 个异常会话
[INFO] 玩家数据已持久化: UID=123456789
```

#### 预期结果
✅ 超时检测机制生效
✅ 自动触发持久化
✅ 避免数据丢失

---

## 📊 性能测试

### 测试场景：100玩家同时在线

#### 测试工具

使用 [JMeter](https://jmeter.apache.org/) 或自定义机器人进行压力测试。

#### 指标监控

```bash
# 1. Redis监控
redis-cli INFO stats | grep total_commands_processed

# 2. MySQL监控
mysql -e "SHOW STATUS LIKE 'Threads_connected';"

# 3. JVM监控
jstat -gcutil <pid> 1000

# 4. 持久化队列
/yrdb queue
```

#### 预期性能

| 指标 | 目标值 |
|------|--------|
| 消息延迟 | < 10ms |
| 持久化延迟 | < 100ms |
| Redis TPS | 10000+ |
| MySQL TPS | 1000+ |
| 内存使用 | < 2GB |
| CPU使用 | < 50% |

---

## 🐛 常见问题诊断

### 问题诊断流程图

```
玩家加入时数据未初始化？
    │
    ├─→ 检查WaterdogPE日志
    │   是否发送REAL_JOIN？
    │       ├─→ 是 → 检查Nukkit日志
    │       │        是否收到消息？
    │       │            ├─→ 是 → 检查缓存
    │       │            │        /yrdb sessions
    │       │            │        缓存是否存在？
    │       │            │            ├─→ 是 → 检查PlayerJoinEvent
    │       │            │            │        是否触发？
    │       │            │            └─→ 否 → 缓存过期
    │       │            │                     (调整过期时间)
    │       │            └─→ 否 → Plugin Messaging未注册
    │       │                     (检查plugin.yml)
    │       └─→ 否 → WaterdogPE插件未加载
    │                (检查plugins目录)
    └─→ 解决方案：查看对应章节
```

### 日志级别调整

```yaml
# WaterdogPE: waterdog.yml
logging:
  level: DEBUG

# Nukkit: server.properties
debug.level=2
```

---

## 🔄 更新与回滚

### 更新步骤

```bash
# 1. 备份当前版本
cp YRDatabase.jar YRDatabase.jar.backup
cp YRDatabase-Waterdog.jar YRDatabase-Waterdog.jar.backup

# 2. 编译新版本
./gradlew clean shadowJar

# 3. 热更新（可选，需支持）
# 或使用 /yrdb reload

# 4. 重启服务器
# 先重启WaterdogPE，再重启所有Nukkit子服
```

### 回滚步骤

```bash
# 1. 恢复旧版本
mv YRDatabase.jar.backup YRDatabase.jar
mv YRDatabase-Waterdog.jar.backup YRDatabase-Waterdog.jar

# 2. 重启服务器
```

---

## 📞 技术支持

遇到问题？

1. 查看 [README.md](README.md) 的故障排查章节
2. 提交Issue: https://github.com/yirankuma/YRDatabase/issues
3. 加入Discord/QQ群（如有）

---

**测试完成后，请记录测试结果并提交报告！**
