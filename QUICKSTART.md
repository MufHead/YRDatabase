# YRDatabase 快速开始指南

5分钟快速部署YRDatabase！

---

## ⚡ 一键编译

```bash
# Windows
cd E:\ServerPLUGINS\YRDatabase
gradlew.bat clean shadowJar

# Linux/Mac
cd /path/to/YRDatabase
./gradlew clean shadowJar
```

**编译成功后**：
- `yrdatabase-waterdog/build/libs/YRDatabase-Waterdog.jar`
- `yrdatabase-nukkit/build/libs/YRDatabase.jar`

---

## 📂 部署位置

### WaterdogPE (1个文件)
```
WaterdogPE/
└── plugins/
    └── YRDatabase-Waterdog.jar  ← 复制到这里
```

### Nukkit子服 (每个子服1个文件)
```
Nukkit-Server1/
└── plugins/
    └── YRDatabase.jar  ← 复制到这里

Nukkit-Server2/
└── plugins/
    └── YRDatabase.jar  ← 复制到这里

Nukkit-Server3/
└── plugins/
    └── YRDatabase.jar  ← 复制到这里
```

---

## ⚙️ 最小配置

### config.json (仅Nukkit需要)

```json
{
  "UseNeteaseUid": false,
  "redis": {
    "enabled": true,
    "host": "localhost",
    "port": 6379,
    "password": "",
    "database": 0,
    "timeout": 5000,
    "maxConnections": 20
  },
  "mysql": {
    "enabled": true,
    "host": "localhost",
    "port": 3306,
    "database": "yrdatabase",
    "username": "root",
    "password": "your_password",
    "timezone": "Asia/Shanghai",
    "maxPoolSize": 10,
    "minIdle": 2,
    "connectionTimeout": 30000,
    "idleTimeout": 600000,
    "maxLifetime": 1800000
  }
}
```

**修改项**：
- `redis.host` → 你的Redis服务器地址
- `redis.password` → Redis密码（如果有）
- `mysql.host` → 你的MySQL服务器地址
- `mysql.password` → MySQL密码

---

## 🗄️ 数据库准备

### MySQL

```sql
-- 1. 创建数据库
CREATE DATABASE yrdatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2. 创建用户
CREATE USER 'yrdatabase_user'@'%' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON yrdatabase.* TO 'yrdatabase_user'@'%';
FLUSH PRIVILEGES;
```

### Redis

```bash
# 1. 启动Redis
redis-server

# 2. 测试连接
redis-cli PING
# 应返回: PONG
```

---

## 🚀 启动顺序

### 1. 启动Redis和MySQL

```bash
# Redis
redis-server /path/to/redis.conf

# MySQL
systemctl start mysql
```

### 2. 启动WaterdogPE

```bash
cd /path/to/WaterdogPE
java -Xms2G -Xmx2G -jar WaterdogPE.jar
```

**检查日志**：
```log
✅ [INFO] YRDatabase-Waterdog 已成功启动!
```

### 3. 启动所有Nukkit子服

```bash
# 依次启动每个子服
cd /path/to/Nukkit-Server1
java -Xms4G -Xmx4G -jar nukkit.jar
```

**检查日志**（每个子服）：
```log
✅ [INFO] Plugin Messaging 已注册: yrdatabase:main
✅ [INFO] Redis 连接: 正常
✅ [INFO] MySQL 连接: 正常
✅ [INFO] YRDatabase 插件已成功启用！
```

---

## ✅ 验证部署

### 测试1：玩家真实加入

1. 使用客户端连接到WaterdogPE
2. 查看WaterdogPE日志：

```log
✅ [INFO] 玩家真实加入: YourName (UID: 123456789)
✅ [INFO] 已广播REAL_JOIN消息到 3 个子服
```

3. 查看Nukkit日志：

```log
✅ [INFO] 收到REAL_JOIN消息: YourName (UID: 123456789)
✅ [INFO] 玩家真实加入: YourName (UID: 123456789) - 初始化数据
```

### 测试2：转服（不持久化）

1. 使用命令转服：`/server survival`
2. 查看Nukkit日志：

```log
# 旧服务器
[INFO] 玩家退出: YourName (UID: 123456789) - 等待REAL_QUIT信号

# 新服务器
✅ [INFO] 玩家转服加入: YourName (UID: 123456789) - 跳过初始化
```

**注意**：不应出现"持久化"相关日志！

### 测试3：真实退出

1. 关闭客户端
2. 查看WaterdogPE日志：

```log
✅ [INFO] 玩家真实退出: YourName (UID: 123456789)
```

3. 查看Nukkit日志：

```log
✅ [INFO] 收到REAL_QUIT消息: YourName (UID: 123456789)
✅ [INFO] 玩家数据已持久化: UID=123456789
```

---

## 🎮 常用命令

### Nukkit子服命令

```bash
# 查看状态
/yrdb status

# 查看会话缓存
/yrdb sessions

# 查看持久化队列
/yrdb queue

# 重载配置
/yrdb reload
```

---

## 🐛 常见问题

### 问题1：Nukkit收不到消息

**症状**：所有玩家加入都显示"转服加入"

**解决**：
1. 检查WaterdogPE插件是否加载
2. 使用 `/yrdb sessions` 查看缓存是否为空
3. 重启WaterdogPE和所有Nukkit子服

### 问题2：Redis连接失败

**症状**：日志显示"Redis 连接: 未连接"

**解决**：
```bash
# 1. 检查Redis是否启动
redis-cli PING

# 2. 检查防火墙
firewall-cmd --add-port=6379/tcp --permanent
firewall-cmd --reload

# 3. 修改config.json中的Redis地址
```

### 问题3：MySQL连接失败

**症状**：日志显示"MySQL 连接: 未连接"

**解决**：
```bash
# 1. 测试连接
mysql -h your-host -u yrdatabase_user -p yrdatabase

# 2. 检查用户权限
SHOW GRANTS FOR 'yrdatabase_user'@'%';

# 3. 检查防火墙
firewall-cmd --add-port=3306/tcp --permanent
```

---

## 📚 下一步

- 阅读 [README.md](README.md) 了解完整功能
- 查看 [DEPLOYMENT.md](DEPLOYMENT.md) 进行生产部署
- 参考 [ARCHITECTURE.md](ARCHITECTURE.md) 理解架构设计

---

## 🆘 获取帮助

- GitHub Issues: https://github.com/yirankuma/YRDatabase/issues
- 查看日志文件定位问题
- 启用DEBUG模式获取详细日志

---

**恭喜！YRDatabase已成功部署！🎉**
