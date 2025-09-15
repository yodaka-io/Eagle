import java.util.Properties

val mcVersion: String by project
val javaVersion: String by project

plugins {
    java
    kotlin("jvm") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.papermc.paperweight.userdev") version "1.5.8"
}

group = "io.yodaka.eagle"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.20.2-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(kotlin("stdlib"))
    implementation("net.kyori:adventure-api:4.8.1")
    implementation("commons-io:commons-io:2.11.0")
    compileOnly("io.papermc.paper:paper-api:${mcVersion}-R0.1-SNAPSHOT")
}

kotlin {
    jvmToolchain(21)
}

// local.propertiesからoutputDirを読み込み
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val customOutputDir = localProperties.getProperty("outputDir")

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveVersion.set("")
        mergeServiceFiles()
        
        // デフォルトはbuild/libs/に出力
        destinationDirectory.set(file("build/libs"))
        
        // カスタムoutputDirが指定されている場合は追加でコピー
        if (customOutputDir != null) {
            doLast {
                copy {
                    from(archiveFile.get().asFile)
                    into(file(customOutputDir))
                }
            }
        }
    }
    
    build {
        dependsOn(shadowJar)
    }
    
    // 通常のjarタスクを無効化（shadowJarを使用）
    jar {
        enabled = false
    }
}
