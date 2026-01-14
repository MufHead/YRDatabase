# ✅ YRDatabase 已成功发布到 GitHub Packages！

## 发布信息

**发布时间**：2026-01-14
**发布位置**：GitHub Packages
**仓库地址**：https://github.com/MufHead/YRDatabase/packages

---

## 已发布的包

### 1. yrdatabase-common
```
Group ID: com.yirankuma
Artifact ID: yrdatabase-common
Version: 1.0-SNAPSHOT
```

**查看地址**：https://github.com/MufHead/YRDatabase/packages

### 2. yrdatabase-nukkit
```
Group ID: com.yirankuma
Artifact ID: yrdatabase-nukkit
Version: 1.0-SNAPSHOT
```

### 3. yrdatabase-waterdog
```
Group ID: com.yirankuma
Artifact ID: yrdatabase-waterdog
Version: 1.0-SNAPSHOT
```

---

## 如何使用

### 在其他项目中引入依赖

**Gradle (Kotlin DSL)**:
```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/MufHead/YRDatabase")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    // 只使用API
    compileOnly("com.yirankuma:yrdatabase-common:1.0-SNAPSHOT")

    // 或者依赖Nukkit插件
    compileOnly("com.yirankuma:yrdatabase-nukkit:1.0-SNAPSHOT")

    // 或者依赖WaterdogPE插件
    compileOnly("com.yirankuma:yrdatabase-waterdog:1.0-SNAPSHOT")
}
```

**Gradle (Groovy DSL)**:
```groovy
repositories {
    mavenCentral()
    maven {
        url 'https://maven.pkg.github.com/MufHead/YRDatabase'
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    compileOnly 'com.yirankuma:yrdatabase-common:1.0-SNAPSHOT'
}
```

**Maven**:
```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/MufHead/YRDatabase</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.yirankuma</groupId>
    <artifactId>yrdatabase-common</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

**Maven settings.xml** (配置认证):
```xml
<servers>
    <server>
        <id>github</id>
        <username>YOUR_GITHUB_USERNAME</username>
        <password>YOUR_GITHUB_TOKEN</password>
    </server>
</servers>
```

---

## 配置认证

### 方法1: 使用gradle.properties（推荐）

在项目根目录创建 `gradle.properties`:
```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.token=YOUR_GITHUB_TOKEN
```

**注意**：记得将 `gradle.properties` 添加到 `.gitignore`！

### 方法2: 使用环境变量

```bash
# Linux/Mac
export GITHUB_ACTOR=YOUR_GITHUB_USERNAME
export GITHUB_TOKEN=YOUR_GITHUB_TOKEN

# Windows
set GITHUB_ACTOR=YOUR_GITHUB_USERNAME
set GITHUB_TOKEN=YOUR_GITHUB_TOKEN
```

---

## 验证安装

在你的项目中运行：
```bash
./gradlew dependencies --configuration compileClasspath
```

应该能看到 YRDatabase 依赖被正确解析。

---

## 代码示例

### 使用 YRDatabase API

```java
import com.yirankuma.yrdatabase.YRDatabase;
import com.yirankuma.yrdatabase.api.DatabaseManager;

public class MyPlugin extends PluginBase {

    private DatabaseManager db;

