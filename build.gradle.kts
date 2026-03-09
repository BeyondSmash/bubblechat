plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.5"
}

apply(from = "../gradle/hytale-version.gradle.kts")

group = "com.bubblechat"
version = "1.0.2"

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/hytalemodding/hytale-server")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
    flatDir {
        dirs("C:\\Users\\Beyon\\AppData\\Roaming\\Hytale\\install\\pre-release\\package\\game\\latest\\Server")
    }
}

dependencies {
    compileOnly(files("C:\\Users\\Beyon\\AppData\\Roaming\\Hytale\\install\\pre-release\\package\\game\\latest\\Server\\HytaleServer.jar"))
    implementation("com.google.code.findbugs:jsr305:3.0.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.shadowJar {
    archiveBaseName.set("BubbleChat")
    archiveClassifier.set("")
    archiveVersion.set("1.0.2")
    from("assets") {
        into("")
        exclude("manifest.json")
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
