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
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.3-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation ("com.vdurmont:emoji-java:5.1.1")
    compileOnly("io.th0rgal:oraxen:1.183.0")
    implementation("net.dv8tion:JDA:5.2.1")
    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
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
    //destinationDirectory.set(file("C:/Users/Administrator/Desktop/server/vanlia_test/plugins"))
    destinationDirectory.set(file("/Users/lukehemmin/Desktop/plugin_devlop/plugins"))
    manifest {
        attributes(mapOf("Main-Class" to "com.lukehemmin.lukeVanilla.Main"))
    }
}