    @Override
    public void onEnable() {
        // 获取 DatabaseManager
        db = YRDatabase.getDatabaseManager();

        if (db == null) {
            getLogger().error("YRDatabase 未找到！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 使用智能API保存数据
        db.smartSet("player:123456", "{\"level\":10}", 3600)
            .thenAccept(success -> {
                if (success) {
                    getLogger().info("数据保存成功");
                }
            });

        // 读取数据
        db.smartGet("player:123456")
            .thenAccept(data -> {
                if (data != null) {
                    getLogger().info("数据: " + data);
                }
            });
    }
}
```

### plugin.yml 配置

```yaml
name: MyPlugin
version: 1.0.0
main: com.example.MyPlugin
depend: [YRDatabase]  # 声明依赖
```

---

## 更新依赖

当 YRDatabase 有新版本时：

### 更新 SNAPSHOT 版本
```bash
# SNAPSHOT 版本会自动更新，只需清理缓存
./gradlew clean build --refresh-dependencies
```

### 更新到正式版本
```kotlin
dependencies {
    compileOnly("com.yirankuma:yrdatabase-common:1.0.0")  // 改为正式版本号
}
```

---

## GitHub Packages 特点

### 优点
- ✅ 与 GitHub 深度集成
- ✅ 免费（对公开仓库）
- ✅ 支持私有包
- ✅ 版本管理清晰
- ✅ 自动与代码仓库关联

### 限制
- ⚠️ 需要 GitHub Token 才能下载（即使是公开包）
- ⚠️ 每月有存储和传输限制（免费用户 500MB 存储，1GB 传输）

### 配额信息
查看你的使用情况：https://github.com/settings/billing

---

## 发布新版本

### 发布 SNAPSHOT 版本
```bash
# 直接覆盖现有的 SNAPSHOT 版本
./gradlew publishAllPublicationsToGitHubPackagesRepository
```

### 发布正式版本

1. 更新版本号：
```kotlin
// build.gradle.kts
version = "1.0.0"  // 去掉 -SNAPSHOT
```

2. 提交代码并打标签：
```bash
git add .
git commit -m "Release version 1.0.0"
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin master
git push origin v1.0.0
```

3. 发布到 GitHub Packages：
```bash
./gradlew publishAllPublicationsToGitHubPackagesRepository
```

4. 恢复 SNAPSHOT 版本：
```kotlin
version = "1.1.0-SNAPSHOT"  // 开始下一个开发周期
```

---

## 查看已发布的包

访问你的 GitHub 仓库，点击右侧的 "Packages" 链接：
https://github.com/MufHead/YRDatabase/packages

你应该能看到三个包：
- yrdatabase-common
- yrdatabase-nukkit
- yrdatabase-waterdog

---

## 故障排除

### 问题1: 无法下载依赖

**错误**：
```
Could not resolve com.yirankuma:yrdatabase-common:1.0-SNAPSHOT
```

**解决**：
1. 检查是否配置了正确的仓库 URL
2. 检查是否配置了 GitHub Token
3. 检查 Token 是否有 `read:packages` 权限

### 问题2: 401 Unauthorized

**原因**：Token 无效或过期

**解决**：
1. 重新生成 GitHub Token：https://github.com/settings/tokens
2. 更新 `gradle.properties` 中的 token

### 问题3: 404 Not Found

**原因**：包还未发布或仓库名错误

**解决**：
1. 检查包是否已发布：https://github.com/MufHead/YRDatabase/packages
2. 确认仓库 URL 中的用户名和仓库名正确

---

## 删除包版本

如果需要删除某个版本：

1. 访问：https://github.com/MufHead/YRDatabase/packages
2. 点击包名
3. 在右侧找到要删除的版本
4. 点击 "Delete version"

**注意**：正式版本删除后无法恢复！

---

## 相关链接

- **项目主页**：https://github.com/MufHead/YRDatabase
- **Packages 页面**：https://github.com/MufHead/YRDatabase/packages
- **使用文档**：[USAGE_EXAMPLE.md](USAGE_EXAMPLE.md)
- **发布指南**：[MAVEN_PUBLISH_GUIDE.md](MAVEN_PUBLISH_GUIDE.md)
- **GitHub Packages 官方文档**：https://docs.github.com/en/packages

---

## 安全提醒

⚠️ **重要**：
1. 不要将 `gradle.properties` 提交到 Git（已添加到 .gitignore）
2. 不要在公开的代码中硬编码 Token
3. 定期更换 Token
4. 如果 Token 泄露，立即在 GitHub 删除并重新生成

---

## 总结

✅ **已完成**：
- 配置 GitHub Packages 仓库
- 更新 build.gradle.kts 中的仓库 URL
- 创建 gradle.properties 配置文件
- 添加 .gitignore 保护敏感信息
- 成功发布三个模块到 GitHub Packages

✅ **立即可用**：
- 其他开发者可以通过 Gradle/Maven 直接使用
- 支持 SNAPSHOT 版本自动更新
- 完整的源码和 Javadoc

🎉 **YRDatabase 现在是一个公开可用的 Maven 依赖库了！**

---

**发布日期**：2026-01-14
**发布者**：MufHead
**状态**：✅ 成功
