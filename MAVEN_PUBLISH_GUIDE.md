# YRDatabase Maven发布指南

## 概述

YRDatabase现在支持发布为Maven/Gradle依赖，其他开发者可以通过依赖管理工具直接使用。

### 发布的模块

1. **yrdatabase-common** - 公共API和协议定义
2. **yrdatabase-nukkit** - Nukkit子服插件
3. **yrdatabase-waterdog** - WaterdogPE代理端插件

---

## 发布选项

### 选项1: 本地Maven仓库（推荐用于测试）

最简单的方式，不需要任何配置。

**发布命令**：
```bash
./gradlew publishToMavenLocal
```

**其他项目如何使用**：
```gradle
repositories {
    mavenLocal()
}

dependencies {
    implementation("com.yirankuma:yrdatabase-common:1.0-SNAPSHOT")
    // 或
    implementation("com.yirankuma:yrdatabase-nukkit:1.0-SNAPSHOT")
    // 或
    implementation("com.yirankuma:yrdatabase-waterdog:1.0-SNAPSHOT")
}
```

**优点**：
- 无需配置
- 立即可用
- 适合本地开发和测试

**缺点**：
- 只能在本地使用
- 无法分享给其他开发者

---

### 选项2: GitHub Packages（推荐用于开源项目）

使用GitHub作为Maven仓库，免费且易用。

#### 步骤1: 获取GitHub Token

1. 访问 https://github.com/settings/tokens
2. 点击 "Generate new token" → "Generate new token (classic)"
3. 勾选 `write:packages` 权限
4. 生成token并保存（只显示一次！）

#### 步骤2: 配置gradle.properties

创建或编辑 `gradle.properties` 文件：
```properties
gpr.user=你的GitHub用户名
gpr.token=ghp_你的GitHub_token
```

#### 步骤3: 修改build.gradle.kts中的URL

编辑根目录的 `build.gradle.kts`，将 `yourusername` 替换为你的GitHub用户名：
```kotlin
url = uri("https://maven.pkg.github.com/yourusername/YRDatabase")
```

#### 步骤4: 发布

```bash
./gradlew publish
```

或者只发布到GitHub Packages：
```bash
./gradlew publishAllPublicationsToGitHubPackagesRepository
```

#### 其他项目如何使用

**Gradle (Kotlin DSL)**：
```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/yourusername/YRDatabase")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("com.yirankuma:yrdatabase-common:1.0-SNAPSHOT")
}
```

**Maven**：
```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/yourusername/YRDatabase</url>
    </repository>
</repositories>

<servers>
    <server>
        <id>github</id>
        <username>YOUR_GITHUB_USERNAME</username>
        <password>YOUR_GITHUB_TOKEN</password>
    </server>
</servers>

<dependency>
    <groupId>com.yirankuma</groupId>
    <artifactId>yrdatabase-common</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**优点**：
- 免费
- 与GitHub集成
- 支持私有仓库
- 适合开源项目

**缺点**：
- 需要GitHub账号
- 使用者也需要配置token

---

### 选项3: 自定义Maven仓库

使用你自己的Maven仓库（如Nexus、Artifactory等）。

#### 配置gradle.properties

```properties
customRepo.url=https://your-maven-repo.com/releases
customRepo.username=your-username
customRepo.password=your-password
```

#### 发布

```bash
./gradlew publishAllPublicationsToCustomRepoRepository
```

**优点**：
- 完全控制
- 可以设置访问权限
- 适合企业内部使用

**缺点**：
- 需要自己搭建或租用Maven仓库

---

### 选项4: Maven Central（最通用但最复杂）

发布到Maven Central，全球开发者都可以直接使用。

这个过程比较复杂，需要：
1. 注册Sonatype账号
2. 申请groupId (com.yirankuma)
3. 配置GPG签名
4. 配置POM信息

详细步骤请参考：https://central.sonatype.org/publish/publish-guide/

---

## 完整发布流程

### 1. 准备工作

#### 修改版本号

编辑 `build.gradle.kts` 中的版本号：
```kotlin
version = "1.0.0"  // 发布正式版时去掉-SNAPSHOT
```

#### 修改POM信息

编辑 `build.gradle.kts` 中的开发者信息：
```kotlin
developers {
    developer {
        id.set("yirankuma")
        name.set("YiranKuma")
        email.set("your.email@example.com")  // 修改为你的邮箱
    }
}
```

修改GitHub仓库URL：
```kotlin
url.set("https://github.com/yourusername/YRDatabase")  // 修改为你的仓库
```

### 2. 构建和测试

```bash
# 清理之前的构建
./gradlew clean

