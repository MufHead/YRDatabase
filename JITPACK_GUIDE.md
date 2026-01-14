# 🎉 YRDatabase 已发布到 JitPack！

## ✅ 完全公开，无需身份验证！

任何人都可以直接使用，不需要GitHub Token或任何验证！

---

## 📦 发布信息

**JitPack地址**：https://jitpack.io/#MufHead/YRDatabase/v1.0.0

**最新版本**：`v1.0.0`

---

## 🚀 如何使用

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")  // 添加JitPack仓库
}

dependencies {
    // 只使用API（最小依赖）
    compileOnly("com.github.MufHead.YRDatabase:yrdatabase-common:v1.0.0")

    // 或者依赖Nukkit插件（包含完整实现）
    compileOnly("com.github.MufHead.YRDatabase:yrdatabase-nukkit:v1.0.0")

    // 或者依赖WaterdogPE插件
    compileOnly("com.github.MufHead.YRDatabase:yrdatabase-waterdog:v1.0.0")
}
```

### Gradle (Groovy DSL)

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.MufHead.YRDatabase:yrdatabase-common:v1.0.0'
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
        <artifactId>yrdatabase-common</artifactId>
        <version>v1.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

---

## 🎯 三个模块说明

### 1. yrdatabase-common（推荐）

**最小依赖，只包含API接口**

```kotlin
compileOnly("com.github.MufHead.YRDatabase:yrdatabase-common:v1.0.0")
```

**适用场景**：
- ✅ 你只需要使用YRDatabase的API
- ✅ 你的插件会在运行时依赖YRDatabase插件
- ✅ 你不需要Redis/MySQL的实现代码

**大小**：约12 KB

### 2. yrdatabase-nukkit

**完整的Nukkit插件实现**

```kotlin
compileOnly("com.github.MufHead.YRDatabase:yrdatabase-nukkit:v1.0.0")
```

**包含**：
- common模块的所有API
- Redis连接池实现
- MySQL连接池实现
- 所有事件类

**大小**：约14 MB（包含所有依赖）

### 3. yrdatabase-waterdog

**WaterdogPE代理端插件**

```kotlin
compileOnly("com.github.MufHead.YRDatabase:yrdatabase-waterdog:v1.0.0")
```

**包含**：
- Redis Pub/Sub发布功能
- 跨服通信协议

**大小**：约6.4 MB

---

## 📝 完整使用示例

### plugin.yml

```yaml
name: MyPlugin
version: 1.0.0
main: com.example.MyPlugin
depend: [YRDatabase]  # 声明依赖
```

### build.gradle.kts

```kotlin
plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-releases/")
    maven("https://jitpack.io")  // JitPack仓库
}

dependencies {
    // Nukkit核心
    compileOnly("cn.nukkit:nukkit:1.0-SNAPSHOT")

    // YRDatabase API
    compileOnly("com.github.MufHead.YRDatabase:yrdatabase-common:v1.0.0")
}
```

### MyPlugin.java

```java
package com.example;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.plugin.PluginBase;
import com.yirankuma.yrdatabase.YRDatabase;
import com.yirankuma.yrdatabase.api.DatabaseManager;
import com.yirankuma.yrdatabase.event.PlayerDataInitializeEvent;
import com.yirankuma.yrdatabase.event.PlayerDataPersistEvent;

public class MyPlugin extends PluginBase implements Listener {

    private DatabaseManager db;

