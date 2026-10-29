plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "com.sunucu"
version = "0.1.0"

// Paper sürümü tek yerden değişir. 26.2 "deprecated", güncel olan 26.3.
val paperVersion = "26.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion.build.135-stable")

    // config.yml testi için YamlConfiguration (yeni bağımlılık değil, aynı API test sınıf yolunda).
    testImplementation("io.papermc.paper:paper-api:$paperVersion.build.135-stable")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    test {
        useJUnitPlatform()
    }
    compileJava {
        options.compilerArgs.add("-Xlint:deprecation")
    }
    runServer {
        minecraftVersion(paperVersion)
    }
}
