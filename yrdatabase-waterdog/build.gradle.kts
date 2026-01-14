plugins {
    id("com.github.johnrengelman.shadow")
}

dependencies {
    // 依赖common模块
    implementation(project(":yrdatabase-common"))

    // WaterdogPE API
    compileOnly("dev.waterdog.waterdogpe:waterdog:2.0.4-SNAPSHOT")

    // Redis客户端（Lettuce）- 用于Pub/Sub通信
    implementation("io.lettuce:lettuce-core:6.1.10.RELEASE")

    // 日志
    implementation("org.slf4j:slf4j-api:1.7.36")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("YRDatabase-Waterdog.jar")

    // 重定位公共模块和依赖
    relocate("com.yirankuma.yrdatabase.common", "com.yirankuma.yrdatabase.waterdog.libs.common")
    relocate("com.google.gson", "com.yirankuma.yrdatabase.waterdog.libs.gson")
    relocate("io.lettuce", "com.yirankuma.yrdatabase.waterdog.libs.lettuce")
    relocate("io.netty", "com.yirankuma.yrdatabase.waterdog.libs.netty")
    relocate("reactor", "com.yirankuma.yrdatabase.waterdog.libs.reactor")

    // 排除不需要的文件
    exclude("META-INF/DEPENDENCIES")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/NOTICE*")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")

    mergeServiceFiles()

    // 只在本地构建时输出到自定义目录，CI环境使用默认目录
    // 通过环境变量判断是否在CI环境（JitPack或Jenkins）
    val isCI = System.getenv("JITPACK") == "true" || System.getenv("JENKINS_HOME") != null
    if (!isCI) {
        destinationDirectory.set(file("E:/ServerPLUGINS/网易NK服务器插件"))
    }
}

tasks.jar {
    enabled = false
}
