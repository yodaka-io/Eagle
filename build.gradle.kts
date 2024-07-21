import java.util.Properties

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
    compileOnly("io.papermc.paper:paper-api:${mcVersion}-R0.1-SNAPSHOT")
}

kotlin {
    jvmToolchain(21)
}

val localProperties = Properties().apply {
    load(file("local.properties").reader())
}

val outputDir = localProperties.getProperty("outputDir") ?: "build/libs"

tasks {
//    val javaVersion = JavaVersion.VERSION_21

    shadowJar {
        archiveClassifier.set("")
        archiveVersion.set("")
        mergeServiceFiles()
        destinationDirectory.set(file(outputDir))
    }

    build {
        dependsOn(shadowJar)
    }
}
