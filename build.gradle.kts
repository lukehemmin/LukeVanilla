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
    maven("https://jitpack.io")
    maven("https://repo.nexomc.com/releases")
}

dependencies {
    //compileOnly("io.papermc.paper:paper-api:1.21.3-R0.1-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation ("com.vdurmont:emoji-java:5.1.1")
    
    // API 통신을 위한 의존성 추가
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1") 
    
//    compileOnly("io.th0rgal:oraxen:1.186.0")
    compileOnly("com.nexomc:nexo:0.1.0")
    implementation("net.dv8tion:JDA:5.2.1")
    compileOnly("net.citizensnpcs:citizens-main:2.0.37-SNAPSHOT")
    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    compileOnly("com.github.LoneDev6:API-ItemsAdder:3.6.3-beta-14")
    compileOnly("com.github.Gecolay.GSit:GSit:1.13.0") {
        exclude(group = "com.github.Gecolay")
    }
    compileOnly("com.github.Gecolay.GSit:core:1.13.0") {
        exclude(group = "com.github.Gecolay")
    }
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
            file("E:/server/20240130/plugins")
//            file("/Users/lukehemmin/Desktop/Devlop/plugin_devlop/plugins")
    )
    manifest {
        attributes(mapOf("Main-Class" to "com.lukehemmin.lukeVanilla.Main"))
    }
}
