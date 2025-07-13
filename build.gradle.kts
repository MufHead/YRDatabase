plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.yirankuma"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-releases/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
}

dependencies {
    // Nukkit 核心
    compileOnly(files("libs/nukkit.jar"))
    
    // Redis 客户端 - Lettuce (Java 8 兼容版本)
    implementation("io.lettuce:lettuce-core:6.1.10.RELEASE")
    
    // Apache Commons Pool2 (Java 8 兼容版本)
    implementation("org.apache.commons:commons-pool2:2.9.0")
    
    // MySQL 连接池 - HikariCP (Java 8 兼容版本)
    implementation("com.zaxxer:HikariCP:4.0.3")
    
    // MySQL JDBC 驱动
    implementation("mysql:mysql-connector-java:8.0.33")
    
    // JSON 处理
    implementation("com.google.code.gson:gson:2.10.1")
    
    // 日志 (Java 8 兼容版本)
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("org.slf4j:slf4j-simple:1.7.36")
    
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

// Java 版本配置
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// 编译配置
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Shadow JAR 配置
tasks.shadowJar {
    archiveClassifier.set("")
    
    // 重定位依赖包，避免与其他插件冲突
    relocate("io.lettuce", "com.yirankuma.yrdatabase.libs.lettuce")
    relocate("org.apache.commons.pool2", "com.yirankuma.yrdatabase.libs.pool2")
    relocate("com.zaxxer.hikari", "com.yirankuma.yrdatabase.libs.hikari")
    relocate("com.mysql", "com.yirankuma.yrdatabase.libs.mysql")
    relocate("com.google.gson", "com.yirankuma.yrdatabase.libs.gson")
    relocate("org.slf4j", "com.yirankuma.yrdatabase.libs.slf4j")
    
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

    //打包出的文件路径和名称
    archiveFileName.set("YRDatabase.jar")
    destinationDirectory.set(file("E:/ServerPLUGINS/网易NK服务器插件"))
}


// 禁用普通 jar 任务
tasks.jar {
    enabled = false
}

// 测试配置
tasks.test {
    useJUnitPlatform()
}