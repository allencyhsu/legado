plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    id("io.ktor.plugin") version "3.0.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "io.legado"
version = "1.0.0"

application {
    mainClass.set("io.legado.server.ApplicationKt")
}

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
}

val ktorVersion = "3.0.0"
val exposedVersion = "0.53.0"

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-gson:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    // Ktor Client (TTS proxy)
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")

    // Parsing
    implementation("org.jsoup:jsoup:1.17.2")

    // EPUB - pure Java library (available on Maven Central)
    implementation("com.positiondev.epublib:epublib-core:3.1") {
        exclude(group = "org.slf4j")
        exclude(group = "xmlpull")
    }
    implementation("net.sf.kxml:kxml2:2.3.0")  // XML parser for EPUB

    // Utils
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("legado-server")
    archiveClassifier.set("all")
    archiveVersion.set("")
    mergeServiceFiles()
}

kotlin {
    jvmToolchain(17)
}
