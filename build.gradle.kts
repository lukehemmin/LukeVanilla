plugins {
    kotlin("jvm") version "2.0.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    kotlin("plugin.serialization") version "2.2.0"
}

group = "com.lukehemmin"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    // Velocity API를 위한 저장소 추가
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://repo.oraxen.com/releases")
    maven("https://maven.citizensnpcs.co/repo") {
        name = "citizens-repo"
    }
    maven("https://jitpack.io")
    maven("https://repo.nexomc.com/releases")
    // HMCCosmetics 저장소 추가
    maven("https://repo.hibiscusmc.com/releases") {
        name = "hibiscusmc-repo"
    }
    // Custom-Crops API 저장소 추가
    maven("https://repo.momirealms.net/releases/")
    // libby-bukkit을 위한 저장소 추가
    maven("https://repo.maven.apache.org/maven2/")
    maven("https://maven.pkg.github.com/Byteflux/libby") {
        name = "libby-repo"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.zaxxer:HikariCP:5.0.1")
//    compileOnly("io.th0rgal:oraxen:1.186.0")
    compileOnly("com.nexomc:nexo:1.8.0")
    implementation("net.dv8tion:JDA:5.6.1")
    compileOnly("net.citizensnpcs:citizens-main:2.0.39-SNAPSHOT") {
        exclude(group = "*", module = "*")
    }
    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    compileOnly("com.github.LoneDev6:API-ItemsAdder:3.6.3-beta-14")
    compileOnly("com.github.Gecolay.GSit:GSit:1.13.0") {
        exclude(group = "com.github.Gecolay")
    }
    compileOnly("com.github.Gecolay.GSit:core:1.13.0") {
        exclude(group = "com.github.Gecolay")
    }
    
    // LuckPerms API 의존성 추가
    compileOnly("net.luckperms:api:5.2")
    
    // HMCCosmetics API 의존성 추가
    compileOnly("com.hibiscusmc:HMCCosmetics:2.7.9-ff1addfd")

    // Custom-Crops API 의존성 추가
    compileOnly("net.momirealms:custom-crops:3.6.41")
    
    // Velocity API 의존성 - 버전 수정
    compileOnly("com.velocitypowered:velocity-api:3.1.1")
    // Netty buffer for PluginMessageEvent data
    implementation("io.netty:netty-buffer:4.1.99.Final")
    annotationProcessor("com.velocitypowered:velocity-api:3.1.1")
    implementation("com.openai:openai-java:1.6.1")
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.runServer {
    minecraftVersion("1.20.6")
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveBaseName.set("LukeVanilla")
    val isCI = System.getenv("CI")?.toBoolean() ?: false

    destinationDirectory.set(
        if (isCI) file("build/libs")
        else
            //file("/home/lukehemmin/LukeVanilla/run/plugins")
            file("/home/lukehemmin/LukeVanilla/jars")
    )
    manifest {
        attributes(mapOf("Main-Class" to "com.lukehemmin.lukeVanilla.Main"))
    }
}