    @Override
    public void onEnable() {
        // 获取YRDatabase的API
        db = YRDatabase.getDatabaseManager();

        if (db == null) {
            getLogger().error("YRDatabase未安装！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 注册事件监听
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("MyPlugin已启动！");
    }

    // 监听玩家数据初始化事件
    @EventHandler
    public void onPlayerDataInitialize(PlayerDataInitializeEvent event) {
        Player player = event.getPlayer();
        String uid = event.getUid();

        getLogger().info("玩家 " + player.getName() + " 数据初始化");

        // 从数据库加载玩家数据
        db.smartGet("player_data:" + uid)
            .thenAccept(data -> {
                if (data != null) {
                    getLogger().info("加载玩家数据: " + data);
                    // TODO: 将数据加载到内存缓存
                } else {
                    getLogger().info("新玩家，创建初始数据");
                    // TODO: 创建初始数据
                }
            });
    }

    // 监听玩家数据持久化事件
    @EventHandler
    public void onPlayerDataPersist(PlayerDataPersistEvent event) {
        Player player = event.getPlayer();
        String uid = event.getUid();

        // 只在需要持久化时保存（自动排除转服）
        if (event.shouldPersist()) {
            getLogger().info("保存玩家 " + player.getName() + " 的数据");

            // 从内存获取玩家数据并保存到数据库
            String playerData = "{\"level\":10,\"exp\":1000}"; // 示例数据

            db.smartSet("player_data:" + uid, playerData, 3600)
                .thenAccept(success -> {
                    if (success) {
                        getLogger().info("玩家数据保存成功！");
                    } else {
                        getLogger().warning("玩家数据保存失败！");
                    }
                });
        }
    }
}
```

---

## 🔄 发布新版本

### 方法1：Release版本（推荐）

```bash
# 1. 更新版本号
# 编辑 build.gradle.kts: version = "1.0.1"

# 2. 提交更改
git add .
git commit -m "Release version 1.0.1"

# 3. 创建标签
git tag -a v1.0.1 -m "Release version 1.0.1"

# 4. 推送到GitHub
git push origin master
git push origin v1.0.1
```

### 方法2：使用分支（开发版本）

```kotlin
// 使用master分支的最新代码
compileOnly("com.github.MufHead.YRDatabase:yrdatabase-common:master-SNAPSHOT")
```

### 方法3：使用Commit ID（特定版本）

```kotlin
// 使用特定的commit
compileOnly("com.github.MufHead.YRDatabase:yrdatabase-common:7e7f1e2")
```

---

## 🎯 版本选择建议

### 生产环境

```kotlin
// 使用稳定的release版本
compileOnly("com.github.MufHead.YRDatabase:yrdatabase-common:v1.0.0")
```

### 开发测试

```kotlin
// 使用最新的开发版本
compileOnly("com.github.MufHead.YRDatabase:yrdatabase-common:master-SNAPSHOT")

// 记得刷新依赖
// ./gradlew build --refresh-dependencies
```

---

## 📊 查看构建状态

访问 JitPack 查看构建状态：

**主页**：https://jitpack.io/#MufHead/YRDatabase

**特定版本**：https://jitpack.io/#MufHead/YRDatabase/v1.0.0

---

## 🔍 验证依赖

### 方法1：查看依赖树

```bash
./gradlew dependencies --configuration compileClasspath
```

应该能看到：
```
compileClasspath - Compile classpath for source set 'main'.
+--- com.github.MufHead.YRDatabase:yrdatabase-common:v1.0.0
```

### 方法2：测试编译

```bash
./gradlew clean build
```

如果能成功编译，说明依赖正常！

---

## ⚡ JitPack的优势

### 对比GitHub Packages

| 特性 | JitPack | GitHub Packages |
|------|---------|-----------------|
| 身份验证 | ❌ 不需要 | ✅ 需要Token |
| 公开访问 | ✅ 完全公开 | ⚠️ 需要Token |
| 构建方式 | 自动从Git构建 | 手动发布 |
| 配置复杂度 | 极简 | 中等 |
| 费用 | 完全免费 | 免费（有限制） |

### JitPack的特点

- ✅ **零配置**：不需要在项目中配置发布脚本
- ✅ **自动构建**：推送tag后自动构建
- ✅ **多版本支持**：可以使用tag、branch、commit
- ✅ **公开访问**：任何人都能直接下载
- ✅ **永久免费**：对开源项目完全免费
- ✅ **支持子模块**：自动识别多模块项目

---

## 🛠️ 故障排除

### 问题1：JitPack构建失败

**检查**：
1. 访问 https://jitpack.io/#MufHead/YRDatabase/v1.0.0
2. 点击 "Look up" 查看构建日志
3. 确保项目有 `build.gradle.kts` 或 `build.gradle`

### 问题2：依赖解析失败

**解决**：
```bash
# 清理缓存
./gradlew clean build --refresh-dependencies

# 或删除本地缓存
rm -rf ~/.gradle/caches/modules-2/files-2.1/com.github.MufHead.YRDatabase
```

### 问题3：找不到模块

**检查依赖名称**：
```kotlin
// ❌ 错误
compileOnly("com.github.MufHead:YRDatabase:v1.0.0")

// ✅ 正确（注意是 .YRDatabase 不是 :YRDatabase）
compileOnly("com.github.MufHead.YRDatabase:yrdatabase-common:v1.0.0")
```

---

## 📚 相关链接

- **GitHub仓库**：https://github.com/MufHead/YRDatabase
- **JitPack主页**：https://jitpack.io/#MufHead/YRDatabase
- **使用示例**：[USAGE_EXAMPLE.md](USAGE_EXAMPLE.md)
- **事件系统指南**：[EVENT_SYSTEM_GUIDE.md](EVENT_SYSTEM_GUIDE.md)
- **JitPack官方文档**：https://jitpack.io/docs/

---

## 🎉 总结

✅ **已发布到JitPack**：v1.0.0
✅ **完全公开**：不需要任何身份验证
✅ **立即可用**：任何人都可以直接添加依赖

使用方式：
```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.MufHead.YRDatabase:yrdatabase-common:v1.0.0")
}
```

就是这么简单！🚀

---

**发布日期**：2026-01-14
**版本**：v1.0.0
**状态**：✅ 可用
