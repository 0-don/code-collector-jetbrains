import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

val envFile = rootProject.file(".env")
if (envFile.exists()) {
    val props = Properties()
    props.load(FileInputStream(envFile))
    props.forEach { (key, value) ->
        System.setProperty(key.toString(), value.toString())
    }
}

group = "don.codecollector"
version = "1.0.22"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.4.0.202509020913-r") {
        exclude(group = "org.slf4j", module = "slf4j-api")
        exclude(group = "org.slf4j", module = "slf4j-simple")
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }

    intellijPlatform {
        create("IC", "2025.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "252.*"
        }
    }

    publishing {
        token =
            providers
                .environmentVariable("JETBRAINS_TOKEN")
                .orElse(providers.systemProperty("JETBRAINS_TOKEN"))
                .orElse("")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
}