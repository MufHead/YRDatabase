plugins {
    java
}

dependencies {
    // JSON处理
    implementation("com.google.code.gson:gson:2.10.1")

    // 日志接口
    compileOnly("org.slf4j:slf4j-api:1.7.36")
}
