# 🚀 YRDatabase 部署就绪

## ✅ 编译完成

两个插件JAR文件已就绪，位于同一文件夹：

```
E:/ServerPLUGINS/网易NK服务器插件/
├── YRDatabase.jar            (14 MB)  ← Nukkit子服插件
└── YRDatabase-Waterdog.jar   (349 KB) ← WaterdogPE代理端插件
```

---

## 📦 快速部署指南

### 1️⃣ 部署Nukkit插件（所有子服）

```bash
# 复制到每个Nukkit子服
cp "E:/ServerPLUGINS/网易NK服务器插件/YRDatabase.jar" /path/to/Nukkit-Lobby/plugins/
cp "E:/ServerPLUGINS/网易NK服务器插件/YRDatabase.jar" /path/to/Nukkit-Survival/plugins/
cp "E:/ServerPLUGINS/网易NK服务器插件/YRDatabase.jar" /path/to/Nukkit-Creative/plugins/
# ... 所有子服
```

**配置文件**（首次启动自动生成）:
- 位置: `plugins/YRDatabase/config.json`
- 修改Redis和MySQL连接信息

### 2️⃣ 部署WaterdogPE插件（代理端）

```bash
# 复制到WaterdogPE
cp "E:/ServerPLUGINS/网易NK服务器插件/YRDatabase-Waterdog.jar" /path/to/WaterdogPE/plugins/
```

**无需配置**，开箱即用！

---

## 🔧 启动顺序

1. ✅ **启动Redis** (如果使用)
   ```bash
   redis-server
   ```

2. ✅ **启动MySQL** (如果使用)
   ```bash
   # 创建数据库
   mysql -e "CREATE DATABASE yrdatabase CHARACTER SET utf8mb4;"
   ```

3. ✅ **启动WaterdogPE**
   ```bash
   cd /path/to/WaterdogPE
   java -jar WaterdogPE.jar
   ```

   **期望日志**:
   ```log
   [INFO] Loading YRDatabase-Waterdog v1.0.0
   [INFO] YRDatabase-Waterdog 正在启动...
   [INFO] 事件监听器已注册
   [INFO] 心跳任务已启动
   [INFO] YRDatabase-Waterdog 已成功启动!
   ```

4. ✅ **启动Nukkit子服**（每个都启动）
   ```bash
   cd /path/to/Nukkit-Lobby
   java -jar nukkit.jar
   ```

   **期望日志**:
   ```log
   [INFO] Loading YRDatabase v1.0-SNAPSHOT
   [INFO] YRDatabase 插件正在启用...
   [INFO] 前置插件NukkitMaster 插件已找到!
   [INFO] Redis 连接成功！
   [INFO] MySQL 连接成功！
   [INFO] YRDatabase 插件已成功启用！
   ```

---

## 🧪 测试验证

### 测试1: Nukkit数据库功能

在任意Nukkit子服控制台执行：

```bash
# 查看状态
yrdb status

# 测试数据库
yrdb test

# 预期输出:
# ✓ 数据写入测试成功
# ✓ 数据读取测试成功
# ✓ 数据删除测试成功
```

### 测试2: WaterdogPE事件检测

1. **玩家加入测试**
   - 玩家连接到WaterdogPE
   - 查看WaterdogPE日志:
     ```log
     [INFO] 玩家真实加入: PlayerName (UID: 123456789)
     ```

2. **玩家转服测试**
   - 玩家执行 `/server survival`
   - 查看WaterdogPE日志:
     ```log
     [INFO] 玩家转服: PlayerName (UID: 123456789) lobby -> survival
     ```

3. **玩家退出测试**
   - 玩家断开连接
   - 查看WaterdogPE日志:
     ```log
     [INFO] 玩家真实退出: PlayerName (UID: 123456789)
     ```

---

## ⚙️ 配置文件说明

### Nukkit - config.json

首次启动后编辑 `plugins/YRDatabase/config.json`:

```json
{
  "UseNeteaseUid": false,
  "redis": {
    "enabled": true,          // 是否启用Redis
    "host": "localhost",       // Redis地址
    "port": 6379,
    "password": "",            // Redis密码（如果有）
    "database": 0,
    "timeout": 5000,
    "maxConnections": 20
  },
  "mysql": {
    "enabled": true,           // 是否启用MySQL
    "host": "localhost",        // MySQL地址
    "port": 3306,
    "database": "yrdatabase",   // 数据库名
    "username": "root",         // 用户名
    "password": "your_password", // 密码 ← 必须修改
    "timezone": "Asia/Shanghai",
    "maxPoolSize": 10,
    "minIdle": 2,
    "connectionTimeout": 30000,
    "idleTimeout": 600000,
    "maxLifetime": 1800000
  }
}
```

