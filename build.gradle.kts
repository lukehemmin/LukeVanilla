plugins {
    kotlin("jvm") version "2.0.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.lukehemmin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://repo.oraxen.com/releases")
    maven("https://maven.citizensnpcs.co/repo") {
        name = "citizens-repo"
    }
    maven("https://jitpack.io") {
        name = "LoneLibs"
    }
}

dependencies {
    //compileOnly("io.papermc.paper:paper-api:1.21.3-R0.1-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation ("com.vdurmont:emoji-java:5.1.1")
    compileOnly("io.th0rgal:oraxen:1.186.0")
    implementation("net.dv8tion:JDA:5.2.1")
    compileOnly("net.citizensnpcs:citizens-main:2.0.37-SNAPSHOT")
    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    compileOnly("com.github.LoneDev6:API-ItemsAdder:3.6.3-beta-14")
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
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
            file("C:/Users/Administrator/Desktop/server/테스트1.21.4/plugins")
//            file("/Users/lukehemmin/Desktop/Devlop/plugin_devlop/plugins")
    )
    manifest {
        attributes(mapOf("Main-Class" to "com.lukehemmin.lukeVanilla.Main"))
    }
}
