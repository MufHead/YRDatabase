# Jenkins 自动构建配置指南

## 📋 前置要求

在配置 Jenkins 之前，确保以下条件满足：

1. ✅ Jenkins 服务器已安装并运行（https://motci.cn/）
2. ✅ Jenkins 已安装以下插件：
   - Git Plugin
   - Pipeline Plugin
   - Gradle Plugin
   - JUnit Plugin
3. ✅ Jenkins 服务器配置了 JDK 17
4. ✅ 你有 Jenkins 的登录权限

---

## 🚀 方法1：通过 Jenkins Web UI 创建任务

### 步骤 1：登录 Jenkins

访问：https://motci.cn/
使用你的账号登录

### 步骤 2：创建新任务

1. 点击 "新建任务" 或 "New Item"
2. 输入任务名称：`YRDatabase-Build`
3. 选择 "Pipeline" 类型
4. 点击 "确定"

### 步骤 3：配置任务

#### 3.1 General（基本配置）

- **描述**：YRDatabase 自动构建任务
- **GitHub project**：https://github.com/MufHead/YRDatabase
- ☑️ 勾选 "丢弃旧的构建"
  - 保持构建的天数：7
  - 保持构建的最大个数：10

#### 3.2 Build Triggers（构建触发器）

选择以下一项或多项：

**选项 A：GitHub webhook 触发（推荐）**
- ☑️ 勾选 "GitHub hook trigger for GITScm polling"
- 需要在 GitHub 仓库配置 webhook（见下方）

**选项 B：定时构建**
- ☑️ 勾选 "Build periodically"
- Schedule 填写：`H */4 * * *`（每 4 小时构建一次）

**选项 C：轮询 SCM**
- ☑️ 勾选 "Poll SCM"
- Schedule 填写：`H/15 * * * *`（每 15 分钟检查一次代码更新）

#### 3.3 Pipeline（流水线配置）

- **Definition**：Pipeline script from SCM
- **SCM**：Git
- **Repository URL**：`https://github.com/MufHead/YRDatabase.git`
- **Credentials**：
  - 如果仓库是公开的，选择 "none"
  - 如果是私有仓库，需要添加 GitHub 凭证
- **Branch Specifier**：`*/master`
- **Script Path**：`Jenkinsfile`

点击 "保存"

### 步骤 4：首次构建

点击 "立即构建" 测试配置

---

## 🔗 方法2：配置 GitHub Webhook（自动触发构建）

### 在 GitHub 配置 Webhook

1. 访问你的仓库：https://github.com/MufHead/YRDatabase
2. 进入 `Settings` → `Webhooks` → `Add webhook`
3. 配置 Webhook：
   - **Payload URL**：`https://motci.cn/github-webhook/`
   - **Content type**：`application/json`
   - **Secret**：（可选，留空）
   - **Which events**：选择 "Just the push event"
   - ☑️ 勾选 "Active"
4. 点击 "Add webhook"

### 验证 Webhook

推送代码到 GitHub，Jenkins 应该自动触发构建。

---

## 📦 构建产物存储位置

构建成功后，JAR 文件会被归档到：

```
https://motci.cn/job/YRDatabase-Build/lastSuccessfulBuild/artifact/
```

具体文件路径：
- Nukkit 插件：`yrdatabase-nukkit/build/libs/YRDatabase.jar`
- WaterdogPE 插件：`yrdatabase-waterdog/build/libs/YRDatabase-Waterdog.jar`
- Common 模块：`yrdatabase-common/build/libs/yrdatabase-common-1.0-SNAPSHOT.jar`

---

## 🔧 高级配置

### 配置 JDK 17

如果 Jenkins 没有配置 JDK 17：

1. 进入 `Manage Jenkins` → `Global Tool Configuration`
2. 找到 "JDK installations"
3. 点击 "新增 JDK"
   - Name：`JDK17`
   - ☑️ 勾选 "Install automatically"
   - 选择 "Install from java.sun.com" 或指定 JAVA_HOME

### 配置构建参数

编辑 `Jenkinsfile` 添加参数化构建：

```groovy
pipeline {
    agent any

    parameters {
        choice(name: 'BUILD_TYPE', choices: ['Release', 'Debug'], description: '构建类型')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: '跳过测试')
    }

    // ... 其他配置
}
```

