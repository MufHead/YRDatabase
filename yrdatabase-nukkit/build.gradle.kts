plugins {
    id("com.github.johnrengelman.shadow")
}

dependencies {
    // 依赖common模块
    implementation(project(":yrdatabase-common"))

    // 所有libs的jar文件 (Nukkit核心和NukkitMaster)
    compileOnly(fileTree("../libs").include("*.jar"))

    // Redis 客户端 - Lettuce
    implementation("io.lettuce:lettuce-core:6.1.10.RELEASE")

    // Apache Commons Pool2
    implementation("org.apache.commons:commons-pool2:2.9.0")

    // MySQL 连接池 - HikariCP
    implementation("com.zaxxer:HikariCP:4.0.3")

    // MySQL JDBC 驱动
    implementation("mysql:mysql-connector-java:8.0.33")

    // JSON 处理 (已在common模块中)
    implementation("com.google.code.gson:gson:2.10.1")

    // 日志
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("org.slf4j:slf4j-simple:1.7.36")

    // Guava (用于缓存)
    implementation("com.google.guava:guava:31.1-jre")
}

// Shadow JAR 配置
tasks.shadowJar {
    archiveClassifier.set("")

    // 重定位依赖包，避免与其他插件冲突
    relocate("com.yirankuma.yrdatabase.common", "com.yirankuma.yrdatabase.nukkit.libs.common")
    relocate("io.lettuce", "com.yirankuma.yrdatabase.nukkit.libs.lettuce")
    relocate("org.apache.commons.pool2", "com.yirankuma.yrdatabase.nukkit.libs.pool2")
    relocate("com.zaxxer.hikari", "com.yirankuma.yrdatabase.nukkit.libs.hikari")
    relocate("com.mysql", "com.yirankuma.yrdatabase.nukkit.libs.mysql")
    relocate("com.google.gson", "com.yirankuma.yrdatabase.nukkit.libs.gson")
    relocate("org.slf4j", "com.yirankuma.yrdatabase.nukkit.libs.slf4j")
    relocate("com.google.common", "com.yirankuma.yrdatabase.nukkit.libs.guava")

    // 排除不需要的文件
    exclude("META-INF/DEPENDENCIES")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/NOTICE*")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/maven/**")
    exclude("module-info.class")

    // 合并服务文件
    mergeServiceFiles()

    // 打包出的文件路径和名称
    archiveFileName.set("YRDatabase.jar")

    // 只在本地构建时输出到自定义目录，JitPack构建时使用默认目录
    // 通过环境变量JITPACK判断是否在JitPack环境
    if (System.getenv("JITPACK") != "true") {
        destinationDirectory.set(file("E:/ServerPLUGINS/网易NK服务器插件"))
    }
}

// 禁用普通 jar 任务
tasks.jar {
    enabled = false
}
