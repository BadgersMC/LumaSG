import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.1.0"
    id("com.gradleup.shadow") version "8.3.5"
    `maven-publish`
}

group = "net.lumalyte"
version = "2.0.0-SNAPSHOT"

repositories {
    mavenLocal() // nexus-core, nexus-paper
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases") // InvUI
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
    maven("https://repo.nexomc.com/releases/")
}

dependencies {
    // Platform
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // Nexus DI + coroutines (local Maven)
    implementation("net.badgersmc:nexus-core:1.5.3")
    implementation("net.badgersmc:nexus-paper:1.5.3")

    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.55.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.55.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.55.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.55.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.3")

    // GUI
    implementation("xyz.xenondevs.invui:invui:1.46")

    // Discord
    implementation("net.dv8tion:JDA:5.6.1") {
        exclude(module = "opus-java")
    }

    // Adventure text (bundled with Paper, compileOnly)
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")

    // Hooks (optional)
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.nexomc:nexo:1.8.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.127.0")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("com.h2database:h2:2.2.224") // in-memory DB for Exposed tests
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("xyz.xenondevs", "net.lumalyte.lumasg.shaded.invui")
    relocate("com.zaxxer.hikari", "net.lumalyte.lumasg.shaded.hikari")
    relocate("org.jetbrains.exposed", "net.lumalyte.lumasg.shaded.exposed")
}

sourceSets {
    test {
        java.setSrcDirs(emptyList<File>()) // exclude legacy Java tests; new tests are in src/test/kotlin
    }
}

tasks.test {
    useJUnitPlatform()
}
