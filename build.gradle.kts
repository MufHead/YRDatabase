import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

group = "com.yirankuma"
version = "1.0-SNAPSHOT"

// 所有子项目的公共配置
allprojects {
    group = "com.yirankuma"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.opencollab.dev/maven-releases/")
        maven("https://repo.opencollab.dev/maven-snapshots/")
        maven("https://repo.waterdog.dev/main") // WaterdogPE仓库
    }
}

// 子项目通用配置
subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_9
        targetCompatibility = JavaVersion.VERSION_1_9
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.named<Javadoc>("javadoc") {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).apply {
            charSet = "UTF-8"
            encoding = "UTF-8"
            // 禁用严格的Javadoc检查（避免因为缺少文档而失败）
            addStringOption("Xdoclint:none", "-quiet")
        }
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])

                pom {
                    name.set("${project.group}:${project.name}")
                    description.set("YRDatabase - Redis+MySQL数据库前置插件，支持WaterdogPE跨服通信")
                    url.set("https://github.com/MufHead/YRDatabase")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("yirankuma")
                            name.set("YiranKuma")
                            email.set("your.email@example.com")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/MufHead/YRDatabase.git")
                        developerConnection.set("scm:git:ssh://github.com/MufHead/YRDatabase.git")
                        url.set("https://github.com/MufHead/YRDatabase")
                    }
                }
            }
        }

        repositories {
            // 选项1: 本地Maven仓库（用于测试）
            mavenLocal()

            // 选项2: Lanink Maven 仓库（主要发布仓库）
            maven {
                name = "LaninkRepo"
                // 根据版本是否包含 SNAPSHOT 来决定发布到哪个仓库
                url = if (version.toString().endsWith("SNAPSHOT")) {
                    uri("https://repo.lanink.cn/repository/maven-snapshots/")
                } else {
                    uri("https://repo.lanink.cn/repository/maven-releases/")
                }
                credentials {
                    username = project.findProperty("repoUsername") as String? ?: System.getenv("REPO_USERNAME")
                    password = project.findProperty("repoPassword") as String? ?: System.getenv("REPO_PASSWORD")
                }
            }

            // 选项3: GitHub Packages
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/MufHead/YRDatabase")
                credentials {
                    username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                    password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
