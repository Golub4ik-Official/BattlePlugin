plugins {
    `java`
    id("com.gradleup.shadow") version "8.3.5"
}

group = "battle"
version = "1.2.0"

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
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // Paper 1.21.11 запускается на Java 21: API-артефакт paper-api 1.21.11-R0.1-SNAPSHOT
    // собран для Java 21 (в отличие от 26.2.build.+, требующего Java 25).
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // TAB v6 — интеграция цветных ников в табе и над головой (softdepend, см. plugin.yml)
    compileOnly("com.github.NEZNAMY:TAB-API:6.0.0")
    // SQLite для хранения статистики битв (упаковывается в jar плагина)
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveFileName.set("BattlePlugin-${project.version}.jar")
}

tasks.shadowJar {
    archiveFileName.set("BattlePlugin.jar")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/INDEX.LIST", "META-INF/LICENSE*", "META-INF/NOTICE*")
}

// Финальный артефакт (build/libs/BattlePlugin.jar) — fat-jar со встроенным SQLite-драйвером.
tasks.assemble {
    dependsOn(tasks.shadowJar)
}
