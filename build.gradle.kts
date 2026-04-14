plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.novaauth"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    implementation("org.mindrot:jbcrypt:0.4")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    shadowJar {
        archiveClassifier.set("all")
        // relocate bcrypt to reduce classpath conflicts
        relocate("org.mindrot.jbcrypt", "com.novaauth.lib.jbcrypt")
    }

    build {
        dependsOn(shadowJar)
    }
}