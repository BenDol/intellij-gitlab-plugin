import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.jetbrains.intellij") version "1.17.4"
  kotlin("jvm") version "2.1.0"
}

group = "com.bendol.intellij.gitlab"
version = "0.1.2a"

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.google.code.gson:gson:2.10.1")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

intellij {
  version.set("2024.3")
  type.set("IC") // "IC" for IntelliJ Community, "IU" for Ultimate
  //plugins.set(listOf("org.jetbrains.plugins.gitlab"))

  pluginsRepositories {
    marketplace()
  }
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

sourceSets.main {
  java {
    srcDirs("src/main/gen")
  }
}

tasks {
  patchPluginXml {
    changeNotes.set("""
      Initial version of GitLab Pipelines Plugin.
    """.trimIndent())
  }
  runIde {
    // Optional: specify another directory if you want a custom sandbox
    // ideDir.set(file("/path/to/idea"))
    jvmArgs = listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
  }
  buildSearchableOptions {
    enabled = false
  }
}