# 构建所有模块
./gradlew build

# 生成源码JAR和Javadoc JAR
./gradlew sourcesJar javadocJar

# 测试本地发布
./gradlew publishToMavenLocal
```

### 3. 检查生成的文件

发布后会生成以下文件：
```
build/publications/maven/
├── pom-default.xml          # POM文件
├── module.json              # Gradle元数据
yrdatabase-common/build/libs/
├── yrdatabase-common-1.0-SNAPSHOT.jar         # 主JAR
├── yrdatabase-common-1.0-SNAPSHOT-sources.jar # 源码JAR
└── yrdatabase-common-1.0-SNAPSHOT-javadoc.jar # Javadoc JAR
```

### 4. 发布到远程仓库

```bash
# 发布所有模块到所有配置的仓库
./gradlew publish

# 或者只发布到特定仓库
./gradlew publishAllPublicationsToMavenLocal
./gradlew publishAllPublicationsToGitHubPackagesRepository
./gradlew publishAllPublicationsToCustomRepoRepository
```

---

## 使用示例

### 场景1: 只使用API（最常见）

如果你只想使用YRDatabase的API而不需要完整的插件：

```gradle
dependencies {
    compileOnly("com.yirankuma:yrdatabase-common:1.0-SNAPSHOT")
}
```

### 场景2: 依赖Nukkit插件

如果你的插件依赖YRDatabase-Nukkit：

```gradle
dependencies {
    compileOnly("com.yirankuma:yrdatabase-nukkit:1.0-SNAPSHOT")
}
```

在 `plugin.yml` 中声明依赖：
```yaml
depend: [YRDatabase]
```

### 场景3: 依赖WaterdogPE插件

```gradle
dependencies {
    compileOnly("com.yirankuma:yrdatabase-waterdog:1.0-SNAPSHOT")
}
```

### 场景4: 完整示例项目

```kotlin
// build.gradle.kts
plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-releases/")
    maven("https://maven.pkg.github.com/yourusername/YRDatabase") {
        credentials {
            username = project.findProperty("gpr.user") as String?
            password = project.findProperty("gpr.token") as String?
        }
    }
}

dependencies {
    // Nukkit API
    compileOnly("cn.nukkit:nukkit:1.0-SNAPSHOT")

    // YRDatabase依赖
    compileOnly("com.yirankuma:yrdatabase-nukkit:1.0-SNAPSHOT")
}

tasks.shadowJar {
    // 不要打包YRDatabase，因为它已经作为插件存在
    dependencies {
        exclude(dependency("com.yirankuma:yrdatabase-nukkit:.*"))
    }
}
```

---

## 版本管理

### SNAPSHOT版本

开发中的版本，使用 `-SNAPSHOT` 后缀：
```kotlin
version = "1.0-SNAPSHOT"
```

特点：
- 每次发布会覆盖之前的版本
- 适合频繁更新
- 用于开发和测试

### 正式版本

稳定的发布版本，不带 `-SNAPSHOT`：
```kotlin
version = "1.0.0"
```

特点：
- 不可变（发布后不能修改）
- 适合生产环境
- 遵循语义化版本规范

### 版本号规范

推荐使用语义化版本：`主版本.次版本.修订号`

- **主版本**：不兼容的API修改
- **次版本**：向下兼容的功能新增
- **修订号**：向下兼容的问题修正

示例：
```
1.0.0 - 首次正式发布
1.0.1 - 修复bug
1.1.0 - 新增功能
2.0.0 - 重大更新，API不兼容
```

---

## Gradle任务参考

### 查看所有发布任务

```bash
./gradlew tasks --group publishing
```

输出示例：
```
Publishing tasks
----------------
publish - Publishes all publications to all repositories
publishToMavenLocal - Publishes all publications to the local Maven cache
publishMavenPublicationToMavenLocal - Publishes Maven publication 'maven' to the local Maven cache
publishMavenPublicationToGitHubPackagesRepository - Publishes Maven publication 'maven' to Maven repository 'GitHubPackages'
publishMavenPublicationToCustomRepoRepository - Publishes Maven publication 'maven' to Maven repository 'CustomRepo'
```

### 只发布特定模块

```bash
# 只发布common模块
./gradlew :yrdatabase-common:publish

