import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    kotlin("plugin.serialization") version "1.7.10"

    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    //JavaCV
    implementation("org.bytedeco", "javacv-platform", "1.5.7")

    //Git
    implementation("com.github.sya-ri", "kgit", "1.0.5")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_16.toString()
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "com.project_rating.png_to_mp4.PNG2MP4Kt"
    }

    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })

    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}