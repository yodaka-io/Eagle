import java.util.Properties
// import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val mcVersion: String by project
val javaVersion: String by project

plugins {
    java
    kotlin("jvm") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.yodaka.eagle"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("net.kyori:adventure-api:4.8.1")
    // implementation "org.jetbrains.kotlin:kotlin-stdlib"
    compileOnly("io.papermc.paper:paper-api:${mcVersion}-R0.1-SNAPSHOT")
}

kotlin {
    jvmToolchain(21)
}

// val autoRelocate by tasks.register<ConfigureShadowRelocation>("configureShadowRelocation", ConfigureShadowRelocation::class) {
//     target = tasks.getByName("shadowJar") as ShadowJar?
//     val packageName = "${project.group}.${project.name.toLowerCase()}"
//     prefix = "$packageName.shaded"
// }

tasks {
    val javaVersion = JavaVersion.VERSION_21
    // withType<JavaCompile> {
    //     options.encoding = "UTF-8"
    //     sourceCompatibility = javaVersion.toString()
    //     targetCompatibility = javaVersion.toString()
    //     options.release.set(javaVersion.toString().toInt())
    // }
    // withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    //     kotlinOptions { jvmTarget = javaVersion.toString() }
    //     sourceCompatibility = javaVersion.toString()
    //     targetCompatibility = javaVersion.toString()
    // }

    // java {
    //     toolchain { languageVersion.set(JavaLanguageVersion.of(javaVersion.toString())) }
    //     sourceCompatibility = javaVersion
    //     targetCompatibility = javaVersion
    // }

    // compileKotlin {
    //     kotlinOptions.jvmTarget = "${javaVersion}" // JVMターゲットバージョン（適宜変更）
    // }

    shadowJar {
        archiveClassifier.set("")
        archiveVersion.set("")
        mergeServiceFiles()
        // project.configurations.implementation.get().isCanBeResolved = true
        // configurations = listOf(project.configurations.implementation.get())
        // dependsOn(autoRelocate)
        // minimize()
    }

    // val copyJar by creating(Copy::class) {
    //     dependsOn("jar")
    //     from("${buildDir}/libs")
    //     into(getOutputDir())
    // }

    build {
        dependsOn(shadowJar)
        // dependsOn(copyJar)
    }
}

// 外部プロパティファイルから出力ディレクトリを取得する関数
// fun getOutputDir(): String {
//     val properties = Properties()
//     val localPropertiesFile = file("local.properties")
//     if (localPropertiesFile.exists()) {
//         properties.load(localPropertiesFile.inputStream())
//     } else {
//         throw GradleException("local.properties file not found.")
//     }

//     return properties.getProperty("output.dir") ?: throw GradleException("output.dir not specified in local.properties")
// }

// // copyJarタスクでのみ出力ディレクトリを設定するための条件付き設定
// tasks.named<Copy>("copyJar") {
//     doFirst {
//         val outputDir = getOutputDir()
//         if (outputDir != null) {
//             into(outputDir)
//         } else {
//             throw GradleException("output.dir not specified in local.properties")
//         }
//     }
// }













// plugins {
//     kotlin("jvm") version "2.0.0"
//     id("com.github.johnrengelman.shadow") version "8.1.1"
// }

// group = "io.yodaka.eagle"
// version = "1.0-SNAPSHOT"

// repositories {
//     mavenCentral()
//     maven("https://papermc.io/repo/repository/maven-public/")
// }

// dependencies {
//     implementation(kotlin("stdlib"))
//     compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
// }

// java {
//     toolchain {
//         languageVersion.set(JavaLanguageVersion.of(21))
//     }
// }

// tasks {
//     compileKotlin {
//         kotlinOptions {
//             jvmTarget = "21"
//         }
//     }
//     compileTestKotlin {
//         kotlinOptions {
//             jvmTarget = "21"
//         }
//     }

//     shadowJar {
//         archiveClassifier.set("")
//         relocate("kotlin", "io.yodaka.eagle.shadow.kotlin")
//     }
// }

// tasks.withType<ProcessResources> {
//     filesMatching("plugin.yml") {
//         expand("version" to project.version)
//     }
// }
