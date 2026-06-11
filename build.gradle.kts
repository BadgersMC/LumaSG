import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.noarg") version "2.1.0"
    id("com.gradleup.shadow") version "8.3.5"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    jacoco
    `maven-publish`
}

group = "net.lumalyte"
version = "2.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases") // InvUI
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
    maven("https://repo.nexomc.com/releases/")
    mavenLocal() // nexus-core, nexus-paper (not yet published to remote)
}

dependencies {
    // Platform
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Nexus DI + coroutines — resolved via JitPack (public; same coords as other
    // BadgersMC projects). Was mavenLocal-only, which CI could not resolve.
    implementation("com.github.BadgersMC.Nexus:nexus-core:v2.2.1")
    implementation("com.github.BadgersMC.Nexus:nexus-paper:v2.2.1")

    // Kotlin (downloaded at startup by LumaSGLoader)
    compileOnly(kotlin("stdlib"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Database (downloaded at startup by LumaSGLoader)
    compileOnly("org.jetbrains.exposed:exposed-core:0.55.0")
    compileOnly("org.jetbrains.exposed:exposed-dao:0.55.0")
    compileOnly("org.jetbrains.exposed:exposed-jdbc:0.55.0")
    compileOnly("org.jetbrains.exposed:exposed-java-time:0.55.0")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.3.3")

    // GUI (downloaded at startup by LumaSGLoader)
    compileOnly("xyz.xenondevs.invui:invui:1.49")

    // Discord (downloaded at startup by LumaSGLoader)
    compileOnly("net.dv8tion:JDA:5.6.1") {
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
    testImplementation("org.jetbrains.exposed:exposed-core:0.55.0")
    testImplementation("org.jetbrains.exposed:exposed-dao:0.55.0")
    testImplementation("org.jetbrains.exposed:exposed-jdbc:0.55.0")
    testImplementation("org.jetbrains.exposed:exposed-java-time:0.55.0")
    testImplementation(kotlin("stdlib"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    mergeServiceFiles()
    // Only nexus-core and nexus-paper are shaded (internal, not on public Maven).
    // No relocation — NexusContext uses reflection to scan @Service annotations and
    // relocating the package would break its ability to match annotation class references.
    //
    // Exclude Kotlin runtime — downloaded at startup by LumaSGLoader via MavenLibraryResolver.
    // Shading these causes LinkageError (two classloaders loading the same class).
    // Be targeted: only exclude what the loader downloads. Keep kotlinx.serialization
    // (needed by Nexus/kaml, not downloaded separately).
    exclude("kotlin/**")
    exclude("kotlinx/coroutines/**")
    exclude("META-INF/kotlin*")
    exclude("_COROUTINE/**")
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

sourceSets {
    test {
        java.setSrcDirs(emptyList<File>()) // exclude legacy Java tests; new tests are in src/test/kotlin
    }
}

noArg {
    annotation("net.badgersmc.nexus.config.ConfigFile")
}

tasks.test {
    useJUnitPlatform()
}

detekt {
    config.setFrom(file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        xml.required.set(true)
        html.required.set(true)
        sarif.required.set(true)
    }
}