### 配置邮件通知

在 `Jenkinsfile` 的 `post` 部分添加：

```groovy
post {
    success {
        emailext (
            subject: "✅ YRDatabase 构建成功 - Build #${BUILD_NUMBER}",
            body: "构建成功！查看详情：${BUILD_URL}",
            to: "your-email@example.com"
        )
    }
    failure {
        emailext (
            subject: "❌ YRDatabase 构建失败 - Build #${BUILD_NUMBER}",
            body: "构建失败！查看日志：${BUILD_URL}console",
            to: "your-email@example.com"
        )
    }
}
```

---

## 🛠️ 故障排除

### 问题1：Jenkins 无法访问 GitHub

**错误信息**：`Failed to connect to repository`

**解决方案**：
1. 检查 Jenkins 服务器网络是否可以访问 GitHub
2. 如果是私有仓库，确保添加了正确的 SSH Key 或 Personal Access Token
3. 在 Jenkins 中添加凭证：
   - `Manage Jenkins` → `Manage Credentials` → `Add Credentials`
   - Kind：Username with password
   - Username：你的 GitHub 用户名
   - Password：GitHub Personal Access Token

### 问题2：Gradle 构建失败

**错误信息**：`Permission denied: ./gradlew`

**解决方案**：
```bash
# 在仓库根目录执行
git update-index --chmod=+x gradlew
git commit -m "Make gradlew executable"
git push
```

### 问题3：JDK 版本不匹配

**错误信息**：`Unsupported class file major version 61`

**解决方案**：
1. 确保 Jenkins 配置了 JDK 17
2. 在 `Jenkinsfile` 中指定 JDK：
```groovy
tools {
    jdk 'JDK17'
}
```

### 问题4：找不到 Gradle wrapper

**解决方案**：
确保 `gradle/wrapper/gradle-wrapper.jar` 被提交到 Git：
```bash
git add -f gradle/wrapper/gradle-wrapper.jar
git commit -m "Add Gradle wrapper"
git push
```

---

## 📊 查看构建状态

### 构建徽章

在 `README.md` 中添加构建状态徽章：

```markdown
![Build Status](https://motci.cn/buildStatus/icon?job=YRDatabase-Build)
```

### API 访问

通过 Jenkins API 获取构建信息：
```
https://motci.cn/job/YRDatabase-Build/lastBuild/api/json
```

---

## 🔄 与 JitPack 对比

| 特性 | Jenkins (motci.cn) | JitPack |
|------|-------------------|---------|
| **部署方式** | 需要配置任务 | 自动检测 tag |
| **触发方式** | Webhook/定时/手动 | 按需构建 |
| **构建控制** | 完全自定义 | 标准化流程 |
| **产物存储** | Jenkins 归档 | Maven 仓库 |
| **适用场景** | 内部使用、测试版本 | 公开发布、依赖管理 |

**建议**：
- 使用 **Jenkins** 进行日常开发构建和测试
- 使用 **JitPack** 发布稳定版本供其他开发者使用

---

## 📚 相关资源

- **Jenkins 官方文档**：https://www.jenkins.io/doc/
- **Pipeline 语法参考**：https://www.jenkins.io/doc/book/pipeline/syntax/
- **Gradle Plugin**：https://plugins.jenkins.io/gradle/
- **GitHub 仓库**：https://github.com/MufHead/YRDatabase

---

## 📝 完整流程示例

### 开发流程

```bash
# 1. 修改代码
vim yrdatabase-nukkit/src/main/java/...

# 2. 本地测试
./gradlew clean build

# 3. 提交到 GitHub
git add .
git commit -m "Fix: ..."
git push origin master

# 4. Jenkins 自动触发构建（如果配置了 webhook）
# 或者手动在 Jenkins 上点击 "立即构建"

# 5. 构建成功后，从 Jenkins 下载产物
# https://motci.cn/job/YRDatabase-Build/lastSuccessfulBuild/artifact/

# 6. 发布稳定版本到 JitPack
git tag -a v1.0.4 -m "Release v1.0.4"
git push origin v1.0.4
```

---

**创建日期**：2026-01-14
**Jenkins 服务器**：https://motci.cn/
**状态**：待配置