# 只发布nukkit模块
./gradlew :yrdatabase-nukkit:publish

# 只发布waterdog模块
./gradlew :yrdatabase-waterdog:publish
```

### 生成POM预览

```bash
./gradlew generatePomFileForMavenPublication

# 查看生成的POM文件
cat yrdatabase-common/build/publications/maven/pom-default.xml
```

---

## 故障排除

### 问题1: 找不到gradle.properties

**错误**：
```
Could not get unknown property 'gpr' for object of type org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
```

**解决**：
1. 确保 `gradle.properties` 文件在项目根目录
2. 或者使用环境变量：
   ```bash
   export GITHUB_ACTOR=your-username
   export GITHUB_TOKEN=your-token
   ./gradlew publish
   ```

### 问题2: GitHub Token权限不足

**错误**：
```
401 Unauthorized
```

**解决**：
1. 检查token是否有 `write:packages` 权限
2. 重新生成token并更新 `gradle.properties`

### 问题3: Javadoc生成失败

**错误**：
```
Javadoc generation failed
```

**解决**：
代码中已经配置了 `-Xdoclint:none`，如果仍然失败，可以暂时跳过：
```bash
./gradlew publish -x javadocJar
```

### 问题4: 仓库URL错误

**错误**：
```
Could not resolve com.yirankuma:yrdatabase-common:1.0-SNAPSHOT
```

**解决**：
1. 检查仓库URL是否正确
2. 检查版本号是否匹配
3. 对于GitHub Packages，确保URL中的用户名正确

---

## 最佳实践

### 1. 使用版本标签

每次发布正式版本时，创建Git标签：
```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

### 2. 编写CHANGELOG

维护 `CHANGELOG.md` 记录每个版本的变更：
```markdown
## [1.0.0] - 2026-01-14
### Added
- Redis Pub/Sub跨服通信功能
- 统一JSON配置格式

### Changed
- 优化数据库持久化逻辑

### Fixed
- 修复转服时的数据重复问题
```

### 3. 自动化发布

使用GitHub Actions自动发布：

创建 `.github/workflows/publish.yml`：
```yaml
name: Publish to GitHub Packages

on:
  release:
    types: [created]

jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Publish package
        run: ./gradlew publish
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### 4. 发布检查清单

发布前确认：

- [ ] 更新版本号（去掉-SNAPSHOT）
- [ ] 更新CHANGELOG.md
- [ ] 运行所有测试：`./gradlew test`
- [ ] 构建成功：`./gradlew build`
- [ ] 本地测试：`./gradlew publishToMavenLocal`
- [ ] 检查POM信息正确
- [ ] 提交所有更改
- [ ] 创建Git标签
- [ ] 发布到远程仓库
- [ ] 验证发布成功
- [ ] 更新文档

---

## 快速开始

### 最简单的方式（本地测试）

```bash
# 1. 发布到本地
./gradlew publishToMavenLocal

# 2. 在另一个项目中使用
# build.gradle.kts
repositories {
    mavenLocal()
}
dependencies {
    implementation("com.yirankuma:yrdatabase-common:1.0-SNAPSHOT")
}
```

### 推荐方式（GitHub Packages）

```bash
# 1. 配置gradle.properties
echo "gpr.user=yourusername" >> gradle.properties
echo "gpr.token=your_token" >> gradle.properties

# 2. 修改build.gradle.kts中的GitHub URL

# 3. 发布
./gradlew publish

# 4. 其他项目可以直接使用（需要配置同样的认证）
```

---

## 相关文档

- [README.md](README.md) - 项目介绍
- [REDIS_PUBSUB_COMPLETE.md](REDIS_PUBSUB_COMPLETE.md) - Redis Pub/Sub功能说明
- [配置说明.md](配置说明.md) - 配置文件指南
- [Gradle Publishing Plugin](https://docs.gradle.org/current/userguide/publishing_maven.html)
- [GitHub Packages Guide](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry)

---

**发布完成后，记得在README.md中添加依赖使用说明！**
