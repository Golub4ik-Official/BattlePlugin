plugins {
    `java`
}

group = "battle"
version = "1.0.0"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "papermc-snapshots"
        url = uri("https://repo.papermc.io/repository/maven-snapshots/")
    }
}

dependencies {
    // Paper 1.21.11 запускается на Java 21: API-артефакт paper-api 1.21.11-R0.1-SNAPSHOT
    // собран для Java 21 (в отличие от 26.2.build.+, требующего Java 25).
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveFileName.set("BattlePlugin.jar")
}