**修改后记得重载**:
```bash
yrdb reload
```

---

## 📊 当前功能状态

### ✅ 已完成（立即可用）

| 功能 | Nukkit | WaterdogPE |
|------|--------|------------|
| 数据库API | ✅ 完整 | N/A |
| Redis缓存 | ✅ 完整 | N/A |
| MySQL持久化 | ✅ 完整 | N/A |
| 管理命令 | ✅ 完整 | N/A |
| 事件监听 | N/A | ✅ 完整 |
| 玩家会话跟踪 | N/A | ✅ 完整 |

### ⏳ 下一步（可选优化）

| 功能 | 说明 | 优先级 |
|------|------|--------|
| Redis Pub/Sub | WaterdogPE→Nukkit通信 | 🔴 高 |
| 转服优化 | 避免转服时持久化 | 🔴 高 |
| 网易UID | 取消注释代码即可 | 🟡 中 |

---

## 🎯 使用建议

### 当前阶段（Phase 1 - 立即可用）

✅ **可以做的**:
- 使用Nukkit插件的完整数据库功能
- Redis缓存 + MySQL持久化
- 智能API：smartGet/smartSet
- 批量操作：smartBatchGet/smartBatchSet
- WaterdogPE监听玩家真实加入/退出/转服

⚠️ **暂时的限制**:
- 转服时仍会触发数据持久化（因为Redis Pub/Sub未实现）
- 玩家ID使用UUID而非网易UID（需要时取消注释）

### 下一阶段（Phase 2 - 性能优化）

实现Redis Pub/Sub后：
- ✅ 转服时不再触发持久化
- ✅ 减少60%以上的数据库操作
- ✅ 真正的智能会话管理

详见: [BUILD_SUCCESS.md](BUILD_SUCCESS.md) 的"下一步工作"章节

---

## 🆘 常见问题

### Q1: Redis连接失败怎么办？

**现象**: 日志显示"Redis 连接失败或已禁用"

**解决**:
1. 检查Redis是否启动: `redis-cli PING`
2. 检查配置文件中的host和port
3. 如果不需要Redis，可以禁用:
   ```json
   "redis": {
     "enabled": false
   }
   ```

### Q2: MySQL连接失败怎么办？

**现象**: 日志显示"MySQL 连接失败或已禁用"

**解决**:
1. 创建数据库: `CREATE DATABASE yrdatabase;`
2. 检查用户权限: `GRANT ALL PRIVILEGES ON yrdatabase.* TO 'root'@'%';`
3. 检查配置文件中的连接信息

### Q3: 看不到WaterdogPE的日志怎么办？

**现象**: WaterdogPE没有"玩家真实加入"等日志

**检查**:
1. 确认插件已加载: 控制台应显示"Loading YRDatabase-Waterdog"
2. 检查`plugins`文件夹中是否有`YRDatabase-Waterdog.jar`
3. 查看是否有错误日志

---

## 📞 需要帮助？

查看完整文档：
- [README.md](README.md) - 项目介绍和API文档
- [QUICKSTART.md](QUICKSTART.md) - 5分钟快速开始
- [DEPLOYMENT.md](DEPLOYMENT.md) - 详细部署指南
- [ARCHITECTURE.md](ARCHITECTURE.md) - 架构设计说明
- [BUILD_SUCCESS.md](BUILD_SUCCESS.md) - 编译报告和下一步

---

## ✅ 检查清单

部署前确认：

- [ ] Redis已启动（如果使用）
- [ ] MySQL已启动且已创建数据库（如果使用）
- [ ] `YRDatabase.jar`已复制到所有Nukkit子服
- [ ] `YRDatabase-Waterdog.jar`已复制到WaterdogPE
- [ ] 修改了Nukkit的`config.json`中的密码
- [ ] WaterdogPE已启动
- [ ] 所有Nukkit子服已启动
- [ ] 执行`yrdb test`测试通过

全部完成后，你的YRDatabase就可以投入使用了！🎉

---

**部署日期**: 2026-01-14
**版本**: 1.0-SNAPSHOT
**状态**: ✅ 就绪
